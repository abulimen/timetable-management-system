package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TimetableChangeStatusDTO {
    private boolean pendingChanges;
    private String reason;
    private LocalDateTime changedAt;
    private boolean editingEnabled;
}
