package com.university.timetable.solver.move;

import ai.timefold.solver.core.impl.heuristic.move.Move;
import ai.timefold.solver.core.impl.heuristic.selector.move.factory.MoveListFactory;
import com.university.timetable.domain.Lesson;
import com.university.timetable.domain.Room;
import com.university.timetable.domain.TimeTable;
import com.university.timetable.domain.Timeslot;
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
 * When triggered, uses {@link ConflictAnalyzer} to intelligently target
 * the most problematic lessons rather than random destruction.
 */
public class RuinAndRecreateMoveFactory implements MoveListFactory<TimeTable> {

    /** Minimum elapsed time before ruin can fire (seconds). */
    private static final long MIN_COOLDOWN_SECONDS = 300; // 5 minutes

    /** Maximum number of ruin moves to generate per step. */
    private static final int MAX_RUIN_MOVES_PER_STEP = 3;

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

    /** Minimum stagnation calls before ruin fires. */
    private static final int MIN_STAGNATION_CALLS = 500;

    @Override
    public List<? extends Move<TimeTable>> createMoveList(TimeTable solution) {
        // Gate 1: Check admin setting
        if (!isEnabled()) {
            return List.of();
        }

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

        // Gate 3: Find conflicting lessons — need MULTIPLE hard violations
        List<Lesson> conflictingLessons = ConflictAnalyzer.findConflictingLessons(solution);
        if (conflictingLessons.size() < 2) {
            // Fewer than 2 conflicting lessons: normal moves can handle it
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
        if (stagnationCounter < MIN_STAGNATION_CALLS) {
            return List.of();
        }

        // ALL gates passed — generate ruin-and-recreate moves
        int clusterSize = getClusterSize();
        List<Lesson> allLessons = solution.getLessons();
        List<Timeslot> allTimeslots = solution.getTimeslots();
        List<Room> allRooms = solution.getRooms();

        List<Move<TimeTable>> moves = new ArrayList<>();

        // Generate up to MAX_RUIN_MOVES_PER_STEP moves, each with a different seed
        int movesToGenerate = Math.min(MAX_RUIN_MOVES_PER_STEP, conflictingLessons.size());

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
            return css != null && css.isSolverRuinRecreateEnabled();
        } catch (Exception e) {
            return false; // Disabled by default if settings unavailable
        }
    }

    private int getClusterSize() {
        try {
            ConstraintSettingsService css = SpringContextHolder.getBean(ConstraintSettingsService.class);
            if (css != null) {
                return Math.max(3, Math.min(25, css.getSolverRuinRecreateClusterSize()));
            }
        } catch (Exception ignored) {
        }
        return 8; // default
    }
}
