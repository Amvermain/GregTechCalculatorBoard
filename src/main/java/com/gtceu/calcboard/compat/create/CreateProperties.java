package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyKey;

/**
 * Create Kinetic {@link NodePropertyKey} definitions registered to the core {@link NodeProperties} registry.
 */
public final class CreateProperties {

    public static final NodePropertyKey<Integer> KINETIC_RPM = NodeProperties.register(
            NodePropertyKey.ofInt("kinetic_rpm", 32)
    );

    public static final NodePropertyKey<Integer> BASE_GENERATOR_RPM = NodeProperties.register(
            NodePropertyKey.ofInt("base_generator_rpm", 0)
    );

    public static final int[] STANDARD_RPMS = {4, 8, 16, 32, 64, 128, 256};

    private CreateProperties() {}

    public static void init() {
        // Classloading triggers static initializer registration
    }

    public static int getNextRpm(int currentRpm, int direction) {
        int closestIdx = 3;
        int minDiff = Integer.MAX_VALUE;
        for (int i = 0; i < STANDARD_RPMS.length; i++) {
            int diff = Math.abs(STANDARD_RPMS[i] - currentRpm);
            if (diff < minDiff) {
                minDiff = diff;
                closestIdx = i;
            }
        }
        int nextIdx = closestIdx + direction;
        if (nextIdx < 0) nextIdx = STANDARD_RPMS.length - 1;
        else if (nextIdx >= STANDARD_RPMS.length) nextIdx = 0;
        return STANDARD_RPMS[nextIdx];
    }

    public static void cycleRpm(com.gtceu.calcboard.api.model.RecipeNode node, int direction) {
        if (node == null) return;
        node.setRpm(getNextRpm(node.getRpm(), direction));
    }
}
