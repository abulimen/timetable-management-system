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
     * Type of issue: CAPACITY, TIMESLOTS, LECTURER_HOURS, FEATURE_MISMATCH, ZONE_CONFLICT, SAME_COURSE_SAME_DAY, ROOM_CONSTRAINT
     */
    private String type;
    
    /**
     * Severity levels:
     * - CRITICAL: Definitely blocks solving (no valid rooms, no valid timeslots)
     * - HIGH: Very likely to cause infeasibility (1-2 valid rooms for many lessons)
     * - MEDIUM: May cause issues (tight constraints, limited alternatives)
     * - LOW: Warning only (utilization high but manageable)
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
    
    public static InfeasibilityIssue critical(String type, String description, String recommendation) {
        return new InfeasibilityIssue(type, "CRITICAL", description, recommendation);
    }
    
    public static InfeasibilityIssue high(String type, String description, String recommendation) {
        return new InfeasibilityIssue(type, "HIGH", description, recommendation);
    }
    
    public static InfeasibilityIssue medium(String type, String description, String recommendation) {
        return new InfeasibilityIssue(type, "MEDIUM", description, recommendation);
    }
    
    public static InfeasibilityIssue low(String type, String description, String recommendation) {
        return new InfeasibilityIssue(type, "LOW", description, recommendation);
    }
    
    // Legacy methods for backward compatibility
    public static InfeasibilityIssue blocking(String type, String description, String recommendation) {
        return critical(type, description, recommendation);
    }
    
    public static InfeasibilityIssue warning(String type, String description, String recommendation) {
        return medium(type, description, recommendation);
    }
}
