package com.university.timetable.controller;

import com.university.timetable.domain.*;
import com.university.timetable.repository.*;
import com.university.timetable.service.LessonService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
    public ResponseEntity<CourseDTO> create(@RequestBody CourseCreateDTO dto) {
        Course course = new Course();
        course.setCode(dto.code);
        course.setName(dto.name);
        course.setTotalWeeklyHours(dto.totalWeeklyHours);
        course.setOnline(dto.online != null && dto.online);

        if (dto.lecturerId != null) {
            lecturerRepository.findById(dto.lecturerId).ifPresent(course::setLecturer);
        }

        // Handle multi-group (preferred) OR single group (legacy)
        if (dto.studentGroupIds != null && !dto.studentGroupIds.isEmpty()) {
            Set<StudentGroup> groups = dto.studentGroupIds.stream()
                    .map(studentGroupRepository::findById)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .collect(Collectors.toSet());
            course.setStudentGroups(groups);
            // Also set legacy field to first group for compatibility
            if (!groups.isEmpty()) {
                course.setStudentGroup(groups.iterator().next());
            }
        } else if (dto.studentGroupId != null) {
            studentGroupRepository.findById(dto.studentGroupId).ifPresent(course::setStudentGroup);
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

        return ResponseEntity.ok(toDTO(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<CourseDTO> update(@PathVariable Long id, @RequestBody CourseCreateDTO dto) {
        return courseRepository.findById(id)
                .map(course -> {
                    course.setCode(dto.code);
                    course.setName(dto.name);
                    course.setTotalWeeklyHours(dto.totalWeeklyHours);
                    course.setOnline(dto.online != null && dto.online);

                    if (dto.lecturerId != null) {
                        lecturerRepository.findById(dto.lecturerId).ifPresent(course::setLecturer);
                    }

                    // Handle multi-group (preferred) OR single group (legacy)
                    if (dto.studentGroupIds != null && !dto.studentGroupIds.isEmpty()) {
                        Set<StudentGroup> groups = dto.studentGroupIds.stream()
                                .map(studentGroupRepository::findById)
                                .filter(java.util.Optional::isPresent)
                                .map(java.util.Optional::get)
                                .collect(Collectors.toSet());
                        course.setStudentGroups(groups);
                        // Also set legacy field to first group for compatibility
                        if (!groups.isEmpty()) {
                            course.setStudentGroup(groups.iterator().next());
                        }
                    } else if (dto.studentGroupId != null) {
                        studentGroupRepository.findById(dto.studentGroupId).ifPresent(course::setStudentGroup);
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

                    return ResponseEntity.ok(toDTO(courseRepository.save(course)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!courseRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        courseRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/generate-lessons")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<CourseDTO> generateLessons(@PathVariable Long id) {
        return courseRepository.findById(id)
                .map(course -> {
                    lessonService.generateLessons(course);
                    return ResponseEntity.ok(toDTO(course));
                })
                .orElse(ResponseEntity.notFound().build());
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
        dto.allowedZones = course.getAllowedZones() != null
                ? course.getAllowedZones().stream().map(Zone::getName).collect(Collectors.toList())
                : List.of();
        return dto;
    }

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
}
