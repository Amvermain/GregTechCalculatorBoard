package com.gtceu.calcboard.api.type;

/**
 * Defines the external supply behavior of a Junction/Reroute node.
 */
public enum SupplyMode {
    NONE("gui.gtcalcboard.junction.supply_mode.none", false),
    INFINITE("gui.gtcalcboard.junction.supply_mode.infinite", true),
    FIXED_RATE("gui.gtcalcboard.junction.supply_mode.fixed_rate", true);

    private final String translationKey;
    private final boolean isExternal;

    SupplyMode(String translationKey, boolean isExternal) {
        this.translationKey = translationKey;
        this.isExternal = isExternal;
    }

    public String getTranslationKey() {
        return translationKey;
    }

    public boolean isExternal() {
        return isExternal;
    }
}
