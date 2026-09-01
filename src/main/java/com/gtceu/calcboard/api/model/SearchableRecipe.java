package com.gtceu.calcboard.api.model;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Domain record model representing an indexed recipe for search and filtering.
 */
public record SearchableRecipe(
        Object recipe,
        ResourceLocation recipeId,
        String displayName,
        String modId,
        String categoryId,
        String categoryName,
        String inputIndex,
        String outputIndex,
        ResourceLocation[] inputIds,
        ResourceLocation[] outputIds,
        String[] inputNames,
        String[] outputNames,
        boolean isSupported
) {
    public SearchableRecipe(
            Object recipe,
            String displayName,
            String modId,
            String categoryId,
            String categoryName,
            String inputIndex,
            String outputIndex,
            ResourceLocation[] inputIds,
            ResourceLocation[] outputIds,
            String[] inputNames,
            String[] outputNames,
            boolean isSupported
    ) {
        this(recipe, null, displayName, modId, categoryId, categoryName, inputIndex, outputIndex, inputIds, outputIds, inputNames, outputNames, isSupported);
    }

    public SearchableRecipe(
            Object recipe,
            String displayName,
            String modId,
            String categoryId,
            String categoryName,
            String inputIndex,
            String outputIndex,
            ResourceLocation[] inputIds,
            ResourceLocation[] outputIds,
            String[] inputNames,
            String[] outputNames
    ) {
        this(recipe, null, displayName, modId, categoryId, categoryName, inputIndex, outputIndex, inputIds, outputIds, inputNames, outputNames, true);
    }

    public SearchableRecipe(
            Object recipe,
            String displayName,
            String modId,
            String categoryId,
            String categoryName,
            String inputIndex,
            String outputIndex
    ) {
        this(recipe, null, displayName, modId, categoryId, categoryName, inputIndex, outputIndex, null, null, null, null, true);
    }

    public boolean hasExactInput(ResourceLocation id) {
        if (id == null || inputIds == null) return false;
        for (ResourceLocation rid : inputIds) {
            if (id.equals(rid)) return true;
        }
        return false;
    }

    public boolean hasExactOutput(ResourceLocation id) {
        if (id == null || outputIds == null) return false;
        for (ResourceLocation rid : outputIds) {
            if (id.equals(rid)) return true;
        }
        return false;
    }

    public boolean hasInputPath(String path) {
        if (path == null || inputIds == null) return false;
        for (ResourceLocation rid : inputIds) {
            if (path.equalsIgnoreCase(rid.getPath())) return true;
        }
        return false;
    }

    public boolean hasOutputPath(String path) {
        if (path == null || outputIds == null) return false;
        for (ResourceLocation rid : outputIds) {
            if (path.equalsIgnoreCase(rid.getPath())) return true;
        }
        return false;
    }

    public boolean hasExactInputName(String name) {
        if (name == null || inputNames == null) return false;
        for (String s : inputNames) {
            if (name.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    public boolean hasExactOutputName(String name) {
        if (name == null || outputNames == null) return false;
        for (String s : outputNames) {
            if (name.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    public boolean hasInput(String token) {
        if (token == null || token.isEmpty()) return false;
        return inputIndex != null && inputIndex.contains(token.toLowerCase(Locale.ROOT));
    }

    public boolean hasOutput(String token) {
        if (token == null || token.isEmpty()) return false;
        return outputIndex != null && outputIndex.contains(token.toLowerCase(Locale.ROOT));
    }

    public String inputSearchIndex() {
        return inputIndex != null ? inputIndex : "";
    }

    public String outputSearchIndex() {
        return outputIndex != null ? outputIndex : "";
    }
}
