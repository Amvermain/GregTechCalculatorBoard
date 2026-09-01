package com.gtceu.calcboard.compat.gtceu.physics;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;

/**
 * Encapsulates physics, efficiency calculations, and parallel flow scaling for GTCEu turbines (RFC-006).
 */
public final class GTTurbinePhysics {

    private GTTurbinePhysics() {}

    public static boolean isTurbine(RecipeNode node) {
        return GTTurbineHelper.isTurbine(node);
    }

    public static boolean isLargeTurbine(RecipeNode node) {
        return GTTurbineHelper.isLargeTurbine(node);
    }

    public static double getGeneratorMaxEUt(RecipeNode node) {
        return GTTurbineHelper.getGeneratorMaxEUt(node);
    }

    public static int getEffectiveTurbineParallel(RecipeNode node) {
        return GTTurbineHelper.getEffectiveTurbineParallel(node);
    }

    public static void autoCalculateTurbineParallel(RecipeNode node) {
        GTTurbineHelper.autoCalculateTurbineParallel(node);
    }

    public static boolean hasTurbineFlowDeficit(RecipeNode node, FlowGraph graph) {
        return GTTurbineHelper.hasTurbineFlowDeficit(node, graph);
    }

    public static int getTurbineHolderEfficiencyBonus(RecipeNode node) {
        return GTTurbineHelper.getTurbineHolderEfficiencyBonus(node);
    }

    public static int getTotalTurbineEfficiency(RecipeNode node) {
        return GTTurbineHelper.getTotalTurbineEfficiency(node);
    }

    public static GTVoltageTier getRotorHolderTier(RecipeNode node) {
        return GTTurbineHelper.getRotorHolderTier(node);
    }

    public static void setRotorHolderTier(RecipeNode node, GTVoltageTier tier) {
        GTTurbineHelper.setRotorHolderTier(node, tier);
    }

    public static GTVoltageTier getDynamoTier(RecipeNode node) {
        return GTTurbineHelper.getDynamoTier(node);
    }

    public static void setDynamoTier(RecipeNode node, GTVoltageTier tier) {
        GTTurbineHelper.setDynamoTier(node, tier);
    }

    public static int getDynamoAmperage(RecipeNode node) {
        return GTTurbineHelper.getDynamoAmperage(node);
    }

    public static void setDynamoAmperage(RecipeNode node, int amperage) {
        GTTurbineHelper.setDynamoAmperage(node, amperage);
    }

    public static boolean isLubricantBoost(RecipeNode node) {
        return GTTurbineHelper.isLubricantBoost(node);
    }

    public static void setLubricantBoost(RecipeNode node, boolean boost) {
        GTTurbineHelper.setLubricantBoost(node, boost);
    }

    public static boolean isCoolantBoost(RecipeNode node) {
        return GTTurbineHelper.isCoolantBoost(node);
    }

    public static void setCoolantBoost(RecipeNode node, boolean boost) {
        GTTurbineHelper.setCoolantBoost(node, boost);
    }

    public static double getTurbineBoostMultiplier(RecipeNode node) {
        return GTTurbineHelper.getTurbineBoostMultiplier(node);
    }

    public static double calculateRotorWearPerSecond(RecipeNode node) {
        return GTTurbineHelper.calculateRotorWearPerSecond(node);
    }

    public static double calculateRotorLifespanHours(RecipeNode node) {
        return GTTurbineHelper.calculateRotorLifespanHours(node);
    }

    public static double calculateRotorReplacementRatePerHour(RecipeNode node) {
        return GTTurbineHelper.calculateRotorReplacementRatePerHour(node);
    }

    public static void syncTurbineMachineIcon(RecipeNode node) {
        if (node == null || !node.isTurbine()) return;
        if (node.isMultiblock()) {
            net.minecraft.resources.ResourceLocation mbWs = node.getMultiblockWorkstation();
            if (mbWs != null) {
                node.setMachineIcon(mbWs);
            } else if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("steam_turbine")) {
                node.setMachineIcon(net.minecraft.resources.ResourceLocation.tryParse("gtceu:large_steam_turbine"));
            } else if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("gas_turbine")) {
                node.setMachineIcon(net.minecraft.resources.ResourceLocation.tryParse("gtceu:large_gas_turbine"));
            } else if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("plasma_turbine")) {
                node.setMachineIcon(net.minecraft.resources.ResourceLocation.tryParse("gtceu:large_plasma_turbine"));
            }
        } else {
            com.gtceu.calcboard.api.type.GTVoltageTier tier = node.getTargetTier();
            if (tier == null) tier = com.gtceu.calcboard.api.type.GTVoltageTier.LV;
            String prefix = tier.name().toLowerCase(java.util.Locale.ROOT);
            if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("steam_turbine")) {
                node.setMachineIcon(net.minecraft.resources.ResourceLocation.tryParse("gtceu:" + prefix + "_steam_turbine"));
            } else if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("gas_turbine")) {
                node.setMachineIcon(net.minecraft.resources.ResourceLocation.tryParse("gtceu:" + prefix + "_gas_turbine"));
            }
        }
    }
}
