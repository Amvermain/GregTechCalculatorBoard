package com.gtceu.calcboard.api.bom;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
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
        return calculateBOM(nodes, Collections.emptyList(), dualLowerTierEnergyHatches);
    }

    public static MultiblockBOMSummary calculateBOM(FlowGraph graph, boolean dualLowerTierEnergyHatches) {
        if (graph == null) return new MultiblockBOMSummary(List.of(), List.of(), 0, 0);
        return calculateBOM(graph.getNodes(), graph.getFrames(), dualLowerTierEnergyHatches);
    }

    public static MultiblockBOMSummary calculateBOM(List<RecipeNode> inputNodes, List<CanvasGroupFrame> inputFrames, boolean dualLowerTierEnergyHatches) {
        if (inputNodes == null || inputNodes.isEmpty()) {
            return new MultiblockBOMSummary(List.of(), List.of(), 0, 0);
        }

        List<RecipeNode> nodes = new ArrayList<>();
        List<CanvasGroupFrame> frames = new ArrayList<>();
        flattenNodesAndFrames(inputNodes, inputFrames, 1.0, nodes, frames);

        if (nodes.isEmpty()) {
            return new MultiblockBOMSummary(List.of(), List.of(), 0, 0);
        }

        Map<ResourceLocation, ItemAggregationBuilder> itemMap = new LinkedHashMap<>();
        List<MultiblockBOMSummary.MachineBOMContribution> machineContributions = new ArrayList<>();
        int totalMultiblocks = 0;

        // Process shared machine frames: map primary node -> required machine count, and set of slave nodes to skip
        Map<String, Integer> sharedFrameMasterCounts = new HashMap<>();
        Set<String> sharedFrameSlavesToSkip = new HashSet<>();
        processSharedMachineFrames(frames, nodes, sharedFrameMasterCounts, sharedFrameSlavesToSkip);

        for (RecipeNode node : nodes) {
            if (node == null || node.isReroute()) continue;
            if (sharedFrameSlavesToSkip.contains(node.getId())) continue;

            if (node.isCompoundNode()) {
                boolean isSingleMultiblockCluster = node.isMultiblock()
                        || MultiblockDetector.isMultiblock(node.getMachineIcon())
                        || (node.getMultiblockWorkstation() != null && MultiblockDetector.isMultiblock(node.getMultiblockWorkstation()));

                boolean sharesClusterMachine = false;
                if (node.getCompoundGroupId() != null) {
                    RecipeNode master = null;
                    for (RecipeNode other : nodes) {
                        if (other != null && node.getCompoundGroupId().equals(other.getCompoundGroupId()) && other.isCompoundMaster()) {
                            master = other;
                            break;
                        }
                    }
                    if (master != null && Objects.equals(node.getMachineIcon(), master.getMachineIcon())) {
                        sharesClusterMachine = true;
                    }
                }

                if ((isSingleMultiblockCluster || sharesClusterMachine) && !node.isCompoundMaster()) {
                    continue;
                }
            }

            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            List<MultiblockStructurePart> resolvedParts = adapter != null
                    ? adapter.resolveStructureParts(node, dualLowerTierEnergyHatches)
                    : List.of();

            if (resolvedParts.isEmpty()) continue;

            int machineCount = sharedFrameMasterCounts.containsKey(node.getId())
                    ? sharedFrameMasterCounts.get(node.getId())
                    : Math.max(1, (int) Math.ceil(node.getMachineCount()));
            totalMultiblocks += machineCount;

            ResourceLocation machineId = node.getMachineIcon();
            if (machineId == null && !node.getAvailableWorkstations().isEmpty()) {
                machineId = node.getAvailableWorkstations().get(0);
            }

            String machineName = node.getName() != null && !node.getName().isBlank()
                    ? node.getName()
                    : (machineId != null ? resolveDisplayName(machineId) : "Machine");

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

    private static String resolveDisplayName(ResourceLocation id) {
        if (id == null) return "";
        try {
            var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
            if (item != null && item != net.minecraft.world.item.Items.AIR) {
                String hn = new net.minecraft.world.item.ItemStack(item).getHoverName().getString();
                if (MultiblockBOMSummary.BOMItemEntry.isValidDisplayName(hn)) {
                    return hn;
                }
            }
            var blk = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getValue(id);
            if (blk != null && blk != net.minecraft.world.level.block.Blocks.AIR) {
                String bn = blk.getName().getString();
                if (MultiblockBOMSummary.BOMItemEntry.isValidDisplayName(bn)) {
                    return bn;
                }
            }
            String blockTrans = net.minecraft.network.chat.Component.translatable(id.toLanguageKey("block")).getString();
            if (MultiblockBOMSummary.BOMItemEntry.isValidDisplayName(blockTrans)) {
                return blockTrans;
            }
            String itemTrans = net.minecraft.network.chat.Component.translatable(id.toLanguageKey("item")).getString();
            if (MultiblockBOMSummary.BOMItemEntry.isValidDisplayName(itemTrans)) {
                return itemTrans;
            }
        } catch (Throwable ignored) {}
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

    private static void flattenNodesAndFrames(
            List<RecipeNode> sourceNodes,
            List<CanvasGroupFrame> sourceFrames,
            double parentMultiplier,
            List<RecipeNode> flatNodes,
            List<CanvasGroupFrame> flatFrames
    ) {
        if (sourceNodes == null) return;
        if (sourceFrames != null) {
            flatFrames.addAll(sourceFrames);
        }

        for (RecipeNode node : sourceNodes) {
            if (node == null || node.isReroute()) continue;

            if (node.isModule()) {
                double moduleMultiplier = parentMultiplier * Math.max(1.0, node.getMachineCount());
                if (node.getSubGraph() != null) {
                    flattenNodesAndFrames(
                            node.getSubGraph().getNodes(),
                            node.getSubGraph().getFrames(),
                            moduleMultiplier,
                            flatNodes,
                            flatFrames
                    );
                }
            } else {
                if (Math.abs(parentMultiplier - 1.0) > 0.0001) {
                    RecipeNode scaled = node.copy();
                    scaled.setMachineCount(node.getMachineCount() * parentMultiplier);
                    flatNodes.add(scaled);
                } else {
                    flatNodes.add(node);
                }
            }
        }
    }

    private static void processSharedMachineFrames(
            Collection<CanvasGroupFrame> frames,
            Collection<RecipeNode> nodes,
            Map<String, Integer> masterCounts,
            Set<String> slavesToSkip
    ) {
        if (frames == null) return;
        for (CanvasGroupFrame frame : frames) {
            processSingleSharedFrame(frame, nodes, masterCounts, slavesToSkip);
        }
    }

    private static void processSingleSharedFrame(
            CanvasGroupFrame frame,
            Collection<RecipeNode> nodes,
            Map<String, Integer> masterCounts,
            Set<String> slavesToSkip
    ) {
        if (frame == null || !frame.isSharedMachineFrame()) return;
        List<RecipeNode> enclosedNodes = frame.getEnclosedNodes(nodes);
        if (enclosedNodes.isEmpty()) return;

        Map<String, List<RecipeNode>> machineGroups = groupNodesBySignature(enclosedNodes);
        for (List<RecipeNode> group : machineGroups.values()) {
            aggregateSharedGroup(group, masterCounts, slavesToSkip);
        }
    }

    private static Map<String, List<RecipeNode>> groupNodesBySignature(List<RecipeNode> enclosedNodes) {
        Map<String, List<RecipeNode>> machineGroups = new LinkedHashMap<>();
        for (RecipeNode n : enclosedNodes) {
            if (n == null || n.isReroute()) continue;
            String groupKey = getNodeSignature(n);
            machineGroups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(n);
        }
        return machineGroups;
    }

    private static String getNodeSignature(RecipeNode n) {
        ResourceLocation icon = n.getMachineIcon();
        if (icon == null && !n.getAvailableWorkstations().isEmpty()) {
            icon = n.getAvailableWorkstations().get(0);
        }
        String tierName = n.getTargetTier() != null ? n.getTargetTier().name() : "default";
        return (icon != null ? icon.toString() : "unknown") + "@" + tierName;
    }

    private static void aggregateSharedGroup(
            List<RecipeNode> group,
            Map<String, Integer> masterCounts,
            Set<String> slavesToSkip
    ) {
        if (group == null || group.isEmpty()) return;
        double totalDuty = 0.0;
        for (RecipeNode n : group) {
            if (n.isOperational()) {
                totalDuty += n.getMachineCount();
            }
        }
        int reqMachines = Math.max(1, (int) Math.ceil(totalDuty - 0.00001));
        RecipeNode master = group.get(0);
        masterCounts.put(master.getId(), reqMachines);
        for (int i = 1; i < group.size(); i++) {
            slavesToSkip.add(group.get(i).getId());
        }
    }
}
