package com.gtceu.calcboard.compat.gtceu.handler;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTFusionHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates voltage tiers, overclock limits, and energy hatch capacities for GTCEu machines.
 */
public final class GTEnergyHatchCalculator {

    private GTEnergyHatchCalculator() {}

    public static int getMaxAllowedEnergyHatches(RecipeNode node) {
        if (node == null || !node.isMultiblock()) return 1;
        if (node.isGenerator()) return 1;

        ResourceLocation mbId = node.getMachineIcon();
        MultiblockStructureDef def = mbId != null ? MultiblockStructureCatalog.getStructure(mbId) : null;
        if (def == null && node.getMultiblockWorkstation() != null) {
            mbId = node.getMultiblockWorkstation();
            def = MultiblockStructureCatalog.getStructure(mbId);
        }

        if (mbId != null && MultiblockDetector.isSteamMultiblock(mbId)) {
            return 0;
        }

        if (def != null && def.energyHatchSlotCount() > 0) {
            return def.energyHatchSlotCount();
        }

        return 2;
    }

    public static GTVoltageTier getPrimaryEnergyHatchTier(RecipeNode node) {
        if (node == null) return null;
        List<GTEnergyHatchAddon> hatches = new ArrayList<>();
        for (MachineAddon a : node.getAddons()) {
            if (a instanceof GTEnergyHatchAddon eh) {
                hatches.add(eh);
            }
        }
        if (hatches.isEmpty()) {
            return null;
        }
        if (hatches.size() == 2 && getMaxAllowedEnergyHatches(node) >= 2 && hatches.get(0).getTier() == hatches.get(1).getTier()) {
            GTVoltageTier base = hatches.get(0).getTier();
            return base.ordinal() < GTVoltageTier.MAX.ordinal()
                    ? GTVoltageTier.getByIndex(base.ordinal() + 1)
                    : base;
        }
        GTVoltageTier maxSingleHatchTier = GTVoltageTier.ULV;
        for (var h : hatches) {
            if (h.getTier().ordinal() > maxSingleHatchTier.ordinal()) {
                maxSingleHatchTier = h.getTier();
            }
        }
        return maxSingleHatchTier;
    }

    public static boolean requiresEnergyHatch(RecipeNode node) {
        if (node == null || !node.isMultiblock() || node.isModule() || node.isGenerator()) {
            return false;
        }
        if (node.isFusion() || GTFusionHelper.isFusion(node)) {
            return false;
        }
        if (node.getEnergyType() != EnergyType.ELECTRIC_EU) {
            return false;
        }
        if (GTPowerCalculator.isBoilerRecipe(node) || GTCombustionHelper.isCombustionFamily(node)) {
            return false;
        }
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            return false;
        }
        ResourceLocation mbId = node.getMachineIcon() != null ? node.getMachineIcon() : node.getMultiblockWorkstation();
        if (mbId != null && MultiblockDetector.isSteamMultiblock(mbId)) {
            return false;
        }
        return true;
    }

    public static void updateNodeTierFromEnergyHatches(RecipeNode node) {
        if (node == null) return;
        GTVoltageTier hatchTier = getPrimaryEnergyHatchTier(node);
        if (hatchTier == null) {
            if (node.getRecipeTier() != null) {
                node.setTargetTier(node.getRecipeTier());
            }
            return;
        }

        node.setTargetTier(hatchTier);

        if (com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.isTurbine(node)) {
            List<GTEnergyHatchAddon> hatches = new ArrayList<>();
            for (MachineAddon a : node.getAddons()) {
                if (a instanceof GTEnergyHatchAddon eh) {
                    hatches.add(eh);
                }
            }
            if (!hatches.isEmpty()) {
                GTEnergyHatchAddon primary = hatches.get(0);
                com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.setDynamoTier(node, primary.getTier());
                int totalAmps = hatches.stream().mapToInt(GTEnergyHatchAddon::getAmperage).sum();
                com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.setDynamoAmperage(node, totalAmps);
            }
        }
    }

    public static long getMaxEUtCapacity(RecipeNode node) {
        if (node == null) return Long.MAX_VALUE;
        List<GTEnergyHatchAddon> hatches = new ArrayList<>();
        for (MachineAddon a : node.getAddons()) {
            if (a instanceof GTEnergyHatchAddon eh) {
                hatches.add(eh);
            }
        }
        if (!hatches.isEmpty()) {
            long total = 0;
            for (var h : hatches) {
                total += (long) h.getTier().getVoltage() * h.getAmperage();
            }
            return total;
        }
        if (node.getTargetTier() != null) {
            boolean hasParallelHatch = node.getAddons().stream().anyMatch(a ->
                    a instanceof GTParallelHatchAddon || a.getCategory() == MachineAddon.Category.PARALLEL);
            if (hasParallelHatch && node.getRecipeTier() != null && node.getTargetTier().ordinal() > node.getRecipeTier().ordinal()) {
                return node.getTargetTier().getVoltage() * 2L;
            }
        }
        return Long.MAX_VALUE;
    }

    public static long getOverclockVoltage(RecipeNode node) {
        if (node == null) return Long.MAX_VALUE;
        List<GTEnergyHatchAddon> hatches = new ArrayList<>();
        for (MachineAddon a : node.getAddons()) {
            if (a instanceof GTEnergyHatchAddon eh) {
                hatches.add(eh);
            }
        }
        if (!hatches.isEmpty()) {
            long totalInputVoltage = 0;
            long inputAmperage = 0;
            for (var h : hatches) {
                totalInputVoltage += (long) h.getTier().getVoltage() * h.getAmperage();
                inputAmperage += h.getAmperage();
            }
            if (totalInputVoltage <= 1 || inputAmperage <= 1) {
                return totalInputVoltage;
            }
            return calculateEffectiveOverclockVoltage(totalInputVoltage, inputAmperage);
        }
        if (node.isMultiblock() && node.getTargetTier() != null) {
            boolean hasParallelHatch = node.getAddons().stream().anyMatch(a ->
                    a instanceof GTParallelHatchAddon || a.getCategory() == MachineAddon.Category.PARALLEL);
            if (hasParallelHatch && node.getRecipeTier() != null && node.getTargetTier().ordinal() > node.getRecipeTier().ordinal()) {
                return node.getTargetTier().getVoltage();
            }
        }
        return Long.MAX_VALUE;
    }

    private static long calculateEffectiveOverclockVoltage(long totalInputVoltage, long inputAmperage) {
        long voltage = totalInputVoltage;
        long amperage = inputAmperage;
        if (hasPrimeFactorGreaterThanTwo(amperage) || isPowerOfFour(amperage)) {
            amperage = 1;
        } else if (amperage % 4 == 0) {
            while (amperage > 4) {
                amperage /= 4;
            }
            voltage /= amperage;
        } else if (amperage == 2) {
            voltage /= amperage;
        } else {
            amperage = 1;
        }

        if (amperage == 1) {
            GTVoltageTier floorTier = GTVoltageTier.getMaxTierProvided(voltage);
            return floorTier != null ? floorTier.getVoltage() : voltage;
        }
        return voltage;
    }

    private static boolean hasPrimeFactorGreaterThanTwo(long l) {
        int i = 2;
        long max = l / 2;
        while (i <= max) {
            if (l % i == 0) {
                if (i > 2) return true;
                l /= i;
            } else {
                i++;
            }
        }
        return false;
    }

    private static boolean isPowerOfFour(long l) {
        if (l == 0) return false;
        if ((l & (l - 1)) != 0) return false;
        return (l & 0x55555555L) != 0;
    }

    public static boolean hasEnergyHatch(RecipeNode node) {
        if (node == null) return false;
        for (MachineAddon a : node.getAddons()) {
            if (a instanceof GTEnergyHatchAddon || a.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
                return true;
            }
        }
        return false;
    }
}
