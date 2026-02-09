package com.university.timetable.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkImportResult {
    @Builder.Default
    private int createdCount = 0;
    @Builder.Default
    private int updatedCount = 0;
    @Builder.Default
    private int skippedCount = 0;
    @Builder.Default
    private int errorCount = 0;

    @Builder.Default
    private List<ImportRowDetail> validRows = new ArrayList<>();
    @Builder.Default
    private List<ImportRowDetail> warningRows = new ArrayList<>();
    @Builder.Default
    private List<String> globalErrors = new ArrayList<>();
    @Builder.Default
    private List<ImportRowError> rowErrors = new ArrayList<>();

    @Builder.Default
    private List<ImportConflictDTO> conflicts = new ArrayList<>();

    private Long importHistoryId;

    /**
     * The CSV content generated from resolved data, ready for staging.
     */
    private String generatedCsv;
}
