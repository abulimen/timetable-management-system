package com.university.timetable.solver.move;

import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Timeslot;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TimeslotChangeMove}.
 * Tests move doability, equality, and toString without a live solver.
 */
class TimeslotChangeMoveTest {

    @Test
    void isMoveDoable_differentTimeslot_returnsTrue() {
        Lesson lesson = lesson(1L, timeslot(1L, DayOfWeek.MONDAY, 9), false);
        Timeslot newTimeslot = timeslot(2L, DayOfWeek.MONDAY, 10);

        TimeslotChangeMove move = new TimeslotChangeMove(lesson, newTimeslot);
        assertThat(move.isMoveDoable(null)).isTrue();
    }

    @Test
    void isMoveDoable_sameTimeslot_returnsFalse() {
        Timeslot ts = timeslot(1L, DayOfWeek.MONDAY, 9);
        Lesson lesson = lesson(1L, ts, false);

        TimeslotChangeMove move = new TimeslotChangeMove(lesson, ts);
        assertThat(move.isMoveDoable(null)).isFalse();
    }

    @Test
    void isMoveDoable_pinnedLesson_returnsFalse() {
        Lesson lesson = lesson(1L, timeslot(1L, DayOfWeek.MONDAY, 9), true);
        Timeslot newTimeslot = timeslot(2L, DayOfWeek.TUESDAY, 10);

        TimeslotChangeMove move = new TimeslotChangeMove(lesson, newTimeslot);
        assertThat(move.isMoveDoable(null)).isFalse();
    }

    @Test
    void isMoveDoable_nullCurrentTimeslot_returnsTrue() {
        Lesson lesson = lesson(1L, null, false);
        Timeslot newTimeslot = timeslot(1L, DayOfWeek.MONDAY, 9);

        TimeslotChangeMove move = new TimeslotChangeMove(lesson, newTimeslot);
        assertThat(move.isMoveDoable(null)).isTrue();
    }

    @Test
    void equals_sameMove_returnsTrue() {
        Lesson lesson = lesson(1L, null, false);
        Timeslot ts = timeslot(1L, DayOfWeek.MONDAY, 9);

        TimeslotChangeMove move1 = new TimeslotChangeMove(lesson, ts);
        TimeslotChangeMove move2 = new TimeslotChangeMove(lesson, ts);
        assertThat(move1).isEqualTo(move2);
        assertThat(move1.hashCode()).isEqualTo(move2.hashCode());
    }

    @Test
    void equals_differentMove_returnsFalse() {
        Lesson lesson = lesson(1L, null, false);
        Timeslot ts1 = timeslot(1L, DayOfWeek.MONDAY, 9);
        Timeslot ts2 = timeslot(2L, DayOfWeek.TUESDAY, 10);

        TimeslotChangeMove move1 = new TimeslotChangeMove(lesson, ts1);
        TimeslotChangeMove move2 = new TimeslotChangeMove(lesson, ts2);
        assertThat(move1).isNotEqualTo(move2);
    }

    @Test
    void toString_containsLessonIdAndTimeslots() {
        Lesson lesson = lesson(42L, timeslot(1L, DayOfWeek.MONDAY, 9), false);
        Timeslot newTs = timeslot(2L, DayOfWeek.TUESDAY, 10);

        TimeslotChangeMove move = new TimeslotChangeMove(lesson, newTs);
        assertThat(move.toString()).contains("42");
    }

    // ---- Helpers ----

    private static Lesson lesson(Long id, Timeslot timeslot, boolean pinned) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setTimeslot(timeslot);
        lesson.setPinned(pinned);
        return lesson;
    }

    private static Timeslot timeslot(Long id, DayOfWeek day, int hour) {
        Timeslot ts = new Timeslot(day, LocalTime.of(hour, 0));
        ts.setId(id);
        
        
        return ts;
    }
}
