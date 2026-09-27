package com.gtceu.calcboard.api.history.command;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.PoolViewMode;
import com.gtceu.calcboard.api.model.RecipeNode;

public class AddRecipeToSharedFrameCommand implements BoardCommand {
    private final String frameId;
    private final RecipeNode node;
    private final double assignedMachineCount;
    private final String description;

    public AddRecipeToSharedFrameCommand(String frameId, RecipeNode node, double assignedMachineCount, String description) {
        this.frameId = frameId;
        this.node = node;
        this.assignedMachineCount = assignedMachineCount;
        this.description = description;
    }

    @Override
    public void undo(FlowGraph graph) {
        if (graph == null) return;
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.removeRecipe(node.getId(), graph);
        } else {
            graph.removeNode(node);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        if (graph == null) return;
        if (!graph.getNodes().contains(node)) {
            graph.addNode(node);
        }
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.addRecipeInline(node, graph);
            node.setMachineCount(assignedMachineCount);
            if (frame.getViewMode() == PoolViewMode.EMBEDDED_PANEL) {
                frame.relayoutEmbeddedCards(graph);
            }
        }
    }

    @Override
    public String getDescription() {
        return description != null ? description : "Add Recipe to Shared Frame";
    }
}
