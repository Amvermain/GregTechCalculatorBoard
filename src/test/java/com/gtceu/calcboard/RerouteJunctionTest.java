package com.gtceu.calcboard;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class RerouteJunctionTest {

    @Test
    public void testRerouteNodeCreationAndZeroCost() {
        RecipeNode reroute = RecipeNode.createReroute(100.0, 200.0);
        Assertions.assertTrue(reroute.isReroute());
        Assertions.assertEquals(100.0, reroute.getPosX(), 0.001);
        Assertions.assertEquals(200.0, reroute.getPosY(), 0.001);
        Assertions.assertEquals(32, reroute.getCardWidth());
        Assertions.assertEquals(32, reroute.getCardHeight());
        Assertions.assertEquals(0.0, reroute.getBaseEUt(), 0.001);
        Assertions.assertEquals(0.0, reroute.getBaseDurationTicks(), 0.001);

        FlowGraph graph = new FlowGraph();
        graph.addNode(reroute);

        BalanceSummary summary = graph.computeSummary();
        Assertions.assertEquals(0, summary.totalMachineCount());
        Assertions.assertEquals(0.0, summary.totalEUt(), 0.001);
    }

    @Test
    public void testRerouteDynamicIngredientBinding() {
        RecipeNode reroute = RecipeNode.createReroute(50.0, 50.0);
        IngredientStack dummySteam = IngredientStack.fluid(new ResourceLocation("gtceu:steam"), "Steam", 500.0);

        reroute.bindRerouteIngredient(dummySteam);
        Assertions.assertEquals(1, reroute.getInputs().size());
        Assertions.assertEquals(1, reroute.getOutputs().size());
        Assertions.assertEquals(new ResourceLocation("gtceu:steam"), reroute.getInputs().get(0).getId());
        Assertions.assertEquals(new ResourceLocation("gtceu:steam"), reroute.getOutputs().get(0).getId());
        Assertions.assertEquals("Steam", reroute.getInputs().get(0).getDisplayName());
        Assertions.assertEquals(500.0, reroute.getInputs().get(0).getAmount(), 0.001);

        reroute.unbindRerouteIngredient();
        Assertions.assertTrue(reroute.getInputs().isEmpty());
        Assertions.assertTrue(reroute.getOutputs().isEmpty());
    }

    @Test
    public void testAutoRatioPropagationThroughReroute() {
        FlowGraph graph = new FlowGraph();

        // 1. Producer: Produces 100 Steam/s at count = 1.0 (20 ticks per cycle -> 1 cycle/s = 100 Steam/s)
        RecipeNode producer = RecipeNode.create("Boiler", 20.0, 0.0, GTVoltageTier.LV);
        producer.getOutputs().add(IngredientStack.fluid(new ResourceLocation("gtceu:steam"), "Steam", 100.0));
        producer.setMachineCount(1.0);
        graph.addNode(producer);

        // 2. Reroute Junction
        RecipeNode reroute = RecipeNode.createReroute(100.0, 100.0);
        reroute.bindRerouteIngredient(IngredientStack.fluid(new ResourceLocation("gtceu:steam"), "Steam", 100.0));
        graph.addNode(reroute);

        // 3. Consumer: Consumes 500 Steam/s at count = 1.0 (20 ticks per cycle -> 1 cycle/s = 500 Steam/s)
        RecipeNode consumer = RecipeNode.create("Steam Turbine", 20.0, 32.0, GTVoltageTier.LV);
        consumer.getInputs().add(IngredientStack.fluid(new ResourceLocation("gtceu:steam"), "Steam", 500.0));
        consumer.setMachineCount(1.0);
        graph.addNode(consumer);

        // Wire producer -> reroute -> consumer
        graph.addConnection(producer.getId(), 0, reroute.getId(), 0);
        graph.addConnection(reroute.getId(), 0, consumer.getId(), 0);

        // Calculate consumer match count starting from producer
        double matchedConsumer = FlowGraphSolver.calculateConsumerMatchCount(graph, producer, 0, reroute, 0);
        Assertions.assertEquals(1.0, matchedConsumer, 0.001);

        // Auto-ratio backwards from consumer (consumer demands 500, producer supplies 100 per machine -> producer should scale to 5.0)
        graph.autoRatioFromAnchor(consumer);
        Assertions.assertEquals(5.0, producer.getMachineCount(), 0.001);
        Assertions.assertEquals(1.0, consumer.getMachineCount(), 0.001);
    }

    @Test
    public void testRerouteNbtSerialization() {
        RecipeNode reroute = RecipeNode.createReroute(120.0, 240.0);
        reroute.bindRerouteIngredient(IngredientStack.item(new ResourceLocation("minecraft:iron_ingot"), "Iron Ingot", 4.0));

        CompoundTag tag = reroute.serializeNBT();
        Assertions.assertTrue(tag.getBoolean("isReroute"));

        RecipeNode deserialized = RecipeNode.deserializeNBT(tag);
        Assertions.assertTrue(deserialized.isReroute());
        Assertions.assertEquals(120.0, deserialized.getPosX(), 0.001);
        Assertions.assertEquals(240.0, deserialized.getPosY(), 0.001);
        Assertions.assertEquals(new ResourceLocation("minecraft:iron_ingot"), deserialized.getInputs().get(0).getId());
        Assertions.assertEquals(new ResourceLocation("minecraft:iron_ingot"), deserialized.getOutputs().get(0).getId());
        Assertions.assertEquals(4.0, deserialized.getInputs().get(0).getAmount(), 0.001);
    }

    @Test
    public void testTargetBatchAmountAndReset() {
        RecipeNode reroute = RecipeNode.createReroute(100.0, 100.0);
        Assertions.assertFalse(reroute.hasTargetBatch());
        Assertions.assertEquals(0.0, reroute.getTargetBatchAmount(), 0.001);

        reroute.setTargetBatchAmount(64000.0);
        Assertions.assertTrue(reroute.hasTargetBatch());
        Assertions.assertEquals(64000.0, reroute.getTargetBatchAmount(), 0.001);

        // Reset (e.g. via Shift+RightClick)
        reroute.setTargetBatchAmount(0.0);
        Assertions.assertFalse(reroute.hasTargetBatch());
        Assertions.assertEquals(0.0, reroute.getTargetBatchAmount(), 0.001);
    }

    @Test
    public void testRerouteNodeRemovalInGraph() {
        FlowGraph graph = new FlowGraph();
        RecipeNode producer = RecipeNode.create("Producer", 20.0, 0.0, GTVoltageTier.LV);
        RecipeNode reroute = RecipeNode.createReroute(50.0, 50.0);
        RecipeNode consumer = RecipeNode.create("Consumer", 20.0, 0.0, GTVoltageTier.LV);

        graph.addNode(producer);
        graph.addNode(reroute);
        graph.addNode(consumer);

        graph.addConnection(producer.getId(), 0, reroute.getId(), 0);
        graph.addConnection(reroute.getId(), 0, consumer.getId(), 0);

        Assertions.assertEquals(3, graph.getNodes().size());
        Assertions.assertEquals(2, graph.getConnections().size());

        // Remove reroute node
        graph.removeNode(reroute);
        Assertions.assertEquals(2, graph.getNodes().size());
        Assertions.assertNull(graph.findNodeById(reroute.getId()));
        Assertions.assertEquals(0, graph.getConnections().size(), "Connections to/from reroute must be cleaned up");
    }
}


