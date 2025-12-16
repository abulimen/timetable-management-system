package com.university.timetable.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO for import validation results.
 * 
 * Based on specs.md Reporting requirements:
 * - Total Rows processed
 * - Successful imports
 * - List of Errors (Row Number, Column, Error Message)
 * - List of Warnings (Auto-corrections)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDTO {
    
    private int totalRows;
    private int successfulImports;
    private List<ValidationErrorDTO> errors = new ArrayList<>();
    private List<ValidationWarningDTO> warnings = new ArrayList<>();
    
    public void incrementSuccess() {
        successfulImports++;
    }
    
    public void incrementTotal() {
        totalRows++;
    }
    
    public void addError(int rowNumber, String column, String message, String errorCode) {
        errors.add(new ValidationErrorDTO(rowNumber, column, message, errorCode));
    }
    
    public void addWarning(int rowNumber, String column, String originalValue, 
                          String correctedValue, String reason) {
        warnings.add(new ValidationWarningDTO(rowNumber, column, originalValue, correctedValue, reason));
    }
    
    public boolean hasErrors() {
        return !errors.isEmpty();
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationErrorDTO {
        private int rowNumber;
        private String column;
        private String message;
        private String errorCode;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationWarningDTO {
        private int rowNumber;
        private String column;
        private String originalValue;
        private String correctedValue;
        private String reason;
    }
}
