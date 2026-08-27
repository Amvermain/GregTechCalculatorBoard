package com.gtceu.calcboard.compat.gtceu.model;

import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Model variants for GTCEu Modern (LPT) and GTCEuM Star Technology Fork (SPT, NPT) Plasma Turbines.
 * Evaluated deterministically using exact ResourceLocation sets without heuristic string deductions.
 */
public enum GTPlasmaTurbineModel {
    /**
     * Standard GTCEu Modern Large Plasma Turbine (1x parallel capacity, 16,384 EU/t base).
     */
    LPT("LPT", "§d", 1, 16384.0, 0xFFE080FF,
            ResourceLocation.tryParse("gtceu:large_plasma_turbine"),
            Set.of(
                    ResourceLocation.tryParse("gtceu:large_plasma_turbine"),
                    ResourceLocation.tryParse("gtceu:plasma_large_turbine")
            )),

    /**
     * Star Technology Fork Supreme Plasma Turbine (6x parallel capacity, 98,304 EU/t base).
     */
    SPT("SPT", "§b", 6, 98304.0, 0xFF55FFFF,
            ResourceLocation.tryParse("gtceu:supreme_plasma_turbine"),
            Set.of(
                    ResourceLocation.tryParse("gtceu:supreme_plasma_turbine"),
                    ResourceLocation.tryParse("start_core:supreme_plasma_turbine"),
                    ResourceLocation.tryParse("gtceu_start:supreme_plasma_turbine")
            )),

    /**
     * Star Technology Fork Nyinsane Plasma Turbine (12x parallel capacity, 196,608 EU/t base).
     */
    NPT("NPT", "§5", 12, 196608.0, 0xFFAA00FF,
            ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine"),
            Set.of(
                    ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine"),
                    ResourceLocation.tryParse("start_core:nyinsane_plasma_turbine"),
                    ResourceLocation.tryParse("gtceu_start:nyinsane_plasma_turbine")
            ));

    private final String shortName;
    private final String formatCode;
    private final int parallelMultiplier;
    private final double baseProductionEUt;
    private final int color;
    private final ResourceLocation defaultIcon;
    private final Set<ResourceLocation> matchingIcons;

    GTPlasmaTurbineModel(String shortName, String formatCode, int parallelMultiplier, double baseProductionEUt, int color, ResourceLocation defaultIcon, Set<ResourceLocation> matchingIcons) {
        this.shortName = shortName;
        this.formatCode = formatCode;
        this.parallelMultiplier = parallelMultiplier;
        this.baseProductionEUt = baseProductionEUt;
        this.color = color;
        this.defaultIcon = defaultIcon;
        this.matchingIcons = matchingIcons;
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
        return defaultIcon;
    }

    public ResourceLocation getMachineIcon(RecipeNode node) {
        if (node != null) {
            for (ResourceLocation ws : node.getAvailableWorkstations()) {
                if (ws != null && matchingIcons.contains(ws)) {
                    return ws;
                }
            }
        }
        return defaultIcon;
    }

    public static GTPlasmaTurbineModel getModel(RecipeNode node) {
        if (node == null) return LPT;
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            for (GTPlasmaTurbineModel m : values()) {
                if (m.matchingIcons.contains(icon)) {
                    return m;
                }
            }
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                for (GTPlasmaTurbineModel m : values()) {
                    if (m.matchingIcons.contains(ws)) {
                        return m;
                    }
                }
            }
        }
        return LPT;
    }

    private static final Set<ResourceLocation> PLASMA_CATEGORIES = Set.of(
            ResourceLocation.tryParse("gtceu:plasma_generator"),
            ResourceLocation.tryParse("gtceu:plasma_turbine"),
            ResourceLocation.tryParse("start_core:plasma_generator"),
            ResourceLocation.tryParse("start_core:plasma_turbine")
    );

    public static boolean isPlasmaTurbine(RecipeNode node) {
        if (node == null) return false;
        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null && PLASMA_CATEGORIES.contains(catId)) {
            return true;
        }
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            for (GTPlasmaTurbineModel m : values()) {
                if (m.matchingIcons.contains(icon)) return true;
            }
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                for (GTPlasmaTurbineModel m : values()) {
                    if (m.matchingIcons.contains(ws)) return true;
                }
            }
        }
        return false;
    }
}

