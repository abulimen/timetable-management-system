package com.university.timetable.solver.move;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.TimeTable;
import com.university.timetable.domain.Timeslot;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Custom move that atomically swaps both timeslot and room between two lessons.
 * Used by {@link NearbyMoveFactory} to create nearby-biased swap moves.
 */
public class LessonSwapMove implements Move<TimeTable> {

    private final Lesson lessonA;
    private final Lesson lessonB;

    public LessonSwapMove(Lesson lessonA, Lesson lessonB) {
        this.lessonA = lessonA;
        this.lessonB = lessonB;
    }

    @Override
    public boolean isMoveDoable(ScoreDirector<TimeTable> scoreDirector) {
        if (lessonA.isPinned() || lessonB.isPinned()) {
            return false;
        }
        // Not doable if both variables are identical (no-op swap)
        return !Objects.equals(lessonA.getTimeslot(), lessonB.getTimeslot())
                || !Objects.equals(lessonA.getRoom(), lessonB.getRoom());
    }

    @Override
    public void doMoveOnly(ScoreDirector<TimeTable> scoreDirector) {
        Timeslot oldTimeslotA = lessonA.getTimeslot();
        Timeslot oldTimeslotB = lessonB.getTimeslot();
        Room oldRoomA = lessonA.getRoom();
        Room oldRoomB = lessonB.getRoom();

        // Swap timeslots
        scoreDirector.beforeVariableChanged(lessonA, "timeslot");
        lessonA.setTimeslot(oldTimeslotB);
        scoreDirector.afterVariableChanged(lessonA, "timeslot");

        scoreDirector.beforeVariableChanged(lessonB, "timeslot");
        lessonB.setTimeslot(oldTimeslotA);
        scoreDirector.afterVariableChanged(lessonB, "timeslot");

        // Swap rooms
        scoreDirector.beforeVariableChanged(lessonA, "room");
        lessonA.setRoom(oldRoomB);
        scoreDirector.afterVariableChanged(lessonA, "room");

        scoreDirector.beforeVariableChanged(lessonB, "room");
        lessonB.setRoom(oldRoomA);
        scoreDirector.afterVariableChanged(lessonB, "room");
    }

    @Override
    public LessonSwapMove rebase(ScoreDirector<TimeTable> destinationScoreDirector) {
        return new LessonSwapMove(
                destinationScoreDirector.lookUpWorkingObject(lessonA),
                destinationScoreDirector.lookUpWorkingObject(lessonB));
    }

    @Override
    public Collection<?> getPlanningEntities() {
        return List.of(lessonA, lessonB);
    }

    @Override
    public Collection<?> getPlanningValues() {
        return List.of();
    }

    public Lesson getLessonA() {
        return lessonA;
    }

    public Lesson getLessonB() {
        return lessonB;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof LessonSwapMove other))
            return false;
        // Symmetric: swap(A,B) == swap(B,A)
        return (Objects.equals(lessonA, other.lessonA) && Objects.equals(lessonB, other.lessonB))
                || (Objects.equals(lessonA, other.lessonB) && Objects.equals(lessonB, other.lessonA));
    }

    @Override
    public int hashCode() {
        // Symmetric hash: must be the same for swap(A,B) and swap(B,A)
        return Objects.hash(lessonA, lessonB) + Objects.hash(lessonB, lessonA);
    }

    @Override
    public String toString() {
        return "LessonSwap(lesson=" + lessonA.getId() + " ↔ lesson=" + lessonB.getId() + ")";
    }
}
