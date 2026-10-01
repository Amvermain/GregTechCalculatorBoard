package com.gtceu.calcboard.api.bom;

/**
 * Strategy mode for selecting Input/Output bus and hatch tiers in Multiblock Bill of Materials (BOM).
 */
public enum BOMHatchTierMode {
    /**
     * Matches the machine's operating voltage tier (default behavior).
     */
    MATCH_MACHINE,

    /**
     * Automatically scales down buses and hatches to the minimum tier required by recipe item and fluid counts.
     */
    AUTO_MINIMUM,

    /**
     * Forces all Input/Output buses and hatches to LV tier.
     */
    FORCE_LV,

    /**
     * Forces all Input/Output buses and hatches to MV tier.
     */
    FORCE_MV,

    /**
     * Forces all Input/Output buses and hatches to HV tier.
     */
    FORCE_HV;

    public BOMHatchTierMode next() {
        BOMHatchTierMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public BOMHatchTierMode previous() {
        BOMHatchTierMode[] values = values();
        return values[(ordinal() - 1 + values.length) % values.length];
    }
}
