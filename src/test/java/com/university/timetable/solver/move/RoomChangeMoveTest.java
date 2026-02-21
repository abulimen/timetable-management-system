package com.university.timetable.solver.move;

import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.Zone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RoomChangeMove}.
 */
class RoomChangeMoveTest {

    @Test
    void isMoveDoable_differentRoom_returnsTrue() {
        Lesson lesson = lesson(1L, room(1L, "R101", 50), false);
        Room newRoom = room(2L, "R102", 60);

        RoomChangeMove move = new RoomChangeMove(lesson, newRoom);
        assertThat(move.isMoveDoable(null)).isTrue();
    }

    @Test
    void isMoveDoable_sameRoom_returnsFalse() {
        Room r = room(1L, "R101", 50);
        Lesson lesson = lesson(1L, r, false);

        RoomChangeMove move = new RoomChangeMove(lesson, r);
        assertThat(move.isMoveDoable(null)).isFalse();
    }

    @Test
    void isMoveDoable_pinnedLesson_returnsFalse() {
        Lesson lesson = lesson(1L, room(1L, "R101", 50), true);
        Room newRoom = room(2L, "R102", 60);

        RoomChangeMove move = new RoomChangeMove(lesson, newRoom);
        assertThat(move.isMoveDoable(null)).isFalse();
    }

    @Test
    void isMoveDoable_nullCurrentRoom_returnsTrue() {
        Lesson lesson = lesson(1L, null, false);
        Room newRoom = room(1L, "R101", 50);

        RoomChangeMove move = new RoomChangeMove(lesson, newRoom);
        assertThat(move.isMoveDoable(null)).isTrue();
    }

    @Test
    void equals_sameMove_returnsTrue() {
        Lesson lesson = lesson(1L, null, false);
        Room r = room(1L, "R101", 50);

        RoomChangeMove move1 = new RoomChangeMove(lesson, r);
        RoomChangeMove move2 = new RoomChangeMove(lesson, r);
        assertThat(move1).isEqualTo(move2);
        assertThat(move1.hashCode()).isEqualTo(move2.hashCode());
    }

    @Test
    void equals_differentMove_returnsFalse() {
        Lesson lesson = lesson(1L, null, false);

        RoomChangeMove move1 = new RoomChangeMove(lesson, room(1L, "R101", 50));
        RoomChangeMove move2 = new RoomChangeMove(lesson, room(2L, "R102", 60));
        assertThat(move1).isNotEqualTo(move2);
    }

    @Test
    void toString_containsLessonId() {
        Lesson lesson = lesson(42L, room(1L, "R101", 50), false);
        Room newRoom = room(2L, "R102", 60);

        RoomChangeMove move = new RoomChangeMove(lesson, newRoom);
        assertThat(move.toString()).contains("42");
    }

    // ---- Helpers ----

    private static Lesson lesson(Long id, Room room, boolean pinned) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setRoom(room);
        lesson.setPinned(pinned);
        return lesson;
    }

    private static Room room(Long id, String name, int capacity) {
        Room room = new Room();
        room.setId(id);
        room.setName(name);
        room.setCapacity(capacity);
        return room;
    }
}
