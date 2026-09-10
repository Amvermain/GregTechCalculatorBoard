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

    public static double computeNominalCps(RecipeNode node) {
        return node.getOverclockResult().getCyclesPerSecond() * node.getMachineCount() * node.getTotalParallel();
    }
}
