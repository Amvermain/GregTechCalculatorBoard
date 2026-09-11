package com.gtceu.calcboard.api.spi.extension;

import com.gtceu.calcboard.api.model.CompoundRecipeBuilder;
import com.gtceu.calcboard.api.model.RecipeDetails;
import net.minecraft.resources.ResourceLocation;

/**
 * Provider interface for compound recipes, recipe adaptation, and multi-step recipe cluster generation.
 */
public interface ICompoundRecipeProvider extends IModExtension {

    default boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, RecipeDetails details) {
        return false;
    }

    default CompoundRecipeBuilder.CompoundCluster buildCompoundRecipe(
            Object recipeObj,
            Object backingRecipe,
            ResourceLocation preferredWorkstation,
            double startX,
            double startY
    ) {
        return null;
    }
}
