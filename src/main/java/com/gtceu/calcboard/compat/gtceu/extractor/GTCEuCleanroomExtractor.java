package com.gtceu.calcboard.compat.gtceu.extractor;

import com.gtceu.calcboard.api.property.IRecipePropertyExtractor;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Extracts Cleanroom type requirement for GTCEu recipes.
 */
public class GTCEuCleanroomExtractor implements IRecipePropertyExtractor {

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
        if (recipeDataTag != null && recipeDataTag.contains("cleanroom")) {
            String cleanroom = recipeDataTag.getString("cleanroom");
            if (cleanroom != null && !cleanroom.isEmpty()) {
                store.setById("cleanroom_type", cleanroom);
            }
        }
    }
}
