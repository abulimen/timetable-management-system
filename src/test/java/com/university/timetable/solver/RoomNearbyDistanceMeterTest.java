package com.university.timetable.solver;

import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.Zone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RoomNearbyDistanceMeter}.
 */
class RoomNearbyDistanceMeterTest {

    private final RoomNearbyDistanceMeter meter = new RoomNearbyDistanceMeter();

    @Test
    void sameRoom_returnsZero() {
        Room room = room(1L, 50, zone(1L));
        Lesson lesson = lessonWithRoom(room);

        assertThat(meter.getNearbyDistance(lesson, room)).isEqualTo(0.0);
    }

    @Test
    void sameZoneSimilarCapacity_returnsSmall() {
        Zone z = zone(1L);
        Room origin = room(1L, 50, z);
        Room nearby = room(2L, 55, z);
        Lesson lesson = lessonWithRoom(origin);

        double distance = meter.getNearbyDistance(lesson, nearby);
        assertThat(distance).isLessThan(10.0);
        // Expected: 1.0 + (5/10.0) = 1.5
        assertThat(distance).isEqualTo(1.5);
    }

    @Test
    void differentZone_returnsLarge() {
        Room origin = room(1L, 50, zone(1L));
        Room farRoom = room(2L, 60, zone(2L));
        Lesson lesson = lessonWithRoom(origin);

        double distance = meter.getNearbyDistance(lesson, farRoom);
        assertThat(distance).isGreaterThanOrEqualTo(100.0);
        // Expected: 100.0 + (10/10.0) = 101.0
        assertThat(distance).isEqualTo(101.0);
    }

    @Test
    void nullOriginRoom_returnsMaxValue() {
        Lesson lesson = new Lesson();
        lesson.setId(1L);
        // No room set

        assertThat(meter.getNearbyDistance(lesson, room(1L, 50, zone(1L))))
                .isEqualTo(Double.MAX_VALUE);
    }

    @Test
    void ordering_sameZoneIsCloserThanDifferentZone() {
        Zone z = zone(1L);
        Room origin = room(1L, 50, z);
        Room sameZone = room(2L, 100, z); // Big capacity diff, but same zone
        Room diffZone = room(3L, 51, zone(2L)); // Tiny capacity diff, different zone
        Lesson lesson = lessonWithRoom(origin);

        double distSameZone = meter.getNearbyDistance(lesson, sameZone);
        double distDiffZone = meter.getNearbyDistance(lesson, diffZone);

        assertThat(distSameZone).isLessThan(distDiffZone);
    }

    // ---- Helpers ----

    private static Lesson lessonWithRoom(Room room) {
        Lesson lesson = new Lesson();
        lesson.setId(1L);
        lesson.setRoom(room);
        return lesson;
    }

    private static Room room(Long id, int capacity, Zone zone) {
        Room room = new Room();
        room.setId(id);
        room.setCapacity(capacity);
        room.setZone(zone);
        return room;
    }

    private static Zone zone(Long id) {
        Zone zone = new Zone();
        zone.setId(id);
        zone.setName("Zone " + id);
        return zone;
    }
}
