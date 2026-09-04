package com.gtceu.calcboard.compat.gtceu.physics;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Dedicated helper for GTCEu Fusion Reactor tier calculation, start EU requirements,
 * and minimum voltage tier resolutions.
 */
public final class GTFusionHelper {

    private GTFusionHelper() {}

    public static boolean isFusion(RecipeNode node) {
        if (node == null) return false;
        if (node.getEuToStart() > 0 || node.getRequiredReflectorTier() > 0) return true;
        ResourceLocation catId = node.getRecipeCategoryId();
        if (catId != null) {
            String path = catId.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("fusion")) return true;
        }
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            String path = icon.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("fusion")) return true;
        }
        return false;
    }

    public static int getFusionTier(RecipeNode node) {
        if (node == null) return 1;
        long startEU = node.getEuToStart();
        if (startEU > 0) {
            if (startEU <= 160_000_000L) return 1;
            if (startEU <= 320_000_000L) return 2;
            if (startEU <= 640_000_000L) return 3;
            return 4;
        }
        ResourceLocation icon = node.getMachineIcon();
        if (icon != null) {
            String path = icon.getPath().toLowerCase(Locale.ROOT);
            if (path.contains("mk2") || path.contains("zpm") || path.contains("_ii") || path.endsWith("_2")) return 2;
            if (path.contains("mk3") || path.contains("uv") || path.contains("_iii") || path.endsWith("_3")) return 3;
            if (path.contains("mk4") || path.contains("uev") || path.contains("_iv") || path.endsWith("_4")) return 4;
            if (path.contains("mk5") || path.contains("uxv") || path.contains("_v") || path.endsWith("_5")) return 5;
        }
        return 1;
    }

    public static GTVoltageTier getMinFusionVoltageTier(RecipeNode node) {
        int fTier = getFusionTier(node);
        return fTier == 1 ? GTVoltageTier.LuV 
                : (fTier == 2 ? GTVoltageTier.ZPM 
                : (fTier == 3 ? GTVoltageTier.UV 
                : (fTier == 4 ? GTVoltageTier.UEV : GTVoltageTier.UXV)));
    }

    public static int getReflectorOverclockDelta(RecipeNode node) {
        if (node == null || !isFusion(node)) return 0;
        int req = node.getRequiredReflectorTier();
        if (req <= 0) return 0;
        int inst = node.getInstalledReflectorTier();
        return Math.max(0, inst - req);
    }
}
