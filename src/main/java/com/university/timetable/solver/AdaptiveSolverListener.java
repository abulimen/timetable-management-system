package com.university.timetable.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.event.BestSolutionChangedEvent;
import ai.timefold.solver.core.api.solver.event.SolverEventListener;
import com.university.timetable.domain.TimeTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Listens for solver events and updates the adaptive soft constraint multiplier.
 * 
 * <p>When the hard score improves (fewer violations), soft constraints are 
 * strengthened to focus more on quality. When hard violations are high, soft
 * constraints are weakened to focus on feasibility.
 * 
 * <p>The multiplier is stored in a static AtomicReference that the
 * TimetableConstraintProvider reads during constraint evaluation.
 * 
 * <p>The multiplier ranges from 0.3 (hard violations at max) to 1.0 (zero hard violations).
 * This creates a smooth transition from "fix hard constraints" to "optimize quality".
 */
public class AdaptiveSolverListener implements SolverEventListener<TimeTable> {

    private static final Logger log = LoggerFactory.getLogger(AdaptiveSolverListener.class);

    /** Static reference to the current soft weight multiplier */
    public static final AtomicReference<Double> SOFT_WEIGHT_MULTIPLIER = 
            new AtomicReference<>(0.3);

    private static final double MULTIPLIER_MIN = 0.3;
    private static final double MULTIPLIER_MAX = 1.0;
    private static final int MAX_POSSIBLE_VIOLATIONS = 1832 * 5; // Estimate

    private int lastHardScore = Integer.MIN_VALUE;
    private int updateCount = 0;

    @Override
    public void bestSolutionChanged(BestSolutionChangedEvent<TimeTable> event) {
        TimeTable solution = event.getNewBestSolution();
        if (solution == null || solution.getScore() == null) {
            return;
        }

        HardSoftScore score = solution.getScore();
        int hardScore = score.hardScore();

        // Only update if hard score changed significantly (every 10 violations)
        if (Math.abs(hardScore - lastHardScore) >= 10 || hardScore >= 0) {
            int violations = Math.abs(hardScore);
            double ratio = (double) violations / MAX_POSSIBLE_VIOLATIONS;
            ratio = Math.min(1.0, Math.max(0.0, ratio));
            double multiplier = MULTIPLIER_MIN + (1.0 - ratio) * (MULTIPLIER_MAX - MULTIPLIER_MIN);

            SOFT_WEIGHT_MULTIPLIER.set(multiplier);
            lastHardScore = hardScore;
            updateCount++;

            if (updateCount % 10 == 0) {
                log.info("Adaptive multiplier updated: hard={}, multiplier={:.2f}", 
                        hardScore, multiplier);
            }
        }
    }
}