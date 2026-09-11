package com.gtceu.calcboard.client.gui.interaction.state;

import com.gtceu.calcboard.client.gui.interaction.CanvasContextMenuManager;
import com.gtceu.calcboard.client.gui.interaction.CanvasFrameInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasNoteInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasPanZoomHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasQuickAddMarkerHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasSelectionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasWireInteractionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CanvasStateMachineTest {

    private CanvasInteractionContext context;
    private CanvasStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        CanvasPanZoomHandler panZoomHandler = new CanvasPanZoomHandler();
        CanvasSelectionHandler selectionHandler = new CanvasSelectionHandler();
        CanvasQuickAddMarkerHandler quickAddMarkerHandler = new CanvasQuickAddMarkerHandler();
        CanvasFrameInteractionHandler frameHandler = new CanvasFrameInteractionHandler();
        CanvasNoteInteractionHandler noteHandler = new CanvasNoteInteractionHandler();
        CanvasWireInteractionHandler wireHandler = new CanvasWireInteractionHandler();
        CanvasContextMenuManager contextMenuManager = new CanvasContextMenuManager(null);

        context = new CanvasInteractionContext(
                null,
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
    }

    @Test
    void testInitialStateIsIdle() {
        assertNotNull(stateMachine.getCurrentState());
        assertEquals(CanvasIdleState.STATE_NAME, stateMachine.getCurrentState().getStateName());
        assertTrue(stateMachine.isInState(CanvasIdleState.class));
        assertFalse(stateMachine.isInState(CanvasWireConnectingState.class));
    }

    @Test
    void testTransitionToWireConnectingState() {
        CanvasWireConnectingState wireState = new CanvasWireConnectingState();
        stateMachine.transitionTo(wireState);

        assertEquals(CanvasWireConnectingState.STATE_NAME, stateMachine.getCurrentState().getStateName());
        assertTrue(stateMachine.isInState(CanvasWireConnectingState.class));
        assertFalse(stateMachine.isInState(CanvasIdleState.class));
    }

    @Test
    void testReturnToIdle() {
        stateMachine.transitionTo(new CanvasBoxSelectingState());
        assertTrue(stateMachine.isInState(CanvasBoxSelectingState.class));

        stateMachine.returnToIdle();
        assertTrue(stateMachine.isInState(CanvasIdleState.class));
    }

    @Test
    void testCancelCurrentInteractionCleansUpAndReturnsToIdle() {
        context.getDragStartPositions().put("test_node", new double[]{10.0, 20.0});
        stateMachine.transitionTo(new CanvasNodeDraggingState());
        assertTrue(stateMachine.isInState(CanvasNodeDraggingState.class));

        stateMachine.cancelCurrentInteraction();
        assertTrue(stateMachine.isInState(CanvasIdleState.class));
        assertTrue(context.getDragStartPositions().isEmpty());
    }

    @Test
    void testSameStateTransitionIsNoOp() {
        CanvasInteractionState current = stateMachine.getCurrentState();
        stateMachine.transitionTo(current);
        assertEquals(current, stateMachine.getCurrentState());
    }

    @Test
    void testBoxSelectingStateLifecycle() {
        CanvasBoxSelectingState boxState = new CanvasBoxSelectingState();
        stateMachine.transitionTo(boxState);
        assertTrue(stateMachine.isInState(CanvasBoxSelectingState.class));

        stateMachine.dispatchMouseDown(100, 100, 0);
        stateMachine.dispatchMouseDrag(150, 150, 0, 50, 50);
        stateMachine.dispatchMouseUp(150, 150, 0);

        assertTrue(stateMachine.isInState(CanvasIdleState.class));
    }

    @Test
    void testPanningStateLifecycle() {
        CanvasPanningState panState = new CanvasPanningState();
        stateMachine.transitionTo(panState);
        assertTrue(stateMachine.isInState(CanvasPanningState.class));

        stateMachine.dispatchMouseUp(100, 100, 1);
        assertTrue(stateMachine.isInState(CanvasIdleState.class));
    }
}
