package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.interaction.CanvasFrameInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasNoteInteractionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasPanZoomHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasQuickAddMarkerHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasSelectionHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasWireInteractionHandler;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CanvasInteractionHandler {

    private final BoardScreen screen;

    private final CanvasPanZoomHandler panZoomHandler = new CanvasPanZoomHandler();
    private final CanvasSelectionHandler selectionHandler = new CanvasSelectionHandler();
    private final CanvasQuickAddMarkerHandler quickAddMarkerHandler = new CanvasQuickAddMarkerHandler();
    private final CanvasFrameInteractionHandler frameHandler = new CanvasFrameInteractionHandler();
    private final CanvasNoteInteractionHandler noteHandler = new CanvasNoteInteractionHandler();
    private final CanvasWireInteractionHandler wireHandler = new CanvasWireInteractionHandler();

    private NodeWidget draggingNode = null;
    private double lastDragCanvasX, lastDragCanvasY;

    private NodeWidget resizingNode = null;
    private double resizeStartCanvasX, resizeStartCanvasY;
    private int origNodeWidth, origNodeHeight;

    private final Map<String, double[]> dragStartPositions = new HashMap<>();

    public CanvasInteractionHandler(BoardScreen screen) {
        this.screen = screen;
    }

    public boolean hasQuickAddMarker() {
        return quickAddMarkerHandler.hasQuickAddMarker();
    }

    public void checkMarkerCursorDistance(double canvasMouseX, double canvasMouseY) {
        quickAddMarkerHandler.checkMarkerCursorDistance(canvasMouseX, canvasMouseY);
    }

    public double getQuickAddMarkerCanvasX() {
        return quickAddMarkerHandler.getQuickAddMarkerCanvasX();
    }

    public double getQuickAddMarkerCanvasY() {
        return quickAddMarkerHandler.getQuickAddMarkerCanvasY();
    }

    public boolean hasQuickAddWireContext() {
        return quickAddMarkerHandler.hasQuickAddWireContext();
    }

    public void clearQuickAddMarker() {
        quickAddMarkerHandler.clearQuickAddMarker();
    }

    public CanvasPanZoomHandler getPanZoomHandler() {
        return panZoomHandler;
    }

    public CanvasSelectionHandler getSelectionHandler() {
        return selectionHandler;
    }

    public CanvasQuickAddMarkerHandler getQuickAddMarkerHandler() {
        return quickAddMarkerHandler;
    }

    public CanvasFrameInteractionHandler getFrameHandler() {
        return frameHandler;
    }

    public CanvasNoteInteractionHandler getNoteHandler() {
        return noteHandler;
    }

    public CanvasWireInteractionHandler getWireHandler() {
        return wireHandler;
    }

    public NodeWidget getWireStartNode() {
        return wireHandler.getWireStartNode();
    }

    public int getWireStartPortIdx() {
        return wireHandler.getWireStartPortIdx();
    }

    public boolean isWireStartInput() {
        return wireHandler.isWireStartInput();
    }

    public boolean isDraggingWire() {
        return wireHandler.isDraggingWire();
    }

    public boolean isPanning() {
        return panZoomHandler.isPanning();
    }

    public void cancelWireDrag() {
        wireHandler.cancelWireDrag();
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (wireHandler.isDraggingWire() && button == 1) {
            wireHandler.cancelWireDrag();
            return true;
        }

        double canvasMouseX = screen.toCanvasX(mouseX);
        double canvasMouseY = screen.toCanvasY(mouseY);

        if (handleHiddenPortsPopupClick(canvasMouseX, canvasMouseY, button)) {
            return true;
        }

        if (handleNodeWidgetsClick(canvasMouseX, canvasMouseY, button)) {
            return true;
        }

        if (wireHandler.handleWireClick(canvasMouseX, canvasMouseY, button, screen)) {
            return true;
        }

        if (frameHandler.handleMouseClicked(canvasMouseX, canvasMouseY, button, screen, dragStartPositions)) {
            lastDragCanvasX = canvasMouseX;
            lastDragCanvasY = canvasMouseY;
            return true;
        }

        if (noteHandler.handleMouseClicked(canvasMouseX, canvasMouseY, button, screen, dragStartPositions)) {
            lastDragCanvasX = canvasMouseX;
            lastDragCanvasY = canvasMouseY;
            return true;
        }

        commitActiveNodeWidgetEdits();

        if (handleQuickAddButtonsClick(canvasMouseX, canvasMouseY, button)) {
            return true;
        }

        if (button == 0) {
            return handleEmptySpaceClick(canvasMouseX, canvasMouseY);
        }

        if (button == 1 || button == 2) {
            panZoomHandler.startPan(mouseX, mouseY);
            return true;
        }

        return false;
    }

    private boolean handleHiddenPortsPopupClick(double canvasMouseX, double canvasMouseY, int button) {
        List<NodeWidget> nodeWidgets = screen.getNodeWidgets();
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            var popup = widget.getHiddenPortsPopup();
            if (popup == null || !popup.isVisible()) continue;

            if (popup.isPointInside(canvasMouseX, canvasMouseY)) {
                return popup.mouseClicked(canvasMouseX, canvasMouseY, button);
            }
            if (button == 0) {
                popup.close();
            }
        }
        return false;
    }

    private boolean handleNodeWidgetsClick(double canvasMouseX, double canvasMouseY, int button) {
        List<NodeWidget> nodeWidgets = screen.getNodeWidgets();
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            if (!widget.isPointInside(canvasMouseX, canvasMouseY)) continue;

            if (button == 0) {
                screen.bringNodeToFront(widget.getNode());
            }

            if (wireHandler.handlePortClick(widget, canvasMouseX, canvasMouseY, button, screen)) {
                return true;
            }

            if (button == 0 && widget.isResizeHandleHovered(canvasMouseX, canvasMouseY)) {
                return startNodeResize(widget, canvasMouseX, canvasMouseY);
            }

            if (button == 0 && widget.checkHeaderDoubleClick(canvasMouseX, canvasMouseY)) {
                return screen.ensureEditPermission();
            }

            if (widget.mouseClicked(canvasMouseX, canvasMouseY, button)) {
                return true;
            }

            if (button == 0) {
                handleNodeSelectionClick(widget);
            }

            if (widget.isHeaderHovered(canvasMouseX, canvasMouseY) && button == 0 && !widget.getNameEditor().isEditing()) {
                startNodeDrag(widget, canvasMouseX, canvasMouseY);
                return true;
            }
            return true;
        }
        return false;
    }

    private boolean startNodeResize(NodeWidget widget, double canvasMouseX, double canvasMouseY) {
        if (!screen.ensureEditPermission()) return true;
        resizingNode = widget;
        resizeStartCanvasX = canvasMouseX;
        resizeStartCanvasY = canvasMouseY;
        origNodeWidth = widget.getWidth();
        origNodeHeight = widget.getHeight();
        return true;
    }

    private void handleNodeSelectionClick(NodeWidget widget) {
        if (!screen.ensureEditPermission()) return;
        boolean shift = Screen.hasShiftDown();
        if (shift) {
            screen.toggleSelectNode(widget.getNode().getId());
        } else if (!screen.isNodeSelected(widget.getNode().getId())) {
            screen.selectNode(widget.getNode().getId(), false);
        }
    }

    private void startNodeDrag(NodeWidget widget, double canvasMouseX, double canvasMouseY) {
        draggingNode = widget;
        lastDragCanvasX = canvasMouseX;
        lastDragCanvasY = canvasMouseY;
        dragStartPositions.clear();

        FlowGraph graph = screen.getGraph();
        if (screen.isNodeSelected(widget.getNode().getId())) {
            captureMultiSelectionPositions(graph);
        } else {
            captureSingleNodePositions(widget.getNode(), graph);
        }
    }

    private void captureMultiSelectionPositions(FlowGraph graph) {
        for (String selId : screen.getSelectedNodeIds()) {
            RecipeNode sn = graph.findNodeById(selId);
            if (sn != null) captureSingleNodePositions(sn, graph);
        }
        for (String selNoteId : screen.getSelectedNoteIds()) {
            CanvasStickyNote sn = graph.findStickyNoteById(selNoteId);
            if (sn != null) dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
        }
        for (String selFrameId : screen.getSelectedFrameIds()) {
            CanvasGroupFrame sf = graph.findFrameById(selFrameId);
            if (sf != null) dragStartPositions.put(sf.getId(), new double[]{sf.getPosX(), sf.getPosY()});
        }
    }

    private void captureSingleNodePositions(RecipeNode targetNode, FlowGraph graph) {
        dragStartPositions.put(targetNode.getId(), new double[]{targetNode.getPosX(), targetNode.getPosY()});
        if (targetNode.isCompoundNode()) {
            for (RecipeNode sib : graph.findCompoundSiblingNodes(targetNode.getCompoundGroupId())) {
                dragStartPositions.put(sib.getId(), new double[]{sib.getPosX(), sib.getPosY()});
            }
            CanvasGroupFrame cf = graph.findCompoundFrame(targetNode.getCompoundGroupId());
            if (cf != null) {
                dragStartPositions.put(cf.getId(), new double[]{cf.getPosX(), cf.getPosY()});
            }
        }
    }

    private void commitActiveNodeWidgetEdits() {
        for (NodeWidget w : screen.getNodeWidgets()) {
            w.commitCountEdit();
        }
    }

    private boolean handleQuickAddButtonsClick(double canvasMouseX, double canvasMouseY, int button) {
        if (button != 0 || !hasQuickAddMarker()) return false;

        double markerX = quickAddMarkerHandler.getQuickAddMarkerCanvasX();
        double markerY = quickAddMarkerHandler.getQuickAddMarkerCanvasY();

        boolean inSearchBtn = canvasMouseX >= markerX - 44 && canvasMouseX <= markerX - 24 && canvasMouseY >= markerY - 10 && canvasMouseY <= markerY + 10;
        boolean inJunctionBtn = canvasMouseX >= markerX - 21 && canvasMouseX <= markerX - 1 && canvasMouseY >= markerY - 10 && canvasMouseY <= markerY + 10;
        boolean inFrameBtn = canvasMouseX >= markerX + 2 && canvasMouseX <= markerX + 22 && canvasMouseY >= markerY - 10 && canvasMouseY <= markerY + 10;
        boolean inNoteBtn = canvasMouseX >= markerX + 25 && canvasMouseX <= markerX + 45 && canvasMouseY >= markerY - 10 && canvasMouseY <= markerY + 10;

        if (inSearchBtn) {
            openSearchFromMarker(markerX, markerY);
            return true;
        }
        if (inJunctionBtn) {
            insertJunctionFromMarker(markerX, markerY);
            return true;
        }
        if (inFrameBtn) {
            screen.createFrameAt(markerX, markerY);
            clearQuickAddMarker();
            return true;
        }
        if (inNoteBtn) {
            screen.createNoteAt(markerX, markerY);
            clearQuickAddMarker();
            return true;
        }
        return false;
    }

    private void openSearchFromMarker(double markerX, double markerY) {
        if (hasQuickAddWireContext()) {
            screen.getSearchDialog().openForContextualWire(
                    quickAddMarkerHandler.getQuickAddWireSourceNode(),
                    quickAddMarkerHandler.getQuickAddWirePortIdx(),
                    quickAddMarkerHandler.isQuickAddWireInput(),
                    quickAddMarkerHandler.getQuickAddWireStack(),
                    markerX,
                    markerY,
                    quickAddMarkerHandler.isQuickAddWireShiftAutoRatio()
            );
        } else {
            screen.getSearchDialog().openAt(markerX, markerY);
        }
        clearQuickAddMarker();
    }

    private void insertJunctionFromMarker(double markerX, double markerY) {
        if (screen.ensureEditPermission()) {
            if (hasQuickAddWireContext()) {
                RecipeNode reroute = RecipeNode.createReroute(markerX - 16, markerY - 16);
                if (quickAddMarkerHandler.getQuickAddWireStack() != null) {
                    reroute.bindRerouteIngredient(quickAddMarkerHandler.getQuickAddWireStack());
                }
                screen.getGraph().addNode(reroute);
                if (quickAddMarkerHandler.isQuickAddWireInput()) {
                    screen.getGraph().addConnection(reroute.getId(), 0, quickAddMarkerHandler.getQuickAddWireSourceNode().getId(), quickAddMarkerHandler.getQuickAddWirePortIdx());
                } else {
                    screen.getGraph().addConnection(quickAddMarkerHandler.getQuickAddWireSourceNode().getId(), quickAddMarkerHandler.getQuickAddWirePortIdx(), reroute.getId(), 0);
                }
                screen.recordCommand(new BoardCommand.AddNodesCommand(List.of(reroute), List.of(), "Add Junction Node"));
                screen.rebuildWidgets();
                screen.markSummaryDirty();
                TutorialManager.getInstance().onJunctionInserted();
            } else {
                screen.addRerouteNodeAt(markerX, markerY);
                TutorialManager.getInstance().onJunctionInserted();
            }
        }
        clearQuickAddMarker();
    }

    private boolean handleEmptySpaceClick(double canvasMouseX, double canvasMouseY) {
        if (quickAddMarkerHandler.handleEmptyCanvasClick(canvasMouseX, canvasMouseY, screen)) {
            selectionHandler.stopBoxSelection();
            return true;
        }

        selectionHandler.startBoxSelection(canvasMouseX, canvasMouseY);
        if (!Screen.hasShiftDown()) {
            screen.clearSelection();
        }
        return true;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double canvasMouseX = screen.toCanvasX(mouseX);
        double canvasMouseY = screen.toCanvasY(mouseY);

        for (NodeWidget w : screen.getNodeWidgets()) {
            if (w.isAnyEditorActive() && w.mouseDragged(canvasMouseX, canvasMouseY, button, dragX, dragY)) {
                return true;
            }
        }

        if (selectionHandler.isBoxSelecting() && button == 0) {
            selectionHandler.updateBoxSelection(canvasMouseX, canvasMouseY);
            return true;
        }

        if (panZoomHandler.handleMouseDragged(mouseX, mouseY, button, screen)) {
            return true;
        }

        if (noteHandler.handleMouseDragged(canvasMouseX, canvasMouseY, screen, lastDragCanvasX, lastDragCanvasY, dragStartPositions)) {
            lastDragCanvasX = canvasMouseX;
            lastDragCanvasY = canvasMouseY;
            return true;
        }

        if (frameHandler.handleMouseDragged(canvasMouseX, canvasMouseY, screen, lastDragCanvasX, lastDragCanvasY, dragStartPositions)) {
            lastDragCanvasX = canvasMouseX;
            lastDragCanvasY = canvasMouseY;
            return true;
        }

        if (resizingNode != null && button == 0) {
            double deltaX = canvasMouseX - resizeStartCanvasX;
            double deltaY = canvasMouseY - resizeStartCanvasY;
            int newWidth = (int) Math.max(190, Math.min(500, origNodeWidth + deltaX));
            int newHeight = (int) Math.max(resizingNode.calculateAutoHeight(), Math.min(600, origNodeHeight + deltaY));
            resizingNode.getNode().setCardWidth(newWidth);
            resizingNode.getNode().setCardHeight(newHeight);
            return true;
        }

        if (draggingNode != null && button == 0) {
            applyNodeDrag(canvasMouseX, canvasMouseY);
            return true;
        }

        if (panZoomHandler.isPanning() && (button == 1 || button == 2)) {
            screen.setPanX(screen.getPanX() + dragX);
            screen.setPanY(screen.getPanY() + dragY);
            TutorialManager.getInstance().onPanOrZoom();
            return true;
        }

        return false;
    }

    private void applyNodeDrag(double curCanvasX, double curCanvasY) {
        double deltaX = curCanvasX - lastDragCanvasX;
        double deltaY = curCanvasY - lastDragCanvasY;
        lastDragCanvasX = curCanvasX;
        lastDragCanvasY = curCanvasY;

        FlowGraph graph = screen.getGraph();
        for (String id : dragStartPositions.keySet()) {
            RecipeNode n = graph.findNodeById(id);
            if (n != null) {
                n.setPos(n.getPosX() + deltaX, n.getPosY() + deltaY);
                continue;
            }
            CanvasGroupFrame f = graph.findFrameById(id);
            if (f != null) {
                f.moveBy(deltaX, deltaY);
                continue;
            }
            CanvasStickyNote note = graph.findStickyNoteById(id);
            if (note != null) {
                note.moveBy(deltaX, deltaY);
            }
        }
        screen.markSummaryDirty();
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (wireHandler.handleWireReleased(mouseX, mouseY, button, screen, quickAddMarkerHandler)) {
                return true;
            }

            if (selectionHandler.isBoxSelecting()) {
                finishMarqueeBoxSelection();
                return true;
            }

            if (resizingNode != null) {
                resizingNode = null;
                return true;
            }

            if (noteHandler.handleMouseReleased(screen.toCanvasX(mouseX), screen.toCanvasY(mouseY), button, screen, dragStartPositions)) {
                return true;
            }

            if (frameHandler.handleMouseReleased(screen.toCanvasX(mouseX), screen.toCanvasY(mouseY), button, screen, dragStartPositions)) {
                return true;
            }

            if (draggingNode != null) {
                recordDragMoveCommand();
                draggingNode = null;
                return true;
            }
        }

        if (panZoomHandler.isPanning() && (button == 1 || button == 2)) {
            panZoomHandler.stopPan();
            return true;
        }

        return false;
    }

    private void finishMarqueeBoxSelection() {
        double minX = Math.min(selectionHandler.getBoxSelectStartX(), selectionHandler.getBoxSelectCurX());
        double maxX = Math.max(selectionHandler.getBoxSelectStartX(), selectionHandler.getBoxSelectCurX());
        double minY = Math.min(selectionHandler.getBoxSelectStartY(), selectionHandler.getBoxSelectCurY());
        double maxY = Math.max(selectionHandler.getBoxSelectStartY(), selectionHandler.getBoxSelectCurY());

        if (Math.abs(maxX - minX) > 6 || Math.abs(maxY - minY) > 6) {
            clearQuickAddMarker();
            selectionHandler.finishBoxSelection(screen, Screen.hasShiftDown());
        } else {
            selectionHandler.stopBoxSelection();
            quickAddMarkerHandler.triggerContextualMarker(selectionHandler.getBoxSelectStartX(), selectionHandler.getBoxSelectStartY(), null, -1, false, null, false);
        }
    }

    private void recordDragMoveCommand() {
        if (dragStartPositions.isEmpty()) return;
        Map<String, double[]> nodeDeltas = new HashMap<>();
        Map<String, double[]> noteDeltas = new HashMap<>();
        Map<String, double[]> frameDeltas = new HashMap<>();
        FlowGraph graph = screen.getGraph();

        for (Map.Entry<String, double[]> entry : dragStartPositions.entrySet()) {
            String id = entry.getKey();
            double origX = entry.getValue()[0];
            double origY = entry.getValue()[1];

            RecipeNode n = graph.findNodeById(id);
            if (n != null) {
                double dx = n.getPosX() - origX;
                double dy = n.getPosY() - origY;
                if (Math.abs(dx) > 0.001 || Math.abs(dy) > 0.001) nodeDeltas.put(id, new double[]{dx, dy});
                continue;
            }

            CanvasGroupFrame f = graph.findFrameById(id);
            if (f != null) {
                double dx = f.getPosX() - origX;
                double dy = f.getPosY() - origY;
                if (Math.abs(dx) > 0.001 || Math.abs(dy) > 0.001) frameDeltas.put(id, new double[]{dx, dy});
                continue;
            }

            CanvasStickyNote note = graph.findStickyNoteById(id);
            if (note != null) {
                double dx = note.getPosX() - origX;
                double dy = note.getPosY() - origY;
                if (Math.abs(dx) > 0.001 || Math.abs(dy) > 0.001) noteDeltas.put(id, new double[]{dx, dy});
            }
        }

        if (!nodeDeltas.isEmpty() || !noteDeltas.isEmpty() || !frameDeltas.isEmpty()) {
            screen.recordCommand(new BoardCommand.MoveComponentsCommand(nodeDeltas, noteDeltas, frameDeltas));
        }
        dragStartPositions.clear();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean handled = panZoomHandler.handleMouseScrolled(mouseX, mouseY, delta, screen);
        if (handled) {
            TutorialManager.getInstance().onPanOrZoom();
        }
        return handled;
    }

    public void renderMarquee(GuiGraphics graphics) {
        selectionHandler.renderMarquee(graphics, screen);
    }

    public void renderWireDrag(GuiGraphics graphics, double mouseX, double mouseY) {
        wireHandler.renderWireDrag(graphics, screen, mouseX, mouseY);
    }
}
