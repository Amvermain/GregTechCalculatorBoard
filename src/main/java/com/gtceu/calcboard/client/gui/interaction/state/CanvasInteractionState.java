package com.gtceu.calcboard.client.gui.interaction.state;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Represents a distinct user interaction state on the board canvas.
 */
public interface CanvasInteractionState {

    String getStateName();

    default void onEnter(CanvasInteractionContext ctx) {}

    default void onExit(CanvasInteractionContext ctx) {}

    default boolean onMouseDown(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        return false;
    }

    default boolean onMouseDrag(CanvasInteractionContext ctx, double canvasX, double canvasY, int button, double dx, double dy) {
        return false;
    }

    default boolean onMouseUp(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        return false;
    }

    default boolean onKeyPressed(CanvasInteractionContext ctx, int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default void renderOverlay(CanvasInteractionContext ctx, GuiGraphics graphics, float partialTicks) {}

    void cancel(CanvasInteractionContext ctx);
}
