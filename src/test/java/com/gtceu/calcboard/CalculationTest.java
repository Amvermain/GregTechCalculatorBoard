package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;

public class CalculationTest {

    @Test
    public void testStandardOverclock() {
        // Base: 100 ticks (5s), 30 EU/t (LV)
        // Target: HV (tier delta = 2)
        // Standard GT: 4x EU/t, 2x speed (0.5x duration) per tier
        // Tier 1 (MV): 120 EU/t, 50 ticks (2.5s)
        // Tier 2 (HV): 480 EU/t, 25 ticks (1.25s)
        OverclockMode.OverclockResult res = OverclockMode.STANDARD.calculate(100.0, 30.0, 2);
        
        Assertions.assertEquals(25.0, res.durationTicks(), 0.001);
        Assertions.assertEquals(480.0, res.eut(), 0.001);
        Assertions.assertEquals(2, res.overclocks());
        Assertions.assertEquals(20.0 / 25.0, res.getCyclesPerSecond(), 0.001); // 0.8 cycles/s
    }

    @Test
    public void testPerfectOverclock() {
        // Base: 100 ticks, 30 EU/t
        // Perfect OC: 4x EU/t, 4x speed per tier
        // Tier 1 (MV): 120 EU/t, 25 ticks
        // Tier 2 (HV): 480 EU/t, 6.25 ticks
        OverclockMode.OverclockResult res = OverclockMode.PERFECT.calculate(100.0, 30.0, 2);

        Assertions.assertEquals(6.25, res.durationTicks(), 0.001);
        Assertions.assertEquals(480.0, res.eut(), 0.001);
        Assertions.assertEquals(20.0 / 6.25, res.getCyclesPerSecond(), 0.001); // 3.2 cycles/s
    }

    @Test
    public void testPyrolyseAndDistillationTowerFlowBalance() {
        // Scenario: 1 Pyrolyse Oven produces Charcoal + Wood Tar
        // 3 Distillation Towers consume Wood Tar to produce Creosote, Phenol, Benzene, Toluene etc.
        FlowGraph graph = new FlowGraph();

        // 1. Pyrolyse Oven
        RecipeNode pyrolyse = RecipeNode.create("Pyrolyse Oven (Charcoal)", 320.0, 64.0, GTVoltageTier.MV);
        pyrolyse.setTargetTier(GTVoltageTier.HV); // 1 OC -> 256 EU/t, 160 ticks (8s -> 0.125 cycles/s)
        pyrolyse.setMachineCount(1.0);
        pyrolyse.addInput(IngredientStack.item(new ResourceLocation("minecraft", "oak_log"), "Oak Log", 16, 1.0));
        pyrolyse.addOutput(IngredientStack.item(new ResourceLocation("minecraft", "charcoal"), "Charcoal", 20, 1.0));
        pyrolyse.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 1000, 1.0)); // 1000mB per 8s = 125 mB/s
        graph.addNode(pyrolyse);

        // 2. Distillation Tower
        RecipeNode distTower = RecipeNode.create("Distillation Tower (Wood Tar)", 200.0, 120.0, GTVoltageTier.MV);
        distTower.setTargetTier(GTVoltageTier.HV); // 1 OC -> 480 EU/t, 100 ticks (5s -> 0.2 cycles/s)
        distTower.setMachineCount(3.0); // 3 machines -> 0.6 cycles/s
        distTower.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 1000, 1.0)); // consumes 1000 * 0.6 = 600 mB/s
        distTower.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "creosote"), "Creosote", 500, 1.0)); // produces 500 * 0.6 = 300 mB/s
        distTower.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "phenol"), "Phenol", 100, 1.0));
        graph.addNode(distTower);

        BalanceSummary summary = graph.computeSummary();

        // Total EU/t: Pyrolyse (256 * 1) + DistTower (480 * 3) = 256 + 1440 = 1696 EU/t
        Assertions.assertEquals(1696.0, summary.totalEUt(), 0.001);
        Assertions.assertEquals(GTVoltageTier.HV, summary.highestVoltageTier());

        // Wood Tar: Pyrolyse produces 125 mB/s, DistTower consumes 600 mB/s -> Deficit of 475 mB/s
        IngredientStack woodTar = IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 1000, 1.0);
        Double deficit = summary.rawInputs().get(woodTar);
        Assertions.assertNotNull(deficit);
        Assertions.assertEquals(475.0, deficit, 0.001);

        // Charcoal output: 20 * 0.125 = 2.5 items/s
        IngredientStack charcoal = IngredientStack.item(new ResourceLocation("minecraft", "charcoal"), "Charcoal", 20, 1.0);
        Double charcoalRate = summary.netOutputs().get(charcoal);
        Assertions.assertNotNull(charcoalRate);
        Assertions.assertEquals(2.5, charcoalRate, 0.001);
    }

    @Test
    public void testSaveAndLoadGraphNBT() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node1 = RecipeNode.create("Machine A", 100.0, 32.0, GTVoltageTier.LV);
        node1.setMachineCount(2.5);
        node1.setTargetTier(GTVoltageTier.HV);
        node1.setOverclockMode(OverclockMode.PERFECT);
        node1.setParallel(4);
        node1.addInput(IngredientStack.item(new ResourceLocation("minecraft", "iron_ingot"), "Iron Ingot", 2, 1.0));
        node1.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "molten_iron"), "Molten Iron", 288, 1.0));
        graph.addNode(node1);

        RecipeNode node2 = RecipeNode.create("Machine B", 80.0, 128.0, GTVoltageTier.MV);
        node2.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "molten_iron"), "Molten Iron", 144, 1.0));
        graph.addNode(node2);

        graph.addConnection(node1.getId(), 0, node2.getId(), 0);

        // Serialize
        net.minecraft.nbt.CompoundTag tag = graph.serializeNBT(120.0, 80.0, 1.5);

        // Deserialize
        FlowGraph loaded = FlowGraph.deserializeNBT(tag);

        Assertions.assertEquals(2, loaded.getNodes().size());
        Assertions.assertEquals(1, loaded.getConnections().size());

        RecipeNode loadedNode1 = loaded.findNodeById(node1.getId());
        Assertions.assertNotNull(loadedNode1);
        Assertions.assertEquals(2.5, loadedNode1.getMachineCount(), 0.001);
        Assertions.assertEquals(GTVoltageTier.HV, loadedNode1.getTargetTier());
        Assertions.assertEquals(OverclockMode.PERFECT, loadedNode1.getOverclockMode());
        Assertions.assertEquals(4, loadedNode1.getParallel());
        Assertions.assertEquals(1, loadedNode1.getInputs().size());
        Assertions.assertEquals(1, loadedNode1.getOutputs().size());

        Assertions.assertEquals(120.0, tag.getDouble("panX"), 0.001);
        Assertions.assertEquals(80.0, tag.getDouble("panY"), 0.001);
        Assertions.assertEquals(1.5, tag.getDouble("zoom"), 0.001);
    }

    @Test
    public void testTierScalingPreservesProductionRate() {
        RecipeNode node = RecipeNode.create("Test Machine", 100.0, 32.0, GTVoltageTier.LV);
        node.setMachineCount(4.0);
        node.setTargetTier(GTVoltageTier.LV);
        node.setOverclockMode(OverclockMode.STANDARD);
        node.addOutput(IngredientStack.item(new ResourceLocation("minecraft", "iron_ingot"), "Iron", 1, 1.0));

        double initialRate = node.calculateOutputRates().values().iterator().next(); // 4 machines * (20/100) = 0.8 items/s
        Assertions.assertEquals(0.8, initialRate, 0.001);

        // Tier up to MV: count should halve from 4.0 to 2.0
        double speedRatio = node.getOverclockMode().getSpeedFactor(); // 2.0
        node.setMachineCount(node.getMachineCount() / speedRatio);
        node.setTargetTier(GTVoltageTier.MV);

        double mvRate = node.calculateOutputRates().values().iterator().next(); // 2 machines * (20/50) = 0.8 items/s
        Assertions.assertEquals(0.8, mvRate, 0.001);

        // Tier up to HV: count should halve from 2.0 to 1.0
        node.setMachineCount(node.getMachineCount() / speedRatio);
        node.setTargetTier(GTVoltageTier.HV);

        double hvRate = node.calculateOutputRates().values().iterator().next(); // 1 machine * (20/25) = 0.8 items/s
        Assertions.assertEquals(0.8, hvRate, 0.001);

        // Tier down back to LV: count doubles twice
        node.setMachineCount(node.getMachineCount() * speedRatio * speedRatio);
        node.setTargetTier(GTVoltageTier.LV);

        double backRate = node.calculateOutputRates().values().iterator().next();
        Assertions.assertEquals(4.0, node.getMachineCount(), 0.001);
        Assertions.assertEquals(0.8, backRate, 0.001);
    }

    @Test
    public void testAutoRatioFromAnchorNode() {
        // Scenario: 3-tier chain
        // 1. Pyrolyse Oven produces Wood Tar (1000mB per 16s = 62.5 mB/s per machine)
        // 2. Distillation Tower consumes Wood Tar (1000mB per 5s = 200 mB/s per machine)
        // Anchor: Distillation Tower set to 2.0 machines (consumes 400 mB/s Wood Tar)
        // Goal: Auto-ratio should calculate Pyrolyse Oven machine count = 400 / 62.5 = 6.4 machines!

        FlowGraph graph = new FlowGraph();

        RecipeNode pyrolyse = RecipeNode.create("Pyrolyse Oven", 320.0, 64.0, GTVoltageTier.MV);
        pyrolyse.setTargetTier(GTVoltageTier.MV); // 320 ticks (16s -> 0.0625 cycles/s)
        pyrolyse.setMachineCount(1.0);
        pyrolyse.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 1000, 1.0));
        graph.addNode(pyrolyse);

        RecipeNode distTower = RecipeNode.create("Distillation Tower", 100.0, 120.0, GTVoltageTier.MV);
        distTower.setTargetTier(GTVoltageTier.MV); // 100 ticks (5s -> 0.2 cycles/s)
        distTower.setMachineCount(2.0); // 2 machines -> 400 mB/s consumption
        distTower.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 1000, 1.0));
        distTower.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "creosote"), "Creosote", 500, 1.0));
        distTower.setBaseNode(true);
        graph.addNode(distTower);

        graph.addConnection(pyrolyse.getId(), 0, distTower.getId(), 0);

        // Run Anchor Auto-Ratio
        graph.autoRatioFromAnchor(distTower);

        // Check Pyrolyse Oven count: should be 400 / 62.5 = 6.4
        Assertions.assertEquals(6.4, pyrolyse.getMachineCount(), 0.001);
        Assertions.assertEquals(2.0, distTower.getMachineCount(), 0.001); // Anchor remains unchanged!

        // Summary balance: Wood Tar should be completely balanced (delta = 0)
        BalanceSummary summary = graph.computeSummary();
        IngredientStack woodTar = IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 1000, 1.0);
        Double balancedRate = summary.fullyBalanced().get(woodTar);
        Assertions.assertNotNull(balancedRate);
        Assertions.assertEquals(400.0, balancedRate, 0.001);
    }

    @Test
    public void testMaxTierCapAndParallelOptimization() {
        FlowGraph graph = new FlowGraph();
        graph.setMaxTierCap(GTVoltageTier.HV); // Cap at HV!

        RecipeNode pyrolyse = RecipeNode.create("Pyrolyse Oven", 320.0, 64.0, GTVoltageTier.MV);
        pyrolyse.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 1000, 1.0));
        graph.addNode(pyrolyse);

        RecipeNode distTower = RecipeNode.create("Distillation Tower", 100.0, 120.0, GTVoltageTier.MV);
        distTower.setMachineCount(4.0); // 4 machines
        distTower.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 1000, 1.0));
        distTower.setBaseNode(true);
        graph.addNode(distTower);

        graph.addConnection(pyrolyse.getId(), 0, distTower.getId(), 0);

        // Run Max Throughput Optimizer with Prefer Parallels
        graph.optimizeMaxThroughput(true, false);

        // Assert nodes did not exceed HV cap
        Assertions.assertEquals(GTVoltageTier.HV, pyrolyse.getTargetTier());
        Assertions.assertEquals(GTVoltageTier.HV, distTower.getTargetTier());

        // Assert consolidation into parallels (e.g. 4.0 machines -> 1.0 machine with 4x parallel)
        Assertions.assertTrue(pyrolyse.getMachineCount() > 0);
    }

    @Test
    public void testBlueprintExportAndImportRoundTrip() {
        FlowGraph graph = new FlowGraph();

        RecipeNode macerator = RecipeNode.create("Macerator", 100.0, 32.0, GTVoltageTier.LV);
        macerator.addInput(IngredientStack.item(new ResourceLocation("minecraft", "iron_ore"), "Iron Ore", 1, 1.0));
        macerator.addOutput(IngredientStack.item(new ResourceLocation("gtceu", "crushed_iron_ore"), "Crushed Iron Ore", 2, 1.0));
        macerator.setMachineCount(3.5);
        macerator.setBaseNode(true);
        graph.addNode(macerator);

        RecipeNode furnace = RecipeNode.create("Electric Furnace", 60.0, 32.0, GTVoltageTier.LV);
        furnace.addInput(IngredientStack.item(new ResourceLocation("gtceu", "crushed_iron_ore"), "Crushed Iron Ore", 1, 1.0));
        furnace.addOutput(IngredientStack.item(new ResourceLocation("minecraft", "iron_ingot"), "Iron Ingot", 1, 1.0));
        graph.addNode(furnace);

        graph.addConnection(macerator.getId(), 0, furnace.getId(), 0);

        // Export to Blueprint string
        String code = BlueprintCodec.exportToString(graph, 120.0, 80.0, 1.25);
        Assertions.assertNotNull(code);
        Assertions.assertTrue(code.startsWith("GTBOARD:"));
        Assertions.assertTrue(code.length() > 20);

        // Import back from Blueprint string
        double[] viewport = new double[3];
        FlowGraph imported = BlueprintCodec.importFromString(code, viewport);

        Assertions.assertNotNull(imported);
        Assertions.assertEquals(2, imported.getNodes().size());
        Assertions.assertEquals(1, imported.getConnections().size());

        // Viewport assertions
        Assertions.assertEquals(120.0, viewport[0], 0.001);
        Assertions.assertEquals(80.0, viewport[1], 0.001);
        Assertions.assertEquals(1.25, viewport[2], 0.001);

        // Node properties assertion
        RecipeNode loadedMacerator = imported.getNodes().get(0);
        Assertions.assertEquals("Macerator", loadedMacerator.getName());
        Assertions.assertEquals(3.5, loadedMacerator.getMachineCount(), 0.001);
        Assertions.assertTrue(loadedMacerator.isBaseNode());
    }
}
