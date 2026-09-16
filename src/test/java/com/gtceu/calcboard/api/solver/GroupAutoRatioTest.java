package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MinecraftBootstrapExtension.class)
public class GroupAutoRatioTest {

    @Test
    public void testGroupAutoRatioScalesExternalNodesAndPreservesInternalCounts() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation oreItem = ResourceLocation.tryParse("gtceu:crushed_copper_ore");
        ResourceLocation refinedItem = ResourceLocation.tryParse("gtceu:purified_copper_ore");
        ResourceLocation finalItem = ResourceLocation.tryParse("gtceu:copper_ingot");

        // External Producer: produces 2 ore/sec at count = 1.0
        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:miner"), "Miner", 20.0, 30.0, GTVoltageTier.LV);
        producer.setPosX(0);
        producer.setPosY(100);
        producer.setMachineCount(1.0);
        producer.getOutputs().add(IngredientStack.item(oreItem, "Crushed Ore", 2.0));
        graph.addNode(producer);

        // Group internal node 1: consumes 2 ore/sec, produces 2 refined/sec per machine
        // With count = 5.0, requires 10 ore/sec, produces 10 refined/sec
        RecipeNode node1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:refiner"), "Primary Refiner", 20.0, 30.0, GTVoltageTier.LV);
        node1.setPosX(300);
        node1.setPosY(50);
        node1.setMachineCount(5.0);
        node1.getInputs().add(IngredientStack.item(oreItem, "Crushed Ore", 2.0));
        node1.getOutputs().add(IngredientStack.item(refinedItem, "Refined Ore", 2.0));
        graph.addNode(node1);

        // Group internal node 2: consumes 2 refined/sec, produces 2 ingots/sec per machine
        // With count = 3.0, requires 6 refined/sec, produces 6 ingots/sec
        RecipeNode node2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:smelter"), "Smelter A", 20.0, 30.0, GTVoltageTier.LV);
        node2.setPosX(500);
        node2.setPosY(50);
        node2.setMachineCount(3.0);
        node2.getInputs().add(IngredientStack.item(refinedItem, "Refined Ore", 2.0));
        node2.getOutputs().add(IngredientStack.item(finalItem, "Ingot", 2.0));
        graph.addNode(node2);

        // Group internal node 3: consumes 1 refined/sec per machine
        // With count = 3.0, requires 3 refined/sec
        RecipeNode node3 = RecipeNode.create(ResourceLocation.tryParse("gtceu:centrifuge"), "Byproduct Extractor", 20.0, 30.0, GTVoltageTier.LV);
        node3.setPosX(500);
        node3.setPosY(200);
        node3.setMachineCount(3.0);
        node3.getInputs().add(IngredientStack.item(refinedItem, "Refined Ore", 1.0));
        graph.addNode(node3);

        // External Consumer: consumes 2 ingots/sec per machine
        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:assembler"), "Assembler", 20.0, 30.0, GTVoltageTier.LV);
        consumer.setPosX(800);
        consumer.setPosY(100);
        consumer.setMachineCount(1.0);
        consumer.getInputs().add(IngredientStack.item(finalItem, "Ingot", 2.0));
        graph.addNode(consumer);

        // Wiring: producer -> node1, node1 -> node2, node1 -> node3, node2 -> consumer
        graph.addConnection(producer.getId(), 0, node1.getId(), 0);
        graph.addConnection(node1.getId(), 0, node2.getId(), 0);
        graph.addConnection(node1.getId(), 0, node3.getId(), 0);
        graph.addConnection(node2.getId(), 0, consumer.getId(), 0);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Process Block", List.of(node1, node2, node3), CanvasGroupFrame.COLOR_BLUE);
        graph.addFrame(frame);

        int changed = FlowGraphSolver.autoRatioFromGroupFrame(graph, frame, AutoRatioMode.INTEGER_CEIL);
        Assertions.assertTrue(changed > 0, "External nodes should be updated");

        // Group internal node counts must be strictly preserved
        Assertions.assertEquals(5.0, node1.getMachineCount(), 1e-4, "Group internal node1 count must remain 5.0");
        Assertions.assertEquals(3.0, node2.getMachineCount(), 1e-4, "Group internal node2 count must remain 3.0");
        Assertions.assertEquals(3.0, node3.getMachineCount(), 1e-4, "Group internal node3 count must remain 3.0");

        // External producer needs to satisfy 10 ore/sec (5 refiners * 2 ore/sec)
        // With miner producing 2 ore/sec, miner count should be 5.0
        Assertions.assertEquals(5.0, producer.getMachineCount(), 1e-4, "Producer must scale to satisfy group demand");

        // External consumer receives 6 ingots/sec (3 smelters * 2 ingots/sec)
        // With assembler consuming 2 ingots/sec, assembler count should be 3.0
        Assertions.assertEquals(3.0, consumer.getMachineCount(), 1e-4, "Consumer must scale to match group output");
    }

    @Test
    public void testGroupAutoRatioFractional() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation oreItem = ResourceLocation.tryParse("gtceu:crushed_iron_ore");

        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:miner"), "Miner", 20.0, 30.0, GTVoltageTier.LV);
        producer.setPosX(0);
        producer.setPosY(100);
        producer.setMachineCount(1.0);
        producer.getOutputs().add(IngredientStack.item(oreItem, "Crushed Ore", 3.0));
        graph.addNode(producer);

        RecipeNode refiner = RecipeNode.create(ResourceLocation.tryParse("gtceu:refiner"), "Refiner", 20.0, 30.0, GTVoltageTier.LV);
        refiner.setPosX(300);
        refiner.setPosY(100);
        refiner.setMachineCount(1.0);
        refiner.getInputs().add(IngredientStack.item(oreItem, "Crushed Ore", 5.0));
        graph.addNode(refiner);

        graph.addConnection(producer.getId(), 0, refiner.getId(), 0);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Refiner Frame", List.of(refiner), CanvasGroupFrame.COLOR_PURPLE);
        graph.addFrame(frame);

        int changed = FlowGraphSolver.autoRatioFromGroupFrame(graph, frame, AutoRatioMode.FRACTIONAL);
        Assertions.assertTrue(changed > 0);

        Assertions.assertEquals(1.0, refiner.getMachineCount(), 1e-4);
        // Producer needs 5.0 / 3.0 = 1.6667
        Assertions.assertEquals(5.0 / 3.0, producer.getMachineCount(), 1e-3);
    }

    @Test
    public void testGroupWithNoExternalConnectionsReturnsZero() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation item = ResourceLocation.tryParse("gtceu:wood");

        RecipeNode node1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:sawmill"), "Sawmill", 20.0, 30.0, GTVoltageTier.LV);
        node1.setMachineCount(2.0);
        node1.getOutputs().add(IngredientStack.item(item, "Plank", 1.0));
        graph.addNode(node1);

        RecipeNode node2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:lathe"), "Lathe", 20.0, 30.0, GTVoltageTier.LV);
        node2.setMachineCount(2.0);
        node2.getInputs().add(IngredientStack.item(item, "Plank", 1.0));
        graph.addNode(node2);

        graph.addConnection(node1.getId(), 0, node2.getId(), 0);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Isolated Frame", List.of(node1, node2), CanvasGroupFrame.COLOR_SLATE);
        graph.addFrame(frame);

        int changed = FlowGraphSolver.autoRatioFromGroupFrame(graph, frame, AutoRatioMode.INTEGER_CEIL);
        Assertions.assertEquals(0, changed, "Isolated frame must return 0 changed nodes");
    }

    @Test
    public void testEmptyGroupReturnsZero() {
        FlowGraph graph = new FlowGraph();
        CanvasGroupFrame emptyFrame = new CanvasGroupFrame("empty-id", "Empty", CanvasGroupFrame.COLOR_BLUE, 0, 0, 200, 200);
        graph.addFrame(emptyFrame);

        int changed = FlowGraphSolver.autoRatioFromGroupFrame(graph, emptyFrame, AutoRatioMode.INTEGER_CEIL);
        Assertions.assertEquals(0, changed, "Empty frame must return 0");
    }
}
