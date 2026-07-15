package com.university.timetable.solver.move;

import ai.timefold.solver.core.impl.heuristic.move.Move;
import ai.timefold.solver.core.impl.heuristic.selector.move.factory.MoveListFactory;
import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.TimeTable;
import com.university.timetable.domain.Timeslot;
import com.university.timetable.solver.AdaptiveSolverListener;
import com.university.timetable.solver.SpringContextHolder;
import com.university.timetable.service.ConstraintSettingsService;

import java.util.*;

/**
 * Factory for intelligent ruin-and-recreate moves.
 * <p>
 * Trigger conditions (ALL must be met):
 * <ol>
 * <li>Admin setting "ruin_recreate_enabled" is ON</li>
 * <li>Sufficient time has elapsed (max of 5 minutes or the configured
 * no-improve limit)</li>
 * <li>The solution still has MULTIPLE hard constraint violations</li>
 * <li>The solver appears stuck (score hasn't improved recently)</li>
 * </ol>
 * <p>
 * Adaptive behavior: when hard violations are high (soft multiplier low),
 * reduces stagnation threshold to trigger more aggressively and generates
 * more ruin moves per step.
 * <p>
 * When triggered, uses {@link ConflictAnalyzer} to intelligently target
 * the most problematic lessons rather than random destruction.
 */
public class RuinAndRecreateMoveFactory implements MoveListFactory<TimeTable> {

    /** Minimum elapsed time before ruin can fire (seconds). */
    private static final long MIN_COOLDOWN_SECONDS = 120; // 2 minutes

    /** Base number of ruin moves to generate per step. */
    private static final int BASE_MAX_RUIN_MOVES_PER_STEP = 3;
    private static final int MAX_RUIN_MOVES_CAP = 8;

    /**
     * Tracks when this factory was first called (proxy for solve start time).
     * Reset each time the factory is first invoked after a period of inactivity.
     */
    private long firstCallTimestamp = 0;

    /**
     * Tracks the best hard score seen. Used to detect stagnation.
     */
    private int bestHardScoreSeen = Integer.MIN_VALUE;
    private long bestHardScoreTimestamp = 0;

    /** Number of consecutive calls with no improvement. */
    private int stagnationCounter = 0;

    /** Base minimum stagnation calls before ruin fires. */
    private static final int BASE_MIN_STAGNATION_CALLS = 100;
    private static final int MIN_STAGNATION_CALLS_FLOOR = 30;

    @Override
    public List<? extends Move<TimeTable>> createMoveList(TimeTable solution) {
        // Gate 1: Check admin setting
        if (!isEnabled()) {
            return List.of();
        }

        // Adaptive stagnation threshold: when hard violations are high (soft multiplier low),
        // reduce stagnation threshold to trigger ruin-recreate more aggressively
        double softMultiplier = AdaptiveSolverListener.SOFT_WEIGHT_MULTIPLIER.get();
        int adaptiveStagnationThreshold = (int) (BASE_MIN_STAGNATION_CALLS * softMultiplier + MIN_STAGNATION_CALLS_FLOOR * (1.0 - softMultiplier));
        int adaptiveMaxMoves = (int) (BASE_MAX_RUIN_MOVES_PER_STEP + (MAX_RUIN_MOVES_CAP - BASE_MAX_RUIN_MOVES_PER_STEP) * (1.0 - softMultiplier));

        long now = System.currentTimeMillis();

        // Initialize timestamp on first call
        if (firstCallTimestamp == 0) {
            firstCallTimestamp = now;
            bestHardScoreTimestamp = now;
        }

        // Gate 2: Check cooldown — must have been solving for at least
        // max(5 minutes, configured no-improve limit)
        long cooldownSeconds = getCooldownSeconds();
        long elapsedSeconds = (now - firstCallTimestamp) / 1000;
        if (elapsedSeconds < cooldownSeconds) {
            return List.of();
        }

        // Gate 3: Find conflicting lessons — need at least ONE hard violation
        List<Lesson> conflictingLessons = ConflictAnalyzer.findConflictingLessons(solution);
        if (conflictingLessons.isEmpty()) {
            // No conflicts: normal moves are sufficient
            return List.of();
        }

        // Gate 4: Check for stagnation — score hasn't improved recently
        int currentHardViolations = conflictingLessons.size();
        if (currentHardViolations < bestHardScoreSeen || bestHardScoreSeen == Integer.MIN_VALUE) {
            bestHardScoreSeen = currentHardViolations;
            bestHardScoreTimestamp = now;
            stagnationCounter = 0;
        } else {
            stagnationCounter++;
        }

        // Must be stuck for a meaningful number of calls
        if (stagnationCounter < adaptiveStagnationThreshold) {
            return List.of();
        }

        // ALL gates passed — generate ruin-and-recreate moves
        int clusterSize = getClusterSize();
        List<Lesson> allLessons = solution.getLessons();
        List<Timeslot> allTimeslots = solution.getTimeslots();
        List<Room> allRooms = solution.getRooms();

        List<Move<TimeTable>> moves = new ArrayList<>();

        // Generate up to adaptiveMaxMoves moves, each with a different seed
        int movesToGenerate = Math.min(adaptiveMaxMoves, conflictingLessons.size());

        for (int i = 0; i < movesToGenerate; i++) {
            Lesson seed = conflictingLessons.get(i);
            List<Lesson> cluster = ConflictAnalyzer.buildConflictCluster(seed, allLessons, clusterSize);

            if (cluster.size() >= 2) {
                moves.add(new RuinAndRecreateMove(cluster, allTimeslots, allRooms));
            }
        }

        // Reset stagnation counter if we generated moves (don't fire every single step)
        if (!moves.isEmpty()) {
            stagnationCounter = 0;
        }

        return moves;
    }

    /**
     * Calculate the cooldown period: max(5 minutes, configured no-improve limit).
     */
    private long getCooldownSeconds() {
        long noImproveLimitSeconds = 60; // default
        try {
            ConstraintSettingsService css = SpringContextHolder.getBean(ConstraintSettingsService.class);
            if (css != null) {
                noImproveLimitSeconds = css.getSolverUnimprovedSecondsSpentLimit();
            }
        } catch (Exception ignored) {
            // Use default if Spring context not available
        }
        return Math.max(MIN_COOLDOWN_SECONDS, noImproveLimitSeconds);
    }

    private boolean isEnabled() {
        try {
            ConstraintSettingsService css = SpringContextHolder.getBean(ConstraintSettingsService.class);
            if (css != null) {
                return css.isSolverRuinRecreateEnabled();
            }
        } catch (Exception ignored) {
        }
        return true; // Enabled by default — critical for escaping stuck hard violations
    }

    private int getClusterSize() {
        int configured = 8; // default
        try {
            ConstraintSettingsService css = SpringContextHolder.getBean(ConstraintSettingsService.class);
            if (css != null) {
                configured = css.getSolverRuinRecreateClusterSize();
            }
        } catch (Exception ignored) {
        }
        // Scale cluster size for large datasets: allow up to 50 for datasets >600 lessons
        int maxSize = configured >= 8 ? 50 : 25;
        return Math.max(3, Math.min(maxSize, configured));
    }
}
