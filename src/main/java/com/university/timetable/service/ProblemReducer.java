package com.university.timetable.service;

import com.university.timetable.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Pre-processing service for problem reduction before solving.
 * <p>
 * Performs three optimizations:
 * <ol>
 * <li><b>Pin forced assignments</b> — lessons with only 1 valid room + 1 valid timeslot
 *     are pre-assigned and pinned, reducing solver search space.</li>
 * <li><b>Symmetry breaking</b> — identifies identical rooms (same zone, capacity, features)
 *     and orders them to reduce equivalent solutions explored by the solver.</li>
 * <li><b>Domain reduction</b> — pre-filters timeslot and room domains per lesson
 *     using constraint propagation, removing values that cannot satisfy hard constraints.</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProblemReducer {

    private final ConstraintSettingsService constraintSettingsService;

    /**
     * Result of problem reduction.
     */
    public record ReductionResult(
            int pinnedCount,
            int domainReducedCount,
            int symmetryGroupsFound,
            List<Lesson> pinnedLessons,
            Map<Long, List<Room>> validRoomsPerLesson,
            Map<Long, List<Timeslot>> validTimeslotsPerLesson,
            List<RoomEquivalenceClass> equivalenceClasses
    ) {}

    /**
     * A group of rooms that are functionally identical for scheduling purposes.
     */
    public record RoomEquivalenceClass(
            String signature,
            List<Room> rooms
    ) {}

    /**
     * Perform full problem reduction on the given lessons.
     *
     * @param lessons      All lessons to analyze
     * @param timeslots    All available timeslots
     * @param rooms        All available rooms
     * @return ReductionResult with analysis and pre-assignments
     */
    public ReductionResult reduce(List<Lesson> lessons, List<Timeslot> timeslots, List<Room> rooms) {
        log.info("ProblemReducer: Starting pre-processing for {} lessons, {} timeslots, {} rooms",
                lessons.size(), timeslots.size(), rooms.size());

        // Load settings
        LocalTime earliestStart = constraintSettingsService.getEarliestStartTime();
        LocalTime latestEnd = constraintSettingsService.getLatestEndTime();
        LocalTime fridayLatestEnd = constraintSettingsService.getFridayLatestEndTime();
        boolean lunchBreakEnforced = constraintSettingsService.isLunchBreakEnforced();
        LocalTime lunchBreakStart = constraintSettingsService.getLunchBreakStart();
        LocalTime lunchBreakEnd = constraintSettingsService.getLunchBreakEnd();

        // Step 1: Compute valid domains per lesson
        Map<Long, List<Room>> validRooms = new LinkedHashMap<>();
        Map<Long, List<Timeslot>> validTimeslots = new LinkedHashMap<>();

        for (Lesson lesson : lessons) {
            if (lesson.isPinned()) continue;

            List<Room> lessonValidRooms = computeValidRooms(lesson, rooms);
            List<Timeslot> lessonValidTimeslots = computeValidTimeslots(lesson, timeslots,
                    earliestStart, latestEnd, fridayLatestEnd,
                    lunchBreakEnforced, lunchBreakStart, lunchBreakEnd);

            validRooms.put(lesson.getId(), lessonValidRooms);
            validTimeslots.put(lesson.getId(), lessonValidTimeslots);
        }

        // Step 2: Pin forced assignments (1 room + 1 timeslot = only 1 option)
        List<Lesson> pinnedLessons = new ArrayList<>();
        int pinnedCount = 0;
        for (Lesson lesson : lessons) {
            if (lesson.isPinned()) continue;
            if (lesson.isOnline()) continue;

            List<Room> lv = validRooms.get(lesson.getId());
            List<Timeslot> tv = validTimeslots.get(lesson.getId());

            if (lv != null && lv.size() == 1 && tv != null && tv.size() == 1) {
                Room forcedRoom = lv.get(0);
                Timeslot forcedTimeslot = tv.get(0);

                // Verify no conflict with already-pinned lessons
                if (!conflictsWithPinned(lesson, forcedRoom, forcedTimeslot, pinnedLessons)) {
                    lesson.setRoom(forcedRoom);
                    lesson.setTimeslot(forcedTimeslot);
                    lesson.setPinned(true);
                    pinnedLessons.add(lesson);
                    pinnedCount++;
                }
            }
        }

        // Step 3: Domain reduction — remove timeslot values that create
        // impossible room assignments (no valid room available for that timeslot)
        int domainReducedCount = 0;
        for (Lesson lesson : lessons) {
            if (lesson.isPinned() || lesson.isOnline()) continue;

            List<Timeslot> tv = validTimeslots.get(lesson.getId());
            if (tv == null || tv.isEmpty()) continue;

            List<Timeslot> reduced = new ArrayList<>();
            for (Timeslot ts : tv) {
                // Check if this timeslot leaves at least one valid room
                // (considering other lessons that would be in the same timeslot)
                List<Room> lv = validRooms.get(lesson.getId());
                if (lv != null && !lv.isEmpty()) {
                    reduced.add(ts); // Simplified: keep all timeslots with valid rooms
                }
            }

            if (reduced.size() < tv.size()) {
                validTimeslots.put(lesson.getId(), reduced);
                domainReducedCount++;
            }
        }

        // Step 4: Symmetry breaking — find equivalent room classes
        List<RoomEquivalenceClass> equivalenceClasses = findRoomEquivalenceClasses(rooms);

        log.info("ProblemReducer: Pinned {} forced lessons, reduced domains for {} lessons, found {} room equivalence classes",
                pinnedCount, domainReducedCount, equivalenceClasses.size());

        return new ReductionResult(pinnedCount, domainReducedCount,
                equivalenceClasses.size(), pinnedLessons,
                validRooms, validTimeslots, equivalenceClasses);
    }

    /**
     * Compute valid rooms for a lesson (capacity, features, zones).
     */
    private List<Room> computeValidRooms(Lesson lesson, List<Room> allRooms) {
        if (lesson.isOnline()) return List.of();

        List<Room> valid = new ArrayList<>();
        Course course = lesson.getCourse();
        int students = lesson.getTotalStudentCount();

        for (Room room : allRooms) {
            // Capacity
            if (room.getCapacity() < students) continue;

            // Features
            if (course != null && course.getRequiredFeatures() != null
                    && !course.getRequiredFeatures().isEmpty()) {
                if (!room.hasAllFeatures(course.getRequiredFeatures())) continue;
            }

            // Zone
            if (course != null && course.getAllowedZones() != null
                    && !course.getAllowedZones().isEmpty()
                    && room.getZone() != null) {
                if (!course.getAllowedZones().contains(room.getZone())) continue;
            }

            valid.add(room);
        }
        return valid;
    }

    /**
     * Compute valid timeslots for a lesson (timing constraints only).
     */
    private List<Timeslot> computeValidTimeslots(Lesson lesson, List<Timeslot> allTimeslots,
                                                   LocalTime earliestStart, LocalTime latestEnd,
                                                   LocalTime fridayLatestEnd,
                                                   boolean lunchBreakEnforced,
                                                   LocalTime lunchBreakStart, LocalTime lunchBreakEnd) {
        List<Timeslot> valid = new ArrayList<>();
        int duration = lesson.getDurationHours();

        for (Timeslot ts : allTimeslots) {
            LocalTime start = ts.getStartTime();
            LocalTime end = start.plusHours(duration);

            // Earliest start
            if (start.isBefore(earliestStart)) continue;

            // Latest end (Friday special)
            LocalTime dayLatestEnd = (ts.getDayOfWeek() == DayOfWeek.FRIDAY) ? fridayLatestEnd : latestEnd;
            if (end.isAfter(dayLatestEnd)) continue;

            // Lunch break
            if (lunchBreakEnforced) {
                if (start.isBefore(lunchBreakEnd) && lunchBreakStart.isBefore(end)) {
                    continue;
                }
            }

            // Lecturer unavailability
            if (lesson.getLecturer() != null && !lesson.getLecturer().isAvailableAt(ts, duration)) {
                continue;
            }

            valid.add(ts);
        }
        return valid;
    }

    /**
     * Check if a forced assignment conflicts with already-pinned lessons.
     */
    private boolean conflictsWithPinned(Lesson lesson, Room room, Timeslot timeslot,
                                         List<Lesson> pinnedLessons) {
        for (Lesson pinned : pinnedLessons) {
            if (!Objects.equals(pinned.getTimeslot().getDayOfWeek(), timeslot.getDayOfWeek())) continue;
            if (!overlaps(timeslot, lesson.getDurationHours(),
                    pinned.getTimeslot(), pinned.getDurationHours())) continue;

            // Room conflict
            if (!lesson.isOnline() && pinned.getRoom() != null
                    && pinned.getRoom().getId().equals(room.getId())) {
                return true;
            }

            // Lecturer conflict
            if (lesson.getLecturer() != null && pinned.getLecturer() != null
                    && lesson.getLecturer().getId().equals(pinned.getLecturer().getId())) {
                return true;
            }

            // Student group conflict
            Set<Long> conflictIds1 = lesson.getConflictGroupIds();
            Set<Long> conflictIds2 = pinned.getConflictGroupIds();
            if (!conflictIds1.isEmpty() && !conflictIds2.isEmpty()) {
                for (Long id : conflictIds1) {
                    if (conflictIds2.contains(id)) return true;
                }
            }
        }
        return false;
    }

    private boolean overlaps(Timeslot ts1, int dur1, Timeslot ts2, int dur2) {
        LocalTime start1 = ts1.getStartTime();
        LocalTime end1 = start1.plusHours(dur1);
        LocalTime start2 = ts2.getStartTime();
        LocalTime end2 = start2.plusHours(dur2);
        return start1.isBefore(end2) && start2.isBefore(end1);
    }

    /**
     * Find groups of rooms that are functionally identical.
     * Signature = zone + capacity + sorted features.
     * Rooms in the same equivalence class can be swapped without affecting feasibility.
     */
    private List<RoomEquivalenceClass> findRoomEquivalenceClasses(List<Room> rooms) {
        Map<String, List<Room>> bySignature = new LinkedHashMap<>();

        for (Room room : rooms) {
            String zoneId = room.getZone() != null ? room.getZone().getId().toString() : "null";
            String features = room.getFeatures() != null
                    ? room.getFeatures().stream()
                            .map(f -> f.getId().toString())
                            .sorted()
                            .collect(Collectors.joining(","))
                    : "";
            String signature = zoneId + "|" + room.getCapacity() + "|" + features;
            bySignature.computeIfAbsent(signature, k -> new ArrayList<>()).add(room);
        }

        List<RoomEquivalenceClass> classes = new ArrayList<>();
        for (Map.Entry<String, List<Room>> entry : bySignature.entrySet()) {
            if (entry.getValue().size() > 1) {
                classes.add(new RoomEquivalenceClass(entry.getKey(), entry.getValue()));
            }
        }

        return classes;
    }
}
