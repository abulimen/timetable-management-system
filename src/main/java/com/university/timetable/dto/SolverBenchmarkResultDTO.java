package com.university.timetable.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SolverBenchmarkResultDTO {
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private int warmupRuns;
    private int measuredRuns;
    private int pollIntervalMs;
    private int perRunTimeoutSeconds;
    private List<ScenarioResultDTO> scenarios = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ScenarioResultDTO {
        private String mode;
        private String profile;
        private List<RunSampleDTO> warmupSamples = new ArrayList<>();
        private List<RunSampleDTO> measuredSamples = new ArrayList<>();
        private Long minDurationMs;
        private Long p50DurationMs;
        private Long p95DurationMs;
        private Long maxDurationMs;
        private Double avgDurationMs;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RunSampleDTO {
        private String state;
        private String runOutcome;
        private String score;
        private Integer bestHardScore;
        private Integer bestSoftScore;
        private Boolean feasible;
        private Boolean validForPerf;
        private Long durationMs;
    }
}
