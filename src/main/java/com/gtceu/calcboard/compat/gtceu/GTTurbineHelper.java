package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;

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
        if (node == null || node.getEnergyType() == EnergyType.KINETIC_SU) return false;
        if (node.getEnergyTypeOverride() != null && node.getEnergyTypeOverride() != EnergyType.ELECTRIC_EU && node.getEnergyTypeOverride() != EnergyType.NONE) return false;
        if (hasRotorAddon(node)) return true;

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
            if (sanitized.contains("turbine") || sanitized.contains("plasma_generator") || sanitized.contains("plasma")) return true;
        }

        return false;
    }

    /**
     * Checks whether the given node represents a GTCEu Large Multiblock Turbine.
     */
    public static boolean isLargeTurbine(RecipeNode node) {
        if (node == null || node.getEnergyType() == EnergyType.KINETIC_SU) {
            return false;
        }
        if (node.getEnergyTypeOverride() != null && node.getEnergyTypeOverride() != EnergyType.ELECTRIC_EU && node.getEnergyTypeOverride() != EnergyType.NONE) {
            return false;
        }
        if (hasRotorAddon(node)) return true;
        if (node.isMultiblock()) return isTurbine(node);
        if (node.getName() != null) {
            String sanitized = node.getName().toLowerCase().trim().replace(" ", "_");
            if (sanitized.contains("large") && (sanitized.contains("turbine") || sanitized.contains("plasma"))) return true;
            if (sanitized.contains("supreme") || sanitized.contains("nyinsane")) return true;
            if (MultiblockDetector.isTurbineMachine(ResourceLocation.tryParse("gtceu:" + sanitized))) return true;
        }
        return false;
    }

    /**
     * Checks whether the node has a rotor addon equipped or non-standard rotor properties.
     */
    public static boolean hasRotorAddon(RecipeNode node) {
        if (node == null || node.getEnergyType() == EnergyType.KINETIC_SU || (node.getEnergyTypeOverride() != null && node.getEnergyTypeOverride() != EnergyType.ELECTRIC_EU)) return false;
        String rName = node.getRotorName();
        int rEff = node.getRotorEfficiency();
        int rPow = node.getRotorPower();
        return node.getAddons().stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)
                || (rName != null && !rName.isEmpty() && !rName.startsWith("Standard"))
                || rEff != 100
                || (rPow > 0 && rPow != 100);
    }

    /**
     * Deductively identifies the base voltage tier for this turbine type from GTCEu Machine Definitions or Recipe Category.
     */
    public static GTVoltageTier getTurbineBaseTier(RecipeNode node) {
        if (node == null) return GTVoltageTier.EV;
        ResourceLocation machineIcon = node.getMachineIcon();
        if (machineIcon != null) {
            GTVoltageTier tier = MultiblockDetector.getTurbineBaseTier(machineIcon);
            if (tier != null) return tier;
        }
        ResourceLocation recipeCategoryId = node.getRecipeCategoryId();
        if (recipeCategoryId != null) {
            GTVoltageTier tier = MultiblockDetector.getTurbineBaseTier(recipeCategoryId);
            if (tier != null) return tier;
        }
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null) {
                GTVoltageTier tier = MultiblockDetector.getTurbineBaseTier(ws);
                if (tier != null) return tier;
            }
        }
        return GTVoltageTier.EV;
    }

    /**
     * Base production for a turbine machine (in EU/t).
     */
    public static double getTurbineBaseProduction(RecipeNode node) {
        if (node == null) return 4096.0;
        ResourceLocation machineIcon = node.getMachineIcon();
        if (machineIcon != null) {
            Double prod = MultiblockDetector.getTurbineBaseProduction(machineIcon);
            if (prod != null) return prod;
        }
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
        GTVoltageTier baseTier = getTurbineBaseTier(node);
        return baseTier != null ? (double) (baseTier.getVoltage() * 2L) : 4096.0;
    }

    /**
     * Calculates the rotor holder efficiency bonus for a large turbine.
     */
    public static int getTurbineHolderEfficiencyBonus(RecipeNode node) {
        if (node == null || !isLargeTurbine(node)) return 0;
        GTVoltageTier baseTier = getTurbineBaseTier(node);
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
        GTVoltageTier baseTier = getTurbineBaseTier(node);
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
     * Calculates the maximum generator EU/t capacity based on the equipped rotor holder / material.
     */
    public static double getGeneratorMaxEUt(RecipeNode node) {
        if (node == null || !node.isGenerator()) return Double.MAX_VALUE;
        if (!isLargeTurbine(node)) return Double.MAX_VALUE;

        String rName = node.getRotorName();
        int rPower = node.getRotorPower();
        boolean hasRotor = node.getAddons().stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)
                || (rName != null && !rName.isEmpty() && !rName.startsWith("Standard"));
        if (!hasRotor) return Double.MAX_VALUE;

        int activePower = rPower > 0 && rPower != 100 ? rPower : TurbineRotorHelper.getRotorStats(rName).power();
        for (MachineAddon addon : node.getAddons()) {
            if (addon.getCategory() == MachineAddon.Category.ROTOR) {
                if (addon.getRotorPower() > 0) {
                    activePower = addon.getRotorPower();
                }
                break;
            }
        }
        if (activePower <= 0) activePower = 100;

        return getNodeRotorHolderMaxEUt(node, node.getTargetTier(), activePower);
    }

    /**
     * Calculates effective turbine parallel count based on rotor holder capacity cap.
     */
    public static int getEffectiveTurbineParallel(RecipeNode node) {
        if (node == null || !isLargeTurbine(node)) return Math.max(1, node != null ? node.getParallel() : 1);
        if (!hasRotorAddon(node)) return Math.max(1, node.getParallel());
        double cap = getGeneratorMaxEUt(node);
        double recipeEUt = Math.abs(node.getBaseEUt());
        if (recipeEUt <= 0 || cap >= Double.MAX_VALUE) return Math.max(1, node.getParallel());
        int calculated = (int) Math.max(1, Math.ceil(cap / recipeEUt));
        return Math.max(calculated, node.getParallel());
    }

    /**
     * Automatically calculates and tunes optimal turbine parallel count.
     */
    public static void autoCalculateTurbineParallel(RecipeNode node) {
        if (node == null || !node.isGenerator() || !isLargeTurbine(node)) {
            if (node != null) node.setParallel(1);
            return;
        }
        if (node.getBaseEUt() <= 0) return;

        String rName = node.getRotorName();
        int rEff = node.getRotorEfficiency();
        int rPow = node.getRotorPower();
        boolean hasRotor = node.getAddons().stream().anyMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR)
                || (rName != null && !rName.isEmpty() && !rName.startsWith("Standard"));
        if (!hasRotor && rEff <= 100 && (rPow <= 100 || rPow == 0)) {
            return;
        }

        int activePower = rPow > 0 && rPow != 100 ? rPow : TurbineRotorHelper.getRotorStats(rName).power();
        for (MachineAddon addon : node.getAddons()) {
            if (addon.getCategory() == MachineAddon.Category.ROTOR) {
                if (addon.getRotorPower() > 0) {
                    activePower = addon.getRotorPower();
                }
                break;
            }
        }

        double holderMax = getNodeRotorHolderMaxEUt(node, node.getTargetTier(), activePower);
        node.setParallel((int) Math.max(1, Math.ceil(holderMax / node.getBaseEUt())));
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


