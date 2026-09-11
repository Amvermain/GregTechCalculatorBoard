package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.storage.RecipeNodeSerializer;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Verifies RFC-020 Section 3.2.4: Junction Batch Accumulator Buffer Formulation.
 */
public class JunctionBatchBufferTest {

    @Test
    @DisplayName("RFC-020: RecipeNode correctly sets and gets buffer properties")
    void testBufferPropertiesAccess() {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Junction", 20, 0, GTVoltageTier.LV);
        node.setReroute(true);

        Assertions.assertFalse(node.isJunctionBuffer());
        Assertions.assertEquals(0.0, node.getJunctionBufferSize(), 0.001);

        node.setJunctionBuffer(true);
        node.setJunctionBufferSize(500.0);

        Assertions.assertTrue(node.isJunctionBuffer());
        Assertions.assertEquals(500.0, node.getJunctionBufferSize(), 0.001);
    }

    @Test
    @DisplayName("RFC-020: Junction charge duration accurately computes T_charge = B / Q_in")
    void testJunctionChargeDurationCalculation() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:continuous_producer"), "Producer", 20, 30, GTVoltageTier.LV);
        producer.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 200));
        graph.addNode(producer);

        RecipeNode junction = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Junction Buffer", 20, 0, GTVoltageTier.LV);
        junction.setReroute(true);
        junction.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 1));
        junction.setJunctionBuffer(true);
        junction.setJunctionBufferSize(500.0);
        graph.addNode(junction);

        graph.addConnection(new FlowGraph.ConnectionEdge(producer.getId(), 0, junction.getId(), 0));

        // Inflow = 200 items/s, Buffer = 500 items -> T_charge = 500 / 200 = 2.5s
        double chargeDuration = junction.getJunctionChargeDuration(graph);
        Assertions.assertEquals(2.5, chargeDuration, 0.001);
    }

    @Test
    @DisplayName("RFC-020: Downstream batch consumer efficiency matches mass conservation eta = min(1.0, Q_in / Q_demand)")
    void testDownstreamBatchEfficiencyResolution() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:continuous_producer"), "Producer", 20, 30, GTVoltageTier.LV);
        producer.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 200));
        graph.addNode(producer);

        RecipeNode junction = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Junction Buffer", 20, 0, GTVoltageTier.LV);
        junction.setReroute(true);
        junction.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 1));
        junction.setJunctionBuffer(true);
        junction.setJunctionBufferSize(500.0);
        graph.addNode(junction);

        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:batch_consumer"), "Batch Consumer", 20, 30, GTVoltageTier.LV);
        consumer.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 200));
        graph.addNode(consumer);

        graph.addConnection(new FlowGraph.ConnectionEdge(producer.getId(), 0, junction.getId(), 0));
        graph.addConnection(new FlowGraph.ConnectionEdge(junction.getId(), 0, consumer.getId(), 0));

        Map<String, Double> effMap = FlowBalanceMatrixSolver.computeNodeEfficiencies(graph);
        Assertions.assertEquals(1.0, effMap.get(consumer.getId()), 0.001, "Downstream batch consumer should operate at 100% efficiency without false starvation");
    }

    @Test
    @DisplayName("RFC-020: RecipeNodeSerializer serializes and restores junction buffer properties")
    void testBufferPropertiesNBTSerialization() {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), "Junction Buffer", 20, 0, GTVoltageTier.LV);
        node.setReroute(true);
        node.setJunctionBuffer(true);
        node.setJunctionBufferSize(640.0);

        CompoundTag tag = RecipeNodeSerializer.serialize(node);
        RecipeNode deserialized = RecipeNodeSerializer.deserialize(tag);

        Assertions.assertNotNull(deserialized);
        Assertions.assertTrue(deserialized.isJunctionBuffer());
        Assertions.assertEquals(640.0, deserialized.getJunctionBufferSize(), 0.001);
    }
}
