package com.gtceu.calcboard;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.dialog.AutoConnectFilterDialog;
import com.gtceu.calcboard.client.gui.widget.ToolbarWidget;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class AutoConnectFilterTest {

    @Test
    public void testScanCandidatesGroupsByResourceAndCountsWires() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation ironId = ResourceLocation.tryParse("minecraft:iron_ingot");
        ResourceLocation waterId = ResourceLocation.tryParse("minecraft:water");

        // Producer 1: produces Iron Ingot and Water
        RecipeNode p1 = new RecipeNode("p1", "Producer 1", 100, 100, GTVoltageTier.LV);
        p1.getOutputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        p1.getOutputs().add(IngredientStack.fluid(waterId, "Water", 1000.0));
        graph.addNode(p1);

        // Producer 2: also produces Water
        RecipeNode p2 = new RecipeNode("p2", "Producer 2", 100, 100, GTVoltageTier.LV);
        p2.getOutputs().add(IngredientStack.fluid(waterId, "Water", 500.0));
        graph.addNode(p2);

        // Consumer 1: consumes Iron Ingot
        RecipeNode c1 = new RecipeNode("c1", "Consumer 1", 100, 100, GTVoltageTier.LV);
        c1.getInputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(c1);

        // Consumer 2: consumes Water
        RecipeNode c2 = new RecipeNode("c2", "Consumer 2", 100, 100, GTVoltageTier.LV);
        c2.getInputs().add(IngredientStack.fluid(waterId, "Water", 1000.0));
        graph.addNode(c2);

        List<AutoConnectFilterDialog.ResourceEntry> candidates = AutoConnectFilterDialog.scanCandidates(graph);

        Assertions.assertEquals(2, candidates.size(), "Should find 2 unique resource types");

        // Water has 2 potential wires (p1->c2, p2->c2), Iron has 1 potential wire (p1->c1)
        AutoConnectFilterDialog.ResourceEntry waterEntry = candidates.stream()
                .filter(e -> e.getStack().getId().equals(waterId))
                .findFirst().orElse(null);
        AutoConnectFilterDialog.ResourceEntry ironEntry = candidates.stream()
                .filter(e -> e.getStack().getId().equals(ironId))
                .findFirst().orElse(null);

        Assertions.assertNotNull(waterEntry);
        Assertions.assertNotNull(ironEntry);
        Assertions.assertEquals(2, waterEntry.getPotentialWireCount(), "Water should have 2 potential wire connections");
        Assertions.assertEquals(1, ironEntry.getPotentialWireCount(), "Iron should have 1 potential wire connection");
    }

    @Test
    public void testAutoConnectFiltersExcludedResources() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation ironId = ResourceLocation.tryParse("minecraft:iron_ingot");
        ResourceLocation waterId = ResourceLocation.tryParse("minecraft:water");

        RecipeNode p1 = new RecipeNode("p1", "Producer", 100, 100, GTVoltageTier.LV);
        p1.getOutputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        p1.getOutputs().add(IngredientStack.fluid(waterId, "Water", 1000.0));
        graph.addNode(p1);

        RecipeNode c1 = new RecipeNode("c1", "Iron Consumer", 100, 100, GTVoltageTier.LV);
        c1.getInputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(c1);

        RecipeNode c2 = new RecipeNode("c2", "Water Consumer", 100, 100, GTVoltageTier.LV);
        c2.getInputs().add(IngredientStack.fluid(waterId, "Water", 1000.0));
        graph.addNode(c2);

        // 1. Run autoConnect with ONLY Iron Ingot allowed (Water excluded)
        List<BoardCommand> subCommands = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> added = ToolbarWidget.autoConnect(graph, subCommands, Set.of(ironId));

        Assertions.assertEquals(1, added.size(), "Only 1 wire should be connected");
        Assertions.assertEquals("p1", added.get(0).fromNodeId());
        Assertions.assertEquals(0, added.get(0).outputIndex());
        Assertions.assertEquals("c1", added.get(0).toNodeId());
        Assertions.assertEquals(0, added.get(0).inputIndex());

        // 2. Undo the operation
        BoardCommand.AddNodesCommand cmd = new BoardCommand.AddNodesCommand(Collections.emptyList(), added, "Auto Connect");
        cmd.undo(graph);
        Assertions.assertEquals(0, graph.getConnections().size(), "Undo must remove added edges");

        // 3. Run autoConnect with ALL resources allowed (null filter)
        List<FlowGraph.ConnectionEdge> addedAll = ToolbarWidget.autoConnect(graph, subCommands, null);
        Assertions.assertEquals(2, addedAll.size(), "All 2 wires should be connected when filter is null");
        Assertions.assertEquals(2, graph.getConnections().size());
    }
}
