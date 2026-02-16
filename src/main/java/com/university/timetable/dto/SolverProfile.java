package com.university.timetable.dto;

public enum SolverProfile {
    FAST_FEASIBLE,
    BALANCED,
    QUALITY;

    public static SolverProfile fromNullable(String value) {
        if (value == null || value.isBlank()) {
            return BALANCED;
        }
        return SolverProfile.valueOf(value.trim().toUpperCase());
    }
}
