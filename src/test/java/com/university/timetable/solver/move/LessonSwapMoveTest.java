package com.university.timetable.solver.move;

import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.Timeslot;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link LessonSwapMove}.
 */
class LessonSwapMoveTest {

    @Test
    void isMoveDoable_differentAssignments_returnsTrue() {
        Lesson a = lesson(1L, timeslot(1L, DayOfWeek.MONDAY, 9), room(1L, 50), false);
        Lesson b = lesson(2L, timeslot(2L, DayOfWeek.TUESDAY, 10), room(2L, 60), false);

        LessonSwapMove move = new LessonSwapMove(a, b);
        assertThat(move.isMoveDoable(null)).isTrue();
    }

    @Test
    void isMoveDoable_identicalAssignments_returnsFalse() {
        Timeslot ts = timeslot(1L, DayOfWeek.MONDAY, 9);
        Room r = room(1L, 50);
        Lesson a = lesson(1L, ts, r, false);
        Lesson b = lesson(2L, ts, r, false);

        LessonSwapMove move = new LessonSwapMove(a, b);
        assertThat(move.isMoveDoable(null)).isFalse();
    }

    @Test
    void isMoveDoable_eitherPinned_returnsFalse() {
        Lesson a = lesson(1L, timeslot(1L, DayOfWeek.MONDAY, 9), room(1L, 50), true);
        Lesson b = lesson(2L, timeslot(2L, DayOfWeek.TUESDAY, 10), room(2L, 60), false);

        assertThat(new LessonSwapMove(a, b).isMoveDoable(null)).isFalse();
        assertThat(new LessonSwapMove(b, a).isMoveDoable(null)).isFalse();

        // Both pinned
        Lesson c = lesson(3L, timeslot(3L, DayOfWeek.WEDNESDAY, 11), room(3L, 70), true);
        assertThat(new LessonSwapMove(a, c).isMoveDoable(null)).isFalse();
    }

    @Test
    void isMoveDoable_differentTimeslotSameRoom_returnsTrue() {
        Room r = room(1L, 50);
        Lesson a = lesson(1L, timeslot(1L, DayOfWeek.MONDAY, 9), r, false);
        Lesson b = lesson(2L, timeslot(2L, DayOfWeek.TUESDAY, 10), r, false);

        assertThat(new LessonSwapMove(a, b).isMoveDoable(null)).isTrue();
    }

    @Test
    void isMoveDoable_partiallyUnassigned_returnsTrue() {
        Lesson a = lesson(1L, timeslot(1L, DayOfWeek.MONDAY, 9), null, false);
        Lesson b = lesson(2L, null, room(1L, 50), false);

        assertThat(new LessonSwapMove(a, b).isMoveDoable(null)).isTrue();
    }

    @Test
    void equals_symmetricPair_returnsTrue() {
        Lesson a = lesson(1L, null, null, false);
        Lesson b = lesson(2L, null, null, false);

        LessonSwapMove moveAB = new LessonSwapMove(a, b);
        LessonSwapMove moveBA = new LessonSwapMove(b, a);

        assertThat(moveAB).isEqualTo(moveBA);
        assertThat(moveAB.hashCode()).isEqualTo(moveBA.hashCode());
    }

    @Test
    void equals_differentLessons_returnsFalse() {
        Lesson a = lesson(1L, null, null, false);
        Lesson b = lesson(2L, null, null, false);
        Lesson c = lesson(3L, null, null, false);

        LessonSwapMove moveAB = new LessonSwapMove(a, b);
        LessonSwapMove moveAC = new LessonSwapMove(a, c);
        assertThat(moveAB).isNotEqualTo(moveAC);
    }

    @Test
    void toString_containsBothLessonIds() {
        Lesson a = lesson(10L, null, null, false);
        Lesson b = lesson(20L, null, null, false);

        LessonSwapMove move = new LessonSwapMove(a, b);
        assertThat(move.toString()).contains("10").contains("20");
    }

    // ---- Helpers ----

    private static Lesson lesson(Long id, Timeslot timeslot, Room room, boolean pinned) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setTimeslot(timeslot);
        lesson.setRoom(room);
        lesson.setPinned(pinned);
        return lesson;
    }

    private static Timeslot timeslot(Long id, DayOfWeek day, int hour) {
        Timeslot ts = new Timeslot(day, LocalTime.of(hour, 0));
        ts.setId(id);
        
        
        return ts;
    }

    private static Room room(Long id, int capacity) {
        Room room = new Room();
        room.setId(id);
        room.setCapacity(capacity);
        return room;
    }
}
