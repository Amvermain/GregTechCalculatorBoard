package com.gtceu.calcboard.client.gui.interaction.state;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.interaction.CanvasContextMenuManager;
import com.gtceu.calcboard.client.gui.interaction.CanvasFrameInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasNoteInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasPanZoomHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasQuickAddMarkerHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasSelectionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasWireInteractionHandler;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanvasIdleStateTest {

    private BoardScreen screen;
    private CanvasInteractionContext context;
    private CanvasStateMachine stateMachine;
    private CanvasIdleState idleState;

    @BeforeEach
    @AfterEach
    void cleanup() {
        BoardManager.getInstance().resetToDefault();
    }

    @BeforeEach
    void setUp() {
        screen = new BoardScreen();
        CanvasPanZoomHandler panZoomHandler = new CanvasPanZoomHandler();
        CanvasSelectionHandler selectionHandler = new CanvasSelectionHandler();
        CanvasQuickAddMarkerHandler quickAddMarkerHandler = new CanvasQuickAddMarkerHandler();
        CanvasFrameInteractionHandler frameHandler = new CanvasFrameInteractionHandler();
        CanvasNoteInteractionHandler noteHandler = new CanvasNoteInteractionHandler();
        CanvasWireInteractionHandler wireHandler = new CanvasWireInteractionHandler();
        CanvasContextMenuManager contextMenuManager = new CanvasContextMenuManager(null);

        context = new CanvasInteractionContext(
                screen,
                null,
                panZoomHandler,
                selectionHandler,
                quickAddMarkerHandler,
                frameHandler,
                noteHandler,
                wireHandler,
                contextMenuManager
        );
        stateMachine = new CanvasStateMachine(context);
        idleState = new CanvasIdleState();
    }

    @Test
    void testCommitCompoundNodeEditsWithoutConcurrentModificationException() {
        FlowGraph graph = screen.getGraph();
        RecipeNode compoundNode = RecipeNode.create("Compound Node", 100, 100, GTVoltageTier.LV);
        compoundNode.setPosX(500);
        compoundNode.setPosY(500);
        compoundNode.setCompoundMetadata("group_1", 0, 2, compoundNode.getId());
        RecipeNode siblingNode = RecipeNode.create("Sibling Node", 300, 100, GTVoltageTier.LV);
        siblingNode.setPosX(800);
        siblingNode.setPosY(500);
        siblingNode.setCompoundMetadata("group_1", 1, 2, compoundNode.getId());

        graph.addNode(compoundNode);
        graph.addNode(siblingNode);
        screen.rebuildWidgets();

        NodeWidget targetWidget = screen.getNodeWidgets().get(0);
        targetWidget.getCountEditor().startEditing();
        targetWidget.getCountEditor().getEditor().setText("3.5");
        Assertions.assertTrue(targetWidget.isAnyEditorActive());

        Assertions.assertDoesNotThrow(() -> {
            idleState.onMouseDown(context, 0, 0, 0);
        });

        Assertions.assertEquals(3.5, compoundNode.getMachineCount(), 0.0001);
    }

    @Test
    void testCommitModuleNodeEditsWithoutConcurrentModificationException() {
        FlowGraph graph = screen.getGraph();
        RecipeNode moduleNode = RecipeNode.create("Module Node", 100, 100, GTVoltageTier.LV);
        moduleNode.setPosX(500);
        moduleNode.setPosY(500);
        moduleNode.setModule(true);
        moduleNode.setSubPageId("subpage_test");
        RecipeNode secondNode = RecipeNode.create("Second Node", 300, 100, GTVoltageTier.LV);
        secondNode.setPosX(800);
        secondNode.setPosY(500);

        graph.addNode(moduleNode);
        graph.addNode(secondNode);
        screen.rebuildWidgets();

        NodeWidget targetWidget = screen.getNodeWidgets().get(0);
        targetWidget.getCountEditor().startEditing();
        targetWidget.getCountEditor().getEditor().setText("2.0");
        Assertions.assertTrue(targetWidget.isAnyEditorActive());

        Assertions.assertDoesNotThrow(() -> {
            idleState.onMouseDown(context, 0, 0, 0);
        });

        Assertions.assertEquals(2.0, moduleNode.getMachineCount(), 0.0001);
    }
}
