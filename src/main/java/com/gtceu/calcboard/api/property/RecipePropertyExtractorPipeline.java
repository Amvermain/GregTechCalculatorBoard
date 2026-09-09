package com.gtceu.calcboard.api.property;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Registry and execution pipeline for {@link IRecipePropertyExtractor}.
 */
public final class RecipePropertyExtractorPipeline {

    private static final List<IRecipePropertyExtractor> EXTRACTORS = new ArrayList<>();

    private RecipePropertyExtractorPipeline() {}

    public static synchronized void register(IRecipePropertyExtractor extractor) {
        if (extractor != null && !EXTRACTORS.contains(extractor)) {
            EXTRACTORS.add(extractor);
        }
    }

    public static List<IRecipePropertyExtractor> getExtractors() {
        return Collections.unmodifiableList(EXTRACTORS);
    }

    /**
     * Executes all matching extractors on the given recipe data and populates the store.
     */
    public static void extractAll(Object backingRecipe, CompoundTag recipeDataTag, ResourceLocation categoryId, NodePropertyStore store) {
        if (store == null) return;
        for (IRecipePropertyExtractor extractor : EXTRACTORS) {
            try {
                if (extractor.matches(backingRecipe, categoryId)) {
                    extractor.extract(backingRecipe, recipeDataTag, categoryId, store);
                }
            } catch (Throwable ignored) {}
        }
    }
}
