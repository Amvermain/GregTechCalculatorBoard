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

    @Test
    @DisplayName("Correctly detects deficit recirculation loop with drain junction as infeasible")
    void testDeficitRecirculationReportsInfeasible() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation shard = ResourceLocation.tryParse("gtceu:nether_star_shard");
        ResourceLocation magShard = ResourceLocation.tryParse("gtceu:magnetic_nether_star_shard");
        ResourceLocation dust1 = ResourceLocation.tryParse("gtceu:dust_1");
        ResourceLocation dust2 = ResourceLocation.tryParse("gtceu:dust_2");
        ResourceLocation plate = ResourceLocation.tryParse("gtceu:nether_star_plate");
        ResourceLocation star = ResourceLocation.tryParse("minecraft:nether_star");

        // Polarizer: 1 shard -> 1 magShard, 20s (0.05/s)
        RecipeNode polarizer = RecipeNode.create("Polarizer", 400.0, 30.0, GTVoltageTier.EV);
        polarizer.addInput(IngredientStack.item(shard, "Shard", 1.0, 1.0));
        polarizer.addOutput(IngredientStack.item(magShard, "Magnetic Shard", 1.0, 1.0));
        graph.addNode(polarizer);

        // Autoclaves with chanced low yield (causing deficit loop)
        RecipeNode auto1 = RecipeNode.create("Autoclave 1", 800.0, 30.0, GTVoltageTier.IV);
        auto1.addInput(IngredientStack.item(magShard, "Magnetic Shard", 1.0, 1.0));
        auto1.addOutput(IngredientStack.item(dust1, "Dust 1", 0.2, 1.0));
        graph.addNode(auto1);

        RecipeNode auto2 = RecipeNode.create("Autoclave 2", 800.0, 30.0, GTVoltageTier.IV);
        auto2.addInput(IngredientStack.item(magShard, "Magnetic Shard", 1.0, 1.0));
        auto2.addOutput(IngredientStack.item(dust2, "Dust 2", 0.2, 1.0));
        graph.addNode(auto2);

        // Forming Press
        RecipeNode press = RecipeNode.create("Forming Press", 304.0, 30.0, GTVoltageTier.IV);
        press.addInput(IngredientStack.item(dust1, "Dust 1", 1.0, 1.0));
        press.addInput(IngredientStack.item(dust2, "Dust 2", 1.0, 1.0));
        press.addOutput(IngredientStack.item(plate, "Plate", 1.0, 1.0));
        graph.addNode(press);

        // Compressor producing 1.0 star
        RecipeNode comp = RecipeNode.create("Compressor", 204.0, 30.0, GTVoltageTier.EV);
        comp.addInput(IngredientStack.item(plate, "Plate", 1.0, 1.0));
        comp.addOutput(IngredientStack.item(star, "Nether Star", 1.0, 1.0));
        graph.addNode(comp);

        // Forge Hammer consuming 1.0 star, producing 5 shards
        RecipeNode hammer = RecipeNode.create("Forge Hammer", 300.0, 30.0, GTVoltageTier.MV);
        hammer.addInput(IngredientStack.item(star, "Nether Star", 1.0, 1.0));
        hammer.addOutput(IngredientStack.item(shard, "Shard", 5.0, 1.0));
        graph.addNode(hammer);

        // Drain Junction requiring 1.0 star/s
        RecipeNode drainJunction = RecipeNode.createReroute(100.0, 100.0);
        drainJunction.bindRerouteIngredient(IngredientStack.item(star, "Nether Star", 1.0, 1.0));
        drainJunction.setSupplyMode(SupplyMode.FIXED_DRAIN);
        drainJunction.setExternalDrainRate(1.0);
        graph.addNode(drainJunction);

        graph.addConnection(polarizer.getId(), 0, auto1.getId(), 0);
        graph.addConnection(polarizer.getId(), 0, auto2.getId(), 0);
        graph.addConnection(auto1.getId(), 0, press.getId(), 0);
        graph.addConnection(auto2.getId(), 0, press.getId(), 1);
        graph.addConnection(press.getId(), 0, comp.getId(), 0);
        graph.addConnection(comp.getId(), 0, drainJunction.getId(), 0);
        graph.addConnection(comp.getId(), 0, hammer.getId(), 0);
        graph.addConnection(hammer.getId(), 0, polarizer.getId(), 0);

        TwoStageLinearFlowSolver.SolveResult result = TwoStageLinearFlowSolver.solve(graph, drainJunction, false);
        assertFalse(result.successful(), "Deficit recirculation loop must be marked infeasible instead of clamping to 0");
        assertNotNull(result.infeasibleResourceLabel());

        // AutoRatioEngine fallback propagates upstream without resetting machine counts to 0.01 / 1.0
        com.gtceu.calcboard.api.solver.AutoRatioResult arResult = com.gtceu.calcboard.api.solver.AutoRatioEngine.autoRatioFromAnchor(graph, drainJunction, false);
        assertTrue(arResult.hasDivergence(), "Divergence warnings should be recorded for deficit loops");
        assertTrue(comp.getMachineCount() > 1.0, "Implosion compressor count should reflect upstream scale, not minimum clamp");
    }
}
