package com.gtceu.calcboard.api.property;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Standard registry and constant definitions for pure domain {@link NodePropertyKey}.
 * Mod-specific property keys are declared in their respective compat packages
 * and registered dynamically via {@link #register(NodePropertyKey)}.
 */
public final class NodeProperties {

    private static final Map<String, NodePropertyKey<?>> REGISTRY = new HashMap<>();

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
