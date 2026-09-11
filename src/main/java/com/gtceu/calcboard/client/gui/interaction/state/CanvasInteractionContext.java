package com.gtceu.calcboard.client.gui.interaction.state;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.CanvasInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasContextMenuManager;
import com.gtceu.calcboard.client.gui.interaction.CanvasFrameInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasNoteInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasPanZoomHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasQuickAddMarkerHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasSelectionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasWireInteractionHandler;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Shared execution context providing canvas handlers, geometry transforms, and buffers for interaction states.
 */
public final class CanvasInteractionContext {

    private final BoardScreen screen;
    private final CanvasInteractionHandler interactionHandler;
    private final CanvasPanZoomHandler panZoomHandler;
    private final CanvasSelectionHandler selectionHandler;
    private final CanvasQuickAddMarkerHandler quickAddMarkerHandler;
    private final CanvasFrameInteractionHandler frameHandler;
    private final CanvasNoteInteractionHandler noteHandler;
    private final CanvasWireInteractionHandler wireHandler;
    private final CanvasContextMenuManager contextMenuManager;

    private CanvasStateMachine stateMachine;

    private final Map<String, double[]> dragStartPositions = new HashMap<>();

    private NodeWidget draggingNode;
    private double lastDragCanvasX;
    private double lastDragCanvasY;
    private double dragStartMouseCanvasX;
    private double dragStartMouseCanvasY;

    private NodeWidget resizingNode;
    private double resizeStartCanvasX;
    private double resizeStartCanvasY;
    private int origNodeWidth;
    private int origNodeHeight;

    private double rightClickStartMouseX;
    private double rightClickStartMouseY;
    private double rightClickStartCanvasX;
    private double rightClickStartCanvasY;
    private boolean potentialRightClick;

    public CanvasInteractionContext(
            BoardScreen screen,
            CanvasInteractionHandler interactionHandler,
            CanvasPanZoomHandler panZoomHandler,
            CanvasSelectionHandler selectionHandler,
            CanvasQuickAddMarkerHandler quickAddMarkerHandler,
            CanvasFrameInteractionHandler frameHandler,
            CanvasNoteInteractionHandler noteHandler,
            CanvasWireInteractionHandler wireHandler,
            CanvasContextMenuManager contextMenuManager) {
        this.screen = screen;
        this.interactionHandler = interactionHandler;
        this.panZoomHandler = Objects.requireNonNull(panZoomHandler);
        this.selectionHandler = Objects.requireNonNull(selectionHandler);
        this.quickAddMarkerHandler = Objects.requireNonNull(quickAddMarkerHandler);
        this.frameHandler = Objects.requireNonNull(frameHandler);
        this.noteHandler = Objects.requireNonNull(noteHandler);
        this.wireHandler = Objects.requireNonNull(wireHandler);
        this.contextMenuManager = Objects.requireNonNull(contextMenuManager);
    }

    public void setStateMachine(CanvasStateMachine stateMachine) {
        this.stateMachine = stateMachine;
    }

    public CanvasStateMachine getStateMachine() {
        return stateMachine;
    }

    public BoardScreen getScreen() {
        return screen;
    }

    public CanvasInteractionHandler getInteractionHandler() {
        return interactionHandler;
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

    public CanvasContextMenuManager getContextMenuManager() {
        return contextMenuManager;
    }

    public Map<String, double[]> getDragStartPositions() {
        return dragStartPositions;
    }

    public NodeWidget getDraggingNode() {
        return draggingNode;
    }

    public void setDraggingNode(NodeWidget draggingNode) {
        this.draggingNode = draggingNode;
    }

    public double getLastDragCanvasX() {
        return lastDragCanvasX;
    }

    public void setLastDragCanvasX(double lastDragCanvasX) {
        this.lastDragCanvasX = lastDragCanvasX;
    }

    public double getLastDragCanvasY() {
        return lastDragCanvasY;
    }

    public void setLastDragCanvasY(double lastDragCanvasY) {
        this.lastDragCanvasY = lastDragCanvasY;
    }

    public double getDragStartMouseCanvasX() {
        return dragStartMouseCanvasX;
    }

    public void setDragStartMouseCanvasX(double dragStartMouseCanvasX) {
        this.dragStartMouseCanvasX = dragStartMouseCanvasX;
    }

    public double getDragStartMouseCanvasY() {
        return dragStartMouseCanvasY;
    }

    public void setDragStartMouseCanvasY(double dragStartMouseCanvasY) {
        this.dragStartMouseCanvasY = dragStartMouseCanvasY;
    }

    public NodeWidget getResizingNode() {
        return resizingNode;
    }

    public void setResizingNode(NodeWidget resizingNode) {
        this.resizingNode = resizingNode;
    }

    public double getResizeStartCanvasX() {
        return resizeStartCanvasX;
    }

    public void setResizeStartCanvasX(double resizeStartCanvasX) {
        this.resizeStartCanvasX = resizeStartCanvasX;
    }

    public double getResizeStartCanvasY() {
        return resizeStartCanvasY;
    }

    public void setResizeStartCanvasY(double resizeStartCanvasY) {
        this.resizeStartCanvasY = resizeStartCanvasY;
    }

    public int getOrigNodeWidth() {
        return origNodeWidth;
    }

    public void setOrigNodeWidth(int origNodeWidth) {
        this.origNodeWidth = origNodeWidth;
    }

    public int getOrigNodeHeight() {
        return origNodeHeight;
    }

    public void setOrigNodeHeight(int origNodeHeight) {
        this.origNodeHeight = origNodeHeight;
    }

    public double getRightClickStartMouseX() {
        return rightClickStartMouseX;
    }

    public void setRightClickStartMouseX(double rightClickStartMouseX) {
        this.rightClickStartMouseX = rightClickStartMouseX;
    }

    public double getRightClickStartMouseY() {
        return rightClickStartMouseY;
    }

    public void setRightClickStartMouseY(double rightClickStartMouseY) {
        this.rightClickStartMouseY = rightClickStartMouseY;
    }

    public double getRightClickStartCanvasX() {
        return rightClickStartCanvasX;
    }

    public void setRightClickStartCanvasX(double rightClickStartCanvasX) {
        this.rightClickStartCanvasX = rightClickStartCanvasX;
    }

    public double getRightClickStartCanvasY() {
        return rightClickStartCanvasY;
    }

    public void setRightClickStartCanvasY(double rightClickStartCanvasY) {
        this.rightClickStartCanvasY = rightClickStartCanvasY;
    }

    public boolean isPotentialRightClick() {
        return potentialRightClick;
    }

    public void setPotentialRightClick(boolean potentialRightClick) {
        this.potentialRightClick = potentialRightClick;
    }
}
