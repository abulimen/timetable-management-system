package com.university.timetable.service;

import com.university.timetable.domain.Lecturer;
import com.university.timetable.domain.User;
import com.university.timetable.domain.UserRole;
import com.university.timetable.dto.CreateUserRequest;
import com.university.timetable.dto.UpdateUserRequest;
import com.university.timetable.dto.UserDTO;
import com.university.timetable.repository.LecturerRepository;
import com.university.timetable.repository.RefreshTokenRepository;
import com.university.timetable.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for user management operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final LecturerRepository lecturerRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";

    /**
     * Get all users with pagination.
     */
    public Page<UserDTO> getAllUsers(Pageable pageable) {
        return userRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toDTO);
    }

    /**
     * Get user by ID.
     */
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        return toDTO(user);
    }

    /**
     * Search users by name or email.
     */
    public Page<UserDTO> searchUsers(String query, Pageable pageable) {
        return userRepository.searchUsers(query, pageable)
                .map(this::toDTO);
    }

    /**
     * Get users by role.
     */
    public List<UserDTO> getUsersByRole(UserRole role) {
        return userRepository.findByRole(role).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create a new user.
     */
    @Transactional
    public UserDTO createUser(CreateUserRequest request, User creatorUser) {
        // Check if email already exists
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        // Validate role hierarchy - can only create users with lower roles
        if (!canManageRole(creatorUser.getRole(), request.getRole())) {
            throw new IllegalArgumentException("You cannot create a user with role: " + request.getRole());
        }

        // Validate password complexity
        validatePassword(request.getPassword());

        User user = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .phone(request.getPhone())
                .department(request.getDepartment())
                .role(request.getRole())
                .active(true)
                .emailVerified(false)
                .mustChangePassword(true)
                .build();

        // Link to lecturer if specified
        if (request.getLecturerId() != null) {
            Lecturer lecturer = lecturerRepository.findById(request.getLecturerId())
                    .orElseThrow(() -> new IllegalArgumentException("Lecturer not found: " + request.getLecturerId()));
            user.setLecturer(lecturer);
        }

        user = userRepository.save(user);
        log.info("User created: {} by {}", user.getEmail(), creatorUser.getEmail());

        return toDTO(user);
    }

    /**
     * Update an existing user.
     */
    @Transactional
    public UserDTO updateUser(Long id, UpdateUserRequest request, User updaterUser) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        // Check permission - can only edit users with lower or equal roles
        if (!canManageRole(updaterUser.getRole(), user.getRole())) {
            throw new IllegalArgumentException("You cannot edit this user");
        }

        // Update email if changed
        if (request.getEmail() != null && !request.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
                throw new IllegalArgumentException("Email already exists: " + request.getEmail());
            }
            user.setEmail(request.getEmail().toLowerCase());
        }

        if (request.getFirstName() != null) {
            user.setFirstName(request.getFirstName());
        }
        if (request.getLastName() != null) {
            user.setLastName(request.getLastName());
        }
        if (request.getPhone() != null) {
            user.setPhone(request.getPhone());
        }
        if (request.getDepartment() != null) {
            user.setDepartment(request.getDepartment());
        }
        if (request.getRole() != null && canManageRole(updaterUser.getRole(), request.getRole())) {
            user.setRole(request.getRole());
        }
        if (request.getActive() != null) {
            user.setActive(request.getActive());
        }

        // Link to lecturer if specified
        if (request.getLecturerId() != null) {
            Lecturer lecturer = lecturerRepository.findById(request.getLecturerId())
                    .orElseThrow(() -> new IllegalArgumentException("Lecturer not found: " + request.getLecturerId()));
            user.setLecturer(lecturer);
        }

        user = userRepository.save(user);
        log.info("User updated: {} by {}", user.getEmail(), updaterUser.getEmail());

        return toDTO(user);
    }

    /**
     * Deactivate a user (soft delete).
     */
    @Transactional
    public void deactivateUser(Long id, User deleterUser) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        // Only SUPER_ADMIN can deactivate users
        if (deleterUser.getRole() != UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Only SUPER_ADMIN can deactivate users");
        }

        // Cannot deactivate yourself
        if (user.getId().equals(deleterUser.getId())) {
            throw new IllegalArgumentException("You cannot deactivate your own account");
        }

        user.setActive(false);
        userRepository.save(user);

        // Revoke all refresh tokens
        refreshTokenRepository.revokeAllUserTokens(id);

        log.info("User deactivated: {} by {}", user.getEmail(), deleterUser.getEmail());
    }

    /**
     * Reset user password (admin action).
     */
    @Transactional
    public String resetPassword(Long id, User adminUser) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        // Check permission
        if (!canManageRole(adminUser.getRole(), user.getRole())) {
            throw new IllegalArgumentException("You cannot reset this user's password");
        }

        // Generate random password
        String newPassword = generateRandomPassword();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(true);
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        // Revoke all existing refresh tokens
        refreshTokenRepository.revokeAllUserTokens(id);

        log.info("Password reset for user: {} by {}", user.getEmail(), adminUser.getEmail());

        return newPassword;
    }

    /**
     * Lock user account.
     */
    @Transactional
    public void lockUser(Long id, int minutes) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        LocalDateTime lockUntil = LocalDateTime.now().plusMinutes(minutes);
        userRepository.lockAccount(id, lockUntil);

        // Revoke all refresh tokens
        refreshTokenRepository.revokeAllUserTokens(id);

        log.info("User locked until {}: {}", lockUntil, user.getEmail());
    }

    /**
     * Unlock user account.
     */
    @Transactional
    public void unlockUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));

        userRepository.resetFailedLoginAttempts(id);
        log.info("User unlocked: {}", user.getEmail());
    }

    /**
     * Change user's own password.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // Verify current password
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password is incorrect");
        }

        // Validate new password
        validatePassword(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        // Revoke all other refresh tokens (logout from other devices)
        refreshTokenRepository.revokeAllUserTokens(userId);

        log.info("Password changed for user: {}", user.getEmail());
    }

    /**
     * Check if user can manage another role.
     */
    private boolean canManageRole(UserRole managerRole, UserRole targetRole) {
        // SUPER_ADMIN can manage anyone
        if (managerRole == UserRole.SUPER_ADMIN) {
            return true;
        }
        // ADMIN can manage COORDINATOR and below
        if (managerRole == UserRole.ADMIN) {
            return targetRole.ordinal() >= UserRole.COORDINATOR.ordinal();
        }
        return false;
    }

    /**
     * Validate password complexity.
     */
    private void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw new IllegalArgumentException("Password must be at least 8 characters");
        }
        if (!password.matches(".*[A-Z].*")) {
            throw new IllegalArgumentException("Password must contain at least one uppercase letter");
        }
        if (!password.matches(".*[a-z].*")) {
            throw new IllegalArgumentException("Password must contain at least one lowercase letter");
        }
        if (!password.matches(".*[0-9].*")) {
            throw new IllegalArgumentException("Password must contain at least one number");
        }
    }

    /**
     * Generate random password.
     */
    private String generateRandomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder password = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            password.append(PASSWORD_CHARS.charAt(random.nextInt(PASSWORD_CHARS.length())));
        }
        return password.toString();
    }

    /**
     * Convert User entity to DTO.
     */
    private UserDTO toDTO(User user) {
        return UserDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .phone(user.getPhone())
                .department(user.getDepartment())
                .role(user.getRole())
                .lecturerId(user.getLecturer() != null ? user.getLecturer().getId() : null)
                .lecturerName(user.getLecturer() != null ? user.getLecturer().getName() : null)
                .active(user.getActive())
                .emailVerified(user.getEmailVerified())
                .createdAt(user.getCreatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .mustChangePassword(user.getMustChangePassword())
                .build();
    }
}
