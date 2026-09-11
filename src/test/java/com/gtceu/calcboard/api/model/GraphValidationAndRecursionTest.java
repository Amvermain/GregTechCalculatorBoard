package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.bom.MultiblockBOMCalculator;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.FlowSummaryAggregator;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class GraphValidationAndRecursionTest {

    @Test
    public void testCleanupConnectionsPreservesAlternativeMatches() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create("Producer", 100.0, 32.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_plate"), "Iron Plate", 1.0));
        graph.addNode(producer);

        RecipeNode consumer = RecipeNode.create("Consumer", 100.0, 32.0, GTVoltageTier.LV);
        IngredientStack tagInput = IngredientStack.item(ResourceLocation.tryParse("forge:plates/iron"), "Iron Plates Tag", 1.0);
        tagInput.setAlternatives(List.of(ResourceLocation.tryParse("gtceu:iron_plate"), ResourceLocation.tryParse("minecraft:iron_ingot")));
        consumer.addInput(tagInput);
        graph.addNode(consumer);

        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);
        Assertions.assertEquals(1, graph.getConnections().size());

        boolean removed = graph.cleanupInvalidConnections();
        Assertions.assertFalse(removed);
        Assertions.assertEquals(1, graph.getConnections().size());
    }

    @Test
    public void testCleanupConnectionsPreservesUnboundReroute() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create("Producer", 100.0, 32.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));
        graph.addNode(producer);

        RecipeNode reroute = RecipeNode.createReroute(100.0, 100.0);
        graph.addNode(reroute);

        graph.addConnection(producer.getId(), 0, reroute.getId(), 0);
        Assertions.assertEquals(1, graph.getConnections().size());

        boolean removed = graph.cleanupInvalidConnections();
        Assertions.assertFalse(removed);
        Assertions.assertEquals(1, graph.getConnections().size());
    }

    @Test
    public void testCleanupConnectionsPreservesStressUnits() {
        FlowGraph graph = new FlowGraph();

        RecipeNode motor = RecipeNode.create("Motor", 20.0, 0.0, GTVoltageTier.LV);
        motor.addOutput(IngredientStack.stressUnit(256.0));
        graph.addNode(motor);

        RecipeNode mill = RecipeNode.create("Mill", 20.0, 0.0, GTVoltageTier.LV);
        mill.addInput(IngredientStack.stressUnit(256.0));
        graph.addNode(mill);

        graph.addConnection(motor.getId(), 0, mill.getId(), 0);
        Assertions.assertEquals(1, graph.getConnections().size());

        boolean removed = graph.cleanupInvalidConnections();
        Assertions.assertFalse(removed);
        Assertions.assertEquals(1, graph.getConnections().size());
    }

    @Test
    public void testCleanupConnectionsRemovesIncompatible() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create("Producer", 100.0, 32.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));
        graph.addNode(producer);

        RecipeNode consumer = RecipeNode.create("Consumer", 100.0, 32.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:gold_ingot"), "Gold Ingot", 1.0));
        graph.addNode(consumer);

        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);
        Assertions.assertEquals(1, graph.getConnections().size());

        boolean removed = graph.cleanupInvalidConnections();
        Assertions.assertTrue(removed);
        Assertions.assertEquals(0, graph.getConnections().size());
    }

    @Test
    public void testCircularSubGraphSerializationTerminatesSafely() {
        FlowGraph graphA = new FlowGraph();
        RecipeNode moduleA = RecipeNode.create("Module A", 100.0, 32.0, GTVoltageTier.LV);
        moduleA.setModule(true);
        graphA.addNode(moduleA);

        FlowGraph graphB = new FlowGraph();
        RecipeNode moduleB = RecipeNode.create("Module B", 100.0, 32.0, GTVoltageTier.LV);
        moduleB.setModule(true);
        graphB.addNode(moduleB);

        moduleA.setSubGraph(graphB);
        moduleB.setSubGraph(graphA);

        Assertions.assertDoesNotThrow(() -> {
            CompoundTag tag = graphA.serializeNBT();
            Assertions.assertNotNull(tag);
        });

        Assertions.assertDoesNotThrow(() -> {
            RecipeNode copy = moduleA.copy();
            Assertions.assertNotNull(copy);
        });
    }

    @Test
    public void testCircularSubGraphSummaryTerminatesSafely() {
        FlowGraph graphA = new FlowGraph();
        RecipeNode moduleA = RecipeNode.create("Module A", 100.0, 32.0, GTVoltageTier.LV);
        moduleA.setModule(true);
        graphA.addNode(moduleA);

        FlowGraph graphB = new FlowGraph();
        RecipeNode moduleB = RecipeNode.create("Module B", 100.0, 32.0, GTVoltageTier.LV);
        moduleB.setModule(true);
        graphB.addNode(moduleB);

        moduleA.setSubGraph(graphB);
        moduleB.setSubGraph(graphA);

        Assertions.assertDoesNotThrow(() -> {
            BalanceSummary summary = FlowSummaryAggregator.computeSummary(graphA, false);
            Assertions.assertNotNull(summary);
        });
    }

    @Test
    public void testCircularSubGraphBOMTerminatesSafely() {
        FlowGraph graphA = new FlowGraph();
        RecipeNode moduleA = RecipeNode.create("Module A", 100.0, 32.0, GTVoltageTier.LV);
        moduleA.setModule(true);
        graphA.addNode(moduleA);

        FlowGraph graphB = new FlowGraph();
        RecipeNode moduleB = RecipeNode.create("Module B", 100.0, 32.0, GTVoltageTier.LV);
        moduleB.setModule(true);
        graphB.addNode(moduleB);

        moduleA.setSubGraph(graphB);
        moduleB.setSubGraph(graphA);

        Assertions.assertDoesNotThrow(() -> {
            MultiblockBOMSummary bom = MultiblockBOMCalculator.calculateBOM(graphA, false);
            Assertions.assertNotNull(bom);
        });
    }

    @Test
    public void testDeepSubGraphTerminatesAtDepthLimit() {
        FlowGraph root = new FlowGraph();
        FlowGraph current = root;

        for (int i = 0; i < 25; i++) {
            RecipeNode module = RecipeNode.create("Sub " + i, 100.0, 32.0, GTVoltageTier.LV);
            module.setModule(true);
            FlowGraph next = new FlowGraph();
            module.setSubGraph(next);
            current.addNode(module);
            current = next;
        }

        Assertions.assertDoesNotThrow(() -> {
            CompoundTag tag = root.serializeNBT();
            Assertions.assertNotNull(tag);
        });

        Assertions.assertDoesNotThrow(() -> {
            BalanceSummary summary = FlowSummaryAggregator.computeSummary(root, false);
            Assertions.assertNotNull(summary);
        });

        Assertions.assertDoesNotThrow(() -> {
            MultiblockBOMSummary bom = MultiblockBOMCalculator.calculateBOM(root, false);
            Assertions.assertNotNull(bom);
        });
    }
}
