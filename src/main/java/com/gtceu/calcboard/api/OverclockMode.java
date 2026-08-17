package com.gtceu.calcboard.api;

public enum OverclockMode {
    STANDARD("Standard (4x EU / 2x Speed)", 4.0, 2.0),
    PERFECT("Perfect (4x EU / 4x Speed)", 4.0, 4.0),
    LOSSLESS("Lossless (2x EU / 2x Speed)", 2.0, 2.0);

    private final String displayName;
    private final double energyFactor;
    private final double speedFactor;

    OverclockMode(String displayName, double energyFactor, double speedFactor) {
        this.displayName = displayName;
        this.energyFactor = energyFactor;
        this.speedFactor = speedFactor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getEnergyFactor() {
        return energyFactor;
    }

    public double getSpeedFactor() {
        return speedFactor;
    }

    /**
     * Calculates resulting duration (ticks) and energy (EU/t) given base values and tier delta.
     */
    public OverclockResult calculate(double baseDurationTicks, double baseEUt, int tierDelta) {
        if (tierDelta <= 0) {
            return new OverclockResult(baseDurationTicks, baseEUt, 1.0, 0);
        }

        double currentDuration = baseDurationTicks;
        double currentEUt = baseEUt;
        int performedOcs = 0;

        for (int i = 0; i < tierDelta; i++) {
            currentEUt *= energyFactor;
            currentDuration /= speedFactor;
            performedOcs++;
        }

        // Calculate batches per tick if duration falls below 1 tick (sub-tick execution)
        double batchesPerTick = 1.0;
        double effectiveDurationTicks = currentDuration;
        if (currentDuration < 1.0 && currentDuration > 0) {
            batchesPerTick = 1.0 / currentDuration;
            effectiveDurationTicks = 1.0;
        }

        return new OverclockResult(effectiveDurationTicks, currentEUt, batchesPerTick, performedOcs);
    }

    public static record OverclockResult(
        double durationTicks,
        double eut,
        double batchesPerTick,
        int overclocks
    ) {
        public double getCyclesPerSecond() {
            if (durationTicks <= 0) return 20.0 * batchesPerTick;
            return (20.0 / durationTicks) * batchesPerTick;
        }
    }
}
