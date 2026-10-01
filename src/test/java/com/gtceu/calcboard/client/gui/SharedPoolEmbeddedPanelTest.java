package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.history.command.AddRecipeToSharedFrameCommand;
import com.gtceu.calcboard.api.history.command.RemoveRecipeFromSharedFrameCommand;
import com.gtceu.calcboard.api.history.command.SetFrameViewModeCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.PoolViewMode;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.canvas.CanvasWireRenderer;
import com.gtceu.calcboard.client.gui.render.EmbeddedPanelRenderer;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.interaction.CanvasBundleWiringHandler;
import com.gtceu.calcboard.client.gui.model.PortRef;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;

@ExtendWith(MinecraftBootstrapExtension.class)
public class SharedPoolEmbeddedPanelTest {

    @Test
    public void testDefaultViewModeAndStateTransitions() {
        CanvasGroupFrame frame = new CanvasGroupFrame("sp_test_1", "Centrifuge Pool", CanvasGroupFrame.COLOR_CYAN, 100, 100, 360, 240);
        frame.setSharedMachineFrame(true);

        Assertions.assertEquals(PoolViewMode.EMBEDDED_PANEL, frame.getViewMode());
        Assertions.assertFalse(frame.isFolded());

        FlowGraph graph = new FlowGraph();
        graph.addFrame(frame);

        frame.setViewMode(PoolViewMode.FOLDED_CARD, graph);
        Assertions.assertEquals(PoolViewMode.FOLDED_CARD, frame.getViewMode());
        Assertions.assertTrue(frame.isFolded());

        frame.setViewMode(PoolViewMode.EMBEDDED_PANEL, graph);
        Assertions.assertEquals(PoolViewMode.EMBEDDED_PANEL, frame.getViewMode());
        Assertions.assertFalse(frame.isFolded());

        frame.setViewMode(PoolViewMode.EXPANDED_FRAME, graph);
        Assertions.assertEquals(PoolViewMode.EXPANDED_FRAME, frame.getViewMode());
        Assertions.assertFalse(frame.isFolded());
    }

    @Test
    public void testSetFrameViewModeCommandUndoRedo() {
        FlowGraph graph = new FlowGraph();
        CanvasGroupFrame frame = new CanvasGroupFrame("sp_cmd_1", "Thermal Pool", CanvasGroupFrame.COLOR_AMBER, 50, 50, 340, 220);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        Assertions.assertEquals(PoolViewMode.EMBEDDED_PANEL, frame.getViewMode());

        var cmd = new SetFrameViewModeCommand(frame.getId(), PoolViewMode.EMBEDDED_PANEL, PoolViewMode.FOLDED_CARD);
        cmd.redo(graph);
        Assertions.assertEquals(PoolViewMode.FOLDED_CARD, frame.getViewMode());
        Assertions.assertTrue(frame.isFolded());

        cmd.undo(graph);
        Assertions.assertEquals(PoolViewMode.EMBEDDED_PANEL, frame.getViewMode());
        Assertions.assertFalse(frame.isFolded());
    }

    @Test
    public void testNbtSerializationWithViewModeAndCompatibility() {
        CanvasGroupFrame frame = new CanvasGroupFrame("sp_nbt_1", "Mixer Pool", CanvasGroupFrame.COLOR_PURPLE, 200, 150, 360, 240);
        frame.setSharedMachineFrame(true);
        frame.setSharedMachineId(ResourceLocation.tryParse("gtceu:mixer"));
        frame.setSharedTier(GTVoltageTier.HV);
        frame.setViewMode(PoolViewMode.EMBEDDED_PANEL);

        CompoundTag tag = frame.serializeNBT();
        Assertions.assertEquals("EMBEDDED_PANEL", tag.getString("poolViewMode"));
        Assertions.assertEquals("gtceu:mixer", tag.getString("sharedMachineId"));
        Assertions.assertEquals("HV", tag.getString("sharedTier"));

        CanvasGroupFrame restored = CanvasGroupFrame.deserializeNBT(tag);
        Assertions.assertTrue(restored.isSharedMachineFrame());
        Assertions.assertEquals(PoolViewMode.EMBEDDED_PANEL, restored.getViewMode());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:mixer"), restored.getSharedMachineId());
        Assertions.assertEquals(GTVoltageTier.HV, restored.getSharedTier());

        // Backward compatibility fallback test
        tag.remove("poolViewMode");
        tag.putBoolean("isFolded", false);
        CanvasGroupFrame legacyRestored = CanvasGroupFrame.deserializeNBT(tag);
        Assertions.assertEquals(PoolViewMode.EXPANDED_FRAME, legacyRestored.getViewMode());

        tag.putBoolean("isFolded", true);
        CanvasGroupFrame legacyFolded = CanvasGroupFrame.deserializeNBT(tag);
        Assertions.assertEquals(PoolViewMode.FOLDED_CARD, legacyFolded.getViewMode());
    }

    @Test
    public void testInlineAddRecipeDutyProvisioning() {
        FlowGraph graph = new FlowGraph();
        CanvasGroupFrame frame = new CanvasGroupFrame("sp_duty_1", "Chemical Reactor Pool", CanvasGroupFrame.COLOR_EMERALD, 100, 100, 360, 200);
        frame.setSharedMachineFrame(true);
        frame.setTargetPoolCapacity(2.0);
        graph.addFrame(frame);

        RecipeNode r1 = RecipeNode.create("Recipe 1", 100, 20, GTVoltageTier.MV);
        graph.addNode(r1);
        frame.addRecipeInline(r1, graph);
        // Initially empty pool with capacity 2.0: remaining 2.0, max 1.0 assigned
        Assertions.assertEquals(1.0, r1.getMachineCount(), 0.001);

        r1.setMachineCount(0.5);

        RecipeNode r2 = RecipeNode.create("Recipe 2", 100, 20, GTVoltageTier.MV);
        graph.addNode(r2);
        frame.addRecipeInline(r2, graph);
        // Remaining capacity: 2.0 - 0.5 = 1.5, max 1.0
        Assertions.assertEquals(1.0, r2.getMachineCount(), 0.001);

        RecipeNode r3 = RecipeNode.create("Recipe 3", 100, 20, GTVoltageTier.MV);
        graph.addNode(r3);
        frame.addRecipeInline(r3, graph);
        // Current duty: 0.5 + 1.0 = 1.5. Remaining: 0.5
        Assertions.assertEquals(0.5, r3.getMachineCount(), 0.001);

        RecipeNode r4 = RecipeNode.create("Recipe 4", 100, 20, GTVoltageTier.MV);
        graph.addNode(r4);
        frame.addRecipeInline(r4, graph);
        // Fully saturated (2.0 >= 2.0), should provision minimum 0.1
        Assertions.assertEquals(0.1, r4.getMachineCount(), 0.001);
    }

    @Test
    public void testEmbeddedLayoutAndPortHitDetection() {
        FlowGraph graph = new FlowGraph();
        CanvasGroupFrame frame = new CanvasGroupFrame("sp_layout_1", "Wiremill Pool", CanvasGroupFrame.COLOR_BLUE, 100, 100, 360, 200);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        RecipeNode sub1 = RecipeNode.create("Sub 1", 100, 20, GTVoltageTier.LV);
        sub1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 1.0));
        sub1.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:copper_single_wire"), "Copper Wire", 2.0));
        graph.addNode(sub1);
        frame.addRecipeInline(sub1, graph);

        frame.relayoutEmbeddedCards(graph);

        double[] inputAnchor = EmbeddedPanelRenderer.getEmbeddedPortAnchor(frame, sub1, 0, true);
        double[] outputAnchor = EmbeddedPanelRenderer.getEmbeddedPortAnchor(frame, sub1, 0, false);

        Assertions.assertTrue(inputAnchor[0] < outputAnchor[0]);
        Assertions.assertEquals(inputAnchor[1], outputAnchor[1], 0.001);

        var hitInput = EmbeddedPanelRenderer.findHoveredEmbeddedPort(graph, inputAnchor[0], inputAnchor[1]);
        Assertions.assertNotNull(hitInput);
        Assertions.assertTrue(hitInput.isInput());
        Assertions.assertEquals(0, hitInput.portIndex());
        Assertions.assertEquals(sub1.getId(), hitInput.subNode().getId());

        var hitOutput = EmbeddedPanelRenderer.findHoveredEmbeddedPort(graph, outputAnchor[0], outputAnchor[1]);
        Assertions.assertNotNull(hitOutput);
        Assertions.assertFalse(hitOutput.isInput());
        Assertions.assertEquals(0, hitOutput.portIndex());
        Assertions.assertEquals(sub1.getId(), hitOutput.subNode().getId());
    }

    @Test
    public void testWireEndpointsRoutingToEmbeddedPortAnchor() {
        FlowGraph graph = new FlowGraph();

        RecipeNode upstream = RecipeNode.create("Furnace", 100, 20, GTVoltageTier.LV);
        upstream.setPos(0, 0);
        upstream.setCardWidth(180);
        upstream.setCardHeight(160);
        upstream.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 1.0));
        graph.addNode(upstream);

        CanvasGroupFrame frame = new CanvasGroupFrame("sp_wire_1", "Wiremill Pool", CanvasGroupFrame.COLOR_BLUE, 300, 100, 360, 200);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        RecipeNode sub = RecipeNode.create("Sub Wire", 100, 20, GTVoltageTier.LV);
        sub.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 1.0));
        graph.addNode(sub);
        frame.addRecipeInline(sub, graph);

        frame.relayoutEmbeddedCards(graph);

        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(upstream.getId(), 0, sub.getId(), 0);
        graph.addConnection(upstream.getId(), 0, sub.getId(), 0);

        Assertions.assertTrue(graph.isNodeInEmbeddedPanel(sub.getId()));

        double[] subAnchor = EmbeddedPanelRenderer.getEmbeddedPortAnchor(frame, sub, 0, true);
        CanvasWireRenderer.ResolvedWireEndpoints endpoints = CanvasWireRenderer.resolveWireEndpointsForWidgets(graph, n -> null, edge);

        Assertions.assertNotNull(endpoints);
        Assertions.assertEquals((float) subAnchor[0], endpoints.x2(), 0.001f);
        Assertions.assertEquals((float) subAnchor[1], endpoints.y2(), 0.001f);
    }

    @Test
    public void testResolvePortAnchorForEmbeddedPanelAndFoldedFrame() {
        FlowGraph graph = new FlowGraph();

        CanvasGroupFrame frame = new CanvasGroupFrame("sp_anchor_test", "Wiremill Pool", CanvasGroupFrame.COLOR_BLUE, 300, 100, 360, 200);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        RecipeNode sub = RecipeNode.create("Sub Wire", 100, 20, GTVoltageTier.LV);
        sub.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 1.0));
        sub.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_wire"), "Copper Wire", 2.0));
        graph.addNode(sub);
        frame.addRecipeInline(sub, graph);
        frame.relayoutEmbeddedCards(graph);

        double[] expectedInAnchor = EmbeddedPanelRenderer.getEmbeddedPortAnchor(frame, sub, 0, true);
        double[] expectedOutAnchor = EmbeddedPanelRenderer.getEmbeddedPortAnchor(frame, sub, 0, false);

        CanvasWireRenderer.PortAnchor inAnchor = CanvasWireRenderer.resolvePortAnchor(graph, n -> null, sub, 0, true);
        CanvasWireRenderer.PortAnchor outAnchor = CanvasWireRenderer.resolvePortAnchor(graph, n -> null, sub, 0, false);

        Assertions.assertEquals((float) expectedInAnchor[0], inAnchor.x(), 0.001f);
        Assertions.assertEquals((float) expectedInAnchor[1], inAnchor.y(), 0.001f);
        Assertions.assertEquals(-1.0f, inAnchor.dirX(), 0.001f);

        Assertions.assertEquals((float) expectedOutAnchor[0], outAnchor.x(), 0.001f);
        Assertions.assertEquals((float) expectedOutAnchor[1], outAnchor.y(), 0.001f);
        Assertions.assertEquals(1.0f, outAnchor.dirX(), 0.001f);

        frame.setViewMode(PoolViewMode.FOLDED_CARD, graph);
        CanvasWireRenderer.PortAnchor foldedOutAnchor = CanvasWireRenderer.resolvePortAnchor(graph, n -> null, sub, 0, false);
        Assertions.assertEquals((float) (frame.getPosX() + frame.getWidth() - 5.0), foldedOutAnchor.x(), 0.001f);
        Assertions.assertEquals(1.0f, foldedOutAnchor.dirX(), 0.001f);
    }

    @Test
    public void testExpandedToEmbeddedAndFoldedStateMachineTransitions() {
        CanvasGroupFrame frame = new CanvasGroupFrame("sp_sm_trans", "Centrifuge Pool", CanvasGroupFrame.COLOR_CYAN, 100, 100, 360, 240);
        frame.setSharedMachineFrame(true);
        FlowGraph graph = new FlowGraph();
        graph.addFrame(frame);

        frame.setViewMode(PoolViewMode.EXPANDED_FRAME, graph);
        Assertions.assertEquals(PoolViewMode.EXPANDED_FRAME, frame.getViewMode());

        PoolViewMode target1 = (frame.getViewMode() == PoolViewMode.EMBEDDED_PANEL) ? PoolViewMode.FOLDED_CARD : PoolViewMode.EMBEDDED_PANEL;
        frame.setViewMode(target1, graph);
        Assertions.assertEquals(PoolViewMode.EMBEDDED_PANEL, frame.getViewMode());

        PoolViewMode target2 = (frame.getViewMode() == PoolViewMode.EMBEDDED_PANEL) ? PoolViewMode.FOLDED_CARD : PoolViewMode.EMBEDDED_PANEL;
        frame.setViewMode(target2, graph);
        Assertions.assertEquals(PoolViewMode.FOLDED_CARD, frame.getViewMode());

        PoolViewMode target3 = (frame.getViewMode() == PoolViewMode.EMBEDDED_PANEL) ? PoolViewMode.FOLDED_CARD : PoolViewMode.EMBEDDED_PANEL;
        frame.setViewMode(target3, graph);
        Assertions.assertEquals(PoolViewMode.EMBEDDED_PANEL, frame.getViewMode());
    }

    @Test
    public void testAddAndRemoveRecipeToSharedFrameCommandUndoRedo() {
        FlowGraph graph = new FlowGraph();
        CanvasGroupFrame frame = new CanvasGroupFrame("sp_cmd_add_rem", "Electrolyzer Pool", CanvasGroupFrame.COLOR_AMBER, 100, 100, 360, 220);
        frame.setSharedMachineFrame(true);
        frame.setTargetPoolCapacity(2.0);
        graph.addFrame(frame);

        RecipeNode subNode = RecipeNode.create("Electrolyze Water", 100, 20, GTVoltageTier.LV);
        subNode.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));
        subNode.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrogen"), "Hydrogen", 2000.0));
        subNode.setMachineCount(0.35);

        AddRecipeToSharedFrameCommand addCmd = new AddRecipeToSharedFrameCommand(frame.getId(), subNode, 0.35, "Add Electrolyze Water");
        addCmd.redo(graph);

        Assertions.assertTrue(frame.containsNode(subNode.getId()));
        Assertions.assertEquals(0.35, subNode.getMachineCount(), 0.001);

        RecipeNode upstream = RecipeNode.create("Pump", 50, 10, GTVoltageTier.LV);
        upstream.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));
        graph.addNode(upstream);

        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(upstream.getId(), 0, subNode.getId(), 0);
        graph.addConnection(edge);
        Assertions.assertEquals(1, graph.getConnections().size());

        RemoveRecipeFromSharedFrameCommand remCmd = new RemoveRecipeFromSharedFrameCommand(
                frame.getId(), subNode, List.of(edge), "Remove Electrolyze Water"
        );
        remCmd.redo(graph);

        Assertions.assertFalse(frame.containsNode(subNode.getId()));
        Assertions.assertNull(graph.findNodeById(subNode.getId()));
        Assertions.assertEquals(0, graph.getConnections().size());

        remCmd.undo(graph);
        Assertions.assertTrue(frame.containsNode(subNode.getId()));
        Assertions.assertNotNull(graph.findNodeById(subNode.getId()));
        Assertions.assertEquals(0.35, subNode.getMachineCount(), 0.001);
        Assertions.assertEquals(1, graph.getConnections().size());

        addCmd.undo(graph);
        Assertions.assertFalse(frame.containsNode(subNode.getId()));
        Assertions.assertNull(graph.findNodeById(subNode.getId()));
    }

    @Test
    public void testEmbeddedNodeIsolationFromSelectionModel() {
        FlowGraph graph = new FlowGraph();
        RecipeNode standalone = RecipeNode.create("Standalone Furnace", 100, 20, GTVoltageTier.LV);
        graph.addNode(standalone);

        CanvasGroupFrame frame = new CanvasGroupFrame("sp_iso_frame", "Chemical Pool", CanvasGroupFrame.COLOR_PURPLE, 200, 200, 360, 220);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        RecipeNode enclosed = RecipeNode.create("Enclosed Reactor", 100, 20, GTVoltageTier.MV);
        graph.addNode(enclosed);
        frame.addRecipeInline(enclosed, graph);

        Assertions.assertTrue(graph.isNodeInEmbeddedPanel(enclosed.getId()));
        Assertions.assertTrue(graph.isNodeInFoldedOrEmbeddedFrame(enclosed.getId()));
        Assertions.assertFalse(graph.isNodeInFoldedOrEmbeddedFrame(standalone.getId()));

        BoardSelectionModel selectionModel = new BoardSelectionModel();
        IBoardScreenContext mockContext = (IBoardScreenContext) Proxy.newProxyInstance(
                IBoardScreenContext.class.getClassLoader(),
                new Class<?>[]{IBoardScreenContext.class},
                (proxy, method, args) -> "getGraph".equals(method.getName()) ? graph : null
        );

        selectionModel.selectAll(mockContext);
        Assertions.assertTrue(selectionModel.isNodeSelected(standalone.getId()));
        Assertions.assertFalse(selectionModel.isNodeSelected(enclosed.getId()));
        Assertions.assertTrue(selectionModel.isFrameSelected(frame.getId()));
    }

    @Test
    public void testEmbeddedPanelCapacityScaling() {
        FlowGraph graph = new FlowGraph();
        CanvasGroupFrame frame = new CanvasGroupFrame("sp_scale_test", "Mixer Pool", CanvasGroupFrame.COLOR_EMERALD, 50, 50, 360, 200);
        frame.setSharedMachineFrame(true);
        frame.setTargetPoolCapacity(2.0);
        graph.addFrame(frame);

        RecipeNode r1 = RecipeNode.create("Recipe 1", 100, 20, GTVoltageTier.MV);
        RecipeNode r2 = RecipeNode.create("Recipe 2", 100, 20, GTVoltageTier.MV);
        graph.addNode(r1);
        graph.addNode(r2);
        frame.addRecipeInline(r1, graph);
        frame.addRecipeInline(r2, graph);

        r1.setMachineCount(0.5);
        r2.setMachineCount(0.5);
        Assertions.assertEquals(1.0, frame.computeTotalMachineDuty(graph), 0.001);

        double oldCap = frame.getTargetPoolCapacity();
        double newCap = 4.0;
        frame.setTargetPoolCapacity(newCap);
        double factor = newCap / oldCap;
        frame.scaleEnclosedNodes(graph, factor);

        Assertions.assertEquals(1.0, r1.getMachineCount(), 0.001);
        Assertions.assertEquals(1.0, r2.getMachineCount(), 0.001);
        Assertions.assertEquals(2.0, frame.computeTotalMachineDuty(graph), 0.001);
        Assertions.assertEquals(4.0, frame.getTargetPoolCapacity(), 0.001);
    }

    @Test
    public void testSharedFrameRecipeCategorySynchronizationAndNbt() {
        FlowGraph graph = new FlowGraph();
        CanvasGroupFrame frame = new CanvasGroupFrame("sp_sync_cat", "Cutter Pool", CanvasGroupFrame.COLOR_CYAN, 100, 100, 360, 200);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        Assertions.assertNull(frame.getSharedRecipeCategoryId());

        RecipeNode cutterNode = RecipeNode.create("Bolt Recipe", 100, 20, GTVoltageTier.MV);
        cutterNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:cutter"));
        cutterNode.setMachineIcon(ResourceLocation.tryParse("gtceu:mv_cutter"));
        graph.addNode(cutterNode);

        frame.addRecipeInline(cutterNode, graph);

        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:cutter"), frame.getSharedRecipeCategoryId());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:mv_cutter"), frame.getSharedMachineId());
        Assertions.assertEquals(GTVoltageTier.MV, frame.getSharedTier());

        CompoundTag tag = frame.serializeNBT();
        Assertions.assertEquals("gtceu:cutter", tag.getString("sharedRecipeCategoryId"));
        Assertions.assertEquals("gtceu:mv_cutter", tag.getString("sharedMachineId"));

        CanvasGroupFrame restored = CanvasGroupFrame.deserializeNBT(tag);
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:cutter"), restored.getSharedRecipeCategoryId());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:mv_cutter"), restored.getSharedMachineId());

        RecipeNode newMaster = RecipeNode.create("Laser Cutter Recipe", 200, 40, GTVoltageTier.HV);
        newMaster.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:laser_cutter"));
        newMaster.setMachineIcon(ResourceLocation.tryParse("gtceu:hv_laser_cutter"));
        graph.addNode(newMaster);
        frame.syncHardwareConfig(newMaster, graph);

        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:laser_cutter"), frame.getSharedRecipeCategoryId());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:hv_laser_cutter"), frame.getSharedMachineId());
        Assertions.assertEquals(GTVoltageTier.HV, frame.getSharedTier());
    }

    @Test
    public void testSharedFrameCategoryResolutionForRecipeSearch() {
        FlowGraph graph = new FlowGraph();
        CanvasGroupFrame frame = new CanvasGroupFrame("sp_search_prefill", "Advanced Cutter Pool", CanvasGroupFrame.COLOR_CYAN, 100, 100, 360, 200);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        RecipeNode subNode = RecipeNode.create("Cutter Sub", 100, 20, GTVoltageTier.MV);
        subNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:cutter"));
        subNode.setMachineIcon(ResourceLocation.tryParse("gtceu:mv_cutter"));
        graph.addNode(subNode);
        frame.addRecipeInline(subNode, graph);

        ResourceLocation resolvedCat = frame.getSharedRecipeCategoryId(graph);
        ResourceLocation resolvedIcon = frame.getSharedMachineIcon(graph);

        Assertions.assertNotNull(resolvedCat);
        Assertions.assertEquals("cutter", resolvedCat.getPath());
        Assertions.assertEquals("mv_cutter", resolvedIcon.getPath());

        String categoryPrefill = "[" + resolvedCat.getPath() + "] ";
        Assertions.assertEquals("[cutter] ", categoryPrefill);

        var parsedCatQuery = com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.parseQuery(categoryPrefill.trim());
        var parsedIconQuery = com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.parseQuery("[" + resolvedIcon.getPath() + "]");

        com.gtceu.calcboard.api.model.SearchableRecipe sampleRecipe = new com.gtceu.calcboard.api.model.SearchableRecipe(
                "dummy",
                ResourceLocation.tryParse("gtceu:cut_bolt"),
                "Cut Bolt",
                "gtceu",
                "gtceu:cutter",
                "Cutter",
                "rod",
                "bolt",
                null,
                null,
                new String[]{"Iron Rod"},
                new String[]{"Iron Bolt"},
                true
        );

        Assertions.assertTrue(com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.matches(sampleRecipe, parsedCatQuery));
        Assertions.assertFalse(com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.matches(sampleRecipe, parsedIconQuery));
    }

    @Test
    public void testEmbeddedPanelResizeDirectionAndAction() {
        CanvasGroupFrame frame = new CanvasGroupFrame("sp_resize_test", "Cutter Pool", CanvasGroupFrame.COLOR_CYAN, 100, 100, 360, 200);
        frame.setSharedMachineFrame(true);

        Assertions.assertEquals(PoolViewMode.EMBEDDED_PANEL, frame.getViewMode());

        // Test right edge resize hover
        var eastDir = com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer.getResizeDirection(frame, 460.0, 150.0);
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer.ResizeDirection.EAST, eastDir);

        // Test south-east corner resize hover
        var seDir = com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer.getResizeDirection(frame, 460.0, 300.0);
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer.ResizeDirection.SOUTH_EAST, seDir);

        // Test getClickedAction returns RESIZE
        var action = com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer.getClickedAction(frame, 460.0, 150.0);
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer.FrameAction.RESIZE, action);

        // Folded card must NOT allow resize
        frame.setViewMode(PoolViewMode.FOLDED_CARD);
        var foldedDir = com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer.getResizeDirection(frame, 460.0, 150.0);
        Assertions.assertEquals(com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer.ResizeDirection.NONE, foldedDir);
    }

    @Test
    public void testEmbeddedPanelHeightPreservationAndExpansion() {
        FlowGraph graph = new FlowGraph();
        CanvasGroupFrame frame = new CanvasGroupFrame("sp_height_test", "LCR Pool", CanvasGroupFrame.COLOR_EMERALD, 100, 100, 360, 80);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        RecipeNode r1 = RecipeNode.create("Recipe 1", 100, 20, GTVoltageTier.HV);
        r1.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));
        r1.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrogen"), "Hydrogen", 2000.0));
        graph.addNode(r1);
        frame.addRecipeInline(r1, graph);

        frame.relayoutEmbeddedCards(graph);
        double minH = frame.computeMinEmbeddedHeight(graph);
        Assertions.assertEquals(minH, frame.getHeight(), 0.001);

        // User resizes frame height larger
        frame.setHeight(minH + 100.0);
        frame.relayoutEmbeddedCards(graph);
        Assertions.assertEquals(minH + 100.0, frame.getHeight(), 0.001);

        // autoFit resets height back to minRequired
        boolean fitted = frame.autoFit(graph, CanvasGroupFrame.DEFAULT_PADDING);
        Assertions.assertTrue(fitted);
        Assertions.assertEquals(minH, frame.getHeight(), 0.001);
    }

    @Test
    public void testContextualWireLinkingToSharedFrameSubNode() {
        com.gtceu.calcboard.api.storage.BoardManager.getInstance().resetToDefault();
        FlowGraph graph = com.gtceu.calcboard.api.storage.BoardManager.getInstance().getActiveGraph();

        RecipeNode srcNode = RecipeNode.create("Distillation Tower", 100, 20, GTVoltageTier.HV);
        srcNode.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_oil"), "Heavy Oil", 480.0));
        graph.addNode(srcNode);

        CanvasGroupFrame frame = new CanvasGroupFrame("sp_wire_drop", "LCR Pool", CanvasGroupFrame.COLOR_EMERALD, 300, 100, 360, 200);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        RecipeNode lcrSub = RecipeNode.create("Heavy Oil Cracking", 100, 20, GTVoltageTier.HV);
        lcrSub.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_oil"), "Heavy Oil", 120.0));
        lcrSub.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:cracked_heavy_oil"), "Cracked Heavy Oil", 100.0));
        graph.addNode(lcrSub);
        frame.addRecipeInline(lcrSub, graph);

        // Contextual wire target from srcNode output port 0
        com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog.ContextualWireTarget target =
                new com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog.ContextualWireTarget(
                        srcNode, 0, false, srcNode.getOutputs().get(0), 300, 150, false
                );

        BoardScreen screen = new BoardScreen();
        com.gtceu.calcboard.client.gui.dialog.RecipeSearchNodeSpawner.linkContextualWire(screen, target, lcrSub);

        boolean connected = graph.getConnections().stream().anyMatch(e ->
                e.fromNodeId().equals(srcNode.getId()) && e.outputIndex() == 0 &&
                e.toNodeId().equals(lcrSub.getId()) && e.inputIndex() == 0
        );
        Assertions.assertTrue(connected);
    }

    @Test
    public void testSinglePortDropOnSharedFrameAutoConnectsToExistingSubNode() {
        BoardManager.getInstance().resetToDefault();
        FlowGraph graph = BoardManager.getInstance().getActiveGraph();

        RecipeNode srcNode = RecipeNode.create("Distillation Tower", 100, 20, GTVoltageTier.HV);
        srcNode.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_oil"), "Heavy Oil", 100.0));
        graph.addNode(srcNode);

        CanvasGroupFrame frame = new CanvasGroupFrame("sp_auto_conn", "LCR Pool", CanvasGroupFrame.COLOR_EMERALD, 300, 100, 360, 200);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        RecipeNode lcrSub = RecipeNode.create("Heavy Oil Cracking", 100, 20, GTVoltageTier.HV);
        lcrSub.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_oil"), "Heavy Oil", 120.0));
        lcrSub.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:cracked_heavy_oil"), "Cracked Heavy Oil", 100.0));
        graph.addNode(lcrSub);
        frame.addRecipeInline(lcrSub, graph);

        BoardScreen screen = new BoardScreen();

        PortRef portRef = new PortRef(srcNode.getId(), false, 0);
        boolean handled = CanvasBundleWiringHandler.handleBundleConnectToFrame(frame, Set.of(portRef), screen);
        Assertions.assertTrue(handled);

        boolean connected = graph.getConnections().stream().anyMatch(e ->
                e.fromNodeId().equals(srcNode.getId()) && e.outputIndex() == 0 &&
                e.toNodeId().equals(lcrSub.getId()) && e.inputIndex() == 0
        );
        Assertions.assertTrue(connected);
    }
}
