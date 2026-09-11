package com.gtceu.calcboard.api.type;

/**
 * Flexible rate time units for scaling and rendering ingredient flow rates.
 * Supports /t (tick), /s (second), /min (minute), /h (hour), /d (day), 1x (per craft).
 */
public enum RateTimeUnit {
    PER_TICK("/t", 0.05, "gui.gtcalcboard.unit.per_tick"),
    PER_SECOND("/s", 1.0, "gui.gtcalcboard.unit.per_second"),
    PER_MINUTE("/min", 60.0, "gui.gtcalcboard.unit.per_minute"),
    PER_HOUR("/h", 3600.0, "gui.gtcalcboard.unit.per_hour"),
    PER_DAY("/d", 86400.0, "gui.gtcalcboard.unit.per_day"),
    PER_RECIPE("1x", 1.0, "gui.gtcalcboard.unit.per_recipe");

    private final String suffix;
    private final double factor;
    private final String translationKey;

    RateTimeUnit(String suffix, double factor, String translationKey) {
        this.suffix = suffix;
        this.factor = factor;
        this.translationKey = translationKey;
    }

    public String getSuffix() {
        return suffix;
    }

    public double getFactor() {
        return factor;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public boolean isRecipeBatchMode() {
        return this == PER_RECIPE;
    }

    public RateTimeUnit next() {
        RateTimeUnit[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}

