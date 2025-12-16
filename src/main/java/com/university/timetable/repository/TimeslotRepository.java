package com.university.timetable.repository;

import com.university.timetable.domain.Timeslot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TimeslotRepository extends JpaRepository<Timeslot, Long> {
    List<Timeslot> findByDayOfWeek(DayOfWeek dayOfWeek);
    Optional<Timeslot> findByDayOfWeekAndStartTime(DayOfWeek dayOfWeek, LocalTime startTime);
}
