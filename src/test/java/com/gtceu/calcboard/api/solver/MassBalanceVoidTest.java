package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.RecipeNodeSerializer;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SupplyMode;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MinecraftBootstrapExtension.class)
public class MassBalanceVoidTest {

    @Test
    @DisplayName("RFC-019: Junction VOID_SINK absorbs surplus byproducts and clears them from netOutputs")
    void testJunctionVoidSinkAbsorbsSurplus() {
        FlowGraph graph = new FlowGraph();

        // 1. Reactor producing Sulfuric Acid (Main) and Sulfur Dioxide (Byproduct)
        // 20 ticks (1s), 1 machine -> 1 cycle/s
        RecipeNode reactor = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Chemical Reactor", 20, 30, GTVoltageTier.LV);
        reactor.setMachineCount(1.0);
        IngredientStack sulfur = IngredientStack.item(ResourceLocation.tryParse("gtceu:sulfur_dust"), "Sulfur", 1);
        IngredientStack water = IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000);
        IngredientStack sulfuricAcid = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 500);
        IngredientStack sulfurDioxide = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfur_dioxide"), "Sulfur Dioxide", 67);

        reactor.getInputs().add(sulfur);
        reactor.getInputs().add(water);
        reactor.getOutputs().add(sulfuricAcid);
        reactor.getOutputs().add(sulfurDioxide);
        graph.addNode(reactor);

        // Without void sink: both Sulfuric Acid and Sulfur Dioxide should be in netOutputs
        BalanceSummary summaryInitial = FlowSummaryAggregator.computeSummary(graph);
        Assertions.assertTrue(summaryInitial.netOutputs().containsKey(sulfuricAcid));
        Assertions.assertTrue(summaryInitial.netOutputs().containsKey(sulfurDioxide));
        Assertions.assertFalse(summaryInitial.hasVoidedOutputs());

        // 2. Add Reroute Junction as VOID_SINK connected to Sulfur Dioxide (output index 1)
        RecipeNode sinkJunction = RecipeNode.createReroute(100, 100);
        sinkJunction.setSupplyMode(SupplyMode.VOID_SINK);
        sinkJunction.getInputs().add(sulfurDioxide);
        sinkJunction.getOutputs().add(sulfurDioxide);
        graph.addNode(sinkJunction);

        graph.addConnection(new FlowGraph.ConnectionEdge(reactor.getId(), 1, sinkJunction.getId(), 0));

        // Recompute summary
        BalanceSummary summaryWithSink = FlowSummaryAggregator.computeSummary(graph);

        // Sulfuric Acid remains in Net Products
        Assertions.assertTrue(summaryWithSink.netOutputs().containsKey(sulfuricAcid));
        Assertions.assertEquals(500.0, summaryWithSink.netOutputs().get(sulfuricAcid), 0.01);

        // Sulfur Dioxide must NOT be in Net Products, but in voidedOutputs
        Assertions.assertFalse(summaryWithSink.netOutputs().containsKey(sulfurDioxide), "Voided byproduct should be removed from Net Products");
        Assertions.assertTrue(summaryWithSink.hasVoidedOutputs());
        Assertions.assertTrue(summaryWithSink.voidedOutputs().containsKey(sulfurDioxide));
        Assertions.assertEquals(67.0, summaryWithSink.voidedOutputs().get(sulfurDioxide), 0.01);
    }

    @Test
    @DisplayName("RFC-019: Direct port void marking removes byproduct from Net Products and moves to voidedOutputs")
    void testDirectPortVoidMarking() {
        FlowGraph graph = new FlowGraph();

        RecipeNode reactor = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Chemical Reactor", 20, 30, GTVoltageTier.LV);
        reactor.setMachineCount(1.0);
        IngredientStack sulfuricAcid = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 500);
        IngredientStack sulfurTrioxide = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfur_trioxide"), "Sulfur Trioxide", 100);

        reactor.getOutputs().add(sulfuricAcid);
        reactor.getOutputs().add(sulfurTrioxide);
        graph.addNode(reactor);

        // Mark output 1 (Sulfur Trioxide) as voided
        reactor.setOutputPortVoided(1, true);
        Assertions.assertTrue(reactor.isOutputPortVoided(1));
        Assertions.assertFalse(reactor.isOutputPortVoided(0));

        BalanceSummary summary = FlowSummaryAggregator.computeSummary(graph);

        Assertions.assertTrue(summary.netOutputs().containsKey(sulfuricAcid));
        Assertions.assertEquals(500.0, summary.netOutputs().get(sulfuricAcid), 0.01);

        Assertions.assertFalse(summary.netOutputs().containsKey(sulfurTrioxide));
        Assertions.assertTrue(summary.hasVoidedOutputs());
        Assertions.assertEquals(100.0, summary.voidedOutputs().get(sulfurTrioxide), 0.01);
    }

    @Test
    @DisplayName("RFC-019: Partial downstream consumption leaves only surplus in voidedOutputs")
    void testPartialConsumptionAndVoidRemainder() {
        FlowGraph graph = new FlowGraph();

        // Producer outputs 100/s oxygen
        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:electrolyzer"), "Electrolyzer", 20, 30, GTVoltageTier.LV);
        IngredientStack oxygen = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 100);
        producer.getOutputs().add(oxygen);
        producer.setOutputPortVoided(0, true); // Mark port as voided!
        graph.addNode(producer);

        // Consumer requires 40/s oxygen
        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Chemical Reactor", 20, 30, GTVoltageTier.LV);
        IngredientStack oxygenIn = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 40);
        consumer.getInputs().add(oxygenIn);
        graph.addNode(consumer);

        // Connect producer -> consumer
        graph.addConnection(new FlowGraph.ConnectionEdge(producer.getId(), 0, consumer.getId(), 0));

        BalanceSummary summary = FlowSummaryAggregator.computeSummary(graph);

        // Consumption (40) is satisfied, remainder (60) is voided
        Assertions.assertFalse(summary.netOutputs().containsKey(oxygen));
        Assertions.assertEquals(60.0, summary.voidedOutputs().get(oxygen), 0.01);
    }

    @Test
    @DisplayName("RFC-019: NBT serialization and deserialization preserves voidedOutputs and VOID_SINK supply mode")
    void testNbtRoundTripWithVoidState() {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Chemical Reactor", 20, 30, GTVoltageTier.LV);
        node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 500));
        node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfur_dioxide"), "Sulfur Dioxide", 67));
        node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfur_trioxide"), "Sulfur Trioxide", 100));

        node.setOutputPortVoided(1, true);
        node.setOutputPortVoided(2, true);

        RecipeNode junction = RecipeNode.createReroute(50, 50);
        junction.setSupplyMode(SupplyMode.VOID_SINK);

        CompoundTag nodeTag = node.serializeNBT();
        CompoundTag junctionTag = junction.serializeNBT();

        RecipeNode deserializedNode = RecipeNode.deserializeNBT(nodeTag);
        RecipeNode deserializedJunction = RecipeNode.deserializeNBT(junctionTag);

        Assertions.assertTrue(deserializedNode.isOutputPortVoided(1));
        Assertions.assertTrue(deserializedNode.isOutputPortVoided(2));
        Assertions.assertFalse(deserializedNode.isOutputPortVoided(0));
        Assertions.assertEquals(2, deserializedNode.getVoidedOutputCount());

        Assertions.assertTrue(deserializedJunction.isVoidSink());
        Assertions.assertEquals(SupplyMode.VOID_SINK, deserializedJunction.getSupplyMode());
    }

    @Test
    @DisplayName("RFC-019: In 1:N split, VOID_SINK has lowest priority and never starves normal consumer nodes")
    void testOneToManySplitVoidSinkHasLowestPriority() {
        FlowGraph graph = new FlowGraph();

        // Producer: Electrolyzer producing 100/s Oxygen
        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:electrolyzer"), "Electrolyzer", 20, 30, GTVoltageTier.LV);
        producer.setMachineCount(1.0);
        IngredientStack oxygen = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 100);
        producer.getOutputs().add(oxygen);
        graph.addNode(producer);

        // Consumer: Chemical Reactor requiring 40/s Oxygen
        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Chemical Reactor", 20, 30, GTVoltageTier.LV);
        consumer.setMachineCount(1.0);
        consumer.getInputs().add(oxygen); // requires 100 per recipe * (20/20) = 100/s? wait, let's create 40/s
        IngredientStack oxygenIn = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 40);
        consumer.getInputs().set(0, oxygenIn);
        graph.addNode(consumer);

        // Junction VOID_SINK
        RecipeNode sinkJunction = RecipeNode.createReroute(150, 150);
        sinkJunction.setSupplyMode(SupplyMode.VOID_SINK);
        sinkJunction.getInputs().add(oxygen);
        sinkJunction.getOutputs().add(oxygen);
        graph.addNode(sinkJunction);

        // 1:N Split from producer output 0:
        // Wire 1: producer -> consumer
        graph.addConnection(new FlowGraph.ConnectionEdge(producer.getId(), 0, consumer.getId(), 0));
        // Wire 2: producer -> VOID_SINK junction
        graph.addConnection(new FlowGraph.ConnectionEdge(producer.getId(), 0, sinkJunction.getId(), 0));

        // 1. Verify consumer input port stats: must be 100% satisfied (not starved by sink!)
        FlowGraphSolver.PortFlowStats inStats = graph.getInputPortStats(consumer, 0);
        Assertions.assertEquals(40.0, inStats.requiredOrProducedRate(), 0.01, "Consumer demands 40/s");
        Assertions.assertTrue(inStats.connectedRate() >= 40.0, "Consumer must receive at least 40/s");
        Assertions.assertTrue(inStats.connectedRate() / inStats.requiredOrProducedRate() >= 1.0, "Consumer must not be starved (saturation >= 100%)");

        // 2. Verify producer output port stats: demand must only count normal consumer (40/s), not infinite void demand
        FlowGraphSolver.PortFlowStats outStats = graph.getOutputPortStats(producer, 0);
        Assertions.assertEquals(100.0, outStats.requiredOrProducedRate(), 0.01, "Producer outputs 100/s");
        Assertions.assertEquals(40.0, outStats.connectedRate(), 0.01, "Connected demand should only be normal consumer (40/s)");
        Assertions.assertTrue(outStats.isOutputSurplus(), "Producer should show surplus of 60/s");

        // 3. Verify overall flow balance summary
        BalanceSummary summary = FlowSummaryAggregator.computeSummary(graph);
        Assertions.assertFalse(summary.netOutputs().containsKey(oxygen), "Oxygen surplus should be completely voided, leaving 0 net output");
        Assertions.assertTrue(summary.hasVoidedOutputs(), "Summary must contain voided outputs");
        Assertions.assertEquals(60.0, summary.voidedOutputs().get(oxygen), 0.01, "Exactly 60/s surplus must be voided");
    }
}
