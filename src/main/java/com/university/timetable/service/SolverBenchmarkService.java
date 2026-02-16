package com.university.timetable.service;

import com.university.timetable.dto.SolveRequestDTO;
import com.university.timetable.dto.SolverBenchmarkRequestDTO;
import com.university.timetable.dto.SolverBenchmarkResultDTO;
import com.university.timetable.dto.SolverBenchmarkResultDTO.RunSampleDTO;
import com.university.timetable.dto.SolverBenchmarkResultDTO.ScenarioResultDTO;
import com.university.timetable.dto.SolverProfile;
import com.university.timetable.dto.SolverStatusDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SolverBenchmarkService {

    private final SolverService solverService;

    public SolverBenchmarkResultDTO runBenchmark(SolverBenchmarkRequestDTO request) {
        if (solverService.isSolving()) {
            throw new IllegalStateException("Cannot run benchmark while solver is active.");
        }

        int warmupRuns = bound(request.getWarmupRuns(), 0, 10, 1);
        int measuredRuns = bound(request.getMeasuredRuns(), 1, 20, 5);
        int pollIntervalMs = bound(request.getPollIntervalMs(), 200, 5000, 1000);
        int timeoutSec = bound(request.getPerRunTimeoutSeconds(), 30, 3600, 600);
        boolean skipFeasibility = request.getSkipFeasibility() == null || request.getSkipFeasibility();
        boolean clearAssignmentsBeforeEachRun = request.getClearAssignmentsBeforeEachRun() == null
                || request.getClearAssignmentsBeforeEachRun();

        List<String> modes = request.getModes() == null || request.getModes().isEmpty()
                ? List.of("FULL_REPLAN")
                : request.getModes();
        List<String> profiles = request.getProfiles() == null || request.getProfiles().isEmpty()
                ? List.of(SolverProfile.BALANCED.name())
                : request.getProfiles();

        SolverBenchmarkResultDTO result = new SolverBenchmarkResultDTO();
        result.setStartedAt(LocalDateTime.now());
        result.setWarmupRuns(warmupRuns);
        result.setMeasuredRuns(measuredRuns);
        result.setPollIntervalMs(pollIntervalMs);
        result.setPerRunTimeoutSeconds(timeoutSec);

        for (String rawMode : modes) {
            String mode = normalizeMode(rawMode);
            for (String rawProfile : profiles) {
                SolverProfile profile = SolverProfile.fromNullable(rawProfile);
                ScenarioResultDTO scenario = new ScenarioResultDTO();
                scenario.setMode(mode);
                scenario.setProfile(profile.name());
                log.info("Benchmark scenario started: mode={}, profile={}, warmup={}, measured={}",
                        mode, profile, warmupRuns, measuredRuns);

                for (int i = 0; i < warmupRuns; i++) {
                    scenario.getWarmupSamples().add(runSingle(mode, profile, skipFeasibility, clearAssignmentsBeforeEachRun, pollIntervalMs, timeoutSec));
                }
                for (int i = 0; i < measuredRuns; i++) {
                    scenario.getMeasuredSamples().add(runSingle(mode, profile, skipFeasibility, clearAssignmentsBeforeEachRun, pollIntervalMs, timeoutSec));
                }

                List<Long> durations = scenario.getMeasuredSamples().stream()
                        .filter(sample -> Boolean.TRUE.equals(sample.getValidForPerf()))
                        .map(RunSampleDTO::getDurationMs)
                        .filter(v -> v != null && v >= 0)
                        .sorted(Comparator.naturalOrder())
                        .toList();
                if (!durations.isEmpty()) {
                    scenario.setMinDurationMs(durations.get(0));
                    scenario.setMaxDurationMs(durations.get(durations.size() - 1));
                    scenario.setP50DurationMs(percentile(durations, 0.50));
                    scenario.setP95DurationMs(percentile(durations, 0.95));
                    scenario.setAvgDurationMs(durations.stream().mapToLong(Long::longValue).average().orElse(0.0));
                }
                result.getScenarios().add(scenario);
                log.info("Benchmark scenario complete: mode={}, profile={}, p50={}ms, p95={}ms, avg={}ms",
                        mode, profile, scenario.getP50DurationMs(), scenario.getP95DurationMs(), scenario.getAvgDurationMs());
            }
        }

        result.setFinishedAt(LocalDateTime.now());
        return result;
    }

    private RunSampleDTO runSingle(
            String mode,
            SolverProfile profile,
            boolean skipFeasibility,
            boolean clearAssignmentsBeforeEachRun,
            int pollIntervalMs,
            int timeoutSec) {
        if (clearAssignmentsBeforeEachRun) {
            int cleared = solverService.clearCurrentTimetable();
            log.debug("Benchmark pre-run clear: {} lessons reset", cleared);
        }

        SolveRequestDTO request = new SolveRequestDTO();
        request.setMode(mode);
        request.setProfile(profile.name());
        request.setSkipFeasibility(skipFeasibility);

        solverService.startSolving(request);

        long startedAt = System.currentTimeMillis();
        long timeoutAt = startedAt + timeoutSec * 1000L;
        while (true) {
            SolverStatusDTO status = solverService.getStatus();
            if (status != null && "NOT_SOLVING".equalsIgnoreCase(status.getState())) {
                boolean validForPerf = "COMPLETED".equalsIgnoreCase(status.getRunOutcome())
                        && Boolean.TRUE.equals(status.getFeasible())
                        && status.getDurationMs() != null;
                return new RunSampleDTO(
                        status.getState(),
                        status.getRunOutcome(),
                        status.getScore(),
                        status.getBestHardScore(),
                        status.getBestSoftScore(),
                        status.getFeasible(),
                        validForPerf,
                        status.getDurationMs());
            }
            if (System.currentTimeMillis() >= timeoutAt) {
                solverService.terminate();
                throw new IllegalStateException("Benchmark run timed out after " + timeoutSec + " seconds.");
            }
            sleep(pollIntervalMs);
        }
    }

    private int bound(Integer value, int min, int max, int fallback) {
        int raw = value == null ? fallback : value;
        return Math.max(min, Math.min(max, raw));
    }

    private long percentile(List<Long> sortedValues, double p) {
        if (sortedValues.isEmpty()) {
            return 0L;
        }
        if (sortedValues.size() == 1) {
            return sortedValues.get(0);
        }
        int index = (int) Math.ceil(p * sortedValues.size()) - 1;
        index = Math.max(0, Math.min(index, sortedValues.size() - 1));
        return sortedValues.get(index);
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "FULL_REPLAN";
        }
        String normalized = mode.trim().toUpperCase();
        return switch (normalized) {
            case "FULL_REPLAN", "STABILITY" -> normalized;
            default -> throw new IllegalArgumentException("Unsupported benchmark mode: " + mode);
        };
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Benchmark interrupted.");
        }
    }
}
