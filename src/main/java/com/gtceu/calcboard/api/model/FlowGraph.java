package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.FlowGraphModuleHandler;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Pure graph topology data container for the Calculator Board.
 * Holds nodes, connection edges, and NBT serialization/deserialization.
 * Complex calculation and module algorithms are delegated to FlowGraphSolver and FlowGraphModuleHandler.
 */
public class FlowGraph {
    private final List<RecipeNode> nodes = new ArrayList<>();
    private final List<ConnectionEdge> connections = new ArrayList<>();
    private final List<CanvasGroupFrame> frames = new ArrayList<>();
    private final List<CanvasStickyNote> stickyNotes = new ArrayList<>();
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
        return Collections.unmodifiableList(nodes);
    }

    public List<ConnectionEdge> getConnections() {
        return connections;
    }

    public List<CanvasGroupFrame> getFrames() {
        return frames;
    }

    public List<CanvasStickyNote> getStickyNotes() {
        return stickyNotes;
    }

    public void addStickyNote(CanvasStickyNote note) {
        if (note != null && !stickyNotes.contains(note)) {
            stickyNotes.add(note);
        }
    }

    public void removeStickyNote(CanvasStickyNote note) {
        if (note != null) {
            stickyNotes.remove(note);
        }
    }

    public CanvasStickyNote findStickyNoteById(String id) {
        if (id == null) return null;
        for (CanvasStickyNote n : stickyNotes) {
            if (n.getId().equals(id)) return n;
        }
        return null;
    }

    public void addFrame(CanvasGroupFrame frame) {
        if (frame != null && !frames.contains(frame)) {
            frames.add(frame);
        }
    }

    public void removeFrame(CanvasGroupFrame frame) {
        if (frame != null) {
            if (frame.isCompoundFrame() && frame.getCompoundGroupId() != null && !frame.getCompoundGroupId().isEmpty()) {
                deleteCompoundGroup(frame.getCompoundGroupId());
            } else {
                frames.remove(frame);
            }
        }
    }

    public CanvasGroupFrame findFrameById(String id) {
        for (CanvasGroupFrame f : frames) {
            if (f.getId().equals(id)) return f;
        }
        return null;
    }

    public CanvasGroupFrame findFrameEnclosingNode(RecipeNode node) {
        if (node == null || frames.isEmpty()) return null;
        for (CanvasGroupFrame frame : frames) {
            if (frame != null) {
                if (frame.containsNode(node.getId())) {
                    return frame;
                }
                for (RecipeNode enclosed : frame.getEnclosedNodes(this)) {
                    if (enclosed != null && enclosed.getId().equals(node.getId())) {
                        return frame;
                    }
                }
            }
        }
        return null;
    }

    public boolean isNodeInSharedMachineFrame(RecipeNode node) {
        CanvasGroupFrame frame = findFrameEnclosingNode(node);
        return frame != null && frame.isSharedMachineFrame();
    }

    public void addNode(RecipeNode node) {
        if (node != null) {
            nodes.add(node);
            nodeMap.put(node.getId(), node);
        }
    }

    public void removeNode(String nodeId) {
        if (nodeId == null) return;
        RecipeNode node = findNodeById(nodeId);
        if (node != null) {
            removeNode(node);
        }
    }

    public void removeNode(RecipeNode node) {
        if (node != null) {
            if (node.isCompoundNode()) {
                deleteCompoundGroup(node.getCompoundGroupId());
            } else {
                nodes.remove(node);
                nodeMap.remove(node.getId());
                connections.removeIf(edge -> edge.fromNodeId.equals(node.getId()) || edge.toNodeId.equals(node.getId()));
                for (CanvasGroupFrame f : frames) {
                    f.removeNode(node.getId());
                }
            }
        }
    }

    public void bringNodeToFront(RecipeNode node) {
        if (node != null && nodes.remove(node)) {
            nodes.add(node);
        }
    }

    public List<RecipeNode> findCompoundSiblingNodes(String compoundGroupId) {
        if (compoundGroupId == null || compoundGroupId.isEmpty()) return Collections.emptyList();
        List<RecipeNode> siblings = new ArrayList<>();
        for (RecipeNode n : nodes) {
            if (compoundGroupId.equals(n.getCompoundGroupId())) {
                siblings.add(n);
            }
        }
        return siblings;
    }

    public CanvasGroupFrame findCompoundFrame(String compoundGroupId) {
        if (compoundGroupId == null || compoundGroupId.isEmpty()) return null;
        for (CanvasGroupFrame f : frames) {
            if (f.isCompoundFrame() && compoundGroupId.equals(f.getCompoundGroupId())) {
                return f;
            }
        }
        return null;
    }

    public void deleteCompoundGroup(String compoundGroupId) {
        if (compoundGroupId == null || compoundGroupId.isEmpty()) return;
        List<RecipeNode> siblings = findCompoundSiblingNodes(compoundGroupId);
        Set<String> siblingIds = new HashSet<>();
        for (RecipeNode n : siblings) {
            siblingIds.add(n.getId());
            nodes.remove(n);
            nodeMap.remove(n.getId());
        }

        connections.removeIf(edge -> siblingIds.contains(edge.fromNodeId) || siblingIds.contains(edge.toNodeId));

        frames.removeIf(f -> f.isCompoundFrame() && compoundGroupId.equals(f.getCompoundGroupId()));
        for (CanvasGroupFrame f : frames) {
            for (String sId : siblingIds) {
                f.removeNode(sId);
            }
        }
    }

    public void syncCompoundParameters(RecipeNode sourceNode) {
        if (sourceNode == null || !sourceNode.isCompoundNode()) return;
        String groupId = sourceNode.getCompoundGroupId();
        List<RecipeNode> siblings = findCompoundSiblingNodes(groupId);
        for (RecipeNode sibling : siblings) {
            if (sibling.getId().equals(sourceNode.getId())) continue;
            sibling.setMachineCount(sourceNode.getMachineCount());
            sibling.setTargetTier(sourceNode.getTargetTier());
            sibling.setOverclockMode(sourceNode.getOverclockMode());
            sibling.setParallel(sourceNode.getParallel());
            sibling.setSteamMode(sourceNode.getSteamMode());
            sibling.setMultiblock(sourceNode.isMultiblock());
            sibling.setThreadingConfig(sourceNode.getThreadingConfig());
            sibling.setGenerator(sourceNode.isGenerator());

            if (sourceNode.getProperties().has(com.gtceu.calcboard.api.property.NodeProperties.KINETIC_RPM)) {
                sibling.getProperties().set(com.gtceu.calcboard.api.property.NodeProperties.KINETIC_RPM,
                        sourceNode.getProperties().get(com.gtceu.calcboard.api.property.NodeProperties.KINETIC_RPM));
            }
            if (sourceNode.getProperties().has(com.gtceu.calcboard.api.property.NodeProperties.BOILER_THROTTLE)) {
                sibling.getProperties().set(com.gtceu.calcboard.api.property.NodeProperties.BOILER_THROTTLE,
                        sourceNode.getProperties().get(com.gtceu.calcboard.api.property.NodeProperties.BOILER_THROTTLE));
            }
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

    public RecipeNode getNode(String id) {
        return findNodeById(id);
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
        frames.clear();
        stickyNotes.clear();
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

    public void addConnection(ConnectionEdge edge) {
        if (edge != null) {
            addConnection(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex());
        }
    }

    public void removeConnection(ConnectionEdge edge) {
        connections.remove(edge);
    }

    public boolean cleanupInvalidConnections() {
        return connections.removeIf(edge -> {
            RecipeNode from = findNodeById(edge.fromNodeId());
            RecipeNode to = findNodeById(edge.toNodeId());
            if (from == null || to == null) return true;
            if (edge.outputIndex() < 0 || edge.outputIndex() >= from.getOutputs().size()) return true;
            if (edge.inputIndex() < 0 || edge.inputIndex() >= to.getInputs().size()) return true;

            IngredientStack outStack = from.getOutputs().get(edge.outputIndex());
            IngredientStack inStack = to.getInputs().get(edge.inputIndex());
            if (outStack == null || inStack == null) return true;
            if (outStack.getId() == null || inStack.getId() == null) return true;

            // Fluid vs Item compatibility check
            if (outStack.isFluid() != inStack.isFluid()) return true;

            // Resource ID compatibility check
            if (outStack.getId().equals(inStack.getId())) return false;

            return true;
        });
    }

    public void copyFrom(FlowGraph other) {
        this.clear();
        if (other != null) {
            for (RecipeNode n : other.nodes) {
                this.addNode(n);
            }
            this.connections.addAll(other.connections);
            for (CanvasGroupFrame f : other.frames) {
                this.frames.add(f.copy());
            }
            for (CanvasStickyNote sn : other.stickyNotes) {
                this.stickyNotes.add(sn.copy());
            }
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

    public void autoRatioHarmonized(RecipeNode anchor) {
        FlowGraphSolver.autoRatioHarmonized(this, anchor);
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
        return FlowGraphModuleHandler.groupIntoModule(this, targetNodeIds, moduleName, null);
    }

    public RecipeNode groupIntoModule(Set<String> targetNodeIds, String moduleName, CanvasGroupFrame primaryFrame) {
        return FlowGraphModuleHandler.groupIntoModule(this, targetNodeIds, moduleName, primaryFrame);
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

        if (!frames.isEmpty()) {
            ListTag frameList = new ListTag();
            for (CanvasGroupFrame f : frames) {
                frameList.add(f.serializeNBT());
            }
            tag.put("frames", frameList);
        }

        if (!stickyNotes.isEmpty()) {
            ListTag noteList = new ListTag();
            for (CanvasStickyNote sn : stickyNotes) {
                noteList.add(sn.serializeNBT());
            }
            tag.put("stickyNotes", noteList);
        }

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
        if (tag.contains("frames", Tag.TAG_LIST)) {
            ListTag frameList = tag.getList("frames", Tag.TAG_COMPOUND);
            for (int i = 0; i < frameList.size(); i++) {
                graph.frames.add(CanvasGroupFrame.deserializeNBT(frameList.getCompound(i)));
            }
        }
        if (tag.contains("stickyNotes", Tag.TAG_LIST)) {
            ListTag noteList = tag.getList("stickyNotes", Tag.TAG_COMPOUND);
            for (int i = 0; i < noteList.size(); i++) {
                graph.stickyNotes.add(CanvasStickyNote.deserializeNBT(noteList.getCompound(i)));
            }
        }
        return graph;
    }

    public FlowGraph copy() {
        CompoundTag tag = serializeNBT(0, 0, 1.0);
        return FlowGraph.deserializeNBT(tag);
    }

    /**
     * Replaces the active recipe parameters and ports of a node with a new recipe template,
     * intelligently preserving existing wire connections for matching ingredients (by ID and fluid type).
     */
    public com.gtceu.calcboard.api.history.BoardCommand.SwitchRecipeCommand switchNodeRecipe(RecipeNode targetNode, RecipeNode newRecipeTemplate) {
        if (targetNode == null || newRecipeTemplate == null) return null;

        String nodeId = targetNode.getId();
        var oldSnapshot = com.gtceu.calcboard.api.history.BoardCommand.SwitchRecipeCommand.RecipeSnapshot.of(targetNode);
        var newSnapshot = com.gtceu.calcboard.api.history.BoardCommand.SwitchRecipeCommand.RecipeSnapshot.of(newRecipeTemplate);

        List<ConnectionEdge> oldEdges = new ArrayList<>();
        for (ConnectionEdge e : connections) {
            if (e.fromNodeId().equals(nodeId) || e.toNodeId().equals(nodeId)) {
                oldEdges.add(e);
            }
        }

        newSnapshot.applyTo(targetNode);

        List<ConnectionEdge> newEdges = new ArrayList<>();
        List<IngredientStack> oldInputs = oldSnapshot.inputs();
        List<IngredientStack> newInputs = targetNode.getInputs();
        List<IngredientStack> oldOutputs = oldSnapshot.outputs();
        List<IngredientStack> newOutputs = targetNode.getOutputs();

        for (ConnectionEdge edge : oldEdges) {
            if (edge.toNodeId().equals(nodeId)) {
                int oldInIdx = edge.inputIndex();
                if (oldInIdx >= 0 && oldInIdx < oldInputs.size()) {
                    IngredientStack oldStack = oldInputs.get(oldInIdx);
                    int newInIdx = findMatchingPortIndex(newInputs, oldStack);
                    if (newInIdx >= 0) {
                        newEdges.add(new ConnectionEdge(edge.fromNodeId(), edge.outputIndex(), nodeId, newInIdx));
                    }
                }
            } else if (edge.fromNodeId().equals(nodeId)) {
                int oldOutIdx = edge.outputIndex();
                if (oldOutIdx >= 0 && oldOutIdx < oldOutputs.size()) {
                    IngredientStack oldStack = oldOutputs.get(oldOutIdx);
                    int newOutIdx = findMatchingPortIndex(newOutputs, oldStack);
                    if (newOutIdx >= 0) {
                        newEdges.add(new ConnectionEdge(nodeId, newOutIdx, edge.toNodeId(), edge.inputIndex()));
                    }
                }
            }
        }

        connections.removeIf(e -> e.fromNodeId().equals(nodeId) || e.toNodeId().equals(nodeId));
        for (ConnectionEdge e : newEdges) {
            if (!connections.contains(e)) {
                connections.add(e);
            }
        }

        return new com.gtceu.calcboard.api.history.BoardCommand.SwitchRecipeCommand(nodeId, oldSnapshot, newSnapshot, oldEdges, newEdges);
    }

    private static int findMatchingPortIndex(List<IngredientStack> ports, IngredientStack target) {
        if (ports == null || target == null || target.getId() == null) return -1;
        for (int i = 0; i < ports.size(); i++) {
            IngredientStack p = ports.get(i);
            if (p != null && p.getId() != null && p.getId().equals(target.getId()) && p.isFluid() == target.isFluid()) {
                return i;
            }
        }
        return -1;
    }
}


