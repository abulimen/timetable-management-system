package com.university.timetable.controller;

import com.university.timetable.domain.*;
import com.university.timetable.repository.*;
import com.university.timetable.service.AuditLogService;
import com.university.timetable.service.LessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/courses")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CourseController {

    private final CourseRepository courseRepository;
    private final LecturerRepository lecturerRepository;
    private final StudentGroupRepository studentGroupRepository;
    private final FeatureRepository featureRepository;
    private final ZoneRepository zoneRepository;
    private final LessonService lessonService;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<CourseDTO> getAll() {
        return courseRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CourseDTO> getById(@PathVariable Long id) {
        return courseRepository.findById(id)
                .map(c -> ResponseEntity.ok(toDTO(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> create(@RequestBody CourseCreateDTO dto) {
        Course course = new Course();
        String normalizedCode = dto.code != null ? dto.code.trim().toUpperCase() : null;
        course.setCode(normalizedCode);
        course.setName(dto.name);
        course.setTotalWeeklyHours(dto.totalWeeklyHours);
        course.setOnline(dto.online != null && dto.online);

        if (dto.lecturerId != null) {
            lecturerRepository.findById(dto.lecturerId).ifPresent(course::setLecturer);
        }

        Set<StudentGroup> groups = resolveStudentGroups(dto);
        if (!groups.isEmpty()) {
            course.setStudentGroups(groups);
            course.setStudentGroup(groups.iterator().next());
        } else {
            course.setStudentGroups(new HashSet<>());
            course.setStudentGroup(null);
        }

        String overlapError = validateNoCourseGroupOverlap(normalizedCode, groups, null);
        if (overlapError != null) {
            return ResponseEntity.badRequest().body(Map.of("message", overlapError));
        }

        if (dto.requiredFeatureIds != null && !dto.requiredFeatureIds.isEmpty()) {
            Set<Feature> features = dto.requiredFeatureIds.stream()
                    .map(featureRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .collect(Collectors.toSet());
            course.setRequiredFeatures(features);
        }

        if (dto.allowedZoneIds != null && !dto.allowedZoneIds.isEmpty()) {
            Set<Zone> zones = dto.allowedZoneIds.stream()
                    .map(zoneRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .collect(Collectors.toSet());
            course.setAllowedZones(zones);
        }

        Course saved = courseRepository.save(course);

        // Auto-generate lessons for new course
        if (dto.generateLessons != null && dto.generateLessons) {
            lessonService.generateLessons(saved);
        }

        // Audit logging
        auditLogService.logAction(AuditAction.CREATE, "Course", saved.getId().toString(),
                saved.getCode() + " - " + saved.getName(), null, toDTO(saved),
                "Created course " + saved.getCode());

        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody CourseCreateDTO dto) {
        return courseRepository.findById(id)
                .map(course -> {
                    CourseDTO previousState = toDTO(course);
                    String normalizedCode = dto.code != null ? dto.code.trim().toUpperCase() : null;
                    course.setCode(normalizedCode);
                    course.setName(dto.name);
                    course.setTotalWeeklyHours(dto.totalWeeklyHours);
                    course.setOnline(dto.online != null && dto.online);

                    if (dto.lecturerId != null) {
                        lecturerRepository.findById(dto.lecturerId).ifPresent(course::setLecturer);
                    } else {
                        course.setLecturer(null);
                    }

                    Set<StudentGroup> groups = resolveStudentGroups(dto);
                    if (!groups.isEmpty()) {
                        course.setStudentGroups(groups);
                        course.setStudentGroup(groups.iterator().next());
                    } else {
                        course.setStudentGroup(null);
                        course.setStudentGroups(new HashSet<>());
                    }

                    String overlapError = validateNoCourseGroupOverlap(normalizedCode, groups, course.getId());
                    if (overlapError != null) {
                        return ResponseEntity.badRequest().body(Map.of("message", overlapError));
                    }

                    if (dto.requiredFeatureIds != null) {
                        Set<Feature> features = dto.requiredFeatureIds.stream()
                                .map(featureRepository::findById)
                                .filter(java.util.Optional::isPresent)
                                .map(java.util.Optional::get)
                                .collect(Collectors.toSet());
                        course.setRequiredFeatures(features);
                    }

                    if (dto.allowedZoneIds != null) {
                        Set<Zone> zones = dto.allowedZoneIds.stream()
                                .map(zoneRepository::findById)
                                .filter(java.util.Optional::isPresent)
                                .map(java.util.Optional::get)
                                .collect(Collectors.toSet());
                        course.setAllowedZones(zones);
                    }

                    Course updated = courseRepository.save(course);

                    // Audit logging
                    auditLogService.logAction(AuditAction.UPDATE, "Course", updated.getId().toString(),
                            updated.getCode() + " - " + updated.getName(), previousState, toDTO(updated),
                            "Updated course " + updated.getCode());

                    return ResponseEntity.ok(toDTO(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private Set<StudentGroup> resolveStudentGroups(CourseCreateDTO dto) {
        Set<StudentGroup> groups = new HashSet<>();
        if (dto.studentGroupIds != null && !dto.studentGroupIds.isEmpty()) {
            groups.addAll(dto.studentGroupIds.stream()
                    .map(studentGroupRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .collect(Collectors.toSet()));
        }
        if (dto.studentGroupId != null) {
            studentGroupRepository.findById(dto.studentGroupId).ifPresent(groups::add);
        }
        return groups;
    }

    private String validateNoCourseGroupOverlap(String code, Set<StudentGroup> incomingGroups, Long currentCourseId) {
        if (code == null || code.isBlank() || incomingGroups == null || incomingGroups.isEmpty()) {
            return null;
        }

        Set<Long> incomingIds = incomingGroups.stream()
                .map(StudentGroup::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        for (Course existing : courseRepository.findByCode(code)) {
            if (currentCourseId != null && Objects.equals(existing.getId(), currentCourseId)) {
                continue;
            }

            Set<StudentGroup> existingGroups = existing.getAllStudentGroups();
            Set<String> overlapNames = existingGroups.stream()
                    .filter(group -> incomingIds.contains(group.getId()))
                    .map(StudentGroup::getName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toCollection(java.util.TreeSet::new));

            if (!overlapNames.isEmpty()) {
                return "Duplicate course-group assignment: course code '" + code + "' already has group(s) "
                        + String.join(", ", overlapNames) + " in another course entry.";
            }
        }
        return null;
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return courseRepository.findById(id)
                .map(course -> {
                    CourseDTO previousState = toDTO(course);
                    courseRepository.deleteById(id);

                    // Audit logging
                    auditLogService.logAction(AuditAction.DELETE, "Course", id.toString(),
                            course.getCode() + " - " + course.getName(), previousState, null,
                            "Deleted course " + course.getCode());

                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/generate-lessons")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<CourseDTO> generateLessons(@PathVariable Long id) {
        return courseRepository.findById(id)
                .map(course -> {
                    lessonService.generateLessons(course);
                    auditLogService.logAction(
                            AuditAction.SYSTEM_ACTION,
                            "Course",
                            String.valueOf(course.getId()),
                            course.getCode() + " - " + course.getName(),
                            null,
                            Map.of("lessonsGenerated", true),
                            "Generated lessons for course " + course.getCode());
                    return ResponseEntity.ok(toDTO(course));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    // ==================== BATCH OPERATIONS ====================

    /**
     * PATCH /api/v1/courses/batch
     * Bulk update courses (change lecturer, update hours, etc.)
     */
    @PatchMapping("/batch")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<BatchResponse> batchUpdate(@RequestBody BatchUpdateRequest request) {
        if (request.ids == null || request.ids.isEmpty()) {
            return ResponseEntity.badRequest().body(new BatchResponse(0, 0, List.of("No course IDs provided")));
        }

        int updated = 0;
        int failed = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (Long id : request.ids) {
            try {
                Course course = courseRepository.findById(id).orElse(null);
                if (course == null) {
                    failed++;
                    errors.add("Course ID " + id + " not found");
                    continue;
                }

                // Apply updates
                if (request.lecturerId != null) {
                    if (request.lecturerId == -1) {
                        course.setLecturer(null); // Clear lecturer
                    } else {
                        lecturerRepository.findById(request.lecturerId).ifPresent(course::setLecturer);
                    }
                }
                if (request.totalWeeklyHours != null) {
                    course.setTotalWeeklyHours(request.totalWeeklyHours);
                }
                if (request.online != null) {
                    course.setOnline(request.online);
                }

                courseRepository.save(course);
                updated++;

                // Audit log
                auditLogService.logAction(AuditAction.UPDATE, "Course", id.toString(),
                        course.getCode(), null, null, "Batch updated");

            } catch (Exception e) {
                failed++;
                errors.add("Course ID " + id + ": " + e.getMessage());
            }
        }

        return ResponseEntity.ok(new BatchResponse(updated, failed, errors));
    }

    /**
     * DELETE /api/v1/courses/batch
     * Bulk delete courses
     */
    @DeleteMapping("/batch")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<BatchResponse> batchDelete(@RequestBody BatchDeleteRequest request) {
        if (request.ids == null || request.ids.isEmpty()) {
            return ResponseEntity.badRequest().body(new BatchResponse(0, 0, List.of("No course IDs provided")));
        }

        int deleted = 0;
        int failed = 0;
        List<String> errors = new java.util.ArrayList<>();

        for (Long id : request.ids) {
            try {
                Course course = courseRepository.findById(id).orElse(null);
                if (course == null) {
                    failed++;
                    errors.add("Course ID " + id + " not found");
                    continue;
                }

                String code = course.getCode();
                courseRepository.deleteById(id);
                deleted++;

                // Audit log
                auditLogService.logAction(AuditAction.DELETE, "Course", id.toString(),
                        code, null, null, "Batch deleted");

            } catch (Exception e) {
                failed++;
                errors.add("Course ID " + id + ": " + e.getMessage());
            }
        }

        return ResponseEntity.ok(new BatchResponse(deleted, failed, errors));
    }

    private CourseDTO toDTO(Course course) {
        CourseDTO dto = new CourseDTO();
        dto.id = course.getId();
        dto.code = course.getCode();
        dto.name = course.getName();
        dto.totalWeeklyHours = course.getTotalWeeklyHours();
        dto.online = course.isOnline();
        dto.lecturerId = course.getLecturer() != null ? course.getLecturer().getId() : null;
        dto.lecturerName = course.getLecturer() != null ? course.getLecturer().getName() : null;

        // Legacy single group (for backward compatibility)
        dto.studentGroupId = course.getStudentGroup() != null ? course.getStudentGroup().getId() : null;
        dto.studentGroupName = course.getStudentGroup() != null ? course.getStudentGroup().getName() : null;

        // Multi-group support - combine legacy and new groups
        Set<StudentGroup> allGroups = course.getAllStudentGroups();
        dto.studentGroupIds = allGroups.stream()
                .map(StudentGroup::getId)
                .collect(Collectors.toList());
        dto.studentGroupNames = allGroups.stream()
                .map(StudentGroup::getName)
                .collect(Collectors.toList());

        dto.requiredFeatures = course.getRequiredFeatures() != null
                ? course.getRequiredFeatures().stream().map(Feature::getName).collect(Collectors.toList())
                : List.of();
        dto.requiredFeatureIds = course.getRequiredFeatures() != null
                ? course.getRequiredFeatures().stream().map(Feature::getId).collect(Collectors.toList())
                : List.of();
        dto.allowedZones = course.getAllowedZones() != null
                ? course.getAllowedZones().stream().map(Zone::getName).collect(Collectors.toList())
                : List.of();
        dto.allowedZoneIds = course.getAllowedZones() != null
                ? course.getAllowedZones().stream().map(Zone::getId).collect(Collectors.toList())
                : List.of();
        return dto;
    }

    // ==================== DTOs ====================

    public static class CourseDTO {
        public Long id;
        public String code;
        public String name;
        public Integer totalWeeklyHours;
        public Long lecturerId;
        public String lecturerName;
        public Long studentGroupId; // Legacy single group
        public String studentGroupName; // Legacy single group name
        public List<Long> studentGroupIds; // Multi-group support
        public List<String> studentGroupNames; // Multi-group names
        public List<String> requiredFeatures;
        public List<String> allowedZones;
        public List<Long> requiredFeatureIds;
        public List<Long> allowedZoneIds;
        public Boolean online;
    }

    public static class CourseCreateDTO {
        public String code;
        public String name;
        public Integer totalWeeklyHours;
        public Long lecturerId;
        public Long studentGroupId; // Legacy single group (still supported)
        public List<Long> studentGroupIds; // Multi-group support
        public List<Long> requiredFeatureIds;
        public List<Long> allowedZoneIds;
        public Boolean generateLessons;
        public Boolean online;
    }

    public static class BatchUpdateRequest {
        public List<Long> ids;
        public Long lecturerId; // null = don't change, -1 = clear
        public Integer totalWeeklyHours;
        public Boolean online;
    }

    public static class BatchDeleteRequest {
        public List<Long> ids;
    }

    public static class BatchResponse {
        public int updated;
        public int failed;
        public List<String> errors;

        public BatchResponse(int updated, int failed, List<String> errors) {
            this.updated = updated;
            this.failed = failed;
            this.errors = errors;
        }
    }
}
