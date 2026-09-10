package com.gtceu.calcboard.compat.gtceu.handler;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;

/**
 * Handles combustion engine addons (oxygen boost, coolant, oxidizers) installation and compatibility.
 */
public final class GTCombustionAddonHelper {

    private GTCombustionAddonHelper() {}

    public static boolean isCombustionBoostAddon(MachineAddon addon) {
        if (addon == null || addon.getId() == null) return false;
        String id = addon.getId();
        return "gtceu:oxygen_boost".equals(id)
                || "gtceu:liquid_oxygen_boost".equals(id)
                || isCoolantAddon(addon)
                || isOxidizerAddon(addon);
    }

    public static boolean isCoolantAddon(MachineAddon addon) {
        if (addon == null || addon.getId() == null) return false;
        String id = addon.getId();
        return "start_core:distilled_water_coolant".equals(id) || "start_core:deionized_water_coolant".equals(id);
    }

    public static boolean isOxidizerAddon(MachineAddon addon) {
        if (addon == null || addon.getId() == null) return false;
        String id = addon.getId();
        return "start_core:t1_oxidizer_boost".equals(id)
                || "start_core:t2_oxidizer_boost".equals(id)
                || "start_core:t3_oxidizer_boost".equals(id)
                || "start_core:t4_oxidizer_boost".equals(id);
    }

    public static boolean isCombustionBoostCompatible(RecipeNode node, MachineAddon addon) {
        String id = addon.getId();
        if (GTCombustionHelper.isLargeCombustionEngine(node)) {
            return "gtceu:oxygen_boost".equals(id);
        }
        if (GTCombustionHelper.isExtremeCombustionEngine(node)) {
            return "gtceu:liquid_oxygen_boost".equals(id);
        }
        if (GTCombustionHelper.isModularCombustionFrame(node)
                || GTCombustionHelper.isStarTCombustionModule(node)
                || GTCombustionHelper.isStarTRocketModule(node)) {
            if (isCoolantAddon(addon)) {
                return true;
            }
        }
        if (GTCombustionHelper.START_T1_COMBUSTION.equals(node.getMachineIcon())) {
            return "start_core:t1_oxidizer_boost".equals(id);
        }
        if (GTCombustionHelper.START_T2_COMBUSTION.equals(node.getMachineIcon())) {
            return "start_core:t2_oxidizer_boost".equals(id);
        }
        if (GTCombustionHelper.START_T3_COMBUSTION.equals(node.getMachineIcon())) {
            return "start_core:t3_oxidizer_boost".equals(id);
        }
        if (GTCombustionHelper.START_T4_COMBUSTION.equals(node.getMachineIcon())) {
            return "start_core:t4_oxidizer_boost".equals(id);
        }
        return false;
    }

    public static void applyCombustionBoostInstallation(RecipeNode node, MachineAddon addon) {
        String id = addon.getId();
        if ("gtceu:oxygen_boost".equals(id)) {
            node.getAddons().removeIf(a -> "gtceu:oxygen_boost".equals(a.getId()));
            node.getProperties().set(GTCEuProperties.OXYGEN_BOOST, true);
        } else if ("gtceu:liquid_oxygen_boost".equals(id)) {
            node.getAddons().removeIf(a -> "gtceu:liquid_oxygen_boost".equals(a.getId()));
            node.getProperties().set(GTCEuProperties.LIQUID_OXYGEN_BOOST, true);
        } else if ("start_core:distilled_water_coolant".equals(id)) {
            node.getAddons().removeIf(GTCombustionAddonHelper::isCoolantAddon);
            node.getProperties().set(GTCEuProperties.COMBUSTION_COOLANT_TYPE, "distilled_water");
        } else if ("start_core:deionized_water_coolant".equals(id)) {
            node.getAddons().removeIf(GTCombustionAddonHelper::isCoolantAddon);
            node.getProperties().set(GTCEuProperties.COMBUSTION_COOLANT_TYPE, "deionized_water");
        } else if ("start_core:t1_oxidizer_boost".equals(id)) {
            node.getAddons().removeIf(GTCombustionAddonHelper::isOxidizerAddon);
            node.getProperties().set(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE, "white_fuming_nitric_acid");
        } else if ("start_core:t2_oxidizer_boost".equals(id)) {
            node.getAddons().removeIf(GTCombustionAddonHelper::isOxidizerAddon);
            node.getProperties().set(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE, "red_fuming_nitric_acid");
        } else if ("start_core:t3_oxidizer_boost".equals(id)) {
            node.getAddons().removeIf(GTCombustionAddonHelper::isOxidizerAddon);
            node.getProperties().set(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE, "dioxygen_difluoride");
        } else if ("start_core:t4_oxidizer_boost".equals(id)) {
            node.getAddons().removeIf(GTCombustionAddonHelper::isOxidizerAddon);
            node.getProperties().set(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE, "ferrocenium_superoxide");
        }
    }

    public static void applyCombustionBoostRemoval(RecipeNode node, MachineAddon addon) {
        String id = addon.getId();
        if ("gtceu:oxygen_boost".equals(id)) {
            node.getProperties().set(GTCEuProperties.OXYGEN_BOOST, false);
        } else if ("gtceu:liquid_oxygen_boost".equals(id)) {
            node.getProperties().set(GTCEuProperties.LIQUID_OXYGEN_BOOST, false);
        } else if ("start_core:distilled_water_coolant".equals(id) || "start_core:deionized_water_coolant".equals(id)) {
            node.getProperties().set(GTCEuProperties.COMBUSTION_COOLANT_TYPE, "none");
        } else if ("start_core:t1_oxidizer_boost".equals(id)
                || "start_core:t2_oxidizer_boost".equals(id)
                || "start_core:t3_oxidizer_boost".equals(id)
                || "start_core:t4_oxidizer_boost".equals(id)) {
            node.getProperties().set(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE, "none");
        }
        GTCombustionHelper.syncCombustionInputs(node);
    }
}
