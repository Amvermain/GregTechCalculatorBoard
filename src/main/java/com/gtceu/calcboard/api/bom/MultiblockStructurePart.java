package com.gtceu.calcboard.api.bom;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

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
            var item = BuiltInRegistries.ITEM.get(itemId);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                return new ItemStack(item);
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }
}
