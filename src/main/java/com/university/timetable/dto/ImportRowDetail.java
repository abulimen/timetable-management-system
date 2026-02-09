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
public class ImportRowDetail {
    private int rowNumber;
    private Map<String, String> data;
    private String status; // NEW, EXISTING, UPDATED
    private String message;
}
