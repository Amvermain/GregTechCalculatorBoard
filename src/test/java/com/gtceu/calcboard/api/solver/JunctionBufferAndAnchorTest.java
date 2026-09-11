package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SupplyMode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class JunctionBufferAndAnchorTest {

    @Test
    public void testVoidSinkSpillwayPriority() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation ironId = ResourceLocation.tryParse("minecraft:iron_ingot");

        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:macerator"), "Producer", 20, 30, GTVoltageTier.LV);
        producer.getOutputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(producer);

        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:furnace"), "Consumer", 20, 30, GTVoltageTier.LV);
        consumer.getInputs().add(IngredientStack.item(ironId, "Iron Ingot", 0.8));
        graph.addNode(consumer);

        RecipeNode voidSink = RecipeNode.createReroute(100, 100);
        voidSink.bindRerouteIngredient(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        voidSink.setSupplyMode(SupplyMode.VOID_SINK);
        graph.addNode(voidSink);

        FlowGraph.ConnectionEdge edgeToConsumer = new FlowGraph.ConnectionEdge(producer.getId(), 0, consumer.getId(), 0);
        FlowGraph.ConnectionEdge edgeToVoid = new FlowGraph.ConnectionEdge(producer.getId(), 0, voidSink.getId(), 0);
        graph.addConnection(edgeToConsumer);
        graph.addConnection(edgeToVoid);

        Map<FlowGraph.ConnectionEdge, Double> allocations = FlowEdgeAllocator.calculateOutgoingEdgeAllocations(
                graph, producer, 0, 1.0, null, (FlowEdgeAllocator.CachedEdgeIndex) null
        );

        Assertions.assertEquals(0.8, allocations.getOrDefault(edgeToConsumer, 0.0), 1e-4);
        Assertions.assertEquals(0.2, allocations.getOrDefault(edgeToVoid, 0.0), 1e-4);

        Map<FlowGraph.ConnectionEdge, Double> starvedAllocations = FlowEdgeAllocator.calculateOutgoingEdgeAllocations(
                graph, producer, 0, 0.5, null, (FlowEdgeAllocator.CachedEdgeIndex) null
        );

        Assertions.assertEquals(0.5, starvedAllocations.getOrDefault(edgeToConsumer, 0.0), 1e-4);
        Assertions.assertEquals(0.0, starvedAllocations.getOrDefault(edgeToVoid, 0.0), 1e-4);
    }

    @Test
    public void testSupplyJunctionAnchorDownstreamScaling() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation waterId = ResourceLocation.tryParse("minecraft:water");

        RecipeNode supplyJunction = RecipeNode.createReroute(50, 50);
        supplyJunction.bindRerouteIngredient(IngredientStack.fluid(waterId, "Water", 1000.0));
        supplyJunction.setSupplyMode(SupplyMode.FIXED_RATE);
        supplyJunction.setExternalSupplyRate(1000.0);
        supplyJunction.setBaseNode(true);
        graph.addNode(supplyJunction);

        RecipeNode boiler = RecipeNode.create(ResourceLocation.tryParse("gtceu:boiler"), "Boiler", 20, 30, GTVoltageTier.LV);
        boiler.getInputs().add(IngredientStack.fluid(waterId, "Water", 250.0));
        boiler.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 250.0));
        boiler.setMachineCount(1.0);
        graph.addNode(boiler);

        graph.addConnection(new FlowGraph.ConnectionEdge(supplyJunction.getId(), 0, boiler.getId(), 0));

        AutoRatioEngine.autoRatioFromAnchor(graph, supplyJunction, true);

        Assertions.assertEquals(1.0, supplyJunction.getMachineCount(), 1e-4);
        Assertions.assertEquals(4.0, boiler.getMachineCount(), 1e-4);
    }

    @Test
    public void testDrainJunctionAnchorUpstreamScaling() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation quartzId = ResourceLocation.tryParse("minecraft:quartz");

        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:sifter"), "Sifter", 20, 30, GTVoltageTier.LV);
        producer.getOutputs().add(IngredientStack.item(quartzId, "Quartz", 0.5));
        producer.setMachineCount(1.0);
        graph.addNode(producer);

        RecipeNode drainJunction = RecipeNode.createReroute(120, 120);
        drainJunction.bindRerouteIngredient(IngredientStack.item(quartzId, "Quartz", 1.0));
        drainJunction.setSupplyMode(SupplyMode.FIXED_DRAIN);
        drainJunction.setExternalDrainRate(2.0);
        drainJunction.setBaseNode(true);
        graph.addNode(drainJunction);

        graph.addConnection(new FlowGraph.ConnectionEdge(producer.getId(), 0, drainJunction.getId(), 0));

        AutoRatioEngine.autoRatioFromAnchor(graph, drainJunction, true);

        Assertions.assertEquals(1.0, drainJunction.getMachineCount(), 1e-4);
        Assertions.assertEquals(4.0, producer.getMachineCount(), 1e-4);
    }

    @Test
    public void testPolarizerBranchWithFixedDrainAllocationAndNetSurplus() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation shardId = ResourceLocation.tryParse("gtceu:nether_star_shard");

        RecipeNode polarizer = RecipeNode.create(ResourceLocation.tryParse("gtceu:polarizer"), "Polarizer", 20, 30, GTVoltageTier.LV);
        polarizer.getOutputs().add(IngredientStack.item(shardId, "Shard", 1.0));
        polarizer.setMachineCount(1.0);
        graph.addNode(polarizer);

        double singleDemand = 0.8333 / 4.0;
        for (int i = 0; i < 4; i++) {
            RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:assembler"), "Consumer " + i, 20, 30, GTVoltageTier.LV);
            consumer.getInputs().add(IngredientStack.item(shardId, "Shard", singleDemand));
            consumer.setMachineCount(1.0);
            graph.addNode(consumer);
            graph.addConnection(new FlowGraph.ConnectionEdge(polarizer.getId(), 0, consumer.getId(), 0));
        }

        RecipeNode drainJunction = RecipeNode.createReroute(200, 200);
        drainJunction.bindRerouteIngredient(IngredientStack.item(shardId, "Shard", 1.0));
        drainJunction.setSupplyMode(SupplyMode.FIXED_DRAIN);
        drainJunction.setExternalDrainRate(0.1667);
        graph.addNode(drainJunction);
        FlowGraph.ConnectionEdge drainEdge = new FlowGraph.ConnectionEdge(polarizer.getId(), 0, drainJunction.getId(), 0);
        graph.addConnection(drainEdge);

        Map<FlowGraph.ConnectionEdge, Double> allocations = FlowEdgeAllocator.calculateOutgoingEdgeAllocations(
                graph, polarizer, 0, 1.0, null, (FlowEdgeAllocator.CachedEdgeIndex) null
        );

        Assertions.assertEquals(0.1667, allocations.getOrDefault(drainEdge, 0.0), 1e-4);

        double inflow = ProductionETACalculator.calculateNetInflowRate(graph, drainJunction, 0);
        Assertions.assertEquals(0.1667, inflow, 1e-4);

        double netSurplus = inflow - drainJunction.getExternalDrainRate();
        Assertions.assertEquals(0.0, netSurplus, 1e-4);
    }
}
