package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight DTO for user identification data.
 * Used for client-side validation of duplicates.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserIdentifierDTO {
    private String email;
    private String phone;
}
