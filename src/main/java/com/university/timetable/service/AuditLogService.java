package com.university.timetable.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.timetable.domain.*;
import com.university.timetable.repository.AuditLogRepository;
import com.university.timetable.repository.UserRepository;
import com.university.timetable.util.AuditRequestContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing audit logs.
 * Provides async logging and query capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    // ========== LOGGING METHODS ==========

    /**
     * Log a user action asynchronously.
     * Uses a new transaction to ensure logs are written even if the main
     * transaction rolls back.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAction(AuditAction action, String entityType, String entityId,
            String entityName, Object previousValue, Object newValue,
            String description) {
        try {
            logActionSync(action, entityType, entityId, entityName, previousValue, newValue, description, true, null);
        } catch (Exception e) {
            log.error("Failed to log audit action: {}", e.getMessage(), e);
        }
    }

    /**
     * Log a user action synchronously (for critical operations).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logActionSync(AuditAction action, String entityType, String entityId,
            String entityName, Object previousValue, Object newValue,
            String description, boolean success, String errorMessage) {
        try {
            String userId = AuditRequestContext.getCurrentUserId();
            String userName = getUserName(userId);
            String ipAddress = AuditRequestContext.getClientIpAddress();
            String sessionId = AuditRequestContext.getSessionId();

            AuditLog auditLog = AuditLog.builder()
                    .timestamp(LocalDateTime.now())
                    .actorType(userId != null ? ActorType.USER : ActorType.SYSTEM)
                    .actorId(userId)
                    .actorName(userName)
                    .actorIpAddress(ipAddress)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .entityName(entityName)
                    .previousValue(toJson(previousValue))
                    .newValue(toJson(newValue))
                    .changedFields(getChangedFields(previousValue, newValue))
                    .description(description)
                    .requestId(UUID.randomUUID().toString().substring(0, 8))
                    .sessionId(sessionId)
                    .success(success)
                    .errorMessage(errorMessage)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit logged: {} {} {} by {}", action, entityType, entityId, userId);
        } catch (Exception e) {
            log.error("Failed to save audit log: {}", e.getMessage(), e);
        }
    }

    /**
     * Log a system action (scheduler, bulk operations, etc.).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSystemAction(String description, boolean success, String errorMessage) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .timestamp(LocalDateTime.now())
                    .actorType(ActorType.SYSTEM)
                    .actorId("SYSTEM")
                    .actorName("System")
                    .action(AuditAction.SYSTEM_ACTION)
                    .description(description)
                    .requestId(UUID.randomUUID().toString().substring(0, 8))
                    .success(success)
                    .errorMessage(errorMessage)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("System audit logged: {}", description);
        } catch (Exception e) {
            log.error("Failed to save system audit log: {}", e.getMessage(), e);
        }
    }

    /**
     * Log a scheduler action (solver operations).
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logSchedulerAction(String description, boolean success) {
        try {
            String userId = AuditRequestContext.getCurrentUserId();
            String userName = getUserName(userId);

            AuditLog auditLog = AuditLog.builder()
                    .timestamp(LocalDateTime.now())
                    .actorType(userId != null ? ActorType.USER : ActorType.SCHEDULER)
                    .actorId(userId != null ? userId : "SCHEDULER")
                    .actorName(userName != null ? userName : "Scheduler")
                    .actorIpAddress(AuditRequestContext.getClientIpAddress())
                    .action(AuditAction.SYSTEM_ACTION)
                    .description(description)
                    .requestId(UUID.randomUUID().toString().substring(0, 8))
                    .success(success)
                    .build();

            auditLogRepository.save(auditLog);
            log.debug("Scheduler audit logged: {}", description);
        } catch (Exception e) {
            log.error("Failed to save scheduler audit log: {}", e.getMessage(), e);
        }
    }

    // ========== QUERY METHODS ==========

    /**
     * Get all logs with pagination.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getAllLogs(Pageable pageable) {
        return auditLogRepository.findAll(pageable);
    }

    /**
     * Get a single log entry by ID.
     */
    @Transactional(readOnly = true)
    public Optional<AuditLog> getLogById(Long id) {
        return auditLogRepository.findById(id);
    }

    /**
     * Query logs with multiple filters.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> queryLogs(LocalDateTime startDate, LocalDateTime endDate,
            List<String> entityTypes, List<AuditAction> actions,
            String actorId, Boolean success, Pageable pageable) {
        return auditLogRepository.findWithFilters(startDate, endDate, entityTypes, actions, actorId, success, pageable);
    }

    /**
     * Get history for a specific entity.
     */
    @Transactional(readOnly = true)
    public Page<AuditLog> getEntityHistory(String entityType, String entityId, Pageable pageable) {
        return auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
    }

    /**
     * Get summary statistics.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSummary(LocalDateTime since) {
        Map<String, Object> summary = new HashMap<>();

        // Action counts
        List<Object[]> actionCounts = auditLogRepository.countByAction(since);
        Map<String, Long> actionMap = actionCounts.stream()
                .collect(Collectors.toMap(
                        row -> ((AuditAction) row[0]).name(),
                        row -> (Long) row[1]));
        summary.put("byAction", actionMap);

        // Entity type counts
        List<Object[]> entityCounts = auditLogRepository.countByEntityType(since);
        Map<String, Long> entityMap = entityCounts.stream()
                .filter(row -> row[0] != null)
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]));
        summary.put("byEntityType", entityMap);

        return summary;
    }

    /**
     * Get filter options for UI.
     */
    @Transactional(readOnly = true)
    public Map<String, List<String>> getFilterOptions() {
        Map<String, List<String>> options = new HashMap<>();
        options.put("entityTypes", auditLogRepository.findDistinctEntityTypes());
        options.put("actorIds", auditLogRepository.findDistinctActorIds());
        options.put("actions", Arrays.stream(AuditAction.values()).map(Enum::name).toList());
        return options;
    }

    // ========== EXPORT METHODS ==========

    /**
     * Export logs to CSV format.
     */
    @Transactional(readOnly = true)
    public String exportToCsv(LocalDateTime startDate, LocalDateTime endDate,
            List<String> entityTypes, List<AuditAction> actions) {
        // Get all matching logs (no pagination)
        List<AuditLog> logs = auditLogRepository.findWithFilters(
                startDate, endDate, entityTypes, actions, null, null,
                Pageable.unpaged()).getContent();

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);

        // Header
        pw.println(
                "ID,Timestamp,Actor Type,Actor ID,Actor Name,IP Address,Action,Entity Type,Entity ID,Entity Name,Description,Success,Error Message");

        // Data rows
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (AuditLog log : logs) {
            pw.printf("%d,\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",\"%s\",%s,\"%s\"%n",
                    log.getId(),
                    log.getTimestamp() != null ? log.getTimestamp().format(formatter) : "",
                    nullSafe(log.getActorType()),
                    nullSafe(log.getActorId()),
                    escapeCsv(log.getActorName()),
                    nullSafe(log.getActorIpAddress()),
                    nullSafe(log.getAction()),
                    nullSafe(log.getEntityType()),
                    nullSafe(log.getEntityId()),
                    escapeCsv(log.getEntityName()),
                    escapeCsv(log.getDescription()),
                    log.getSuccess(),
                    escapeCsv(log.getErrorMessage()));
        }

        return sw.toString();
    }

    // ========== HELPER METHODS ==========

    private String getUserName(String userId) {
        if (userId == null) {
            return null;
        }
        try {
            return userRepository.findByEmailIgnoreCase(userId)
                    .map(User::getFullName)
                    .orElse(userId);
        } catch (Exception e) {
            return userId;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON: {}", e.getMessage());
            return obj.toString();
        }
    }

    private String getChangedFields(Object previous, Object current) {
        if (previous == null || current == null) {
            return null;
        }
        // For simplicity, return null - could implement field comparison if needed
        return null;
    }

    private String nullSafe(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\"", "\"\"");
    }
}
