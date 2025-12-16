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
     * True if no BLOCKING issues were found.
     */
    private boolean feasible;
    
    /**
     * List of all detected issues (both BLOCKING and WARNING).
     */
    private List<InfeasibilityIssue> issues = new ArrayList<>();
    
    /**
     * Summary counts.
     */
    private int blockingCount;
    private int warningCount;
    
    /**
     * Problem size info for context.
     */
    private int lessonCount;
    private int timeslotCount;
    private int roomCount;
    private int availableRoomSlots;  // timeslots * rooms
    
    public void addIssue(InfeasibilityIssue issue) {
        issues.add(issue);
        if ("BLOCKING".equals(issue.getSeverity())) {
            blockingCount++;
            feasible = false;
        } else {
            warningCount++;
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
}
