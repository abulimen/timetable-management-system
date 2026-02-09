package com.university.timetable.service;

import com.university.timetable.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for bulk data operations including system-wide data wipe.
 * Handles deletion in correct FK order to avoid constraint violations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataWipeService {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final LecturerRepository lecturerRepository;
    private final RoomRepository roomRepository;
    private final ZoneRepository zoneRepository;
    private final FeatureRepository featureRepository;
    private final TimeslotRepository timeslotRepository;
    private final AuditLogService auditLogService;

    /**
     * Wipe ALL data from the system (except settings).
     * Deletes in correct FK order to avoid constraint violations.
     * 
     * @return Map of entity names to deleted counts
     */
    @Transactional
    public Map<String, Long> wipeAllData() {
        log.warn("SYSTEM WIPE INITIATED - Deleting all data...");

        Map<String, Long> deletedCounts = new LinkedHashMap<>();

        // Delete in FK order (children first, then parents)

        // 1. Lessons (references timeslots, rooms, lecturers, courses)
        long lessonCount = lessonRepository.count();
        lessonRepository.deleteAll();
        deletedCounts.put("lessons", lessonCount);
        log.info("Deleted {} lessons", lessonCount);

        // 2. Timeslots
        long timeslotCount = timeslotRepository.count();
        timeslotRepository.deleteAll();
        deletedCounts.put("timeslots", timeslotCount);
        log.info("Deleted {} timeslots", timeslotCount);

        // 3. Courses (references lecturers, student groups, features, zones)
        long courseCount = courseRepository.count();
        courseRepository.deleteAll();
        deletedCounts.put("courses", courseCount);
        log.info("Deleted {} courses", courseCount);

        // 4. Student Groups
        long groupCount = studentGroupRepository.count();
        studentGroupRepository.deleteAll();
        deletedCounts.put("studentGroups", groupCount);
        log.info("Deleted {} student groups", groupCount);

        // 5. Lecturers
        long lecturerCount = lecturerRepository.count();
        lecturerRepository.deleteAll();
        deletedCounts.put("lecturers", lecturerCount);
        log.info("Deleted {} lecturers", lecturerCount);

        // 6. Rooms (references zones, features)
        long roomCount = roomRepository.count();
        roomRepository.deleteAll();
        deletedCounts.put("rooms", roomCount);
        log.info("Deleted {} rooms", roomCount);

        // 7. Zones
        long zoneCount = zoneRepository.count();
        zoneRepository.deleteAll();
        deletedCounts.put("zones", zoneCount);
        log.info("Deleted {} zones", zoneCount);

        // 8. Features
        long featureCount = featureRepository.count();
        featureRepository.deleteAll();
        deletedCounts.put("features", featureCount);
        log.info("Deleted {} features", featureCount);

        log.warn("SYSTEM WIPE COMPLETE - All data deleted");

        // Audit logging (critical action)
        String summary = deletedCounts.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining(", "));
        auditLogService.logSystemAction(
                "SYSTEM DATA WIPE executed. Deleted: " + summary,
                true, null);

        return deletedCounts;
    }

    /**
     * Delete all records of a specific entity type.
     */
    @Transactional
    public long deleteAllOfType(String entityType) {
        log.warn("Bulk delete initiated for entity: {}", entityType);

        long count = switch (entityType.toLowerCase()) {
            case "lessons" -> {
                long c = lessonRepository.count();
                lessonRepository.deleteAll();
                yield c;
            }
            case "courses" -> {
                // Must delete lessons first
                lessonRepository.deleteAll();
                long c = courseRepository.count();
                courseRepository.deleteAll();
                yield c;
            }
            case "student-groups", "studentgroups" -> {
                // Must delete lessons and courses first (courses reference student groups)
                lessonRepository.deleteAll();
                courseRepository.deleteAll();
                long c = studentGroupRepository.count();
                studentGroupRepository.deleteAll();
                yield c;
            }
            case "lecturers" -> {
                // Must delete lessons and courses first (courses reference lecturers)
                lessonRepository.deleteAll();
                courseRepository.deleteAll();
                long c = lecturerRepository.count();
                lecturerRepository.deleteAll();
                yield c;
            }

            case "rooms" -> {
                // Must delete lessons first
                lessonRepository.deleteAll();
                long c = roomRepository.count();
                roomRepository.deleteAll();
                yield c;
            }
            case "zones" -> {
                // Must delete lessons and rooms first (rooms reference zones)
                lessonRepository.deleteAll();
                roomRepository.deleteAll();
                long c = zoneRepository.count();
                zoneRepository.deleteAll();
                yield c;
            }
            case "features" -> {
                long c = featureRepository.count();
                featureRepository.deleteAll();
                yield c;
            }
            case "timeslots" -> {
                // Must delete lessons first
                lessonRepository.deleteAll();
                long c = timeslotRepository.count();
                timeslotRepository.deleteAll();
                yield c;
            }
            default -> throw new IllegalArgumentException("Unknown entity type: " + entityType);
        };

        log.info("Deleted {} {} records", count, entityType);
        return count;
    }
}
