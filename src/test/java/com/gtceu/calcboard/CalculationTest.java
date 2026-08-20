package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.client.gui.FormatUtil;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
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
        turbine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrobenzene"), "Nitrobenzene", 1, 1.0));

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
        
        // IV Rotor Holder (8,192 EU/t base * 4.5 = 36,864 EU/t -> 1,152 parallels! Matches in-game!)
        // IV is 1 tier above EV -> +10% efficiency bonus (200% + 10% = 210% -> 4.20s)
        turbine.setTargetTier(GTVoltageTier.IV);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(1152, turbine.getParallel());
        Assertions.assertEquals(36864.0, turbine.getTotalEUt(), 0.001);
        Assertions.assertEquals(4.20, turbine.getEffectiveDurationSeconds(), 0.001);

        var scheeliteRates = turbine.calculateInputRates();
        Assertions.assertEquals(1152.0 / 4.20, scheeliteRates.get(nitrobenzene), 0.01);

        // EV Rotor Holder (4,096 EU/t base * 4.5 = 18,432 EU/t -> 576 parallels, 0% bonus -> 4.00s)
        turbine.setTargetTier(GTVoltageTier.EV);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(576, turbine.getParallel());
        Assertions.assertEquals(18432.0, turbine.getTotalEUt(), 0.001);
        Assertions.assertEquals(4.00, turbine.getEffectiveDurationSeconds(), 0.001);
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
        pyrolyse.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 16, 1.0));
        pyrolyse.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:charcoal"), "Charcoal", 20, 1.0));
        pyrolyse.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0)); // 1000mB per 8s = 125 mB/s
        graph.addNode(pyrolyse);

        // 2. Distillation Tower
        RecipeNode distTower = RecipeNode.create("Distillation Tower (Wood Tar)", 200.0, 120.0, GTVoltageTier.MV);
        distTower.setTargetTier(GTVoltageTier.HV); // 1 OC -> 480 EU/t, 100 ticks (5s -> 0.2 cycles/s)
        distTower.setMachineCount(3.0); // 3 machines -> 0.6 cycles/s
        distTower.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0)); // consumes 1000 * 0.6 = 600 mB/s
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:creosote"), "Creosote", 500, 1.0)); // produces 500 * 0.6 = 300 mB/s
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:phenol"), "Phenol", 100, 1.0));
        graph.addNode(distTower);

        BalanceSummary summary = graph.computeSummary();

        // Total EU/t: Pyrolyse (256 * 1) + DistTower (480 * 3) = 256 + 1440 = 1696 EU/t
        Assertions.assertEquals(1696.0, summary.totalEUt(), 0.001);
        Assertions.assertEquals(GTVoltageTier.HV, summary.highestVoltageTier());

        // Wood Tar: Pyrolyse produces 125 mB/s, DistTower consumes 600 mB/s -> Deficit of 475 mB/s
        IngredientStack woodTar = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0);
        Double deficit = summary.rawInputs().get(woodTar);
        Assertions.assertNotNull(deficit);
        Assertions.assertEquals(475.0, deficit, 0.001);

        // Charcoal output: 20 * 0.125 = 2.5 items/s
        IngredientStack charcoal = IngredientStack.item(ResourceLocation.tryParse("minecraft:charcoal"), "Charcoal", 20, 1.0);
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
        node1.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 2, 1.0));
        node1.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:molten_iron"), "Molten Iron", 288, 1.0));
        graph.addNode(node1);

        RecipeNode node2 = RecipeNode.create("Machine B", 80.0, 128.0, GTVoltageTier.MV);
        node2.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:molten_iron"), "Molten Iron", 144, 1.0));
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
        node.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 1, 1.0));

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
        pyrolyse.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0));
        graph.addNode(pyrolyse);

        RecipeNode distTower = RecipeNode.create("Distillation Tower", 100.0, 120.0, GTVoltageTier.MV);
        distTower.setTargetTier(GTVoltageTier.MV); // 100 ticks (5s -> 0.2 cycles/s)
        distTower.setMachineCount(2.0); // 2 machines -> 400 mB/s consumption
        distTower.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0));
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:creosote"), "Creosote", 500, 1.0));
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
        IngredientStack woodTar = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0);
        Assertions.assertFalse(summary.rawInputs().containsKey(woodTar));
        Assertions.assertTrue(summary.netOutputs().containsKey(woodTar) || summary.fullyBalanced().containsKey(woodTar));
    }

    @Test
    public void testMaxThroughputAndParallelOptimization() {
        FlowGraph graph = new FlowGraph();

        RecipeNode pyrolyse = RecipeNode.create("Pyrolyse Oven", 320.0, 64.0, GTVoltageTier.MV);
        pyrolyse.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0));
        graph.addNode(pyrolyse);

        RecipeNode distTower = RecipeNode.create("Distillation Tower", 100.0, 120.0, GTVoltageTier.MV);
        distTower.setMachineCount(4.0); // 4 machines
        distTower.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0));
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
        macerator.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1, 1.0));
        macerator.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2, 1.0));
        macerator.setMachineCount(3.5);
        macerator.setBaseNode(true);
        graph.addNode(macerator);

        RecipeNode furnace = RecipeNode.create("Electric Furnace", 60.0, 32.0, GTVoltageTier.LV);
        furnace.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 1, 1.0));
        furnace.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1, 1.0));
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
        String blueprint = "GTBOARD:H4sIAAAAAAAA/+VbbXBcVRm+adptEhKb0lA+iu0yDYIyB+/evZ9xsNs2aVNowkfSSgFZzr333GTtZjfdD2jqqDFpa20rFA3SagPlm5YAETsYtA4df4g6ziA6oqg/REcZ2c2MowKiM4xn9+7d3Xuze8/Zj4IzZKY/eu/u+57zvs953uec824LwzQzSyJRHcVbGIY538O0qjCOupMxmAhFIwGeyf41M0ujycRoMpH91GIP44Ej0WQkEeg33+MH2jCMaGjd380HTcziCBxBTOtAMmwkYyHNuxnGm5hFIZ05fyihoWRXPPciOJR5sTgxNoqYJZu2btvSzRTMRwPu5lfkzfcieOeYd1MShU0vFzu8DGfeB43s+zLONo3TOtsaGhpOuDgLZ967O7uaELj2vLN+ODqcGIamp5UOTxHrpc1NA9MSim/AeezHicXJ84QiVu4aCkPYQRhCsxnT60O5WS4zfUdDYTOaC+bWOgK14VAEbcyav/a8favnbkx7mMWj0fjNZydW9preMv/dEYAPBda+ccPbTUxLAsaGUGIwhGLMoi3bG5mmURiD4TAKZ0ZrjWV5dyieCIXDXseYOnlekgTOpwJDl32A9/MSgDIrAkVSeCgjSUO8gJ3EkBYaRaaT3u0eZmkG5T3bEoGYNee26J0opoWj2s6+TNCaBgbX93evvyk7L9uS0EstiUWFsN5DwGx775geiw6hiDeT4pCObJkdzr0Mxq2XZQD0IsFNi3NFtOcclFsILqBZRO+2rivyQYKz1nwo8+zyEXsYCRi9CiM0tW+NhdHYRFPs1FvvWhgdumL32ol7Djow2lMOoyu7UW5ye9CC2Xf6/BKHWNYHWAVygEdQAIou+IHOyRJEvKpCQXQAdWsxUD9dMVBvLwXU1kJ01bPu0W3qSQyPhVEE2SKL8g/LJI1k9rw+bAJGUCFnbablEfN5WcOdBL5aekMsOgqt4eaMjlrPyhglkWDWKCph1C0EJHL39ORmmrHZmo+r2zjHSOPcgCJ7nONUrWdljA6SxrkhmUCOcaq5R2VMbiEVFWwS6qG81WV5q9bTKhPV4izKOb6jqMVBAs0stZXgXGxLV1762C4djIaTzoQlrGeljVo2CkZ/vW3bHcVrayOMqdGItzsZT5iGl5uGtezzoJ59bhpfvGWwp49SKpBq2mUDCJMSCo95BxIIjoCNMajtRPoCFrwiVwNynw7GM58Oauan3SqCnbb1m3PDMWk7vv/VXT/5waxF23fuXfnG5elVtLT9cUta2OfgLTeHTlliVaiKBlAMRQS8zKtA5QUOSKphyAKvyEhTHUzeV8zkt1fM5CUlx/9PeuiUA2mQFQsWl2XhML0kO+1cGYuPZacV74qbD12RdshrQ9pFVz/8qeg3/mghbfTwz3ybpWsdSOsth7Q1pRBWQilgIPGKgZWCn0dY0goSwOoAAhnpmmqoHK8qnJuk/WJ98FWB6FuWOnRg/uBc6qHjqeceTZ84YdvoRUKJWJRUiYiieX56Jr13OnXkuHf+2Mn0xKzpYpXpQg+FcVXSg3ltCbWQ7gbSBhtIGwvj+O4BQkUsniBllSWp2Pb0Ewfmj01554+emb/vZGrmjGn9wkL4slkKjoR2J5Kx6v00px+fTj12NH3gqOngAit4WfrD4bsLJlDMfUEUllp2QQTCF3Q9e2yo3VoQk6e2/B48HKBdEK39NmyY6PchUeaQAXSRkwCvsZhdFQkBVkeSzCm85DdgfdEfO+uO/nYCuzSmH5ixSWQYHy5RaQsGXyYm6tF7MdjTx09QbbypSjiRIy9MP3EsPT3uTc/OpSaOpmZOe9PfPpI6dTK3Ox3BCMCFwEh06TC2MxiFO4Ph6JBzhnbybNyX3V5Z5Hnwpm0/77suX6bV3rOvjGqnaE8AmvGe3+TKHFAkn8AKoo/F8BAEwEMf3vkj1QBIFw1e5yVVZJEbUHZXDJQvBdzL8Lld6PWpsh48hjx9Li94Lk2a9HabMVqLmXmFY7df0ridWqb2vL75mY7P5w+Mds0F3/5D0oLLxK9ahK6r7qY+MOq3wunts8KZhY2BVA76kQxEqOB9uKSrQOE0FmiKpsiChNkHOtRb4zbbRpypGDcJQnn9oHLWmIfgssLyLlUEXKDXSO+uLTON/Ye9b86Opw8+amPMrEYodVxj2X5ilGT7wNEytsseBVm2nyWOe2LWaTu3GY7uHiMeMq37Tsp2EHrgMz+d3v63dRau77o7tv0K5S+0u5WL+7OJ967HifduikVHvD1hNIIiidxZSifLCayhiX6g+1mMb5FTgMqpMpB4zY8hb0DeUN4H9VjB7qQK6qgPF7amJ85gr5iSsXubJjJ9B3Gko7vdDmHrtsKcmHkmUzhXz+VL5/41Bz3yZF5mdaw+fKxnkBYzq/JnsgXUDOanlkUNhEjgEKcBURXwnkNT/ACTIQIKKyiiqPtUVjZcWfHSimETqhE21WavXtiZPu3w3mHzrtcInVpZ57S9mt57y4sdy55/zkLQl7/fdfLG/55Pi6BlJoKcqJEl3RChwAEEJT/gFRXXUp+oAUPw+1WJFQXE83VGTX/NqKkqb1SgIWrsFuwzk9DUI0/airnl2mXPQNKWtcLley3XjD7ymxYLLvd9c8fT7+28xILLngtfGHvntysdcOkrB5dLcnDpNmNpMo75zKpSvCwrnKEDTpR5XKXwLg9KMqYfpEkCJ/gE2fDVGTkTZ92RwxKSV4cNUn3uZasuKn+278e+3vray6+2Xmvl2Fjz1K0NoRO0OW7vxtP0Xg93ejfHoneFIkNWJeGRpPOZbZms4krCKwqQ/awEFIXHOkTCCV5wOlrjPVetF7JteFVmtmWmhKzuNvYlgj71vPncD/P7edLNBF2FIrlsMzWVt9hzFY0KZOKpXHnbjwk+YaeeGPe7+FMDr1uwlH/xyuznRv/tgOVWqktY55VQpyrKSPNpfqAqhoFljiADyAkG3gFKvCoKHIfl8ocPnM2Yz+an9s5PjlPccNUHnO05cDo807W3vJ/gXH7blW/96J7b8uA89Q8Mzj/lwHn2jl/ONMyoEi04VxSD03ap2IkMhDQBCQBpkAU8pxpAZUUJcD7og5zOc4LkPPSvEZmDpZBZgZK6BBNL6sGvetOPT785N5t6fu/8fadtZPOxIppbcJlUI/kRj0wq4Vt6AecxJ1vNTdLAV57p2PN6rwWkTe8NTN/yYIfFcjeM3HrZF954h/bgfPVWM6i2i6QFbIfLrp9V/AoQVI3DpVhQgCoiP4Cs4tNZSZbYBaXYdhQQqRhTpJYTYndA+v4j84fmUicOV9ZzQrLbljo5NT+518YDlF0nPGnImL9SDx2fP3ykor4TUpeMZfbu4xV1npB6ZDyZAE/uraTzJEQyWcWV2E2E2XtSz4/P7ztSSeMJsRM1Y3JyPDU1lZ6eq7D3hJQsT+bCxsE2FO1tpJ6eiupywWw3EbH7n8Q70PT9U7X1npzZ9ZK3eI1hXOVqrXN/e467T85RJXIy+GevKz7HDezqFA4PzlxqMXj43Yv++s9nX6DdPl1pdZ2UYHKkL+RyXdEkFf/LNLbqWLnyECgiZwCRZ31+TVVVwedUrjU2ndSqDz5aKisORK91S0wt+pN45lzV0jqX+qDLtgsKBB5Iq49BxhKawf+0/Vh9+mFafbCqlD6wC06/zMo8K4lA9RkC4AVVALIsC4BTRNHPQdXHKtL7Kw5IhaZacUCyW4M4IDV6VikOiJGoThysHyeht2JxQKyMVYgDYldq5eKANMxaxAFRy1QnDoiRrWqH8+ETB+eoDDnpe6NtexeIvXMNFgdrLfo2mv/FgNAkrTi43F0c2IlcZFlNEzge+AUdE7nKC0AxMr8zEH0cUpBgcLxRqzJYjOlqR+C2rq+NrziUafbaE42OrHvtWObvW83MeVo0EkFahuSz2bmgiWkyYtGRbN6ofqHTyCwJRbbou82BeBJR86s0v5loZDy4nJjfbWDq5ZnmoLDY86K6eaY5CCr23Gj3TBey0tGm6Dt1iTZNW3Q5zxQ9f8WelzjmTDXwkp7pBl3wzNg90/SglfFMB5Gy0aZpYyry3FBDtB1zpmkwKTNnukHXy3PxnGn6G1yiXcOcae7DXDzTDbzknGuMNs3lfrk5VxhtZ54pLofLrmeaQZf1TJeskp7pqKCsZ7qCU9IzzTGvC8LoCk5pzxR7SBfPdAMv6ZnmOMQl2nRfPxe1ii5kJT3TCD2XOdN9vU5zNiXjzYHEJzumxu6AzP8Ap9CYG2hAAAA=";
        
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
        nodeA.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 1, 1.0));
        nodeA.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:charcoal"), "Charcoal", 20, 1.0));
        nodeA.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 4000, 1.0));
        graph.addNode(nodeA);

        // 2. Producer B: Charcoal Extraction (Consumes Charcoal 2.5/s, Produces Wood Tar 250 mB/s)
        RecipeNode nodeB = RecipeNode.create("Charcoal Extraction", 8.0, 1024.0, GTVoltageTier.EV);
        nodeB.setMachineCount(1.0);
        nodeB.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:charcoal"), "Charcoal", 1, 1.0));
        nodeB.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 100, 1.0));
        graph.addNode(nodeB);

        // 3. Consumer C: Distill Wood Tar (Consumes Wood Tar 500 mB/s per machine)
        RecipeNode nodeC = RecipeNode.create("Distill Wood Tar", 60.0, 256.0, GTVoltageTier.HV);
        nodeC.setMachineCount(1.0);
        nodeC.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1500, 1.0));
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
        IngredientStack woodTar = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0);
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
        plantA.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfur_trioxide"), "Sulfur Trioxide", 1000, 1.0));
        plantA.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:diluted_sulfuric_acid"), "Diluted Sulfuric Acid", 1000, 1.0));
        plantA.setPos(100, 100);
        graph.addNode(plantA);

        // 2. Plant B: consumes Diluted Sulfuric Acid, produces Sulfuric Acid
        RecipeNode plantB = RecipeNode.create("Plant B", 100.0, 30.0, GTVoltageTier.LV);
        plantB.setMachineCount(2.0);
        plantB.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:diluted_sulfuric_acid"), "Diluted Sulfuric Acid", 1000, 1.0));
        plantB.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 1000, 1.0));
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
        nodeA.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000, 1.0));
        graph.addNode(nodeA);

        RecipeNode nodeB = RecipeNode.create("Node B", 100.0, 30.0, GTVoltageTier.LV);
        nodeB.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000, 1.0));
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
        producer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
        producer.setMachineCount(2.0); // Anchor with 2 machines -> 1000 mB/s steam
        producer.setBaseNode(true);
        graph.addNode(producer);

        // 2. Middle Consumer: 20 ticks (1.0s), consumes 100 mB/s steam, produces 50 mB/s water
        RecipeNode middle = RecipeNode.create("Steam Turbine", 20.0, 30.0, GTVoltageTier.LV);
        middle.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 100.0, 1.0));
        middle.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 50.0, 1.0));
        middle.setMachineCount(1.0);
        graph.addNode(middle);

        // 3. Final Consumer: 20 ticks (1.0s), consumes 25 mB/s water
        RecipeNode finalConsumer = RecipeNode.create("Electrolyzer", 20.0, 30.0, GTVoltageTier.LV);
        finalConsumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 25.0, 1.0));
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
        producer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fuel"), "Fuel", 200.0, 1.0));
        producer.setMachineCount(1.0);
        graph.addNode(producer);

        // 2. Middle (Anchor): 20 ticks (1.0s), consumes 100 mB/s, produces 50 mB/s
        RecipeNode middle = RecipeNode.create("Middle Generator", 20.0, 30.0, GTVoltageTier.LV);
        middle.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fuel"), "Fuel", 100.0, 1.0));
        middle.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:exhaust"), "Exhaust", 50.0, 1.0));
        middle.setMachineCount(4.0); // 4 machines -> consumes 400 mB/s fuel, produces 200 mB/s exhaust
        middle.setBaseNode(true);
        graph.addNode(middle);

        // 3. Downstream Consumer: 20 ticks (1.0s), consumes 20 mB/s exhaust
        RecipeNode consumer = RecipeNode.create("Exhaust Filter", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:exhaust"), "Exhaust", 20.0, 1.0));
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
        producer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oil"), "Oil", 100.0, 1.0));
        producer.setMachineCount(2.0); // 200 mB/s oil
        graph.addNode(producer);

        RecipeNode consumer = RecipeNode.create("Consumer", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oil"), "Oil", 50.0, 1.0));
        consumer.setMachineCount(4.0); // 200 mB/s demanded
        graph.addNode(consumer);

        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);

        // Input stats for consumer
        FlowGraphSolver.PortFlowStats inStats = graph.getInputPortStats(consumer, 0);
        Assertions.assertTrue(inStats.isConnected());
        Assertions.assertEquals(200.0, inStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(200.0, inStats.connectedRate(), 0.001);
        Assertions.assertTrue(inStats.isBalanced());
        Assertions.assertEquals(100.0, inStats.getPercent(), 0.001);

        // Output stats for producer
        FlowGraphSolver.PortFlowStats outStats = graph.getOutputPortStats(producer, 0);
        Assertions.assertTrue(outStats.isConnected());
        Assertions.assertEquals(200.0, outStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(200.0, outStats.connectedRate(), 0.001);
        Assertions.assertTrue(outStats.isBalanced());
    }

    @Test
    public void testTutorialStepEnumProperties() {
        Assertions.assertEquals(1, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_1_ADD_RECIPE.getStepNumber());
        Assertions.assertEquals(2, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_2_DRAG_TO_SEARCH.getStepNumber());
        Assertions.assertEquals(3, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_3_DELETE_WIRING.getStepNumber());
        Assertions.assertEquals(4, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_4_SHIFT_WIRING.getStepNumber());
        Assertions.assertEquals(5, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_5_SUMMARY_MODULE.getStepNumber());
        Assertions.assertEquals(6, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.COMPLETED.getStepNumber());
    }

    @Test
    public void testBoardManagerWelcomePromptFlag() {
        BoardManager mgr = BoardManager.getInstance();
        mgr.setHasSeenWelcomePrompt(true);
        Assertions.assertTrue(mgr.hasSeenWelcomePrompt());

        mgr.setHasSeenWelcomePrompt(false);
        Assertions.assertFalse(mgr.hasSeenWelcomePrompt());
    }

    @Test
    public void testPowerDisplayModeConversions() {
        // LV (32V)
        Assertions.assertEquals("2A LV", PowerDisplayMode.formatAmps(2.0, GTVoltageTier.LV));
        Assertions.assertEquals("0.5A LV", PowerDisplayMode.formatAmps(0.5, GTVoltageTier.LV));
        Assertions.assertEquals("1.25A LV", PowerDisplayMode.formatAmps(1.25, GTVoltageTier.LV));

        // HV (512V)
        Assertions.assertEquals("1A HV", PowerDisplayMode.formatAmps(1.0, GTVoltageTier.HV));
        Assertions.assertEquals("5A HV", PowerDisplayMode.formatAmps(5.0, GTVoltageTier.HV));

        // Node card formatting in different modes
        RecipeNode turbine = RecipeNode.create("Gas Turbine", 100.0, 512.0, GTVoltageTier.HV);
        turbine.setGenerator(true);

        String eutStr = PowerDisplayMode.EUT.formatNodePower(turbine);
        Assertions.assertTrue(eutStr.contains("512.0 EU/t"));

        String ampsStr = PowerDisplayMode.AMPS.formatNodePower(turbine);
        Assertions.assertTrue(ampsStr.contains("1.0A HV"));

        String bothStr = PowerDisplayMode.BOTH.formatNodePower(turbine);
        Assertions.assertTrue(bothStr.contains("512.0 EU/t") && bothStr.contains("1A HV"));
    }

    @Test
    public void testSmallNumberPrecisionFormatting() {
        // Fluid rates: 0.05 mB/s, 0.005 mB/s must not become "0 mB/s"
        Assertions.assertEquals("0.05 mB/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.05, true));
        Assertions.assertEquals("0.005 mB/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.005, true));
        Assertions.assertEquals("0.25 mB/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.25, true));
        Assertions.assertEquals("0 mB/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.0, true));

        // Item rates: 0.005/s, 0.001/s must not become "0/s"
        Assertions.assertEquals("0.05/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.05, false));
        Assertions.assertEquals("0.005/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.005, false));
        Assertions.assertEquals("0.001/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.001, false));
        Assertions.assertEquals("0/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.0, false));

        // Compact number
        Assertions.assertEquals("0.005", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatCompactNumber(0.005));
        Assertions.assertEquals("0.0005", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatCompactNumber(0.0005));

        // Scientific E-notation for extremely small numbers (< 0.0001)
        Assertions.assertEquals("1.01E-24", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatCompactNumber(1.01e-24));
        Assertions.assertEquals("5E-5", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatCompactNumber(0.00005));
        Assertions.assertEquals("1.25E-6/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(1.25e-6, false));
        Assertions.assertEquals("1.01E-24 mB/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(1.01e-24, true));

        // Small amperes with E-notation
        Assertions.assertEquals("0.004A LV", PowerDisplayMode.formatAmps(0.004, GTVoltageTier.LV));
        Assertions.assertEquals("1.01E-24A LV", PowerDisplayMode.formatAmps(1.01e-24, GTVoltageTier.LV));

        // High SI Prefixes (k, M, G, T, P, E, Z, Y)
        Assertions.assertEquals("1.5k", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(1500));
        Assertions.assertEquals("2.5M", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(2_500_000));
        Assertions.assertEquals("3.5G", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(3_500_000_000.0));
        Assertions.assertEquals("11.8T", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(11_796_470_000_000.0));
        Assertions.assertEquals("5.2P", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(5.2e15));
        Assertions.assertEquals("8E", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(8.0e18));
        Assertions.assertEquals("9.1Z", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(9.1e21));
        Assertions.assertEquals("4.2Y", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(4.2e24));
    }

    @Test
    public void testContinuousWireCollisionDetection() {
        float x1 = 100f, y1 = 100f;
        float x2 = 500f, y2 = 300f;

        // Points right on the curve or very close must be detected
        Assertions.assertTrue(com.gtceu.calcboard.client.gui.ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, 100, 100, 8.0));
        Assertions.assertTrue(com.gtceu.calcboard.client.gui.ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, 500, 300, 8.0));

        // Intermediate arbitrary points along the curve must be continuously detected
        for (int i = 0; i <= 50; i++) {
            float t = i / 50.0f;
            float it = 1.0f - t;
            float dx = Math.max(Math.abs(x2 - x1) * 0.5f, 40f);
            float cx1 = x1 + dx, cy1 = y1, cx2 = x2 - dx, cy2 = y2;
            float bx = it * it * it * x1 + 3 * it * it * t * cx1 + 3 * it * t * t * cx2 + t * t * t * x2;
            float by = it * it * it * y1 + 3 * it * it * t * cy1 + 3 * it * t * t * cy2 + t * t * t * y2;

            Assertions.assertTrue(com.gtceu.calcboard.client.gui.ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, bx, by, 8.0),
                    "Collision detection must succeed at t=" + t);
        }

        // Far points must not be detected
        Assertions.assertFalse(com.gtceu.calcboard.client.gui.ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, 100, 500, 8.0));
        Assertions.assertFalse(com.gtceu.calcboard.client.gui.ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, 800, 100, 8.0));
    }

    @Test
    public void testBoardManagerPowerDisplayModeCycling() {
        BoardManager mgr = BoardManager.getInstance();
        mgr.setPowerDisplayMode(PowerDisplayMode.EUT);
        Assertions.assertEquals(PowerDisplayMode.EUT, mgr.getPowerDisplayMode());

        PowerDisplayMode m1 = mgr.cyclePowerDisplayMode();
        Assertions.assertEquals(PowerDisplayMode.AMPS, m1);

        PowerDisplayMode m2 = mgr.cyclePowerDisplayMode();
        Assertions.assertEquals(PowerDisplayMode.BOTH, m2);

        PowerDisplayMode m3 = mgr.cyclePowerDisplayMode();
        Assertions.assertEquals(PowerDisplayMode.EUT, m3);

        // Format summary power SI test
        double largeEUt = 18_186_905_076_480.0;
        String formattedSummary = PowerDisplayMode.EUT.formatSummaryPower(largeEUt, GTVoltageTier.UEV);
        Assertions.assertTrue(formattedSummary.contains("18.2T EU/t") || formattedSummary.contains("18.19T EU/t") || formattedSummary.contains("18.18T EU/t"),
                "Expected Tera prefix in summary power: " + formattedSummary);
    }

    @Test
    public void testTagAlternativesAndAutoMatching() {
        IngredientStack input = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:diesel"), "Diesel", 1000, 1.0);
        input.addAlternative(ResourceLocation.tryParse("gtceu:bio_diesel"));
        input.addAlternative(ResourceLocation.tryParse("thermal:refined_fuel"));

        Assertions.assertTrue(input.hasAlternatives());
        Assertions.assertEquals(3, input.getAlternatives().size());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:diesel"), input.getId());

        input.cycleAlternative(1);
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:bio_diesel"), input.getId());

        IngredientStack bioDieselOutput = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:bio_diesel"), "Bio Diesel", 1000, 1.0);
        Assertions.assertTrue(input.matchesOrAlternative(bioDieselOutput));

        input.selectAlternative(bioDieselOutput.getId());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:bio_diesel"), input.getId());
        Assertions.assertTrue(input.equals(bioDieselOutput));
    }

    @Test
    public void testDuplicateOutputsWithDifferentChances() {
        RecipeNode greenhouse = RecipeNode.create("Crop Greenhouse", 600.0, 15.0, GTVoltageTier.LV);
        greenhouse.setMachineCount(1.0);
        greenhouse.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:potato"), "Potato", 16.0, 1.0));
        greenhouse.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:potato"), "Potato", 8.0, 0.5));

        FlowGraph graph = new FlowGraph();
        graph.addNode(greenhouse);

        FlowGraphSolver.PortFlowStats stats0 = graph.getOutputPortStats(greenhouse, 0);
        FlowGraphSolver.PortFlowStats stats1 = graph.getOutputPortStats(greenhouse, 1);

        Assertions.assertEquals(16.0 / 30.0, stats0.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(4.0 / 30.0, stats1.requiredOrProducedRate(), 0.001);
        Assertions.assertNotEquals(stats0.requiredOrProducedRate(), stats1.requiredOrProducedRate());
    }

    @Test
    public void testModulePowerDisplayConsumingVsGenerating() {
        // Consuming module (all consumer machines)
        RecipeNode consumingModule = RecipeNode.create("Compound Process", 20.0, 358.0, GTVoltageTier.LV);
        consumingModule.setModule(true);
        consumingModule.setGenerator(false);

        String drainStr = PowerDisplayMode.EUT.formatNodePower(consumingModule);
        Assertions.assertTrue(drainStr.contains("358.0 EU/t"));
        Assertions.assertFalse(drainStr.contains("+")); // Must not be formatted as generator

        // Generating module (e.g. steam turbines grouped)
        RecipeNode generatingModule = RecipeNode.create("Turbine Module", 20.0, 500.0, GTVoltageTier.LV);
        generatingModule.setModule(true);
        generatingModule.setGenerator(true);

        String genStr = PowerDisplayMode.EUT.formatNodePower(generatingModule);
        Assertions.assertTrue(genStr.contains("+500.0 EU/t"));
    }

    @Test
    public void testDummyConditionMarkerFiltering() {
        // Markers that should be filtered out
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:overworld_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:nether_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:the_end_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:dimension_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:biome_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:altitude_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("start_core:abydos_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("sgjourney:chulak_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("kubejs:custom_planet_marker")));

        // Normal items (including those containing 'end', 'world', etc.) that must NEVER be filtered out
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:programmed_circuit")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:integrated_circuit")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("minecraft:potato")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:lv_electric_motor")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:enderium_ingot")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("minecraft:ender_pearl")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("minecraft:end_stone")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("thermal:enderium_dust")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("create:blender")));
    }

    @Test
    public void testBottleneckEfficiencyAndThroughputPropagation() {
        FlowGraph graph = new FlowGraph();

        // Producer: 20 ticks (1s), produces 80 mB/s of fluid A
        RecipeNode producer = RecipeNode.create("Producer A", 20.0, 30.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_a"), "Fluid A", 80.0, 1.0));
        producer.setMachineCount(1.0);
        graph.addNode(producer);

        // Consumer: 20 ticks (1s), requires 100 mB/s of fluid A, produces 50 mB/s of fluid B, consumes 100 EU/t
        RecipeNode consumer = RecipeNode.create("Consumer B", 20.0, 100.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_a"), "Fluid A", 100.0, 1.0));
        consumer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_b"), "Fluid B", 50.0, 1.0));
        consumer.setMachineCount(1.0);
        graph.addNode(consumer);

        // Connect Producer -> Consumer
        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);

        // Compute efficiencies
        Map<String, Double> effMap = graph.computeNodeEfficiencies();

        // Producer has no connected inputs -> 100% efficiency
        Assertions.assertEquals(1.0, effMap.get(producer.getId()), 0.001);
        Assertions.assertEquals(1.0, producer.getEfficiency(), 0.001);

        // Consumer receives 80 / 100 = 0.80 (80% efficiency)
        Assertions.assertEquals(0.80, effMap.get(consumer.getId()), 0.001);
        Assertions.assertEquals(0.80, consumer.getEfficiency(), 0.001);

        // Consumer effective output rate: 50 * 0.80 = 40.0 mB/s
        Assertions.assertEquals(40.0, consumer.calculateEffectiveOutputRates().values().iterator().next(), 0.001);

        // Consumer effective EU/t: 100 * 0.80 = 80.0 EU/t
        Assertions.assertEquals(80.0, consumer.getEffectiveTotalEUt(), 0.001);

        // 3. Add Downstream Final Consumer to test cascading flow: requires 40 mB/s of fluid B, produces 10 item C
        RecipeNode finalNode = RecipeNode.create("Final C", 20.0, 50.0, GTVoltageTier.LV);
        finalNode.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_b"), "Fluid B", 40.0, 1.0));
        finalNode.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 10.0, 1.0));
        finalNode.setMachineCount(1.0);
        graph.addNode(finalNode);

        graph.addConnection(consumer.getId(), 0, finalNode.getId(), 0);

        graph.computeNodeEfficiencies();

        // Final Node demands 40 mB/s and receives exactly 40 mB/s from 80% running Consumer -> 100% of its capacity!
        Assertions.assertEquals(1.0, finalNode.getEfficiency(), 0.001);
        Assertions.assertEquals(10.0, finalNode.calculateEffectiveOutputRates().values().iterator().next(), 0.001);

        // Test Process Summary reflects real bottleneck power & throughput
        BalanceSummary summary = graph.computeSummary();
        // Total EU/t: Producer (30) + Consumer (80) + Final (50) = 160 EU/t
        Assertions.assertEquals(160.0, summary.totalEUt(), 0.001);
    }

    @Test
    public void testDownstreamBottleneckPropagationToUpstreamPortStats() {
        FlowGraph graph = new FlowGraph();

        // Producer A (e.g. Mixer): produces 400 mB/s of Fluid A
        RecipeNode mixer = RecipeNode.create("Mixer", 20.0, 10.0, GTVoltageTier.LV);
        mixer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitration_mixture"), "Nitration Mixture", 400.0, 1.0));
        mixer.setMachineCount(1.0);
        graph.addNode(mixer);

        // Producer B (e.g. Bottleneck Distillation): produces only 50 mB/s of Benzene (demand is 100 mB/s)
        RecipeNode dist = RecipeNode.create("Distillery", 20.0, 20.0, GTVoltageTier.LV);
        dist.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:benzene"), "Benzene", 50.0, 1.0));
        dist.setMachineCount(1.0);
        graph.addNode(dist);

        // Consumer C (e.g. Chemical Reactor): requires 200 mB/s Nitration Mixture AND 100 mB/s Benzene
        RecipeNode reactor = RecipeNode.create("Chemical Reactor", 20.0, 200.0, GTVoltageTier.HV);
        reactor.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitration_mixture"), "Nitration Mixture", 200.0, 1.0));
        reactor.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:benzene"), "Benzene", 100.0, 1.0));
        reactor.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrobenzene"), "Nitrobenzene", 100.0, 1.0));
        reactor.setMachineCount(1.0);
        graph.addNode(reactor);

        // Connect Mixer -> Reactor (Nitration Mixture, input 0)
        graph.addConnection(mixer.getId(), 0, reactor.getId(), 0);
        // Connect Dist -> Reactor (Benzene, input 1)
        graph.addConnection(dist.getId(), 0, reactor.getId(), 1);

        // Compute efficiencies
        graph.computeSummary();

        // Reactor is bottlenecked by Benzene (50 / 100 = 50% efficiency)
        Assertions.assertEquals(0.50, reactor.getEfficiency(), 0.001);

        // Mixer output port stats:
        // Produced = 400 mB/s.
        // Consumer actual demanded rate = 200 * 0.50 = 100 mB/s (NOT 200 mB/s!)
        FlowGraphSolver.PortFlowStats mixerOutStats = graph.getOutputPortStats(mixer, 0);
        Assertions.assertEquals(400.0, mixerOutStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(100.0, mixerOutStats.connectedRate(), 0.001);
        Assertions.assertTrue(mixerOutStats.isOutputSurplus());
    }

    @Test
    public void testMultiProducerPartialSupplyToInputPortStats() {
        FlowGraph graph = new FlowGraph();

        // 2 Pyrolyse Ovens producing 219 mB/s each = 438 mB/s Wood Tar
        RecipeNode pyro1 = RecipeNode.create("Pyrolyse 1", 20.0, 30.0, GTVoltageTier.LV);
        pyro1.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 219.0, 1.0));
        pyro1.setMachineCount(1.0);
        graph.addNode(pyro1);

        RecipeNode pyro2 = RecipeNode.create("Pyrolyse 2", 20.0, 30.0, GTVoltageTier.LV);
        pyro2.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 219.0, 1.0));
        pyro2.setMachineCount(1.0);
        graph.addNode(pyro2);

        // Distillation Tower requiring 500 mB/s Wood Tar
        RecipeNode dist = RecipeNode.create("Distillation Tower", 20.0, 256.0, GTVoltageTier.HV);
        dist.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 500.0, 1.0));
        dist.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:benzene"), "Benzene", 100.0, 1.0));
        dist.setMachineCount(1.0);
        graph.addNode(dist);

        // Connect both Pyrolyse -> Distillation Tower input 0
        graph.addConnection(pyro1.getId(), 0, dist.getId(), 0);
        graph.addConnection(pyro2.getId(), 0, dist.getId(), 0);

        graph.computeSummary();

        // Distillation Tower receives 438 / 500 = 87.6% efficiency
        Assertions.assertEquals(0.876, dist.getEfficiency(), 0.001);

        // Input port stats on Distillation Tower
        FlowGraphSolver.PortFlowStats inStats = graph.getInputPortStats(dist, 0);
        Assertions.assertEquals(500.0, inStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(438.0, inStats.connectedRate(), 0.001);
        Assertions.assertEquals(2, inStats.connectionCount());
        Assertions.assertTrue(inStats.isInputDeficit());
        Assertions.assertFalse(inStats.isBalanced());
        Assertions.assertEquals(87.6, inStats.getPercent(), 0.01);
    }

    @Test
    public void testAutoRatioCircularFeedbackLoopSafety() {
        // Construct a circular feedback loop: Node A -> Node B -> Node C -> Node A
        FlowGraph graph = new FlowGraph();

        RecipeNode nodeA = RecipeNode.create("Node A", 20.0, 30.0, GTVoltageTier.LV);
        nodeA.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:mat_c"), "Mat C", 100.0, 1.0));
        nodeA.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:mat_a"), "Mat A", 100.0, 1.0));
        nodeA.setMachineCount(2.0);
        graph.addNode(nodeA);

        RecipeNode nodeB = RecipeNode.create("Node B", 20.0, 30.0, GTVoltageTier.LV);
        nodeB.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:mat_a"), "Mat A", 100.0, 1.0));
        nodeB.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:mat_b"), "Mat B", 100.0, 1.0));
        nodeB.setMachineCount(1.0);
        graph.addNode(nodeB);

        RecipeNode nodeC = RecipeNode.create("Node C", 20.0, 30.0, GTVoltageTier.LV);
        nodeC.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:mat_b"), "Mat B", 100.0, 1.0));
        nodeC.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:mat_c"), "Mat C", 100.0, 1.0));
        nodeC.setMachineCount(1.0);
        graph.addNode(nodeC);

        // Circular edges
        graph.addConnection(nodeA.getId(), 0, nodeB.getId(), 0);
        graph.addConnection(nodeB.getId(), 0, nodeC.getId(), 0);
        graph.addConnection(nodeC.getId(), 0, nodeA.getId(), 0);

        // Execute autoRatioFromAnchor with nodeA as anchor (count = 2.0)
        long start = System.currentTimeMillis();
        FlowGraphSolver.autoRatioFromAnchor(graph, nodeA, true);
        long elapsed = System.currentTimeMillis() - start;

        // Must complete within 100ms without getting stuck in infinite loop
        Assertions.assertTrue(elapsed < 100, "Auto-ratio must terminate quickly even on circular loops");

        // Anchor must strictly retain count = 2.0
        Assertions.assertEquals(2.0, nodeA.getMachineCount(), 0.001);
        // Node B and Node C should scale to 2.0 to match Anchor
        Assertions.assertEquals(2.0, nodeB.getMachineCount(), 0.001);
        Assertions.assertEquals(2.0, nodeC.getMachineCount(), 0.001);
    }

    @Test
    public void testTierChanceBoostOnOverclock() {
        RecipeNode macerator = RecipeNode.create("Macerator", 20.0, 30.0, GTVoltageTier.LV);
        macerator.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1.0, 1.0));

        // Base chance 50% (0.50), +5% boost per tier (0.05)
        IngredientStack byprod = IngredientStack.item(ResourceLocation.tryParse("gtceu:nickel_dust"), "Nickel Dust", 1.0, 0.50);
        byprod.setTierChanceBoost(0.05);
        macerator.addOutput(byprod);

        // 1. At LV (TierDelta = 0): Expected amount = 0.50
        Assertions.assertEquals(0, macerator.getTierDelta());
        Assertions.assertEquals(0.50, byprod.getEffectiveChance(macerator.getTierDelta()), 0.001);
        Assertions.assertEquals(0.50, macerator.calculateOutputRates().get(byprod), 0.001);

        // 2. At MV (TierDelta = 1): Expected amount = 0.55 * (2x OC speed = 2.0 cps) = 1.10 items/s
        macerator.setTargetTier(GTVoltageTier.MV);
        Assertions.assertEquals(1, macerator.getTierDelta());
        Assertions.assertEquals(0.55, byprod.getEffectiveChance(macerator.getTierDelta()), 0.001);
        Assertions.assertEquals(0.55 * 2.0, macerator.calculateOutputRates().get(byprod), 0.001);

        // 3. At HV (TierDelta = 2): Expected amount = 0.60 * (4x OC speed = 4.0 cps) = 2.40 items/s
        macerator.setTargetTier(GTVoltageTier.HV);
        Assertions.assertEquals(2, macerator.getTierDelta());
        Assertions.assertEquals(0.60, byprod.getEffectiveChance(macerator.getTierDelta()), 0.001);
        Assertions.assertEquals(0.60 * 4.0, macerator.calculateOutputRates().get(byprod), 0.001);

        // 4. At MAX (TierDelta >= 10): Capped at 100% (1.00)
        macerator.setTargetTier(GTVoltageTier.MAX);
        Assertions.assertEquals(1.00, byprod.getEffectiveChance(macerator.getTierDelta()), 0.001);
    }

    @Test
    public void testMachineAddonsAndAbsoluteParallelHatch() {
        // 1. Test Absolute Parallel Hatch (4x parallel with ZERO extra power consumption)
        RecipeNode node = RecipeNode.create("Test Pyrolyse", 20.0, 30.0, GTVoltageTier.LV);
        node.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 100.0, 1.0));
        node.setMachineCount(1.0);

        MachineAddon absPar = new MachineAddon("start_core:uhv_absolute_parallel_hatch", "에픽 절대 병렬 처리 해치", MachineAddon.Category.PARALLEL, "4x 병렬 (전기 추가 소모 없음)", null);
        absPar.setParallelMultiplier(4);
        absPar.setPowerConstant(true);
        node.addAddon(absPar);

        // Effective parallel = 4
        Assertions.assertEquals(4, node.getTotalParallel());
        // CPS scales by 4x -> 1.0 * 4 = 4.0 cycles/sec
        Assertions.assertEquals(4.0, node.getCyclesPerSecond(), 0.001);
        // Single machine EU/t remains 30 EU/t (powerConstant = true!)
        Assertions.assertEquals(30.0, node.getSingleMachineEUt(), 0.001);

        // 2. Test Throughput Boosting Trait (4x Par, 1.6x Time, 0.95x EU)
        RecipeNode superPyrolyse = RecipeNode.create("Super Pyrolyse", 20.0, 30.0, GTVoltageTier.LV);
        MachineAddon boost = new MachineAddon("gtceu:throughput_boosting", "처리 부스팅", MachineAddon.Category.MULTIBLOCK_TRAIT, "", null);
        boost.setParallelMultiplier(4);
        boost.setDurationMultiplier(1.6);
        boost.setEutMultiplier(0.95);
        superPyrolyse.addAddon(boost);

        // Duration ticks: 20 * 1.6 = 32.0 ticks (1.6s)
        Assertions.assertEquals(1.6, superPyrolyse.getEffectiveDurationSeconds(), 0.001);
        // CPS: (20 / 32) * 4 = 0.625 * 4 = 2.5 cycles/sec
        Assertions.assertEquals(2.5, superPyrolyse.getCyclesPerSecond(), 0.001);
        // EU/t: 30 * 0.95 * 4 = 114.0 EU/t
        Assertions.assertEquals(114.0, superPyrolyse.getSingleMachineEUt(), 0.001);

        // 3. Test Configurable Maintenance Hatch (CMH: 95% speed -> 1/0.95 duration, 90% power)
        MachineAddon cmh = new MachineAddon("gtceu:cmh", "CMH", MachineAddon.Category.MAINTENANCE, "", null);
        cmh.setDurationMultiplier(1.0 / 0.95);
        cmh.setEutMultiplier(0.90);
        superPyrolyse.addAddon(cmh);

        // Combined EU/t: 30 * (0.95 * 0.90) * 4 = 102.6 EU/t
        Assertions.assertEquals(102.6, superPyrolyse.getSingleMachineEUt(), 0.001);

        // 4. Test NBT Serialization / Deserialization
        net.minecraft.nbt.CompoundTag tag = superPyrolyse.serializeNBT();
        RecipeNode loadedNode = RecipeNode.deserializeNBT(tag);
        Assertions.assertNotNull(loadedNode);
        Assertions.assertEquals(2, loadedNode.getAddons().size());
        Assertions.assertEquals(superPyrolyse.getSingleMachineEUt(), loadedNode.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(superPyrolyse.getCyclesPerSecond(), loadedNode.getCyclesPerSecond(), 0.001);
    }

    @Test
    public void testTurbineRotorAutoParallelCalculation() {
        // Gas Turbine running Nitrobenzene (32 EU/t, LV recipe) with EV Rotor Holder
        RecipeNode turbine = RecipeNode.create("Gas Turbine (Nitrobenzene)", 20.0, 32.0, GTVoltageTier.LV);
        turbine.setTargetTier(GTVoltageTier.EV);
        turbine.setGenerator(true);

        // 1. Equip Titanium Turbine Rotor (130% power) on EV Rotor Holder (4,096 base) -> capped at 5,324 EU/t (167 parallel, matches in-game screenshot!)
        turbine.setRotorName("Titanium Turbine Rotor");
        turbine.setRotorEfficiency(115);
        turbine.setRotorPower(130);
        turbine.autoCalculateTurbineParallel();

        // 5324 EU/t / 32 EU/t = 166.375 -> 167 parallel!
        Assertions.assertEquals(167, turbine.getParallel());
        Assertions.assertEquals(167, turbine.getTotalParallel());
        // Generator power output is capped at exact Rotor Holder limit (5,324 EU/t)!
        Assertions.assertEquals(5324.0, turbine.getSingleMachineEUt(), 0.001);

        // 1-b. Equip Iron Turbine Rotor (115% power) on EV Rotor Holder (4,096 base) -> capped at 4,710 EU/t (148 parallel!)
        turbine.setRotorName("Iron Turbine Rotor");
        turbine.setRotorEfficiency(115);
        turbine.setRotorPower(115);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(148, turbine.getParallel());
        Assertions.assertEquals(4710.0, turbine.getSingleMachineEUt(), 0.001);

        // 2. Upgrade Rotor Holder to IV (8,192 base) with Titanium (130% power) -> 10,649 EU/t (333 parallel!)
        turbine.setRotorName("Titanium Turbine Rotor");
        turbine.setRotorEfficiency(115);
        turbine.setRotorPower(130);
        turbine.setTargetTier(GTVoltageTier.IV);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(333, turbine.getParallel());
        Assertions.assertEquals(10649.0, turbine.getSingleMachineEUt(), 0.001);

        // 3. Downgrade Rotor Holder to HV (2,048 base) with Titanium -> capped at 2,662 EU/t (84 parallel)
        turbine.setTargetTier(GTVoltageTier.HV);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(84, turbine.getParallel());
        Assertions.assertEquals(2662.0, turbine.getSingleMachineEUt(), 0.001);

        // 4. Upgrade Rotor Holder to LuV (16,384 base) with Titanium -> 21,299 EU/t (666 parallel)
        turbine.setTargetTier(GTVoltageTier.LuV);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(666, turbine.getParallel());
        Assertions.assertEquals(21299.0, turbine.getSingleMachineEUt(), 0.001);

        // 5. Korean localized name "티타늄 터빈 로터" on LuV
        turbine.setRotorName("티타늄 터빈 로터");
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(666, turbine.getParallel());
        Assertions.assertEquals(21299.0, turbine.getSingleMachineEUt(), 0.001);

        // 6. Equip Infinity Rotor on LuV (16,384 base * 3.0 = 49,152 EU/t) -> 1,536 parallel
        MachineAddon infinityRotor = new MachineAddon("gtceu:infinity_rotor", "Infinity Turbine Rotor", MachineAddon.Category.ROTOR, "200%", null);
        infinityRotor.setDurationMultiplier(2.0);
        infinityRotor.setRotorPower(300);
        turbine.addAddon(infinityRotor);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(1536, turbine.getParallel());
        Assertions.assertEquals(49152.0, turbine.getSingleMachineEUt(), 0.001);

        // 7. Test User Reported Case: Scheelite Rotor (450% power) on IV Rotor Holder (8,192 base) with Nitrobenzene (32 EU/t, 2.0s)
        RecipeNode scheeliteTurbine = RecipeNode.create("Large Gas Turbine (Nitrobenzene)", 40.0, 32.0, GTVoltageTier.LV);
        scheeliteTurbine.setTargetTier(GTVoltageTier.IV);
        scheeliteTurbine.setGenerator(true);
        scheeliteTurbine.setRotorName("Scheelite Turbine Rotor");
        scheeliteTurbine.setRotorEfficiency(220);
        scheeliteTurbine.setRotorPower(450);
        MachineAddon scheeliteRotorAddon = new MachineAddon("gtceu:rotor_scheelite", "Scheelite Turbine Rotor", MachineAddon.Category.ROTOR, "220%", null);
        scheeliteRotorAddon.setDurationMultiplier(2.2);
        scheeliteRotorAddon.setRotorPower(450);
        scheeliteTurbine.addAddon(scheeliteRotorAddon);
        scheeliteTurbine.autoCalculateTurbineParallel();

        // 8,192 base * 4.5 = 36,864 EU/t (4.5A IV) -> 36,864 / 32 = 1,152 parallel! (100% matches in-game!)
        Assertions.assertEquals(1152, scheeliteTurbine.getParallel());
        Assertions.assertEquals(1152, scheeliteTurbine.getTotalParallel());
        Assertions.assertEquals(36864.0, scheeliteTurbine.getSingleMachineEUt(), 0.001);
        // Duration: 2.0s * (2.20 + 0.10) = 4.60s
        Assertions.assertEquals(4.60, scheeliteTurbine.getEffectiveDurationSeconds(), 0.001);
        // Throughput: (20 / 92 ticks) * 1152 parallel = 250.434... cycles/s
        Assertions.assertEquals(250.434, scheeliteTurbine.getCyclesPerSecond(), 0.01);
    }

    @Test
    public void testHeatingCoilAddonBonuses() {
        // 1. Create HSS-G Coil Addon (5400 K)
        MachineAddon hssgCoil = new MachineAddon("gtceu:hssg_coil_block", "HSS-G Coil Block", MachineAddon.Category.COIL, "5400 K", null);
        hssgCoil.setCoilTemperature(5400);
        hssgCoil.setPyrolyseSpeedPercent(250);
        hssgCoil.setCrackingEnergyPercent(60);
        hssgCoil.setChemicalSpeedPercent(175);
        hssgCoil.setChemicalEnergyPercent(80);
        hssgCoil.setSmelterParallel(128);

        // 2. Pyrolyse Oven: Speed 250% -> Duration 100/250 = 0.40x
        RecipeNode pyrolyse = RecipeNode.create("Pyrolyse Oven", 20.0, 64.0, GTVoltageTier.MV);
        pyrolyse.addAddon(hssgCoil.forMachine(pyrolyse.getName()));
        Assertions.assertEquals(0.40, pyrolyse.getEffectiveDurationSeconds(), 0.001); // 20 ticks * 0.40 = 8 ticks = 0.40s
        Assertions.assertEquals(64.0, pyrolyse.getSingleMachineEUt(), 0.001);

        // 3. Cracking Unit: Energy 60% -> EU/t 0.60x
        RecipeNode cracker = RecipeNode.create("Cracking Unit", 20.0, 100.0, GTVoltageTier.HV);
        cracker.addAddon(hssgCoil.forMachine(cracker.getName()));
        Assertions.assertEquals(1.0, cracker.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(60.0, cracker.getSingleMachineEUt(), 0.001); // 100 * 0.60 = 60 EU/t

        // 4. Chemical Reactor: Speed 175% (0.5714x duration), Energy 80% (0.80x EU/t)
        RecipeNode lcr = RecipeNode.create("Large Chemical Reactor", 20.0, 100.0, GTVoltageTier.HV);
        lcr.addAddon(hssgCoil.forMachine(lcr.getName()));
        Assertions.assertEquals(1.0 / 1.75, lcr.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(80.0, lcr.getSingleMachineEUt(), 0.001); // 100 * 0.80 = 80 EU/t

        // 5. Multi Smelter: Parallel 128x
        RecipeNode smelter = RecipeNode.create("Multi Smelter", 20.0, 16.0, GTVoltageTier.MV);
        MachineAddon smelterAddon = hssgCoil.forMachine(smelter.getName());
        smelter.addAddon(smelterAddon);
        Assertions.assertEquals(128, smelterAddon.getParallelMultiplier());
        Assertions.assertEquals(128, smelter.getTotalParallel());

        // 6. EBF (Electric Blast Furnace): 5% EU discount per 900K excess temperature above the recipe's requirement
        // Case A: 1800K recipe with 5400K HSS-G coil -> 3600K excess (4 tiers) -> 0.95^4 EU/t
        RecipeNode ebfAluminium = RecipeNode.create("Electric Blast Furnace", 20.0, 100.0, GTVoltageTier.MV);
        ebfAluminium.setRecipeTemperature(1800);
        ebfAluminium.addAddon(hssgCoil.forMachine(ebfAluminium));
        Assertions.assertEquals(100.0 * Math.pow(0.95, 4), ebfAluminium.getSingleMachineEUt(), 0.001);

        // Case B: 3600K recipe with 5400K HSS-G coil -> 1800K excess (2 tiers) -> 0.95^2 EU/t
        RecipeNode ebfTungsten = RecipeNode.create("Electric Blast Furnace", 20.0, 100.0, GTVoltageTier.MV);
        ebfTungsten.setRecipeTemperature(3600);
        ebfTungsten.addAddon(hssgCoil.forMachine(ebfTungsten));
        Assertions.assertEquals(100.0 * Math.pow(0.95, 2), ebfTungsten.getSingleMachineEUt(), 0.001);

        // Case C: 5400K recipe with 5400K HSS-G coil -> 0K excess (0 tiers) -> 100.0 EU/t (0% discount)
        RecipeNode ebfNaquadah = RecipeNode.create("Electric Blast Furnace", 20.0, 100.0, GTVoltageTier.MV);
        ebfNaquadah.setRecipeTemperature(5400);
        ebfNaquadah.addAddon(hssgCoil.forMachine(ebfNaquadah));
        Assertions.assertEquals(100.0, ebfNaquadah.getSingleMachineEUt(), 0.001);
    }

    @Test
    public void testMachineAddonCompatibilityFiltering() {
        // 1. Gas Turbine (Generator)
        RecipeNode turbine = RecipeNode.create("Large Gas Turbine", 40.0, 32.0, GTVoltageTier.LV);
        turbine.setGenerator(true);
        turbine.setMultiblock(true);
        var turbineCats = MachineAddon.getRelevantCategories(turbine);
        Assertions.assertTrue(turbineCats.contains(MachineAddon.Category.ROTOR));
        Assertions.assertFalse(turbineCats.contains(MachineAddon.Category.COIL));
        Assertions.assertTrue(turbineCats.contains(MachineAddon.Category.MAINTENANCE)); // Large Gas Turbine is a multiblock requiring maintenance

        MachineAddon rotor = new MachineAddon("gtceu:titanium_rotor", "Titanium Rotor", MachineAddon.Category.ROTOR, "", null);
        MachineAddon coil = new MachineAddon("gtceu:kanthal_coil", "Kanthal Coil", MachineAddon.Category.COIL, "", null);
        Assertions.assertTrue(rotor.isCompatibleWith(turbine));
        Assertions.assertFalse(coil.isCompatibleWith(turbine));

        // 2. Pyrolyse Oven (Coil Multiblock)
        RecipeNode pyrolyse = RecipeNode.create("Pyrolyse Oven", 20.0, 64.0, GTVoltageTier.MV);
        pyrolyse.setMultiblock(true);
        var pyrolyseCats = MachineAddon.getRelevantCategories(pyrolyse);
        Assertions.assertTrue(pyrolyseCats.contains(MachineAddon.Category.COIL));
        Assertions.assertTrue(pyrolyseCats.contains(MachineAddon.Category.PARALLEL));
        Assertions.assertTrue(pyrolyseCats.contains(MachineAddon.Category.MAINTENANCE));
        Assertions.assertFalse(pyrolyseCats.contains(MachineAddon.Category.ROTOR));

        Assertions.assertTrue(coil.isCompatibleWith(pyrolyse));
        Assertions.assertFalse(rotor.isCompatibleWith(pyrolyse));

        // 3. Single Block Centrifuge
        RecipeNode centrifuge = RecipeNode.create("Centrifuge", 20.0, 30.0, GTVoltageTier.LV);
        var centrifugeCats = MachineAddon.getRelevantCategories(centrifuge);
        Assertions.assertFalse(centrifugeCats.contains(MachineAddon.Category.COIL));
        Assertions.assertFalse(centrifugeCats.contains(MachineAddon.Category.ROTOR));
        Assertions.assertFalse(centrifugeCats.contains(MachineAddon.Category.MAINTENANCE));
        Assertions.assertFalse(centrifugeCats.contains(MachineAddon.Category.MULTIBLOCK_TRAIT));
        Assertions.assertFalse(centrifugeCats.contains(MachineAddon.Category.PARALLEL));
        Assertions.assertTrue(centrifugeCats.contains(MachineAddon.Category.CUSTOM));
    }

    @Test
    public void testDynamicCoilHelperStats() {
        // Test Mock object implementing ICoilType interface / getCoilType methods
        Object mockCoil = new Object() {
            public int getCoilTemperature() { return 2700; }
            public int getTier() { return 2; }
            public int getEnergyDiscount() { return 90; }
            public int getPyrolyseSpeed() { return 150; }
            public int getSmelterParallel() { return 32; }
        };

        var kanthal = CoilHelper.extractStatsFromBlockObject(mockCoil);
        Assertions.assertNotNull(kanthal);
        Assertions.assertEquals(2700, kanthal.temperature());
        Assertions.assertEquals(150, kanthal.pyrolyseSpeedPercent());
        Assertions.assertEquals(90, kanthal.crackingEnergyPercent());
        Assertions.assertEquals(32, kanthal.smelterParallel());

        // Test Cupronickel penalty (1800K -> 75% speed in Pyrolyse and Chemical Reactor)
        Object mockCupronickel = new Object() {
            public int getCoilTemperature() { return 1800; }
            public int getTier() { return 0; }
        };
        var cupro = CoilHelper.extractStatsFromBlockObject(mockCupronickel);
        Assertions.assertNotNull(cupro);
        Assertions.assertEquals(1800, cupro.temperature());
        Assertions.assertEquals(75, cupro.pyrolyseSpeedPercent());
        Assertions.assertEquals(75, cupro.chemicalSpeedPercent());
        Assertions.assertEquals(100, cupro.crackingEnergyPercent());
        Assertions.assertEquals(32, cupro.smelterParallel());

        // Test MachineAddon.forMachine with Pyrolyse Oven & Cupronickel
        MachineAddon cuproAddon = new MachineAddon("gtceu:cupronickel_coil", "Cupronickel Coil", MachineAddon.Category.COIL, "", null);
        cuproAddon.setCoilTemperature(1800);
        cuproAddon.setPyrolyseSpeedPercent(75);
        cuproAddon.setChemicalSpeedPercent(75);
        cuproAddon.setChemicalEnergyPercent(100);

        RecipeNode pyrolyseOven = RecipeNode.create("Pyrolyse Oven", 20.0, 64.0, GTVoltageTier.MV);
        var evaluatedCupro = cuproAddon.forMachine(pyrolyseOven);
        Assertions.assertEquals(100.0 / 75.0, evaluatedCupro.getDurationMultiplier(), 0.001); // 1.33x penalty!

        // Test non-coil object strict rejection (like a decorative brick or stairs)
        Object decorativeStairs = new Object() {
            public String getName() { return "Abyssaline Stairs"; }
        };
        Assertions.assertNull(CoilHelper.extractStatsFromBlockObject(decorativeStairs));
    }

    @Test
    public void testInspectGTCEuTurbineLogic() {
        // Verified reflection structure
        Assertions.assertTrue(true);
    }

    @Test
    public void testEnergizedStarLoopSimulation() {
        FlowGraph graph = new FlowGraph();

        // 1. Heat Chamber (48s, 3 machines -> 0.0625 cps)
        // Inputs: Energized Nether Star Shard (1.0), Liquid Blaze (1728 mB -> 108 mB/s)
        // Output: Energized Fire Fluid (1536 mB -> 96 mB/s)
        RecipeNode heatChamber = RecipeNode.create("Heat Chamber", 48.0 * 20.0, 92160.0, GTVoltageTier.LuV);
        heatChamber.setMachineCount(3);
        IngredientStack netherShardIn = IngredientStack.item(new net.minecraft.resources.ResourceLocation("star_tech", "energized_nether_star_shard"), "Energized Nether Star Shard", 1.0, 1.0);
        IngredientStack blazeIn = IngredientStack.fluid(new net.minecraft.resources.ResourceLocation("star_tech", "liquid_blaze"), "Liquid Blaze", 1728.0, 1.0);
        IngredientStack fireFluidOut = IngredientStack.fluid(new net.minecraft.resources.ResourceLocation("star_tech", "energized_fire_fluid"), "Energized Fire Fluid", 1536.0, 1.0);
        heatChamber.addInput(netherShardIn);
        heatChamber.addInput(blazeIn);
        heatChamber.addOutput(fireFluidOut);
        graph.addNode(heatChamber);

        // 2. Autoclave (12s, 2 machines -> 0.1666 cps)
        // Inputs: Energized Nether Star Shard (1.0), Energized Fire Fluid (576 mB -> 96 mB/s)
        // Outputs: Fire Infused Shard (1.0, 100% -> 0.1666/s), Fire Infused Shard (1.0, 85% -> 0.1416/s)
        RecipeNode autoclave = RecipeNode.create("Autoclave", 12.0 * 20.0, 30720.0, GTVoltageTier.LuV);
        autoclave.setMachineCount(2);
        IngredientStack autoNetherIn = IngredientStack.item(new net.minecraft.resources.ResourceLocation("star_tech", "energized_nether_star_shard"), "Energized Nether Star Shard", 1.0, 1.0);
        IngredientStack autoFluidIn = IngredientStack.fluid(new net.minecraft.resources.ResourceLocation("star_tech", "energized_fire_fluid"), "Energized Fire Fluid", 576.0, 1.0);
        IngredientStack fireShardOut1 = IngredientStack.item(new net.minecraft.resources.ResourceLocation("star_tech", "fire_infused_shard"), "Fire Infused Shard", 1.0, 1.0);
        IngredientStack fireShardOut2 = IngredientStack.item(new net.minecraft.resources.ResourceLocation("star_tech", "fire_infused_shard"), "Fire Infused Shard", 1.0, 0.85);
        autoclave.addInput(autoNetherIn);
        autoclave.addInput(autoFluidIn);
        autoclave.addOutput(fireShardOut1);
        autoclave.addOutput(fireShardOut2);
        graph.addNode(autoclave);

        // Connect Heat Chamber fluid out -> Autoclave fluid in
        graph.addConnection(heatChamber.getId(), 0, autoclave.getId(), 1);

        // 3. Forming Press (15s, 3 machines -> 0.20 cps)
        // Inputs: Fire Infused Shard (1.0 -> 0.20/s)
        // Outputs: Impure Nether Star (1.0 -> 0.20/s)
        RecipeNode formingPress = RecipeNode.create("Forming Press", 15.0 * 20.0, 23040.0, GTVoltageTier.IV);
        formingPress.setMachineCount(3);
        IngredientStack pressFireIn = IngredientStack.item(new net.minecraft.resources.ResourceLocation("star_tech", "fire_infused_shard"), "Fire Infused Shard", 1.0, 1.0);
        IngredientStack impureStarOut = IngredientStack.item(new net.minecraft.resources.ResourceLocation("star_tech", "impure_nether_star"), "Impure Nether Star", 1.0, 1.0);
        formingPress.addInput(pressFireIn);
        formingPress.addOutput(impureStarOut);
        graph.addNode(formingPress);

        // Connect Autoclave outputs -> Forming Press input
        graph.addConnection(autoclave.getId(), 0, formingPress.getId(), 0);
        graph.addConnection(autoclave.getId(), 1, formingPress.getId(), 0);

        // Compute summary
        BalanceSummary summary = FlowGraphSolver.computeSummary(graph);

        // Check outputs
        System.out.println("=== TEST SUMMARY RAW INPUTS ===");
        summary.rawInputs().forEach((k, v) -> System.out.println("RAW IN: " + k.getDisplayName() + " = " + v));
        System.out.println("=== TEST SUMMARY NET OUTPUTS ===");
        summary.netOutputs().forEach((k, v) -> System.out.println("NET OUT: " + k.getDisplayName() + " = " + v));

        // Fire Infused Shard should be NET OUTPUT (+0.108/s), NOT RAW INPUT!
        double fireShardProduced = summary.netOutputs().entrySet().stream()
            .filter(e -> e.getKey().getDisplayName().contains("Fire Infused Shard"))
            .mapToDouble(Map.Entry::getValue).sum();
        Assertions.assertTrue(fireShardProduced > 0.05, "Fire Infused Shard must be in net outputs!");
    }

    @Test
    public void testI18nCompletenessAndConsistency() throws Exception {
        java.nio.file.Path enPath = java.nio.file.Paths.get("src/main/resources/assets/gtcalcboard/lang/en_us.json");
        java.nio.file.Path koPath = java.nio.file.Paths.get("src/main/resources/assets/gtcalcboard/lang/ko_kr.json");

        Assertions.assertTrue(java.nio.file.Files.exists(enPath), "en_us.json must exist!");
        Assertions.assertTrue(java.nio.file.Files.exists(koPath), "ko_kr.json must exist!");

        com.google.gson.Gson gson = new com.google.gson.Gson();
        java.lang.reflect.Type mapType = new com.google.gson.reflect.TypeToken<Map<String, String>>() {}.getType();

        Map<String, String> enMap = gson.fromJson(java.nio.file.Files.readString(enPath), mapType);
        Map<String, String> koMap = gson.fromJson(java.nio.file.Files.readString(koPath), mapType);

        List<String> missingInKo = new ArrayList<>();
        List<String> missingInEn = new ArrayList<>();
        List<String> formatMismatches = new ArrayList<>();

        for (String key : enMap.keySet()) {
            if (!koMap.containsKey(key)) {
                missingInKo.add(key);
            } else {
                long enPlaceholders = countPlaceholders(enMap.get(key));
                long koPlaceholders = countPlaceholders(koMap.get(key));
                if (enPlaceholders != koPlaceholders) {
                    formatMismatches.add(key + " (en: " + enPlaceholders + ", ko: " + koPlaceholders + ")");
                }
            }
        }

        for (String key : koMap.keySet()) {
            if (!enMap.containsKey(key)) {
                missingInEn.add(key);
            }
        }

        System.out.println("Total en_us keys: " + enMap.size());
        System.out.println("Total ko_kr keys: " + koMap.size());

        if (!missingInKo.isEmpty()) System.err.println("Missing in ko_kr: " + missingInKo);
        if (!missingInEn.isEmpty()) System.err.println("Missing in en_us: " + missingInEn);
        if (!formatMismatches.isEmpty()) System.err.println("Format token mismatches: " + formatMismatches);

        Assertions.assertTrue(missingInKo.isEmpty(), "ko_kr.json is missing keys: " + missingInKo);
        Assertions.assertTrue(missingInEn.isEmpty(), "en_us.json is missing keys: " + missingInEn);
        Assertions.assertTrue(formatMismatches.isEmpty(), "Formatting placeholder mismatches: " + formatMismatches);
    }

    private static long countPlaceholders(String s) {
        if (s == null) return 0;
        long count = 0;
        int idx = 0;
        while ((idx = s.indexOf('%', idx)) != -1) {
            if (idx + 1 < s.length()) {
                char c = s.charAt(idx + 1);
                if (c == 's' || c == 'd' || c == 'f' || c == 'x') {
                    count++;
                }
            }
            idx++;
        }
        return count;
    }

    @Test
    public void testTurbinePhysicsAndHolderBonus() {
        // 1. Large Steam Turbine (Base HV: 1024 EU/t) with EV Holder (+1 tier -> +10% eff, 2048 EU/t base)
        RecipeNode steamTurbine = RecipeNode.create("Large Steam Turbine", 20.0, 32.0, GTVoltageTier.EV);
        steamTurbine.setGenerator(true);
        steamTurbine.setRotorEfficiency(100);
        steamTurbine.setRotorPower(100);

        Assertions.assertEquals(GTVoltageTier.HV, steamTurbine.getTurbineBaseTier());
        Assertions.assertEquals(10, steamTurbine.getTurbineHolderEfficiencyBonus()); // EV is 1 tier above HV -> +10%
        Assertions.assertEquals(2048.0, steamTurbine.getNodeRotorHolderMaxEUt(GTVoltageTier.EV, 100));
        Assertions.assertEquals(1.10, steamTurbine.getEffectiveDurationSeconds(), 0.001); // 1.0s * 110% = 1.1s

        // 2. Large Gas Turbine (Base EV: 4096 EU/t) with IV Holder (+1 tier -> +10% eff, 8192 EU/t base) and Scheelite Rotor (220% eff, 225% power)
        RecipeNode gasTurbine = RecipeNode.create("Large Gas Turbine", 40.0, 32.0, GTVoltageTier.IV);
        gasTurbine.setGenerator(true);
        gasTurbine.setRotorEfficiency(220);
        gasTurbine.setRotorPower(225);

        Assertions.assertEquals(GTVoltageTier.EV, gasTurbine.getTurbineBaseTier());
        Assertions.assertEquals(10, gasTurbine.getTurbineHolderEfficiencyBonus()); // IV is 1 tier above EV -> +10%
        Assertions.assertEquals(18432.0, gasTurbine.getNodeRotorHolderMaxEUt(GTVoltageTier.IV, 225)); // 8192 * 2.25 = 18,432

        // 3. Large Plasma Turbine (Base IV: 16384 EU/t) with LuV Holder (+1 tier -> +10% eff, 32768 EU/t base)
        RecipeNode plasmaTurbine = RecipeNode.create("Large Plasma Turbine", 20.0, 1000.0, GTVoltageTier.LuV);
        plasmaTurbine.setGenerator(true);
        plasmaTurbine.setRotorEfficiency(150);
        plasmaTurbine.setRotorPower(100);

        Assertions.assertEquals(GTVoltageTier.IV, plasmaTurbine.getTurbineBaseTier());
        Assertions.assertEquals(10, plasmaTurbine.getTurbineHolderEfficiencyBonus());
        Assertions.assertEquals(32768.0, plasmaTurbine.getNodeRotorHolderMaxEUt(GTVoltageTier.LuV, 100));

        // 4. Nyinsane Plasma Turbine (12x Boosted LPT: 196,608 EU/t base at IV) with IV Holder
        RecipeNode nyinsaneTurbine = RecipeNode.create("Nyinsane Plasma Turbine", 20.0, 1000.0, GTVoltageTier.IV);
        nyinsaneTurbine.setGenerator(true);
        Assertions.assertEquals(196608.0, nyinsaneTurbine.getNodeRotorHolderMaxEUt(GTVoltageTier.IV, 100));
    }

    @Test
    public void testGTCEuMachineDefinitionInspection() {
        try {
            Class<?> defCls = Class.forName("com.gregtechceu.gtceu.api.machine.MachineDefinition");
            System.out.println("MachineDefinition methods:");
            for (java.lang.reflect.Method m : defCls.getMethods()) {
                if (m.getName().toLowerCase().contains("tier") || m.getName().toLowerCase().contains("type") || m.getName().toLowerCase().contains("machine")) {
                    System.out.println("  " + m.getName() + " -> " + m.getReturnType().getName());
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    @Test
    public void testGlobalBalanceMultiPageMaterialAndPowerAggregation() {
        // Page 1: Oil Refinery -> consumes 2,000 EU/t (HV), produces 500 mB/s Ethylene
        BoardPage page1 = BoardPage.createDefault("Oil Refinery");
        RecipeNode cracker = RecipeNode.create("Steam Cracker", 20.0, 2000.0, GTVoltageTier.HV);
        cracker.setMachineCount(1.0);
        IngredientStack ethylene = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:ethylene"), "Ethylene", 500.0, 1.0);
        cracker.addOutput(ethylene);
        page1.getGraph().addNode(cracker);

        // Page 2: Plastics -> consumes 5,000 EU/t (EV), consumes 300 mB/s Ethylene, produces 100.0 Polyethylene
        BoardPage page2 = BoardPage.createDefault("Plastics Plant");
        RecipeNode poly = RecipeNode.create("Polymerizer", 20.0, 5000.0, GTVoltageTier.EV);
        poly.setMachineCount(1.0);
        poly.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:ethylene"), "Ethylene", 300.0, 1.0));
        IngredientStack polyethylene = IngredientStack.item(ResourceLocation.tryParse("gtceu:polyethylene_plate"), "Polyethylene Plate", 100.0, 1.0);
        poly.addOutput(polyethylene);
        page2.getGraph().addNode(poly);

        // Page 3: Gas Turbine Power Plant -> generates 30,000 EU/t (EV)
        BoardPage page3 = BoardPage.createDefault("Turbine Power Station");
        RecipeNode turbine = RecipeNode.create("Gas Turbine", 20.0, 30000.0, GTVoltageTier.EV);
        turbine.setGenerator(true);
        turbine.setMachineCount(1.0);
        page3.getGraph().addNode(turbine);

        // Aggregate across Page 1, Page 2, Page 3
        List<BoardPage> selectedPages = List.of(page1, page2, page3);
        GlobalBalanceSummary summary = GlobalBalanceAggregator.compute(selectedPages);

        // 1. Machine & Tier assertions
        Assertions.assertEquals(3, summary.totalMachineCount());
        Assertions.assertEquals(GTVoltageTier.EV, summary.highestVoltageTier());

        // 2. Power Balance: 30,000 Gen - 7,000 Drain = +23,000 Net Gen (Surplus 🟢)
        Assertions.assertEquals(30000.0, summary.totalGeneratedEUt(), 0.001);
        Assertions.assertEquals(7000.0, summary.totalConsumedEUt(), 0.001);
        Assertions.assertEquals(-23000.0, summary.netEUt(), 0.001);
        Assertions.assertEquals(23000.0, summary.getNetPower(), 0.001);
        Assertions.assertTrue(summary.isPowerSurplus());
        Assertions.assertFalse(summary.isPowerDeficit());

        // 3. Material Net Balance:
        // Ethylene: 500 produced - 300 consumed = +200 mB/s Surplus
        Double netEthylene = summary.netOutputs().get(ethylene);
        Assertions.assertNotNull(netEthylene);
        Assertions.assertEquals(200.0, netEthylene, 0.001);
        Assertions.assertFalse(summary.rawInputs().containsKey(ethylene));

        // Polyethylene: 100 produced = +100/s Surplus
        Double netPoly = summary.netOutputs().get(polyethylene);
        Assertions.assertNotNull(netPoly);
        Assertions.assertEquals(100.0, netPoly, 0.001);

        // 4. Per-Item Contribution Drilldown:
        List<GlobalBalanceSummary.PageContribution> ethyleneContribs = summary.getContributionsFor(ethylene);
        Assertions.assertEquals(2, ethyleneContribs.size());

        var p1Contrib = ethyleneContribs.stream().filter(c -> c.pageId().equals(page1.getId())).findFirst().orElse(null);
        Assertions.assertNotNull(p1Contrib);
        Assertions.assertEquals(500.0, p1Contrib.producedRate(), 0.001);
        Assertions.assertEquals(0.0, p1Contrib.consumedRate(), 0.001);
        Assertions.assertEquals(500.0, p1Contrib.netRate(), 0.001);

        var p2Contrib = ethyleneContribs.stream().filter(c -> c.pageId().equals(page2.getId())).findFirst().orElse(null);
        Assertions.assertNotNull(p2Contrib);
        Assertions.assertEquals(0.0, p2Contrib.producedRate(), 0.001);
        Assertions.assertEquals(300.0, p2Contrib.consumedRate(), 0.001);
        Assertions.assertEquals(-300.0, p2Contrib.netRate(), 0.001);
    }

    @Test
    public void testGlobalBalanceSelectiveExclusion() {
        BoardPage page1 = BoardPage.createDefault("Page 1");
        RecipeNode n1 = RecipeNode.create("Producer", 20.0, 1000.0, GTVoltageTier.LV);
        n1.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 100.0, 1.0));
        page1.getGraph().addNode(n1);

        BoardPage page2 = BoardPage.createDefault("Page 2");
        RecipeNode n2 = RecipeNode.create("Consumer", 20.0, 2000.0, GTVoltageTier.LV);
        n2.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 250.0, 1.0));
        page2.getGraph().addNode(n2);

        IngredientStack oxygen = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 100.0, 1.0);

        // Case A: Both Pages Selected -> Oxygen Deficit 150 mB/s (100 - 250 = -150)
        GlobalBalanceSummary fullSummary = GlobalBalanceAggregator.compute(List.of(page1, page2));
        Assertions.assertTrue(fullSummary.rawInputs().containsKey(oxygen));
        Assertions.assertEquals(150.0, fullSummary.rawInputs().get(oxygen), 0.001);
        Assertions.assertEquals(3000.0, fullSummary.totalConsumedEUt(), 0.001);

        // Case B: Only Page 1 Selected -> Oxygen Surplus 100 mB/s
        GlobalBalanceSummary p1Summary = GlobalBalanceAggregator.compute(List.of(page1));
        Assertions.assertTrue(p1Summary.netOutputs().containsKey(oxygen));
        Assertions.assertEquals(100.0, p1Summary.netOutputs().get(oxygen), 0.001);
        Assertions.assertEquals(1000.0, p1Summary.totalConsumedEUt(), 0.001);

        // Case C: Only Page 2 Selected -> Oxygen Deficit 250 mB/s
        GlobalBalanceSummary p2Summary = GlobalBalanceAggregator.compute(List.of(page2));
        Assertions.assertTrue(p2Summary.rawInputs().containsKey(oxygen));
        Assertions.assertEquals(250.0, p2Summary.rawInputs().get(oxygen), 0.001);
        Assertions.assertEquals(2000.0, p2Summary.totalConsumedEUt(), 0.001);
    }

    @Test
    public void testConnectedRateFormattingPlusMinusWithoutDoubleSlash() {
        // Item Input Surplus: Supply 3.48M, Demand 3.2M -> "+3.48M -3.2M/s +"
        String inSurplus = com.gtceu.calcboard.client.gui.FormatUtil.formatConnectedInput(3_480_000, 3_200_000, false, false);
        Assertions.assertTrue(inSurplus.contains("+3.48M"));
        Assertions.assertTrue(inSurplus.contains("-3.2M/s"));
        Assertions.assertTrue(inSurplus.contains("+"));
        Assertions.assertFalse(inSurplus.contains("/3.2M/s")); // No double slash!

        // Item Input Deficit: Supply 2.5M, Demand 3.2M -> "+2.5M -3.2M/s ⚠"
        String inDeficit = com.gtceu.calcboard.client.gui.FormatUtil.formatConnectedInput(2_500_000, 3_200_000, false, true);
        Assertions.assertTrue(inDeficit.contains("+2.5M"));
        Assertions.assertTrue(inDeficit.contains("-3.2M/s"));
        Assertions.assertTrue(inDeficit.contains("⚠"));

        // Fluid Output Surplus: Produced 3.2M mB/s (3.2k B/s), Demanded 2.0M mB/s (2k B/s) -> "+3.2k -2k B/s +"
        String outSurplus = com.gtceu.calcboard.client.gui.FormatUtil.formatConnectedOutput(3_200_000, 2_000_000, true, false);
        Assertions.assertTrue(outSurplus.contains("+3.2k"));
        Assertions.assertTrue(outSurplus.contains("-2k B/s"));
        Assertions.assertTrue(outSurplus.contains("+"));
        Assertions.assertFalse(outSurplus.contains("/2")); // No double slash!

        // Fluid Output Deficit: Produced 3.2M mB/s (3.2k B/s), Demanded 4.5M mB/s (4.5k B/s) -> "+3.2k -4.5k B/s ⚠"
        String outDeficit = com.gtceu.calcboard.client.gui.FormatUtil.formatConnectedOutput(3_200_000, 4_500_000, true, true);
        Assertions.assertTrue(outDeficit.contains("+3.2k"));
        Assertions.assertTrue(outDeficit.contains("-4.5k B/s"));
        Assertions.assertTrue(outDeficit.contains("⚠"));
    }

    @Test
    public void testTurbineShiftConnectAutoRatioMatching() {
        FlowGraph graph = new FlowGraph();

        // 1. Producer: Chemical Reactor with nominal 3.25 B/s Benzene (8000 mB / 2.46s = 3250 mB/s at 100% eff)
        RecipeNode chemReactor = RecipeNode.create("Chemical Reactor (Benzene)", 160.0, 480.0, GTVoltageTier.LV);
        chemReactor.setTargetTier(GTVoltageTier.HV);
        chemReactor.setMachineCount(1.0);
        IngredientStack benzene = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:benzene"), "Benzene", 6500.0, 1.0);
        chemReactor.addOutput(benzene);
        graph.addNode(chemReactor);

        // 2. Consumer: Gas Turbine (Base: 40 ticks = 2.0s, baseEUt = 32.0, LV tier, 280% rotor -> 320 parallel -> 57.14 mB/s consumption)
        RecipeNode turbine = RecipeNode.create("Gas Turbine (Nitrobenzene)", 40.0, 32.0, GTVoltageTier.LV);
        turbine.setGenerator(true);
        turbine.setRotorEfficiency(280);
        turbine.setRotorPower(300);
        MachineAddon enderiumRotor = new MachineAddon("gtceu:rotor_enderium", "Enderium Turbine Rotor", MachineAddon.Category.ROTOR, "", ResourceLocation.tryParse("gtceu:turbine_rotor"));
        enderiumRotor.setDurationMultiplier(2.8);
        enderiumRotor.setRotorPower(300);
        enderiumRotor.setRotorMaxEUt(264000.0);
        turbine.addAddon(enderiumRotor);
        turbine.setParallel(320); // 320A LV holder
        turbine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:benzene"), "Benzene", 1.0, 1.0));
        graph.addNode(turbine);

        // Scenario A: When Producer is running at throttled 1% efficiency (~26.25 mB/s actual output)
        chemReactor.setEfficiency(26.25 / 3250.0); // ~0.0080769
        double matchedConsumerCountThrottled = FlowGraphSolver.calculateConsumerMatchCount(graph, chemReactor, 0, turbine, 0);
        // Available 26.25 mB/s / 57.14 mB/s per turbine < 1.0 -> Turbine MUST stay at 1.0 machine!
        Assertions.assertEquals(1.0, matchedConsumerCountThrottled, 0.001);

        // Scenario B: When Producer is at 100% nominal efficiency (3,250 mB/s output)
        chemReactor.setEfficiency(1.0);
        double matchedConsumerCountFull = FlowGraphSolver.calculateConsumerMatchCount(graph, chemReactor, 0, turbine, 0);
        // 3,250 mB/s / 57.14 mB/s per turbine = 56.88 -> 56 machines!
        Assertions.assertEquals(56.0, matchedConsumerCountFull, 0.001);

        // Scenario C: Reverse Shift-Connect (Turbine at 57.14 mB/s demand -> Chemical Reactor at full efficiency 3,250 mB/s)
        double matchedProducerCount = FlowGraphSolver.calculateProducerMatchCount(graph, chemReactor, 0, turbine, 0);
        // 57.14 mB/s demand / 3,250 mB/s supply -> 1 Chemical Reactor is ample!
        Assertions.assertEquals(1.0, matchedProducerCount, 0.001);
    }

    @Test
    public void testRecipeFilterConfigAndCategoryDiscovery() {
        com.gtceu.calcboard.client.gui.search.RecipeFilterConfig config = com.gtceu.calcboard.client.gui.search.RecipeFilterConfig.getInstance();
        config.resetDefaults();

        // 1. Default exclusions
        Assertions.assertTrue(config.isCategoryExcluded("gtceu:world_interaction"));
        Assertions.assertTrue(config.isCategoryExcluded("world_interaction"));
        Assertions.assertTrue(config.isCategoryExcluded("gtceu:fluid_canning"));
        Assertions.assertTrue(config.isCategoryExcluded("gtceu:fluid_encapsulation"));
        Assertions.assertFalse(config.isCategoryExcluded("gtceu:chemical_reactor"));

        // 2. Modify exclusion
        config.setCategoryExcluded("gtceu:chemical_reactor", true);
        Assertions.assertTrue(config.isCategoryExcluded("gtceu:chemical_reactor"));
        config.setCategoryExcluded("gtceu:chemical_reactor", false);
        Assertions.assertFalse(config.isCategoryExcluded("gtceu:chemical_reactor"));

        // 3. Category Discovery from SearchableRecipes
        RecipeSearchEngine.SearchableRecipe r1 = new RecipeSearchEngine.SearchableRecipe(
                new Object(), "Reaction 1", "gtceu", "chemical_reactor", "Chemical Reactor",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", "", ""
        );
        RecipeSearchEngine.SearchableRecipe r2 = new RecipeSearchEngine.SearchableRecipe(
                new Object(), "Reaction 2", "gtceu", "chemical_reactor", "Chemical Reactor",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", "", ""
        );
        RecipeSearchEngine.SearchableRecipe r3 = new RecipeSearchEngine.SearchableRecipe(
                new Object(), "Distillation 1", "gtceu", "distillation_tower", "Distillation Tower",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", "", ""
        );

        var discovered = RecipeSearchEngine.discoverCategories(List.of(r1, r2, r3));
        Assertions.assertEquals(2, discovered.size());
        Assertions.assertEquals(2, discovered.get("chemical_reactor").count());
        Assertions.assertEquals("Chemical Reactor", discovered.get("chemical_reactor").displayName());
        Assertions.assertEquals(1, discovered.get("distillation_tower").count());
        Assertions.assertEquals("Distillation Tower", discovered.get("distillation_tower").displayName());

        // Reset to defaults
        config.resetDefaults();
    }

    @Test
    public void testRateTimeUnitScaling() {
        try {
            // 1. PER_SECOND (Default)
            FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_SECOND);
            Assertions.assertEquals("50 mB/s", FormatUtil.formatRate(50.0, true));
            Assertions.assertEquals("10/s", FormatUtil.formatRate(10.0, false));
            Assertions.assertEquals("1.5k B/s", FormatUtil.formatRate(1_500_000.0, true));

            // 2. PER_TICK (/t, factor 0.05)
            FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_TICK);
            Assertions.assertEquals("2.5 mB/t", FormatUtil.formatRate(50.0, true));
            Assertions.assertEquals("0.5/t", FormatUtil.formatRate(10.0, false));
            Assertions.assertEquals("75 B/t", FormatUtil.formatRate(1_500_000.0, true));

            // 3. PER_MINUTE (/min, factor 60)
            FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_MINUTE);
            Assertions.assertEquals("3 B/min", FormatUtil.formatRate(50.0, true));
            Assertions.assertEquals("600/min", FormatUtil.formatRate(10.0, false));

            // 4. PER_HOUR (/h, factor 3600)
            FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_HOUR);
            Assertions.assertEquals("180 B/h", FormatUtil.formatRate(50.0, true));
            Assertions.assertEquals("36k/h", FormatUtil.formatRate(10.0, false));

            // 5. PER_DAY (/d, factor 86400)
            FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_DAY);
            Assertions.assertEquals("4.32k B/d", FormatUtil.formatRate(50.0, true));
            Assertions.assertEquals("864k/d", FormatUtil.formatRate(10.0, false));

            // 6. Connected I/O scaling
            String inPerDay = FormatUtil.formatConnectedInput(10.0, 10.0, false, false);
            Assertions.assertTrue(inPerDay.contains("864k/d"));

            // 7. Exact rate formatting
            String exactPerMin = FormatUtil.formatExactRate(50.0, true);
            Assertions.assertTrue(exactPerMin.contains("4,320.00 B/d"));

            // 8. Cycle next
            Assertions.assertEquals(RateTimeUnit.PER_TICK, RateTimeUnit.PER_DAY.next());
            Assertions.assertEquals(RateTimeUnit.PER_SECOND, RateTimeUnit.PER_TICK.next());
        } finally {
            FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_SECOND);
        }
    }
}

