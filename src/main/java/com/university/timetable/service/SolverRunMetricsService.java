package com.university.timetable.service;

import com.university.timetable.domain.SolverRunMetric;
import com.university.timetable.repository.SolverRunMetricRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SolverRunMetricsService {

    private final SolverRunMetricRepository solverRunMetricRepository;

    @Transactional
    public void recordRun(SolverRunMetric metric) {
        solverRunMetricRepository.save(metric);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getMetrics(String mode, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 1000));
        Pageable pageable = PageRequest.of(0, boundedLimit);

        List<SolverRunMetric> runs = (mode == null || mode.isBlank())
                ? solverRunMetricRepository.findAllByOrderByStartedAtDesc(pageable).getContent()
                : solverRunMetricRepository.findByModeIgnoreCaseOrderByStartedAtDesc(mode.trim(), pageable).getContent();

        List<Long> durations = runs.stream()
                .map(SolverRunMetric::getDurationMs)
                .filter(Objects::nonNull)
                .filter(v -> v >= 0)
                .sorted()
                .collect(Collectors.toList());

        List<Long> firstBest = runs.stream()
                .map(SolverRunMetric::getTimeToFirstBestMs)
                .filter(Objects::nonNull)
                .filter(v -> v >= 0)
                .sorted()
                .collect(Collectors.toList());

        Map<String, Long> statusCounts = runs.stream()
                .collect(Collectors.groupingBy(
                        metric -> metric.getStatus() == null ? "UNKNOWN" : metric.getStatus(),
                        Collectors.counting()));

        List<Map<String, Object>> recentRuns = new ArrayList<>(runs.size());
        for (SolverRunMetric run : runs) {
            Map<String, Object> row = new HashMap<>();
            row.put("runId", run.getRunId());
            row.put("mode", run.getMode());
            row.put("profile", run.getProfile());
            row.put("status", run.getStatus());
            row.put("bestScore", run.getBestScore());
            row.put("bestHardScore", run.getBestHardScore());
            row.put("bestSoftScore", run.getBestSoftScore());
            row.put("durationMs", run.getDurationMs());
            row.put("impactedLessonsCount", run.getImpactedLessonsCount());
            row.put("lockedLessonsCount", run.getLockedLessonsCount());
            row.put("changedLessonsCount", run.getChangedLessonsCount());
            row.put("timeToFirstBestMs", run.getTimeToFirstBestMs());
            row.put("improvementCount", run.getImprovementCount());
            row.put("persistenceCount", run.getPersistenceCount());
            row.put("avgPersistenceMs", run.getAvgPersistenceMs());
            row.put("lessonsCount", run.getLessonsCount());
            row.put("timeslotsCount", run.getTimeslotsCount());
            row.put("roomsCount", run.getRoomsCount());
            row.put("startedAt", run.getStartedAt());
            row.put("finishedAt", run.getFinishedAt());
            row.put("errorMessage", run.getErrorMessage());
            row.put("moveThreadCount", run.getMoveThreadCount());
            row.put("environmentMode", run.getEnvironmentMode());
            row.put("parallelSolverCount", run.getParallelSolverCount());
            row.put("availableProcessors", run.getAvailableProcessors());
            recentRuns.add(row);
        }

        Map<String, Object> durationStats = buildStats(durations);
        Map<String, Object> firstBestStats = buildStats(firstBest);

        Map<String, Object> response = new HashMap<>();
        response.put("modeFilter", mode);
        response.put("sampleSize", runs.size());
        response.put("durationMs", durationStats);
        response.put("timeToFirstBestMs", firstBestStats);
        response.put("statusCounts", statusCounts);
        response.put("recentRuns", recentRuns);
        return response;
    }

    private Map<String, Object> buildStats(List<Long> sortedValues) {
        Map<String, Object> stats = new HashMap<>();
        if (sortedValues.isEmpty()) {
            stats.put("count", 0);
            stats.put("min", null);
            stats.put("max", null);
            stats.put("avg", null);
            stats.put("p50", null);
            stats.put("p95", null);
            return stats;
        }

        long min = sortedValues.get(0);
        long max = sortedValues.get(sortedValues.size() - 1);
        double avg = sortedValues.stream().mapToLong(Long::longValue).average().orElse(0.0);
        long p50 = percentile(sortedValues, 0.50);
        long p95 = percentile(sortedValues, 0.95);

        stats.put("count", sortedValues.size());
        stats.put("min", min);
        stats.put("max", max);
        stats.put("avg", avg);
        stats.put("p50", p50);
        stats.put("p95", p95);
        return stats;
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
}
