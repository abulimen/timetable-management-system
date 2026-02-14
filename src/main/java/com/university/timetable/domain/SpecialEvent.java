package com.university.timetable.domain;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Represents a fixed special event like interdisciplinary seminars.
 * These events are scheduled at specific times and block all affected
 * student groups, rooms, and lecturers from having other classes.
 */
@Entity
@Table(name = "special_event")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SpecialEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "day_of_week", nullable = false)
    @Enumerated(EnumType.STRING)
    private DayOfWeek dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "duration_hours", nullable = false)
    private int durationHours = 2;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lecturer_id")
    private Lecturer lecturer;

    @Column(name = "is_online")
    private boolean online = false;

    @Column(name = "is_active")
    private boolean active = true;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * Student groups affected by this special event.
     * All these groups are blocked from having other classes during this event.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "special_event_student_group", joinColumns = @JoinColumn(name = "special_event_id"), inverseJoinColumns = @JoinColumn(name = "student_group_id"))
    private Set<StudentGroup> studentGroups = new HashSet<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * Calculate the end time based on start time and duration.
     */
    public LocalTime getEndTime() {
        return startTime.plusHours(durationHours);
    }

    /**
     * Check if a given timeslot overlaps with this special event.
     */
    public boolean overlapsWithTimeslot(Timeslot timeslot) {
        if (timeslot.getDayOfWeek() != this.dayOfWeek) {
            return false;
        }
        LocalTime eventEnd = getEndTime();
        // Timeslot is typically 1 hour, so end time = start time + 1 hour
        LocalTime slotStart = timeslot.getStartTime();
        LocalTime slotEnd = slotStart.plusHours(1);

        // Overlap check: events overlap if one starts before the other ends
        return !(slotEnd.isBefore(this.startTime) || slotEnd.equals(this.startTime) ||
                slotStart.isAfter(eventEnd) || slotStart.equals(eventEnd));
    }

    /**
     * Check if a student group is affected by this event.
     */
    public boolean affectsStudentGroup(StudentGroup group) {
        if (group == null || studentGroups == null || studentGroups.isEmpty()) {
            return false;
        }
        for (StudentGroup eventGroup : studentGroups) {
            if (eventGroup != null && eventGroup.hasConflictWith(group)) {
                return true;
            }
        }
        return false;
    }
}
