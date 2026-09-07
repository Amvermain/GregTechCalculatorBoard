package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.*;

/**
 * Analyzes graph topology, finding upstream/downstream subgraphs,
 * direct suppliers, and supplier propagation chains.
 */
public final class FlowGraphTopologyAnalyzer {

    private FlowGraphTopologyAnalyzer() {}

    public static Set<String> getDirectSuppliers(FlowGraph graph, String targetNodeId) {
        Set<String> directSuppliers = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(targetNodeId);
        visited.add(targetNodeId);

        while (!queue.isEmpty()) {
            String currId = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.toNodeId().equals(currId)) {
                    RecipeNode src = graph.findNodeById(edge.fromNodeId());
                    if (src != null && visited.add(src.getId())) {
                        if (src.isReroute()) {
                            queue.add(src.getId());
                        } else {
                            directSuppliers.add(src.getId());
                        }
                    }
                }
            }
        }
        return directSuppliers;
    }

    public static Set<String> findDownstreamNodes(FlowGraph graph, String anchorId, Set<String> directAnchorSuppliers) {
        Set<String> downstream = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(anchorId);

        while (!queue.isEmpty()) {
            String currId = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.fromNodeId().equals(currId)) {
                    String nextId = edge.toNodeId();
                    if (!nextId.equals(anchorId) && !directAnchorSuppliers.contains(nextId)) {
                        if (downstream.add(nextId)) {
                            queue.add(nextId);
                        }
                    }
                }
            }
        }
        return downstream;
    }

    public static Set<String> findUpstreamNodes(FlowGraph graph, String anchorId, Set<String> downstreamNodes) {
        Set<String> upstream = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(anchorId);
        if (downstreamNodes != null) {
            queue.addAll(downstreamNodes);
        }

        while (!queue.isEmpty()) {
            String currId = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.toNodeId().equals(currId)) {
                    String prevId = edge.fromNodeId();
                    if (!prevId.equals(anchorId) && (downstreamNodes == null || !downstreamNodes.contains(prevId))) {
                        if (upstream.add(prevId)) {
                            queue.add(prevId);
                        }
                    }
                }
            }
        }
        return upstream;
    }

    public static void collectFeedingProducers(FlowGraph graph, String targetNodeId, int inIdx, Set<RecipeNode> result) {
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(targetNodeId + ":" + inIdx);
        visited.add(targetNodeId + ":" + inIdx);

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            int colonIdx = curr.indexOf(':');
            if (colonIdx < 0) continue;
            String currNodeId = curr.substring(0, colonIdx);
            int currInIdx = Integer.parseInt(curr.substring(colonIdx + 1));

            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                processFeedingEdge(graph, edge, currNodeId, currInIdx, visited, queue, result);
            }
        }
    }

    private static void processFeedingEdge(
            FlowGraph graph,
            FlowGraph.ConnectionEdge edge,
            String currNodeId,
            int currInIdx,
            Set<String> visited,
            Queue<String> queue,
            Set<RecipeNode> result
    ) {
        if (!edge.toNodeId().equals(currNodeId) || edge.inputIndex() != currInIdx) return;
        RecipeNode src = graph.findNodeById(edge.fromNodeId());
        if (src == null) return;

        if (!src.isReroute()) {
            result.add(src);
            return;
        }

        if (src.isExternalSupply() || src.isInfiniteSupply() || src.isBaseNode()) {
            result.add(src);
        }
        String nextKey = src.getId() + ":0";
        if (visited.add(nextKey)) {
            queue.add(nextKey);
        }
    }

    public static Set<String> findConnectedComponent(FlowGraph graph, Collection<String> startNodeIds) {
        if (graph == null || startNodeIds == null || startNodeIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> connected = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        for (String id : startNodeIds) {
            if (id != null && connected.add(id)) {
                queue.add(id);
            }
        }

        while (!queue.isEmpty()) {
            String currId = queue.poll();
            collectAdjacentNodeIds(graph, currId, connected, queue);
        }
        return connected;
    }

    private static void collectAdjacentNodeIds(FlowGraph graph, String currId, Set<String> connected, Queue<String> queue) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(currId)) {
                offerConnectedNode(edge.toNodeId(), connected, queue);
            } else if (edge.toNodeId().equals(currId)) {
                offerConnectedNode(edge.fromNodeId(), connected, queue);
            }
        }
    }

    private static void offerConnectedNode(String nodeId, Set<String> connected, Queue<String> queue) {
        if (connected.add(nodeId)) {
            queue.add(nodeId);
        }
    }

    public static boolean hasDirectedPath(FlowGraph graph, String fromId, String toId) {
        if (graph == null || fromId == null || toId == null) return false;
        if (fromId.equals(toId)) return true;

        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(fromId);
        visited.add(fromId);

        while (!queue.isEmpty()) {
            String currId = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (!edge.fromNodeId().equals(currId)) continue;
                String nextId = edge.toNodeId();
                if (nextId.equals(toId)) return true;
                if (visited.add(nextId)) {
                    queue.add(nextId);
                }
            }
        }
        return false;
    }
}

