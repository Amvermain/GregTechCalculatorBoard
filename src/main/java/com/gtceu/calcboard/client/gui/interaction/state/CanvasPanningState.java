package com.gtceu.calcboard.client.gui.interaction.state;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.interaction.CanvasPanZoomHandler;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;

import java.util.List;

/**
 * State active while dragging to pan the board viewport across the canvas.
 */
public final class CanvasPanningState implements CanvasInteractionState {

    public static final String STATE_NAME = "PANNING";

    @Override
    public String getStateName() {
        return STATE_NAME;
    }

    @Override
    public void onExit(CanvasInteractionContext ctx) {
        ctx.getPanZoomHandler().stopPan();
        ctx.setPotentialRightClick(false);
    }

    @Override
    public void cancel(CanvasInteractionContext ctx) {
        ctx.getPanZoomHandler().stopPan();
        ctx.setPotentialRightClick(false);
        ctx.getStateMachine().returnToIdle();
    }

    @Override
    public boolean onMouseDrag(CanvasInteractionContext ctx, double canvasX, double canvasY, int button, double dx, double dy) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null) return false;

        double screenX = screen.toScreenX(canvasX);
        double screenY = screen.toScreenY(canvasY);
        CanvasPanZoomHandler panZoomHandler = ctx.getPanZoomHandler();

        if (panZoomHandler.handleMouseDragged(screenX, screenY, button, screen)) {
            return true;
        }

        if (panZoomHandler.isPanning() && (button == 1 || button == 2)) {
            screen.setPanX(screen.getPanX() + dx);
            screen.setPanY(screen.getPanY() + dy);
            TutorialManager.getInstance().onPanOrZoom();
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseUp(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        BoardScreen screen = ctx.getScreen();
        double screenX = screen != null ? screen.toScreenX(canvasX) : canvasX;
        double screenY = screen != null ? screen.toScreenY(canvasY) : canvasY;

        if (button == 1 && ctx.isPotentialRightClick()) {
            ctx.setPotentialRightClick(false);
            double dist = Math.hypot(screenX - ctx.getRightClickStartMouseX(), screenY - ctx.getRightClickStartMouseY());
            if (dist < 4.5) {
                openAppropriateContextMenu(ctx, screenX, screenY, ctx.getRightClickStartCanvasX(), ctx.getRightClickStartCanvasY());
                ctx.getPanZoomHandler().stopPan();
                ctx.getStateMachine().returnToIdle();
                return true;
            }
        }

        if (button == 1 || button == 2) {
            ctx.getPanZoomHandler().stopPan();
            ctx.getStateMachine().returnToIdle();
            return true;
        }
        return false;
    }

    private void openAppropriateContextMenu(CanvasInteractionContext ctx, double mouseX, double mouseY, double canvasX, double canvasY) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null) return;

        if (screen.findHoveredWire(canvasX, canvasY, 8.0) != null) {
            return;
        }

        List<NodeWidget> nodeWidgets = screen.getNodeWidgets();
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget nw = nodeWidgets.get(i);
            if (!nw.isPointInside(canvasX, canvasY)) continue;

            if (screen.getSelectedNodeIds().size() > 1 && screen.isNodeSelected(nw.getNode().getId())) {
                ctx.getContextMenuManager().openForSelection(mouseX, mouseY);
                return;
            }

            ctx.getContextMenuManager().openForNode(mouseX, mouseY, nw);
            return;
        }

        if (screen.getSelectedNodeIds().size() > 1 && isInsideSelectionBounds(screen, canvasX, canvasY)) {
            ctx.getContextMenuManager().openForSelection(mouseX, mouseY);
            return;
        }

        ctx.getContextMenuManager().openForCanvas(mouseX, mouseY, canvasX, canvasY);
    }

    private boolean isInsideSelectionBounds(BoardScreen screen, double canvasX, double canvasY) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (NodeWidget nw : screen.getNodeWidgets()) {
            if (!screen.isNodeSelected(nw.getNode().getId())) continue;
            minX = Math.min(minX, nw.getNode().getPosX());
            minY = Math.min(minY, nw.getNode().getPosY());
            maxX = Math.max(maxX, nw.getNode().getPosX() + nw.getWidth());
            maxY = Math.max(maxY, nw.getNode().getPosY() + nw.getHeight());
        }
        return canvasX >= minX && canvasX <= maxX && canvasY >= minY && canvasY <= maxY;
    }
}
