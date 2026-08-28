package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.CompoundRecipeBuilder;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Deterministic API reflection extractor for GTCEu / StarT Fork Layered Recipes.
 * Interfaces directly with {@code com.gregtechceu.gtceu.api.recipe.LayeredRecipeHelper}.
 */
public final class GTCEuLayeredRecipeExtractor {

    private GTCEuLayeredRecipeExtractor() {}

    public static boolean isLayeredRecipe(Object backingRecipe) {
        if (backingRecipe == null) return false;
        try {
            Class<?> helperClass = Class.forName("com.gregtechceu.gtceu.api.recipe.LayeredRecipeHelper");
            for (Method m : helperClass.getMethods()) {
                if (m.getName().equals("hasLayeredSteps") && m.getParameterCount() == 1) {
                    Object res = m.invoke(null, backingRecipe);
                    if (res instanceof Boolean b && b) return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static List<Object> extractLayeredSteps(Object backingRecipe) {
        if (backingRecipe == null) return Collections.emptyList();
        try {
            Class<?> helperClass = Class.forName("com.gregtechceu.gtceu.api.recipe.LayeredRecipeHelper");
            for (Method m : helperClass.getMethods()) {
                if (m.getName().equals("getLayeredSteps") && m.getParameterCount() == 1) {
                    Object res = m.invoke(null, backingRecipe);
                    if (res instanceof List<?> list) {
                        return (List<Object>) list;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    public static CompoundRecipeBuilder.CompoundCluster buildCompoundCluster(
            Object backingRecipe,
            String machineName,
            ResourceLocation machineIcon,
            GTVoltageTier tier,
            double startX,
            double startY
    ) {
        if (!isLayeredRecipe(backingRecipe)) return null;

        List<Object> stepRecipes = extractLayeredSteps(backingRecipe);
        if (stepRecipes.isEmpty() || stepRecipes.size() < 2) return null;

        List<CompoundRecipeBuilder.LayerSpec> layers = new ArrayList<>();
        double totalDurationTicks = 0.0;
        double baseEUt = 0.0;

        int stepIndex = 1;
        for (Object stepObj : stepRecipes) {
            if (stepObj == null) continue;

            EmiRecipeConverter.RecipeDetails stepDetails = new EmiRecipeConverter.RecipeDetails();
            GTCEuRecipeHandler.extractGTRecipeDetails(stepObj, stepDetails);

            totalDurationTicks += stepDetails.durationTicks;
            if (baseEUt <= 0.0) {
                baseEUt = stepDetails.eut;
            }

            List<IngredientStack> stepInputs = GTCEuRecipeHandler.extractGTRecipeContents(stepObj, "inputs");
            List<IngredientStack> stepOutputs = GTCEuRecipeHandler.extractGTRecipeContents(stepObj, "outputs");

            String roman = CompoundRecipeBuilder.formatRoman(stepIndex);
            layers.add(new CompoundRecipeBuilder.LayerSpec("Layer " + roman, stepDetails.durationTicks, stepDetails.eut, stepInputs, stepOutputs));
            stepIndex++;
        }

        if (layers.size() < 2) return null;

        return CompoundRecipeBuilder.build(machineName, machineIcon, totalDurationTicks, baseEUt, tier, layers, startX, startY);
    }
}
