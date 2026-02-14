package com.university.timetable.dto;

import lombok.Data;

import java.util.List;

@Data
public class ArchivedSpecialEventDTO {
    private Long id;
    private String name;
    private String description;
    private String dayOfWeek;
    private String startTime;
    private String endTime;
    private Integer durationHours;
    private Long roomId;
    private String roomName;
    private Long lecturerId;
    private String lecturerName;
    private Boolean online;
    private Boolean active;
    private List<Long> studentGroupIds;
    private List<String> studentGroupNames;
}
