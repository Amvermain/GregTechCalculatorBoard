package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.model.CompoundRecipeBuilder;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.GTCEuLayeredRecipeExtractor;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.resources.ResourceLocation;

/**
 * Deterministic runtime detector for multi-step / layered recipes.
 * Leverages official mod APIs (GTCEu LayeredRecipeHelper, Create SequencedAssembly) without view/pixel heuristics.
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
        if (backing == null) return null;

        String machineName = preferredWorkstation != null ? EmiRecipeConverter.formatName(preferredWorkstation.getPath()) : "Machine";
        if (recipe.getCategory() != null && recipe.getCategory().getId() != null) {
            machineName = EmiRecipeConverter.formatName(recipe.getCategory().getId().getPath());
        }

        ResourceLocation icon = preferredWorkstation != null ? preferredWorkstation : EmiRecipeConverter.findMachineIcon(recipe);
        EmiRecipeConverter.RecipeDetails details = EmiRecipeConverter.extractRecipeDetails(recipe, preferredWorkstation);
        GTVoltageTier tier = details.tier;

        // 1. GTCEu / StarT Fork Layered Recipe Helper (Official API)
        if (GTCEuLayeredRecipeExtractor.isLayeredRecipe(backing)) {
            CompoundRecipeBuilder.CompoundCluster cluster = GTCEuLayeredRecipeExtractor.buildCompoundCluster(
                    backing, machineName, icon, tier, startX, startY
            );
            if (cluster != null && !cluster.nodes().isEmpty()) {
                return cluster;
            }
        }

        return null;
    }
}
