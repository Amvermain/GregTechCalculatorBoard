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
 * Wire severance / disconnection.
 */
public class DisconnectWireCommand implements BoardCommand {
    private final FlowGraph.ConnectionEdge edge;

    public DisconnectWireCommand(FlowGraph.ConnectionEdge edge) {
        this.edge = edge;
    }

    @Override
    public void undo(FlowGraph graph) {
        graph.addConnection(edge);
    }

    @Override
    public void redo(FlowGraph graph) {
        graph.removeConnection(edge);
    }

    @Override
    public String getDescription() {
        return "Disconnect wire";
    }
}
