package com.gtceu.calcboard.client.gui.interaction.state;

import com.gtceu.calcboard.client.gui.BoardScreen;
import org.lwjgl.glfw.GLFW;

/**
 * State active while interacting with or dragging canvas group frames.
 */
public final class CanvasFrameInteractingState implements CanvasInteractionState {

    public static final String STATE_NAME = "FRAME_INTERACTING";

    @Override
    public String getStateName() {
        return STATE_NAME;
    }

    @Override
    public void onExit(CanvasInteractionContext ctx) {
        ctx.getDragStartPositions().clear();
    }

    @Override
    public void cancel(CanvasInteractionContext ctx) {
        ctx.getDragStartPositions().clear();
        ctx.getStateMachine().returnToIdle();
    }

    @Override
    public boolean onMouseDrag(CanvasInteractionContext ctx, double canvasX, double canvasY, int button, double dx, double dy) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null) return false;
        boolean handled = ctx.getFrameHandler().handleMouseDragged(
                canvasX, canvasY, screen, ctx.getLastDragCanvasX(), ctx.getLastDragCanvasY(), ctx.getDragStartPositions());
        if (handled) {
            ctx.setLastDragCanvasX(canvasX);
            ctx.setLastDragCanvasY(canvasY);
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseUp(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        if (button == 0) {
            BoardScreen screen = ctx.getScreen();
            boolean handled = ctx.getFrameHandler().handleMouseReleased(
                    canvasX, canvasY, button, screen, ctx.getDragStartPositions());
            ctx.getStateMachine().returnToIdle();
            return handled;
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
}
