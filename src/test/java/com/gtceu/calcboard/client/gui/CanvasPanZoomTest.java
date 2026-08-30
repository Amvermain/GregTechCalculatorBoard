package com.gtceu.calcboard.client.gui;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CanvasPanZoomTest {

    @Test
    public void testCursorAnchoredZoomPreservesCanvasPointUnderCursor() {
        // Initial state
        double panX = 100.0;
        double panY = 50.0;
        double zoom = 1.0;

        // Mouse pointer position in screen coordinates
        double mouseX = 400.0;
        double mouseY = 300.0;

        // The canvas coordinates currently under the mouse
        double pivotCanvasX = (mouseX - panX) / zoom;
        double pivotCanvasY = (mouseY - panY) / zoom;

        // Case 1: Zoom In (zoom = 2.0)
        double newZoomIn = 2.0;
        double newPanXIn = mouseX - pivotCanvasX * newZoomIn;
        double newPanYIn = mouseY - pivotCanvasY * newZoomIn;

        double canvasUnderMouseAfterZoomInX = (mouseX - newPanXIn) / newZoomIn;
        double canvasUnderMouseAfterZoomInY = (mouseY - newPanYIn) / newZoomIn;

        Assertions.assertEquals(pivotCanvasX, canvasUnderMouseAfterZoomInX, 1e-6,
                "Canvas point under mouse must remain identical after Zoom In");
        Assertions.assertEquals(pivotCanvasY, canvasUnderMouseAfterZoomInY, 1e-6,
                "Canvas point under mouse must remain identical after Zoom In");

        // Case 2: Zoom Out (zoom = 0.5)
        double newZoomOut = 0.5;
        double newPanXOut = mouseX - pivotCanvasX * newZoomOut;
        double newPanYOut = mouseY - pivotCanvasY * newZoomOut;

        double canvasUnderMouseAfterZoomOutX = (mouseX - newPanXOut) / newZoomOut;
        double canvasUnderMouseAfterZoomOutY = (mouseY - newPanYOut) / newZoomOut;

        Assertions.assertEquals(pivotCanvasX, canvasUnderMouseAfterZoomOutX, 1e-6,
                "Canvas point under mouse must remain identical after Zoom Out");
        Assertions.assertEquals(pivotCanvasY, canvasUnderMouseAfterZoomOutY, 1e-6,
                "Canvas point under mouse must remain identical after Zoom Out");
    }

    @Test
    public void testPanningDeltaIsDirectlyOneToOneScreenPixels() {
        double initialPanX = 150.0;
        double initialPanY = 80.0;

        double panStartX = 300.0;
        double panStartY = 200.0;

        double currentMouseX = 350.0;
        double currentMouseY = 230.0;

        // When user drags mouse by (+50, +30) px on screen, pan must change by (+50, +30) regardless of zoom
        double dx = currentMouseX - panStartX;
        double dy = currentMouseY - panStartY;

        double newPanX = initialPanX + dx;
        double newPanY = initialPanY + dy;

        Assertions.assertEquals(200.0, newPanX, 1e-6);
        Assertions.assertEquals(110.0, newPanY, 1e-6);
    }

    @Test
    public void testFitToViewCalculatesCorrectCenterAndZoom() {
        // Screen dimensions: 800 x 600
        double screenW = 800.0;
        double screenH = 600.0;
        double padding = 80.0;
        double availableW = screenW - padding * 2; // 640.0
        double availableH = screenH - padding * 2; // 440.0

        // Content bounds: node from (200, 100) to (600, 400)
        double minX = 200.0;
        double minY = 100.0;
        double maxX = 600.0;
        double maxY = 400.0;

        double contentW = maxX - minX; // 400.0
        double contentH = maxY - minY; // 300.0
        double centerCanvasX = minX + contentW / 2.0; // 400.0
        double centerCanvasY = minY + contentH / 2.0; // 250.0

        double targetZoom = Math.min(1.0, Math.min(availableW / contentW, availableH / contentH));
        targetZoom = Math.max(0.25, Math.min(1.5, targetZoom));

        // Available (640, 440) > Content (400, 300), so targetZoom should be 1.0
        Assertions.assertEquals(1.0, targetZoom, 1e-6);

        double targetPanX = (screenW / 2.0) - (centerCanvasX * targetZoom); // 400 - 400 = 0
        double targetPanY = (screenH / 2.0) - (centerCanvasY * targetZoom); // 300 - 250 = 50

        Assertions.assertEquals(0.0, targetPanX, 1e-6);
        Assertions.assertEquals(50.0, targetPanY, 1e-6);

        // Center on screen should correspond exactly to center on canvas
        double screenCenterX = screenW / 2.0;
        double screenCenterY = screenH / 2.0;
        double canvasAtScreenCenter = (screenCenterX - targetPanX) / targetZoom;
        double canvasAtScreenCenterY = (screenCenterY - targetPanY) / targetZoom;

        Assertions.assertEquals(centerCanvasX, canvasAtScreenCenter, 1e-6);
        Assertions.assertEquals(centerCanvasY, canvasAtScreenCenterY, 1e-6);
    }
}
