package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

/**
 * Pure graph topology data container for the Calculator Board.
 * Holds nodes, connection edges, and NBT serialization/deserialization.
 * Complex calculation and module algorithms are delegated to FlowGraphSolver and FlowGraphModuleHandler.
 */
public class FlowGraph {
    private final List<RecipeNode> nodes = new ArrayList<>();
    private final List<ConnectionEdge> connections = new ArrayList<>();
    private final Map<String, RecipeNode> nodeMap = new HashMap<>();

    public record ConnectionEdge(
        String fromNodeId,
        int outputIndex,
        String toNodeId,
        int inputIndex
    ) {
        public CompoundTag serializeNBT() {
            CompoundTag tag = new CompoundTag();
            tag.putString("fromNode", fromNodeId);
            tag.putInt("outIdx", outputIndex);
            tag.putString("toNode", toNodeId);
            tag.putInt("inIdx", inputIndex);
            return tag;
        }

        public static ConnectionEdge deserializeNBT(CompoundTag tag) {
            return new ConnectionEdge(
                tag.getString("fromNode"),
                tag.getInt("outIdx"),
                tag.getString("toNode"),
                tag.getInt("inIdx")
            );
        }
    }

    public List<RecipeNode> getNodes() {
        return nodes;
    }

    public List<ConnectionEdge> getConnections() {
        return connections;
    }

    public void addNode(RecipeNode node) {
        if (node != null) {
            nodes.add(node);
            nodeMap.put(node.getId(), node);
        }
    }

    public void removeNode(RecipeNode node) {
        if (node != null) {
            nodes.remove(node);
            nodeMap.remove(node.getId());
            connections.removeIf(edge -> edge.fromNodeId.equals(node.getId()) || edge.toNodeId.equals(node.getId()));
        }
    }

    public RecipeNode findNodeById(String id) {
        if (id == null) return null;
        RecipeNode cached = nodeMap.get(id);
        if (cached != null) return cached;
        // Fallback linear scan if map is not synchronized
        for (RecipeNode n : nodes) {
            if (n.getId().equals(id)) {
                nodeMap.put(id, n);
                return n;
            }
        }
        return null;
    }

    public RecipeNode findBaseNode() {
        for (RecipeNode n : nodes) {
            if (n.isBaseNode()) return n;
        }
        return null;
    }

    public void setBaseNode(RecipeNode target) {
        for (RecipeNode n : nodes) {
            n.setBaseNode(n == target);
        }
    }

    public void clear() {
        nodes.clear();
        connections.clear();
        nodeMap.clear();
    }

    public void addConnection(String fromNodeId, int outIdx, String toNodeId, int inIdx) {
        for (ConnectionEdge edge : connections) {
            if (edge.fromNodeId.equals(fromNodeId) && edge.outputIndex == outIdx
                && edge.toNodeId.equals(toNodeId) && edge.inputIndex == inIdx) {
                return;
            }
        }
        connections.add(new ConnectionEdge(fromNodeId, outIdx, toNodeId, inIdx));
    }

    public void removeConnection(ConnectionEdge edge) {
        connections.remove(edge);
    }

    public void copyFrom(FlowGraph other) {
        this.clear();
        if (other != null) {
            for (RecipeNode n : other.nodes) {
                this.addNode(n);
            }
            this.connections.addAll(other.connections);
        }
    }

    // =========================================================================
    // Solver Delegations (FlowGraphSolver)
    // =========================================================================

    public void autoRatioFromAnchor(RecipeNode anchor) {
        FlowGraphSolver.autoRatioFromAnchor(this, anchor, true);
    }

    public void autoRatioFromAnchor(RecipeNode anchor, boolean integerCounts) {
        FlowGraphSolver.autoRatioFromAnchor(this, anchor, integerCounts);
    }

    public Map<String, Double> computeNodeEfficiencies() {
        return FlowGraphSolver.computeNodeEfficiencies(this);
    }

    public FlowGraphSolver.PortFlowStats getInputPortStats(RecipeNode node, int inputIndex) {
        return FlowGraphSolver.getInputPortStats(this, node, inputIndex);
    }

    public FlowGraphSolver.PortFlowStats getOutputPortStats(RecipeNode node, int outputIndex) {
        return FlowGraphSolver.getOutputPortStats(this, node, outputIndex);
    }

    public BalanceSummary computeSummary() {
        return FlowGraphSolver.computeSummary(this);
    }

    public void optimizeMaxThroughput(boolean preferParallels, boolean integerCounts) {
        FlowGraphSolver.optimizeMaxThroughput(this, preferParallels, integerCounts);
    }

    // =========================================================================
    // Module Delegations (FlowGraphModuleHandler)
    // =========================================================================

    public RecipeNode groupIntoModule(String moduleName) {
        return FlowGraphModuleHandler.groupIntoModule(this, null, moduleName);
    }

    public RecipeNode groupIntoModule(Set<String> targetNodeIds, String moduleName) {
        return FlowGraphModuleHandler.groupIntoModule(this, targetNodeIds, moduleName);
    }

    public boolean expandModule(RecipeNode moduleNode) {
        return FlowGraphModuleHandler.expandModule(this, moduleNode);
    }

    // =========================================================================
    // NBT Serialization / Deserialization
    // =========================================================================

    public CompoundTag serializeNBT() {
        return serializeNBT(0, 0, 1.0);
    }

    public CompoundTag serializeNBT(double panX, double panY, double zoom) {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("panX", panX);
        tag.putDouble("panY", panY);
        tag.putDouble("zoom", zoom);

        ListTag nodeList = new ListTag();
        for (RecipeNode n : nodes) {
            nodeList.add(n.serializeNBT());
        }
        tag.put("nodes", nodeList);

        ListTag edgeList = new ListTag();
        for (ConnectionEdge edge : connections) {
            edgeList.add(edge.serializeNBT());
        }
        tag.put("connections", edgeList);
        return tag;
    }

    public static FlowGraph deserializeNBT(CompoundTag tag) {
        FlowGraph graph = new FlowGraph();
        if (tag.contains("nodes", Tag.TAG_LIST)) {
            ListTag nodeList = tag.getList("nodes", Tag.TAG_COMPOUND);
            for (int i = 0; i < nodeList.size(); i++) {
                graph.addNode(RecipeNode.deserializeNBT(nodeList.getCompound(i)));
            }
        }
        if (tag.contains("connections", Tag.TAG_LIST)) {
            ListTag edgeList = tag.getList("connections", Tag.TAG_COMPOUND);
            for (int i = 0; i < edgeList.size(); i++) {
                graph.connections.add(ConnectionEdge.deserializeNBT(edgeList.getCompound(i)));
            }
        }
        return graph;
    }
}
