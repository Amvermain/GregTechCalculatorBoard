package com.gtceu.calcboard.api;

import net.minecraft.resources.ResourceLocation;

/**
 * Model variants for GTCEu Modern Plasma Turbines (Large, Supreme, Nyinsane).
 */
public enum GTPlasmaTurbineModel {
    LPT("LPT", "§d", 1, 16384.0, 0xFFE080FF, ResourceLocation.tryParse("gtceu:large_plasma_turbine")),
    SPT("SPT", "§b", 6, 98304.0, 0xFF55FFFF, ResourceLocation.tryParse("gtceu:supreme_plasma_turbine")),
    NPT("NPT", "§5", 12, 196608.0, 0xFFAA00FF, ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine"));

    private final String shortName;
    private final String formatCode;
    private final int parallelMultiplier;
    private final double baseProductionEUt;
    private final int color;
    private final ResourceLocation machineIcon;

    GTPlasmaTurbineModel(String shortName, String formatCode, int parallelMultiplier, double baseProductionEUt, int color, ResourceLocation machineIcon) {
        this.shortName = shortName;
        this.formatCode = formatCode;
        this.parallelMultiplier = parallelMultiplier;
        this.baseProductionEUt = baseProductionEUt;
        this.color = color;
        this.machineIcon = machineIcon;
    }

    public String getShortName() {
        return shortName;
    }

    public String getFormatCode() {
        return formatCode;
    }

    public int getParallelMultiplier() {
        return parallelMultiplier;
    }

    public double getBaseProductionEUt() {
        return baseProductionEUt;
    }

    public int getColor() {
        return color;
    }

    public ResourceLocation getMachineIcon() {
        return machineIcon;
    }

    public static GTPlasmaTurbineModel getModel(RecipeNode node) {
        if (node == null) return LPT;
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            String path = icon.getPath().toLowerCase(java.util.Locale.ROOT);
            if (path.contains("nyinsane")) return NPT;
            if (path.contains("supreme") || path.contains("boosted")) return SPT;
            if (path.contains("large_plasma")) return LPT;
        }
        if (node.getName() != null) {
            String name = node.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("nyinsane") || name.contains("npt")) return NPT;
            if (name.contains("supreme") || name.contains("spt")) return SPT;
        }
        return LPT;
    }

    public static boolean isPlasmaTurbine(RecipeNode node) {
        if (node == null || !node.isTurbine()) return false;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("plasma_turbine")) {
            return true;
        }
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null && icon.getPath().contains("plasma_turbine")) {
            return true;
        }
        if (node.getName() != null && node.getName().toLowerCase(java.util.Locale.ROOT).contains("plasma turbine")) {
            return true;
        }
        return false;
    }
}
