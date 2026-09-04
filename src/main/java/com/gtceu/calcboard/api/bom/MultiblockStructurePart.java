package com.gtceu.calcboard.api.bom;

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
        return BOMDisplayNameResolver.resolveItemStack(itemId);
    }

    @Override
    public String displayName() {
        return BOMDisplayNameResolver.resolve(itemId, this.displayName);
    }
}
