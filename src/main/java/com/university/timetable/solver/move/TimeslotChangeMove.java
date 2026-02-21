package com.university.timetable.solver.move;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.TimeTable;
import com.university.timetable.domain.Timeslot;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Custom move that changes a lesson's timeslot.
 * Used by {@link NearbyMoveFactory} to create nearby-biased timeslot changes.
 */
public class TimeslotChangeMove implements Move<TimeTable> {

    private final Lesson lesson;
    private final Timeslot newTimeslot;

    public TimeslotChangeMove(Lesson lesson, Timeslot newTimeslot) {
        this.lesson = lesson;
        this.newTimeslot = newTimeslot;
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<TimeTable> scoreDirector) {
        if (lesson.isPinned()) {
            return false;
        }
        return !Objects.equals(lesson.getTimeslot(), newTimeslot);
    }

    @Override
    public void doMoveOnly(ScoreDirector<TimeTable> scoreDirector) {
        scoreDirector.beforeVariableChanged(lesson, "timeslot");
        lesson.setTimeslot(newTimeslot);
        scoreDirector.afterVariableChanged(lesson, "timeslot");
    }

    @Override
    public TimeslotChangeMove rebase(ScoreDirector<TimeTable> destinationScoreDirector) {
        return new TimeslotChangeMove(
                destinationScoreDirector.lookUpWorkingObject(lesson),
                destinationScoreDirector.lookUpWorkingObject(newTimeslot));
    }

    @Override
    public Collection<?> getPlanningEntities() {
        return List.of(lesson);
    }

    @Override
    public Collection<?> getPlanningValues() {
        return List.of(newTimeslot);
    }

    public Lesson getLesson() {
        return lesson;
    }

    public Timeslot getNewTimeslot() {
        return newTimeslot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof TimeslotChangeMove other))
            return false;
        return Objects.equals(lesson, other.lesson)
                && Objects.equals(newTimeslot, other.newTimeslot);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lesson, newTimeslot);
    }

    @Override
    public String toString() {
        return "TimeslotChange(lesson=" + lesson.getId()
                + ", " + lesson.getTimeslot() + " → " + newTimeslot + ")";
    }
}
