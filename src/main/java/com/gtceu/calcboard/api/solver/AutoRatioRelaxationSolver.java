package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Executes iterative relaxation passes and cycle divergence checks for flow graphs.
 */
public final class AutoRatioRelaxationSolver {

    private AutoRatioRelaxationSolver() {}

    public static void executeRelaxationPasses(
            FlowGraph graph,
            RecipeNode anchor,
            double targetAnchorCount,
            Set<String> upstreamNodes,
            Set<String> downstreamNodes,
            Set<String> cyclicNodes,
            boolean integerCounts,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        boolean isEligible = isAnchorCycleRelaxationEligible(graph, anchor, downstreamNodes, upstreamNodes, cyclicNodes, divergenceContext);
        int maxPasses = isEligible ? Math.max(25, (cyclicNodes != null ? cyclicNodes.size() : 5) * 4) : 1;
        double prevDelta = Double.MAX_VALUE;
        int compoundingExplosionCount = 0;

        for (int pass = 0; pass < maxPasses; pass++) {
            Map<String, Double> countsBeforePass = captureNodeCounts(graph);

            executeSingleRelaxationPass(graph, anchor, targetAnchorCount, upstreamNodes, downstreamNodes, integerCounts, divergenceContext);

            if (!isEligible || (divergenceContext != null && divergenceContext.isClampedBySafetyLimit())) {
                break;
            }

            double currentDelta = computeMaxDelta(graph, countsBeforePass);
            if (currentDelta < 1e-4) {
                break;
            }

            if (isDeltaExploding(currentDelta, prevDelta)) {
                compoundingExplosionCount++;
                if (compoundingExplosionCount >= 3) {
                    recordExpandingDivergence(graph, cyclicNodes, divergenceContext);
                    break;
                }
            } else {
                compoundingExplosionCount = 0;
            }

            prevDelta = currentDelta;
        }
    }

    private static boolean isAnchorCycleRelaxationEligible(
            FlowGraph graph,
            RecipeNode anchor,
            Set<String> downstreamNodes,
            Set<String> upstreamNodes,
            Set<String> cyclicNodes,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        if (cyclicNodes == null || cyclicNodes.isEmpty()) {
            return false;
        }
        if (cyclicNodes.contains(anchor.getId())) {
            return true;
        }
        if (hasBalancedCycleNode(upstreamNodes, cyclicNodes, divergenceContext)) {
            return true;
        }
        if (hasBalancedCycleNode(downstreamNodes, cyclicNodes, divergenceContext)) {
            return true;
        }
        return hasCrossCouplingCycle(graph, anchor, downstreamNodes, upstreamNodes);
    }

    private static boolean hasBalancedCycleNode(
            Set<String> nodeIds,
            Set<String> cyclicNodes,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        if (nodeIds == null || cyclicNodes == null) return false;
        for (String id : nodeIds) {
            if (!cyclicNodes.contains(id)) continue;
            if (divergenceContext != null && divergenceContext.getDivergentNodeIds().contains(id)) continue;
            return true;
        }
        return false;
    }

    private static boolean hasCrossCouplingCycle(
            FlowGraph graph,
            RecipeNode anchor,
            Set<String> downstreamNodes,
            Set<String> upstreamNodes
    ) {
        if (downstreamNodes == null || downstreamNodes.isEmpty()) {
            return false;
        }
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (downstreamNodes.contains(edge.fromNodeId())) {
                String toId = edge.toNodeId();
                if (toId.equals(anchor.getId()) || (upstreamNodes != null && upstreamNodes.contains(toId))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void executeSingleRelaxationPass(
            FlowGraph graph,
            RecipeNode anchor,
            double targetAnchorCount,
            Set<String> upstreamNodes,
            Set<String> downstreamNodes,
            boolean integerCounts,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        Map<String, Double> countsMap = new HashMap<>();
        countsMap.put(anchor.getId(), targetAnchorCount);

        AutoRatioEngine.solveDownstreamPassRestricted(graph, anchor, countsMap, downstreamNodes, integerCounts);
        applyCalculatedCounts(graph, countsMap, downstreamNodes);

        AutoRatioEngine.solveUpstreamPassRestricted(graph, anchor, countsMap, upstreamNodes, downstreamNodes, integerCounts, divergenceContext);
        applyCalculatedCounts(graph, countsMap, upstreamNodes);

        anchor.setMachineCount(targetAnchorCount);
    }

    private static void applyCalculatedCounts(FlowGraph graph, Map<String, Double> countsMap, Set<String> targetNodeIds) {
        if (targetNodeIds == null || countsMap == null) return;
        for (String id : targetNodeIds) {
            RecipeNode n = graph.findNodeById(id);
            if (n != null && countsMap.containsKey(id)) {
                n.setMachineCount(countsMap.get(id));
            }
        }
    }

    private static Map<String, Double> captureNodeCounts(FlowGraph graph) {
        Map<String, Double> snapshot = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            snapshot.put(n.getId(), n.getMachineCount());
        }
        return snapshot;
    }

    private static double computeMaxDelta(FlowGraph graph, Map<String, Double> previousCounts) {
        double maxDelta = 0.0;
        for (RecipeNode n : graph.getNodes()) {
            double prev = previousCounts.getOrDefault(n.getId(), 0.0);
            double delta = Math.abs(n.getMachineCount() - prev);
            if (delta > maxDelta) {
                maxDelta = delta;
            }
        }
        return maxDelta;
    }

    private static boolean isDeltaExploding(double currentDelta, double prevDelta) {
        return currentDelta > 200.0 && currentDelta > prevDelta * 2.0;
    }

    private static void recordExpandingDivergence(
            FlowGraph graph,
            Set<String> cyclicNodes,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        if (divergenceContext == null || cyclicNodes == null || cyclicNodes.isEmpty()) return;
        List<RecipeNode> cyclicList = new ArrayList<>();
        for (String id : cyclicNodes) {
            RecipeNode n = graph.findNodeById(id);
            if (n != null) cyclicList.add(n);
        }
        if (cyclicList.isEmpty()) return;
        divergenceContext.recordPositiveFeedback(cyclicList.get(0).getId(), cyclicList);
    }

    public static void normalizeNodeCounts(FlowGraph graph, RecipeNode anchor, double targetAnchorCount, boolean integerCounts) {
        for (RecipeNode n : graph.getNodes()) {
            if (n.getId().equals(anchor.getId())) {
                n.setMachineCount(targetAnchorCount);
                continue;
            }
            if (n.isReroute()) {
                n.setMachineCount(1.0);
                continue;
            }
            n.setMachineCount(AutoRatioEngine.quantizeMachineCount(graph, n, n.getMachineCount(), FlowBalanceMatrixSolver.CountRoundingMode.CEIL, integerCounts));
        }
        anchor.setMachineCount(targetAnchorCount);
    }
}
