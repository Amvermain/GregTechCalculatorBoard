package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class JunctionRecirculationLoopRegressionTest {

    @Test
    @DisplayName("Regression: Junction Node in damped recirculation loop must converge to steady-state efficiency without false growth warning")
    public void testDampedLoopWithJunctionNode() {
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

        RecipeNode feed = RecipeNode.create("External Acid Source", 20.0, 30.0, GTVoltageTier.LV);
        feed.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:acid"), "Acid", 10.0, 1.0));
        feed.setMachineCount(1.0);
        graph.addNode(feed);

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:acid"), "Acid", 133.5, 1.0));
        graph.addNode(junction);

        // Wiring:
        // extractor -> separator
        graph.addConnection(extractor.getId(), 0, separator.getId(), 0);
        // separator acid output -> junction
        graph.addConnection(separator.getId(), 0, junction.getId(), 0);
        // external acid feed -> junction
        graph.addConnection(feed.getId(), 0, junction.getId(), 0);
        // junction -> extractor acid input
        graph.addConnection(junction.getId(), 0, extractor.getId(), 0);

        // 1. Stability Analyzer test: must not flag positive_feedback (Growth)
        ProcessStabilityAnalyzer.DivergenceContext divCtx = new ProcessStabilityAnalyzer.DivergenceContext();
        ProcessStabilityAnalyzer.detectUnfedDeficitLoops(graph, null, divCtx);
        Assertions.assertNotEquals("positive_feedback", divCtx.getReason(extractor.getId()),
                "Loop should not be flagged as positive feedback / growth loop");
        Assertions.assertNotEquals("positive_feedback", divCtx.getReason(separator.getId()),
                "Loop should not be flagged as positive feedback / growth loop");

        // 2. Efficiency computation: must converge to steady state (~30 / 133.5 = ~0.2247), NOT 0.0
        Map<String, Double> effMap = graph.computeNodeEfficiencies();
        double expectedEff = 30.0 / 133.5;
        Assertions.assertEquals(expectedEff, effMap.get(extractor.getId()), 0.01,
                "Extractor efficiency should converge to steady-state efficiency (~0.225) instead of 0.0");
        Assertions.assertEquals(expectedEff, effMap.get(separator.getId()), 0.01,
                "Separator efficiency should converge to steady-state efficiency (~0.225) instead of 0.0");

        // 3. Port flow stats: should recognize steady state recirculation
        FlowGraphSolver.PortFlowStats stats = graph.getInputPortStats(extractor, 0);
        Assertions.assertTrue(stats.isConnected());
        Assertions.assertTrue(stats.isSteadyStateRecirculating(), "Input port should be marked as steady-state recirculating");
        Assertions.assertFalse(stats.isInputDeficit(), "Input port should not be in deficit");
    }

    @Test
    @DisplayName("Regression: Cycle ratio calculation through Junction Node must preserve step ratio and not trigger positive_feedback")
    public void testCycleThroughJunctionPreservesRatio() {
        FlowGraph graph = new FlowGraph();

        // Node A produces 20.0 of intermediate per cycle, consumes 10.0 of input
        RecipeNode nodeA = RecipeNode.create("Machine A", 20.0, 30.0, GTVoltageTier.LV);
        nodeA.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:chem_b"), "Chem B", 10.0, 1.0));
        nodeA.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:chem_a"), "Chem A", 20.0, 1.0));
        nodeA.setMachineCount(1.0);
        graph.addNode(nodeA);

        // Node B consumes 10.0 of Chem A, produces 2.0 of Chem B (overall loop ratio: (20/10) * (2/10) = 0.4 < 1.0)
        RecipeNode nodeB = RecipeNode.create("Machine B", 20.0, 30.0, GTVoltageTier.LV);
        nodeB.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:chem_a"), "Chem A", 10.0, 1.0));
        nodeB.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:chem_b"), "Chem B", 2.0, 1.0));
        nodeB.setMachineCount(1.0);
        graph.addNode(nodeB);

        RecipeNode junction = RecipeNode.createReroute(0, 0);
        junction.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:chem_b"), "Chem B", 10.0, 1.0));
        graph.addNode(junction);

        // Cycle: Node A -> Node B -> Junction -> Node A
        graph.addConnection(nodeA.getId(), 0, nodeB.getId(), 0);
        graph.addConnection(nodeB.getId(), 0, junction.getId(), 0);
        graph.addConnection(junction.getId(), 0, nodeA.getId(), 0);

        ProcessStabilityAnalyzer.DivergenceContext divCtx = new ProcessStabilityAnalyzer.DivergenceContext();
        ProcessStabilityAnalyzer.detectUnfedDeficitLoops(graph, null, divCtx);
    }

    @Test
    @DisplayName("User Blueprint: Compare normal blueprint vs broken blueprint")
    public void testUserBlueprintReproduction() {
        String normalBp = "GTBOARD:Page 16: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";
        String brokenBp = "GTBOARD:Page 16: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";

        System.out.println("=== TESTING NORMAL BLUEPRINT ===");
        runBlueprintTest(normalBp);

        System.out.println("\n=== TESTING BROKEN BLUEPRINT ===");
        runBlueprintTest(brokenBp);
    }

    private void runBlueprintTest(String bpString) {
        com.gtceu.calcboard.api.storage.BlueprintPackage pkg = com.gtceu.calcboard.api.storage.BlueprintCodec.importPackageFromString(bpString);
        Assertions.assertNotNull(pkg);
        FlowGraph graph = pkg.getGraph();
        Assertions.assertNotNull(graph);

        RecipeNode anchor = null;
        for (RecipeNode n : graph.getNodes()) {
            if (n.isBaseNode()) anchor = n;
        }
        Assertions.assertNotNull(anchor, "Must have an anchor node");

        AutoRatioResult result = AutoRatioEngine.autoRatioFromAnchor(graph, anchor, false);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.divergentNodeIds().isEmpty(), "Divergent nodes must be empty, but was: " + result.divergentNodeIds());

        RecipeNode lbv = graph.findNodeById("8bf36233-946f-4b8b-9f66-74265925c9e3");
        Assertions.assertNotNull(lbv, "Large Brewing Vat must exist");
        Assertions.assertEquals(70.3125, lbv.getMachineCount(), 0.01, "LBV count should converge to 70.3125");

        for (RecipeNode n : graph.getNodes()) {
            Assertions.assertFalse(
                    Boolean.TRUE.equals(n.getProperties().get(NodeProperties.DIVERGENCE_WARNING)),
                    "Node " + n.getName() + " should not have divergence warning"
            );
        }
    }
}
