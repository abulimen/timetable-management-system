package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "solver_run_metric")
@Data
@NoArgsConstructor
public class SolverRunMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "run_id", nullable = false, length = 64)
    private String runId;

    @Column(name = "mode", nullable = false, length = 32)
    private String mode;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "best_score", length = 64)
    private String bestScore;

    @Column(name = "best_hard_score")
    private Integer bestHardScore;

    @Column(name = "best_soft_score")
    private Integer bestSoftScore;

    @Column(name = "lessons_count")
    private Integer lessonsCount;

    @Column(name = "timeslots_count")
    private Integer timeslotsCount;

    @Column(name = "rooms_count")
    private Integer roomsCount;

    @Column(name = "improvement_count", nullable = false)
    private Long improvementCount = 0L;

    @Column(name = "persistence_count", nullable = false)
    private Long persistenceCount = 0L;

    @Column(name = "avg_persistence_ms")
    private Long avgPersistenceMs;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "impacted_lessons_count")
    private Integer impactedLessonsCount;

    @Column(name = "locked_lessons_count")
    private Integer lockedLessonsCount;

    @Column(name = "changed_lessons_count")
    private Integer changedLessonsCount;

    @Column(name = "time_to_first_best_ms")
    private Long timeToFirstBestMs;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
