package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.event.FlowGraphEvent;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraftforge.common.MinecraftForge;

import java.util.*;

/**
 * Propagates machine counts across the graph via BFS passes (upstream, downstream, bottleneck resolution),
 * enforcing integer quantization and safety scaling bounds.
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

        com.gtceu.calcboard.api.solver.linear.TwoStageLinearFlowSolver.SolveResult linearResult =
                com.gtceu.calcboard.api.solver.linear.TwoStageLinearFlowSolver.solve(graph, anchor, integerCounts);

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

        executeRelaxationPasses(
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
        normalizeNodeCounts(graph, anchor, targetAnchorCount, integerCounts);

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
                List<FlowGraph.ConnectionEdge> inEdges = findPortIncomingEdges(graph, consumer.getId(), inIdx);
                if (inEdges.isEmpty()) continue;

                for (FlowGraph.ConnectionEdge edge : inEdges) {
                    processUpstreamEdge(graph, edge, anchor, countsMap, allowedUpstreamNodes, integerCounts, upVisitCounts, upQueue, divergenceContext);
                }
            }
        }
    }

    private static List<FlowGraph.ConnectionEdge> findPortIncomingEdges(FlowGraph graph, String consumerId, int inIdx) {
        List<FlowGraph.ConnectionEdge> inEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(consumerId) && edge.inputIndex() == inIdx) {
                inEdges.add(edge);
            }
        }
        return inEdges;
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
        return calculateTotalConnectedPortDemand(graph, producer, outputIndex, null);
    }

    private record DemandHop(String nodeId, int outputIndex, double weight) {}

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

    private static int countPortInDegree(FlowGraph graph, String consumerId, int inputIndex) {
        int inDegree = 0;
        for (FlowGraph.ConnectionEdge inEdge : graph.getConnections()) {
            if (inEdge.toNodeId().equals(consumerId) && inEdge.inputIndex() == inputIndex) {
                inDegree++;
            }
        }
        return inDegree;
    }

    private static int countPortOutDegree(FlowGraph graph, String producerId, int outputIndex) {
        int outDegree = 0;
        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
            if (outEdge.fromNodeId().equals(producerId) && outEdge.outputIndex() == outputIndex) {
                outDegree++;
            }
        }
        return outDegree;
    }

    private record SupplyHop(String nodeId, int inIdx, double weight) {}

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
        resolveBottlenecksPass(graph, anchor, upstreamNodes, downstreamNodes, integerCounts, null);
    }

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

        double incomingSupply = calculateEffectiveIncomingSupply(graph, consumer, inIdx, null, true);
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
            double newCount = quantizeMachineCount(graph, p, targetCount, FlowBalanceMatrixSolver.CountRoundingMode.CEIL, integerCounts);

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
        solveUpstreamPassRestricted(graph, p, upCounts, upstreamNodes, downstreamNodes, integerCounts, divergenceContext);
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

    private static void executeRelaxationPasses(
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

        solveDownstreamPassRestricted(graph, anchor, countsMap, downstreamNodes, integerCounts);
        applyCalculatedCounts(graph, countsMap, downstreamNodes);

        solveUpstreamPassRestricted(graph, anchor, countsMap, upstreamNodes, downstreamNodes, integerCounts, divergenceContext);
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

    private static void normalizeNodeCounts(FlowGraph graph, RecipeNode anchor, double targetAnchorCount, boolean integerCounts) {
        for (RecipeNode n : graph.getNodes()) {
            if (n.getId().equals(anchor.getId())) {
                n.setMachineCount(targetAnchorCount);
                continue;
            }
            if (n.isReroute()) {
                n.setMachineCount(1.0);
                continue;
            }
            n.setMachineCount(quantizeMachineCount(graph, n, n.getMachineCount(), FlowBalanceMatrixSolver.CountRoundingMode.CEIL, integerCounts));
        }
        anchor.setMachineCount(targetAnchorCount);
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
