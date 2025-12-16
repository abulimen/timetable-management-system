package com.university.timetable.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * DTO for timetable view responses.
 * Based on design.md TimetableViewDTO specification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimetableViewDTO {
    
    private Long lessonId;
    private String courseCode;
    private String courseName;
    private int partNumber;
    private int durationHours;
    
    // Timeslot
    private DayOfWeek dayOfWeek;
    private LocalTime startTime;
    private LocalTime endTime;
    
    // Room
    private Long roomId;
    private String roomName;
    private int roomCapacity;
    
    // Lecturer
    private Long lecturerId;
    private String lecturerName;
    
    // Student Group
    private Long studentGroupId;
    private String studentGroupName;
    private int studentGroupSize;
    
    // Status
    private boolean pinned;
    private boolean scheduled;
    private boolean online;
}
