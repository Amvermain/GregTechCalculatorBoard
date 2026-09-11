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

    public static final ResourceLocation FUSION_REACTOR_CATEGORY = ResourceLocation.tryParse("gtceu:fusion_reactor");

    public static final ResourceLocation FUSION_MK1 = ResourceLocation.tryParse("gtceu:luv_fusion_reactor");
    public static final ResourceLocation FUSION_MK2 = ResourceLocation.tryParse("gtceu:zpm_fusion_reactor");
    public static final ResourceLocation FUSION_MK3 = ResourceLocation.tryParse("gtceu:uv_fusion_reactor");
    public static final ResourceLocation FUSION_MK4 = ResourceLocation.tryParse("gtceu:uev_fusion_reactor");
    public static final ResourceLocation FUSION_MK5 = ResourceLocation.tryParse("gtceu:uxv_fusion_reactor");

    public static final ResourceLocation START_REFLECTOR_FUSION_I = ResourceLocation.tryParse("start_core:reflector_fusion_reactor_i");
    public static final ResourceLocation START_REFLECTOR_FUSION_II = ResourceLocation.tryParse("start_core:reflector_fusion_reactor_ii");
    public static final ResourceLocation START_REFLECTOR_FUSION_III = ResourceLocation.tryParse("start_core:reflector_fusion_reactor_iii");
    public static final ResourceLocation START_REFLECTOR_FUSION = ResourceLocation.tryParse("start_core:reflector_fusion_reactor");

    public static final ResourceLocation GT_REFLECTOR_FUSION_I = ResourceLocation.tryParse("gtceu:reflector_fusion_reactor_i");
    public static final ResourceLocation GT_REFLECTOR_FUSION_II = ResourceLocation.tryParse("gtceu:reflector_fusion_reactor_ii");
    public static final ResourceLocation GT_REFLECTOR_FUSION_III = ResourceLocation.tryParse("gtceu:reflector_fusion_reactor_iii");
    public static final ResourceLocation GT_REFLECTOR_FUSION = ResourceLocation.tryParse("gtceu:reflector_fusion_reactor");

    private static final java.util.Set<ResourceLocation> FUSION_CATEGORIES = java.util.Set.of(
            FUSION_REACTOR_CATEGORY,
            START_REFLECTOR_FUSION_I,
            START_REFLECTOR_FUSION_II,
            START_REFLECTOR_FUSION_III,
            START_REFLECTOR_FUSION,
            GT_REFLECTOR_FUSION_I,
            GT_REFLECTOR_FUSION_II,
            GT_REFLECTOR_FUSION_III,
            GT_REFLECTOR_FUSION
    );

    private static final java.util.Set<ResourceLocation> FUSION_MACHINES = java.util.Set.of(
            FUSION_MK1,
            FUSION_MK2,
            FUSION_MK3,
            FUSION_MK4,
            FUSION_MK5,
            START_REFLECTOR_FUSION_I,
            START_REFLECTOR_FUSION_II,
            START_REFLECTOR_FUSION_III,
            START_REFLECTOR_FUSION,
            GT_REFLECTOR_FUSION_I,
            GT_REFLECTOR_FUSION_II,
            GT_REFLECTOR_FUSION_III,
            GT_REFLECTOR_FUSION
    );

    public static boolean isFusionCategory(ResourceLocation catId) {
        return catId != null && FUSION_CATEGORIES.contains(catId);
    }

    public static boolean isFusionMachine(ResourceLocation icon) {
        return icon != null && FUSION_MACHINES.contains(icon);
    }

    public static boolean isFusion(RecipeNode node) {
        if (node == null) return false;
        if (node.getEuToStart() > 0 || node.getRequiredReflectorTier() > 0) return true;
        if (isFusionCategory(node.getRecipeCategoryId())) return true;
        return isFusionMachine(node.getMachineIcon());
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
            if (FUSION_MK2.equals(icon) || START_REFLECTOR_FUSION_II.equals(icon) || GT_REFLECTOR_FUSION_II.equals(icon)) return 2;
            if (FUSION_MK3.equals(icon) || START_REFLECTOR_FUSION_III.equals(icon) || GT_REFLECTOR_FUSION_III.equals(icon)) return 3;
            if (FUSION_MK4.equals(icon)) return 4;
            if (FUSION_MK5.equals(icon)) return 5;
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
