package com.gtceu.calcboard.api.type;

/**
 * Display mode for fluid rates and amounts.
 * Supports AUTO (adaptive mB/B scaling), ALWAYS_MB (forced mB), and ALWAYS_B (forced Buckets).
 */
public enum FluidUnitMode {
    AUTO("Auto", "gui.gtcalcboard.config.fluid_unit.auto"),
    ALWAYS_MB("mB", "gui.gtcalcboard.config.fluid_unit.always_mb"),
    ALWAYS_B("B", "gui.gtcalcboard.config.fluid_unit.always_b");

    private final String label;
    private final String translationKey;

    FluidUnitMode(String label, String translationKey) {
        this.label = label;
        this.translationKey = translationKey;
    }

    public String getLabel() {
        return label;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public String getLangKey() {
        return translationKey;
    }

    public FluidUnitMode next() {
        FluidUnitMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }
}
