package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.client.gui.BoardScreen;

/**
 * Handles canvas panning (middle/right-click dragging) and smooth cursor-anchored zooming.
 */
public class CanvasPanZoomHandler {

    private boolean isPanning = false;
    private double panStartX = 0;
    private double panStartY = 0;

    public CanvasPanZoomHandler() {}

    public boolean isPanning() {
        return isPanning;
    }

    public void startPan(double mouseX, double mouseY) {
        this.isPanning = true;
        this.panStartX = mouseX;
        this.panStartY = mouseY;
    }

    public void stopPan() {
        this.isPanning = false;
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button, BoardScreen screen) {
        if (isPanning) {
            double dx = mouseX - panStartX;
            double dy = mouseY - panStartY;
            screen.setPanX(screen.getPanX() + dx);
            screen.setPanY(screen.getPanY() + dy);
            panStartX = mouseX;
            panStartY = mouseY;
            return true;
        }
        return false;
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double delta, BoardScreen screen) {
        if (screen == null) return false;

        double oldZoom = screen.getZoom();
        double zoomFactor = (delta > 0) ? 1.15 : (1.0 / 1.15);
        double newZoom = Math.max(0.25, Math.min(3.0, oldZoom * zoomFactor));

        if (Math.abs(newZoom - oldZoom) < 0.0001) {
            return false;
        }

        // Anchor zooming directly around current mouse pointer position
        double pivotCanvasX = screen.toCanvasX(mouseX);
        double pivotCanvasY = screen.toCanvasY(mouseY);

        screen.setZoom(newZoom);

        double newPanX = mouseX - pivotCanvasX * newZoom;
        double newPanY = mouseY - pivotCanvasY * newZoom;

        screen.setPanX(newPanX);
        screen.setPanY(newPanY);
        return true;
    }
}
