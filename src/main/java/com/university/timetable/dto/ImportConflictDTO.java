package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO representing a conflict between existing and imported data during bulk
 * import.
 * Used to allow users to decide how to resolve each conflict before finalizing
 * import.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportConflictDTO {

    /**
     * Row number in the CSV (1-indexed, excluding header)
     */
    private int rowNumber;

    /**
     * The key that identifies the conflict (e.g., course code, email)
     */
    private String key;

    /**
     * Type of key (e.g., "code", "email")
     */
    private String keyType;

    /**
     * Data from the existing record in the database
     */
    private Map<String, Object> existingData;

    /**
     * Data from the new import row
     */
    private Map<String, Object> newData;

    /**
     * List of field names that have different values between existing and new
     */
    private List<String> conflictingFields;

    /**
     * ID of the existing entity (for resolution)
     */
    private Long existingId;

    /**
     * Resolution type selected by user (null until resolved)
     */
    private ConflictResolution resolution;

    /**
     * Enum for conflict resolution options
     */
    public enum ConflictResolution {
        KEEP_EXISTING, // Skip the new row, keep existing data
        UPDATE, // Update existing record with new data
        SKIP, // Skip this row entirely (don't import)
        CREATE_NEW // Create as a new record with modified key (e.g., code_2)
    }
}
