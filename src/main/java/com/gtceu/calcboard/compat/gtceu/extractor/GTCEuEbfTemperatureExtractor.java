package com.gtceu.calcboard.compat.gtceu.extractor;

import com.gtceu.calcboard.api.property.IRecipePropertyExtractor;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Extracts Electric Blast Furnace (EBF) required temperature for GTCEu recipes.
 */
public class GTCEuEbfTemperatureExtractor implements IRecipePropertyExtractor {

    @Override
    public String getModId() {
        return "gtceu";
    }

    @Override
    public boolean matches(Object backingRecipe, ResourceLocation categoryId) {
        return (categoryId != null && categoryId.getNamespace().equals("gtceu"))
                || (backingRecipe != null && backingRecipe.getClass().getName().contains("GTRecipe"));
    }

    @Override
    public void extract(Object backingRecipe, CompoundTag recipeDataTag, ResourceLocation categoryId, NodePropertyStore store) {
        int temp = 0;
        if (recipeDataTag != null) {
            if (recipeDataTag.contains("ebf_temp")) temp = recipeDataTag.getInt("ebf_temp");
            else if (recipeDataTag.contains("temp")) temp = recipeDataTag.getInt("temp");
            else if (recipeDataTag.contains("temperature")) temp = recipeDataTag.getInt("temperature");
        }
        if (temp > 0) {
            store.setById("ebf_temperature", temp);
        }
    }
}
