package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostics report with plain English descriptions of timetable problems.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosticsReportDTO {
    
    private int totalLessons;
    private int scheduledLessons;
    private int unscheduledLessons;
    private int totalIssues;
    private int blockingIssues;
    private int warningIssues;
    private List<DiagnosticsIssueDTO> issues = new ArrayList<>();
    
    /**
     * Returns a plain English summary of the report.
     */
    public String getSummary() {
        if (totalIssues == 0) {
            return String.format("Your timetable looks good! All %d lessons are scheduled with no problems found.", totalLessons);
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("We found %d problem(s) in your timetable. ", totalIssues));
        
        if (blockingIssues > 0) {
            sb.append(String.format("%d of these are serious issues that need to be fixed. ", blockingIssues));
        }
        if (warningIssues > 0) {
            sb.append(String.format("%d are warnings that you may want to review. ", warningIssues));
        }
        
        return sb.toString();
    }
}
