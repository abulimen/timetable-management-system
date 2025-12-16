package com.university.timetable.controller;

import com.university.timetable.service.BulkImportService;
import com.university.timetable.service.DataWipeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * Controller for bulk data operations.
 * Handles system-wide data wipe, bulk entity deletion, and CSV import.
 */
@RestController
@RequestMapping("/api/v1/bulk")
@RequiredArgsConstructor
@Slf4j
public class BulkOperationsController {

    private final DataWipeService dataWipeService;
    private final BulkImportService bulkImportService;

    /**
     * DELETE /api/v1/bulk/system-wipe
     * Wipe ALL data from the system (requires confirmation token).
     */
    @DeleteMapping("/system-wipe")
    public ResponseEntity<?> systemWipe(@RequestBody Map<String, String> body) {
        String token = body.get("confirmationToken");
        
        if (!"DELETE".equals(token)) {
            log.warn("System wipe rejected - invalid confirmation token");
            return ResponseEntity.badRequest().body(Map.of(
                "error", "CONFIRMATION_REQUIRED",
                "message", "You must provide confirmationToken: 'DELETE' to proceed"
            ));
        }
        
        log.warn("SYSTEM WIPE AUTHORIZED - Proceeding with data deletion");
        Map<String, Long> deletedCounts = dataWipeService.wipeAllData();
        
        long totalDeleted = deletedCounts.values().stream().mapToLong(Long::longValue).sum();
        
        return ResponseEntity.ok(Map.of(
            "status", "WIPED",
            "message", "All data has been deleted",
            "totalDeleted", totalDeleted,
            "breakdown", deletedCounts
        ));
    }

    /**
     * DELETE /api/v1/bulk/{entity}/all
     * Delete all records of a specific entity type.
     */
    @DeleteMapping("/{entity}/all")
    public ResponseEntity<?> deleteAllOfEntity(
            @PathVariable String entity,
            @RequestBody Map<String, Object> body) {
        
        Boolean confirm = (Boolean) body.get("confirm");
        if (!Boolean.TRUE.equals(confirm)) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "CONFIRMATION_REQUIRED",
                "message", "You must provide confirm: true to proceed"
            ));
        }
        
        try {
            long deleted = dataWipeService.deleteAllOfType(entity);
            return ResponseEntity.ok(Map.of(
                "status", "DELETED",
                "entity", entity,
                "deleted", deleted,
                "message", String.format("Deleted %d %s records", deleted, entity)
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_ENTITY",
                "message", e.getMessage()
            ));
        }
    }

    /**
     * POST /api/v1/bulk/{entity}/import
     * Import records from CSV file.
     * 
     * Formats:
     * - lecturers: name,email
     * - rooms: name,capacity,zoneName
     * - student-groups: name,size,parentGroupName
     * - zones: name
     * - courses: code,name,weeklyHours,lecturerEmail,studentGroupName
     */
    @PostMapping("/{entity}/import")
    public ResponseEntity<?> importFromCsv(
            @PathVariable String entity,
            @RequestParam("file") MultipartFile file) {
        
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "EMPTY_FILE",
                "message", "Please upload a CSV file"
            ));
        }
        
        log.info("Importing {} from CSV file: {}", entity, file.getOriginalFilename());
        
        try {
            Map<String, Object> result = switch (entity.toLowerCase()) {
                case "lecturers" -> bulkImportService.importLecturers(file);
                case "rooms" -> bulkImportService.importRooms(file);
                case "student-groups", "studentgroups" -> bulkImportService.importStudentGroups(file);
                case "zones" -> bulkImportService.importZones(file);
                case "features" -> bulkImportService.importFeatures(file);
                case "courses" -> bulkImportService.importCourses(file);
                default -> throw new IllegalArgumentException("Unknown entity type: " + entity);
            };
            
            return ResponseEntity.ok(Map.of(
                "status", "IMPORTED",
                "entity", entity,
                "created", result.get("created"),
                "skipped", result.get("skipped"),
                "errors", result.get("errors")
            ));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "INVALID_ENTITY",
                "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Import failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                "error", "IMPORT_FAILED",
                "message", "Import failed: " + e.getMessage()
            ));
        }
    }

    /**
     * GET /api/v1/bulk/{entity}/template
     * Download CSV template for the specified entity type.
     */
    @GetMapping(value = "/{entity}/template", produces = "text/csv")
    public ResponseEntity<String> downloadTemplate(@PathVariable String entity) {
        try {
            String template = bulkImportService.getTemplate(entity);
            String filename = entity.toLowerCase() + "_template.csv";
            return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .header("Content-Type", "text/csv; charset=UTF-8")
                .body(template);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body("Unknown entity type: " + entity);
        }
    }

    /**
     * GET /api/v1/bulk/import-formats
     * Get expected CSV formats for each entity type.
     */
    @GetMapping("/import-formats")
    public ResponseEntity<Map<String, Object>> getImportFormats() {
        return ResponseEntity.ok(Map.of(
            "lecturers", Map.of("format", "name,email", "example", "John Smith,john@uni.edu"),
            "rooms", Map.of("format", "name,capacity,zone_name", "example", "Room A101,50,Building A"),
            "student-groups", Map.of("format", "name,size,parent_group_name", "example", "COSC_1A,40,COSC_Year1"),
            "zones", Map.of("format", "name", "example", "Building A"),
            "courses", Map.of("format", "code,name,weekly_hours,lecturer_email,student_group_name", "example", "COSC101,Intro to Programming,3,john@uni.edu,COSC_1A"),
            "importOrder", "1. Zones → 2. Lecturers → 3. Student Groups → 4. Rooms → 5. Courses"
        ));
    }
}
