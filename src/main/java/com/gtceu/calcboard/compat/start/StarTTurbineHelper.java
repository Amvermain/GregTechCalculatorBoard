package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.GTPlasmaTurbineModel;
import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.api.RecipeNode;

public class StarTTurbineHelper {

    public static boolean isStarTTurbine(RecipeNode node) {
        if (node == null || !node.isTurbine()) return false;
        GTPlasmaTurbineModel model = GTPlasmaTurbineModel.getModel(node);
        return model == GTPlasmaTurbineModel.SPT || model == GTPlasmaTurbineModel.NPT;
    }

    public static boolean isStarTTrait(MachineAddon addon) {
        if (addon == null || addon.getId() == null) return false;
        String id = addon.getId();
        return id.contains("spt_") || id.contains("npt_");
    }

    public static boolean isCompatibleStarTTrait(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null || !isStarTTrait(addon)) {
            return false;
        }
        if (!node.isTurbine()) {
            return false;
        }
        GTPlasmaTurbineModel model = GTPlasmaTurbineModel.getModel(node);
        if (model == GTPlasmaTurbineModel.SPT) {
            return addon.getId().contains("spt_");
        } else if (model == GTPlasmaTurbineModel.NPT) {
            return addon.getId().contains("npt_");
        }
        return false;
    }
}
