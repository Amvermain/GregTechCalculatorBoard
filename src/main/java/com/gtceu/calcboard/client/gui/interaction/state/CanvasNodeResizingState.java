package com.gtceu.calcboard.client.gui.interaction.state;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * State active while dragging a node card resize handle.
 */
public final class CanvasNodeResizingState implements CanvasInteractionState {

    public static final String STATE_NAME = "NODE_RESIZING";

    @Override
    public String getStateName() {
        return STATE_NAME;
    }

    @Override
    public void onExit(CanvasInteractionContext ctx) {
        ctx.setResizingNode(null);
    }

    @Override
    public void cancel(CanvasInteractionContext ctx) {
        NodeWidget resizingNode = ctx.getResizingNode();
        if (resizingNode != null) {
            resizingNode.getNode().setCardWidth(ctx.getOrigNodeWidth());
            resizingNode.getNode().setCardHeight(ctx.getOrigNodeHeight());
        }
        ctx.setResizingNode(null);
        ctx.getStateMachine().returnToIdle();
    }

    @Override
    public boolean onMouseDrag(CanvasInteractionContext ctx, double canvasX, double canvasY, int button, double dx, double dy) {
        NodeWidget resizingNode = ctx.getResizingNode();
        if (resizingNode != null && button == 0) {
            applyNodeResize(ctx, resizingNode, canvasX, canvasY);
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseUp(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        if (button == 0 && ctx.getResizingNode() != null) {
            NodeWidget resizingNode = ctx.getResizingNode();
            resizingNode.getTextCache().markDirty();
            BoardScreen screen = ctx.getScreen();
            if (screen != null && screen.getWireRenderer() != null) {
                screen.getWireRenderer().markDirty();
            }
            ctx.setResizingNode(null);
            ctx.getStateMachine().returnToIdle();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyPressed(CanvasInteractionContext ctx, int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel(ctx);
            return true;
        }
        return false;
    }

    private void applyNodeResize(CanvasInteractionContext ctx, NodeWidget resizingNode, double canvasX, double canvasY) {
        double origX = resizingNode.getNode().getPosX();
        double origY = resizingNode.getNode().getPosY();
        double rawDeltaX = canvasX - ctx.getResizeStartCanvasX();
        double rawDeltaY = canvasY - ctx.getResizeStartCanvasY();
        boolean isSnap = Screen.hasControlDown() || BoardManager.getInstance().isGridSnapEnabled();
        int gridSize = Math.max(16, BoardManager.getInstance().getGridSnapSize());

        int newWidth;
        int newHeight;
        if (isSnap) {
            double targetRightX = origX + ctx.getOrigNodeWidth() + rawDeltaX;
            double targetBottomY = origY + ctx.getOrigNodeHeight() + rawDeltaY;
            double snappedRightX = Math.round(targetRightX / (double) gridSize) * (double) gridSize;
            double snappedBottomY = Math.round(targetBottomY / (double) gridSize) * (double) gridSize;
            newWidth = (int) Math.max(190, Math.min(500, snappedRightX - origX));
            newHeight = (int) Math.max(resizingNode.calculateAutoHeight(), Math.min(600, snappedBottomY - origY));
        } else {
            newWidth = (int) Math.max(190, Math.min(500, ctx.getOrigNodeWidth() + rawDeltaX));
            newHeight = (int) Math.max(resizingNode.calculateAutoHeight(), Math.min(600, ctx.getOrigNodeHeight() + rawDeltaY));
        }
        resizingNode.getNode().setCardWidth(newWidth);
        resizingNode.getNode().setCardHeight(newHeight);
    }
}
