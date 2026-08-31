package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.model.CompoundRecipeBuilder;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.resources.ResourceLocation;

/**
 * Deterministic runtime detector for multi-step / layered recipes.
 * Dispatches to registered {@link IModAdapter} instances using official mod APIs and runtime reflection.
 */
public final class EmiStepRecipeDetector {

    private EmiStepRecipeDetector() {}

    /**
     * Attempts to detect if the recipe is an official multi-step / progressive recipe via backend mod APIs.
     * Returns a bonded {@link CompoundRecipeBuilder.CompoundCluster} if confirmed, or {@code null} otherwise.
     */
    public static CompoundRecipeBuilder.CompoundCluster tryDetectAndBuild(
            Object recipeObj,
            ResourceLocation preferredWorkstation,
            double startX,
            double startY
    ) {
        if (!(recipeObj instanceof EmiRecipe recipe)) return null;
        return tryDetectAndBuild(recipe, preferredWorkstation, startX, startY);
    }

    public static CompoundRecipeBuilder.CompoundCluster tryDetectAndBuild(
            EmiRecipe recipe,
            ResourceLocation preferredWorkstation,
            double startX,
            double startY
    ) {
        if (recipe == null) return null;

        Object backing = EmiRecipeConverter.unwrapBackingRecipe(recipe);
        ResourceLocation catId = recipe.getCategory() != null ? recipe.getCategory().getId() : null;

        for (IModAdapter adapter : ModAdapterRegistry.getAdapters()) {
            if (adapter.isLoaded() && (catId == null || adapter.handlesCategory(catId))) {
                CompoundRecipeBuilder.CompoundCluster cluster = adapter.buildCompoundRecipe(
                        recipe, backing, preferredWorkstation, startX, startY
                );
                if (cluster != null && !cluster.nodes().isEmpty()) {
                    return cluster;
                }
            }
        }

        return null;
    }
}
