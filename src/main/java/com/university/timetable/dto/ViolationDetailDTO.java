package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Details about a single violation instance.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ViolationDetailDTO {
    
    /**
     * The entity involved (e.g., "CS101-Part1").
     */
    private String entity;
    
    /**
     * Human-readable description of what's wrong.
     */
    private String description;
    
    /**
     * Actionable recommendation to fix this violation.
     */
    private String recommendation;
}
