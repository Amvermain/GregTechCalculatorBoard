package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class ClosedLoopRecirculationTest {

    @Test
    @DisplayName("RFC-022: Bayer process closed-loop recirculation sustains 100% efficiency without death spiral")
    public void testBayerProcessClosedLoopWaterRecirculation() {
        FlowGraph graph = new FlowGraph();

        // 1. Mixer: Consumes Bauxite 1.0/s and Water 40 mB/s, Produces Slurry 40 mB/s (1 sec recipe)
        RecipeNode mixer = RecipeNode.create("Mixer", 20.0, 30.0, GTVoltageTier.LV);
        mixer.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:bauxite_ore"), "Bauxite", 1.0, 1.0));
        mixer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 40.0, 1.0));
        mixer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:bauxite_slurry"), "Bauxite Slurry", 40.0, 1.0));
        mixer.setMachineCount(1.0);
        graph.addNode(mixer);

        // 2. Fluid Heater: Consumes Water 4 mB/s, Produces Steam 4 mB/s (1 sec recipe)
        RecipeNode heater = RecipeNode.create("Fluid Heater", 20.0, 30.0, GTVoltageTier.LV);
        heater.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 4.0, 1.0));
        heater.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 4.0, 1.0));
        heater.setMachineCount(1.0);
        graph.addNode(heater);

        // 3. Cracker: Consumes Slurry 40 mB/s and Steam 4 mB/s, Produces Cracked Slurry 40 mB/s (1 sec recipe)
        RecipeNode cracker = RecipeNode.create("Cracker", 20.0, 30.0, GTVoltageTier.LV);
        cracker.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:bauxite_slurry"), "Bauxite Slurry", 40.0, 1.0));
        cracker.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 4.0, 1.0));
        cracker.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:cracked_slurry"), "Cracked Slurry", 40.0, 1.0));
        cracker.setMachineCount(1.0);
        graph.addNode(cracker);

        // 4. Centrifuge: Consumes Cracked Slurry 40 mB/s, Produces Alumina 1.0/s and Water 100 mB/s (1 sec recipe)
        RecipeNode centrifuge = RecipeNode.create("Centrifuge", 20.0, 30.0, GTVoltageTier.LV);
        centrifuge.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:cracked_slurry"), "Cracked Slurry", 40.0, 1.0));
        centrifuge.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:alumina"), "Alumina", 1.0, 1.0));
        centrifuge.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0, 1.0));
        centrifuge.setMachineCount(1.0);
        graph.addNode(centrifuge);

        // Wiring the process chain
        graph.addConnection(mixer.getId(), 0, cracker.getId(), 0);      // Slurry -> Cracker
        graph.addConnection(heater.getId(), 0, cracker.getId(), 1);     // Steam -> Cracker
        graph.addConnection(cracker.getId(), 0, centrifuge.getId(), 0); // Cracked Slurry -> Centrifuge

        // Closed-loop recirculation: Centrifuge Water (100 mB/s) back to Mixer (40 mB/s) and Heater (4 mB/s)
        graph.addConnection(centrifuge.getId(), 1, mixer.getId(), 1);
        graph.addConnection(centrifuge.getId(), 1, heater.getId(), 0);

        Map<String, Double> effMap = graph.computeNodeEfficiencies();

        Assertions.assertEquals(1.0, effMap.get(mixer.getId()), 0.001);
        Assertions.assertEquals(1.0, effMap.get(heater.getId()), 0.001);
        Assertions.assertEquals(1.0, effMap.get(cracker.getId()), 0.001);
        Assertions.assertEquals(1.0, effMap.get(centrifuge.getId()), 0.001);

        FlowGraphSolver.PortFlowStats mixerWaterIn = graph.getInputPortStats(mixer, 1);
        Assertions.assertTrue(mixerWaterIn.isConnected());
        Assertions.assertFalse(mixerWaterIn.isInputDeficit());
        Assertions.assertTrue(mixerWaterIn.connectedRate() >= 40.0 - 0.001);

        FlowGraphSolver.PortFlowStats heaterWaterIn = graph.getInputPortStats(heater, 0);
        Assertions.assertTrue(heaterWaterIn.isConnected());
        Assertions.assertFalse(heaterWaterIn.isInputDeficit());
        Assertions.assertTrue(heaterWaterIn.connectedRate() >= 4.0 - 0.001);
    }

    @Test
    @DisplayName("RFC-022: Multi-branch demand-filling greedy allocation satisfies consumer demands")
    public void testGreedyDemandFillingAllocation() {
        FlowGraph graph = new FlowGraph();

        RecipeNode supplier = RecipeNode.create("Supplier", 20.0, 30.0, GTVoltageTier.LV);
        supplier.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0, 1.0));
        supplier.setMachineCount(1.0);
        graph.addNode(supplier);

        RecipeNode consumerSmall = RecipeNode.create("Consumer Small", 20.0, 30.0, GTVoltageTier.LV);
        consumerSmall.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 4.0, 1.0));
        consumerSmall.setMachineCount(1.0);
        graph.addNode(consumerSmall);

        RecipeNode consumerLarge = RecipeNode.create("Consumer Large", 20.0, 30.0, GTVoltageTier.LV);
        consumerLarge.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 40.0, 1.0));
        consumerLarge.setMachineCount(1.0);
        graph.addNode(consumerLarge);

        graph.addConnection(supplier.getId(), 0, consumerSmall.getId(), 0);
        graph.addConnection(supplier.getId(), 0, consumerLarge.getId(), 0);

        graph.computeSummary();

        FlowGraphSolver.PortFlowStats smallStats = graph.getInputPortStats(consumerSmall, 0);
        FlowGraphSolver.PortFlowStats largeStats = graph.getInputPortStats(consumerLarge, 0);

        Assertions.assertTrue(smallStats.connectedRate() >= 4.0 - 0.001);
        Assertions.assertTrue(largeStats.connectedRate() >= 40.0 - 0.001);
        Assertions.assertFalse(smallStats.isInputDeficit());
        Assertions.assertFalse(largeStats.isInputDeficit());
    }

    @Test
    @DisplayName("RFC-022: Effective deficit evaluation accurately differentiates throttled vs deficit")
    public void testEffectiveDeficitEvaluation() {
        FlowGraphSolver.PortFlowStats stats = new FlowGraphSolver.PortFlowStats(
                0.48,   // nominal required
                0.019,  // connected supply
                1,      // connection count
                true,   // isConnected
                0.0094, // effective required
                true    // isUpstreamThrottled
        );

        Assertions.assertTrue(stats.isConnected());
        Assertions.assertFalse(stats.isInputDeficit());
        Assertions.assertTrue(stats.isNominalDeficit());
        Assertions.assertTrue(stats.isUpstreamThrottled());
        Assertions.assertTrue(stats.isInputSurplus());
    }

    @Test
    @DisplayName("RFC-022: Closed-loop throttles proportionally when external feed is constrained")
    public void testClosedLoopExternalFeedThrottling() {
        FlowGraph graph = new FlowGraph();

        RecipeNode oreSupplier = RecipeNode.create("Ore Supplier", 20.0, 30.0, GTVoltageTier.LV);
        oreSupplier.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:bauxite_ore"), "Bauxite", 0.5, 1.0)); // 50% feed
        oreSupplier.setMachineCount(1.0);
        graph.addNode(oreSupplier);

        RecipeNode mixer = RecipeNode.create("Mixer", 20.0, 30.0, GTVoltageTier.LV);
        mixer.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:bauxite_ore"), "Bauxite", 1.0, 1.0));
        mixer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 40.0, 1.0));
        mixer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:bauxite_slurry"), "Bauxite Slurry", 40.0, 1.0));
        mixer.setMachineCount(1.0);
        graph.addNode(mixer);

        RecipeNode centrifuge = RecipeNode.create("Centrifuge", 20.0, 30.0, GTVoltageTier.LV);
        centrifuge.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:bauxite_slurry"), "Bauxite Slurry", 40.0, 1.0));
        centrifuge.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0, 1.0));
        centrifuge.setMachineCount(1.0);
        graph.addNode(centrifuge);

        graph.addConnection(oreSupplier.getId(), 0, mixer.getId(), 0);
        graph.addConnection(mixer.getId(), 0, centrifuge.getId(), 0);
        graph.addConnection(centrifuge.getId(), 0, mixer.getId(), 1);

        Map<String, Double> effMap = graph.computeNodeEfficiencies();

        Assertions.assertEquals(0.5, effMap.get(mixer.getId()), 0.01);
        Assertions.assertEquals(0.5, effMap.get(centrifuge.getId()), 0.01);
    }

    @Test
    @DisplayName("Regression: Single-input bottleneck deficit must be identified as isInputDeficit, not throttled or surplus")
    public void testSinglePortBottleneckDeficitEvaluation() {
        FlowGraph graph = new FlowGraph();

        // Supplier: produces 40.0/s
        RecipeNode supplier = RecipeNode.create("Supplier", 20.0, 30.0, GTVoltageTier.LV);
        supplier.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:uranium_plate"), "Plate", 40.0, 1.0));
        supplier.setMachineCount(1.0);
        graph.addNode(supplier);

        // Consumer: demands 80.0/s
        RecipeNode consumer = RecipeNode.create("Consumer", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:uranium_plate"), "Plate", 80.0, 1.0));
        consumer.setMachineCount(1.0);
        graph.addNode(consumer);

        graph.addConnection(supplier.getId(), 0, consumer.getId(), 0);
        graph.computeSummary();

        FlowGraphSolver.PortFlowStats stats = graph.getInputPortStats(consumer, 0);
        Assertions.assertTrue(stats.isConnected());
        Assertions.assertEquals(80.0, stats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(40.0, stats.connectedRate(), 0.001);
        Assertions.assertEquals(40.0, stats.effectiveRate(), 0.001);

        // Bottleneck port must be deficit, NOT surplus, NOT upstream throttled
        Assertions.assertTrue(stats.isInputDeficit(), "Bottleneck port must be evaluated as input deficit");
        Assertions.assertTrue(stats.isNominalDeficit(), "Bottleneck port must be evaluated as nominal deficit");
        Assertions.assertFalse(stats.isUpstreamThrottled(), "Bottleneck port cannot be upstream throttled");
        Assertions.assertFalse(stats.isInputSurplus(), "Bottleneck port cannot be input surplus");
    }

    @Test
    @DisplayName("Regression: Junction buffer node correctly detects buffer and evaluates deficit")
    public void testJunctionBufferPortStats() {
        FlowGraph graph = new FlowGraph();

        RecipeNode supplier = RecipeNode.create("Supplier", 20.0, 30.0, GTVoltageTier.LV);
        supplier.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:uranium_plate"), "Plate", 40.0, 1.0));
        supplier.setMachineCount(1.0);
        graph.addNode(supplier);

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.setJunctionBuffer(true);
        junction.setJunctionBufferSize(232.0);
        graph.addNode(junction);

        RecipeNode consumer = RecipeNode.create("Consumer", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:uranium_plate"), "Plate", 80.0, 1.0));
        consumer.setMachineCount(1.0);
        graph.addNode(consumer);

        graph.addConnection(supplier.getId(), 0, junction.getId(), 0);
        graph.addConnection(junction.getId(), 0, consumer.getId(), 0);
        graph.computeSummary();

        RecipeNode bufferNode = graph.findConnectedBufferNode(consumer, 0);
        Assertions.assertNotNull(bufferNode);
        Assertions.assertTrue(bufferNode.isJunctionBuffer());
        Assertions.assertEquals(232.0, bufferNode.getJunctionBufferSize(), 0.001);
        Assertions.assertEquals(5.80, bufferNode.getJunctionChargeDuration(graph), 0.01);

        FlowGraphSolver.PortFlowStats stats = graph.getInputPortStats(consumer, 0);
        Assertions.assertTrue(stats.isInputDeficit());
        Assertions.assertFalse(stats.isInputSurplus());
    }
}
