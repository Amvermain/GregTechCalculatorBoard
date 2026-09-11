package com.gtceu.calcboard.compat.greate;

import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyKey;

/**
 * Greate Kinetic & Tiered {@link NodePropertyKey} definitions registered to the core {@link NodeProperties} registry.
 */
public final class GreateProperties {

    public static final NodePropertyKey<Boolean> IS_GREATE = NodeProperties.register(
            NodePropertyKey.ofBoolean("is_greate", false)
    );

    public static final NodePropertyKey<Integer> MACHINE_TIER = NodeProperties.register(
            NodePropertyKey.ofInt("greate_machine_tier", -1)
    );

    public static final NodePropertyKey<Integer> REQUIRED_RECIPE_TIER = NodeProperties.register(
            NodePropertyKey.ofInt("greate_required_tier", -1)
    );

    public static final NodePropertyKey<Double> SHAFT_CAPACITY = NodeProperties.register(
            NodePropertyKey.ofDouble("greate_shaft_capacity", 8.0)
    );

    public static final NodePropertyKey<Integer> CIRCUIT_NUMBER = NodeProperties.register(
            NodePropertyKey.ofInt("greate_circuit_number", -1)
    );

    public static final NodePropertyKey<String> HEAT_CONDITION = NodeProperties.register(
            NodePropertyKey.ofString("greate_heat_condition", "NONE")
    );

    private static final double[] TIER_CAPACITIES = {
            8.0,          // 0: ULS (Andesite Alloy)
            32.0,         // 1: LS (Steel)
            128.0,        // 2: MS (Aluminium)
            512.0,        // 3: HS (Stainless Steel)
            2048.0,       // 4: ES (Titanium)
            8192.0,       // 5: IS (Tungsten Steel)
            32768.0,      // 6: LuS (Rhodium-Plated Palladium)
            131072.0,     // 7: ZPMS (Naquadah Alloy)
            524288.0,     // 8: US (Darmstadtium)
            2097152.0     // 9: UHS (Neutronium)
    };

    private static final String[] TIER_NAMES = {
            "ULS", "LS", "MS", "HS", "ES", "IS", "LuS", "ZPMS", "US", "UHS"
    };

    private static final int[] TIER_COLORS = {
            0xFFAAAAAA, // 0: ULS (Andesite Alloy)
            0xFFC0C0C0, // 1: LS (Steel)
            0xFF55FFFF, // 2: MS (Aluminium)
            0xFFFFD700, // 3: HS (Stainless Steel)
            0xFF9932CC, // 4: ES (Titanium)
            0xFF4169E1, // 5: IS (Tungsten Steel)
            0xFFFF69B4, // 6: LuS (Rhodium-Plated Palladium)
            0xFFFF4500, // 7: ZPMS (Naquadah Alloy)
            0xFF00CED1, // 8: US (Darmstadtium)
            0xFF8B0000  // 9: UHS (Neutronium)
    };

    private GreateProperties() {}

    public static void init() {
    }

    public static double getShaftCapacityForTier(int tier) {
        if (tier < 0) return TIER_CAPACITIES[0];
        if (tier >= TIER_CAPACITIES.length) return TIER_CAPACITIES[TIER_CAPACITIES.length - 1];
        return TIER_CAPACITIES[tier];
    }

    public static String getTierName(int tier) {
        if (tier < 0 || tier >= TIER_NAMES.length) return "ULS";
        return TIER_NAMES[tier];
    }

    public static int getTierColor(int tier) {
        if (tier < 0 || tier >= TIER_COLORS.length) return 0xFFAAAAAA;
        return TIER_COLORS[tier];
    }

    public static void cycleTier(com.gtceu.calcboard.api.model.RecipeNode node, int direction) {
        if (node == null) return;
        int curTier = Math.max(0, node.getProperties().get(MACHINE_TIER));
        int nextTier = Math.max(0, Math.min(9, curTier + direction));
        node.getProperties().set(MACHINE_TIER, nextTier);
        node.getProperties().set(SHAFT_CAPACITY, getShaftCapacityForTier(nextTier));
        node.setTargetTier(com.gtceu.calcboard.api.type.GTVoltageTier.getByIndex(nextTier));
        GreateMachineHelper.syncMachineIconToTier(node, nextTier);
    }
}
