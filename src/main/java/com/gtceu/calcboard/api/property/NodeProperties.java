package com.gtceu.calcboard.api.property;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard registry and constant definitions for {@link NodePropertyKey}.
 */
public final class NodeProperties {

    private static final Map<String, NodePropertyKey<?>> REGISTRY = new HashMap<>();

    // GTCEu & Fusion Properties
    public static final NodePropertyKey<Long> FUSION_START_EU = register(
            NodePropertyKey.ofLong("fusion_start_eu", 0L)
    );
    public static final NodePropertyKey<Integer> REQUIRED_REFLECTOR_TIER = register(
            NodePropertyKey.ofInt("required_reflector_tier", 0)
    );
    public static final NodePropertyKey<Integer> EBF_TEMPERATURE = register(
            NodePropertyKey.ofInt("ebf_temperature", 0)
    );
    public static final NodePropertyKey<String> CLEANROOM_TYPE = register(
            NodePropertyKey.ofString("cleanroom_type", "")
    );
    public static final NodePropertyKey<Integer> BOILER_THROTTLE = register(
            NodePropertyKey.ofInt("boiler_throttle", 100)
    );

    // Large Turbine & Rotor Properties
    public static final NodePropertyKey<Integer> TURBINE_ROTOR_EFFICIENCY = register(
            NodePropertyKey.ofInt("rotor_efficiency", 100)
    );
    public static final NodePropertyKey<Integer> TURBINE_ROTOR_POWER = register(
            NodePropertyKey.ofInt("rotor_power", 100)
    );
    public static final NodePropertyKey<String> TURBINE_ROTOR_NAME = register(
            NodePropertyKey.ofString("rotor_name", "Standard (100%)")
    );

    // Create Kinetic Properties
    public static final NodePropertyKey<Integer> KINETIC_RPM = register(
            NodePropertyKey.ofInt("kinetic_rpm", 32)
    );

    // Thermal & Systeams Properties
    public static final NodePropertyKey<Double> THERMAL_BASE_ENERGY_RF = register(
            NodePropertyKey.ofDouble("thermal_base_energy_rf", 0.0)
    );

    // Goal & ETA Properties (RFC-005)
    public static final NodePropertyKey<Double> TARGET_BATCH_AMOUNT = register(
            NodePropertyKey.ofDouble("target_batch_amount", 0.0)
    );
    public static final NodePropertyKey<Double> TARGET_BATCH_TIME_SEC = register(
            NodePropertyKey.ofDouble("target_batch_time_sec", 0.0)
    );

    // Generic / Unsupported Recipe Marker (RFC-001)
    public static final NodePropertyKey<Boolean> IS_GENERIC_UNSUPPORTED = register(
            NodePropertyKey.ofBoolean("is_generic_unsupported", false)
    );

    // Compound / Layered Recipe Properties
    public static final NodePropertyKey<String> COMPOUND_GROUP_ID = register(
            NodePropertyKey.ofString("compound_group_id", "")
    );
    public static final NodePropertyKey<Integer> COMPOUND_LAYER_INDEX = register(
            NodePropertyKey.ofInt("compound_layer_index", 0)
    );
    public static final NodePropertyKey<Integer> COMPOUND_TOTAL_LAYERS = register(
            NodePropertyKey.ofInt("compound_total_layers", 1)
    );
    public static final NodePropertyKey<String> COMPOUND_MASTER_NODE_ID = register(
            NodePropertyKey.ofString("compound_master_node_id", "")
    );

    private NodeProperties() {}

    public static <T> NodePropertyKey<T> register(NodePropertyKey<T> key) {
        REGISTRY.put(key.getId(), key);
        return key;
    }

    public static NodePropertyKey<?> getById(String id) {
        return REGISTRY.get(id);
    }

    public static Map<String, NodePropertyKey<?>> getAll() {
        return Collections.unmodifiableMap(REGISTRY);
    }
}
