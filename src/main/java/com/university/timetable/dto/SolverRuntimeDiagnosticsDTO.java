package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolverRuntimeDiagnosticsDTO {
    private int availableProcessors;
    private String moveThreadCount;
    private String environmentMode;
    private String parallelSolverCount;
    private boolean reproducible;
}
