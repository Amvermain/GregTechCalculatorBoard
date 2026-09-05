package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.FlowBalanceMatrixSolver;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.solver.FlowSummaryAggregator;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SupplyMode;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class JunctionFixedDrainTest {

    @Test
    public void testFixedDrainSerializationAndProperties() {
        RecipeNode junction = RecipeNode.createReroute(100.0, 150.0);
        junction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));
        junction.setSupplyMode(SupplyMode.FIXED_DRAIN);
        junction.setExternalDrainRate(65536.0);

        CompoundTag tag = junction.serializeNBT();
        RecipeNode deserialized = RecipeNode.deserializeNBT(tag);

        Assertions.assertTrue(deserialized.isReroute());
        Assertions.assertEquals(SupplyMode.FIXED_DRAIN, deserialized.getSupplyMode());
        Assertions.assertTrue(deserialized.isFixedDrain());
        Assertions.assertFalse(deserialized.isVoidSink());
        Assertions.assertFalse(deserialized.isExternalSupply());
        Assertions.assertFalse(deserialized.isInfiniteSupply());
        Assertions.assertEquals(65536.0, deserialized.getExternalDrainRate(), 0.001);
    }

    @Test
    public void testFixedDrainGeneratesUpstreamDemandWithoutDownstreamConsumer() {
        FlowGraph graph = new FlowGraph();

        RecipeNode pump = RecipeNode.create("Pump", 20.0, 0.0, GTVoltageTier.LV);
        pump.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));
        pump.setMachineCount(1.0);
        graph.addNode(pump);

        RecipeNode junction = RecipeNode.createReroute(100.0, 100.0);
        junction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));
        junction.setSupplyMode(SupplyMode.FIXED_DRAIN);
        junction.setExternalDrainRate(500.0);
        graph.addNode(junction);

        graph.addConnection(pump.getId(), 0, junction.getId(), 0);

        double upstreamDemand = FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, pump, 0);
        Assertions.assertEquals(500.0, upstreamDemand, 0.001);
    }

    @Test
    public void testFixedDrainAutoRatioScalesUpstreamMachines() {
        FlowGraph graph = new FlowGraph();

        RecipeNode pump = RecipeNode.create("Pump", 20.0, 0.0, GTVoltageTier.LV);
        pump.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));
        pump.setMachineCount(1.0);
        graph.addNode(pump);

        RecipeNode junction = RecipeNode.createReroute(100.0, 100.0);
        junction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));
        junction.setSupplyMode(SupplyMode.FIXED_DRAIN);
        junction.setExternalDrainRate(350.0);
        graph.addNode(junction);

        graph.addConnection(pump.getId(), 0, junction.getId(), 0);

        FlowGraphSolver.autoRatioFromAnchor(graph, junction, false);

        Assertions.assertEquals(3.5, pump.getMachineCount(), 0.001);
    }

    @Test
    public void testFixedDrainWithBothDrainAndDownstreamConsumer() {
        FlowGraph graph = new FlowGraph();

        RecipeNode pump = RecipeNode.create("Pump", 20.0, 0.0, GTVoltageTier.LV);
        pump.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));
        pump.setMachineCount(1.0);
        graph.addNode(pump);

        RecipeNode junction = RecipeNode.createReroute(100.0, 100.0);
        junction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));
        junction.setSupplyMode(SupplyMode.FIXED_DRAIN);
        junction.setExternalDrainRate(200.0);
        graph.addNode(junction);

        RecipeNode reactor = RecipeNode.create("Reactor", 20.0, 30.0, GTVoltageTier.LV);
        reactor.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 300.0));
        reactor.setMachineCount(1.0);
        graph.addNode(reactor);

        graph.addConnection(pump.getId(), 0, junction.getId(), 0);
        graph.addConnection(junction.getId(), 0, reactor.getId(), 0);

        double upstreamDemand = FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, pump, 0);
        Assertions.assertEquals(500.0, upstreamDemand, 0.001);

        FlowGraphSolver.autoRatioFromAnchor(graph, junction, false);
        Assertions.assertEquals(5.0, pump.getMachineCount(), 0.001);
    }

    @Test
    public void testFixedDrainFlowSummaryAggregatorOffsetsProduction() {
        FlowGraph graph = new FlowGraph();

        RecipeNode pump = RecipeNode.create("Pump", 20.0, 0.0, GTVoltageTier.LV);
        pump.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 250.0));
        pump.setMachineCount(2.0);
        graph.addNode(pump);

        RecipeNode junction = RecipeNode.createReroute(100.0, 100.0);
        junction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));
        junction.setSupplyMode(SupplyMode.FIXED_DRAIN);
        junction.setExternalDrainRate(500.0);
        graph.addNode(junction);

        graph.addConnection(pump.getId(), 0, junction.getId(), 0);

        BalanceSummary summary = FlowSummaryAggregator.computeSummary(graph);
        ResourceLocation waterId = ResourceLocation.tryParse("minecraft:water");

        Double netOutputWater = summary.netOutputs().get(waterId);
        Double rawInputWater = summary.rawInputs().get(waterId);

        Assertions.assertTrue(netOutputWater == null || netOutputWater <= 0.001);
        Assertions.assertTrue(rawInputWater == null || rawInputWater <= 0.001);
    }

    @Test
    public void testFixedDrainOutputPortStatsReflectsDrainDemand() {
        FlowGraph graph = new FlowGraph();

        RecipeNode slicer = RecipeNode.create("Quartz Slicing", 20.0, 30.0, GTVoltageTier.LV);
        slicer.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:quartz"), "Nether Quartz", 4.0));
        slicer.setMachineCount(0.15);
        graph.addNode(slicer);

        RecipeNode junction = RecipeNode.createReroute(100.0, 100.0);
        junction.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("minecraft:quartz"), "Nether Quartz", 1.0));
        junction.setSupplyMode(SupplyMode.FIXED_DRAIN);
        junction.setExternalDrainRate(100.0);
        graph.addNode(junction);

        graph.addConnection(slicer.getId(), 0, junction.getId(), 0);

        FlowGraphSolver.PortFlowStats outStats = graph.getOutputPortStats(slicer, 0);
        Assertions.assertTrue(outStats.isConnected());
        Assertions.assertEquals(0.6, outStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(100.0, outStats.connectedRate(), 0.001);
        Assertions.assertTrue(outStats.isOutputDeficit());
        Assertions.assertFalse(outStats.isOutputSurplus());
        Assertions.assertFalse(outStats.isBalanced());

        FlowGraphSolver.PortFlowStats inStats = graph.getInputPortStats(junction, 0);
        Assertions.assertTrue(inStats.isConnected());
        Assertions.assertEquals(100.0, inStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(0.6, inStats.connectedRate(), 0.001);
        Assertions.assertTrue(inStats.isInputDeficit());
    }
}
