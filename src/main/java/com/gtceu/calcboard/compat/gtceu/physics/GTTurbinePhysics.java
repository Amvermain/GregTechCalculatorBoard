package com.gtceu.calcboard.compat.gtceu.physics;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;

/**
 * Encapsulates physics, efficiency calculations, and parallel flow scaling for GTCEu turbines.
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
