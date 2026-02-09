package com.university.timetable.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.university.timetable.domain.*;
import com.university.timetable.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing import history and rollback operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ImportHistoryService {

    private final ImportHistoryRepository importHistoryRepository;
    private final ObjectMapper objectMapper;
    private final ConstraintSettingsService settingsService;

    // Repositories needed for rollback operations
    private final CourseRepository courseRepository;
    private final LecturerRepository lecturerRepository;
    private final RoomRepository roomRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final ZoneRepository zoneRepository;
    private final FeatureRepository featureRepository;
    private final UserRepository userRepository;

    /**
     * Record a successful import operation.
     */
    @Transactional
    public ImportHistory recordImport(
            String entityType,
            String importMode,
            String fileName,
            List<Long> createdIds,
            User user) {
        String createdIdsJson = null;
        try {
            if (createdIds != null && !createdIds.isEmpty()) {
                createdIdsJson = objectMapper.writeValueAsString(createdIds);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize created IDs", e);
        }

        ImportHistory history = ImportHistory.builder()
                .timestamp(LocalDateTime.now())
                .entityType(entityType)
                .importMode(importMode)
                .fileName(fileName)
                .createdCount(createdIds != null ? createdIds.size() : 0)
                .updatedCount(0)
                .skippedCount(0)
                .createdIds(createdIdsJson)
                .canRollback(true)
                .rolledBack(false)
                .user(user)
                .build();

        return importHistoryRepository.save(history);
    }

    /**
     * Get all import history ordered by most recent first.
     */
    @Transactional(readOnly = true)
    public List<ImportHistory> getHistory() {
        return importHistoryRepository.findAllWithUserOrderByTimestampDesc();
    }

    /**
     * Get import history for a specific entity type.
     */
    public List<ImportHistory> getHistoryByEntity(String entityType) {
        return importHistoryRepository.findByEntityTypeOrderByTimestampDesc(entityType);
    }

    /**
     * Rollback an import operation by deleting created entities.
     */
    @Transactional
    public boolean rollback(Long historyId, User rolledBackByUser) {
        Optional<ImportHistory> historyOpt = importHistoryRepository.findRollbackCandidate(historyId);

        if (historyOpt.isEmpty()) {
            log.warn("Import history {} not found or not eligible for rollback", historyId);
            return false;
        }

        ImportHistory history = historyOpt.get();
        int window = settingsService.getRollbackWindowHours();

        if (!history.isRollbackAvailable(window)) {
            log.warn("Rollback window expired for import {}", historyId);
            return false;
        }

        try {
            // Parse created IDs
            List<Long> createdIds = parseCreatedIds(history.getCreatedIds());

            if (createdIds != null && !createdIds.isEmpty()) {
                deleteEntities(history.getEntityType(), createdIds);
            }

            // Mark as rolled back
            history.setRolledBack(true);
            history.setRolledBackAt(LocalDateTime.now());
            history.setRolledBackBy(rolledBackByUser);
            importHistoryRepository.save(history);

            log.info("Successfully rolled back import {} ({} {} entities)",
                    historyId, createdIds != null ? createdIds.size() : 0, history.getEntityType());
            return true;

        } catch (Exception e) {
            log.error("Failed to rollback import {}", historyId, e);
            throw new RuntimeException("Rollback failed: " + e.getMessage(), e);
        }
    }

    /**
     * Delete entities by type and IDs.
     */
    private void deleteEntities(String entityType, List<Long> ids) {
        switch (entityType.toUpperCase()) {
            case "COURSES":
                courseRepository.deleteAllById(ids);
                break;
            case "LECTURERS":
                lecturerRepository.deleteAllById(ids);
                break;
            case "ROOMS":
                roomRepository.deleteAllById(ids);
                break;
            case "STUDENT_GROUPS":
                studentGroupRepository.deleteAllById(ids);
                break;
            case "ZONES":
                zoneRepository.deleteAllById(ids);
                break;
            case "FEATURES":
                featureRepository.deleteAllById(ids);
                break;
            case "USERS":
                userRepository.deleteAllById(ids);
                break;
            default:
                throw new IllegalArgumentException("Unknown entity type: " + entityType);
        }
    }

    /**
     * Parse JSON array of created IDs.
     */
    private List<Long> parseCreatedIds(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Long>>() {
            });
        } catch (JsonProcessingException e) {
            log.error("Failed to parse created IDs JSON", e);
            return List.of();
        }
    }

    /**
     * Check if a specific import can be rolled back.
     */
    public boolean canRollback(Long historyId) {
        int window = settingsService.getRollbackWindowHours();
        return importHistoryRepository.findById(historyId)
                .map(h -> h.isRollbackAvailable(window))
                .orElse(false);
    }
}
