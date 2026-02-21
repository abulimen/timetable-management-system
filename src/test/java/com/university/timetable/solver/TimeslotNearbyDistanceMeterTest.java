package com.university.timetable.solver;

import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Timeslot;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TimeslotNearbyDistanceMeter}.
 */
class TimeslotNearbyDistanceMeterTest {

    private final TimeslotNearbyDistanceMeter meter = new TimeslotNearbyDistanceMeter();

    @Test
    void sameTimeslot_returnsZero() {
        Timeslot ts = timeslot(1L, DayOfWeek.MONDAY, 9);
        Lesson lesson = lessonWithTimeslot(ts);

        assertThat(meter.getNearbyDistance(lesson, ts)).isEqualTo(0.0);
    }

    @Test
    void sameDayDifferentHour_returnsHourDiff() {
        Lesson lesson = lessonWithTimeslot(timeslot(1L, DayOfWeek.MONDAY, 9));

        assertThat(meter.getNearbyDistance(lesson, timeslot(2L, DayOfWeek.MONDAY, 11))).isEqualTo(2.0);
        assertThat(meter.getNearbyDistance(lesson, timeslot(3L, DayOfWeek.MONDAY, 7))).isEqualTo(2.0);
    }

    @Test
    void differentDay_returnsWeightedDistance() {
        Lesson lesson = lessonWithTimeslot(timeslot(1L, DayOfWeek.MONDAY, 9));

        // Tuesday same hour: 1 day * 12 + 0 hour = 12
        assertThat(meter.getNearbyDistance(lesson, timeslot(2L, DayOfWeek.TUESDAY, 9))).isEqualTo(12.0);

        // Wednesday different hour: 2 days * 12 + 2 hours = 26
        assertThat(meter.getNearbyDistance(lesson, timeslot(3L, DayOfWeek.WEDNESDAY, 11))).isEqualTo(26.0);
    }

    @Test
    void nullOriginTimeslot_returnsMaxValue() {
        Lesson lesson = new Lesson();
        lesson.setId(1L);
        // No timeslot set

        assertThat(meter.getNearbyDistance(lesson, timeslot(1L, DayOfWeek.MONDAY, 9)))
                .isEqualTo(Double.MAX_VALUE);
    }

    @Test
    void ordering_closerTimeslotHasSmallerDistance() {
        Lesson lesson = lessonWithTimeslot(timeslot(1L, DayOfWeek.MONDAY, 9));

        double distSameDay = meter.getNearbyDistance(lesson, timeslot(2L, DayOfWeek.MONDAY, 10));
        double distNextDay = meter.getNearbyDistance(lesson, timeslot(3L, DayOfWeek.TUESDAY, 10));
        double distFriday = meter.getNearbyDistance(lesson, timeslot(4L, DayOfWeek.FRIDAY, 16));

        assertThat(distSameDay).isLessThan(distNextDay);
        assertThat(distNextDay).isLessThan(distFriday);
    }

    // ---- Helpers ----

    private static Lesson lessonWithTimeslot(Timeslot ts) {
        Lesson lesson = new Lesson();
        lesson.setId(1L);
        lesson.setTimeslot(ts);
        return lesson;
    }

    private static Timeslot timeslot(Long id, DayOfWeek day, int hour) {
        Timeslot ts = new Timeslot(day, LocalTime.of(hour, 0));
        ts.setId(id);
        return ts;
    }
}
