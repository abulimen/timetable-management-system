package com.university.timetable.service;

import com.university.timetable.domain.ImportBatch;
import com.university.timetable.domain.User;
import com.university.timetable.dto.BulkImportResult;
import com.university.timetable.repository.ImportBatchRepository;
import com.university.timetable.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import com.university.timetable.dto.ImportConflictDTO;
import com.university.timetable.dto.ImportWithResolutionsRequest;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// ... inside class ...

/**
 * Service for handling the staging and approval workflow of bulk imports.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StagingService {

    private final ImportBatchRepository importBatchRepository;
    private final BulkImportService bulkImportService;
    private final UserRepository userRepository;
    private static final int PREVIEW_MAX_VALID_ROWS = 500;

    // Header mappings for conflict resolution
    private static final Map<String, String[]> HEADER_MAPPINGS = Map.of(
            "COURSES",
            new String[] { "code", "name", "weeklyHours", "lecturerEmail", "studentGroupNames", "isOnline",
                    "requiredFeatures", "allowedZones" },
            "LECTURERS", new String[] { "name", "email" },
            "ROOMS", new String[] { "name", "capacity", "zoneName", "features" },
            "ZONES", new String[] { "name" },
            "STUDENT-GROUPS", new String[] { "name", "size", "parentGroupName" },
            "FEATURES", new String[] { "name", "description" },
            "USERS", new String[] { "firstName", "lastName", "email", "role" });

    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult approveBatch(Long batchId, User approver, Map<Integer, String> resolutionsMap)
            throws Exception {
        long startMs = System.currentTimeMillis();
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));

        if (batch.getStatus() != ImportBatch.ImportStatus.PENDING) {
            throw new IllegalStateException("Batch is not in PENDING state");
        }

        BulkImportResult result;
        List<String[]> csvRows = parseBytes(batch.getFileData());

        if (resolutionsMap != null && !resolutionsMap.isEmpty()) {
            // Apply resolutions
            Map<Integer, ImportConflictDTO.ConflictResolution> resolutions = new HashMap<>();
            resolutionsMap.forEach((k, v) -> resolutions.put(k, ImportConflictDTO.ConflictResolution.valueOf(v)));

            List<Map<String, String>> mappedRows = mapRowsToHeaders(csvRows, batch.getEntityType());
            ImportWithResolutionsRequest request = new ImportWithResolutionsRequest(mappedRows, resolutions);

            result = executeImportWithResolutions(request, batch.getEntityType());
        } else {
            // Standard import
            result = executeImport(csvRows, batch.getEntityType(), false, batch.getOriginalFilename());
        }

        // Update batch status
        if (result.getImportHistoryId() != null) {
            batch.setImportHistoryId(result.getImportHistoryId());
        }
        batch.setStatus(ImportBatch.ImportStatus.APPROVED);
        batch.setApprovedBy(approver);
        batch.setApprovalDate(LocalDateTime.now());
        importBatchRepository.save(batch);
        log.info("approveBatch(batchId={}, entityType={}) completed in {} ms", batchId, batch.getEntityType(),
                System.currentTimeMillis() - startMs);

        return compactResultForApproval(result);
    }

    private List<Map<String, String>> mapRowsToHeaders(List<String[]> csvRows, String entityType) {
        String type = entityType.toUpperCase();

        // Special handling for STUDENT-GROUPS to auto-detect format
        if (type.equals("STUDENTGROUPS") || type.equals("STUDENT-GROUPS")) {
            return mapStudentGroupRowsToHeaders(csvRows);
        }

        String[] headers = HEADER_MAPPINGS.get(type);
        if (headers == null) {
            throw new IllegalArgumentException("Conflict resolution not supported for entity type: " + entityType);
        }

        List<Map<String, String>> result = new ArrayList<>();
        for (String[] row : csvRows) {
            Map<String, String> map = new HashMap<>();
            for (int i = 0; i < headers.length && i < row.length; i++) {
                map.put(headers[i], row[i] != null ? row[i] : "");
            }
            result.add(map);
        }
        return result;
    }

    /**
     * Maps STUDENT-GROUPS CSV rows to headers, auto-detecting legacy vs new format.
     * Same heuristic as BulkImportService.importStudentGroups.
     */
    private List<Map<String, String>> mapStudentGroupRowsToHeaders(List<String[]> csvRows) {
        List<Map<String, String>> result = new ArrayList<>();

        for (String[] row : csvRows) {
            Map<String, String> map = new HashMap<>();

            // Detect format using same heuristic as BulkImportService
            String rawCol1 = row.length > 1 ? row[1].trim().toUpperCase() : "";
            String rawCol2 = row.length > 2 ? row[2].trim() : "";

            boolean isLevelNumber = rawCol2.matches("^(100|200|300|400|500|600)$");
            boolean isSizeNumber = rawCol1.matches("^\\d+$") && !rawCol1.equals("0") && !rawCol1.equals("1");

            if (row.length <= 3 || (!rawCol2.isEmpty() && !isLevelNumber) || isSizeNumber) {
                // LEGACY FORMAT: name, size, parentGroupName
                map.put("name", row.length > 0 ? row[0].trim() : "");
                map.put("size", row.length > 1 ? row[1].trim() : "0");
                map.put("parentGroupName", row.length > 2 ? row[2].trim() : "");
            } else {
                // NEW FORMAT: base_name, is_parent, level, group, size, parent_group_name
                // Convert to "name" field using computeName for compatibility
                String baseName = row.length > 0 ? row[0].trim() : "";
                String levelStr = row.length > 2 ? row[2].trim() : "";
                String groupNotation = row.length > 3 ? row[3].trim() : "";

                int level = 100;
                try {
                    level = Integer.parseInt(levelStr);
                } catch (Exception e) {
                }

                String computedName = com.university.timetable.domain.StudentGroup.computeName(baseName, level,
                        groupNotation);

                map.put("name", computedName);
                map.put("size", row.length > 4 ? row[4].trim() : "0");
                map.put("parentGroupName", row.length > 5 ? row[5].trim() : "");
            }

            result.add(map);
        }
        return result;
    }

    private BulkImportResult executeImportWithResolutions(ImportWithResolutionsRequest request, String entityType) {
        return switch (entityType.toUpperCase()) {
            case "COURSES" -> bulkImportService.importCoursesWithResolutions(request);
            case "ZONES" -> bulkImportService.importZonesWithResolutions(request);
            case "FEATURES" -> bulkImportService.importFeaturesWithResolutions(request);
            case "LECTURERS" -> throw new IllegalStateException(
                    "Lecturers CSV import has been retired. Use users import with LECTURER role.");
            case "ROOMS" -> bulkImportService.importRoomsWithResolutions(request);
            case "STUDENT-GROUPS", "STUDENTGROUPS" -> bulkImportService.importStudentGroupsWithResolutions(request);
            default -> throw new IllegalArgumentException("Resolutions not supported for: " + entityType);
        };
    }

    /**
     * Create a pending import batch from raw content (e.g. internally generated).
     * 
     * @param skipValidation if true, skip dry-run validation (use for pre-validated
     *                       resolved data)
     */
    @Transactional
    public ImportBatch createBatchFromContent(String originalFilename, byte[] content, String entityType, User uploader,
            String submissionNote, boolean skipValidation)
            throws Exception {

        if (!skipValidation) {
            // Validate content via dry run
            List<String[]> rows = parseBytes(content);
            executeImport(rows, entityType, true, originalFilename);
        }

        ImportBatch batch = ImportBatch.builder()
                .entityType(entityType.toUpperCase())
                .originalFilename(originalFilename)
                .fileData(content)
                .status(ImportBatch.ImportStatus.PENDING)
                .createdBy(uploader)
                .createdAt(LocalDateTime.now())
                .submissionNote(submissionNote)
                .build();

        return importBatchRepository.save(batch);
    }

    /**
     * Create a pending import batch from raw content (e.g. internally generated).
     * Validates the content before saving.
     */
    @Transactional
    public ImportBatch createBatchFromContent(String originalFilename, byte[] content, String entityType, User uploader,
            String submissionNote)
            throws Exception {
        return createBatchFromContent(originalFilename, content, entityType, uploader, submissionNote, false);
    }

    /**
     * Create a pending import batch.
     * Validates the file format (dry run) but does NOT persist data to live tables.
     */
    @Transactional
    public ImportBatch createBatch(MultipartFile file, String entityType, User uploader, String submissionNote)
            throws Exception {
        return createBatchFromContent(file.getOriginalFilename(), file.getBytes(), entityType, uploader,
                submissionNote);
    }

    /**
     * Approve and execute a pending batch.
     */
    @Transactional(rollbackFor = Exception.class)
    public BulkImportResult approveBatch(Long batchId, User approver) throws Exception {
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));

        if (batch.getStatus() != ImportBatch.ImportStatus.PENDING) {
            throw new IllegalStateException("Batch is not in PENDING state");
        }

        // Execute the actual import
        List<String[]> rows = parseBytes(batch.getFileData());
        BulkImportResult result = executeImport(rows, batch.getEntityType(), false, batch.getOriginalFilename());

        // Update batch status
        batch.setStatus(ImportBatch.ImportStatus.APPROVED);
        batch.setApprovedBy(approver);
        batch.setApprovalDate(LocalDateTime.now());
        importBatchRepository.save(batch);

        return result;
    }

    /**
     * Reject a pending batch.
     */
    @Transactional
    public void rejectBatch(Long batchId, User rejector, String reason) {
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));

        if (batch.getStatus() != ImportBatch.ImportStatus.PENDING) {
            throw new IllegalStateException("Batch is not in PENDING state");
        }

        batch.setStatus(ImportBatch.ImportStatus.REJECTED);
        batch.setApprovedBy(rejector); // Used as "Actioned By"
        batch.setApprovalDate(LocalDateTime.now());
        batch.setRejectionReason(reason);
        importBatchRepository.save(batch);
    }

    /**
     * Revert a rejected batch back to draft for re-editing.
     * Creates a NEW draft copy to preserve the rejection history.
     */
    @Transactional
    public ImportBatch revertToDraft(Long batchId, User user) {
        ImportBatch oldBatch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));

        // Security: only the original uploader can revert their batch
        if (!oldBatch.getCreatedBy().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized to modify this batch");
        }

        if (oldBatch.getStatus() != ImportBatch.ImportStatus.REJECTED) {
            throw new IllegalStateException("Only rejected batches can be reverted to draft");
        }

        // Ensure fileData is present
        byte[] fileData = oldBatch.getFileData();
        if (fileData == null || fileData.length == 0) {
            throw new IllegalStateException("Cannot revert: Original batch has no file data");
        }

        // Create new draft from rejected batch
        ImportBatch newDraft = ImportBatch.builder()
                .entityType(oldBatch.getEntityType())
                .originalFilename(oldBatch.getOriginalFilename())
                .fileData(fileData) // Copy content
                .submissionNote(oldBatch.getSubmissionNote()) // Preserve user's note
                .createdBy(user)
                .createdAt(LocalDateTime.now())
                .status(ImportBatch.ImportStatus.DRAFT)
                .build();

        return importBatchRepository.save(newDraft);
    }

    /**
     * Preview a pending batch (Dry Run).
     */
    @Transactional(readOnly = true)
    public BulkImportResult previewBatch(Long batchId) throws Exception {
        long startMs = System.currentTimeMillis();
        ImportBatch batch = importBatchRepository.findById(batchId)
                .orElseThrow(() -> new IllegalArgumentException("Batch not found"));

        List<String[]> rows = parseBytes(batch.getFileData());
        try {
            BulkImportResult result = executeImport(rows, batch.getEntityType(), true, batch.getOriginalFilename());
            log.info("previewBatch(batchId={}, entityType={}, rows={}) completed in {} ms", batchId,
                    batch.getEntityType(), rows.size(), System.currentTimeMillis() - startMs);
            return compactResultForPreview(result);
        } catch (com.university.timetable.service.BulkImportService.BulkImportException e) {
            log.info("previewBatch(batchId={}, entityType={}, rows={}) completed with validation errors in {} ms",
                    batchId, batch.getEntityType(), rows.size(), System.currentTimeMillis() - startMs);
            return compactResultForPreview(e.getResult());
        }
    }

    public List<ImportBatch> getPendingBatches() {
        return importBatchRepository.findByStatusOrderByCreatedAtDesc(ImportBatch.ImportStatus.PENDING);
    }

    // Helper to validate using BulkImportService dry run
    private void validateBatchContent(MultipartFile file, String entityType) throws Exception {
        List<String[]> rows = bulkImportService.parseCsv(file);
        // We run a dry run. If it fails, it throws BulkImportException
        executeImport(rows, entityType, true, file.getOriginalFilename());
    }

    private BulkImportResult executeImport(List<String[]> rows, String entityType, boolean dryRun,
            String originalFilename) throws Exception {
        return switch (entityType.toUpperCase()) {
            case "LECTURERS" -> throw new IllegalStateException(
                    "Lecturers CSV import has been retired. Use users import with LECTURER role.");
            case "ROOMS" -> bulkImportService.importRooms(rows, dryRun, originalFilename);
            case "STUDENT-GROUPS", "STUDENTGROUPS" ->
                bulkImportService.importStudentGroups(rows, dryRun, originalFilename);
            case "ZONES" -> bulkImportService.importZones(rows, dryRun, originalFilename);
            case "FEATURES" -> bulkImportService.importFeatures(rows, dryRun, originalFilename);
            case "COURSES" -> bulkImportService.importCourses(rows, dryRun, originalFilename);
            case "USERS" -> bulkImportService.importUsers(rows, dryRun, originalFilename);
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };
    }

    @Transactional
    public void restoreBatchFromHistory(Long historyId) {
        importBatchRepository.findByImportHistoryId(historyId).ifPresent(batch -> {
            batch.setStatus(ImportBatch.ImportStatus.PENDING);
            batch.setApprovedBy(null);
            batch.setApprovalDate(null);
            // Keep the history link? Or clear it?
            // User might re-approve, creating NEW history.
            // If we keep it, we might find it again.
            // But rollback deletes the entities, so the history record is marked "rolled
            // back".
            // If we re-approve, we get a new History ID.
            // So we should probably clear the old link or overwrite it later.
            // Overwriting later is fine.
            importBatchRepository.save(batch);
            log.info("Restored Staging Batch {} to PENDING state after rollback of history {}", batch.getId(),
                    historyId);
        });
    }

    // ==================== DRAFT MANAGEMENT ====================

    @Transactional
    public ImportBatch createDraft(MultipartFile file, String entityType, User uploader) throws Exception {
        // No validation needed for drafts (it's a draft!), but basic file check ok
        if (file.isEmpty())
            throw new IllegalArgumentException("Empty file");

        ImportBatch batch = ImportBatch.builder()
                .entityType(entityType.toUpperCase())
                .originalFilename(file.getOriginalFilename())
                .fileData(file.getBytes())
                .status(ImportBatch.ImportStatus.DRAFT)
                .createdBy(uploader)
                .createdAt(LocalDateTime.now())
                .submissionNote("Draft")
                .build();

        return importBatchRepository.save(batch);
    }

    public List<ImportBatch> getDrafts(User user) {
        return importBatchRepository.findByStatusAndCreatedBy_IdOrderByCreatedAtDesc(
                ImportBatch.ImportStatus.DRAFT, user.getId());
    }

    /**
     * Get all submitted batches for a user (non-draft: PENDING, APPROVED,
     * REJECTED).
     */
    public List<ImportBatch> getMySubmissions(User user) {
        return importBatchRepository.findByCreatedBy_IdOrderByCreatedAtDesc(user.getId())
                .stream()
                .filter(b -> b.getStatus() != ImportBatch.ImportStatus.DRAFT)
                .toList();
    }

    @Transactional(readOnly = true)
    public ImportBatch getDraft(Long id, User user) {
        ImportBatch draft = importBatchRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Draft not found"));
        // Security check: only owner can see their draft
        if (!draft.getCreatedBy().getId().equals(user.getId())) {
            throw new SecurityException("Not authorized to access this draft");
        }
        return draft;
    }

    @Transactional
    public void updateDraft(Long id, User user, String csvContent) {
        ImportBatch draft = getDraft(id, user);
        draft.setFileData(csvContent.getBytes(StandardCharsets.UTF_8));
        importBatchRepository.save(draft);
    }

    @Transactional
    public void deleteDraft(Long id, User user) {
        ImportBatch draft = getDraft(id, user);
        importBatchRepository.delete(draft);
    }

    @Transactional
    public void submitDraft(Long id, User user) throws Exception {
        long startMs = System.currentTimeMillis();
        ImportBatch draft = getDraft(id, user);

        // Validate before submitting (throws exception if invalid)
        // We need to parse the current byte content
        List<String[]> rows = parseBytes(draft.getFileData());
        executeImport(rows, draft.getEntityType(), true, draft.getOriginalFilename());

        draft.setStatus(ImportBatch.ImportStatus.PENDING);
        draft.setCreatedAt(LocalDateTime.now()); // Reset creation time to submission time? Or keep original? user might
                                                 // prefer updated time.
        draft.setSubmissionNote("Submitted from Draft");
        importBatchRepository.save(draft);
        log.info("submitDraft(id={}, entityType={}, user={}) completed in {} ms",
                id, draft.getEntityType(), user.getEmail(), System.currentTimeMillis() - startMs);
    }

    private List<String[]> parseBytes(byte[] data) throws Exception {
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8));
        return bulkImportService.parseCsv(reader);
    }

    private BulkImportResult compactResultForApproval(BulkImportResult result) {
        // Approval UI only needs summary counts, not per-row payloads.
        result.setValidRows(new ArrayList<>());
        result.setWarningRows(new ArrayList<>());
        result.setConflicts(new ArrayList<>());
        result.setGeneratedCsv(null);
        return result;
    }

    private BulkImportResult compactResultForPreview(BulkImportResult result) {
        if (result.getValidRows() != null && result.getValidRows().size() > PREVIEW_MAX_VALID_ROWS) {
            int total = result.getValidRows().size();
            result.setValidRows(new ArrayList<>(result.getValidRows().subList(0, PREVIEW_MAX_VALID_ROWS)));
            result.getGlobalErrors().add("Preview truncated to first " + PREVIEW_MAX_VALID_ROWS + " valid rows out of "
                    + total + " rows for performance.");
        }
        return result;
    }
}
