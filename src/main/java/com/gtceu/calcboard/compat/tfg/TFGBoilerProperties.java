package com.gtceu.calcboard.compat.tfg;

import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyKey;

/**
 * TFG Large Boiler specific property keys registered into the domain {@link NodeProperties} registry.
 */
public final class TFGBoilerProperties {

    /** Active booster fluid index (0: None, 1..9: TFGBoilerPhysics booster list index). */
    public static final NodePropertyKey<Integer> BOOSTER_INDEX = NodeProperties.register(
            NodePropertyKey.ofInt("tfg_booster_index", 0)
    );

    /** Active water quality tier (0: Standard Water 1.0x, 1: Distilled Water 1.5x boost). */
    public static final NodePropertyKey<Integer> WATER_TIER = NodeProperties.register(
            NodePropertyKey.ofInt("tfg_water_tier", 0)
    );

    /** Boiler operation mode (0: Standard Combustion, 1: Super Boiler Dual Fuel). */
    public static final NodePropertyKey<Integer> BOILER_MODE = NodeProperties.register(
            NodePropertyKey.ofInt("tfg_boiler_mode", 0)
    );

    private TFGBoilerProperties() {}

    /**
     * Ensures class loading and static property registration.
     */
    public static void init() {
        // Trigger classloader
    }
}
