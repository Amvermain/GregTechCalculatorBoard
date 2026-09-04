package com.gtceu.calcboard.api.catalog;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

import java.util.function.Consumer;

/**
 * Service Provider Interface (SPI) for abstracting client-side level recipes and language environments safely.
 */
public interface ILevelRecipeProvider {

    boolean isRecipeBakingComplete();

    void collectClientRecipes(Consumer<ItemStack> collector);

    ItemStack getRecipeResultItem(Recipe<?> recipe);

    String getSelectedLanguage();
}
