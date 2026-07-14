package com.university.timetable.solver.move;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import com.university.timetable.domain.*;

import java.util.*;

/**
 * Pillar move: shifts ALL lessons of the same course to a new timeslot together.
 * <p>
 * This is a structural move that escapes local optima unreachable by individual
 * lesson moves. For example, if a course has 3 lessons all on Monday and needs
 * them spread across different days, individual moves can't fix this because
 * moving one lesson to Tuesday may violate same-course-same-day. Moving all 3
 * together to a new day preserves feasibility.
 * <p>
 * Room assignments are preserved (same rooms, new timeslot).
 * If a lesson's current room is incompatible with the new timeslot,
 * the move falls back to picking the first compatible room.
 */
public class CoursePillarMove implements Move<TimeTable> {

    private final List<Lesson> pillarLessons;
    private final Timeslot targetTimeslot;
    private final List<Room> allRooms;

    public CoursePillarMove(List<Lesson> pillarLessons, Timeslot targetTimeslot, List<Room> allRooms) {
        this.pillarLessons = pillarLessons;
        this.targetTimeslot = targetTimeslot;
        this.allRooms = allRooms;
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<TimeTable> scoreDirector) {
        if (pillarLessons.isEmpty()) return false;

        // Don't move to the same timeslot as current assignment
        Timeslot currentTimeslot = pillarLessons.get(0).getTimeslot();
        if (currentTimeslot != null && currentTimeslot.equals(targetTimeslot)) return false;

        // All lessons must be unpinned
        for (Lesson lesson : pillarLessons) {
            if (lesson.isPinned()) return false;
        }

        // Check that at least one compatible room exists for each lesson at target timeslot
        for (Lesson lesson : pillarLessons) {
            if (lesson.isOnline()) continue;
            boolean hasCompatibleRoom = false;
            for (Room room : allRooms) {
                if (NearbyMoveFactory.isRoomCompatible(lesson, room)) {
                    hasCompatibleRoom = true;
                    break;
                }
            }
            if (!hasCompatibleRoom) return false;
        }

        return true;
    }

    @Override
    public void doMoveOnly(ScoreDirector<TimeTable> scoreDirector) {
        for (Lesson lesson : pillarLessons) {
            // Change timeslot
            scoreDirector.beforeVariableChanged(lesson, "timeslot");
            lesson.setTimeslot(targetTimeslot);
            scoreDirector.afterVariableChanged(lesson, "timeslot");

            // Keep current room if compatible, otherwise find a compatible one
            Room currentRoom = lesson.getRoom();
            if (currentRoom == null || !NearbyMoveFactory.isRoomCompatible(lesson, currentRoom)) {
                Room compatibleRoom = null;
                for (Room room : allRooms) {
                    if (NearbyMoveFactory.isRoomCompatible(lesson, room)) {
                        compatibleRoom = room;
                        break;
                    }
                }
                if (compatibleRoom != null) {
                    scoreDirector.beforeVariableChanged(lesson, "room");
                    lesson.setRoom(compatibleRoom);
                    scoreDirector.afterVariableChanged(lesson, "room");
                }
            }
        }
        scoreDirector.triggerVariableListeners();
    }

    @Override
    public CoursePillarMove rebase(ScoreDirector<TimeTable> destinationScoreDirector) {
        List<Lesson> rebasedLessons = new ArrayList<>();
        for (Lesson lesson : pillarLessons) {
            rebasedLessons.add(destinationScoreDirector.lookUpWorkingObject(lesson));
        }
        return new CoursePillarMove(rebasedLessons,
                destinationScoreDirector.lookUpWorkingObject(targetTimeslot),
                allRooms);
    }

    @Override
    public Collection<?> getPlanningEntities() {
        return pillarLessons;
    }

    @Override
    public Collection<?> getPlanningValues() {
        return List.of(targetTimeslot);
    }

    @Override
    public String toString() {
        return "CoursePillarMove{" +
                "course=" + (pillarLessons.isEmpty() ? "?" : pillarLessons.get(0).getCourse()) +
                ", lessons=" + pillarLessons.size() +
                ", targetTimeslot=" + targetTimeslot +
                '}';
    }
}
