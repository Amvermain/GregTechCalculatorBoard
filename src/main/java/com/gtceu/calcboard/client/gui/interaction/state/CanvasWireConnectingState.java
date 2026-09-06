package com.gtceu.calcboard.client.gui.interaction.state;

import com.gtceu.calcboard.client.gui.BoardScreen;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.glfw.GLFW;

/**
 * State active while dragging a connection wire between node ports.
 */
public final class CanvasWireConnectingState implements CanvasInteractionState {

    public static final String STATE_NAME = "WIRE_CONNECTING";

    @Override
    public String getStateName() {
        return STATE_NAME;
    }

    @Override
    public void onExit(CanvasInteractionContext ctx) {
        if (ctx.getWireHandler().isDraggingWire()) {
            ctx.getWireHandler().cancelWireDrag();
        }
    }

    @Override
    public void cancel(CanvasInteractionContext ctx) {
        ctx.getWireHandler().cancelWireDrag();
        ctx.getStateMachine().returnToIdle();
    }

    @Override
    public boolean onMouseDown(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        if (button == 1) {
            cancel(ctx);
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseUp(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        if (button == 0) {
            BoardScreen screen = ctx.getScreen();
            double screenX = screen != null ? screen.toScreenX(canvasX) : canvasX;
            double screenY = screen != null ? screen.toScreenY(canvasY) : canvasY;
            boolean handled = ctx.getWireHandler().handleWireReleased(screenX, screenY, button, screen, ctx.getQuickAddMarkerHandler());
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

    @Override
    public void renderOverlay(CanvasInteractionContext ctx, GuiGraphics graphics, float partialTicks) {
        BoardScreen screen = ctx.getScreen();
        if (screen != null) {
            ctx.getWireHandler().renderWireDrag(graphics, screen, screen.getLastMouseX(), screen.getLastMouseY());
        }
    }
}
