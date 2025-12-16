package com.university.timetable.controller;

import com.university.timetable.dto.ImportResultDTO;
import com.university.timetable.service.IngestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * ImportController - handles Excel file uploads for data import.
 * 
 * Based on design.md API Specification:
 * POST /api/v1/import/upload - Upload Excel file for data import
 */
@RestController
@RequestMapping("/api/v1/import")
@RequiredArgsConstructor
@Slf4j
public class ImportController {

    private final IngestionService ingestionService;

    /**
     * POST /api/v1/import/upload
     * Body: MultipartFile (Excel)
     * Action: Parses, validates, persists data, generates lessons.
     */
    @PostMapping("/upload")
    public ResponseEntity<ImportResultDTO> uploadExcel(
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        
        log.info("Received file: {}, size: {} bytes", 
            file.getOriginalFilename(), file.getSize());
        
        try {
            ImportResultDTO result = ingestionService.importExcel(file);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error importing Excel file", e);
            ImportResultDTO errorResult = new ImportResultDTO();
            errorResult.addError(0, "file", "Failed to process file: " + e.getMessage(), "IMPORT_FAILED");
            return ResponseEntity.badRequest().body(errorResult);
        }
    }
}
