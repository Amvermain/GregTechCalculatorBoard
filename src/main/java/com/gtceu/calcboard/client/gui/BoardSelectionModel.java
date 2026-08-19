package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.NodeClipboard;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages node selection state, bounding box marquee selection,
 * and bulk operations (delete, copy, cut, paste, duplicate) for BoardScreen.
 */
public class BoardSelectionModel {
    private final Set<String> selectedNodeIds = new HashSet<>();

    public Set<String> getSelectedNodeIds() {
        return selectedNodeIds;
    }

    public boolean isSelected(String id) {
        return id != null && selectedNodeIds.contains(id);
    }

    public boolean isEmpty() {
        return selectedNodeIds.isEmpty();
    }

    public int size() {
        return selectedNodeIds.size();
    }

    public void select(String id, boolean multi) {
        if (!multi) {
            selectedNodeIds.clear();
        }
        if (id != null) {
            selectedNodeIds.add(id);
        }
    }

    public void toggle(String id) {
        if (id == null) return;
        if (selectedNodeIds.contains(id)) {
            selectedNodeIds.remove(id);
        } else {
            selectedNodeIds.add(id);
        }
    }

    public void clear() {
        selectedNodeIds.clear();
    }

    public void selectAll(BoardScreen screen) {
        selectedNodeIds.clear();
        FlowGraph graph = screen.getGraph();
        if (graph != null) {
            for (RecipeNode n : graph.getNodes()) {
                selectedNodeIds.add(n.getId());
            }
        }
        TutorialManager.getInstance().onSelectAll();
    }

    public void deleteSelection(BoardScreen screen) {
        if (selectedNodeIds.isEmpty()) return;
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        int count = selectedNodeIds.size();
        List<RecipeNode> removedNodes = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> removedEdges = new ArrayList<>();

        for (RecipeNode n : graph.getNodes()) {
            if (selectedNodeIds.contains(n.getId())) {
                removedNodes.add(n);
            }
        }
        for (FlowGraph.ConnectionEdge e : graph.getConnections()) {
            if (selectedNodeIds.contains(e.fromNodeId()) || selectedNodeIds.contains(e.toNodeId())) {
                removedEdges.add(e);
            }
        }

        graph.getNodes().removeAll(removedNodes);
        graph.getConnections().removeAll(removedEdges);

        screen.recordCommand(new BoardCommand.RemoveNodesCommand(removedNodes, removedEdges, "Delete " + count + " components"));
        selectedNodeIds.clear();
        screen.rebuildWidgets();
        screen.markSummaryDirty();

        for (RecipeNode n : removedNodes) {
            TutorialManager.getInstance().onNodeRemoved(n);
        }

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§c🗑 ").append(Component.translatable("message.gtcalcboard.deleted_components", String.valueOf(count))), true);
        }
    }

    public void copySelection(BoardScreen screen) {
        if (selectedNodeIds.isEmpty()) {
            screen.getToolbarWidget().copyBlueprintToClipboard();
            return;
        }
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        NodeClipboard.getInstance().copy(graph, selectedNodeIds);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.copied_components", String.valueOf(selectedNodeIds.size()))), true);
        }
    }

    public void pasteSelection(BoardScreen screen, double canvasX, double canvasY) {
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        if (!NodeClipboard.getInstance().hasContent()) {
            screen.getToolbarWidget().importBlueprintFromClipboard();
            return;
        }

        List<RecipeNode> newNodes = NodeClipboard.getInstance().paste(graph, canvasX, canvasY);
        Set<String> newIds = new HashSet<>();
        for (RecipeNode n : newNodes) {
            newIds.add(n.getId());
        }

        List<FlowGraph.ConnectionEdge> newEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge e : graph.getConnections()) {
            if (newIds.contains(e.fromNodeId()) && newIds.contains(e.toNodeId())) {
                newEdges.add(e);
            }
        }

        screen.recordCommand(new BoardCommand.AddNodesCommand(newNodes, newEdges, "Paste " + newNodes.size() + " components"));
        selectedNodeIds.clear();
        for (RecipeNode n : newNodes) {
            selectedNodeIds.add(n.getId());
        }

        screen.rebuildWidgets();
        screen.markSummaryDirty();
        TutorialManager.getInstance().onPasted();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.pasted_components", String.valueOf(newNodes.size()))), true);
        }
    }

    public void cutSelection(BoardScreen screen) {
        if (selectedNodeIds.isEmpty()) return;
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        int count = selectedNodeIds.size();
        NodeClipboard.getInstance().copy(graph, selectedNodeIds);

        List<RecipeNode> removedNodes = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> removedEdges = new ArrayList<>();

        for (RecipeNode n : graph.getNodes()) {
            if (selectedNodeIds.contains(n.getId())) {
                removedNodes.add(n);
            }
        }
        for (FlowGraph.ConnectionEdge e : graph.getConnections()) {
            if (selectedNodeIds.contains(e.fromNodeId()) || selectedNodeIds.contains(e.toNodeId())) {
                removedEdges.add(e);
            }
        }

        graph.getNodes().removeAll(removedNodes);
        graph.getConnections().removeAll(removedEdges);

        screen.recordCommand(new BoardCommand.RemoveNodesCommand(removedNodes, removedEdges, "Cut " + count + " components"));
        selectedNodeIds.clear();
        screen.rebuildWidgets();
        screen.markSummaryDirty();
        TutorialManager.getInstance().onCut();

        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§6✂ ").append(Component.translatable("message.gtcalcboard.cut_components", String.valueOf(count))), true);
        }
    }

    public void duplicateSelection(BoardScreen screen, double mouseX, double mouseY) {
        if (selectedNodeIds.isEmpty()) return;
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        NodeClipboard.getInstance().copy(graph, selectedNodeIds);
        double canvasX = screen.toCanvasX(mouseX) + 30.0;
        double canvasY = screen.toCanvasY(mouseY) + 30.0;
        pasteSelection(screen, canvasX, canvasY);
    }
}
