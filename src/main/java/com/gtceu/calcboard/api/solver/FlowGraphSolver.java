package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.Map;

/**
 * Pure calculation & graph algorithm solver facade for Calculator Board.
 * Delegates specialized operations to:
 * - {@link FlowGraphTopologyAnalyzer}: Topology traversal, upstream/downstream subgraph analysis
 * - {@link FlowBalanceMatrixSolver}: AutoRatio BFS propagation, bottleneck solving, fixed-point efficiency, harmonized scaling
 * - {@link FlowSummaryAggregator}: Balance summary computation, port flow statistics, total energy deltas
 */
public final class FlowGraphSolver {

    private FlowGraphSolver() {}

    /**
     * Port flow statistics record for inputs and outputs.
     */
    public record PortFlowStats(
        double requiredOrProducedRate,
        double connectedRate,
        int connectionCount,
        boolean isConnected
    ) {
        public double getRatio() {
            if (requiredOrProducedRate <= 0.0001) return 1.0;
            return connectedRate / requiredOrProducedRate;
        }

        public double getPercent() {
            return getRatio() * 100.0;
        }

        public boolean isBalanced() {
            return isConnected && Math.abs(connectedRate - requiredOrProducedRate) <= 0.001;
        }

        public boolean isInputDeficit() {
            return isConnected && connectedRate < requiredOrProducedRate - 0.001;
        }

        public boolean isInputSurplus() {
            return isConnected && connectedRate > requiredOrProducedRate + 0.001;
        }

        public boolean isOutputSurplus() {
            return isConnected && connectedRate < requiredOrProducedRate - 0.001;
        }

        public boolean isOutputDeficit() {
            return isConnected && connectedRate > requiredOrProducedRate + 0.001;
        }

        public boolean isDeficit() {
            return isInputDeficit();
        }

        public boolean isSurplus() {
            return isInputSurplus();
        }
    }

    /**
     * Propagates machine counts across the graph starting from the anchor node.
     */
    public static void autoRatioFromAnchor(FlowGraph graph, RecipeNode anchor, boolean integerCounts) {
        FlowBalanceMatrixSolver.autoRatioFromAnchor(graph, anchor, integerCounts);
    }

    /**
     * Computes the bottleneck-constrained operating efficiency for every node in the graph.
     */
    public static Map<String, Double> computeNodeEfficiencies(FlowGraph graph) {
        return FlowBalanceMatrixSolver.computeNodeEfficiencies(graph);
    }

    /**
     * Obtains input port flow statistics.
     */
    public static PortFlowStats getInputPortStats(FlowGraph graph, RecipeNode node, int inputIndex) {
        return FlowSummaryAggregator.getInputPortStats(graph, node, inputIndex);
    }

    /**
     * Obtains output port flow statistics.
     */
    public static PortFlowStats getOutputPortStats(FlowGraph graph, RecipeNode node, int outputIndex) {
        return FlowSummaryAggregator.getOutputPortStats(graph, node, outputIndex);
    }

    /**
     * Solves the overall graph and computes total power, raw ingredients, net outputs, and byproducts.
     */
    public static BalanceSummary computeSummary(FlowGraph graph) {
        return FlowSummaryAggregator.computeSummary(graph);
    }

    /**
     * Computes the balance summary using existing node efficiencies and port states without re-evaluating efficiencies.
     */
    public static BalanceSummary computeSummaryPreservingEfficiencies(FlowGraph graph) {
        return FlowSummaryAggregator.computeSummaryPreservingEfficiencies(graph);
    }

    /**
     * Optimizes all node tiers and machine counts for maximum throughput.
     */
    public static void optimizeMaxThroughput(FlowGraph graph, boolean preferParallels, boolean integerCounts) {
        FlowBalanceMatrixSolver.optimizeMaxThroughput(graph, preferParallels, integerCounts);
    }

    /**
     * Calculates the optimal consumer machine count for Shift-Drag connection (Floor matching).
     */
    public static double calculateConsumerMatchCount(FlowGraph graph, RecipeNode producer, int outPortIdx, RecipeNode consumer, int inPortIdx) {
        return FlowBalanceMatrixSolver.calculateConsumerMatchCount(graph, producer, outPortIdx, consumer, inPortIdx);
    }

    /**
     * Calculates the optimal producer machine count for Shift-Drag connection (Ceil matching).
     */
    public static double calculateProducerMatchCount(FlowGraph graph, RecipeNode producer, int outPortIdx, RecipeNode consumer, int inPortIdx) {
        return FlowBalanceMatrixSolver.calculateProducerMatchCount(graph, producer, outPortIdx, consumer, inPortIdx);
    }

    /**
     * Finds a practical, compact anchor machine count (capped at 16x) that minimizes rounding inefficiency.
     */
    public static double findPerfectHarmonizedAnchorCount(FlowGraph graph, RecipeNode anchor) {
        return FlowBalanceMatrixSolver.findPerfectHarmonizedAnchorCount(graph, anchor);
    }

    /**
     * Executes Harmonized Auto-Ratio: scales the anchor and all upstream/downstream machines
     * to the minimal clean integer ratio with zero waste/bottleneck.
     */
    public static void autoRatioHarmonized(FlowGraph graph, RecipeNode anchor) {
        FlowBalanceMatrixSolver.autoRatioHarmonized(graph, anchor);
    }
}


