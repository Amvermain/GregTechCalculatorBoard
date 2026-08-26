package com.gtceu.calcboard.api.bom;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Represents a single component part in a multiblock structure recipe.
 */
public record MultiblockStructurePart(
    ResourceLocation itemId,
    String displayName,
    int amount,
    PartCategory category
) {
    public ItemStack resolveItemStack() {
        if (itemId == null) return ItemStack.EMPTY;
        try {
            var item = ForgeRegistries.ITEMS.getValue(itemId);
            if (item != null) {
                return new ItemStack(item);
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }
}
