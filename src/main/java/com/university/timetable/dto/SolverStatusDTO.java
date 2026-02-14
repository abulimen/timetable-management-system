package com.university.timetable.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

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
    private String score;
    private Long durationMs;
    
    public SolverStatusDTO(String state, String score) {
        this.state = state;
        this.score = score;
    }

    public SolverStatusDTO(String jobId, String state, String score) {
        this.jobId = jobId;
        this.state = state;
        this.score = score;
    }
}
