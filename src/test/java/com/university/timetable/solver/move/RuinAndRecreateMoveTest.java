package com.university.timetable.solver.move;

import com.university.timetable.domain.*;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RuinAndRecreateMove}.
 */
class RuinAndRecreateMoveTest {

    @Test
    void isMoveDoable_clusterOfOne_returnsFalse() {
        Lesson lesson = lesson(1L, timeslot(1L, DayOfWeek.MONDAY, 9), room(1L, 50), false);
        RuinAndRecreateMove move = new RuinAndRecreateMove(
                List.of(lesson), List.of(), List.of());

        assertThat(move.isMoveDoable(null)).isFalse();
    }

    @Test
    void isMoveDoable_clusterOfTwo_returnsTrue() {
        RuinAndRecreateMove move = new RuinAndRecreateMove(List.of(
                lesson(1L, timeslot(1L, DayOfWeek.MONDAY, 9), room(1L, 50), false),
                lesson(2L, timeslot(2L, DayOfWeek.TUESDAY, 10), room(2L, 60), false)),
                List.of(), List.of());

        assertThat(move.isMoveDoable(null)).isTrue();
    }

    @Test
    void isMoveDoable_allUnassigned_returnsFalse() {
        RuinAndRecreateMove move = new RuinAndRecreateMove(List.of(
                lesson(1L, null, null, false),
                lesson(2L, null, null, false)),
                List.of(), List.of());

        assertThat(move.isMoveDoable(null)).isFalse();
    }

    @Test
    void getCluster_returnsCluster() {
        Lesson l1 = lesson(1L, null, null, false);
        Lesson l2 = lesson(2L, null, null, false);
        RuinAndRecreateMove move = new RuinAndRecreateMove(
                List.of(l1, l2), List.of(), List.of());

        assertThat(move.getCluster()).containsExactly(l1, l2);
    }

    @Test
    void toString_containsClusterSize() {
        RuinAndRecreateMove move = new RuinAndRecreateMove(List.of(
                lesson(1L, timeslot(1L, DayOfWeek.MONDAY, 9), null, false),
                lesson(2L, null, null, false),
                lesson(3L, null, null, false)),
                List.of(), List.of());

        assertThat(move.toString()).contains("3");
    }

    // ---- Helpers ----

    private static Lesson lesson(Long id, Timeslot ts, Room room, boolean pinned) {
        Lesson l = new Lesson();
        l.setId(id);
        l.setTimeslot(ts);
        l.setRoom(room);
        l.setPinned(pinned);
        return l;
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
