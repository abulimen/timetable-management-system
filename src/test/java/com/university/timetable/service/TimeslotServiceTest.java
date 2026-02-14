package com.university.timetable.service;

import com.university.timetable.domain.Timeslot;
import com.university.timetable.repository.LessonRepository;
import com.university.timetable.repository.TimeslotRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for TimeslotService "Missing Tooth" pattern.
 * Based on specs.md Temporal Modeling:
 * - Mon-Thu: 07:00-11:00, 13:00-17:00 (excludes 12:00)
 * - Friday: 07:00-11:00 only
 */
@ExtendWith(MockitoExtension.class)
class TimeslotServiceTest {

    @Mock
    private TimeslotRepository timeslotRepository;
    @Mock
    private LessonRepository lessonRepository;
    @Mock
    private ConstraintSettingsService settingsService;
    @Mock
    private EntityManager entityManager;

    @InjectMocks
    private TimeslotService timeslotService;

    @BeforeEach
    void setUp() {
        when(timeslotRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonRepository.findAll()).thenReturn(Collections.emptyList());
        when(lessonRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        when(settingsService.getEarliestStartTime()).thenReturn(LocalTime.of(7, 0));
        when(settingsService.getLatestEndTime()).thenReturn(LocalTime.of(18, 0));
        when(settingsService.getLunchBreakStart()).thenReturn(LocalTime.of(12, 0));
        when(settingsService.getLunchBreakEnd()).thenReturn(LocalTime.of(13, 0));
        when(settingsService.getFridayLatestEndTime()).thenReturn(LocalTime.of(12, 0));
    }

    @Test
    void generateTimeslots_createsCorrectNumberOfSlots() {
        List<Timeslot> timeslots = timeslotService.generateTimeslots();
        
        // Mon-Thu: 5 morning + 5 afternoon = 10 per day * 4 days = 40
        // Friday: 5 morning = 5
        // Total: 45
        assertThat(timeslots).hasSize(45);
    }

    @Test
    void generateTimeslots_excludesLunchHour() {
        List<Timeslot> timeslots = timeslotService.generateTimeslots();
        
        // No 12:00 slots should exist
        assertThat(timeslots)
            .noneMatch(t -> t.getStartTime().equals(LocalTime.of(12, 0)));
    }

    @Test
    void generateTimeslots_mondayHasCorrectSlots() {
        List<Timeslot> timeslots = timeslotService.generateTimeslots();
        
        List<Timeslot> mondaySlots = timeslots.stream()
            .filter(t -> t.getDayOfWeek() == DayOfWeek.MONDAY)
            .toList();
        
        // 5 morning (7-11) + 5 afternoon (13-17) = 10
        assertThat(mondaySlots).hasSize(10);
        
        // Verify morning slots
        assertThat(mondaySlots).anyMatch(t -> t.getStartTime().equals(LocalTime.of(7, 0)));
        assertThat(mondaySlots).anyMatch(t -> t.getStartTime().equals(LocalTime.of(11, 0)));
        
        // Verify afternoon slots
        assertThat(mondaySlots).anyMatch(t -> t.getStartTime().equals(LocalTime.of(13, 0)));
        assertThat(mondaySlots).anyMatch(t -> t.getStartTime().equals(LocalTime.of(17, 0)));
        
        // Verify no lunch slot
        assertThat(mondaySlots).noneMatch(t -> t.getStartTime().equals(LocalTime.of(12, 0)));
    }

    @Test
    void generateTimeslots_fridayHasOnlyMorningSlots() {
        List<Timeslot> timeslots = timeslotService.generateTimeslots();
        
        List<Timeslot> fridaySlots = timeslots.stream()
            .filter(t -> t.getDayOfWeek() == DayOfWeek.FRIDAY)
            .toList();
        
        // Friday: 5 morning slots only
        assertThat(fridaySlots).hasSize(5);
        
        // All slots should be before noon
        assertThat(fridaySlots)
            .allMatch(t -> t.getStartTime().isBefore(LocalTime.of(12, 0)));
    }

    @Test
    void generateTimeslots_noWeekendsIncluded() {
        List<Timeslot> timeslots = timeslotService.generateTimeslots();
        
        assertThat(timeslots)
            .noneMatch(t -> t.getDayOfWeek() == DayOfWeek.SATURDAY);
        assertThat(timeslots)
            .noneMatch(t -> t.getDayOfWeek() == DayOfWeek.SUNDAY);
    }

    @Test
    void generateTimeslots_allSlotsStartOnTheHour() {
        List<Timeslot> timeslots = timeslotService.generateTimeslots();
        
        assertThat(timeslots)
            .allMatch(t -> t.getStartTime().getMinute() == 0);
    }
}
