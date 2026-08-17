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

    @Test
    public void testPetrochemNitrobenzeneBlueprint() {
        String blueprint = "GTBOARD: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";
        
        double[] viewport = new double[3];
        FlowGraph graph = BlueprintCodec.importFromString(blueprint, viewport);
        Assertions.assertNotNull(graph);
        System.out.println("Imported " + graph.getNodes().size() + " nodes and " + graph.getConnections().size() + " connections.");

        RecipeNode nitrobenzene = null;
        for (RecipeNode n : graph.getNodes()) {
            if (n.getName().toLowerCase().contains("nitrobenzene")) {
                nitrobenzene = n;
                break;
            }
        }
        Assertions.assertNotNull(nitrobenzene);
        nitrobenzene.setMachineCount(1.0);
        nitrobenzene.setBaseNode(true);

        graph.autoRatioFromAnchor(nitrobenzene);

        System.out.println("=== Calculated Machine Counts for Nitrobenzene Anchor ===");
        for (RecipeNode n : graph.getNodes()) {
            System.out.printf("Node: %-30s | Count: %7.2f | Tier: %s | Single CPS: %.4f | Total CPS: %.4f%n",
                    n.getName(), n.getMachineCount(), n.getTargetTier().getName(),
                    n.getOverclockResult().getCyclesPerSecond(), n.getCyclesPerSecond());
        }

        // Assert that upstream oil / cracking nodes do NOT explode into hundreds
        for (RecipeNode n : graph.getNodes()) {
            Assertions.assertTrue(n.getMachineCount() < 50.0, "Node " + n.getName() + " exploded to " + n.getMachineCount());
        }
    }
}
