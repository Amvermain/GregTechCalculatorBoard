package com.gtceu.calcboard.client.gui.render;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.CanvasInteractionHandler;
import com.gtceu.calcboard.client.gui.canvas.BoardHudRenderer;
import com.gtceu.calcboard.client.gui.canvas.CanvasWireRenderer;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles canvas space transformation, viewport culling, and layered Z-offset rendering of graph elements.
 */
public class BoardCanvasRenderer {
    public void renderCanvasScene(
            GuiGraphics graphics,
            BoardScreen screen,
            FlowGraph graph,
            List<NodeWidget> nodeWidgets,
            CanvasWireRenderer wireRenderer,
            CanvasInteractionHandler canvasHandler,
            double panX, double panY, double zoom,
            int width, int height,
            double mouseX, double mouseY,
            float partialTicks
    ) {
        if (graph == null) return;

        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        graphics.pose().pushPose();
        graphics.pose().translate((float) panX, (float) panY, 0.0f);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0f);

        double canvasMouseX = screen.toCanvasX(mouseX);
        double canvasMouseY = screen.toCanvasY(mouseY);

        double screenLeft = -panX / zoom - 100;
        double screenRight = (-panX + width) / zoom + 100;
        double screenTop = -panY / zoom - 100;
        double screenBottom = (-panY + height) / zoom + 100;

        CanvasGroupFrameRenderer.renderFrames(graphics, graph, canvasMouseX, canvasMouseY, null, screen.getSelectedFrameIds(), screenLeft, screenRight, screenTop, screenBottom);
        CanvasStickyNoteRenderer.renderNotes(graphics, graph, canvasMouseX, canvasMouseY, screen.getSelectedNoteIds(), screenLeft, screenRight, screenTop, screenBottom);

        if (wireRenderer != null) {
            wireRenderer.renderWires(graphics, screen, graph, canvasMouseX, canvasMouseY, screenLeft, screenRight, screenTop, screenBottom, zoom);
        }

        renderNodeWidgets(graphics, screen, nodeWidgets, canvasMouseX, canvasMouseY, screenLeft, screenRight, screenTop, screenBottom, partialTicks);
        graphics.flush();
        RenderSystem.disableDepthTest();

        renderQuickActionAndMarquee(graphics, canvasHandler, canvasMouseX, canvasMouseY);

        graphics.pose().popPose();
        graphics.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableDepthTest();
    }

    private void renderNodeWidgets(
            GuiGraphics graphics,
            BoardScreen screen,
            List<NodeWidget> nodeWidgets,
            double canvasMouseX,
            double canvasMouseY,
            double screenLeft,
            double screenRight,
            double screenTop,
            double screenBottom,
            float partialTicks
    ) {
        if (nodeWidgets == null || nodeWidgets.isEmpty()) return;

        List<NodeWidget> deferredSelected = null;
        for (int i = 0; i < nodeWidgets.size(); i++) {
            NodeWidget widget = nodeWidgets.get(i);
            if (screen.isNodeSelected(widget.getNode().getId())) {
                if (deferredSelected == null) deferredSelected = new ArrayList<>(4);
                deferredSelected.add(widget);
                continue;
            }
            if (isWithinViewport(widget, screenLeft, screenRight, screenTop, screenBottom)) {
                renderSingleNode(graphics, widget, (float) (i * 100.0f + 50.0f), canvasMouseX, canvasMouseY, partialTicks);
            }
        }

        if (deferredSelected != null) {
            float selectedBaseZ = (float) (nodeWidgets.size() * 100.0f + 500.0f);
            for (int i = 0; i < deferredSelected.size(); i++) {
                NodeWidget widget = deferredSelected.get(i);
                if (isWithinViewport(widget, screenLeft, screenRight, screenTop, screenBottom)) {
                    renderSingleNode(graphics, widget, selectedBaseZ + (float) (i * 100.0f), canvasMouseX, canvasMouseY, partialTicks);
                }
            }
        }
    }

    private boolean isWithinViewport(NodeWidget widget, double left, double right, double top, double bottom) {
        double nx = widget.getNode().getPosX();
        double ny = widget.getNode().getPosY();
        int nw = widget.getWidth();
        int nh = widget.getHeight();
        return nx + nw >= left && nx <= right && ny + nh >= top && ny <= bottom;
    }

    private void renderSingleNode(GuiGraphics graphics, NodeWidget widget, float zOffset, double mouseX, double mouseY, float partialTicks) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0f, 0.0f, zOffset);
        widget.render(graphics, (int) mouseX, (int) mouseY, partialTicks);
        graphics.pose().popPose();
    }

    private void renderQuickActionAndMarquee(GuiGraphics graphics, CanvasInteractionHandler canvasHandler, double mouseX, double mouseY) {
        if (canvasHandler == null) return;
        canvasHandler.checkMarkerCursorDistance(mouseX, mouseY);
        if (canvasHandler.hasQuickAddMarker()) {
            Font font = Minecraft.getInstance().font;
            BoardHudRenderer.renderQuickAddMarker(graphics, font, canvasHandler.getQuickAddMarkerCanvasX(), canvasHandler.getQuickAddMarkerCanvasY(), mouseX, mouseY);
        }
        canvasHandler.renderMarquee(graphics);
    }
}
