package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.client.gui.interaction.CanvasContextMenuManager;
import com.gtceu.calcboard.client.gui.interaction.CanvasFrameInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasNoteInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasPanZoomHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasQuickAddMarkerHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasSelectionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasWireInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasInteractionContext;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasPanningState;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasStateMachine;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasWireConnectingState;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.GuiGraphics;

/**
 * High-level canvas interaction coordinator delegating events to the CanvasStateMachine.
 */
public class CanvasInteractionHandler {

    private final BoardScreen screen;

    private final CanvasPanZoomHandler panZoomHandler = new CanvasPanZoomHandler();
    private final CanvasSelectionHandler selectionHandler = new CanvasSelectionHandler();
    private final CanvasQuickAddMarkerHandler quickAddMarkerHandler = new CanvasQuickAddMarkerHandler();
    private final CanvasFrameInteractionHandler frameHandler = new CanvasFrameInteractionHandler();
    private final CanvasNoteInteractionHandler noteHandler = new CanvasNoteInteractionHandler();
    private final CanvasWireInteractionHandler wireHandler = new CanvasWireInteractionHandler();
    private final CanvasContextMenuManager contextMenuManager;

    private final CanvasInteractionContext context;
    private final CanvasStateMachine stateMachine;

    public CanvasInteractionHandler(BoardScreen screen) {
        this.screen = screen;
        this.contextMenuManager = new CanvasContextMenuManager(screen);

        this.context = new CanvasInteractionContext(
                screen,
                this,
                panZoomHandler,
                selectionHandler,
                quickAddMarkerHandler,
                frameHandler,
                noteHandler,
                wireHandler,
                contextMenuManager
        );
        this.stateMachine = new CanvasStateMachine(context);
    }

    public CanvasStateMachine getStateMachine() {
        return stateMachine;
    }

    public CanvasInteractionContext getContext() {
        return context;
    }

    public boolean hasQuickAddMarker() {
        return quickAddMarkerHandler.hasQuickAddMarker();
    }

    public void checkMarkerCursorDistance(double canvasMouseX, double canvasMouseY) {
        quickAddMarkerHandler.checkMarkerCursorDistance(canvasMouseX, canvasMouseY);
    }

    public double getQuickAddMarkerCanvasX() {
        return quickAddMarkerHandler.getQuickAddMarkerCanvasX();
    }

    public double getQuickAddMarkerCanvasY() {
        return quickAddMarkerHandler.getQuickAddMarkerCanvasY();
    }

    public boolean hasQuickAddWireContext() {
        return quickAddMarkerHandler.hasQuickAddWireContext();
    }

    public void clearQuickAddMarker() {
        quickAddMarkerHandler.clearQuickAddMarker();
    }

    public CanvasPanZoomHandler getPanZoomHandler() {
        return panZoomHandler;
    }

    public CanvasSelectionHandler getSelectionHandler() {
        return selectionHandler;
    }

    public CanvasQuickAddMarkerHandler getQuickAddMarkerHandler() {
        return quickAddMarkerHandler;
    }

    public CanvasFrameInteractionHandler getFrameHandler() {
        return frameHandler;
    }

    public CanvasNoteInteractionHandler getNoteHandler() {
        return noteHandler;
    }

    public CanvasWireInteractionHandler getWireHandler() {
        return wireHandler;
    }

    public NodeWidget getWireStartNode() {
        return wireHandler.getWireStartNode();
    }

    public int getWireStartPortIdx() {
        return wireHandler.getWireStartPortIdx();
    }

    public boolean isWireStartInput() {
        return wireHandler.isWireStartInput();
    }

    public boolean isDraggingWire() {
        return stateMachine.isInState(CanvasWireConnectingState.class) || wireHandler.isDraggingWire();
    }

    public boolean isPanning() {
        return stateMachine.isInState(CanvasPanningState.class) || panZoomHandler.isPanning();
    }

    public CanvasContextMenuManager getContextMenuManager() {
        return contextMenuManager;
    }

    public void cancelWireDrag() {
        stateMachine.cancelCurrentInteraction();
        wireHandler.cancelWireDrag();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double canvasMouseX = screen != null ? screen.toCanvasX(mouseX) : mouseX;
        double canvasMouseY = screen != null ? screen.toCanvasY(mouseY) : mouseY;
        return stateMachine.dispatchMouseDown(canvasMouseX, canvasMouseY, button);
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double canvasMouseX = screen != null ? screen.toCanvasX(mouseX) : mouseX;
        double canvasMouseY = screen != null ? screen.toCanvasY(mouseY) : mouseY;

        if (screen != null) {
            for (NodeWidget w : screen.getNodeWidgets()) {
                if (w.isAnyEditorActive() && w.mouseDragged(canvasMouseX, canvasMouseY, button, dragX, dragY)) {
                    return true;
                }
            }
        }

        return stateMachine.dispatchMouseDrag(canvasMouseX, canvasMouseY, button, dragX, dragY);
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double canvasMouseX = screen != null ? screen.toCanvasX(mouseX) : mouseX;
        double canvasMouseY = screen != null ? screen.toCanvasY(mouseY) : mouseY;
        return stateMachine.dispatchMouseUp(canvasMouseX, canvasMouseY, button);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean handled = panZoomHandler.handleMouseScrolled(mouseX, mouseY, delta, screen);
        if (handled) {
            TutorialManager.getInstance().onPanOrZoom();
        }
        return handled;
    }

    public void renderMarquee(GuiGraphics graphics) {
        selectionHandler.renderMarquee(graphics, screen);
    }

    public void renderWireDrag(GuiGraphics graphics, double mouseX, double mouseY) {
        wireHandler.renderWireDrag(graphics, screen, mouseX, mouseY);
    }
}
