package com.university.timetable.config;

import com.university.timetable.domain.TimeTable;
import org.optaplanner.core.api.score.buildin.hardsoft.HardSoftScore;
import org.optaplanner.core.api.solver.SolverFactory;
import org.optaplanner.core.api.solver.SolverManager;
import org.optaplanner.core.api.solver.SolutionManager;
import org.optaplanner.core.config.solver.SolverConfig;
import org.optaplanner.core.config.solver.SolverManagerConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for OptaPlanner beans.
 */
@Configuration
public class OptaPlannerConfig {

    /**
     * SolverFactory for creating solvers.
     */
    @Bean
    public SolverFactory<TimeTable> solverFactory() {
        return SolverFactory.create(
            SolverConfig.createFromXmlResource("solver-config.xml"));
    }

    /**
     * SolverManager for async solving.
     */
    @Bean
    public SolverManager<TimeTable, Long> solverManager(SolverFactory<TimeTable> solverFactory) {
        return SolverManager.create(solverFactory, new SolverManagerConfig());
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
}
