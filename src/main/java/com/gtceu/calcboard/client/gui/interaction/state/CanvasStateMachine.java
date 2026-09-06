package com.gtceu.calcboard.client.gui.interaction.state;

import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

/**
 * Orchestrates canvas interaction states, ensuring deterministic lifecycle transitions and event routing.
 */
public final class CanvasStateMachine {

    private final CanvasInteractionContext context;
    private final CanvasIdleState idleState;
    private CanvasInteractionState currentState;

    public CanvasStateMachine(CanvasInteractionContext context) {
        this.context = Objects.requireNonNull(context);
        this.context.setStateMachine(this);
        this.idleState = new CanvasIdleState();
        this.currentState = this.idleState;
        this.currentState.onEnter(this.context);
    }

    public synchronized void transitionTo(CanvasInteractionState newState) {
        if (newState == null || newState == currentState) return;

        currentState.onExit(context);
        this.currentState = newState;
        this.currentState.onEnter(context);
    }

    public void returnToIdle() {
        transitionTo(idleState);
    }

    public void cancelCurrentInteraction() {
        currentState.cancel(context);
        if (currentState != idleState) {
            returnToIdle();
        }
    }

    public CanvasInteractionState getCurrentState() {
        return currentState;
    }

    public boolean isInState(Class<? extends CanvasInteractionState> stateClass) {
        return stateClass.isInstance(currentState);
    }

    public boolean dispatchMouseDown(double canvasX, double canvasY, int button) {
        return currentState.onMouseDown(context, canvasX, canvasY, button);
    }

    public boolean dispatchMouseDrag(double canvasX, double canvasY, int button, double dx, double dy) {
        return currentState.onMouseDrag(context, canvasX, canvasY, button, dx, dy);
    }

    public boolean dispatchMouseUp(double canvasX, double canvasY, int button) {
        return currentState.onMouseUp(context, canvasX, canvasY, button);
    }

    public boolean dispatchKeyPressed(int keyCode, int scanCode, int modifiers) {
        return currentState.onKeyPressed(context, keyCode, scanCode, modifiers);
    }

    public void renderOverlay(GuiGraphics graphics, float partialTicks) {
        currentState.renderOverlay(context, graphics, partialTicks);
    }
}
