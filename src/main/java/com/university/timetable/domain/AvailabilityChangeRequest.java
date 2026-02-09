package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.*;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * AvailabilityChangeRequest - represents a request from a lecturer to change
 * their availability after the deadline has passed.
 * 
 * Per USER_AUTH_REQUIREMENTS.md Section 3.4.3:
 * After the availability deadline, lecturers cannot directly edit their
 * availability.
 * Instead, they must submit a change request that goes through approval
 * workflow.
 */
@Entity
@Table(name = "availability_change_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvailabilityChangeRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The lecturer whose availability is being changed.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id", nullable = false)
    private Lecturer lecturer;

    /**
     * The user who submitted this request.
     * Usually the lecturer's linked user account.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requested_by_id", nullable = false)
    private User requestedBy;

    // ==================== Requested Change Details ====================

    /**
     * Day of the week for the availability change.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, columnDefinition = "VARCHAR(20)")
    private DayOfWeek dayOfWeek;

    /**
     * Start time of the availability period.
     */
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    /**
     * End time of the availability period.
     */
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    /**
     * The new status being requested.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false, columnDefinition = "VARCHAR(20)")
    private AvailabilityStatus newStatus;

    /**
     * Reason for the change (required, min 20 chars).
     */
    @Column(name = "reason", nullable = false, length = 1000)
    private String reason;

    // ==================== Workflow Status ====================

    /**
     * Current status of the request in the approval workflow.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, columnDefinition = "VARCHAR(20)")
    @Builder.Default
    private RequestStatus status = RequestStatus.PENDING;

    /**
     * The user who reviewed this request (Admin/Coordinator).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_id")
    private User reviewedBy;

    /**
     * When the request was reviewed.
     */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * Notes from the reviewer (reason for approval/rejection).
     */
    @Column(name = "review_notes", length = 1000)
    private String reviewNotes;

    // ==================== Impact Analysis ====================

    /**
     * Number of lessons affected by this change.
     * Calculated when the request is created.
     */
    @Column(name = "affected_lessons_count")
    @Builder.Default
    private Integer affectedLessonsCount = 0;

    /**
     * Comma-separated list of affected lesson IDs.
     */
    @Column(name = "affected_lesson_ids", length = 2000)
    private String affectedLessonIds;

    // ==================== Timestamps ====================

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ==================== Enums ====================

    /**
     * The unavailability status - only UNAVAILABLE is used.
     * Kept as enum for potential future extensibility.
     */
    public enum AvailabilityStatus {
        UNAVAILABLE
    }

    /**
     * The status of the change request.
     */
    public enum RequestStatus {
        PENDING, // Waiting for review
        APPROVED, // Approved, availability updated
        REJECTED, // Rejected, no change made
        RETURNED // Returned for more information
    }
}
