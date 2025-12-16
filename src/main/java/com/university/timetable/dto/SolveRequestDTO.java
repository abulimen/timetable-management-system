package com.university.timetable.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * DTO for solver solve request body.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolveRequestDTO {
    private String mode = "FULL_REPLAN";
}
