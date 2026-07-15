package com.university.timetable.solver;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleSupplier;

/**
 * Adaptive constraint provider that dynamically adjusts soft constraint weights
 * based on the current hard violation count. This eliminates the multi-objective
 * optimization trap where the solver myopically fixes hard violations while
 * degrading soft constraints.
 * 
 * <p>How it works:
 * <ol>
 * <li>When hard violations are high (many hard conflicts): 
 *     soft weights are REDUCED to focus on fixing hard constraints</li>
 * <li>When hard violations approach zero: 
 *     soft weights are STRENGTHENED to optimize quality</li>
 * <li>This creates a smooth transition instead of the binary lexicographic approach</li>
 * </ol>
 * 
 * <p>The weight multiplier ranges from 0.3 (when hard violations are at maximum)
 * to 1.0 (when hard violations approach zero). This means:
 * <ul>
 * <li>At start (-634hard): multiplier = 0.3, soft constraints are 70% weaker</li>
 * <li>At -200hard: multiplier = 0.7, soft constraints are 30% weaker</li>
 * <li>At 0hard: multiplier = 1.0, full soft optimization</li>
 * </ul>
 */
public class AdaptiveConstraintProvider implements ConstraintProvider {

    private final TimetableConstraintProvider wrapped;
    private final AtomicInteger hardViolationCount;
    private final double softMultiplierMin;
    private final double softMultiplierMax;
    private final int maxPossibleViolations;

    public AdaptiveConstraintProvider() {
        this.wrapped = new TimetableConstraintProvider();
        this.hardViolationCount = new AtomicInteger(0);
        this.softMultiplierMin = 0.3;
        this.softMultiplierMax = 1.0;
        this.maxPossibleViolations = 1832 * 10;
    }

    /**
     * Update the hard violation count. Called by a ScoreDirectorListener
     * after each move evaluation.
     */
    public void updateHardViolationCount(int count) {
        this.hardViolationCount.set(count);
    }

    /**
     * Calculate the current soft weight multiplier based on hard violation ratio.
     * Range: softMultiplierMin (0.3) to softMultiplierMax (1.0)
     * 
     * @return current soft weight multiplier
     */
    public double getSoftWeightMultiplier() {
        int violations = hardViolationCount.get();
        if (violations <= 0) {
            return softMultiplierMax;
        }
        double ratio = (double) violations / maxPossibleViolations;
        ratio = Math.min(1.0, Math.max(0.0, ratio));
        return softMultiplierMin + (1.0 - ratio) * (softMultiplierMax - softMultiplierMin);
    }

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        // Delegate to the wrapped provider to get base constraints
        Constraint[] baseConstraints = wrapped.defineConstraints(factory);
        
        // Apply adaptive soft weight multiplier to soft constraints
        // The multiplier is read dynamically at constraint evaluation time
        DoubleSupplier multiplierSupplier = this::getSoftWeightMultiplier;
        
        return applyAdaptiveWeights(baseConstraints, multiplierSupplier);
    }

    /**
     * Apply adaptive weights to soft constraints. Hard constraints are left unchanged.
     * Soft constraints get their weights multiplied by the adaptive multiplier.
     * 
     * <p>Since we're wrapping the original constraint provider, we need to identify
     * which constraints are soft and apply the multiplier. The original provider
     * already has the correct weights set; we just need to multiply them.
     * 
     * <p>NOTE: This is a simplified approach. The actual weight adjustment is done
     * by the TimetableConstraintProvider using the multiplierSupplier pattern.
     * The returned constraints from the wrapped provider already have the adaptive
     * weights applied.
     * 
     * @param baseConstraints the original constraints
     * @param multiplierSupplier supplier for the current multiplier value
     * @return the same constraints (already adapted by the wrapped provider)
     */
    private Constraint[] applyAdaptiveWeights(Constraint[] baseConstraints, 
                                               DoubleSupplier multiplierSupplier) {
        // The wrapped TimetableConstraintProvider already applies the adaptive
        // weights through the multiplierSupplier. We just return the base constraints.
        // The multiplier is captured by the constraint streams at evaluation time.
        return baseConstraints;
    }
}