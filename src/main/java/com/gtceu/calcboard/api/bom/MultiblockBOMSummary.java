package com.gtceu.calcboard.api.bom;

import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Locale;

public record MultiblockBOMSummary(
    List<BOMItemEntry> aggregatedItems,
    List<MachineBOMContribution> machineContributions,
    int totalMultiblockCount,
    int totalUniqueItemTypes
) {
    public static MultiblockBOMSummary merge(java.util.Collection<MultiblockBOMSummary> summaries) {
        if (summaries == null || summaries.isEmpty()) {
            return new MultiblockBOMSummary(List.of(), List.of(), 0, 0);
        }
        if (summaries.size() == 1) {
            return summaries.iterator().next();
        }

        java.util.Map<ResourceLocation, MergedItemBuilder> itemMap = new java.util.LinkedHashMap<>();
        List<MachineBOMContribution> contributions = new java.util.ArrayList<>();
        int totalMultiblocks = 0;

        for (MultiblockBOMSummary s : summaries) {
            if (s == null) continue;
            totalMultiblocks += s.totalMultiblockCount();
            contributions.addAll(s.machineContributions());

            for (BOMItemEntry item : s.aggregatedItems()) {
                mergeSingleItem(item, itemMap);
            }
        }

        List<BOMItemEntry> aggregated = new java.util.ArrayList<>();
        int stackSize = 64;
        for (MergedItemBuilder b : itemMap.values()) {
            int s = b.totalAmount / stackSize;
            int r = b.totalAmount % stackSize;
            aggregated.add(new BOMItemEntry(
                    b.itemId,
                    b.displayName,
                    b.totalAmount,
                    s,
                    r,
                    b.category,
                    java.util.Collections.unmodifiableList(b.usedByMachines)
            ));
        }

        return new MultiblockBOMSummary(
                java.util.Collections.unmodifiableList(aggregated),
                java.util.Collections.unmodifiableList(contributions),
                totalMultiblocks,
                aggregated.size()
        );
    }

    private static void mergeSingleItem(BOMItemEntry item, java.util.Map<ResourceLocation, MergedItemBuilder> itemMap) {
        if (item == null || item.itemId() == null) return;
        MergedItemBuilder builder = itemMap.computeIfAbsent(item.itemId(), k -> new MergedItemBuilder(
                item.itemId(), item.displayName(), item.category()
        ));
        builder.totalAmount += item.totalAmount();
        for (String m : item.usedByMachines()) {
            if (!builder.usedByMachines.contains(m)) {
                builder.usedByMachines.add(m);
            }
        }
    }

    private static class MergedItemBuilder {
        final ResourceLocation itemId;
        final String displayName;
        final PartCategory category;
        int totalAmount = 0;
        final List<String> usedByMachines = new java.util.ArrayList<>();

        MergedItemBuilder(ResourceLocation itemId, String displayName, PartCategory category) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.category = category;
        }
    }

    public record BOMItemEntry(
        ResourceLocation itemId,
        String displayName,
        int totalAmount,
        int stackCount,
        int remainder,
        PartCategory category,
        List<String> usedByMachines
    ) {
        @Override
        public String displayName() {
            return BOMDisplayNameResolver.resolve(itemId, this.displayName);
        }

        public static boolean isValidDisplayName(String name) {
            return BOMDisplayNameResolver.isValidDisplayName(name);
        }

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
            return BOMDisplayNameResolver.resolveItemStack(itemId);
        }
    }

    public record MachineBOMContribution(
        RecipeNode node,
        String machineName,
        int physicalMachineCount,
        List<MultiblockStructurePart> requiredParts
    ) {}
}

