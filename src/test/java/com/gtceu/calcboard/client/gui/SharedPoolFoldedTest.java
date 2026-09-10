package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.canvas.CanvasWireRenderer;
import com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class SharedPoolFoldedTest {

    @Test
    public void testFoldToggleAndDimensionPreservation() {
        CanvasGroupFrame frame = new CanvasGroupFrame("sp1", "Centrifuges", CanvasGroupFrame.COLOR_BLUE, 100, 100, 450, 320);
        frame.setSharedMachineFrame(true);

        Assertions.assertFalse(frame.isFolded());
        Assertions.assertEquals(450.0, frame.getWidth(), 0.001);
        Assertions.assertEquals(320.0, frame.getHeight(), 0.001);

        frame.setFolded(true);
        Assertions.assertTrue(frame.isFolded());
        Assertions.assertEquals(450.0, frame.getSavedUnfoldedWidth(), 0.001);
        Assertions.assertEquals(320.0, frame.getSavedUnfoldedHeight(), 0.001);
        Assertions.assertEquals(280.0, frame.getWidth(), 0.001);
        Assertions.assertEquals(88.0, frame.getHeight(), 0.001);

        frame.setFolded(false);
        Assertions.assertFalse(frame.isFolded());
        Assertions.assertEquals(450.0, frame.getWidth(), 0.001);
        Assertions.assertEquals(320.0, frame.getHeight(), 0.001);
    }

    @Test
    public void testToggleFrameFoldCommand() {
        FlowGraph graph = new FlowGraph();
        CanvasGroupFrame frame = new CanvasGroupFrame("sp3", "Mixer Pool", CanvasGroupFrame.COLOR_CYAN, 50, 50, 400, 300);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        Assertions.assertFalse(frame.isFolded());
        var cmd = new com.gtceu.calcboard.api.history.command.ToggleFrameFoldCommand(frame.getId(), false, true);
        cmd.redo(graph);
        Assertions.assertTrue(frame.isFolded());
        Assertions.assertEquals(88.0, frame.getHeight(), 0.001);

        cmd.undo(graph);
        Assertions.assertFalse(frame.isFolded());
        Assertions.assertEquals(300.0, frame.getHeight(), 0.001);
    }

    @Test
    public void testNbtSerializationOfFoldedFrame() {
        CanvasGroupFrame frame = new CanvasGroupFrame("sp2", "Chemical Pool", CanvasGroupFrame.COLOR_PURPLE, 200, 150, 500, 350);
        frame.setSharedMachineFrame(true);
        frame.setTargetPoolCapacity(3.0);
        frame.addNode("node_chem_1");
        frame.addNode("node_chem_2");
        frame.setFolded(true);

        CompoundTag tag = frame.serializeNBT();
        CanvasGroupFrame restored = CanvasGroupFrame.deserializeNBT(tag);

        Assertions.assertTrue(restored.isSharedMachineFrame());
        Assertions.assertTrue(restored.isFolded());
        Assertions.assertEquals(3.0, restored.getTargetPoolCapacity(), 0.001);
        Assertions.assertEquals(500.0, restored.getSavedUnfoldedWidth(), 0.001);
        Assertions.assertEquals(350.0, restored.getSavedUnfoldedHeight(), 0.001);
        Assertions.assertEquals(2, restored.getContainedNodeIds().size());
        Assertions.assertTrue(restored.getContainedNodeIds().contains("node_chem_1"));
        Assertions.assertTrue(restored.getContainedNodeIds().contains("node_chem_2"));

        restored.setFolded(false);
        Assertions.assertEquals(500.0, restored.getWidth(), 0.001);
        Assertions.assertEquals(350.0, restored.getHeight(), 0.001);
    }

    @Test
    public void testEnclosedNodesProportionalScaling() {
        FlowGraph graph = new FlowGraph();
        RecipeNode n1 = RecipeNode.create("Recipe 1", 100, 20, GTVoltageTier.LV);
        n1.setMachineCount(0.25);
        RecipeNode n2 = RecipeNode.create("Recipe 2", 100, 20, GTVoltageTier.LV);
        n2.setMachineCount(0.50);
        RecipeNode n3 = RecipeNode.create("Recipe 3", 100, 20, GTVoltageTier.LV);
        n3.setMachineCount(0.50);

        graph.addNode(n1);
        graph.addNode(n2);
        graph.addNode(n3);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Shared Pool", List.of(n1, n2, n3), CanvasGroupFrame.COLOR_EMERALD);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        double initialTotal = frame.computeTotalMachineDuty(graph);
        Assertions.assertEquals(1.25, initialTotal, 0.001);

        frame.scaleEnclosedNodes(graph, 2.0);

        Assertions.assertEquals(0.50, n1.getMachineCount(), 0.001);
        Assertions.assertEquals(1.00, n2.getMachineCount(), 0.001);
        Assertions.assertEquals(1.00, n3.getMachineCount(), 0.001);

        double newTotal = frame.computeTotalMachineDuty(graph);
        Assertions.assertEquals(2.50, newTotal, 0.001);

        double ratio1to2 = n1.getMachineCount() / n2.getMachineCount();
        double ratio2to3 = n2.getMachineCount() / n3.getMachineCount();
        Assertions.assertEquals(0.5, ratio1to2, 0.001);
        Assertions.assertEquals(1.0, ratio2to3, 0.001);
    }

    @Test
    public void testBoundaryEdgeDetectionAndInternalCulling() {
        FlowGraph graph = new FlowGraph();

        RecipeNode extSupplier = RecipeNode.create("External Supplier", 50, 10, GTVoltageTier.LV);
        extSupplier.setPos(0, 0);
        RecipeNode in1 = RecipeNode.create("Inner 1", 100, 20, GTVoltageTier.LV);
        in1.setPos(200, 200);
        RecipeNode in2 = RecipeNode.create("Inner 2", 100, 20, GTVoltageTier.LV);
        in2.setPos(350, 200);

        IngredientStack ore = IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_ore"), "Iron Ore", 10);
        extSupplier.getOutputs().add(ore);
        in1.getInputs().add(ore);
        in1.getOutputs().add(ore);
        in2.getInputs().add(ore);

        graph.addNode(extSupplier);
        graph.addNode(in1);
        graph.addNode(in2);

        FlowGraph.ConnectionEdge edgeExt = new FlowGraph.ConnectionEdge(extSupplier.getId(), 0, in1.getId(), 0);
        FlowGraph.ConnectionEdge edgeInt = new FlowGraph.ConnectionEdge(in1.getId(), 0, in2.getId(), 0);
        graph.addConnection(edgeExt);
        graph.addConnection(edgeInt);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Shared Centrifuge", List.of(in1, in2), CanvasGroupFrame.COLOR_CYAN);
        frame.setSharedMachineFrame(true);
        frame.setFolded(true, graph);
        graph.addFrame(frame);

        List<FlowGraph.ConnectionEdge> boundaryEdges = FlowGraphTopologyAnalyzer.findExternalBoundaryEdges(graph, frame);
        Assertions.assertEquals(1, boundaryEdges.size());
        Assertions.assertTrue(boundaryEdges.contains(edgeExt));
        Assertions.assertFalse(boundaryEdges.contains(edgeInt));

        var endpointsInt = CanvasWireRenderer.resolveWireEndpoints(graph, null, edgeInt);
        Assertions.assertNotNull(endpointsInt);
        Assertions.assertTrue(endpointsInt.isInternalCull());

        var endpointsExt = CanvasWireRenderer.resolveWireEndpoints(graph, null, edgeExt);
        Assertions.assertNotNull(endpointsExt);
        Assertions.assertFalse(endpointsExt.isInternalCull());
        Assertions.assertEquals(frame.getPosX() + 5.0, endpointsExt.x2(), 0.001);
    }

    @Test
    public void testPortAggregationAndDeficitIndicator() {
        FlowGraph graph = new FlowGraph();

        RecipeNode n1 = RecipeNode.create("Centrifuge A", 20, 128, GTVoltageTier.MV);
        n1.setMachineCount(1.0);
        IngredientStack ore = IngredientStack.item(ResourceLocation.tryParse("gtceu:chromite_ore"), "Chromite Ore", 10);
        IngredientStack chromite = IngredientStack.item(ResourceLocation.tryParse("gtceu:chromite_dust"), "Chromite Dust", 10);
        n1.getInputs().add(ore);
        n1.getOutputs().add(chromite);
        n1.setEfficiency(0.5);

        RecipeNode n2 = RecipeNode.create("Centrifuge B", 20, 128, GTVoltageTier.MV);
        n2.setMachineCount(1.0);
        IngredientStack mag = IngredientStack.item(ResourceLocation.tryParse("gtceu:magnesium_dust"), "Magnesium Dust", 5);
        n2.getOutputs().add(mag);

        graph.addNode(n1);
        graph.addNode(n2);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Centrifuge Pool", List.of(n1, n2), CanvasGroupFrame.COLOR_BLUE);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        FlowGraphTopologyAnalyzer.FoldedPortSummary summary = FlowGraphTopologyAnalyzer.aggregateFoldedPorts(graph, frame);

        Assertions.assertEquals(1, summary.inputs().size());
        FlowGraphTopologyAnalyzer.AggregatedFoldedPort inPort = summary.inputs().get(0);
        Assertions.assertEquals("gtceu:chromite_ore", inPort.ingredient().getId().toString());
        Assertions.assertTrue(inPort.hasDeficit());
        Assertions.assertEquals(10.0, inPort.nominalRate(), 0.001);
        Assertions.assertEquals(5.0, inPort.actualRate(), 0.001);

        Assertions.assertEquals(2, summary.outputs().size());
    }

    @Test
    public void testFoldedSharedMachineIconResolution() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation furnaceId = ResourceLocation.tryParse("minecraft:furnace");
        RecipeNode node1 = RecipeNode.create(furnaceId, "Smelting Glass", 20.0, 10.0, GTVoltageTier.LV);
        RecipeNode node2 = RecipeNode.create(furnaceId, "Smelting Iron", 20.0, 10.0, GTVoltageTier.LV);
        graph.addNode(node1);
        graph.addNode(node2);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Smelting Pool", List.of(node1, node2), CanvasGroupFrame.COLOR_CYAN);
        frame.setSharedMachineFrame(true);
        frame.setFolded(true, graph);
        graph.addFrame(frame);

        ResourceLocation resolvedIcon = frame.getSharedMachineIcon(graph);
        Assertions.assertEquals(furnaceId, resolvedIcon);

        ItemStack iconStack = NodeCardRenderer.getOrCreateMachineIcon(resolvedIcon);
        Assertions.assertNotNull(iconStack);
        Assertions.assertFalse(iconStack.isEmpty());

        ItemStack emptyStack = NodeCardRenderer.getOrCreateMachineIcon(null);
        Assertions.assertNotNull(emptyStack);
        Assertions.assertTrue(emptyStack.isEmpty());
    }

    @Test
    public void testFoldedPortHitDetection() {
        FlowGraph graph = new FlowGraph();
        RecipeNode n1 = RecipeNode.create("Centrifuge A", 20, 128, GTVoltageTier.MV);
        n1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:chromite_ore"), "Chromite Ore", 10));
        n1.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:magnesium_dust"), "Magnesium Dust", 5));
        graph.addNode(n1);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Centrifuge Pool", List.of(n1), CanvasGroupFrame.COLOR_BLUE);
        frame.setPosX(100.0);
        frame.setPosY(100.0);
        frame.setSharedMachineFrame(true);
        frame.setFolded(true, graph);
        graph.addFrame(frame);

        CanvasGroupFrameRenderer.FoldedPortHit inHit = CanvasGroupFrameRenderer.findHoveredFoldedPort(graph, 110.0, 168.0);
        Assertions.assertNotNull(inHit);
        Assertions.assertTrue(inHit.isInput());
        Assertions.assertEquals(0, inHit.portIndex());
        Assertions.assertEquals("gtceu:chromite_ore", inHit.port().ingredient().getId().toString());

        CanvasGroupFrameRenderer.FoldedPortHit outHit = CanvasGroupFrameRenderer.findHoveredFoldedPort(graph, 100.0 + frame.getWidth() - 10.0, 168.0);
        Assertions.assertNotNull(outHit);
        Assertions.assertFalse(outHit.isInput());
        Assertions.assertEquals(0, outHit.portIndex());
        Assertions.assertEquals("gtceu:magnesium_dust", outHit.port().ingredient().getId().toString());

        CanvasGroupFrameRenderer.FoldedPortHit centerHit = CanvasGroupFrameRenderer.findHoveredFoldedPort(graph, 200.0, 168.0);
        Assertions.assertNull(centerHit);

        CanvasGroupFrameRenderer.FoldedPortHit outsideHit = CanvasGroupFrameRenderer.findHoveredFoldedPort(graph, 50.0, 50.0);
        Assertions.assertNull(outsideHit);

        frame.setFolded(false, graph);
        Assertions.assertNull(CanvasGroupFrameRenderer.findHoveredFoldedPort(graph, 110.0, 168.0));
    }

    @Test
    public void testFoldedFrameNodesExcludedFromSelection() {
        FlowGraph graph = new FlowGraph();
        RecipeNode n1 = RecipeNode.create("Node 1", 20, 10, GTVoltageTier.LV);
        RecipeNode n2 = RecipeNode.create("Node 2", 20, 10, GTVoltageTier.LV);
        RecipeNode ext = RecipeNode.create("External", 20, 10, GTVoltageTier.LV);
        ext.setPosX(1000.0);
        ext.setPosY(1000.0);
        graph.addNode(n1);
        graph.addNode(n2);
        graph.addNode(ext);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Pool", List.of(n1, n2), CanvasGroupFrame.COLOR_BLUE);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        Assertions.assertFalse(graph.isNodeInFoldedFrame(n1.getId()));
        Assertions.assertFalse(graph.isNodeInFoldedFrame(n2.getId()));
        Assertions.assertFalse(graph.isNodeInFoldedFrame(ext.getId()));

        frame.setFolded(true, graph);
        Assertions.assertTrue(graph.isNodeInFoldedFrame(n1.getId()));
        Assertions.assertTrue(graph.isNodeInFoldedFrame(n2.getId()));
        Assertions.assertFalse(graph.isNodeInFoldedFrame(ext.getId()));

        BoardSelectionModel selectionModel = new BoardSelectionModel();
        selectionModel.select(n1.getId(), true);
        selectionModel.select(ext.getId(), true);
        Assertions.assertTrue(selectionModel.isNodeSelected(n1.getId()));
        Assertions.assertTrue(selectionModel.isNodeSelected(ext.getId()));

        selectionModel.deselectNode(n1.getId());
        Assertions.assertFalse(selectionModel.isNodeSelected(n1.getId()));
        Assertions.assertTrue(selectionModel.isNodeSelected(ext.getId()));

        frame.setFolded(false, graph);
        Assertions.assertFalse(graph.isNodeInFoldedFrame(n1.getId()));
        Assertions.assertFalse(graph.isNodeInFoldedFrame(n2.getId()));
    }
}
