package com.gtceu.calcboard.api.bom;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Pure domain coordinator and quantity aggregator for Bill of Materials (BOM) calculations.
 * Delegates individual machine structure resolution to respective domain IModAdapter instances.
 */
public class MultiblockBOMCalculator {

    public static MultiblockBOMSummary calculateBOM(List<RecipeNode> nodes, boolean dualLowerTierEnergyHatches) {
        if (nodes == null || nodes.isEmpty()) {
            return new MultiblockBOMSummary(List.of(), List.of(), 0, 0);
        }

        Map<ResourceLocation, ItemAggregationBuilder> itemMap = new LinkedHashMap<>();
        List<MultiblockBOMSummary.MachineBOMContribution> machineContributions = new ArrayList<>();
        int totalMultiblocks = 0;

        for (RecipeNode node : nodes) {
            if (node == null || node.isReroute()) continue;

            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            List<MultiblockStructurePart> resolvedParts = adapter != null
                    ? adapter.resolveStructureParts(node, dualLowerTierEnergyHatches)
                    : List.of();

            if (resolvedParts.isEmpty()) continue;

            int machineCount = Math.max(1, (int) Math.ceil(node.getMachineCount()));
            totalMultiblocks += machineCount;

            ResourceLocation machineId = node.getMachineIcon();
            if (machineId == null && !node.getAvailableWorkstations().isEmpty()) {
                machineId = node.getAvailableWorkstations().get(0);
            }

            String machineName = node.getName() != null && !node.getName().isBlank()
                    ? node.getName()
                    : (machineId != null ? formatDisplayName(machineId) : "Machine");

            machineContributions.add(new MultiblockBOMSummary.MachineBOMContribution(
                    node,
                    machineName,
                    machineCount,
                    resolvedParts
            ));

            String machineUsageStr = String.format(Locale.ROOT, "%s (x%d)", machineName, machineCount);

            for (MultiblockStructurePart part : resolvedParts) {
                if (part == null || part.itemId() == null) continue;
                if (part.itemId().getPath().equals("air") || part.itemId().toString().equals("minecraft:air")) continue;
                int totalForNode = part.amount() * machineCount;
                ItemAggregationBuilder builder = itemMap.computeIfAbsent(part.itemId(), k -> new ItemAggregationBuilder(
                        part.itemId(),
                        part.displayName(),
                        part.category()
                ));
                builder.totalAmount += totalForNode;
                if (!builder.usedByMachines.contains(machineUsageStr)) {
                    builder.usedByMachines.add(machineUsageStr);
                }
            }
        }

        List<MultiblockBOMSummary.BOMItemEntry> aggregatedItems = new ArrayList<>();
        for (ItemAggregationBuilder b : itemMap.values()) {
            int stackSize = 64;
            int s = b.totalAmount / stackSize;
            int r = b.totalAmount % stackSize;
            aggregatedItems.add(new MultiblockBOMSummary.BOMItemEntry(
                    b.itemId,
                    b.displayName,
                    b.totalAmount,
                    s,
                    r,
                    b.category,
                    b.usedByMachines
            ));
        }

        // Sort: Category priority (CONTROLLER -> CASING -> COIL -> HATCH_BUS -> OTHER) then totalAmount desc
        aggregatedItems.sort((a, b) -> {
            int catComp = Integer.compare(a.category().ordinal(), b.category().ordinal());
            if (catComp != 0) return catComp;
            return Integer.compare(b.totalAmount(), a.totalAmount());
        });

        return new MultiblockBOMSummary(
                aggregatedItems,
                machineContributions,
                totalMultiblocks,
                aggregatedItems.size()
        );
    }

    private static String formatDisplayName(ResourceLocation id) {
        if (id == null) return "";
        return MultiblockStructureCatalog.formatMachineName(id.getPath());
    }

    private static class ItemAggregationBuilder {
        ResourceLocation itemId;
        String displayName;
        PartCategory category;
        int totalAmount = 0;
        List<String> usedByMachines = new ArrayList<>();

        ItemAggregationBuilder(ResourceLocation itemId, String displayName, PartCategory category) {
            this.itemId = itemId;
            this.displayName = displayName;
            this.category = category;
        }
    }
}
