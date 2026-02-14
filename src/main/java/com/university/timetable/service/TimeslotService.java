package com.university.timetable.service;

import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Timeslot;
import com.university.timetable.repository.LessonRepository;
import com.university.timetable.repository.TimeslotRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * TimeslotService - generates valid timeslots DYNAMICALLY from settings.
 * 
 * Reads from ConstraintSettingsService:
 * - earliest_start_time: First slot of the day
 * - latest_end_time: Last slot must end by this time (Mon-Thu)
 * - friday_latest_end_time: Last slot must end by this time (Friday)
 * - lunch_break_start, lunch_break_end: Excluded from scheduling
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimeslotService {

    private final TimeslotRepository timeslotRepository;
    private final LessonRepository lessonRepository;
    private final ConstraintSettingsService settingsService;
    private final EntityManager entityManager;

    /**
     * Generate all valid timeslots based on database settings.
     * 
     * Reads timing from settings:
     * - earliest_start_time (default 07:00)
     * - latest_end_time (default 19:00)
     * - lunch_break_start (default 12:00)
     * - lunch_break_end (default 14:00)
     * - friday_latest_end_time (default 12:00)
     */
    @Transactional
    public List<Timeslot> generateTimeslots() {
        long startNanos = System.nanoTime();
        // Get settings
        int startHour = settingsService.getEarliestStartTime().getHour();
        int endHour = settingsService.getLatestEndTime().getHour();
        int lunchStartHour = settingsService.getLunchBreakStart().getHour();
        int lunchEndHour = settingsService.getLunchBreakEnd().getHour();
        int fridayEndHour = settingsService.getFridayLatestEndTime().getHour();
        
        log.info("Generating timeslots from settings: start={}, end={}, lunchStart={}, lunchEnd={}, fridayEnd={}",
                startHour, endHour, lunchStartHour, lunchEndHour, fridayEndHour);
        
        // Clear lesson timeslot references first (to avoid FK constraint violations)
        List<Lesson> lessons = lessonRepository.findAll();
        int previouslyAssigned = 0;
        for (Lesson lesson : lessons) {
            if (lesson.getTimeslot() != null) {
                previouslyAssigned++;
            }
            lesson.setTimeslot(null);
        }
        long clearStart = System.nanoTime();
        lessonRepository.saveAll(lessons);
        entityManager.flush();
        log.debug("Cleared timeslot assignments for {} lessons ({} previously assigned) in {} ms",
                lessons.size(), previouslyAssigned, elapsedMs(clearStart));

        // Now delete all timeslots
        long deleteStart = System.nanoTime();
        timeslotRepository.deleteAll();
        entityManager.flush();
        entityManager.clear();
        log.debug("Deleted existing timeslots in {} ms", elapsedMs(deleteStart));

        List<Timeslot> timeslots = new ArrayList<>();

        // Monday through Thursday
        List<DayOfWeek> weekDays = List.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY
        );
        
        for (DayOfWeek day : weekDays) {
            for (int hour = startHour; hour < endHour; hour++) {
                // Skip lunch break hours
                if (hour >= lunchStartHour && hour < lunchEndHour) {
                    continue;
                }
                timeslots.add(createTimeslot(day, LocalTime.of(hour, 0)));
            }
        }

        // Friday: Uses friday_latest_end_time setting
        for (int hour = startHour; hour < fridayEndHour; hour++) {
            // Skip lunch break hours (if lunch is before Friday end time)
            if (hour >= lunchStartHour && hour < lunchEndHour) {
                continue;
            }
            timeslots.add(createTimeslot(DayOfWeek.FRIDAY, LocalTime.of(hour, 0)));
        }

        long saveStart = System.nanoTime();
        List<Timeslot> saved = timeslotRepository.saveAll(timeslots);
        log.info("Generated {} timeslots dynamically from settings in {} ms",
                saved.size(), elapsedMs(startNanos));
        log.debug("Timeslot insert/save completed in {} ms", elapsedMs(saveStart));

        return saved;
    }

    /**
     * Regenerate timeslots - call this after settings change.
     * WARNING: This will clear all existing lesson timeslot assignments!
     */
    @Transactional
    public List<Timeslot> regenerateTimeslots() {
        long startNanos = System.nanoTime();
        log.info("Regenerating timeslots from updated settings...");
        settingsService.refreshCache();
        List<Timeslot> regenerated = generateTimeslots();
        log.info("Timeslot regeneration total duration: {} ms", elapsedMs(startNanos));
        return regenerated;
    }

    /**
     * Ensure current DB timeslots match active settings.
     * Regenerates only when there is a mismatch or when no timeslots exist.
     */
    @Transactional
    public List<Timeslot> ensureTimeslotsMatchSettings() {
        long startNanos = System.nanoTime();
        List<Timeslot> existing = timeslotRepository.findAll();
        if (existing.isEmpty()) {
            log.info("No timeslots found. Generating from settings...");
            return generateTimeslots();
        }

        Set<String> expected = buildExpectedTimeslotKeysFromSettings();
        Set<String> actual = new HashSet<>(existing.size());
        for (Timeslot timeslot : existing) {
            actual.add(toTimeslotKey(timeslot.getDayOfWeek(), timeslot.getStartTime()));
        }

        if (expected.size() == actual.size() && actual.containsAll(expected)) {
            log.info("Timeslots already match current settings ({} slots). Skipping regeneration ({} ms).",
                    existing.size(), elapsedMs(startNanos));
            return existing;
        }

        log.warn("Timeslot layout mismatch detected (expected {} unique slots, actual {}). Regenerating...",
                expected.size(), actual.size());
        List<Timeslot> regenerated = generateTimeslots();
        log.info("Timeslot mismatch corrected in {} ms", elapsedMs(startNanos));
        return regenerated;
    }

    private Timeslot createTimeslot(DayOfWeek day, LocalTime startTime) {
        return new Timeslot(day, startTime);
    }

    /**
     * Get all existing timeslots from database.
     */
    public List<Timeslot> getAllTimeslots() {
        return timeslotRepository.findAll();
    }

    /**
     * Get timeslots for a specific day.
     */
    public List<Timeslot> getTimeslotsForDay(DayOfWeek day) {
        return timeslotRepository.findByDayOfWeek(day);
    }

    /**
     * Check if timeslots have been generated.
     */
    public boolean hasTimeslots() {
        return timeslotRepository.count() > 0;
    }

    private Set<String> buildExpectedTimeslotKeysFromSettings() {
        int startHour = settingsService.getEarliestStartTime().getHour();
        int endHour = settingsService.getLatestEndTime().getHour();
        int lunchStartHour = settingsService.getLunchBreakStart().getHour();
        int lunchEndHour = settingsService.getLunchBreakEnd().getHour();
        int fridayEndHour = settingsService.getFridayLatestEndTime().getHour();

        Set<String> expected = new HashSet<>();
        List<DayOfWeek> weekDays = List.of(
                DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY);

        for (DayOfWeek day : weekDays) {
            for (int hour = startHour; hour < endHour; hour++) {
                if (hour >= lunchStartHour && hour < lunchEndHour) {
                    continue;
                }
                expected.add(toTimeslotKey(day, LocalTime.of(hour, 0)));
            }
        }

        for (int hour = startHour; hour < fridayEndHour; hour++) {
            if (hour >= lunchStartHour && hour < lunchEndHour) {
                continue;
            }
            expected.add(toTimeslotKey(DayOfWeek.FRIDAY, LocalTime.of(hour, 0)));
        }
        return expected;
    }

    private String toTimeslotKey(DayOfWeek dayOfWeek, LocalTime startTime) {
        return dayOfWeek + "|" + startTime;
    }

    private long elapsedMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}

