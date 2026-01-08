package com.university.timetable.domain;

/**
 * User roles for role-based access control.
 * Hierarchical: SUPER_ADMIN > ADMIN > COORDINATOR > LECTURER > VIEWER
 */
public enum UserRole {
    SUPER_ADMIN, // Full system control, can manage admins
    ADMIN, // System configuration, user management
    COORDINATOR, // Timetable creation and management
    LECTURER, // View schedules, manage own availability
    VIEWER // Read-only access to timetables
}
