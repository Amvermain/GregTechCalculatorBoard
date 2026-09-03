package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Isolated indexing implementation for EMI recipes.
 * Only loaded when EMI is present to prevent NoClassDefFoundError.
 */
public final class EmiRecipeSearchIndexer {

    private record StackSearchData(String name, String searchText) {}
    private static final Map<ResourceLocation, String> STACK_NAME_CACHE = new ConcurrentHashMap<>(4096);
    private static final Map<ResourceLocation, StackSearchData> STACK_DATA_CACHE = new ConcurrentHashMap<>(4096);
    private static final Map<EmiRecipeCategory, String> CATEGORY_WS_TEXT_CACHE = new ConcurrentHashMap<>();
    private static final Map<EmiRecipeCategory, String> CATEGORY_NAME_CACHE = new ConcurrentHashMap<>();

    private EmiRecipeSearchIndexer() {}

    public static SearchableRecipe buildIndex(Object recipe) {
        if (!(recipe instanceof EmiRecipe er)) return null;

        String displayName = extractDisplayName(er);
        String modId = "";
        String categoryId = "";
        String categoryName = "";

        ResourceLocation recipeId = er.getId();
        if (recipeId != null) {
            modId = recipeId.getNamespace().toLowerCase(Locale.ROOT).intern();
        }

        EmiRecipeCategory cat = er.getCategory();
        if (cat != null) {
            if (cat.getId() != null) {
                categoryId = cat.getId().getPath().toLowerCase(Locale.ROOT).intern();
                if (modId.isEmpty()) {
                    modId = cat.getId().getNamespace().toLowerCase(Locale.ROOT).intern();
                }
            }
            categoryName = CATEGORY_NAME_CACHE.computeIfAbsent(cat, c -> {
                try {
                    Component catComp = c.getName();
                    if (catComp != null) {
                        return catComp.getString().intern();
                    }
                } catch (Throwable ignored) {}
                return "";
            });
        }

        StringBuilder inSb = new StringBuilder();
        StringBuilder outSb = new StringBuilder();
        List<ResourceLocation> inputIds = new ArrayList<>();
        List<ResourceLocation> outputIds = new ArrayList<>();
        List<String> inputNames = new ArrayList<>();
        List<String> outputNames = new ArrayList<>();

        if (er.getOutputs() != null) {
            for (EmiStack out : er.getOutputs()) {
                indexStackCompact(out, outSb, outputIds, outputNames);
            }
        }

        if (er.getInputs() != null) {
            for (EmiIngredient in : er.getInputs()) {
                if (in != null && in.getEmiStacks() != null) {
                    for (EmiStack stack : in.getEmiStacks()) {
                        indexStackCompact(stack, inSb, inputIds, inputNames);
                    }
                }
            }
        }

        if (er instanceof KineticGenerationEmiRecipe kg) {
            if (kg.getDisplayName() != null && !kg.getDisplayName().isEmpty()) {
                displayName = kg.getDisplayName();
            }
            if (kg.getMachineIconId() != null) {
                modId = kg.getMachineIconId().getNamespace().toLowerCase(Locale.ROOT).intern();
                outputIds.add(kg.getMachineIconId());
                outSb.append(' ').append(kg.getMachineIconId().toString().toLowerCase(Locale.ROOT));
                outSb.append(' ').append(kg.getMachineIconId().getPath().toLowerCase(Locale.ROOT));
            }
            if (kg.getDisplayName() != null) {
                outputNames.add(kg.getDisplayName());
                outSb.append(' ').append(kg.getDisplayName().toLowerCase(Locale.ROOT));
            }
            for (com.gtceu.calcboard.api.model.IngredientStack out : kg.getOutputStacks()) {
                if (out != null && out.getId() != null) {
                    outputIds.add(out.getId());
                    if (out.getDisplayName() != null) {
                        outputNames.add(out.getDisplayName());
                        outSb.append(' ').append(out.getDisplayName().toLowerCase(Locale.ROOT));
                    }
                    outSb.append(' ').append(out.getId().toString().toLowerCase(Locale.ROOT));
                    outSb.append(' ').append(out.getId().getPath().toLowerCase(Locale.ROOT));
                    if (out.isStressUnit()) {
                        outSb.append(" su stress units kinetic 스트레스");
                        outputNames.add("stress units");
                        outputNames.add("Stress Units");
                    }
                }
            }
            for (com.gtceu.calcboard.api.model.IngredientStack in : kg.getInputStacks()) {
                if (in != null && in.getId() != null) {
                    inputIds.add(in.getId());
                    if (in.getDisplayName() != null) {
                        inputNames.add(in.getDisplayName());
                        inSb.append(' ').append(in.getDisplayName().toLowerCase(Locale.ROOT));
                    }
                    inSb.append(' ').append(in.getId().toString().toLowerCase(Locale.ROOT));
                    inSb.append(' ').append(in.getId().getPath().toLowerCase(Locale.ROOT));
                    if (in.isStressUnit()) {
                        inSb.append(" su stress units kinetic 스트레스");
                        inputNames.add("stress units");
                        inputNames.add("Stress Units");
                    }
                }
            }
        }

        if (cat != null && cat.getId() != null && com.gtceu.calcboard.api.util.ModCompatHelper.isCreateFamilyNamespace(cat.getId().getNamespace())) {
            inSb.append(" create:stress_units stress_units stress units su kinetic 스트레스");
            inputNames.add("stress units");
            inputNames.add("Stress Units");
            inputIds.add(ResourceLocation.tryParse("create:stress_units"));
        }

        if (cat != null && cat.getId() != null && (cat.getId().getPath().contains("boiler") || cat.getId().getPath().contains("turbine") || cat.getId().getPath().contains("generator"))) {
            try {
                EmiRecipeConverter.RecipeDetails details = EmiRecipeConverter.extractRecipeDetails(er, null);
                if (details != null) {
                    if (details.overrideOutputs && !details.customOutputs.isEmpty()) {
                        for (com.gtceu.calcboard.api.model.IngredientStack cos : details.customOutputs) {
                            if (cos != null && cos.getId() != null) {
                                outputIds.add(cos.getId());
                                if (cos.getDisplayName() != null) {
                                    outputNames.add(cos.getDisplayName());
                                    outSb.append(' ').append(cos.getDisplayName().toLowerCase(Locale.ROOT));
                                }
                                outSb.append(' ').append(cos.getId().toString().toLowerCase(Locale.ROOT));
                                outSb.append(' ').append(cos.getId().getPath().toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                    if (!details.extraInputs.isEmpty()) {
                        for (com.gtceu.calcboard.api.model.IngredientStack ein : details.extraInputs) {
                            if (ein != null && ein.getId() != null) {
                                inputIds.add(ein.getId());
                                if (ein.getDisplayName() != null) {
                                    inputNames.add(ein.getDisplayName());
                                    inSb.append(' ').append(ein.getDisplayName().toLowerCase(Locale.ROOT));
                                }
                                inSb.append(' ').append(ein.getId().toString().toLowerCase(Locale.ROOT));
                                inSb.append(' ').append(ein.getId().getPath().toLowerCase(Locale.ROOT));
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        if (cat != null) {
            inSb.append(getCategoryWorkstationText(cat));
        }

        ResourceLocation[] inArr = inputIds.isEmpty() ? null : inputIds.toArray(new ResourceLocation[0]);
        ResourceLocation[] outArr = outputIds.isEmpty() ? null : outputIds.toArray(new ResourceLocation[0]);
        String[] inNamesArr = inputNames.isEmpty() ? null : inputNames.toArray(new String[0]);
        String[] outNamesArr = outputNames.isEmpty() ? null : outputNames.toArray(new String[0]);

        ResourceLocation catResId = (cat != null) ? cat.getId() : null;
        boolean isSupported = (er instanceof KineticGenerationEmiRecipe)
                || com.gtceu.calcboard.compat.ModAdapterRegistry.isCategorySupported(catResId);
        if (!isSupported && modId != null && !modId.isEmpty()) {
            isSupported = com.gtceu.calcboard.compat.ModAdapterRegistry.isRecipeSupported(modId, catResId);
        }

        return new SearchableRecipe(
                er,
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

    private static String getCategoryWorkstationText(EmiRecipeCategory cat) {
        if (cat == null) return "";
        return CATEGORY_WS_TEXT_CACHE.computeIfAbsent(cat, c -> {
            StringBuilder sb = new StringBuilder();
            try {
                var rm = dev.emi.emi.api.EmiApi.getRecipeManager();
                if (rm != null) {
                    var workstations = rm.getWorkstations(c);
                    if (workstations != null) {
                        for (EmiIngredient wsIng : workstations) {
                            if (wsIng != null && wsIng.getEmiStacks() != null) {
                                for (EmiStack wsStack : wsIng.getEmiStacks()) {
                                    if (wsStack != null) {
                                        if (wsStack.getId() != null) {
                                            sb.append(' ').append(wsStack.getId().toString().toLowerCase(Locale.ROOT));
                                            sb.append(' ').append(wsStack.getId().getPath().toLowerCase(Locale.ROOT));
                                        }
                                        try {
                                            if (wsStack.getName() != null) {
                                                sb.append(' ').append(wsStack.getName().getString().toLowerCase(Locale.ROOT));
                                            }
                                        } catch (Throwable ignored) {}
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}
            return sb.toString();
        });
    }

    public static String getStackDisplayName(EmiStack stack) {
        if (stack == null) return "";
        ResourceLocation id = stack.getId();
        if (id == null) return "";
        return STACK_NAME_CACHE.computeIfAbsent(id, k -> {
            try {
                Component comp = stack.getName();
                if (comp != null) return comp.getString();
            } catch (Throwable ignored) {}
            return id.getPath();
        });
    }

    private static void indexStackCompact(EmiStack stack, StringBuilder sb, List<ResourceLocation> ids, List<String> names) {
        if (stack == null) return;
        ResourceLocation id = stack.getId();
        if (id == null) return;

        ids.add(id);
        StackSearchData data = STACK_DATA_CACHE.get(id);
        if (data == null) {
            String n = getStackDisplayName(stack);

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

    private static String extractDisplayName(EmiRecipe recipe) {
        try {
            if (recipe.getOutputs() != null && !recipe.getOutputs().isEmpty()) {
                var firstOut = recipe.getOutputs().get(0);
                if (firstOut != null) {
                    String n = getStackDisplayName(firstOut);
                    if (!n.isEmpty()) return n;
                }
            }
        } catch (Throwable ignored) {}
        if (recipe.getId() != null) {
            String path = recipe.getId().getPath();
            if (path.contains("/")) {
                path = path.substring(path.lastIndexOf('/') + 1);
            }
            return EmiRecipeConverter.formatName(path);
        }
        return "Unknown Recipe";
    }

    public static void clearCaches() {
        STACK_NAME_CACHE.clear();
        STACK_DATA_CACHE.clear();
        CATEGORY_WS_TEXT_CACHE.clear();
        CATEGORY_NAME_CACHE.clear();
    }
}
