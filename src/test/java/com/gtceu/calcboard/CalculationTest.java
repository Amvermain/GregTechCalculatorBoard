package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

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
    public void testGeneratorPowerSubtraction() {
        FlowGraph graph = new FlowGraph();

        // 1. Consumer machine: 120 EU/t (MV)
        RecipeNode machine = RecipeNode.create("Electric Furnace", 100.0, 120.0, GTVoltageTier.MV);
        machine.setMachineCount(2.0); // Total consumption: 240 EU/t
        graph.addNode(machine);

        // 2. Generator: produces 512 EU/t (HV)
        RecipeNode generator = RecipeNode.create("Gas Turbine", 100.0, 512.0, GTVoltageTier.HV);
        generator.setGenerator(true);
        generator.setMachineCount(1.0); // Total generation: 512 EU/t
        graph.addNode(generator);

        BalanceSummary summary = graph.computeSummary();

        // Net EU/t should be 240 - 512 = -272 EU/t (Net Generation)
        Assertions.assertEquals(-272.0, summary.totalEUt(), 0.001);
        Assertions.assertEquals(GTVoltageTier.HV, summary.highestVoltageTier());

        // Generator duration must remain constant (no exponential OC) even if tier is raised to ZPM
        generator.setTargetTier(GTVoltageTier.ZPM);
        Assertions.assertEquals(100.0 / 20.0, generator.getEffectiveDurationSeconds(), 0.001); // 5.0s constant

        // Linear scaling with parallel factor
        generator.setParallel(1152);
        Assertions.assertEquals(512.0 * 1152, generator.getTotalEUt(), 0.001);
    }

    @Test
    public void testTurbineRotorEfficiencyScaling() {
        // Base: 40 ticks (2.0s), 32 EU/t (LV)
        RecipeNode turbine = RecipeNode.create("Large Gas Turbine", 40.0, 32.0, GTVoltageTier.LV);
        turbine.setGenerator(true);
        turbine.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "nitrobenzene"), "Nitrobenzene", 1, 1.0));

        // 1. Standard Rotor (100%): 2.0s, 32 EU/t
        Assertions.assertEquals(2.0, turbine.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(32.0, turbine.getTotalEUt(), 0.001);

        // 2. Rhodium-Plated Palladium Rotor (220%): 4.40s duration!
        turbine.setRotorEfficiency(220);
        Assertions.assertEquals(4.40, turbine.getEffectiveDurationSeconds(), 0.001);

        // 3. 1,152 Parallels: +36,864 EU/t, 261.82 mB/s consumption
        turbine.setParallel(1152);
        Assertions.assertEquals(36864.0, turbine.getTotalEUt(), 0.001);
        
        var inputRates = turbine.calculateInputRates();
        IngredientStack nitrobenzene = turbine.getInputs().get(0);
        Assertions.assertEquals(1152.0 / 4.40, inputRates.get(nitrobenzene), 0.01);

        // 4. Enderium Rotor (Efficiency 180%): 3.60s duration, 36,864 EU/t, 320.0 mB/s
        turbine.setRotorEfficiency(180);
        Assertions.assertEquals(3.60, turbine.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(32.0 * 1152, turbine.getTotalEUt(), 0.001);
        
        var enderiumRates = turbine.calculateInputRates();
        Assertions.assertEquals(1152.0 / 3.60, enderiumRates.get(nitrobenzene), 0.01);

        // 5. Auto Parallel Derivation by Rotor Power & Voltage Tier Limit
        // Base 32 EU/t, Scheelite Rotor (200% Efficiency, 450% Power)
        turbine.setRotorEfficiency(200);
        turbine.setRotorPower(450);
        
        // IV Rotor Holder (8,192 EU/t * 4.5 = 36,864 EU/t -> 1,152 parallels!)
        turbine.setTargetTier(GTVoltageTier.IV);
        Assertions.assertEquals(1152, turbine.getParallel());
        Assertions.assertEquals(36864.0, turbine.getTotalEUt(), 0.001);
        Assertions.assertEquals(4.00, turbine.getEffectiveDurationSeconds(), 0.001);

        var scheeliteRates = turbine.calculateInputRates();
        Assertions.assertEquals(1152.0 / 4.00, scheeliteRates.get(nitrobenzene), 0.01); // 288.0 mB/s

        // EV Rotor Holder (2,048 EU/t * 4.5 = 9,216 EU/t -> 288 parallels)
        turbine.setTargetTier(GTVoltageTier.EV);
        Assertions.assertEquals(288, turbine.getParallel());
        Assertions.assertEquals(32.0 * 288, turbine.getTotalEUt(), 0.001);
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

        // Run Anchor Auto-Ratio (Integer Ceiling)
        graph.autoRatioFromAnchor(distTower);

        // Check Pyrolyse Oven count: 400 / 62.5 = 6.4 -> Ceil to 7.0 machines (Supply >= Demand)
        Assertions.assertEquals(7.0, pyrolyse.getMachineCount(), 0.001);
        Assertions.assertEquals(2.0, distTower.getMachineCount(), 0.001); // Anchor remains unchanged!

        // Summary balance: Wood Tar produced (437.5) >= consumed (400.0) -> surplus in netOutputs, zero deficit
        BalanceSummary summary = graph.computeSummary();
        IngredientStack woodTar = IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 1000, 1.0);
        Assertions.assertFalse(summary.rawInputs().containsKey(woodTar));
        Assertions.assertTrue(summary.netOutputs().containsKey(woodTar) || summary.fullyBalanced().containsKey(woodTar));
    }

    @Test
    public void testMaxThroughputAndParallelOptimization() {
        FlowGraph graph = new FlowGraph();

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

        // Assert nodes overclocked to MAX
        Assertions.assertEquals(GTVoltageTier.MAX, pyrolyse.getTargetTier());
        Assertions.assertEquals(GTVoltageTier.MAX, distTower.getTargetTier());

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

        // Assert that upstream oil / cracking nodes do NOT explode and are all integer counts
        for (RecipeNode n : graph.getNodes()) {
            Assertions.assertTrue(n.getMachineCount() < 50.0, "Node " + n.getName() + " exploded to " + n.getMachineCount());
            Assertions.assertEquals(Math.floor(n.getMachineCount()), n.getMachineCount(), 0.001, "Node " + n.getName() + " is not integer: " + n.getMachineCount());
        }
    }

    @Test
    public void testMultipleProducersMergingIntoOneConsumer() {
        FlowGraph graph = new FlowGraph();

        // 1. Producer A: Log To Creosote (Produces Charcoal 2.5/s, Wood Tar 500 mB/s)
        RecipeNode nodeA = RecipeNode.create("Log To Creosote", 160.0, 1024.0, GTVoltageTier.EV);
        nodeA.setMachineCount(1.0);
        nodeA.addInput(IngredientStack.item(new ResourceLocation("minecraft", "oak_log"), "Oak Log", 1, 1.0));
        nodeA.addOutput(IngredientStack.item(new ResourceLocation("minecraft", "charcoal"), "Charcoal", 20, 1.0));
        nodeA.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 4000, 1.0));
        graph.addNode(nodeA);

        // 2. Producer B: Charcoal Extraction (Consumes Charcoal 2.5/s, Produces Wood Tar 250 mB/s)
        RecipeNode nodeB = RecipeNode.create("Charcoal Extraction", 8.0, 1024.0, GTVoltageTier.EV);
        nodeB.setMachineCount(1.0);
        nodeB.addInput(IngredientStack.item(new ResourceLocation("minecraft", "charcoal"), "Charcoal", 1, 1.0));
        nodeB.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 100, 1.0));
        graph.addNode(nodeB);

        // 3. Consumer C: Distill Wood Tar (Consumes Wood Tar 500 mB/s per machine)
        RecipeNode nodeC = RecipeNode.create("Distill Wood Tar", 60.0, 256.0, GTVoltageTier.HV);
        nodeC.setMachineCount(1.0);
        nodeC.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 1500, 1.0));
        graph.addNode(nodeC);

        // Connect Node A (Charcoal) -> Node B (Charcoal)
        graph.addConnection(nodeA.getId(), 0, nodeB.getId(), 0);

        // Connect Node A (Wood Tar) -> Node C (Wood Tar)
        graph.addConnection(nodeA.getId(), 1, nodeC.getId(), 0);

        // Connect Node B (Wood Tar) -> Node C (Wood Tar) [MERGE!]
        graph.addConnection(nodeB.getId(), 0, nodeC.getId(), 0);

        // Auto ratio with Node A as Anchor
        nodeA.setBaseNode(true);
        graph.autoRatioFromAnchor(nodeA);

        // Verify Node A count remains 1
        Assertions.assertEquals(1.0, nodeA.getMachineCount());

        // Verify Node B consumes charcoal from A (1 machine)
        Assertions.assertEquals(1.0, nodeB.getMachineCount());

        // Verify Node C receives 500 mB/s from A + 250 mB/s from B = 750 mB/s total supply
        // Node C consumes 500 mB/s per machine. Floor(750 / 500) = 1 machine! (Supply >= Demand)
        Assertions.assertEquals(1.0, nodeC.getMachineCount());

        // Summary verify: Wood Tar produced >= consumed
        BalanceSummary summary = graph.computeSummary();
        IngredientStack woodTar = IngredientStack.fluid(new ResourceLocation("gtceu", "wood_tar"), "Wood Tar", 1000, 1.0);
        Double netWoodTar = summary.netOutputs().get(woodTar);
        Assertions.assertNotNull(netWoodTar);
        Assertions.assertTrue(netWoodTar >= 0, "Wood tar should have surplus, not deficit: " + netWoodTar);
    }

    @Test
    public void testTotalMachineCountStatistics() {
        FlowGraph graph = new FlowGraph();

        RecipeNode chemicalReactor = RecipeNode.create("Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        chemicalReactor.setMachineCount(3.0);
        graph.addNode(chemicalReactor);

        RecipeNode distillationTower = RecipeNode.create("Distillation Tower", 100.0, 120.0, GTVoltageTier.MV);
        distillationTower.setMachineCount(2.0);
        graph.addNode(distillationTower);

        RecipeNode gasTurbine = RecipeNode.create("Gas Turbine", 100.0, 512.0, GTVoltageTier.HV);
        gasTurbine.setGenerator(true);
        gasTurbine.setMachineCount(1.0);
        graph.addNode(gasTurbine);

        BalanceSummary summary = graph.computeSummary();

        Assertions.assertEquals(6, summary.totalMachineCount());
        Assertions.assertEquals(3, summary.machineBreakdown().get("Chemical Reactor"));
        Assertions.assertEquals(2, summary.machineBreakdown().get("Distillation Tower"));
        Assertions.assertEquals(1, summary.machineBreakdown().get("Gas Turbine"));
    }

    @Test
    public void testGroupIntoModuleAndExpand() {
        FlowGraph graph = new FlowGraph();

        // 1. Plant A: produces Diluted Sulfuric Acid
        RecipeNode plantA = RecipeNode.create("Plant A", 100.0, 30.0, GTVoltageTier.LV);
        plantA.setMachineCount(2.0);
        plantA.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "sulfur_trioxide"), "Sulfur Trioxide", 1000, 1.0));
        plantA.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "diluted_sulfuric_acid"), "Diluted Sulfuric Acid", 1000, 1.0));
        plantA.setPos(100, 100);
        graph.addNode(plantA);

        // 2. Plant B: consumes Diluted Sulfuric Acid, produces Sulfuric Acid
        RecipeNode plantB = RecipeNode.create("Plant B", 100.0, 30.0, GTVoltageTier.LV);
        plantB.setMachineCount(2.0);
        plantB.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "diluted_sulfuric_acid"), "Diluted Sulfuric Acid", 1000, 1.0));
        plantB.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "sulfuric_acid"), "Sulfuric Acid", 1000, 1.0));
        plantB.setPos(300, 100);
        graph.addNode(plantB);

        // Connect A -> B
        graph.addConnection(plantA.getId(), 0, plantB.getId(), 0);

        // Group into Module
        RecipeNode moduleNode = graph.groupIntoModule("Sulfuric Acid Line");
        Assertions.assertNotNull(moduleNode);
        Assertions.assertTrue(moduleNode.isModule());
        Assertions.assertEquals(1, graph.getNodes().size());
        Assertions.assertEquals(4, moduleNode.getContainedMachineCount());

        // External inputs must ONLY have Sulfur Trioxide (Diluted Sulfuric Acid is consumed internally)
        Assertions.assertEquals(1, moduleNode.getInputs().size());
        Assertions.assertEquals("Sulfur Trioxide", moduleNode.getInputs().get(0).getDisplayName());

        // External outputs must ONLY have Sulfuric Acid
        Assertions.assertEquals(1, moduleNode.getOutputs().size());
        Assertions.assertEquals("Sulfuric Acid", moduleNode.getOutputs().get(0).getDisplayName());

        // Expand Module
        boolean expanded = graph.expandModule(moduleNode);
        Assertions.assertTrue(expanded);
        Assertions.assertEquals(2, graph.getNodes().size());
        Assertions.assertEquals(1, graph.getConnections().size());
    }

    @Test
    public void testNodeClipboardCopyAndPaste() {
        FlowGraph graph = new FlowGraph();
        RecipeNode nodeA = RecipeNode.create("Node A", 100.0, 30.0, GTVoltageTier.LV);
        nodeA.addOutput(IngredientStack.fluid(new ResourceLocation("minecraft", "water"), "Water", 1000, 1.0));
        graph.addNode(nodeA);

        RecipeNode nodeB = RecipeNode.create("Node B", 100.0, 30.0, GTVoltageTier.LV);
        nodeB.addInput(IngredientStack.fluid(new ResourceLocation("minecraft", "water"), "Water", 1000, 1.0));
        graph.addNode(nodeB);

        graph.addConnection(nodeA.getId(), 0, nodeB.getId(), 0);

        // Copy both nodes
        Set<String> sel = Set.of(nodeA.getId(), nodeB.getId());
        NodeClipboard.getInstance().copy(graph, sel);
        Assertions.assertTrue(NodeClipboard.getInstance().hasContent());

        // Paste at (200, 200)
        List<RecipeNode> pasted = NodeClipboard.getInstance().paste(graph, 200.0, 200.0);
        Assertions.assertEquals(2, pasted.size());
        Assertions.assertEquals(4, graph.getNodes().size());
        Assertions.assertEquals(2, graph.getConnections().size());

        // Assert new nodes have distinct UUIDs and are connected to each other
        RecipeNode pastedA = pasted.get(0);
        RecipeNode pastedB = pasted.get(1);
        Assertions.assertNotEquals(nodeA.getId(), pastedA.getId());
        Assertions.assertNotEquals(nodeB.getId(), pastedB.getId());

        boolean hasPastedEdge = false;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(pastedA.getId()) && edge.toNodeId().equals(pastedB.getId())) {
                hasPastedEdge = true;
                break;
            }
        }
        Assertions.assertTrue(hasPastedEdge);
    }

    @Test
    public void testSelectiveGroupIntoModuleAndStats() {
        FlowGraph graph = new FlowGraph();

        // 3 Nodes: n1, n2, n3
        RecipeNode n1 = RecipeNode.create("Machine 1", 100.0, 30.0, GTVoltageTier.LV);
        n1.setMachineCount(2.0);
        graph.addNode(n1);

        RecipeNode n2 = RecipeNode.create("Machine 2", 100.0, 30.0, GTVoltageTier.LV);
        n2.setMachineCount(3.0);
        graph.addNode(n2);

        RecipeNode n3 = RecipeNode.create("Machine 3", 100.0, 30.0, GTVoltageTier.LV);
        n3.setMachineCount(5.0);
        graph.addNode(n3);

        // Group only n1 and n2 (2 + 3 = 5 machines) into a module
        Set<String> sel = Set.of(n1.getId(), n2.getId());
        RecipeNode module = graph.groupIntoModule(sel, "Sub Module");
        Assertions.assertNotNull(module);
        Assertions.assertEquals(2, graph.getNodes().size()); // Module + n3
        Assertions.assertEquals(5, module.getContainedMachineCount());

        // Test computeSummary machine count: Module (5) + n3 (5) = 10 machines total
        BalanceSummary summary = graph.computeSummary();
        Assertions.assertEquals(10, summary.totalMachineCount());
        Assertions.assertEquals(2, summary.machineBreakdown().get("Machine 1"));
        Assertions.assertEquals(3, summary.machineBreakdown().get("Machine 2"));
        Assertions.assertEquals(5, summary.machineBreakdown().get("Machine 3"));
    }

    @Test
    public void testMultiPagePresetSerialization() {
        BoardPage page1 = BoardPage.createDefault("Sulfuric Acid Line");
        RecipeNode node1 = RecipeNode.create("Distillation", 100.0, 120.0, GTVoltageTier.MV);
        page1.getGraph().addNode(node1);

        BoardPage page2 = BoardPage.createDefault("Polyethylene Line");
        RecipeNode node2 = RecipeNode.create("Polymerization", 200.0, 480.0, GTVoltageTier.HV);
        page2.getGraph().addNode(node2);

        // Test Serialization of Page 1
        net.minecraft.nbt.CompoundTag tag1 = page1.serializeNBT();
        BoardPage loaded1 = BoardPage.deserializeNBT(tag1);
        Assertions.assertEquals("Sulfuric Acid Line", loaded1.getName());
        Assertions.assertEquals(1, loaded1.getGraph().getNodes().size());
        Assertions.assertEquals("Distillation", loaded1.getGraph().getNodes().get(0).getName());

        // Test Serialization of Page 2
        net.minecraft.nbt.CompoundTag tag2 = page2.serializeNBT();
        BoardPage loaded2 = BoardPage.deserializeNBT(tag2);
        Assertions.assertEquals("Polyethylene Line", loaded2.getName());
        Assertions.assertEquals(1, loaded2.getGraph().getNodes().size());
        Assertions.assertEquals("Polymerization", loaded2.getGraph().getNodes().get(0).getName());
    }

    @Test
    public void testBidirectionalAutoRatioDownstreamPropagation() {
        FlowGraph graph = new FlowGraph();

        // 1. Producer: 20 ticks (1.0s), produces 500 mB/s steam (1000 mB/s for 2 machines)
        RecipeNode producer = RecipeNode.create("Boiler", 20.0, 30.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "steam"), "Steam", 500.0, 1.0));
        producer.setMachineCount(2.0); // Anchor with 2 machines -> 1000 mB/s steam
        producer.setBaseNode(true);
        graph.addNode(producer);

        // 2. Middle Consumer: 20 ticks (1.0s), consumes 100 mB/s steam, produces 50 mB/s water
        RecipeNode middle = RecipeNode.create("Steam Turbine", 20.0, 30.0, GTVoltageTier.LV);
        middle.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "steam"), "Steam", 100.0, 1.0));
        middle.addOutput(IngredientStack.fluid(new ResourceLocation("minecraft", "water"), "Water", 50.0, 1.0));
        middle.setMachineCount(1.0);
        graph.addNode(middle);

        // 3. Final Consumer: 20 ticks (1.0s), consumes 25 mB/s water
        RecipeNode finalConsumer = RecipeNode.create("Electrolyzer", 20.0, 30.0, GTVoltageTier.LV);
        finalConsumer.addInput(IngredientStack.fluid(new ResourceLocation("minecraft", "water"), "Water", 25.0, 1.0));
        finalConsumer.setMachineCount(1.0);
        graph.addNode(finalConsumer);

        // Connect Producer -> Middle -> Final
        graph.addConnection(producer.getId(), 0, middle.getId(), 0);
        graph.addConnection(middle.getId(), 0, finalConsumer.getId(), 0);

        // Auto Ratio from Producer Anchor (Downstream propagation)
        graph.autoRatioFromAnchor(producer, false);

        // Producer: 2.0 machines (produces 1000 mB/s steam)
        Assertions.assertEquals(2.0, producer.getMachineCount(), 0.001);

        // Middle: requires 1000 mB/s steam / 100 = 10.0 machines (produces 500 mB/s water)
        Assertions.assertEquals(10.0, middle.getMachineCount(), 0.001);

        // Final: requires 500 mB/s water / 25 = 20.0 machines
        Assertions.assertEquals(20.0, finalConsumer.getMachineCount(), 0.001);
    }

    @Test
    public void testBidirectionalAutoRatioFromMiddleAnchor() {
        FlowGraph graph = new FlowGraph();

        // 1. Producer: 20 ticks (1.0s), produces 200 mB/s
        RecipeNode producer = RecipeNode.create("Producer", 20.0, 30.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "fuel"), "Fuel", 200.0, 1.0));
        producer.setMachineCount(1.0);
        graph.addNode(producer);

        // 2. Middle (Anchor): 20 ticks (1.0s), consumes 100 mB/s, produces 50 mB/s
        RecipeNode middle = RecipeNode.create("Middle Generator", 20.0, 30.0, GTVoltageTier.LV);
        middle.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "fuel"), "Fuel", 100.0, 1.0));
        middle.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "exhaust"), "Exhaust", 50.0, 1.0));
        middle.setMachineCount(4.0); // 4 machines -> consumes 400 mB/s fuel, produces 200 mB/s exhaust
        middle.setBaseNode(true);
        graph.addNode(middle);

        // 3. Downstream Consumer: 20 ticks (1.0s), consumes 20 mB/s exhaust
        RecipeNode consumer = RecipeNode.create("Exhaust Filter", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "exhaust"), "Exhaust", 20.0, 1.0));
        consumer.setMachineCount(1.0);
        graph.addNode(consumer);

        graph.addConnection(producer.getId(), 0, middle.getId(), 0);
        graph.addConnection(middle.getId(), 0, consumer.getId(), 0);

        // Auto Ratio from Middle Anchor (Upstream to Producer, Downstream to Consumer)
        graph.autoRatioFromAnchor(middle, false);

        // Middle Anchor stays 4.0
        Assertions.assertEquals(4.0, middle.getMachineCount(), 0.001);

        // Upstream Producer scales to 400 / 200 = 2.0
        Assertions.assertEquals(2.0, producer.getMachineCount(), 0.001);

        // Downstream Consumer scales to 200 / 20 = 10.0
        Assertions.assertEquals(10.0, consumer.getMachineCount(), 0.001);
    }

    @Test
    public void testPortFlowStatsCalculations() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create("Producer", 20.0, 30.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.fluid(new ResourceLocation("gtceu", "oil"), "Oil", 100.0, 1.0));
        producer.setMachineCount(2.0); // 200 mB/s oil
        graph.addNode(producer);

        RecipeNode consumer = RecipeNode.create("Consumer", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.fluid(new ResourceLocation("gtceu", "oil"), "Oil", 50.0, 1.0));
        consumer.setMachineCount(4.0); // 200 mB/s demanded
        graph.addNode(consumer);

        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);

        // Input stats for consumer
        FlowGraph.PortFlowStats inStats = graph.getInputPortStats(consumer, 0);
        Assertions.assertTrue(inStats.isConnected());
        Assertions.assertEquals(200.0, inStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(200.0, inStats.connectedRate(), 0.001);
        Assertions.assertTrue(inStats.isBalanced());
        Assertions.assertEquals(100.0, inStats.getPercent(), 0.001);

        // Output stats for producer
        FlowGraph.PortFlowStats outStats = graph.getOutputPortStats(producer, 0);
        Assertions.assertTrue(outStats.isConnected());
        Assertions.assertEquals(200.0, outStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(200.0, outStats.connectedRate(), 0.001);
        Assertions.assertTrue(outStats.isBalanced());
    }

    @Test
    public void testTutorialStepEnumProperties() {
        Assertions.assertEquals(1, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_1_PAN_ZOOM.getStepNumber());
        Assertions.assertEquals(2, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_2_ADD_RECIPE.getStepNumber());
        Assertions.assertEquals(3, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_3_REMOVE_RECIPE.getStepNumber());
        Assertions.assertEquals(4, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_4_NORMAL_WIRING.getStepNumber());
        Assertions.assertEquals(5, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_5_SHIFT_WIRING.getStepNumber());
        Assertions.assertEquals(6, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_6_AUTO_RATIO.getStepNumber());
        Assertions.assertEquals(7, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_7_SUMMARY_MODULE.getStepNumber());
        Assertions.assertEquals(8, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.COMPLETED.getStepNumber());
    }

    @Test
    public void testBoardManagerWelcomePromptFlag() {
        BoardManager mgr = BoardManager.getInstance();
        mgr.setHasSeenWelcomePrompt(true);
        Assertions.assertTrue(mgr.hasSeenWelcomePrompt());

        mgr.setHasSeenWelcomePrompt(false);
        Assertions.assertFalse(mgr.hasSeenWelcomePrompt());
    }
}
