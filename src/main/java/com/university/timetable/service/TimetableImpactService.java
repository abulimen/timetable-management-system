package com.university.timetable.service;

import com.university.timetable.domain.Course;
import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.SpecialEvent;
import com.university.timetable.domain.StudentGroup;
import com.university.timetable.dto.ImpactPreviewRequestDTO;
import com.university.timetable.dto.ImpactPreviewResponseDTO;
import com.university.timetable.repository.CourseRepository;
import com.university.timetable.repository.LessonRepository;
import com.university.timetable.repository.SpecialEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class TimetableImpactService {

    private final LessonRepository lessonRepository;
    private final CourseRepository courseRepository;
    private final SpecialEventRepository specialEventRepository;

    @Value("${solver.scoped.max-impact-ratio:0.25}")
    private double maxImpactRatio;

    public ImpactPreviewResponseDTO preview(ImpactPreviewRequestDTO request) {
        if (request == null || request.getChangeType() == null || request.getChangeType().isBlank()) {
            throw new IllegalArgumentException("changeType is required");
        }

        String changeType = request.getChangeType().trim().toUpperCase();
        return switch (changeType) {
            case "COURSE_CREATE" -> {
                requireEntityId(request);
                yield previewForCourseCreate(request.getEntityId());
            }
            case "COURSE_CANCEL" -> {
                requireEntityId(request);
                yield previewForCourseCancel(request.getEntityId());
            }
            case "LECTURER_REASSIGN" -> {
                requireEntityId(request);
                yield previewForLecturerReassign(request.getEntityId(), request.getOptions());
            }
            case "SPECIAL_EVENT_UPSERT" -> previewForSpecialEventUpsert(request);
            case "MANUAL" -> previewForManual(request.getOptions() != null ? request.getOptions().getImpactedLessonIds() : null);
            default -> throw new IllegalArgumentException("Unsupported changeType: " + changeType);
        };
    }

    public ImpactPreviewResponseDTO previewForCourseCreate(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));

        Set<Long> impacted = new LinkedHashSet<>();
        impacted.addAll(lessonRepository.findByCourse(course).stream()
                .map(Lesson::getId)
                .filter(Objects::nonNull)
                .toList());

        if (course.getLecturer() != null) {
            impacted.addAll(lessonRepository.findByLecturer(course.getLecturer()).stream()
                    .map(Lesson::getId)
                    .filter(Objects::nonNull)
                    .toList());
        }

        Set<StudentGroup> courseGroups = course.getAllStudentGroups();
        if (!courseGroups.isEmpty()) {
            impacted.addAll(findLessonsTouchingGroups(courseGroups).stream()
                    .map(Lesson::getId)
                    .filter(Objects::nonNull)
                    .toList());
        }

        return buildPreview(impacted, List.of());
    }

    public ImpactPreviewResponseDTO previewForCourseCancel(Long courseId) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));

        Set<Long> impacted = new LinkedHashSet<>();
        if (course.getLecturer() != null) {
            impacted.addAll(lessonRepository.findByLecturer(course.getLecturer()).stream()
                    .map(Lesson::getId)
                    .filter(Objects::nonNull)
                    .toList());
        }
        Set<StudentGroup> courseGroups = course.getAllStudentGroups();
        if (!courseGroups.isEmpty()) {
            impacted.addAll(findLessonsTouchingGroups(courseGroups).stream()
                    .map(Lesson::getId)
                    .filter(Objects::nonNull)
                    .toList());
        }
        impacted.addAll(lessonRepository.findByCourse(course).stream()
                .map(Lesson::getId)
                .filter(Objects::nonNull)
                .toList());

        return buildPreview(impacted, List.of("Course cancellation removes selected course lessons from active timetable."));
    }

    public ImpactPreviewResponseDTO previewForLecturerReassign(Long courseId, ImpactPreviewRequestDTO.ImpactOptionsDTO options) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new IllegalArgumentException("Course not found: " + courseId));

        Set<Long> impacted = new LinkedHashSet<>();
        impacted.addAll(lessonRepository.findByCourse(course).stream()
                .map(Lesson::getId)
                .filter(Objects::nonNull)
                .toList());

        if (course.getLecturer() != null) {
            impacted.addAll(lessonRepository.findByLecturer(course.getLecturer()).stream()
                    .map(Lesson::getId)
                    .filter(Objects::nonNull)
                    .toList());
        }

        if (options != null && options.getLecturerId() != null) {
            Long lecturerId = options.getLecturerId();
            impacted.addAll(lessonRepository.findAll().stream()
                    .filter(lesson -> lesson.getLecturer() != null && Objects.equals(lesson.getLecturer().getId(), lecturerId))
                    .map(Lesson::getId)
                    .filter(Objects::nonNull)
                    .toList());
        }

        return buildPreview(impacted, List.of("Course-wide lecturer reassignment selected."));
    }

    public ImpactPreviewResponseDTO previewForSpecialEvent(SpecialEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("Special event is required");
        }
        Set<Long> impacted = detectSpecialEventConflicts(event).stream()
                .map(Lesson::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return buildPreview(impacted, List.of());
    }

    private ImpactPreviewResponseDTO previewForSpecialEventUpsert(ImpactPreviewRequestDTO request) {
        if (request.getEntityId() != null) {
            SpecialEvent event = specialEventRepository.findById(request.getEntityId())
                    .orElseThrow(() -> new IllegalArgumentException("Special event not found: " + request.getEntityId()));
            return previewForSpecialEvent(event);
        }

        ImpactPreviewRequestDTO.ImpactOptionsDTO options = request.getOptions();
        if (options == null) {
            throw new IllegalArgumentException("options is required for SPECIAL_EVENT_UPSERT without entityId");
        }
        DayOfWeek dayOfWeek = DayOfWeek.valueOf(requireValue(options.getDayOfWeek(), "options.dayOfWeek").trim().toUpperCase());
        LocalTime start = LocalTime.parse(requireValue(options.getStartTime(), "options.startTime"));
        int durationHours = options.getDurationHours() != null ? options.getDurationHours() : 1;
        LocalTime end = start.plusHours(durationHours);
        Long roomId = options.getRoomId();
        Long lecturerId = options.getLecturerId();
        Set<Long> groupIds = options.getStudentGroupIds() != null
                ? new HashSet<>(options.getStudentGroupIds())
                : Collections.emptySet();

        Set<Long> impacted = lessonRepository.findAll().stream()
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getTimeslot().getDayOfWeek() == dayOfWeek)
                .filter(lesson -> {
                    LocalTime lessonStart = lesson.getTimeslot().getStartTime();
                    LocalTime lessonEnd = lessonStart.plusHours(lesson.getDurationHours());
                    return lessonStart.isBefore(end) && lessonEnd.isAfter(start);
                })
                .filter(lesson -> hasSpecialEventConflict(lesson, roomId, lecturerId, groupIds))
                .map(Lesson::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return buildPreview(impacted, List.of());
    }

    private ImpactPreviewResponseDTO previewForManual(List<Long> impactedLessonIds) {
        Set<Long> impacted = impactedLessonIds == null
                ? new LinkedHashSet<>()
                : impactedLessonIds.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
        return buildPreview(impacted, List.of());
    }

    private ImpactPreviewResponseDTO buildPreview(Set<Long> impacted, List<String> extraWarnings) {
        List<Lesson> allLessons = lessonRepository.findAll();
        Set<Long> allIds = allLessons.stream()
                .map(Lesson::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<Long> normalizedImpacted = impacted.stream()
                .filter(allIds::contains)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<Long> locked = new LinkedHashSet<>(allIds);
        locked.removeAll(normalizedImpacted);

        Map<Long, Lesson> byId = allLessons.stream()
                .filter(lesson -> lesson.getId() != null)
                .collect(Collectors.toMap(Lesson::getId, lesson -> lesson));
        List<Lesson> impactedLessons = normalizedImpacted.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing((Lesson lesson) -> lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek().getValue() : 9)
                        .thenComparing(lesson -> lesson.getTimeslot() != null ? lesson.getTimeslot().getStartTime() : LocalTime.MAX)
                        .thenComparing(lesson -> lesson.getCourse() != null ? lesson.getCourse().getCode() : ""))
                .toList();

        ImpactPreviewResponseDTO response = new ImpactPreviewResponseDTO();
        response.setImpactedLessonIds(new ArrayList<>(normalizedImpacted));
        response.setLockedLessonIds(new ArrayList<>(locked));
        response.setImpactedLessons(impactedLessons.stream().map(this::toImpactLesson).toList());

        ImpactPreviewResponseDTO.ImpactSummaryDTO summary = new ImpactPreviewResponseDTO.ImpactSummaryDTO();
        summary.setTotalLessons(allIds.size());
        summary.setImpactedCount(normalizedImpacted.size());
        summary.setLockedCount(locked.size());
        summary.setByDay(new LinkedHashMap<>());
        summary.setByLecturer(new LinkedHashMap<>());
        summary.setByGroup(new LinkedHashMap<>());
        summary.setByRoom(new LinkedHashMap<>());

        for (Lesson lesson : impactedLessons) {
            String day = lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek().name() : "UNSCHEDULED";
            summary.getByDay().merge(day, 1, Integer::sum);
            String lecturer = lesson.getLecturer() != null ? lesson.getLecturer().getName() : "No Lecturer";
            summary.getByLecturer().merge(lecturer, 1, Integer::sum);
            String room = lesson.getRoom() != null ? lesson.getRoom().getName() : "No Room";
            summary.getByRoom().merge(room, 1, Integer::sum);
            for (String groupName : expandLessonGroupNames(lesson)) {
                summary.getByGroup().merge(groupName, 1, Integer::sum);
            }
        }
        response.setSummary(summary);

        List<String> warnings = new ArrayList<>();
        if (normalizedImpacted.isEmpty()) {
            warnings.add("No impacted lessons detected.");
        }
        if (!allIds.isEmpty()) {
            double ratio = (double) normalizedImpacted.size() / (double) allIds.size();
            if (ratio > maxImpactRatio) {
                warnings.add(String.format(
                        "Impacted scope is large: %d of %d lessons (%.1f%%). Consider full replan or explicit large-scope override.",
                        normalizedImpacted.size(), allIds.size(), ratio * 100.0));
            }
        }
        if (extraWarnings != null) {
            warnings.addAll(extraWarnings);
        }
        response.setWarnings(warnings);
        return response;
    }

    private List<Lesson> detectSpecialEventConflicts(SpecialEvent event) {
        if (event.getDayOfWeek() == null || event.getStartTime() == null || event.getDurationHours() <= 0) {
            return List.of();
        }
        LocalTime eventStart = event.getStartTime();
        LocalTime eventEnd = event.getEndTime();
        Set<StudentGroup> eventGroups = event.getStudentGroups() != null ? event.getStudentGroups() : Set.of();

        return lessonRepository.findAll().stream()
                .filter(lesson -> lesson.getTimeslot() != null && lesson.getTimeslot().getDayOfWeek() == event.getDayOfWeek())
                .filter(lesson -> {
                    LocalTime lessonStart = lesson.getTimeslot().getStartTime();
                    LocalTime lessonEnd = lessonStart.plusHours(lesson.getDurationHours());
                    return lessonStart.isBefore(eventEnd) && lessonEnd.isAfter(eventStart);
                })
                .filter(lesson -> {
                    boolean roomConflict = event.getRoom() != null && lesson.getRoom() != null
                            && Objects.equals(event.getRoom().getId(), lesson.getRoom().getId());
                    boolean lecturerConflict = event.getLecturer() != null && lesson.getLecturer() != null
                            && Objects.equals(event.getLecturer().getId(), lesson.getLecturer().getId());
                    boolean groupConflict = lesson.getStudentGroups().stream()
                            .anyMatch(lessonGroup -> eventGroups.stream().anyMatch(lessonGroup::hasConflictWith));
                    return roomConflict || lecturerConflict || groupConflict;
                })
                .toList();
    }

    private boolean hasSpecialEventConflict(Lesson lesson, Long roomId, Long lecturerId, Set<Long> eventGroupIds) {
        boolean roomConflict = roomId != null && lesson.getRoom() != null && Objects.equals(lesson.getRoom().getId(), roomId);
        boolean lecturerConflict = lecturerId != null && lesson.getLecturer() != null && Objects.equals(lesson.getLecturer().getId(), lecturerId);
        boolean groupConflict = false;
        if (eventGroupIds != null && !eventGroupIds.isEmpty()) {
            Set<Long> lessonGroupIds = lesson.getStudentGroups().stream()
                    .map(StudentGroup::getId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            groupConflict = !Collections.disjoint(lessonGroupIds, eventGroupIds);
        }
        return roomConflict || lecturerConflict || groupConflict;
    }

    private Collection<Lesson> findLessonsTouchingGroups(Set<StudentGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return List.of();
        }
        return lessonRepository.findAll().stream()
                .filter(lesson -> lesson.getCourse() != null)
                .filter(lesson -> {
                    Set<StudentGroup> lessonGroups = lesson.getCourse().getAllStudentGroups();
                    for (StudentGroup lessonGroup : lessonGroups) {
                        for (StudentGroup requestedGroup : groups) {
                            if (lessonGroup != null && requestedGroup != null && lessonGroup.hasConflictWith(requestedGroup)) {
                                return true;
                            }
                        }
                    }
                    return false;
                })
                .toList();
    }

    private ImpactPreviewResponseDTO.ImpactLessonDTO toImpactLesson(Lesson lesson) {
        ImpactPreviewResponseDTO.ImpactLessonDTO dto = new ImpactPreviewResponseDTO.ImpactLessonDTO();
        dto.setLessonId(lesson.getId());
        dto.setCourseCode(lesson.getCourse() != null ? lesson.getCourse().getCode() : "");
        dto.setCourseName(lesson.getCourse() != null ? lesson.getCourse().getName() : "");
        dto.setDayOfWeek(lesson.getTimeslot() != null ? lesson.getTimeslot().getDayOfWeek().name() : "UNSCHEDULED");
        dto.setStartTime(lesson.getTimeslot() != null ? lesson.getTimeslot().getStartTime().toString() : null);
        dto.setEndTime(lesson.getTimeslot() != null ? lesson.getTimeslot().getStartTime().plusHours(lesson.getDurationHours()).toString() : null);
        dto.setLecturerName(lesson.getLecturer() != null ? lesson.getLecturer().getName() : "No Lecturer");
        dto.setRoomName(lesson.getRoom() != null ? lesson.getRoom().getName() : "No Room");
        dto.setGroupNames(expandLessonGroupNames(lesson));
        return dto;
    }

    private List<String> expandLessonGroupNames(Lesson lesson) {
        if (lesson.getCourse() == null) {
            return List.of();
        }
        Set<String> names = new LinkedHashSet<>();
        for (StudentGroup group : lesson.getCourse().getAllStudentGroups()) {
            if (group == null) {
                continue;
            }
            if (group.getParentGroup() != null) {
                names.add(group.getName());
                continue;
            }
            if (group.getChildren() != null && !group.getChildren().isEmpty()) {
                group.getChildren().stream()
                        .filter(Objects::nonNull)
                        .map(StudentGroup::getName)
                        .filter(Objects::nonNull)
                        .forEach(names::add);
            } else {
                names.add(group.getName());
            }
        }
        return names.stream().sorted().toList();
    }

    private void requireEntityId(ImpactPreviewRequestDTO request) {
        if (request.getEntityId() == null) {
            throw new IllegalArgumentException("entityId is required for " + request.getChangeType());
        }
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }
}
