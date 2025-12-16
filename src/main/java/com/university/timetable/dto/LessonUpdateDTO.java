package com.university.timetable.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for lesson update (PATCH) requests.
 * Based on design.md API specification.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LessonUpdateDTO {
    private Long assignedTimeslotId;
    private Long assignedRoomId;
    private Boolean pinned;
}
