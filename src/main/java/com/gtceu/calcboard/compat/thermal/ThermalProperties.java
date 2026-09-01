package com.gtceu.calcboard.compat.thermal;

import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyKey;

/**
 * Thermal & Systeams {@link NodePropertyKey} definitions registered to the core {@link NodeProperties} registry.
 */
public final class ThermalProperties {

    public static final NodePropertyKey<Double> THERMAL_BASE_ENERGY_RF = NodeProperties.register(
            NodePropertyKey.ofDouble("thermal_base_energy_rf", 0.0)
    );

    private ThermalProperties() {}

    public static void init() {
        // Classloading triggers static initializer registration
    }
}
