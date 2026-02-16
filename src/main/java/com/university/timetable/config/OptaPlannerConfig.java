package com.university.timetable.config;

import com.university.timetable.domain.TimeTable;
import com.university.timetable.dto.SolverRuntimeDiagnosticsDTO;
import com.university.timetable.service.ConstraintSettingsService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.api.solver.SolutionManager;
import org.optaplanner.core.config.constructionheuristic.ConstructionHeuristicPhaseConfig;
import org.optaplanner.core.config.solver.EnvironmentMode;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.SolverManagerConfig;
import org.optaplanner.core.config.localsearch.LocalSearchPhaseConfig;
import org.optaplanner.core.config.localsearch.decider.forager.LocalSearchForagerConfig;
import org.optaplanner.core.config.phase.PhaseConfig;
import org.optaplanner.core.config.solver.termination.TerminationConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for OptaPlanner beans.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
public class OptaPlannerConfig {

    private final ConstraintSettingsService settingsService;

    @Value("${solver.performance.move-thread-count:4}")
    private String moveThreadCountDefault;

    @Value("${solver.performance.environment-mode:REPRODUCIBLE}")
    private String environmentModeDefault;

    @Value("${solver.performance.parallel-solver-count:1}")
    private String parallelSolverCountDefault;

    @PostConstruct
    void logResolvedRuntimeConfig() {
        log.info("Solver runtime config: processors={}, moveThreadCount={}, environmentMode={}, parallelSolverCount={}",
                Runtime.getRuntime().availableProcessors(),
                resolveMoveThreadCount(),
                resolveEnvironmentMode(),
                resolveParallelSolverCount());
    }

    /**
     * SolverFactory for creating solvers.
     */
    @Bean
    public SolverFactory<TimeTable> solverFactory() {
        SolverConfig config = SolverConfig.createFromXmlResource("solver-config.xml");
        config.setMoveThreadCount(resolveMoveThreadCount());
        config.setEnvironmentMode(EnvironmentMode.valueOf(resolveEnvironmentMode()));
        applyDynamicTuning(config);
        return SolverFactory.create(config);
    }

    /**
     * SolverManager for async solving.
     */
    @Bean
    public SolverManager<TimeTable, Long> solverManager(SolverFactory<TimeTable> solverFactory) {
        SolverManagerConfig managerConfig = new SolverManagerConfig();
        managerConfig.setParallelSolverCount(resolveParallelSolverCount());
        return SolverManager.create(solverFactory, managerConfig);
    }

    /**
     * SolutionManager for score explanation and analysis.
     * Used by ConstraintJustificationService to explain violations.
     */
    @Bean
    public SolutionManager<TimeTable, HardSoftScore> solutionManager(
            SolverFactory<TimeTable> solverFactory) {
        return SolutionManager.create(solverFactory);
    }

    @Bean
    public SolverRuntimeDiagnosticsDTO solverRuntimeDiagnosticsDTO() {
        int processors = Runtime.getRuntime().availableProcessors();
        String resolvedMode = resolveEnvironmentMode();
        return new SolverRuntimeDiagnosticsDTO(
                processors,
                resolveMoveThreadCount(),
                resolvedMode,
                resolveParallelSolverCount(),
                "REPRODUCIBLE".equals(resolvedMode));
    }

    private String resolveMoveThreadCount() {
        String value = settingsService.getSolverMoveThreadCount();
        if (value == null || value.isBlank()) {
            return sanitizeMoveThreadCount(moveThreadCountDefault);
        }
        return sanitizeMoveThreadCount(value.trim());
    }

    private String resolveEnvironmentMode() {
        String value = settingsService.getSolverEnvironmentMode();
        if (value == null || value.isBlank()) {
            value = environmentModeDefault;
        }
        String normalized = value.trim().toUpperCase();
        if (!"REPRODUCIBLE".equals(normalized) && !"NON_REPRODUCIBLE".equals(normalized)) {
            log.warn("Invalid solver_environment_mode='{}'; falling back to REPRODUCIBLE", value);
            return "REPRODUCIBLE";
        }
        return normalized;
    }

    private String resolveParallelSolverCount() {
        String value = settingsService.getSolverParallelSolverCount();
        if (value == null || value.isBlank()) {
            return sanitizeParallelSolverCount(parallelSolverCountDefault);
        }
        return sanitizeParallelSolverCount(value.trim());
    }

    private void applyDynamicTuning(SolverConfig config) {
        int minutesLimit = settingsService.getSolverMinutesSpentLimit();
        int unimprovedLimit = settingsService.getSolverUnimprovedSecondsSpentLimit();
        int acceptedCountLimit = settingsService.getSolverForagerAcceptedCountLimit();

        TerminationConfig termination = config.getTerminationConfig();
        if (termination == null) {
            termination = new TerminationConfig();
            config.setTerminationConfig(termination);
        }
        termination.setMinutesSpentLimit((long) Math.max(1, minutesLimit));
        termination.setUnimprovedSecondsSpentLimit((long) Math.max(5, unimprovedLimit));

        List<PhaseConfig> phases = config.getPhaseConfigList();
        if (phases == null || phases.isEmpty()) {
            phases = new ArrayList<>();
            phases.add(new ConstructionHeuristicPhaseConfig());
            phases.add(new LocalSearchPhaseConfig());
            config.setPhaseConfigList(phases);
        }
        for (PhaseConfig phase : phases) {
            if (phase instanceof LocalSearchPhaseConfig localSearchPhaseConfig) {
                LocalSearchForagerConfig foragerConfig = localSearchPhaseConfig.getForagerConfig();
                if (foragerConfig == null) {
                    foragerConfig = new LocalSearchForagerConfig();
                    localSearchPhaseConfig.setForagerConfig(foragerConfig);
                }
                foragerConfig.setAcceptedCountLimit(Math.max(1, acceptedCountLimit));
            }
        }

        log.info("Applied solver tuning from admin settings: minutesLimit={}, unimprovedSeconds={}, acceptedCountLimit={}",
                minutesLimit, unimprovedLimit, acceptedCountLimit);
    }

    private String sanitizeMoveThreadCount(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if ("AUTO".equals(normalized)) {
            return "AUTO";
        }
        try {
            int n = Integer.parseInt(normalized);
            if (n > 0) {
                return String.valueOf(n);
            }
        } catch (NumberFormatException ignored) {
        }
        log.warn("Invalid solver_move_thread_count='{}'; falling back to 4", value);
        return "4";
    }

    private String sanitizeParallelSolverCount(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        if ("AUTO".equals(normalized)) {
            return "AUTO";
        }
        try {
            int n = Integer.parseInt(normalized);
            if (n > 0) {
                return String.valueOf(n);
            }
        } catch (NumberFormatException ignored) {
        }
        log.warn("Invalid solver_parallel_solver_count='{}'; falling back to 1", value);
        return "1";
    }
}
