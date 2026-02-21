package com.university.timetable.solver.move;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.TimeTable;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Custom move that changes a lesson's room.
 * Used by {@link NearbyMoveFactory} to create nearby-biased room changes.
 */
public class RoomChangeMove implements Move<TimeTable> {

    private final Lesson lesson;
    private final Room newRoom;

    public RoomChangeMove(Lesson lesson, Room newRoom) {
        this.lesson = lesson;
        this.newRoom = newRoom;
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<TimeTable> scoreDirector) {
        if (lesson.isPinned()) {
            return false;
        }
        return !Objects.equals(lesson.getRoom(), newRoom);
    }

    @Override
    public void doMoveOnly(ScoreDirector<TimeTable> scoreDirector) {
        scoreDirector.beforeVariableChanged(lesson, "room");
        lesson.setRoom(newRoom);
        scoreDirector.afterVariableChanged(lesson, "room");
    }

    @Override
    public RoomChangeMove rebase(ScoreDirector<TimeTable> destinationScoreDirector) {
        return new RoomChangeMove(
                destinationScoreDirector.lookUpWorkingObject(lesson),
                destinationScoreDirector.lookUpWorkingObject(newRoom));
    }

    @Override
    public Collection<?> getPlanningEntities() {
        return List.of(lesson);
    }

    @Override
    public Collection<?> getPlanningValues() {
        return List.of(newRoom);
    }

    public Lesson getLesson() {
        return lesson;
    }

    public Room getNewRoom() {
        return newRoom;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof RoomChangeMove other))
            return false;
        return Objects.equals(lesson, other.lesson)
                && Objects.equals(newRoom, other.newRoom);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lesson, newRoom);
    }

    @Override
    public String toString() {
        return "RoomChange(lesson=" + lesson.getId()
                + ", " + lesson.getRoom() + " → " + newRoom + ")";
    }
}
