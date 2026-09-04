package com.gtceu.calcboard.api.solver;

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

    @Test
    public void testAutoConnectPreservesExistingMultiStagePipelineAndOnlyConnectsUnconnectedJunction() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation diamondId = ResourceLocation.tryParse("minecraft:diamond");
        ResourceLocation steamId = ResourceLocation.tryParse("gtceu:steam");
        ResourceLocation warmSteamId = ResourceLocation.tryParse("systeams:steamier");
        ResourceLocation hotSteamId = ResourceLocation.tryParse("systeams:steamiest");
        ResourceLocation superheatedSteamId = ResourceLocation.tryParse("systeams:steamiester");

        // 1. Diamond Junction (Source Hub)
        RecipeNode diamondJunc = RecipeNode.createReroute(0, 0);
        diamondJunc.bindRerouteIngredient(IngredientStack.item(diamondId, "Diamond", 1.0));
        graph.addNode(diamondJunc);

        // 2. Boiler 1: [Diamond, Water (alts: Steam, Warm, Hot)] -> [Steam (alts: Warm, Hot, Superheated)]
        RecipeNode boiler1 = new RecipeNode("b1", "Boiler 1", 20, 0, GTVoltageTier.LV);
        boiler1.getInputs().add(IngredientStack.item(diamondId, "Diamond", 1.0));
        IngredientStack b1InFluid = IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0);
        b1InFluid.addAlternative(steamId);
        b1InFluid.addAlternative(warmSteamId);
        b1InFluid.addAlternative(hotSteamId);
        boiler1.getInputs().add(b1InFluid);
        IngredientStack b1OutFluid = IngredientStack.fluid(steamId, "Steam", 500.0);
        b1OutFluid.addAlternative(warmSteamId);
        b1OutFluid.addAlternative(hotSteamId);
        b1OutFluid.addAlternative(superheatedSteamId);
        boiler1.getOutputs().add(b1OutFluid);
        graph.addNode(boiler1);

        // 3. Boiler 2: [Diamond, Steam] -> [Warm Steam]
        RecipeNode boiler2 = new RecipeNode("b2", "Boiler 2", 20, 0, GTVoltageTier.LV);
        boiler2.getInputs().add(IngredientStack.item(diamondId, "Diamond", 1.0));
        IngredientStack b2InFluid = IngredientStack.fluid(steamId, "Steam", 500.0);
        b2InFluid.addAlternative(warmSteamId);
        b2InFluid.addAlternative(hotSteamId);
        boiler2.getInputs().add(b2InFluid);
        IngredientStack b2OutFluid = IngredientStack.fluid(warmSteamId, "Warm Steam", 500.0);
        b2OutFluid.addAlternative(hotSteamId);
        b2OutFluid.addAlternative(superheatedSteamId);
        boiler2.getOutputs().add(b2OutFluid);
        graph.addNode(boiler2);

        // 4. Boiler 3: [Diamond, Warm Steam] -> [Hot Steam]
        RecipeNode boiler3 = new RecipeNode("b3", "Boiler 3", 20, 0, GTVoltageTier.LV);
        boiler3.getInputs().add(IngredientStack.item(diamondId, "Diamond", 1.0));
        IngredientStack b3InFluid = IngredientStack.fluid(warmSteamId, "Warm Steam", 500.0);
        b3InFluid.addAlternative(steamId);
        b3InFluid.addAlternative(hotSteamId);
        boiler3.getInputs().add(b3InFluid);
        IngredientStack b3OutFluid = IngredientStack.fluid(hotSteamId, "Hot Steam", 500.0);
        b3OutFluid.addAlternative(superheatedSteamId);
        boiler3.getOutputs().add(b3OutFluid);
        graph.addNode(boiler3);

        // 5. Connect existing Steam pipeline: b1 -> b2 -> b3
        graph.addConnection(boiler1.getId(), 0, boiler2.getId(), 1);
        graph.addConnection(boiler2.getId(), 0, boiler3.getId(), 1);

        Assertions.assertEquals(2, graph.getConnections().size());

        // 6. Scan candidates: Only Diamond (3 wires) should be found! No steam cross-connections!
        List<AutoConnectFilterDialog.ResourceEntry> candidates = AutoConnectFilterDialog.scanCandidates(graph);
        Assertions.assertEquals(1, candidates.size(), "Should ONLY detect Diamond as connection candidate");
        Assertions.assertEquals(diamondId, candidates.get(0).getStack().getId());
        Assertions.assertEquals(3, candidates.get(0).getPotentialWireCount(), "Diamond should have 3 wires to the 3 boilers");

        // 7. Execute autoConnect with null filter (all candidates)
        List<FlowGraph.ConnectionEdge> added = ToolbarWidget.autoConnect(graph, null, null);
        Assertions.assertEquals(3, added.size(), "Only 3 Diamond wires should be added");
        Assertions.assertEquals(5, graph.getConnections().size(), "Total connections should be 2 existing steam + 3 new diamond");

        // 8. Verify the existing steam connections were not corrupted
        Assertions.assertEquals(warmSteamId, boiler3.getInputs().get(1).getId(), "Boiler 3 input alternative must NOT be mutated to Steam");
    }
}
