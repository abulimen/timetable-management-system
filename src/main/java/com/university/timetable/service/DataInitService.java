package com.university.timetable.service;

import com.university.timetable.domain.Course;
import com.university.timetable.repository.CourseRepository;
import com.university.timetable.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * DataInitService - initializes lessons after sample data is loaded.
 * Generates lessons for all courses that don't have lessons yet.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataInitService {

    private final CourseRepository courseRepository;
    private final LessonRepository lessonRepository;
    private final LessonService lessonService;
    private final TimeslotService timeslotService;

    /**
     * Initialize data after application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialize() {
        log.info("Initializing data...");
        
        // Generate timeslots if not exist
        if (!timeslotService.hasTimeslots()) {
            timeslotService.generateTimeslots();
        }
        
        // Generate lessons for courses that don't have any
        List<Course> courses = courseRepository.findAll();
        int generated = 0;
        
        for (Course course : courses) {
            long lessonCount = lessonRepository.countByCourse(course);
            if (lessonCount == 0 && course.getTotalWeeklyHours() > 0) {
                lessonService.generateLessons(course);
                generated++;
            }
        }
        
        if (generated > 0) {
            log.info("Generated lessons for {} courses", generated);
        }
        
        log.info("Data initialization complete. {} courses, {} lessons, {} timeslots",
            courseRepository.count(),
            lessonRepository.count(),
            timeslotService.getAllTimeslots().size());
    }
}
