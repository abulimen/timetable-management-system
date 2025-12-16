package com.university.timetable.service;

import com.university.timetable.dto.ImportResultDTO;
import com.university.timetable.repository.ZoneRepository;
import com.university.timetable.util.LevenshteinMatcher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * ValidationService - validates imported Excel data.
 * 
 * Based on specs.md Excel Validation Protocol:
 * - Rule 1: 24-Hour Rule (weekly hours > 10 = reject)
 * - Rule 2: Ghost Room Rule (non-existent zone = reject)
 * - Rule 3: Time Paradox Rule (invalid HH:MM format = reject)
 * - Cleaning: Trim whitespace, standardize case
 * - Fuzzy Matching: Levenshtein for department names
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ValidationService {

    private final ZoneRepository zoneRepository;
    
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]?[0-9]|2[0-3]):[0-5][0-9]$");
    private static final int MAX_WEEKLY_HOURS = 10;
    private static final double FUZZY_AUTO_CORRECT_THRESHOLD = 0.9;
    private static final double FUZZY_CREATE_NEW_THRESHOLD = 0.6;

    /**
     * Rule 1: 24-Hour Rule
     * Check: If Weekly Hours > 10 -> Reject Row (Likely typo).
     */
    public boolean validateWeeklyHours(int hours, ImportResultDTO result, int row) {
        if (hours > MAX_WEEKLY_HOURS) {
            result.addError(row, "weekly_hours", 
                "Weekly hours (" + hours + ") exceeds maximum of " + MAX_WEEKLY_HOURS + " (likely typo)", 
                "HOURS_EXCEEDED");
            return false;
        }
        if (hours <= 0) {
            result.addError(row, "weekly_hours", 
                "Weekly hours must be positive", 
                "INVALID_HOURS");
            return false;
        }
        return true;
    }

    /**
     * Rule 2: Ghost Room Rule
     * Check: If Allowed Zones contains a non-existent Zone -> Reject Row.
     */
    public boolean validateZonesExist(List<String> zoneNames, ImportResultDTO result, int row) {
        for (String zoneName : zoneNames) {
            if (!zoneRepository.existsByName(sanitizeString(zoneName))) {
                result.addError(row, "allowed_zones", 
                    "Zone '" + zoneName + "' does not exist", 
                    "INVALID_ZONE");
                return false;
            }
        }
        return true;
    }

    /**
     * Rule 3: Time Paradox Rule
     * Check: If Unavailable Time is not HH:MM format or out of bounds -> Reject Row.
     */
    public boolean validateTimeFormat(String time, ImportResultDTO result, int row, String column) {
        if (time == null || time.trim().isEmpty()) {
            return true; // Empty time is allowed (means no unavailability)
        }
        
        String trimmed = time.trim();
        if (!TIME_PATTERN.matcher(trimmed).matches()) {
            result.addError(row, column, 
                "Invalid time format '" + time + "'. Expected HH:MM", 
                "INVALID_TIME_FORMAT");
            return false;
        }
        
        try {
            LocalTime.parse(trimmed);
        } catch (DateTimeParseException e) {
            result.addError(row, column, 
                "Invalid time value '" + time + "'", 
                "INVALID_TIME_VALUE");
            return false;
        }
        
        return true;
    }

    /**
     * Sanitize string input: trim whitespace and standardize to Title Case.
     */
    public String sanitizeString(String input) {
        if (input == null) {
            return null;
        }
        String trimmed = input.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        return toTitleCase(trimmed);
    }

    /**
     * Convert string to Title Case.
     */
    private String toTitleCase(String input) {
        StringBuilder titleCase = new StringBuilder();
        boolean nextTitleCase = true;

        for (char c : input.toCharArray()) {
            if (Character.isSpaceChar(c)) {
                nextTitleCase = true;
            } else if (nextTitleCase) {
                c = Character.toTitleCase(c);
                nextTitleCase = false;
            } else {
                c = Character.toLowerCase(c);
            }
            titleCase.append(c);
        }

        return titleCase.toString();
    }

    /**
     * Fuzzy match a name against existing names.
     * Based on specs.md:
     * - Match > 90%: Auto-correct and log warning
     * - Match < 60%: Create new
     * 
     * @return The matched/corrected name, or the original if no good match
     */
    public String fuzzyMatchName(String input, List<String> existingNames, 
                                  ImportResultDTO result, int row, String column) {
        if (input == null || existingNames == null || existingNames.isEmpty()) {
            return sanitizeString(input);
        }
        
        String sanitized = sanitizeString(input);
        
        // Check for exact match first
        for (String existing : existingNames) {
            if (existing.equalsIgnoreCase(sanitized)) {
                return existing;
            }
        }
        
        // Find best fuzzy match
        String bestMatch = null;
        double bestScore = 0;
        
        for (String existing : existingNames) {
            double score = LevenshteinMatcher.similarity(sanitized, existing);
            if (score > bestScore) {
                bestScore = score;
                bestMatch = existing;
            }
        }
        
        if (bestScore > FUZZY_AUTO_CORRECT_THRESHOLD) {
            // Auto-correct with warning
            result.addWarning(row, column, input, bestMatch, 
                "Auto-corrected via fuzzy match (" + String.format("%.0f%%", bestScore * 100) + " similarity)");
            log.info("Fuzzy match: '{}' -> '{}' ({}%)", input, bestMatch, String.format("%.0f", bestScore * 100));
            return bestMatch;
        } else if (bestScore < FUZZY_CREATE_NEW_THRESHOLD) {
            // Create new (the sanitized input)
            log.info("No close match for '{}' (best: {}%), will create new", input, String.format("%.0f", bestScore * 100));
            return sanitized;
        }
        
        // Between thresholds - return sanitized original
        return sanitized;
    }

    /**
     * Validate course code format (basic validation).
     */
    public boolean validateCourseCode(String code, ImportResultDTO result, int row) {
        if (code == null || code.trim().isEmpty()) {
            result.addError(row, "code", "Course code is required", "MISSING_CODE");
            return false;
        }
        return true;
    }

    /**
     * Validate required string field.
     */
    public boolean validateRequired(String value, String fieldName, ImportResultDTO result, int row) {
        if (value == null || value.trim().isEmpty()) {
            result.addError(row, fieldName, fieldName + " is required", "MISSING_REQUIRED");
            return false;
        }
        return true;
    }

    /**
     * Validate positive integer.
     */
    public boolean validatePositiveInteger(Integer value, String fieldName, ImportResultDTO result, int row) {
        if (value == null || value <= 0) {
            result.addError(row, fieldName, fieldName + " must be a positive integer", "INVALID_NUMBER");
            return false;
        }
        return true;
    }
}
