package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel;
import net.minecraft.resources.ResourceLocation;

public class StarTTurbineHelper {

    public static final ResourceLocation SUPREME_PLASMA_TURBINE_ID = ResourceLocation.tryParse("start_core:supreme_plasma_turbine");
    public static final ResourceLocation NYINSANE_PLASMA_TURBINE_ID = ResourceLocation.tryParse("start_core:nyinsane_plasma_turbine");

    public static boolean isStarTTurbine(RecipeNode node) {
        if (node == null) return false;
        GTPlasmaTurbineModel model = GTPlasmaTurbineModel.getModel(node);
        if (model == GTPlasmaTurbineModel.SPT || model == GTPlasmaTurbineModel.NPT) {
            return true;
        }
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            if (icon.equals(SUPREME_PLASMA_TURBINE_ID) || icon.equals(NYINSANE_PLASMA_TURBINE_ID)) {
                return true;
            }
            String p = icon.getPath();
            return p.contains("supreme_plasma_turbine") || p.contains("nyinsane_plasma_turbine");
        }
        return false;
    }

    public static boolean supportsBoost(RecipeNode node) {
        if (node == null || !node.isTurbine()) return false;
        if (!GTPlasmaTurbineModel.isPlasmaTurbine(node)) return false;
        GTPlasmaTurbineModel model = GTPlasmaTurbineModel.getModel(node);
        return model == GTPlasmaTurbineModel.SPT || model == GTPlasmaTurbineModel.NPT;
    }

    /**
     * Calculates the deterministic boost multiplier based on StarT Core BoostedPlasmaTurbine.java:
     * - SPT (Supreme):
     *     WS2 + SS-He3 (Active): 2.0x (+100%)
     *     WS2 Only (Passive): 1.25x (+25%)
     *     No WS2 (Unboosted): 0.9x (-10% penalty)
     * - NPT (Nyinsane):
     *     WS2 + BEC-Og (Active): 3.0x (+200%)
     *     WS2 Only (Passive): 1.50x (+50%)
     *     No WS2 (Unboosted): 0.8x (-20% penalty)
     */
    public static double getTurbineBoostMultiplier(RecipeNode node) {
        if (!supportsBoost(node)) return 1.0;
        GTPlasmaTurbineModel model = GTPlasmaTurbineModel.getModel(node);
        boolean lub = Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.LUBRICANT_BOOST));
        boolean cool = Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.COOLANT_BOOST));

        if (model == GTPlasmaTurbineModel.SPT) {
            if (lub && cool) return 2.0;
            if (lub) return 1.25;
            return 0.9;
        } else if (model == GTPlasmaTurbineModel.NPT) {
            if (lub && cool) return 3.0;
            if (lub) return 1.50;
            return 0.8;
        }
        return 1.0;
    }

    public static void cycleTurbineBoost(RecipeNode node, int direction) {
        if (!supportsBoost(node)) return;
        boolean lub = Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.LUBRICANT_BOOST));
        boolean cool = Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.COOLANT_BOOST));
        // 0: Unboosted (No WS2) -> 1: Passive (WS2) -> 2: Full (WS2 + Active)
        int cur = (lub && cool) ? 2 : (lub ? 1 : 0);
        int next = (cur + direction + 3) % 3;
        if (next == 1) {
            node.getProperties().set(GTCEuProperties.LUBRICANT_BOOST, true);
            node.getProperties().set(GTCEuProperties.COOLANT_BOOST, false);
        } else if (next == 2) {
            node.getProperties().set(GTCEuProperties.LUBRICANT_BOOST, true);
            node.getProperties().set(GTCEuProperties.COOLANT_BOOST, true);
        } else {
            node.getProperties().set(GTCEuProperties.LUBRICANT_BOOST, false);
            node.getProperties().set(GTCEuProperties.COOLANT_BOOST, false);
        }
        syncBoosterInputs(node);
    }

    public static boolean isBoosterFluid(com.gtceu.calcboard.api.model.IngredientStack in) {
        if (in == null || !in.isFluid() || in.getId() == null) return false;
        String p = in.getId().getPath();
        return p.contains("tungsten_disulfide") || p.contains("superstate_helium_3")
                || p.contains("superstate") || p.contains("bec_og") || p.contains("oganesson");
    }

    public static void syncBoosterInputs(RecipeNode node) {
        if (node == null) return;
        node.getInputs().removeIf(StarTTurbineHelper::isBoosterFluid);

        if (!supportsBoost(node)) return;

        GTPlasmaTurbineModel model = GTPlasmaTurbineModel.getModel(node);
        boolean lub = Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.LUBRICANT_BOOST));
        boolean cool = Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.COOLANT_BOOST));

        int parallel = Math.max(1, node.getTotalParallel());
        double durationSec = Math.max(0.05, node.getEffectiveDurationSeconds());

        if (model == GTPlasmaTurbineModel.SPT) {
            if (lub) {
                // WS2: 1000 mB / hr
                double perRecipeAmount = (1000.0 / 3600.0) * durationSec / parallel;
                node.getInputs().add(com.gtceu.calcboard.api.model.IngredientStack.fluid(ResourceLocation.tryParse("gtceu:tungsten_disulfide"), "Tungsten Disulfide", perRecipeAmount, 1.0));
            }
            if (lub && cool) {
                // SS-He3: 2500 mB / hr
                double perRecipeAmount = (2500.0 / 3600.0) * durationSec / parallel;
                node.getInputs().add(com.gtceu.calcboard.api.model.IngredientStack.fluid(ResourceLocation.tryParse("gtceu:superstate_helium_3"), "Superstate Helium 3", perRecipeAmount, 1.0));
            }
        } else if (model == GTPlasmaTurbineModel.NPT) {
            if (lub) {
                // WS2: 2500 mB / hr
                double perRecipeAmount = (2500.0 / 3600.0) * durationSec / parallel;
                node.getInputs().add(com.gtceu.calcboard.api.model.IngredientStack.fluid(ResourceLocation.tryParse("gtceu:tungsten_disulfide"), "Tungsten Disulfide", perRecipeAmount, 1.0));
            }
            if (lub && cool) {
                // BEC-Og: 800 mB / hr
                double perRecipeAmount = (800.0 / 3600.0) * durationSec / parallel;
                node.getInputs().add(com.gtceu.calcboard.api.model.IngredientStack.fluid(ResourceLocation.tryParse("gtceu:bec_og"), "Oganesson Stabilized BEC", perRecipeAmount, 1.0));
            }
        }
    }

    public static boolean isStarTTrait(MachineAddon addon) {
        if (addon == null || addon.getId() == null) return false;
        String id = addon.getId();
        return id.startsWith("start_core:spt_") || id.startsWith("start_core:npt_") || id.contains("spt_") || id.contains("npt_");
    }

    public static boolean isCompatibleStarTTrait(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null || !isStarTTrait(addon)) {
            return false;
        }
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            if (icon.equals(SUPREME_PLASMA_TURBINE_ID) || icon.getPath().contains("supreme_plasma_turbine")) {
                return addon.getId().contains("spt_");
            } else if (icon.equals(NYINSANE_PLASMA_TURBINE_ID) || icon.getPath().contains("nyinsane_plasma_turbine")) {
                return addon.getId().contains("npt_");
            }
        }
        return false;
    }
}
