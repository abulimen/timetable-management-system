package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Report from pre-solve feasibility check.
 * Contains all detected issues that may prevent a valid solution.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class InfeasibilityReport {
    
    /**
     * True if no CRITICAL or HIGH issues were found.
     */
    private boolean feasible;
    
    /**
     * List of all detected issues grouped by severity.
     */
    private List<InfeasibilityIssue> issues = new ArrayList<>();
    
    /**
     * Detailed formatted analysis text for display.
     */
    private String analysisText;
    
    /**
     * Summary counts by severity.
     */
    private int criticalCount;
    private int highCount;
    private int mediumCount;
    private int lowCount;
    
    /**
     * Problem size info for context.
     */
    private int lessonCount;
    private int timeslotCount;
    private int roomCount;
    private int availableRoomSlots;  // timeslots * rooms
    
    public void addIssue(InfeasibilityIssue issue) {
        issues.add(issue);
        switch (issue.getSeverity()) {
            case "CRITICAL" -> criticalCount++;
            case "HIGH" -> highCount++;
            case "MEDIUM" -> mediumCount++;
            case "LOW" -> lowCount++;
        }
        // Only infeasible if there are CRITICAL or HIGH issues
        if ("CRITICAL".equals(issue.getSeverity()) || "HIGH".equals(issue.getSeverity())) {
            feasible = false;
        }
    }
    
    public static InfeasibilityReport feasible(int lessons, int timeslots, int rooms) {
        InfeasibilityReport report = new InfeasibilityReport();
        report.setFeasible(true);
        report.setLessonCount(lessons);
        report.setTimeslotCount(timeslots);
        report.setRoomCount(rooms);
        report.setAvailableRoomSlots(timeslots * rooms);
        return report;
    }
    
    public static InfeasibilityReport infeasible(int lessons, int timeslots, int rooms, List<InfeasibilityIssue> issues) {
        InfeasibilityReport report = new InfeasibilityReport();
        report.setFeasible(false);
        report.setLessonCount(lessons);
        report.setTimeslotCount(timeslots);
        report.setRoomCount(rooms);
        report.setAvailableRoomSlots(timeslots * rooms);
        report.setIssues(issues);
        for (InfeasibilityIssue issue : issues) {
            switch (issue.getSeverity()) {
                case "CRITICAL" -> report.criticalCount++;
                case "HIGH" -> report.highCount++;
                case "MEDIUM" -> report.mediumCount++;
                case "LOW" -> report.lowCount++;
            }
        }
        return report;
    }
}
