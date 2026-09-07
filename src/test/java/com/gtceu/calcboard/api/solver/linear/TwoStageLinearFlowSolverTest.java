package com.gtceu.calcboard.api.solver.linear;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SupplyMode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TwoStageLinearFlowSolverTest {

    @Test
    @DisplayName("Solves a 3-node linear chain in a single pass")
    void testLinearChain3Nodes() {
        FlowGraph graph = new FlowGraph();

        RecipeNode nodeA = RecipeNode.create("Smelter", 20.0, 30.0, GTVoltageTier.LV);
        nodeA.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 4.0, 1.0));
        nodeA.setMachineCount(1.0);
        graph.addNode(nodeA);

        RecipeNode nodeB = RecipeNode.create("Bending Machine", 20.0, 30.0, GTVoltageTier.LV);
        nodeB.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 2.0, 1.0));
        nodeB.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_plate"), "Iron Plate", 1.0, 1.0));
        nodeB.setMachineCount(1.0);
        graph.addNode(nodeB);

        RecipeNode nodeC = RecipeNode.create("Extruder", 20.0, 30.0, GTVoltageTier.LV);
        nodeC.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_plate"), "Iron Plate", 2.0, 1.0));
        nodeC.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_gear"), "Iron Gear", 1.0, 1.0));
        nodeC.setMachineCount(2.0);
        graph.addNode(nodeC);

        graph.addConnection(nodeA.getId(), 0, nodeB.getId(), 0);
        graph.addConnection(nodeB.getId(), 0, nodeC.getId(), 0);

        TwoStageLinearFlowSolver.SolveResult result = TwoStageLinearFlowSolver.solve(graph, nodeC, true);

        assertTrue(result.successful());
        Map<String, Double> counts = result.machineCounts();
        assertEquals(2.0, counts.get(nodeA.getId()), 1e-4);
        assertEquals(4.0, counts.get(nodeB.getId()), 1e-4);
        assertEquals(2.0, counts.get(nodeC.getId()), 1e-4);
    }

    @Test
    @DisplayName("Solves a continuous drain junction anchor in a single pass")
    void testContinuousDrainJunctionAnchorSinglePass() {
        FlowGraph graph = new FlowGraph();

        RecipeNode compressor = RecipeNode.create("Implosion Compressor", 200.0, 30.0, GTVoltageTier.HV);
        compressor.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0, 1.0));
        compressor.setMachineCount(1.0);
        graph.addNode(compressor);

        RecipeNode junction = RecipeNode.createReroute(100.0, 100.0);
        junction.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0, 1.0));
        junction.setSupplyMode(SupplyMode.FIXED_DRAIN);
        junction.setExternalDrainRate(2.0);
        graph.addNode(junction);

        graph.addConnection(compressor.getId(), 0, junction.getId(), 0);

        TwoStageLinearFlowSolver.SolveResult result = TwoStageLinearFlowSolver.solve(graph, junction, true);

        assertTrue(result.successful());
        Map<String, Double> counts = result.machineCounts();
        assertEquals(20.0, counts.get(compressor.getId()), 1e-4);
    }

    @Test
    @DisplayName("Solves closed-loop recirculation balance deterministically")
    void testClosedLoopRecirculationBalance() {
        FlowGraph graph = new FlowGraph();

        RecipeNode nodeA = RecipeNode.create("Reactor", 20.0, 30.0, GTVoltageTier.LV);
        nodeA.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:raw_material"), "Raw", 1.0, 1.0));
        nodeA.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:recycled_fluid"), "Recycled", 1.0, 1.0));
        nodeA.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:inter_product"), "Inter", 2.0, 1.0));
        nodeA.setMachineCount(2.0);
        graph.addNode(nodeA);

        RecipeNode nodeB = RecipeNode.create("Separator", 20.0, 30.0, GTVoltageTier.LV);
        nodeB.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:inter_product"), "Inter", 2.0, 1.0));
        nodeB.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:final_product"), "Final", 1.0, 1.0));
        nodeB.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:recycled_fluid"), "Recycled", 1.0, 1.0));
        nodeB.setMachineCount(1.0);
        graph.addNode(nodeB);

        graph.addConnection(nodeA.getId(), 0, nodeB.getId(), 0);
        graph.addConnection(nodeB.getId(), 1, nodeA.getId(), 1);

        TwoStageLinearFlowSolver.SolveResult result = TwoStageLinearFlowSolver.solve(graph, nodeA, true);

        assertTrue(result.successful());
        Map<String, Double> counts = result.machineCounts();
        assertEquals(2.0, counts.get(nodeA.getId()), 1e-4);
        assertEquals(2.0, counts.get(nodeB.getId()), 1e-4);
    }

    @Test
    @DisplayName("Solves complex multi-branch Nether Star recirculation with drain junction in 1 pass")
    void testNetherStarRecirculationWithDrainJunction() {
        FlowGraph graph = new FlowGraph();

        // 1. 4 Autoclaves producing 4 dusts
        RecipeNode auto1 = RecipeNode.create("Autoclave 1", 20.0, 30.0, GTVoltageTier.LV);
        auto1.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:polarized_star"), "Polarized Star", 1.0, 1.0));
        auto1.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:dust_1"), "Dust 1", 1.0, 1.0));
        graph.addNode(auto1);

        RecipeNode auto2 = RecipeNode.create("Autoclave 2", 20.0, 30.0, GTVoltageTier.LV);
        auto2.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:polarized_star"), "Polarized Star", 1.0, 1.0));
        auto2.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:dust_2"), "Dust 2", 1.0, 1.0));
        graph.addNode(auto2);

        // 2. Forming Press: Consumes Dust 1 & Dust 2, Produces Raw Star 1.0/s
        RecipeNode press = RecipeNode.create("Forming Press", 20.0, 30.0, GTVoltageTier.LV);
        press.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:dust_1"), "Dust 1", 1.0, 1.0));
        press.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:dust_2"), "Dust 2", 1.0, 1.0));
        press.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:raw_star"), "Raw Star", 1.0, 1.0));
        graph.addNode(press);

        // 3. Compressor: Consumes Raw Star 1.0/s, Produces Nether Star 2.0/s (1 nether star for drain, 1 for recycle)
        RecipeNode comp = RecipeNode.create("Compressor", 20.0, 30.0, GTVoltageTier.LV);
        comp.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:raw_star"), "Raw Star", 1.0, 1.0));
        comp.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 2.0, 1.0));
        graph.addNode(comp);

        // 4. Polarizer (Recycling): Consumes Nether Star 1.0/s, Produces Polarized Star 2.0/s
        RecipeNode polarizer = RecipeNode.create("Polarizer", 20.0, 30.0, GTVoltageTier.LV);
        polarizer.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0, 1.0));
        polarizer.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:polarized_star"), "Polarized Star", 2.0, 1.0));
        graph.addNode(polarizer);

        // 5. Junction Node (Fixed Drain: 2.0 Nether Star/s)
        RecipeNode drainJunction = RecipeNode.createReroute(100.0, 100.0);
        drainJunction.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0, 1.0));
        drainJunction.setSupplyMode(SupplyMode.FIXED_DRAIN);
        drainJunction.setExternalDrainRate(2.0);
        graph.addNode(drainJunction);

        // Wiring
        graph.addConnection(auto1.getId(), 0, press.getId(), 0);
        graph.addConnection(auto2.getId(), 0, press.getId(), 1);
        graph.addConnection(press.getId(), 0, comp.getId(), 0);
        graph.addConnection(comp.getId(), 0, drainJunction.getId(), 0); // Drain 2.0/s
        graph.addConnection(comp.getId(), 0, polarizer.getId(), 0);     // Recycle
        graph.addConnection(polarizer.getId(), 0, auto1.getId(), 0);
        graph.addConnection(polarizer.getId(), 0, auto2.getId(), 0);

        // Solve with drainJunction anchor (Fixed Drain = 2.0 /s)
        TwoStageLinearFlowSolver.SolveResult result = TwoStageLinearFlowSolver.solve(graph, drainJunction, true);

        if (!result.successful()) {
            System.err.println("TEST FAILURE REASON: " + result.infeasibleResourceLabel());
        }
        assertTrue(result.successful(), "Expected solve to succeed, but failed with reason: " + result.infeasibleResourceLabel());
        Map<String, Double> counts = result.machineCounts();
        assertNotNull(counts);
        // All nodes are computed deterministically in a single pass without divergence warnings!
        assertTrue(counts.get(comp.getId()) >= 1.0);
        assertTrue(counts.get(press.getId()) >= 1.0);
    }
}
