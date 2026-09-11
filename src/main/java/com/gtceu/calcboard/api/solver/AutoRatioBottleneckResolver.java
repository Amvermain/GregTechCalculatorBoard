package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Identifies and scales bottleneck producer nodes in flow graphs to satisfy consumer demands.
 */
public final class AutoRatioBottleneckResolver {

    public static final double MAX_SINGLE_SCALE_RATIO = 100.0;
    public static final double MAX_AUTO_RATIO_MACHINE_COUNT = 100_000.0;

    private AutoRatioBottleneckResolver() {}

    public static void resolveBottlenecksPass(
            FlowGraph graph,
            RecipeNode anchor,
            Set<String> upstreamNodes,
            Set<String> downstreamNodes,
            boolean integerCounts,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        FlowEdgeAllocator.CachedEdgeIndex edgeIndex = FlowEdgeAllocator.buildEdgeIndex(graph);
        Set<String> cyclicNodes = findCyclicNodeIds(graph, edgeIndex);

        int maxPasses = 10;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean changed = resolveGraphBottlenecks(graph, anchor, upstreamNodes, downstreamNodes, cyclicNodes, integerCounts, divergenceContext);
            if (!changed) {
                break;
            }
        }
        if (divergenceContext != null) {
            ProcessStabilityAnalyzer.checkUnresolvedLoopBottlenecks(graph, anchor, upstreamNodes, downstreamNodes, cyclicNodes, divergenceContext);
        }
    }

    private static boolean resolveGraphBottlenecks(
            FlowGraph graph,
            RecipeNode anchor,
            Set<String> upstreamNodes,
            Set<String> downstreamNodes,
            Set<String> cyclicNodes,
            boolean integerCounts,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        boolean changed = false;
        for (RecipeNode consumer : graph.getNodes()) {
            if (resolveConsumerBottlenecks(graph, consumer, anchor, upstreamNodes, downstreamNodes, cyclicNodes, integerCounts, divergenceContext)) {
                changed = true;
            }
        }
        return changed;
    }

    private static boolean resolveConsumerBottlenecks(
            FlowGraph graph,
            RecipeNode consumer,
            RecipeNode anchor,
            Set<String> upstreamNodes,
            Set<String> downstreamNodes,
            Set<String> cyclicNodes,
            boolean integerCounts,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        if (!ProcessStabilityAnalyzer.isEligibleBottleneckConsumer(consumer, anchor, upstreamNodes, downstreamNodes)) {
            return false;
        }
        boolean changed = false;
        int inputCount = consumer.isReroute() ? 1 : consumer.getInputs().size();
        for (int inIdx = 0; inIdx < inputCount; inIdx++) {
            if (resolvePortBottleneck(graph, consumer, inIdx, anchor, upstreamNodes, downstreamNodes, cyclicNodes, integerCounts, divergenceContext)) {
                changed = true;
            }
        }
        return changed;
    }

    private static boolean resolvePortBottleneck(
            FlowGraph graph,
            RecipeNode consumer,
            int inIdx,
            RecipeNode anchor,
            Set<String> upstreamNodes,
            Set<String> downstreamNodes,
            Set<String> cyclicNodes,
            boolean integerCounts,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        if (!ProcessStabilityAnalyzer.hasIncomingConnection(graph, consumer.getId(), inIdx)) {
            return false;
        }

        double singleInRate = ProcessStabilityAnalyzer.getConsumerPortInputRate(consumer, inIdx);
        double requiredDemand = ProcessStabilityAnalyzer.getConsumerPortDemand(consumer, inIdx);
        if (requiredDemand <= 1e-5) {
            return false;
        }

        double incomingSupply = AutoRatioFlowTraverser.calculateEffectiveIncomingSupply(graph, consumer, inIdx, null, true);
        if (incomingSupply >= requiredDemand - 1e-4) {
            return false;
        }

        Set<RecipeNode> skippedCyclicProducers = new LinkedHashSet<>();
        List<RecipeNode> validProducers = ProcessStabilityAnalyzer.findBottleneckProducers(graph, consumer, inIdx, anchor, downstreamNodes, cyclicNodes, skippedCyclicProducers);
        if (validProducers.isEmpty()) {
            if (!skippedCyclicProducers.isEmpty() && divergenceContext != null) {
                if (!ProcessStabilityAnalyzer.isIntegerRoundingDeficit(consumer, singleInRate, incomingSupply, requiredDemand)) {
                    divergenceContext.recordSuppressedRecirculation(consumer.getId(), skippedCyclicProducers);
                }
            }
            return false;
        }

        double scaleRatio = (incomingSupply > 1e-6)
                ? Math.min(MAX_SINGLE_SCALE_RATIO, requiredDemand / incomingSupply)
                : 2.0;

        return applyBottleneckScaling(graph, validProducers, scaleRatio, anchor, upstreamNodes, downstreamNodes, cyclicNodes, integerCounts, divergenceContext);
    }

    private static boolean applyBottleneckScaling(
            FlowGraph graph,
            List<RecipeNode> validProducers,
            double scaleRatio,
            RecipeNode anchor,
            Set<String> upstreamNodes,
            Set<String> downstreamNodes,
            Set<String> cyclicNodes,
            boolean integerCounts,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        boolean changed = false;
        for (RecipeNode p : validProducers) {
            double currentCount = p.getMachineCount();
            double rawScaled = currentCount * scaleRatio;
            if (rawScaled >= MAX_AUTO_RATIO_MACHINE_COUNT - 1e-4 && divergenceContext != null) {
                if (ProcessStabilityAnalyzer.isMicroYieldProducer(p)) {
                    divergenceContext.recordMicroYieldClamp(p.getId());
                } else {
                    divergenceContext.recordSafetyClamp(p.getId());
                }
            }
            double targetCount = Math.min(MAX_AUTO_RATIO_MACHINE_COUNT, rawScaled);
            double newCount = AutoRatioEngine.quantizeMachineCount(graph, p, targetCount, FlowBalanceMatrixSolver.CountRoundingMode.CEIL, integerCounts);

            if (newCount <= currentCount + 1e-4) continue;

            p.setMachineCount(newCount);
            changed = true;

            propagateBottleneckUpstream(graph, p, newCount, anchor, upstreamNodes, downstreamNodes, cyclicNodes, integerCounts, divergenceContext);
        }
        return changed;
    }

    private static void propagateBottleneckUpstream(
            FlowGraph graph,
            RecipeNode p,
            double newCount,
            RecipeNode anchor,
            Set<String> upstreamNodes,
            Set<String> downstreamNodes,
            Set<String> cyclicNodes,
            boolean integerCounts,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        Map<String, Double> upCounts = new HashMap<>();
        upCounts.put(p.getId(), newCount);
        AutoRatioEngine.solveUpstreamPassRestricted(graph, p, upCounts, upstreamNodes, downstreamNodes, integerCounts, divergenceContext);
        for (Map.Entry<String, Double> e : upCounts.entrySet()) {
            RecipeNode un = graph.findNodeById(e.getKey());
            if (un == null || un.getId().equals(anchor.getId())) continue;
            if (downstreamNodes != null && downstreamNodes.contains(un.getId())) continue;
            if (cyclicNodes != null && cyclicNodes.contains(un.getId())) continue;
            if (e.getValue() >= MAX_AUTO_RATIO_MACHINE_COUNT - 1e-4 && divergenceContext != null) {
                if (ProcessStabilityAnalyzer.isMicroYieldProducer(un)) {
                    divergenceContext.recordMicroYieldClamp(un.getId());
                } else {
                    divergenceContext.recordSafetyClamp(un.getId());
                }
            }
            un.setMachineCount(Math.min(MAX_AUTO_RATIO_MACHINE_COUNT, e.getValue()));
        }
    }

    public static Set<String> findCyclicNodeIds(FlowGraph graph, FlowEdgeAllocator.CachedEdgeIndex edgeIndex) {
        Set<String> cyclicNodes = new HashSet<>();
        List<Set<String>> sccs = ProcessStabilityAnalyzer.findStronglyConnectedComponents(graph, edgeIndex);
        for (Set<String> scc : sccs) {
            if (scc.size() > 1 || ProcessStabilityAnalyzer.hasSelfLoop(graph, scc, edgeIndex)) {
                cyclicNodes.addAll(scc);
            }
        }
        return cyclicNodes;
    }
}
