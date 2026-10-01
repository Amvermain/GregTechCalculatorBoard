package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.CrossPageExportTarget;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.SupplyMode;

import java.util.*;

/**
 * Multi-page flow coordinator managing inter-page dependency DAGs,
 * Tarjan SCC circular dependency detection, and cross-page flow distribution.
 */
public final class WorkspaceFlowCoordinator {

    private WorkspaceFlowCoordinator() {}

    public record InterPageLink(
            String sourcePageId,
            String sourceNodeId,
            String targetPageId,
            String targetNodeId,
            int priority,
            double fixedLimit
    ) {}

    public record WorkspaceFlowResult(
            List<String> topologicalOrder,
            Set<String> circularPageIds,
            Set<String> circularNodeIds,
            Set<String> brokenLinkNodeIds,
            Set<String> starvedNodeIds,
            Map<String, Double> allocatedRates,
            Map<String, Double> demandRates
    ) {
        public static final WorkspaceFlowResult EMPTY = new WorkspaceFlowResult(
                List.of(), Set.of(), Set.of(), Set.of(), Set.of(), Map.of(), Map.of()
        );

        public boolean hasCycles() {
            return !circularPageIds.isEmpty();
        }

        public boolean isBroken(String nodeId) {
            return brokenLinkNodeIds.contains(nodeId);
        }

        public boolean isCircular(String nodeId) {
            return circularNodeIds.contains(nodeId);
        }

        public boolean isStarved(String nodeId) {
            return starvedNodeIds.contains(nodeId);
        }

        public double getAllocatedRate(String nodeId) {
            return allocatedRates.getOrDefault(nodeId, 0.0);
        }

        public double getDemandRate(String nodeId) {
            return demandRates.getOrDefault(nodeId, 0.0);
        }
    }

    public record SourceJunctionMetrics(
            double totalProduction,
            double localDemand,
            double remoteExport,
            double totalUsage,
            double availableSurplus
    ) {
        public static final SourceJunctionMetrics EMPTY = new SourceJunctionMetrics(0.0, 0.0, 0.0, 0.0, 0.0);
    }

    public static SourceJunctionMetrics calculateSourceJunctionMetrics(BoardPage srcPage, RecipeNode srcNode) {
        if (srcPage == null || srcNode == null || srcPage.getGraph() == null) {
            return SourceJunctionMetrics.EMPTY;
        }
        FlowGraph graph = srcPage.getGraph();
        double totalProduction = calculateJunctionProduction(srcNode, graph);
        double localDemand = FlowBalanceMatrixSolver.calculateTotalConnectedPortEffectiveDemand(graph, srcNode, 0);
        if (srcNode.isFixedDrain()) {
            localDemand += srcNode.getExternalDrainRate();
        }
        double remoteExport = srcNode.getAllocatedExportRate();
        double totalUsage = localDemand + remoteExport;
        double availableSurplus = Double.isInfinite(totalProduction)
                ? Double.POSITIVE_INFINITY
                : Math.max(0.0, totalProduction - totalUsage);
        return new SourceJunctionMetrics(totalProduction, localDemand, remoteExport, totalUsage, availableSurplus);
    }

    public static double calculateJunctionProduction(RecipeNode node, FlowGraph graph) {
        if (node == null) return 0.0;
        if (node.isInfiniteSupply()) return Double.POSITIVE_INFINITY;
        if (node.isExternalSupply()) return node.getExternalSupplyRate();
        if (graph == null) return 0.0;
        return hasIncomingConnection(graph, node.getId())
                ? ProductionETACalculator.calculateNetInflowRate(graph, node, 0)
                : 0.0;
    }

    private static boolean hasIncomingConnection(FlowGraph graph, String nodeId) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(nodeId) && edge.inputIndex() == 0) {
                return true;
            }
        }
        return false;
    }

    private static volatile WorkspaceFlowResult lastResult = WorkspaceFlowResult.EMPTY;
    private static volatile Map<String, BoardPage> lastPageMap = Collections.emptyMap();
    private static volatile List<InterPageLink> lastLinks = Collections.emptyList();

    public static WorkspaceFlowResult getLastResult() {
        return lastResult;
    }

    public static void invalidate() {
        lastResult = WorkspaceFlowResult.EMPTY;
        lastPageMap = Collections.emptyMap();
        lastLinks = Collections.emptyList();
    }

    public static List<InterPageLink> getLinksForSource(String srcPageId, String srcNodeId) {
        if (srcPageId == null || srcNodeId == null || lastLinks.isEmpty()) {
            return Collections.emptyList();
        }
        List<InterPageLink> matched = new ArrayList<>();
        for (InterPageLink link : lastLinks) {
            if (srcPageId.equals(link.sourcePageId()) && srcNodeId.equals(link.sourceNodeId())) {
                matched.add(link);
            }
        }
        return matched;
    }

    public static int getOutgoingMaxPriority(FlowGraph graph, RecipeNode node) {
        return getOutgoingMaxPriority(graph, node, new HashSet<>());
    }

    private static int getOutgoingMaxPriority(FlowGraph graph, RecipeNode node, Set<String> visited) {
        if (graph == null || node == null) return 0;
        if (!visited.add(node.getId())) return 0;
        int maxPri = 0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!edge.fromNodeId().equals(node.getId()) || edge.outputIndex() != 0) continue;
            maxPri = Math.max(maxPri, edge.priority());
            RecipeNode target = graph.findNodeById(edge.toNodeId());
            if (target != null && target.isReroute() && edge.priority() == 0) {
                maxPri = Math.max(maxPri, getOutgoingMaxPriority(graph, target, visited));
            }
        }
        return maxPri;
    }

    public static BoardPage getPage(String pageId) {
        if (pageId == null) return null;
        BoardPage page = lastPageMap.get(pageId);
        if (page != null) return page;
        BoardManager bm = BoardManager.getInstance();
        return bm != null ? bm.getPage(pageId).orElse(null) : null;
    }

    public static String findPageIdForGraph(FlowGraph graph) {
        if (graph == null) return null;
        for (Map.Entry<String, BoardPage> entry : lastPageMap.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getGraph() == graph) {
                return entry.getKey();
            }
        }
        BoardManager bm = BoardManager.getInstance();
        if (bm != null) {
            for (BoardPage p : bm.getPages()) {
                if (p != null && p.getGraph() == graph) {
                    return p.getId();
                }
            }
        }
        return null;
    }

    public static WorkspaceFlowResult coordinate() {
        return coordinate(BoardManager.getInstance().getPages());
    }

    public static WorkspaceFlowResult coordinate(List<BoardPage> pages) {
        if (pages == null || pages.isEmpty()) {
            lastResult = WorkspaceFlowResult.EMPTY;
            lastPageMap = Collections.emptyMap();
            return lastResult;
        }

        Map<String, BoardPage> pageMap = new LinkedHashMap<>();
        for (BoardPage page : pages) {
            if (page != null && page.getId() != null) {
                pageMap.put(page.getId(), page);
            }
        }
        lastPageMap = Collections.unmodifiableMap(pageMap);

        Set<String> brokenLinkNodeIds = new HashSet<>();
        List<InterPageLink> links = collectInterPageLinks(pageMap, brokenLinkNodeIds);
        lastLinks = Collections.unmodifiableList(links);

        Map<String, Set<String>> adjacency = buildAdjacencyMap(pageMap.keySet(), links);
        CycleDetectionResult cycleResult = detectCyclesTarjan(adjacency, links);

        List<String> topologicalOrder = computeTopologicalOrder(pageMap.keySet(), adjacency, cycleResult.circularPageIds());

        for (BoardPage page : pageMap.values()) {
            resetPageRerouteRates(page);
        }

        Map<String, Double> allocatedRates = new LinkedHashMap<>();
        Map<String, Double> demandRates = new LinkedHashMap<>();
        Set<String> starvedNodeIds = new HashSet<>();

        solveAndPropagateFlows(
                pageMap,
                topologicalOrder,
                links,
                cycleResult.circularPageIds(),
                brokenLinkNodeIds,
                allocatedRates,
                demandRates,
                starvedNodeIds
        );

        for (String cNodeId : cycleResult.circularNodeIds()) {
            allocatedRates.put(cNodeId, 0.0);
            demandRates.put(cNodeId, 0.0);
        }
        for (String bNodeId : brokenLinkNodeIds) {
            allocatedRates.put(bNodeId, 0.0);
            demandRates.put(bNodeId, 0.0);
        }

        WorkspaceFlowResult result = new WorkspaceFlowResult(
                topologicalOrder,
                Collections.unmodifiableSet(cycleResult.circularPageIds()),
                Collections.unmodifiableSet(cycleResult.circularNodeIds()),
                Collections.unmodifiableSet(brokenLinkNodeIds),
                Collections.unmodifiableSet(starvedNodeIds),
                Collections.unmodifiableMap(allocatedRates),
                Collections.unmodifiableMap(demandRates)
        );
        lastResult = result;
        return result;
    }

    private static List<InterPageLink> collectInterPageLinks(Map<String, BoardPage> pageMap, Set<String> brokenLinkNodeIds) {
        List<InterPageLink> links = new ArrayList<>();
        for (BoardPage page : pageMap.values()) {
            collectPageInterLinks(page, pageMap, brokenLinkNodeIds, links);
        }
        return links;
    }

    private static void collectPageInterLinks(
            BoardPage page,
            Map<String, BoardPage> pageMap,
            Set<String> brokenLinkNodeIds,
            List<InterPageLink> links
    ) {
        if (page.getGraph() == null) return;
        for (RecipeNode node : page.getGraph().getNodes()) {
            if (!node.isReroute()) continue;
            collectExplicitExportLinks(page, node, pageMap, brokenLinkNodeIds, links);
            collectImplicitLinkedSourceLinks(page, node, pageMap, brokenLinkNodeIds, links);
        }
    }

    private static void collectExplicitExportLinks(
            BoardPage page,
            RecipeNode node,
            Map<String, BoardPage> pageMap,
            Set<String> brokenLinkNodeIds,
            List<InterPageLink> links
    ) {
        for (CrossPageExportTarget target : node.getExportTargets()) {
            BoardPage targetPage = pageMap.get(target.targetPageId());
            if (targetPage == null) {
                brokenLinkNodeIds.add(node.getId());
                continue;
            }
            RecipeNode consumer = findConsumerNode(targetPage, page.getId(), node.getId());
            String targetNodeId = consumer != null ? consumer.getId() : "";
            int pri = target.priority();
            if (consumer != null && targetPage.getGraph() != null) {
                pri = Math.max(pri, getOutgoingMaxPriority(targetPage.getGraph(), consumer));
            }
            links.add(new InterPageLink(page.getId(), node.getId(), target.targetPageId(), targetNodeId, pri, target.fixedLimit()));
        }
    }

    private static void collectImplicitLinkedSourceLinks(
            BoardPage page,
            RecipeNode node,
            Map<String, BoardPage> pageMap,
            Set<String> brokenLinkNodeIds,
            List<InterPageLink> links
    ) {
        if (!node.isLinkedJunction()) return;
        String srcPageId = node.getLinkedSourcePageId();
        String srcNodeId = node.getLinkedSourceNodeId();
        if (srcPageId.isBlank()) return;

        BoardPage srcPage = pageMap.get(srcPageId);
        if (srcPage == null) {
            brokenLinkNodeIds.add(node.getId());
            return;
        }
        RecipeNode srcNode = srcPage.getGraph().findNodeById(srcNodeId);
        if (srcNode == null || !srcNode.isReroute()) {
            brokenLinkNodeIds.add(node.getId());
            return;
        }
        boolean hasExplicitExport = srcNode.getExportTargets().stream()
                .anyMatch(exp -> exp.targetPageId().equals(page.getId()));
        if (!hasExplicitExport) {
            int derivedPriority = getOutgoingMaxPriority(page.getGraph(), node);
            links.add(new InterPageLink(srcPageId, srcNodeId, page.getId(), node.getId(), derivedPriority, 0.0));
        }
    }

    private static Map<String, Set<String>> buildAdjacencyMap(Set<String> allPageIds, List<InterPageLink> links) {
        Map<String, Set<String>> adj = new LinkedHashMap<>();
        for (String pageId : allPageIds) {
            adj.put(pageId, new LinkedHashSet<>());
        }
        for (InterPageLink link : links) {
            if (adj.containsKey(link.sourcePageId()) && adj.containsKey(link.targetPageId())) {
                adj.get(link.sourcePageId()).add(link.targetPageId());
            }
        }
        return adj;
    }

    private record CycleDetectionResult(Set<String> circularPageIds, Set<String> circularNodeIds) {}

    private static CycleDetectionResult detectCyclesTarjan(Map<String, Set<String>> adj, List<InterPageLink> links) {
        Map<String, Integer> indices = new HashMap<>();
        Map<String, Integer> lowlinks = new HashMap<>();
        Deque<String> stack = new ArrayDeque<>();
        Set<String> onStack = new HashSet<>();
        List<List<String>> sccs = new ArrayList<>();
        int[] indexCounter = new int[]{0};

        for (String node : adj.keySet()) {
            if (!indices.containsKey(node)) {
                tarjanStrongConnect(node, adj, indices, lowlinks, stack, onStack, sccs, indexCounter);
            }
        }

        Set<String> circularPageIds = new HashSet<>();
        Set<String> circularNodeIds = new HashSet<>();

        for (List<String> scc : sccs) {
            if (!isSccCycle(scc, adj)) continue;
            circularPageIds.addAll(scc);
            collectCircularNodesFromLinks(scc, links, circularNodeIds);
        }

        return new CycleDetectionResult(circularPageIds, circularNodeIds);
    }

    private static boolean isSccCycle(List<String> scc, Map<String, Set<String>> adj) {
        if (scc.size() > 1) return true;
        if (scc.size() == 1) {
            String single = scc.get(0);
            return adj.getOrDefault(single, Collections.emptySet()).contains(single);
        }
        return false;
    }

    private static void collectCircularNodesFromLinks(
            List<String> scc,
            List<InterPageLink> links,
            Set<String> circularNodeIds
    ) {
        for (InterPageLink link : links) {
            if (!scc.contains(link.sourcePageId()) || !scc.contains(link.targetPageId())) {
                continue;
            }
            addNodeIdIfPresent(circularNodeIds, link.sourceNodeId());
            addNodeIdIfPresent(circularNodeIds, link.targetNodeId());
        }
    }

    private static void addNodeIdIfPresent(Set<String> set, String nodeId) {
        if (nodeId != null && !nodeId.isBlank()) {
            set.add(nodeId);
        }
    }

    private static void tarjanStrongConnect(
            String u,
            Map<String, Set<String>> adj,
            Map<String, Integer> indices,
            Map<String, Integer> lowlinks,
            Deque<String> stack,
            Set<String> onStack,
            List<List<String>> sccs,
            int[] indexCounter
    ) {
        indices.put(u, indexCounter[0]);
        lowlinks.put(u, indexCounter[0]);
        indexCounter[0]++;
        stack.push(u);
        onStack.add(u);

        for (String v : adj.getOrDefault(u, Collections.emptySet())) {
            if (!indices.containsKey(v)) {
                tarjanStrongConnect(v, adj, indices, lowlinks, stack, onStack, sccs, indexCounter);
                lowlinks.put(u, Math.min(lowlinks.get(u), lowlinks.get(v)));
            } else if (onStack.contains(v)) {
                lowlinks.put(u, Math.min(lowlinks.get(u), indices.get(v)));
            }
        }

        if (lowlinks.get(u).equals(indices.get(u))) {
            List<String> component = new ArrayList<>();
            String w;
            do {
                w = stack.pop();
                onStack.remove(w);
                component.add(w);
            } while (!u.equals(w));
            sccs.add(component);
        }
    }

    private static List<String> computeTopologicalOrder(
            Set<String> allPages,
            Map<String, Set<String>> adj,
            Set<String> circularPages
    ) {
        Map<String, Integer> inDegrees = new LinkedHashMap<>();
        for (String p : allPages) {
            inDegrees.put(p, 0);
        }

        for (Map.Entry<String, Set<String>> entry : adj.entrySet()) {
            String u = entry.getKey();
            for (String v : entry.getValue()) {
                if (circularPages.contains(u) && circularPages.contains(v)) {
                    continue;
                }
                inDegrees.put(v, inDegrees.getOrDefault(v, 0) + 1);
            }
        }

        Queue<String> queue = new ArrayDeque<>();
        for (Map.Entry<String, Integer> entry : inDegrees.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<String> order = new ArrayList<>();
        while (!queue.isEmpty()) {
            String u = queue.poll();
            order.add(u);

            for (String v : adj.getOrDefault(u, Collections.emptySet())) {
                if (circularPages.contains(u) && circularPages.contains(v)) {
                    continue;
                }
                int nextDeg = inDegrees.get(v) - 1;
                inDegrees.put(v, nextDeg);
                if (nextDeg == 0) {
                    queue.add(v);
                }
            }
        }

        for (String p : allPages) {
            if (!order.contains(p)) {
                order.add(p);
            }
        }

        return order;
    }

    private static void solveAndPropagateFlows(
            Map<String, BoardPage> pageMap,
            List<String> topologicalOrder,
            List<InterPageLink> links,
            Set<String> circularPages,
            Set<String> brokenLinkNodeIds,
            Map<String, Double> allocatedRates,
            Map<String, Double> demandRates,
            Set<String> starvedNodeIds
    ) {
        for (String pageId : topologicalOrder) {
            BoardPage page = pageMap.get(pageId);
            if (page == null || page.getGraph() == null) continue;
            propagatePageFlows(page, pageMap, links, circularPages, brokenLinkNodeIds, allocatedRates, demandRates, starvedNodeIds);
        }
        for (BoardPage page : pageMap.values()) {
            if (page.getGraph() != null) {
                page.getGraph().setCachedSummary(FlowGraphSolver.computeSummary(page.getGraph()));
            }
        }
    }

    private static void propagatePageFlows(
            BoardPage page,
            Map<String, BoardPage> pageMap,
            List<InterPageLink> links,
            Set<String> circularPages,
            Set<String> brokenLinkNodeIds,
            Map<String, Double> allocatedRates,
            Map<String, Double> demandRates,
            Set<String> starvedNodeIds
    ) {
        FlowGraph graph = page.getGraph();
        graph.setCachedSummary(FlowGraphSolver.computeSummary(graph));

        for (RecipeNode node : graph.getNodes()) {
            if (!node.isReroute()) continue;
            processRerouteNodeFlows(page.getId(), node, graph, pageMap, links, circularPages, brokenLinkNodeIds, allocatedRates, demandRates, starvedNodeIds);
        }
    }

    private static void processRerouteNodeFlows(
            String pageId,
            RecipeNode node,
            FlowGraph graph,
            Map<String, BoardPage> pageMap,
            List<InterPageLink> links,
            Set<String> circularPages,
            Set<String> brokenLinkNodeIds,
            Map<String, Double> allocatedRates,
            Map<String, Double> demandRates,
            Set<String> starvedNodeIds
    ) {
        Map<CrossPageExportTarget, RecipeNode> targetConsumerMap = new LinkedHashMap<>();
        Map<CrossPageExportTarget, Double> targetDemands = new LinkedHashMap<>();

        resolveExportTargets(pageId, node, pageMap, circularPages, brokenLinkNodeIds, targetConsumerMap, targetDemands, demandRates);
        resolveImplicitLinks(pageId, node, pageMap, links, targetConsumerMap, targetDemands, demandRates);

        if (targetDemands.isEmpty()) return;

        double totalProducerRate = FlowEdgeAllocator.getEffectiveProducerOutputRate(graph, node, 0);
        Map<CrossPageExportTarget, Double> allocations = FlowEdgeAllocator.calculateCrossPageAllocations(
                graph, node, totalProducerRate, targetDemands
        );

        applyAllocations(pageId, allocations, targetConsumerMap, targetDemands, circularPages, allocatedRates, starvedNodeIds);

        double totalExport = 0.0;
        for (double val : allocations.values()) {
            totalExport += val;
        }
        node.asJunction().setAllocatedExportRate(totalExport);
    }

    private static void resolveExportTargets(
            String pageId,
            RecipeNode node,
            Map<String, BoardPage> pageMap,
            Set<String> circularPages,
            Set<String> brokenLinkNodeIds,
            Map<CrossPageExportTarget, RecipeNode> targetConsumerMap,
            Map<CrossPageExportTarget, Double> targetDemands,
            Map<String, Double> demandRates
    ) {
        for (CrossPageExportTarget target : node.getExportTargets()) {
            BoardPage dstPage = pageMap.get(target.targetPageId());
            if (dstPage == null) {
                brokenLinkNodeIds.add(node.getId());
                continue;
            }
            RecipeNode consumer = findConsumerNode(dstPage, pageId, node.getId());
            CrossPageExportTarget effectiveTarget = resolveEffectiveTarget(target, consumer, dstPage);

            if (consumer != null) {
                targetConsumerMap.put(effectiveTarget, consumer);
                syncLinkedJunctionIngredient(node, consumer);
            }
            if (circularPages.contains(pageId) && circularPages.contains(target.targetPageId())) {
                targetDemands.put(effectiveTarget, 0.0);
                if (consumer != null) {
                    consumer.asJunction().setAllocatedInputRate(0.0);
                }
                continue;
            }
            if (consumer == null) {
                targetDemands.put(effectiveTarget, 0.0);
                continue;
            }
            double demand = calculateConsumerDemand(dstPage.getGraph(), consumer);
            targetDemands.put(effectiveTarget, demand);
            demandRates.put(consumer.getId(), demand);
        }
    }

    private static CrossPageExportTarget resolveEffectiveTarget(CrossPageExportTarget target, RecipeNode consumer, BoardPage dstPage) {
        if (consumer == null || dstPage == null || dstPage.getGraph() == null) {
            return target;
        }
        int consumerPri = getOutgoingMaxPriority(dstPage.getGraph(), consumer);
        if (consumerPri <= target.priority()) {
            return target;
        }
        return new CrossPageExportTarget(target.targetPageId(), consumerPri, target.fixedLimit());
    }

    private static void resolveImplicitLinks(
            String pageId,
            RecipeNode node,
            Map<String, BoardPage> pageMap,
            List<InterPageLink> links,
            Map<CrossPageExportTarget, RecipeNode> targetConsumerMap,
            Map<CrossPageExportTarget, Double> targetDemands,
            Map<String, Double> demandRates
    ) {
        for (InterPageLink link : links) {
            if (!link.sourcePageId().equals(pageId) || !node.getId().equals(link.sourceNodeId())) {
                continue;
            }
            BoardPage dstPage = pageMap.get(link.targetPageId());
            if (dstPage == null) {
                continue;
            }
            RecipeNode consumer = resolveLinkConsumer(link, dstPage, pageId, node.getId());
            if (consumer == null) {
                continue;
            }

            int pri = link.priority();
            if (dstPage.getGraph() != null) {
                pri = Math.max(pri, getOutgoingMaxPriority(dstPage.getGraph(), consumer));
            }
            CrossPageExportTarget implicitTarget = new CrossPageExportTarget(link.targetPageId(), pri, link.fixedLimit());
            if (targetDemands.containsKey(implicitTarget)) {
                continue;
            }
            targetConsumerMap.put(implicitTarget, consumer);
            syncLinkedJunctionIngredient(node, consumer);
            double demand = calculateConsumerDemand(dstPage.getGraph(), consumer);
            targetDemands.put(implicitTarget, demand);
            demandRates.put(consumer.getId(), demand);
        }
    }

    private static RecipeNode resolveLinkConsumer(InterPageLink link, BoardPage dstPage, String srcPageId, String srcNodeId) {
        if (link.targetNodeId() != null && !link.targetNodeId().isBlank() && dstPage.getGraph() != null) {
            RecipeNode found = dstPage.getGraph().findNodeById(link.targetNodeId());
            if (found != null) return found;
        }
        return findConsumerNode(dstPage, srcPageId, srcNodeId);
    }

    private static void applyAllocations(
            String pageId,
            Map<CrossPageExportTarget, Double> allocations,
            Map<CrossPageExportTarget, RecipeNode> targetConsumerMap,
            Map<CrossPageExportTarget, Double> targetDemands,
            Set<String> circularPages,
            Map<String, Double> allocatedRates,
            Set<String> starvedNodeIds
    ) {
        for (Map.Entry<CrossPageExportTarget, Double> entry : allocations.entrySet()) {
            CrossPageExportTarget target = entry.getKey();
            double alloc = circularPages.contains(pageId) && circularPages.contains(target.targetPageId()) ? 0.0 : entry.getValue();

            RecipeNode consumer = targetConsumerMap.get(target);
            if (consumer == null) {
                continue;
            }
            consumer.asJunction().setAllocatedInputRate(alloc);
            allocatedRates.put(consumer.getId(), alloc);

            double demand = targetDemands.getOrDefault(target, 0.0);
            if (demand > 0.0001 && alloc < demand - 0.001) {
                starvedNodeIds.add(consumer.getId());
            }
        }
    }

    private static double calculateConsumerDemand(FlowGraph graph, RecipeNode consumerNode) {
        if (graph == null || consumerNode == null) return 0.0;
        double downstreamDemand = FlowEdgeAllocator.getConnectedConsumerDemand(graph, consumerNode, 0);
        if (downstreamDemand > 0.0001) {
            return downstreamDemand;
        }
        if (isUnconstrainedRelayConsumer(graph, consumerNode, new HashSet<>())) {
            return Double.MAX_VALUE;
        }
        return 0.0;
    }

    private static boolean isUnconstrainedRelayConsumer(FlowGraph graph, RecipeNode node, Set<String> visited) {
        if (node == null || !node.isReroute() || node.isVoidSink()) return false;
        if (!visited.add(node.getId())) return false;

        List<FlowGraph.ConnectionEdge> outEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(node.getId()) && edge.outputIndex() == 0) {
                outEdges.add(edge);
            }
        }
        if (outEdges.isEmpty()) {
            return true;
        }
        for (FlowGraph.ConnectionEdge edge : outEdges) {
            RecipeNode next = graph.findNodeById(edge.toNodeId());
            if (next != null && next.isReroute() && isUnconstrainedRelayConsumer(graph, next, visited)) {
                return true;
            }
        }
        return false;
    }

    public static RecipeNode findConsumerNode(BoardPage dstPage, String srcPageId, String srcNodeId) {
        if (dstPage == null || dstPage.getGraph() == null) return null;
        RecipeNode fallback = null;
        for (RecipeNode n : dstPage.getGraph().getNodes()) {
            if (!isCandidateLinkedJunction(n, srcPageId)) continue;
            MatchResult match = checkNodeMatch(n, srcNodeId);
            if (match == MatchResult.EXACT) return n;
            if (match == MatchResult.FALLBACK && fallback == null) fallback = n;
        }
        return fallback;
    }

    private enum MatchResult { NONE, EXACT, FALLBACK }

    private static boolean isCandidateLinkedJunction(RecipeNode n, String srcPageId) {
        if (n == null || !n.isReroute() || n.getSupplyMode() != SupplyMode.LINKED_JUNCTION) {
            return false;
        }
        if (srcPageId == null || srcPageId.isBlank()) {
            return true;
        }
        return srcPageId.equals(n.getLinkedSourcePageId());
    }

    private static MatchResult checkNodeMatch(RecipeNode n, String srcNodeId) {
        if (srcNodeId == null || srcNodeId.isBlank()) return MatchResult.EXACT;
        String consumerSrcNodeId = n.getLinkedSourceNodeId();
        if (srcNodeId.equals(consumerSrcNodeId)) return MatchResult.EXACT;
        if (consumerSrcNodeId == null || consumerSrcNodeId.isBlank()) return MatchResult.FALLBACK;
        return MatchResult.NONE;
    }

    private static void resetPageRerouteRates(BoardPage page) {
        if (page == null || page.getGraph() == null) return;
        for (RecipeNode node : page.getGraph().getNodes()) {
            resetNodeRates(node);
        }
    }

    private static void resetNodeRates(RecipeNode node) {
        if (node == null || !node.isReroute()) return;
        if (node.getSupplyMode() == SupplyMode.LINKED_JUNCTION) {
            node.asJunction().setAllocatedInputRate(0.0);
        }
        node.asJunction().setAllocatedExportRate(0.0);
    }

    private static void syncLinkedJunctionIngredient(RecipeNode sourceNode, RecipeNode consumerNode) {
        if (sourceNode == null || consumerNode == null || !consumerNode.isLinkedJunction()) {
            return;
        }
        IngredientStack srcIng = sourceNode.getRerouteIngredient();
        if (srcIng != null) {
            IngredientStack curIng = consumerNode.getRerouteIngredient();
            if (curIng == null || !curIng.equals(srcIng)) {
                consumerNode.bindRerouteIngredient(srcIng.copy());
            }
        }
    }
}
