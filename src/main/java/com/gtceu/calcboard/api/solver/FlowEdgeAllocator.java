package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.*;

/**
 * Calculates outgoing edge flow allocations, demand-weighted splitting,
 * effective producer/consumer port rates, and cached graph edge indexing.
 */
public final class FlowEdgeAllocator {

    private FlowEdgeAllocator() {}

    public record CachedEdgeIndex(
            Map<String, List<FlowGraph.ConnectionEdge>> inEdges,
            Map<String, List<FlowGraph.ConnectionEdge>> outEdges,
            Map<FlowGraph.PortKey, List<FlowGraph.ConnectionEdge>> inPortEdges,
            Map<FlowGraph.PortKey, List<FlowGraph.ConnectionEdge>> outPortEdges
    ) {
        public List<FlowGraph.ConnectionEdge> getInEdges(String nodeId) {
            return inEdges.getOrDefault(nodeId, Collections.emptyList());
        }

        public List<FlowGraph.ConnectionEdge> getOutEdges(String nodeId) {
            return outEdges.getOrDefault(nodeId, Collections.emptyList());
        }

        public List<FlowGraph.ConnectionEdge> getInPortEdges(String nodeId, int inputIndex) {
            return inPortEdges.getOrDefault(new FlowGraph.PortKey(nodeId, true, inputIndex), Collections.emptyList());
        }

        public List<FlowGraph.ConnectionEdge> getOutPortEdges(String nodeId, int outputIndex) {
            return outPortEdges.getOrDefault(new FlowGraph.PortKey(nodeId, false, outputIndex), Collections.emptyList());
        }
    }

    public record CachedPortRates(
            Map<String, double[]> inputRates,
            Map<String, double[]> outputRates
    ) {
        public double getInputRate(RecipeNode node, int inIdx) {
            if (node == null || inIdx < 0) return 0.0;
            double[] rates = inputRates.get(node.getId());
            if (rates != null && inIdx < rates.length) {
                return rates[inIdx];
            }
            return node.getInputSlotRate(inIdx, false);
        }

        public double getOutputRate(RecipeNode node, int outIdx) {
            if (node == null || outIdx < 0) return 0.0;
            double[] rates = outputRates.get(node.getId());
            if (rates != null && outIdx < rates.length) {
                return rates[outIdx];
            }
            return node.getOutputSlotRate(outIdx, false);
        }
    }

    public record SolverContext(
            CachedEdgeIndex edgeIndex,
            CachedPortRates portRates
    ) {
        public static SolverContext create(FlowGraph graph) {
            return new SolverContext(buildEdgeIndex(graph), buildPortRates(graph));
        }

        public List<FlowGraph.ConnectionEdge> getInEdges(String nodeId) {
            return edgeIndex != null ? edgeIndex.getInEdges(nodeId) : Collections.emptyList();
        }

        public List<FlowGraph.ConnectionEdge> getOutEdges(String nodeId) {
            return edgeIndex != null ? edgeIndex.getOutEdges(nodeId) : Collections.emptyList();
        }

        public double getInputRate(RecipeNode node, int inIdx) {
            return portRates != null ? portRates.getInputRate(node, inIdx) : (node != null ? node.getInputSlotRate(inIdx, false) : 0.0);
        }

        public double getOutputRate(RecipeNode node, int outIdx) {
            return portRates != null ? portRates.getOutputRate(node, outIdx) : (node != null ? node.getOutputSlotRate(outIdx, false) : 0.0);
        }
    }

    public static CachedEdgeIndex buildEdgeIndex(FlowGraph graph) {
        if (graph == null) {
            return new CachedEdgeIndex(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
        Map<String, List<FlowGraph.ConnectionEdge>> inEdges = new HashMap<>();
        Map<String, List<FlowGraph.ConnectionEdge>> outEdges = new HashMap<>();
        Map<FlowGraph.PortKey, List<FlowGraph.ConnectionEdge>> inPortEdges = new HashMap<>();
        Map<FlowGraph.PortKey, List<FlowGraph.ConnectionEdge>> outPortEdges = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            inEdges.put(n.getId(), new ArrayList<>());
            outEdges.put(n.getId(), new ArrayList<>());
        }
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            List<FlowGraph.ConnectionEdge> ins = inEdges.get(edge.toNodeId());
            if (ins != null) ins.add(edge);
            List<FlowGraph.ConnectionEdge> outs = outEdges.get(edge.fromNodeId());
            if (outs != null) outs.add(edge);

            FlowGraph.PortKey inKey = new FlowGraph.PortKey(edge.toNodeId(), true, edge.inputIndex());
            inPortEdges.computeIfAbsent(inKey, k -> new ArrayList<>(2)).add(edge);

            FlowGraph.PortKey outKey = new FlowGraph.PortKey(edge.fromNodeId(), false, edge.outputIndex());
            outPortEdges.computeIfAbsent(outKey, k -> new ArrayList<>(2)).add(edge);
        }
        return new CachedEdgeIndex(inEdges, outEdges, inPortEdges, outPortEdges);
    }

    public static CachedPortRates buildPortRates(FlowGraph graph) {
        if (graph == null) {
            return new CachedPortRates(Collections.emptyMap(), Collections.emptyMap());
        }
        Map<String, double[]> inRates = new HashMap<>(graph.getNodes().size() * 2);
        Map<String, double[]> outRates = new HashMap<>(graph.getNodes().size() * 2);
        for (RecipeNode node : graph.getNodes()) {
            if (node == null || node.isReroute()) continue;
            int inCount = node.getInputs().size();
            double[] ins = new double[inCount];
            for (int i = 0; i < inCount; i++) {
                ins[i] = node.getInputSlotRate(i, false);
            }
            inRates.put(node.getId(), ins);

            int outCount = node.getOutputs().size();
            double[] outs = new double[outCount];
            for (int i = 0; i < outCount; i++) {
                outs[i] = node.getOutputSlotRate(i, false);
            }
            outRates.put(node.getId(), outs);
        }
        return new CachedPortRates(inRates, outRates);
    }

    public static Map<FlowGraph.ConnectionEdge, Double> calculateOutgoingEdgeAllocations(
            FlowGraph graph, RecipeNode producer, int outputIndex, double totalProducerRate) {
        return calculateOutgoingEdgeAllocations(graph, producer, outputIndex, totalProducerRate, null, (SolverContext) null);
    }

    public static Map<FlowGraph.ConnectionEdge, Double> calculateOutgoingEdgeAllocations(
            FlowGraph graph, RecipeNode producer, int outputIndex, double totalProducerRate, Map<String, Double> effMap) {
        return calculateOutgoingEdgeAllocations(graph, producer, outputIndex, totalProducerRate, effMap, (SolverContext) null);
    }

    public static Map<FlowGraph.ConnectionEdge, Double> calculateOutgoingEdgeAllocations(
            FlowGraph graph, RecipeNode producer, int outputIndex, double totalProducerRate, Map<String, Double> effMap, CachedEdgeIndex edgeIndex) {
        return calculateOutgoingEdgeAllocations(graph, producer, outputIndex, totalProducerRate, effMap, edgeIndex != null ? new SolverContext(edgeIndex, null) : null);
    }

    public static Map<FlowGraph.ConnectionEdge, Double> calculateOutgoingEdgeAllocations(
            FlowGraph graph, RecipeNode producer, int outputIndex, double totalProducerRate, Map<String, Double> effMap, SolverContext context) {
        Map<FlowGraph.ConnectionEdge, Double> allocations = new LinkedHashMap<>();
        if (graph == null || producer == null || totalProducerRate <= 0.00001) {
            return allocations;
        }

        List<FlowGraph.ConnectionEdge> outEdges = collectOutgoingEdgesForPort(graph, producer.getId(), outputIndex, context != null ? context.edgeIndex() : null);
        if (outEdges.isEmpty()) return allocations;

        if (outEdges.size() == 1 && !outEdges.get(0).hasFixedLimit()) {
            allocations.put(outEdges.get(0), totalProducerRate);
            return allocations;
        }

        double remainingFlow = totalProducerRate;
        List<FlowGraph.ConnectionEdge> variableEdges = new ArrayList<>(outEdges.size());
        for (FlowGraph.ConnectionEdge edge : outEdges) {
            if (edge.hasFixedLimit()) {
                double alloc = Math.min(edge.fixedFlowLimit(), remainingFlow);
                allocations.put(edge, alloc);
                remainingFlow = Math.max(0.0, remainingFlow - alloc);
            } else {
                variableEdges.add(edge);
            }
        }

        if (variableEdges.isEmpty()) {
            return allocations;
        }

        if (variableEdges.size() == 1) {
            allocations.put(variableEdges.get(0), remainingFlow);
            return allocations;
        }

        allocateVariableEdges(graph, variableEdges, remainingFlow, effMap, allocations, context);
        return allocations;
    }

    public static List<FlowGraph.ConnectionEdge> collectOutgoingEdgesForPort(
            FlowGraph graph, String producerId, int outputIndex, CachedEdgeIndex edgeIndex) {
        if (edgeIndex != null) {
            return edgeIndex.getOutPortEdges(producerId, outputIndex);
        }
        List<FlowGraph.ConnectionEdge> result = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(producerId) && edge.outputIndex() == outputIndex) {
                result.add(edge);
            }
        }
        return result;
    }

    private static void allocateVariableEdges(
            FlowGraph graph,
            List<FlowGraph.ConnectionEdge> variableEdges,
            double remainingFlow,
            Map<String, Double> effMap,
            Map<FlowGraph.ConnectionEdge, Double> allocations,
            SolverContext context
    ) {
        List<FlowGraph.ConnectionEdge> normalEdges = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> voidEdges = new ArrayList<>();
        Map<FlowGraph.ConnectionEdge, Double> demandMap = new LinkedHashMap<>();
        double totalNormalDemand = 0.0;

        for (FlowGraph.ConnectionEdge edge : variableEdges) {
            RecipeNode consumer = graph.findNodeById(edge.toNodeId());
            if (consumer != null && consumer.isVoidSink()) {
                voidEdges.add(edge);
                demandMap.put(edge, 0.0);
            } else {
                normalEdges.add(edge);
                double demand = getConnectedConsumerDemand(graph, consumer, edge.inputIndex(), effMap, context);
                demandMap.put(edge, demand);
                totalNormalDemand += demand;
            }
        }

        if (totalNormalDemand <= 0.0001) {
            if (!voidEdges.isEmpty()) {
                double split = remainingFlow / voidEdges.size();
                for (FlowGraph.ConnectionEdge edge : voidEdges) {
                    allocations.put(edge, split);
                }
                for (FlowGraph.ConnectionEdge edge : normalEdges) {
                    allocations.put(edge, 0.0);
                }
            } else {
                double split = remainingFlow / variableEdges.size();
                for (FlowGraph.ConnectionEdge edge : variableEdges) {
                    allocations.put(edge, split);
                }
            }
            return;
        }

        if (remainingFlow >= totalNormalDemand - 0.0001) {
            double surplus = remainingFlow - totalNormalDemand;
            double shareableDemand = 0.0;
            for (FlowGraph.ConnectionEdge edge : normalEdges) {
                RecipeNode consumer = graph.findNodeById(edge.toNodeId());
                if (!isFixedCappedConsumer(graph, consumer)) {
                    shareableDemand += demandMap.get(edge);
                }
            }
            for (FlowGraph.ConnectionEdge edge : normalEdges) {
                double demand = demandMap.get(edge);
                RecipeNode consumer = graph.findNodeById(edge.toNodeId());
                boolean isFixedCapped = isFixedCappedConsumer(graph, consumer);
                double surplusShare = (!isFixedCapped && voidEdges.isEmpty() && shareableDemand > 0.0001)
                        ? surplus * (demand / shareableDemand)
                        : 0.0;
                allocations.put(edge, demand + surplusShare);
            }
            if (!voidEdges.isEmpty()) {
                double voidSplit = surplus / voidEdges.size();
                for (FlowGraph.ConnectionEdge edge : voidEdges) {
                    allocations.put(edge, voidSplit);
                }
            }
            return;
        }

        for (FlowGraph.ConnectionEdge edge : normalEdges) {
            allocations.put(edge, remainingFlow * (demandMap.get(edge) / totalNormalDemand));
        }
        for (FlowGraph.ConnectionEdge edge : voidEdges) {
            allocations.put(edge, 0.0);
        }
    }

    private static boolean isFixedCappedConsumer(FlowGraph graph, RecipeNode consumer) {
        if (consumer == null || !consumer.isFixedDrain()) return false;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(consumer.getId())) {
                return false;
            }
        }
        return true;
    }

    public static double getEdgeAllocatedFlow(FlowGraph graph, FlowGraph.ConnectionEdge targetEdge, Map<String, Double> effMap) {
        return getEdgeAllocatedFlow(graph, targetEdge, effMap, new HashSet<>(), (SolverContext) null);
    }

    public static double getEdgeAllocatedFlow(FlowGraph graph, FlowGraph.ConnectionEdge targetEdge, Map<String, Double> effMap, Set<String> visited) {
        return getEdgeAllocatedFlow(graph, targetEdge, effMap, visited, (SolverContext) null);
    }

    public static double getEdgeAllocatedFlow(
            FlowGraph graph,
            FlowGraph.ConnectionEdge targetEdge,
            Map<String, Double> effMap,
            Set<String> visited,
            CachedEdgeIndex edgeIndex
    ) {
        return getEdgeAllocatedFlow(graph, targetEdge, effMap, visited, edgeIndex != null ? new SolverContext(edgeIndex, null) : null);
    }

    public static double getEdgeAllocatedFlow(
            FlowGraph graph,
            FlowGraph.ConnectionEdge targetEdge,
            Map<String, Double> effMap,
            Set<String> visited,
            SolverContext context
    ) {
        if (graph == null || targetEdge == null) return 0.0;
        RecipeNode producer = graph.findNodeById(targetEdge.fromNodeId());
        if (producer == null || targetEdge.outputIndex() < 0 || (!producer.isReroute() && targetEdge.outputIndex() >= producer.getOutputs().size())) {
            return 0.0;
        }
        double prodActualRate = getEffectiveProducerOutputRate(graph, producer, targetEdge.outputIndex(), effMap, visited, context);
        if (prodActualRate <= 0.00001) {
            return 0.0;
        }

        CachedEdgeIndex edgeIndex = context != null ? context.edgeIndex() : null;
        List<FlowGraph.ConnectionEdge> outEdges = edgeIndex != null
                ? edgeIndex.getOutPortEdges(producer.getId(), targetEdge.outputIndex())
                : collectOutgoingEdgesForPort(graph, producer.getId(), targetEdge.outputIndex(), null);

        if (outEdges.size() == 1 && !outEdges.get(0).hasFixedLimit()) {
            return prodActualRate;
        }

        Map<FlowGraph.ConnectionEdge, Double> allocations = calculateOutgoingEdgeAllocations(graph, producer, targetEdge.outputIndex(), prodActualRate, effMap, context);
        return allocations.getOrDefault(targetEdge, 0.0);
    }

    public static double getEffectiveProducerOutputRate(FlowGraph graph, RecipeNode producer, int outputIndex) {
        return getEffectiveProducerOutputRate(graph, producer, outputIndex, null);
    }

    public static double getEffectiveProducerOutputRate(FlowGraph graph, RecipeNode producer, int outputIndex, Map<String, Double> effMap) {
        return getEffectiveProducerOutputRate(graph, producer, outputIndex, effMap, new HashSet<>(), (SolverContext) null);
    }

    public static double getEffectiveProducerOutputRate(FlowGraph graph, RecipeNode producer, int outputIndex, Map<String, Double> effMap, Set<String> visited) {
        return getEffectiveProducerOutputRate(graph, producer, outputIndex, effMap, visited, (SolverContext) null);
    }

    public static double getEffectiveProducerOutputRate(
            FlowGraph graph,
            RecipeNode producer,
            int outputIndex,
            Map<String, Double> effMap,
            Set<String> visited,
            CachedEdgeIndex edgeIndex
    ) {
        return getEffectiveProducerOutputRate(graph, producer, outputIndex, effMap, visited, edgeIndex != null ? new SolverContext(edgeIndex, null) : null);
    }

    public static double getEffectiveProducerOutputRate(
            FlowGraph graph,
            RecipeNode producer,
            int outputIndex,
            Map<String, Double> effMap,
            Set<String> visited,
            SolverContext context
    ) {
        if (graph == null || producer == null || outputIndex < 0 || (!producer.isReroute() && outputIndex >= producer.getOutputs().size())) return 0.0;
        if (!visited.add(producer.getId())) return 0.0;

        try {
            if (!producer.isReroute()) {
                double prodEff = effMap != null ? effMap.getOrDefault(producer.getId(), producer.getEfficiency()) : producer.getEfficiency();
                double prodNominalRate = context != null ? context.getOutputRate(producer, outputIndex) : producer.getOutputSlotRate(outputIndex, false);
                return prodNominalRate * prodEff;
            }

            boolean hasIncoming = false;
            double incomingSupply = 0.0;
            List<FlowGraph.ConnectionEdge> inCandidates = context != null
                    ? context.getInEdges(producer.getId())
                    : graph.getConnections();
            for (FlowGraph.ConnectionEdge inEdge : inCandidates) {
                if (inEdge.toNodeId().equals(producer.getId()) && inEdge.inputIndex() == 0) {
                    hasIncoming = true;
                    incomingSupply += getEdgeAllocatedFlow(graph, inEdge, effMap, visited, context);
                }
            }

            if (producer.isInfiniteSupply()) {
                return calculateTotalConnectedDemand(graph, producer, outputIndex, effMap, context);
            }

            if (producer.isExternalSupply() && producer.getExternalSupplyRate() > 0.0) {
                return incomingSupply + producer.getExternalSupplyRate();
            }

            if (!hasIncoming) {
                return calculateTotalConnectedDemand(graph, producer, outputIndex, effMap, context);
            }

            return incomingSupply;
        } finally {
            visited.remove(producer.getId());
        }
    }

    private static double calculateTotalConnectedDemand(
            FlowGraph graph,
            RecipeNode producer,
            int outputIndex,
            Map<String, Double> effMap,
            SolverContext context
    ) {
        double totalPortDemand = 0.0;
        List<FlowGraph.ConnectionEdge> outCandidates = context != null
                ? context.getOutEdges(producer.getId())
                : graph.getConnections();
        for (FlowGraph.ConnectionEdge outEdge : outCandidates) {
            if (outEdge.fromNodeId().equals(producer.getId()) && outEdge.outputIndex() == outputIndex) {
                RecipeNode c = graph.findNodeById(outEdge.toNodeId());
                totalPortDemand += getConnectedConsumerDemand(graph, c, outEdge.inputIndex(), effMap, context);
            }
        }
        return totalPortDemand;
    }

    public static double getConnectedConsumerDemand(FlowGraph graph, RecipeNode consumer, int inputIndex) {
        return getConnectedConsumerDemand(graph, consumer, inputIndex, null, null);
    }

    public static double getConnectedConsumerDemand(FlowGraph graph, RecipeNode consumer, int inputIndex, Map<String, Double> effMap) {
        return getConnectedConsumerDemand(graph, consumer, inputIndex, effMap, null);
    }

    public static double getConnectedConsumerDemand(FlowGraph graph, RecipeNode consumer, int inputIndex, Map<String, Double> effMap, SolverContext context) {
        if (consumer == null || consumer.isVoidSink()) return 0.0;
        if (consumer.isReroute()) {
            double drain = consumer.isFixedDrain() ? consumer.getExternalDrainRate() : 0.0;
            return drain + calculateTotalRerouteOutputDemand(graph, consumer);
        }
        if (inputIndex < consumer.getInputs().size()) {
            double nominalRate = context != null ? context.getInputRate(consumer, inputIndex) : consumer.getInputSlotRate(inputIndex, false);
            if (effMap != null && effMap.containsKey(consumer.getId())) {
                return nominalRate * effMap.get(consumer.getId());
            }
            return nominalRate;
        }
        return 0.0;
    }

    private static double calculateTotalRerouteOutputDemand(FlowGraph graph, RecipeNode rerouteNode) {
        double total = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(rerouteNode.getId()) && edge.outputIndex() == 0) {
                RecipeNode target = graph.findNodeById(edge.toNodeId());
                if (target != null) {
                    total += getConnectedConsumerDemand(graph, target, edge.inputIndex());
                }
            }
        }
        return total;
    }
}
