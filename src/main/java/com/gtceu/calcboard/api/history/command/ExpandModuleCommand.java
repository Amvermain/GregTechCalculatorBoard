package com.gtceu.calcboard.api.history.command;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Expansion of a compound module back into its constituent machines.
 */
public class ExpandModuleCommand implements BoardCommand {
    private final RecipeNode moduleNode;
    private final List<RecipeNode> expandedNodes;
    private final List<FlowGraph.ConnectionEdge> restoredEdges;
    private final List<FlowGraph.ConnectionEdge> moduleEdges;
    private final List<CanvasGroupFrame> expandedFrames;
    private final List<CanvasStickyNote> expandedNotes;

    public ExpandModuleCommand(
            RecipeNode moduleNode,
            List<RecipeNode> expandedNodes,
            List<FlowGraph.ConnectionEdge> restoredEdges,
            List<FlowGraph.ConnectionEdge> moduleEdges,
            List<CanvasGroupFrame> expandedFrames,
            List<CanvasStickyNote> expandedNotes
    ) {
        this.moduleNode = moduleNode;
        this.expandedNodes = new ArrayList<>(expandedNodes);
        this.restoredEdges = new ArrayList<>(restoredEdges);
        this.moduleEdges = new ArrayList<>(moduleEdges);
        this.expandedFrames = expandedFrames != null ? new ArrayList<>(expandedFrames) : Collections.emptyList();
        this.expandedNotes = expandedNotes != null ? new ArrayList<>(expandedNotes) : Collections.emptyList();
    }

    public ExpandModuleCommand(RecipeNode moduleNode, List<RecipeNode> expandedNodes, List<FlowGraph.ConnectionEdge> restoredEdges, List<FlowGraph.ConnectionEdge> moduleEdges) {
        this(moduleNode, expandedNodes, restoredEdges, moduleEdges,
                moduleNode != null && moduleNode.getSubGraph() != null ? moduleNode.getSubGraph().getFrames() : Collections.emptyList(),
                moduleNode != null && moduleNode.getSubGraph() != null ? moduleNode.getSubGraph().getStickyNotes() : Collections.emptyList());
    }

    @Override
    public void undo(FlowGraph graph) {
        for (RecipeNode n : expandedNodes) {
            graph.removeNode(n);
        }
        for (FlowGraph.ConnectionEdge e : restoredEdges) {
            graph.removeConnection(e);
        }
        for (CanvasGroupFrame f : expandedFrames) {
            graph.removeFrame(f);
        }
        for (CanvasStickyNote note : expandedNotes) {
            graph.removeStickyNote(note);
        }
        if (!graph.getNodes().contains(moduleNode)) {
            graph.addNode(moduleNode);
        }
        for (FlowGraph.ConnectionEdge e : moduleEdges) {
            graph.addConnection(e);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        graph.removeNode(moduleNode);
        for (FlowGraph.ConnectionEdge e : moduleEdges) {
            graph.removeConnection(e);
        }
        for (RecipeNode n : expandedNodes) {
            if (!graph.getNodes().contains(n)) {
                graph.addNode(n);
            }
        }
        for (FlowGraph.ConnectionEdge e : restoredEdges) {
            graph.addConnection(e);
        }
        for (CanvasGroupFrame f : expandedFrames) {
            if (!graph.getFrames().contains(f)) {
                graph.addFrame(f);
            }
        }
        for (CanvasStickyNote note : expandedNotes) {
            if (!graph.getStickyNotes().contains(note)) {
                graph.addStickyNote(note);
            }
        }
    }

    @Override
    public String getDescription() {
        return "Expand Module into " + expandedNodes.size() + " components";
    }
}
