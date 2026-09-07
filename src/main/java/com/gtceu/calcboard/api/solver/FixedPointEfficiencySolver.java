package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.*;

/**
 * Computes node operating efficiencies using fixed-point iterative convergence
 * and precomputed self-sustaining cycle resource balances.
 */
public final class FixedPointEfficiencySolver {

    private FixedPointEfficiencySolver() {}

    public record SelfSustainingResource(
            IngredientStack.Type type,
            net.minecraft.resources.ResourceLocation id
    ) {
        public boolean matches(IngredientStack stack) {
            if (stack == null) return false;
            return stack.getType() == type && Objects.equals(stack.getId(), id);
        }
    }

    public record SelfSustainingLoop(
            Set<String> nodeIds,
            SelfSustainingResource resource,
            double selfSufficiencyRatio,
            double externalFeedEfficiency
    ) {
        public boolean matches(RecipeNode node, IngredientStack stack) {
            return node != null && nodeIds.contains(node.getId()) && resource.matches(stack);
        }
    }

    public record ExternalFeedPort(
            String consumerNodeId,
            int inIdx,
            double nominalRate,
            List<FlowGraph.ConnectionEdge> inEdges
    ) {}

    public record PrecomputedLoopMeta(
            Set<String> scc,
            SelfSustainingResource resource,
            double selfSufficiencyRatio,
            List<ExternalFeedPort> externalFeedPorts
    ) {}

    public static Map<String, Double> computeNodeEfficiencies(FlowGraph graph) {
        Map<String, Double> effMap = new HashMap<>();
        if (graph == null) return effMap;
        graph.cleanupInvalidConnections();

        for (RecipeNode node : graph.getNodes()) {
            effMap.put(node.getId(), 1.0);
        }

        FlowEdgeAllocator.SolverContext context = FlowEdgeAllocator.SolverContext.create(graph);
        List<PrecomputedLoopMeta> loopMetas = precomputeLoopMetas(graph, context);

        for (int iter = 0; iter < 10; iter++) {
            boolean changed = false;
            List<SelfSustainingLoop> loops = evaluateLoops(graph, loopMetas, effMap, context);

            for (RecipeNode consumer : graph.getNodes()) {
                double calculatedEff = computeConsumerEfficiency(graph, consumer, loops, effMap, context);
                double oldEff = effMap.get(consumer.getId());
                consumer.setEfficiency(calculatedEff);
                if (Math.abs(oldEff - calculatedEff) > 0.0001) {
                    effMap.put(consumer.getId(), calculatedEff);
                    changed = true;
                }
            }

            if (propagateCompoundBottlenecks(graph, effMap)) {
                changed = true;
            }

            if (!changed) break;
        }

        return effMap;
    }

    private static boolean propagateCompoundBottlenecks(FlowGraph graph, Map<String, Double> effMap) {
        boolean changed = false;
        for (RecipeNode node : graph.getNodes()) {
            if (!node.isCompoundNode() || node.getCompoundLayerIndex() <= 0) {
                continue;
            }
            String groupId = node.getCompoundGroupId();
            int myLayer = node.getCompoundLayerIndex();
            RecipeNode prevLayer = null;
            for (RecipeNode other : graph.getNodes()) {
                if (other.isCompoundNode() && groupId.equals(other.getCompoundGroupId()) && other.getCompoundLayerIndex() == myLayer - 1) {
                    prevLayer = other;
                    break;
                }
            }
            if (prevLayer != null) {
                double prevEff = effMap.getOrDefault(prevLayer.getId(), 1.0);
                double currentEff = effMap.getOrDefault(node.getId(), 1.0);
                if (prevEff < currentEff - 0.0001) {
                    effMap.put(node.getId(), prevEff);
                    node.setEfficiency(prevEff);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static List<SelfSustainingLoop> evaluateLoops(
            FlowGraph graph,
            List<PrecomputedLoopMeta> loopMetas,
            Map<String, Double> effMap,
            FlowEdgeAllocator.SolverContext context
    ) {
        List<SelfSustainingLoop> result = new ArrayList<>(loopMetas.size());
        for (PrecomputedLoopMeta meta : loopMetas) {
            double feedEff = computeLoopFeedEfficiency(graph, meta, effMap, context);
            result.add(new SelfSustainingLoop(meta.scc(), meta.resource(), meta.selfSufficiencyRatio(), feedEff));
        }
        return result;
    }

    private static double computeLoopFeedEfficiency(
            FlowGraph graph,
            PrecomputedLoopMeta meta,
            Map<String, Double> effMap,
            FlowEdgeAllocator.SolverContext context
    ) {
        if (meta.externalFeedPorts().isEmpty()) {
            return 1.0;
        }
        double minFeedEff = 1.0;
        for (ExternalFeedPort p : meta.externalFeedPorts()) {
            double supply = computeIncomingSupply(graph, p.inEdges(), effMap, context);
            double feedRatio = Math.max(0.0, Math.min(1.0, supply / p.nominalRate()));
            minFeedEff = Math.min(minFeedEff, feedRatio);
        }
        return minFeedEff;
    }

    private static double computeConsumerEfficiency(
            FlowGraph graph,
            RecipeNode consumer,
            List<SelfSustainingLoop> loops,
            Map<String, Double> effMap,
            FlowEdgeAllocator.SolverContext context
    ) {
        double minRatio = 1.0;
        boolean hasConnectedInput = false;

        for (int inIdx = 0; inIdx < consumer.getInputs().size(); inIdx++) {
            double portRatio = computePortRatio(graph, consumer, inIdx, loops, effMap, context);
            if (portRatio < 0.0) {
                continue;
            }
            hasConnectedInput = true;
            minRatio = Math.min(minRatio, portRatio);
        }

        return hasConnectedInput ? Math.max(0.0, Math.min(1.0, minRatio)) : 1.0;
    }

    private static double computePortRatio(
            FlowGraph graph,
            RecipeNode consumer,
            int inIdx,
            List<SelfSustainingLoop> loops,
            Map<String, Double> effMap,
            FlowEdgeAllocator.SolverContext context
    ) {
        FlowEdgeAllocator.CachedEdgeIndex edgeIndex = context != null ? context.edgeIndex() : null;
        List<FlowGraph.ConnectionEdge> inEdges = findIncomingEdges(graph, consumer.getId(), inIdx, edgeIndex);
        if (inEdges.isEmpty()) {
            return -1.0;
        }
        IngredientStack inStack = consumer.getInputs().get(inIdx);
        double nominalInRate = context != null ? context.getInputRate(consumer, inIdx) : consumer.getInputSlotRate(inIdx, false);
        if (nominalInRate <= 0.00001) {
            return -1.0;
        }

        double totalIncomingSupply = computeIncomingSupply(graph, inEdges, effMap, context);
        double portRatio = totalIncomingSupply / nominalInRate;
        portRatio = applyLoopRelaxation(consumer, inStack, portRatio, loops);

        if (inStack.isStressUnit() && portRatio < 0.9999) {
            return 0.0;
        }
        return portRatio;
    }

    private static double applyLoopRelaxation(
            RecipeNode consumer,
            IngredientStack inStack,
            double baseRatio,
            List<SelfSustainingLoop> loops
    ) {
        double ratio = baseRatio;
        for (SelfSustainingLoop loop : loops) {
            if (loop.matches(consumer, inStack)) {
                double loopBound = Math.min(1.0, loop.selfSufficiencyRatio()) * loop.externalFeedEfficiency();
                ratio = Math.max(ratio, loopBound);
            }
        }
        return ratio;
    }

    public static List<FlowGraph.ConnectionEdge> findIncomingEdges(FlowGraph graph, String nodeId, int inputIndex, FlowEdgeAllocator.CachedEdgeIndex edgeIndex) {
        if (edgeIndex != null) {
            return edgeIndex.getInPortEdges(nodeId, inputIndex);
        }
        List<FlowGraph.ConnectionEdge> inEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(nodeId) && edge.inputIndex() == inputIndex) {
                inEdges.add(edge);
            }
        }
        return inEdges;
    }

    public static double computeIncomingSupply(
            FlowGraph graph,
            List<FlowGraph.ConnectionEdge> inEdges,
            Map<String, Double> effMap,
            FlowEdgeAllocator.SolverContext context
    ) {
        double totalIncomingSupply = 0.0;
        for (FlowGraph.ConnectionEdge edge : inEdges) {
            RecipeNode producer = graph.findNodeById(edge.fromNodeId());
            if (producer == null) continue;
            if (!producer.isReroute() && edge.outputIndex() >= producer.getOutputs().size()) continue;
            totalIncomingSupply += FlowEdgeAllocator.getEdgeAllocatedFlow(graph, edge, effMap, new HashSet<>(), context);
        }
        return totalIncomingSupply;
    }

    public static List<PrecomputedLoopMeta> precomputeLoopMetas(FlowGraph graph, FlowEdgeAllocator.SolverContext context) {
        List<PrecomputedLoopMeta> result = new ArrayList<>();
        if (graph == null || graph.getNodes().isEmpty() || graph.getConnections().isEmpty()) {
            return result;
        }

        FlowEdgeAllocator.CachedEdgeIndex edgeIndex = context != null ? context.edgeIndex() : FlowEdgeAllocator.buildEdgeIndex(graph);
        List<Set<String>> sccs = ProcessStabilityAnalyzer.findStronglyConnectedComponents(graph, edgeIndex);
        for (Set<String> scc : sccs) {
            if (scc.size() < 2 && !ProcessStabilityAnalyzer.hasSelfLoop(graph, scc, edgeIndex)) {
                continue;
            }
            Map<SelfSustainingResource, Double> prodTotals = new HashMap<>();
            Map<SelfSustainingResource, Double> demTotals = new HashMap<>();

            accumulateLoopResourceTotals(graph, scc, prodTotals, demTotals, context);

            for (Map.Entry<SelfSustainingResource, Double> entry : demTotals.entrySet()) {
                SelfSustainingResource res = entry.getKey();
                double dem = entry.getValue();
                double prod = prodTotals.getOrDefault(res, 0.0);
                if (dem > 0.0001 && prod >= dem - 0.001) {
                    double ratio = prod / dem;
                    List<ExternalFeedPort> extPorts = findExternalFeedPorts(graph, scc, res, context);
                    result.add(new PrecomputedLoopMeta(scc, res, ratio, extPorts));
                }
            }
        }
        return result;
    }

    private static List<ExternalFeedPort> findExternalFeedPorts(
            FlowGraph graph,
            Set<String> scc,
            SelfSustainingResource recirculatedRes,
            FlowEdgeAllocator.SolverContext context
    ) {
        List<ExternalFeedPort> extPorts = new ArrayList<>();
        FlowEdgeAllocator.CachedEdgeIndex edgeIndex = context != null ? context.edgeIndex() : null;
        for (String nodeId : scc) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node == null || node.isReroute()) continue;

            for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
                IngredientStack inStack = node.getInputs().get(inIdx);
                if (recirculatedRes.matches(inStack)) {
                    continue;
                }
                double nomRate = context != null ? context.getInputRate(node, inIdx) : node.getInputSlotRate(inIdx, false);
                if (nomRate <= 0.0001) continue;

                List<FlowGraph.ConnectionEdge> inEdges = findIncomingEdges(graph, nodeId, inIdx, edgeIndex);
                List<FlowGraph.ConnectionEdge> externalEdges = filterExternalFeedEdges(inEdges, scc);
                if (!externalEdges.isEmpty()) {
                    extPorts.add(new ExternalFeedPort(nodeId, inIdx, nomRate, externalEdges));
                }
            }
        }
        return extPorts;
    }

    private static List<FlowGraph.ConnectionEdge> filterExternalFeedEdges(
            List<FlowGraph.ConnectionEdge> inEdges,
            Set<String> scc
    ) {
        List<FlowGraph.ConnectionEdge> externalEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : inEdges) {
            if (!scc.contains(edge.fromNodeId())) {
                externalEdges.add(edge);
            }
        }
        return externalEdges;
    }

    private static void accumulateLoopResourceTotals(
            FlowGraph graph,
            Set<String> scc,
            Map<SelfSustainingResource, Double> prodTotals,
            Map<SelfSustainingResource, Double> demTotals,
            FlowEdgeAllocator.SolverContext context
    ) {
        FlowEdgeAllocator.CachedEdgeIndex edgeIndex = context != null ? context.edgeIndex() : null;
        for (String nodeId : scc) {
            List<FlowGraph.ConnectionEdge> outEdges = edgeIndex != null
                    ? edgeIndex.getOutEdges(nodeId)
                    : graph.getConnections();
            for (FlowGraph.ConnectionEdge edge : outEdges) {
                if (!scc.contains(edge.toNodeId())) continue;
                RecipeNode producer = graph.findNodeById(edge.fromNodeId());
                if (producer != null && !producer.isReroute() && edge.outputIndex() < producer.getOutputs().size()) {
                    IngredientStack outStack = producer.getOutputs().get(edge.outputIndex());
                    SelfSustainingResource res = new SelfSustainingResource(outStack.getType(), outStack.getId());
                    prodTotals.putIfAbsent(res, 0.0);
                    demTotals.putIfAbsent(res, 0.0);
                }
            }
        }

        for (String nodeId : scc) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node == null || node.isReroute()) continue;
            accumulatePortTotals(node, prodTotals, demTotals, context);
        }
    }

    private static void accumulatePortTotals(
            RecipeNode node,
            Map<SelfSustainingResource, Double> prodTotals,
            Map<SelfSustainingResource, Double> demTotals,
            FlowEdgeAllocator.SolverContext context
    ) {
        for (int outIdx = 0; outIdx < node.getOutputs().size(); outIdx++) {
            IngredientStack out = node.getOutputs().get(outIdx);
            SelfSustainingResource res = new SelfSustainingResource(out.getType(), out.getId());
            if (prodTotals.containsKey(res)) {
                double rate = context != null ? context.getOutputRate(node, outIdx) : node.getOutputSlotRate(outIdx, false);
                prodTotals.put(res, prodTotals.get(res) + rate);
            }
        }
        for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
            IngredientStack in = node.getInputs().get(inIdx);
            SelfSustainingResource res = new SelfSustainingResource(in.getType(), in.getId());
            if (demTotals.containsKey(res)) {
                double rate = context != null ? context.getInputRate(node, inIdx) : node.getInputSlotRate(inIdx, false);
                demTotals.put(res, demTotals.get(res) + rate);
            }
        }
    }
}
