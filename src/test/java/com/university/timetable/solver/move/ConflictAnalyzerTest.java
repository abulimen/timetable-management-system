package com.university.timetable.solver.move;

import com.university.timetable.domain.*;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ConflictAnalyzer}.
 */
class ConflictAnalyzerTest {

    @Test
    void findConflictingLessons_noConflicts_returnsEmpty() {
        TimeTable tt = new TimeTable();
        tt.setLessons(List.of(
                lesson(1L, timeslot(1L, DayOfWeek.MONDAY, 9), room(1L, 50), lecturer(1L), false),
                lesson(2L, timeslot(2L, DayOfWeek.TUESDAY, 10), room(2L, 60), lecturer(2L), false)));
        tt.setTimeslots(List.of());
        tt.setRooms(List.of());

        List<Lesson> result = ConflictAnalyzer.findConflictingLessons(tt);
        assertThat(result).isEmpty();
    }

    @Test
    void findConflictingLessons_roomConflict_returnsBothLessons() {
        Timeslot ts = timeslot(1L, DayOfWeek.MONDAY, 9);
        Room r = room(1L, 50);
        TimeTable tt = new TimeTable();
        tt.setLessons(List.of(
                lesson(1L, ts, r, lecturer(1L), false),
                lesson(2L, ts, r, lecturer(2L), false)));
        tt.setTimeslots(List.of());
        tt.setRooms(List.of());

        List<Lesson> result = ConflictAnalyzer.findConflictingLessons(tt);
        assertThat(result).hasSize(2);
    }

    @Test
    void findConflictingLessons_lecturerConflict_returnsBothLessons() {
        Lecturer lec = lecturer(1L);
        Timeslot ts = timeslot(1L, DayOfWeek.MONDAY, 9);
        TimeTable tt = new TimeTable();
        tt.setLessons(List.of(
                lesson(1L, ts, room(1L, 50), lec, false),
                lesson(2L, ts, room(2L, 60), lec, false)));
        tt.setTimeslots(List.of());
        tt.setRooms(List.of());

        List<Lesson> result = ConflictAnalyzer.findConflictingLessons(tt);
        assertThat(result).hasSize(2);
    }

    @Test
    void findConflictingLessons_pinnedLessonsExcluded() {
        Timeslot ts = timeslot(1L, DayOfWeek.MONDAY, 9);
        Room r = room(1L, 50);
        TimeTable tt = new TimeTable();
        tt.setLessons(List.of(
                lesson(1L, ts, r, lecturer(1L), true), // pinned
                lesson(2L, ts, r, lecturer(2L), false)));
        tt.setTimeslots(List.of());
        tt.setRooms(List.of());

        List<Lesson> result = ConflictAnalyzer.findConflictingLessons(tt);
        assertThat(result).isEmpty(); // Pinned lesson is excluded from analysis
    }

    @Test
    void findConflictingLessons_mostBlamedFirst() {
        Timeslot ts = timeslot(1L, DayOfWeek.MONDAY, 9);
        Room r = room(1L, 50);
        Lecturer lec = lecturer(1L);
        TimeTable tt = new TimeTable();
        // Lesson 1 has both room AND lecturer conflict with lesson 2
        // Lesson 3 only has room conflict with lesson 2
        tt.setLessons(List.of(
                lesson(1L, ts, r, lec, false),
                lesson(2L, ts, r, lec, false),
                lesson(3L, ts, r, lecturer(3L), false)));
        tt.setTimeslots(List.of());
        tt.setRooms(List.of());

        List<Lesson> result = ConflictAnalyzer.findConflictingLessons(tt);
        assertThat(result).isNotEmpty();
        // Lessons 1 and 2 should be more blamed than lesson 3
        assertThat(result.get(0).getId()).isIn(1L, 2L);
    }

    @Test
    void buildConflictCluster_includesSeedAndPartners() {
        Timeslot ts = timeslot(1L, DayOfWeek.MONDAY, 9);
        Room r = room(1L, 50);
        Lesson seed = lesson(1L, ts, r, lecturer(1L), false);
        Lesson partner = lesson(2L, ts, r, lecturer(2L), false);
        Lesson unrelated = lesson(3L, timeslot(2L, DayOfWeek.TUESDAY, 10), room(2L, 60), lecturer(3L), false);

        List<Lesson> allLessons = new ArrayList<>(List.of(seed, partner, unrelated));

        List<Lesson> cluster = ConflictAnalyzer.buildConflictCluster(seed, allLessons, 10);
        assertThat(cluster).contains(seed, partner);
        assertThat(cluster).doesNotContain(unrelated);
    }

    @Test
    void buildConflictCluster_respectsMaxSize() {
        Timeslot ts = timeslot(1L, DayOfWeek.MONDAY, 9);
        Room r = room(1L, 50);
        List<Lesson> allLessons = new ArrayList<>();
        for (long i = 1; i <= 20; i++) {
            allLessons.add(lesson(i, ts, r, lecturer(i), false));
        }

        List<Lesson> cluster = ConflictAnalyzer.buildConflictCluster(allLessons.get(0), allLessons, 5);
        assertThat(cluster).hasSizeLessThanOrEqualTo(5);
    }

    // ---- Helpers ----

    private static Lesson lesson(Long id, Timeslot ts, Room room, Lecturer lec, boolean pinned) {
        Lesson l = new Lesson();
        l.setId(id);
        l.setTimeslot(ts);
        l.setRoom(room);
        l.setLecturer(lec);
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

    private static Lecturer lecturer(Long id) {
        Lecturer l = new Lecturer();
        l.setId(id);
        return l;
    }
}
