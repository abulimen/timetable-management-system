package com.university.timetable.controller;

import com.university.timetable.domain.User;
import com.university.timetable.dto.LoginRequest;
import com.university.timetable.dto.LoginResponse;
import com.university.timetable.dto.RefreshTokenRequest;
import com.university.timetable.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Authentication controller handling login, logout, and token refresh.
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService authService;

    /**
     * Login with email and password.
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            LoginResponse response = authService.login(request);
            return ResponseEntity.ok(response);
        } catch (LockedException e) {
            log.warn("Login failed - account locked: {}", request.getEmail());
            return ResponseEntity.status(423).body(Map.of(
                    "error", "Account Locked",
                    "message", e.getMessage()));
        } catch (BadCredentialsException e) {
            log.warn("Login failed - bad credentials: {}", request.getEmail());
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Authentication Failed",
                    "message", "Invalid email or password"));
        } catch (Exception e) {
            log.error("Login error: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of(
                    "error", "Internal Error",
                    "message", "An error occurred during login"));
        }
    }

    /**
     * Refresh access token using refresh token.
     * POST /api/auth/refresh
     */
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        try {
            LoginResponse response = authService.refreshToken(request);
            return ResponseEntity.ok(response);
        } catch (BadCredentialsException e) {
            log.warn("Token refresh failed: {}", e.getMessage());
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Token Refresh Failed",
                    "message", "Invalid or expired refresh token"));
        }
    }

    /**
     * Logout by revoking refresh token.
     * POST /api/auth/logout
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        String refreshToken = request != null ? request.getRefreshToken() : null;
        authService.logout(refreshToken);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Get current authenticated user info.
     * GET /api/auth/me
     */
    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of(
                    "error", "Not Authenticated",
                    "message", "No valid authentication found"));
        }

        String email = authentication.getName();
        User user = authService.getCurrentUser(email);

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getEmail(),
                "firstName", user.getFirstName(),
                "lastName", user.getLastName(),
                "role", user.getRole().name(),
                "lecturerId", user.getLecturer() != null ? user.getLecturer().getId() : null,
                "lastLoginAt", user.getLastLoginAt() != null ? user.getLastLoginAt().toString() : null));
    }

    /**
     * Logout from all devices.
     * POST /api/auth/logout-all
     */
    @PostMapping("/logout-all")
    public ResponseEntity<?> logoutAll() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        User user = authService.getCurrentUser(email);

        authService.logoutAll(user.getId());

        return ResponseEntity.ok(Map.of("message", "Logged out from all devices"));
    }
}
