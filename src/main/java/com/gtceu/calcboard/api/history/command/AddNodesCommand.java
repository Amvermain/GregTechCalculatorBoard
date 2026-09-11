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
 * Addition of one or more recipe nodes and their internal connections.
 */
public class AddNodesCommand implements BoardCommand {
    private final List<RecipeNode> nodes;
    private final List<FlowGraph.ConnectionEdge> edges;
    private final String description;

    public AddNodesCommand(List<RecipeNode> nodes, List<FlowGraph.ConnectionEdge> edges, String description) {
        this.nodes = new ArrayList<>(nodes);
        this.edges = edges != null ? new ArrayList<>(edges) : Collections.emptyList();
        this.description = description;
    }

    public AddNodesCommand(RecipeNode node, String description) {
        this(List.of(node), Collections.emptyList(), description);
    }

    @Override
    public void undo(FlowGraph graph) {
        for (FlowGraph.ConnectionEdge e : edges) {
            graph.removeConnection(e);
        }
        for (RecipeNode n : nodes) {
            graph.removeNode(n);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
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
    public String getDescription() {
        return description != null ? description : "Add " + nodes.size() + " components";
    }
}
