package com.university.timetable.domain;

import org.optaplanner.core.api.domain.entity.PlanningEntity;
import org.optaplanner.core.api.domain.entity.PlanningPin;
import org.optaplanner.core.api.domain.lookup.PlanningId;
import org.optaplanner.core.api.domain.variable.PlanningVariable;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.time.LocalTime;
import java.util.Set;

/**
 * Lesson entity - the core Planning Entity for OptaPlanner.
 * 
 * A Lesson is a split part of a Course (e.g., MTH101-Part1, MTH101-Part2).
 * OptaPlanner assigns each Lesson to a Timeslot and Room.
 * 
 * Based on design.md Lesson Entity specification:
 * - @PlanningEntity for OptaPlanner
 * - @PlanningVariable for timeslot and room
 * - @PlanningPin for admin override pinning
 */
@Entity
@Table(name = "lesson")
@PlanningEntity(difficultyComparatorClass = com.university.timetable.solver.LessonDifficultyComparator.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @PlanningId
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "duration_hours", nullable = false)
    private int durationHours; // 1 or 2 hours

    @Column(name = "part_number", nullable = false)
    private int partNumber; // e.g., 1 for MTH101-Part1

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "lecturer_id")
    private Lecturer lecturer;

    /**
     * Planning Variable: The assigned timeslot.
     * OptaPlanner will determine this value.
     * strengthComparatorClass enables WEAKEST_FIT/STRONGEST_FIT heuristics.
     */
    @PlanningVariable(
        valueRangeProviderRefs = "timeslotRange",
        strengthComparatorClass = com.university.timetable.solver.TimeslotStrengthComparator.class
    )
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assigned_timeslot_id")
    private Timeslot timeslot;

    /**
     * Planning Variable: The assigned room.
     * OptaPlanner will determine this value.
     * strengthComparatorClass enables WEAKEST_FIT/STRONGEST_FIT heuristics.
     */
    @PlanningVariable(
        valueRangeProviderRefs = "roomRange",
        strengthComparatorClass = com.university.timetable.solver.RoomStrengthComparator.class
    )
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "assigned_room_id")
    private Room room;

    /**
     * Admin override - if true, this lesson's assignment is locked.
     */
    @Column(name = "is_pinned")
    private boolean pinned;

    public Lesson(Course course, int durationHours, int partNumber, Lecturer lecturer) {
        this.course = course;
        this.durationHours = durationHours;
        this.partNumber = partNumber;
        this.lecturer = lecturer;
    }

    /**
     * Return pinned status for OptaPlanner's @PlanningPin.
     * When pinned is true, OptaPlanner will not modify this lesson.
     */
    @PlanningPin
    public boolean isPinned() {
        return pinned;
    }

    /**
     * Get the end time of this lesson.
     * Derived from timeslot start time + duration.
     */
    public LocalTime getEndTime() {
        if (timeslot == null) {
            return null;
        }
        return timeslot.getStartTime().plusHours(durationHours);
    }

    /**
     * Get a display name for this lesson (e.g., "MTH101-Part1").
     */
    public String getDisplayName() {
        return course.getCode() + "-Part" + partNumber;
    }

    /**
     * Get the primary student group for this lesson (legacy support).
     * For combined groups, use getStudentGroups() instead.
     */
    public StudentGroup getStudentGroup() {
        return course != null ? course.getStudentGroup() : null;
    }

    /**
     * Get all student groups for this lesson.
     * Supports combined classes (e.g., Groups A+D+E).
     */
    public Set<StudentGroup> getStudentGroups() {
        return course != null ? course.getAllStudentGroups() : Set.of();
    }

    /**
     * Get total student count across all groups.
     * Used for room capacity constraint.
     */
    public int getTotalStudentCount() {
        return course != null ? course.getTotalStudentCount() : 0;
    }

    /**
     * Check if this lesson is for an online course.
     * Online lessons don't require physical rooms.
     */
    public boolean isOnline() {
        return course != null && course.isOnline();
    }

    @Override
    public String toString() {
        return getDisplayName() + " (" + durationHours + "hr)";
    }
}
