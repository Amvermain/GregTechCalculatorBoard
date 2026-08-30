package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.*;

/**
 * Fast O(V + E) directed cycle detector for FlowGraph canvas connections using 3-color DFS.
 */
public final class CycleDetector {

    private enum Color {
        WHITE, // Unvisited
        GRAY,  // Currently in recursion stack (active path)
        BLACK  // Finished visiting downstream
    }

    private CycleDetector() {}

    /**
     * Checks whether the given graph contains any directed feedback cycle among its connections.
     */
    public static boolean hasCycle(FlowGraph graph) {
        if (graph == null || graph.getNodes().isEmpty() || graph.getConnections().isEmpty()) {
            return false;
        }

        // Build adjacency list: fromNodeId -> list of toNodeIds
        Map<String, List<String>> adj = new HashMap<>();
        for (RecipeNode node : graph.getNodes()) {
            adj.put(node.getId(), new ArrayList<>());
        }

        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (adj.containsKey(edge.fromNodeId()) && adj.containsKey(edge.toNodeId())) {
                adj.get(edge.fromNodeId()).add(edge.toNodeId());
            }
        }

        Map<String, Color> colors = new HashMap<>();
        for (RecipeNode node : graph.getNodes()) {
            colors.put(node.getId(), Color.WHITE);
        }

        for (RecipeNode startNode : graph.getNodes()) {
            String startId = startNode.getId();
            if (colors.get(startId) == Color.WHITE) {
                Deque<String> stack = new ArrayDeque<>();
                stack.push(startId);

                while (!stack.isEmpty()) {
                    String curr = stack.peek();
                    Color currColor = colors.get(curr);

                    if (currColor == Color.WHITE) {
                        colors.put(curr, Color.GRAY);
                    }

                    boolean hasUnvisitedChild = false;
                    List<String> neighbors = adj.getOrDefault(curr, Collections.emptyList());
                    for (String next : neighbors) {
                        Color nextColor = colors.get(next);
                        if (nextColor == Color.GRAY) {
                            return true; // Back-edge detected!
                        }
                        if (nextColor == Color.WHITE) {
                            stack.push(next);
                            hasUnvisitedChild = true;
                            break;
                        }
                    }

                    if (!hasUnvisitedChild) {
                        stack.pop();
                        colors.put(curr, Color.BLACK);
                    }
                }
            }
        }

        return false;
    }
}
