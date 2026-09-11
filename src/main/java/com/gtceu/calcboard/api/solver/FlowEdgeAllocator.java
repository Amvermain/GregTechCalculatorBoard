package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.FlowSplitMode;

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

        FlowSplitMode splitMode = (producer != null && producer.isReroute())
                ? producer.getJunctionSplitMode()
                : FlowSplitMode.PROPORTIONAL;
        if (splitMode == null) {
            splitMode = FlowSplitMode.PROPORTIONAL;
        }

        if (hasCustomPriority(outEdges)) {
            allocateHierarchicalPriorityEdges(graph, outEdges, totalProducerRate, effMap, allocations, splitMode, context);
            return allocations;
        }

        if (splitMode == FlowSplitMode.EQUAL) {
            allocateEqualEdges(graph, outEdges, totalProducerRate, effMap, allocations, context);
            return allocations;
        }

        if (splitMode == FlowSplitMode.WEIGHTED) {
            allocateWeightedEdges(graph, outEdges, totalProducerRate, effMap, allocations, context);
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

        allocateProportionalEdges(graph, variableEdges, remainingFlow, effMap, allocations, context);
        return allocations;
    }

    private static boolean hasCustomPriority(List<FlowGraph.ConnectionEdge> outEdges) {
        if (outEdges == null) return false;
        for (FlowGraph.ConnectionEdge edge : outEdges) {
            if (edge.priority() != 0) {
                return true;
            }
        }
        return false;
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

    private static void allocateProportionalEdges(
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
                distributeEvenly(voidEdges, remainingFlow, allocations);
                distributeEvenly(normalEdges, 0.0, allocations);
            } else {
                distributeEvenly(variableEdges, remainingFlow, allocations);
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
            distributeEvenly(voidEdges, voidEdges.isEmpty() ? 0.0 : surplus, allocations);
            return;
        }

        for (FlowGraph.ConnectionEdge edge : normalEdges) {
            allocations.put(edge, remainingFlow * (demandMap.get(edge) / totalNormalDemand));
        }
        distributeEvenly(voidEdges, 0.0, allocations);
    }

    private static void allocateEqualEdges(
            FlowGraph graph,
            List<FlowGraph.ConnectionEdge> outEdges,
            double totalProducerRate,
            Map<String, Double> effMap,
            Map<FlowGraph.ConnectionEdge, Double> allocations,
            SolverContext context
    ) {
        List<FlowGraph.ConnectionEdge> normalEdges = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> voidEdges = new ArrayList<>();
        Map<FlowGraph.ConnectionEdge, Double> capMap = new LinkedHashMap<>();
        double totalNormalCap = 0.0;

        boolean hasUncapped = false;
        for (FlowGraph.ConnectionEdge edge : outEdges) {
            RecipeNode consumer = graph.findNodeById(edge.toNodeId());
            if (consumer != null && consumer.isVoidSink()) {
                voidEdges.add(edge);
            } else {
                normalEdges.add(edge);
                double demand = getConnectedConsumerDemand(graph, consumer, edge.inputIndex(), effMap, context);
                double cap = edge.hasFixedLimit()
                        ? (demand > 0.0001 ? Math.min(demand, edge.fixedFlowLimit()) : edge.fixedFlowLimit())
                        : (demand > 0.0001 ? demand : Double.MAX_VALUE);
                capMap.put(edge, cap);
                if (cap < Double.MAX_VALUE) {
                    totalNormalCap += cap;
                } else {
                    hasUncapped = true;
                }
            }
        }

        distributeEvenly(voidEdges, 0.0, allocations);
        if (normalEdges.isEmpty()) {
            distributeEvenly(voidEdges, totalProducerRate, allocations);
            return;
        }

        if (voidEdges.isEmpty()) {
            allocateEqualWithoutVoidSink(normalEdges, totalProducerRate, allocations);
            return;
        }

        allocateEqualWithVoidSink(normalEdges, voidEdges, capMap, totalNormalCap, hasUncapped, totalProducerRate, allocations);
    }

    private static void allocateEqualWithoutVoidSink(
            List<FlowGraph.ConnectionEdge> normalEdges,
            double flow,
            Map<FlowGraph.ConnectionEdge, Double> allocations
    ) {
        Map<FlowGraph.ConnectionEdge, Double> limits = new LinkedHashMap<>();
        for (FlowGraph.ConnectionEdge edge : normalEdges) {
            limits.put(edge, edge.hasFixedLimit() ? edge.fixedFlowLimit() : Double.MAX_VALUE);
        }
        allocateEqualWithCaps(normalEdges, limits, flow, allocations);
    }

    private static void allocateEqualWithVoidSink(
            List<FlowGraph.ConnectionEdge> normalEdges,
            List<FlowGraph.ConnectionEdge> voidEdges,
            Map<FlowGraph.ConnectionEdge, Double> capMap,
            double totalNormalCap,
            boolean hasUncappedNormalEdge,
            double flow,
            Map<FlowGraph.ConnectionEdge, Double> allocations
    ) {
        if (hasUncappedNormalEdge) {
            allocateEqualWithCaps(normalEdges, capMap, flow, allocations);
            return;
        }

        if (totalNormalCap <= 0.0001) {
            distributeEvenly(voidEdges, flow, allocations);
            for (FlowGraph.ConnectionEdge edge : normalEdges) {
                allocations.put(edge, 0.0);
            }
            return;
        }

        if (flow >= totalNormalCap - 0.0001) {
            for (FlowGraph.ConnectionEdge edge : normalEdges) {
                allocations.put(edge, capMap.getOrDefault(edge, 0.0));
            }
            double surplus = Math.max(0.0, flow - totalNormalCap);
            distributeEvenly(voidEdges, surplus, allocations);
            return;
        }

        allocateEqualWithCaps(normalEdges, capMap, flow, allocations);
    }

    private static void allocateEqualWithCaps(
            List<FlowGraph.ConnectionEdge> edges,
            Map<FlowGraph.ConnectionEdge, Double> caps,
            double flow,
            Map<FlowGraph.ConnectionEdge, Double> allocations
    ) {
        if (edges == null || edges.isEmpty()) return;
        List<FlowGraph.ConnectionEdge> active = new ArrayList<>(edges);
        double remaining = flow;

        while (!active.isEmpty() && remaining > 0.0001) {
            double share = remaining / active.size();
            List<FlowGraph.ConnectionEdge> cappedThisPass = new ArrayList<>();

            for (FlowGraph.ConnectionEdge edge : active) {
                double cap = caps.getOrDefault(edge, Double.MAX_VALUE);
                if (cap <= share + 0.000001) {
                    cappedThisPass.add(edge);
                }
            }

            if (cappedThisPass.isEmpty()) {
                for (FlowGraph.ConnectionEdge edge : active) {
                    allocations.put(edge, share);
                }
                return;
            }

            for (FlowGraph.ConnectionEdge edge : cappedThisPass) {
                double cap = caps.getOrDefault(edge, Double.MAX_VALUE);
                allocations.put(edge, cap);
                remaining = Math.max(0.0, remaining - cap);
                active.remove(edge);
            }
        }

        for (FlowGraph.ConnectionEdge edge : active) {
            allocations.put(edge, 0.0);
        }
    }

    private static void allocateWeightedEdges(
            FlowGraph graph,
            List<FlowGraph.ConnectionEdge> outEdges,
            double totalProducerRate,
            Map<String, Double> effMap,
            Map<FlowGraph.ConnectionEdge, Double> allocations,
            SolverContext context
    ) {
        List<FlowGraph.ConnectionEdge> normalEdges = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> voidEdges = new ArrayList<>();
        Map<FlowGraph.ConnectionEdge, Double> capMap = new LinkedHashMap<>();
        double totalNormalCap = 0.0;

        boolean hasUncapped = false;
        for (FlowGraph.ConnectionEdge edge : outEdges) {
            RecipeNode consumer = graph.findNodeById(edge.toNodeId());
            if (consumer != null && consumer.isVoidSink()) {
                voidEdges.add(edge);
            } else {
                normalEdges.add(edge);
                double demand = getConnectedConsumerDemand(graph, consumer, edge.inputIndex(), effMap, context);
                double cap = edge.hasFixedLimit()
                        ? (demand > 0.0001 ? Math.min(demand, edge.fixedFlowLimit()) : edge.fixedFlowLimit())
                        : (demand > 0.0001 ? demand : Double.MAX_VALUE);
                capMap.put(edge, cap);
                if (cap < Double.MAX_VALUE) {
                    totalNormalCap += cap;
                } else {
                    hasUncapped = true;
                }
            }
        }

        distributeWeighted(voidEdges, 0.0, allocations);
        if (normalEdges.isEmpty()) {
            distributeWeighted(voidEdges, totalProducerRate, allocations);
            return;
        }

        if (voidEdges.isEmpty()) {
            allocateWeightedWithoutVoidSink(normalEdges, totalProducerRate, allocations);
            return;
        }

        allocateWeightedWithVoidSink(normalEdges, voidEdges, capMap, totalNormalCap, hasUncapped, totalProducerRate, allocations);
    }

    private static void allocateWeightedWithoutVoidSink(
            List<FlowGraph.ConnectionEdge> normalEdges,
            double flow,
            Map<FlowGraph.ConnectionEdge, Double> allocations
    ) {
        Map<FlowGraph.ConnectionEdge, Double> limits = new LinkedHashMap<>();
        for (FlowGraph.ConnectionEdge edge : normalEdges) {
            limits.put(edge, edge.hasFixedLimit() ? edge.fixedFlowLimit() : Double.MAX_VALUE);
        }
        allocateWeightedWithCaps(normalEdges, limits, flow, allocations);
    }

    private static void allocateWeightedWithVoidSink(
            List<FlowGraph.ConnectionEdge> normalEdges,
            List<FlowGraph.ConnectionEdge> voidEdges,
            Map<FlowGraph.ConnectionEdge, Double> capMap,
            double totalNormalCap,
            boolean hasUncappedNormalEdge,
            double flow,
            Map<FlowGraph.ConnectionEdge, Double> allocations
    ) {
        if (hasUncappedNormalEdge) {
            allocateWeightedWithCaps(normalEdges, capMap, flow, allocations);
            return;
        }

        if (totalNormalCap <= 0.0001) {
            distributeWeighted(voidEdges, flow, allocations);
            for (FlowGraph.ConnectionEdge edge : normalEdges) {
                allocations.put(edge, 0.0);
            }
            return;
        }

        if (flow >= totalNormalCap - 0.0001) {
            for (FlowGraph.ConnectionEdge edge : normalEdges) {
                allocations.put(edge, capMap.getOrDefault(edge, 0.0));
            }
            double surplus = Math.max(0.0, flow - totalNormalCap);
            distributeWeighted(voidEdges, surplus, allocations);
            return;
        }

        allocateWeightedWithCaps(normalEdges, capMap, flow, allocations);
    }

    private static void allocateWeightedWithCaps(
            List<FlowGraph.ConnectionEdge> edges,
            Map<FlowGraph.ConnectionEdge, Double> caps,
            double flow,
            Map<FlowGraph.ConnectionEdge, Double> allocations
    ) {
        if (edges == null || edges.isEmpty()) return;
        List<FlowGraph.ConnectionEdge> active = new ArrayList<>(edges);
        double remaining = flow;

        while (!active.isEmpty() && remaining > 0.0001) {
            double totalWeight = 0.0;
            for (FlowGraph.ConnectionEdge edge : active) {
                totalWeight += getEdgeWeight(edge);
            }

            boolean useEqual = (totalWeight <= 0.000001);
            List<FlowGraph.ConnectionEdge> cappedThisPass = new ArrayList<>();

            for (FlowGraph.ConnectionEdge edge : active) {
                double share = useEqual
                        ? (remaining / active.size())
                        : (remaining * (getEdgeWeight(edge) / totalWeight));
                double cap = caps.getOrDefault(edge, Double.MAX_VALUE);
                if (cap <= share + 0.000001) {
                    cappedThisPass.add(edge);
                }
            }

            if (cappedThisPass.isEmpty()) {
                for (FlowGraph.ConnectionEdge edge : active) {
                    double share = useEqual
                            ? (remaining / active.size())
                            : (remaining * (getEdgeWeight(edge) / totalWeight));
                    allocations.put(edge, share);
                }
                return;
            }

            for (FlowGraph.ConnectionEdge edge : cappedThisPass) {
                double cap = caps.getOrDefault(edge, Double.MAX_VALUE);
                allocations.put(edge, cap);
                remaining = Math.max(0.0, remaining - cap);
                active.remove(edge);
            }
        }

        for (FlowGraph.ConnectionEdge edge : active) {
            allocations.put(edge, 0.0);
        }
    }

    public static double getEdgeWeight(FlowGraph.ConnectionEdge edge) {
        if (edge == null) return 1.0;
        return FlowGraph.ConnectionEdge.sanitizeWeight(edge.weight());
    }

    private static void distributeWeighted(
            List<FlowGraph.ConnectionEdge> edges,
            double flow,
            Map<FlowGraph.ConnectionEdge, Double> allocations
    ) {
        if (edges == null || edges.isEmpty()) return;
        if (flow <= 0.000001) {
            for (FlowGraph.ConnectionEdge edge : edges) {
                allocations.put(edge, 0.0);
            }
            return;
        }
        double totalWeight = 0.0;
        for (FlowGraph.ConnectionEdge edge : edges) {
            totalWeight += getEdgeWeight(edge);
        }
        if (totalWeight <= 0.000001) {
            distributeEvenly(edges, flow, allocations);
            return;
        }
        for (FlowGraph.ConnectionEdge edge : edges) {
            allocations.put(edge, flow * (getEdgeWeight(edge) / totalWeight));
        }
    }

    private static void allocateHierarchicalPriorityEdges(
            FlowGraph graph,
            List<FlowGraph.ConnectionEdge> outEdges,
            double totalProducerRate,
            Map<String, Double> effMap,
            Map<FlowGraph.ConnectionEdge, Double> allocations,
            FlowSplitMode splitMode,
            SolverContext context
    ) {
        List<FlowGraph.ConnectionEdge> normalEdges = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> voidEdges = new ArrayList<>();
        Map<FlowGraph.ConnectionEdge, Double> demandMap = new LinkedHashMap<>();
        Map<FlowGraph.ConnectionEdge, Double> capMap = new LinkedHashMap<>();
        double totalNormalCap = 0.0;

        for (FlowGraph.ConnectionEdge edge : outEdges) {
            RecipeNode consumer = graph.findNodeById(edge.toNodeId());
            if (consumer != null && consumer.isVoidSink()) {
                voidEdges.add(edge);
                demandMap.put(edge, 0.0);
                capMap.put(edge, 0.0);
            } else {
                normalEdges.add(edge);
                double demand = getConnectedConsumerDemand(graph, consumer, edge.inputIndex(), effMap, context);
                demandMap.put(edge, demand);
                double cap = edge.hasFixedLimit()
                        ? (demand > 0.0001 ? Math.min(demand, edge.fixedFlowLimit()) : edge.fixedFlowLimit())
                        : demand;
                capMap.put(edge, cap);
                totalNormalCap += cap;
            }
        }

        distributeEvenly(voidEdges, 0.0, allocations);

        if (normalEdges.isEmpty()) {
            if (splitMode == FlowSplitMode.WEIGHTED) {
                distributeWeighted(voidEdges, totalProducerRate, allocations);
            } else {
                distributeEvenly(voidEdges, totalProducerRate, allocations);
            }
            return;
        }

        Map<Integer, List<FlowGraph.ConnectionEdge>> priorityTiers = new TreeMap<>(Collections.reverseOrder());
        for (FlowGraph.ConnectionEdge edge : normalEdges) {
            priorityTiers.computeIfAbsent(edge.priority(), p -> new ArrayList<>()).add(edge);
        }

        if (totalNormalCap <= 0.0001) {
            if (!voidEdges.isEmpty()) {
                if (splitMode == FlowSplitMode.WEIGHTED) {
                    distributeWeighted(voidEdges, totalProducerRate, allocations);
                } else {
                    distributeEvenly(voidEdges, totalProducerRate, allocations);
                }
                distributeEvenly(normalEdges, 0.0, allocations);
            } else {
                allocateZeroDemandPriorityTiers(priorityTiers, totalProducerRate, allocations, splitMode);
            }
            return;
        }

        double currentFlow = totalProducerRate;
        for (Map.Entry<Integer, List<FlowGraph.ConnectionEdge>> entry : priorityTiers.entrySet()) {
            currentFlow = allocateSinglePriorityTier(entry.getValue(), capMap, currentFlow, splitMode, allocations);
        }

        if (currentFlow > 0.0001) {
            handlePrioritySurplus(graph, normalEdges, voidEdges, demandMap, currentFlow, allocations, splitMode);
        }
    }

    private static double allocateSinglePriorityTier(
            List<FlowGraph.ConnectionEdge> tierEdges,
            Map<FlowGraph.ConnectionEdge, Double> capMap,
            double currentFlow,
            FlowSplitMode splitMode,
            Map<FlowGraph.ConnectionEdge, Double> allocations
    ) {
        if (currentFlow <= 0.000001) {
            distributeEvenly(tierEdges, 0.0, allocations);
            return 0.0;
        }

        double tierDemand = 0.0;
        for (FlowGraph.ConnectionEdge edge : tierEdges) {
            tierDemand += capMap.getOrDefault(edge, 0.0);
        }

        if (tierDemand <= 0.0001) {
            distributeEvenly(tierEdges, 0.0, allocations);
            return currentFlow;
        }

        if (currentFlow >= tierDemand - 0.0001) {
            for (FlowGraph.ConnectionEdge edge : tierEdges) {
                allocations.put(edge, capMap.get(edge));
            }
            return Math.max(0.0, currentFlow - tierDemand);
        }

        if (splitMode == FlowSplitMode.EQUAL) {
            allocateEqualWithCaps(tierEdges, capMap, currentFlow, allocations);
        } else if (splitMode == FlowSplitMode.WEIGHTED) {
            allocateWeightedWithCaps(tierEdges, capMap, currentFlow, allocations);
        } else {
            for (FlowGraph.ConnectionEdge edge : tierEdges) {
                allocations.put(edge, currentFlow * (capMap.get(edge) / tierDemand));
            }
        }
        return 0.0;
    }

    private static void allocateZeroDemandPriorityTiers(
            Map<Integer, List<FlowGraph.ConnectionEdge>> priorityTiers,
            double flow,
            Map<FlowGraph.ConnectionEdge, Double> allocations,
            FlowSplitMode splitMode
    ) {
        boolean firstTier = true;
        for (List<FlowGraph.ConnectionEdge> tierEdges : priorityTiers.values()) {
            if (firstTier) {
                if (splitMode == FlowSplitMode.WEIGHTED) {
                    distributeWeighted(tierEdges, flow, allocations);
                } else {
                    distributeEvenly(tierEdges, flow, allocations);
                }
                firstTier = false;
            } else {
                distributeEvenly(tierEdges, 0.0, allocations);
            }
        }
    }

    private static void handlePrioritySurplus(
            FlowGraph graph,
            List<FlowGraph.ConnectionEdge> normalEdges,
            List<FlowGraph.ConnectionEdge> voidEdges,
            Map<FlowGraph.ConnectionEdge, Double> demandMap,
            double surplus,
            Map<FlowGraph.ConnectionEdge, Double> allocations,
            FlowSplitMode splitMode
    ) {
        if (!voidEdges.isEmpty()) {
            if (splitMode == FlowSplitMode.WEIGHTED) {
                distributeWeighted(voidEdges, surplus, allocations);
            } else {
                distributeEvenly(voidEdges, surplus, allocations);
            }
            return;
        }

        List<FlowGraph.ConnectionEdge> shareableEdges = new ArrayList<>();
        double shareableDemand = 0.0;
        double shareableWeight = 0.0;
        for (FlowGraph.ConnectionEdge edge : normalEdges) {
            RecipeNode consumer = graph.findNodeById(edge.toNodeId());
            if (!edge.hasFixedLimit() && !isFixedCappedConsumer(graph, consumer)) {
                shareableEdges.add(edge);
                shareableDemand += demandMap.getOrDefault(edge, 0.0);
                shareableWeight += getEdgeWeight(edge);
            }
        }

        if (shareableEdges.isEmpty()) {
            return;
        }

        if (splitMode == FlowSplitMode.WEIGHTED) {
            if (shareableWeight <= 0.0001) {
                double split = surplus / shareableEdges.size();
                for (FlowGraph.ConnectionEdge edge : shareableEdges) {
                    allocations.put(edge, allocations.getOrDefault(edge, 0.0) + split);
                }
            } else {
                for (FlowGraph.ConnectionEdge edge : shareableEdges) {
                    double edgeWeight = getEdgeWeight(edge);
                    allocations.put(edge, allocations.getOrDefault(edge, 0.0) + surplus * (edgeWeight / shareableWeight));
                }
            }
        } else if (splitMode == FlowSplitMode.EQUAL || shareableDemand <= 0.0001) {
            double split = surplus / shareableEdges.size();
            for (FlowGraph.ConnectionEdge edge : shareableEdges) {
                allocations.put(edge, allocations.getOrDefault(edge, 0.0) + split);
            }
        } else {
            for (FlowGraph.ConnectionEdge edge : shareableEdges) {
                double demand = demandMap.get(edge);
                double currentAlloc = allocations.getOrDefault(edge, demand);
                allocations.put(edge, currentAlloc + surplus * (demand / shareableDemand));
            }
        }
    }

    private static void distributeEvenly(
            List<FlowGraph.ConnectionEdge> edges,
            double flow,
            Map<FlowGraph.ConnectionEdge, Double> allocations
    ) {
        if (edges.isEmpty()) return;
        double split = flow / edges.size();
        for (FlowGraph.ConnectionEdge edge : edges) {
            allocations.put(edge, split);
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

            if (producer.isFixedDrain() && producer.getExternalDrainRate() > 0.0) {
                return Math.max(0.0, incomingSupply - producer.getExternalDrainRate());
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
        return getConnectedConsumerDemand(graph, consumer, inputIndex, effMap, context, new HashSet<>());
    }

    public static double getConnectedConsumerDemand(
            FlowGraph graph,
            RecipeNode consumer,
            int inputIndex,
            Map<String, Double> effMap,
            SolverContext context,
            Set<String> visited
    ) {
        if (consumer == null || consumer.isVoidSink()) return 0.0;
        if (!visited.add(consumer.getId())) return 0.0;
        try {
            if (consumer.isReroute()) {
                double drain = consumer.isFixedDrain() ? consumer.getExternalDrainRate() : 0.0;
                return drain + calculateTotalRerouteOutputDemand(graph, consumer, effMap, context, visited);
            }
            if (inputIndex < consumer.getInputs().size()) {
                double nominalRate = context != null ? context.getInputRate(consumer, inputIndex) : consumer.getInputSlotRate(inputIndex, false);
                if (effMap != null && effMap.containsKey(consumer.getId())) {
                    return nominalRate * effMap.get(consumer.getId());
                }
                return nominalRate;
            }
            return 0.0;
        } finally {
            visited.remove(consumer.getId());
        }
    }

    private static double calculateTotalRerouteOutputDemand(
            FlowGraph graph,
            RecipeNode rerouteNode,
            Map<String, Double> effMap,
            SolverContext context,
            Set<String> visited
    ) {
        double total = 0.0;
        List<FlowGraph.ConnectionEdge> outCandidates = context != null
                ? context.getOutEdges(rerouteNode.getId())
                : graph.getConnections();
        for (FlowGraph.ConnectionEdge edge : outCandidates) {
            if (edge.fromNodeId().equals(rerouteNode.getId()) && edge.outputIndex() == 0) {
                RecipeNode target = graph.findNodeById(edge.toNodeId());
                if (target != null) {
                    total += getConnectedConsumerDemand(graph, target, edge.inputIndex(), effMap, context, visited);
                }
            }
        }
        return total;
    }
}
