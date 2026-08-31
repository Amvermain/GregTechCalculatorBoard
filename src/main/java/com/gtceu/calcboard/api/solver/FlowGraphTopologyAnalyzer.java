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
                if (edge.toNodeId().equals(currNodeId) && edge.inputIndex() == currInIdx) {
                    RecipeNode src = graph.findNodeById(edge.fromNodeId());
                    if (src != null) {
                        if (src.isReroute()) {
                            String nextKey = src.getId() + ":0";
                            if (visited.add(nextKey)) {
                                queue.add(nextKey);
                            }
                        } else {
                            result.add(src);
                        }
                    }
                }
            }
        }
    }
}
