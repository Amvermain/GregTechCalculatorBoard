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
 * Wire connection creation (with optional machine count delta for Shift-drag balance).
 */
public class ConnectWireCommand implements BoardCommand {
    private final FlowGraph.ConnectionEdge edge;
    private final String scaledNodeId;
    private final Double oldMachineCount;
    private final Double newMachineCount;

    public ConnectWireCommand(FlowGraph.ConnectionEdge edge, String scaledNodeId, Double oldMachineCount, Double newMachineCount) {
        this.edge = edge;
        this.scaledNodeId = scaledNodeId;
        this.oldMachineCount = oldMachineCount;
        this.newMachineCount = newMachineCount;
    }

    public ConnectWireCommand(FlowGraph.ConnectionEdge edge) {
        this(edge, null, null, null);
    }

    @Override
    public void undo(FlowGraph graph) {
        graph.removeConnection(edge);
        if (scaledNodeId != null && oldMachineCount != null) {
            RecipeNode node = graph.findNodeById(scaledNodeId);
            if (node != null) {
                node.setMachineCount(oldMachineCount);
            }
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        graph.addConnection(edge);
        if (scaledNodeId != null && newMachineCount != null) {
            RecipeNode node = graph.findNodeById(scaledNodeId);
            if (node != null) {
                node.setMachineCount(newMachineCount);
            }
        }
    }

    @Override
    public String getDescription() {
        return "Connect wire";
    }
}
