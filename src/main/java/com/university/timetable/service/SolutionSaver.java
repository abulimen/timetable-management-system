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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
     * Uses batched updates to reduce DB round-trips on large datasets.
     */
    @Transactional
    public void saveSolution(TimeTable solution) {
        long startNanos = System.nanoTime();
        log.info("Saving solution with score {} and {} lessons",
                solution.getScore(), solution.getLessons().size());

        int updated = 0;
        int missingDbLessons = 0;
        int missingTimeslots = 0;
        int missingRooms = 0;

        long preloadStart = System.nanoTime();
        Map<String, Timeslot> timeslotByKey = new HashMap<>();
        for (Timeslot timeslot : timeslotRepository.findAll()) {
            timeslotByKey.put(timeslotKey(timeslot), timeslot);
        }
        Map<Long, Room> roomById = new HashMap<>();
        for (Room room : roomRepository.findAll()) {
            roomById.put(room.getId(), room);
        }
        List<Long> lessonIds = solution.getLessons().stream()
                .map(Lesson::getId)
                .filter(Objects::nonNull)
                .toList();
        Map<Long, Lesson> dbLessonById = new HashMap<>();
        for (Lesson dbLesson : lessonRepository.findAllById(lessonIds)) {
            dbLessonById.put(dbLesson.getId(), dbLesson);
        }
        long preloadMs = elapsedMs(preloadStart);

        long mapAndUpdateStart = System.nanoTime();
        List<Lesson> changedLessons = new ArrayList<>(solution.getLessons().size());
        for (Lesson solverLesson : solution.getLessons()) {
            Long lessonId = solverLesson.getId();
            if (lessonId == null) {
                continue;
            }
            Lesson dbLesson = dbLessonById.get(lessonId);
            if (dbLesson == null) {
                missingDbLessons++;
                continue;
            }

            Timeslot solverTimeslot = solverLesson.getTimeslot();
            Timeslot dbTimeslot = null;
            if (solverTimeslot != null) {
                dbTimeslot = timeslotByKey.get(timeslotKey(solverTimeslot));
                if (dbTimeslot == null) {
                    missingTimeslots++;
                }
            }

            Room solverRoom = solverLesson.getRoom();
            Room dbRoom = null;
            if (solverRoom != null) {
                dbRoom = roomById.get(solverRoom.getId());
                if (dbRoom == null) {
                    missingRooms++;
                }
            }

            Long currentTimeslotId = dbLesson.getTimeslot() != null ? dbLesson.getTimeslot().getId() : null;
            Long nextTimeslotId = dbTimeslot != null ? dbTimeslot.getId() : null;
            Long currentRoomId = dbLesson.getRoom() != null ? dbLesson.getRoom().getId() : null;
            Long nextRoomId = dbRoom != null ? dbRoom.getId() : null;
            if (Objects.equals(currentTimeslotId, nextTimeslotId) && Objects.equals(currentRoomId, nextRoomId)) {
                continue;
            }

            dbLesson.setTimeslot(dbTimeslot);
            dbLesson.setRoom(dbRoom);
            changedLessons.add(dbLesson);
        }

        if (!changedLessons.isEmpty()) {
            lessonRepository.saveAll(changedLessons);
            updated = changedLessons.size();
        }
        long updateMs = elapsedMs(mapAndUpdateStart);

        if (missingTimeslots > 0 || missingRooms > 0 || missingDbLessons > 0) {
            log.warn("Save solution had unresolved references: missingLessons={}, missingTimeslots={}, missingRooms={}",
                    missingDbLessons, missingTimeslots, missingRooms);
        }
        log.info("Saved solution updates: {} changed lessons in {} ms (preload={} ms, map+update={} ms, missingLessons={}, missingTimeslots={}, missingRooms={})",
                updated, elapsedMs(startNanos), preloadMs, updateMs, missingDbLessons, missingTimeslots, missingRooms);
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }

    private String timeslotKey(Timeslot timeslot) {
        return timeslot.getDayOfWeek() + "|" + timeslot.getStartTime();
    }
}
