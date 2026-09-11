package com.gtceu.calcboard.compat.create.extractor;

import com.gtceu.calcboard.api.property.IRecipePropertyExtractor;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Extracts default kinetic RPM for Create family recipes.
 */
public class CreateKineticRpmExtractor implements IRecipePropertyExtractor {

    @Override
    public String getModId() {
        return "create";
    }

    @Override
    public boolean matches(Object backingRecipe, ResourceLocation categoryId) {
        return categoryId != null && ModCompatHelper.isCreateFamilyNamespace(categoryId.getNamespace());
    }

    @Override
    public void extract(Object backingRecipe, CompoundTag recipeDataTag, ResourceLocation categoryId, NodePropertyStore store) {
        if (!store.hasById("kinetic_rpm")) {
            store.setById("kinetic_rpm", 32);
        }
    }
}
