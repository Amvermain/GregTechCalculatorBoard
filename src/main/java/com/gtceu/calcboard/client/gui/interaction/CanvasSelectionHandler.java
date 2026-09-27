package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles marquee / box selection of multiple canvas entities (nodes, group frames, sticky notes).
 */
public class CanvasSelectionHandler {

    private boolean isBoxSelecting = false;
    private double boxSelectStartX, boxSelectStartY;
    private double boxSelectCurX, boxSelectCurY;
    private final Map<String, double[]> dragStartPositions = new HashMap<>();

    public CanvasSelectionHandler() {}

    public boolean isBoxSelecting() {
        return isBoxSelecting;
    }

    public double getBoxSelectStartX() {
        return boxSelectStartX;
    }

    public double getBoxSelectStartY() {
        return boxSelectStartY;
    }

    public double getBoxSelectCurX() {
        return boxSelectCurX;
    }

    public double getBoxSelectCurY() {
        return boxSelectCurY;
    }

    public Map<String, double[]> getDragStartPositions() {
        return dragStartPositions;
    }

    public void startBoxSelection(double canvasX, double canvasY) {
        this.isBoxSelecting = true;
        this.boxSelectStartX = canvasX;
        this.boxSelectStartY = canvasY;
        this.boxSelectCurX = canvasX;
        this.boxSelectCurY = canvasY;
    }

    public void updateBoxSelection(double canvasX, double canvasY) {
        if (isBoxSelecting) {
            this.boxSelectCurX = canvasX;
            this.boxSelectCurY = canvasY;
        }
    }

    public void stopBoxSelection() {
        this.isBoxSelecting = false;
    }

    public void captureDragStartPositions(Set<String> selectedNodeIds, List<NodeWidget> widgets) {
        dragStartPositions.clear();
        if (selectedNodeIds == null || widgets == null) return;
        for (NodeWidget w : widgets) {
            if (selectedNodeIds.contains(w.getNode().getId())) {
                dragStartPositions.put(w.getNode().getId(), new double[]{w.getNode().getPosX(), w.getNode().getPosY()});
            }
        }
    }

    public void finishBoxSelection(BoardScreen screen, boolean shiftOrCtrlDown) {
        if (!isBoxSelecting) return;
        isBoxSelecting = false;

        double minX = Math.min(boxSelectStartX, boxSelectCurX);
        double maxX = Math.max(boxSelectStartX, boxSelectCurX);
        double minY = Math.min(boxSelectStartY, boxSelectCurY);
        double maxY = Math.max(boxSelectStartY, boxSelectCurY);

        if (!shiftOrCtrlDown) {
            screen.clearSelection();
        }

        // Select overlapping Nodes or Ports based on coverage threshold
        for (NodeWidget w : screen.getNodeWidgets()) {
            RecipeNode n = w.getNode();
            if (screen.getGraph() != null && screen.getGraph().isNodeInFoldedOrEmbeddedFrame(n.getId())) {
                continue;
            }
            double nw = w.getWidth();
            double nh = w.getHeight();
            double nx1 = n.getPosX();
            double ny1 = n.getPosY();
            double nx2 = nx1 + nw;
            double ny2 = ny1 + nh;

            double interX1 = Math.max(minX, nx1);
            double interX2 = Math.min(maxX, nx2);
            double interY1 = Math.max(minY, ny1);
            double interY2 = Math.min(maxY, ny2);

            if (interX2 > interX1 && interY2 > interY1) {
                double interArea = (interX2 - interX1) * (interY2 - interY1);
                double nodeArea = nw * nh;
                double ratio = nodeArea > 0 ? (interArea / nodeArea) : 1.0;

                if (ratio >= 0.35 || n.isReroute()) {
                    screen.selectNode(n.getId(), true);
                } else {
                    boolean portMatched = selectOverlappingPorts(screen, n, w, minX, minY, maxX, maxY);
                    if (!portMatched && ratio > 0.15) {
                        screen.selectNode(n.getId(), true);
                    }
                }
            }
        }

        // Select overlapping Frames
        for (CanvasGroupFrame f : screen.getGraph().getFrames()) {
            boolean overlaps = (f.getPosX() < maxX && f.getPosX() + f.getWidth() > minX &&
                    f.getPosY() < maxY && f.getPosY() + f.getHeight() > minY);
            if (overlaps) {
                screen.selectFrame(f.getId(), true);
            }
        }

        // Select overlapping Sticky Notes
        for (CanvasStickyNote note : screen.getGraph().getStickyNotes()) {
            boolean overlaps = (note.getPosX() < maxX && note.getPosX() + note.getWidth() > minX &&
                    note.getPosY() < maxY && note.getPosY() + note.getHeight() > minY);
            if (overlaps) {
                screen.selectNote(note.getId(), true);
            }
        }
    }

    public void renderMarquee(GuiGraphics graphics, BoardScreen screen) {
        if (!isBoxSelecting) return;
        int minX = (int) Math.floor(Math.min(boxSelectStartX, boxSelectCurX));
        int maxX = (int) Math.ceil(Math.max(boxSelectStartX, boxSelectCurX));
        int minY = (int) Math.floor(Math.min(boxSelectStartY, boxSelectCurY));
        int maxY = (int) Math.ceil(Math.max(boxSelectStartY, boxSelectCurY));

        renderIntersectedEntitiesHighlight(graphics, screen, minX, minY, maxX, maxY);
        renderMarqueeBox(graphics, minX, minY, maxX, maxY);
    }

    private void renderMarqueeBox(GuiGraphics graphics, int minX, int minY, int maxX, int maxY) {
        graphics.fill(minX, minY, maxX, maxY, 0x3338BDF8);
        graphics.renderOutline(minX, minY, maxX - minX, maxY - minY, 0xFF38BDF8);
    }

    private void renderIntersectedEntitiesHighlight(GuiGraphics graphics, BoardScreen screen, int minX, int minY, int maxX, int maxY) {
        if (screen == null) return;
        for (NodeWidget w : screen.getNodeWidgets()) {
            RecipeNode n = w.getNode();
            if (screen.getGraph() != null && screen.getGraph().isNodeInFoldedOrEmbeddedFrame(n.getId())) {
                continue;
            }
            double nx = n.getPosX();
            double ny = n.getPosY();
            int nw = w.getWidth();
            int nh = w.getHeight();
            if (nx < maxX && nx + nw > minX && ny < maxY && ny + nh > minY) {
                renderNodeHighlight(graphics, (int) nx, (int) ny, nw, nh);
            }
        }
    }

    private void renderNodeHighlight(GuiGraphics graphics, int x, int y, int w, int h) {
        graphics.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x2E38BDF8);
        graphics.renderOutline(x - 2, y - 2, w + 4, h + 4, 0xFF38BDF8);
    }

    private boolean selectOverlappingPorts(BoardScreen screen, RecipeNode n, NodeWidget w, double minX, double minY, double maxX, double maxY) {
        boolean inputMatched = selectPorts(screen, n, w, true, n.getVisibleInputIndices(), minX, minY, maxX, maxY);
        boolean outputMatched = selectPorts(screen, n, w, false, n.getVisibleOutputIndices(), minX, minY, maxX, maxY);
        return inputMatched || outputMatched;
    }

    private boolean selectPorts(BoardScreen screen, RecipeNode n, NodeWidget w, boolean isInput, List<Integer> indices, double minX, double minY, double maxX, double maxY) {
        boolean matched = false;
        for (int idx : indices) {
            if (w.isPortOverlapping(isInput, idx, minX, minY, maxX, maxY)) {
                screen.selectPort(n.getId(), isInput, idx, true);
                matched = true;
            }
        }
        return matched;
    }
}
