package com.university.timetable.service;

import com.university.timetable.domain.*;
import com.university.timetable.dto.*;
import com.university.timetable.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CP-SAT based diagnostics service that analyzes the current timetable
 * and reports constraint violations in plain, user-friendly English.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CpSatDiagnosticsService {

    private final LessonRepository lessonRepository;
    private final TimeslotRepository timeslotRepository;
    private final RoomRepository roomRepository;
    private final SpecialEventRepository specialEventRepository;
    private final ConstraintSettingsService constraintSettingsService;

    /**
     * Analyze the current timetable and return a detailed diagnostics report
     * with plain English descriptions of any problems found.
     */
    @Transactional
    public DiagnosticsReportDTO analyzeTimetable() {
        log.info("CP-SAT Diagnostics: Starting timetable analysis...");

        List<Lesson> lessons = lessonRepository.findAllWithCourseAndLecturer();
        List<Timeslot> timeslots = timeslotRepository.findAll();
        List<Room> rooms = roomRepository.findAllWithFeatures();
        List<SpecialEvent> specialEvents = specialEventRepository.findByActiveTrue();

        DiagnosticsReportDTO report = new DiagnosticsReportDTO();
        report.setTotalLessons(lessons.size());
        report.setScheduledLessons((int) lessons.stream().filter(l -> l.getTimeslot() != null).count());
        report.setUnscheduledLessons((int) lessons.stream().filter(l -> l.getTimeslot() == null).count());

        List<DiagnosticsIssueDTO> issues = new ArrayList<>();

        // Run all diagnostic checks
        checkRoomConflicts(lessons, issues);
        checkLecturerConflicts(lessons, issues);
        checkStudentGroupConflicts(lessons, issues);
        checkRoomCapacity(lessons, issues);
        checkRoomFeatures(lessons, rooms, issues);
        checkZoneRestrictions(lessons, issues);
        checkLecturerUnavailability(lessons, issues);
        checkLunchBreakViolations(lessons, issues);
        checkSpecialEventConflicts(lessons, specialEvents, issues);
        checkEndTimeViolations(lessons, issues);

        // Sort by severity (BLOCKING first) then by count
        issues.sort((a, b) -> {
            int severityCompare = Boolean.compare("BLOCKING".equals(b.getSeverity()), "BLOCKING".equals(a.getSeverity()));
            if (severityCompare != 0) return severityCompare;
            return Integer.compare(b.getAffectedCount(), a.getAffectedCount());
        });

        report.setIssues(issues);
        report.setTotalIssues(issues.size());
        report.setBlockingIssues((int) issues.stream().filter(i -> "BLOCKING".equals(i.getSeverity())).count());
        report.setWarningIssues((int) issues.stream().filter(i -> "WARNING".equals(i.getSeverity())).count());

        log.info("CP-SAT Diagnostics: Found {} issues ({} blocking, {} warnings)",
                issues.size(), report.getBlockingIssues(), report.getWarningIssues());

        return report;
    }

    /**
     * Check for room conflicts - two lessons in the same room at overlapping times.
     */
    private void checkRoomConflicts(List<Lesson> lessons, List<DiagnosticsIssueDTO> issues) {
        Map<Long, List<Lesson>> lessonsByRoom = lessons.stream()
                .filter(l -> l.getRoom() != null && l.getTimeslot() != null)
                .collect(Collectors.groupingBy(l -> l.getRoom().getId()));

        int conflictCount = 0;
        List<String> examples = new ArrayList<>();

        for (Map.Entry<Long, List<Lesson>> entry : lessonsByRoom.entrySet()) {
            List<Lesson> roomLessons = entry.getValue();
            roomLessons.sort(Comparator.comparing(l -> l.getTimeslot().getStartTime()));

            for (int i = 0; i < roomLessons.size(); i++) {
                Lesson a = roomLessons.get(i);
                for (int j = i + 1; j < roomLessons.size(); j++) {
                    Lesson b = roomLessons.get(j);

                    if (a.getTimeslot().getDayOfWeek() != b.getTimeslot().getDayOfWeek()) continue;

                    LocalTime aStart = a.getTimeslot().getStartTime();
                    LocalTime aEnd = aStart.plusHours(a.getDurationHours());
                    LocalTime bStart = b.getTimeslot().getStartTime();
                    LocalTime bEnd = bStart.plusHours(b.getDurationHours());

                    if (aStart.isBefore(bEnd) && bStart.isBefore(aEnd)) {
                        conflictCount++;
                        if (examples.size() < 3) {
                            examples.add(String.format(
                                    "On %s, room '%s' has '%s' at %s and '%s' at %s overlapping each other",
                                    a.getTimeslot().getDayOfWeek(),
                                    a.getRoom().getName(),
                                    a.getDisplayName(),
                                    aStart,
                                    b.getDisplayName(),
                                    bStart));
                        }
                    }
                }
            }
        }

        if (conflictCount > 0) {
            issues.add(DiagnosticsIssueDTO.blocking(
                    "ROOM_DOUBLE_BOOKED",
                    "Some rooms have been assigned to two different classes at the same time",
                    String.format("Found %d instances where a room is double-booked. For example: %s.",
                            conflictCount, String.join("; ", examples)),
                    "Move one of the conflicting lessons to a different time slot or assign it to a different room."
            ));
        }
    }

    /**
     * Check for lecturer conflicts - a lecturer teaching two classes at once.
     */
    private void checkLecturerConflicts(List<Lesson> lessons, List<DiagnosticsIssueDTO> issues) {
        Map<Long, List<Lesson>> lessonsByLecturer = lessons.stream()
                .filter(l -> l.getLecturer() != null && l.getTimeslot() != null)
                .collect(Collectors.groupingBy(l -> l.getLecturer().getId()));

        int conflictCount = 0;
        List<String> examples = new ArrayList<>();

        for (Map.Entry<Long, List<Lesson>> entry : lessonsByLecturer.entrySet()) {
            List<Lesson> lecturerLessons = entry.getValue();
            String lecturerName = lecturerLessons.get(0).getLecturer().getName();

            for (int i = 0; i < lecturerLessons.size(); i++) {
                Lesson a = lecturerLessons.get(i);
                for (int j = i + 1; j < lecturerLessons.size(); j++) {
                    Lesson b = lecturerLessons.get(j);

                    if (a.getTimeslot().getDayOfWeek() != b.getTimeslot().getDayOfWeek()) continue;

                    LocalTime aStart = a.getTimeslot().getStartTime();
                    LocalTime aEnd = aStart.plusHours(a.getDurationHours());
                    LocalTime bStart = b.getTimeslot().getStartTime();
                    LocalTime bEnd = bStart.plusHours(b.getDurationHours());

                    if (aStart.isBefore(bEnd) && bStart.isBefore(aEnd)) {
                        conflictCount++;
                        if (examples.size() < 3) {
                            examples.add(String.format(
                                    "%s is scheduled to teach both '%s' and '%s' on %s at %s",
                                    lecturerName, a.getDisplayName(), b.getDisplayName(),
                                    a.getTimeslot().getDayOfWeek(), aStart));
                        }
                    }
                }
            }
        }

        if (conflictCount > 0) {
            issues.add(DiagnosticsIssueDTO.blocking(
                    "LECTURER_DOUBLE_BOOKED",
                    "Some lecturers are scheduled to teach two different classes at the same time",
                    String.format("Found %d instances where a lecturer has overlapping classes. For example: %s.",
                            conflictCount, String.join("; ", examples)),
                    "Reschedule one of the conflicting classes to a different time, or assign a different lecturer."
            ));
        }
    }

    /**
     * Check for student group conflicts - students expected in two places at once.
     */
    private void checkStudentGroupConflicts(List<Lesson> lessons, List<DiagnosticsIssueDTO> issues) {
        Map<Long, List<Lesson>> lessonsByGroup = new HashMap<>();

        for (Lesson lesson : lessons) {
            if (lesson.getTimeslot() == null) continue;
            for (StudentGroup group : lesson.getStudentGroups()) {
                lessonsByGroup.computeIfAbsent(group.getId(), k -> new ArrayList<>()).add(lesson);
            }
        }

        int conflictCount = 0;
        List<String> examples = new ArrayList<>();

        for (Map.Entry<Long, List<Lesson>> entry : lessonsByGroup.entrySet()) {
            List<Lesson> groupLessons = entry.getValue();
            String groupName = groupLessons.get(0).getStudentGroups().stream()
                    .filter(g -> g.getId().equals(entry.getKey()))
                    .findFirst().map(StudentGroup::getName).orElse("Unknown Group");

            for (int i = 0; i < groupLessons.size(); i++) {
                Lesson a = groupLessons.get(i);
                for (int j = i + 1; j < groupLessons.size(); j++) {
                    Lesson b = groupLessons.get(j);

                    if (a.getTimeslot().getDayOfWeek() != b.getTimeslot().getDayOfWeek()) continue;

                    LocalTime aStart = a.getTimeslot().getStartTime();
                    LocalTime aEnd = aStart.plusHours(a.getDurationHours());
                    LocalTime bStart = b.getTimeslot().getStartTime();
                    LocalTime bEnd = bStart.plusHours(b.getDurationHours());

                    if (aStart.isBefore(bEnd) && bStart.isBefore(aEnd)) {
                        conflictCount++;
                        if (examples.size() < 3) {
                            examples.add(String.format(
                                    "Students in '%s' have both '%s' and '%s' on %s at %s",
                                    groupName, a.getDisplayName(), b.getDisplayName(),
                                    a.getTimeslot().getDayOfWeek(), aStart));
                        }
                    }
                }
            }
        }

        if (conflictCount > 0) {
            issues.add(DiagnosticsIssueDTO.blocking(
                    "STUDENT_GROUP_CONFLICT",
                    "Some student groups are scheduled for two different classes at the same time",
                    String.format("Found %d instances where students would need to be in two places at once. For example: %s.",
                            conflictCount, String.join("; ", examples)),
                    "Reschedule one of the conflicting classes so students can attend both."
            ));
        }
    }

    /**
     * Check if rooms have enough capacity for assigned lessons.
     */
    private void checkRoomCapacity(List<Lesson> lessons, List<DiagnosticsIssueDTO> issues) {
        List<String> examples = new ArrayList<>();

        for (Lesson lesson : lessons) {
            if (lesson.getRoom() == null || lesson.isOnline()) continue;

            int students = lesson.getTotalStudentCount();
            int capacity = lesson.getRoom().getCapacity();

            if (students > capacity) {
                examples.add(String.format(
                        "'%s' has %d students but is assigned to room '%s' which only holds %d people",
                        lesson.getDisplayName(), students, lesson.getRoom().getName(), capacity));
            }
        }

        if (!examples.isEmpty()) {
            issues.add(DiagnosticsIssueDTO.blocking(
                    "ROOM_TOO_SMALL",
                    "Some classes have more students than their assigned room can hold",
                    String.format("Found %d classes where the room is too small for the number of students. For example: %s.",
                            examples.size(), String.join("; ", examples.subList(0, Math.min(3, examples.size())))),
                    "Move these classes to larger rooms, or split the class into smaller groups."
            ));
        }
    }

    /**
     * Check if rooms have the required features for their assigned lessons.
     */
    private void checkRoomFeatures(List<Lesson> lessons, List<Room> rooms, List<DiagnosticsIssueDTO> issues) {
        List<String> examples = new ArrayList<>();

        for (Lesson lesson : lessons) {
            if (lesson.getRoom() == null || lesson.getCourse() == null || lesson.isOnline()) continue;

            Set<Feature> required = lesson.getCourse().getRequiredFeatures();
            if (required == null || required.isEmpty()) continue;

            Set<Feature> missing = new HashSet<>(required);
            missing.removeAll(lesson.getRoom().getFeatures());

            if (!missing.isEmpty()) {
                String missingNames = missing.stream().map(Feature::getName).collect(Collectors.joining(", "));
                examples.add(String.format(
                        "'%s' requires %s but room '%s' does not have %s",
                        lesson.getDisplayName(), missingNames, lesson.getRoom().getName(),
                        missing.size() == 1 ? "this feature" : "these features"));
            }
        }

        if (!examples.isEmpty()) {
            issues.add(DiagnosticsIssueDTO.blocking(
                    "ROOM_MISSING_FEATURES",
                    "Some classes need special room features that their assigned room doesn't have",
                    String.format("Found %d classes where the room lacks required features. For example: %s.",
                            examples.size(), String.join("; ", examples.subList(0, Math.min(3, examples.size())))),
                    "Move these classes to rooms that have the required features (like labs, projectors, etc.)."
            ));
        }
    }

    /**
     * Check if lessons are in allowed zones.
     */
    private void checkZoneRestrictions(List<Lesson> lessons, List<DiagnosticsIssueDTO> issues) {
        List<String> examples = new ArrayList<>();

        for (Lesson lesson : lessons) {
            if (lesson.getRoom() == null || lesson.getCourse() == null || lesson.isOnline()) continue;

            Set<Zone> allowed = lesson.getCourse().getAllowedZones();
            if (allowed == null || allowed.isEmpty()) continue;

            Zone roomZone = lesson.getRoom().getZone();
            if (roomZone == null || !allowed.contains(roomZone)) {
                String allowedNames = allowed.stream().map(Zone::getName).collect(Collectors.joining(" or "));
                examples.add(String.format(
                        "'%s' must be held in %s but is assigned to room '%s' which is in %s",
                        lesson.getDisplayName(), allowedNames, lesson.getRoom().getName(),
                        roomZone != null ? roomZone.getName() : "no specific zone"));
            }
        }

        if (!examples.isEmpty()) {
            issues.add(DiagnosticsIssueDTO.blocking(
                    "ZONE_RESTRICTION_VIOLATED",
                    "Some classes are scheduled in buildings they're not supposed to be in",
                    String.format("Found %d classes in the wrong building zone. For example: %s.",
                            examples.size(), String.join("; ", examples.subList(0, Math.min(3, examples.size())))),
                    "Move these classes to rooms in the correct building or zone."
            ));
        }
    }

    /**
     * Check if lecturers are scheduled during their unavailable times.
     */
    private void checkLecturerUnavailability(List<Lesson> lessons, List<DiagnosticsIssueDTO> issues) {
        if (!constraintSettingsService.isUnavailabilitySystemEnabled()) return;

        List<String> examples = new ArrayList<>();

        for (Lesson lesson : lessons) {
            if (lesson.getLecturer() == null || lesson.getTimeslot() == null) continue;

            List<LecturerUnavailability> unavailabilities = lesson.getLecturer().getUnavailabilities();
            if (unavailabilities == null || unavailabilities.isEmpty()) continue;

            for (LecturerUnavailability unavail : unavailabilities) {
                if (unavail.getDayOfWeek() == lesson.getTimeslot().getDayOfWeek()) {
                    LocalTime lessonStart = lesson.getTimeslot().getStartTime();
                    LocalTime lessonEnd = lessonStart.plusHours(lesson.getDurationHours());

                    if (lessonStart.isBefore(unavail.getEndTime()) && unavail.getStartTime().isBefore(lessonEnd)) {
                        examples.add(String.format(
                                "%s is scheduled for '%s' on %s at %s, but they're unavailable during that time",
                                lesson.getLecturer().getName(), lesson.getDisplayName(),
                                lesson.getTimeslot().getDayOfWeek(), lessonStart));
                    }
                }
            }
        }

        if (!examples.isEmpty()) {
            issues.add(DiagnosticsIssueDTO.blocking(
                    "LECTURER_UNAVAILABLE",
                    "Some classes are scheduled when the lecturer is not available",
                    String.format("Found %d classes scheduled during lecturer unavailable times. For example: %s.",
                            examples.size(), String.join("; ", examples.subList(0, Math.min(3, examples.size())))),
                    "Reschedule these classes to times when the lecturer is available."
            ));
        }
    }

    /**
     * Check if lessons overlap with lunch break.
     */
    private void checkLunchBreakViolations(List<Lesson> lessons, List<DiagnosticsIssueDTO> issues) {
        if (!constraintSettingsService.isLunchBreakEnforced()) return;

        LocalTime lunchStart = constraintSettingsService.getLunchBreakStart();
        LocalTime lunchEnd = constraintSettingsService.getLunchBreakEnd();

        List<String> examples = new ArrayList<>();

        for (Lesson lesson : lessons) {
            if (lesson.getTimeslot() == null) continue;

            LocalTime lessonStart = lesson.getTimeslot().getStartTime();
            LocalTime lessonEnd = lessonStart.plusHours(lesson.getDurationHours());

            if (lessonStart.isBefore(lunchEnd) && lunchStart.isBefore(lessonEnd)) {
                examples.add(String.format(
                        "'%s' is scheduled from %s to %s, which overlaps with lunch break (%s to %s)",
                        lesson.getDisplayName(), lessonStart, lessonEnd, lunchStart, lunchEnd));
            }
        }

        if (!examples.isEmpty()) {
            issues.add(DiagnosticsIssueDTO.warning(
                    "LUNCH_BREAK_OVERLAP",
                    "Some classes are scheduled during the lunch break period",
                    String.format("Found %d classes overlapping with lunch time. For example: %s.",
                            examples.size(), String.join("; ", examples.subList(0, Math.min(3, examples.size())))),
                    "Move these classes to before or after lunch to give students and lecturers time to eat."
            ));
        }
    }

    /**
     * Check if lessons overlap with special events.
     */
    private void checkSpecialEventConflicts(List<Lesson> lessons, List<SpecialEvent> specialEvents, List<DiagnosticsIssueDTO> issues) {
        List<String> examples = new ArrayList<>();

        for (Lesson lesson : lessons) {
            if (lesson.getTimeslot() == null) continue;

            for (SpecialEvent event : specialEvents) {
                if (!event.isActive()) continue;
                if (event.getDayOfWeek() != lesson.getTimeslot().getDayOfWeek()) continue;

                LocalTime lessonStart = lesson.getTimeslot().getStartTime();
                LocalTime lessonEnd = lessonStart.plusHours(lesson.getDurationHours());
                LocalTime eventStart = event.getStartTime();
                LocalTime eventEnd = event.getEndTime();

                if (lessonStart.isBefore(eventEnd) && eventStart.isBefore(lessonEnd)) {
                    // Check if this event affects this lesson
                    boolean affectsStudents = lesson.getStudentGroups().stream()
                            .anyMatch(sg -> event.affectsStudentGroup(sg));
                    boolean affectsLecturer = event.getLecturer() != null &&
                            lesson.getLecturer() != null &&
                            event.getLecturer().getId().equals(lesson.getLecturer().getId());
                    boolean affectsRoom = event.getRoom() != null &&
                            lesson.getRoom() != null &&
                            event.getRoom().getId().equals(lesson.getRoom().getId());

                    if (affectsStudents || affectsLecturer || affectsRoom) {
                        examples.add(String.format(
                                "'%s' overlaps with special event '%s' on %s (%s to %s)",
                                lesson.getDisplayName(), event.getName(),
                                event.getDayOfWeek(), eventStart, eventEnd));
                    }
                }
            }
        }

        if (!examples.isEmpty()) {
            issues.add(DiagnosticsIssueDTO.blocking(
                    "SPECIAL_EVENT_CONFLICT",
                    "Some classes overlap with special events like seminars or assemblies",
                    String.format("Found %d classes conflicting with special events. For example: %s.",
                            examples.size(), String.join("; ", examples.subList(0, Math.min(3, examples.size())))),
                    "Move these classes to a different time so they don't conflict with the special event."
            ));
        }
    }

    /**
     * Check if lessons extend beyond allowed end times.
     */
    private void checkEndTimeViolations(List<Lesson> lessons, List<DiagnosticsIssueDTO> issues) {
        LocalTime latestEnd = constraintSettingsService.getLatestEndTime();
        LocalTime fridayLatestEnd = constraintSettingsService.getFridayLatestEndTime();

        List<String> examples = new ArrayList<>();

        for (Lesson lesson : lessons) {
            if (lesson.getTimeslot() == null) continue;

            LocalTime lessonStart = lesson.getTimeslot().getStartTime();
            LocalTime lessonEnd = lessonStart.plusHours(lesson.getDurationHours());
            LocalTime allowedEnd = lesson.getTimeslot().getDayOfWeek() == DayOfWeek.FRIDAY ? fridayLatestEnd : latestEnd;

            if (lessonEnd.isAfter(allowedEnd)) {
                examples.add(String.format(
                        "'%s' ends at %s on %s, but classes must end by %s",
                        lesson.getDisplayName(), lessonEnd, lesson.getTimeslot().getDayOfWeek(), allowedEnd));
            }
        }

        if (!examples.isEmpty()) {
            issues.add(DiagnosticsIssueDTO.warning(
                    "LESSON_EXCEEDS_END_TIME",
                    "Some classes run later than the allowed end time",
                    String.format("Found %d classes ending too late. For example: %s.",
                            examples.size(), String.join("; ", examples.subList(0, Math.min(3, examples.size())))),
                    "Start these classes earlier so they finish before the end time limit."
            ));
        }
    }
}
