package com.gtceu.calcboard.api.bom;

import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;
import java.util.Locale;

public record MultiblockBOMSummary(
    List<BOMItemEntry> aggregatedItems,
    List<MachineBOMContribution> machineContributions,
    int totalMultiblockCount,
    int totalUniqueItemTypes
) {
    public record BOMItemEntry(
        ResourceLocation itemId,
        String displayName,
        int totalAmount,
        int stackCount,
        int remainder,
        PartCategory category,
        List<String> usedByMachines
    ) {
        public String formatStackCount() {
            if (totalAmount <= 0) return "0";
            if (stackCount > 0 && remainder > 0) {
                return String.format(Locale.ROOT, "%d stacks + %d (%d)", stackCount, remainder, totalAmount);
            } else if (stackCount > 0) {
                return String.format(Locale.ROOT, "%d stacks (%d)", stackCount, totalAmount);
            } else {
                return String.format(Locale.ROOT, "%d items", totalAmount);
            }
        }

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

    public record MachineBOMContribution(
        RecipeNode node,
        String machineName,
        int physicalMachineCount,
        List<MultiblockStructurePart> requiredParts
    ) {}
}

