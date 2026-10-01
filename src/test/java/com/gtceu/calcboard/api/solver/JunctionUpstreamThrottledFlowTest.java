package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class JunctionUpstreamThrottledFlowTest {

    @Test
    @DisplayName("Junction demand accounts for downstream throttled effective consumption rather than nominal rate")
    public void testJunctionEffectiveDemandWithUpstreamThrottledConsumer() {
        FlowGraph graph = new FlowGraph();

        // 1. Upstream supply for sulfur: supplies 0.3/s out of 1.0/s nominal needed by reactor1 (30% throttle)
        RecipeNode sulfurSupply = RecipeNode.createReroute(0, 0);
        sulfurSupply.setSupplyMode(com.gtceu.calcboard.api.type.SupplyMode.FIXED_RATE);
        sulfurSupply.setExternalSupplyRate(0.3);
        sulfurSupply.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("gtceu:sulfur_dust"), "Sulfur", 1.0, 1.0));
        graph.addNode(sulfurSupply);

        // 2. Junction providing Oxygen Gas with 1083.33 mB/s external supply
        RecipeNode oxygenJunction = RecipeNode.createReroute(100, 0);
        oxygenJunction.setSupplyMode(com.gtceu.calcboard.api.type.SupplyMode.FIXED_RATE);
        oxygenJunction.setExternalSupplyRate(1083.333);
        oxygenJunction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen Gas", 1000.0, 1.0));
        graph.addNode(oxygenJunction);

        // 3. Chemical Reactor 1: requires Sulfur 1.0/s and Oxygen 1333.333 mB/s (1 sec cycle)
        RecipeNode reactor1 = RecipeNode.create("Chemical Reactor 1", 20.0, 20.0, GTVoltageTier.MV);
        reactor1.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:sulfur_dust"), "Sulfur", 1.0, 1.0));
        reactor1.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen Gas", 1333.333, 1.0));
        reactor1.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfur_dioxide"), "Sulfur Dioxide", 1000.0, 1.0));
        reactor1.setMachineCount(1.0);
        graph.addNode(reactor1);

        // 4. Chemical Reactor 2: requires Oxygen 200.0 mB/s (1 sec cycle)
        RecipeNode reactor2 = RecipeNode.create("Chemical Reactor 2", 20.0, 20.0, GTVoltageTier.MV);
        reactor2.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen Gas", 200.0, 1.0));
        reactor2.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:carbon_dioxide"), "Carbon Dioxide", 200.0, 1.0));
        reactor2.setMachineCount(1.0);
        graph.addNode(reactor2);

        // Wiring
        graph.addConnection(sulfurSupply.getId(), 0, reactor1.getId(), 0);
        graph.addConnection(oxygenJunction.getId(), 0, reactor1.getId(), 1);
        graph.addConnection(oxygenJunction.getId(), 0, reactor2.getId(), 0);

        // Solve efficiencies
        FixedPointEfficiencySolver.computeNodeEfficiencies(graph);

        // Reactor 1 is throttled to 30% due to sulfur bottleneck
        Assertions.assertEquals(0.3, reactor1.getEfficiency(), 0.001);
        Assertions.assertEquals(1.0, reactor2.getEfficiency(), 0.001);

        // Verify Junction nominal vs effective demand
        double nominalDemand = FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, oxygenJunction, 0);
        double effectiveDemand = FlowBalanceMatrixSolver.calculateTotalConnectedPortEffectiveDemand(graph, oxygenJunction, 0);

        Assertions.assertEquals(1533.333, nominalDemand, 0.01);
        Assertions.assertEquals(600.0, effectiveDemand, 0.01);

        // Verify Junction port stats: surplus because supply (1083.33) > effective demand (600.0)
        FlowGraphSolver.PortFlowStats junctionStats = graph.getInputPortStats(oxygenJunction, 0);
        Assertions.assertEquals(1533.333, junctionStats.requiredOrProducedRate(), 0.01);
        Assertions.assertEquals(600.0, junctionStats.effectiveRate(), 0.01);
        Assertions.assertEquals(1083.333, junctionStats.connectedRate(), 0.01);
        Assertions.assertTrue(junctionStats.isUpstreamThrottled());
        Assertions.assertFalse(junctionStats.isInputDeficit());
        Assertions.assertTrue(junctionStats.isInputSurplus());

        // Verify downstream Reactor 1 oxygen input stats
        FlowGraphSolver.PortFlowStats r1OxygenStats = graph.getInputPortStats(reactor1, 1);
        Assertions.assertEquals(1333.333, r1OxygenStats.requiredOrProducedRate(), 0.01);
        Assertions.assertTrue(r1OxygenStats.effectiveRate() >= 400.0 - 0.01);
        Assertions.assertTrue(r1OxygenStats.isUpstreamThrottled());
        Assertions.assertFalse(r1OxygenStats.isInputDeficit());

        // Verify downstream Reactor 2 oxygen input stats is not starved
        FlowGraphSolver.PortFlowStats r2OxygenStats = graph.getInputPortStats(reactor2, 0);
        Assertions.assertEquals(200.0, r2OxygenStats.requiredOrProducedRate(), 0.01);
        Assertions.assertTrue(r2OxygenStats.connectedRate() >= 200.0 - 0.01);
        Assertions.assertFalse(r2OxygenStats.isInputDeficit());
    }

    @Test
    @DisplayName("Linked junction with allocated supply does not show deficit when downstream is throttled")
    public void testLinkedJunctionEffectiveDemandWithUpstreamThrottledConsumer() {
        FlowGraph graph = new FlowGraph();

        // 1. Upstream supply for sulfur: supplies 0.3/s out of 1.0/s nominal needed by reactor1 (30% throttle)
        RecipeNode sulfurSupply = RecipeNode.createReroute(0, 0);
        sulfurSupply.setSupplyMode(com.gtceu.calcboard.api.type.SupplyMode.FIXED_RATE);
        sulfurSupply.setExternalSupplyRate(0.3);
        sulfurSupply.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("gtceu:sulfur_dust"), "Sulfur", 1.0, 1.0));
        graph.addNode(sulfurSupply);

        // 2. Linked Junction receiving 1083.333 mB/s from cross-page link
        RecipeNode linkedJunction = RecipeNode.createReroute(100, 0);
        linkedJunction.setSupplyMode(com.gtceu.calcboard.api.type.SupplyMode.LINKED_JUNCTION);
        linkedJunction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen Gas", 1000.0, 1.0));
        linkedJunction.setLinkedSource("source_page_id", "source_node_id");
        linkedJunction.setAllocatedInputRate(1083.333);
        graph.addNode(linkedJunction);

        // 3. Chemical Reactor 1: requires Sulfur 1.0/s and Oxygen 1333.333 mB/s (1 sec cycle)
        RecipeNode reactor1 = RecipeNode.create("Chemical Reactor 1", 20.0, 20.0, GTVoltageTier.MV);
        reactor1.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:sulfur_dust"), "Sulfur", 1.0, 1.0));
        reactor1.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen Gas", 1333.333, 1.0));
        reactor1.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfur_dioxide"), "Sulfur Dioxide", 1000.0, 1.0));
        reactor1.setMachineCount(1.0);
        graph.addNode(reactor1);

        // 4. Chemical Reactor 2: requires Oxygen 200.0 mB/s (1 sec cycle)
        RecipeNode reactor2 = RecipeNode.create("Chemical Reactor 2", 20.0, 20.0, GTVoltageTier.MV);
        reactor2.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen Gas", 200.0, 1.0));
        reactor2.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:carbon_dioxide"), "Carbon Dioxide", 200.0, 1.0));
        reactor2.setMachineCount(1.0);
        graph.addNode(reactor2);

        // Wiring
        graph.addConnection(sulfurSupply.getId(), 0, reactor1.getId(), 0);
        graph.addConnection(linkedJunction.getId(), 0, reactor1.getId(), 1);
        graph.addConnection(linkedJunction.getId(), 0, reactor2.getId(), 0);

        // Solve efficiencies
        FixedPointEfficiencySolver.computeNodeEfficiencies(graph);

        // Reactor 1 is throttled to 30% due to sulfur bottleneck
        Assertions.assertEquals(0.3, reactor1.getEfficiency(), 0.001);
        Assertions.assertEquals(1.0, reactor2.getEfficiency(), 0.001);

        // Verify linked junction demands
        double nominalDemand = FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, linkedJunction, 0);
        double effectiveDemand = FlowBalanceMatrixSolver.calculateTotalConnectedPortEffectiveDemand(graph, linkedJunction, 0);

        Assertions.assertEquals(1533.333, nominalDemand, 0.01);
        Assertions.assertEquals(600.0, effectiveDemand, 0.01);

        // Verify linked junction port stats: surplus because allocated (1083.33) > effective demand (600.0)
        FlowGraphSolver.PortFlowStats junctionStats = graph.getInputPortStats(linkedJunction, 0);
        Assertions.assertEquals(1533.333, junctionStats.requiredOrProducedRate(), 0.01);
        Assertions.assertEquals(600.0, junctionStats.effectiveRate(), 0.01);
        Assertions.assertEquals(1083.333, junctionStats.connectedRate(), 0.01);
        Assertions.assertTrue(junctionStats.isUpstreamThrottled());
        Assertions.assertFalse(junctionStats.isInputDeficit());
        Assertions.assertTrue(junctionStats.isInputSurplus());
    }
}
