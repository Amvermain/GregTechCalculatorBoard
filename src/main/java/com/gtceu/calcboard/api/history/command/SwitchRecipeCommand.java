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
 * In-place recipe switching for an existing node with smart wire preservation.
 */
public class SwitchRecipeCommand implements BoardCommand {
    public record RecipeSnapshot(
            String name,
            double baseDurationTicks,
            double baseEUt,
            com.gtceu.calcboard.api.type.GTVoltageTier recipeTier,
            ResourceLocation recipeCategoryId,
            List<IngredientStack> inputs,
            List<IngredientStack> outputs
    ) {
        public static RecipeSnapshot of(RecipeNode node) {
            List<IngredientStack> inList = new ArrayList<>();
            for (IngredientStack in : node.getInputs()) {
                inList.add(in.copy());
            }
            List<IngredientStack> outList = new ArrayList<>();
            for (IngredientStack out : node.getOutputs()) {
                outList.add(out.copy());
            }
            return new RecipeSnapshot(
                    node.getRawName(),
                    node.getBaseDurationTicks(),
                    node.getBaseEUt(),
                    node.getRecipeTier(),
                    node.getRecipeCategoryId(),
                    inList,
                    outList
            );
        }

        public void applyTo(RecipeNode node) {
            node.setName(name);
            node.setBaseDurationTicks(baseDurationTicks);
            node.setBaseEUt(baseEUt);
            node.setRecipeTier(recipeTier);
            node.setRecipeCategoryId(recipeCategoryId);
            node.getInputs().clear();
            for (IngredientStack in : inputs) {
                node.getInputs().add(in.copy());
            }
            node.getOutputs().clear();
            for (IngredientStack out : outputs) {
                node.getOutputs().add(out.copy());
            }
        }
    }

    private final String nodeId;
    private final RecipeSnapshot oldRecipe;
    private final RecipeSnapshot newRecipe;
    private final List<FlowGraph.ConnectionEdge> oldEdges;
    private final List<FlowGraph.ConnectionEdge> newEdges;

    public SwitchRecipeCommand(
            String nodeId,
            RecipeSnapshot oldRecipe,
            RecipeSnapshot newRecipe,
            List<FlowGraph.ConnectionEdge> oldEdges,
            List<FlowGraph.ConnectionEdge> newEdges
    ) {
        this.nodeId = nodeId;
        this.oldRecipe = oldRecipe;
        this.newRecipe = newRecipe;
        this.oldEdges = new ArrayList<>(oldEdges);
        this.newEdges = new ArrayList<>(newEdges);
    }

    @Override
    public void undo(FlowGraph graph) {
        RecipeNode node = graph.findNodeById(nodeId);
        if (node != null) {
            oldRecipe.applyTo(node);
        }
        graph.removeConnectionIf(e -> e.fromNodeId().equals(nodeId) || e.toNodeId().equals(nodeId));
        for (FlowGraph.ConnectionEdge e : oldEdges) {
            graph.addConnection(e);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        RecipeNode node = graph.findNodeById(nodeId);
        if (node != null) {
            newRecipe.applyTo(node);
        }
        graph.removeConnectionIf(e -> e.fromNodeId().equals(nodeId) || e.toNodeId().equals(nodeId));
        for (FlowGraph.ConnectionEdge e : newEdges) {
            graph.addConnection(e);
        }
    }

    @Override
    public String getDescription() {
        return "Switch recipe for " + (newRecipe != null ? newRecipe.name() : "node");
    }
}
