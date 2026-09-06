package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.CompoundRecipeBuilder;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Decomposes multi-step progressive (layered) recipes into sequential compound clusters.
 * Utilizes static reflection and multi-tier deterministic NBT deduction to extract individual
 * processing layers without merging composite steps into a single monolithic recipe card.
 */
public final class GTCEuLayeredRecipeExtractor {

    private static final Method HAS_LAYERED_STEPS;
    private static final Method GET_LAYERED_STEPS_RECIPE;
    private static final Method GET_LAYERED_STEPS_TAG;
    private static final Method CALCULATE_RECIPE_STEPS;
    private static final Method IS_LAYERED_TYPE;
    private static final Field RECIPE_FIELD_IN_EMI;

    static {
        Method hasSteps = null;
        Method getStepsRecipe = null;
        Method getStepsTag = null;
        Method calcSteps = null;
        try {
            Class<?> helperClass = Class.forName("com.gregtechceu.gtceu.api.recipe.LayeredRecipeHelper");
            for (Method m : helperClass.getMethods()) {
                if (m.getParameterCount() != 1) continue;
                Class<?> pType = m.getParameterTypes()[0];
                if ("hasLayeredSteps".equals(m.getName())) {
                    if (!CompoundTag.class.isAssignableFrom(pType)) {
                        hasSteps = m;
                    }
                } else if ("getLayeredSteps".equals(m.getName())) {
                    if (CompoundTag.class.isAssignableFrom(pType)) {
                        getStepsTag = m;
                    } else {
                        getStepsRecipe = m;
                    }
                } else if ("calculateRecipeSteps".equals(m.getName())) {
                    calcSteps = m;
                }
            }
        } catch (Throwable ignored) {}
        HAS_LAYERED_STEPS = hasSteps;
        GET_LAYERED_STEPS_RECIPE = getStepsRecipe;
        GET_LAYERED_STEPS_TAG = getStepsTag;
        CALCULATE_RECIPE_STEPS = calcSteps;

        Method isLayered = null;
        try {
            Class<?> typeClass = Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipeType");
            isLayered = typeClass.getMethod("isLayered");
        } catch (Throwable ignored) {}
        IS_LAYERED_TYPE = isLayered;

        Field emiRecipe = null;
        try {
            Class<?> emiClass = Class.forName("com.gregtechceu.gtceu.integration.emi.recipe.GTEmiRecipe");
            emiRecipe = emiClass.getDeclaredField("recipe");
            emiRecipe.setAccessible(true);
        } catch (Throwable ignored) {}
        RECIPE_FIELD_IN_EMI = emiRecipe;
    }

    private GTCEuLayeredRecipeExtractor() {}

    public static boolean isLayeredRecipe(Object backingRecipe) {
        return isLayeredRecipe(backingRecipe, null);
    }

    public static boolean isLayeredRecipe(Object backingRecipe, Object recipeObj) {
        Object candidate = resolveBackingRecipe(backingRecipe, recipeObj);
        if (candidate == null) return false;

        if (checkHasLayeredSteps(candidate)) return true;

        CompoundTag dataTag = GTCEuRecipeHandler.extractRecipeDataTag(candidate);
        if (dataTag != null && (dataTag.contains("layered_steps") || dataTag.contains("layered_info") || dataTag.contains("is_layer"))) {
            return true;
        }

        return checkRecipeTypeIsLayered(candidate);
    }

    public static List<Object> extractLayeredSteps(Object backingRecipe) {
        return extractLayeredSteps(backingRecipe, null);
    }

    @SuppressWarnings("unchecked")
    public static List<Object> extractLayeredSteps(Object backingRecipe, Object recipeObj) {
        Object candidate = resolveBackingRecipe(backingRecipe, recipeObj);
        if (candidate == null) return Collections.emptyList();

        List<Object> steps = invokeGetLayeredStepsRecipe(candidate);
        if (!steps.isEmpty()) return steps;

        CompoundTag dataTag = GTCEuRecipeHandler.extractRecipeDataTag(candidate);
        if (dataTag != null) {
            steps = invokeGetLayeredStepsTag(dataTag);
            if (!steps.isEmpty()) return steps;

            if (dataTag.contains("layered_info")) {
                steps = invokeCalculateRecipeSteps(candidate);
                if (!steps.isEmpty()) return steps;
            }
        }

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
        return buildCompoundCluster(backingRecipe, null, machineName, machineIcon, tier, startX, startY);
    }

    public static CompoundRecipeBuilder.CompoundCluster buildCompoundCluster(
            Object backingRecipe,
            Object recipeObj,
            String machineName,
            ResourceLocation machineIcon,
            GTVoltageTier tier,
            double startX,
            double startY
    ) {
        Object resolvedRecipe = resolveBackingRecipe(backingRecipe, recipeObj);
        if (resolvedRecipe == null || !isLayeredRecipe(resolvedRecipe, recipeObj)) return null;

        List<Object> stepRecipes = extractLayeredSteps(resolvedRecipe, recipeObj);
        if (stepRecipes.size() < 2) return null;

        EmiRecipeConverter.RecipeDetails parentDetails = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(resolvedRecipe, parentDetails);

        double totalDurationTicks = parentDetails.durationTicks;
        double baseEUt = parentDetails.eut;
        GTVoltageTier effectiveTier = tier != null ? tier : parentDetails.tier;
        double fallbackDurationPerStep = totalDurationTicks > 0.0 ? totalDurationTicks / stepRecipes.size() : 20.0;

        List<CompoundRecipeBuilder.LayerSpec> layers = new ArrayList<>();
        double accumulatedDuration = 0.0;
        int stepIndex = 1;

        for (Object stepObj : stepRecipes) {
            if (stepObj == null) continue;

            EmiRecipeConverter.RecipeDetails stepDetails = new EmiRecipeConverter.RecipeDetails();
            GTCEuRecipeHandler.extractGTRecipeDetails(stepObj, stepDetails);

            double stepDuration = stepDetails.durationTicks > 0.0 ? stepDetails.durationTicks : fallbackDurationPerStep;
            double stepEUt = stepDetails.eut > 0.0 ? stepDetails.eut : baseEUt;
            accumulatedDuration += stepDuration;

            List<IngredientStack> stepInputs = GTCEuRecipeHandler.extractGTRecipeContents(stepObj, "inputs");
            List<IngredientStack> stepOutputs = GTCEuRecipeHandler.extractGTRecipeContents(stepObj, "outputs");

            String roman = CompoundRecipeBuilder.formatRoman(stepIndex);
            layers.add(new CompoundRecipeBuilder.LayerSpec("Layer " + roman, stepDuration, stepEUt, stepInputs, stepOutputs));
            stepIndex++;
        }

        if (layers.size() < 2) return null;

        double finalDuration = accumulatedDuration > 0.0 ? accumulatedDuration : totalDurationTicks;
        return CompoundRecipeBuilder.build(machineName, machineIcon, finalDuration, baseEUt, effectiveTier, layers, startX, startY);
    }

    private static Object resolveBackingRecipe(Object backingRecipe, Object recipeObj) {
        if (backingRecipe != null) {
            Object unwrapped = GTCEuRecipeHandler.unwrapRecipe(backingRecipe);
            if (unwrapped != null) return unwrapped;
        }

        if (recipeObj != null && RECIPE_FIELD_IN_EMI != null) {
            try {
                Object inner = RECIPE_FIELD_IN_EMI.get(recipeObj);
                if (inner != null) {
                    return GTCEuRecipeHandler.unwrapRecipe(inner);
                }
            } catch (Throwable ignored) {}
        }

        return backingRecipe;
    }

    private static boolean checkHasLayeredSteps(Object recipe) {
        if (HAS_LAYERED_STEPS == null || recipe == null) return false;
        try {
            Object res = HAS_LAYERED_STEPS.invoke(null, recipe);
            return res instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean checkRecipeTypeIsLayered(Object recipe) {
        if (IS_LAYERED_TYPE == null || recipe == null) return false;
        try {
            Field typeField = recipe.getClass().getField("recipeType");
            Object recipeType = typeField.get(recipe);
            if (recipeType != null) {
                Object res = IS_LAYERED_TYPE.invoke(recipeType);
                return res instanceof Boolean b && b;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> invokeGetLayeredStepsRecipe(Object recipe) {
        if (GET_LAYERED_STEPS_RECIPE == null || recipe == null) return Collections.emptyList();
        try {
            Object res = GET_LAYERED_STEPS_RECIPE.invoke(null, recipe);
            if (res instanceof List<?> list && !list.isEmpty()) {
                return (List<Object>) list;
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> invokeGetLayeredStepsTag(CompoundTag tag) {
        if (GET_LAYERED_STEPS_TAG == null || tag == null) return Collections.emptyList();
        try {
            Object res = GET_LAYERED_STEPS_TAG.invoke(null, tag);
            if (res instanceof List<?> list && !list.isEmpty()) {
                return (List<Object>) list;
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> invokeCalculateRecipeSteps(Object recipe) {
        if (CALCULATE_RECIPE_STEPS == null || recipe == null) return Collections.emptyList();
        try {
            Object res = CALCULATE_RECIPE_STEPS.invoke(null, recipe);
            if (res instanceof List<?> list && !list.isEmpty()) {
                return (List<Object>) list;
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }
}
