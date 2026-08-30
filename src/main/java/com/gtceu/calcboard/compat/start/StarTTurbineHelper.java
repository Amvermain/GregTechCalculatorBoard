package com.gtceu.calcboard.compat.start;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.resources.ResourceLocation;
import com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel;

public class StarTTurbineHelper {

    public static final ResourceLocation SUPREME_PLASMA_TURBINE_ID = ResourceLocation.tryParse("start_core:supreme_plasma_turbine");
    public static final ResourceLocation NYINSANE_PLASMA_TURBINE_ID = ResourceLocation.tryParse("start_core:nyinsane_plasma_turbine");

    public static boolean isStarTTurbine(RecipeNode node) {
        if (node == null) return false;
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
