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

    @Override
    public String displayName() {
        ItemStack is = resolveItemStack();
        if (!is.isEmpty()) {
            try {
                String hoverName = is.getHoverName().getString();
                if (MultiblockBOMSummary.BOMItemEntry.isValidDisplayName(hoverName)) {
                    return hoverName;
                }
            } catch (Throwable ignored) {}
        }
        if (itemId != null) {
            try {
                var blk = ForgeRegistries.BLOCKS.getValue(itemId);
                if (blk != null && blk != net.minecraft.world.level.block.Blocks.AIR) {
                    String bName = blk.getName().getString();
                    if (MultiblockBOMSummary.BOMItemEntry.isValidDisplayName(bName)) {
                        return bName;
                    }
                }
                String blockTrans = net.minecraft.network.chat.Component.translatable(itemId.toLanguageKey("block")).getString();
                if (MultiblockBOMSummary.BOMItemEntry.isValidDisplayName(blockTrans)) {
                    return blockTrans;
                }
                String itemTrans = net.minecraft.network.chat.Component.translatable(itemId.toLanguageKey("item")).getString();
                if (MultiblockBOMSummary.BOMItemEntry.isValidDisplayName(itemTrans)) {
                    return itemTrans;
                }
            } catch (Throwable ignored) {}
        }
        if (MultiblockBOMSummary.BOMItemEntry.isValidDisplayName(this.displayName)) {
            return this.displayName;
        }
        if (itemId != null) {
            return MultiblockStructureCatalog.formatMachineName(itemId.getPath());
        }
        return "Unknown";
    }
}
