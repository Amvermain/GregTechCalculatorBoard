package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Handles the contextual Quick Add Marker (+) triggered by empty-space double clicks
 * or wire dragging onto empty canvas space.
 */
public class CanvasQuickAddMarkerHandler {

    private boolean hasQuickAddMarker = false;
    private double quickAddMarkerCanvasX = 0;
    private double quickAddMarkerCanvasY = 0;
    private long quickAddMarkerTime = 0;
    private long lastEmptyClickTime = 0;
    private double lastEmptyClickCanvasX = 0;
    private double lastEmptyClickCanvasY = 0;
    private long lastMarkerHoverTime = 0;

    // Quick Add Contextual Wire Binding
    private RecipeNode quickAddWireSourceNode = null;
    private int quickAddWirePortIdx = -1;
    private boolean quickAddWireIsInput = false;
    private IngredientStack quickAddWireStack = null;
    private boolean quickAddWireShiftAutoRatio = false;

    public CanvasQuickAddMarkerHandler() {}

    public boolean hasQuickAddMarker() {
        if (!hasQuickAddMarker) return false;
        long elapsed = System.currentTimeMillis() - quickAddMarkerTime;
        if (elapsed > 4000) {
            clearQuickAddMarker();
            return false;
        }
        return true;
    }

    public void checkMarkerCursorDistance(double canvasMouseX, double canvasMouseY) {
        if (!hasQuickAddMarker) return;
        double minX = quickAddMarkerCanvasX - 48;
        double maxX = quickAddMarkerCanvasX + 48;
        double minY = quickAddMarkerCanvasY - 14;
        double maxY = quickAddMarkerCanvasY + 14;

        double dx = Math.max(0, Math.max(minX - canvasMouseX, canvasMouseX - maxX));
        double dy = Math.max(0, Math.max(minY - canvasMouseY, canvasMouseY - maxY));
        double dist = Math.hypot(dx, dy);

        long now = System.currentTimeMillis();
        if (dist <= 15.0) {
            lastMarkerHoverTime = now;
        } else if (dist > 35.0) {
            if (dist > 60.0 || (now - lastMarkerHoverTime > 350)) {
                clearQuickAddMarker();
            }
        }
    }

    public double getQuickAddMarkerCanvasX() {
        return quickAddMarkerCanvasX;
    }

    public double getQuickAddMarkerCanvasY() {
        return quickAddMarkerCanvasY;
    }

    public boolean hasQuickAddWireContext() {
        return quickAddWireSourceNode != null;
    }

    public RecipeNode getQuickAddWireSourceNode() {
        return quickAddWireSourceNode;
    }

    public int getQuickAddWirePortIdx() {
        return quickAddWirePortIdx;
    }

    public boolean isQuickAddWireInput() {
        return quickAddWireIsInput;
    }

    public IngredientStack getQuickAddWireStack() {
        return quickAddWireStack;
    }

    public boolean isQuickAddWireShiftAutoRatio() {
        return quickAddWireShiftAutoRatio;
    }

    public void clearQuickAddMarker() {
        this.hasQuickAddMarker = false;
        this.quickAddWireSourceNode = null;
        this.quickAddWirePortIdx = -1;
        this.quickAddWireIsInput = false;
        this.quickAddWireStack = null;
        this.quickAddWireShiftAutoRatio = false;
    }

    public void triggerContextualMarker(double canvasX, double canvasY, RecipeNode srcNode, int portIdx, boolean isInput, IngredientStack stack, boolean shiftAutoRatio) {
        this.hasQuickAddMarker = true;
        this.quickAddMarkerCanvasX = canvasX;
        this.quickAddMarkerCanvasY = canvasY;
        this.quickAddMarkerTime = System.currentTimeMillis();
        this.quickAddWireSourceNode = srcNode;
        this.quickAddWirePortIdx = portIdx;
        this.quickAddWireIsInput = isInput;
        this.quickAddWireStack = stack;
        this.quickAddWireShiftAutoRatio = shiftAutoRatio;
    }

    public boolean handleEmptyCanvasClick(double canvasX, double canvasY, BoardScreen screen) {
        long now = System.currentTimeMillis();
        if (now - lastEmptyClickTime < 350 && Math.hypot(canvasX - lastEmptyClickCanvasX, canvasY - lastEmptyClickCanvasY) < 15.0) {
            triggerContextualMarker(canvasX, canvasY, null, -1, false, null, false);
            lastEmptyClickTime = 0;
            return true;
        } else {
            lastEmptyClickTime = now;
            lastEmptyClickCanvasX = canvasX;
            lastEmptyClickCanvasY = canvasY;
            return false;
        }
    }
}
