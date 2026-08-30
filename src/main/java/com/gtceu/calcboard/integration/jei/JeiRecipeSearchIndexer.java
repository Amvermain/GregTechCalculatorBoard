package com.gtceu.calcboard.integration.jei;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.SearchableRecipe;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTCEuLayeredRecipeExtractor;
import com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolated indexing implementation for JEI recipes.
 */
public final class JeiRecipeSearchIndexer {

    private record StackSearchData(String name, String searchText) {}
    private static final Map<ResourceLocation, StackSearchData> STACK_DATA_CACHE = new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, String> CATEGORY_CATALYST_TEXT_CACHE = new ConcurrentHashMap<>();

    private JeiRecipeSearchIndexer() {}

    public static <T> SearchableRecipe buildIndex(JeiRecipeWrapper<T> wrapper, IJeiRuntime jeiRuntime) {
        if (wrapper == null) return null;
        return buildIndex(wrapper.category(), wrapper.recipe(), jeiRuntime);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public static SearchableRecipe buildIndexUntyped(IRecipeCategory<?> category, Object recipe, IJeiRuntime jeiRuntime) {
        if (category == null || recipe == null) return null;
        return buildIndex((IRecipeCategory) category, recipe, jeiRuntime);
    }

    public static <T> SearchableRecipe buildIndex(IRecipeCategory<T> category, T recipe, IJeiRuntime jeiRuntime) {
        if (category == null || recipe == null) return null;

        ResourceLocation catResId = (category.getRecipeType() != null) ? category.getRecipeType().getUid() : null;
        String modId = "";
        String categoryId = "";
        String categoryName = "";

        if (catResId != null) {
            categoryId = catResId.getPath().toLowerCase(Locale.ROOT).intern();
            modId = catResId.getNamespace().toLowerCase(Locale.ROOT).intern();
        }

        try {
            Component catComp = category.getTitle();
            if (catComp != null) {
                categoryName = catComp.getString().intern();
            }
        } catch (Throwable ignored) {}

        ResourceLocation recipeId = null;
        try {
            recipeId = category.getRegistryName(recipe);
        } catch (Throwable ignored) {}

        if (recipeId != null) {
            modId = recipeId.getNamespace().toLowerCase(Locale.ROOT).intern();
        }

        // Layout collection
        JeiRecipeLayoutCollector collector = new JeiRecipeLayoutCollector();
        try {
            category.setRecipe(collector, recipe, JeiRecipeLayoutCollector.EmptyFocusGroup.INSTANCE);
        } catch (Throwable ignored) {}

        List<IngredientStack> inputs = new ArrayList<>();
        List<IngredientStack> outputs = new ArrayList<>();

        boolean isGT = GTCEuRecipeHandler.isGTRecipe(recipe) || (catResId != null && ("gtceu".equals(catResId.getNamespace()) || "start_core".equals(catResId.getNamespace())));
        if (isGT) {
            List<IngredientStack> gtIns = GTCEuRecipeHandler.extractGTRecipeContents(recipe, "inputs");
            List<IngredientStack> gtOuts = GTCEuRecipeHandler.extractGTRecipeContents(recipe, "outputs");
            if (gtIns != null && !gtIns.isEmpty()) inputs.addAll(gtIns);
            if (gtOuts != null && !gtOuts.isEmpty()) outputs.addAll(gtOuts);
        }

        if (inputs.isEmpty()) {
            inputs.addAll(collector.extractInputs());
        }
        if (outputs.isEmpty()) {
            outputs.addAll(collector.extractOutputs());
        }

        // Vanilla Recipe Fallback extraction (Smelting, Crafting, Blasting, Stonecutting, etc.)
        if (inputs.isEmpty() || outputs.isEmpty()) {
            JeiRecipeConverter.extractVanillaRecipeContents(recipe, inputs, outputs);
        }

        StringBuilder inSb = new StringBuilder();
        StringBuilder outSb = new StringBuilder();
        List<ResourceLocation> inputIds = new ArrayList<>();
        List<ResourceLocation> outputIds = new ArrayList<>();
        List<String> inputNames = new ArrayList<>();
        List<String> outputNames = new ArrayList<>();

        for (IngredientStack out : outputs) {
            indexIngredientStack(out, outSb, outputIds, outputNames);
        }

        for (IngredientStack in : inputs) {
            indexIngredientStack(in, inSb, inputIds, inputNames);
        }

        if (catResId != null) {
            inSb.append(getCategoryCatalystText(category, jeiRuntime));
        }

        if (catResId != null && com.gtceu.calcboard.api.util.ModCompatHelper.isCreateFamilyNamespace(catResId.getNamespace())) {
            inSb.append(" create:stress_units stress_units stress units su kinetic 스트레스");
            inputNames.add("stress units");
            inputNames.add("Stress Units");
            inputIds.add(ResourceLocation.tryParse("create:stress_units"));
        }

        String displayName = !outputs.isEmpty() ? outputs.get(0).getDisplayName() : (!categoryName.isEmpty() ? categoryName : "Recipe");

        ResourceLocation[] inArr = inputIds.isEmpty() ? null : inputIds.toArray(new ResourceLocation[0]);
        ResourceLocation[] outArr = outputIds.isEmpty() ? null : outputIds.toArray(new ResourceLocation[0]);
        String[] inNamesArr = inputNames.isEmpty() ? null : inputNames.toArray(new String[0]);
        String[] outNamesArr = outputNames.isEmpty() ? null : outputNames.toArray(new String[0]);

        boolean isSupported = ModAdapterRegistry.isCategorySupported(catResId);
        if (!isSupported && !modId.isEmpty()) {
            isSupported = ModAdapterRegistry.isRecipeSupported(modId, catResId);
        }

        JeiRecipeWrapper<T> wrapper = new JeiRecipeWrapper<>(category, recipe);

        return new SearchableRecipe(
                wrapper,
                recipeId,
                displayName,
                modId,
                categoryId,
                categoryName,
                inSb.toString().trim(),
                outSb.toString().trim(),
                inArr,
                outArr,
                inNamesArr,
                outNamesArr,
                isSupported
        );
    }

    private static String getCategoryCatalystText(IRecipeCategory<?> category, IJeiRuntime jeiRuntime) {
        if (category == null || category.getRecipeType() == null) return "";
        ResourceLocation catId = category.getRecipeType().getUid();
        return CATEGORY_CATALYST_TEXT_CACHE.computeIfAbsent(catId, id -> {
            StringBuilder sb = new StringBuilder();
            var cap = com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix.getInstance().getCapability(catId);
            if (cap != null) {
                for (ResourceLocation wsId : cap.availableWorkstations()) {
                    if (wsId != null) {
                        sb.append(' ').append(wsId.toString().toLowerCase(Locale.ROOT));
                        sb.append(' ').append(wsId.getPath().toLowerCase(Locale.ROOT));
                    }
                }
            }
            if (jeiRuntime != null) {
                try {
                    var catalystLookup = jeiRuntime.getRecipeManager().createRecipeCatalystLookup(category.getRecipeType());
                    if (catalystLookup != null) {
                        var catalysts = catalystLookup.get().toList();
                        for (var cat : catalysts) {
                            if (cat != null) {
                                ItemStack is = cat.getItemStack().orElse(ItemStack.EMPTY);
                                if (is.isEmpty() && cat.getIngredient() instanceof ItemStack s) {
                                    is = s;
                                }
                                if (!is.isEmpty()) {
                                    ResourceLocation iId = ForgeRegistries.ITEMS.getKey(is.getItem());
                                    if (iId != null) {
                                        sb.append(' ').append(iId.toString().toLowerCase(Locale.ROOT));
                                        sb.append(' ').append(iId.getPath().toLowerCase(Locale.ROOT));
                                        String name = is.getHoverName().getString();
                                        if (!name.isEmpty()) {
                                            sb.append(' ').append(name.toLowerCase(Locale.ROOT));
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            return sb.toString();
        });
    }

    private static void indexIngredientStack(IngredientStack stack, StringBuilder sb, List<ResourceLocation> ids, List<String> names) {
        if (stack == null || stack.getId() == null) return;
        ResourceLocation id = stack.getId();
        ids.add(id);

        StackSearchData data = STACK_DATA_CACHE.get(id);
        if (data == null) {
            String n = stack.getDisplayName() != null ? stack.getDisplayName() : "";
            StringBuilder ssb = new StringBuilder();
            ssb.append(' ').append(id.toString().toLowerCase(Locale.ROOT));
            ssb.append(' ').append(id.getPath().toLowerCase(Locale.ROOT));
            if (id.getPath().contains("stress_unit") || id.getPath().equals("cogwheel")) {
                ssb.append(" su stress units kinetic 스트레스");
            }
            if (!n.isEmpty()) {
                ssb.append(' ').append(n.toLowerCase(Locale.ROOT));
            }
            data = new StackSearchData(n, ssb.toString());
            STACK_DATA_CACHE.put(id, data);
        }

        sb.append(data.searchText());
        if (!data.name().isEmpty()) {
            names.add(data.name());
        }
    }

    public static void clearCaches() {
        STACK_DATA_CACHE.clear();
        CATEGORY_CATALYST_TEXT_CACHE.clear();
    }
}
