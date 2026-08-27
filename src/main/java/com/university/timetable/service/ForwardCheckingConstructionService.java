package com.university.timetable.service;

import com.university.timetable.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

/**
 * Forward-checking construction heuristic for timetable scheduling.
 * 
 * <p>Instead of blindly assigning lessons (like FIRST_FIT_DECREASING), this service:
 * <ol>
 * <li>Builds a conflict graph identifying which lessons constrain each other</li>
 * <li>Sorts lessons by constraint degree (most constrained first)</li>
 * <li>Assigns each lesson to the timeslot+room that:
 *   <ul>
 *   <li>Doesn't conflict with already-assigned lessons</li>
 *   <li>Minimizes the reduction of available options for future lessons</li>
 *   </ul>
 * </li>
 * <li>Propagates constraints after each assignment (forward checking)</li>
 * </ol>
 * 
 * <p>This produces a much better initial solution than blind FFD, especially for
 * dense constraint graphs where cross-faculty lecturers create cascading conflicts.
 * 
 * <p>The resulting assignment is passed to Timefold as an initial solution,
 * skipping the construction heuristic phase entirely.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ForwardCheckingConstructionService {

    private final ConstraintSettingsService constraintSettingsService;

    /**
     * Build an initial solution using forward-checking construction.
     * 
     * @param lessons   All lessons to schedule
     * @param timeslots All available timeslots
     * @param rooms     All available rooms
     * @return TimeTable with initial assignments (may have conflicts, but minimized)
     */
    public TimeTable construct(List<Lesson> lessons, List<Timeslot> timeslots, List<Room> rooms) {
        long startTime = System.currentTimeMillis();
        log.info("Forward-checking construction: {} lessons, {} timeslots, {} rooms",
                lessons.size(), timeslots.size(), rooms.size());

        // Load constraint settings
        LocalTime earliestStart = constraintSettingsService.getEarliestStartTime();
        LocalTime latestEnd = constraintSettingsService.getLatestEndTime();
        LocalTime fridayLatestEnd = constraintSettingsService.getFridayLatestEndTime();
        LocalTime lunchStart = constraintSettingsService.getLunchBreakStart();
        LocalTime lunchEnd = constraintSettingsService.getLunchBreakEnd();
        boolean lunchEnforced = constraintSettingsService.isLunchBreakEnforced();

        // 0. Index lessons by ID for fast lookup
        Map<Long, Lesson> lessonsById = new HashMap<>();
        for (Lesson l : lessons) {
            if (l.getId() != null) {
                lessonsById.put(l.getId(), l);
            }
        }

        // 1. Build conflict graph
        Map<Long, Set<Long>> conflictGraph = buildConflictGraph(lessons);
        log.info("Conflict graph built: {} nodes, avg degree = {:.1f}",
                conflictGraph.size(),
                conflictGraph.values().stream().mapToInt(Set::size).average().orElse(0));

        // 2. Pre-compute valid timeslots and rooms per lesson
        Map<Long, List<Timeslot>> validTimeslots = new HashMap<>();
        Map<Long, List<Room>> validRooms = new HashMap<>();
        for (Lesson lesson : lessons) {
            validTimeslots.put(lesson.getId(), computeValidTimeslots(lesson, timeslots,
                    earliestStart, latestEnd, fridayLatestEnd, lunchStart, lunchEnd, lunchEnforced));
            validRooms.put(lesson.getId(), computeValidRooms(lesson, rooms));
        }

        // 3. Sort lessons by constraint degree (highest first = hardest to schedule)
        List<Lesson> sortedLessons = new ArrayList<>(lessons);
        sortedLessons.sort((a, b) -> {
            int degreeA = conflictGraph.getOrDefault(a.getId(), Set.of()).size();
            int degreeB = conflictGraph.getOrDefault(b.getId(), Set.of()).size();
            if (degreeA != degreeB) return Integer.compare(degreeB, degreeA);
            // Tiebreak: fewer valid options = harder
            int optionsA = validTimeslots.get(a.getId()).size() * validRooms.get(a.getId()).size();
            int optionsB = validTimeslots.get(b.getId()).size() * validRooms.get(b.getId()).size();
            return Integer.compare(optionsA, optionsB);
        });

        // 4. Track assignments for conflict checking
        // Key: lecturerId -> set of (timeslotIndex) assignments
        Map<Long, Set<Integer>> lecturerTimeslotUsage = new HashMap<>();
        // Key: groupId -> set of timeslotIndex assignments
        Map<Long, Set<Integer>> groupTimeslotUsage = new HashMap<>();
        // Key: roomId -> set of timeslotIndex assignments
        Map<Long, Set<Integer>> roomTimeslotUsage = new HashMap<>();

        // 5. Greedy assignment with forward checking
        int assigned = 0;
        int failed = 0;
        for (Lesson lesson : sortedLessons) {
            if (lesson.isPinned() && lesson.getTimeslot() != null && lesson.getRoom() != null) {
                // Pinned lessons are already assigned
                recordAssignment(lesson, lecturerTimeslotUsage, groupTimeslotUsage, roomTimeslotUsage,
                        timeslots);
                assigned++;
                continue;
            }

            List<Timeslot> tsCandidates = validTimeslots.get(lesson.getId());
            List<Room> roomCandidates = validRooms.get(lesson.getId());

            if (tsCandidates.isEmpty() || roomCandidates.isEmpty()) {
                log.warn("Lesson {} has no valid candidates (ts={}, rooms={})",
                        lesson.getId(), tsCandidates.size(), roomCandidates.size());
                failed++;
                continue;
            }

            // Find the best timeslot+room combination
            // "Best" = least conflicts with existing assignments + most options left for future lessons
            int bestTsIdx = -1;
            int bestRoomIdx = -1;
            int bestScore = Integer.MIN_VALUE;

            for (int tsIdx = 0; tsIdx < tsCandidates.size(); tsIdx++) {
                Timeslot ts = tsCandidates.get(tsIdx);
                int tsIndex = timeslots.indexOf(ts);

                // Check lecturer conflict
                Long lecturerId = lesson.getLecturer() != null ? lesson.getLecturer().getId() : null;
                if (lecturerId != null && lecturerTimeslotUsage.containsKey(lecturerId)) {
                    if (lecturerTimeslotUsage.get(lecturerId).contains(tsIndex)) {
                        continue; // Lecturer already has a lesson at this time
                    }
                }

                // Check student group conflicts
                boolean groupConflict = false;
                for (Long groupId : lesson.getConflictGroupIds()) {
                    if (groupTimeslotUsage.containsKey(groupId) &&
                            groupTimeslotUsage.get(groupId).contains(tsIndex)) {
                        groupConflict = true;
                        break;
                    }
                }
                if (groupConflict) continue;

                // Try each valid room
                for (int rIdx = 0; rIdx < roomCandidates.size(); rIdx++) {
                    Room room = roomCandidates.get(rIdx);

                    // Check room conflict
                    if (roomTimeslotUsage.containsKey(room.getId()) &&
                            roomTimeslotUsage.get(room.getId()).contains(tsIndex)) {
                        continue; // Room already occupied at this time
                    }

                    // Score this assignment: prioritize timeslots that leave most options for future lessons
                    int score = scoreAssignment(lesson, ts, room, tsIndex, conflictGraph,
                            lecturerTimeslotUsage, groupTimeslotUsage, roomTimeslotUsage,
                            validTimeslots, lessonsById, timeslots);

                    if (score > bestScore) {
                        bestScore = score;
                        bestTsIdx = tsIdx;
                        bestRoomIdx = rIdx;
                    }
                }
            }

            if (bestTsIdx >= 0 && bestRoomIdx >= 0) {
                lesson.setTimeslot(tsCandidates.get(bestTsIdx));
                lesson.setRoom(roomCandidates.get(bestRoomIdx));
                recordAssignment(lesson, lecturerTimeslotUsage, groupTimeslotUsage,
                        roomTimeslotUsage, timeslots);
                assigned++;
            } else {
                // No valid assignment found - leave unassigned (Timefold will handle it)
                failed++;
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("Forward-checking construction complete: {} assigned, {} failed, {}ms",
                assigned, failed, elapsed);

        TimeTable result = new TimeTable();
        result.setLessons(lessons);
        result.setTimeslots(timeslots);
        result.setRooms(rooms);
        return result;
    }

    /**
     * Build a conflict graph: which lessons have hard constraints that conflict with each other.
     * Two lessons conflict if they share a lecturer or share a student group.
     */
    private Map<Long, Set<Long>> buildConflictGraph(List<Lesson> lessons) {
        Map<Long, Set<Long>> graph = new HashMap<>();

        // Group by lecturer
        Map<Long, List<Lesson>> byLecturer = new HashMap<>();
        for (Lesson lesson : lessons) {
            if (lesson.getLecturer() != null && lesson.getLecturer().getId() != null) {
                byLecturer.computeIfAbsent(lesson.getLecturer().getId(), k -> new ArrayList<>())
                        .add(lesson);
            }
        }

        // Add edges: all lessons with same lecturer conflict with each other
        for (List<Lesson> group : byLecturer.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    addEdge(graph, group.get(i).getId(), group.get(j).getId());
                }
            }
        }

        // Group by student group (conflict group IDs)
        Map<Long, List<Lesson>> byGroup = new HashMap<>();
        for (Lesson lesson : lessons) {
            for (Long groupId : lesson.getConflictGroupIds()) {
                byGroup.computeIfAbsent(groupId, k -> new ArrayList<>()).add(lesson);
            }
        }

        // Add edges: all lessons with same student group conflict with each other
        for (List<Lesson> group : byGroup.values()) {
            for (int i = 0; i < group.size(); i++) {
                for (int j = i + 1; j < group.size(); j++) {
                    addEdge(graph, group.get(i).getId(), group.get(j).getId());
                }
            }
        }

        return graph;
    }

    private void addEdge(Map<Long, Set<Long>> graph, Long a, Long b) {
        if (a == null || b == null) return;
        graph.computeIfAbsent(a, k -> new HashSet<>()).add(b);
        graph.computeIfAbsent(b, k -> new HashSet<>()).add(a);
    }

    /**
     * Record an assignment in the usage tracking maps for conflict checking.
     */
    private void recordAssignment(Lesson lesson,
                                   Map<Long, Set<Integer>> lecturerUsage,
                                   Map<Long, Set<Integer>> groupUsage,
                                   Map<Long, Set<Integer>> roomUsage,
                                   List<Timeslot> timeslots) {
        if (lesson.getTimeslot() == null) return;
        int tsIndex = timeslots.indexOf(lesson.getTimeslot());

        if (lesson.getLecturer() != null && lesson.getLecturer().getId() != null) {
            lecturerUsage.computeIfAbsent(lesson.getLecturer().getId(), k -> new HashSet<>())
                    .add(tsIndex);
        }

        for (Long groupId : lesson.getConflictGroupIds()) {
            groupUsage.computeIfAbsent(groupId, k -> new HashSet<>()).add(tsIndex);
        }

        if (lesson.getRoom() != null && !lesson.isOnline() && lesson.getRoom().getId() != null) {
            roomUsage.computeIfAbsent(lesson.getRoom().getId(), k -> new HashSet<>())
                    .add(tsIndex);
        }
    }

    /**
     * Score an assignment based on how many future options it leaves open.
     * Higher score = better (leaves more options for conflicting lessons).
     */
    private int scoreAssignment(Lesson lesson, Timeslot ts, Room room, int tsIndex,
                                  Map<Long, Set<Long>> conflictGraph,
                                  Map<Long, Set<Integer>> lecturerUsage,
                                  Map<Long, Set<Integer>> groupUsage,
                                  Map<Long, Set<Integer>> roomUsage,
                                  Map<Long, List<Timeslot>> validTimeslots,
                                  Map<Long, Lesson> lessonsById,
                                  List<Timeslot> timeslots) {
        int score = 0;

        // For each conflicting lesson, count how many timeslots would still be available
        Set<Long> conflicts = conflictGraph.getOrDefault(lesson.getId(), Set.of());
        for (Long conflictId : conflicts) {
            List<Timeslot> conflictTimeslots = validTimeslots.get(conflictId);
            if (conflictTimeslots == null) continue;
            Lesson conflictLesson = lessonsById.get(conflictId);

            int available = 0;
            for (Timeslot cTs : conflictTimeslots) {
                int cTsIndex = timeslots.indexOf(cTs);
                // Check if this timeslot is still available for the conflicting lesson
                if (!isTimeslotBlocked(cTsIndex, conflictLesson, tsIndex, lesson, lecturerUsage, groupUsage)) {
                    available++;
                }
            }
            score += available;
        }

        // Bonus: prefer timeslots in the middle of the day (less likely to block others)
        int hour = ts.getStartTime().getHour();
        if (hour >= 9 && hour <= 15) {
            score += 5; // Prefer mid-day slots
        }

        // Penalty: prefer smaller rooms (less capacity waste)
        int waste = room.getCapacity() - lesson.getTotalStudentCount();
        score -= waste / 10;

        return score;
    }

    /**
     * Determine if timeslot index cTsIndex is blocked for conflictLesson when assigning lesson at tsIndex.
     */
    public boolean isTimeslotBlocked(int cTsIndex, Lesson conflictLesson, int assigningTsIndex, Lesson assigningLesson,
                                      Map<Long, Set<Integer>> lecturerUsage,
                                      Map<Long, Set<Integer>> groupUsage) {
        // 1. Direct collision with assigningLesson placed at assigningTsIndex
        if (cTsIndex == assigningTsIndex && sharesResource(conflictLesson, assigningLesson)) {
            return true;
        }

        // 2. ConflictLesson's lecturer is already assigned at cTsIndex
        if (conflictLesson != null && conflictLesson.getLecturer() != null && conflictLesson.getLecturer().getId() != null) {
            Set<Integer> lecturerSlots = lecturerUsage.get(conflictLesson.getLecturer().getId());
            if (lecturerSlots != null && lecturerSlots.contains(cTsIndex)) {
                return true;
            }
        }

        // 3. Any of conflictLesson's student groups is already assigned at cTsIndex
        if (conflictLesson != null && conflictLesson.getConflictGroupIds() != null) {
            for (Long groupId : conflictLesson.getConflictGroupIds()) {
                Set<Integer> groupSlots = groupUsage.get(groupId);
                if (groupSlots != null && groupSlots.contains(cTsIndex)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean sharesResource(Lesson a, Lesson b) {
        if (a == null || b == null) return false;
        if (a.getLecturer() != null && b.getLecturer() != null &&
                a.getLecturer().getId() != null && b.getLecturer().getId() != null &&
                Objects.equals(a.getLecturer().getId(), b.getLecturer().getId())) {
            return true;
        }
        if (a.getConflictGroupIds() != null && b.getConflictGroupIds() != null) {
            for (Long gIdA : a.getConflictGroupIds()) {
                if (b.getConflictGroupIds().contains(gIdA)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Compute valid timeslots for a lesson based on timing constraints.
     */
    private List<Timeslot> computeValidTimeslots(Lesson lesson, List<Timeslot> timeslots,
                                                   LocalTime earliestStart, LocalTime latestEnd,
                                                   LocalTime fridayLatestEnd,
                                                   LocalTime lunchStart, LocalTime lunchEnd,
                                                   boolean lunchEnforced) {
        List<Timeslot> valid = new ArrayList<>();
        int duration = lesson.getDurationHours();

        for (Timeslot ts : timeslots) {
            LocalTime start = ts.getStartTime();
            LocalTime end = start.plusHours(duration);

            // Check earliest start
            if (start.isBefore(earliestStart)) continue;

            // Check latest end
            LocalTime dayLatestEnd = (ts.getDayOfWeek() == DayOfWeek.FRIDAY) ? fridayLatestEnd : latestEnd;
            if (end.isAfter(dayLatestEnd)) continue;

            // Check lunch break
            if (lunchEnforced && start.isBefore(lunchEnd) && lunchStart.isBefore(end)) {
                continue;
            }

            // Check lecturer unavailability
            if (lesson.getLecturer() != null && !lesson.getLecturer().isAvailableAt(ts, duration)) {
                continue;
            }

            valid.add(ts);
        }
        return valid;
    }

    /**
     * Compute valid rooms for a lesson based on capacity, features, zones.
     */
    private List<Room> computeValidRooms(Lesson lesson, List<Room> rooms) {
        if (lesson.isOnline()) return new ArrayList<>(rooms);

        List<Room> valid = new ArrayList<>();
        Course course = lesson.getCourse();
        int students = lesson.getTotalStudentCount();

        for (Room room : rooms) {
            // Capacity
            if (room.getCapacity() < students) continue;

            // Features
            if (course != null && course.getRequiredFeatures() != null
                    && !course.getRequiredFeatures().isEmpty()) {
                if (!room.hasAllFeatures(course.getRequiredFeatures())) continue;
            }

            // Zones
            if (course != null && course.getAllowedZones() != null
                    && !course.getAllowedZones().isEmpty()
                    && room.getZone() != null) {
                if (!course.getAllowedZones().contains(room.getZone())) continue;
            }

            valid.add(room);
        }
        return valid;
    }
}