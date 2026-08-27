package com.university.timetable.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.university.timetable.domain.*;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TimeTableSolverIntegrationTest {

    @Test
    void solverFindsZeroHardConflictSolutionForFeasibleProblem() {
        SolverConfig solverConfig = SolverConfig.createFromXmlResource("solver-config.xml");

        TerminationConfig terminationConfig = new TerminationConfig();
        terminationConfig.setSecondsSpentLimit(5L);
        solverConfig.setTerminationConfig(terminationConfig);

        SolverFactory<TimeTable> solverFactory = SolverFactory.create(solverConfig);
        Solver<TimeTable> solver = solverFactory.buildSolver();

        // 1. Create problem facts
        Timeslot ts1 = new Timeslot(1L, DayOfWeek.MONDAY, LocalTime.of(9, 0));
        Timeslot ts2 = new Timeslot(2L, DayOfWeek.MONDAY, LocalTime.of(10, 0));
        Timeslot ts3 = new Timeslot(3L, DayOfWeek.TUESDAY, LocalTime.of(9, 0));
        Timeslot ts4 = new Timeslot(4L, DayOfWeek.TUESDAY, LocalTime.of(10, 0));

        List<Timeslot> timeslots = List.of(ts1, ts2, ts3, ts4);

        Room room1 = new Room();
        room1.setId(1L);
        room1.setName("Room 101");
        room1.setCapacity(50);

        Room room2 = new Room();
        room2.setId(2L);
        room2.setName("Room 102");
        room2.setCapacity(60);

        List<Room> rooms = List.of(room1, room2);

        Lecturer lecturer1 = new Lecturer("Dr. Alice", "alice@example.com");
        lecturer1.setId(1L);
        Lecturer lecturer2 = new Lecturer("Dr. Bob", "bob@example.com");
        lecturer2.setId(2L);

        List<Lecturer> lecturers = List.of(lecturer1, lecturer2);

        StudentGroup group1 = new StudentGroup("CS", 100, "A", 35);
        group1.setId(1L);
        StudentGroup group2 = new StudentGroup("CS", 200, "A", 40);
        group2.setId(2L);

        List<StudentGroup> groups = List.of(group1, group2);

        Course course1 = new Course("CS101", "Intro to Programming", 2);
        course1.setId(1L);
        course1.setStudentGroups(Set.of(group1));

        Course course2 = new Course("CS201", "Algorithms", 2);
        course2.setId(2L);
        course2.setStudentGroups(Set.of(group2));

        // 2. Create unassigned planning entities
        Lesson lesson1 = new Lesson(course1, 1, 1, lecturer1);
        lesson1.setId(1L);

        Lesson lesson2 = new Lesson(course1, 1, 2, lecturer1);
        lesson2.setId(2L);

        Lesson lesson3 = new Lesson(course2, 1, 1, lecturer2);
        lesson3.setId(3L);

        Lesson lesson4 = new Lesson(course2, 1, 2, lecturer2);
        lesson4.setId(4L);

        List<Lesson> lessons = new ArrayList<>(List.of(lesson1, lesson2, lesson3, lesson4));

        TimeTable problem = new TimeTable(lessons, timeslots, rooms, lecturers, groups);

        // 3. Solve
        TimeTable solution = solver.solve(problem);

        // 4. Validate output
        assertNotNull(solution);
        assertNotNull(solution.getScore());
        assertEquals(0, solution.getScore().hardScore(), "A feasible timetable must achieve 0 hard conflicts");
        for (Lesson lesson : solution.getLessons()) {
            assertNotNull(lesson.getTimeslot(), "Every lesson must be assigned a timeslot");
            assertNotNull(lesson.getRoom(), "Every lesson must be assigned a room");
        }
    }
}
