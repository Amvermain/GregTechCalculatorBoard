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

    public static double calculateETA(double targetAmount, double netRatePerSec) {
        if (targetAmount <= 0.0) return 0.0;
        if (netRatePerSec <= 1e-9) return Double.POSITIVE_INFINITY;
        return targetAmount / netRatePerSec;
    }

    public static double calculateETA(FlowGraph graph, RecipeNode targetNode, double targetAmount, double netRatePerSec) {
        if (targetAmount <= 0.0) return 0.0;
        if (netRatePerSec <= 1e-9) return Double.POSITIVE_INFINITY;

        double cycleDuration = findUpstreamCycleDuration(graph, targetNode);
        if (cycleDuration > 0.0001) {
            double cycleCapacity = netRatePerSec * cycleDuration;
            if (cycleCapacity > 1e-9) {
                long cycles = (long) Math.ceil(targetAmount / cycleCapacity);
                return cycles * cycleDuration;
            }
        }
        return targetAmount / netRatePerSec;
    }

    public static double calculateDepletionTime(double supplyAmount, double netOutflowRate) {
        if (supplyAmount <= 0.0) return 0.0;
        if (netOutflowRate <= 1e-9) return Double.POSITIVE_INFINITY;
        return supplyAmount / netOutflowRate;
    }

    public static double calculateDepletionTime(FlowGraph graph, RecipeNode sourceNode, double supplyAmount, double netOutflowRate) {
        if (supplyAmount <= 0.0) return 0.0;
        if (netOutflowRate <= 1e-9) return Double.POSITIVE_INFINITY;

        double cycleDuration = findDownstreamCycleDuration(graph, sourceNode);
        if (cycleDuration > 0.0001) {
            double cycleCapacity = netOutflowRate * cycleDuration;
            if (cycleCapacity > 1e-9) {
                long cycles = (long) Math.ceil(supplyAmount / cycleCapacity);
                return cycles * cycleDuration;
            }
        }
        return supplyAmount / netOutflowRate;
    }

    public static double findUpstreamCycleDuration(FlowGraph graph, RecipeNode targetNode) {
        if (graph == null || targetNode == null) return 0.0;

        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(targetNode.getId());
        visited.add(targetNode.getId());

        double maxDuration = 0.0;

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.toNodeId().equals(currentId)) {
                    RecipeNode fromNode = graph.findNodeById(edge.fromNodeId());
                    if (fromNode != null && visited.add(fromNode.getId())) {
                        if (fromNode.isReroute()) {
                            queue.add(fromNode.getId());
                        } else {
                            maxDuration = Math.max(maxDuration, fromNode.getEffectiveDurationSeconds());
                        }
                    }
                }
            }
        }
        return maxDuration;
    }

    public static double findDownstreamCycleDuration(FlowGraph graph, RecipeNode sourceNode) {
        if (graph == null || sourceNode == null) return 0.0;

        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(sourceNode.getId());
        visited.add(sourceNode.getId());

        double maxDuration = 0.0;

        while (!queue.isEmpty()) {
            String currentId = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.fromNodeId().equals(currentId)) {
                    RecipeNode toNode = graph.findNodeById(edge.toNodeId());
                    if (toNode != null && visited.add(toNode.getId())) {
                        if (toNode.isReroute()) {
                            queue.add(toNode.getId());
                        } else {
                            maxDuration = Math.max(maxDuration, toNode.getEffectiveDurationSeconds());
                        }
                    }
                }
            }
        }
        return maxDuration;
    }

    public static double calculateNetOutflowRate(FlowGraph graph, RecipeNode sourceNode) {
        if (graph == null || sourceNode == null) return 0.0;

        record ConsumerHop(String nodeId, double weight) {}
        Queue<ConsumerHop> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        queue.add(new ConsumerHop(sourceNode.getId(), 1.0));
        visited.add(sourceNode.getId());

        double totalOutflow = 0.0;

        while (!queue.isEmpty()) {
            ConsumerHop hop = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.fromNodeId().equals(hop.nodeId)) {
                    RecipeNode consumer = graph.findNodeById(edge.toNodeId());
                    if (consumer != null) {
                        if (consumer.isReroute()) {
                            if (visited.add(consumer.getId())) {
                                queue.add(new ConsumerHop(consumer.getId(), hop.weight));
                            }
                        } else if (edge.inputIndex() < consumer.getInputs().size()) {
                            IngredientStack inStack = consumer.getInputs().get(edge.inputIndex());
                            double cRate = consumer.calculateSingleMachineInputRate(inStack) * consumer.getMachineCount();

                            int inDegree = 0;
                            for (FlowGraph.ConnectionEdge inEdge : graph.getConnections()) {
                                if (inEdge.toNodeId().equals(consumer.getId()) && inEdge.inputIndex() == edge.inputIndex()) {
                                    inDegree++;
                                }
                            }
                            totalOutflow += (cRate * hop.weight) / Math.max(1, inDegree);
                        }
                    }
                }
            }
        }
        return totalOutflow;
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


