package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.*;

/**
 * Fast O(V + E) directed cycle detector for FlowGraph canvas connections using 3-color DFS.
 */
public final class CycleDetector {

    private enum Color {
        WHITE,
        GRAY,
        BLACK
    }

    private CycleDetector() {}

    public static boolean hasCycle(FlowGraph graph) {
        if (graph == null || graph.getNodes().isEmpty() || graph.getConnections().isEmpty()) {
            return false;
        }

        Map<String, List<String>> adj = buildAdjacencyMap(graph);
        Map<String, Color> colors = initColorMap(graph.getNodes());

        for (RecipeNode startNode : graph.getNodes()) {
            if (colors.get(startNode.getId()) == Color.WHITE && traverseComponent(startNode.getId(), adj, colors)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, List<String>> buildAdjacencyMap(FlowGraph graph) {
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
        return adj;
    }

    private static Map<String, Color> initColorMap(List<RecipeNode> nodes) {
        Map<String, Color> colors = new HashMap<>();
        for (RecipeNode node : nodes) {
            colors.put(node.getId(), Color.WHITE);
        }
        return colors;
    }

    private static boolean traverseComponent(String startId, Map<String, List<String>> adj, Map<String, Color> colors) {
        Deque<String> stack = new ArrayDeque<>();
        stack.push(startId);

        while (!stack.isEmpty()) {
            if (processCurrentNode(stack, adj, colors)) {
                return true;
            }
        }
        return false;
    }

    private static boolean processCurrentNode(Deque<String> stack, Map<String, List<String>> adj, Map<String, Color> colors) {
        String curr = stack.peek();
        if (colors.get(curr) == Color.WHITE) {
            colors.put(curr, Color.GRAY);
        }

        List<String> neighbors = adj.getOrDefault(curr, Collections.emptyList());
        for (String next : neighbors) {
            Color nextColor = colors.get(next);
            if (nextColor == Color.GRAY) {
                return true;
            }
            if (nextColor == Color.WHITE) {
                stack.push(next);
                return false;
            }
        }

        stack.pop();
        colors.put(curr, Color.BLACK);
        return false;
    }
}
