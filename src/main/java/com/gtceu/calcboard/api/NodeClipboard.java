package com.gtceu.calcboard.api;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

public class NodeClipboard {
    private static final NodeClipboard INSTANCE = new NodeClipboard();

    public static NodeClipboard getInstance() {
        return INSTANCE;
    }

    private CompoundTag clipboardData = null;

    private NodeClipboard() {}

    public boolean hasContent() {
        return clipboardData != null && clipboardData.contains("nodes", Tag.TAG_LIST);
    }

    public void copy(FlowGraph graph, Set<String> selectedNodeIds) {
        if (graph == null || selectedNodeIds == null || selectedNodeIds.isEmpty()) {
            clipboardData = null;
            return;
        }

        List<RecipeNode> selectedNodes = new ArrayList<>();
        for (RecipeNode n : graph.getNodes()) {
            if (selectedNodeIds.contains(n.getId())) {
                selectedNodes.add(n);
            }
        }

        if (selectedNodes.isEmpty()) {
            clipboardData = null;
            return;
        }

        // Calculate bounding box centroid
        double sumX = 0, sumY = 0;
        for (RecipeNode n : selectedNodes) {
            sumX += n.getPosX();
            sumY += n.getPosY();
        }
        double centerX = sumX / selectedNodes.size();
        double centerY = sumY / selectedNodes.size();

        CompoundTag tag = new CompoundTag();
        tag.putDouble("centerX", centerX);
        tag.putDouble("centerY", centerY);

        ListTag nodeList = new ListTag();
        for (RecipeNode n : selectedNodes) {
            nodeList.add(n.serializeNBT());
        }
        tag.put("nodes", nodeList);

        ListTag edgeList = new ListTag();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (selectedNodeIds.contains(edge.fromNodeId()) && selectedNodeIds.contains(edge.toNodeId())) {
                edgeList.add(edge.serializeNBT());
            }
        }
        tag.put("connections", edgeList);

        this.clipboardData = tag;
    }

    /**
     * Pastes clipboard content at the specified canvas target coordinates.
     * @return List of newly created RecipeNodes
     */
    public List<RecipeNode> paste(FlowGraph graph, double targetCanvasX, double targetCanvasY) {
        if (graph == null || !hasContent()) return Collections.emptyList();

        double origCenterX = clipboardData.getDouble("centerX");
        double origCenterY = clipboardData.getDouble("centerY");
        double offsetX = targetCanvasX - origCenterX;
        double offsetY = targetCanvasY - origCenterY;

        ListTag nodeList = clipboardData.getList("nodes", Tag.TAG_COMPOUND);
        ListTag edgeList = clipboardData.getList("connections", Tag.TAG_COMPOUND);

        Map<String, RecipeNode> idMap = new HashMap<>();
        List<RecipeNode> newNodes = new ArrayList<>();

        for (int i = 0; i < nodeList.size(); i++) {
            RecipeNode origNode = RecipeNode.deserializeNBT(nodeList.getCompound(i));
            String oldId = origNode.getId();

            // Clone with brand new UUID
            RecipeNode newNode = RecipeNode.deserializeNBT(origNode.serializeNBT());
            String newId = UUID.randomUUID().toString();
            newNode.setId(newId);
            newNode.setPos(origNode.getPosX() + offsetX, origNode.getPosY() + offsetY);

            idMap.put(oldId, newNode);
            newNodes.add(newNode);
            graph.addNode(newNode);
        }

        // Reconnect internal wires with newly mapped IDs
        for (int i = 0; i < edgeList.size(); i++) {
            FlowGraph.ConnectionEdge origEdge = FlowGraph.ConnectionEdge.deserializeNBT(edgeList.getCompound(i));
            RecipeNode newFrom = idMap.get(origEdge.fromNodeId());
            RecipeNode newTo = idMap.get(origEdge.toNodeId());
            if (newFrom != null && newTo != null) {
                graph.addConnection(newFrom.getId(), origEdge.outputIndex(), newTo.getId(), origEdge.inputIndex());
            }
        }

        return newNodes;
    }
}
