package com.university.timetable.dto;

public enum SolverProfile {
    BALANCED,
    QUALITY;

    public static SolverProfile fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        try {
            return SolverProfile.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return BALANCED;
        }
    }
}
