package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.api.RecipeNode;
import net.minecraft.resources.ResourceLocation;
import com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel;

public class StarTTurbineHelper {

    public static boolean isStarTTurbine(RecipeNode node) {
        if (node == null) return false;
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            String p = icon.getPath();
            return p.contains("supreme_plasma_turbine") || p.contains("nyinsane_plasma_turbine");
        }
        return false;
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
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            String p = icon.getPath();
            if (p.contains("supreme_plasma_turbine")) {
                return addon.getId().contains("spt_");
            } else if (p.contains("nyinsane_plasma_turbine")) {
                return addon.getId().contains("npt_");
            }
        }
        return false;
    }
}
