package com.gtceu.calcboard.api.spi.viewer;

import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Service Provider Interface for external recipe viewer abstractions (EMI, JEI, REI).
 * Enables core domain catalogs and BOM builders to discover recipes, structures,
 * and category workstations without coupling to external viewer APIs.
 */
public interface IRecipeViewerBridge {

    String getViewerId();

    boolean isAvailable();

    boolean isRecipeBakingComplete();

    default void discoverMultiblockStructures(Consumer<MultiblockStructureDef> consumer) {
    }

    default void discoverCategoryWorkstations(BiConsumer<ResourceLocation, ResourceLocation> workstationConsumer) {
    }

    default void discoverRecipeOutputs(Consumer<ItemStack> outputConsumer) {
    }

    default boolean extractRecipeOutputs(Object recipe, List<ItemStack> outputs) {
        return false;
    }

    default void discoverMultiblockRecipes(BiConsumer<ResourceLocation, Object> multiblockRecipeConsumer) {
    }

    default void discoverMultiblockControllers(Consumer<ResourceLocation> controllerConsumer) {
    }

    default ResourceLocation findMachineIcon(Object recipe) {
        return null;
    }

    default void invalidateTextCaches() {
    }
}
