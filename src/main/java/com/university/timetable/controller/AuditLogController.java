package com.university.timetable.controller;

import com.university.timetable.domain.AuditAction;
import com.university.timetable.domain.AuditLog;
import com.university.timetable.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * REST controller for audit log viewing and export.
 * Access restricted to ADMIN and SUPER_ADMIN roles.
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
@Slf4j
public class AuditLogController {

    private final AuditLogService auditLogService;

    /**
     * List audit logs with filtering and pagination.
     * GET /api/audit-logs
     */
    @GetMapping
    public ResponseEntity<Page<AuditLogDTO>> getLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) List<String> entityTypes,
            @RequestParam(required = false) List<String> actions,
            @RequestParam(required = false) String actorId,
            @RequestParam(required = false) Boolean success,
            @PageableDefault(size = 25, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        List<AuditAction> actionEnums = actions != null
                ? actions.stream().map(AuditAction::valueOf).toList()
                : null;

        Page<AuditLog> logs = auditLogService.queryLogs(
                startDate, endDate, entityTypes, actionEnums, actorId, success, pageable);

        return ResponseEntity.ok(logs.map(this::toDTO));
    }

    /**
     * Get a single audit log entry.
     * GET /api/audit-logs/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<AuditLogDTO> getLogById(@PathVariable Long id) {
        return auditLogService.getLogById(id)
                .map(this::toDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get audit history for a specific entity.
     * GET /api/audit-logs/entity/{entityType}/{entityId}
     */
    @GetMapping("/entity/{entityType}/{entityId}")
    public ResponseEntity<Page<AuditLogDTO>> getEntityHistory(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @PageableDefault(size = 25, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {

        Page<AuditLog> logs = auditLogService.getEntityHistory(entityType, entityId, pageable);
        return ResponseEntity.ok(logs.map(this::toDTO));
    }

    /**
     * Get summary statistics.
     * GET /api/audit-logs/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime since) {

        if (since == null) {
            since = LocalDateTime.now().minusDays(7);
        }
        return ResponseEntity.ok(auditLogService.getSummary(since));
    }

    /**
     * Get filter options for UI dropdowns.
     * GET /api/audit-logs/filter-options
     */
    @GetMapping("/filter-options")
    public ResponseEntity<Map<String, List<String>>> getFilterOptions() {
        return ResponseEntity.ok(auditLogService.getFilterOptions());
    }

    /**
     * Export audit logs as CSV.
     * GET /api/audit-logs/export
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportLogs(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(required = false) List<String> entityTypes,
            @RequestParam(required = false) List<String> actions) {

        List<AuditAction> actionEnums = actions != null
                ? actions.stream().map(AuditAction::valueOf).toList()
                : null;

        String csv = auditLogService.exportToCsv(startDate, endDate, entityTypes, actionEnums);

        String filename = "audit_logs_" + LocalDateTime.now().toString().replace(":", "-") + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv.getBytes());
    }

    /**
     * Convert entity to DTO.
     */
    private AuditLogDTO toDTO(AuditLog log) {
        AuditLogDTO dto = new AuditLogDTO();
        dto.id = log.getId();
        dto.timestamp = log.getTimestamp();
        dto.actorType = log.getActorType() != null ? log.getActorType().name() : null;
        dto.actorId = log.getActorId();
        dto.actorName = log.getActorName();
        dto.actorIpAddress = log.getActorIpAddress();
        dto.action = log.getAction() != null ? log.getAction().name() : null;
        dto.entityType = log.getEntityType();
        dto.entityId = log.getEntityId();
        dto.entityName = log.getEntityName();
        dto.previousValue = log.getPreviousValue();
        dto.newValue = log.getNewValue();
        dto.changedFields = log.getChangedFields();
        dto.description = log.getDescription();
        dto.success = log.getSuccess();
        dto.errorMessage = log.getErrorMessage();
        return dto;
    }

    /**
     * DTO for audit log entries.
     */
    public static class AuditLogDTO {
        public Long id;
        public LocalDateTime timestamp;
        public String actorType;
        public String actorId;
        public String actorName;
        public String actorIpAddress;
        public String action;
        public String entityType;
        public String entityId;
        public String entityName;
        public String previousValue;
        public String newValue;
        public String changedFields;
        public String description;
        public Boolean success;
        public String errorMessage;
    }
}
