package com.university.timetable.service;

import com.university.timetable.domain.Course;
import com.university.timetable.domain.Lesson;
import com.university.timetable.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * LessonService - implements the course splitting algorithm.
 * 
 * Based on specs.md Course Splitting Algorithm:
 * - Maximize 2-hour blocks
 * - Remainder gets 1-hour block
 * 
 * Logic Table:
 * | Total Hours | Generated Lessons |
 * | 1 Hour      | 1 Lesson (1hr) |
 * | 2 Hours     | 1 Lesson (2hr) |
 * | 3 Hours     | 1 Lesson (2hr) + 1 Lesson (1hr) |
 * | 4 Hours     | 1 Lesson (2hr) + 1 Lesson (2hr) |
 * | 5 Hours     | 1 Lesson (2hr) + 1 Lesson (2hr) + 1 Lesson (1hr) |
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LessonService {

    private final LessonRepository lessonRepository;

    /**
     * Generate lessons for a course based on its total weekly hours.
     * This runs before the solver starts.
     */
    @Transactional
    public List<Lesson> generateLessons(Course course) {
        log.info("Generating lessons for course {} ({} hours)", 
            course.getCode(), course.getTotalWeeklyHours());
        
        List<Lesson> lessons = new ArrayList<>();
        int totalHours = course.getTotalWeeklyHours();
        int twoHourBlocks = totalHours / 2;
        int remainingHour = totalHours % 2;
        
        int partNumber = 1;
        
        // Generate 2-hour lessons
        for (int i = 0; i < twoHourBlocks; i++) {
            Lesson lesson = new Lesson();
            lesson.setCourse(course);
            lesson.setDurationHours(2);
            lesson.setPartNumber(partNumber++);
            lesson.setLecturer(course.getLecturer());
            lessons.add(lesson);
        }
        
        // Generate 1-hour lesson if remainder exists
        if (remainingHour == 1) {
            Lesson lesson = new Lesson();
            lesson.setCourse(course);
            lesson.setDurationHours(1);
            lesson.setPartNumber(partNumber);
            lesson.setLecturer(course.getLecturer());
            lessons.add(lesson);
        }
        
        List<Lesson> saved = lessonRepository.saveAll(lessons);
        log.info("Generated {} lessons for course {}", saved.size(), course.getCode());
        
        return saved;
    }

    /**
     * Update lessons when course hours change.
     * Based on specs.md update logic:
     * - If hours increase: Generate deficit lessons
     * - If hours decrease: Remove unpinned/unscheduled lessons first
     */
    @Transactional
    public void updateLessonsForCourse(Course course, int newHours) {
        List<Lesson> existing = lessonRepository.findByCourse(course);
        int currentHours = existing.stream()
            .mapToInt(Lesson::getDurationHours)
            .sum();
        
        if (newHours > currentHours) {
            generateAdditionalLessons(course, newHours - currentHours, existing);
        } else if (newHours < currentHours) {
            removeExcessLessons(existing, currentHours - newHours);
        }
    }

    /**
     * Generate additional lessons to make up the deficit.
     */
    private void generateAdditionalLessons(Course course, int deficitHours, List<Lesson> existing) {
        log.info("Adding {} hours of lessons for course {}", deficitHours, course.getCode());
        
        int maxPartNumber = existing.stream()
            .mapToInt(Lesson::getPartNumber)
            .max()
            .orElse(0);
        
        List<Lesson> newLessons = new ArrayList<>();
        int twoHourBlocks = deficitHours / 2;
        int remainingHour = deficitHours % 2;
        
        for (int i = 0; i < twoHourBlocks; i++) {
            Lesson lesson = new Lesson();
            lesson.setCourse(course);
            lesson.setDurationHours(2);
            lesson.setPartNumber(++maxPartNumber);
            lesson.setLecturer(course.getLecturer());
            newLessons.add(lesson);
        }
        
        if (remainingHour == 1) {
            Lesson lesson = new Lesson();
            lesson.setCourse(course);
            lesson.setDurationHours(1);
            lesson.setPartNumber(++maxPartNumber);
            lesson.setLecturer(course.getLecturer());
            newLessons.add(lesson);
        }
        
        lessonRepository.saveAll(newLessons);
    }

    /**
     * Remove excess lessons, prioritizing unpinned and unscheduled lessons.
     */
    private void removeExcessLessons(List<Lesson> existing, int excessHours) {
        log.info("Removing {} hours of lessons", excessHours);
        
        // Sort by priority: unpinned & unscheduled first
        List<Lesson> sorted = existing.stream()
            .sorted(Comparator
                .comparing(Lesson::isPinned)  // Unpinned first
                .thenComparing(l -> l.getTimeslot() != null && l.getRoom() != null)) // Unscheduled first
            .toList();
        
        int hoursToRemove = excessHours;
        List<Lesson> toDelete = new ArrayList<>();
        
        for (Lesson lesson : sorted) {
            if (hoursToRemove <= 0) break;
            toDelete.add(lesson);
            hoursToRemove -= lesson.getDurationHours();
        }
        
        lessonRepository.deleteAll(toDelete);
    }

    /**
     * Get all lessons for a course.
     */
    public List<Lesson> getLessonsForCourse(Course course) {
        return lessonRepository.findByCourse(course);
    }

    /**
     * Get all lessons.
     */
    public List<Lesson> getAllLessons() {
        return lessonRepository.findAll();
    }

    /**
     * Pin a lesson to its current assignment.
     */
    @Transactional
    public Lesson pinLesson(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
            .orElseThrow(() -> new IllegalArgumentException("Lesson not found: " + lessonId));
        lesson.setPinned(true);
        return lessonRepository.save(lesson);
    }

    /**
     * Unpin a lesson.
     */
    @Transactional
    public Lesson unpinLesson(Long lessonId) {
        Lesson lesson = lessonRepository.findById(lessonId)
            .orElseThrow(() -> new IllegalArgumentException("Lesson not found: " + lessonId));
        lesson.setPinned(false);
        return lessonRepository.save(lesson);
    }
}
