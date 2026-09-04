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

    public static boolean isBoilerRecipe(RecipeNode node) {
        if (node == null) return false;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("boiler")) {
            return true;
        }
        if (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("boiler")) {
            return true;
        }
        return false;
    }

    public static boolean isLargeBoilerRecipe(RecipeNode node) {
        if (node == null) return false;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("large_boiler")) {
            return true;
        }
        return false;
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
                } else if (in.isFluid() && !WATER_ID.equals(in.getId()) && !in.getId().getPath().equals("water")) {
                    hasNonWaterFluid = true;
                }
            }
        }
        if (hasNonWaterFluid) return true;
        if (hasItemInput) return false;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("liquid")) return true;
        if (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("liquid")) return true;
        return false;
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
