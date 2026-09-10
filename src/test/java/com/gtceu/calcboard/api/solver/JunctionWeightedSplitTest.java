package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.FlowSplitMode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SupplyMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class JunctionWeightedSplitTest {

    @Test
    @DisplayName("RFC-042: Basic weight ratio splitting (2:5:3 scenario)")
    void testBasicWeightRatioSplitting() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.setJunctionSplitMode(FlowSplitMode.WEIGHTED);
        junction.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1));
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "Machine 1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 200));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "Machine 2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 200));
        graph.addNode(m2);

        RecipeNode m3 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m3"), "Machine 3", 20, 30, GTVoltageTier.LV);
        m3.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 200));
        graph.addNode(m3);

        FlowGraph.ConnectionEdge edge1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 0.0, 0, 2.0);
        FlowGraph.ConnectionEdge edge2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 0.0, 0, 5.0);
        FlowGraph.ConnectionEdge edge3 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m3.getId(), 0, 0.0, 0, 3.0);
        graph.addConnection(edge1);
        graph.addConnection(edge2);
        graph.addConnection(edge3);

        Map<FlowGraph.ConnectionEdge, Double> alloc100 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 100.0);
        Assertions.assertEquals(20.0, alloc100.get(edge1), 0.001);
        Assertions.assertEquals(50.0, alloc100.get(edge2), 0.001);
        Assertions.assertEquals(30.0, alloc100.get(edge3), 0.001);

        Map<FlowGraph.ConnectionEdge, Double> alloc150 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 150.0);
        Assertions.assertEquals(30.0, alloc150.get(edge1), 0.001);
        Assertions.assertEquals(75.0, alloc150.get(edge2), 0.001);
        Assertions.assertEquals(45.0, alloc150.get(edge3), 0.001);
    }

    @Test
    @DisplayName("RFC-042: Weighted splitting with fixed limit caps (water-filling)")
    void testWeightedSplitWithFixedLimitCaps() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.setJunctionSplitMode(FlowSplitMode.WEIGHTED);
        junction.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1));
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "Machine 1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 200));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "Machine 2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 200));
        graph.addNode(m2);

        RecipeNode m3 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m3"), "Machine 3", 20, 30, GTVoltageTier.LV);
        m3.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 200));
        graph.addNode(m3);

        FlowGraph.ConnectionEdge edge1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 25.0, 0, 2.0);
        FlowGraph.ConnectionEdge edge2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 0.0, 0, 5.0);
        FlowGraph.ConnectionEdge edge3 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m3.getId(), 0, 0.0, 0, 3.0);
        graph.addConnection(edge1);
        graph.addConnection(edge2);
        graph.addConnection(edge3);

        Map<FlowGraph.ConnectionEdge, Double> alloc100 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 100.0);
        Assertions.assertEquals(20.0, alloc100.get(edge1), 0.001);
        Assertions.assertEquals(50.0, alloc100.get(edge2), 0.001);
        Assertions.assertEquals(30.0, alloc100.get(edge3), 0.001);

        Map<FlowGraph.ConnectionEdge, Double> alloc150 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 150.0);
        Assertions.assertEquals(25.0, alloc150.get(edge1), 0.001);
        Assertions.assertEquals(78.125, alloc150.get(edge2), 0.001);
        Assertions.assertEquals(46.875, alloc150.get(edge3), 0.001);
    }

    @Test
    @DisplayName("RFC-042: Weighted splitting within priority tiers")
    void testWeightedSplitWithinPriorityTiers() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.setJunctionSplitMode(FlowSplitMode.WEIGHTED);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1));
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "Machine 1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 60));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "Machine 2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 60));
        graph.addNode(m2);

        RecipeNode m3 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m3"), "Machine 3", 20, 30, GTVoltageTier.LV);
        m3.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 60));
        graph.addNode(m3);

        RecipeNode m4 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m4"), "Machine 4", 20, 30, GTVoltageTier.LV);
        m4.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 60));
        graph.addNode(m4);

        FlowGraph.ConnectionEdge edge1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 0.0, 2, 1.0);
        FlowGraph.ConnectionEdge edge2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 0.0, 1, 2.0);
        FlowGraph.ConnectionEdge edge3 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m3.getId(), 0, 0.0, 1, 1.0);
        FlowGraph.ConnectionEdge edge4 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m4.getId(), 0, 0.0, 0, 1.0);
        graph.addConnection(edge1);
        graph.addConnection(edge2);
        graph.addConnection(edge3);
        graph.addConnection(edge4);

        Map<FlowGraph.ConnectionEdge, Double> alloc100 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 100.0);
        Assertions.assertEquals(60.0, alloc100.get(edge1), 0.001);
        Assertions.assertEquals(40.0 * 2.0 / 3.0, alloc100.get(edge2), 0.001);
        Assertions.assertEquals(40.0 * 1.0 / 3.0, alloc100.get(edge3), 0.001);
        Assertions.assertEquals(0.0, alloc100.get(edge4), 0.001);

        Map<FlowGraph.ConnectionEdge, Double> alloc180 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 180.0);
        Assertions.assertEquals(60.0, alloc180.get(edge1), 0.001);
        Assertions.assertEquals(60.0, alloc180.get(edge2), 0.001);
        Assertions.assertEquals(60.0, alloc180.get(edge3), 0.001);
        Assertions.assertEquals(0.0, alloc180.get(edge4), 0.001);

        Map<FlowGraph.ConnectionEdge, Double> alloc200 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 200.0);
        Assertions.assertEquals(60.0, alloc200.get(edge1), 0.001);
        Assertions.assertEquals(60.0, alloc200.get(edge2), 0.001);
        Assertions.assertEquals(60.0, alloc200.get(edge3), 0.001);
        Assertions.assertEquals(20.0, alloc200.get(edge4), 0.001);
    }

    @Test
    @DisplayName("RFC-042: Weighted splitting with void sinks and surplus")
    void testWeightedSplitWithVoidSink() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.setJunctionSplitMode(FlowSplitMode.WEIGHTED);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:stone"), "Stone", 1));
        graph.addNode(junction);

        RecipeNode normalConsumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:stone_consumer"), "Consumer", 20, 30, GTVoltageTier.LV);
        normalConsumer.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:stone"), "Stone", 50));
        graph.addNode(normalConsumer);

        RecipeNode voidSink1 = RecipeNode.createReroute(0, 0);
        voidSink1.setSupplyMode(SupplyMode.VOID_SINK);
        voidSink1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:stone"), "Stone", 1));
        graph.addNode(voidSink1);

        RecipeNode voidSink2 = RecipeNode.createReroute(0, 0);
        voidSink2.setSupplyMode(SupplyMode.VOID_SINK);
        voidSink2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:stone"), "Stone", 1));
        graph.addNode(voidSink2);

        FlowGraph.ConnectionEdge normalEdge = new FlowGraph.ConnectionEdge(junction.getId(), 0, normalConsumer.getId(), 0, 0.0, 0, 2.0);
        FlowGraph.ConnectionEdge voidEdge1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, voidSink1.getId(), 0, 0.0, 0, 1.0);
        FlowGraph.ConnectionEdge voidEdge2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, voidSink2.getId(), 0, 0.0, 0, 3.0);
        graph.addConnection(normalEdge);
        graph.addConnection(voidEdge1);
        graph.addConnection(voidEdge2);

        Map<FlowGraph.ConnectionEdge, Double> alloc90 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 90.0);
        Assertions.assertEquals(50.0, alloc90.get(normalEdge), 0.001);
        Assertions.assertEquals(10.0, alloc90.get(voidEdge1), 0.001);
        Assertions.assertEquals(30.0, alloc90.get(voidEdge2), 0.001);

        Map<FlowGraph.ConnectionEdge, Double> alloc30 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 30.0);
        Assertions.assertEquals(30.0, alloc30.get(normalEdge), 0.001);
        Assertions.assertEquals(0.0, alloc30.get(voidEdge1), 0.001);
        Assertions.assertEquals(0.0, alloc30.get(voidEdge2), 0.001);
    }

    @Test
    @DisplayName("RFC-042: Zero weight edges and fallback when all weights zero")
    void testZeroAndFallbackWeights() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.setJunctionSplitMode(FlowSplitMode.WEIGHTED);
        junction.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:lava"), "Lava", 1));
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "Machine 1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:lava"), "Lava", 100));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "Machine 2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:lava"), "Lava", 100));
        graph.addNode(m2);

        FlowGraph.ConnectionEdge edge1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 0.0, 0, 0.0);
        FlowGraph.ConnectionEdge edge2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 0.0, 0, 4.0);
        graph.addConnection(edge1);
        graph.addConnection(edge2);

        Map<FlowGraph.ConnectionEdge, Double> alloc = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 80.0);
        Assertions.assertEquals(0.0, alloc.get(edge1), 0.001);
        Assertions.assertEquals(80.0, alloc.get(edge2), 0.001);

        graph.setConnectionWeight(edge2, 0.0);
        FlowGraph.ConnectionEdge updatedEdge2 = graph.getConnections().stream()
                .filter(e -> e.toNodeId().equals(m2.getId()))
                .findFirst().orElseThrow();
        Assertions.assertEquals(0.0, updatedEdge2.weight(), 0.001);

        Map<FlowGraph.ConnectionEdge, Double> allocAllZero = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 80.0);
        FlowGraph.ConnectionEdge liveEdge1 = graph.getConnections().stream().filter(e -> e.toNodeId().equals(m1.getId())).findFirst().orElseThrow();
        FlowGraph.ConnectionEdge liveEdge2 = graph.getConnections().stream().filter(e -> e.toNodeId().equals(m2.getId())).findFirst().orElseThrow();
        Assertions.assertEquals(40.0, allocAllZero.get(liveEdge1), 0.001);
        Assertions.assertEquals(40.0, allocAllZero.get(liveEdge2), 0.001);
    }

    @Test
    @DisplayName("RFC-042: ConnectionEdge NBT serialization preserves custom weight")
    void testConnectionEdgeSerialization() {
        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge("fromNode", 0, "toNode", 1, 45.0, 3, 7.5);
        CompoundTag tag = edge.serializeNBT();

        FlowGraph.ConnectionEdge restored = FlowGraph.ConnectionEdge.deserializeNBT(tag);
        Assertions.assertEquals("fromNode", restored.fromNodeId());
        Assertions.assertEquals(0, restored.outputIndex());
        Assertions.assertEquals("toNode", restored.toNodeId());
        Assertions.assertEquals(1, restored.inputIndex());
        Assertions.assertEquals(45.0, restored.fixedFlowLimit(), 0.001);
        Assertions.assertEquals(3, restored.priority());
        Assertions.assertEquals(7.5, restored.weight(), 0.001);

        FlowGraph.ConnectionEdge defaultEdge = new FlowGraph.ConnectionEdge("a", 0, "b", 0);
        CompoundTag defTag = defaultEdge.serializeNBT();
        Assertions.assertFalse(defTag.contains("weight"));
        FlowGraph.ConnectionEdge defRestored = FlowGraph.ConnectionEdge.deserializeNBT(defTag);
        Assertions.assertEquals(1.0, defRestored.weight(), 0.001);
    }

    @Test
    @DisplayName("RFC-042: FlowGraph setConnectionWeight and setConnectionProperties")
    void testFlowGraphConnectionWeightModifications() {
        FlowGraph graph = new FlowGraph();
        graph.addConnection("src", 0, "dst", 0, 10.0, 1, 2.0);

        FlowGraph.ConnectionEdge initial = graph.getConnections().get(0);
        Assertions.assertEquals(2.0, initial.weight(), 0.001);

        boolean updatedW = graph.setConnectionWeight("src", 0, "dst", 0, 5.5);
        Assertions.assertTrue(updatedW);
        Assertions.assertEquals(5.5, graph.getConnections().get(0).weight(), 0.001);

        boolean updatedAll = graph.setConnectionProperties("src", 0, "dst", 0, 20.0, 4, 8.0);
        Assertions.assertTrue(updatedAll);
        FlowGraph.ConnectionEdge updated = graph.getConnections().get(0);
        Assertions.assertEquals(20.0, updated.fixedFlowLimit(), 0.001);
        Assertions.assertEquals(4, updated.priority());
        Assertions.assertEquals(8.0, updated.weight(), 0.001);
    }

    @Test
    @DisplayName("RFC-042: RecipeNode FlowSplitMode WEIGHTED property serialization")
    void testRecipeNodeFlowSplitModeSerialization() {
        RecipeNode node = RecipeNode.createReroute(10, 20);
        Assertions.assertEquals(FlowSplitMode.PROPORTIONAL, node.getJunctionSplitMode());

        node.setJunctionSplitMode(FlowSplitMode.WEIGHTED);
        Assertions.assertEquals(FlowSplitMode.WEIGHTED, node.getJunctionSplitMode());

        CompoundTag tag = node.serializeNBT();
        RecipeNode restored = RecipeNode.deserializeNBT(tag);
        Assertions.assertEquals(FlowSplitMode.WEIGHTED, restored.getJunctionSplitMode());
    }

    @Test
    @DisplayName("Regression: Uncapped consumer alongside void sink does not receive Double.MAX_VALUE")
    void testUncappedConsumerWithVoidSinkDoesNotAssignDoubleMax() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.setJunctionSplitMode(FlowSplitMode.WEIGHTED);
        junction.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1));
        graph.addNode(junction);

        RecipeNode normalWithDemand = RecipeNode.create(ResourceLocation.tryParse("gtceu:m_demand"), "With Demand", 20, 30, GTVoltageTier.LV);
        normalWithDemand.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 40));
        graph.addNode(normalWithDemand);

        RecipeNode normalUncapped = RecipeNode.create(ResourceLocation.tryParse("gtceu:m_uncapped"), "Uncapped", 20, 30, GTVoltageTier.LV);
        graph.addNode(normalUncapped);

        RecipeNode voidSink = RecipeNode.createReroute(0, 0);
        voidSink.setSupplyMode(SupplyMode.VOID_SINK);
        voidSink.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1));
        graph.addNode(voidSink);

        FlowGraph.ConnectionEdge edgeDemand = new FlowGraph.ConnectionEdge(junction.getId(), 0, normalWithDemand.getId(), 0, 0.0, 0, 1.0);
        FlowGraph.ConnectionEdge edgeUncapped = new FlowGraph.ConnectionEdge(junction.getId(), 0, normalUncapped.getId(), 0, 0.0, 0, 1.0);
        FlowGraph.ConnectionEdge edgeVoid = new FlowGraph.ConnectionEdge(junction.getId(), 0, voidSink.getId(), 0, 0.0, 0, 1.0);
        graph.addConnection(edgeDemand);
        graph.addConnection(edgeUncapped);
        graph.addConnection(edgeVoid);

        Map<FlowGraph.ConnectionEdge, Double> alloc = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 100.0);
        Assertions.assertTrue(Double.isFinite(alloc.get(edgeUncapped)), "Allocation must be finite, not Double.MAX_VALUE");
        Assertions.assertEquals(40.0, alloc.get(edgeDemand), 0.001);
        Assertions.assertEquals(60.0, alloc.get(edgeUncapped), 0.001);
        Assertions.assertEquals(0.0, alloc.get(edgeVoid), 0.001);
    }

    @Test
    @DisplayName("Regression: Simultaneous multi-edge capping in waterfilling algorithm")
    void testSimultaneousMultiEdgeWaterfilling() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.setJunctionSplitMode(FlowSplitMode.WEIGHTED);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1));
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "M1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 200));
        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "M2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 200));
        RecipeNode m3 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m3"), "M3", 20, 30, GTVoltageTier.LV);
        m3.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 200));
        graph.addNode(m1);
        graph.addNode(m2);
        graph.addNode(m3);

        FlowGraph.ConnectionEdge edge1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 10.0, 0, 1.0);
        FlowGraph.ConnectionEdge edge2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 12.0, 0, 1.0);
        FlowGraph.ConnectionEdge edge3 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m3.getId(), 0, 0.0, 0, 1.0);
        graph.addConnection(edge1);
        graph.addConnection(edge2);
        graph.addConnection(edge3);

        Map<FlowGraph.ConnectionEdge, Double> alloc = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 50.0);
        Assertions.assertEquals(10.0, alloc.get(edge1), 0.001);
        Assertions.assertEquals(12.0, alloc.get(edge2), 0.001);
        Assertions.assertEquals(28.0, alloc.get(edge3), 0.001);
    }

    @Test
    @DisplayName("Regression: Multi-stage cascaded junction pipeline with different weights")
    void testMultiStageJunctionPipeline() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.createReroute(0, 0);
        producer.setSupplyMode(SupplyMode.FIXED_RATE);
        producer.setExternalSupplyRate(100.0);
        producer.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1));
        graph.addNode(producer);

        RecipeNode j1 = RecipeNode.createReroute(0, 0);
        j1.setJunctionSplitMode(FlowSplitMode.WEIGHTED);
        j1.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1));
        graph.addNode(j1);

        RecipeNode j2 = RecipeNode.createReroute(0, 0);
        j2.setJunctionSplitMode(FlowSplitMode.WEIGHTED);
        j2.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1));
        graph.addNode(j2);

        RecipeNode mA = RecipeNode.create(ResourceLocation.tryParse("gtceu:mA"), "Machine A", 20, 30, GTVoltageTier.LV);
        mA.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100));
        RecipeNode mB = RecipeNode.create(ResourceLocation.tryParse("gtceu:mB"), "Machine B", 20, 30, GTVoltageTier.LV);
        mB.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100));
        RecipeNode mC = RecipeNode.create(ResourceLocation.tryParse("gtceu:mC"), "Machine C", 20, 30, GTVoltageTier.LV);
        mC.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100));
        graph.addNode(mA);
        graph.addNode(mB);
        graph.addNode(mC);

        FlowGraph.ConnectionEdge prodToJ1 = new FlowGraph.ConnectionEdge(producer.getId(), 0, j1.getId(), 0);
        FlowGraph.ConnectionEdge j1ToA = new FlowGraph.ConnectionEdge(j1.getId(), 0, mA.getId(), 0, 0.0, 0, 1.0);
        FlowGraph.ConnectionEdge j1ToJ2 = new FlowGraph.ConnectionEdge(j1.getId(), 0, j2.getId(), 0, 0.0, 0, 3.0);
        FlowGraph.ConnectionEdge j2ToB = new FlowGraph.ConnectionEdge(j2.getId(), 0, mB.getId(), 0, 0.0, 0, 2.0);
        FlowGraph.ConnectionEdge j2ToC = new FlowGraph.ConnectionEdge(j2.getId(), 0, mC.getId(), 0, 0.0, 0, 1.0);

        graph.addConnection(prodToJ1);
        graph.addConnection(j1ToA);
        graph.addConnection(j1ToJ2);
        graph.addConnection(j2ToB);
        graph.addConnection(j2ToC);

        double flowA = FlowEdgeAllocator.getEdgeAllocatedFlow(graph, j1ToA, null);
        double flowJ2 = FlowEdgeAllocator.getEdgeAllocatedFlow(graph, j1ToJ2, null);
        double flowB = FlowEdgeAllocator.getEdgeAllocatedFlow(graph, j2ToB, null);
        double flowC = FlowEdgeAllocator.getEdgeAllocatedFlow(graph, j2ToC, null);

        Assertions.assertEquals(25.0, flowA, 0.001);
        Assertions.assertEquals(75.0, flowJ2, 0.001);
        Assertions.assertEquals(50.0, flowB, 0.001);
        Assertions.assertEquals(25.0, flowC, 0.001);
    }

    @Test
    @DisplayName("Robustness: NaN, Infinity, and negative weights are sanitized to valid defaults")
    void testRobustnessAgainstNaNAndInfinityWeights() {
        FlowGraph.ConnectionEdge edgeNaN = new FlowGraph.ConnectionEdge("src", 0, "dst", 0, 0.0, 0, Double.NaN);
        Assertions.assertEquals(1.0, edgeNaN.weight(), 0.001);

        FlowGraph.ConnectionEdge edgeInf = new FlowGraph.ConnectionEdge("src", 0, "dst", 0, 0.0, 0, Double.POSITIVE_INFINITY);
        Assertions.assertEquals(1.0, edgeInf.weight(), 0.001);

        FlowGraph.ConnectionEdge edgeNeg = new FlowGraph.ConnectionEdge("src", 0, "dst", 0, 0.0, 0, -10.0);
        Assertions.assertEquals(1.0, edgeNeg.weight(), 0.001);

        FlowGraph.ConnectionEdge edgeZero = new FlowGraph.ConnectionEdge("src", 0, "dst", 0, 0.0, 0, 0.0);
        Assertions.assertEquals(0.0, edgeZero.weight(), 0.001);

        FlowGraph graph = new FlowGraph();
        graph.addConnection("src", 0, "dst", 0, 0.0, 0, Double.NaN);
        Assertions.assertEquals(1.0, graph.getConnections().get(0).weight(), 0.001);

        graph.setConnectionProperties("src", 0, "dst", 0, 0.0, 0, Double.POSITIVE_INFINITY);
        Assertions.assertEquals(1.0, graph.getConnections().get(0).weight(), 0.001);
    }
}
