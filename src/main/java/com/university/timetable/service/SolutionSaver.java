package com.university.timetable.service;

import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.TimeTable;
import com.university.timetable.repository.LessonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * SolutionSaver - saves solver solutions with proper transaction handling.
 * This is a separate service to ensure @Transactional works correctly
 * when called from the solver's async callback.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SolutionSaver {

    private final LessonRepository lessonRepository;

    /**
     * Save the solution to database with a new transaction.
     */
    @Transactional
    public void saveSolution(TimeTable solution) {
        log.info("Saving solution with score {} and {} lessons", 
            solution.getScore(), solution.getLessons().size());
        
        int saved = 0;
        for (Lesson lesson : solution.getLessons()) {
            Long timeslotId = lesson.getTimeslot() != null ? lesson.getTimeslot().getId() : null;
            Long roomId = lesson.getRoom() != null ? lesson.getRoom().getId() : null;
            
            log.debug("Saving Lesson {}: timeslot={}, room={}", 
                lesson.getId(), timeslotId, roomId);
            
            // Re-fetch the lesson from DB and update only the planning variables
            lessonRepository.findById(lesson.getId()).ifPresent(dbLesson -> {
                dbLesson.setTimeslot(lesson.getTimeslot());
                dbLesson.setRoom(lesson.getRoom());
                lessonRepository.save(dbLesson);
            });
            saved++;
        }
        
        log.info("Saved {} lessons successfully", saved);
    }
}
