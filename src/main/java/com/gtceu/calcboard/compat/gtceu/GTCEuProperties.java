package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyKey;
import com.gtceu.calcboard.api.type.GTVoltageTier;

/**
 * GTCEu-specific {@link NodePropertyKey} definitions registered to the core {@link NodeProperties} registry.
 */
public final class GTCEuProperties {

    // GTCEu & Fusion Properties
    public static final NodePropertyKey<Long> FUSION_START_EU = NodeProperties.register(
            NodePropertyKey.ofLong("fusion_start_eu", 0L)
    );
    public static final NodePropertyKey<Integer> REQUIRED_REFLECTOR_TIER = NodeProperties.register(
            NodePropertyKey.ofInt("required_reflector_tier", 0)
    );
    public static final NodePropertyKey<Integer> EBF_TEMPERATURE = NodeProperties.register(
            NodePropertyKey.ofInt("ebf_temperature", 0)
    );
    public static final NodePropertyKey<String> CLEANROOM_TYPE = NodeProperties.register(
            NodePropertyKey.ofString("cleanroom_type", "")
    );
    public static final NodePropertyKey<Integer> BOILER_THROTTLE = NodeProperties.register(
            NodePropertyKey.ofInt("boiler_throttle", 100)
    );

    // Large Turbine & Rotor Properties
    public static final NodePropertyKey<Integer> TURBINE_ROTOR_EFFICIENCY = NodeProperties.register(
            NodePropertyKey.ofInt("rotor_efficiency", 100)
    );
    public static final NodePropertyKey<Integer> TURBINE_ROTOR_POWER = NodeProperties.register(
            NodePropertyKey.ofInt("rotor_power", 100)
    );
    public static final NodePropertyKey<String> TURBINE_ROTOR_NAME = NodeProperties.register(
            NodePropertyKey.ofString("rotor_name", "Standard (100%)")
    );

    // RFC-006: Decoupled Hatch Tiers & Turbine Enhancements
    public static final NodePropertyKey<GTVoltageTier> ROTOR_HOLDER_TIER = NodeProperties.register(
            NodePropertyKey.ofEnum("rotor_holder_tier", GTVoltageTier.class, null)
    );
    public static final NodePropertyKey<GTVoltageTier> DYNAMO_HATCH_TIER = NodeProperties.register(
            NodePropertyKey.ofEnum("dynamo_hatch_tier", GTVoltageTier.class, null)
    );
    public static final NodePropertyKey<Integer> DYNAMO_AMPERAGE = NodeProperties.register(
            NodePropertyKey.ofInt("dynamo_amperage", 16)
    );
    public static final NodePropertyKey<Boolean> LUBRICANT_BOOST = NodeProperties.register(
            NodePropertyKey.ofBoolean("lubricant_boost", false)
    );
    public static final NodePropertyKey<Boolean> COOLANT_BOOST = NodeProperties.register(
            NodePropertyKey.ofBoolean("coolant_boost", false)
    );
    public static final NodePropertyKey<Double> ROTOR_WEAR_PER_SEC = NodeProperties.register(
            NodePropertyKey.ofDouble("rotor_wear_per_sec", 0.0)
    );

    private GTCEuProperties() {}

    /**
     * Ensures class loading and property key registration.
     */
    public static void init() {
        // Classloading triggers static initializer registration
    }
}
