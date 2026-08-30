package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.bom.MultiblockBOMCalculator;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.model.*;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.storage.NodeClipboard;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

public class CompoundNodeTest {

    @Test
    public void testCompoundNodeBuilderAndMetadata() {
        CompoundRecipeBuilder.LayerSpec layer1 = new CompoundRecipeBuilder.LayerSpec(
                "Layer I",
                List.of(IngredientStack.item(ResourceLocation.tryParse("gtceu:long_rod"), "Long Rod", 1.0),
                        IngredientStack.item(ResourceLocation.tryParse("gtceu:ring"), "Ring", 2.0)),
                List.of()
        );
        CompoundRecipeBuilder.LayerSpec layer2 = new CompoundRecipeBuilder.LayerSpec(
                "Layer II",
                List.of(IngredientStack.item(ResourceLocation.tryParse("gtceu:long_rod"), "Long Rod", 1.0),
                        IngredientStack.item(ResourceLocation.tryParse("gtceu:spool"), "Spool", 1.0),
                        IngredientStack.fluid(ResourceLocation.tryParse("gtceu:soldering_alloy"), "Soldering Alloy", 1300.0)),
                List.of()
        );
        CompoundRecipeBuilder.LayerSpec layer3 = new CompoundRecipeBuilder.LayerSpec(
                "Layer III",
                List.of(IngredientStack.item(ResourceLocation.tryParse("gtceu:long_rod"), "Long Rod", 1.0),
                        IngredientStack.item(ResourceLocation.tryParse("gtceu:dark_spool"), "Dark Spool", 1.0),
                        IngredientStack.fluid(ResourceLocation.tryParse("gtceu:soldering_alloy"), "Soldering Alloy", 1300.0)),
                List.of(IngredientStack.item(ResourceLocation.tryParse("gtceu:large_rotor"), "Large Rotor", 1.0))
        );

        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                "Large Rotor Machine",
                ResourceLocation.tryParse("gtceu:large_rotor_machine"),
                2400.0, // 120s = 2400 ticks
                245760.0,
                GTVoltageTier.UV,
                List.of(layer1, layer2, layer3),
                100,
                100
        );

        Assertions.assertEquals(3, cluster.nodes().size());
        Assertions.assertNotNull(cluster.frame());
        Assertions.assertTrue(cluster.frame().isCompoundFrame());

        RecipeNode n1 = cluster.nodes().get(0);
        RecipeNode n2 = cluster.nodes().get(1);
        RecipeNode n3 = cluster.nodes().get(2);

        Assertions.assertTrue(n1.isCompoundNode());
        Assertions.assertTrue(n1.isCompoundMaster());
        Assertions.assertEquals(0, n1.getCompoundLayerIndex());
        Assertions.assertEquals(3, n1.getCompoundTotalLayers());

        Assertions.assertTrue(n2.isCompoundNode());
        Assertions.assertFalse(n2.isCompoundMaster());
        Assertions.assertEquals(1, n2.getCompoundLayerIndex());

        Assertions.assertTrue(n3.isCompoundNode());
        Assertions.assertFalse(n3.isCompoundMaster());
        Assertions.assertEquals(2, n3.getCompoundLayerIndex());

        Assertions.assertEquals(n1.getCompoundGroupId(), n2.getCompoundGroupId());
        Assertions.assertEquals(n1.getCompoundGroupId(), n3.getCompoundGroupId());
        Assertions.assertEquals(n1.getCompoundGroupId(), cluster.frame().getCompoundGroupId());

        // Check badge generation
        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(n1);
        Assertions.assertFalse(badges.isEmpty());
        Assertions.assertTrue(badges.stream().anyMatch(b -> b.text().contains("I/III")));
    }

    @Test
    public void testCascadeDeletion() {
        FlowGraph graph = new FlowGraph();

        CompoundRecipeBuilder.LayerSpec layer1 = new CompoundRecipeBuilder.LayerSpec("Layer I", List.of(), List.of());
        CompoundRecipeBuilder.LayerSpec layer2 = new CompoundRecipeBuilder.LayerSpec("Layer II", List.of(), List.of());
        CompoundRecipeBuilder.LayerSpec layer3 = new CompoundRecipeBuilder.LayerSpec("Layer III", List.of(), List.of());

        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                "Large Rotor Machine",
                ResourceLocation.tryParse("gtceu:large_rotor_machine"),
                2400.0,
                245760.0,
                GTVoltageTier.UV,
                List.of(layer1, layer2, layer3),
                100,
                100
        );

        for (RecipeNode n : cluster.nodes()) {
            graph.addNode(n);
        }
        graph.addFrame(cluster.frame());

        // External independent node
        RecipeNode external = RecipeNode.create("Macerator", 100, 30, GTVoltageTier.LV);
        graph.addNode(external);

        Assertions.assertEquals(4, graph.getNodes().size());
        Assertions.assertEquals(1, graph.getFrames().size());

        // Delete Layer II node -> Should cascade delete Layer I, Layer II, Layer III and the CompoundFrame!
        RecipeNode layer2Node = cluster.nodes().get(1);
        graph.removeNode(layer2Node);

        Assertions.assertEquals(1, graph.getNodes().size());
        Assertions.assertEquals(external.getId(), graph.getNodes().get(0).getId());
        Assertions.assertEquals(0, graph.getFrames().size());
    }

    @Test
    public void testParameterSynchronization() {
        FlowGraph graph = new FlowGraph();

        CompoundRecipeBuilder.LayerSpec layer1 = new CompoundRecipeBuilder.LayerSpec("Layer I", List.of(), List.of());
        CompoundRecipeBuilder.LayerSpec layer2 = new CompoundRecipeBuilder.LayerSpec("Layer II", List.of(), List.of());

        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                "Large Rotor Machine",
                ResourceLocation.tryParse("gtceu:large_rotor_machine"),
                2400.0,
                245760.0,
                GTVoltageTier.UV,
                List.of(layer1, layer2),
                100,
                100
        );

        for (RecipeNode n : cluster.nodes()) {
            graph.addNode(n);
        }

        RecipeNode n1 = cluster.nodes().get(0);
        RecipeNode n2 = cluster.nodes().get(1);

        // Change Count on n1
        n1.setMachineCount(4.0);
        n1.setTargetTier(GTVoltageTier.UHV);
        n1.setOverclockMode(OverclockMode.PERFECT);
        graph.syncCompoundParameters(n1);

        Assertions.assertEquals(4.0, n2.getMachineCount(), 0.001);
        Assertions.assertEquals(GTVoltageTier.UHV, n2.getTargetTier());
        Assertions.assertEquals(OverclockMode.PERFECT, n2.getOverclockMode());

        // Change Count on n2 -> should sync back to n1
        n2.setMachineCount(1.5);
        graph.syncCompoundParameters(n2);

        Assertions.assertEquals(1.5, n1.getMachineCount(), 0.001);
    }

    @Test
    public void testSolverAndBOMDeduplication() {
        FlowGraph graph = new FlowGraph();

        CompoundRecipeBuilder.LayerSpec layer1 = new CompoundRecipeBuilder.LayerSpec(
                "Layer I",
                List.of(IngredientStack.item(ResourceLocation.tryParse("gtceu:long_rod"), "Long Rod", 1.0)),
                List.of()
        );
        CompoundRecipeBuilder.LayerSpec layer2 = new CompoundRecipeBuilder.LayerSpec(
                "Layer II",
                List.of(IngredientStack.item(ResourceLocation.tryParse("gtceu:long_rod"), "Long Rod", 1.0)),
                List.of()
        );
        CompoundRecipeBuilder.LayerSpec layer3 = new CompoundRecipeBuilder.LayerSpec(
                "Layer III",
                List.of(IngredientStack.item(ResourceLocation.tryParse("gtceu:long_rod"), "Long Rod", 1.0)),
                List.of(IngredientStack.item(ResourceLocation.tryParse("gtceu:large_rotor"), "Large Rotor", 1.0))
        );

        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                "Large Rotor Machine",
                ResourceLocation.tryParse("gtceu:large_rotor_machine"),
                2400.0, // 120s
                245760.0,
                GTVoltageTier.UV,
                List.of(layer1, layer2, layer3),
                100,
                100
        );

        for (RecipeNode n : cluster.nodes()) {
            graph.addNode(n);
        }

        BalanceSummary summary = FlowGraphSolver.computeSummary(graph);

        // Machine count should be exactly 1, not 3!
        Assertions.assertEquals(1, summary.totalMachineCount());

        // Consumed EU/t should be exactly 245,760, not 3x!
        Assertions.assertEquals(245760.0, summary.totalEUt(), 1.0);

        // Multiblock BOM Calculator should only count 1 machine
        MultiblockBOMSummary bomSummary = MultiblockBOMCalculator.calculateBOM(graph.getNodes(), false);
        Assertions.assertEquals(1, bomSummary.machineContributions().size());
        Assertions.assertEquals(1, bomSummary.totalMultiblockCount());
    }

    @Test
    public void testClipboardCopyAndPaste() {
        FlowGraph graph = new FlowGraph();

        CompoundRecipeBuilder.LayerSpec layer1 = new CompoundRecipeBuilder.LayerSpec("Layer I", List.of(), List.of());
        CompoundRecipeBuilder.LayerSpec layer2 = new CompoundRecipeBuilder.LayerSpec("Layer II", List.of(), List.of());

        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                "Large Rotor Machine",
                ResourceLocation.tryParse("gtceu:large_rotor_machine"),
                2400.0,
                245760.0,
                GTVoltageTier.UV,
                List.of(layer1, layer2),
                100,
                100
        );

        for (RecipeNode n : cluster.nodes()) {
            graph.addNode(n);
        }
        graph.addFrame(cluster.frame());

        // Copy selection consisting of ONLY Layer I
        RecipeNode n1 = cluster.nodes().get(0);
        NodeClipboard.getInstance().copy(graph, Set.of(n1.getId()));

        Assertions.assertTrue(NodeClipboard.getInstance().hasContent());

        // Paste at new location
        NodeClipboard.PasteResult result = NodeClipboard.getInstance().paste(graph, 500, 500);

        Assertions.assertEquals(2, result.nodes().size());
        Assertions.assertEquals(1, result.frames().size());

        RecipeNode pasted1 = result.nodes().get(0);
        RecipeNode pasted2 = result.nodes().get(1);
        CanvasGroupFrame pastedFrame = result.frames().get(0);

        // Pasted compound group ID should be a fresh new ID, distinct from the original
        Assertions.assertNotEquals(n1.getCompoundGroupId(), pasted1.getCompoundGroupId());
        Assertions.assertEquals(pasted1.getCompoundGroupId(), pasted2.getCompoundGroupId());
        Assertions.assertEquals(pasted1.getCompoundGroupId(), pastedFrame.getCompoundGroupId());
    }

    @Test
    public void testLayerSpecCustomDurationAndEUt() {
        CompoundRecipeBuilder.LayerSpec layer1 = new CompoundRecipeBuilder.LayerSpec(
                "Layer I", 800.0, 245760.0, List.of(), List.of()
        );
        CompoundRecipeBuilder.LayerSpec layer2 = new CompoundRecipeBuilder.LayerSpec(
                "Layer II", 800.0, 245760.0, List.of(), List.of()
        );
        CompoundRecipeBuilder.LayerSpec layer3 = new CompoundRecipeBuilder.LayerSpec(
                "Layer III", 800.0, 245760.0, List.of(), List.of()
        );

        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                "Large Rotor Machine",
                ResourceLocation.tryParse("gtceu:large_rotor_machine"),
                2400.0,
                245760.0,
                GTVoltageTier.UV,
                List.of(layer1, layer2, layer3),
                100,
                100
        );

        Assertions.assertEquals(3, cluster.nodes().size());
        for (RecipeNode n : cluster.nodes()) {
            Assertions.assertEquals(800.0, n.getBaseDurationTicks(), 0.001);
            Assertions.assertEquals(245760.0, n.getBaseEUt(), 0.001);
        }
    }

    @Test
    public void testCompoundNodeBottleneckPropagation() {
        FlowGraph graph = new FlowGraph();

        CompoundRecipeBuilder.LayerSpec layer1 = new CompoundRecipeBuilder.LayerSpec(
                "Layer I", 800.0, 245760.0,
                List.of(IngredientStack.item(ResourceLocation.tryParse("gtceu:long_rod"), "Long Rod", 1.0)),
                List.of()
        );
        CompoundRecipeBuilder.LayerSpec layer2 = new CompoundRecipeBuilder.LayerSpec(
                "Layer II", 800.0, 245760.0,
                List.of(IngredientStack.item(ResourceLocation.tryParse("gtceu:spool"), "Spool", 1.0)),
                List.of()
        );
        CompoundRecipeBuilder.LayerSpec layer3 = new CompoundRecipeBuilder.LayerSpec(
                "Layer III", 800.0, 245760.0,
                List.of(IngredientStack.item(ResourceLocation.tryParse("gtceu:dark_spool"), "Dark Spool", 1.0)),
                List.of(IngredientStack.item(ResourceLocation.tryParse("gtceu:large_rotor"), "Large Rotor", 1.0))
        );

        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                "Large Rotor Machine",
                ResourceLocation.tryParse("gtceu:large_rotor_machine"),
                2400.0,
                245760.0,
                GTVoltageTier.UV,
                List.of(layer1, layer2, layer3),
                100,
                100
        );

        for (RecipeNode n : cluster.nodes()) {
            graph.addNode(n);
        }

        RecipeNode layer1Node = cluster.nodes().get(0);
        RecipeNode layer2Node = cluster.nodes().get(1);
        RecipeNode layer3Node = cluster.nodes().get(2);

        // Create an under-supplying producer for Layer II's spool (produces 0.0125/s when Layer II demands 0.025/s -> 50% bottleneck)
        RecipeNode spoolProducer = RecipeNode.create("Spool Maker", 1600.0, 30.0, GTVoltageTier.LV);
        spoolProducer.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:spool"), "Spool", 1.0));
        graph.addNode(spoolProducer);

        // Connect spoolProducer (output 0) -> layer2Node (input 0)
        graph.addConnection(spoolProducer.getId(), 0, layer2Node.getId(), 0);

        Map<String, Double> effMap = FlowGraphSolver.computeNodeEfficiencies(graph);

        // Layer I has no connected inputs -> 1.0
        Assertions.assertEquals(1.0, effMap.get(layer1Node.getId()), 0.001);

        // Layer II is supplied with 0.0125/s against 0.025/s required -> 0.5 (50%)
        Assertions.assertEquals(0.5, effMap.get(layer2Node.getId()), 0.001);

        // Layer III is downstream in the compound group -> MUST propagate bottleneck to 0.5 (50%)!
        Assertions.assertEquals(0.5, effMap.get(layer3Node.getId()), 0.001);
        Assertions.assertEquals(0.5, layer3Node.getEfficiency(), 0.001);

        // Layer III effective output rate should be scaled down to 50% (0.0125/s instead of nominal 0.025/s)
        Assertions.assertEquals(0.0125, layer3Node.getOutputSlotRate(0, true), 0.0001);
    }

    @Test
    public void testCompoundGroupMoveAndCommandUndoRedo() {
        FlowGraph graph = new FlowGraph();

        CompoundRecipeBuilder.LayerSpec layer1 = new CompoundRecipeBuilder.LayerSpec("Layer I", List.of(), List.of());
        CompoundRecipeBuilder.LayerSpec layer2 = new CompoundRecipeBuilder.LayerSpec("Layer II", List.of(), List.of());

        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                "Large Rotor Machine",
                ResourceLocation.tryParse("gtceu:large_rotor_machine"),
                2400.0,
                245760.0,
                GTVoltageTier.UV,
                List.of(layer1, layer2),
                100,
                100
        );

        for (RecipeNode n : cluster.nodes()) {
            graph.addNode(n);
        }
        graph.addFrame(cluster.frame());

        RecipeNode n1 = cluster.nodes().get(0);
        RecipeNode n2 = cluster.nodes().get(1);
        CanvasGroupFrame frame = cluster.frame();

        double origN1X = n1.getPosX();
        double origN1Y = n1.getPosY();
        double origN2X = n2.getPosX();
        double origN2Y = n2.getPosY();
        double origFrameX = frame.getPosX();
        double origFrameY = frame.getPosY();

        // Simulate bonded move of compound group
        double dx = 150.0;
        double dy = 75.0;

        com.gtceu.calcboard.api.history.BoardCommand.MoveComponentsCommand moveCmd =
                new com.gtceu.calcboard.api.history.BoardCommand.MoveComponentsCommand(
                        java.util.Map.of(n1.getId(), new double[]{dx, dy}, n2.getId(), new double[]{dx, dy}),
                        java.util.Map.of(),
                        java.util.Map.of(frame.getId(), new double[]{dx, dy})
                );

        n1.setPos(n1.getPosX() + dx, n1.getPosY() + dy);
        n2.setPos(n2.getPosX() + dx, n2.getPosY() + dy);
        frame.moveBy(dx, dy);

        Assertions.assertEquals(origN1X + dx, n1.getPosX(), 0.001);
        Assertions.assertEquals(origN1Y + dy, n1.getPosY(), 0.001);
        Assertions.assertEquals(origN2X + dx, n2.getPosX(), 0.001);
        Assertions.assertEquals(origN2Y + dy, n2.getPosY(), 0.001);
        Assertions.assertEquals(origFrameX + dx, frame.getPosX(), 0.001);
        Assertions.assertEquals(origFrameY + dy, frame.getPosY(), 0.001);

        // Undo move -> All nodes and frame must return in perfect sync
        moveCmd.undo(graph);
        Assertions.assertEquals(origN1X, n1.getPosX(), 0.001);
        Assertions.assertEquals(origN1Y, n1.getPosY(), 0.001);
        Assertions.assertEquals(origN2X, n2.getPosX(), 0.001);
        Assertions.assertEquals(origN2Y, n2.getPosY(), 0.001);
        Assertions.assertEquals(origFrameX, frame.getPosX(), 0.001);
        Assertions.assertEquals(origFrameY, frame.getPosY(), 0.001);

        // Redo move
        moveCmd.redo(graph);
        Assertions.assertEquals(origN1X + dx, n1.getPosX(), 0.001);
        Assertions.assertEquals(origN1Y + dy, n1.getPosY(), 0.001);
        Assertions.assertEquals(origN2X + dx, n2.getPosX(), 0.001);
        Assertions.assertEquals(origN2Y + dy, n2.getPosY(), 0.001);
        Assertions.assertEquals(origFrameX + dx, frame.getPosX(), 0.001);
        Assertions.assertEquals(origFrameY + dy, frame.getPosY(), 0.001);
    }
}
