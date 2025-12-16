package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single infeasibility issue detected during pre-solve validation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InfeasibilityIssue {
    
    /**
     * Type of issue: CAPACITY, TIMESLOTS, LECTURER_HOURS, FEATURE_MISMATCH, ZONE_CONFLICT
     */
    private String type;
    
    /**
     * Severity: BLOCKING (solver will definitely fail) or WARNING (may succeed but unlikely)
     */
    private String severity;
    
    /**
     * Human-readable description of the issue.
     */
    private String description;
    
    /**
     * Actionable recommendation to fix the issue.
     */
    private String recommendation;
    
    public static InfeasibilityIssue blocking(String type, String description, String recommendation) {
        return new InfeasibilityIssue(type, "BLOCKING", description, recommendation);
    }
    
    public static InfeasibilityIssue warning(String type, String description, String recommendation) {
        return new InfeasibilityIssue(type, "WARNING", description, recommendation);
    }
}
