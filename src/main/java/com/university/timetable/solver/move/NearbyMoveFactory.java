package com.university.timetable.solver.move;

import ai.timefold.solver.core.impl.heuristic.move.Move;
import ai.timefold.solver.core.impl.heuristic.selector.move.factory.MoveListFactory;
import com.university.timetable.domain.*;
import com.university.timetable.solver.RoomNearbyDistanceMeter;
import com.university.timetable.solver.TimeslotNearbyDistanceMeter;

import java.util.*;

/**
 * Custom nearby-biased move factory for Timefold Community Edition.
 * <p>
 * Replicates the Enterprise-only "nearby selection" feature by generating
 * moves biased toward nearby timeslots and rooms using the existing distance
 * meters.
 * <p>
 * Includes constraint-aware filtering: moves that would obviously violate
 * hard constraints (room capacity, required features, zone restriction) are
 * skipped entirely, avoiding wasted score evaluations.
 */
public class NearbyMoveFactory implements MoveListFactory<TimeTable> {

    /** Maximum number of nearby timeslots to consider per lesson. */
    static final int TIMESLOT_NEARBY_LIMIT = 8;

    /** Maximum number of nearby rooms to consider per lesson. */
    static final int ROOM_NEARBY_LIMIT = 6;

    /** Maximum number of swap candidates per anchor lesson. */
    static final int SWAP_NEARBY_LIMIT = 4;

    /**
     * Hard cap for lessons considered in one step.
     * Keeps move-list creation bounded on very large datasets.
     */
    static final int MAX_LESSONS_PER_STEP = 320;

    /** Maximum anchor lessons used for swap generation. */
    static final int SWAP_ANCHOR_LIMIT = 180;

    private final TimeslotNearbyDistanceMeter timeslotDistanceMeter = new TimeslotNearbyDistanceMeter();
    private final RoomNearbyDistanceMeter roomDistanceMeter = new RoomNearbyDistanceMeter();

    @Override
    public List<? extends Move<TimeTable>> createMoveList(TimeTable solution) {
        List<Lesson> movableLessons = solution.getLessons().stream()
                .filter(lesson -> !lesson.isPinned())
                .toList();

        if (movableLessons.isEmpty()) {
            return List.of();
        }

        if (movableLessons.size() > MAX_LESSONS_PER_STEP) {
            List<Lesson> sampled = new ArrayList<>(movableLessons);
            Collections.shuffle(sampled);
            movableLessons = sampled.subList(0, MAX_LESSONS_PER_STEP);
        }

        List<Timeslot> allTimeslots = solution.getTimeslots();
        List<Room> allRooms = solution.getRooms();
        List<Move<TimeTable>> moves = new ArrayList<>();

        for (Lesson lesson : movableLessons) {
            // 1. Nearby timeslot change moves
            addNearbyTimeslotMoves(lesson, allTimeslots, moves);

            // 2. Nearby room change moves (with constraint filtering)
            addNearbyRoomMoves(lesson, allRooms, moves);
        }

        // 3. Swap moves (sampled and filtered for large-scale performance)
        addNearbySwapMoves(movableLessons, moves);

        // Shuffle to ensure fair distribution across entities
        Collections.shuffle(moves);

        return moves;
    }

    /**
     * Add timeslot change moves for the N nearest timeslots to this lesson.
     */
    private void addNearbyTimeslotMoves(Lesson lesson, List<Timeslot> allTimeslots,
            List<Move<TimeTable>> moves) {
        if (lesson.getTimeslot() == null) {
            int limit = Math.min(TIMESLOT_NEARBY_LIMIT, allTimeslots.size());
            for (int i = 0; i < limit; i++) {
                moves.add(new TimeslotChangeMove(lesson, allTimeslots.get(i)));
            }
            return;
        }

        List<Timeslot> sorted = allTimeslots.stream()
                .sorted(Comparator.comparingDouble(ts -> timeslotDistanceMeter.getNearbyDistance(lesson, ts)))
                .toList();

        int limit = Math.min(TIMESLOT_NEARBY_LIMIT, sorted.size());
        for (int i = 0; i < limit; i++) {
            Timeslot candidate = sorted.get(i);
            if (!Objects.equals(candidate, lesson.getTimeslot())) {
                moves.add(new TimeslotChangeMove(lesson, candidate));
            }
        }
    }

    /**
     * Add room change moves for the N nearest COMPATIBLE rooms to this lesson.
     * Rooms that violate hard constraints are filtered out before distance sorting.
     */
    private void addNearbyRoomMoves(Lesson lesson, List<Room> allRooms,
            List<Move<TimeTable>> moves) {
        // Filter to only compatible rooms first
        List<Room> compatibleRooms = allRooms.stream()
                .filter(room -> isRoomCompatible(lesson, room))
                .toList();

        if (lesson.getRoom() == null) {
            int limit = Math.min(ROOM_NEARBY_LIMIT, compatibleRooms.size());
            for (int i = 0; i < limit; i++) {
                moves.add(new RoomChangeMove(lesson, compatibleRooms.get(i)));
            }
            return;
        }

        List<Room> sorted = compatibleRooms.stream()
                .sorted(Comparator.comparingDouble(room -> roomDistanceMeter.getNearbyDistance(lesson, room)))
                .toList();

        int limit = Math.min(ROOM_NEARBY_LIMIT, sorted.size());
        for (int i = 0; i < limit; i++) {
            Room candidate = sorted.get(i);
            if (!Objects.equals(candidate, lesson.getRoom())) {
                moves.add(new RoomChangeMove(lesson, candidate));
            }
        }
    }

    /**
     * Add swap moves between nearby lesson pairs.
     * "Nearby" is defined by combined timeslot + room distance.
     * Pairs are deduplicated so swap(A,B) is only generated once.
     */
    private void addNearbySwapMoves(List<Lesson> movableLessons, List<Move<TimeTable>> moves) {
        if (movableLessons.size() < 2) {
            return;
        }

        // Full pairwise sorting is too expensive on large datasets.
        // Use shuffled anchors + lightweight compatibility filtering.
        List<Lesson> shuffled = new ArrayList<>(movableLessons);
        Collections.shuffle(shuffled);
        int anchorLimit = Math.min(SWAP_ANCHOR_LIMIT, shuffled.size());

        for (int i = 0; i < anchorLimit; i++) {
            Lesson lessonA = shuffled.get(i);
            int generated = 0;
            for (int j = i + 1; j < shuffled.size() && generated < SWAP_NEARBY_LIMIT; j++) {
                Lesson lessonB = shuffled.get(j);
                if (lessonA.getId() == null || lessonB.getId() == null) {
                    continue;
                }
                if (lessonA.getId() >= lessonB.getId()) {
                    continue;
                }
                if (!isPotentiallyUsefulSwap(lessonA, lessonB)) {
                    continue;
                }
                moves.add(new LessonSwapMove(lessonA, lessonB));
                generated++;
            }
        }
    }

    /**
     * Check if a room is compatible with a lesson based on static hard constraints.
     * <p>
     * Filters checked (all are static — don't depend on other lesson assignments):
     * <ul>
     * <li>Room capacity must accommodate total student count</li>
     * <li>Room must have all required features (lab, projector, etc.)</li>
     * <li>Room zone must be in the course's allowed zones (if restricted)</li>
     * </ul>
     *
     * @return true if the room is potentially valid for this lesson
     */
    static boolean isRoomCompatible(Lesson lesson, Room room) {
        // Online lessons accept any room
        if (lesson.isOnline()) {
            return true;
        }

        // Capacity check: room must fit all students
        if (room.getCapacity() < lesson.getTotalStudentCount()) {
            return false;
        }

        // Feature check: room must have all required features
        Course course = lesson.getCourse();
        if (course != null && course.getRequiredFeatures() != null
                && !course.getRequiredFeatures().isEmpty()) {
            if (!room.hasAllFeatures(course.getRequiredFeatures())) {
                return false;
            }
        }

        // Zone check: room's zone must be in course's allowed zones
        if (course != null && course.getAllowedZones() != null
                && !course.getAllowedZones().isEmpty()
                && room.getZone() != null) {
            if (!course.getAllowedZones().contains(room.getZone())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Quick pre-filter for swap candidates to avoid generating obviously weak moves.
     */
    private boolean isPotentiallyUsefulSwap(Lesson lessonA, Lesson lessonB) {
        // Keep swaps mostly local in time if both are assigned.
        if (lessonA.getTimeslot() != null && lessonB.getTimeslot() != null) {
            double distance = timeslotDistanceMeter.getNearbyDistance(lessonA, lessonB.getTimeslot());
            if (distance > TIMESLOT_NEARBY_LIMIT * 1.5) {
                return false;
            }
        }

        // Ensure each lesson can at least potentially use the other's room.
        if (lessonA.getRoom() != null && !isRoomCompatible(lessonB, lessonA.getRoom())) {
            return false;
        }
        if (lessonB.getRoom() != null && !isRoomCompatible(lessonA, lessonB.getRoom())) {
            return false;
        }
        return true;
    }
}
