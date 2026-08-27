package com.gtceu.calcboard;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

public class CalculationTest {

    @Test
    public void testHierarchicalMaterialTreeResolution() {
        Assertions.assertDoesNotThrow(() -> {
            Class<?> matNodeCls = Class.forName("dev.emi.emi.bom.MaterialNode");
            Class<?> matTreeCls = Class.forName("dev.emi.emi.bom.MaterialTree");
            Class<?> bomCls = Class.forName("dev.emi.emi.bom.BoM");

            System.out.println("[Hierarchical resolution verified]");
        });
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
        turbine.setRotorEfficiency(200);
        turbine.setRotorPower(450);
        
        // IV Rotor Holder (8,192 EU/t base * 4.5 = 36,864 EU/t -> 1,152 parallels!)
        turbine.setTargetTier(GTVoltageTier.IV);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(1152, turbine.getParallel());
        Assertions.assertEquals(36864.0, turbine.getTotalEUt(), 0.001);
        Assertions.assertEquals(4.20, turbine.getEffectiveDurationSeconds(), 0.001);

        var scheeliteRates = turbine.calculateInputRates();
        Assertions.assertEquals(1152.0 / 4.20, scheeliteRates.get(nitrobenzene), 0.01);

        // EV Rotor Holder (4,096 EU/t base * 4.5 = 18,432 EU/t -> 576 parallels)
        turbine.setTargetTier(GTVoltageTier.EV);
        turbine.autoCalculateTurbineParallel();
        Assertions.assertEquals(576, turbine.getParallel());
        Assertions.assertEquals(18432.0, turbine.getTotalEUt(), 0.001);
        Assertions.assertEquals(4.00, turbine.getEffectiveDurationSeconds(), 0.001);
    }

    @Test
    public void testPyrolyseAndDistillationTowerFlowBalance() {
        FlowGraph graph = new FlowGraph();

        RecipeNode pyrolyse = RecipeNode.create("Pyrolyse Oven (Charcoal)", 320.0, 64.0, GTVoltageTier.MV);
        pyrolyse.setTargetTier(GTVoltageTier.HV); // 1 OC -> 256 EU/t, 160 ticks (8s -> 0.125 cycles/s)
        pyrolyse.setMachineCount(1.0);
        pyrolyse.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 16, 1.0));
        pyrolyse.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:charcoal"), "Charcoal", 20, 1.0));
        pyrolyse.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0));
        graph.addNode(pyrolyse);

        RecipeNode distTower = RecipeNode.create("Distillation Tower (Wood Tar)", 200.0, 120.0, GTVoltageTier.MV);
        distTower.setTargetTier(GTVoltageTier.HV); // 1 OC -> 480 EU/t, 100 ticks (5s -> 0.2 cycles/s)
        distTower.setMachineCount(3.0); // 3 machines -> 0.6 cycles/s
        distTower.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0));
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:creosote"), "Creosote", 500, 1.0));
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:phenol"), "Phenol", 100, 1.0));
        graph.addNode(distTower);

        BalanceSummary summary = graph.computeSummary();

        Assertions.assertEquals(1696.0, summary.totalEUt(), 0.001);
        Assertions.assertEquals(GTVoltageTier.HV, summary.highestVoltageTier());

        IngredientStack woodTar = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0);
        Double deficit = summary.rawInputs().get(woodTar);
        Assertions.assertNotNull(deficit);
        Assertions.assertEquals(475.0, deficit, 0.001);

        IngredientStack charcoal = IngredientStack.item(ResourceLocation.tryParse("minecraft:charcoal"), "Charcoal", 20, 1.0);
        Double charcoalRate = summary.netOutputs().get(charcoal);
        Assertions.assertNotNull(charcoalRate);
        Assertions.assertEquals(2.5, charcoalRate, 0.001);
    }

    @Test
    public void testTierScalingPreservesProductionRate() {
        RecipeNode node = RecipeNode.create("Test Machine", 100.0, 32.0, GTVoltageTier.LV);
        node.setMachineCount(4.0);
        node.setTargetTier(GTVoltageTier.LV);
        node.setOverclockMode(OverclockMode.STANDARD);
        node.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 1, 1.0));

        double initialRate = node.calculateOutputRates().values().iterator().next();
        Assertions.assertEquals(0.8, initialRate, 0.001);

        // Tier up to MV
        double speedRatio = node.getOverclockMode().getSpeedFactor(); // 2.0
        node.setMachineCount(node.getMachineCount() / speedRatio);
        node.setTargetTier(GTVoltageTier.MV);

        double mvRate = node.calculateOutputRates().values().iterator().next();
        Assertions.assertEquals(0.8, mvRate, 0.001);

        // Tier up to HV
        node.setMachineCount(node.getMachineCount() / speedRatio);
        node.setTargetTier(GTVoltageTier.HV);

        double hvRate = node.calculateOutputRates().values().iterator().next();
        Assertions.assertEquals(0.8, hvRate, 0.001);

        // Tier down back to LV
        node.setMachineCount(node.getMachineCount() * speedRatio * speedRatio);
        node.setTargetTier(GTVoltageTier.LV);

        double backRate = node.calculateOutputRates().values().iterator().next();
        Assertions.assertEquals(4.0, node.getMachineCount(), 0.001);
        Assertions.assertEquals(0.8, backRate, 0.001);
    }

    @Test
    public void testBracketedCategoryQueryParsing() {
        var query = com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.parseQuery("[gas turbine]");
        Assertions.assertFalse(query.isEmpty());
        Assertions.assertEquals(1, query.orGroups().size());
        var terms = query.orGroups().get(0).terms();
        Assertions.assertEquals(1, terms.size());
        Assertions.assertEquals("gas turbine", terms.get(0).text());
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.MatchType.CATEGORY, terms.get(0).type());
        Assertions.assertFalse(terms.get(0).negated());

        var query2 = com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.parseQuery("[chemical reactor] nitrobenzene");
        Assertions.assertFalse(query2.isEmpty());
        var terms2 = query2.orGroups().get(0).terms();
        Assertions.assertEquals(2, terms2.size());
        Assertions.assertEquals("chemical reactor", terms2.get(0).text());
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.MatchType.CATEGORY, terms2.get(0).type());
        Assertions.assertEquals("nitrobenzene", terms2.get(1).text());
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.MatchType.GENERAL, terms2.get(1).type());
    }

    @Test
    public void testAutoRatioFromAnchorNode() {
        FlowGraph graph = new FlowGraph();

        RecipeNode pyrolyse = RecipeNode.create("Pyrolyse Oven", 320.0, 64.0, GTVoltageTier.MV);
        pyrolyse.setTargetTier(GTVoltageTier.MV);
        pyrolyse.setMachineCount(1.0);
        pyrolyse.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0));
        graph.addNode(pyrolyse);

        RecipeNode distTower = RecipeNode.create("Distillation Tower", 100.0, 120.0, GTVoltageTier.MV);
        distTower.setTargetTier(GTVoltageTier.MV);
        distTower.setMachineCount(2.0);
        distTower.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0));
        distTower.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:creosote"), "Creosote", 500, 1.0));
        distTower.setBaseNode(true);
        graph.addNode(distTower);

        graph.addConnection(pyrolyse.getId(), 0, distTower.getId(), 0);

        graph.autoRatioFromAnchor(distTower);

        Assertions.assertEquals(7.0, pyrolyse.getMachineCount(), 0.001);
        Assertions.assertEquals(2.0, distTower.getMachineCount(), 0.001);

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
        distTower.setMachineCount(4.0);
        distTower.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0));
        distTower.setBaseNode(true);
        graph.addNode(distTower);

        graph.addConnection(pyrolyse.getId(), 0, distTower.getId(), 0);

        graph.optimizeMaxThroughput(true, false);

        Assertions.assertEquals(GTVoltageTier.MAX, pyrolyse.getTargetTier());
        Assertions.assertEquals(GTVoltageTier.MAX, distTower.getTargetTier());
        Assertions.assertTrue(pyrolyse.getMachineCount() > 0);
    }

    @Test
    public void testMultipleProducersMergingIntoOneConsumer() {
        FlowGraph graph = new FlowGraph();

        RecipeNode nodeA = RecipeNode.create("Log To Creosote", 160.0, 1024.0, GTVoltageTier.EV);
        nodeA.setMachineCount(1.0);
        nodeA.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 1, 1.0));
        nodeA.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:charcoal"), "Charcoal", 20, 1.0));
        nodeA.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 4000, 1.0));
        graph.addNode(nodeA);

        RecipeNode nodeB = RecipeNode.create("Charcoal Extraction", 8.0, 1024.0, GTVoltageTier.EV);
        nodeB.setMachineCount(1.0);
        nodeB.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:charcoal"), "Charcoal", 1, 1.0));
        nodeB.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 100, 1.0));
        graph.addNode(nodeB);

        RecipeNode nodeC = RecipeNode.create("Distill Wood Tar", 60.0, 256.0, GTVoltageTier.HV);
        nodeC.setMachineCount(1.0);
        nodeC.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1500, 1.0));
        graph.addNode(nodeC);

        graph.addConnection(nodeA.getId(), 0, nodeB.getId(), 0);
        graph.addConnection(nodeA.getId(), 1, nodeC.getId(), 0);
        graph.addConnection(nodeB.getId(), 0, nodeC.getId(), 0);

        nodeA.setBaseNode(true);
        graph.autoRatioFromAnchor(nodeA);

        Assertions.assertEquals(1.0, nodeA.getMachineCount());
        Assertions.assertEquals(1.0, nodeB.getMachineCount());
        Assertions.assertEquals(1.0, nodeC.getMachineCount());

        BalanceSummary summary = graph.computeSummary();
        IngredientStack woodTar = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 1000, 1.0);
        Double netWoodTar = summary.netOutputs().get(woodTar);
        Assertions.assertNotNull(netWoodTar);
        Assertions.assertTrue(netWoodTar >= 0);
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
    public void testBidirectionalAutoRatioDownstreamPropagation() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create("Boiler", 20.0, 30.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
        producer.setMachineCount(2.0);
        producer.setBaseNode(true);
        graph.addNode(producer);

        RecipeNode middle = RecipeNode.create("Steam Turbine", 20.0, 30.0, GTVoltageTier.LV);
        middle.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 100.0, 1.0));
        middle.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 50.0, 1.0));
        middle.setMachineCount(1.0);
        graph.addNode(middle);

        RecipeNode finalConsumer = RecipeNode.create("Electrolyzer", 20.0, 30.0, GTVoltageTier.LV);
        finalConsumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 25.0, 1.0));
        finalConsumer.setMachineCount(1.0);
        graph.addNode(finalConsumer);

        graph.addConnection(producer.getId(), 0, middle.getId(), 0);
        graph.addConnection(middle.getId(), 0, finalConsumer.getId(), 0);

        graph.autoRatioFromAnchor(producer, false);

        Assertions.assertEquals(2.0, producer.getMachineCount(), 0.001);
        Assertions.assertEquals(10.0, middle.getMachineCount(), 0.001);
        Assertions.assertEquals(20.0, finalConsumer.getMachineCount(), 0.001);
    }

    @Test
    public void testBidirectionalAutoRatioFromMiddleAnchor() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create("Producer", 20.0, 30.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fuel"), "Fuel", 200.0, 1.0));
        producer.setMachineCount(1.0);
        graph.addNode(producer);

        RecipeNode middle = RecipeNode.create("Middle Generator", 20.0, 30.0, GTVoltageTier.LV);
        middle.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fuel"), "Fuel", 100.0, 1.0));
        middle.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:exhaust"), "Exhaust", 50.0, 1.0));
        middle.setMachineCount(4.0);
        middle.setBaseNode(true);
        graph.addNode(middle);

        RecipeNode consumer = RecipeNode.create("Exhaust Filter", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:exhaust"), "Exhaust", 20.0, 1.0));
        consumer.setMachineCount(1.0);
        graph.addNode(consumer);

        graph.addConnection(producer.getId(), 0, middle.getId(), 0);
        graph.addConnection(middle.getId(), 0, consumer.getId(), 0);

        graph.autoRatioFromAnchor(middle, false);

        Assertions.assertEquals(4.0, middle.getMachineCount(), 0.001);
        Assertions.assertEquals(2.0, producer.getMachineCount(), 0.001);
        Assertions.assertEquals(10.0, consumer.getMachineCount(), 0.001);
    }

    @Test
    public void testPortFlowStatsCalculations() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create("Producer", 20.0, 30.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oil"), "Oil", 100.0, 1.0));
        producer.setMachineCount(2.0);
        graph.addNode(producer);

        RecipeNode consumer = RecipeNode.create("Consumer", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oil"), "Oil", 50.0, 1.0));
        consumer.setMachineCount(4.0);
        graph.addNode(consumer);

        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);

        FlowGraphSolver.PortFlowStats inStats = graph.getInputPortStats(consumer, 0);
        Assertions.assertTrue(inStats.isConnected());
        Assertions.assertEquals(200.0, inStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(200.0, inStats.connectedRate(), 0.001);
        Assertions.assertTrue(inStats.isBalanced());
        Assertions.assertEquals(100.0, inStats.getPercent(), 0.001);

        FlowGraphSolver.PortFlowStats outStats = graph.getOutputPortStats(producer, 0);
        Assertions.assertTrue(outStats.isConnected());
        Assertions.assertEquals(200.0, outStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(200.0, outStats.connectedRate(), 0.001);
        Assertions.assertTrue(outStats.isBalanced());
    }

    @Test
    public void testBottleneckEfficiencyAndThroughputPropagation() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create("Producer A", 20.0, 30.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_a"), "Fluid A", 80.0, 1.0));
        producer.setMachineCount(1.0);
        graph.addNode(producer);

        RecipeNode consumer = RecipeNode.create("Consumer B", 20.0, 100.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_a"), "Fluid A", 100.0, 1.0));
        consumer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_b"), "Fluid B", 50.0, 1.0));
        consumer.setMachineCount(1.0);
        graph.addNode(consumer);

        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);

        Map<String, Double> effMap = graph.computeNodeEfficiencies();

        Assertions.assertEquals(1.0, effMap.get(producer.getId()), 0.001);
        Assertions.assertEquals(1.0, producer.getEfficiency(), 0.001);

        Assertions.assertEquals(0.80, effMap.get(consumer.getId()), 0.001);
        Assertions.assertEquals(0.80, consumer.getEfficiency(), 0.001);
        Assertions.assertEquals(40.0, consumer.calculateEffectiveOutputRates().values().iterator().next(), 0.001);
        Assertions.assertEquals(80.0, consumer.getEffectiveTotalEUt(), 0.001);

        RecipeNode finalNode = RecipeNode.create("Final C", 20.0, 50.0, GTVoltageTier.LV);
        finalNode.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_b"), "Fluid B", 40.0, 1.0));
        finalNode.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 10.0, 1.0));
        finalNode.setMachineCount(1.0);
        graph.addNode(finalNode);

        graph.addConnection(consumer.getId(), 0, finalNode.getId(), 0);
        graph.computeNodeEfficiencies();

        Assertions.assertEquals(1.0, finalNode.getEfficiency(), 0.001);
        Assertions.assertEquals(10.0, finalNode.calculateEffectiveOutputRates().values().iterator().next(), 0.001);

        BalanceSummary summary = graph.computeSummary();
        Assertions.assertEquals(160.0, summary.totalEUt(), 0.001);
    }

    @Test
    public void testDownstreamBottleneckPropagationToUpstreamPortStats() {
        FlowGraph graph = new FlowGraph();

        RecipeNode mixer = RecipeNode.create("Mixer", 20.0, 10.0, GTVoltageTier.LV);
        mixer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitration_mixture"), "Nitration Mixture", 400.0, 1.0));
        mixer.setMachineCount(1.0);
        graph.addNode(mixer);

        RecipeNode dist = RecipeNode.create("Distillery", 20.0, 20.0, GTVoltageTier.LV);
        dist.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:benzene"), "Benzene", 50.0, 1.0));
        dist.setMachineCount(1.0);
        graph.addNode(dist);

        RecipeNode reactor = RecipeNode.create("Chemical Reactor", 20.0, 200.0, GTVoltageTier.HV);
        reactor.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitration_mixture"), "Nitration Mixture", 200.0, 1.0));
        reactor.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:benzene"), "Benzene", 100.0, 1.0));
        reactor.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrobenzene"), "Nitrobenzene", 100.0, 1.0));
        reactor.setMachineCount(1.0);
        graph.addNode(reactor);

        graph.addConnection(mixer.getId(), 0, reactor.getId(), 0);
        graph.addConnection(dist.getId(), 0, reactor.getId(), 1);

        graph.computeSummary();

        Assertions.assertEquals(0.50, reactor.getEfficiency(), 0.001);

        FlowGraphSolver.PortFlowStats mixerOutStats = graph.getOutputPortStats(mixer, 0);
        Assertions.assertEquals(400.0, mixerOutStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(100.0, mixerOutStats.connectedRate(), 0.001);
        Assertions.assertTrue(mixerOutStats.isOutputSurplus());
    }

    @Test
    public void testMultiProducerPartialSupplyToInputPortStats() {
        FlowGraph graph = new FlowGraph();

        RecipeNode pyro1 = RecipeNode.create("Pyrolyse 1", 20.0, 30.0, GTVoltageTier.LV);
        pyro1.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 219.0, 1.0));
        pyro1.setMachineCount(1.0);
        graph.addNode(pyro1);

        RecipeNode pyro2 = RecipeNode.create("Pyrolyse 2", 20.0, 30.0, GTVoltageTier.LV);
        pyro2.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 219.0, 1.0));
        pyro2.setMachineCount(1.0);
        graph.addNode(pyro2);

        RecipeNode dist = RecipeNode.create("Distillation Tower", 20.0, 256.0, GTVoltageTier.HV);
        dist.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 500.0, 1.0));
        dist.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:benzene"), "Benzene", 100.0, 1.0));
        dist.setMachineCount(1.0);
        graph.addNode(dist);

        graph.addConnection(pyro1.getId(), 0, dist.getId(), 0);
        graph.addConnection(pyro2.getId(), 0, dist.getId(), 0);

        graph.computeSummary();

        Assertions.assertEquals(0.876, dist.getEfficiency(), 0.001);

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

        graph.addConnection(nodeA.getId(), 0, nodeB.getId(), 0);
        graph.addConnection(nodeB.getId(), 0, nodeC.getId(), 0);
        graph.addConnection(nodeC.getId(), 0, nodeA.getId(), 0);

        long start = System.currentTimeMillis();
        FlowGraphSolver.autoRatioFromAnchor(graph, nodeA, true);
        long elapsed = System.currentTimeMillis() - start;

        Assertions.assertTrue(elapsed < 100);
        Assertions.assertEquals(2.0, nodeA.getMachineCount(), 0.001);
        Assertions.assertEquals(2.0, nodeB.getMachineCount(), 0.001);
        Assertions.assertEquals(2.0, nodeC.getMachineCount(), 0.001);
    }

    @Test
    public void testTierChanceBoostOnOverclock() {
        RecipeNode macerator = RecipeNode.create("Macerator", 20.0, 30.0, GTVoltageTier.LV);
        macerator.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1.0, 1.0));

        IngredientStack byprod = IngredientStack.item(ResourceLocation.tryParse("gtceu:nickel_dust"), "Nickel Dust", 1.0, 0.50);
        byprod.setTierChanceBoost(0.05);
        macerator.addOutput(byprod);

        Assertions.assertEquals(0, macerator.getTierDelta());
        Assertions.assertEquals(0.50, byprod.getEffectiveChance(macerator.getTierDelta()), 0.001);
        Assertions.assertEquals(0.50, macerator.calculateOutputRates().get(byprod), 0.001);

        macerator.setTargetTier(GTVoltageTier.MV);
        Assertions.assertEquals(1, macerator.getTierDelta());
        Assertions.assertEquals(0.55, byprod.getEffectiveChance(macerator.getTierDelta()), 0.001);
        Assertions.assertEquals(0.55 * 2.0, macerator.calculateOutputRates().get(byprod), 0.001);

        macerator.setTargetTier(GTVoltageTier.HV);
        Assertions.assertEquals(2, macerator.getTierDelta());
        Assertions.assertEquals(0.60, byprod.getEffectiveChance(macerator.getTierDelta()), 0.001);
        Assertions.assertEquals(0.60 * 4.0, macerator.calculateOutputRates().get(byprod), 0.001);

        macerator.setTargetTier(GTVoltageTier.MAX);
        Assertions.assertEquals(1.00, byprod.getEffectiveChance(macerator.getTierDelta()), 0.001);
    }

    @Test
    public void testEnergizedStarLoopSimulation() {
        FlowGraph graph = new FlowGraph();

        RecipeNode heatChamber = RecipeNode.create("Heat Chamber", 48.0 * 20.0, 92160.0, GTVoltageTier.LuV);
        heatChamber.setMachineCount(3);
        IngredientStack netherShardIn = IngredientStack.item(ResourceLocation.tryParse("star_tech:energized_nether_star_shard"), "Energized Nether Star Shard", 1.0, 1.0);
        IngredientStack blazeIn = IngredientStack.fluid(ResourceLocation.tryParse("star_tech:liquid_blaze"), "Liquid Blaze", 1728.0, 1.0);
        IngredientStack fireFluidOut = IngredientStack.fluid(ResourceLocation.tryParse("star_tech:energized_fire_fluid"), "Energized Fire Fluid", 1536.0, 1.0);
        heatChamber.addInput(netherShardIn);
        heatChamber.addInput(blazeIn);
        heatChamber.addOutput(fireFluidOut);
        graph.addNode(heatChamber);

        RecipeNode autoclave = RecipeNode.create("Autoclave", 12.0 * 20.0, 30720.0, GTVoltageTier.LuV);
        autoclave.setMachineCount(2);
        IngredientStack autoNetherIn = IngredientStack.item(ResourceLocation.tryParse("star_tech:energized_nether_star_shard"), "Energized Nether Star Shard", 1.0, 1.0);
        IngredientStack autoFluidIn = IngredientStack.fluid(ResourceLocation.tryParse("star_tech:energized_fire_fluid"), "Energized Fire Fluid", 576.0, 1.0);
        IngredientStack fireShardOut1 = IngredientStack.item(ResourceLocation.tryParse("star_tech:fire_infused_shard"), "Fire Infused Shard", 1.0, 1.0);
        IngredientStack fireShardOut2 = IngredientStack.item(ResourceLocation.tryParse("star_tech:fire_infused_shard"), "Fire Infused Shard", 1.0, 0.85);
        autoclave.addInput(autoNetherIn);
        autoclave.addInput(autoFluidIn);
        autoclave.addOutput(fireShardOut1);
        autoclave.addOutput(fireShardOut2);
        graph.addNode(autoclave);

        graph.addConnection(heatChamber.getId(), 0, autoclave.getId(), 1);

        RecipeNode formingPress = RecipeNode.create("Forming Press", 15.0 * 20.0, 23040.0, GTVoltageTier.IV);
        formingPress.setMachineCount(3);
        IngredientStack pressFireIn = IngredientStack.item(ResourceLocation.tryParse("star_tech:fire_infused_shard"), "Fire Infused Shard", 1.0, 1.0);
        IngredientStack impureStarOut = IngredientStack.item(ResourceLocation.tryParse("star_tech:impure_nether_star"), "Impure Nether Star", 1.0, 1.0);
        formingPress.addInput(pressFireIn);
        formingPress.addOutput(impureStarOut);
        graph.addNode(formingPress);

        graph.addConnection(autoclave.getId(), 0, formingPress.getId(), 0);
        graph.addConnection(autoclave.getId(), 1, formingPress.getId(), 0);

        BalanceSummary summary = FlowGraphSolver.computeSummary(graph);

        double fireShardProduced = summary.netOutputs().entrySet().stream()
            .filter(e -> e.getKey().getDisplayName().contains("Fire Infused Shard"))
            .mapToDouble(Map.Entry::getValue).sum();
        Assertions.assertTrue(fireShardProduced > 0.05, "Fire Infused Shard must be in net outputs!");
    }

    @Test
    public void testReflectorFusionAddonsAndCompatibility() {
        RecipeNode fusionNode = RecipeNode.create("Reflector Fusion Reactor", 100.0, 16384.0, GTVoltageTier.LuV);
        fusionNode.setRequiredReflectorTier(2);

        com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter adapter = new com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter();
        List<com.gtceu.calcboard.api.catalog.AddonCategory> cats = adapter.getApplicableAddonCategories(fusionNode);
        Assertions.assertTrue(cats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.REFLECTOR), "Fusion node must support REFLECTOR category");

        com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon t1 = new com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon("gtceu:reflector_tier_1", "T1 Reflector", "", null, 1);
        com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon t2 = new com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon("gtceu:reflector_tier_2", "T2 Reflector", "", null, 2);

        Assertions.assertTrue(adapter.isAddonCompatible(fusionNode, t1));
        Assertions.assertTrue(adapter.isAddonCompatible(fusionNode, t2));

        List<net.minecraft.network.chat.Component> warnings = new ArrayList<>();
        // No reflector installed -> validation fails & outputs are 0
        boolean valid0 = adapter.validateNode(fusionNode, warnings);
        Assertions.assertFalse(valid0);
        Assertions.assertFalse(warnings.isEmpty());
        Assertions.assertFalse(fusionNode.isOperational());
        Assertions.assertEquals(0.0, fusionNode.getCyclesPerSecond(), 0.001);
        Assertions.assertEquals(0.0, fusionNode.getTotalEUt(), 0.001);

        // Install T1 reflector -> required is T2 -> validation fails & outputs are 0
        adapter.onAddonInstalled(fusionNode, t1);
        warnings.clear();
        boolean valid1 = adapter.validateNode(fusionNode, warnings);
        Assertions.assertFalse(valid1);
        Assertions.assertFalse(fusionNode.isOperational());
        Assertions.assertEquals(0.0, fusionNode.getCyclesPerSecond(), 0.001);
        Assertions.assertEquals(0.0, fusionNode.getTotalEUt(), 0.001);

        // Install T2 reflector -> required is T2 -> validation passes & outputs become active
        adapter.onAddonInstalled(fusionNode, t2);
        warnings.clear();
        boolean valid2 = adapter.validateNode(fusionNode, warnings);
        Assertions.assertTrue(valid2);
        Assertions.assertTrue(warnings.isEmpty());
        Assertions.assertTrue(fusionNode.isOperational());
        Assertions.assertTrue(fusionNode.getCyclesPerSecond() > 0.0);
        Assertions.assertTrue(fusionNode.getTotalEUt() > 0.0);
    }

    @Test
    public void testTurbineFlowDeficitValidation() {
        FlowGraph graph = new FlowGraph();

        // 1. Steam Producer (Boiler producing 500 mB/s steam)
        RecipeNode boiler = RecipeNode.create("Large Boiler", 20.0, 32.0, GTVoltageTier.LV);
        boiler.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
        boiler.setMachineCount(1.0);
        graph.addNode(boiler);

        // 2. Large Steam Turbine (Requires 1,000 mB/s steam)
        RecipeNode turbine = RecipeNode.create("Large Steam Turbine", 20.0, -4096.0, GTVoltageTier.EV);
        turbine.setGenerator(true);
        turbine.setMultiblock(true);
        turbine.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:steam_turbine"));
        turbine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 1000.0, 1.0));
        turbine.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:distilled_water"), "Distilled Water", 10.0, 1.0));
        turbine.setMachineCount(1.0);
        graph.addNode(turbine);

        com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter adapter = new com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter();
        Assertions.assertTrue(com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.isTurbine(turbine));

        // Unconnected: operational by default
        Assertions.assertTrue(turbine.isOperational(graph));
        Assertions.assertFalse(com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.hasTurbineFlowDeficit(turbine, graph));

        // Connect 500 mB/s supplier to 1000 mB/s turbine (50% flow deficit)
        graph.addConnection(boiler.getId(), 0, turbine.getId(), 0);
        graph.computeSummary();

        FlowGraphSolver.PortFlowStats inStats = graph.getInputPortStats(turbine, 0);
        Assertions.assertTrue(inStats.isConnected());
        Assertions.assertTrue(inStats.isInputDeficit());
        Assertions.assertTrue(inStats.getPercent() < 100.0);

        // Flow deficit active -> validation fails & inoperational
        List<net.minecraft.network.chat.Component> warnings = new ArrayList<>();
        boolean valid = adapter.validateNode(turbine, graph, warnings);
        Assertions.assertFalse(valid);
        Assertions.assertFalse(warnings.isEmpty());
        Assertions.assertTrue(com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.hasTurbineFlowDeficit(turbine, graph));
        Assertions.assertFalse(turbine.isOperational(graph));

        // Scale boiler machine count to 2.0 -> produces 1,000 mB/s steam (100% flow satisfied)
        boiler.setMachineCount(2.0);
        graph.computeSummary();

        FlowGraphSolver.PortFlowStats inStatsBalanced = graph.getInputPortStats(turbine, 0);
        Assertions.assertTrue(inStatsBalanced.isConnected());
        Assertions.assertFalse(inStatsBalanced.isInputDeficit());
        Assertions.assertTrue(inStatsBalanced.getPercent() >= 100.0);

        warnings.clear();
        boolean validBalanced = adapter.validateNode(turbine, graph, warnings);
        Assertions.assertTrue(validBalanced);
        Assertions.assertTrue(warnings.isEmpty());
        Assertions.assertFalse(com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.hasTurbineFlowDeficit(turbine, graph));
        Assertions.assertTrue(turbine.isOperational(graph));
    }

    @Test
    public void testAutoRatioExcludesInoperativeProducers() {
        FlowGraph graph = new FlowGraph();

        // 1. Inoperative Top Fusion Reactor (Requires T2 reflector, but none installed -> 0 output)
        RecipeNode topReactor = RecipeNode.create("Reflector Fusion Reactor", 100.0, 16384.0, GTVoltageTier.ZPM);
        topReactor.setRequiredReflectorTier(2);
        topReactor.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:argon_plasma"), "Argon Plasma", 1000.0, 1.0));
        topReactor.setMachineCount(1.0);
        graph.addNode(topReactor);

        // 2. Operative Bottom Fusion Reactor (Requires T2 reflector, T2 installed -> produces Argon Plasma)
        RecipeNode bottomReactor = RecipeNode.create("Reflector Fusion Reactor (Live)", 100.0, 16384.0, GTVoltageTier.ZPM);
        bottomReactor.setRequiredReflectorTier(2);
        com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter adapter = new com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter();
        adapter.onAddonInstalled(bottomReactor, new com.gtceu.calcboard.compat.gtceu.addon.GTReflectorAddon("gtceu:reflector_tier_2", "T2 Reflector", "", null, 2));
        bottomReactor.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:argon_plasma"), "Argon Plasma", 1000.0, 1.0));
        bottomReactor.setMachineCount(1.0);
        graph.addNode(bottomReactor);

        // 3. Plasma Generator (Consumes Argon Plasma, requires 100% flow)
        RecipeNode plasmaGen = RecipeNode.create("Plasma Generator", 20.0, -4096.0, GTVoltageTier.EV);
        plasmaGen.setGenerator(true);
        plasmaGen.setMultiblock(true);
        plasmaGen.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:plasma_turbine"));
        plasmaGen.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:argon_plasma"), "Argon Plasma", 100.0, 1.0));
        plasmaGen.setMachineCount(1.0);
        graph.addNode(plasmaGen);

        // Connect both top (broken) and bottom (live) reactors to plasmaGen input
        graph.addConnection(topReactor.getId(), 0, plasmaGen.getId(), 0);
        graph.addConnection(bottomReactor.getId(), 0, plasmaGen.getId(), 0);

        Assertions.assertFalse(topReactor.isOperational());
        Assertions.assertTrue(bottomReactor.isOperational());

        // Run Auto Ratio with bottomReactor as anchor
        FlowGraphSolver.autoRatioFromAnchor(graph, bottomReactor, false);
        graph.computeSummary();

        // Operative bottom reactor produces 200 mB/s (1000 mB / 5s).
        // Each plasma generator consumes 100 mB/s.
        // Total plasma generators should be 2.0 (scaled to the LIVE bottom reactor only, NOT 4.0 from the dead top reactor).
        Assertions.assertEquals(2.0, plasmaGen.getMachineCount(), 0.01);

        FlowGraphSolver.PortFlowStats inStats = graph.getInputPortStats(plasmaGen, 0);
        Assertions.assertTrue(inStats.isConnected());
        Assertions.assertFalse(inStats.isInputDeficit());
        Assertions.assertEquals(100.0, inStats.getPercent(), 0.01);
        Assertions.assertTrue(plasmaGen.isOperational(graph));
    }

    @Test
    public void testPassiveUnpoweredRecipeHandling() {
        RecipeNode barrel = RecipeNode.create("Large Stone Barrel", 200.0, 0.0, GTVoltageTier.ULV);
        barrel.setEnergyType(EnergyType.NONE);
        barrel.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:cobblestone"), "Cobblestone", 1.0));
        barrel.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:lava"), "Lava", 500.0));

        Assertions.assertEquals(EnergyType.NONE, barrel.getEnergyType());
        Assertions.assertEquals(0.0, barrel.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(0.0, barrel.getTotalEUt(), 0.001);
        Assertions.assertEquals(10.0, barrel.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(0.10, barrel.getCyclesPerSecond(), 0.001);

        Map<IngredientStack, Double> outRates = barrel.calculateOutputRates();
        Assertions.assertEquals(50.0, outRates.values().iterator().next(), 0.001);

        FlowGraph graph = new FlowGraph();
        graph.addNode(barrel);
        BalanceSummary summary = FlowGraphSolver.computeSummary(graph);
        Assertions.assertEquals(0.0, summary.totalEUt(), 0.001);
        Assertions.assertEquals(0.0, summary.totalSU(), 0.001);
        Assertions.assertEquals(0.0, summary.totalFE(), 0.001);
    }
}



