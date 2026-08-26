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

    public ResourceLocation getMachineIcon(RecipeNode node) {
        if (node != null) {
            for (ResourceLocation ws : node.getAvailableWorkstations()) {
                if (ws != null) {
                    String path = ws.getPath().toLowerCase(java.util.Locale.ROOT);
                    if (this == NPT && (path.contains("nyinsane") || path.contains("npt"))) return ws;
                    if (this == SPT && (path.contains("supreme") || path.contains("spt") || path.contains("boosted"))) return ws;
                    if (this == LPT && (path.contains("large") || path.contains("lpt") || path.contains("plasma_large"))) return ws;
                }
            }
        }
        if (this == LPT) {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(this.machineIcon);
            if (item == null || item == net.minecraft.world.item.Items.AIR) {
                var alt = ResourceLocation.tryParse("gtceu:plasma_large_turbine");
                if (net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(alt)) {
                    return alt;
                }
            }
        }
        return machineIcon;
    }

    public static GTPlasmaTurbineModel getModel(RecipeNode node) {
        if (node == null) return LPT;
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            String path = icon.getPath().toLowerCase(java.util.Locale.ROOT);
            if (icon.equals(NPT.machineIcon) || path.contains("nyinsane") || path.contains("npt")) return NPT;
            if (icon.equals(SPT.machineIcon) || path.contains("supreme") || path.contains("spt") || path.contains("boosted")) return SPT;
            if (icon.equals(LPT.machineIcon) || path.contains("large") || path.contains("plasma_generator")) return LPT;
        }
        return LPT;
    }

    public static boolean isPlasmaTurbine(RecipeNode node) {
        if (node == null) return false;
        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null) {
            if (catId.equals(ResourceLocation.tryParse("gtceu:plasma_generator"))
                    || catId.equals(ResourceLocation.tryParse("gtceu:plasma_turbine"))
                    || catId.getPath().contains("plasma")) {
                return true;
            }
        }
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            if (icon.equals(LPT.machineIcon) || icon.equals(SPT.machineIcon) || icon.equals(NPT.machineIcon)
                    || icon.getPath().contains("plasma")) {
                return true;
            }
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && (ws.equals(LPT.machineIcon) || ws.equals(SPT.machineIcon) || ws.equals(NPT.machineIcon) || ws.getPath().contains("plasma"))) {
                return true;
            }
        }
        return false;
    }
}
