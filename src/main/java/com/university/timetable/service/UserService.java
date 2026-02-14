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
import java.util.Optional;
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
    private final EmailService emailService;

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%";

    /**
     * Get all users with pagination.
     */
    @Transactional(readOnly = true)
    public Page<UserDTO> getAllUsers(Pageable pageable) {
        return userRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toDTO);
    }

    /**
     * Get user by ID.
     */
    @Transactional(readOnly = true)
    public UserDTO getUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + id));
        return toDTO(user);
    }

    /**
     * Search users by name or email.
     */
    @Transactional(readOnly = true)
    public Page<UserDTO> searchUsers(String query, Pageable pageable) {
        return userRepository.searchUsers(query, pageable)
                .map(this::toDTO);
    }

    /**
     * Get users by role.
     */
    @Transactional(readOnly = true)
    public List<UserDTO> getUsersByRole(UserRole role) {
        return userRepository.findByRole(role).stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Create a new user.
     * Password is auto-generated and emailed to the user.
     */
    @Transactional
    public UserDTO createUser(CreateUserRequest request, User creatorUser) {
        // Check if email already exists
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists: " + request.getEmail());
        }

        // SUPER_ADMIN cannot be created - there can only be one!
        if (request.getRole() == UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException(
                    "Cannot create SUPER_ADMIN accounts. There can only be one SUPER_ADMIN.");
        }

        // Validate role hierarchy - can only create users with lower roles
        if (!canManageRole(creatorUser.getRole(), request.getRole())) {
            throw new IllegalArgumentException("You cannot create a user with role: " + request.getRole());
        }

        // Auto-generate secure password
        String generatedPassword = generateRandomPassword();

        User user = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(generatedPassword))
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

        // Auto-create/link Lecturer entity if role is LECTURER
        if (user.getRole() == UserRole.LECTURER) {
            createOrLinkLecturer(user);
        }

        // Send welcome email with credentials (async)
        emailService.sendWelcomeEmail(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                generatedPassword);

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

        // Track previous role for lecturer sync
        UserRole previousRole = user.getRole();

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

        // Sync Lecturer entity if role changed
        syncLecturerEntity(user, previousRole);

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

        // Cannot deactivate any SUPER_ADMIN account (including yourself)
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Cannot deactivate SUPER_ADMIN accounts");
        }

        user.setActive(false);
        userRepository.save(user);

        // Unlink lecturer if user was a LECTURER
        if (user.getRole() == UserRole.LECTURER) {
            unlinkLecturer(user);
        }

        // Revoke all refresh tokens
        refreshTokenRepository.revokeAllUserTokens(id);

        log.info("User deactivated: {} by {}", user.getEmail(), deleterUser.getEmail());
    }

    /**
     * Reset user password (admin action).
     * New password is emailed to the user.
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

        // Send password reset email (async)
        emailService.sendPasswordResetEmail(
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                newPassword);

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

        // Cannot lock SUPER_ADMIN accounts
        if (user.getRole() == UserRole.SUPER_ADMIN) {
            throw new IllegalArgumentException("Cannot lock SUPER_ADMIN accounts");
        }

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
     * Synchronize Lecturer entity based on user role.
     * - If user has LECTURER role: Create Lecturer if not exists, link it
     * - If user doesn't have LECTURER role: Unlink existing Lecturer
     */
    private void syncLecturerEntity(User user, UserRole previousRole) {
        boolean wasLecturer = previousRole == UserRole.LECTURER;
        boolean isLecturer = user.getRole() == UserRole.LECTURER;

        if (isLecturer && !wasLecturer) {
            // User is now a LECTURER - create/link Lecturer entity
            createOrLinkLecturer(user);
        } else if (!isLecturer && wasLecturer) {
            // User is no longer a LECTURER - unlink Lecturer entity
            unlinkLecturer(user);
        }
    }

    /**
     * Create or link a Lecturer entity for a user with LECTURER role.
     * Updates BOTH sides of the bidirectional relationship.
     */
    private void createOrLinkLecturer(User user) {
        // Check if lecturer already exists for this user (via Lecturer.user)
        Optional<Lecturer> existingLecturer = lecturerRepository.findByUser(user);
        if (existingLecturer.isPresent()) {
            // Ensure User.lecturer is also set
            if (user.getLecturer() == null) {
                user.setLecturer(existingLecturer.get());
                userRepository.save(user);
            }
            log.info("Lecturer already linked to user: {}", user.getEmail());
            return;
        }

        // Check if there's a lecturer with matching email that can be linked
        Optional<Lecturer> lecturerByEmail = lecturerRepository.findByEmail(user.getEmail());
        if (lecturerByEmail.isPresent()) {
            Lecturer lecturer = lecturerByEmail.get();
            if (lecturer.getUser() == null) {
                // Link both sides
                lecturer.setUser(user);
                user.setLecturer(lecturer);
                lecturerRepository.save(lecturer);
                userRepository.save(user);
                log.info("Linked existing lecturer {} to user {}", lecturer.getName(), user.getEmail());
                return;
            }
        }

        // Create new Lecturer entity
        String fullName = user.getFirstName() + " " + user.getLastName();
        Lecturer newLecturer = new Lecturer();
        newLecturer.setName(fullName);
        newLecturer.setEmail(user.getEmail());
        newLecturer.setUser(user);
        Lecturer savedLecturer = lecturerRepository.save(newLecturer);

        // Link user to lecturer
        user.setLecturer(savedLecturer);
        userRepository.save(user);

        log.info("Created new lecturer {} for user {}", fullName, user.getEmail());
    }

    /**
     * Unlink a Lecturer entity when user is no longer a LECTURER.
     * Does NOT delete the Lecturer - they may have courses/lessons assigned.
     */
    private void unlinkLecturer(User user) {
        lecturerRepository.findByUser(user).ifPresent(lecturer -> {
            lecturer.setUser(null);
            lecturerRepository.save(lecturer);
            log.info("Unlinked lecturer {} from user {}", lecturer.getName(), user.getEmail());
        });
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
     * Get all user identifiers for validation.
     */
    @Transactional(readOnly = true)
    public List<com.university.timetable.dto.UserIdentifierDTO> getAllUserIdentifiers() {
        return userRepository.findAllIdentifiers();
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
