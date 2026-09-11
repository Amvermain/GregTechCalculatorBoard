package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Traverses flow graph connections to compute port demands and effective incoming supplies.
 */
public final class AutoRatioFlowTraverser {

    private AutoRatioFlowTraverser() {}

    private record DemandHop(String nodeId, int outputIndex, double weight) {}
    private record SupplyHop(String nodeId, int inIdx, double weight) {}

    public static List<FlowGraph.ConnectionEdge> findPortIncomingEdges(FlowGraph graph, String consumerId, int inIdx) {
        List<FlowGraph.ConnectionEdge> inEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(consumerId) && edge.inputIndex() == inIdx) {
                inEdges.add(edge);
            }
        }
        return inEdges;
    }

    public static double calculateTotalConnectedPortDemand(FlowGraph graph, RecipeNode producer, int outputIndex) {
        return calculateTotalConnectedPortDemand(graph, producer, outputIndex, null);
    }

    public static double calculateTotalConnectedPortDemand(FlowGraph graph, RecipeNode producer, int outputIndex, Map<String, Double> countsMap) {
        if (producer == null || producer.isVoidSink()) return 0.0;

        Queue<DemandHop> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        queue.add(new DemandHop(producer.getId(), outputIndex, 1.0));
        visited.add(producer.getId() + ":" + outputIndex);

        double totalPortDemand = 0.0;

        while (!queue.isEmpty()) {
            DemandHop hop = queue.poll();
            for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                if (!outEdge.fromNodeId().equals(hop.nodeId) || outEdge.outputIndex() != hop.outputIndex) {
                    continue;
                }
                RecipeNode cNode = graph.findNodeById(outEdge.toNodeId());
                if (cNode == null) continue;

                if (cNode.isReroute()) {
                    totalPortDemand += computeRerouteDrainDemand(cNode, hop);
                    processRerouteDemandHop(graph, cNode, hop, countsMap, queue, visited);
                } else if (outEdge.inputIndex() < cNode.getInputs().size()) {
                    totalPortDemand += computeDirectPortDemand(graph, cNode, outEdge, hop, countsMap);
                }
            }
        }
        return totalPortDemand;
    }

    private static double computeRerouteDrainDemand(RecipeNode node, DemandHop hop) {
        if (node.isFixedDrain() && node.getExternalDrainRate() > 0.0) {
            return node.getExternalDrainRate() * hop.weight;
        }
        return 0.0;
    }

    private static void processRerouteDemandHop(
            FlowGraph graph,
            RecipeNode cNode,
            DemandHop hop,
            Map<String, Double> countsMap,
            Queue<DemandHop> queue,
            Set<String> visited
    ) {
        if (cNode.isInfiniteSupply() || cNode.isVoidSink()) {
            return;
        }
        if (!visited.add(cNode.getId() + ":0")) {
            return;
        }
        double nextWeight = hop.weight;
        if (cNode.isExternalSupply() && cNode.getExternalSupplyRate() > 0.0) {
            double downstreamDemand = calculateTotalConnectedPortDemand(graph, cNode, 0, countsMap);
            double netDemand = Math.max(0.0, downstreamDemand - cNode.getExternalSupplyRate());
            double factor = downstreamDemand > 0.0001 ? Math.min(1.0, netDemand / downstreamDemand) : 0.0;
            nextWeight = hop.weight * factor;
        }
        if (nextWeight > 0.00001) {
            queue.add(new DemandHop(cNode.getId(), 0, nextWeight));
        }
    }

    private static double computeDirectPortDemand(
            FlowGraph graph,
            RecipeNode cNode,
            FlowGraph.ConnectionEdge outEdge,
            DemandHop hop,
            Map<String, Double> countsMap
    ) {
        double cCount = countsMap != null ? countsMap.getOrDefault(cNode.getId(), cNode.getMachineCount()) : cNode.getMachineCount();
        IngredientStack inStack = cNode.getInputs().get(outEdge.inputIndex());
        double singleInRate = cNode.calculateSingleMachineInputRate(inStack);
        double cReq = singleInRate * cCount;
        if (outEdge.hasFixedLimit()) {
            cReq = Math.min(cReq, outEdge.fixedFlowLimit());
        }

        int inDegree = countPortInDegree(graph, cNode.getId(), outEdge.inputIndex());
        return (cReq * hop.weight) / Math.max(1, inDegree);
    }

    public static int countPortInDegree(FlowGraph graph, String consumerId, int inputIndex) {
        int inDegree = 0;
        for (FlowGraph.ConnectionEdge inEdge : graph.getConnections()) {
            if (inEdge.toNodeId().equals(consumerId) && inEdge.inputIndex() == inputIndex) {
                inDegree++;
            }
        }
        return inDegree;
    }

    public static int countPortOutDegree(FlowGraph graph, String producerId, int outputIndex) {
        int outDegree = 0;
        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
            if (outEdge.fromNodeId().equals(producerId) && outEdge.outputIndex() == outputIndex) {
                outDegree++;
            }
        }
        return outDegree;
    }

    public static double calculateEffectiveIncomingSupply(FlowGraph graph, RecipeNode consumer, int inIdx, Map<String, Double> countsMap) {
        return calculateEffectiveIncomingSupply(graph, consumer, inIdx, countsMap, false);
    }

    public static double calculateEffectiveIncomingSupply(FlowGraph graph, RecipeNode consumer, int inIdx, Map<String, Double> countsMap, boolean demandProportional) {
        if (consumer == null) return 0.0;

        Queue<SupplyHop> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        queue.add(new SupplyHop(consumer.getId(), inIdx, 1.0));
        visited.add(consumer.getId() + ":" + inIdx);

        double totalIncomingSupply = 0.0;

        while (!queue.isEmpty()) {
            SupplyHop hop = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (!edge.toNodeId().equals(hop.nodeId) || edge.inputIndex() != hop.inIdx) {
                    continue;
                }
                RecipeNode p = graph.findNodeById(edge.fromNodeId());
                if (p == null) continue;

                if (p.isReroute()) {
                    totalIncomingSupply += computeRerouteExternalSupply(graph, p, edge.outputIndex(), hop.weight);
                    processRerouteHop(graph, p, hop, demandProportional, visited, queue);
                } else if (edge.outputIndex() < p.getOutputs().size()) {
                    totalIncomingSupply += computeProducerIncomingSupply(graph, p, edge, consumer, inIdx, hop.weight, countsMap, demandProportional);
                }
            }
        }
        return totalIncomingSupply;
    }

    private static double computeRerouteExternalSupply(FlowGraph graph, RecipeNode p, int outputIndex, double weight) {
        if (!p.isExternalSupply() || p.getExternalSupplyRate() <= 0.0) return 0.0;
        int outDegree = countPortOutDegree(graph, p.getId(), outputIndex);
        return (p.getExternalSupplyRate() * weight) / Math.max(1, outDegree);
    }

    private static void processRerouteHop(
            FlowGraph graph,
            RecipeNode p,
            SupplyHop hop,
            boolean demandProportional,
            Set<String> visited,
            Queue<SupplyHop> queue
    ) {
        double nextWeight;
        if (demandProportional) {
            int inDegree = countPortInDegree(graph, p.getId(), 0);
            nextWeight = hop.weight / Math.max(1, inDegree);
        } else {
            int outDegree = countPortOutDegree(graph, p.getId(), 0);
            nextWeight = hop.weight / Math.max(1, outDegree);
        }

        if (visited.add(p.getId() + ":0")) {
            queue.add(new SupplyHop(p.getId(), 0, nextWeight));
        }
    }

    private static double getEffectiveConsumerPortDemand(RecipeNode consumer, int inIdx, Map<String, Double> countsMap) {
        if (consumer == null) return 0.0;
        if (consumer.isReroute()) {
            return consumer.isFixedDrain() ? consumer.getExternalDrainRate() : 0.0;
        }
        if (inIdx < 0 || inIdx >= consumer.getInputs().size()) return 0.0;
        IngredientStack inStack = consumer.getInputs().get(inIdx);
        double cC = countsMap != null ? countsMap.getOrDefault(consumer.getId(), consumer.getMachineCount()) : consumer.getMachineCount();
        return consumer.calculateSingleMachineInputRate(inStack) * cC;
    }

    private static double computeProducerIncomingSupply(
            FlowGraph graph,
            RecipeNode p,
            FlowGraph.ConnectionEdge edge,
            RecipeNode consumer,
            int inIdx,
            double weight,
            Map<String, Double> countsMap,
            boolean demandProportional
    ) {
        double pC = countsMap != null ? countsMap.getOrDefault(p.getId(), p.getMachineCount()) : p.getMachineCount();
        IngredientStack outStack = p.getOutputs().get(edge.outputIndex());
        double pRate = p.calculateSingleMachineOutputRate(outStack) * pC;

        if (demandProportional) {
            double consumerDemand = getEffectiveConsumerPortDemand(consumer, inIdx, countsMap);
            double totalPortDemand = calculateTotalConnectedPortDemand(graph, p, edge.outputIndex(), countsMap);

            if (totalPortDemand > 0.0001 && consumerDemand > 0.0001) {
                double allocated = (totalPortDemand <= pRate + 0.0001)
                        ? consumerDemand
                        : (pRate * (consumerDemand / totalPortDemand));
                return allocated * weight;
            }
        }

        int outDegree = countPortOutDegree(graph, p.getId(), edge.outputIndex());
        return (pRate * weight) / Math.max(1, outDegree);
    }

    public static boolean isPortDrivenByDownstreamChain(FlowGraph graph, String consumerId, int inIdx, String anchorId, Map<String, Double> countsMap) {
        Set<RecipeNode> feeders = new HashSet<>();
        FlowGraphTopologyAnalyzer.collectFeedingProducers(graph, consumerId, inIdx, feeders);
        for (RecipeNode feeder : feeders) {
            if (feeder.getId().equals(anchorId) || countsMap.containsKey(feeder.getId())) {
                return true;
            }
        }
        return false;
    }
}
