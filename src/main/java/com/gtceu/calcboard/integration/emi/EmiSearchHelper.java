package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.ParsedQuery;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.Locale;

/**
 * Isolated search helper for EMI-specific query operations.
 * Only loaded when EMI is present.
 */
public final class EmiSearchHelper {

    private EmiSearchHelper() {}

    public static ResourceLocation getRecipeId(Object recipeObj) {
        if (recipeObj instanceof EmiRecipe er) {
            return er.getId();
        }
        return null;
    }

    public static boolean isKineticGenerator(Object recipeObj) {
        if (recipeObj instanceof KineticGenerationEmiRecipe kg) {
            return kg.isGenerator();
        }
        return false;
    }

    public static ResourceLocation resolveContextualDefaultRecipeId(IngredientStack sourceStack, boolean targetIsFluid) {
        try {
            ResourceLocation id = sourceStack.getId();
            if (id != null) {
                if (targetIsFluid) {
                    var fluid = BuiltInRegistries.FLUID.get(id);
                    if (fluid != null) {
                        var emiStack = EmiStack.of(fluid);
                        var def = BoM.getRecipe(emiStack);
                        return def != null ? def.getId() : null;
                    }
                } else {
                    var item = BuiltInRegistries.ITEM.get(id);
                    if (item != null) {
                        var emiStack = EmiStack.of(item);
                        var def = BoM.getRecipe(emiStack);
                        return def != null ? def.getId() : null;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isDefaultRecipe(Object recipeObj, boolean hasContext, ResourceLocation finalContextualDefaultId, boolean hasQuery, ParsedQuery parsedQuery) {
        if (!(recipeObj instanceof EmiRecipe er)) return false;
        if (hasContext && finalContextualDefaultId != null) {
            return er.getId() != null && er.getId().equals(finalContextualDefaultId);
        }
        try {
            for (var out : er.getOutputs()) {
                EmiRecipe def = BoM.getRecipe(out);
                boolean matchesDef = (def != null && (def.equals(er) || (def.getId() != null && def.getId().equals(er.getId()))))
                        || BoM.isDefaultRecipe(out, er);
                if (matchesDef) {
                    if (hasQuery && parsedQuery != null) {
                        String outName = out.getName() != null ? out.getName().getString().toLowerCase(Locale.ROOT) : "";
                        String outId = out.getId() != null ? out.getId().toString().toLowerCase(Locale.ROOT) : "";
                        String outPath = out.getId() != null ? out.getId().getPath().toLowerCase(Locale.ROOT) : "";
                        for (var group : parsedQuery.orGroups()) {
                            for (var term : group.terms()) {
                                if (!term.negated()) {
                                    String t = term.text();
                                    if (outName.contains(t) || outId.contains(t) || outPath.contains(t)) {
                                        return true;
                                    }
                                }
                            }
                        }
                    } else {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isDefaultRecipe(Object recipeObj, boolean hasContext, ResourceLocation finalContextualDefaultId, boolean hasQuery, String searchBoxValue) {
        if (!(recipeObj instanceof EmiRecipe er)) return false;
        if (hasContext && finalContextualDefaultId != null) {
            return er.getId() != null && er.getId().equals(finalContextualDefaultId);
        }
        try {
            for (var out : er.getOutputs()) {
                EmiRecipe def = BoM.getRecipe(out);
                boolean matchesDef = (def != null && (def.equals(er) || (def.getId() != null && def.getId().equals(er.getId()))))
                        || BoM.isDefaultRecipe(out, er);
                if (matchesDef) {
                    String q = searchBoxValue != null ? searchBoxValue.trim().toLowerCase(Locale.ROOT) : "";
                    if (q.isEmpty()) {
                        return true;
                    }
                    String outName = out.getName() != null ? out.getName().getString().toLowerCase(Locale.ROOT) : "";
                    String outId = out.getId() != null ? out.getId().toString().toLowerCase(Locale.ROOT) : "";
                    String outPath = out.getId() != null ? out.getId().getPath().toLowerCase(Locale.ROOT) : "";
                    if (outName.contains(q) || outId.contains(q) || outPath.contains(q)) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
