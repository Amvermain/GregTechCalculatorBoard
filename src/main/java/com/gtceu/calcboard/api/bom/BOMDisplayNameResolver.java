package com.gtceu.calcboard.api.bom;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Utility for resolving deterministic display names and ItemStacks for multiblock BOM entries.
 */
public final class BOMDisplayNameResolver {

    private BOMDisplayNameResolver() {}

    public static ItemStack resolveItemStack(ResourceLocation itemId) {
        if (itemId == null) return ItemStack.EMPTY;
        try {
            if (ForgeRegistries.ITEMS != null) {
                var item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item != null) {
                    return new ItemStack(item);
                }
            }
        } catch (Throwable ignored) {}
        return ItemStack.EMPTY;
    }

    public static String resolve(ResourceLocation itemId, String fallbackName) {
        ItemStack is = resolveItemStack(itemId);
        if (!is.isEmpty()) {
            try {
                String hoverName = is.getHoverName().getString();
                if (isValidDisplayName(hoverName)) {
                    return hoverName;
                }
            } catch (Throwable ignored) {}
        }

        String blockName = resolveFromBlockRegistry(itemId);
        if (blockName != null) {
            return blockName;
        }

        if (itemId != null) {
            try {
                String blockTrans = Component.translatable(itemId.toLanguageKey("block")).getString();
                if (isValidDisplayName(blockTrans)) {
                    return blockTrans;
                }
                String itemTrans = Component.translatable(itemId.toLanguageKey("item")).getString();
                if (isValidDisplayName(itemTrans)) {
                    return itemTrans;
                }
            } catch (Throwable ignored) {}
        }

        if (isValidDisplayName(fallbackName)) {
            return fallbackName;
        }

        if (itemId != null) {
            return MultiblockStructureCatalog.formatMachineName(itemId.getPath());
        }

        return "Unknown";
    }

    private static String resolveFromBlockRegistry(ResourceLocation itemId) {
        if (itemId == null || ForgeRegistries.BLOCKS == null) return null;
        try {
            var blk = ForgeRegistries.BLOCKS.getValue(itemId);
            if (blk != null && blk != Blocks.AIR) {
                String bName = blk.getName().getString();
                if (isValidDisplayName(bName)) {
                    return bName;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isValidDisplayName(String name) {
        if (name == null || name.isBlank()) return false;
        return !name.startsWith("block.") && !name.startsWith("item.")
                && !name.startsWith("tagprefix.") && !name.startsWith("gtceu.")
                && !name.contains(".gtceu.") && !name.equals("tagprefix.frame");
    }
}
