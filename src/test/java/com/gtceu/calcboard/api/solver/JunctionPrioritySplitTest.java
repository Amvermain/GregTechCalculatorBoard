package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Verifies RFC-020 Section 3.2.3: Junction Node Fixed Priority Flow Split.
 */
public class JunctionPrioritySplitTest {

    @Test
    @DisplayName("RFC-020: 2-Stage Allocation assigns fixed limit first, then distributes remainder proportionally")
    void testTwoStageFixedPrioritySplit() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Junction", 20, 0, GTVoltageTier.LV);
        junction.setReroute(true);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1));
        junction.setExternalSupplyRate(100.0);
        graph.addNode(junction);

        RecipeNode consumerA = RecipeNode.create(ResourceLocation.tryParse("gtceu:machine_a"), "Machine A", 20, 30, GTVoltageTier.LV);
        consumerA.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 50));
        graph.addNode(consumerA);

        RecipeNode consumerB = RecipeNode.create(ResourceLocation.tryParse("gtceu:machine_b"), "Machine B", 20, 30, GTVoltageTier.LV);
        consumerB.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 50));
        graph.addNode(consumerB);

        FlowGraph.ConnectionEdge edgeA = new FlowGraph.ConnectionEdge(junction.getId(), 0, consumerA.getId(), 0, 30.0);
        FlowGraph.ConnectionEdge edgeB = new FlowGraph.ConnectionEdge(junction.getId(), 0, consumerB.getId(), 0, 0.0);
        graph.addConnection(edgeA);
        graph.addConnection(edgeB);

        Map<FlowGraph.ConnectionEdge, Double> allocations = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 100.0);
        Assertions.assertEquals(30.0, allocations.get(edgeA), 0.001, "Priority fixed edge A should receive exactly 30.0");
        Assertions.assertEquals(70.0, allocations.get(edgeB), 0.001, "Variable edge B should receive full remaining 70.0");
    }

    @Test
    @DisplayName("RFC-020: Multi-branch fixed split distributes remainder weighted by consumer demand")
    void testMultiBranchWeightedRemainder() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Junction", 20, 0, GTVoltageTier.LV);
        junction.setReroute(true);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 1));
        junction.setExternalSupplyRate(100.0);
        graph.addNode(junction);

        RecipeNode consumer1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:machine_1"), "M1", 20, 30, GTVoltageTier.LV);
        consumer1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 40));
        graph.addNode(consumer1);

        RecipeNode consumer2 = RecipeNode.create(ResourceLocation.tryParse("gtceu:machine_2"), "M2", 20, 30, GTVoltageTier.LV);
        consumer2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 60));
        graph.addNode(consumer2);

        RecipeNode consumer3 = RecipeNode.create(ResourceLocation.tryParse("gtceu:machine_3"), "M3", 20, 30, GTVoltageTier.LV);
        consumer3.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 30));
        graph.addNode(consumer3);

        FlowGraph.ConnectionEdge edge1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, consumer1.getId(), 0, 20.0);
        FlowGraph.ConnectionEdge edge2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, consumer2.getId(), 0, 0.0);
        FlowGraph.ConnectionEdge edge3 = new FlowGraph.ConnectionEdge(junction.getId(), 0, consumer3.getId(), 0, 0.0);
        graph.addConnection(edge1);
        graph.addConnection(edge2);
        graph.addConnection(edge3);

        Map<FlowGraph.ConnectionEdge, Double> allocations = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 100.0);
        Assertions.assertEquals(20.0, allocations.get(edge1), 0.001);

        // Remaining 80.0 is divided 2:1 between M2 (60) and M3 (30)
        Assertions.assertEquals(80.0 * (60.0 / 90.0), allocations.get(edge2), 0.001);
        Assertions.assertEquals(80.0 * (30.0 / 90.0), allocations.get(edge3), 0.001);
    }

    @Test
    @DisplayName("RFC-020: Insufficient inflow satisfies priority fixed lines in sequence until exhausted")
    void testInsufficientInflowSequentialExhaustion() {
        FlowGraph graph = new FlowGraph();

        RecipeNode junction = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Junction", 20, 0, GTVoltageTier.LV);
        junction.setReroute(true);
        junction.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:gold_ingot"), "Gold", 1));
        graph.addNode(junction);

        RecipeNode cA = RecipeNode.create(ResourceLocation.tryParse("gtceu:a"), "A", 20, 30, GTVoltageTier.LV);
        cA.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:gold_ingot"), "Gold", 50));
        graph.addNode(cA);

        RecipeNode cB = RecipeNode.create(ResourceLocation.tryParse("gtceu:b"), "B", 20, 30, GTVoltageTier.LV);
        cB.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:gold_ingot"), "Gold", 50));
        graph.addNode(cB);

        RecipeNode cC = RecipeNode.create(ResourceLocation.tryParse("gtceu:c"), "C", 20, 30, GTVoltageTier.LV);
        cC.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:gold_ingot"), "Gold", 50));
        graph.addNode(cC);

        FlowGraph.ConnectionEdge e1 = new FlowGraph.ConnectionEdge(junction.getId(), 0, cA.getId(), 0, 30.0);
        FlowGraph.ConnectionEdge e2 = new FlowGraph.ConnectionEdge(junction.getId(), 0, cB.getId(), 0, 30.0);
        FlowGraph.ConnectionEdge e3 = new FlowGraph.ConnectionEdge(junction.getId(), 0, cC.getId(), 0, 0.0);
        graph.addConnection(e1);
        graph.addConnection(e2);
        graph.addConnection(e3);

        Map<FlowGraph.ConnectionEdge, Double> allocations = FlowBalanceMatrixSolver.calculateOutgoingEdgeAllocations(graph, junction, 0, 45.0);
        Assertions.assertEquals(30.0, allocations.get(e1), 0.001);
        Assertions.assertEquals(15.0, allocations.get(e2), 0.001);
        Assertions.assertEquals(0.0, allocations.get(e3), 0.001);
    }

    @Test
    @DisplayName("RFC-020: FlowGraph setConnectionFixedLimit dynamically modifies edge limit")
    void testSetConnectionFixedLimit() {
        FlowGraph graph = new FlowGraph();
        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge("j1", 0, "m1", 0);
        graph.addConnection(edge);
        Assertions.assertFalse(graph.getConnections().get(0).hasFixedLimit());

        boolean updated = graph.setConnectionFixedLimit("j1", 0, "m1", 0, 42.5);
        Assertions.assertTrue(updated);
        Assertions.assertTrue(graph.getConnections().get(0).hasFixedLimit());
        Assertions.assertEquals(42.5, graph.getConnections().get(0).fixedFlowLimit(), 0.001);
    }

    @Test
    @DisplayName("RFC-020: ConnectionEdge NBT serialization preserves fixedFlowLimit")
    void testConnectionEdgeSerialization() {
        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge("src", 1, "dst", 2, 75.0);
        CompoundTag tag = edge.serializeNBT();
        FlowGraph.ConnectionEdge deserialized = FlowGraph.ConnectionEdge.deserializeNBT(tag);

        Assertions.assertEquals("src", deserialized.fromNodeId());
        Assertions.assertEquals(1, deserialized.outputIndex());
        Assertions.assertEquals("dst", deserialized.toNodeId());
        Assertions.assertEquals(2, deserialized.inputIndex());
        Assertions.assertTrue(deserialized.hasFixedLimit());
        Assertions.assertEquals(75.0, deserialized.fixedFlowLimit(), 0.001);
    }

    @Test
    @DisplayName("RFC-020: Diamond converging flow evaluates correctly without false cycle deficit")
    void testDiamondConvergentFlowWithoutFalseDeficit() {
        FlowGraph graph = new FlowGraph();

        RecipeNode root = RecipeNode.create(ResourceLocation.tryParse("gtceu:root"), "Root", 20, 30, GTVoltageTier.LV);
        root.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 100));
        graph.addNode(root);

        RecipeNode juncA = RecipeNode.create(ResourceLocation.tryParse("gtceu:junc_a"), "Junc A", 20, 0, GTVoltageTier.LV);
        juncA.setReroute(true);
        juncA.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1));
        graph.addNode(juncA);

        RecipeNode juncB = RecipeNode.create(ResourceLocation.tryParse("gtceu:junc_b"), "Junc B", 20, 0, GTVoltageTier.LV);
        juncB.setReroute(true);
        juncB.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1));
        graph.addNode(juncB);

        RecipeNode juncMerge = RecipeNode.create(ResourceLocation.tryParse("gtceu:junc_merge"), "Junc Merge", 20, 0, GTVoltageTier.LV);
        juncMerge.setReroute(true);
        juncMerge.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1));
        graph.addNode(juncMerge);

        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:consumer"), "Consumer", 20, 30, GTVoltageTier.LV);
        consumer.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 100));
        graph.addNode(consumer);

        graph.addConnection(new FlowGraph.ConnectionEdge(root.getId(), 0, juncA.getId(), 0, 40.0));
        graph.addConnection(new FlowGraph.ConnectionEdge(root.getId(), 0, juncB.getId(), 0, 0.0));
        graph.addConnection(new FlowGraph.ConnectionEdge(juncA.getId(), 0, juncMerge.getId(), 0));
        graph.addConnection(new FlowGraph.ConnectionEdge(juncB.getId(), 0, juncMerge.getId(), 0));
        graph.addConnection(new FlowGraph.ConnectionEdge(juncMerge.getId(), 0, consumer.getId(), 0));

        Map<String, Double> effMap = FlowBalanceMatrixSolver.computeNodeEfficiencies(graph);
        Assertions.assertEquals(1.0, effMap.get(consumer.getId()), 0.001, "Consumer should operate at 100% efficiency in diamond converging flow");
    }

    @Test
    @DisplayName("RFC-020: Unbound reroute node routes flow correctly even with empty outputs list")
    void testUnboundRerouteNodePassThrough() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:producer"), "Producer", 20, 30, GTVoltageTier.LV);
        producer.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 50));
        graph.addNode(producer);

        RecipeNode reroute = RecipeNode.create(ResourceLocation.tryParse("gtceu:reroute"), "Reroute", 20, 0, GTVoltageTier.LV);
        reroute.setReroute(true);
        // Do not bind any ingredient: outputs is empty
        Assertions.assertTrue(reroute.getOutputs().isEmpty());
        graph.addNode(reroute);

        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:consumer"), "Consumer", 20, 30, GTVoltageTier.LV);
        consumer.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 50));
        graph.addNode(consumer);

        graph.addConnection(new FlowGraph.ConnectionEdge(producer.getId(), 0, reroute.getId(), 0));
        graph.addConnection(new FlowGraph.ConnectionEdge(reroute.getId(), 0, consumer.getId(), 0));

        Map<String, Double> effMap = FlowBalanceMatrixSolver.computeNodeEfficiencies(graph);
        Assertions.assertEquals(1.0, effMap.get(consumer.getId()), 0.001, "Consumer should operate at 100% efficiency through unbound reroute node");
    }
}


