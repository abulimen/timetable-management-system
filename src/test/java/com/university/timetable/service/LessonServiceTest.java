package com.university.timetable.service;

import com.university.timetable.domain.Course;
import com.university.timetable.domain.Lesson;
import com.university.timetable.repository.LessonRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

/**
 * Unit tests for LessonService course splitting algorithm.
 * Based on specs.md Course Splitting Algorithm:
 * - 1 Hour  -> 1 Lesson (1hr)
 * - 2 Hours -> 1 Lesson (2hr)
 * - 3 Hours -> 1 Lesson (2hr) + 1 Lesson (1hr)
 * - 4 Hours -> 1 Lesson (2hr) + 1 Lesson (2hr)
 * - 5 Hours -> 1 Lesson (2hr) + 1 Lesson (2hr) + 1 Lesson (1hr)
 */
@ExtendWith(MockitoExtension.class)
class LessonServiceTest {

    @Mock
    private LessonRepository lessonRepository;

    @InjectMocks
    private LessonService lessonService;

    @BeforeEach
    void setUp() {
        when(lessonRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void generateLessons_1Hour_createsOneLesson() {
        Course course = createCourse("CS101", 1);
        
        List<Lesson> lessons = lessonService.generateLessons(course);
        
        assertThat(lessons).hasSize(1);
        assertThat(lessons.get(0).getDurationHours()).isEqualTo(1);
        assertThat(totalHours(lessons)).isEqualTo(1);
    }

    @Test
    void generateLessons_2Hours_createsOneTwoHourLesson() {
        Course course = createCourse("CS102", 2);
        
        List<Lesson> lessons = lessonService.generateLessons(course);
        
        assertThat(lessons).hasSize(1);
        assertThat(lessons.get(0).getDurationHours()).isEqualTo(2);
        assertThat(totalHours(lessons)).isEqualTo(2);
    }

    @Test
    void generateLessons_3Hours_createsTwoHourPlusOneHour() {
        Course course = createCourse("CS103", 3);
        
        List<Lesson> lessons = lessonService.generateLessons(course);
        
        assertThat(lessons).hasSize(2);
        assertThat(lessons.stream().filter(l -> l.getDurationHours() == 2).count()).isEqualTo(1);
        assertThat(lessons.stream().filter(l -> l.getDurationHours() == 1).count()).isEqualTo(1);
        assertThat(totalHours(lessons)).isEqualTo(3);
    }

    @Test
    void generateLessons_4Hours_createsTwoTwoHourLessons() {
        Course course = createCourse("CS104", 4);
        
        List<Lesson> lessons = lessonService.generateLessons(course);
        
        assertThat(lessons).hasSize(2);
        assertThat(lessons).allMatch(l -> l.getDurationHours() == 2);
        assertThat(totalHours(lessons)).isEqualTo(4);
    }

    @Test
    void generateLessons_5Hours_createsThreeLessons() {
        Course course = createCourse("CS105", 5);
        
        List<Lesson> lessons = lessonService.generateLessons(course);
        
        assertThat(lessons).hasSize(3);
        assertThat(lessons.stream().filter(l -> l.getDurationHours() == 2).count()).isEqualTo(2);
        assertThat(lessons.stream().filter(l -> l.getDurationHours() == 1).count()).isEqualTo(1);
        assertThat(totalHours(lessons)).isEqualTo(5);
    }

    @Test
    void generateLessons_setsPartNumbersSequentially() {
        Course course = createCourse("CS106", 5);
        
        List<Lesson> lessons = lessonService.generateLessons(course);
        
        assertThat(lessons.get(0).getPartNumber()).isEqualTo(1);
        assertThat(lessons.get(1).getPartNumber()).isEqualTo(2);
        assertThat(lessons.get(2).getPartNumber()).isEqualTo(3);
    }

    @Test
    void generateLessons_setsCourseReference() {
        Course course = createCourse("CS107", 2);
        
        List<Lesson> lessons = lessonService.generateLessons(course);
        
        assertThat(lessons).allMatch(l -> l.getCourse() == course);
    }

    private Course createCourse(String code, int hours) {
        Course course = new Course();
        course.setCode(code);
        course.setName("Test Course " + code);
        course.setTotalWeeklyHours(hours);
        return course;
    }

    private int totalHours(List<Lesson> lessons) {
        return lessons.stream().mapToInt(Lesson::getDurationHours).sum();
    }
}
