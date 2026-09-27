package com.gtceu.calcboard.api.model;

/**
 * Helper providing junction charging and reroute binding calculations.
 */
public final class NodeJunctionHelper {

    private NodeJunctionHelper() {}

    public static void bindRerouteIngredient(RecipeNode node, IngredientStack stack) {
        if (node == null || !node.isReroute() || stack == null) return;
        node.getInputs().clear();
        node.getOutputs().clear();
        node.getInputs().add(stack.copy());
        node.getOutputs().add(stack.copy());
        node.setName(stack.getDisplayName());
        if (stack.getAmount() > 0.0) {
            node.setTargetBatchAmount(stack.getAmount());
        }
        RecipeSpec junctionSpec = new RecipeSpec(
                node.getId(),
                node.getRecipeCategoryId(),
                0.0,
                0.0,
                java.util.List.of(stack.copy()),
                java.util.List.of(stack.copy())
        );
        node.setBaseSpecOnly(junctionSpec);
    }

    public static void unbindRerouteIngredient(RecipeNode node) {
        if (node == null || !node.isReroute()) return;
        node.getInputs().clear();
        node.getOutputs().clear();
        node.setName("Reroute");
        node.setBaseSpecOnly(null);
    }

    public static double getJunctionChargeDuration(RecipeNode node, FlowGraph graph) {
        if (node == null || !node.isJunctionBuffer() || graph == null) return 0.0;
        double bufferSize = node.getJunctionBufferSize();
        if (bufferSize <= 0.0001) return 0.0;
        var stats = graph.getInputPortStats(node, 0);
        double inRate = (stats != null && stats.isConnected()) ? stats.connectedRate() : 0.0;
        if (inRate <= 0.0001 && node.isExternalSupply()) {
            inRate = node.getExternalSupplyRate();
        }
        return inRate > 0.0001 ? bufferSize / inRate : 0.0;
    }
}
