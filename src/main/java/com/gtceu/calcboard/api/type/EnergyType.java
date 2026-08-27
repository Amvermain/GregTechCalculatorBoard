package com.gtceu.calcboard.api.type;

/**
 * Energy and driving mechanics type for RecipeNodes (RFC-004).
 */
public enum EnergyType {
    /** GregTech electric machine (LV~MAX voltage tiers, 4x overclocking, EU/t) */
    ELECTRIC_EU("EU/t", 0xFF55FFFF, true, true),

    /** Thermal / Mekanism RF/FE electric machine or generator */
    ELECTRIC_FE("FE/t", 0xFFFF5555, false, false),

    /** Create kinetic machinery (Stress Units & RPM) */
    KINETIC_SU("SU", 0xFFFFAA00, false, false),

    /** Thermal Systeams Steam Boiler or self-combustion thermal generator (0 EU/t, Steam output) */
    HEAT_OR_SELF("Steam", 0xFF55FF88, false, false),

    /** Botania Mana machinery */
    MANA("Mana/t", 0xFF55FFFF, false, false),

    /** Passive / manual / unpowered recipe (Crafting table, stonecutter, campfire, stone barrel, etc.) */
    NONE("-", 0xFF888888, false, false);

    private final String unitLabel;
    private final int accentColor;
    private final boolean supportsGtVoltageTiers;
    private final boolean supportsGtOverclockMode;

    EnergyType(String unitLabel, int accentColor, boolean supportsGtVoltageTiers, boolean supportsGtOverclockMode) {
        this.unitLabel = unitLabel;
        this.accentColor = accentColor;
        this.supportsGtVoltageTiers = supportsGtVoltageTiers;
        this.supportsGtOverclockMode = supportsGtOverclockMode;
    }

    public String getUnitLabel() {
        return unitLabel;
    }

    public int getAccentColor() {
        return accentColor;
    }

    public boolean supportsGtVoltageTiers() {
        return supportsGtVoltageTiers;
    }

    public boolean supportsGtOverclockMode() {
        return supportsGtOverclockMode;
    }
}

