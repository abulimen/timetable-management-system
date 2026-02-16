package com.university.timetable.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

/**
 * DTO for solver solve request body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolveRequestDTO {
    private String mode = "FULL_REPLAN";
    private String profile = "BALANCED";
    private Boolean skipFeasibility = false;
    private SolveScopeDTO scope;
    private Boolean allowLargeScope = false;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SolveScopeDTO {
        private List<Long> impactedLessonIds;
        private List<Long> excludedLessonIds;
        private String reason;
    }
}
