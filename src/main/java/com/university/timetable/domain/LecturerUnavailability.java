package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * LecturerUnavailability entity representing blackout periods.
 * Lecturers cannot be scheduled during these periods.
 * 
 * Based on design.md: "Lecturers have 'Blackout Periods' 
 * (e.g., 'Dr. Smith is unavailable Mon 7-11')"
 */
@Entity
@Table(name = "lecturer_unavailability")
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class LecturerUnavailability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id", nullable = false)
    private Lecturer lecturer;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, columnDefinition = "VARCHAR(10)")
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    public LecturerUnavailability(DayOfWeek dayOfWeek, LocalTime startTime, LocalTime endTime) {
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    /**
     * Check if this unavailability period overlaps with a lesson time.
     * Uses interval overlap: StartA < EndB AND StartB < EndA
     * 
     * Based on design.md overlap detection logic.
     */
    public boolean overlaps(DayOfWeek day, LocalTime lessonStart, LocalTime lessonEnd) {
        if (!this.dayOfWeek.equals(day)) {
            return false;
        }
        return lessonStart.isBefore(this.endTime) && lessonEnd.isAfter(this.startTime);
    }

    @Override
    public String toString() {
        return dayOfWeek + " " + startTime + "-" + endTime;
    }
}
