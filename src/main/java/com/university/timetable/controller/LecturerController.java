package com.university.timetable.controller;

import com.university.timetable.domain.*;
import com.university.timetable.repository.CourseRepository;
import com.university.timetable.repository.LecturerRepository;
import com.university.timetable.repository.LessonRepository;
import com.university.timetable.repository.UserRepository;
import com.university.timetable.service.AuditLogService;
import com.university.timetable.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/lecturers")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LecturerController {

    private final LecturerRepository lecturerRepository;
    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final UserRepository userRepository;
    private final AuthService authService;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public List<LecturerDTO> getAll() {
        return lecturerRepository.findAll().stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<LecturerDTO> getById(@PathVariable Long id) {
        return lecturerRepository.findById(id)
                .map(l -> ResponseEntity.ok(toDTO(l)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get the current user's lecturer profile.
     * Returns 404 if the user is not linked to a lecturer.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyProfile(Authentication authentication) {
        User currentUser = authService.getCurrentUser(authentication.getName());
        return lecturerRepository.findByUser(currentUser)
                .map(l -> ResponseEntity.ok(toDTO(l)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get courses taught by the current lecturer.
     */
    @GetMapping("/me/courses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyCourses(Authentication authentication) {
        User currentUser = authService.getCurrentUser(authentication.getName());
        return lecturerRepository.findByUser(currentUser)
                .map(lecturer -> {
                    List<Map<String, Object>> courses = courseRepository.findByLecturer(lecturer).stream()
                            .map(c -> {
                                Map<String, Object> courseMap = new HashMap<>();
                                courseMap.put("id", c.getId());
                                courseMap.put("code", c.getCode());
                                courseMap.put("name", c.getName());
                                courseMap.put("weeklyHours", c.getTotalWeeklyHours());
                                courseMap.put("studentGroups", c.getStudentGroups().stream()
                                        .map(sg -> sg.getName())
                                        .collect(Collectors.toList()));
                                return courseMap;
                            })
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(courses);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get scheduled lessons for the current lecturer.
     */
    @GetMapping("/me/lessons")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<?> getMyLessons(Authentication authentication) {
        User currentUser = authService.getCurrentUser(authentication.getName());
        return lecturerRepository.findByUser(currentUser)
                .map(lecturer -> {
                    List<Map<String, Object>> lessons = lessonRepository.findByLecturer(lecturer).stream()
                            .filter(l -> l.getTimeslot() != null && l.getRoom() != null)
                            .map(l -> {
                                Map<String, Object> lessonMap = new HashMap<>();
                                lessonMap.put("id", l.getId());
                                lessonMap.put("courseCode", l.getCourse().getCode());
                                lessonMap.put("courseName", l.getCourse().getName());
                                lessonMap.put("day", l.getTimeslot().getDayOfWeek().toString());
                                lessonMap.put("startTime", l.getTimeslot().getStartTime().toString());
                                lessonMap.put("endTime",
                                        l.getTimeslot().getStartTime().plusHours(l.getDurationHours()).toString());
                                lessonMap.put("room", l.getRoom().getName());
                                lessonMap.put("duration", l.getDurationHours());
                                return lessonMap;
                            })
                            .collect(Collectors.toList());
                    return ResponseEntity.ok(lessons);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get all lecturer unavailabilities (for admin view).
     */
    @GetMapping("/unavailabilities")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public List<Map<String, Object>> getAllUnavailabilities() {
        return lecturerRepository.findAll().stream()
                .flatMap(lecturer -> lecturer.getUnavailabilities().stream()
                        .map(u -> {
                            Map<String, Object> map = new HashMap<>();
                            map.put("id", u.getId());
                            map.put("lecturerId", lecturer.getId());
                            map.put("lecturerName", lecturer.getName());
                            map.put("lecturerEmail", lecturer.getEmail());
                            map.put("dayOfWeek", u.getDayOfWeek().toString());
                            map.put("startTime", u.getStartTime().toString());
                            map.put("endTime", u.getEndTime().toString());
                            return map;
                        }))
                .collect(Collectors.toList());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public LecturerDTO create(@RequestBody LecturerCreateDTO dto) {
        Lecturer lecturer = new Lecturer();
        lecturer.setName(dto.name);
        lecturer.setEmail(dto.email);
        Lecturer saved = lecturerRepository.save(lecturer);

        auditLogService.logAction(AuditAction.CREATE, "Lecturer", saved.getId().toString(),
                saved.getName(), null, toDTO(saved), "Created lecturer " + saved.getName());

        return toDTO(saved);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<LecturerDTO> update(@PathVariable Long id, @RequestBody LecturerCreateDTO dto) {
        return lecturerRepository.findById(id)
                .map(lecturer -> {
                    LecturerDTO previousState = toDTO(lecturer);
                    lecturer.setName(dto.name);
                    lecturer.setEmail(dto.email);
                    Lecturer updated = lecturerRepository.save(lecturer);

                    auditLogService.logAction(AuditAction.UPDATE, "Lecturer", updated.getId().toString(),
                            updated.getName(), previousState, toDTO(updated), "Updated lecturer " + updated.getName());

                    return ResponseEntity.ok(toDTO(updated));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        return lecturerRepository.findById(id)
                .map(lecturer -> {
                    LecturerDTO previousState = toDTO(lecturer);

                    // Unlink from user if linked
                    if (lecturer.getUser() != null) {
                        User linkedUser = lecturer.getUser();
                        linkedUser.setLecturer(null);
                        userRepository.save(linkedUser);
                    }

                    lecturerRepository.deleteById(id);

                    auditLogService.logAction(AuditAction.DELETE, "Lecturer", id.toString(),
                            lecturer.getName(), previousState, null, "Deleted lecturer " + lecturer.getName());

                    return ResponseEntity.noContent().<Void>build();
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/unavailability")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<LecturerDTO> addUnavailability(@PathVariable Long id, @RequestBody UnavailabilityDTO dto) {
        return lecturerRepository.findById(id)
                .map(lecturer -> {
                    LecturerUnavailability unavailability = new LecturerUnavailability();
                    unavailability.setLecturer(lecturer);
                    unavailability.setDayOfWeek(DayOfWeek.valueOf(dto.dayOfWeek.toUpperCase()));
                    unavailability.setStartTime(LocalTime.parse(dto.startTime));
                    unavailability.setEndTime(LocalTime.parse(dto.endTime));
                    lecturer.getUnavailabilities().add(unavailability);
                    return ResponseEntity.ok(toDTO(lecturerRepository.save(lecturer)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}/unavailability/{unavailabilityId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<LecturerDTO> removeUnavailability(@PathVariable Long id,
            @PathVariable Long unavailabilityId) {
        return lecturerRepository.findById(id)
                .map(lecturer -> {
                    lecturer.getUnavailabilities().removeIf(u -> u.getId().equals(unavailabilityId));
                    return ResponseEntity.ok(toDTO(lecturerRepository.save(lecturer)));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    private LecturerDTO toDTO(Lecturer lecturer) {
        LecturerDTO dto = new LecturerDTO();
        dto.id = lecturer.getId();
        dto.name = lecturer.getName();
        dto.email = lecturer.getEmail();
        dto.unavailabilities = lecturer.getUnavailabilities() != null
                ? lecturer.getUnavailabilities().stream().map(u -> {
                    UnavailabilityDTO uDto = new UnavailabilityDTO();
                    uDto.id = u.getId();
                    uDto.dayOfWeek = u.getDayOfWeek().toString();
                    uDto.startTime = u.getStartTime().toString();
                    uDto.endTime = u.getEndTime().toString();
                    return uDto;
                }).collect(Collectors.toList())
                : List.of();
        return dto;
    }

    public static class LecturerDTO {
        public Long id;
        public String name;
        public String email;
        public List<UnavailabilityDTO> unavailabilities;
    }

    public static class LecturerCreateDTO {
        public String name;
        public String email;
    }

    public static class UnavailabilityDTO {
        public Long id;
        public String dayOfWeek;
        public String startTime;
        public String endTime;
    }
}
