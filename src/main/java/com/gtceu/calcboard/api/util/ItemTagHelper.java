package com.gtceu.calcboard.api.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/**
 * Utility for bridging Minecraft 1.21.1 ItemStack DataComponents / CustomData NBT operations.
 */
public final class ItemTagHelper {

    private ItemTagHelper() {}

    public static boolean hasTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null && !data.isEmpty();
    }

    public static CompoundTag getTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : null;
    }

    public static CompoundTag getOrCreateTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return new CompoundTag();
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data != null ? data.copyTag() : new CompoundTag();
    }

    public static void setTag(ItemStack stack, CompoundTag tag) {
        if (stack == null || stack.isEmpty()) return;
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
        }
    }
}
