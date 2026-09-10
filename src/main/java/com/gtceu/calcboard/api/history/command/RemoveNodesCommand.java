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

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;

import java.util.*;

/**
 * Removal (Delete / Cut) of one or more recipe nodes and their associated wires.
 */
public class RemoveNodesCommand implements BoardCommand {
    private final List<RecipeNode> nodes;
    private final List<FlowGraph.ConnectionEdge> edges;
    private final String description;
    private final List<BoardPage> capturedSubPages;

    public RemoveNodesCommand(List<RecipeNode> nodes, List<FlowGraph.ConnectionEdge> edges, String description, List<BoardPage> capturedSubPages) {
        this.nodes = new ArrayList<>(nodes);
        this.edges = new ArrayList<>(edges);
        this.description = description;
        if (capturedSubPages != null) {
            this.capturedSubPages = new ArrayList<>(capturedSubPages);
        } else {
            List<BoardPage> list = new ArrayList<>();
            for (RecipeNode n : nodes) {
                if (n.isModule() && n.getSubPageId() != null) {
                    BoardManager.getInstance().getPage(n.getSubPageId()).ifPresent(list::add);
                }
            }
            this.capturedSubPages = list;
        }
    }

    public RemoveNodesCommand(List<RecipeNode> nodes, List<FlowGraph.ConnectionEdge> edges, String description) {
        this(nodes, edges, description, null);
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
        for (BoardPage p : capturedSubPages) {
            if (BoardManager.getInstance().getPage(p.getId()).isEmpty()) {
                BoardManager.getInstance().getPageManager().addPage(p);
            }
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
        for (BoardPage p : capturedSubPages) {
            BoardManager.getInstance().removePage(p);
        }
    }

    @Override
    public String getDescription() {
        return description != null ? description : "Remove " + nodes.size() + " components";
    }
}
