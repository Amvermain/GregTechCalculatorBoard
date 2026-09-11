package com.gtceu.calcboard.compat.gtceu.handler;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTTurbinePhysics;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles installation and removal lifecycles of GTCEu machine addons.
 */
public final class GTAddonLifecycleHandler {

    private GTAddonLifecycleHandler() {}

    public static void onAddonInstalled(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (!node.isMultiblock() && addon.getCategory() != AddonCategory.CUSTOM && addon.getCategory() != AddonCategory.THERMAL_AUGMENT) {
            node.setMultiblock(true);
            ResourceLocation mbWs = node.getMultiblockWorkstation();
            if (mbWs != null) {
                node.setMachineIcon(mbWs);
            }
        }
        applyAddonInstallation(node, addon);
        node.markOverclockDirty();
    }

    private static void applyAddonInstallation(RecipeNode node, MachineAddon addon) {
        if (addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT) {
            if (addon.isThermalUpgradeKit()) {
                node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.THERMAL_AUGMENT && a.isThermalUpgradeKit());
            }
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
            int eff = (int) Math.round(addon.getDurationMultiplier() * 100.0);
            int power = addon.getRotorPower() > 0 ? addon.getRotorPower() : 100;
            node.setRotorEfficiency(eff);
            node.setRotorPower(power);
            node.setRotorName(addon.getName());
            node.getAddons().add(addon);
            GTTurbinePhysics.autoCalculateTurbineParallel(node);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.COIL) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.COIL);
            MachineAddon tailored = addon.forMachine(node);
            node.getAddons().add(tailored);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.REFLECTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.REFLECTOR);
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.MAINTENANCE) {
            if (GTAddonCompatibilityHandler.isMufflerAddon(addon)) {
                node.getAddons().removeIf(GTAddonCompatibilityHandler::isMufflerAddon);
            } else {
                node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.MAINTENANCE && !GTAddonCompatibilityHandler.isMufflerAddon(a));
            }
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.PARALLEL) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.PARALLEL);
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
            int maxHatches = GTEnergyHatchCalculator.getMaxAllowedEnergyHatches(node);
            List<MachineAddon> existing = new ArrayList<>();
            for (MachineAddon a : node.getAddons()) {
                if (a.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
                    existing.add(a);
                }
            }
            while (existing.size() >= maxHatches && !existing.isEmpty()) {
                node.getAddons().remove(existing.remove(0));
            }
            node.getAddons().add(addon);
            GTEnergyHatchCalculator.updateNodeTierFromEnergyHatches(node);
            return;
        }
        if (GTCombustionAddonHelper.isCombustionBoostAddon(addon)) {
            GTCombustionAddonHelper.applyCombustionBoostInstallation(node, addon);
            node.getAddons().add(addon);
            GTCombustionHelper.syncCombustionInputs(node);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT) {
            node.getAddons().removeIf(a -> a.getId().equals(addon.getId()));
            node.getAddons().add(addon);
            return;
        }
        if (addon.getCategory() == MachineAddon.Category.HATCH_BUS) {
            node.getAddons().add(addon);
            return;
        }
        node.getAddons().removeIf(a -> a.getId().equals(addon.getId()));
        node.getAddons().add(addon);
    }

    public static void onAddonRemoved(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return;
        if (addon.getCategory() == MachineAddon.Category.ENERGY_HATCH) {
            GTEnergyHatchCalculator.updateNodeTierFromEnergyHatches(node);
            if (GTTurbineHelper.isTurbine(node)) {
                List<GTEnergyHatchAddon> remaining = node.getAddons().stream()
                        .filter(a -> a instanceof GTEnergyHatchAddon)
                        .map(a -> (GTEnergyHatchAddon) a)
                        .toList();
                if (!remaining.isEmpty()) {
                    GTTurbineHelper.setDynamoTier(node, remaining.get(0).getTier());
                    int totalAmps = remaining.stream().mapToInt(GTEnergyHatchAddon::getAmperage).sum();
                    GTTurbineHelper.setDynamoAmperage(node, totalAmps);
                } else {
                    GTTurbineHelper.setDynamoTier(node, node.getTargetTier() != null ? node.getTargetTier() : GTVoltageTier.EV);
                    GTTurbineHelper.setDynamoAmperage(node, 1);
                }
            }
        } else if (addon.getCategory() == MachineAddon.Category.ROTOR) {
            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.ROTOR);
            node.setRotorEfficiency(100);
            node.setRotorPower(100);
            GTTurbinePhysics.autoCalculateTurbineParallel(node);
        } else if (addon.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT) {
            node.getAddons().removeIf(a -> a.getId().equals(addon.getId()));
            if (GTCombustionAddonHelper.isCombustionBoostAddon(addon)) {
                GTCombustionAddonHelper.applyCombustionBoostRemoval(node, addon);
            }
        }
        node.markOverclockDirty();
    }
}
