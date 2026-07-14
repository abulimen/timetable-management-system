package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single diagnostics issue with plain English description and recommendation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticsIssueDTO {
    
    /**
     * Machine-readable issue type.
     */
    private String type;
    
    /**
     * Short title for display.
     */
    private String title;
    
    /**
     * Severity: BLOCKING (must fix) or WARNING (should review).
     */
    private String severity;
    
    /**
     * Plain English description of the problem.
     */
    private String description;
    
    /**
     * Plain English recommendation on how to fix it.
     */
    private String recommendation;
    
    /**
     * Number of lessons/instances affected.
     */
    private int affectedCount;
    
    public static DiagnosticsIssueDTO blocking(String type, String title, String description, String recommendation) {
        DiagnosticsIssueDTO issue = new DiagnosticsIssueDTO();
        issue.setType(type);
        issue.setTitle(title);
        issue.setSeverity("BLOCKING");
        issue.setDescription(description);
        issue.setRecommendation(recommendation);
        issue.setAffectedCount(1);
        return issue;
    }
    
    public static DiagnosticsIssueDTO warning(String type, String title, String description, String recommendation) {
        DiagnosticsIssueDTO issue = new DiagnosticsIssueDTO();
        issue.setType(type);
        issue.setTitle(title);
        issue.setSeverity("WARNING");
        issue.setDescription(description);
        issue.setRecommendation(recommendation);
        issue.setAffectedCount(1);
        return issue;
    }
}
