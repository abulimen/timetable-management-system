package com.university.timetable.repository;

import com.university.timetable.domain.User;
import com.university.timetable.domain.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find user by email (case-insensitive).
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * Check if email exists.
     */
    boolean existsByEmailIgnoreCase(String email);

    /**
     * Find all active users.
     */
    List<User> findByActiveTrue();

    /**
     * Find users by role.
     */
    List<User> findByRole(UserRole role);

    /**
     * Find users by role with pagination.
     */
    Page<User> findByRole(UserRole role, Pageable pageable);

    /**
     * Find all users with pagination.
     */
    Page<User> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find users linked to a specific lecturer.
     */
    Optional<User> findByLecturerId(Long lecturerId);

    /**
     * Increment failed login attempts.
     */
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = u.failedLoginAttempts + 1 WHERE u.id = :userId")
    void incrementFailedLoginAttempts(@Param("userId") Long userId);

    /**
     * Reset failed login attempts.
     */
    @Modifying
    @Query("UPDATE User u SET u.failedLoginAttempts = 0, u.lockedUntil = null WHERE u.id = :userId")
    void resetFailedLoginAttempts(@Param("userId") Long userId);

    /**
     * Lock user account until specified time.
     */
    @Modifying
    @Query("UPDATE User u SET u.lockedUntil = :lockUntil WHERE u.id = :userId")
    void lockAccount(@Param("userId") Long userId, @Param("lockUntil") LocalDateTime lockUntil);

    /**
     * Update last login time.
     */
    @Modifying
    @Query("UPDATE User u SET u.lastLoginAt = :loginTime WHERE u.id = :userId")
    void updateLastLoginTime(@Param("userId") Long userId, @Param("loginTime") LocalDateTime loginTime);

    /**
     * Search users by name or email.
     */
    @Query("SELECT u FROM User u WHERE " +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<User> searchUsers(@Param("search") String search, Pageable pageable);

    /**
     * Count users by role.
     */
    long countByRole(UserRole role);

    /**
     * Count active users.
     */
    long countByActiveTrue();

    /**
     * Get all user identifiers (email, phone) for validation.
     */
    @Query("SELECT new com.university.timetable.dto.UserIdentifierDTO(u.email, u.phone) FROM User u")
    List<com.university.timetable.dto.UserIdentifierDTO> findAllIdentifiers();
}
