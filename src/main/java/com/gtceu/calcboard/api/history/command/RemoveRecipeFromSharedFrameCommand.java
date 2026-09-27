package com.gtceu.calcboard.api.history.command;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.PoolViewMode;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RemoveRecipeFromSharedFrameCommand implements BoardCommand {
    private final String frameId;
    private final RecipeNode node;
    private final double savedMachineCount;
    private final List<FlowGraph.ConnectionEdge> edges;
    private final String description;

    public RemoveRecipeFromSharedFrameCommand(String frameId, RecipeNode node, List<FlowGraph.ConnectionEdge> edges, String description) {
        this.frameId = frameId;
        this.node = node;
        this.savedMachineCount = node != null ? node.getMachineCount() : 1.0;
        this.edges = edges != null ? new ArrayList<>(edges) : Collections.emptyList();
        this.description = description;
    }

    @Override
    public void undo(FlowGraph graph) {
        if (graph == null) return;
        if (!graph.getNodes().contains(node)) {
            graph.addNode(node);
        }
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.addRecipeInline(node, graph);
            node.setMachineCount(savedMachineCount);
        }
        for (FlowGraph.ConnectionEdge e : edges) {
            graph.addConnection(e);
        }
        if (frame != null && frame.getViewMode() == PoolViewMode.EMBEDDED_PANEL) {
            frame.relayoutEmbeddedCards(graph);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        if (graph == null) return;
        for (FlowGraph.ConnectionEdge e : edges) {
            graph.removeConnection(e);
        }
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.removeRecipe(node.getId(), graph);
        } else {
            graph.removeNode(node);
        }
    }

    @Override
    public String getDescription() {
        return description != null ? description : "Remove Recipe from Shared Frame";
    }
}
