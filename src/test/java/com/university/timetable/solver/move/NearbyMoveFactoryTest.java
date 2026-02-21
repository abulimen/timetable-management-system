package com.university.timetable.solver.move;

import ai.timefold.solver.core.impl.heuristic.move.Move;
import com.university.timetable.domain.*;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link NearbyMoveFactory}.
 * Tests move generation, nearby bias, and edge cases.
 */
class NearbyMoveFactoryTest {

    private final NearbyMoveFactory factory = new NearbyMoveFactory();

    @Test
    void createMoveList_returnsNonEmpty() {
        TimeTable problem = createSmallProblem();

        List<? extends Move<TimeTable>> moves = factory.createMoveList(problem);

        assertThat(moves).isNotEmpty();
    }

    @Test
    void createMoveList_excludesPinnedLessons() {
        TimeTable problem = createSmallProblem();
        // Pin all lessons
        problem.getLessons().forEach(l -> l.setPinned(true));

        List<? extends Move<TimeTable>> moves = factory.createMoveList(problem);

        assertThat(moves).isEmpty();
    }

    @Test
    void createMoveList_containsTimeslotChangeMoves() {
        TimeTable problem = createSmallProblem();

        List<? extends Move<TimeTable>> moves = factory.createMoveList(problem);

        boolean hasTimeslotChange = moves.stream()
                .anyMatch(m -> m instanceof TimeslotChangeMove);
        assertThat(hasTimeslotChange).isTrue();
    }

    @Test
    void createMoveList_containsRoomChangeMoves() {
        TimeTable problem = createSmallProblem();

        List<? extends Move<TimeTable>> moves = factory.createMoveList(problem);

        boolean hasRoomChange = moves.stream()
                .anyMatch(m -> m instanceof RoomChangeMove);
        assertThat(hasRoomChange).isTrue();
    }

    @Test
    void createMoveList_containsSwapMoves() {
        TimeTable problem = createSmallProblem();

        List<? extends Move<TimeTable>> moves = factory.createMoveList(problem);

        boolean hasSwap = moves.stream()
                .anyMatch(m -> m instanceof LessonSwapMove);
        assertThat(hasSwap).isTrue();
    }

    @Test
    void createMoveList_emptyLessons_returnsEmpty() {
        TimeTable problem = new TimeTable();
        problem.setLessons(new ArrayList<>());
        problem.setTimeslots(List.of(timeslot(1L, DayOfWeek.MONDAY, 9)));
        problem.setRooms(List.of(room(1L, "R1", 50, null)));

        List<? extends Move<TimeTable>> moves = factory.createMoveList(problem);

        assertThat(moves).isEmpty();
    }

    @Test
    void createMoveList_allPinned_returnsEmpty() {
        TimeTable problem = createSmallProblem();
        problem.getLessons().forEach(l -> l.setPinned(true));

        List<? extends Move<TimeTable>> moves = factory.createMoveList(problem);

        assertThat(moves).isEmpty();
    }

    @Test
    void createMoveList_nearbyBias_timeslotMoveFavorsCloseTimeslots() {
        // Create a problem with a lesson at Monday 9am
        // and timeslots ranging from Monday 8am to Friday 4pm
        TimeTable problem = new TimeTable();

        List<Timeslot> timeslots = new ArrayList<>();
        long tsId = 1L;
        for (DayOfWeek day : List.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            for (int hour = 8; hour <= 16; hour++) {
                timeslots.add(timeslot(tsId++, day, hour));
            }
        }
        problem.setTimeslots(timeslots);

        Room r1 = room(1L, "R1", 50, null);
        problem.setRooms(List.of(r1));

        // Lesson at Monday 9am
        Timeslot mondayNine = timeslots.stream()
                .filter(ts -> ts.getDayOfWeek() == DayOfWeek.MONDAY && ts.getStartTime().getHour() == 9)
                .findFirst().orElseThrow();

        Lesson lesson = lesson(1L, mondayNine, r1, false);
        problem.setLessons(List.of(lesson));

        List<? extends Move<TimeTable>> moves = factory.createMoveList(problem);

        // Extract timeslot change moves
        List<TimeslotChangeMove> timeslotMoves = moves.stream()
                .filter(m -> m instanceof TimeslotChangeMove)
                .map(m -> (TimeslotChangeMove) m)
                .toList();

        assertThat(timeslotMoves).isNotEmpty();

        // Verify nearby bias: most timeslot moves should be Monday/Tuesday (nearby)
        long nearbyCount = timeslotMoves.stream()
                .filter(m -> {
                    DayOfWeek day = m.getNewTimeslot().getDayOfWeek();
                    return day == DayOfWeek.MONDAY || day == DayOfWeek.TUESDAY;
                })
                .count();

        // At least half of the moves should be Monday/Tuesday (nearby)
        assertThat(nearbyCount).isGreaterThanOrEqualTo(timeslotMoves.size() / 2);
    }

    @Test
    void createMoveList_nearbyBias_roomMoveFavorsSameZone() {
        TimeTable problem = new TimeTable();

        Zone zoneA = new Zone();
        zoneA.setId(1L);
        zoneA.setName("Zone A");

        Zone zoneB = new Zone();
        zoneB.setId(2L);
        zoneB.setName("Zone B");

        // 3 rooms in Zone A, 5 rooms in Zone B
        List<Room> rooms = new ArrayList<>();
        rooms.add(room(1L, "A-101", 50, zoneA));
        rooms.add(room(2L, "A-102", 55, zoneA));
        rooms.add(room(3L, "A-103", 60, zoneA));
        rooms.add(room(4L, "B-201", 100, zoneB));
        rooms.add(room(5L, "B-202", 120, zoneB));
        rooms.add(room(6L, "B-203", 140, zoneB));
        rooms.add(room(7L, "B-204", 160, zoneB));
        rooms.add(room(8L, "B-205", 180, zoneB));
        problem.setRooms(rooms);

        Timeslot ts = timeslot(1L, DayOfWeek.MONDAY, 9);
        problem.setTimeslots(List.of(ts));

        // Lesson in Zone A, room A-101
        Lesson lesson = lesson(1L, ts, rooms.get(0), false);
        problem.setLessons(List.of(lesson));

        List<? extends Move<TimeTable>> moves = factory.createMoveList(problem);

        List<RoomChangeMove> roomMoves = moves.stream()
                .filter(m -> m instanceof RoomChangeMove)
                .map(m -> (RoomChangeMove) m)
                .toList();

        assertThat(roomMoves).isNotEmpty();

        // Since ROOM_NEARBY_LIMIT is 8, and we have 7 non-current rooms total,
        // all should be included. But the Zone A rooms should be listed.
        long zoneACount = roomMoves.stream()
                .filter(m -> m.getNewRoom().getZone() != null
                        && m.getNewRoom().getZone().getId().equals(1L))
                .count();

        // All 2 remaining Zone A rooms should be present
        assertThat(zoneACount).isEqualTo(2);
    }

    // ---- Problem builders ----

    private TimeTable createSmallProblem() {
        TimeTable problem = new TimeTable();

        List<Timeslot> timeslots = List.of(
                timeslot(1L, DayOfWeek.MONDAY, 8),
                timeslot(2L, DayOfWeek.MONDAY, 9),
                timeslot(3L, DayOfWeek.MONDAY, 10),
                timeslot(4L, DayOfWeek.TUESDAY, 8),
                timeslot(5L, DayOfWeek.TUESDAY, 9));
        problem.setTimeslots(timeslots);

        List<Room> rooms = List.of(
                room(1L, "R101", 50, null),
                room(2L, "R102", 60, null),
                room(3L, "R103", 100, null));
        problem.setRooms(rooms);

        List<Lesson> lessons = List.of(
                lesson(1L, timeslots.get(0), rooms.get(0), false),
                lesson(2L, timeslots.get(1), rooms.get(1), false),
                lesson(3L, timeslots.get(2), rooms.get(2), false));
        problem.setLessons(new ArrayList<>(lessons));

        return problem;
    }

    // ---- Entity factories ----

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

    private static Room room(Long id, String name, int capacity, Zone zone) {
        Room room = new Room();
        room.setId(id);
        room.setName(name);
        room.setCapacity(capacity);
        room.setZone(zone);
        return room;
    }
}
