package com.gtceu.calcboard.compat.gtceu.physics;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.type.GTBoilerTier;

import java.util.Locale;

/**
 * Encapsulates physics, throttle modifiers, and steam generation rates for GTCEu boilers.
 */
public final class GTBoilerPhysics {

    private GTBoilerPhysics() {}

    public static final net.minecraft.resources.ResourceLocation STEAM_BOILER_RECIPE = net.minecraft.resources.ResourceLocation.tryParse("gtceu:steam_boiler");
    public static final net.minecraft.resources.ResourceLocation LARGE_BOILER_RECIPE = net.minecraft.resources.ResourceLocation.tryParse("gtceu:large_boiler");
    public static final net.minecraft.resources.ResourceLocation SYSTEAMS_BOILING_RECIPE = net.minecraft.resources.ResourceLocation.tryParse("systeams:boiling");
    public static final net.minecraft.resources.ResourceLocation SYSTEAMS_STEAM_BOILER_RECIPE = net.minecraft.resources.ResourceLocation.tryParse("systeams:steam_boiler");

    private static final java.util.Set<net.minecraft.resources.ResourceLocation> BOILER_CATEGORIES = java.util.Set.of(
            STEAM_BOILER_RECIPE,
            LARGE_BOILER_RECIPE,
            SYSTEAMS_BOILING_RECIPE,
            SYSTEAMS_STEAM_BOILER_RECIPE
    );

    private static final java.util.Set<net.minecraft.resources.ResourceLocation> LARGE_BOILER_CATEGORIES = java.util.Set.of(
            LARGE_BOILER_RECIPE
    );

    private static final java.util.Set<net.minecraft.resources.ResourceLocation> BOILER_MACHINES = java.util.Set.of(
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:lp_steam_solid_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:hp_steam_solid_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:lp_steam_liquid_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:hp_steam_liquid_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:bronze_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:steel_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:titanium_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:tungstensteel_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:large_bronze_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:large_steel_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:large_titanium_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:large_tungstensteel_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("systeams:lapidary_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("systeams:stirling_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("systeams:compression_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("systeams:gourmand_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("systeams:magmatic_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("systeams:pneumatic_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("systeams:disenchantment_boiler")
    );

    private static final java.util.Set<net.minecraft.resources.ResourceLocation> LARGE_BOILER_MACHINES = java.util.Set.of(
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:large_bronze_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:large_steel_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:large_titanium_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:large_tungstensteel_boiler")
    );

    private static final java.util.Set<net.minecraft.resources.ResourceLocation> LIQUID_BOILER_MACHINES = java.util.Set.of(
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:lp_steam_liquid_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("gtceu:hp_steam_liquid_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("systeams:compression_boiler"),
            net.minecraft.resources.ResourceLocation.tryParse("systeams:magmatic_boiler")
    );

    public static boolean isBoilerCategory(net.minecraft.resources.ResourceLocation catId) {
        return catId != null && BOILER_CATEGORIES.contains(catId);
    }

    public static boolean isLargeBoilerCategory(net.minecraft.resources.ResourceLocation catId) {
        return catId != null && LARGE_BOILER_CATEGORIES.contains(catId);
    }

    public static boolean isBoilerRecipe(RecipeNode node) {
        if (node == null) return false;
        if (isBoilerCategory(node.getRecipeCategoryId())) {
            return true;
        }
        net.minecraft.resources.ResourceLocation icon = node.getMachineIcon();
        return icon != null && BOILER_MACHINES.contains(icon);
    }

    public static boolean isLargeBoilerRecipe(RecipeNode node) {
        if (node == null) return false;
        if (isLargeBoilerCategory(node.getRecipeCategoryId())) {
            return true;
        }
        net.minecraft.resources.ResourceLocation icon = node.getMachineIcon();
        return icon != null && LARGE_BOILER_MACHINES.contains(icon);
    }

    private static final net.minecraft.resources.ResourceLocation WATER_ID = net.minecraft.resources.ResourceLocation.tryParse("minecraft:water");

    public static boolean isLiquidBoilerRecipe(RecipeNode node) {
        if (!isBoilerRecipe(node)) return false;
        boolean hasNonWaterFluid = false;
        boolean hasItemInput = false;
        for (IngredientStack in : node.getInputs()) {
            if (in != null && in.getId() != null) {
                if (in.isItem()) {
                    hasItemInput = true;
                } else if (in.isFluid() && !WATER_ID.equals(in.getId())) {
                    hasNonWaterFluid = true;
                }
            }
        }
        if (hasNonWaterFluid) return true;
        if (hasItemInput) return false;
        net.minecraft.resources.ResourceLocation icon = node.getMachineIcon();
        return icon != null && LIQUID_BOILER_MACHINES.contains(icon);
    }

    public static double getBoilerSpeedMultiplier(RecipeNode node) {
        if (node == null) return 1.0;
        GTBoilerTier bt = GTBoilerTier.getBoilerTier(node);
        boolean isLiquid = isLiquidBoilerRecipe(node);
        boolean isLargeBoiler = isLargeBoilerRecipe(node);
        double speed = bt.getSpeedMultiplier(isLiquid, isLargeBoiler);
        if (bt.isMultiblock()) {
            int throttle = node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.BOILER_THROTTLE);
            throttle = Math.max(25, Math.min(100, throttle));
            speed *= (throttle / 100.0);
        }
        return speed;
    }
}
