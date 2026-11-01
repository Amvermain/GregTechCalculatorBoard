package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.OverclockMode;

/**
 * Helper providing overclock resolution, effective parallel and power calculations.
 */
public final class NodePerformanceHelper {

    private NodePerformanceHelper() {}

    public static OverclockMode.OverclockResult computeOverclockResult(RecipeNode node) {
        try {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.gtceu.calcboard.api.event.RecipeNodeEvent.PreCalculation(node));
        } catch (Throwable ignored) {}
        return ModAdapterRegistry.getAdapterForNode(node).computeOverclock(node, node.getTargetTier(), node.isGenerator());
    }

    public static int computeTotalParallel(RecipeNode node) {
        if (node.isMultiblock() && (node.getCustomParallel() <= 1 || NodeAddonHelper.getCombinedParallelMultiplier(node.getAddons()) > 1)) {
            node.setCustomParallel(0);
        }
        if (node.getCustomParallel() > 0) {
            return node.getCustomParallel();
        }
        return ModAdapterRegistry.getAdapterForNode(node).computeEffectiveParallel(node);
    }

    public static double computeSingleMachinePower(RecipeNode node) {
        return ModAdapterRegistry.getAdapterForNode(node).computeSingleMachinePower(node);
    }

    public static double computeTotalEUt(RecipeNode node) {
        if (!node.isOperational()) return 0.0;
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) return 0.0;
        return node.getSingleMachineEUt() * node.getMachineCount();
    }

    public static double computeCompoundCycleDurationTicks(RecipeNode node) {
        if (node == null) return 0.0;
        double stepTicks = node.getOverclockResult().durationTicks();
        if (!node.isCompoundNode()) {
            return stepTicks;
        }
        FlowGraph graph = node.getParentGraph();
        if (graph != null) {
            java.util.List<RecipeNode> siblings = graph.findCompoundSiblingNodes(node.getCompoundGroupId());
            if (siblings.size() > 1) {
                double totalTicks = sumSiblingDurationTicks(siblings);
                if (totalTicks > 0.0) {
                    return totalTicks;
                }
            }
        }
        int totalLayers = Math.max(1, node.getCompoundTotalLayers());
        return stepTicks * totalLayers;
    }

    private static double sumSiblingDurationTicks(java.util.List<RecipeNode> siblings) {
        double totalTicks = 0.0;
        for (RecipeNode sibling : siblings) {
            totalTicks += sibling.getOverclockResult().durationTicks();
        }
        return totalTicks;
    }

    public static double computeSingleMachineCyclesPerSecond(RecipeNode node) {
        if (node == null) return 0.0;
        if (!node.isCompoundNode()) {
            return node.getOverclockResult().getCyclesPerSecond();
        }
        double cycleTicks = computeCompoundCycleDurationTicks(node);
        return cycleTicks > 0.0 ? (20.0 / cycleTicks) * node.getOverclockResult().batchesPerTick() : 0.0;
    }

    public static double computeNominalCps(RecipeNode node) {
        return computeSingleMachineCyclesPerSecond(node) * node.getMachineCount() * node.getTotalParallel();
    }
}
