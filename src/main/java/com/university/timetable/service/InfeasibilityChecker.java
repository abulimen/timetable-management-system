package com.university.timetable.service;

import com.university.timetable.domain.*;
import com.university.timetable.dto.InfeasibilityIssue;
import com.university.timetable.dto.InfeasibilityReport;
import com.university.timetable.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Pre-solve validation service that checks for obvious infeasibility issues
 * BEFORE running the solver. This helps catch impossible constraints early.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InfeasibilityChecker {

    private final LessonRepository lessonRepository;
    private final TimeslotRepository timeslotRepository;
    private final RoomRepository roomRepository;
    private final CourseRepository courseRepository;
    private final LecturerRepository lecturerRepository;
    private final TimeslotService timeslotService;

    /**
     * Run all feasibility checks and return a comprehensive report.
     * AUTOMATICALLY REGENERATES TIMESLOTS from current settings before checking.
     */
    @Transactional
    public InfeasibilityReport checkFeasibility() {
        long startNanos = System.nanoTime();
        log.info("Running pre-solve feasibility checks...");

        // Ensure timeslots align with current settings; regenerate only if mismatch
        long regenerateStart = System.nanoTime();
        log.info("Ensuring timeslots match current settings before feasibility check...");
        List<Timeslot> regeneratedTimeslots = timeslotService.ensureTimeslotsMatchSettings();
        log.debug("Timeslot ensure/regeneration finished in {} ms ({} timeslots)",
                elapsedMs(regenerateStart), regeneratedTimeslots.size());

        List<Lesson> lessons = lessonRepository.findAll();
        List<Timeslot> timeslots = regeneratedTimeslots;
        List<Room> rooms = roomRepository.findAll();
        List<Course> courses = courseRepository.findAll();

        InfeasibilityReport report = InfeasibilityReport.feasible(
                lessons.size(), timeslots.size(), rooms.size());

        // Run all checks
        long checkStart = System.nanoTime();
        checkRoomSlotCapacity(report, lessons, timeslots, rooms);
        log.debug("Feasibility check 'roomSlotCapacity' completed in {} ms", elapsedMs(checkStart));
        checkStart = System.nanoTime();
        checkLargestGroupFits(report, courses, rooms);
        log.debug("Feasibility check 'largestGroupFits' completed in {} ms", elapsedMs(checkStart));
        checkStart = System.nanoTime();
        checkFeatureAvailability(report, courses, rooms);
        log.debug("Feasibility check 'featureAvailability' completed in {} ms", elapsedMs(checkStart));
        checkStart = System.nanoTime();
        checkLecturerOverload(report, lessons, timeslots);
        log.debug("Feasibility check 'lecturerOverload' completed in {} ms", elapsedMs(checkStart));
        checkStart = System.nanoTime();
        checkZoneCompatibility(report, courses, rooms);
        log.debug("Feasibility check 'zoneCompatibility' completed in {} ms", elapsedMs(checkStart));

        log.info("Feasibility check complete in {} ms: feasible={}, blocking={}, warnings={}",
                elapsedMs(startNanos), report.isFeasible(), report.getBlockingCount(), report.getWarningCount());

        return report;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    /**
     * Check 1: Are there enough room-slots for all lessons?
     * If lessons > timeslots * rooms, it's mathematically impossible.
     */
    private void checkRoomSlotCapacity(InfeasibilityReport report,
            List<Lesson> lessons, List<Timeslot> timeslots, List<Room> rooms) {

        int totalLessons = lessons.size();
        int availableSlots = timeslots.size() * rooms.size();

        if (totalLessons > availableSlots) {
            report.addIssue(InfeasibilityIssue.blocking(
                    "INSUFFICIENT_SLOTS",
                    String.format("%d lessons to schedule but only %d room-slots available (%d timeslots × %d rooms)",
                            totalLessons, availableSlots, timeslots.size(), rooms.size()),
                    "Add more rooms, extend operating hours, or reduce lesson count"));
        } else if (totalLessons > availableSlots * 0.8) {
            // Warning if utilization > 80%
            report.addIssue(InfeasibilityIssue.warning(
                    "HIGH_UTILIZATION",
                    String.format("High utilization: %d lessons using %.0f%% of %d available slots",
                            totalLessons, (totalLessons * 100.0 / availableSlots), availableSlots),
                    "Solution may be tight. Consider adding capacity buffers"));
        }
    }

    /**
     * Check 2: Can the largest student group fit in at least one room?
     * Skips online courses since they don't require physical rooms.
     */
    private void checkLargestGroupFits(InfeasibilityReport report,
            List<Course> courses, List<Room> rooms) {

        int largestRoomCapacity = rooms.stream()
                .mapToInt(Room::getCapacity)
                .max()
                .orElse(0);

        for (Course course : courses) {
            // Skip online courses - they don't need physical rooms
            if (course.isOnline()) {
                continue;
            }

            int totalStudents = course.getTotalStudentCount();
            if (totalStudents > largestRoomCapacity) {
                String groupNames = course.getAllStudentGroups().stream()
                        .map(StudentGroup::getName)
                        .collect(Collectors.joining(" + "));

                report.addIssue(InfeasibilityIssue.blocking(
                        "CAPACITY_EXCEEDED",
                        String.format("Course '%s' has %d students (%s) but largest room has capacity %d",
                                course.getCode(), totalStudents, groupNames, largestRoomCapacity),
                        "Split into smaller groups or add a larger room"));
            }
        }
    }

    /**
     * Check 3: For courses requiring specific features, do suitable rooms exist?
     * Skips online courses since they don't require physical rooms.
     */
    private void checkFeatureAvailability(InfeasibilityReport report,
            List<Course> courses, List<Room> rooms) {

        for (Course course : courses) {
            // Skip online courses - they don't need physical rooms
            if (course.isOnline()) {
                continue;
            }

            Set<Feature> required = course.getRequiredFeatures();
            if (required == null || required.isEmpty()) {
                continue;
            }

            // Find rooms that have ALL required features AND enough capacity
            int minCapacity = course.getTotalStudentCount();
            boolean foundSuitable = rooms.stream()
                    .anyMatch(room -> room.getCapacity() >= minCapacity &&
                            room.hasAllFeatures(required));

            if (!foundSuitable) {
                String featureNames = required.stream()
                        .map(Feature::getName)
                        .collect(Collectors.joining(", "));

                report.addIssue(InfeasibilityIssue.blocking(
                        "FEATURE_MISMATCH",
                        String.format("Course '%s' requires [%s] with capacity ≥%d but no suitable room exists",
                                course.getCode(), featureNames, minCapacity),
                        "Add required features to a room or relax course requirements"));
            }
        }
    }

    /**
     * Check 4: Are any lecturers overloaded beyond available hours?
     */
    private void checkLecturerOverload(InfeasibilityReport report,
            List<Lesson> lessons, List<Timeslot> timeslots) {

        // Calculate available teaching hours (timeslots count as 1 hour each,
        // simplified)
        int maxHoursPerWeek = timeslots.size();

        // Group lessons by lecturer and sum hours
        Map<Lecturer, Integer> lecturerHours = new HashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.getLecturer() != null) {
                lecturerHours.merge(lesson.getLecturer(),
                        lesson.getDurationHours(), Integer::sum);
            }
        }

        for (Map.Entry<Lecturer, Integer> entry : lecturerHours.entrySet()) {
            Lecturer lecturer = entry.getKey();
            int assignedHours = entry.getValue();

            // Calculate available hours (max - unavailability periods)
            int unavailableSlots = 0;
            if (lecturer.getUnavailabilities() != null) {
                unavailableSlots = lecturer.getUnavailabilities().size();
            }
            int availableHours = maxHoursPerWeek - unavailableSlots;

            if (assignedHours > availableHours) {
                report.addIssue(InfeasibilityIssue.blocking(
                        "LECTURER_OVERLOAD",
                        String.format("Lecturer '%s' assigned %d hours but only %d slots available",
                                lecturer.getName(), assignedHours, availableHours),
                        "Reduce course load or assign additional lecturers"));
            } else if (assignedHours > availableHours * 0.9) {
                report.addIssue(InfeasibilityIssue.warning(
                        "LECTURER_HIGH_LOAD",
                        String.format("Lecturer '%s' at %.0f%% capacity (%d/%d hours)",
                                lecturer.getName(), (assignedHours * 100.0 / availableHours),
                                assignedHours, availableHours),
                        "Consider load balancing across lecturers"));
            }
        }
    }

    /**
     * Check 5: For courses with zone restrictions, do matching rooms exist?
     * Skips online courses since they don't require physical rooms.
     */
    private void checkZoneCompatibility(InfeasibilityReport report,
            List<Course> courses, List<Room> rooms) {

        for (Course course : courses) {
            // Skip online courses - they don't need physical rooms
            if (course.isOnline()) {
                continue;
            }

            Set<Zone> allowedZones = course.getAllowedZones();
            if (allowedZones == null || allowedZones.isEmpty()) {
                continue; // No zone restriction
            }

            int minCapacity = course.getTotalStudentCount();

            // Check if any room matches zone AND capacity
            boolean foundSuitable = rooms.stream()
                    .anyMatch(room -> room.getCapacity() >= minCapacity &&
                            room.getZone() != null &&
                            allowedZones.contains(room.getZone()));

            if (!foundSuitable) {
                String zoneNames = allowedZones.stream()
                        .map(Zone::getName)
                        .collect(Collectors.joining(", "));

                report.addIssue(InfeasibilityIssue.blocking(
                        "ZONE_MISMATCH",
                        String.format(
                                "Course '%s' restricted to zones [%s] with capacity ≥%d but no suitable room exists",
                                course.getCode(), zoneNames, minCapacity),
                        "Add rooms in allowed zones or relax zone restrictions"));
            }
        }
    }
}
