package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Tracks bulk import operations for audit and rollback purposes.
 */
@Entity
@Table(name = "import_history")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "entity_type", nullable = false, length = 50)
    private String entityType;

    @Column(name = "import_mode", nullable = false, length = 20)
    @Builder.Default
    private String importMode = "STRICT";

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "created_count", nullable = false)
    @Builder.Default
    private Integer createdCount = 0;

    @Column(name = "updated_count", nullable = false)
    @Builder.Default
    private Integer updatedCount = 0;

    @Column(name = "skipped_count", nullable = false)
    @Builder.Default
    private Integer skippedCount = 0;

    /**
     * JSON array of created entity IDs for rollback (deletion).
     * Format: [1, 2, 3, ...]
     */
    @Column(name = "created_ids", columnDefinition = "TEXT")
    private String createdIds;

    /**
     * JSON array of updated entities' previous state for rollback.
     * Format: [{"id": 1, "previousState": {...}}, ...]
     */
    @Column(name = "updated_data", columnDefinition = "TEXT")
    private String updatedData;

    @Column(name = "can_rollback")
    @Builder.Default
    private Boolean canRollback = true;

    @Column(name = "rolled_back")
    @Builder.Default
    private Boolean rolledBack = false;

    @Column(name = "rolled_back_at")
    private LocalDateTime rolledBackAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rolled_back_by")
    private User rolledBackBy;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }

    /**
     * Check if this import can still be rolled back.
     * 
     * @param windowHours Number of hours the rollback window is open. -1 for
     *                    unlimited.
     */
    public boolean isRollbackAvailable(int windowHours) {
        if (rolledBack || !canRollback) {
            return false;
        }

        // Unlimited window
        if (windowHours < 0) {
            return true;
        }

        LocalDateTime rollbackDeadline = timestamp.plusHours(windowHours);
        return LocalDateTime.now().isBefore(rollbackDeadline);
    }
}
