package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasBoxSelectingState;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasNodeDraggingState;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasNodeResizingState;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasWireConnectingState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;

class InteractionScenarioTest {

    private BoardPage page;
    private CanvasTestHarness harness;

    @BeforeEach
    void setUp() {
        BoardManager.getInstance().resetToDefault();
        page = BoardPage.createDefault("Scenario Page");
        harness = new CanvasTestHarness(page);
    }

    @AfterEach
    void tearDown() {
        BoardManager.getInstance().resetToDefault();
    }

    @Test
    void testNodeDragAndCancelWithEscape() {
        RecipeNode node = createStandardNode("node-1", "Chemical Reactor", 100, 100);
        page.getGraph().addNode(node);
        harness.getContext().rebuildWidgets();

        harness.startDragNode("node-1");
        harness.assertState(CanvasNodeDraggingState.class);

        harness.mouseDrag(250, 300, 0, 120, 190);
        Assertions.assertEquals(220, node.getPosX(), 0.001);
        Assertions.assertEquals(290, node.getPosY(), 0.001);

        harness.pressKey(GLFW.GLFW_KEY_ESCAPE);
        harness.assertIdle();
        Assertions.assertEquals(100, node.getPosX(), 0.001);
        Assertions.assertEquals(100, node.getPosY(), 0.001);
        Assertions.assertTrue(harness.getContext().getDragStartPositions().isEmpty());
    }

    @Test
    void testNodeDragAndCommit() {
        RecipeNode node = createStandardNode("node-1", "Centrifuge", 100, 100);
        page.getGraph().addNode(node);
        harness.getContext().rebuildWidgets();

        harness.dragNode("node-1", 120, 80);
        harness.assertIdle();
        Assertions.assertEquals(220, node.getPosX(), 0.001);
        Assertions.assertEquals(180, node.getPosY(), 0.001);
        Assertions.assertTrue(harness.getContext().getDragStartPositions().isEmpty());
    }

    @Test
    void testWireConnectionAndCancelWithEscape() {
        RecipeNode nodeA = createStandardNode("nodeA", "Generator", 50, 50);
        page.getGraph().addNode(nodeA);
        harness.getContext().rebuildWidgets();

        harness.startWire("nodeA", 0, false);
        harness.assertState(CanvasWireConnectingState.class);
        Assertions.assertTrue(harness.getContext().getWireHandler().isDraggingWire());

        harness.pressKey(GLFW.GLFW_KEY_ESCAPE);
        harness.assertIdle();
        Assertions.assertFalse(harness.getContext().getWireHandler().isDraggingWire());
        Assertions.assertTrue(page.getGraph().getConnections().isEmpty());
    }

    @Test
    void testWireConnectionAndCancelWithRightClick() {
        RecipeNode nodeA = createStandardNode("nodeA", "Producer", 50, 50);
        page.getGraph().addNode(nodeA);
        harness.getContext().rebuildWidgets();

        harness.startWire("nodeA", 0, false);
        harness.assertState(CanvasWireConnectingState.class);

        harness.mouseDown(200, 200, 1);
        harness.assertIdle();
        Assertions.assertFalse(harness.getContext().getWireHandler().isDraggingWire());
    }

    @Test
    void testWireConnectionSuccess() {
        RecipeNode nodeA = createStandardNode("nodeA", "Source", 50, 50);
        RecipeNode nodeB = createStandardNode("nodeB", "Consumer", 300, 50);
        page.getGraph().addNode(nodeA);
        page.getGraph().addNode(nodeB);
        harness.getContext().rebuildWidgets();

        harness.connectWire("nodeA", 0, "nodeB", 0);
        harness.assertIdle();

        List<FlowGraph.ConnectionEdge> edges = page.getGraph().getConnections();
        Assertions.assertEquals(1, edges.size());
        FlowGraph.ConnectionEdge edge = edges.get(0);
        Assertions.assertEquals("nodeA", edge.fromNodeId());
        Assertions.assertEquals(0, edge.outputIndex());
        Assertions.assertEquals("nodeB", edge.toNodeId());
        Assertions.assertEquals(0, edge.inputIndex());
    }

    @Test
    void testBoxSelectionAndCancelWithEscape() {
        RecipeNode nodeA = createStandardNode("nodeA", "Node A", 60, 60);
        page.getGraph().addNode(nodeA);
        harness.getContext().rebuildWidgets();

        harness.mouseDown(10, 10, 0);
        harness.assertState(CanvasBoxSelectingState.class);
        harness.mouseDrag(200, 200, 0, 190, 190);
        Assertions.assertTrue(harness.getContext().getSelectionHandler().isBoxSelecting());

        harness.pressKey(GLFW.GLFW_KEY_ESCAPE);
        harness.assertIdle();
        Assertions.assertFalse(harness.getContext().getSelectionHandler().isBoxSelecting());
    }

    @Test
    void testBoxSelectionSuccess() {
        RecipeNode nodeA = createStandardNode("nodeA", "Node Inside", 50, 50);
        RecipeNode nodeB = createStandardNode("nodeB", "Node Outside", 600, 600);
        page.getGraph().addNode(nodeA);
        page.getGraph().addNode(nodeB);
        harness.getContext().rebuildWidgets();

        harness.boxSelect(20, 20, 350, 250);
        harness.assertIdle();
        Assertions.assertTrue(harness.getScreen().isNodeSelected("nodeA"));
        Assertions.assertFalse(harness.getScreen().isNodeSelected("nodeB"));
    }

    @Test
    void testReverseWireConnectionSuccess() {
        RecipeNode nodeA = createStandardNode("nodeA", "Producer", 50, 50);
        RecipeNode nodeB = createStandardNode("nodeB", "Consumer", 300, 50);
        page.getGraph().addNode(nodeA);
        page.getGraph().addNode(nodeB);
        harness.getContext().rebuildWidgets();

        harness.startWire("nodeB", 0, true);
        harness.assertState(CanvasWireConnectingState.class);

        double[] outPort = harness.getPortCenter("nodeA", false, 0);
        harness.mouseDrag(outPort[0], outPort[1], 0, 10, 10);
        harness.mouseUp(outPort[0], outPort[1], 0);
        harness.assertIdle();

        List<FlowGraph.ConnectionEdge> edges = page.getGraph().getConnections();
        Assertions.assertEquals(1, edges.size());
        FlowGraph.ConnectionEdge edge = edges.get(0);
        Assertions.assertEquals("nodeA", edge.fromNodeId());
        Assertions.assertEquals(0, edge.outputIndex());
        Assertions.assertEquals("nodeB", edge.toNodeId());
        Assertions.assertEquals(0, edge.inputIndex());
    }

    @Test
    void testMultiNodeDragAndCancelWithEscape() {
        RecipeNode nodeA = createStandardNode("nodeA", "First", 100, 100);
        RecipeNode nodeB = createStandardNode("nodeB", "Second", 300, 100);
        page.getGraph().addNode(nodeA);
        page.getGraph().addNode(nodeB);
        harness.getContext().rebuildWidgets();

        harness.getScreen().selectNode("nodeA", false);
        harness.getScreen().selectNode("nodeB", true);
        Assertions.assertTrue(harness.getScreen().isNodeSelected("nodeA"));
        Assertions.assertTrue(harness.getScreen().isNodeSelected("nodeB"));

        harness.startDragNode("nodeA");
        harness.assertState(CanvasNodeDraggingState.class);

        harness.mouseDrag(200, 250, 0, 70, 140);
        Assertions.assertEquals(170, nodeA.getPosX(), 0.001);
        Assertions.assertEquals(240, nodeA.getPosY(), 0.001);
        Assertions.assertEquals(370, nodeB.getPosX(), 0.001);
        Assertions.assertEquals(240, nodeB.getPosY(), 0.001);

        harness.pressKey(GLFW.GLFW_KEY_ESCAPE);
        harness.assertIdle();
        Assertions.assertEquals(100, nodeA.getPosX(), 0.001);
        Assertions.assertEquals(100, nodeA.getPosY(), 0.001);
        Assertions.assertEquals(300, nodeB.getPosX(), 0.001);
        Assertions.assertEquals(100, nodeB.getPosY(), 0.001);
        Assertions.assertTrue(harness.getContext().getDragStartPositions().isEmpty());
    }

    @Test
    void testNodeResizeAndCancelWithEscape() {
        RecipeNode nodeA = createStandardNode("nodeA", "Resizable", 100, 100);
        nodeA.setCardWidth(260);
        nodeA.setCardHeight(90);
        page.getGraph().addNode(nodeA);
        harness.getContext().rebuildWidgets();

        var widget = harness.getWidget("nodeA");
        Assertions.assertNotNull(widget);
        int origW = nodeA.getCardWidth();
        int origH = nodeA.getCardHeight();

        double rx = widget.getNode().getPosX() + widget.getWidth() - 4;
        double ry = widget.getNode().getPosY() + widget.getHeight() - 4;
        harness.mouseDown(rx, ry, 0);
        harness.assertState(CanvasNodeResizingState.class);

        harness.mouseDrag(rx + 60, ry + 40, 0, 60, 40);
        Assertions.assertNotEquals(origW, nodeA.getCardWidth());

        harness.pressKey(GLFW.GLFW_KEY_ESCAPE);
        harness.assertIdle();
        Assertions.assertEquals(origW, nodeA.getCardWidth());
        Assertions.assertEquals(origH, nodeA.getCardHeight());
        Assertions.assertNull(harness.getContext().getResizingNode());
    }

    @Test
    void testNodeResizeAndCommit() {
        RecipeNode nodeA = createStandardNode("nodeA", "Resizable", 100, 100);
        nodeA.setCardWidth(260);
        nodeA.setCardHeight(90);
        page.getGraph().addNode(nodeA);
        harness.getContext().rebuildWidgets();

        var widget = harness.getWidget("nodeA");
        Assertions.assertNotNull(widget);

        double rx = widget.getNode().getPosX() + widget.getWidth() - 4;
        double ry = widget.getNode().getPosY() + widget.getHeight() - 4;
        harness.mouseDown(rx, ry, 0);
        harness.assertState(CanvasNodeResizingState.class);

        harness.mouseDrag(rx + 60, ry + 40, 0, 60, 40);
        harness.mouseUp(rx + 60, ry + 40, 0);
        harness.assertIdle();
        Assertions.assertEquals(320, nodeA.getCardWidth());
        Assertions.assertEquals(146, nodeA.getCardHeight());
        Assertions.assertNull(harness.getContext().getResizingNode());
    }

    private RecipeNode createStandardNode(String id, String name, double x, double y) {
        RecipeNode node = new RecipeNode(id, name, 100.0, 30.0, GTVoltageTier.LV);
        node.setPosX(x);
        node.setPosY(y);
        node.getInputs().add(com.gtceu.calcboard.testutil.TestFixtures.item("minecraft:iron_ingot", "Iron Ingot", 1.0));
        node.getOutputs().add(com.gtceu.calcboard.testutil.TestFixtures.item("minecraft:iron_nugget", "Iron Nugget", 9.0));
        return node;
    }
}
