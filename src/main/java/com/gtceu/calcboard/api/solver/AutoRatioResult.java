package com.gtceu.calcboard.api.solver;

import java.util.Collections;
import java.util.Set;

/**
 * Immutable execution result summary of an Auto-Ratio solver pass.
 *
 * @param totalNodesUpdated Number of machine counts mutated during the pass.
 * @param divergentNodeIds Node IDs where runaway scaling was suppressed.
 * @param clampedBySafetyLimit True if any machine hit the hard safety ceiling.
 */
public record AutoRatioResult(
        int totalNodesUpdated,
        Set<String> divergentNodeIds,
        boolean clampedBySafetyLimit
) {
    public static final AutoRatioResult EMPTY = new AutoRatioResult(0, Collections.emptySet(), false);

    public boolean hasDivergence() {
        return (divergentNodeIds != null && !divergentNodeIds.isEmpty()) || clampedBySafetyLimit;
    }
}
