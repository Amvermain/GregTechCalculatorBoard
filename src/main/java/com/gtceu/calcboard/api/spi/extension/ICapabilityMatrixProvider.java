package com.gtceu.calcboard.api.spi.extension;

import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import net.minecraft.world.item.Item;

import java.util.Set;

/**
 * Provider interface for category capability matrix enrichment and synthetic EMI recipe registration.
 */
public interface ICapabilityMatrixProvider extends IModExtension {

    default void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
    }

    default void registerSyntheticEmiRecipes(Object emiRegistry, Object emiCategory, Set<Item> activeRecipeItems) {
    }
}
