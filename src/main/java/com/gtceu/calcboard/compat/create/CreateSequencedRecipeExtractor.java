package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.model.CompoundRecipeBuilder;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Deterministic API reflection extractor for Create Sequenced Assembly Recipes.
 * Interfaces directly with {@code com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe}.
 */
public final class CreateSequencedRecipeExtractor {

    private static final String SEQUENCED_RECIPE_CLASS = "com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe";

    private CreateSequencedRecipeExtractor() {}

    public static boolean isSequencedRecipe(Object backingRecipe) {
        if (backingRecipe == null) return false;
        try {
            Class<?> clazz = Class.forName(SEQUENCED_RECIPE_CLASS);
            return clazz.isInstance(backingRecipe);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    public static CompoundRecipeBuilder.CompoundCluster buildCompoundCluster(
            Object backingRecipe,
            String defaultMachineName,
            ResourceLocation defaultMachineIcon,
            GTVoltageTier tier,
            double startX,
            double startY
    ) {
        if (!isSequencedRecipe(backingRecipe)) return null;

        try {
            Class<?> seqClass = Class.forName(SEQUENCED_RECIPE_CLASS);

            int loops = 1;
            try {
                Method getLoopsMethod = seqClass.getMethod("getLoops");
                Object loopsObj = getLoopsMethod.invoke(backingRecipe);
                if (loopsObj instanceof Number num && num.intValue() > 0) {
                    loops = num.intValue();
                }
            } catch (Throwable ignored) {}

            Method getSequenceMethod = seqClass.getMethod("getSequence");
            Object sequenceObj = getSequenceMethod.invoke(backingRecipe);
            if (!(sequenceObj instanceof List<?> rawSteps) || rawSteps.isEmpty()) {
                return null;
            }

            // Extract initial ingredient (base item for sequence)
            Ingredient baseIngredient = null;
            try {
                Method getIngredientMethod = seqClass.getMethod("getIngredient");
                Object ingObj = getIngredientMethod.invoke(backingRecipe);
                if (ingObj instanceof Ingredient ing) {
                    baseIngredient = ing;
                }
            } catch (Throwable ignored) {}

            // Extract final result
            List<IngredientStack> finalOutputs = new ArrayList<>();
            try {
                Method getResultMethod = seqClass.getMethod("getResultItem");
                Object resItemObj = getResultMethod.invoke(backingRecipe);
                if (resItemObj instanceof ItemStack resStack && !resStack.isEmpty()) {
                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(resStack.getItem());
                    String name = resStack.getHoverName().getString();
                    finalOutputs.add(IngredientStack.item(itemId, name, resStack.getCount()));
                }
            } catch (Throwable ignored) {
                try {
                    Method getResultMethod = seqClass.getMethod("getResultItem", net.minecraft.core.RegistryAccess.class);
                    // Fallback reflection if registry access parameter required
                } catch (Throwable ignored2) {}
            }

            List<CompoundRecipeBuilder.LayerSpec> layers = new ArrayList<>();
            double totalDurationTicks = 0.0;
            double baseSU = 128.0;

            int globalStepNumber = 1;
            for (int loop = 0; loop < loops; loop++) {
                for (Object stepObj : rawSteps) {
                    if (stepObj == null) continue;

                    Object subRecipe = null;
                    try {
                        Method getRecipeMethod = stepObj.getClass().getMethod("getRecipe");
                        subRecipe = getRecipeMethod.invoke(stepObj);
                    } catch (Throwable ignored) {
                        subRecipe = stepObj;
                    }

                    if (subRecipe == null) continue;

                    List<IngredientStack> stepInputs = new ArrayList<>();
                    List<IngredientStack> stepOutputs = new ArrayList<>();

                    // For the very first step of loop 0, include the base ingredient
                    if (globalStepNumber == 1 && baseIngredient != null && baseIngredient.getItems().length > 0) {
                        ItemStack firstStack = baseIngredient.getItems()[0];
                        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(firstStack.getItem());
                        String name = firstStack.getHoverName().getString();
                        stepInputs.add(IngredientStack.item(itemId, name, firstStack.getCount() > 0 ? firstStack.getCount() : 1));
                    }

                    // Extract additional step inputs (e.g. secondary item from deployer, fluid from spout)
                    extractSubRecipeInputs(subRecipe, stepInputs);

                    int stepDuration = 20; // Default 1 second
                    try {
                        Method durMethod = subRecipe.getClass().getMethod("getProcessingDuration");
                        Object durObj = durMethod.invoke(subRecipe);
                        if (durObj instanceof Number num && num.intValue() > 0) {
                            stepDuration = num.intValue();
                        }
                    } catch (Throwable ignored) {}

                    totalDurationTicks += stepDuration;

                    String stepTitle = "Step " + globalStepNumber;
                    if (loops > 1) {
                        stepTitle += " (Loop " + (loop + 1) + "/" + loops + ")";
                    }

                    // If this is the absolute last step, attach final outputs
                    boolean isLastStep = (loop == loops - 1) && (stepObj == rawSteps.get(rawSteps.size() - 1));
                    if (isLastStep) {
                        stepOutputs.addAll(finalOutputs);
                    }

                    ResourceLocation stepMachineIcon = extractStepMachineIcon(subRecipe);

                    layers.add(new CompoundRecipeBuilder.LayerSpec(
                            stepTitle,
                            stepMachineIcon,
                            stepDuration,
                            baseSU,
                            stepInputs,
                            stepOutputs
                    ));

                    globalStepNumber++;
                }
            }

            if (layers.isEmpty()) return null;

            return CompoundRecipeBuilder.build(
                    defaultMachineName != null ? defaultMachineName : "Sequenced Assembly",
                    defaultMachineIcon != null ? defaultMachineIcon : ResourceLocation.tryParse("create:sequenced_assembly"),
                    totalDurationTicks,
                    baseSU,
                    tier != null ? tier : GTVoltageTier.ULV,
                    layers,
                    startX,
                    startY
            );

        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void extractSubRecipeInputs(Object subRecipe, List<IngredientStack> collector) {
        if (subRecipe == null || collector == null) return;
        try {
            // Check Ingredients
            Method getIngredientsMethod = subRecipe.getClass().getMethod("getIngredients");
            Object ingListObj = getIngredientsMethod.invoke(subRecipe);
            if (ingListObj instanceof List<?> ingList) {
                // In Sequenced Recipes, the first ingredient is usually the transition item placeholder,
                // so secondary ingredients start from index 1.
                for (int i = 1; i < ingList.size(); i++) {
                    Object itemIng = ingList.get(i);
                    if (itemIng instanceof Ingredient ing && ing.getItems().length > 0) {
                        ItemStack st = ing.getItems()[0];
                        if (!st.isEmpty()) {
                            ResourceLocation id = ForgeRegistries.ITEMS.getKey(st.getItem());
                            collector.add(IngredientStack.item(id, st.getHoverName().getString(), st.getCount() > 0 ? st.getCount() : 1));
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        try {
            // Check FluidIngredients
            Method getFluidIngredientsMethod = subRecipe.getClass().getMethod("getFluidIngredients");
            Object fluidListObj = getFluidIngredientsMethod.invoke(subRecipe);
            if (fluidListObj instanceof List<?> fluidList) {
                for (Object fIng : fluidList) {
                    if (fIng == null) continue;
                    try {
                        Method getRequiredAmount = fIng.getClass().getMethod("getRequiredAmount");
                        Object amtObj = getRequiredAmount.invoke(fIng);
                        long amount = (amtObj instanceof Number n) ? n.longValue() : 1000L;

                        Method getMatchingFluids = fIng.getClass().getMethod("getMatchingFluidStacks");
                        Object mFluids = getMatchingFluids.invoke(fIng);
                        if (mFluids instanceof List<?> fStacks && !fStacks.isEmpty()) {
                            Object fStack = fStacks.get(0);
                            Method getFluidMethod = fStack.getClass().getMethod("getFluid");
                            Object fluidObj = getFluidMethod.invoke(fStack);
                            if (fluidObj instanceof net.minecraft.world.level.material.Fluid fluid) {
                                ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
                                collector.add(IngredientStack.fluid(fluidId, fluidId != null ? fluidId.getPath() : "Fluid", amount));
                            }
                        }
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
    }

    public static ResourceLocation extractStepMachineIcon(Object subRecipe) {
        if (subRecipe == null) return null;
        String clName = subRecipe.getClass().getName().toLowerCase(Locale.ROOT);
        if (clName.contains("deploy")) {
            return ResourceLocation.tryParse("create:deployer");
        } else if (clName.contains("fill") || clName.contains("spout")) {
            return ResourceLocation.tryParse("create:spout");
        } else if (clName.contains("press")) {
            return ResourceLocation.tryParse("create:mechanical_press");
        } else if (clName.contains("cut") || clName.contains("saw")) {
            return ResourceLocation.tryParse("create:mechanical_saw");
        }

        try {
            Method getSerializerM = subRecipe.getClass().getMethod("getSerializer");
            Object ser = getSerializerM.invoke(subRecipe);
            if (ser != null) {
                ResourceLocation sId = ForgeRegistries.RECIPE_SERIALIZERS.getKey((net.minecraft.world.item.crafting.RecipeSerializer<?>) ser);
                if (sId != null) {
                    String p = sId.getPath().toLowerCase(Locale.ROOT);
                    if (p.contains("deploy")) return ResourceLocation.tryParse("create:deployer");
                    if (p.contains("fill") || p.contains("spout")) return ResourceLocation.tryParse("create:spout");
                    if (p.contains("press")) return ResourceLocation.tryParse("create:mechanical_press");
                    if (p.contains("cut") || p.contains("saw")) return ResourceLocation.tryParse("create:mechanical_saw");
                }
            }
        } catch (Throwable ignored) {}

        return ResourceLocation.tryParse("create:deployer");
    }
}
