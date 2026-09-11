package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
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

    public static List<FlowGraph.ConnectionEdge> findExternalBoundaryEdges(FlowGraph graph, CanvasGroupFrame frame) {
        if (graph == null || frame == null) return Collections.emptyList();
        Set<String> enclosedIds = new HashSet<>(frame.getContainedNodeIds());
        for (RecipeNode n : frame.getEnclosedNodes(graph)) {
            enclosedIds.add(n.getId());
        }
        List<FlowGraph.ConnectionEdge> boundaryEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            boolean fromInside = enclosedIds.contains(edge.fromNodeId());
            boolean toInside = enclosedIds.contains(edge.toNodeId());
            if (fromInside ^ toInside) {
                boundaryEdges.add(edge);
            }
        }
        return boundaryEdges;
    }

    /**
     * Aggregates boundary I/O ports across all operational nodes enclosed within a folded group frame.
     *
     * @param graph the flow graph containing the nodes
     * @param frame the folded group frame
     * @return the aggregated port summary containing grouped input and output ports
     */
    public static FoldedPortSummary aggregateFoldedPorts(FlowGraph graph, CanvasGroupFrame frame) {
        if (graph == null || frame == null) {
            return new FoldedPortSummary(Collections.emptyList(), Collections.emptyList());
        }
        Map<String, AggregatedFoldedPortBuilder> inputBuilders = new LinkedHashMap<>();
        Map<String, AggregatedFoldedPortBuilder> outputBuilders = new LinkedHashMap<>();

        for (RecipeNode node : frame.getEnclosedNodes(graph)) {
            if (node == null || node.isReroute() || !node.isOperational(graph)) continue;

            for (int i = 0; i < node.getInputs().size(); i++) {
                IngredientStack stack = node.getInputs().get(i);
                if (stack == null || stack.getAmount() <= 0.0) continue;
                String key = (stack.isFluid() ? "F:" : "I:") + (stack.getId() != null ? stack.getId().toString() : "null");
                double nominal = node.getInputSlotRate(i, false);
                double actual = node.getInputSlotRate(i, true);
                inputBuilders.computeIfAbsent(key, k -> new AggregatedFoldedPortBuilder(stack, true))
                        .add(nominal, actual, new RecipeNode.PortOrigin(node.getId(), i));
            }

            for (int i = 0; i < node.getOutputs().size(); i++) {
                IngredientStack stack = node.getOutputs().get(i);
                if (stack == null || stack.getAmount() <= 0.0) continue;
                String key = (stack.isFluid() ? "F:" : "I:") + (stack.getId() != null ? stack.getId().toString() : "null");
                double nominal = node.getOutputSlotRate(i, false);
                double actual = node.getOutputSlotRate(i, true);
                outputBuilders.computeIfAbsent(key, k -> new AggregatedFoldedPortBuilder(stack, false))
                        .add(nominal, actual, new RecipeNode.PortOrigin(node.getId(), i));
            }
        }

        List<AggregatedFoldedPort> inputs = new ArrayList<>(inputBuilders.size());
        for (AggregatedFoldedPortBuilder b : inputBuilders.values()) {
            inputs.add(b.build());
        }

        List<AggregatedFoldedPort> outputs = new ArrayList<>(outputBuilders.size());
        for (AggregatedFoldedPortBuilder b : outputBuilders.values()) {
            outputs.add(b.build());
        }

        return new FoldedPortSummary(inputs, outputs);
    }

    /**
     * Represents a single aggregated virtual port on a folded shared pool card.
     */
    public record AggregatedFoldedPort(
            IngredientStack ingredient,
            double nominalRate,
            double actualRate,
            boolean isInput,
            boolean hasDeficit,
            List<RecipeNode.PortOrigin> internalOrigins
    ) {}

    /**
     * Container holding all aggregated input and output ports for a folded frame.
     */
    public record FoldedPortSummary(
            List<AggregatedFoldedPort> inputs,
            List<AggregatedFoldedPort> outputs
    ) {
        public int maxPortCount() {
            return Math.max(inputs.size(), outputs.size());
        }

        public int findInputIndexForOrigin(String nodeId, int portIndex) {
            for (int i = 0; i < inputs.size(); i++) {
                for (RecipeNode.PortOrigin origin : inputs.get(i).internalOrigins()) {
                    if (origin.internalNodeId().equals(nodeId) && origin.internalPortIndex() == portIndex) {
                        return i;
                    }
                }
            }
            return -1;
        }

        public int findOutputIndexForOrigin(String nodeId, int portIndex) {
            for (int i = 0; i < outputs.size(); i++) {
                for (RecipeNode.PortOrigin origin : outputs.get(i).internalOrigins()) {
                    if (origin.internalNodeId().equals(nodeId) && origin.internalPortIndex() == portIndex) {
                        return i;
                    }
                }
            }
            return -1;
        }
    }

    private static class AggregatedFoldedPortBuilder {
        private final IngredientStack baseStack;
        private final boolean isInput;
        private double nominalRate = 0.0;
        private double actualRate = 0.0;
        private final List<RecipeNode.PortOrigin> origins = new ArrayList<>();

        public AggregatedFoldedPortBuilder(IngredientStack baseStack, boolean isInput) {
            this.baseStack = baseStack;
            this.isInput = isInput;
        }

        public void add(double nominal, double actual, RecipeNode.PortOrigin origin) {
            this.nominalRate += nominal;
            this.actualRate += actual;
            this.origins.add(origin);
        }

        public AggregatedFoldedPort build() {
            IngredientStack displayStack = baseStack.withAmount(nominalRate);
            boolean hasDeficit = isInput && (actualRate < nominalRate - 0.001);
            return new AggregatedFoldedPort(displayStack, nominalRate, actualRate, isInput, hasDeficit, origins);
        }
    }
}

