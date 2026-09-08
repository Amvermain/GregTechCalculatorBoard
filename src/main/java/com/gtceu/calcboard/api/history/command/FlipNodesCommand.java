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
 * Reversible command for horizontally flipping one or more nodes.
 */
public class FlipNodesCommand implements BoardCommand {
    private final Map<String, Boolean> previousStates;
    private final Map<String, Boolean> newStates;

    public FlipNodesCommand(Map<String, Boolean> previousStates, Map<String, Boolean> newStates) {
        this.previousStates = new HashMap<>(previousStates);
        this.newStates = new HashMap<>(newStates);
    }

    public FlipNodesCommand(RecipeNode node, boolean previousState, boolean newState) {
        this(Map.of(node.getId(), previousState), Map.of(node.getId(), newState));
    }

    @Override
    public void undo(FlowGraph graph) {
        for (Map.Entry<String, Boolean> entry : previousStates.entrySet()) {
            RecipeNode node = graph.findNodeById(entry.getKey());
            if (node != null) {
                node.setFlipped(entry.getValue());
            }
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        for (Map.Entry<String, Boolean> entry : newStates.entrySet()) {
            RecipeNode node = graph.findNodeById(entry.getKey());
            if (node != null) {
                node.setFlipped(entry.getValue());
            }
        }
    }

    @Override
    public String getDescription() {
        return "Flip " + newStates.size() + " nodes horizontally";
    }
}
