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
 * Vector translation command for moving one or more nodes by (dx, dy).
 */
public class MoveNodesCommand implements BoardCommand {
    private final Map<String, double[]> deltas; // nodeId -> [dx, dy]

    public MoveNodesCommand(Map<String, double[]> deltas) {
        this.deltas = new HashMap<>(deltas);
    }

    @Override
    public void undo(FlowGraph graph) {
        for (Map.Entry<String, double[]> entry : deltas.entrySet()) {
            RecipeNode node = graph.findNodeById(entry.getKey());
            if (node != null) {
                node.setPosX(node.getPosX() - entry.getValue()[0]);
                node.setPosY(node.getPosY() - entry.getValue()[1]);
            }
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        for (Map.Entry<String, double[]> entry : deltas.entrySet()) {
            RecipeNode node = graph.findNodeById(entry.getKey());
            if (node != null) {
                node.setPosX(node.getPosX() + entry.getValue()[0]);
                node.setPosY(node.getPosY() + entry.getValue()[1]);
            }
        }
    }

    @Override
    public String getDescription() {
        return "Move " + deltas.size() + " components";
    }
}
