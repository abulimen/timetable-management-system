package com.university.timetable.controller;

import com.university.timetable.domain.ImportHistory;
import com.university.timetable.domain.ImportBatch;
import com.university.timetable.domain.User;
import com.university.timetable.service.BulkImportService;
import com.university.timetable.service.ConstraintSettingsService;
import com.university.timetable.service.DataWipeService;
import com.university.timetable.service.ImportHistoryService;
import com.university.timetable.service.StagingService;
import com.university.timetable.repository.UserRepository;
import com.university.timetable.dto.BulkImportResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller for bulk data operations.
 * Handles system-wide data wipe, bulk entity deletion, CSV import, and import
 * history.
 */
@RestController
@RequestMapping("/api/v1/bulk")
@RequiredArgsConstructor
@Slf4j
public class BulkOperationsController {

    private final DataWipeService dataWipeService;
    private final BulkImportService bulkImportService;
    private final ImportHistoryService importHistoryService;
    private final ConstraintSettingsService constraintSettingsService;
    private final StagingService stagingService;
    private final UserRepository userRepository;

    /**
     * DELETE /api/v1/bulk/system-wipe
     * Wipe ALL data from the system (requires confirmation token).
     * CRITICAL: Only SUPER_ADMIN can perform this operation.
     */
    @DeleteMapping("/system-wipe")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<?> systemWipe(@RequestBody Map<String, String> body) {
        String token = body.get("confirmationToken");

        if (!"DELETE".equals(token)) {
            log.warn("System wipe rejected - invalid confirmation token");
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "CONFIRMATION_REQUIRED",
                    "message", "You must provide confirmationToken: 'DELETE' to proceed"));
        }

        log.warn("SYSTEM WIPE AUTHORIZED - Proceeding with data deletion");
        Map<String, Long> deletedCounts = dataWipeService.wipeAllData();

        long totalDeleted = deletedCounts.values().stream().mapToLong(Long::longValue).sum();

        return ResponseEntity.ok(Map.of(
                "status", "WIPED",
                "message", "All data has been deleted",
                "totalDeleted", totalDeleted,
                "breakdown", deletedCounts));
    }

    /**
     * DELETE /api/v1/bulk/{entity}/all
     * Delete all records of a specific entity type.
     */
    @DeleteMapping("/{entity}/all")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> deleteAllOfEntity(
            @PathVariable String entity,
            @RequestBody Map<String, Object> body) {

        Boolean confirm = (Boolean) body.get("confirm");
        if (!Boolean.TRUE.equals(confirm)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "CONFIRMATION_REQUIRED",
                    "message", "You must provide confirm: true to proceed"));
        }

        try {
            long deleted = dataWipeService.deleteAllOfType(entity);
            return ResponseEntity.ok(Map.of(
                    "status", "DELETED",
                    "entity", entity,
                    "deleted", deleted,
                    "message", String.format("Deleted %d %s records", deleted, entity)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_ENTITY",
                    "message", e.getMessage()));
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> importFromCsv(
            @PathVariable String entity,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "EMPTY_FILE",
                    "message", "Please upload a CSV file"));
        }

        log.info("Importing {} from CSV file: {} (dryRun={})", entity, file.getOriginalFilename(), dryRun);

        try {
            BulkImportResult result = switch (entity.toLowerCase()) {
                case "lecturers" -> bulkImportService.importLecturers(file, dryRun);
                case "rooms" -> bulkImportService.importRooms(file, dryRun);
                case "student-groups", "studentgroups" -> bulkImportService.importStudentGroups(file, dryRun);
                case "zones" -> bulkImportService.importZones(file, dryRun);
                case "features" -> bulkImportService.importFeatures(file, dryRun);
                case "courses" -> bulkImportService.importCourses(file, dryRun);
                case "users" -> bulkImportService.importUsers(file, dryRun);
                default -> throw new IllegalArgumentException("Unknown entity type: " + entity);
            };

            return ResponseEntity.ok(result);

        } catch (BulkImportService.BulkImportException e) {
            // Atomic import validation failed - return detailed result
            log.warn("Bulk import validation failed for {} with {} errors", entity, e.getResult().getErrorCount());
            return ResponseEntity.unprocessableEntity().body(e.getResult());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "INVALID_ENTITY",
                    "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Import failed", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "IMPORT_FAILED",
                    "message", "Import failed: " + e.getMessage()));
        }
    }

    /**
     * GET /api/v1/bulk/{entity}/template
     * Download CSV template for the specified entity type.
     */
    @GetMapping(value = "/{entity}/template", produces = "text/csv")
    @PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<Map<String, Object>> getImportFormats() {
        return ResponseEntity.ok(Map.of(
                "lecturers", Map.of("format", "name,email", "example", "John Smith,john@uni.edu"),
                "rooms", Map.of("format", "name,capacity,zone_name", "example", "Room A101,50,Building A"),
                "student-groups", Map.of("format", "name,size,parent_group_name", "example", "COSC_1A,40,COSC_Year1"),
                "zones", Map.of("format", "name", "example", "Building A"),
                "courses",
                Map.of("format", "code,name,weekly_hours,lecturer_email,student_group_name", "example",
                        "COSC101,Intro to Programming,3,john@uni.edu,COSC_1A"),
                "importOrder", "1. Zones → 2. Lecturers → 3. Student Groups → 4. Rooms → 5. Courses"));
    }

    // ==================== IMPORT HISTORY ENDPOINTS ====================

    /**
     * GET /api/v1/bulk/history
     * Get list of all import operations with rollback status.
     */
    @GetMapping("/history")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<List<Map<String, Object>>> getImportHistory() {
        List<ImportHistory> history = importHistoryService.getHistory();
        int rollbackWindowHours = constraintSettingsService.getRollbackWindowHours();

        List<Map<String, Object>> response = history.stream().map(h -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", h.getId());
            map.put("timestamp", h.getTimestamp().toString());
            map.put("entityType", h.getEntityType());
            map.put("importMode", h.getImportMode());
            map.put("fileName", h.getFileName() != null ? h.getFileName() : "");
            map.put("createdCount", h.getCreatedCount());
            map.put("updatedCount", h.getUpdatedCount());
            map.put("skippedCount", h.getSkippedCount());
            map.put("canRollback", h.isRollbackAvailable(rollbackWindowHours));
            map.put("rolledBack", h.getRolledBack());
            map.put("userName",
                    h.getUser() != null ? h.getUser().getFirstName() + " " + h.getUser().getLastName() : "System");
            return map;
        }).toList();

        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/bulk/history/{id}/rollback
     * Rollback a specific import operation (within 24 hours).
     */
    @PostMapping("/history/{id}/rollback")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> rollbackImport(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {

        // Check if rollback is possible
        if (!importHistoryService.canRollback(id)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "ROLLBACK_NOT_AVAILABLE",
                    "message",
                    "This import cannot be rolled back. It may be older than 24 hours or already rolled back."));
        }

        try {
            User currentUser = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            boolean success = importHistoryService.rollback(id, currentUser);

            if (success) {
                // Try to restore staging batch if it exists
                stagingService.restoreBatchFromHistory(id);

                log.info("Import {} successfully rolled back by {}", id, userDetails.getUsername());
                return ResponseEntity.ok(Map.of(
                        "status", "ROLLED_BACK",
                        "message", "Import has been successfully rolled back",
                        "importId", id));
            } else {
                return ResponseEntity.badRequest().body(Map.of(
                        "error", "ROLLBACK_FAILED",
                        "message", "Rollback operation failed"));
            }
        } catch (Exception e) {
            log.error("Rollback failed for import {}", id, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "ROLLBACK_ERROR",
                    "message", "An error occurred during rollback: " + e.getMessage()));
        }
    }

    // ==================== STAGING AREA ENDPOINTS ====================

    /**
     * POST /api/v1/bulk/staging/{entityType}
     * Submit a file for approval (Staging).
     */
    @PostMapping("/staging/{entityType}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> submitForApproval(
            @PathVariable String entityType,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "note", required = false) String note,
            @AuthenticationPrincipal UserDetails userDetails) {

        try {
            User uploader = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            var batch = stagingService.createBatch(file, entityType, uploader, note);

            return ResponseEntity.ok(Map.of(
                    "status", "SUBMITTED",
                    "message", "File submitted for approval",
                    "batchId", batch.getId()));
        } catch (com.university.timetable.service.BulkImportService.BulkImportException e) {
            return ResponseEntity.unprocessableEntity().body(e.getResult());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/bulk/staging/pending
     * List all pending batches.
     */
    @GetMapping("/staging/pending")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getPendingBatches() {
        return ResponseEntity.ok(stagingService.getPendingBatches());
    }

    /**
     * GET /api/v1/bulk/staging/{batchId}/preview
     * Preview a batch details (parsed content check).
     */
    @GetMapping("/staging/{batchId}/preview")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> previewBatch(@PathVariable Long batchId) {
        try {
            BulkImportResult result = stagingService.previewBatch(batchId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/bulk/staging/{batchId}/approve
     * Approve and execute a batch.
     */
    @PostMapping("/staging/{batchId}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> approveBatch(
            @PathVariable Long batchId,
            @RequestBody(required = false) Map<String, Object> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User approver = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Map<Integer, String> resolutions = null;
            if (body != null && body.containsKey("resolutions")) {
                resolutions = new HashMap<>();
                Map<?, ?> rawRes = (Map<?, ?>) body.get("resolutions");
                for (Map.Entry<?, ?> entry : rawRes.entrySet()) {
                    Integer rowNum = Integer.valueOf(entry.getKey().toString());
                    String resolution = entry.getValue().toString();
                    resolutions.put(rowNum, resolution);
                }
            }

            BulkImportResult result = stagingService.approveBatch(batchId, approver, resolutions);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/bulk/staging/{batchId}/reject
     * Reject a batch.
     */
    @PostMapping("/staging/{batchId}/reject")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> rejectBatch(
            @PathVariable Long batchId,
            @RequestBody(required = false) Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User rejector = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String reason = body != null ? body.get("reason") : null;
            stagingService.rejectBatch(batchId, rejector, reason);
            return ResponseEntity.ok(Map.of("status", "REJECTED"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/bulk/staging/{batchId}/revert-to-draft
     * Revert a rejected batch back to draft for re-editing.
     */
    @PostMapping("/staging/{batchId}/revert-to-draft")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> revertToDraft(
            @PathVariable Long batchId,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            ImportBatch newDraft = stagingService.revertToDraft(batchId, user);
            return ResponseEntity.ok(Map.of("status", "DRAFT", "id", newDraft.getId()));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== DRAFT ENDPOINTS ====================

    /**
     * POST /api/v1/bulk/staging/draft/{entityType}
     * Create a new draft import.
     */
    @PostMapping("/staging/draft/{entityType}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> createDraft(
            @PathVariable String entityType,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User uploader = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            var batch = stagingService.createDraft(file, entityType, uploader);
            return ResponseEntity.ok(Map.of(
                    "status", "DRAFT_CREATED",
                    "draftId", batch.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/v1/bulk/staging/drafts
     * List my drafts.
     */
    @GetMapping("/staging/drafts")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> getMyDrafts(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(stagingService.getDrafts(user));
    }

    /**
     * GET /api/v1/bulk/staging/my-submissions
     * List user's submitted batches (PENDING, APPROVED, REJECTED).
     */
    @GetMapping("/staging/my-submissions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> getMySubmissions(@AuthenticationPrincipal UserDetails userDetails) {
        User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
                .orElseThrow(() -> new RuntimeException("User not found"));
        return ResponseEntity.ok(stagingService.getMySubmissions(user));
    }

    /**
     * GET /api/v1/bulk/staging/draft/{id}
     * Get draft details (including content).
     */
    @GetMapping("/staging/draft/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> getDraft(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            var draft = stagingService.getDraft(id, user); // checks ownership

            // Return content as string along with metadata
            String content = new String(draft.getFileData(), java.nio.charset.StandardCharsets.UTF_8);

            return ResponseEntity.ok(Map.of(
                    "id", draft.getId(),
                    "entityType", draft.getEntityType(),
                    "originalFilename", draft.getOriginalFilename(),
                    "content", content,
                    "createdAt", draft.getCreatedAt().toString()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * PUT /api/v1/bulk/staging/draft/{id}
     * Update draft content.
     */
    @PutMapping("/staging/draft/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> updateDraft(
            @PathVariable Long id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String content = body.get("content");
            if (content == null)
                throw new IllegalArgumentException("Content required");

            stagingService.updateDraft(id, user, content);
            return ResponseEntity.ok(Map.of("status", "UPDATED"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * POST /api/v1/bulk/staging/draft/{id}/submit
     * Submit draft for approval.
     */
    @PostMapping("/staging/draft/{id}/submit")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> submitDraft(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            stagingService.submitDraft(id, user);
            return ResponseEntity.ok(Map.of("status", "SUBMITTED"));
        } catch (IllegalArgumentException e) {
            // Validation error often comes as IllegalArgumentException from
            // BulkImportService
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (com.university.timetable.service.BulkImportService.BulkImportException e) {
            return ResponseEntity.unprocessableEntity().body(e.getResult());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /api/v1/bulk/staging/draft/{id}
     * Delete draft.
     */
    @DeleteMapping("/staging/draft/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> deleteDraft(
            @PathVariable Long id,
            @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userRepository.findByEmailIgnoreCase(userDetails.getUsername())
                    .orElseThrow(() -> new RuntimeException("User not found"));

            stagingService.deleteDraft(id, user);
            return ResponseEntity.ok(Map.of("status", "DELETED"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
