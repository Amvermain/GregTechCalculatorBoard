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

            List<IngredientStack> stepInputs = extractGTRecipeContents(stepObj, "inputs");
            List<IngredientStack> stepOutputs = extractGTRecipeContents(stepObj, "outputs");

            String roman = CompoundRecipeBuilder.formatRoman(stepIndex);
            layers.add(new CompoundRecipeBuilder.LayerSpec("Layer " + roman, stepDetails.durationTicks, stepDetails.eut, stepInputs, stepOutputs));
            stepIndex++;
        }

        if (layers.size() < 2) return null;

        return CompoundRecipeBuilder.build(machineName, machineIcon, totalDurationTicks, baseEUt, tier, layers, startX, startY);
    }

    public static List<IngredientStack> extractGTRecipeContents(Object gtRecipe, String fieldName) {
        List<IngredientStack> result = new ArrayList<>();
        if (gtRecipe == null) return result;

        try {
            Field f = null;
            Class<?> cur = gtRecipe.getClass();
            while (cur != null && cur != Object.class) {
                try {
                    f = cur.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    break;
                } catch (Throwable ignored) {
                    cur = cur.getSuperclass();
                }
            }

            if (f != null) {
                Object mapObj = f.get(gtRecipe);
                if (mapObj instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        Object cap = entry.getKey();
                        Object contentList = entry.getValue();
                        if (contentList instanceof List<?> list) {
                            boolean isFluid = cap != null && cap.toString().toLowerCase(Locale.ROOT).contains("fluid");
                            for (Object contentObj : list) {
                                IngredientStack is = parseGTContent(contentObj, isFluid);
                                if (is != null && is.getId() != null && !EmiRecipeConverter.isDummyConditionMarker(is.getId())) {
                                    result.add(is);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return result;
    }

    private static IngredientStack parseGTContent(Object contentObj, boolean isFluid) {
        if (contentObj == null) return null;
        try {
            Method getContentMethod = contentObj.getClass().getMethod("getContent");
            Object inner = getContentMethod.invoke(contentObj);
            float chance = 1.0f;
            try {
                Field chanceField = contentObj.getClass().getField("chance");
                chance = chanceField.getFloat(contentObj);
            } catch (Throwable ignored) {}

            if (inner instanceof ItemStack stack) {
                if (stack.isEmpty()) return null;
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
                String name = stack.getHoverName().getString();
                return IngredientStack.item(id, name, stack.getCount(), chance);
            }

            if (inner instanceof net.minecraftforge.fluids.FluidStack fStack) {
                if (fStack.isEmpty()) return null;
                ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fStack.getFluid());
                String name = fStack.getDisplayName().getString();
                return IngredientStack.fluid(id, name, fStack.getAmount(), chance);
            }

            if (inner instanceof net.minecraft.world.item.crafting.Ingredient ing) {
                var items = ing.getItems();
                if (items != null && items.length > 0 && !items[0].isEmpty()) {
                    ItemStack first = items[0];
                    ResourceLocation id = ForgeRegistries.ITEMS.getKey(first.getItem());
                    String name = first.getHoverName().getString();
                    long amount = 1;
                    try {
                        Method getAmountMethod = inner.getClass().getMethod("getAmount");
                        amount = ((Number) getAmountMethod.invoke(inner)).longValue();
                    } catch (Throwable ignored) {}
                    return IngredientStack.item(id, name, amount, chance);
                }
            }

            if (inner != null && inner.getClass().getName().contains("FluidIngredient")) {
                try {
                    Method getStacksMethod = inner.getClass().getMethod("getStacks");
                    Object res = getStacksMethod.invoke(inner);
                    if (res instanceof net.minecraftforge.fluids.FluidStack[] fArray && fArray.length > 0) {
                        net.minecraftforge.fluids.FluidStack first = fArray[0];
                        ResourceLocation id = ForgeRegistries.FLUIDS.getKey(first.getFluid());
                        String name = first.getDisplayName().getString();
                        return IngredientStack.fluid(id, name, first.getAmount(), chance);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }
}
