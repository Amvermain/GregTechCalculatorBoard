package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.*;

/**
 * High-level facade for flow balance calculations, AutoRatio BFS propagation,
 * divergence defense, edge allocation, and harmonized ratio optimization.
 * Delegates specialized operations to:
 * <ul>
 *     <li>{@link AutoRatioEngine}: BFS propagation, upstream/downstream passes, bottleneck solving</li>
 *     <li>{@link ProcessStabilityAnalyzer}: Cycle gain analysis, divergence detection, contextual guidance</li>
 *     <li>{@link FlowEdgeAllocator}: Outgoing edge allocation, effective port flow rates, graph indexing</li>
 *     <li>{@link FixedPointEfficiencySolver}: Iterative efficiency convergence and loop self-sufficiency</li>
 *     <li>{@link HarmonizedRatioOptimizer}: Harmonized integer scaling and shared machine pool ratios</li>
 * </ul>
 */
public final class FlowBalanceMatrixSolver {

    public enum CountRoundingMode {
        FLOOR,
        CEIL,
        ROUND
    }

    public static final class DivergenceContext {
        private final ProcessStabilityAnalyzer.DivergenceContext delegate = new ProcessStabilityAnalyzer.DivergenceContext();

        public void recordSuppressedRecirculation(String consumerId, Collection<RecipeNode> cyclicProducers) {
            delegate.recordSuppressedRecirculation(consumerId, cyclicProducers);
        }

        public void recordPositiveFeedback(String consumerId, Collection<RecipeNode> cyclicProducers) {
            delegate.recordPositiveFeedback(consumerId, cyclicProducers);
        }

        public void recordCatalystDecay(String consumerId, Collection<RecipeNode> cyclicProducers) {
            delegate.recordCatalystDecay(consumerId, cyclicProducers);
        }

        public void recordAnchorConflict(String nodeId) {
            delegate.recordAnchorConflict(nodeId);
        }

        public void clearNodeDivergence(String nodeId) {
            delegate.clearNodeDivergence(nodeId);
        }

        public void recordSafetyClamp(String nodeId) {
            delegate.recordSafetyClamp(nodeId);
        }

        public void recordMicroYieldClamp(String nodeId) {
            delegate.recordMicroYieldClamp(nodeId);
        }

        public Set<String> getDivergentNodeIds() {
            return delegate.getDivergentNodeIds();
        }

        public String getReason(String nodeId) {
            return delegate.getReason(nodeId);
        }

        public boolean isClampedBySafetyLimit() {
            return delegate.isClampedBySafetyLimit();
        }

        public ProcessStabilityAnalyzer.DivergenceContext getInternalDelegate() {
            return delegate;
        }
    }

    public static final double MAX_SINGLE_SCALE_RATIO = AutoRatioEngine.MAX_SINGLE_SCALE_RATIO;
    public static final double MAX_AUTO_RATIO_MACHINE_COUNT = AutoRatioEngine.MAX_AUTO_RATIO_MACHINE_COUNT;

    private FlowBalanceMatrixSolver() {}

    public static double quantizeMachineCount(
            FlowGraph graph,
            RecipeNode node,
            double rawCount,
            CountRoundingMode mode,
            boolean integerCounts
    ) {
        return AutoRatioEngine.quantizeMachineCount(graph, node, rawCount, mode, integerCounts);
    }

    public static AutoRatioResult autoRatioFromAnchor(FlowGraph graph, RecipeNode anchor, boolean integerCounts) {
        return AutoRatioEngine.autoRatioFromAnchor(graph, anchor, integerCounts);
    }

    public static void solveUpstreamPassRestricted(FlowGraph graph, RecipeNode anchor, Map<String, Double> countsMap, Set<String> allowedUpstreamNodes, boolean integerCounts) {
        AutoRatioEngine.solveUpstreamPassRestricted(graph, anchor, countsMap, allowedUpstreamNodes, integerCounts);
    }

    public static void solveUpstreamPassRestricted(FlowGraph graph, RecipeNode anchor, Map<String, Double> countsMap, Set<String> allowedUpstreamNodes, Set<String> downstreamNodes, boolean integerCounts) {
        AutoRatioEngine.solveUpstreamPassRestricted(graph, anchor, countsMap, allowedUpstreamNodes, downstreamNodes, integerCounts);
    }

    public static void solveUpstreamPassRestricted(
            FlowGraph graph,
            RecipeNode anchor,
            Map<String, Double> countsMap,
            Set<String> allowedUpstreamNodes,
            Set<String> downstreamNodes,
            boolean integerCounts,
            DivergenceContext divergenceContext
    ) {
        AutoRatioEngine.solveUpstreamPassRestricted(
                graph,
                anchor,
                countsMap,
                allowedUpstreamNodes,
                downstreamNodes,
                integerCounts,
                divergenceContext != null ? divergenceContext.getInternalDelegate() : null
        );
    }

    public static double calculateTotalConnectedPortDemand(FlowGraph graph, RecipeNode producer, int outputIndex) {
        return AutoRatioEngine.calculateTotalConnectedPortDemand(graph, producer, outputIndex);
    }

    public static double calculateTotalConnectedPortDemand(FlowGraph graph, RecipeNode producer, int outputIndex, Map<String, Double> countsMap) {
        return AutoRatioEngine.calculateTotalConnectedPortDemand(graph, producer, outputIndex, countsMap);
    }

    public static double calculateEffectiveIncomingSupply(FlowGraph graph, RecipeNode consumer, int inIdx, Map<String, Double> countsMap) {
        return AutoRatioEngine.calculateEffectiveIncomingSupply(graph, consumer, inIdx, countsMap);
    }

    public static double calculateEffectiveIncomingSupply(FlowGraph graph, RecipeNode consumer, int inIdx, Map<String, Double> countsMap, boolean demandProportional) {
        return AutoRatioEngine.calculateEffectiveIncomingSupply(graph, consumer, inIdx, countsMap, demandProportional);
    }

    public static boolean isPortDrivenByDownstreamChain(FlowGraph graph, String consumerId, int inIdx, String anchorId, Map<String, Double> countsMap) {
        return AutoRatioEngine.isPortDrivenByDownstreamChain(graph, consumerId, inIdx, anchorId, countsMap);
    }

    public static void solveDownstreamPassRestricted(FlowGraph graph, RecipeNode anchor, Map<String, Double> countsMap, Set<String> allowedDownstreamNodes, boolean integerCounts) {
        AutoRatioEngine.solveDownstreamPassRestricted(graph, anchor, countsMap, allowedDownstreamNodes, integerCounts);
    }

    public static void resolveBottlenecksPass(FlowGraph graph, RecipeNode anchor, Set<String> upstreamNodes, Set<String> downstreamNodes, boolean integerCounts) {
        AutoRatioEngine.resolveBottlenecksPass(graph, anchor, upstreamNodes, downstreamNodes, integerCounts);
    }

    public static void resolveBottlenecksPass(
            FlowGraph graph,
            RecipeNode anchor,
            Set<String> upstreamNodes,
            Set<String> downstreamNodes,
            boolean integerCounts,
            DivergenceContext divergenceContext
    ) {
        AutoRatioEngine.resolveBottlenecksPass(
                graph,
                anchor,
                upstreamNodes,
                downstreamNodes,
                integerCounts,
                divergenceContext != null ? divergenceContext.getInternalDelegate() : null
        );
    }

    public static Set<String> findUnfedDeficitLoopNodeIds(FlowGraph graph) {
        return ProcessStabilityAnalyzer.findUnfedDeficitLoopNodeIds(graph);
    }

    public static void detectUnfedDeficitLoops(
            FlowGraph graph,
            RecipeNode anchor,
            DivergenceContext divergenceContext
    ) {
        ProcessStabilityAnalyzer.detectUnfedDeficitLoops(
                graph,
                anchor,
                divergenceContext != null ? divergenceContext.getInternalDelegate() : null
        );
    }

    public static double getEffectiveProducerOutputRate(FlowGraph graph, RecipeNode producer, int outputIndex) {
        return FlowEdgeAllocator.getEffectiveProducerOutputRate(graph, producer, outputIndex);
    }

    public static Map<FlowGraph.ConnectionEdge, Double> calculateOutgoingEdgeAllocations(
            FlowGraph graph, RecipeNode producer, int outputIndex, double totalProducerRate) {
        return FlowEdgeAllocator.calculateOutgoingEdgeAllocations(graph, producer, outputIndex, totalProducerRate);
    }

    public static Map<FlowGraph.ConnectionEdge, Double> calculateOutgoingEdgeAllocations(
            FlowGraph graph, RecipeNode producer, int outputIndex, double totalProducerRate, Map<String, Double> effMap) {
        return FlowEdgeAllocator.calculateOutgoingEdgeAllocations(graph, producer, outputIndex, totalProducerRate, effMap);
    }

    public static Map<FlowGraph.ConnectionEdge, Double> calculateOutgoingEdgeAllocations(
            FlowGraph graph, RecipeNode producer, int outputIndex, double totalProducerRate, Map<String, Double> effMap, FlowEdgeAllocator.CachedEdgeIndex edgeIndex) {
        return FlowEdgeAllocator.calculateOutgoingEdgeAllocations(graph, producer, outputIndex, totalProducerRate, effMap, edgeIndex);
    }

    public static Map<FlowGraph.ConnectionEdge, Double> calculateOutgoingEdgeAllocations(
            FlowGraph graph, RecipeNode producer, int outputIndex, double totalProducerRate, Map<String, Double> effMap, FlowEdgeAllocator.SolverContext context) {
        return FlowEdgeAllocator.calculateOutgoingEdgeAllocations(graph, producer, outputIndex, totalProducerRate, effMap, context);
    }

    public static double getEdgeAllocatedFlow(FlowGraph graph, FlowGraph.ConnectionEdge targetEdge, Map<String, Double> effMap) {
        return FlowEdgeAllocator.getEdgeAllocatedFlow(graph, targetEdge, effMap);
    }

    public static double getEdgeAllocatedFlow(FlowGraph graph, FlowGraph.ConnectionEdge targetEdge, Map<String, Double> effMap, Set<String> visited) {
        return FlowEdgeAllocator.getEdgeAllocatedFlow(graph, targetEdge, effMap, visited);
    }

    public static double getEdgeAllocatedFlow(
            FlowGraph graph,
            FlowGraph.ConnectionEdge targetEdge,
            Map<String, Double> effMap,
            Set<String> visited,
            FlowEdgeAllocator.CachedEdgeIndex edgeIndex
    ) {
        return FlowEdgeAllocator.getEdgeAllocatedFlow(graph, targetEdge, effMap, visited, edgeIndex);
    }

    public static double getEdgeAllocatedFlow(
            FlowGraph graph,
            FlowGraph.ConnectionEdge targetEdge,
            Map<String, Double> effMap,
            Set<String> visited,
            FlowEdgeAllocator.SolverContext context
    ) {
        return FlowEdgeAllocator.getEdgeAllocatedFlow(graph, targetEdge, effMap, visited, context);
    }

    public static double getEffectiveProducerOutputRate(FlowGraph graph, RecipeNode producer, int outputIndex, Map<String, Double> effMap) {
        return FlowEdgeAllocator.getEffectiveProducerOutputRate(graph, producer, outputIndex, effMap);
    }

    public static double getEffectiveProducerOutputRate(FlowGraph graph, RecipeNode producer, int outputIndex, Map<String, Double> effMap, Set<String> visited) {
        return FlowEdgeAllocator.getEffectiveProducerOutputRate(graph, producer, outputIndex, effMap, visited);
    }

    public static double getEffectiveProducerOutputRate(
            FlowGraph graph,
            RecipeNode producer,
            int outputIndex,
            Map<String, Double> effMap,
            Set<String> visited,
            FlowEdgeAllocator.CachedEdgeIndex edgeIndex
    ) {
        return FlowEdgeAllocator.getEffectiveProducerOutputRate(graph, producer, outputIndex, effMap, visited, edgeIndex);
    }

    public static double getEffectiveProducerOutputRate(
            FlowGraph graph,
            RecipeNode producer,
            int outputIndex,
            Map<String, Double> effMap,
            Set<String> visited,
            FlowEdgeAllocator.SolverContext context
    ) {
        return FlowEdgeAllocator.getEffectiveProducerOutputRate(graph, producer, outputIndex, effMap, visited, context);
    }

    public static double getConnectedConsumerDemand(FlowGraph graph, RecipeNode consumer, int inputIndex) {
        return FlowEdgeAllocator.getConnectedConsumerDemand(graph, consumer, inputIndex);
    }

    public static double getConnectedConsumerDemand(FlowGraph graph, RecipeNode consumer, int inputIndex, Map<String, Double> effMap) {
        return FlowEdgeAllocator.getConnectedConsumerDemand(graph, consumer, inputIndex, effMap);
    }

    public static double getConnectedConsumerDemand(FlowGraph graph, RecipeNode consumer, int inputIndex, Map<String, Double> effMap, FlowEdgeAllocator.SolverContext context) {
        return FlowEdgeAllocator.getConnectedConsumerDemand(graph, consumer, inputIndex, effMap, context);
    }

    public static FlowEdgeAllocator.CachedEdgeIndex buildEdgeIndex(FlowGraph graph) {
        return FlowEdgeAllocator.buildEdgeIndex(graph);
    }

    public static FlowEdgeAllocator.CachedPortRates buildPortRates(FlowGraph graph) {
        return FlowEdgeAllocator.buildPortRates(graph);
    }

    public static Map<String, Double> computeNodeEfficiencies(FlowGraph graph) {
        return FixedPointEfficiencySolver.computeNodeEfficiencies(graph);
    }

    public static void optimizeMaxThroughput(FlowGraph graph, boolean preferParallels, boolean integerCounts) {
        HarmonizedRatioOptimizer.optimizeMaxThroughput(graph, preferParallels, integerCounts);
    }

    public static double calculateConsumerMatchCount(FlowGraph graph, RecipeNode producer, int outPortIdx, RecipeNode consumer, int inPortIdx) {
        return HarmonizedRatioOptimizer.calculateConsumerMatchCount(graph, producer, outPortIdx, consumer, inPortIdx);
    }

    public static double calculateProducerMatchCount(FlowGraph graph, RecipeNode producer, int outPortIdx, RecipeNode consumer, int inPortIdx) {
        return HarmonizedRatioOptimizer.calculateProducerMatchCount(graph, producer, outPortIdx, consumer, inPortIdx);
    }

    public static double findPerfectHarmonizedAnchorCount(FlowGraph graph, RecipeNode anchor) {
        return HarmonizedRatioOptimizer.findPerfectHarmonizedAnchorCount(graph, anchor);
    }

    public static AutoRatioResult autoRatioHarmonized(FlowGraph graph, RecipeNode anchor) {
        return HarmonizedRatioOptimizer.autoRatioHarmonized(graph, anchor);
    }

    public static int autoRatioFromSharedPool(FlowGraph graph, CanvasGroupFrame poolFrame, double targetMachines, AutoRatioMode mode) {
        return HarmonizedRatioOptimizer.autoRatioFromSharedPool(graph, poolFrame, targetMachines, mode);
    }
}
