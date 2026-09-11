package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeProperties;

import java.util.*;

/**
 * Analyzes process flow stability, cycle gain ratios, pre-solve deficit loops,
 * conflicting anchor constraints, and divergence contextual categorization.
 */
public final class ProcessStabilityAnalyzer {

    public static final class DivergenceContext {
        private final Set<String> divergentNodeIds = new LinkedHashSet<>();
        private final Map<String, String> nodeReasons = new LinkedHashMap<>();
        private boolean clampedBySafetyLimit = false;

        public void recordSuppressedRecirculation(String consumerId, Collection<RecipeNode> cyclicProducers) {
            recordNodes(consumerId, cyclicProducers, "recirculation_loop");
        }

        public void recordPositiveFeedback(String consumerId, Collection<RecipeNode> cyclicProducers) {
            recordNodes(consumerId, cyclicProducers, "positive_feedback");
        }

        public void recordCatalystDecay(String consumerId, Collection<RecipeNode> cyclicProducers) {
            recordNodes(consumerId, cyclicProducers, "catalyst_decay");
        }

        public void recordAnchorConflict(String nodeId) {
            if (nodeId != null) {
                divergentNodeIds.add(nodeId);
                nodeReasons.put(nodeId, "anchor_conflict");
            }
        }

        public void clearNodeDivergence(String nodeId) {
            if (nodeId != null) {
                divergentNodeIds.remove(nodeId);
                nodeReasons.remove(nodeId);
            }
        }

        public void recordSafetyClamp(String nodeId) {
            if (nodeId != null) {
                divergentNodeIds.add(nodeId);
                nodeReasons.put(nodeId, "safety_clamp");
            }
            clampedBySafetyLimit = true;
        }

        public void recordMicroYieldClamp(String nodeId) {
            if (nodeId != null) {
                divergentNodeIds.add(nodeId);
                nodeReasons.put(nodeId, "micro_yield_clamp");
            }
            clampedBySafetyLimit = true;
        }

        private void recordNodes(String consumerId, Collection<RecipeNode> cyclicProducers, String reason) {
            if (consumerId != null) {
                divergentNodeIds.add(consumerId);
                nodeReasons.putIfAbsent(consumerId, reason);
            }
            if (cyclicProducers != null) {
                for (RecipeNode p : cyclicProducers) {
                    if (p != null) {
                        divergentNodeIds.add(p.getId());
                        nodeReasons.putIfAbsent(p.getId(), reason);
                    }
                }
            }
        }

        public Set<String> getDivergentNodeIds() {
            return divergentNodeIds;
        }

        public String getReason(String nodeId) {
            return nodeReasons.getOrDefault(nodeId, clampedBySafetyLimit ? "safety_clamp" : "recirculation_loop");
        }

        public boolean isClampedBySafetyLimit() {
            return clampedBySafetyLimit;
        }
    }

    private ProcessStabilityAnalyzer() {}

    public static Set<String> findUnfedDeficitLoopNodeIds(FlowGraph graph) {
        if (graph == null) return Collections.emptySet();
        DivergenceContext ctx = new DivergenceContext();
        detectUnfedDeficitLoops(graph, null, ctx);
        return Collections.unmodifiableSet(ctx.getDivergentNodeIds());
    }

    public static void detectUnfedDeficitLoops(
            FlowGraph graph,
            RecipeNode anchor,
            DivergenceContext divergenceContext
    ) {
        if (graph == null || divergenceContext == null) return;
        FlowEdgeAllocator.CachedEdgeIndex edgeIndex = FlowEdgeAllocator.buildEdgeIndex(graph);
        List<Set<String>> sccs = findStronglyConnectedComponents(graph, edgeIndex);

        for (Set<String> scc : sccs) {
            if (scc.size() < 2 && !hasSelfLoop(graph, scc, edgeIndex)) {
                continue;
            }
            analyzeSccCycles(graph, scc, anchor, divergenceContext);
        }
    }

    private static void analyzeSccCycles(
            FlowGraph graph,
            Set<String> sccNodeIds,
            RecipeNode anchor,
            DivergenceContext divergenceContext
    ) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!sccNodeIds.contains(edge.fromNodeId()) || !sccNodeIds.contains(edge.toNodeId())) {
                continue;
            }
            RecipeNode src = graph.findNodeById(edge.fromNodeId());
            RecipeNode dst = graph.findNodeById(edge.toNodeId());
            if (src == null || dst == null || src.isReroute() || dst.isReroute()) {
                continue;
            }

            List<FlowGraph.ConnectionEdge> cycleEdges = findCycleEdges(graph, dst.getId(), src.getId(), edge, sccNodeIds);
            if (!cycleEdges.isEmpty()) {
                checkCycleForDeficit(graph, cycleEdges, sccNodeIds, anchor, divergenceContext);
            }
        }
    }

    private static List<FlowGraph.ConnectionEdge> findCycleEdges(
            FlowGraph graph,
            String fromId,
            String toId,
            FlowGraph.ConnectionEdge closingEdge,
            Set<String> sccNodeIds
    ) {
        if (fromId.equals(toId)) {
            return Collections.singletonList(closingEdge);
        }

        Map<String, FlowGraph.ConnectionEdge> prevEdge = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(fromId);

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            if (curr.equals(toId)) break;

            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (!edge.fromNodeId().equals(curr)) continue;
                String next = edge.toNodeId();
                if (!sccNodeIds.contains(next) && !isRerouteInGraph(graph, next)) continue;
                if (!prevEdge.containsKey(next)) {
                    prevEdge.put(next, edge);
                    queue.add(next);
                }
            }
        }

        if (!prevEdge.containsKey(toId)) {
            return Collections.emptyList();
        }

        List<FlowGraph.ConnectionEdge> cycle = new ArrayList<>();
        cycle.add(closingEdge);
        String curr = toId;
        while (!curr.equals(fromId)) {
            FlowGraph.ConnectionEdge edge = prevEdge.get(curr);
            if (edge == null) break;
            cycle.add(edge);
            curr = edge.fromNodeId();
        }
        return cycle;
    }

    private static void checkCycleForDeficit(
            FlowGraph graph,
            List<FlowGraph.ConnectionEdge> cycleEdges,
            Set<String> sccNodeIds,
            RecipeNode anchor,
            DivergenceContext divergenceContext
    ) {
        double cycleReturnRatio = 1.0;
        List<FlowGraph.ConnectionEdge> deficitEdges = new ArrayList<>();

        for (FlowGraph.ConnectionEdge edge : cycleEdges) {
            RecipeNode src = graph.findNodeById(edge.fromNodeId());
            RecipeNode dst = graph.findNodeById(edge.toNodeId());
            if (src == null || dst == null || src.isReroute() || dst.isReroute()) continue;
            if (edge.outputIndex() >= src.getOutputs().size() || edge.inputIndex() >= dst.getInputs().size()) continue;

            int totalOut = countPortOutDegree(graph, src.getId(), edge.outputIndex());
            int outsideOut = countPortOutsideDegree(graph, src.getId(), edge.outputIndex(), sccNodeIds);
            double retentionFactor = totalOut > 0 ? (double) (totalOut - outsideOut) / totalOut : 1.0;
            double singleRate = src.calculateSingleMachineOutputRate(src.getOutputs().get(edge.outputIndex()));
            double outRate = singleRate * retentionFactor;
            double inRate = dst.calculateSingleMachineInputRate(dst.getInputs().get(edge.inputIndex()));
            if (inRate <= 1e-5) continue;

            double stepRatio = outRate / inRate;
            cycleReturnRatio *= stepRatio;
            if (stepRatio < 1.0 - 1e-4) {
                deficitEdges.add(edge);
            }
        }

        Set<RecipeNode> cycleNodes = collectCycleNodes(graph, cycleEdges);

        if (cycleReturnRatio > 1.0 + 1e-4) {
            if (!hasExternalSink(graph, cycleNodes, cycleEdges, sccNodeIds)) {
                for (RecipeNode n : cycleNodes) {
                    divergenceContext.recordPositiveFeedback(n.getId(), cycleNodes);
                }
            }
            return;
        }

        if (cycleReturnRatio >= 1.0 - 1e-4 || deficitEdges.isEmpty()) {
            return;
        }

        if (hasUnfedDeficitTransition(graph, deficitEdges, sccNodeIds)) {
            boolean isCatalystDecay = (cycleReturnRatio >= 0.95);
            for (RecipeNode n : cycleNodes) {
                if (isCatalystDecay) {
                    divergenceContext.recordCatalystDecay(n.getId(), cycleNodes);
                } else {
                    divergenceContext.recordSuppressedRecirculation(n.getId(), cycleNodes);
                }
            }
        }
    }

    private static Set<RecipeNode> collectCycleNodes(FlowGraph graph, List<FlowGraph.ConnectionEdge> cycleEdges) {
        Set<RecipeNode> cycleNodes = new LinkedHashSet<>();
        for (FlowGraph.ConnectionEdge edge : cycleEdges) {
            RecipeNode src = graph.findNodeById(edge.fromNodeId());
            if (src != null && !src.isReroute()) {
                cycleNodes.add(src);
            }
        }
        return cycleNodes;
    }

    private static boolean hasExternalSink(
            FlowGraph graph,
            Set<RecipeNode> cycleNodes,
            List<FlowGraph.ConnectionEdge> cycleEdges,
            Set<String> sccNodeIds
    ) {
        Set<IngredientStack> cycleResources = new HashSet<>();
        for (FlowGraph.ConnectionEdge cEdge : cycleEdges) {
            RecipeNode src = graph.findNodeById(cEdge.fromNodeId());
            if (src != null && cEdge.outputIndex() < src.getOutputs().size()) {
                cycleResources.add(src.getOutputs().get(cEdge.outputIndex()));
            }
        }

        for (RecipeNode n : cycleNodes) {
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (!edge.fromNodeId().equals(n.getId())) continue;
                if (edge.outputIndex() >= n.getOutputs().size()) continue;
                IngredientStack outStack = n.getOutputs().get(edge.outputIndex());
                if (!cycleResources.contains(outStack)) continue;

                RecipeNode target = graph.findNodeById(edge.toNodeId());
                if (target != null && (target.isVoidSink() || !sccNodeIds.contains(target.getId()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasUnfedDeficitTransition(FlowGraph graph, List<FlowGraph.ConnectionEdge> deficitEdges, Set<String> sccNodeIds) {
        for (FlowGraph.ConnectionEdge defEdge : deficitEdges) {
            RecipeNode consumer = graph.findNodeById(defEdge.toNodeId());
            if (consumer == null) continue;

            Set<RecipeNode> feeders = new HashSet<>();
            FlowGraphTopologyAnalyzer.collectFeedingProducers(graph, consumer.getId(), defEdge.inputIndex(), feeders);
            boolean hasExternalFeed = feeders.stream().anyMatch(f -> !sccNodeIds.contains(f.getId()));
            if (!hasExternalFeed) {
                return true;
            }
        }
        return false;
    }

    public static boolean hasSccExternalFeed(FlowGraph graph, Set<String> sccNodeIds) {
        if (graph == null || sccNodeIds == null || sccNodeIds.isEmpty()) return false;
        for (String nodeId : sccNodeIds) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node == null || node.isReroute()) continue;
            for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
                Set<RecipeNode> feeders = new HashSet<>();
                FlowGraphTopologyAnalyzer.collectFeedingProducers(graph, nodeId, inIdx, feeders);
                if (feeders.stream().anyMatch(f -> !sccNodeIds.contains(f.getId()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int countPortOutDegree(FlowGraph graph, String srcId, int outIdx) {
        int count = 0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(srcId) && edge.outputIndex() == outIdx) {
                count++;
            }
        }
        return Math.max(1, count);
    }

    private static int countPortOutsideDegree(FlowGraph graph, String srcId, int outIdx, Set<String> sccNodeIds) {
        int count = 0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(srcId) && edge.outputIndex() == outIdx) {
                if (sccNodeIds != null && !sccNodeIds.contains(edge.toNodeId())) {
                    count++;
                }
            }
        }
        return count;
    }

    public static boolean isMicroYieldProducer(RecipeNode p) {
        if (p == null || p.getOutputs().isEmpty()) return false;
        for (IngredientStack out : p.getOutputs()) {
            if (p.calculateSingleMachineOutputRate(out) < 1e-4) {
                return true;
            }
        }
        return false;
    }

    public static void detectConflictingAnchors(
            FlowGraph graph,
            Map<String, Double> initialBaseNodeCounts,
            DivergenceContext divergenceContext
    ) {
        if (graph == null || initialBaseNodeCounts == null || divergenceContext == null) return;
        for (Map.Entry<String, Double> entry : initialBaseNodeCounts.entrySet()) {
            RecipeNode other = graph.findNodeById(entry.getKey());
            if (other == null) continue;
            if (other.isReroute()) {
                if (other.isFixedDrain() && hasUnmetIncomingDemand(graph, other)) {
                    divergenceContext.recordAnchorConflict(other.getId());
                }
                continue;
            }
            if (Math.abs(entry.getValue() - other.getMachineCount()) > 1e-3) {
                divergenceContext.recordAnchorConflict(other.getId());
                continue;
            }
            if (hasUnmetIncomingDemand(graph, other)) {
                divergenceContext.recordAnchorConflict(other.getId());
            }
        }
    }

    public static boolean hasUnmetIncomingDemand(FlowGraph graph, RecipeNode node) {
        return hasUnmetIncomingDemand(graph, node, 1.0 - 1e-4);
    }

    public static boolean hasUnmetIncomingDemand(FlowGraph graph, RecipeNode node, double satisfactionThreshold) {
        if (node.isReroute()) {
            if (node.isFixedDrain() && node.getExternalDrainRate() > 0.0) {
                double supply = AutoRatioEngine.calculateEffectiveIncomingSupply(graph, node, 0, null, true);
                return supply < node.getExternalDrainRate() * satisfactionThreshold - 1e-4;
            }
            return false;
        }
        for (int inIdx = 0; inIdx < node.getInputs().size(); inIdx++) {
            if (!hasIncomingConnection(graph, node.getId(), inIdx)) continue;
            IngredientStack inStack = node.getInputs().get(inIdx);
            double singleIn = node.calculateSingleMachineInputRate(inStack);
            double required = singleIn * node.getMachineCount();
            if (required <= 1e-5) continue;
            double supply = AutoRatioEngine.calculateEffectiveIncomingSupply(graph, node, inIdx, null, true);
            if (supply < required * satisfactionThreshold - 1e-4) {
                return true;
            }
        }
        return false;
    }

    private static boolean isRerouteInGraph(FlowGraph graph, String nodeId) {
        RecipeNode n = graph.findNodeById(nodeId);
        return n != null && n.isReroute();
    }

    public static void checkUnresolvedLoopBottlenecks(
            FlowGraph graph,
            RecipeNode anchor,
            Set<String> upstreamNodes,
            Set<String> downstreamNodes,
            Set<String> cyclicNodes,
            DivergenceContext divergenceContext
    ) {
        for (RecipeNode consumer : graph.getNodes()) {
            if (!isEligibleBottleneckConsumer(consumer, anchor, upstreamNodes, downstreamNodes)) {
                continue;
            }
            checkConsumerLoopBottlenecks(graph, consumer, anchor, downstreamNodes, cyclicNodes, divergenceContext);
        }
    }

    private static void checkConsumerLoopBottlenecks(
            FlowGraph graph,
            RecipeNode consumer,
            RecipeNode anchor,
            Set<String> downstreamNodes,
            Set<String> cyclicNodes,
            DivergenceContext divergenceContext
    ) {
        int inputCount = consumer.isReroute() ? 1 : consumer.getInputs().size();
        for (int inIdx = 0; inIdx < inputCount; inIdx++) {
            checkPortLoopBottleneck(graph, consumer, inIdx, anchor, downstreamNodes, cyclicNodes, divergenceContext);
        }
    }

    private static void checkPortLoopBottleneck(
            FlowGraph graph,
            RecipeNode consumer,
            int inIdx,
            RecipeNode anchor,
            Set<String> downstreamNodes,
            Set<String> cyclicNodes,
            DivergenceContext divergenceContext
    ) {
        if (!hasIncomingConnection(graph, consumer.getId(), inIdx)) {
            return;
        }
        double singleInRate = getConsumerPortInputRate(consumer, inIdx);
        double requiredDemand = getConsumerPortDemand(consumer, inIdx);
        if (requiredDemand <= 1e-5) {
            return;
        }

        double incomingSupply = AutoRatioEngine.calculateEffectiveIncomingSupply(graph, consumer, inIdx, null, true);
        if (incomingSupply >= requiredDemand - 1e-4) {
            return;
        }
        Set<RecipeNode> cyclicSuppliers = new LinkedHashSet<>();
        findBottleneckProducers(graph, consumer, inIdx, anchor, downstreamNodes, cyclicNodes, cyclicSuppliers);
        if (!cyclicSuppliers.isEmpty() && !isIntegerRoundingDeficit(consumer, singleInRate, incomingSupply, requiredDemand)) {
            divergenceContext.recordSuppressedRecirculation(consumer.getId(), cyclicSuppliers);
        }
    }

    public static double getConsumerPortInputRate(RecipeNode consumer, int inIdx) {
        if (consumer == null) return 0.0;
        if (consumer.isReroute()) {
            return consumer.isFixedDrain() ? consumer.getExternalDrainRate() : 0.0;
        }
        if (inIdx < 0 || inIdx >= consumer.getInputs().size()) return 0.0;
        return consumer.calculateSingleMachineInputRate(consumer.getInputs().get(inIdx));
    }

    public static double getConsumerPortDemand(RecipeNode consumer, int inIdx) {
        if (consumer == null) return 0.0;
        if (consumer.isReroute()) {
            return consumer.isFixedDrain() ? consumer.getExternalDrainRate() : 0.0;
        }
        double singleInRate = getConsumerPortInputRate(consumer, inIdx);
        return singleInRate * consumer.getMachineCount();
    }

    public static boolean isIntegerRoundingDeficit(RecipeNode consumer, double singleInRate, double incomingSupply, double requiredDemand) {
        if (consumer == null) return false;
        if (consumer.isReroute()) {
            return incomingSupply >= requiredDemand * 0.95;
        }
        if (consumer.getMachineCount() <= 0.0) return false;
        if (consumer.getMachineCount() > 1.0 + 1e-4) {
            return incomingSupply >= singleInRate * (consumer.getMachineCount() - 1.0) - 1e-4;
        }
        return incomingSupply >= requiredDemand * 0.95;
    }

    public static boolean isEligibleBottleneckConsumer(RecipeNode consumer, RecipeNode anchor, Set<String> upstreamNodes, Set<String> downstreamNodes) {
        if (consumer == null) return false;
        if (consumer.isReroute()) {
            if (!consumer.isFixedDrain() || consumer.getExternalDrainRate() <= 0.0) {
                return false;
            }
        }
        if (consumer.getId().equals(anchor.getId())) return true;
        if (upstreamNodes != null && upstreamNodes.contains(consumer.getId())) return true;
        return downstreamNodes != null && downstreamNodes.contains(consumer.getId());
    }

    public static boolean hasIncomingConnection(FlowGraph graph, String consumerId, int inIdx) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(consumerId) && edge.inputIndex() == inIdx) {
                return true;
            }
        }
        return false;
    }

    public static List<RecipeNode> findBottleneckProducers(
            FlowGraph graph,
            RecipeNode consumer,
            int inIdx,
            RecipeNode anchor,
            Set<String> downstreamNodes,
            Set<String> cyclicNodes,
            Set<RecipeNode> skippedCyclicProducers
    ) {
        Set<RecipeNode> feedingProducers = new LinkedHashSet<>();
        FlowGraphTopologyAnalyzer.collectFeedingProducers(graph, consumer.getId(), inIdx, feedingProducers);

        List<RecipeNode> validProducers = new ArrayList<>();
        for (RecipeNode p : feedingProducers) {
            if (p.isReroute()) continue;
            boolean isAnchor = p.getId().equals(anchor.getId());
            if (isAnchor) {
                continue;
            }
            boolean isCyclic = (cyclicNodes != null && cyclicNodes.contains(p.getId()))
                    || (downstreamNodes != null && downstreamNodes.contains(p.getId()))
                    || FlowGraphTopologyAnalyzer.hasDirectedPath(graph, consumer.getId(), p.getId());

            if (isCyclic) {
                if (skippedCyclicProducers != null) {
                    skippedCyclicProducers.add(p);
                }
                continue;
            }
            validProducers.add(p);
        }
        return validProducers;
    }

    public static List<Set<String>> findStronglyConnectedComponents(FlowGraph graph, FlowEdgeAllocator.CachedEdgeIndex edgeIndex) {
        List<Set<String>> sccs = new ArrayList<>();
        Map<String, Integer> indices = new HashMap<>();
        Map<String, Integer> lowlinks = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        int[] index = {0};

        for (RecipeNode node : graph.getNodes()) {
            if (!indices.containsKey(node.getId())) {
                strongConnect(node.getId(), edgeIndex.outEdges(), indices, lowlinks, stack, onStack, index, sccs);
            }
        }
        return sccs;
    }

    private static void strongConnect(
            String u,
            Map<String, List<FlowGraph.ConnectionEdge>> outEdges,
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

        for (FlowGraph.ConnectionEdge edge : outEdges.getOrDefault(u, Collections.emptyList())) {
            String v = edge.toNodeId();
            if (!indices.containsKey(v)) {
                strongConnect(v, outEdges, indices, lowlinks, stack, onStack, index, sccs);
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

    public static boolean hasSelfLoop(FlowGraph graph, Set<String> singleNodeScc, FlowEdgeAllocator.CachedEdgeIndex edgeIndex) {
        if (singleNodeScc.size() != 1) return false;
        String nodeId = singleNodeScc.iterator().next();
        List<FlowGraph.ConnectionEdge> candidates = edgeIndex != null
                ? edgeIndex.getOutEdges(nodeId)
                : graph.getConnections();
        for (FlowGraph.ConnectionEdge edge : candidates) {
            if (edge.fromNodeId().equals(nodeId) && edge.toNodeId().equals(nodeId)) {
                return true;
            }
        }
        return false;
    }

    public static void reconcileLoopDivergences(
            FlowGraph graph,
            RecipeNode anchor,
            DivergenceContext divergenceContext,
            boolean integerCounts
    ) {
        if (graph == null || divergenceContext == null) return;

        FlowEdgeAllocator.CachedEdgeIndex edgeIndex = FlowEdgeAllocator.buildEdgeIndex(graph);
        List<Set<String>> sccs = findStronglyConnectedComponents(graph, edgeIndex);

        for (Set<String> scc : sccs) {
            if (scc.size() < 2 && !hasSelfLoop(graph, scc, edgeIndex)) continue;
            if (isSccSatisfied(graph, scc, divergenceContext, integerCounts)) {
                clearSccDivergences(scc, divergenceContext);
            }
        }
    }

    private static boolean isSccSatisfied(FlowGraph graph, Set<String> scc, DivergenceContext ctx, boolean integerCounts) {
        for (String nodeId : scc) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node == null || node.isReroute()) continue;
            String reason = ctx.getReason(nodeId);
            boolean isCatalyst = "catalyst_decay".equals(reason);
            double threshold = isCatalyst ? (1.0 - 1e-4) : 0.95;
            if (hasUnmetIncomingDemand(graph, node, threshold)) {
                return false;
            }
        }
        return true;
    }

    private static void clearSccDivergences(Set<String> scc, DivergenceContext ctx) {
        for (String nodeId : scc) {
            String reason = ctx.getReason(nodeId);
            if ("recirculation_loop".equals(reason) || "catalyst_decay".equals(reason)) {
                ctx.clearNodeDivergence(nodeId);
            }
        }
    }

    public static void clearDivergenceWarnings(FlowGraph graph) {
        if (graph == null) return;
        for (RecipeNode n : graph.getNodes()) {
            n.getProperties().remove(NodeProperties.DIVERGENCE_WARNING);
            n.getProperties().remove(NodeProperties.DIVERGENCE_REASON);
            n.getProperties().remove(NodeProperties.DIVERGENCE_DETAIL);
        }
    }

    public static void applyDivergenceWarnings(FlowGraph graph, DivergenceContext ctx) {
        if (graph == null || ctx == null) return;
        for (String nodeId : ctx.getDivergentNodeIds()) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node != null) {
                node.getProperties().set(NodeProperties.DIVERGENCE_WARNING, true);
                node.getProperties().set(NodeProperties.DIVERGENCE_REASON, ctx.getReason(nodeId));
            }
        }
    }
}
