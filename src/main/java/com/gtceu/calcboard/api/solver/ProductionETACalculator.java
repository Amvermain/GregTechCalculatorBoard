package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.*;

/**
 * Pure domain calculation engine for target batch production duration (Estimated Time / ETA),
 * total energy consumption, and raw material demand breakdown.
 */
public final class ProductionETACalculator {

    private ProductionETACalculator() {}

    /**
     * Calculates the net inflow rate (units per second) into a specific input port of the target node.
     */
    public static double calculateNetInflowRate(FlowGraph graph, RecipeNode targetNode, int inputPortIndex) {
        if (graph == null || targetNode == null) return 0.0;

        record SupplyHop(String nodeId, int inIdx, double weight) {}
        Queue<SupplyHop> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        queue.add(new SupplyHop(targetNode.getId(), inputPortIndex, 1.0));
        visited.add(targetNode.getId() + ":" + inputPortIndex);

        double totalIncomingSupply = 0.0;

        while (!queue.isEmpty()) {
            SupplyHop hop = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.toNodeId().equals(hop.nodeId) && edge.inputIndex() == hop.inIdx) {
                    RecipeNode p = graph.findNodeById(edge.fromNodeId());
                    if (p != null) {
                        if (p.isReroute()) {
                            int outDegree = 0;
                            for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                                if (outEdge.fromNodeId().equals(p.getId())) {
                                    outDegree++;
                                }
                            }
                            double nextWeight = hop.weight / Math.max(1, outDegree);
                            if (visited.add(p.getId() + ":0")) {
                                queue.add(new SupplyHop(p.getId(), 0, nextWeight));
                            }
                        } else if (edge.outputIndex() < p.getOutputs().size()) {
                            IngredientStack outStack = p.getOutputs().get(edge.outputIndex());
                            double pRate = p.calculateSingleMachineOutputRate(outStack) * p.getMachineCount();

                            int outDegree = 0;
                            for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                                if (outEdge.fromNodeId().equals(p.getId()) && outEdge.outputIndex() == edge.outputIndex()) {
                                    outDegree++;
                                }
                            }
                            totalIncomingSupply += (pRate * hop.weight) / Math.max(1, outDegree);
                        }
                    }
                }
            }
        }
        return totalIncomingSupply;
    }

    /**
     * Calculates estimated completion time in seconds for a given target batch quantity.
     *
     * @param targetAmount   The desired target quantity (items or mB).
     * @param netRatePerSec  The net production / inflow rate per second.
     * @return Duration in seconds, 0.0 if amount is 0, or Double.POSITIVE_INFINITY if rate <= 0.
     */
    public static double calculateETA(double targetAmount, double netRatePerSec) {
        if (targetAmount <= 0.0) return 0.0;
        if (netRatePerSec <= 1e-9) return Double.POSITIVE_INFINITY;
        return targetAmount / netRatePerSec;
    }

    /**
     * Collects all upstream producer nodes feeding into the target node.
     */
    public static Set<RecipeNode> collectUpstreamNodes(FlowGraph graph, RecipeNode targetNode) {
        Set<RecipeNode> result = new HashSet<>();
        if (graph == null || targetNode == null) return result;

        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(targetNode.getId());
        visited.add(targetNode.getId());

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.toNodeId().equals(currentId)) {
                    RecipeNode fromNode = graph.findNodeById(edge.fromNodeId());
                    if (fromNode != null && visited.add(fromNode.getId())) {
                        result.add(fromNode);
                        queue.add(fromNode.getId());
                    }
                }
            }
        }
        return result;
    }

    /**
     * Calculates the total energy (in EU) consumed by upstream machines during the target batch production.
     */
    public static double calculateTotalEnergyForBatch(FlowGraph graph, RecipeNode targetNode, double targetAmount) {
        return calculateTotalEnergyForBatch(graph, targetNode, 0, targetAmount);
    }

    /**
     * Calculates the total energy (in EU) consumed by upstream machines during the target batch production for a specific port.
     */
    public static double calculateTotalEnergyForBatch(FlowGraph graph, RecipeNode targetNode, int targetPortIndex, double targetAmount) {
        if (graph == null || targetNode == null || targetAmount <= 0.0) return 0.0;
        double netRate = calculateNetInflowRate(graph, targetNode, targetPortIndex);
        double etaSec = calculateETA(targetAmount, netRate);
        if (Double.isInfinite(etaSec) || etaSec <= 0.0) return 0.0;

        Set<RecipeNode> upstreams = collectUpstreamNodes(graph, targetNode);
        double totalEUt = 0.0;
        for (RecipeNode n : upstreams) {
            if (n.isReroute() || n.isGenerator()) continue;
            totalEUt += n.getTotalEUt();
        }

        return totalEUt * 20.0 * etaSec;
    }

    /**
     * Calculates total raw materials required across the upstream chain to produce the batch amount.
     */
    public static Map<IngredientStack, Double> calculateTotalRawMaterialsForBatch(FlowGraph graph, RecipeNode targetNode, double targetAmount) {
        return calculateTotalRawMaterialsForBatch(graph, targetNode, 0, targetAmount);
    }

    /**
     * Calculates total raw materials required across the upstream chain to produce the batch amount for a specific port.
     */
    public static Map<IngredientStack, Double> calculateTotalRawMaterialsForBatch(FlowGraph graph, RecipeNode targetNode, int targetPortIndex, double targetAmount) {
        Map<IngredientStack, Double> result = new LinkedHashMap<>();
        if (graph == null || targetNode == null || targetAmount <= 0.0) return result;
        double netRate = calculateNetInflowRate(graph, targetNode, targetPortIndex);
        double etaSec = calculateETA(targetAmount, netRate);
        if (Double.isInfinite(etaSec) || etaSec <= 0.0) return result;

        Set<RecipeNode> upstreams = collectUpstreamNodes(graph, targetNode);
        for (RecipeNode n : upstreams) {
            if (n.isReroute()) continue;
            for (int i = 0; i < n.getInputs().size(); i++) {
                IngredientStack inStack = n.getInputs().get(i);
                if (inStack == null) continue;

                boolean hasUpstreamConnection = false;
                for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                    if (edge.toNodeId().equals(n.getId()) && edge.inputIndex() == i) {
                        hasUpstreamConnection = true;
                        break;
                    }
                }

                if (!hasUpstreamConnection) {
                    double inputRatePerSec = inStack.getAmount() * n.getOverclockResult().getCyclesPerSecond() * n.getTotalParallel() * n.getMachineCount();
                    double totalNeeded = inputRatePerSec * etaSec;
                    
                    IngredientStack canonical = null;
                    for (IngredientStack s : result.keySet()) {
                        if (s.matchesOrAlternative(inStack)) {
                            canonical = s;
                            break;
                        }
                    }
                    if (canonical != null) {
                        result.put(canonical, result.get(canonical) + totalNeeded);
                    } else {
                        result.put(inStack.copy(), totalNeeded);
                    }
                }
            }
        }
        return result;
    }
}


