package com.gtceu.calcboard.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Represents the boiler type/tier for GregTech steam boilers.
 */
public enum GTBoilerTier {
    LP_BRONZE("LP Bronze", "§6", 1.0, 1.0, 120.0, 300.0, 0xFFD28C38, ResourceLocation.tryParse("gtceu:lp_steam_solid_boiler"), ResourceLocation.tryParse("gtceu:lp_steam_liquid_boiler"), false),
    HP_STEEL("HP Steel", "§7", 2.5, 2.0, 300.0, 600.0, 0xFFAAAAAA, ResourceLocation.tryParse("gtceu:hp_steam_solid_boiler"), ResourceLocation.tryParse("gtceu:hp_steam_liquid_boiler"), false),
    LARGE_BRONZE("L-Bronze", "§6", 800.0 / 6.0, 800.0 / 15.0, 16000.0, 16000.0, 0xFFD28C38, ResourceLocation.tryParse("gtceu:bronze_large_boiler"), ResourceLocation.tryParse("gtceu:bronze_large_boiler"), true),
    LARGE_STEEL("L-Steel", "§7", 1800.0 / 6.0, 1800.0 / 15.0, 36000.0, 36000.0, 0xFFAAAAAA, ResourceLocation.tryParse("gtceu:steel_large_boiler"), ResourceLocation.tryParse("gtceu:steel_large_boiler"), true),
    LARGE_TITANIUM("L-Titanium", "§b", 3200.0 / 6.0, 3200.0 / 15.0, 64000.0, 64000.0, 0xFF55FFFF, ResourceLocation.tryParse("gtceu:titanium_large_boiler"), ResourceLocation.tryParse("gtceu:titanium_large_boiler"), true),
    LARGE_TUNGSTENSTEEL("L-Tungsten", "§1", 6400.0 / 6.0, 6400.0 / 15.0, 128000.0, 128000.0, 0xFF5555FF, ResourceLocation.tryParse("gtceu:tungstensteel_large_boiler"), ResourceLocation.tryParse("gtceu:tungstensteel_large_boiler"), true);

    private final String displayName;
    private final String formatCode;
    private final double speedMultiplierSolid;
    private final double speedMultiplierLiquid;
    private final double steamRatePerSecSolid;
    private final double steamRatePerSecLiquid;
    private final int color;
    private final ResourceLocation defaultSolidIcon;
    private final ResourceLocation defaultLiquidIcon;
    private final boolean isMultiblock;

    GTBoilerTier(String displayName, String formatCode,
                 double speedMultiplierSolid, double speedMultiplierLiquid,
                 double steamRatePerSecSolid, double steamRatePerSecLiquid,
                 int color, ResourceLocation defaultSolidIcon, ResourceLocation defaultLiquidIcon, boolean isMultiblock) {
        this.displayName = displayName;
        this.formatCode = formatCode;
        this.speedMultiplierSolid = speedMultiplierSolid;
        this.speedMultiplierLiquid = speedMultiplierLiquid;
        this.steamRatePerSecSolid = steamRatePerSecSolid;
        this.steamRatePerSecLiquid = steamRatePerSecLiquid;
        this.color = color;
        this.defaultSolidIcon = defaultSolidIcon;
        this.defaultLiquidIcon = defaultLiquidIcon;
        this.isMultiblock = isMultiblock;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getFormatCode() {
        return formatCode;
    }

    public double getSpeedMultiplier(boolean isLiquid) {
        return isLiquid ? speedMultiplierLiquid : speedMultiplierSolid;
    }

    public double getLargeBoilerSpeedMultiplier(boolean isLiquid) {
        return switch (this) {
            case LARGE_BRONZE -> 1.0;
            case LARGE_STEEL -> 1800.0 / 800.0;
            case LARGE_TITANIUM -> 3200.0 / 800.0;
            case LARGE_TUNGSTENSTEEL -> 6400.0 / 800.0;
            case LP_BRONZE -> (isLiquid ? 15.0 : 6.0) / 800.0;
            case HP_STEEL -> (isLiquid ? 30.0 : 15.0) / 800.0;
        };
    }

    public double getSpeedMultiplier(boolean isLiquid, boolean isLargeBoiler) {
        return isLargeBoiler ? getLargeBoilerSpeedMultiplier(isLiquid) : getSpeedMultiplier(isLiquid);
    }

    public double getSpeedMultiplier() {
        return speedMultiplierSolid;
    }

    public double getSteamRatePerSec(boolean isLiquid) {
        return isLiquid ? steamRatePerSecLiquid : steamRatePerSecSolid;
    }

    public int getColor() {
        return color;
    }

    public ResourceLocation getDefaultIcon() {
        return defaultSolidIcon != null ? defaultSolidIcon : defaultLiquidIcon;
    }

    public ResourceLocation getDefaultIcon(boolean isLiquid) {
        return isLiquid ? defaultLiquidIcon : defaultSolidIcon;
    }

    public boolean isMultiblock() {
        return isMultiblock;
    }

    public static GTBoilerTier getBoilerTier(RecipeNode node) {
        if (node == null) return LP_BRONZE;
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            String p = icon.getPath().toLowerCase(java.util.Locale.ROOT);
            if (p.contains("tungstensteel")) return LARGE_TUNGSTENSTEEL;
            if (p.contains("titanium")) return LARGE_TITANIUM;
            if (p.contains("steel_large_boiler") || (p.contains("steel") && p.contains("large"))) return LARGE_STEEL;
            if (p.contains("bronze_large_boiler") || (p.contains("bronze") && p.contains("large")) || p.contains("large_boiler")) return LARGE_BRONZE;
            if (p.contains("steel_boiler") || p.contains("hp_") || p.contains("high_pressure") || p.contains("steel_liquid_boiler")) return HP_STEEL;
            if (p.contains("bronze_boiler") || p.contains("lp_") || p.contains("small_boiler") || p.contains("bronze_liquid_boiler")) return LP_BRONZE;
        }
        if (node.isMultiblock()) return LARGE_BRONZE;
        return LP_BRONZE;
    }
}
