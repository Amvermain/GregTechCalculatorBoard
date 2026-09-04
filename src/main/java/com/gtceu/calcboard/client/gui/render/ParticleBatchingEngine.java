package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Calculates deterministic animation batching multipliers, visual cycle durations,
 * incoming deficit warning pulse frequencies, and outgoing derated flow speeds (RFC-020).
 */
public final class ParticleBatchingEngine {

    public static final double ANIMATION_THRESHOLD_SEC = 1.0;
    public static final double BASE_TRAVEL_PERIOD_MS = 1600.0;

    private static final Map<String, Integer> BATCH_MULTIPLIER_CACHE = new ConcurrentHashMap<>();

    private ParticleBatchingEngine() {}

    public static void clearCache() {
        BATCH_MULTIPLIER_CACHE.clear();
    }

    public static int computeBatchMultiplier(double cycleDurationSec) {
        if (cycleDurationSec <= 0.0001 || cycleDurationSec >= ANIMATION_THRESHOLD_SEC) {
            return 1;
        }
        return (int) Math.ceil(ANIMATION_THRESHOLD_SEC / cycleDurationSec);
    }

    public static int getBatchMultiplier(RecipeNode node) {
        if (node == null || node.isReroute()) return 1;
        return BATCH_MULTIPLIER_CACHE.computeIfAbsent(node.getId(), id -> {
            double cycleSec = node.getEffectiveDurationSeconds();
            return computeBatchMultiplier(cycleSec);
        });
    }

    public static void invalidateNode(String nodeId) {
        if (nodeId != null) {
            BATCH_MULTIPLIER_CACHE.remove(nodeId);
        }
    }

    public static double computeBatchAnimationDuration(double cycleDurationSec) {
        int m = computeBatchMultiplier(cycleDurationSec);
        return m * Math.max(0.0001, cycleDurationSec);
    }

    public static double computeBufferChargeDuration(double bufferSize, double inflowRate) {
        if (bufferSize <= 0.0001 || inflowRate <= 0.0001) return 0.0;
        return bufferSize / inflowRate;
    }

    public static float computeBufferAnimationPeriodMs(double chargeDurationSec) {
        if (chargeDurationSec <= 0.0001) return (float) BASE_TRAVEL_PERIOD_MS;
        return (float) Math.max(1000.0, chargeDurationSec * 1000.0);
    }

    public static float computePulseFrequency(float saturationRatio) {
        float phi = Math.max(0.0f, Math.min(1.0f, saturationRatio));
        return 1.0f + 3.0f * (1.0f - phi);
    }

    public static float computePulseAlpha(float saturationRatio, float timeSec) {
        if (saturationRatio >= 0.9999f) return 1.0f;
        float freq = computePulseFrequency(saturationRatio);
        return 0.4f + 0.6f * (float) Math.abs(Math.sin(2.0 * Math.PI * freq * timeSec));
    }

    public static float computeDeratedSpeed(float baseSpeed, float machineEfficiency) {
        float eff = Math.max(0.0f, Math.min(1.0f, machineEfficiency));
        return baseSpeed * eff;
    }

    public static float computeDeratedTravelPeriodMs(float machineEfficiency) {
        float eff = Math.max(0.05f, Math.min(1.0f, machineEfficiency));
        return (float) (BASE_TRAVEL_PERIOD_MS / eff);
    }

    public static String getBadgeLabel(int multiplier, boolean isBuffer) {
        if (isBuffer) return "Bx";
        return multiplier > 1 ? multiplier + "x" : null;
    }
}
