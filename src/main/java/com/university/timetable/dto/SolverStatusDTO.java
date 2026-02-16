package com.university.timetable.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

/**
 * DTO for solver status responses.
 * Based on design.md API specification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolverStatusDTO {
    private String jobId;
    private String state;
    private String runOutcome;
    private String score;
    private String profile;
    private Long durationMs;
    private Integer bestHardScore;
    private Integer bestSoftScore;
    private Boolean feasible;
    private Integer impactedLessonsCount;
    private Integer lockedLessonsCount;
    private Integer changedLockedLessonsCount;
    private Boolean pendingChanges;
    private String pendingChangeReason;
    private LocalDateTime pendingChangeSince;
    private LocalDateTime runStartedAt;
    private LocalDateTime lastImprovementAt;
    private Long timeToFirstBestMs;
    private Long timeToFirstFeasibleMs;
    private Long improvementCount;
    private Long persistenceCount;
    private Long avgPersistenceMs;
    private Integer lessonsCount;
    private Integer timeslotsCount;
    private Integer roomsCount;
    private String moveThreadCount;
    private String environmentMode;
    private String parallelSolverCount;
    private Integer availableProcessors;
    
    public SolverStatusDTO(String state, String score) {
        this.state = state;
        this.score = score;
    }

    public SolverStatusDTO(String jobId, String state, String score) {
        this.jobId = jobId;
        this.state = state;
        this.score = score;
    }

    public SolverStatusDTO(String jobId, String state, String score, Long durationMs) {
        this.jobId = jobId;
        this.state = state;
        this.score = score;
        this.durationMs = durationMs;
    }
}
