package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolverBenchmarkRequestDTO {
    private Integer warmupRuns = 1;
    private Integer measuredRuns = 5;
    private Integer pollIntervalMs = 1000;
    private Integer perRunTimeoutSeconds = 600;
    private List<String> modes = List.of("FULL_REPLAN");
    private List<String> profiles = List.of("BALANCED");
    private Boolean skipFeasibility = true;
    private Boolean clearAssignmentsBeforeEachRun = true;
}
