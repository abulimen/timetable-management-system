package com.university.timetable.service;

import com.university.timetable.domain.ConstraintSetting;
import com.university.timetable.domain.ConstraintSetting.Category;
import com.university.timetable.repository.ConstraintSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for managing and retrieving constraint settings.
 * Provides type-safe getters for various settings and caches values for performance.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConstraintSettingsService {

    private final ConstraintSettingRepository repository;
    
    // Cache for fast access during solving
    private final Map<String, String> settingsCache = new ConcurrentHashMap<>();
    
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    @PostConstruct
    public void init() {
        refreshCache();
        log.info("Loaded {} constraint settings into cache", settingsCache.size());
    }

    /**
     * Refresh the settings cache from database.
     * Call this after updating settings.
     */
    @Transactional(readOnly = true)
    public void refreshCache() {
        settingsCache.clear();
        repository.findAll().forEach(s -> 
            settingsCache.put(s.getSettingKey(), s.getSettingValue())
        );
    }

    // ==================== TIMING SETTINGS ====================

    public LocalTime getLunchBreakStart() {
        return getTime("lunch_break_start", LocalTime.of(12, 0));
    }

    public LocalTime getLunchBreakEnd() {
        return getTime("lunch_break_end", LocalTime.of(13, 0));
    }

    public LocalTime getEarliestStartTime() {
        return getTime("earliest_start_time", LocalTime.of(7, 0));
    }

    public LocalTime getLatestEndTime() {
        return getTime("latest_end_time", LocalTime.of(19, 0));
    }

    public LocalTime getFridayLatestEndTime() {
        return getTime("friday_latest_end_time", LocalTime.of(12, 0));
    }

    // ==================== LIMIT SETTINGS ====================

    public int getMaxLecturerHoursPerDay() {
        return getInt("max_lecturer_hours_per_day", 6);
    }

    public int getMaxStudentConsecutiveHours() {
        return getInt("max_student_consecutive_hours", 4);
    }

    public int getMaxLecturerConsecutiveHours() {
        return getInt("max_lecturer_consecutive_hours", 4);
    }

    public int getMinBreakBetweenLessons() {
        return getInt("min_break_between_lessons", 0);
    }

    // ==================== WEIGHT SETTINGS ====================

    public int getWeightRoomCapacity() {
        return getInt("weight_room_capacity", 1);
    }

    public int getWeightDayBalance() {
        return getInt("weight_day_balance", 2);
    }

    public int getWeightLecturerTransition() {
        return getInt("weight_lecturer_transition", 5);
    }

    public int getWeightStudentFatigue() {
        return getInt("weight_student_fatigue", 1);
    }

    // ==================== FEATURE FLAGS ====================

    public boolean isLunchBreakEnforced() {
        return getBoolean("enforce_lunch_break", true);
    }

    public boolean isDayBalanceEnforced() {
        return getBoolean("enforce_day_balance", true);
    }

    public boolean isSameCourseSameDayAllowed() {
        return getBoolean("same_course_same_day_allowed", false);
    }

    // ==================== CRUD OPERATIONS ====================

    @Transactional(readOnly = true)
    public List<ConstraintSetting> getAllSettings() {
        return repository.findAll();
    }

    @Transactional(readOnly = true)
    public List<ConstraintSetting> getSettingsByCategory(Category category) {
        return repository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public ConstraintSetting getSetting(String key) {
        return repository.findBySettingKey(key)
            .orElseThrow(() -> new IllegalArgumentException("Setting not found: " + key));
    }

    @Transactional
    public ConstraintSetting updateSetting(String key, String value) {
        ConstraintSetting setting = getSetting(key);
        
        // Validate the value based on data type
        validateValue(setting, value);
        
        setting.setSettingValue(value);
        ConstraintSetting saved = repository.save(setting);
        
        // Refresh cache
        settingsCache.put(key, value);
        
        log.info("Updated setting {} = {}", key, value);
        return saved;
    }

    // ==================== HELPER METHODS ====================

    private String getString(String key, String defaultValue) {
        return settingsCache.getOrDefault(key, defaultValue);
    }

    public int getInt(String key, int defaultValue) {
        String value = settingsCache.get(key);
        if (value == null) return defaultValue;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer value for {}: {}", key, value);
            return defaultValue;
        }
    }

    private LocalTime getTime(String key, LocalTime defaultValue) {
        String value = settingsCache.get(key);
        if (value == null) return defaultValue;
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("Invalid time value for {}: {}", key, value);
            return defaultValue;
        }
    }

    private boolean getBoolean(String key, boolean defaultValue) {
        String value = settingsCache.get(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    private void validateValue(ConstraintSetting setting, String value) {
        switch (setting.getDataType()) {
            case INTEGER:
                try {
                    Integer.parseInt(value);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Value must be an integer: " + value);
                }
                break;
            case TIME:
                try {
                    LocalTime.parse(value, TIME_FORMATTER);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Value must be time format HH:mm: " + value);
                }
                break;
            case BOOLEAN:
                if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                    throw new IllegalArgumentException("Value must be true or false: " + value);
                }
                break;
            case STRING:
                // No validation needed
                break;
        }
    }
}
