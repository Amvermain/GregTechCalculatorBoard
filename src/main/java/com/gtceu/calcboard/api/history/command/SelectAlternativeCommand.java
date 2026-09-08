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
 * Toggling or selecting an alternative ingredient on an input/output slot.
 */
public class SelectAlternativeCommand implements BoardCommand {
    private final String nodeId;
    private final int slotIndex;
    private final boolean isInput;
    private final ResourceLocation oldAlternativeId;
    private final ResourceLocation newAlternativeId;

    public SelectAlternativeCommand(String nodeId, int slotIndex, boolean isInput, ResourceLocation oldAlternativeId, ResourceLocation newAlternativeId) {
        this.nodeId = nodeId;
        this.slotIndex = slotIndex;
        this.isInput = isInput;
        this.oldAlternativeId = oldAlternativeId;
        this.newAlternativeId = newAlternativeId;
    }

    @Override
    public void undo(FlowGraph graph) {
        RecipeNode node = graph.findNodeById(nodeId);
        if (node != null) {
            List<IngredientStack> list = isInput ? node.getInputs() : node.getOutputs();
            if (slotIndex >= 0 && slotIndex < list.size()) {
                list.get(slotIndex).selectAlternative(oldAlternativeId);
            }
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        RecipeNode node = graph.findNodeById(nodeId);
        if (node != null) {
            List<IngredientStack> list = isInput ? node.getInputs() : node.getOutputs();
            if (slotIndex >= 0 && slotIndex < list.size()) {
                list.get(slotIndex).selectAlternative(newAlternativeId);
            }
        }
    }

    @Override
    public String getDescription() {
        return "Select alternative ingredient";
    }
}
