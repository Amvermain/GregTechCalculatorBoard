package com.gtceu.calcboard.api.property;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Pluggable recipe property extractor interface for {@link RecipePropertyExtractorPipeline}.
 * Implementations inspect mod-specific recipe backing data (e.g. GTRecipe, Create ProcessingRecipe)
 * and populate special machine properties (EBF temperature, Fusion ignition EU, RPM, Cleanroom, etc.)
 * into the {@link NodePropertyStore}.
 */
public interface IRecipePropertyExtractor {

    /**
     * Target mod ID or namespace.
     */
    String getModId();

    /**
     * Checks whether this extractor matches the given category or backing recipe.
     */
    boolean matches(Object backingRecipe, ResourceLocation categoryId);

    /**
     * Extracts properties and injects them into the {@link NodePropertyStore}.
     */
    void extract(Object backingRecipe, CompoundTag recipeDataTag, ResourceLocation categoryId, NodePropertyStore store);
}
