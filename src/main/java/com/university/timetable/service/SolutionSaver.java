package com.university.timetable.service;

import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.TimeTable;
import com.university.timetable.domain.Timeslot;
import com.university.timetable.repository.LessonRepository;
import com.university.timetable.repository.RoomRepository;
import com.university.timetable.repository.TimeslotRepository;
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
    private final TimeslotRepository timeslotRepository;
    private final RoomRepository roomRepository;

    /**
     * Save the solution to database with a new transaction.
     * Note: We look up timeslots and rooms from DB by their attributes because
     * the solver uses in-memory objects that may have IDs not matching the DB
     * (especially timeslots which are dynamically regenerated from settings).
     */
    @Transactional
    public void saveSolution(TimeTable solution) {
        long startNanos = System.nanoTime();
        log.info("Saving solution with score {} and {} lessons",
                solution.getScore(), solution.getLessons().size());

        int saved = 0;
        int missingTimeslots = 0;
        int missingRooms = 0;

        long lookupAndSaveStart = System.nanoTime();
        for (Lesson lesson : solution.getLessons()) {
            Timeslot solverTimeslot = lesson.getTimeslot();
            Room solverRoom = lesson.getRoom();

            // Look up the actual DB timeslot by day+time
            Timeslot dbTimeslot = null;
            if (solverTimeslot != null) {
                dbTimeslot = timeslotRepository.findByDayOfWeekAndStartTime(
                        solverTimeslot.getDayOfWeek(),
                        solverTimeslot.getStartTime()).orElse(null);

                if (dbTimeslot == null) {
                    missingTimeslots++;
                    log.warn("Timeslot not found in DB: {} {}",
                            solverTimeslot.getDayOfWeek(), solverTimeslot.getStartTime());
                }
            }

            // Look up room by ID (rooms should be stable, but verify it exists)
            Room dbRoom = null;
            if (solverRoom != null) {
                dbRoom = roomRepository.findById(solverRoom.getId()).orElse(null);
                if (dbRoom == null) {
                    missingRooms++;
                    log.warn("Room not found in DB: {}", solverRoom.getId());
                }
            }

            final Timeslot finalTimeslot = dbTimeslot;
            final Room finalRoom = dbRoom;

            // Re-fetch the lesson from DB and update only the planning variables
            lessonRepository.findById(lesson.getId()).ifPresent(dbLesson -> {
                dbLesson.setTimeslot(finalTimeslot);
                dbLesson.setRoom(finalRoom);
                lessonRepository.save(dbLesson);
            });
            saved++;
        }

        log.info("Saved {} lessons successfully in {} ms (lookup+update {} ms, missingTimeslots={}, missingRooms={})",
                saved, elapsedMs(startNanos), elapsedMs(lookupAndSaveStart), missingTimeslots, missingRooms);
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
