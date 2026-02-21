package com.university.timetable.domain;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * TimeTable - the Planning Solution for OptaPlanner.
 * 
 * This is the container that holds all planning entities (lessons)
 * and problem facts (timeslots, rooms, lecturers, student groups).
 * 
 * Based on design.md TimeTable specification:
 * - @PlanningSolution annotation
 * - @ValueRangeProvider for timeslots and rooms
 * - @ProblemFactCollectionProperty for lecturers and student groups
 */
@PlanningSolution
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimeTable {

    /**
     * Planning entities - the lessons that OptaPlanner will schedule.
     */
    @PlanningEntityCollectionProperty
    private List<Lesson> lessons = new ArrayList<>();

    /**
     * Problem facts - available timeslots for scheduling.
     * Also serves as the value range for Lesson.timeslot.
     */
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "timeslotRange")
    private List<Timeslot> timeslots = new ArrayList<>();

    /**
     * Problem facts - available rooms for scheduling.
     * Also serves as the value range for Lesson.room.
     */
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "roomRange")
    private List<Room> rooms = new ArrayList<>();

    /**
     * Problem facts - lecturers for constraint checking.
     */
    @ProblemFactCollectionProperty
    private List<Lecturer> lecturers = new ArrayList<>();

    /**
     * Problem facts - student groups for constraint checking.
     */
    @ProblemFactCollectionProperty
    private List<StudentGroup> studentGroups = new ArrayList<>();

    /**
     * The score calculated by OptaPlanner based on constraints.
     */
    @PlanningScore
    private HardSoftScore score;

    public TimeTable(List<Lesson> lessons, List<Timeslot> timeslots, List<Room> rooms,
            List<Lecturer> lecturers, List<StudentGroup> studentGroups) {
        this.lessons = lessons;
        this.timeslots = timeslots;
        this.rooms = rooms;
        this.lecturers = lecturers;
        this.studentGroups = studentGroups;
    }

    /**
     * Problem facts - special events for constraint checking.
     * Special events block timeslots for affected student groups.
     */
    @ProblemFactCollectionProperty
    private List<SpecialEvent> specialEvents = new ArrayList<>();

    public TimeTable(List<Lesson> lessons, List<Timeslot> timeslots, List<Room> rooms,
            List<Lecturer> lecturers, List<StudentGroup> studentGroups,
            List<SpecialEvent> specialEvents) {
        this.lessons = lessons;
        this.timeslots = timeslots;
        this.rooms = rooms;
        this.lecturers = lecturers;
        this.studentGroups = studentGroups;
        this.specialEvents = specialEvents != null ? specialEvents : new ArrayList<>();
    }

    @Override
    public String toString() {
        return "TimeTable{" +
                "lessons=" + lessons.size() +
                ", timeslots=" + timeslots.size() +
                ", rooms=" + rooms.size() +
                ", specialEvents=" + specialEvents.size() +
                ", score=" + score +
                '}';
    }
}
