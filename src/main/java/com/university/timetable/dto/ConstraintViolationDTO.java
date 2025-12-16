package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Details about violations of a specific constraint.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConstraintViolationDTO {
    
    /**
     * Name of the constraint (e.g., "Room capacity overflow").
     */
    private String constraintName;
    
    /**
     * Number of times this constraint was violated.
     */
    private int matchCount;
    
    /**
     * Score impact (e.g., "-3hard" or "-15soft").
     */
    private String scoreImpact;
    
    /**
     * Weight of this constraint (for soft constraints).
     */
    private int weight;
    
    /**
     * Individual violation details (limited to avoid huge responses).
     */
    private List<ViolationDetailDTO> details = new ArrayList<>();
    
    public void addDetail(ViolationDetailDTO detail) {
        // Limit to 10 details per constraint
        if (details.size() < 10) {
            details.add(detail);
        }
    }
}
