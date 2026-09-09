package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.type.FlowSplitMode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SupplyMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class JunctionEqualAndPrioritySplitTest {

    @Test
    @DisplayName("RFC-041: Priority split cascades flow sequentially based on edge priority (User Scenario)")
    void testPrioritySplitCascadeSequential() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
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

        FlowGraph.ConnectionEdge edge1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 0.0, 2);
        FlowGraph.ConnectionEdge edge2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 0.0, 1);
        FlowGraph.ConnectionEdge edge3 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m3.getId(), 0, 0.0, 0);
        graph.addConnection(edge1);
        graph.addConnection(edge2);
        graph.addConnection(edge3);

        // Case 1: Inflow = 100/s -> M1 gets 60, M2 gets 40, M3 gets 0
        Map<FlowGraph.ConnectionEdge, Double> alloc100 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 100.0);
        Assertions.assertEquals(60.0, alloc100.get(edge1), 0.001);
        Assertions.assertEquals(40.0, alloc100.get(edge2), 0.001);
        Assertions.assertEquals(0.0, alloc100.get(edge3), 0.001);

        // Case 2: Inflow = 150/s -> M1 gets 60, M2 gets 60, M3 gets 30
        Map<FlowGraph.ConnectionEdge, Double> alloc150 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 150.0);
        Assertions.assertEquals(60.0, alloc150.get(edge1), 0.001);
        Assertions.assertEquals(60.0, alloc150.get(edge2), 0.001);
        Assertions.assertEquals(30.0, alloc150.get(edge3), 0.001);

        // Case 3: Inflow = 180/s -> M1 gets 60, M2 gets 60, M3 gets 60
        Map<FlowGraph.ConnectionEdge, Double> alloc180 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 180.0);
        Assertions.assertEquals(60.0, alloc180.get(edge1), 0.001);
        Assertions.assertEquals(60.0, alloc180.get(edge2), 0.001);
        Assertions.assertEquals(60.0, alloc180.get(edge3), 0.001);

        // Case 4: Inflow = 40/s -> M1 gets 40, M2 gets 0, M3 gets 0
        Map<FlowGraph.ConnectionEdge, Double> alloc40 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 40.0);
        Assertions.assertEquals(40.0, alloc40.get(edge1), 0.001);
        Assertions.assertEquals(0.0, alloc40.get(edge2), 0.001);
        Assertions.assertEquals(0.0, alloc40.get(edge3), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Priority split divides tier flow proportionally when edges share the same priority")
    void testPrioritySplitSamePriorityTies() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:gold_ingot"), "Gold", 1));
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "Machine 1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:gold_ingot"), "Gold", 60));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "Machine 2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:gold_ingot"), "Gold", 60));
        graph.addNode(m2);

        RecipeNode m3 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m3"), "Machine 3", 20, 30, GTVoltageTier.LV);
        m3.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:gold_ingot"), "Gold", 60));
        graph.addNode(m3);

        FlowGraph.ConnectionEdge edge1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 0.0, 2);
        FlowGraph.ConnectionEdge edge2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 0.0, 1);
        FlowGraph.ConnectionEdge edge3 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m3.getId(), 0, 0.0, 1);
        graph.addConnection(edge1);
        graph.addConnection(edge2);
        graph.addConnection(edge3);

        Map<FlowGraph.ConnectionEdge, Double> alloc = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 120.0);
        Assertions.assertEquals(60.0, alloc.get(edge1), 0.001);
        Assertions.assertEquals(30.0, alloc.get(edge2), 0.001);
        Assertions.assertEquals(30.0, alloc.get(edge3), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Priority split directs leftover surplus to Void Sink")
    void testPrioritySplitSurplusWithVoidSink() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 1));
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "M1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 60));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "M2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 60));
        graph.addNode(m2);

        RecipeNode voidSink = RecipeNode.createReroute(0, 0);
        voidSink.setSupplyMode(SupplyMode.VOID_SINK);
        graph.addNode(voidSink);

        FlowGraph.ConnectionEdge edge1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 0.0, 2);
        FlowGraph.ConnectionEdge edge2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 0.0, 1);
        FlowGraph.ConnectionEdge edgeVoid = new FlowGraph.ConnectionEdge(junction.getId(), 0, voidSink.getId(), 0, 0.0, 0);
        graph.addConnection(edge1);
        graph.addConnection(edge2);
        graph.addConnection(edgeVoid);

        Map<FlowGraph.ConnectionEdge, Double> alloc = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 150.0);
        Assertions.assertEquals(60.0, alloc.get(edge1), 0.001);
        Assertions.assertEquals(60.0, alloc.get(edge2), 0.001);
        Assertions.assertEquals(30.0, alloc.get(edgeVoid), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Priority split assigns full flow to highest priority when downstream demand is zero")
    void testPrioritySplitZeroDemandDownstream() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        graph.addNode(junction);

        RecipeNode r1 = RecipeNode.createReroute(0, 0);
        graph.addNode(r1);
        RecipeNode r2 = RecipeNode.createReroute(0, 0);
        graph.addNode(r2);

        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, r1.getId(), 0, 0.0, 10);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, r2.getId(), 0, 0.0, 5);
        graph.addConnection(e1);
        graph.addConnection(e2);

        Map<FlowGraph.ConnectionEdge, Double> alloc = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 50.0);
        Assertions.assertEquals(50.0, alloc.get(e1), 0.001);
        Assertions.assertEquals(0.0, alloc.get(e2), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Equal split divides available flow evenly across active outgoing lines")
    void testEqualSplitUncapped() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 1));
        junction.setJunctionSplitMode(FlowSplitMode.EQUAL);
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "M1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 60));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "M2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 60));
        graph.addNode(m2);

        RecipeNode m3 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m3"), "M3", 20, 30, GTVoltageTier.LV);
        m3.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 60));
        graph.addNode(m3);

        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0);
        FlowGraph.ConnectionEdge e3 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m3.getId(), 0);
        graph.addConnection(e1);
        graph.addConnection(e2);
        graph.addConnection(e3);

        Map<FlowGraph.ConnectionEdge, Double> alloc100 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 100.0);
        Assertions.assertEquals(100.0 / 3.0, alloc100.get(e1), 0.001);
        Assertions.assertEquals(100.0 / 3.0, alloc100.get(e2), 0.001);
        Assertions.assertEquals(100.0 / 3.0, alloc100.get(e3), 0.001);

        Map<FlowGraph.ConnectionEdge, Double> alloc150 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 150.0);
        Assertions.assertEquals(50.0, alloc150.get(e1), 0.001);
        Assertions.assertEquals(50.0, alloc150.get(e2), 0.001);
        Assertions.assertEquals(50.0, alloc150.get(e3), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Equal split routes excess surplus to Void Sink after demands are met")
    void testEqualSplitSurplusWithVoidSink() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 1));
        junction.setJunctionSplitMode(FlowSplitMode.EQUAL);
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "M1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 50));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "M2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 50));
        graph.addNode(m2);

        RecipeNode voidSink = RecipeNode.createReroute(0, 0);
        voidSink.setSupplyMode(SupplyMode.VOID_SINK);
        graph.addNode(voidSink);

        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0);
        FlowGraph.ConnectionEdge eVoid = new FlowGraph.ConnectionEdge(junction.getId(), 0, voidSink.getId(), 0);
        graph.addConnection(e1);
        graph.addConnection(e2);
        graph.addConnection(eVoid);

        Map<FlowGraph.ConnectionEdge, Double> alloc = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 150.0);
        Assertions.assertEquals(50.0, alloc.get(e1), 0.001);
        Assertions.assertEquals(50.0, alloc.get(e2), 0.001);
        Assertions.assertEquals(50.0, alloc.get(eVoid), 0.001);
    }

    @Test
    @DisplayName("RFC-041: ConnectionEdge NBT serialization preserves priority and fixed limit")
    void testConnectionEdgeSerializationWithPriority() {
        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge("src", 0, "dst", 1, 25.5, 7);
        CompoundTag tag = edge.serializeNBT();
        FlowGraph.ConnectionEdge deserialized = FlowGraph.ConnectionEdge.deserializeNBT(tag);

        Assertions.assertEquals("src", deserialized.fromNodeId());
        Assertions.assertEquals(0, deserialized.outputIndex());
        Assertions.assertEquals("dst", deserialized.toNodeId());
        Assertions.assertEquals(1, deserialized.inputIndex());
        Assertions.assertEquals(25.5, deserialized.fixedFlowLimit(), 0.001);
        Assertions.assertEquals(7, deserialized.priority());
    }

    @Test
    @DisplayName("RFC-041: FlowGraph setConnectionPriority modifies priority dynamically")
    void testFlowGraphSetConnectionPriority() {
        FlowGraph graph = new FlowGraph();
        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge("junc", 0, "mach", 0, 0.0, 0);
        graph.addConnection(edge);

        Assertions.assertEquals(0, graph.getConnections().get(0).priority());
        boolean updated = graph.setConnectionPriority("junc", 0, "mach", 0, 5);
        Assertions.assertTrue(updated);
        Assertions.assertEquals(5, graph.getConnections().get(0).priority());
    }

    @Test
    @DisplayName("RFC-041: RecipeNode property serialization preserves FlowSplitMode")
    void testJunctionSplitModeSerialization() {
        RecipeNode node = RecipeNode.createReroute(0, 0);
        Assertions.assertEquals(FlowSplitMode.PROPORTIONAL, node.getJunctionSplitMode());

        node.setJunctionSplitMode(FlowSplitMode.EQUAL);
        Assertions.assertEquals(FlowSplitMode.EQUAL, node.getJunctionSplitMode());

        CompoundTag tag = node.getProperties().serializeNBT();
        RecipeNode restored = RecipeNode.createReroute(0, 0);
        restored.getProperties().deserializeNBT(tag);
        Assertions.assertEquals(FlowSplitMode.EQUAL, restored.getJunctionSplitMode());
    }

    @Test
    @DisplayName("RFC-041: Equal split divides flow equally across lines without demand jump discontinuity")
    void testEqualSplitUnequalDemands() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 1));
        junction.setJunctionSplitMode(FlowSplitMode.EQUAL);
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "M1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 20));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "M2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 80));
        graph.addNode(m2);

        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0);
        graph.addConnection(e1);
        graph.addConnection(e2);

        // Inflow 80 -> 40.0 each
        Map<FlowGraph.ConnectionEdge, Double> alloc80 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 80.0);
        Assertions.assertEquals(40.0, alloc80.get(e1), 0.001);
        Assertions.assertEquals(40.0, alloc80.get(e2), 0.001);

        // Inflow 100 -> 50.0 each (no sudden drop for M1!)
        Map<FlowGraph.ConnectionEdge, Double> alloc100 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 100.0);
        Assertions.assertEquals(50.0, alloc100.get(e1), 0.001);
        Assertions.assertEquals(50.0, alloc100.get(e2), 0.001);

        // Inflow 110 -> 55.0 each
        Map<FlowGraph.ConnectionEdge, Double> alloc110 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 110.0);
        Assertions.assertEquals(55.0, alloc110.get(e1), 0.001);
        Assertions.assertEquals(55.0, alloc110.get(e2), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Equal split with Void Sink absorbs surplus after unequal demands are satisfied")
    void testEqualSplitUnequalDemandsWithVoidSink() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 1));
        junction.setJunctionSplitMode(FlowSplitMode.EQUAL);
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "M1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 20));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "M2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 80));
        graph.addNode(m2);

        RecipeNode voidSink = RecipeNode.createReroute(0, 0);
        voidSink.setSupplyMode(SupplyMode.VOID_SINK);
        graph.addNode(voidSink);

        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0);
        FlowGraph.ConnectionEdge eVoid = new FlowGraph.ConnectionEdge(junction.getId(), 0, voidSink.getId(), 0);
        graph.addConnection(e1);
        graph.addConnection(e2);
        graph.addConnection(eVoid);

        // Inflow 30 -> 15 each, void 0
        Map<FlowGraph.ConnectionEdge, Double> alloc30 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 30.0);
        Assertions.assertEquals(15.0, alloc30.get(e1), 0.001);
        Assertions.assertEquals(15.0, alloc30.get(e2), 0.001);
        Assertions.assertEquals(0.0, alloc30.get(eVoid), 0.001);

        // Inflow 60 -> M1 satisfied at 20, M2 gets remaining 40, void 0
        Map<FlowGraph.ConnectionEdge, Double> alloc60 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 60.0);
        Assertions.assertEquals(20.0, alloc60.get(e1), 0.001);
        Assertions.assertEquals(40.0, alloc60.get(e2), 0.001);
        Assertions.assertEquals(0.0, alloc60.get(eVoid), 0.001);

        // Inflow 100 -> M1 gets 20, M2 gets 80, void 0
        Map<FlowGraph.ConnectionEdge, Double> alloc100 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 100.0);
        Assertions.assertEquals(20.0, alloc100.get(e1), 0.001);
        Assertions.assertEquals(80.0, alloc100.get(e2), 0.001);
        Assertions.assertEquals(0.0, alloc100.get(eVoid), 0.001);

        // Inflow 140 -> M1 gets 20, M2 gets 80, void absorbs surplus 40
        Map<FlowGraph.ConnectionEdge, Double> alloc140 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 140.0);
        Assertions.assertEquals(20.0, alloc140.get(e1), 0.001);
        Assertions.assertEquals(80.0, alloc140.get(e2), 0.001);
        Assertions.assertEquals(40.0, alloc140.get(eVoid), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Priority split honors priority over fixed limits (lower priority cannot steal flow)")
    void testPrioritySplitFixedLimitDoesNotStealFromHigherPriority() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 1));
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "M1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 50));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "M2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 50));
        graph.addNode(m2);

        // Lower priority edge M2 has a fixed limit, higher priority M1 has no limit
        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 0.0, 2);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 40.0, 1);
        graph.addConnection(e2); // Insert e2 first to test insertion order independence
        graph.addConnection(e1);

        Map<FlowGraph.ConnectionEdge, Double> alloc = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 40.0);
        Assertions.assertEquals(40.0, alloc.get(e1), 0.001, "Higher priority line must receive flow first despite lower priority having fixed limit");
        Assertions.assertEquals(0.0, alloc.get(e2), 0.001, "Lower priority line must not receive flow until higher priority is satisfied");
    }

    @Test
    @DisplayName("RFC-041: Priority split with Cap restricts line capacity and cascades remainder")
    void testPrioritySplitWithCapAndCascade() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 1));
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "M1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 60));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "M2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 60));
        graph.addNode(m2);

        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 30.0, 2);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 0.0, 1);
        graph.addConnection(e1);
        graph.addConnection(e2);

        // Inflow 50 -> M1 capped at 30, M2 receives remaining 20
        Map<FlowGraph.ConnectionEdge, Double> alloc = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 50.0);
        Assertions.assertEquals(30.0, alloc.get(e1), 0.001);
        Assertions.assertEquals(20.0, alloc.get(e2), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Priority split surplus does not exceed fixed cap limits")
    void testPrioritySplitSurplusDoesNotExceedCap() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 1));
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "M1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 40));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "M2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 40));
        graph.addNode(m2);

        // M1 has cap 40. M2 is uncapped.
        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 40.0, 2);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 0.0, 1);
        graph.addConnection(e1);
        graph.addConnection(e2);

        // Inflow 100 -> M1 gets 40 (cap enforced), uncapped M2 absorbs 20 surplus -> 60
        Map<FlowGraph.ConnectionEdge, Double> alloc = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 100.0);
        Assertions.assertEquals(40.0, alloc.get(e1), 0.001);
        Assertions.assertEquals(60.0, alloc.get(e2), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Equal split with fixed limits caps lines and redistributes flow fairly")
    void testEqualSplitWithFixedLimits() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 1));
        junction.setJunctionSplitMode(FlowSplitMode.EQUAL);
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "M1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 100));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "M2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper", 100));
        graph.addNode(m2);

        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 30.0, 0);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 0.0, 0);
        graph.addConnection(e1);
        graph.addConnection(e2);

        // Inflow 40 -> 20 each (20 <= 30)
        Map<FlowGraph.ConnectionEdge, Double> alloc40 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 40.0);
        Assertions.assertEquals(20.0, alloc40.get(e1), 0.001);
        Assertions.assertEquals(20.0, alloc40.get(e2), 0.001);

        // Inflow 80 -> M1 capped at 30, M2 receives 50
        Map<FlowGraph.ConnectionEdge, Double> alloc80 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 80.0);
        Assertions.assertEquals(30.0, alloc80.get(e1), 0.001);
        Assertions.assertEquals(50.0, alloc80.get(e2), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Regular machine outputs with custom priorities automatically activate priority splitting")
    void testRegularMachineOutputPrioritySplit() {
        FlowGraph graph = new FlowGraph();

        RecipeNode reactor = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Reactor", 20, 30, GTVoltageTier.MV);
        reactor.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrobenzene"), "Nitrobenzene", 100));
        graph.addNode(reactor);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "Engine 1", 20, 30, GTVoltageTier.MV);
        m1.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrobenzene"), "Nitrobenzene", 60));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "Engine 2", 20, 30, GTVoltageTier.MV);
        m2.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrobenzene"), "Nitrobenzene", 60));
        graph.addNode(m2);

        RecipeNode m3 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m3"), "Engine 3", 20, 30, GTVoltageTier.MV);
        m3.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrobenzene"), "Nitrobenzene", 60));
        graph.addNode(m3);

        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(reactor.getId(), 0, m1.getId(), 0, 0.0, 2);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(reactor.getId(), 0, m2.getId(), 0, 0.0, 1);
        FlowGraph.ConnectionEdge e3 = new FlowGraph.ConnectionEdge(reactor.getId(), 0, m3.getId(), 0, 0.0, 0);
        graph.addConnection(e1);
        graph.addConnection(e2);
        graph.addConnection(e3);

        Map<FlowGraph.ConnectionEdge, Double> alloc100 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, reactor, 0, 100.0);
        Assertions.assertEquals(60.0, alloc100.get(e1), 0.001);
        Assertions.assertEquals(40.0, alloc100.get(e2), 0.001);
        Assertions.assertEquals(0.0, alloc100.get(e3), 0.001);

        Map<FlowGraph.ConnectionEdge, Double> alloc150 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, reactor, 0, 150.0);
        Assertions.assertEquals(60.0, alloc150.get(e1), 0.001);
        Assertions.assertEquals(60.0, alloc150.get(e2), 0.001);
        Assertions.assertEquals(30.0, alloc150.get(e3), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Edge priority clamping and dynamic update preserves priority bounds")
    void testPriorityWheelScrollStep() {
        FlowGraph graph = new FlowGraph();
        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge("src", 0, "dst", 0, 0.0, 0);
        graph.addConnection(edge);

        int pri = edge.priority();
        int scrolledUp = Math.max(0, Math.min(99, pri + 1));
        graph.setConnectionPriority("src", 0, "dst", 0, scrolledUp);
        Assertions.assertEquals(1, graph.getConnections().get(0).priority());

        int scrolledDownBelowZero = Math.max(0, Math.min(99, 0 - 1));
        Assertions.assertEquals(0, scrolledDownBelowZero);
    }

    @Test
    @DisplayName("RFC-041: Junction in EQUAL split mode with priority tiers satisfies higher tier first and splits equal within tier")
    void testEqualSplitWithPriorityTiers() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1));
        junction.setJunctionSplitMode(FlowSplitMode.EQUAL);
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "M1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 60));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "M2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 60));
        graph.addNode(m2);

        RecipeNode m3 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m3"), "M3", 20, 30, GTVoltageTier.LV);
        m3.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 60));
        graph.addNode(m3);

        RecipeNode m4 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m4"), "M4", 20, 30, GTVoltageTier.LV);
        m4.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 60));
        graph.addNode(m4);

        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 0.0, 2);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 0.0, 1);
        FlowGraph.ConnectionEdge e3 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m3.getId(), 0, 0.0, 1);
        FlowGraph.ConnectionEdge e4 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m4.getId(), 0, 0.0, 0);
        graph.addConnection(e1);
        graph.addConnection(e2);
        graph.addConnection(e3);
        graph.addConnection(e4);

        // Inflow 120 -> M1 (pri 2) gets 60. Remaining 60 divided equally between M2 (pri 1) and M3 (pri 1) -> 30 each. M4 gets 0.
        Map<FlowGraph.ConnectionEdge, Double> alloc120 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 120.0);
        Assertions.assertEquals(60.0, alloc120.get(e1), 0.001);
        Assertions.assertEquals(30.0, alloc120.get(e2), 0.001);
        Assertions.assertEquals(30.0, alloc120.get(e3), 0.001);
        Assertions.assertEquals(0.0, alloc120.get(e4), 0.001);

        // Inflow 160 -> M1 gets 60. Remaining 100 divided equally between M2 and M3 -> 50 each. M4 gets 0.
        Map<FlowGraph.ConnectionEdge, Double> alloc160 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 160.0);
        Assertions.assertEquals(60.0, alloc160.get(e1), 0.001);
        Assertions.assertEquals(50.0, alloc160.get(e2), 0.001);
        Assertions.assertEquals(50.0, alloc160.get(e3), 0.001);
        Assertions.assertEquals(0.0, alloc160.get(e4), 0.001);

        // Inflow 200 -> M1 gets 60. M2 and M3 fully satisfied at 60 each (120 total). Remaining 20 cascades to M4 (pri 0) -> 20.
        Map<FlowGraph.ConnectionEdge, Double> alloc200 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 200.0);
        Assertions.assertEquals(60.0, alloc200.get(e1), 0.001);
        Assertions.assertEquals(60.0, alloc200.get(e2), 0.001);
        Assertions.assertEquals(60.0, alloc200.get(e3), 0.001);
        Assertions.assertEquals(20.0, alloc200.get(e4), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Junction in EQUAL split mode with unequal demands within a priority tier")
    void testEqualSplitWithPriorityTiersUnequalDemands() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1));
        junction.setJunctionSplitMode(FlowSplitMode.EQUAL);
        graph.addNode(junction);

        RecipeNode m1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m1"), "M1", 20, 30, GTVoltageTier.LV);
        m1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 60));
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m2"), "M2", 20, 30, GTVoltageTier.LV);
        m2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 20));
        graph.addNode(m2);

        RecipeNode m3 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m3"), "M3", 20, 30, GTVoltageTier.LV);
        m3.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 80));
        graph.addNode(m3);

        RecipeNode m4 = RecipeNode.create(ResourceLocation.tryParse("gtceu:m4"), "M4", 20, 30, GTVoltageTier.LV);
        m4.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 60));
        graph.addNode(m4);

        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m1.getId(), 0, 0.0, 2);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m2.getId(), 0, 0.0, 1);
        FlowGraph.ConnectionEdge e3 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m3.getId(), 0, 0.0, 1);
        FlowGraph.ConnectionEdge e4 = new FlowGraph.ConnectionEdge(junction.getId(), 0, m4.getId(), 0, 0.0, 0);
        graph.addConnection(e1);
        graph.addConnection(e2);
        graph.addConnection(e3);
        graph.addConnection(e4);

        // Inflow 120 -> M1 gets 60. Remaining 60 to Tier 1 (M2 demand 20, M3 demand 80).
        // Equal split with caps: 60/2 = 30 > 20 -> M2 capped at 20. Remaining 40 to M3. M4 gets 0.
        Map<FlowGraph.ConnectionEdge, Double> alloc120 = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 120.0);
        Assertions.assertEquals(60.0, alloc120.get(e1), 0.001);
        Assertions.assertEquals(20.0, alloc120.get(e2), 0.001);
        Assertions.assertEquals(40.0, alloc120.get(e3), 0.001);
        Assertions.assertEquals(0.0, alloc120.get(e4), 0.001);
    }

    @Test
    @DisplayName("RFC-041: Void Sink absorbs all flow when normal priority edges have zero demand")
    void testPrioritySplitZeroDemandWithVoidSinkAbsorbsAll() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1));
        graph.addNode(junction);

        RecipeNode idleMachine = RecipeNode.create(ResourceLocation.tryParse("gtceu:idle"), "Idle", 20, 30, GTVoltageTier.LV);
        graph.addNode(idleMachine);

        RecipeNode voidSink = RecipeNode.createReroute(0, 0);
        voidSink.setSupplyMode(SupplyMode.VOID_SINK);
        graph.addNode(voidSink);

        FlowGraph.ConnectionEdge edgeIdle = new FlowGraph.ConnectionEdge(junction.getId(), 0, idleMachine.getId(), 0, 0.0, 5);
        FlowGraph.ConnectionEdge edgeVoid = new FlowGraph.ConnectionEdge(junction.getId(), 0, voidSink.getId(), 0, 0.0, 0);
        graph.addConnection(edgeIdle);
        graph.addConnection(edgeVoid);

        Map<FlowGraph.ConnectionEdge, Double> alloc = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 100.0);
        Assertions.assertEquals(0.0, alloc.get(edgeIdle), 0.001, "Idle machine with 0 demand must not receive flow when void sink is present");
        Assertions.assertEquals(100.0, alloc.get(edgeVoid), 0.001, "Void sink must absorb 100% of flow when normal edges have zero demand");
    }

    @Test
    @DisplayName("RFC-041: Priority clamping guarantees priority remains in valid [0, 99] range")
    void testPriorityClampingBounds() {
        FlowGraph graph = new FlowGraph();
        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge("src", 0, "dst", 0, 0.0, 0);
        graph.addConnection(edge);

        FlowGraph.ConnectionEdge clampedNegative = edge.withPriority(-10);
        Assertions.assertEquals(0, clampedNegative.priority());

        FlowGraph.ConnectionEdge clampedHigh = edge.withPriority(250);
        Assertions.assertEquals(99, clampedHigh.priority());

        graph.setConnectionPriority("src", 0, "dst", 0, -5);
        Assertions.assertEquals(0, graph.getConnections().get(0).priority());

        graph.setConnectionPriority("src", 0, "dst", 0, 150);
        Assertions.assertEquals(99, graph.getConnections().get(0).priority());

        CompoundTag tag = new CompoundTag();
        tag.putString("fromNode", "src");
        tag.putInt("outIdx", 0);
        tag.putString("toNode", "dst");
        tag.putInt("inIdx", 0);
        tag.putInt("priority", -20);
        FlowGraph.ConnectionEdge deserializedNegative = FlowGraph.ConnectionEdge.deserializeNBT(tag);
        Assertions.assertEquals(0, deserializedNegative.priority());

        tag.putInt("priority", 500);
        FlowGraph.ConnectionEdge deserializedHigh = FlowGraph.ConnectionEdge.deserializeNBT(tag);
        Assertions.assertEquals(99, deserializedHigh.priority());
    }
}

