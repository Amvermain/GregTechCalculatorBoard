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

    private CreateProperties() {}

    public static void init() {
        // Classloading triggers static initializer registration
    }
}
