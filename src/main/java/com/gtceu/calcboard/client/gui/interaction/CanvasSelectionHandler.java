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

        // Select overlapping Nodes
        for (NodeWidget w : screen.getNodeWidgets()) {
            RecipeNode n = w.getNode();
            double nw = n.getCardWidth();
            double nh = n.getCardHeight();
            boolean overlaps = (n.getPosX() < maxX && n.getPosX() + nw > minX &&
                    n.getPosY() < maxY && n.getPosY() + nh > minY);
            if (overlaps) {
                screen.selectNode(n.getId(), true);
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

        graphics.fill(minX, minY, maxX, maxY, 0x3338BDF8);
        graphics.renderOutline(minX, minY, maxX - minX, maxY - minY, 0xFF38BDF8);
    }
}
