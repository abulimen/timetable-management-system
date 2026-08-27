package com.university.timetable.service;

import com.university.timetable.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class ForwardCheckingConstructionServiceTest {

    @Mock
    private ConstraintSettingsService constraintSettingsService;

    @InjectMocks
    private ForwardCheckingConstructionService service;

    @BeforeEach
    void setUp() {
        lenient().when(constraintSettingsService.getEarliestStartTime()).thenReturn(LocalTime.of(7, 0));
        lenient().when(constraintSettingsService.getLatestEndTime()).thenReturn(LocalTime.of(18, 0));
        lenient().when(constraintSettingsService.getFridayLatestEndTime()).thenReturn(LocalTime.of(12, 0));
        lenient().when(constraintSettingsService.getLunchBreakStart()).thenReturn(LocalTime.of(12, 0));
        lenient().when(constraintSettingsService.getLunchBreakEnd()).thenReturn(LocalTime.of(13, 0));
        lenient().when(constraintSettingsService.isLunchBreakEnforced()).thenReturn(true);
    }

    @Test
    void isTimeslotBlockedReturnsTrueOnDirectSlotAndResourceOverlap() {
        Lecturer lecturer = new Lecturer("Dr. Alan", "alan@example.com");
        lecturer.setId(10L);

        Course course1 = new Course("CS101", "CS 1", 3);
        course1.setId(1L);
        Course course2 = new Course("CS102", "CS 2", 3);
        course2.setId(2L);

        Lesson lessonA = new Lesson(course1, 1, 1, lecturer);
        lessonA.setId(100L);

        Lesson lessonB = new Lesson(course2, 1, 1, lecturer);
        lessonB.setId(101L);

        Map<Long, Set<Integer>> lecturerUsage = new HashMap<>();
        Map<Long, Set<Integer>> groupUsage = new HashMap<>();

        // Candidate slot 2 coincides with assigning slot 2 for the same lecturer
        boolean blocked = service.isTimeslotBlocked(2, lessonB, 2, lessonA, lecturerUsage, groupUsage);
        assertTrue(blocked, "Timeslot should be blocked when assigning lesson at the same slot with shared lecturer");
    }

    @Test
    void isTimeslotBlockedReturnsTrueWhenLecturerAlreadyUsedAtCandidateSlot() {
        Lecturer lecturer = new Lecturer("Dr. Alan", "alan@example.com");
        lecturer.setId(10L);

        Course course1 = new Course("CS101", "CS 1", 3);
        course1.setId(1L);
        Course course2 = new Course("CS102", "CS 2", 3);
        course2.setId(2L);

        Lesson assigningLesson = new Lesson(course1, 1, 1, null);
        assigningLesson.setId(100L);

        Lesson conflictLesson = new Lesson(course2, 1, 1, lecturer);
        conflictLesson.setId(101L);

        Map<Long, Set<Integer>> lecturerUsage = new HashMap<>();
        lecturerUsage.put(10L, new HashSet<>(Set.of(3))); // Slot 3 already occupied by lecturer
        Map<Long, Set<Integer>> groupUsage = new HashMap<>();

        boolean blocked = service.isTimeslotBlocked(3, conflictLesson, 0, assigningLesson, lecturerUsage, groupUsage);
        assertTrue(blocked, "Timeslot 3 should be blocked because the lecturer is already booked at slot 3");
    }

    @Test
    void isTimeslotBlockedReturnsTrueWhenStudentGroupAlreadyUsedAtCandidateSlot() {
        StudentGroup group = new StudentGroup("CS", 100, "A", 40);
        group.setId(20L);

        Course course1 = new Course("CS101", "CS 1", 3);
        course1.setId(1L);
        Course course2 = new Course("CS102", "CS 2", 3);
        course2.setId(2L);
        course2.setStudentGroups(Set.of(group));

        Lesson assigningLesson = new Lesson(course1, 1, 1, null);
        assigningLesson.setId(100L);

        Lesson conflictLesson = new Lesson(course2, 1, 1, null);
        conflictLesson.setId(101L);

        Map<Long, Set<Integer>> lecturerUsage = new HashMap<>();
        Map<Long, Set<Integer>> groupUsage = new HashMap<>();
        groupUsage.put(20L, new HashSet<>(Set.of(4))); // Slot 4 occupied for group 20

        boolean blocked = service.isTimeslotBlocked(4, conflictLesson, 0, assigningLesson, lecturerUsage, groupUsage);
        assertTrue(blocked, "Timeslot 4 should be blocked because the student group has a prior booking at slot 4");
    }

    @Test
    void isTimeslotBlockedReturnsFalseWhenSlotsAndResourcesAreDisjoint() {
        Lecturer lecturerA = new Lecturer("Dr. Alan", "alan@example.com");
        lecturerA.setId(10L);
        Lecturer lecturerB = new Lecturer("Dr. Beth", "beth@example.com");
        lecturerB.setId(11L);

        Course course1 = new Course("CS101", "CS 1", 3);
        course1.setId(1L);
        Course course2 = new Course("CS102", "CS 2", 3);
        course2.setId(2L);

        Lesson assigningLesson = new Lesson(course1, 1, 1, lecturerA);
        assigningLesson.setId(100L);

        Lesson conflictLesson = new Lesson(course2, 1, 1, lecturerB);
        conflictLesson.setId(101L);

        Map<Long, Set<Integer>> lecturerUsage = new HashMap<>();
        Map<Long, Set<Integer>> groupUsage = new HashMap<>();

        boolean blocked = service.isTimeslotBlocked(1, conflictLesson, 0, assigningLesson, lecturerUsage, groupUsage);
        assertFalse(blocked, "Timeslot should not be blocked when there are no conflicting bookings or overlapping resources");
    }

    @Test
    void constructAssignsTimeslotsAndRoomsToLessons() {
        Timeslot ts1 = new Timeslot(1L, DayOfWeek.MONDAY, LocalTime.of(9, 0));
        Timeslot ts2 = new Timeslot(2L, DayOfWeek.MONDAY, LocalTime.of(10, 0));

        Room room1 = new Room();
        room1.setId(1L);
        room1.setName("Room A");
        room1.setCapacity(50);

        Room room2 = new Room();
        room2.setId(2L);
        room2.setName("Room B");
        room2.setCapacity(50);

        StudentGroup group = new StudentGroup("CS", 100, "A", 30);
        group.setId(1L);

        Course course = new Course("CS101", "Intro CS", 2);
        course.setId(1L);
        course.setStudentGroups(Set.of(group));

        Lecturer lecturer = new Lecturer("Dr. Alan", "alan@example.com");
        lecturer.setId(1L);

        Lesson lesson1 = new Lesson(course, 1, 1, lecturer);
        lesson1.setId(1L);

        Lesson lesson2 = new Lesson(course, 1, 2, lecturer);
        lesson2.setId(2L);

        TimeTable timetable = service.construct(
                List.of(lesson1, lesson2),
                List.of(ts1, ts2),
                List.of(room1, room2));

        assertNotNull(timetable);
        assertEquals(2, timetable.getLessons().size());
        assertNotNull(lesson1.getTimeslot(), "Lesson 1 should receive a timeslot");
        assertNotNull(lesson1.getRoom(), "Lesson 1 should receive a room");
        assertNotNull(lesson2.getTimeslot(), "Lesson 2 should receive a timeslot");
        assertNotNull(lesson2.getRoom(), "Lesson 2 should receive a room");
        assertNotEquals(lesson1.getTimeslot(), lesson2.getTimeslot(), "Lessons sharing lecturer and group must be in distinct timeslots");
    }
}
