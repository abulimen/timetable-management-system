package com.university.timetable.dto;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportRowError {
    private int rowNumber;
    private String message;
    private Map<String, String> rawData;
}
