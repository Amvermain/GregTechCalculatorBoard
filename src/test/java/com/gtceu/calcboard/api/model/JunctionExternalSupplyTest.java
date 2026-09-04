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

public class JunctionExternalSupplyTest {

    @Test
    public void testSupplyModeAndCustomParallelSerialization() {
        RecipeNode junction = RecipeNode.createReroute(100.0, 150.0);
        junction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));
        junction.setSupplyMode(SupplyMode.FIXED_RATE);
        junction.setExternalSupplyRate(250.5);

        RecipeNode machine = RecipeNode.create("Assembler", 20.0, 30.0, GTVoltageTier.LV);
        machine.setCustomParallel(7);

        CompoundTag jTag = junction.serializeNBT();
        CompoundTag mTag = machine.serializeNBT();

        RecipeNode deserializedJunction = RecipeNode.deserializeNBT(jTag);
        RecipeNode deserializedMachine = RecipeNode.deserializeNBT(mTag);

        Assertions.assertTrue(deserializedJunction.isReroute());
        Assertions.assertEquals(SupplyMode.FIXED_RATE, deserializedJunction.getSupplyMode());
        Assertions.assertEquals(250.5, deserializedJunction.getExternalSupplyRate(), 0.001);
        Assertions.assertTrue(deserializedJunction.isExternalSupply());
        Assertions.assertFalse(deserializedJunction.isInfiniteSupply());

        Assertions.assertEquals(7, deserializedMachine.getCustomParallel());
        Assertions.assertEquals(7, deserializedMachine.getTotalParallel());
    }

    @Test
    public void testInfiniteSupplyBlocksUpstreamDemandAndEliminatesDeficit() {
        FlowGraph graph = new FlowGraph();

        // 1. Upstream Pump (Produces 100 Water/s at count = 1.0)
        RecipeNode pump = RecipeNode.create("Pump", 20.0, 0.0, GTVoltageTier.LV);
        pump.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));
        pump.setMachineCount(1.0);
        graph.addNode(pump);

        // 2. Infinite Junction (e.g. Infinite Water Hatch / Creative Source)
        RecipeNode junction = RecipeNode.createReroute(100.0, 100.0);
        junction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));
        junction.setSupplyMode(SupplyMode.INFINITE);
        graph.addNode(junction);

        // 3. Consumer Machine (Demands 500 Water/s at count = 1.0)
        RecipeNode chemicalReactor = RecipeNode.create("Chemical Reactor", 20.0, 30.0, GTVoltageTier.LV);
        chemicalReactor.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 500.0));
        chemicalReactor.setMachineCount(1.0);
        graph.addNode(chemicalReactor);

        // Wire: Pump -> Junction -> Reactor
        graph.addConnection(pump.getId(), 0, junction.getId(), 0);
        graph.addConnection(junction.getId(), 0, chemicalReactor.getId(), 0);

        // 1) Upstream Demand Check: Junction is INFINITE -> Upstream demanded from Pump should be 0.0
        double upstreamDemand = FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, pump, 0);
        Assertions.assertEquals(0.0, upstreamDemand, 0.001);

        // 2) Consumer Effective Inflow: Junction provides full 500.0 Water/s to Reactor
        double effectiveInflow = FlowBalanceMatrixSolver.getEffectiveProducerOutputRate(graph, junction, 0);
        Assertions.assertEquals(500.0, effectiveInflow, 0.001);

        // 3) Balance Summary: Net raw input deficit for water should be 0.0
        BalanceSummary summary = FlowSummaryAggregator.computeSummary(graph);
        ResourceLocation waterId = ResourceLocation.tryParse("minecraft:water");
        Double rawWaterDeficit = summary.rawInputs().get(waterId);
        Assertions.assertTrue(rawWaterDeficit == null || rawWaterDeficit <= 0.001, "Infinite supply must offset raw water deficit completely!");
    }

    @Test
    public void testFixedRateSupplySubtractedFromUpstreamDemand() {
        FlowGraph graph = new FlowGraph();

        // 1. Upstream Pump
        RecipeNode pump = RecipeNode.create("Pump", 20.0, 0.0, GTVoltageTier.LV);
        pump.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));
        pump.setMachineCount(1.0);
        graph.addNode(pump);

        // 2. Fixed Rate Junction: Provides 200 Water/s externally
        RecipeNode junction = RecipeNode.createReroute(100.0, 100.0);
        junction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0));
        junction.setSupplyMode(SupplyMode.FIXED_RATE);
        junction.setExternalSupplyRate(200.0);
        graph.addNode(junction);

        // 3. Consumer: Demands 500 Water/s
        RecipeNode chemicalReactor = RecipeNode.create("Chemical Reactor", 20.0, 30.0, GTVoltageTier.LV);
        chemicalReactor.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 500.0));
        chemicalReactor.setMachineCount(1.0);
        graph.addNode(chemicalReactor);

        // Wire: Pump -> Junction -> Reactor
        graph.addConnection(pump.getId(), 0, junction.getId(), 0);
        graph.addConnection(junction.getId(), 0, chemicalReactor.getId(), 0);

        // Upstream demand from Pump should be 500 - 200 = 300 Water/s
        double upstreamDemand = FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, pump, 0);
        Assertions.assertEquals(300.0, upstreamDemand, 0.001);
    }
}
