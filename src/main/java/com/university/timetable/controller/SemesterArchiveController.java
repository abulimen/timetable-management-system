package com.university.timetable.controller;

import com.university.timetable.domain.AuditAction;
import com.university.timetable.domain.SemesterArchive;
import com.university.timetable.dto.ArchivedSpecialEventDTO;
import com.university.timetable.dto.ArchivedStudentGroupDTO;
import com.university.timetable.dto.ArchiveRequestDTO;
import com.university.timetable.dto.SemesterArchiveDTO;
import com.university.timetable.dto.TimetableViewDTO;
import com.university.timetable.service.AuditLogService;
import com.university.timetable.service.ExportService;
import com.university.timetable.service.SemesterArchiveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

/**
 * REST controller for semester archive operations.
 * 
 * Endpoints:
 * - GET /api/v1/semesters/archives - List all archives
 * - POST /api/v1/semesters/archive - Archive current semester
 * - GET /api/v1/semesters/{code}/timetable - View archived timetable
 * - DELETE /api/v1/semesters/{code} - Delete archive
 */
@RestController
@RequestMapping("/api/v1/semesters")
@RequiredArgsConstructor
@Slf4j
public class SemesterArchiveController {

    private final SemesterArchiveService archiveService;
    private final ExportService exportService;
    private final AuditLogService auditLogService;

    /**
     * GET /semesters/archives
     * List all archived semesters.
     */
    @GetMapping("/archives")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<List<SemesterArchiveDTO>> listArchives() {
        log.info("Listing all semester archives");
        List<SemesterArchiveDTO> archives = archiveService.getAllArchives()
            .stream()
            .map(SemesterArchiveDTO::fromEntity)
            .collect(Collectors.toList());
        auditLogService.logAction(
                AuditAction.SYSTEM_ACTION,
                "SemesterArchive",
                null,
                "all",
                null,
                Map.of("count", archives.size()),
                "Listed semester archives");
        return ResponseEntity.ok(archives);
    }

    /**
     * POST /semesters/archive
     * Archive current semester data to prefixed tables.
     * 
     * Body: {"code": "2024_2025_S1", "name": "2024/2025 1st Semester"}
     */
    @PostMapping("/archive")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> archiveCurrentSemester(@RequestBody ArchiveRequestDTO request) {
        log.info("Archiving current semester as: {}", request.getCode());
        
        if (request.getCode() == null || request.getCode().isBlank()) {
            auditLogService.logActionSync(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    null,
                    request.getCode(),
                    null,
                    null,
                    "Archive semester failed: missing code",
                    false,
                    "Code is required");
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Code is required"));
        }
        
        // Validate code format (alphanumeric and underscores only)
        if (!request.getCode().matches("^[a-zA-Z0-9_]+$")) {
            auditLogService.logActionSync(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    request.getCode(),
                    request.getCode(),
                    null,
                    null,
                    "Archive semester failed: invalid code format",
                    false,
                    "Invalid archive code format");
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Code must be alphanumeric with underscores only"));
        }
        if (request.getCode().length() > 20) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", "Code must be at most 20 characters"));
        }
        
        try {
            SemesterArchive archive = archiveService.archiveCurrentSemester(request);
            auditLogService.logAction(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    archive.getCode(),
                    archive.getName(),
                    null,
                    SemesterArchiveDTO.fromEntity(archive),
                    "Archived semester " + archive.getCode());
            return ResponseEntity.ok(Map.of(
                "message", "Semester archived successfully",
                "archive", SemesterArchiveDTO.fromEntity(archive)
            ));
        } catch (IllegalArgumentException e) {
            auditLogService.logActionSync(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    request.getCode(),
                    request.getCode(),
                    null,
                    null,
                    "Archive semester failed",
                    false,
                    e.getMessage());
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /semesters/{code}
     * Get archive metadata by code.
     */
    @GetMapping("/{code}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getArchive(@PathVariable String code) {
        try {
            SemesterArchive archive = archiveService.getArchive(code);
            auditLogService.logAction(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    archive.getCode(),
                    archive.getName(),
                    null,
                    SemesterArchiveDTO.fromEntity(archive),
                    "Viewed semester archive metadata " + archive.getCode());
            return ResponseEntity.ok(SemesterArchiveDTO.fromEntity(archive));
        } catch (IllegalArgumentException e) {
            auditLogService.logActionSync(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    code,
                    code,
                    null,
                    null,
                    "View semester archive metadata failed",
                    false,
                    e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * GET /semesters/{code}/timetable
     * View timetable from an archived semester.
     */
    @GetMapping("/{code}/timetable")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> getArchivedTimetable(@PathVariable String code) {
        log.info("Fetching archived timetable for: {}", code);
        
        try {
            List<TimetableViewDTO> timetable = archiveService.getArchivedTimetable(code);
            auditLogService.logAction(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    code,
                    code,
                    null,
                    Map.of("rows", timetable.size()),
                    "Viewed archived timetable " + code);
            return ResponseEntity.ok(timetable);
        } catch (IllegalArgumentException e) {
            auditLogService.logActionSync(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    code,
                    code,
                    null,
                    null,
                    "View archived timetable failed",
                    false,
                    e.getMessage());
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            auditLogService.logActionSync(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    code,
                    code,
                    null,
                    null,
                    "View archived timetable failed",
                    false,
                    e.getMessage());
            log.error("Error fetching archived timetable", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to fetch archived timetable: " + e.getMessage()));
        }
    }

    @GetMapping("/{code}/groups")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<List<ArchivedStudentGroupDTO>> getArchivedGroups(@PathVariable String code) {
        return ResponseEntity.ok(archiveService.getArchivedStudentGroups(code));
    }

    @GetMapping("/{code}/special-events")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<List<ArchivedSpecialEventDTO>> getArchivedSpecialEvents(@PathVariable String code) {
        return ResponseEntity.ok(archiveService.getArchivedSpecialEvents(code));
    }

    /**
     * GET /semesters/{code}/export
     * Export archived timetable as CSV.
     */
    @GetMapping(value = "/{code}/export", produces = "text/csv")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<String> exportArchivedTimetable(@PathVariable String code) {
        try {
            List<TimetableViewDTO> timetable = archiveService.getArchivedTimetable(code);
            String csv = buildArchivedTimetableCsv(timetable);
            auditLogService.logAction(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    code,
                    code,
                    null,
                    Map.of("rows", timetable.size()),
                    "Exported archived timetable " + code);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"archived_timetable_" + code + ".csv\"")
                    .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                    .body(csv);
        } catch (IllegalArgumentException e) {
            auditLogService.logActionSync(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    code,
                    code,
                    null,
                    null,
                    "Export archived timetable failed",
                    false,
                    e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{code}/export/excel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<byte[]> exportArchivedTimetableExcel(@PathVariable String code, @RequestBody ExportRequestDTO request) {
        try {
            byte[] data = exportService.exportArchivedToExcel(code, request.groupIds, request.title);
            String filename = "archived_timetable_" + code + ".xlsx";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(data);
        } catch (Exception e) {
            log.error("Error exporting archived timetable to Excel for {}", code, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @PostMapping("/{code}/export/pdf")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<byte[]> exportArchivedTimetablePdf(@PathVariable String code, @RequestBody ExportRequestDTO request) {
        try {
            byte[] data = exportService.exportArchivedToPdf(code, request.groupIds, request.title);
            String filename = "archived_timetable_" + code + ".pdf";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(data);
        } catch (Exception e) {
            log.error("Error exporting archived timetable to PDF for {}", code, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * POST /semesters/{code}/restore
     * Restore archived snapshot to active tables after automatic backup.
     */
    @PostMapping("/{code}/restore")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> restoreArchive(@PathVariable String code) {
        log.info("Restoring archive: {}", code);
        try {
            Map<String, Object> result = archiveService.restoreArchive(code);
            auditLogService.logAction(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    code,
                    code,
                    null,
                    result,
                    "Restored archive " + code + " with auto backup");
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            auditLogService.logActionSync(
                    AuditAction.SYSTEM_ACTION,
                    "SemesterArchive",
                    code,
                    code,
                    null,
                    null,
                    "Restore archive failed",
                    false,
                    e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * DELETE /semesters/{code}
     * Delete an archive and drop its tables.
     */
    @DeleteMapping("/{code}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<?> deleteArchive(@PathVariable String code) {
        log.info("Deleting archive: {}", code);
        
        try {
            SemesterArchive archive = archiveService.getArchive(code);
            archiveService.deleteArchive(code);
            auditLogService.logAction(
                    AuditAction.DELETE,
                    "SemesterArchive",
                    code,
                    archive.getName(),
                    SemesterArchiveDTO.fromEntity(archive),
                    null,
                    "Deleted semester archive " + code);
            return ResponseEntity.ok(Map.of(
                "message", "Archive deleted successfully",
                "code", code
            ));
        } catch (IllegalArgumentException e) {
            auditLogService.logActionSync(
                    AuditAction.DELETE,
                    "SemesterArchive",
                    code,
                    code,
                    null,
                    null,
                    "Delete semester archive failed",
                    false,
                    e.getMessage());
            return ResponseEntity.notFound().build();
        }
    }

    private String buildArchivedTimetableCsv(List<TimetableViewDTO> timetable) {
        StringBuilder sb = new StringBuilder();
        sb.append("course_code,course_name,day_of_week,start_time,end_time,room_name,lecturer_name,student_groups,total_students,online,pinned\n");
        for (TimetableViewDTO row : timetable) {
            StringJoiner groupJoiner = new StringJoiner(" | ");
            if (row.getCombinedGroupNames() != null && !row.getCombinedGroupNames().isEmpty()) {
                row.getCombinedGroupNames().forEach(groupJoiner::add);
            } else if (row.getStudentGroupName() != null) {
                groupJoiner.add(row.getStudentGroupName());
            }
            sb.append(csv(row.getCourseCode())).append(',')
                    .append(csv(row.getCourseName())).append(',')
                    .append(csv(row.getDayOfWeek() != null ? row.getDayOfWeek().name() : "")).append(',')
                    .append(csv(row.getStartTime() != null ? row.getStartTime().toString() : "")).append(',')
                    .append(csv(row.getEndTime() != null ? row.getEndTime().toString() : "")).append(',')
                    .append(csv(row.getRoomName())).append(',')
                    .append(csv(row.getLecturerName())).append(',')
                    .append(csv(groupJoiner.toString())).append(',')
                    .append(row.getTotalStudentCount()).append(',')
                    .append(Boolean.TRUE.equals(row.isOnline())).append(',')
                    .append(Boolean.TRUE.equals(row.isPinned())).append('\n');
        }
        return sb.toString();
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    public static class ExportRequestDTO {
        public List<Long> groupIds;
        public String title;
    }
}
