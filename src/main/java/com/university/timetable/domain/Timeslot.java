package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Timeslot entity representing a valid scheduling slot.
 * Uses the "Missing Tooth" pattern - excludes 12:00-13:00 lunch period.
 * 
 * Based on design.md Timeslot Entity:
 * - Mon-Thu: 07:00-11:00, 13:00-17:00
 * - Friday: 07:00-11:00 only
 */
@Entity
@Table(name = "timeslot", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"day_of_week", "start_time"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Timeslot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    @EqualsAndHashCode.Include
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, columnDefinition = "VARCHAR(10)")
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    public Timeslot(DayOfWeek dayOfWeek, LocalTime startTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
    }

    /**
     * Get the end time for a lesson with specified duration.
     * Derived value, not persisted.
     */
    public LocalTime getEndTime(int durationHours) {
        return startTime.plusHours(durationHours);
    }

    @Override
    public String toString() {
        return dayOfWeek.toString().substring(0, 3) + " " + startTime;
    }
}
