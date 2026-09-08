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
 * Removal (Delete / Cut) of one or more recipe nodes and their associated wires.
 */
public class RemoveNodesCommand implements BoardCommand {
    private final List<RecipeNode> nodes;
    private final List<FlowGraph.ConnectionEdge> edges;
    private final String description;

    public RemoveNodesCommand(List<RecipeNode> nodes, List<FlowGraph.ConnectionEdge> edges, String description) {
        this.nodes = new ArrayList<>(nodes);
        this.edges = new ArrayList<>(edges);
        this.description = description;
    }

    @Override
    public void undo(FlowGraph graph) {
        for (RecipeNode n : nodes) {
            if (!graph.getNodes().contains(n)) {
                graph.addNode(n);
            }
        }
        for (FlowGraph.ConnectionEdge e : edges) {
            graph.addConnection(e);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        for (FlowGraph.ConnectionEdge e : edges) {
            graph.removeConnection(e);
        }
        for (RecipeNode n : nodes) {
            graph.removeNode(n);
        }
    }

    @Override
    public String getDescription() {
        return description != null ? description : "Remove " + nodes.size() + " components";
    }
}
