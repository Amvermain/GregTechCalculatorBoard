package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.event.FlowGraphEvent;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraftforge.common.MinecraftForge;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

/**
 * Propagates machine counts across the graph via BFS passes (upstream, downstream, bottleneck resolution),
 * enforcing integer quantization and safety scaling bounds.
 * Delegated to AutoRatioFlowTraverser, AutoRatioBottleneckResolver, and AutoRatioRelaxationSolver.
 */
public final class AutoRatioEngine {

    public static final double MAX_SINGLE_SCALE_RATIO = 100.0;
    public static final double MAX_AUTO_RATIO_MACHINE_COUNT = 100_000.0;

    private AutoRatioEngine() {}

    public static double quantizeMachineCount(
            FlowGraph graph,
            RecipeNode node,
            double rawCount,
            FlowBalanceMatrixSolver.CountRoundingMode mode,
            boolean integerCounts
    ) {
        if (graph != null && graph.isNodeInSharedMachineFrame(node)) {
            return Math.max(0.0001, Math.round(rawCount * 10000.0) / 10000.0);
        }
        if (!integerCounts) {
            return Math.max(0.0001, Math.round(rawCount * 10000.0) / 10000.0);
        }
        return switch (mode) {
            case FLOOR -> Math.max(1.0, Math.floor(rawCount + 1e-6));
            case CEIL -> Math.max(1.0, Math.ceil(rawCount - 1e-6));
            case ROUND -> Math.max(1.0, (double) Math.round(rawCount));
        };
    }

    public static AutoRatioResult autoRatioFromAnchor(FlowGraph graph, RecipeNode anchor, boolean integerCounts) {
        if (graph == null || anchor == null || graph.getNodes().isEmpty()) {
            return new AutoRatioResult(0, Collections.emptySet(), false);
        }
        postPreSolveEvent(graph);
        graph.cleanupInvalidConnections();
        ProcessStabilityAnalyzer.clearDivergenceWarnings(graph);

        Map<String, Double> initialBaseNodeCounts = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            if (n.isBaseNode() && !n.getId().equals(anchor.getId())) {
                initialBaseNodeCounts.put(n.getId(), n.getMachineCount());
            }
        }

        double currentAnchorCount = anchor.getMachineCount();
        boolean isAnchorAlreadyFractional = Math.abs(currentAnchorCount - Math.round(currentAnchorCount)) > 1e-4;
        boolean preserveFractional = isAnchorAlreadyFractional && com.gtceu.calcboard.api.storage.BoardManager.getInstance().isPreserveFractionalAnchor();

        double targetAnchorCount;
        if (anchor.isReroute()) {
            targetAnchorCount = 1.0;
        } else if (!integerCounts || preserveFractional) {
            targetAnchorCount = Math.max(0.0001, Math.round(currentAnchorCount * 10000.0) / 10000.0);
        } else {
            targetAnchorCount = quantizeMachineCount(graph, anchor, currentAnchorCount, FlowBalanceMatrixSolver.CountRoundingMode.CEIL, true);
        }
        anchor.setMachineCount(targetAnchorCount);

        ProcessStabilityAnalyzer.DivergenceContext divergenceContext = new ProcessStabilityAnalyzer.DivergenceContext();
        ProcessStabilityAnalyzer.detectConflictingAnchors(graph, initialBaseNodeCounts, divergenceContext);

        if (!divergenceContext.getDivergentNodeIds().isEmpty()) {
            ProcessStabilityAnalyzer.applyDivergenceWarnings(graph, divergenceContext);
            postSolveEvent(graph);
            return new AutoRatioResult(graph.getNodes().size(), Collections.unmodifiableSet(divergenceContext.getDivergentNodeIds()), false);
        }

        var linearResult = com.gtceu.calcboard.api.solver.linear.TwoStageLinearFlowSolver.solve(graph, anchor, integerCounts);
        if (linearResult.successful() && !linearResult.underDetermined() && !linearResult.machineCounts().isEmpty()) {
            boolean hasClamp = applyLinearCountsAndCheckClamping(graph, linearResult.machineCounts(), divergenceContext);
            anchor.setMachineCount(targetAnchorCount);
            if (hasClamp) {
                ProcessStabilityAnalyzer.applyDivergenceWarnings(graph, divergenceContext);
            }
            postSolveEvent(graph);
            return new AutoRatioResult(
                    graph.getNodes().size(),
                    Collections.unmodifiableSet(divergenceContext.getDivergentNodeIds()),
                    divergenceContext.isClampedBySafetyLimit()
            );
        }

        Set<String> directAnchorSuppliers = FlowGraphTopologyAnalyzer.getDirectSuppliers(graph, anchor.getId());
        Set<String> downstreamNodes = FlowGraphTopologyAnalyzer.findDownstreamNodes(graph, anchor.getId(), directAnchorSuppliers);
        Set<String> upstreamNodes = FlowGraphTopologyAnalyzer.findUpstreamNodes(graph, anchor.getId(), downstreamNodes);

        divergenceContext = new ProcessStabilityAnalyzer.DivergenceContext();
        ProcessStabilityAnalyzer.detectUnfedDeficitLoops(graph, anchor, divergenceContext);

        FlowEdgeAllocator.CachedEdgeIndex edgeIndex = FlowEdgeAllocator.buildEdgeIndex(graph);
        Set<String> cyclicNodes = findCyclicNodeIds(graph, edgeIndex);

        AutoRatioRelaxationSolver.executeRelaxationPasses(
                graph,
                anchor,
                targetAnchorCount,
                upstreamNodes,
                downstreamNodes,
                cyclicNodes,
                integerCounts,
                divergenceContext
        );

        resolveBottlenecksPass(graph, anchor, upstreamNodes, downstreamNodes, integerCounts, divergenceContext);
        ProcessStabilityAnalyzer.detectConflictingAnchors(graph, initialBaseNodeCounts, divergenceContext);
        anchor.setMachineCount(targetAnchorCount);
        AutoRatioRelaxationSolver.normalizeNodeCounts(graph, anchor, targetAnchorCount, integerCounts);

        ProcessStabilityAnalyzer.reconcileLoopDivergences(graph, anchor, divergenceContext, integerCounts);
        ProcessStabilityAnalyzer.applyDivergenceWarnings(graph, divergenceContext);
        postSolveEvent(graph);

        return new AutoRatioResult(
                graph.getNodes().size(),
                Collections.unmodifiableSet(divergenceContext.getDivergentNodeIds()),
                divergenceContext.isClampedBySafetyLimit()
        );
    }

    private static boolean applyLinearCountsAndCheckClamping(
            FlowGraph graph,
            Map<String, Double> machineCounts,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        boolean hasClamp = false;
        for (Map.Entry<String, Double> entry : machineCounts.entrySet()) {
            RecipeNode n = graph.findNodeById(entry.getKey());
            if (n == null) continue;
            double count = entry.getValue();
            if (count >= MAX_AUTO_RATIO_MACHINE_COUNT - 1e-4) {
                hasClamp = true;
                recordClampDivergence(n, divergenceContext);
                count = MAX_AUTO_RATIO_MACHINE_COUNT;
            }
            n.setMachineCount(count);
        }
        return hasClamp;
    }

    private static void recordClampDivergence(RecipeNode node, ProcessStabilityAnalyzer.DivergenceContext divergenceContext) {
        if (ProcessStabilityAnalyzer.isMicroYieldProducer(node)) {
            divergenceContext.recordMicroYieldClamp(node.getId());
        } else {
            divergenceContext.recordSafetyClamp(node.getId());
        }
    }

    public static void solveUpstreamPassRestricted(FlowGraph graph, RecipeNode anchor, Map<String, Double> countsMap, Set<String> allowedUpstreamNodes, boolean integerCounts) {
        solveUpstreamPassRestricted(graph, anchor, countsMap, allowedUpstreamNodes, null, integerCounts, null);
    }

    public static void solveUpstreamPassRestricted(FlowGraph graph, RecipeNode anchor, Map<String, Double> countsMap, Set<String> allowedUpstreamNodes, Set<String> downstreamNodes, boolean integerCounts) {
        solveUpstreamPassRestricted(graph, anchor, countsMap, allowedUpstreamNodes, downstreamNodes, integerCounts, null);
    }

    public static void solveUpstreamPassRestricted(
            FlowGraph graph,
            RecipeNode anchor,
            Map<String, Double> countsMap,
            Set<String> allowedUpstreamNodes,
            Set<String> downstreamNodes,
            boolean integerCounts,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        Queue<RecipeNode> upQueue = new ArrayDeque<>();
        upQueue.add(anchor);
        if (downstreamNodes != null) {
            for (String downId : downstreamNodes) {
                RecipeNode dn = graph.findNodeById(downId);
                if (dn != null && (!dn.isReroute() || (dn.isFixedDrain() && dn.getExternalDrainRate() > 0.0))) {
                    upQueue.add(dn);
                }
            }
        }

        int maxUpstreamIterations = Math.max(50, graph.getNodes().size() * 5);
        int upIterations = 0;
        Map<String, Integer> upVisitCounts = new HashMap<>();

        while (!upQueue.isEmpty() && upIterations < maxUpstreamIterations) {
            upIterations++;
            RecipeNode consumer = upQueue.poll();
            if (consumer == null) continue;

            int inputCount = consumer.isReroute() ? 1 : consumer.getInputs().size();
            for (int inIdx = 0; inIdx < inputCount; inIdx++) {
                List<FlowGraph.ConnectionEdge> inEdges = AutoRatioFlowTraverser.findPortIncomingEdges(graph, consumer.getId(), inIdx);
                if (inEdges.isEmpty()) continue;

                for (FlowGraph.ConnectionEdge edge : inEdges) {
                    processUpstreamEdge(graph, edge, anchor, countsMap, allowedUpstreamNodes, integerCounts, upVisitCounts, upQueue, divergenceContext);
                }
            }
        }
    }

    private static void processUpstreamEdge(
            FlowGraph graph,
            FlowGraph.ConnectionEdge edge,
            RecipeNode anchor,
            Map<String, Double> countsMap,
            Set<String> allowedUpstreamNodes,
            boolean integerCounts,
            Map<String, Integer> upVisitCounts,
            Queue<RecipeNode> upQueue,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        RecipeNode producer = graph.findNodeById(edge.fromNodeId());
        if (producer == null) return;

        if (producer.isReroute()) {
            upQueue.add(producer);
            return;
        }

        if (producer.getId().equals(anchor.getId())) return;
        if (allowedUpstreamNodes != null && !allowedUpstreamNodes.contains(producer.getId())) return;

        if (edge.outputIndex() < producer.getOutputs().size()) {
            scaleUpstreamProducerNode(graph, producer, edge.outputIndex(), countsMap, integerCounts, upVisitCounts, upQueue, divergenceContext);
        }
    }

    private static void scaleUpstreamProducerNode(
            FlowGraph graph,
            RecipeNode producer,
            int outputIndex,
            Map<String, Double> countsMap,
            boolean integerCounts,
            Map<String, Integer> upVisitCounts,
            Queue<RecipeNode> upQueue,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        IngredientStack outStack = producer.getOutputs().get(outputIndex);
        double singleRate = producer.calculateSingleMachineOutputRate(outStack);
        if (singleRate <= 0.0001) return;

        double totalPortDemand = calculateTotalConnectedPortDemand(graph, producer, outputIndex, countsMap);
        double neededCount = quantizeMachineCount(graph, producer, totalPortDemand / singleRate, FlowBalanceMatrixSolver.CountRoundingMode.CEIL, integerCounts);

        if (neededCount >= MAX_AUTO_RATIO_MACHINE_COUNT - 1e-4 && divergenceContext != null) {
            if (ProcessStabilityAnalyzer.isMicroYieldProducer(producer)) {
                divergenceContext.recordMicroYieldClamp(producer.getId());
            } else {
                divergenceContext.recordSafetyClamp(producer.getId());
            }
        }
        neededCount = Math.min(MAX_AUTO_RATIO_MACHINE_COUNT, neededCount);

        double prevCount = countsMap.getOrDefault(producer.getId(), 0.0);
        int visits = upVisitCounts.getOrDefault(producer.getId(), 0);

        if (neededCount > prevCount + 0.0001 && visits < 3) {
            countsMap.put(producer.getId(), neededCount);
            producer.setMachineCount(neededCount);
            upVisitCounts.put(producer.getId(), visits + 1);
            upQueue.add(producer);
        }
    }

    public static double calculateTotalConnectedPortDemand(FlowGraph graph, RecipeNode producer, int outputIndex) {
        return AutoRatioFlowTraverser.calculateTotalConnectedPortDemand(graph, producer, outputIndex);
    }

    public static double calculateTotalConnectedPortDemand(FlowGraph graph, RecipeNode producer, int outputIndex, Map<String, Double> countsMap) {
        return AutoRatioFlowTraverser.calculateTotalConnectedPortDemand(graph, producer, outputIndex, countsMap);
    }

    public static double calculateEffectiveIncomingSupply(FlowGraph graph, RecipeNode consumer, int inIdx, Map<String, Double> countsMap) {
        return AutoRatioFlowTraverser.calculateEffectiveIncomingSupply(graph, consumer, inIdx, countsMap);
    }

    public static double calculateEffectiveIncomingSupply(FlowGraph graph, RecipeNode consumer, int inIdx, Map<String, Double> countsMap, boolean demandProportional) {
        return AutoRatioFlowTraverser.calculateEffectiveIncomingSupply(graph, consumer, inIdx, countsMap, demandProportional);
    }

    public static boolean isPortDrivenByDownstreamChain(FlowGraph graph, String consumerId, int inIdx, String anchorId, Map<String, Double> countsMap) {
        return AutoRatioFlowTraverser.isPortDrivenByDownstreamChain(graph, consumerId, inIdx, anchorId, countsMap);
    }

    public static void solveDownstreamPassRestricted(FlowGraph graph, RecipeNode anchor, Map<String, Double> countsMap, Set<String> allowedDownstreamNodes, boolean integerCounts) {
        Queue<RecipeNode> downQueue = new ArrayDeque<>();
        downQueue.add(anchor);

        int maxDownstreamIterations = Math.max(50, graph.getNodes().size() * 5);
        int downIterations = 0;
        Map<String, Integer> downVisitCounts = new HashMap<>();

        while (!downQueue.isEmpty() && downIterations < maxDownstreamIterations) {
            downIterations++;
            RecipeNode producer = downQueue.poll();
            if (producer == null) continue;

            Set<RecipeNode> nextConsumers = findEligibleDownstreamConsumers(graph, producer, anchor, allowedDownstreamNodes);
            for (RecipeNode consumer : nextConsumers) {
                processDownstreamConsumer(graph, consumer, anchor, countsMap, integerCounts, downVisitCounts, downQueue);
            }
        }
    }

    private static Set<RecipeNode> findEligibleDownstreamConsumers(FlowGraph graph, RecipeNode producer, RecipeNode anchor, Set<String> allowedDownstreamNodes) {
        Set<RecipeNode> nextConsumers = new LinkedHashSet<>();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!edge.fromNodeId().equals(producer.getId())) continue;
            RecipeNode consumer = graph.findNodeById(edge.toNodeId());
            if (consumer == null || consumer.getId().equals(anchor.getId())) continue;
            if (consumer.isReroute() || allowedDownstreamNodes.contains(consumer.getId())) {
                nextConsumers.add(consumer);
            }
        }
        return nextConsumers;
    }

    private static void processDownstreamConsumer(
            FlowGraph graph,
            RecipeNode consumer,
            RecipeNode anchor,
            Map<String, Double> countsMap,
            boolean integerCounts,
            Map<String, Integer> downVisitCounts,
            Queue<RecipeNode> downQueue
    ) {
        if (consumer.isReroute()) {
            downQueue.add(consumer);
            return;
        }

        double requiredConsumerCount = calculateRequiredDownstreamConsumerCount(graph, consumer, anchor, countsMap);
        if (requiredConsumerCount <= 0.0001) return;

        double finalConsumerCount = quantizeMachineCount(graph, consumer, requiredConsumerCount, FlowBalanceMatrixSolver.CountRoundingMode.FLOOR, integerCounts);
        countsMap.put(consumer.getId(), finalConsumerCount);
        consumer.setMachineCount(finalConsumerCount);

        int v = downVisitCounts.getOrDefault(consumer.getId(), 0);
        if (v < 3) {
            downVisitCounts.put(consumer.getId(), v + 1);
            downQueue.add(consumer);
        }
    }

    private static double calculateRequiredDownstreamConsumerCount(FlowGraph graph, RecipeNode consumer, RecipeNode anchor, Map<String, Double> countsMap) {
        double requiredConsumerCount = 0.0;
        boolean hasDownstreamDrivingInput = false;

        for (int inIdx = 0; inIdx < consumer.getInputs().size(); inIdx++) {
            if (!isPortDrivenByDownstreamChain(graph, consumer.getId(), inIdx, anchor.getId(), countsMap)) {
                continue;
            }

            IngredientStack inStack = consumer.getInputs().get(inIdx);
            double singleInRate = consumer.calculateSingleMachineInputRate(inStack);
            if (singleInRate <= 0.0001) continue;

            double totalIncomingSupply = calculateEffectiveIncomingSupply(graph, consumer, inIdx, countsMap);
            if (totalIncomingSupply > 0.0001) {
                hasDownstreamDrivingInput = true;
                double portConsumerCount = totalIncomingSupply / singleInRate;
                requiredConsumerCount = Math.max(requiredConsumerCount, portConsumerCount);
            }
        }

        return hasDownstreamDrivingInput ? requiredConsumerCount : 0.0;
    }

    public static void resolveBottlenecksPass(FlowGraph graph, RecipeNode anchor, Set<String> upstreamNodes, Set<String> downstreamNodes, boolean integerCounts) {
        AutoRatioBottleneckResolver.resolveBottlenecksPass(graph, anchor, upstreamNodes, downstreamNodes, integerCounts, null);
    }

    public static void resolveBottlenecksPass(
            FlowGraph graph,
            RecipeNode anchor,
            Set<String> upstreamNodes,
            Set<String> downstreamNodes,
            boolean integerCounts,
            ProcessStabilityAnalyzer.DivergenceContext divergenceContext
    ) {
        AutoRatioBottleneckResolver.resolveBottlenecksPass(graph, anchor, upstreamNodes, downstreamNodes, integerCounts, divergenceContext);
    }

    public static Set<String> findCyclicNodeIds(FlowGraph graph, FlowEdgeAllocator.CachedEdgeIndex edgeIndex) {
        return AutoRatioBottleneckResolver.findCyclicNodeIds(graph, edgeIndex);
    }

    private static void postPreSolveEvent(FlowGraph graph) {
        try {
            MinecraftForge.EVENT_BUS.post(new FlowGraphEvent.PreSolve(graph));
        } catch (Throwable ignored) {}
    }

    private static void postSolveEvent(FlowGraph graph) {
        try {
            MinecraftForge.EVENT_BUS.post(new FlowGraphEvent.PostSolve(graph));
        } catch (Throwable ignored) {}
    }
}
