package com.gtceu.calcboard.api.type;

/**
 * Defines the external supply behavior of a Junction/Reroute node.
 */
public enum SupplyMode {
    NONE("gui.gtcalcboard.junction.supply_mode.none", false, false),
    INFINITE("gui.gtcalcboard.junction.supply_mode.infinite", true, false),
    FIXED_RATE("gui.gtcalcboard.junction.supply_mode.fixed_rate", true, false),
    VOID_SINK("gui.gtcalcboard.junction.supply_mode.void_sink", false, true);

    private final String translationKey;
    private final boolean isExternal;
    private final boolean isSink;

    SupplyMode(String translationKey, boolean isExternal) {
        this(translationKey, isExternal, false);
    }

    SupplyMode(String translationKey, boolean isExternal, boolean isSink) {
        this.translationKey = translationKey;
        this.isExternal = isExternal;
        this.isSink = isSink;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public boolean isExternal() {
        return isExternal;
    }

    public boolean isSink() {
        return isSink;
    }
}
