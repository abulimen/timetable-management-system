package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImpactPreviewResponseDTO {
    private List<Long> impactedLessonIds = new ArrayList<>();
    private List<Long> lockedLessonIds = new ArrayList<>();
    private ImpactSummaryDTO summary = new ImpactSummaryDTO();
    private List<String> warnings = new ArrayList<>();
    private List<ImpactLessonDTO> impactedLessons = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactSummaryDTO {
        private int totalLessons;
        private int impactedCount;
        private int lockedCount;
        private Map<String, Integer> byDay = new LinkedHashMap<>();
        private Map<String, Integer> byLecturer = new LinkedHashMap<>();
        private Map<String, Integer> byGroup = new LinkedHashMap<>();
        private Map<String, Integer> byRoom = new LinkedHashMap<>();
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImpactLessonDTO {
        private Long lessonId;
        private String courseCode;
        private String courseName;
        private String dayOfWeek;
        private String startTime;
        private String endTime;
        private String lecturerName;
        private String roomName;
        private List<String> groupNames = new ArrayList<>();
    }
}
