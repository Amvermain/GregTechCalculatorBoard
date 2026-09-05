package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.event.FlowGraphEvent;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraftforge.common.MinecraftForge;

import java.util.*;

/**
 * Solves flow balance equations, AutoRatio BFS propagation, bottleneck resolution,
 * fixed-point efficiency evaluation, and harmonized ratio optimization.
 */
public final class FlowBalanceMatrixSolver {

    public enum CountRoundingMode {
        FLOOR,
        CEIL,
        ROUND
    }

    private FlowBalanceMatrixSolver() {}

    /**
     * Single source of truth for node machine count quantization.
     * Encapsulates Reroute isolation, Shared Machine Pool decimals, epsilon tolerance, and integer modes.
     */
    public static double quantizeMachineCount(
            FlowGraph graph,
            RecipeNode node,
            double rawCount,
            CountRoundingMode mode,
            boolean integerCounts) {
        if (node == null) return 1.0;
        if (node.isReroute()) return 1.0;

        boolean isShared = (graph != null && graph.isNodeInSharedMachineFrame(node));
        if (isShared || !integerCounts) {
            return Math.max(0.0001, Math.round(rawCount * 10000.0) / 10000.0);
        }

        return switch (mode) {
            case FLOOR -> Math.max(1.0, Math.floor(rawCount + 0.00001));
            case CEIL -> Math.max(1.0, Math.ceil(rawCount - 0.00001));
            case ROUND -> Math.max(1.0, (double) Math.round(rawCount));
        };
    }

    /**
     * Propagates machine counts across the graph starting from the anchor node.
     */
    public static void autoRatioFromAnchor(FlowGraph graph, RecipeNode anchor, boolean integerCounts) {
        if (graph == null || anchor == null || graph.getNodes().isEmpty()) return;
        try {
            MinecraftForge.EVENT_BUS.post(new FlowGraphEvent.PreSolve(graph));
        } catch (Throwable ignored) {}
        graph.cleanupInvalidConnections();

        double currentAnchorCount = anchor.getMachineCount();
        boolean isAnchorAlreadyFractional = Math.abs(currentAnchorCount - Math.round(currentAnchorCount)) > 1e-4;
        boolean preserveFractional = isAnchorAlreadyFractional && com.gtceu.calcboard.api.storage.BoardManager.getInstance().isPreserveFractionalAnchor();

        double targetAnchorCount;
        if (!integerCounts || preserveFractional) {
            targetAnchorCount = Math.max(0.0001, Math.round(currentAnchorCount * 10000.0) / 10000.0);
        } else {
            targetAnchorCount = quantizeMachineCount(graph, anchor, currentAnchorCount, CountRoundingMode.CEIL, true);
        }
        anchor.setMachineCount(targetAnchorCount);

        // 1. Identify direct anchor suppliers (upstream boundary) and downstream chain from anchor
        Set<String> directAnchorSuppliers = FlowGraphTopologyAnalyzer.getDirectSuppliers(graph, anchor.getId());
        Set<String> downstreamNodes = FlowGraphTopologyAnalyzer.findDownstreamNodes(graph, anchor.getId(), directAnchorSuppliers);
        Set<String> upstreamNodes = FlowGraphTopologyAnalyzer.findUpstreamNodes(graph, anchor.getId(), downstreamNodes);

        Map<String, Double> countsMap = new HashMap<>();
        countsMap.put(anchor.getId(), targetAnchorCount);

        // 2. Downstream Pass: Process anchor's outputs downstream first so core downstream chain counts are fixed
        solveDownstreamPassRestricted(graph, anchor, countsMap, downstreamNodes, integerCounts);

        for (String downId : downstreamNodes) {
            RecipeNode n = graph.findNodeById(downId);
            if (n != null && countsMap.containsKey(downId)) {
                n.setMachineCount(countsMap.get(downId));
            }
        }

        // 3. Upstream Pass: Satisfy input demand of anchor AND all downstream consumers from upstream producers
        solveUpstreamPassRestricted(graph, anchor, countsMap, upstreamNodes, downstreamNodes, integerCounts);

        for (String upId : upstreamNodes) {
            RecipeNode n = graph.findNodeById(upId);
            if (n != null && countsMap.containsKey(upId)) {
                n.setMachineCount(countsMap.get(upId));
            }
        }

        // 4. Bottleneck Resolution for upstream supply branches
        resolveBottlenecksPass(graph, anchor, upstreamNodes, downstreamNodes, integerCounts);

        anchor.setMachineCount(targetAnchorCount);
        normalizeNodeCounts(graph, anchor, targetAnchorCount, integerCounts);
        try {
            MinecraftForge.EVENT_BUS.post(new FlowGraphEvent.PostSolve(graph));
        } catch (Throwable ignored) {}
    }

    public static void solveUpstreamPassRestricted(FlowGraph graph, RecipeNode anchor, Map<String, Double> countsMap, Set<String> allowedUpstreamNodes, boolean integerCounts) {
        solveUpstreamPassRestricted(graph, anchor, countsMap, allowedUpstreamNodes, null, integerCounts);
    }

    public static void solveUpstreamPassRestricted(FlowGraph graph, RecipeNode anchor, Map<String, Double> countsMap, Set<String> allowedUpstreamNodes, Set<String> downstreamNodes, boolean integerCounts) {
        Queue<RecipeNode> upQueue = new ArrayDeque<>();
        upQueue.add(anchor);
        if (downstreamNodes != null) {
            for (String downId : downstreamNodes) {
                RecipeNode dn = graph.findNodeById(downId);
                if (dn != null && !dn.isReroute()) {
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

            for (int inIdx = 0; inIdx < consumer.getInputs().size(); inIdx++) {
                List<FlowGraph.ConnectionEdge> inEdges = new ArrayList<>();
                for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                    if (edge.toNodeId().equals(consumer.getId()) && edge.inputIndex() == inIdx) {
                        inEdges.add(edge);
                    }
                }
                if (inEdges.isEmpty()) continue;

                for (FlowGraph.ConnectionEdge edge : inEdges) {
                    RecipeNode producer = graph.findNodeById(edge.fromNodeId());
                    if (producer == null || producer.getId().equals(anchor.getId())) continue;
                    if (!producer.isReroute() && !allowedUpstreamNodes.contains(producer.getId())) continue;

                    if (producer.isReroute()) {
                        upQueue.add(producer);
                        continue;
                    }

                    if (edge.outputIndex() < producer.getOutputs().size()) {
                        IngredientStack outStack = producer.getOutputs().get(edge.outputIndex());
                        double singleRate = producer.calculateSingleMachineOutputRate(outStack);

                        if (singleRate > 0.0001) {
                            double totalPortDemand = calculateTotalConnectedPortDemand(graph, producer, edge.outputIndex(), countsMap);
                            double neededCount = quantizeMachineCount(graph, producer, totalPortDemand / singleRate, CountRoundingMode.CEIL, integerCounts);

                            double prevCount = countsMap.getOrDefault(producer.getId(), 0.0);
                            int visits = upVisitCounts.getOrDefault(producer.getId(), 0);

                            if (neededCount > prevCount + 0.0001 && visits < 3) {
                                countsMap.put(producer.getId(), neededCount);
                                producer.setMachineCount(neededCount);
                                upVisitCounts.put(producer.getId(), visits + 1);
                                upQueue.add(producer);
                            }
                        }
                    }
                }
            }
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

        int inDegree = 0;
        for (FlowGraph.ConnectionEdge iEdge : graph.getConnections()) {
            if (iEdge.toNodeId().equals(cNode.getId()) && iEdge.inputIndex() == outEdge.inputIndex()) {
                inDegree++;
            }
        }
        return (cReq * hop.weight) / Math.max(1, inDegree);
    }

    public static double calculateEffectiveIncomingSupply(FlowGraph graph, RecipeNode consumer, int inIdx, Map<String, Double> countsMap) {
        return calculateEffectiveIncomingSupply(graph, consumer, inIdx, countsMap, false);
    }

    public static double calculateEffectiveIncomingSupply(FlowGraph graph, RecipeNode consumer, int inIdx, Map<String, Double> countsMap, boolean demandProportional) {
        if (consumer == null) return 0.0;

        record SupplyHop(String nodeId, int inIdx, double weight) {}
        Queue<SupplyHop> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();

        queue.add(new SupplyHop(consumer.getId(), inIdx, 1.0));
        visited.add(consumer.getId() + ":" + inIdx);

        double totalIncomingSupply = 0.0;

        while (!queue.isEmpty()) {
            SupplyHop hop = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.toNodeId().equals(hop.nodeId) && edge.inputIndex() == hop.inIdx) {
                    RecipeNode p = graph.findNodeById(edge.fromNodeId());
                    if (p != null) {
                        if (p.isReroute()) {
                            double nextWeight;
                            if (demandProportional) {
                                int inDegree = 0;
                                for (FlowGraph.ConnectionEdge inEdge : graph.getConnections()) {
                                    if (inEdge.toNodeId().equals(p.getId())) {
                                        inDegree++;
                                    }
                                }
                                nextWeight = hop.weight / Math.max(1, inDegree);
                            } else {
                                int outDegree = 0;
                                for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                                    if (outEdge.fromNodeId().equals(p.getId())) {
                                        outDegree++;
                                    }
                                }
                                nextWeight = hop.weight / Math.max(1, outDegree);
                            }

                            if (visited.add(p.getId() + ":0")) {
                                queue.add(new SupplyHop(p.getId(), 0, nextWeight));
                            }
                        } else if (edge.outputIndex() < p.getOutputs().size()) {
                            double pC = countsMap != null ? countsMap.getOrDefault(p.getId(), p.getMachineCount()) : p.getMachineCount();
                            IngredientStack outStack = p.getOutputs().get(edge.outputIndex());
                            double pRate = p.calculateSingleMachineOutputRate(outStack) * pC;

                            if (demandProportional && inIdx < consumer.getInputs().size()) {
                                IngredientStack inStack = consumer.getInputs().get(inIdx);
                                double cC = countsMap != null ? countsMap.getOrDefault(consumer.getId(), consumer.getMachineCount()) : consumer.getMachineCount();
                                double consumerDemand = consumer.calculateSingleMachineInputRate(inStack) * cC;
                                double totalPortDemand = calculateTotalConnectedPortDemand(graph, p, edge.outputIndex(), countsMap);

                                if (totalPortDemand > 0.0001 && consumerDemand > 0.0001) {
                                    double allocated = (totalPortDemand <= pRate + 0.0001)
                                            ? consumerDemand
                                            : (pRate * (consumerDemand / totalPortDemand));
                                    totalIncomingSupply += allocated * hop.weight;
                                    continue;
                                }
                            }

                            int outDegree = 0;
                            for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                                if (outEdge.fromNodeId().equals(p.getId()) && outEdge.outputIndex() == edge.outputIndex()) {
                                    outDegree++;
                                }
                            }
                            totalIncomingSupply += (pRate * hop.weight) / Math.max(1, outDegree);
                        }
                    }
                }
            }
        }
        return totalIncomingSupply;
    }

    public static boolean isPortDrivenByDownstreamChain(FlowGraph graph, String consumerId, int inIdx, String anchorId, Map<String, Double> countsMap) {
        Set<RecipeNode> feedingProducers = new LinkedHashSet<>();
        FlowGraphTopologyAnalyzer.collectFeedingProducers(graph, consumerId, inIdx, feedingProducers);
        for (RecipeNode p : feedingProducers) {
            if (p.getId().equals(anchorId)) return true;
            if (countsMap != null && countsMap.containsKey(p.getId())) return true;
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

            Set<RecipeNode> nextConsumers = new LinkedHashSet<>();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.fromNodeId().equals(producer.getId())) {
                    RecipeNode consumer = graph.findNodeById(edge.toNodeId());
                    if (consumer != null && !consumer.getId().equals(anchor.getId())) {
                        if (consumer.isReroute() || allowedDownstreamNodes.contains(consumer.getId())) {
                            nextConsumers.add(consumer);
                        }
                    }
                }
            }

            for (RecipeNode consumer : nextConsumers) {
                if (consumer.isReroute()) {
                    downQueue.add(consumer);
                    continue;
                }

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

                if (hasDownstreamDrivingInput && requiredConsumerCount > 0.0001) {
                    double finalConsumerCount = quantizeMachineCount(graph, consumer, requiredConsumerCount, CountRoundingMode.FLOOR, integerCounts);
                    countsMap.put(consumer.getId(), finalConsumerCount);
                    consumer.setMachineCount(finalConsumerCount);

                    int v = downVisitCounts.getOrDefault(consumer.getId(), 0);
                    if (v < 3) {
                        downVisitCounts.put(consumer.getId(), v + 1);
                        downQueue.add(consumer);
                    }
                }
            }
        }
    }

    public static void resolveBottlenecksPass(FlowGraph graph, RecipeNode anchor, Set<String> upstreamNodes, Set<String> downstreamNodes, boolean integerCounts) {
        int maxPasses = 10;
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean changed = false;

            for (RecipeNode consumer : graph.getNodes()) {
                if (consumer.isReroute()) continue;

                for (int inIdx = 0; inIdx < consumer.getInputs().size(); inIdx++) {
                    boolean hasConnection = false;
                    for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                        if (edge.toNodeId().equals(consumer.getId()) && edge.inputIndex() == inIdx) {
                            hasConnection = true;
                            break;
                        }
                    }
                    if (!hasConnection) continue;

                    IngredientStack inStack = consumer.getInputs().get(inIdx);
                    double singleInRate = consumer.calculateSingleMachineInputRate(inStack);
                    if (singleInRate <= 1e-5) continue;

                    double requiredDemand = singleInRate * consumer.getMachineCount();
                    double incomingSupply = calculateEffectiveIncomingSupply(graph, consumer, inIdx, null, true);

                    if (incomingSupply < requiredDemand - 1e-4) {
                        Set<RecipeNode> feedingProducers = new LinkedHashSet<>();
                        FlowGraphTopologyAnalyzer.collectFeedingProducers(graph, consumer.getId(), inIdx, feedingProducers);

                        List<RecipeNode> validProducers = new ArrayList<>();
                        for (RecipeNode p : feedingProducers) {
                            if (!p.isReroute() && !p.getId().equals(anchor.getId()) && !downstreamNodes.contains(p.getId())) {
                                validProducers.add(p);
                            }
                        }

                        if (!validProducers.isEmpty()) {
                            double scaleRatio = (incomingSupply > 1e-6) ? (requiredDemand / incomingSupply) : 2.0;

                            for (RecipeNode p : validProducers) {
                                double currentCount = p.getMachineCount();
                                double newCount = quantizeMachineCount(graph, p, currentCount * scaleRatio, CountRoundingMode.CEIL, integerCounts);

                                if (newCount > currentCount + 1e-4) {
                                    p.setMachineCount(newCount);
                                    changed = true;

                                    Map<String, Double> upCounts = new HashMap<>();
                                    upCounts.put(p.getId(), newCount);
                                    solveUpstreamPassRestricted(graph, p, upCounts, upstreamNodes, downstreamNodes, integerCounts);
                                    for (Map.Entry<String, Double> e : upCounts.entrySet()) {
                                        RecipeNode un = graph.findNodeById(e.getKey());
                                        if (un != null && !un.getId().equals(anchor.getId())) {
                                            un.setMachineCount(e.getValue());
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!changed) {
                break;
            }
        }
    }

    private static void normalizeNodeCounts(FlowGraph graph, RecipeNode anchor, double targetAnchorCount, boolean integerCounts) {
        for (RecipeNode n : graph.getNodes()) {
            if (n.getId().equals(anchor.getId())) {
                n.setMachineCount(targetAnchorCount);
                continue;
            }
            n.setMachineCount(quantizeMachineCount(graph, n, n.getMachineCount(), CountRoundingMode.CEIL, integerCounts));
        }
        anchor.setMachineCount(targetAnchorCount);
    }

    public static double getEffectiveProducerOutputRate(FlowGraph graph, RecipeNode producer, int outputIndex) {
        return getEffectiveProducerOutputRate(graph, producer, outputIndex, null);
    }

    public static Map<FlowGraph.ConnectionEdge, Double> calculateOutgoingEdgeAllocations(
            FlowGraph graph, RecipeNode producer, int outputIndex, double totalProducerRate) {
        return calculateOutgoingEdgeAllocations(graph, producer, outputIndex, totalProducerRate, null);
    }

    public static Map<FlowGraph.ConnectionEdge, Double> calculateOutgoingEdgeAllocations(
            FlowGraph graph, RecipeNode producer, int outputIndex, double totalProducerRate, Map<String, Double> effMap) {
        Map<FlowGraph.ConnectionEdge, Double> allocations = new LinkedHashMap<>();
        if (graph == null || producer == null || totalProducerRate <= 0.00001) {
            return allocations;
        }

        List<FlowGraph.ConnectionEdge> outEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(producer.getId()) && edge.outputIndex() == outputIndex) {
                outEdges.add(edge);
            }
        }
        if (outEdges.isEmpty()) return allocations;

        double remainingFlow = totalProducerRate;
        List<FlowGraph.ConnectionEdge> variableEdges = new ArrayList<>();
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

        allocateVariableEdges(graph, variableEdges, remainingFlow, effMap, allocations);
        return allocations;
    }

    private static void allocateVariableEdges(
            FlowGraph graph,
            List<FlowGraph.ConnectionEdge> variableEdges,
            double remainingFlow,
            Map<String, Double> effMap,
            Map<FlowGraph.ConnectionEdge, Double> allocations
    ) {
        double totalDemand = 0.0;
        Map<FlowGraph.ConnectionEdge, Double> demandMap = new LinkedHashMap<>();
        for (FlowGraph.ConnectionEdge edge : variableEdges) {
            RecipeNode consumer = graph.findNodeById(edge.toNodeId());
            double demand = getConnectedConsumerDemand(graph, consumer, edge.inputIndex(), effMap);
            demandMap.put(edge, demand);
            totalDemand += demand;
        }

        if (totalDemand <= 0.0001) {
            double split = remainingFlow / variableEdges.size();
            for (FlowGraph.ConnectionEdge edge : variableEdges) {
                allocations.put(edge, split);
            }
            return;
        }

        if (remainingFlow >= totalDemand - 0.0001) {
            double surplus = remainingFlow - totalDemand;
            for (FlowGraph.ConnectionEdge edge : variableEdges) {
                double demand = demandMap.get(edge);
                double surplusShare = surplus * (demand / totalDemand);
                allocations.put(edge, demand + surplusShare);
            }
            return;
        }

        for (FlowGraph.ConnectionEdge edge : variableEdges) {
            allocations.put(edge, remainingFlow * (demandMap.get(edge) / totalDemand));
        }
    }

    public static double getEdgeAllocatedFlow(FlowGraph graph, FlowGraph.ConnectionEdge targetEdge, Map<String, Double> effMap) {
        return getEdgeAllocatedFlow(graph, targetEdge, effMap, new HashSet<>());
    }

    public static double getEdgeAllocatedFlow(FlowGraph graph, FlowGraph.ConnectionEdge targetEdge, Map<String, Double> effMap, Set<String> visited) {
        if (graph == null || targetEdge == null) return 0.0;
        RecipeNode producer = graph.findNodeById(targetEdge.fromNodeId());
        if (producer == null || targetEdge.outputIndex() < 0 || (!producer.isReroute() && targetEdge.outputIndex() >= producer.getOutputs().size())) {
            return 0.0;
        }
        double prodActualRate = getEffectiveProducerOutputRate(graph, producer, targetEdge.outputIndex(), effMap, visited);
        Map<FlowGraph.ConnectionEdge, Double> allocations = calculateOutgoingEdgeAllocations(graph, producer, targetEdge.outputIndex(), prodActualRate, effMap);
        return allocations.getOrDefault(targetEdge, 0.0);
    }

    public static double getEffectiveProducerOutputRate(FlowGraph graph, RecipeNode producer, int outputIndex, Map<String, Double> effMap) {
        return getEffectiveProducerOutputRate(graph, producer, outputIndex, effMap, new HashSet<>());
    }

    public static double getEffectiveProducerOutputRate(FlowGraph graph, RecipeNode producer, int outputIndex, Map<String, Double> effMap, Set<String> visited) {
        if (graph == null || producer == null || outputIndex < 0 || (!producer.isReroute() && outputIndex >= producer.getOutputs().size())) return 0.0;
        if (!visited.add(producer.getId())) return 0.0;

        try {
            if (!producer.isReroute()) {
                double prodEff = effMap != null ? effMap.getOrDefault(producer.getId(), producer.getEfficiency()) : producer.getEfficiency();
                double prodNominalRate = producer.getOutputSlotRate(outputIndex, false);
                return prodNominalRate * prodEff;
            }

            boolean hasIncoming = false;
            double incomingSupply = 0.0;
            for (FlowGraph.ConnectionEdge inEdge : graph.getConnections()) {
                if (inEdge.toNodeId().equals(producer.getId()) && inEdge.inputIndex() == 0) {
                    hasIncoming = true;
                    incomingSupply += getEdgeAllocatedFlow(graph, inEdge, effMap, visited);
                }
            }

            if (producer.isInfiniteSupply()) {
                double totalPortDemand = 0.0;
                for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                    if (outEdge.fromNodeId().equals(producer.getId()) && outEdge.outputIndex() == outputIndex) {
                        RecipeNode c = graph.findNodeById(outEdge.toNodeId());
                        totalPortDemand += getConnectedConsumerDemand(graph, c, outEdge.inputIndex(), effMap);
                    }
                }
                return totalPortDemand;
            }

            if (producer.isExternalSupply() && producer.getExternalSupplyRate() > 0.0) {
                return incomingSupply + producer.getExternalSupplyRate();
            }

            if (!hasIncoming) {
                double totalPortDemand = 0.0;
                for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                    if (outEdge.fromNodeId().equals(producer.getId()) && outEdge.outputIndex() == outputIndex) {
                        RecipeNode c = graph.findNodeById(outEdge.toNodeId());
                        totalPortDemand += getConnectedConsumerDemand(graph, c, outEdge.inputIndex(), effMap);
                    }
                }
                return totalPortDemand;
            }

            return incomingSupply;
        } finally {
            visited.remove(producer.getId());
        }
    }

    public static double getConnectedConsumerDemand(FlowGraph graph, RecipeNode consumer, int inputIndex) {
        return getConnectedConsumerDemand(graph, consumer, inputIndex, null);
    }

    public static double getConnectedConsumerDemand(FlowGraph graph, RecipeNode consumer, int inputIndex, Map<String, Double> effMap) {
        if (consumer == null || consumer.isVoidSink()) return 0.0;
        if (consumer.isReroute()) {
            double drain = consumer.isFixedDrain() ? consumer.getExternalDrainRate() : 0.0;
            return drain + calculateTotalConnectedPortDemand(graph, consumer, 0, null);
        }
        if (inputIndex < consumer.getInputs().size()) {
            if (effMap != null && effMap.containsKey(consumer.getId())) {
                return consumer.getInputSlotRate(inputIndex, false) * effMap.get(consumer.getId());
            }
            return consumer.getInputSlotRate(inputIndex, false);
        }
        return 0.0;
    }

    /**
     * Computes the bottleneck-constrained operating efficiency for every node in the graph.
     */
    public static Map<String, Double> computeNodeEfficiencies(FlowGraph graph) {
        Map<String, Double> effMap = new HashMap<>();
        if (graph == null) return effMap;
        graph.cleanupInvalidConnections();

        for (RecipeNode node : graph.getNodes()) {
            effMap.put(node.getId(), 1.0);
        }

        for (int iter = 0; iter < 10; iter++) {
            boolean changed = false;
            List<SelfSustainingLoop> loops = detectSelfSustainingLoops(graph, effMap);

            for (RecipeNode consumer : graph.getNodes()) {
                double calculatedEff = computeConsumerEfficiency(graph, consumer, loops, effMap);
                double oldEff = effMap.get(consumer.getId());
                consumer.setEfficiency(calculatedEff);
                if (Math.abs(oldEff - calculatedEff) > 0.0001) {
                    effMap.put(consumer.getId(), calculatedEff);
                    changed = true;
                }
            }

            // Propagate compound bottleneck sequentially downstream across layers
            for (RecipeNode node : graph.getNodes()) {
                if (node.isCompoundNode() && node.getCompoundLayerIndex() > 0) {
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
            }

            if (!changed) break;
        }

        return effMap;
    }

    private static double computeConsumerEfficiency(
            FlowGraph graph,
            RecipeNode consumer,
            List<SelfSustainingLoop> loops,
            Map<String, Double> effMap
    ) {
        double minRatio = 1.0;
        boolean hasConnectedInput = false;

        for (int inIdx = 0; inIdx < consumer.getInputs().size(); inIdx++) {
            double portRatio = computePortRatio(graph, consumer, inIdx, loops, effMap);
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
            Map<String, Double> effMap
    ) {
        List<FlowGraph.ConnectionEdge> inEdges = findIncomingEdges(graph, consumer.getId(), inIdx);
        if (inEdges.isEmpty()) {
            return -1.0;
        }
        IngredientStack inStack = consumer.getInputs().get(inIdx);
        double nominalInRate = consumer.getInputSlotRate(inIdx, false);
        if (nominalInRate <= 0.00001) {
            return -1.0;
        }

        double totalIncomingSupply = computeIncomingSupply(graph, inEdges, effMap);
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

    private static List<FlowGraph.ConnectionEdge> findIncomingEdges(FlowGraph graph, String nodeId, int inputIndex) {
        List<FlowGraph.ConnectionEdge> inEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(nodeId) && edge.inputIndex() == inputIndex) {
                inEdges.add(edge);
            }
        }
        return inEdges;
    }

    private static double computeIncomingSupply(FlowGraph graph, List<FlowGraph.ConnectionEdge> inEdges, Map<String, Double> effMap) {
        double totalIncomingSupply = 0.0;
        for (FlowGraph.ConnectionEdge edge : inEdges) {
            RecipeNode producer = graph.findNodeById(edge.fromNodeId());
            if (producer == null) continue;
            if (!producer.isReroute() && edge.outputIndex() >= producer.getOutputs().size()) continue;
            totalIncomingSupply += getEdgeAllocatedFlow(graph, edge, effMap);
        }
        return totalIncomingSupply;
    }

    private record SelfSustainingResource(
            IngredientStack.Type type,
            net.minecraft.resources.ResourceLocation id
    ) {
        public boolean matches(IngredientStack stack) {
            if (stack == null) return false;
            return stack.getType() == type && Objects.equals(stack.getId(), id);
        }
    }

    private record SelfSustainingLoop(
            Set<String> nodeIds,
            SelfSustainingResource resource,
            double selfSufficiencyRatio,
            double externalFeedEfficiency
    ) {
        public boolean matches(RecipeNode node, IngredientStack stack) {
            return node != null && nodeIds.contains(node.getId()) && resource.matches(stack);
        }
    }

    private static List<SelfSustainingLoop> detectSelfSustainingLoops(FlowGraph graph, Map<String, Double> effMap) {
        List<SelfSustainingLoop> result = new ArrayList<>();
        if (graph == null || graph.getNodes().isEmpty() || graph.getConnections().isEmpty()) {
            return result;
        }

        List<Set<String>> sccs = findStronglyConnectedComponents(graph);
        for (Set<String> scc : sccs) {
            if (scc.size() < 2 && !hasSelfLoop(graph, scc)) {
                continue;
            }
            Map<SelfSustainingResource, Double> prodTotals = new HashMap<>();
            Map<SelfSustainingResource, Double> demTotals = new HashMap<>();

            accumulateLoopResourceTotals(graph, scc, prodTotals, demTotals);

            for (Map.Entry<SelfSustainingResource, Double> entry : demTotals.entrySet()) {
                SelfSustainingResource res = entry.getKey();
                double dem = entry.getValue();
                double prod = prodTotals.getOrDefault(res, 0.0);
                if (dem > 0.0001 && prod >= dem - 0.001) {
                    double ratio = prod / dem;
                    double extFeedEff = calculateExternalFeedEfficiency(graph, scc, res, effMap);
                    result.add(new SelfSustainingLoop(scc, res, ratio, extFeedEff));
                }
            }
        }
        return result;
    }

    private static boolean hasSelfLoop(FlowGraph graph, Set<String> singleNodeScc) {
        if (singleNodeScc.size() != 1) return false;
        String nodeId = singleNodeScc.iterator().next();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(nodeId) && edge.toNodeId().equals(nodeId)) {
                return true;
            }
        }
        return false;
    }

    private static void accumulateLoopResourceTotals(
            FlowGraph graph,
            Set<String> scc,
            Map<SelfSustainingResource, Double> prodTotals,
            Map<SelfSustainingResource, Double> demTotals
    ) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!scc.contains(edge.fromNodeId()) || !scc.contains(edge.toNodeId())) {
                continue;
            }
            RecipeNode producer = graph.findNodeById(edge.fromNodeId());
            if (producer != null && !producer.isReroute() && edge.outputIndex() < producer.getOutputs().size()) {
                IngredientStack outStack = producer.getOutputs().get(edge.outputIndex());
                SelfSustainingResource res = new SelfSustainingResource(outStack.getType(), outStack.getId());
                prodTotals.putIfAbsent(res, 0.0);
                demTotals.putIfAbsent(res, 0.0);
            }
        }

        for (String nodeId : scc) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node == null || node.isReroute()) continue;

            for (int outIdx = 0; outIdx < node.getOutputs().size(); outIdx++) {
                IngredientStack out = node.getOutputs().get(outIdx);
                SelfSustainingResource res = new SelfSustainingResource(out.getType(), out.getId());
                if (prodTotals.containsKey(res)) {
                    double rate = node.getOutputSlotRate(outIdx, false);
                    prodTotals.put(res, prodTotals.get(res) + rate);
                }
            }
            for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
                IngredientStack in = node.getInputs().get(inIdx);
                SelfSustainingResource res = new SelfSustainingResource(in.getType(), in.getId());
                if (demTotals.containsKey(res)) {
                    double rate = node.getInputSlotRate(inIdx, false);
                    demTotals.put(res, demTotals.get(res) + rate);
                }
            }
        }
    }

    private static double calculateExternalFeedEfficiency(
            FlowGraph graph,
            Set<String> scc,
            SelfSustainingResource recirculatedRes,
            Map<String, Double> effMap
    ) {
        double minFeedEff = 1.0;
        for (String nodeId : scc) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node == null || node.isReroute()) continue;

            for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
                IngredientStack inStack = node.getInputs().get(inIdx);
                if (recirculatedRes.matches(inStack)) {
                    continue;
                }
                double nomRate = node.getInputSlotRate(inIdx, false);
                if (nomRate <= 0.0001) continue;

                List<FlowGraph.ConnectionEdge> inEdges = findIncomingEdges(graph, nodeId, inIdx);
                if (inEdges.isEmpty()) {
                    continue;
                }
                double supply = computeIncomingSupply(graph, inEdges, effMap);
                double feedRatio = Math.max(0.0, Math.min(1.0, supply / nomRate));
                minFeedEff = Math.min(minFeedEff, feedRatio);
            }
        }
        return minFeedEff;
    }

    private static List<Set<String>> findStronglyConnectedComponents(FlowGraph graph) {
        List<Set<String>> sccs = new ArrayList<>();
        Map<String, List<String>> adj = new HashMap<>();
        for (RecipeNode node : graph.getNodes()) {
            adj.put(node.getId(), new ArrayList<>());
        }
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            List<String> neighbors = adj.get(edge.fromNodeId());
            if (neighbors != null && adj.containsKey(edge.toNodeId())) {
                neighbors.add(edge.toNodeId());
            }
        }

        Map<String, Integer> indices = new HashMap<>();
        Map<String, Integer> lowlinks = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        int[] index = {0};

        for (RecipeNode node : graph.getNodes()) {
            if (!indices.containsKey(node.getId())) {
                strongConnect(node.getId(), adj, indices, lowlinks, stack, onStack, index, sccs);
            }
        }
        return sccs;
    }

    private static void strongConnect(
            String u,
            Map<String, List<String>> adj,
            Map<String, Integer> indices,
            Map<String, Integer> lowlinks,
            Deque<String> stack,
            Set<String> onStack,
            int[] index,
            List<Set<String>> sccs
    ) {
        indices.put(u, index[0]);
        lowlinks.put(u, index[0]);
        index[0]++;
        stack.push(u);
        onStack.add(u);

        for (String v : adj.getOrDefault(u, Collections.emptyList())) {
            if (!indices.containsKey(v)) {
                strongConnect(v, adj, indices, lowlinks, stack, onStack, index, sccs);
                lowlinks.put(u, Math.min(lowlinks.get(u), lowlinks.get(v)));
            } else if (onStack.contains(v)) {
                lowlinks.put(u, Math.min(lowlinks.get(u), indices.get(v)));
            }
        }

        if (lowlinks.get(u).equals(indices.get(u))) {
            Set<String> scc = new HashSet<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                scc.add(w);
            } while (!u.equals(w));
            sccs.add(scc);
        }
    }

    public static void optimizeMaxThroughput(FlowGraph graph, boolean preferParallels, boolean integerCounts) {
        if (graph == null) return;
        RecipeNode anchor = graph.findBaseNode();
        if (anchor == null && !graph.getNodes().isEmpty()) {
            anchor = graph.getNodes().get(0);
        }
        if (anchor == null) return;

        for (RecipeNode n : graph.getNodes()) {
            GTVoltageTier baseTier = n.getRecipeTier();
            GTVoltageTier targetTier = GTVoltageTier.MAX;
            if (targetTier.ordinal() < baseTier.ordinal()) {
                targetTier = baseTier;
            }
            n.setTargetTier(targetTier);
        }

        autoRatioFromAnchor(graph, anchor, integerCounts);

        if (preferParallels || integerCounts) {
            for (RecipeNode n : graph.getNodes()) {
                if (n == anchor && anchor.isBaseNode()) continue;

                double count = n.getMachineCount();
                if (preferParallels && count > 1.0) {
                    int[] standardParallels = {1, 2, 4, 8, 16, 64, 128, 256};
                    int bestP = 1;
                    for (int p : standardParallels) {
                        if (p <= Math.ceil(count)) {
                            bestP = p;
                        }
                    }
                    if (bestP > 1) {
                        n.setParallel(bestP);
                        count = count / bestP;
                        n.setMachineCount(Math.round(count * 100.0) / 100.0);
                    }
                }

                n.setMachineCount(quantizeMachineCount(graph, n, n.getMachineCount(), CountRoundingMode.CEIL, integerCounts));
            }
        }
    }

    public static double calculateConsumerMatchCount(FlowGraph graph, RecipeNode producer, int outPortIdx, RecipeNode consumer, int inPortIdx) {
        if (graph == null || producer == null || consumer == null) return 1.0;
        if (consumer.isReroute()) return 1.0;
        if (outPortIdx >= producer.getOutputs().size() || inPortIdx >= consumer.getInputs().size()) return 1.0;

        double producedRate;
        if (producer.isReroute()) {
            producedRate = getEffectiveProducerOutputRate(graph, producer, outPortIdx, null);
        } else {
            IngredientStack outStack = producer.getOutputs().get(outPortIdx);
            double prodEff = producer.getEfficiency();
            double effFactor = (prodEff > 0.00001) ? prodEff : 1.0;
            producedRate = producer.calculateSingleMachineOutputRate(outStack) * producer.getMachineCount() * effFactor;
        }

        IngredientStack inStack = consumer.getInputs().get(inPortIdx);
        double singleInRate = consumer.calculateSingleMachineInputRate(inStack);
        if (singleInRate <= 0.0001) return 1.0;

        double existingSupply = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(consumer.getId()) && edge.inputIndex() == inPortIdx) {
                if (!edge.fromNodeId().equals(producer.getId())) {
                    RecipeNode otherProd = graph.findNodeById(edge.fromNodeId());
                    if (otherProd != null && edge.outputIndex() < otherProd.getOutputs().size()) {
                        double pRate = getEffectiveProducerOutputRate(graph, otherProd, edge.outputIndex(), null);

                        int outDegree = 0;
                        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                            if (outEdge.fromNodeId().equals(otherProd.getId()) && outEdge.outputIndex() == edge.outputIndex()) {
                                outDegree++;
                            }
                        }
                        existingSupply += pRate / Math.max(1, outDegree);
                    }
                }
            }
        }

        double totalAvailableSupply = producedRate + existingSupply;
        boolean isShared = (graph != null && graph.isNodeInSharedMachineFrame(consumer));
        return quantizeMachineCount(graph, consumer, totalAvailableSupply / singleInRate, CountRoundingMode.FLOOR, !isShared);
    }

    public static double calculateProducerMatchCount(FlowGraph graph, RecipeNode producer, int outPortIdx, RecipeNode consumer, int inPortIdx) {
        if (graph == null || producer == null || consumer == null) return 1.0;
        if (producer.isReroute()) return 1.0;
        if (outPortIdx >= producer.getOutputs().size() || inPortIdx >= consumer.getInputs().size()) return 1.0;

        double totalDemand;
        if (consumer.isReroute()) {
            totalDemand = getConnectedConsumerDemand(graph, consumer, inPortIdx);
        } else {
            IngredientStack inStack = consumer.getInputs().get(inPortIdx);
            totalDemand = consumer.calculateSingleMachineInputRate(inStack) * consumer.getMachineCount();
        }

        double existingSupply = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(consumer.getId()) && edge.inputIndex() == inPortIdx) {
                if (!edge.fromNodeId().equals(producer.getId())) {
                    RecipeNode otherProd = graph.findNodeById(edge.fromNodeId());
                    if (otherProd != null && edge.outputIndex() < otherProd.getOutputs().size()) {
                        double pRate = getEffectiveProducerOutputRate(graph, otherProd, edge.outputIndex(), null);

                        int outDegree = 0;
                        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                            if (outEdge.fromNodeId().equals(otherProd.getId()) && outEdge.outputIndex() == edge.outputIndex()) {
                                outDegree++;
                            }
                        }
                        existingSupply += pRate / Math.max(1, outDegree);
                    }
                }
            }
        }

        double remainingDemand = Math.max(0.0, totalDemand - existingSupply);
        IngredientStack outStack = producer.getOutputs().get(outPortIdx);
        double prodEff = (producer.getEfficiency() > 0.00001) ? producer.getEfficiency() : 1.0;
        double singleOutRate = producer.calculateSingleMachineOutputRate(outStack) * prodEff;
        if (singleOutRate <= 0.0001) return 1.0;

        boolean isShared = (graph != null && graph.isNodeInSharedMachineFrame(producer));
        return quantizeMachineCount(graph, producer, remainingDemand / singleOutRate, CountRoundingMode.CEIL, !isShared);
    }

    public static double findPerfectHarmonizedAnchorCount(FlowGraph graph, RecipeNode anchor) {
        if (graph == null || anchor == null || graph.getNodes().isEmpty()) return 1.0;

        Map<String, Double> originalCounts = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            originalCounts.put(n.getId(), n.getMachineCount());
        }

        anchor.setMachineCount(1.0);
        autoRatioFromAnchor(graph, anchor, false);

        Map<String, Double> baseRatios = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            if (!n.isReroute()) {
                baseRatios.put(n.getId(), n.getMachineCount());
            }
        }

        for (Map.Entry<String, Double> e : originalCounts.entrySet()) {
            RecipeNode n = graph.findNodeById(e.getKey());
            if (n != null) n.setMachineCount(e.getValue());
        }

        int configuredMaxScale = 16;
        double configuredTolerance = 0.02;
        try {
            configuredMaxScale = BoardManager.getInstance().getMaxHarmonizeScale();
            configuredTolerance = BoardManager.getInstance().getHarmonizeSurplusTolerance();
        } catch (Throwable ignored) {}

        int maxScale = Math.max(1, configuredMaxScale);
        double tolerance = Math.max(0.0, configuredTolerance);

        for (int scale = 1; scale <= maxScale; scale++) {
            boolean match = true;
            for (double r : baseRatios.values()) {
                if (r > 1e-4) {
                    double scaled = r * scale;
                    if (tolerance <= 1e-5) {
                        double rounded = Math.round(scaled);
                        if (Math.abs(scaled - rounded) > 0.005) {
                            match = false;
                            break;
                        }
                    } else {
                        if (scaled >= 0.8) {
                            double nearestInt = Math.max(1.0, Math.round(scaled));
                            double relativeError = Math.abs(scaled - nearestInt) / scaled;
                            if (relativeError > tolerance) {
                                match = false;
                                break;
                            }
                        }
                    }
                }
            }
            if (match) {
                return (double) scale;
            }
        }

        int bestScale = 1;
        double bestScore = Double.MAX_VALUE;

        for (int scale = 1; scale <= maxScale; scale++) {
            double totalError = 0.0;
            double maxMajorError = 0.0;
            double totalMachines = 0.0;

            for (double r : baseRatios.values()) {
                if (r > 1e-4) {
                    double scaled = r * scale;
                    double nearestInt = Math.max(1.0, Math.round(scaled));
                    double err = Math.abs(scaled - nearestInt) / Math.max(1.0, scaled);
                    if (scaled >= 0.8) {
                        maxMajorError = Math.max(maxMajorError, err);
                    }
                    totalError += err;
                    totalMachines += nearestInt;
                }
            }

            double tolerancePenalty = (maxMajorError <= tolerance) ? 0.0 : (maxMajorError - tolerance) * 100.0;
            double score = tolerancePenalty + (totalError * 10.0) + (totalMachines * 0.1);

            if (score < bestScore) {
                bestScore = score;
                bestScale = scale;
            }
        }

        return (double) bestScale;
    }

    public static void autoRatioHarmonized(FlowGraph graph, RecipeNode anchor) {
        if (graph == null || anchor == null) return;
        double harmonizedAnchorCount = findPerfectHarmonizedAnchorCount(graph, anchor);
        anchor.setMachineCount(harmonizedAnchorCount);
        autoRatioFromAnchor(graph, anchor, true);
    }
}
