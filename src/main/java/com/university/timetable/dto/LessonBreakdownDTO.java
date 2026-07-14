package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonBreakdownDTO {
    private String zoneName;
    private String featureName;
    private double totalLessons;
    private double totalHours;
    private int roomsAvailable;
    private double utilization;
    private List<LessonDetail> lessons;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LessonDetail {
        private String courseCode;
        private String courseName;
        private int weeklyHours;
        private List<String> studentGroups;
        private String lecturerName;
        private int studentCount;
    }
}
