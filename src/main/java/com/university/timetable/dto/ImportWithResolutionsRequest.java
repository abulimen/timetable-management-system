package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Request DTO for importing data with user-provided conflict resolutions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportWithResolutionsRequest {

    /**
     * The raw CSV data rows (excluding header).
     * Each entry is a map of column name to value.
     */
    private List<Map<String, String>> rows;

    /**
     * Map of row numbers to their resolution choice.
     * Only rows with conflicts need resolutions.
     */
    private Map<Integer, ImportConflictDTO.ConflictResolution> resolutions;
}
