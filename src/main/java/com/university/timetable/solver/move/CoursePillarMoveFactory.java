package com.university.timetable.solver.move;

import ai.timefold.solver.core.impl.heuristic.move.Move;
import ai.timefold.solver.core.impl.heuristic.selector.move.factory.MoveListFactory;
import com.university.timetable.domain.*;
import com.university.timetable.solver.AdaptiveSolverListener;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Factory for course pillar moves.
 * <p>
 * Groups all lessons of the same course (same lecturer, same student groups)
 * and generates moves that shift the entire pillar to a different timeslot.
 * <p>
 * Only generates moves for courses where at least one lesson has a hard
 * constraint violation or a poor soft assignment. This avoids generating
 * thousands of useless pillar moves for well-placed courses.
 * <p>
 * Adaptive behavior: when hard violations are high (soft multiplier low),
 * generates MORE pillar moves to focus on structural fixes. When hard
 * violations approach zero, generates FEWER pillar moves.
 * <p>
 * Limits:
 * <ul>
 * <li>Base: Max 20 courses per step (adaptive up to 50)</li>
 * <li>Base: Max 4 target timeslots per course (adaptive up to 8)</li>
 * </ul>
 */
public class CoursePillarMoveFactory implements MoveListFactory<TimeTable> {

    static final int BASE_MAX_COURSES_PER_STEP = 20;
    static final int MAX_COURSES_PER_STEP_CAP = 50;
    static final int BASE_MAX_TARGET_TIMESLOTS = 4;
    static final int MAX_TARGET_TIMESLOTS_CAP = 8;

    @Override
    public List<? extends Move<TimeTable>> createMoveList(TimeTable solution) {
        List<Lesson> allLessons = solution.getLessons();
        List<Timeslot> allTimeslots = solution.getTimeslots();
        List<Room> allRooms = solution.getRooms();

        // Adaptive limits: when hard violations are high (soft multiplier low),
        // increase pillar move generation to focus on structural fixes
        double softMultiplier = AdaptiveSolverListener.SOFT_WEIGHT_MULTIPLIER.get();
        int adaptiveCoursesLimit = (int) (BASE_MAX_COURSES_PER_STEP + (MAX_COURSES_PER_STEP_CAP - BASE_MAX_COURSES_PER_STEP) * (1.0 - softMultiplier));
        int adaptiveTimeslotLimit = (int) (BASE_MAX_TARGET_TIMESLOTS + (MAX_TARGET_TIMESLOTS_CAP - BASE_MAX_TARGET_TIMESLOTS) * (1.0 - softMultiplier));

        // Group lessons by course
        Map<Long, List<Lesson>> courseLessons = new LinkedHashMap<>();
        for (Lesson lesson : allLessons) {
            if (lesson.getCourse() == null || lesson.getCourse().getId() == null) continue;
            if (lesson.isPinned()) continue;
            courseLessons.computeIfAbsent(lesson.getCourse().getId(), k -> new ArrayList<>()).add(lesson);
        }

        if (courseLessons.isEmpty()) {
            return List.of();
        }

        // Score each course by how "problematic" it is (hard violations + soft penalties)
        // Only include courses with at least some issues
        List<Map.Entry<Long, List<Lesson>>> sortedCourses = courseLessons.entrySet().stream()
                .filter(entry -> {
                    List<Lesson> lessons = entry.getValue();
                    // Include if any lesson has null timeslot or is involved in conflicts
                    return lessons.stream().anyMatch(l -> l.getTimeslot() == null || hasConflict(l, allLessons));
                })
                .sorted((a, b) -> Integer.compare(b.getValue().size(), a.getValue().size()))
                .limit(adaptiveCoursesLimit)
                .toList();

        List<Move<TimeTable>> moves = new ArrayList<>();

        for (Map.Entry<Long, List<Lesson>> entry : sortedCourses) {
            List<Lesson> pillar = entry.getValue();
            if (pillar.isEmpty()) continue;

            // Get the current timeslot of the first lesson as reference
            Timeslot currentTimeslot = pillar.get(0).getTimeslot();
            if (currentTimeslot == null) {
                // Unassigned pillar — generate moves to any timeslot
                int limit = Math.min(adaptiveTimeslotLimit, allTimeslots.size());
                for (int i = 0; i < limit; i++) {
                    moves.add(new CoursePillarMove(pillar, allTimeslots.get(i), allRooms));
                }
                continue;
            }

            // Sort timeslots by "distance" from current (prefer nearby changes)
            List<Timeslot> sortedTimeslots = allTimeslots.stream()
                    .filter(ts -> !ts.equals(currentTimeslot))
                    .sorted(Comparator.comparingInt(ts -> timeslotDistance(currentTimeslot, ts)))
                    .limit(adaptiveTimeslotLimit)
                    .toList();

            for (Timeslot target : sortedTimeslots) {
                moves.add(new CoursePillarMove(pillar, target, allRooms));
            }
        }

        return moves;
    }

    /**
     * Quick check if a lesson is involved in any hard constraint violation.
     */
    private boolean hasConflict(Lesson lesson, List<Lesson> allLessons) {
        if (lesson.getTimeslot() == null) return true;
        for (Lesson other : allLessons) {
            if (other == lesson || other.getTimeslot() == null) continue;
            if (!Objects.equals(lesson.getTimeslot().getDayOfWeek(), other.getTimeslot().getDayOfWeek())) continue;
            if (!overlaps(lesson, other)) continue;

            // Room conflict
            if (!lesson.isOnline() && !other.isOnline()
                    && lesson.getRoom() != null && other.getRoom() != null
                    && lesson.getRoom().getId().equals(other.getRoom().getId())) {
                return true;
            }
            // Lecturer conflict
            if (lesson.getLecturer() != null && other.getLecturer() != null
                    && lesson.getLecturer().getId().equals(other.getLecturer().getId())) {
                return true;
            }
        }
        return false;
    }

    private boolean overlaps(Lesson a, Lesson b) {
        return a.getTimeslot().getStartTime().isBefore(b.getEndTime())
                && b.getTimeslot().getStartTime().isBefore(a.getEndTime());
    }

    /**
     * Simple distance metric: same day = hour difference, different day = day_diff * 12 + hour_diff.
     */
    private int timeslotDistance(Timeslot a, Timeslot b) {
        int dayA = a.getDayOfWeek().getValue(); // MONDAY=1
        int dayB = b.getDayOfWeek().getValue();
        int hourA = a.getStartTime().getHour();
        int hourB = b.getStartTime().getHour();

        if (dayA == dayB) {
            return Math.abs(hourA - hourB);
        }
        return Math.abs(dayA - dayB) * 12 + Math.abs(hourA - hourB);
    }
}
