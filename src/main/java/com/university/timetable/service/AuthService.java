package com.university.timetable.service;

import com.university.timetable.domain.RefreshToken;
import com.university.timetable.domain.User;
import com.university.timetable.dto.LoginRequest;
import com.university.timetable.dto.LoginResponse;
import com.university.timetable.dto.RefreshTokenRequest;
import com.university.timetable.repository.RefreshTokenRepository;
import com.university.timetable.repository.UserRepository;
import com.university.timetable.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Authentication service handling login, logout, and token refresh.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 30;

    /**
     * Authenticate user and generate tokens.
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.getEmail())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        // Check if account is locked
        if (user.isLocked()) {
            log.warn("Login attempt for locked account: {}", request.getEmail());
            throw new LockedException("Account is locked. Try again after " +
                    user.getLockedUntil().toString());
        }

        // Check if account is active
        if (!user.getActive()) {
            log.warn("Login attempt for deactivated account: {}", request.getEmail());
            throw new BadCredentialsException("Account is deactivated");
        }

        try {
            // Authenticate with Spring Security
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

            // Reset failed attempts on successful login
            userRepository.resetFailedLoginAttempts(user.getId());
            userRepository.updateLastLoginTime(user.getId(), LocalDateTime.now());

            // Generate tokens
            String accessToken = jwtService.generateAccessToken(
                    user.getEmail(),
                    user.getRole().name(),
                    user.getId());
            String refreshToken = jwtService.generateRefreshToken(user.getEmail(), user.getId());

            // Save refresh token
            saveRefreshToken(user, refreshToken);

            log.info("User logged in successfully: {}", user.getEmail());

            return buildLoginResponse(user, accessToken, refreshToken);

        } catch (AuthenticationException e) {
            handleFailedLogin(user);
            throw new BadCredentialsException("Invalid email or password");
        }
    }

    /**
     * Refresh access token using refresh token.
     */
    @Transactional
    public LoginResponse refreshToken(RefreshTokenRequest request) {
        String refreshTokenStr = request.getRefreshToken();

        // Validate refresh token format
        if (!jwtService.isTokenValid(refreshTokenStr)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        // Check token type
        String tokenType = jwtService.extractTokenType(refreshTokenStr);
        if (!"refresh".equals(tokenType)) {
            throw new BadCredentialsException("Invalid token type");
        }

        // Find token in database
        RefreshToken storedToken = refreshTokenRepository.findValidToken(
                refreshTokenStr,
                LocalDateTime.now())
                .orElseThrow(() -> new BadCredentialsException("Refresh token not found or expired"));

        // Get user
        User user = storedToken.getUser();

        if (!user.getActive()) {
            throw new BadCredentialsException("User account is deactivated");
        }

        // Generate new tokens
        String newAccessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole().name(),
                user.getId());
        String newRefreshToken = jwtService.generateRefreshToken(user.getEmail(), user.getId());

        // Revoke old refresh token and save new one
        refreshTokenRepository.revokeToken(refreshTokenStr);
        saveRefreshToken(user, newRefreshToken);

        log.debug("Token refreshed for user: {}", user.getEmail());

        return buildLoginResponse(user, newAccessToken, newRefreshToken);
    }

    /**
     * Logout user by revoking refresh token.
     */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isEmpty()) {
            refreshTokenRepository.revokeToken(refreshToken);
            log.debug("Refresh token revoked");
        }
    }

    /**
     * Logout from all devices by revoking all refresh tokens.
     */
    @Transactional
    public void logoutAll(Long userId) {
        refreshTokenRepository.revokeAllUserTokens(userId);
        log.info("All refresh tokens revoked for user ID: {}", userId);
    }

    /**
     * Get current user from authentication.
     */
    public User getCurrentUser(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BadCredentialsException("User not found"));
    }

    /**
     * Handle failed login attempt.
     */
    private void handleFailedLogin(User user) {
        int attempts = user.getFailedLoginAttempts() + 1;
        userRepository.incrementFailedLoginAttempts(user.getId());

        if (attempts >= MAX_FAILED_ATTEMPTS) {
            LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(LOCK_DURATION_MINUTES);
            userRepository.lockAccount(user.getId(), lockUntil);
            log.warn("Account locked due to {} failed attempts: {}", attempts, user.getEmail());
        } else {
            log.debug("Failed login attempt {} for user: {}", attempts, user.getEmail());
        }
    }

    /**
     * Save refresh token to database.
     */
    private void saveRefreshToken(User user, String token) {
        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .user(user)
                .expiresAt(LocalDateTime.now().plusSeconds(jwtService.getRefreshTokenExpirationMs() / 1000))
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);
    }

    /**
     * Build login response.
     */
    private LoginResponse buildLoginResponse(User user, String accessToken, String refreshToken) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtService.getAccessTokenExpirationMs() / 1000)
                .user(LoginResponse.UserDTO.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .firstName(user.getFirstName())
                        .lastName(user.getLastName())
                        .role(user.getRole().name())
                        .lecturerId(user.getLecturer() != null ? user.getLecturer().getId() : null)
                        .build())
                .build();
    }
}
