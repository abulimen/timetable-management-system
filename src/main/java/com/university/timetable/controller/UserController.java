package com.university.timetable.controller;

import com.university.timetable.domain.User;
import com.university.timetable.dto.CreateUserRequest;
import com.university.timetable.dto.UpdateUserRequest;
import com.university.timetable.dto.UserDTO;
import com.university.timetable.service.AuthService;
import com.university.timetable.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * User management controller for admin operations.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
public class UserController {

    private final UserService userService;
    private final AuthService authService;

    /**
     * Get all users with pagination.
     * GET /api/users
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Page<UserDTO>> getAllUsers(
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(userService.getAllUsers(pageable));
    }

    /**
     * Get user by ID.
     * GET /api/users/{id}
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<UserDTO> getUserById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserById(id));
    }

    /**
     * Search users by name or email.
     * GET /api/users/search?q=query
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Page<UserDTO>> searchUsers(
            @RequestParam("q") String query,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(userService.searchUsers(query, pageable));
    }

    /**
     * Create a new user.
     * POST /api/users
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> createUser(
            @Valid @RequestBody CreateUserRequest request,
            Authentication authentication) {
        try {
            User currentUser = authService.getCurrentUser(authentication.getName());
            UserDTO createdUser = userService.createUser(request, currentUser);
            return ResponseEntity.ok(createdUser);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update an existing user.
     * PUT /api/users/{id}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        try {
            User currentUser = authService.getCurrentUser(authentication.getName());
            UserDTO updatedUser = userService.updateUser(id, request, currentUser);
            return ResponseEntity.ok(updatedUser);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Deactivate a user (soft delete).
     * DELETE /api/users/{id}
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> deactivateUser(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            User currentUser = authService.getCurrentUser(authentication.getName());
            userService.deactivateUser(id, currentUser);
            return ResponseEntity.ok(Map.of("message", "User deactivated successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Reset user password (admin action).
     * POST /api/users/{id}/reset-password
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> resetPassword(
            @PathVariable Long id,
            Authentication authentication) {
        try {
            User currentUser = authService.getCurrentUser(authentication.getName());
            String newPassword = userService.resetPassword(id, currentUser);
            return ResponseEntity.ok(Map.of(
                    "message", "Password reset successfully",
                    "temporaryPassword", newPassword));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Lock user account.
     * POST /api/users/{id}/lock
     */
    @PostMapping("/{id}/lock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> lockUser(
            @PathVariable Long id,
            @RequestParam(defaultValue = "30") int minutes) {
        try {
            userService.lockUser(id, minutes);
            return ResponseEntity.ok(Map.of("message", "User locked for " + minutes + " minutes"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Unlock user account.
     * POST /api/users/{id}/unlock
     */
    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> unlockUser(@PathVariable Long id) {
        try {
            userService.unlockUser(id);
            return ResponseEntity.ok(Map.of("message", "User unlocked successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Change own password.
     * PUT /api/users/me/password
     */
    @PutMapping("/me/password")
    public ResponseEntity<?> changeOwnPassword(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        try {
            User currentUser = authService.getCurrentUser(authentication.getName());
            String currentPassword = request.get("currentPassword");
            String newPassword = request.get("newPassword");

            if (currentPassword == null || newPassword == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "Both currentPassword and newPassword are required"));
            }

            userService.changePassword(currentUser.getId(), currentPassword, newPassword);
            return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update own profile.
     * PUT /api/users/me
     */
    @PutMapping("/me")
    public ResponseEntity<?> updateOwnProfile(
            @RequestBody UpdateUserRequest request,
            Authentication authentication) {
        try {
            User currentUser = authService.getCurrentUser(authentication.getName());

            // Users can only update their own profile fields, not role or active status
            request.setRole(null);
            request.setActive(null);

            UserDTO updatedUser = userService.updateUser(currentUser.getId(), request, currentUser);
            return ResponseEntity.ok(updatedUser);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
