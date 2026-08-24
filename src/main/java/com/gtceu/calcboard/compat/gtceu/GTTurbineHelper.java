package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.compat.gtceu.helper.TurbineRotorHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Helper utility encapsulating GregTech Modern turbine mechanics,
 * rotor holder throughput capacity, and flow deficit evaluations.
 */
public final class GTTurbineHelper {

    private GTTurbineHelper() {}

    /**
     * Checks whether the given node represents a GregTech Turbine (Singleblock or Multiblock).
     */
    public static boolean isTurbine(RecipeNode node) {
        if (node == null) return false;
        if (node.isCreateMachine() || node.getEnergyType() != EnergyType.ELECTRIC_EU) return false;
        if (node.hasRotorAddon()) return true;

        ResourceLocation recipeCategoryId = node.getRecipeCategoryId();
        if (recipeCategoryId != null && MultiblockDetector.isTurbineRecipeCategory(recipeCategoryId)) {
            return true;
        }

        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && MultiblockDetector.isTurbineMachine(ws)) {
                return true;
            }
        }

        ResourceLocation machineIcon = node.getMachineIcon();
        if (machineIcon != null && MultiblockDetector.isTurbineMachine(machineIcon)) {
            return true;
        }

        if (node.getName() != null) {
            String sanitized = node.getName().toLowerCase().trim().replace(" ", "_");
            if (sanitized.contains("turbine")) return true;
        }

        return false;
    }

    /**
     * Checks whether the given node represents a GTCEu Large Multiblock Turbine.
     */
    public static boolean isLargeTurbine(RecipeNode node) {
        if (node == null || !node.isGenerator() || node.isCreateMachine() || node.getEnergyType() != EnergyType.ELECTRIC_EU) {
            return false;
        }
        if (hasRotorAddon(node)) return true;
        if (node.isMultiblock()) return isTurbine(node);
        if (node.getName() != null) {
            String sanitized = node.getName().toLowerCase().trim().replace(" ", "_");
            if (sanitized.contains("large") && sanitized.contains("turbine")) return true;
            if (MultiblockDetector.isTurbineMachine(ResourceLocation.tryParse("gtceu:" + sanitized))) return true;
        }
        return false;
    }

    /**
     * Checks whether the node has a rotor addon equipped or non-standard rotor properties.
     */
    public static boolean hasRotorAddon(RecipeNode node) {
        if (node == null || node.isCreateMachine() || node.getEnergyType() != EnergyType.ELECTRIC_EU) return false;
        String rName = node.getRotorName();
        int rEff = node.getRotorEfficiency();
        int rPow = node.getRotorPower();
        return node.getAddons().stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)
                || (rName != null && !rName.isEmpty() && !rName.startsWith("Standard"))
                || rEff != 100
                || (rPow > 0 && rPow != 100);
    }

    /**
     * Base production for a turbine machine (in EU/t).
     */
    public static double getTurbineBaseProduction(RecipeNode node) {
        if (node == null) return 4096.0;
        ResourceLocation recipeCategoryId = node.getRecipeCategoryId();
        if (recipeCategoryId != null) {
            Double prod = MultiblockDetector.getTurbineBaseProduction(recipeCategoryId);
            if (prod != null) return prod;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                Double prod = MultiblockDetector.getTurbineBaseProduction(ws);
                if (prod != null) return prod;
            }
        }
        GTVoltageTier baseTier = node.getTurbineBaseTier();
        return baseTier != null ? (double) (baseTier.getVoltage() * 2L) : 4096.0;
    }

    /**
     * Calculates the rotor holder efficiency bonus for a large turbine.
     */
    public static int getTurbineHolderEfficiencyBonus(RecipeNode node) {
        if (node == null || !isLargeTurbine(node)) return 0;
        GTVoltageTier baseTier = node.getTurbineBaseTier();
        GTVoltageTier targetTier = node.getTargetTier();
        if (baseTier == null || targetTier == null) return 0;
        int delta = targetTier.ordinal() - baseTier.ordinal();
        return Math.max(0, delta * 10);
    }

    /**
     * Calculates base capacity of the rotor holder at the given voltage tier.
     */
    public static double getNodeRotorHolderBaseCapacity(RecipeNode node, GTVoltageTier tier) {
        if (tier == null) return getTurbineBaseProduction(node);
        int tierIndex = tier.ordinal();
        GTVoltageTier baseTier = node.getTurbineBaseTier();
        int delta = tierIndex - (baseTier != null ? baseTier.ordinal() : GTVoltageTier.EV.ordinal());
        double baseProd = getTurbineBaseProduction(node);
        if (delta >= 0) {
            return baseProd * (1L << delta);
        } else {
            return Math.max(baseProd / 8.0, baseProd / (1L << (-delta)));
        }
    }

    /**
     * Calculates maximum throughput EU/t of the rotor holder.
     */
    public static double getNodeRotorHolderMaxEUt(RecipeNode node, GTVoltageTier tier, int rotorPower) {
        double baseCap = getNodeRotorHolderBaseCapacity(node, tier);
        double powerMultiplier = (rotorPower > 0 ? rotorPower : 100) / 100.0;
        return Math.floor(baseCap * powerMultiplier);
    }

    /**
     * Fallback static rotor holder base capacity (EV = 4,096 EU/t base).
     */
    public static double getRotorHolderBaseCapacity(GTVoltageTier tier) {
        if (tier == null) return 4096.0;
        int tierIndex = tier.ordinal();
        int delta = tierIndex - GTVoltageTier.EV.ordinal();
        if (delta >= 0) {
            return 4096.0 * (1L << delta);
        } else {
            return Math.max(512.0, 4096.0 / (1L << (-delta)));
        }
    }

    public static double getRotorHolderMaxEUt(GTVoltageTier tier, int rotorPower) {
        double baseCap = getRotorHolderBaseCapacity(tier);
        double powerMultiplier = (rotorPower > 0 ? rotorPower : 100) / 100.0;
        return Math.floor(baseCap * powerMultiplier);
    }

    public static double getRotorHolderMaxEUt(GTVoltageTier tier) {
        return getRotorHolderMaxEUt(tier, 130);
    }

    public static double getRotorMaterialMaxEUt(String rotorName) {
        return TurbineRotorHelper.getRotorStats(rotorName).durability();
    }

    /**
     * Evaluates whether a turbine node has any connected input with insufficient flow (< 100%).
     */
    public static boolean hasTurbineFlowDeficit(RecipeNode node, FlowGraph graph) {
        if (node == null || graph == null || !isTurbine(node)) return false;
        for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
            FlowGraphSolver.PortFlowStats stats = graph.getInputPortStats(node, inIdx);
            if (stats != null && stats.isConnected() && stats.isInputDeficit()) {
                return true;
            }
        }
        return false;
    }
}
