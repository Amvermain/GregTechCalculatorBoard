package com.gtceu.calcboard.api.storage;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.*;

public class NodeClipboard {
    private static final NodeClipboard INSTANCE = new NodeClipboard();

    public static NodeClipboard getInstance() {
        return INSTANCE;
    }

    public record PasteResult(
            List<RecipeNode> nodes,
            List<CanvasStickyNote> notes,
            List<CanvasGroupFrame> frames,
            List<FlowGraph.ConnectionEdge> edges
    ) {
        public boolean isEmpty() {
            return nodes.isEmpty() && notes.isEmpty() && frames.isEmpty();
        }

        public int size() {
            return nodes.size() + notes.size() + frames.size();
        }
    }

    private CompoundTag clipboardData = null;

    private NodeClipboard() {}

    public boolean hasContent() {
        if (clipboardData == null) return false;
        return (clipboardData.contains("nodes", Tag.TAG_LIST) && !clipboardData.getList("nodes", Tag.TAG_COMPOUND).isEmpty())
            || (clipboardData.contains("notes", Tag.TAG_LIST) && !clipboardData.getList("notes", Tag.TAG_COMPOUND).isEmpty())
            || (clipboardData.contains("frames", Tag.TAG_LIST) && !clipboardData.getList("frames", Tag.TAG_COMPOUND).isEmpty());
    }

    public void copy(FlowGraph graph, Set<String> selectedNodeIds) {
        copy(graph, selectedNodeIds, Collections.emptySet(), Collections.emptySet());
    }

    public void copy(FlowGraph graph, Set<String> selectedNodeIds, Set<String> selectedNoteIds, Set<String> selectedFrameIds) {
        if (graph == null) {
            clipboardData = null;
            return;
        }

        List<RecipeNode> selectedNodes = new ArrayList<>();
        Set<String> effectiveNodeIds = new HashSet<>();
        if (selectedNodeIds != null) {
            effectiveNodeIds.addAll(selectedNodeIds);
            for (RecipeNode n : graph.getNodes()) {
                if (selectedNodeIds.contains(n.getId()) && n.isCompoundNode()) {
                    List<RecipeNode> siblings = graph.findCompoundSiblingNodes(n.getCompoundGroupId());
                    for (RecipeNode sib : siblings) {
                        effectiveNodeIds.add(sib.getId());
                    }
                }
            }
        }

        for (RecipeNode n : graph.getNodes()) {
            if (effectiveNodeIds.contains(n.getId())) {
                selectedNodes.add(n);
            }
        }

        List<CanvasStickyNote> selectedNotes = new ArrayList<>();
        if (selectedNoteIds != null) {
            for (CanvasStickyNote note : graph.getStickyNotes()) {
                if (selectedNoteIds.contains(note.getId())) {
                    selectedNotes.add(note);
                }
            }
        }

        List<CanvasGroupFrame> selectedFrames = new ArrayList<>();
        Set<String> effectiveFrameIds = new HashSet<>();
        if (selectedFrameIds != null) {
            effectiveFrameIds.addAll(selectedFrameIds);
        }
        for (RecipeNode n : selectedNodes) {
            if (n.isCompoundNode()) {
                CanvasGroupFrame cFrame = graph.findCompoundFrame(n.getCompoundGroupId());
                if (cFrame != null) {
                    effectiveFrameIds.add(cFrame.getId());
                }
            }
        }
        for (CanvasGroupFrame frame : graph.getFrames()) {
            if (effectiveFrameIds.contains(frame.getId())) {
                selectedFrames.add(frame);
            }
        }

        if (selectedNodes.isEmpty() && selectedNotes.isEmpty() && selectedFrames.isEmpty()) {
            clipboardData = null;
            return;
        }

        // Calculate bounding box centroid across all selected elements
        double sumX = 0, sumY = 0;
        int count = 0;
        for (RecipeNode n : selectedNodes) {
            sumX += n.getPosX();
            sumY += n.getPosY();
            count++;
        }
        for (CanvasStickyNote note : selectedNotes) {
            sumX += note.getPosX();
            sumY += note.getPosY();
            count++;
        }
        for (CanvasGroupFrame frame : selectedFrames) {
            sumX += frame.getPosX();
            sumY += frame.getPosY();
            count++;
        }

        double centerX = count > 0 ? sumX / count : 0.0;
        double centerY = count > 0 ? sumY / count : 0.0;

        CompoundTag tag = new CompoundTag();
        tag.putDouble("centerX", centerX);
        tag.putDouble("centerY", centerY);

        ListTag nodeList = new ListTag();
        for (RecipeNode n : selectedNodes) {
            nodeList.add(n.serializeNBT());
        }
        tag.put("nodes", nodeList);

        ListTag noteList = new ListTag();
        for (CanvasStickyNote note : selectedNotes) {
            noteList.add(note.serializeNBT());
        }
        tag.put("notes", noteList);

        ListTag frameList = new ListTag();
        for (CanvasGroupFrame frame : selectedFrames) {
            frameList.add(frame.serializeNBT());
        }
        tag.put("frames", frameList);

        ListTag edgeList = new ListTag();
        if (!effectiveNodeIds.isEmpty()) {
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (effectiveNodeIds.contains(edge.fromNodeId()) && effectiveNodeIds.contains(edge.toNodeId())) {
                    edgeList.add(edge.serializeNBT());
                }
            }
        }
        tag.put("connections", edgeList);

        this.clipboardData = tag;
    }

    public PasteResult paste(FlowGraph graph, double targetCanvasX, double targetCanvasY) {
        if (graph == null || !hasContent()) {
            return new PasteResult(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }

        double origCenterX = clipboardData.getDouble("centerX");
        double origCenterY = clipboardData.getDouble("centerY");
        double offsetX = targetCanvasX - origCenterX;
        double offsetY = targetCanvasY - origCenterY;

        ListTag nodeList = clipboardData.getList("nodes", Tag.TAG_COMPOUND);
        ListTag noteList = clipboardData.getList("notes", Tag.TAG_COMPOUND);
        ListTag frameList = clipboardData.getList("frames", Tag.TAG_COMPOUND);
        ListTag edgeList = clipboardData.getList("connections", Tag.TAG_COMPOUND);

        Map<String, RecipeNode> idMap = new HashMap<>();
        List<RecipeNode> newNodes = new ArrayList<>();
        List<CanvasStickyNote> newNotes = new ArrayList<>();
        List<CanvasGroupFrame> newFrames = new ArrayList<>();

        Map<String, String> compoundGroupRemap = new HashMap<>();
        Map<String, String> compoundMasterRemap = new HashMap<>();

        for (int i = 0; i < nodeList.size(); i++) {
            RecipeNode origNode = RecipeNode.deserializeNBT(nodeList.getCompound(i));
            String oldId = origNode.getId();

            RecipeNode newNode = RecipeNode.deserializeNBT(origNode.serializeNBT());
            String newId = UUID.randomUUID().toString();
            newNode.setId(newId);
            newNode.setPos(origNode.getPosX() + offsetX, origNode.getPosY() + offsetY);

            if (origNode.isCompoundNode()) {
                String oldGroupId = origNode.getCompoundGroupId();
                String newGroupId = compoundGroupRemap.computeIfAbsent(oldGroupId, k -> UUID.randomUUID().toString());
                if (origNode.isCompoundMaster()) {
                    compoundMasterRemap.put(newGroupId, newId);
                }
            }

            idMap.put(oldId, newNode);
            newNodes.add(newNode);
        }

        for (RecipeNode newNode : newNodes) {
            if (newNode.isCompoundNode()) {
                String oldGroupId = newNode.getCompoundGroupId();
                String newGroupId = compoundGroupRemap.get(oldGroupId);
                String newMasterId = compoundMasterRemap.getOrDefault(newGroupId, newNode.getId());
                newNode.setCompoundMetadata(newGroupId, newNode.getCompoundLayerIndex(), newNode.getCompoundTotalLayers(), newMasterId);
            }
            graph.addNode(newNode);
        }

        for (int i = 0; i < noteList.size(); i++) {
            CanvasStickyNote origNote = CanvasStickyNote.deserializeNBT(noteList.getCompound(i));
            CanvasStickyNote newNote = new CanvasStickyNote(
                UUID.randomUUID().toString(),
                origNote.getTitle(),
                origNote.getContent(),
                origNote.getColor(),
                origNote.getPosX() + offsetX,
                origNote.getPosY() + offsetY,
                origNote.getWidth(),
                origNote.getHeight()
            );
            newNotes.add(newNote);
            graph.addStickyNote(newNote);
        }

        for (int i = 0; i < frameList.size(); i++) {
            CanvasGroupFrame origFrame = CanvasGroupFrame.deserializeNBT(frameList.getCompound(i));
            CanvasGroupFrame newFrame = new CanvasGroupFrame(
                UUID.randomUUID().toString(),
                origFrame.getTitle(),
                origFrame.getColor(),
                origFrame.getPosX() + offsetX,
                origFrame.getPosY() + offsetY,
                origFrame.getWidth(),
                origFrame.getHeight()
            );
            newFrame.setNote(origFrame.getNote());
            if (origFrame.isCompoundFrame()) {
                newFrame.setCompoundFrame(true);
                String newGroupId = compoundGroupRemap.getOrDefault(origFrame.getCompoundGroupId(), origFrame.getCompoundGroupId());
                newFrame.setCompoundGroupId(newGroupId);
            }
            for (String oldNid : origFrame.getContainedNodeIds()) {
                RecipeNode mapped = idMap.get(oldNid);
                if (mapped != null) {
                    newFrame.addNode(mapped.getId());
                }
            }
            newFrames.add(newFrame);
            graph.addFrame(newFrame);
        }

        List<FlowGraph.ConnectionEdge> newEdges = new ArrayList<>();
        for (int i = 0; i < edgeList.size(); i++) {
            FlowGraph.ConnectionEdge origEdge = FlowGraph.ConnectionEdge.deserializeNBT(edgeList.getCompound(i));
            RecipeNode newFrom = idMap.get(origEdge.fromNodeId());
            RecipeNode newTo = idMap.get(origEdge.toNodeId());
            if (newFrom != null && newTo != null) {
                graph.addConnection(newFrom.getId(), origEdge.outputIndex(), newTo.getId(), origEdge.inputIndex());
                newEdges.add(new FlowGraph.ConnectionEdge(newFrom.getId(), origEdge.outputIndex(), newTo.getId(), origEdge.inputIndex()));
            }
        }

        return new PasteResult(newNodes, newNotes, newFrames, newEdges);
    }
}


