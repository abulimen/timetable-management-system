package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImpactPreviewRequestDTO {
    private String changeType;
    private Long entityId;
    private ImpactOptionsDTO options;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactOptionsDTO {
        private List<Long> impactedLessonIds;
        private Long lecturerId;
        private Long roomId;
        private String dayOfWeek;
        private String startTime;
        private Integer durationHours;
        private List<Long> studentGroupIds;
    }
}
