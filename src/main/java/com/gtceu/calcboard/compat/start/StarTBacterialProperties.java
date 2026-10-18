package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyKey;

/**
 * Node property keys and helpers for Star Technology Bacterial Hydrocarbon Harvester colony stats.
 */
public final class StarTBacterialProperties {

    public static final int MIN_STAT = 1;
    public static final int MAX_STAT = 5;
    public static final int DEFAULT_STAT = 5;

    public static final NodePropertyKey<Integer> BACTERIA_PRODUCTION = NodeProperties.register(
            NodePropertyKey.ofInt("start_bacteria_production", DEFAULT_STAT)
    );

    public static final NodePropertyKey<Integer> BACTERIA_METABOLISM = NodeProperties.register(
            NodePropertyKey.ofInt("start_bacteria_metabolism", DEFAULT_STAT)
    );

    private StarTBacterialProperties() {}

    public static int clampStat(int value) {
        return Math.max(MIN_STAT, Math.min(MAX_STAT, value));
    }

    public static int getProduction(RecipeNode node) {
        if (node == null) return DEFAULT_STAT;
        Integer val = node.getProperties().get(BACTERIA_PRODUCTION);
        return val != null ? clampStat(val) : DEFAULT_STAT;
    }

    public static void setProduction(RecipeNode node, int production) {
        if (node == null) return;
        node.getProperties().set(BACTERIA_PRODUCTION, clampStat(production));
    }

    public static int getMetabolism(RecipeNode node) {
        if (node == null) return DEFAULT_STAT;
        Integer val = node.getProperties().get(BACTERIA_METABOLISM);
        return val != null ? clampStat(val) : DEFAULT_STAT;
    }

    public static void setMetabolism(RecipeNode node, int metabolism) {
        if (node == null) return;
        node.getProperties().set(BACTERIA_METABOLISM, clampStat(metabolism));
    }
}
