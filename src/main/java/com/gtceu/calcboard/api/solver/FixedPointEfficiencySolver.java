package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeProperties;

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

    /**
     * Analytical precomputed metadata for damped recirculation loops (ADR-044).
     * Solves infinite geometric series for steady-state supply: S_steady = S_ext / (1 - r).
     */
    public record PrecomputedDampedLoopMeta(
            Set<String> scc,
            SelfSustainingResource resource,
            double recirculationRatio,
            double nominalDemand,
            double nominalProduction,
            List<FlowGraph.ConnectionEdge> externalEdges
    ) {
        public double computeExternalSupply(FlowGraph graph, Map<String, Double> effMap, FlowEdgeAllocator.SolverContext context) {
            return computeIncomingSupply(graph, externalEdges, effMap, context);
        }

        public double computeSteadyStateSupply(FlowGraph graph, Map<String, Double> effMap, FlowEdgeAllocator.SolverContext context) {
            double sExt = computeExternalSupply(graph, effMap, context);
            if (recirculationRatio >= 1.0 - 1e-4) {
                return sExt;
            }
            return sExt / (1.0 - recirculationRatio);
        }

        public double computeSteadyStateEfficiency(FlowGraph graph, Map<String, Double> effMap, FlowEdgeAllocator.SolverContext context) {
            if (nominalDemand <= 1e-5) return 1.0;
            double sSteady = computeSteadyStateSupply(graph, effMap, context);
            return Math.min(1.0, sSteady / nominalDemand);
        }

        public boolean matches(RecipeNode node, IngredientStack stack) {
            return node != null && scc.contains(node.getId()) && resource.matches(stack);
        }
    }

    public static Map<String, Double> computeNodeEfficiencies(FlowGraph graph) {
        Map<String, Double> effMap = new HashMap<>();
        if (graph == null) return effMap;
        graph.cleanupInvalidConnections();

        for (RecipeNode node : graph.getNodes()) {
            effMap.put(node.getId(), 1.0);
        }

        FlowEdgeAllocator.SolverContext context = FlowEdgeAllocator.SolverContext.create(graph);
        List<PrecomputedLoopMeta> loopMetas = precomputeLoopMetas(graph, context);
        List<PrecomputedDampedLoopMeta> dampedLoopMetas = precomputeDampedLoopMetas(graph, context);

        for (int iter = 0; iter < 10; iter++) {
            boolean changed = false;
            List<SelfSustainingLoop> loops = evaluateLoops(graph, loopMetas, effMap, context);

            for (RecipeNode consumer : graph.getNodes()) {
                double calculatedEff = computeConsumerEfficiency(graph, consumer, loops, dampedLoopMetas, effMap, context);
                double oldEff = effMap.get(consumer.getId());
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

        for (RecipeNode node : graph.getNodes()) {
            Double finalEff = effMap.get(node.getId());
            if (finalEff != null) {
                node.setEfficiency(finalEff);
            }
        }

        graph.invalidatePortStatsCache();

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
            List<PrecomputedDampedLoopMeta> dampedLoopMetas,
            Map<String, Double> effMap,
            FlowEdgeAllocator.SolverContext context
    ) {
        double minRatio = 1.0;
        boolean hasConnectedInput = false;

        for (int inIdx = 0; inIdx < consumer.getInputs().size(); inIdx++) {
            double portRatio = computePortRatio(graph, consumer, inIdx, loops, dampedLoopMetas, effMap, context);
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
            List<PrecomputedDampedLoopMeta> dampedLoopMetas,
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

        PrecomputedDampedLoopMeta matchingDampedMeta = findMatchingDampedLoop(consumer, inStack, dampedLoopMetas);
        double portRatio;
        if (matchingDampedMeta != null) {
            double totalIncomingSupply = computeIncomingSupply(graph, inEdges, effMap, context);
            double actualRatio = totalIncomingSupply / nominalInRate;
            double sExt = matchingDampedMeta.computeExternalSupply(graph, effMap, context);
            double sSteady = matchingDampedMeta.recirculationRatio() < 1.0 - 1e-4
                    ? sExt / (1.0 - matchingDampedMeta.recirculationRatio())
                    : sExt;
            double steadyRatio = matchingDampedMeta.nominalDemand() > 1e-5
                    ? (sSteady / matchingDampedMeta.nominalDemand())
                    : 1.0;
            portRatio = Math.min(steadyRatio, actualRatio);
        } else {
            double totalIncomingSupply = computeIncomingSupply(graph, inEdges, effMap, context);
            portRatio = totalIncomingSupply / nominalInRate;
            portRatio = applyLoopRelaxation(consumer, inStack, portRatio, loops);
        }

        if (inStack.isStressUnit() && portRatio < 0.9999) {
            return 0.0;
        }
        return portRatio;
    }

    private static PrecomputedDampedLoopMeta findMatchingDampedLoop(
            RecipeNode consumer,
            IngredientStack inStack,
            List<PrecomputedDampedLoopMeta> dampedLoopMetas
    ) {
        if (dampedLoopMetas == null || dampedLoopMetas.isEmpty()) return null;
        for (PrecomputedDampedLoopMeta meta : dampedLoopMetas) {
            if (meta.matches(consumer, inStack)) {
                return meta;
            }
        }
        return null;
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
            if (isStrictlyDampedScc(prodTotals, demTotals)) {
                continue;
            }

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

    private static boolean isStrictlyDampedScc(
            Map<SelfSustainingResource, Double> prodTotals,
            Map<SelfSustainingResource, Double> demTotals
    ) {
        boolean hasSurplus = false;
        boolean hasDamped = false;

        for (Map.Entry<SelfSustainingResource, Double> entry : demTotals.entrySet()) {
            double dem = entry.getValue();
            double prod = prodTotals.getOrDefault(entry.getKey(), 0.0);
            if (dem <= 0.0001) continue;

            if (prod >= dem + 0.001) {
                hasSurplus = true;
            } else if (prod < dem - 1e-4) {
                hasDamped = true;
            }
        }

        return hasDamped && !hasSurplus;
    }

    public static List<PrecomputedDampedLoopMeta> precomputeDampedLoopMetas(FlowGraph graph, FlowEdgeAllocator.SolverContext context) {
        List<PrecomputedDampedLoopMeta> result = new ArrayList<>();
        if (graph == null || graph.getNodes().isEmpty() || graph.getConnections().isEmpty()) {
            return result;
        }

        FlowEdgeAllocator.CachedEdgeIndex edgeIndex = context != null ? context.edgeIndex() : FlowEdgeAllocator.buildEdgeIndex(graph);
        List<Set<String>> sccs = ProcessStabilityAnalyzer.findStronglyConnectedComponents(graph, edgeIndex);
        for (Set<String> scc : sccs) {
            if (scc.size() < 2 && !ProcessStabilityAnalyzer.hasSelfLoop(graph, scc, edgeIndex)) {
                continue;
            }
            collectDampedMetasForScc(graph, scc, edgeIndex, context, result);
        }
        return result;
    }

    private static void collectDampedMetasForScc(
            FlowGraph graph,
            Set<String> scc,
            FlowEdgeAllocator.CachedEdgeIndex edgeIndex,
            FlowEdgeAllocator.SolverContext context,
            List<PrecomputedDampedLoopMeta> result
    ) {
        Map<SelfSustainingResource, Double> prodTotals = new HashMap<>();
        Map<SelfSustainingResource, Double> demTotals = new HashMap<>();
        accumulateLoopResourceTotals(graph, scc, prodTotals, demTotals, context);

        if (!isStrictlyDampedScc(prodTotals, demTotals)) {
            return;
        }

        for (Map.Entry<SelfSustainingResource, Double> entry : demTotals.entrySet()) {
            SelfSustainingResource res = entry.getKey();
            double dem = entry.getValue();
            double prod = prodTotals.getOrDefault(res, 0.0);
            if (dem <= 0.0001) continue;

            double ratio = prod / dem;
            if (ratio > 1e-5 && ratio < 1.0 - 1e-4) {
                List<FlowGraph.ConnectionEdge> extEdges = findExternalEdgesForResource(graph, scc, res, edgeIndex);
                result.add(new PrecomputedDampedLoopMeta(scc, res, ratio, dem, prod, extEdges));
            }
        }
    }

    public static List<FlowGraph.ConnectionEdge> findExternalEdgesForResource(
            FlowGraph graph,
            Set<String> scc,
            SelfSustainingResource res,
            FlowEdgeAllocator.CachedEdgeIndex edgeIndex
    ) {
        List<FlowGraph.ConnectionEdge> extEdges = new ArrayList<>();
        for (String nodeId : scc) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node == null || node.isReroute()) continue;
            collectExternalEdgesForNode(graph, node, scc, res, edgeIndex, extEdges);
        }
        return extEdges;
    }

    private static void collectExternalEdgesForNode(
            FlowGraph graph,
            RecipeNode node,
            Set<String> scc,
            SelfSustainingResource res,
            FlowEdgeAllocator.CachedEdgeIndex edgeIndex,
            List<FlowGraph.ConnectionEdge> extEdges
    ) {
        for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
            IngredientStack inStack = node.getInputs().get(inIdx);
            if (!res.matches(inStack)) continue;
            List<FlowGraph.ConnectionEdge> inEdges = findIncomingEdges(graph, node.getId(), inIdx, edgeIndex);
            for (FlowGraph.ConnectionEdge edge : inEdges) {
                if (!scc.contains(edge.fromNodeId())) {
                    extEdges.add(edge);
                }
            }
        }
    }

    public static PrecomputedDampedLoopMeta findDampedLoopMeta(FlowGraph graph, RecipeNode node, int inIdx) {
        if (graph == null || node == null || node.isReroute() || inIdx < 0 || inIdx >= node.getInputs().size()) {
            return null;
        }
        IngredientStack stack = node.getInputs().get(inIdx);
        List<PrecomputedDampedLoopMeta> metas = precomputeDampedLoopMetas(graph, null);
        for (PrecomputedDampedLoopMeta meta : metas) {
            if (meta.matches(node, stack)) {
                return meta;
            }
        }
        return null;
    }

    public static PrecomputedDampedLoopMeta findDampedLoopMetaForNode(FlowGraph graph, RecipeNode node) {
        if (graph == null || node == null || node.isReroute()) {
            return null;
        }
        List<PrecomputedDampedLoopMeta> metas = precomputeDampedLoopMetas(graph, null);
        for (PrecomputedDampedLoopMeta meta : metas) {
            if (meta.scc().contains(node.getId())) {
                return meta;
            }
        }
        return null;
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
