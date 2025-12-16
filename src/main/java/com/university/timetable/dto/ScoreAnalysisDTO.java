package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Complete score analysis with breakdown of all constraint violations.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreAnalysisDTO {
    
    /**
     * The overall score (e.g., "-3hard/50soft").
     */
    private String score;
    
    /**
     * True if hardScore == 0 (valid solution).
     */
    private boolean feasible;
    
    /**
     * Total hard constraint violations.
     */
    private int hardViolationCount;
    
    /**
     * Total soft constraint penalty.
     */
    private int softPenalty;
    
    /**
     * Breakdown of hard constraint violations by constraint name.
     */
    private List<ConstraintViolationDTO> hardViolations = new ArrayList<>();
    
    /**
     * Breakdown of soft constraint penalties by constraint name.
     */
    private List<ConstraintViolationDTO> softViolations = new ArrayList<>();
}
