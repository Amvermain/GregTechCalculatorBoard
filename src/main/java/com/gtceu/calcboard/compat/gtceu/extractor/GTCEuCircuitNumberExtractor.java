package com.gtceu.calcboard.compat.gtceu.extractor;

import com.gtceu.calcboard.api.property.IRecipePropertyExtractor;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.property.NodePropertyStore;
import com.gtceu.calcboard.compat.gtceu.GTCEuRecipeDetailExtractor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

/**
 * Extracts Programmed Circuit configuration number for GTCEu recipes.
 */
public class GTCEuCircuitNumberExtractor implements IRecipePropertyExtractor {

    @Override
    public String getModId() {
        return "gtceu";
    }

    @Override
    public boolean matches(Object backingRecipe, ResourceLocation categoryId) {
        if (categoryId != null && "gtceu".equals(categoryId.getNamespace())) {
            return true;
        }
        return backingRecipe != null && com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.isGTRecipe(backingRecipe);
    }

    @Override
    public void extract(Object backingRecipe, CompoundTag recipeDataTag, ResourceLocation categoryId, NodePropertyStore store) {
        int circuit = GTCEuRecipeDetailExtractor.extractCircuitNumber(backingRecipe);
        if (circuit >= 0) {
            store.set(NodeProperties.CIRCUIT_NUMBER, circuit);
        }
    }
}
