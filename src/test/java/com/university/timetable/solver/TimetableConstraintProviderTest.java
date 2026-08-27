package com.university.timetable.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.university.timetable.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Set;

class TimetableConstraintProviderTest {

    private ConstraintVerifier<TimetableConstraintProvider, TimeTable> constraintVerifier;

    @BeforeEach
    void setUp() {
        constraintVerifier = ConstraintVerifier.build(
                new TimetableConstraintProvider(),
                TimeTable.class,
                Lesson.class);
    }

    @Test
    void roomConflictPenalizesOverlappingLessonsInSameRoom() {
        Room room = new Room();
        room.setId(1L);
        room.setName("Lab 1");
        room.setCapacity(50);

        Timeslot ts = new Timeslot(1L, DayOfWeek.MONDAY, LocalTime.of(9, 0));

        Lecturer lecturer1 = new Lecturer("Dr. Alice", "alice@example.com");
        lecturer1.setId(1L);
        Lecturer lecturer2 = new Lecturer("Dr. Bob", "bob@example.com");
        lecturer2.setId(2L);

        StudentGroup group1 = new StudentGroup("CS", 100, "A", 30);
        group1.setId(1L);
        StudentGroup group2 = new StudentGroup("CS", 200, "A", 30);
        group2.setId(2L);

        Course course1 = new Course("CS101", "Intro to CS", 3);
        course1.setId(1L);
        course1.setStudentGroups(Set.of(group1));

        Course course2 = new Course("CS102", "Data Structures", 3);
        course2.setId(2L);
        course2.setStudentGroups(Set.of(group2));

        Lesson lesson1 = new Lesson(course1, 1, 1, lecturer1);
        lesson1.setId(1L);
        lesson1.setRoom(room);
        lesson1.setTimeslot(ts);

        Lesson lesson2 = new Lesson(course2, 1, 1, lecturer2);
        lesson2.setId(2L);
        lesson2.setRoom(room);
        lesson2.setTimeslot(ts);

        constraintVerifier.verifyThat(TimetableConstraintProvider::roomConflict)
                .given(lesson1, lesson2)
                .penalizesBy(1);
    }

    @Test
    void lecturerConflictPenalizesSameLecturerTeachingTwoClassesAtSameTime() {
        Room room1 = new Room();
        room1.setId(1L);
        room1.setName("Room 101");
        room1.setCapacity(50);

        Room room2 = new Room();
        room2.setId(2L);
        room2.setName("Room 102");
        room2.setCapacity(50);

        Timeslot ts = new Timeslot(1L, DayOfWeek.MONDAY, LocalTime.of(9, 0));

        Lecturer lecturer = new Lecturer("Dr. Alice", "alice@example.com");
        lecturer.setId(1L);

        StudentGroup group1 = new StudentGroup("CS", 100, "A", 30);
        group1.setId(1L);
        StudentGroup group2 = new StudentGroup("CS", 200, "A", 30);
        group2.setId(2L);

        Course course1 = new Course("CS101", "Intro to CS", 3);
        course1.setId(1L);
        course1.setStudentGroups(Set.of(group1));

        Course course2 = new Course("CS102", "Data Structures", 3);
        course2.setId(2L);
        course2.setStudentGroups(Set.of(group2));

        Lesson lesson1 = new Lesson(course1, 1, 1, lecturer);
        lesson1.setId(1L);
        lesson1.setRoom(room1);
        lesson1.setTimeslot(ts);

        Lesson lesson2 = new Lesson(course2, 1, 1, lecturer);
        lesson2.setId(2L);
        lesson2.setRoom(room2);
        lesson2.setTimeslot(ts);

        constraintVerifier.verifyThat(TimetableConstraintProvider::lecturerConflict)
                .given(lesson1, lesson2)
                .penalizesBy(1);
    }

    @Test
    void studentGroupConflictPenalizesSameGroupInTwoClassesAtSameTime() {
        Room room1 = new Room();
        room1.setId(1L);
        room1.setName("Room 101");
        room1.setCapacity(50);

        Room room2 = new Room();
        room2.setId(2L);
        room2.setName("Room 102");
        room2.setCapacity(50);

        Timeslot ts = new Timeslot(1L, DayOfWeek.MONDAY, LocalTime.of(9, 0));

        Lecturer lecturer1 = new Lecturer("Dr. Alice", "alice@example.com");
        lecturer1.setId(1L);
        Lecturer lecturer2 = new Lecturer("Dr. Bob", "bob@example.com");
        lecturer2.setId(2L);

        StudentGroup group = new StudentGroup("CS", 100, "A", 30);
        group.setId(1L);

        Course course1 = new Course("CS101", "Intro to CS", 3);
        course1.setId(1L);
        course1.setStudentGroups(Set.of(group));

        Course course2 = new Course("CS102", "Data Structures", 3);
        course2.setId(2L);
        course2.setStudentGroups(Set.of(group));

        Lesson lesson1 = new Lesson(course1, 1, 1, lecturer1);
        lesson1.setId(1L);
        lesson1.setRoom(room1);
        lesson1.setTimeslot(ts);

        Lesson lesson2 = new Lesson(course2, 1, 1, lecturer2);
        lesson2.setId(2L);
        lesson2.setRoom(room2);
        lesson2.setTimeslot(ts);

        constraintVerifier.verifyThat(TimetableConstraintProvider::studentGroupConflict)
                .given(lesson1, lesson2)
                .penalizesBy(1);
    }

    @Test
    void roomCapacityOverflowPenalizesWhenStudentsExceedSeats() {
        Room smallRoom = new Room();
        smallRoom.setId(1L);
        smallRoom.setName("Small Lab");
        smallRoom.setCapacity(20);

        Timeslot ts = new Timeslot(1L, DayOfWeek.MONDAY, LocalTime.of(9, 0));

        StudentGroup group = new StudentGroup("CS", 100, "All", 45);
        group.setId(1L);

        Course course = new Course("CS101", "Intro to CS", 3);
        course.setId(1L);
        course.setStudentGroups(Set.of(group));

        Lesson lesson = new Lesson(course, 1, 1, null);
        lesson.setId(1L);
        lesson.setRoom(smallRoom);
        lesson.setTimeslot(ts);

        constraintVerifier.verifyThat(TimetableConstraintProvider::roomCapacityOverflow)
                .given(lesson)
                .penalizesBy(1);
    }

    @Test
    void earlyMorningPenaltyAppliesGraduatedPenalty() {
        Timeslot ts7am = new Timeslot(1L, DayOfWeek.MONDAY, LocalTime.of(7, 0));

        Room room = new Room();
        room.setId(1L);
        room.setName("Room 1");
        room.setCapacity(50);

        Course course = new Course("CS101", "Intro to CS", 3);
        course.setId(1L);

        Lesson lesson = new Lesson(course, 1, 1, null);
        lesson.setId(1L);
        lesson.setRoom(room);
        lesson.setTimeslot(ts7am);

        constraintVerifier.verifyThat(TimetableConstraintProvider::earlyMorningPenalty)
                .given(lesson)
                .penalizes();
    }

    @Test
    void lateAfternoonPenaltyAppliesToEveningLessons() {
        Timeslot ts6pm = new Timeslot(1L, DayOfWeek.MONDAY, LocalTime.of(18, 0));

        Room room = new Room();
        room.setId(1L);
        room.setName("Room 1");
        room.setCapacity(50);

        Course course = new Course("CS101", "Intro to CS", 3);
        course.setId(1L);

        Lesson lesson = new Lesson(course, 1, 1, null);
        lesson.setId(1L);
        lesson.setRoom(room);
        lesson.setTimeslot(ts6pm);

        constraintVerifier.verifyThat(TimetableConstraintProvider::lateAfternoonPenalty)
                .given(lesson)
                .penalizes();
    }
}
