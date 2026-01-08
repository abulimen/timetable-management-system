package com.university.timetable.controller;

import com.university.timetable.domain.ConstraintSetting;
import com.university.timetable.domain.ConstraintSetting.Category;
import com.university.timetable.domain.Timeslot;
import com.university.timetable.dto.ConstraintSettingDTO;
import com.university.timetable.service.ConstraintSettingsService;
import com.university.timetable.service.TimeslotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for managing constraint settings.
 * Allows administrators to view and modify constraint parameters.
 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Slf4j
public class SettingsController {

    private final ConstraintSettingsService settingsService;
    private final TimeslotService timeslotService;

    /**
     * Get all constraint settings.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<List<ConstraintSettingDTO>> getAllSettings() {
        log.info("Fetching all constraint settings");
        List<ConstraintSettingDTO> settings = settingsService.getAllSettings()
                .stream()
                .map(ConstraintSettingDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(settings);
    }

    /**
     * Get settings by category.
     */
    @GetMapping("/category/{category}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<List<ConstraintSettingDTO>> getSettingsByCategory(
            @PathVariable String category) {
        log.info("Fetching settings for category: {}", category);
        try {
            Category cat = Category.valueOf(category.toUpperCase());
            List<ConstraintSettingDTO> settings = settingsService.getSettingsByCategory(cat)
                    .stream()
                    .map(ConstraintSettingDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(settings);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get a single setting by key.
     */
    @GetMapping("/{key}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<ConstraintSettingDTO> getSetting(@PathVariable String key) {
        log.info("Fetching setting: {}", key);
        ConstraintSetting setting = settingsService.getSetting(key);
        return ResponseEntity.ok(ConstraintSettingDTO.fromEntity(setting));
    }

    /**
     * Update a setting's value.
     */
    @PutMapping("/{key}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<ConstraintSettingDTO> updateSetting(
            @PathVariable String key,
            @RequestBody Map<String, String> body) {
        String value = body.get("value");
        if (value == null) {
            return ResponseEntity.badRequest().build();
        }

        log.info("Updating setting {} = {}", key, value);
        ConstraintSetting updated = settingsService.updateSetting(key, value);
        return ResponseEntity.ok(ConstraintSettingDTO.fromEntity(updated));
    }

    /**
     * Refresh settings cache (useful after direct DB updates).
     */
    @PostMapping("/refresh")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, String>> refreshCache() {
        log.info("Refreshing settings cache");
        settingsService.refreshCache();
        return ResponseEntity.ok(Map.of("status", "Cache refreshed"));
    }

    /**
     * Regenerate timeslots based on current settings.
     * Call this after changing timing settings to apply changes.
     */
    @PostMapping("/regenerate-timeslots")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN')")
    public ResponseEntity<Map<String, Object>> regenerateTimeslots() {
        log.info("Regenerating timeslots from current settings");
        List<Timeslot> timeslots = timeslotService.regenerateTimeslots();
        return ResponseEntity.ok(Map.of(
                "status", "Timeslots regenerated",
                "count", timeslots.size(),
                "message", "Generated " + timeslots.size() + " timeslots from settings"));
    }

    /**
     * Get current effective settings summary.
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN', 'ADMIN', 'COORDINATOR')")
    public ResponseEntity<Map<String, Object>> getSettingsSummary() {
        return ResponseEntity.ok(Map.of(
                "timing", Map.of(
                        "lunchBreakStart", settingsService.getLunchBreakStart().toString(),
                        "lunchBreakEnd", settingsService.getLunchBreakEnd().toString(),
                        "earliestStartTime", settingsService.getEarliestStartTime().toString(),
                        "latestEndTime", settingsService.getLatestEndTime().toString(),
                        "fridayLatestEndTime", settingsService.getFridayLatestEndTime().toString()),
                "limits", Map.of(
                        "maxLecturerHoursPerDay", settingsService.getMaxLecturerHoursPerDay(),
                        "maxStudentConsecutiveHours", settingsService.getMaxStudentConsecutiveHours(),
                        "minBreakBetweenLessons", settingsService.getMinBreakBetweenLessons()),
                "weights", Map.of(
                        "roomCapacity", settingsService.getWeightRoomCapacity(),
                        "dayBalance", settingsService.getWeightDayBalance(),
                        "lecturerTransition", settingsService.getWeightLecturerTransition(),
                        "studentFatigue", settingsService.getWeightStudentFatigue()),
                "features", Map.of(
                        "lunchBreakEnforced", settingsService.isLunchBreakEnforced(),
                        "dayBalanceEnforced", settingsService.isDayBalanceEnforced(),
                        "sameCourseSameDayAllowed", settingsService.isSameCourseSameDayAllowed())));
    }
}
