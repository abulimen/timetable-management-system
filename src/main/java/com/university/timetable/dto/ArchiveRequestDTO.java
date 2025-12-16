package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for archiving current semester.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArchiveRequestDTO {
    
    /**
     * Unique code for the archive (e.g., "2024_2025_S1").
     * This becomes the table prefix.
     */
    private String code;
    
    /**
     * Human-readable name (e.g., "2024/2025 1st Semester").
     */
    private String name;
    
    /**
     * Academic year (e.g., "2024/2025").
     */
    private String academicYear;
    
    /**
     * Semester number (1 or 2).
     */
    private Integer semesterNumber;
}
