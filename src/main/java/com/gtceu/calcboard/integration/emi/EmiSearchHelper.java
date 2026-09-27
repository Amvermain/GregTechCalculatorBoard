package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.ParsedQuery;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.QueryTerm;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.bom.BoM;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
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

    public static ResourceLocation resolveContextualDefaultRecipeId(IngredientStack sourceStack, boolean targetIsFluid) {
        try {
            ResourceLocation id = sourceStack.getId();
            if (id != null) {
                if (targetIsFluid) {
                    var fluid = ForgeRegistries.FLUIDS.getValue(id);
                    if (fluid != null) {
                        var emiStack = EmiStack.of(fluid);
                        var def = BoM.getRecipe(emiStack);
                        return def != null ? def.getId() : null;
                    }
                } else {
                    var item = ForgeRegistries.ITEMS.getValue(id);
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
                if (isOutputDefaultMatch(out, er, hasQuery, parsedQuery)) {
                    return true;
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
                if (isOutputDefaultMatch(out, er, hasQuery, searchBoxValue)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private static boolean isOutputDefaultMatch(EmiStack out, EmiRecipe er, boolean hasQuery, ParsedQuery parsedQuery) {
        if (!matchesDefault(out, er)) return false;
        if (!hasQuery || parsedQuery == null) return true;
        return matchesParsedQuery(out, parsedQuery);
    }

    private static boolean isOutputDefaultMatch(EmiStack out, EmiRecipe er, boolean hasQuery, String searchBoxValue) {
        if (!matchesDefault(out, er)) return false;
        String q = searchBoxValue != null ? searchBoxValue.trim().toLowerCase(Locale.ROOT) : "";
        if (q.isEmpty()) return true;
        return matchesQueryString(out, q);
    }

    private static boolean matchesDefault(EmiStack out, EmiRecipe er) {
        EmiRecipe def = BoM.getRecipe(out);
        return (def != null && (def.equals(er) || (def.getId() != null && def.getId().equals(er.getId()))))
                || BoM.isDefaultRecipe(out, er);
    }

    private static boolean matchesParsedQuery(EmiStack out, ParsedQuery parsedQuery) {
        String outName = out.getName() != null ? out.getName().getString().toLowerCase(Locale.ROOT) : "";
        String outId = out.getId() != null ? out.getId().toString().toLowerCase(Locale.ROOT) : "";
        String outPath = out.getId() != null ? out.getId().getPath().toLowerCase(Locale.ROOT) : "";
        for (var group : parsedQuery.orGroups()) {
            if (matchesAnyTerm(group.terms(), outName, outId, outPath)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyTerm(List<QueryTerm> terms, String outName, String outId, String outPath) {
        for (var term : terms) {
            if (term.negated()) continue;
            String t = term.text();
            if (outName.contains(t) || outId.contains(t) || outPath.contains(t)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesQueryString(EmiStack out, String query) {
        String outName = out.getName() != null ? out.getName().getString().toLowerCase(Locale.ROOT) : "";
        String outId = out.getId() != null ? out.getId().toString().toLowerCase(Locale.ROOT) : "";
        String outPath = out.getId() != null ? out.getId().getPath().toLowerCase(Locale.ROOT) : "";
        return outName.contains(query) || outId.contains(query) || outPath.contains(query);
    }
}
