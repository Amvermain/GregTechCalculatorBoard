package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class DampedRecirculationLoopTest {

    private FlowGraph createBromineDampedLoop(double extFeedRate) {
        FlowGraph graph = new FlowGraph();

        RecipeNode extractor = RecipeNode.create("Acid Extractor", 20.0, 30.0, GTVoltageTier.LV);
        extractor.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:acid"), "Acid", 133.5, 1.0));
        extractor.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:rich_slurry"), "Rich Slurry", 133.5, 1.0));
        extractor.setMachineCount(1.0);
        graph.addNode(extractor);

        RecipeNode separator = RecipeNode.create("Slurry Separator", 20.0, 30.0, GTVoltageTier.LV);
        separator.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:rich_slurry"), "Rich Slurry", 133.5, 1.0));
        separator.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:acid"), "Acid", 89.0, 1.0));
        separator.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:bromine"), "Bromine", 44.5, 1.0));
        separator.setMachineCount(1.0);
        graph.addNode(separator);

        graph.addConnection(extractor.getId(), 0, separator.getId(), 0);
        graph.addConnection(separator.getId(), 0, extractor.getId(), 0);

        if (extFeedRate > 0.0) {
            RecipeNode feed = RecipeNode.create("External Acid Source", 20.0, 30.0, GTVoltageTier.LV);
            feed.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:acid"), "Acid", extFeedRate, 1.0));
            feed.setMachineCount(1.0);
            graph.addNode(feed);
            graph.addConnection(feed.getId(), 0, extractor.getId(), 0);
        }

        return graph;
    }

    @Test
    @DisplayName("US-1: Damped recirculation loop converges analytically to steady-state efficiency")
    public void testClosedFormAnalyticalConvergence() {
        FlowGraph graph = createBromineDampedLoop(10.0);

        Map<String, Double> effMap = graph.computeNodeEfficiencies();

        RecipeNode extractor = graph.getNodes().stream().filter(n -> n.getName().contains("Extractor")).findFirst().orElseThrow();
        RecipeNode separator = graph.getNodes().stream().filter(n -> n.getName().contains("Separator")).findFirst().orElseThrow();

        double expectedEff = 30.0 / 133.5;

        Assertions.assertEquals(expectedEff, effMap.get(extractor.getId()), 0.005);
        Assertions.assertEquals(expectedEff, effMap.get(separator.getId()), 0.005);
    }

    @Test
    @DisplayName("US-2: PortFlowStats identifies steady-state recirculation and suppresses deficit warning")
    public void testSteadyStateRecirculatingPortStats() {
        FlowGraph graph = createBromineDampedLoop(10.0);
        graph.computeNodeEfficiencies();

        RecipeNode extractor = graph.getNodes().stream().filter(n -> n.getName().contains("Extractor")).findFirst().orElseThrow();
        FlowGraphSolver.PortFlowStats stats = graph.getInputPortStats(extractor, 0);

        Assertions.assertTrue(stats.isConnected());
        Assertions.assertTrue(stats.isSteadyStateRecirculating());
        Assertions.assertFalse(stats.isInputDeficit());
        Assertions.assertFalse(stats.isNominalDeficit());

        Assertions.assertEquals(10.0, stats.externalSupplyRate(), 0.1);
        Assertions.assertEquals(20.0, stats.loopSupplyRate(), 0.5);
        Assertions.assertEquals(30.0, stats.connectedRate(), 0.5);
        Assertions.assertEquals(89.0 / 133.5, stats.recirculationRatio(), 0.001);
    }

    @Test
    @DisplayName("US-3: One-click scaling resizes loop machines to steady-state capacity")
    public void testScaleLoopToSteadyState() {
        FlowGraph graph = createBromineDampedLoop(10.0);
        graph.computeNodeEfficiencies();

        RecipeNode extractor = graph.getNodes().stream().filter(n -> n.getName().contains("Extractor")).findFirst().orElseThrow();
        RecipeNode separator = graph.getNodes().stream().filter(n -> n.getName().contains("Separator")).findFirst().orElseThrow();

        int changed = FlowBalanceMatrixSolver.scaleLoopToSteadyState(graph, extractor.getId());
        Assertions.assertEquals(2, changed);

        double expectedScaledCount = Math.round(1.0 * (30.0 / 133.5) * 1000.0) / 1000.0;
        Assertions.assertEquals(expectedScaledCount, extractor.getMachineCount(), 0.001);
        Assertions.assertEquals(expectedScaledCount, separator.getMachineCount(), 0.001);

        Map<String, Double> newEffMap = graph.computeNodeEfficiencies();
        Assertions.assertEquals(1.0, newEffMap.get(extractor.getId()), 0.01);
        Assertions.assertEquals(1.0, newEffMap.get(separator.getId()), 0.01);

        FlowGraphSolver.PortFlowStats scaledStats = graph.getInputPortStats(extractor, 0);
        Assertions.assertTrue(scaledStats.isBalanced());
        Assertions.assertFalse(scaledStats.isSteadyStateRecirculating());
    }

    @Test
    @DisplayName("US-4: Edge Cases - Zero feed, excess feed, and throttled feed")
    public void testDampedLoopEdgeCases() {
        FlowGraph zeroFeedGraph = createBromineDampedLoop(0.0);
        Map<String, Double> zeroEffMap = zeroFeedGraph.computeNodeEfficiencies();
        for (RecipeNode n : zeroFeedGraph.getNodes()) {
            Assertions.assertEquals(0.0, zeroEffMap.get(n.getId()), 0.001);
        }

        FlowGraph excessGraph = createBromineDampedLoop(100.0);
        Map<String, Double> excessEffMap = excessGraph.computeNodeEfficiencies();
        RecipeNode excessExtractor = excessGraph.getNodes().stream().filter(n -> n.getName().contains("Extractor")).findFirst().orElseThrow();
        Assertions.assertEquals(1.0, excessEffMap.get(excessExtractor.getId()), 0.001);
        FlowGraphSolver.PortFlowStats excessStats = excessGraph.getInputPortStats(excessExtractor, 0);
        Assertions.assertFalse(excessStats.isSteadyStateRecirculating());
        Assertions.assertTrue(excessStats.connectedRate() >= 133.5 - 0.001);

        int changedOnFull = FlowBalanceMatrixSolver.scaleLoopToSteadyState(excessGraph, excessExtractor.getId());
        Assertions.assertEquals(0, changedOnFull);
    }

    @Test
    @DisplayName("Regression: Upstream secondary input bottleneck throttles damped loop correctly")
    public void testUpstreamSecondaryInputBottleneckClampsLoop() {
        FlowGraph graph = createBromineDampedLoop(10.0);

        RecipeNode separator = graph.getNodes().stream().filter(n -> n.getName().contains("Separator")).findFirst().orElseThrow();
        separator.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 50.0, 1.0));

        RecipeNode waterFeed = RecipeNode.create("Throttled Water Source", 20.0, 30.0, GTVoltageTier.LV);
        waterFeed.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 5.0, 1.0));
        waterFeed.setMachineCount(1.0);
        graph.addNode(waterFeed);
        graph.addConnection(waterFeed.getId(), 0, separator.getId(), 1);

        Map<String, Double> effMap = graph.computeNodeEfficiencies();

        Assertions.assertEquals(0.10, effMap.get(separator.getId()), 0.01);

        RecipeNode extractor = graph.getNodes().stream().filter(n -> n.getName().contains("Extractor")).findFirst().orElseThrow();
        double expectedExtractorEff = (10.0 + 89.0 * 0.10) / 133.5;
        Assertions.assertEquals(expectedExtractorEff, effMap.get(extractor.getId()), 0.01);
    }

    @Test
    @DisplayName("Regression: Port receiving zero loop recirculation is not steady-state recirculating")
    public void testZeroLoopSupplyIsNotSteadyStateRecirculating() {
        FlowGraph graph = createBromineDampedLoop(10.0);
        graph.computeNodeEfficiencies();

        RecipeNode separator = graph.getNodes().stream().filter(n -> n.getName().contains("Separator")).findFirst().orElseThrow();
        FlowGraphSolver.PortFlowStats slurryStats = graph.getInputPortStats(separator, 0);

        Assertions.assertFalse(slurryStats.isSteadyStateRecirculating());
        Assertions.assertEquals(0.0, slurryStats.loopSupplyRate(), 0.001);
    }

    @Test
    @DisplayName("Regression: Internal intermediate throttling in surplus loop does not trigger damped loop extinction")
    public void testInternalThrottlingInSurplusLoopDoesNotExtinguish() {
        FlowGraph graph = new FlowGraph();

        RecipeNode polarizer = RecipeNode.create("Polarizer", 20.0, 30.0, GTVoltageTier.EV);
        polarizer.setMachineCount(169.0);
        polarizer.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star_shard"), "Nether Star Shard", 1.0));
        polarizer.addOutput(IngredientStack.item(ResourceLocation.tryParse("star_tech:energized_nether_star_shard"), "Energized Nether Star Shard", 1.0));
        graph.addNode(polarizer);

        RecipeNode[] autoclaves = new RecipeNode[4];
        for (int i = 0; i < 4; i++) {
            autoclaves[i] = RecipeNode.create("Autoclave " + (i + 1), 48.0, 30.0, GTVoltageTier.IV);
            autoclaves[i].setMachineCount(102.0);
            autoclaves[i].addInput(IngredientStack.item(ResourceLocation.tryParse("star_tech:energized_nether_star_shard"), "Energized Nether Star Shard", 1.0));
            autoclaves[i].addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_" + i), "Fluid " + i, 200.0, 1.0));
            autoclaves[i].addOutput(IngredientStack.item(ResourceLocation.tryParse("star_tech:infused_shard_" + i), "Infused Shard " + i, 1.0, 0.50));
            autoclaves[i].addOutput(IngredientStack.item(ResourceLocation.tryParse("star_tech:infused_shard_" + i), "Infused Shard " + i, 1.0, 0.45));
            graph.addNode(autoclaves[i]);
            graph.addConnection(polarizer.getId(), 0, autoclaves[i].getId(), 0);
        }

        RecipeNode formingPress = RecipeNode.create("Forming Press", 15.0, 30.0, GTVoltageTier.IV);
        formingPress.setMachineCount(32.0);
        for (int i = 0; i < 4; i++) {
            formingPress.addInput(IngredientStack.item(ResourceLocation.tryParse("star_tech:infused_shard_" + i), "Infused Shard " + i, 1.0));
            graph.addConnection(autoclaves[i].getId(), 0, formingPress.getId(), i);
            graph.addConnection(autoclaves[i].getId(), 1, formingPress.getId(), i);
        }
        formingPress.addOutput(IngredientStack.item(ResourceLocation.tryParse("star_tech:impure_nether_star"), "Impure Nether Star", 1.0));
        graph.addNode(formingPress);

        RecipeNode implosion = RecipeNode.create("Implosion Compressor", 10.0, 30.0, GTVoltageTier.EV);
        implosion.setMachineCount(21.05);
        implosion.addInput(IngredientStack.item(ResourceLocation.tryParse("star_tech:impure_nether_star"), "Impure Nether Star", 1.0));
        implosion.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:industrial_tnt"), "Industrial TNT", 4.0));
        implosion.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        implosion.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:dark_ash_dust"), "Dark Ash Dust", 1.0, 0.25));
        graph.addNode(implosion);
        graph.addConnection(formingPress.getId(), 0, implosion.getId(), 0);

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        graph.addNode(junction);
        graph.addConnection(implosion.getId(), 0, junction.getId(), 0);

        RecipeNode forgeHammer = RecipeNode.create("Forge Hammer", 15.0, 30.0, GTVoltageTier.HV);
        forgeHammer.setMachineCount(26.0);
        forgeHammer.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star"), "Nether Star", 1.0));
        forgeHammer.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:nether_star_shard"), "Nether Star Shard", 5.0));
        graph.addNode(forgeHammer);
        graph.addConnection(junction.getId(), 0, forgeHammer.getId(), 0);

        graph.addConnection(forgeHammer.getId(), 0, polarizer.getId(), 0);

        Map<String, Double> effs = graph.computeNodeEfficiencies();

        Assertions.assertTrue(effs.get(polarizer.getId()) > 0.95, "Polarizer must stay operational");
        Assertions.assertTrue(effs.get(formingPress.getId()) > 0.90, "Forming Press must operate at ~93-94%");
        Assertions.assertTrue(effs.get(implosion.getId()) > 0.95, "Implosion Compressor must stay operational");
        Assertions.assertTrue(effs.get(forgeHammer.getId()) > 0.95, "Forge Hammer must stay operational");
        for (RecipeNode auto : autoclaves) {
            Assertions.assertTrue(effs.get(auto.getId()) > 0.95, "Autoclave must stay operational");
        }
    }

    @Test
    @DisplayName("US-5: Unfed damped recirculation loop displays warning badge and port notice, then self-heals upon feed")
    public void testUnfedDampedLoopWarningAndSelfHealing() {
        FlowGraph graph = createBromineDampedLoop(0.0);
        Map<String, Double> effMap = graph.computeNodeEfficiencies();

        RecipeNode extractor = graph.getNodes().stream().filter(n -> n.getName().contains("Extractor")).findFirst().orElseThrow();
        RecipeNode separator = graph.getNodes().stream().filter(n -> n.getName().contains("Separator")).findFirst().orElseThrow();

        Assertions.assertEquals(0.0, effMap.get(extractor.getId()), 0.001);
        Assertions.assertEquals(0.0, effMap.get(separator.getId()), 0.001);

        Assertions.assertFalse(extractor.getProperties().has(com.gtceu.calcboard.api.property.NodeProperties.DIVERGENCE_WARNING));
        Assertions.assertFalse(extractor.getProperties().has(com.gtceu.calcboard.api.property.NodeProperties.DIVERGENCE_REASON));
        Assertions.assertFalse(separator.getProperties().has(com.gtceu.calcboard.api.property.NodeProperties.DIVERGENCE_WARNING));
        Assertions.assertFalse(separator.getProperties().has(com.gtceu.calcboard.api.property.NodeProperties.DIVERGENCE_REASON));

        var badges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(extractor);
        Assertions.assertFalse(badges.isEmpty());
        Assertions.assertTrue(badges.stream().anyMatch(b -> b.text().contains("Damped") || b.text().contains("damped_loop") || b.text().contains("감쇄")));

        var explicitBadges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(extractor, graph);
        Assertions.assertFalse(explicitBadges.isEmpty());
        Assertions.assertTrue(explicitBadges.stream().anyMatch(b -> b.text().contains("Damped") || b.text().contains("damped_loop") || b.text().contains("감쇄")));

        FlowGraphSolver.PortFlowStats unfedStats = graph.getInputPortStats(extractor, 0);
        Assertions.assertTrue(unfedStats.isUnfedDampedLoop());
        Assertions.assertFalse(unfedStats.isSteadyStateRecirculating());
        Assertions.assertTrue(unfedStats.isInputDeficit());

        RecipeNode feed = RecipeNode.create("External Acid Source", 20.0, 30.0, GTVoltageTier.LV);
        feed.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:acid"), "Acid", 10.0, 1.0));
        feed.setMachineCount(1.0);
        graph.addNode(feed);
        graph.addConnection(feed.getId(), 0, extractor.getId(), 0);

        graph.computeNodeEfficiencies();

        Assertions.assertFalse(Boolean.TRUE.equals(extractor.getProperties().get(com.gtceu.calcboard.api.property.NodeProperties.DIVERGENCE_WARNING)));
        Assertions.assertFalse(Boolean.TRUE.equals(separator.getProperties().get(com.gtceu.calcboard.api.property.NodeProperties.DIVERGENCE_WARNING)));

        var healedBadges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(extractor);
        Assertions.assertTrue(healedBadges.stream().noneMatch(b -> b.text().contains("Damped") || b.text().contains("damped_loop") || b.text().contains("감쇄")));

        var healedExplicitBadges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(extractor, graph);
        Assertions.assertTrue(healedExplicitBadges.stream().noneMatch(b -> b.text().contains("Damped") || b.text().contains("damped_loop") || b.text().contains("감쇄")));

        FlowGraphSolver.PortFlowStats healedStats = graph.getInputPortStats(extractor, 0);
        Assertions.assertFalse(healedStats.isUnfedDampedLoop());
        Assertions.assertTrue(healedStats.isSteadyStateRecirculating());
    }

    @Test
    @DisplayName("Regression: Batch mode formatting supports steady-state recirculation symbol")
    public void testBatchModeFormattingWithSteadyRecirculation() {
        IngredientStack stack = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:acid"), "Acid", 133.5, 1.0);
        String formatted = com.gtceu.calcboard.client.gui.util.FormatUtil.formatBatchConnectedInput(30.0, 133.5, stack, false, true);
        Assertions.assertTrue(formatted.contains("🔄"));
        Assertions.assertFalse(formatted.contains("⚠"));
    }

    @Test
    @DisplayName("Regression: Declarative badge evaluates on-demand with explicit graph context when detached")
    public void testDeclarativeBadgeResolutionWithDetachedFallback() {
        FlowGraph graph = createBromineDampedLoop(0.0);
        RecipeNode extractor = graph.getNodes().stream().filter(n -> n.getName().contains("Extractor")).findFirst().orElseThrow();

        extractor.setParentGraph(null);
        var detachedBadges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(extractor);
        Assertions.assertTrue(detachedBadges.isEmpty());

        var explicitBadges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(extractor, graph);
        Assertions.assertFalse(explicitBadges.isEmpty());
        Assertions.assertTrue(explicitBadges.stream().anyMatch(b -> b.text().contains("Damped") || b.text().contains("damped_loop") || b.text().contains("감쇄")));

        extractor.setParentGraph(graph);
        var restoredBadges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(extractor);
        Assertions.assertFalse(restoredBadges.isEmpty());
        Assertions.assertTrue(restoredBadges.stream().anyMatch(b -> b.text().contains("Damped") || b.text().contains("damped_loop") || b.text().contains("감쇄")));
    }

    @Test
    @DisplayName("Regression: FlowGraph.switchNodeRecipe invalidates port stats cache on modified nodes")
    public void testSwitchNodeRecipeInvalidatesPortStatsCache() {
        FlowGraph graph = createBromineDampedLoop(0.0);
        RecipeNode extractor = graph.getNodes().stream().filter(n -> n.getName().contains("Extractor")).findFirst().orElseThrow();

        FlowGraphSolver.PortFlowStats initialStats = graph.getInputPortStats(extractor, 0);
        Assertions.assertTrue(initialStats.isUnfedDampedLoop());

        RecipeNode template = RecipeNode.create("Alternative Extractor", 20.0, 30.0, GTVoltageTier.LV);
        template.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:water"), "Water", 50.0, 1.0));
        template.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:rich_slurry"), "Rich Slurry", 50.0, 1.0));

        graph.switchNodeRecipe(extractor, template);

        FlowGraphSolver.PortFlowStats newStats = graph.getInputPortStats(extractor, 0);
        Assertions.assertFalse(newStats.isUnfedDampedLoop());
        Assertions.assertEquals(50.0, newStats.requiredOrProducedRate(), 0.001);
    }

    @Test
    @DisplayName("Regression: RecipeNode setParentGraph marks operational dirty to clear stale graph caching")
    public void testParentGraphLifecycleAndOperationalDirty() {
        RecipeNode node = RecipeNode.create("Test Node", 20.0, 30.0, GTVoltageTier.LV);
        FlowGraph graphA = new FlowGraph();
        graphA.addNode(node);
        Assertions.assertSame(graphA, node.getParentGraph());

        node.isOperational(graphA);

        node.setParentGraph(null);
        Assertions.assertNull(node.getParentGraph());

        FlowGraph graphB = new FlowGraph();
        graphB.addNode(node);
        Assertions.assertSame(graphB, node.getParentGraph());
    }

    private FlowGraph createThreeMachineLoop(double extFeedRate) {
        FlowGraph graph = new FlowGraph();

        RecipeNode topMachine = RecipeNode.create("Top Machine", 5.0, 30.0, GTVoltageTier.LV);
        topMachine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:light_brown"), "Light Brown", 1000.0, 1.0));
        topMachine.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:brown"), "Brown", 1000.0, 1.0));
        topMachine.setMachineCount(1.0);
        graph.addNode(topMachine);

        RecipeNode bottomLeftMachine = RecipeNode.create("Bottom Left Machine", 2.0, 30.0, GTVoltageTier.LV);
        bottomLeftMachine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:brown"), "Brown", 2000.0, 1.0));
        bottomLeftMachine.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:yellow"), "Yellow", 2000.0, 1.0));
        bottomLeftMachine.setMachineCount(1.0);
        graph.addNode(bottomLeftMachine);

        RecipeNode bottomRightMachine = RecipeNode.create("Bottom Right Machine", 2.0, 30.0, GTVoltageTier.LV);
        bottomRightMachine.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:yellow"), "Yellow", 3000.0, 1.0));
        bottomRightMachine.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:light_brown"), "Light Brown", 2000.0, 1.0));
        bottomRightMachine.setMachineCount(1.0);
        graph.addNode(bottomRightMachine);

        graph.addConnection(topMachine.getId(), 0, bottomLeftMachine.getId(), 0);
        graph.addConnection(bottomLeftMachine.getId(), 0, bottomRightMachine.getId(), 0);
        graph.addConnection(bottomRightMachine.getId(), 0, topMachine.getId(), 0);

        if (extFeedRate > 0.0) {
            RecipeNode feed = RecipeNode.create("External Light Brown Source", 20.0, 30.0, GTVoltageTier.LV);
            feed.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:light_brown"), "Light Brown", extFeedRate, 1.0));
            feed.setMachineCount(1.0);
            graph.addNode(feed);
            graph.addConnection(feed.getId(), 0, topMachine.getId(), 0);
        }

        return graph;
    }

    @Test
    @DisplayName("Regression: Three-machine multi-resource damped loop without feed converges to 0% and flags unfed")
    public void testThreeMachineDampedLoopWithoutFeedExtinguishes() {
        FlowGraph graph = createThreeMachineLoop(0.0);
        Map<String, Double> effMap = graph.computeNodeEfficiencies();

        RecipeNode topMachine = graph.getNodes().stream().filter(n -> n.getName().equals("Top Machine")).findFirst().orElseThrow();
        RecipeNode bottomLeftMachine = graph.getNodes().stream().filter(n -> n.getName().equals("Bottom Left Machine")).findFirst().orElseThrow();
        RecipeNode bottomRightMachine = graph.getNodes().stream().filter(n -> n.getName().equals("Bottom Right Machine")).findFirst().orElseThrow();

        Assertions.assertEquals(0.0, effMap.get(topMachine.getId()), 0.001);
        Assertions.assertEquals(0.0, effMap.get(bottomLeftMachine.getId()), 0.001);
        Assertions.assertEquals(0.0, effMap.get(bottomRightMachine.getId()), 0.001);

        FlowGraphSolver.PortFlowStats topStats = graph.getInputPortStats(topMachine, 0);
        Assertions.assertTrue(topStats.isUnfedDampedLoop());
        Assertions.assertFalse(topStats.isSteadyStateRecirculating());
        Assertions.assertTrue(topStats.isInputDeficit());

        FlowGraphSolver.PortFlowStats bottomLeftStats = graph.getInputPortStats(bottomLeftMachine, 0);
        Assertions.assertTrue(bottomLeftStats.isUnfedDampedLoop());
        Assertions.assertFalse(bottomLeftStats.isSteadyStateRecirculating());
        Assertions.assertTrue(bottomLeftStats.isInputDeficit());

        FlowGraphSolver.PortFlowStats bottomRightStats = graph.getInputPortStats(bottomRightMachine, 0);
        Assertions.assertTrue(bottomRightStats.isUnfedDampedLoop());
        Assertions.assertFalse(bottomRightStats.isSteadyStateRecirculating());
        Assertions.assertTrue(bottomRightStats.isInputDeficit());
    }

    @Test
    @DisplayName("Regression: Three-machine multi-resource damped loop with external feed reaches steady-state")
    public void testThreeMachineDampedLoopWithFeedReachesSteadyState() {
        double extFeed = 1000.0;
        FlowGraph graph = createThreeMachineLoop(extFeed);
        Map<String, Double> effMap = graph.computeNodeEfficiencies();

        RecipeNode topMachine = graph.getNodes().stream().filter(n -> n.getName().equals("Top Machine")).findFirst().orElseThrow();
        RecipeNode bottomLeftMachine = graph.getNodes().stream().filter(n -> n.getName().equals("Bottom Left Machine")).findFirst().orElseThrow();
        RecipeNode bottomRightMachine = graph.getNodes().stream().filter(n -> n.getName().equals("Bottom Right Machine")).findFirst().orElseThrow();

        Assertions.assertEquals(0.75, effMap.get(topMachine.getId()), 0.01);
        Assertions.assertEquals(0.15, effMap.get(bottomLeftMachine.getId()), 0.01);
        Assertions.assertEquals(0.10, effMap.get(bottomRightMachine.getId()), 0.01);

        FlowGraphSolver.PortFlowStats topStats = graph.getInputPortStats(topMachine, 0);
        Assertions.assertTrue(topStats.isConnected());
        Assertions.assertFalse(topStats.isInputDeficit());
        Assertions.assertTrue(topStats.isSteadyStateRecirculating());
        Assertions.assertEquals(3000.0, topStats.connectedRate(), 1.0);
        Assertions.assertEquals(1000.0, topStats.externalSupplyRate(), 1.0);
        Assertions.assertEquals(2000.0, topStats.loopSupplyRate(), 1.0);
        Assertions.assertEquals(2.0 / 3.0, topStats.recirculationRatio(), 0.001);

        FlowGraphSolver.PortFlowStats bottomLeftStats = graph.getInputPortStats(bottomLeftMachine, 0);
        Assertions.assertTrue(bottomLeftStats.isInputDeficit());
        Assertions.assertEquals(3000.0, bottomLeftStats.connectedRate(), 1.0);
        Assertions.assertEquals(20000.0, bottomLeftStats.requiredOrProducedRate(), 1.0);

        FlowGraphSolver.PortFlowStats bottomRightStats = graph.getInputPortStats(bottomRightMachine, 0);
        Assertions.assertTrue(bottomRightStats.isInputDeficit());
        Assertions.assertEquals(3000.0, bottomRightStats.connectedRate(), 1.0);
        Assertions.assertEquals(30000.0, bottomRightStats.requiredOrProducedRate(), 1.0);

        FlowGraph fullFeedGraph = createThreeMachineLoop(4000.0 / 3.0);
        RecipeNode fullTopMachine = fullFeedGraph.getNodes().stream().filter(n -> n.getName().equals("Top Machine")).findFirst().orElseThrow();
        RecipeNode fullBottomLeft = fullFeedGraph.getNodes().stream().filter(n -> n.getName().equals("Bottom Left Machine")).findFirst().orElseThrow();
        RecipeNode fullBottomRight = fullFeedGraph.getNodes().stream().filter(n -> n.getName().equals("Bottom Right Machine")).findFirst().orElseThrow();
        Map<String, Double> fullEffMap = fullFeedGraph.computeNodeEfficiencies();

        Assertions.assertEquals(1.0, fullEffMap.get(fullTopMachine.getId()), 0.01);
        Assertions.assertEquals(0.20, fullEffMap.get(fullBottomLeft.getId()), 0.01);
        Assertions.assertEquals(2.0 / 15.0, fullEffMap.get(fullBottomRight.getId()), 0.01);

        FlowGraphSolver.PortFlowStats fullTopStats = fullFeedGraph.getInputPortStats(fullTopMachine, 0);
        Assertions.assertTrue(fullTopStats.isBalanced() || fullTopStats.isSteadyStateRecirculating());
        Assertions.assertFalse(fullTopStats.isInputDeficit());
    }

    @Test
    @DisplayName("Regression: Three-machine damped loop with surplus feed clamps to 100% without overflow")
    public void testThreeMachineDampedLoopWithSurplusFeedClampsToOneHundredPercent() {
        FlowGraph graph = createThreeMachineLoop(2000.0);
        Map<String, Double> effMap = graph.computeNodeEfficiencies();

        RecipeNode topMachine = graph.getNodes().stream().filter(n -> n.getName().equals("Top Machine")).findFirst().orElseThrow();
        RecipeNode bottomLeftMachine = graph.getNodes().stream().filter(n -> n.getName().equals("Bottom Left Machine")).findFirst().orElseThrow();
        RecipeNode bottomRightMachine = graph.getNodes().stream().filter(n -> n.getName().equals("Bottom Right Machine")).findFirst().orElseThrow();

        Assertions.assertEquals(1.0, effMap.get(topMachine.getId()), 0.001);
        Assertions.assertEquals(0.20, effMap.get(bottomLeftMachine.getId()), 0.01);
        Assertions.assertEquals(2.0 / 15.0, effMap.get(bottomRightMachine.getId()), 0.01);

        FlowGraphSolver.PortFlowStats topStats = graph.getInputPortStats(topMachine, 0);
        Assertions.assertTrue(topStats.isConnected());
        Assertions.assertTrue(topStats.isInputSurplus());
        Assertions.assertFalse(topStats.isInputDeficit());
    }

    @Test
    @DisplayName("Regression: Two-machine multi-feed damped loop converges to joint steady-state")
    public void testTwoMachineMultiFeedDampedLoopConverges() {
        FlowGraph graph = new FlowGraph();

        RecipeNode machineA = RecipeNode.create("Machine A", 20.0, 30.0, GTVoltageTier.LV);
        machineA.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_a"), "Fluid A", 100.0, 1.0));
        machineA.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_b"), "Fluid B", 100.0, 1.0));
        machineA.setMachineCount(1.0);
        graph.addNode(machineA);

        RecipeNode machineB = RecipeNode.create("Machine B", 20.0, 30.0, GTVoltageTier.LV);
        machineB.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_b"), "Fluid B", 100.0, 1.0));
        machineB.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_a"), "Fluid A", 50.0, 1.0));
        machineB.setMachineCount(1.0);
        graph.addNode(machineB);

        graph.addConnection(machineA.getId(), 0, machineB.getId(), 0);
        graph.addConnection(machineB.getId(), 0, machineA.getId(), 0);

        RecipeNode feedA = RecipeNode.create("Feed A", 20.0, 30.0, GTVoltageTier.LV);
        feedA.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_a"), "Fluid A", 40.0, 1.0));
        feedA.setMachineCount(1.0);
        graph.addNode(feedA);
        graph.addConnection(feedA.getId(), 0, machineA.getId(), 0);

        RecipeNode feedB = RecipeNode.create("Feed B", 20.0, 30.0, GTVoltageTier.LV);
        feedB.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fluid_b"), "Fluid B", 10.0, 1.0));
        feedB.setMachineCount(1.0);
        graph.addNode(feedB);
        graph.addConnection(feedB.getId(), 0, machineB.getId(), 0);

        Map<String, Double> effMap = graph.computeNodeEfficiencies();

        Assertions.assertEquals(0.90, effMap.get(machineA.getId()), 0.02);
        Assertions.assertEquals(1.00, effMap.get(machineB.getId()), 0.02);
    }
}

