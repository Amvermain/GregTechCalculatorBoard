package com.gtceu.calcboard.client.gui.interaction.state;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.interaction.CanvasSelectionHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * State active while dragging a marquee selection box across the canvas.
 */
public final class CanvasBoxSelectingState implements CanvasInteractionState {

    public static final String STATE_NAME = "BOX_SELECTING";

    @Override
    public String getStateName() {
        return STATE_NAME;
    }

    @Override
    public void onExit(CanvasInteractionContext ctx) {
        ctx.getSelectionHandler().stopBoxSelection();
    }

    @Override
    public void cancel(CanvasInteractionContext ctx) {
        ctx.getSelectionHandler().stopBoxSelection();
        ctx.getStateMachine().returnToIdle();
    }

    @Override
    public boolean onMouseDrag(CanvasInteractionContext ctx, double canvasX, double canvasY, int button, double dx, double dy) {
        if (button == 0 && ctx.getSelectionHandler().isBoxSelecting()) {
            ctx.getSelectionHandler().updateBoxSelection(canvasX, canvasY);
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseUp(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        if (button == 0) {
            finishMarqueeBoxSelection(ctx);
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

    @Override
    public void renderOverlay(CanvasInteractionContext ctx, GuiGraphics graphics, float partialTicks) {
        BoardScreen screen = ctx.getScreen();
        if (screen != null) {
            ctx.getSelectionHandler().renderMarquee(graphics, screen);
        }
    }

    private void finishMarqueeBoxSelection(CanvasInteractionContext ctx) {
        CanvasSelectionHandler handler = ctx.getSelectionHandler();
        double minX = Math.min(handler.getBoxSelectStartX(), handler.getBoxSelectCurX());
        double maxX = Math.max(handler.getBoxSelectStartX(), handler.getBoxSelectCurX());
        double minY = Math.min(handler.getBoxSelectStartY(), handler.getBoxSelectCurY());
        double maxY = Math.max(handler.getBoxSelectStartY(), handler.getBoxSelectCurY());

        ctx.getQuickAddMarkerHandler().clearQuickAddMarker();
        BoardScreen screen = ctx.getScreen();
        if (screen != null && (Math.abs(maxX - minX) > 6 || Math.abs(maxY - minY) > 6)) {
            handler.finishBoxSelection(screen, Screen.hasShiftDown());
        } else {
            handler.stopBoxSelection();
        }
    }
}
