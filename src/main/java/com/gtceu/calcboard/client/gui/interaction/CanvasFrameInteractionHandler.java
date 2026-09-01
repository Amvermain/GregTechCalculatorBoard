package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CanvasFrameInteractionHandler {

    private CanvasGroupFrame draggingFrame = null;
    private CanvasGroupFrame resizingFrame = null;
    private CanvasGroupFrameRenderer.ResizeDirection resizeFrameDir = CanvasGroupFrameRenderer.ResizeDirection.NONE;
    private double resizeFrameStartX, resizeFrameStartY;
    private double origFrameX, origFrameY;
    private double origFrameWidth, origFrameHeight;
    private long lastFrameHeaderClickTime = 0;
    private CanvasGroupFrame lastClickedFrame = null;

    public CanvasGroupFrame getDraggingFrame() {
        return draggingFrame;
    }

    public CanvasGroupFrame getResizingFrame() {
        return resizingFrame;
    }

    public boolean isInteracting() {
        return draggingFrame != null || resizingFrame != null;
    }

    public void cancel() {
        draggingFrame = null;
        resizingFrame = null;
        resizeFrameDir = CanvasGroupFrameRenderer.ResizeDirection.NONE;
    }

    public boolean handleMouseClicked(
            double canvasMouseX,
            double canvasMouseY,
            int button,
            BoardScreen screen,
            Map<String, double[]> dragStartPositions
    ) {
        FlowGraph graph = screen.getGraph();
        if (graph == null) return false;

        for (int i = graph.getFrames().size() - 1; i >= 0; i--) {
            CanvasGroupFrame frame = graph.getFrames().get(i);
            CanvasGroupFrameRenderer.FrameAction action = CanvasGroupFrameRenderer.getClickedAction(frame, canvasMouseX, canvasMouseY);

            if (action != CanvasGroupFrameRenderer.FrameAction.NONE) {
                return executeFrameAction(action, frame, canvasMouseX, canvasMouseY, button, screen);
            }

            if (frame.isPointInHeader(canvasMouseX, canvasMouseY) && button == 0) {
                return handleFrameHeaderClick(frame, canvasMouseX, canvasMouseY, screen, dragStartPositions);
            }
        }
        return false;
    }

    private boolean executeFrameAction(
            CanvasGroupFrameRenderer.FrameAction action,
            CanvasGroupFrame frame,
            double canvasMouseX,
            double canvasMouseY,
            int button,
            BoardScreen screen
    ) {
        if (!screen.ensureEditPermission()) return true;

        if (action == CanvasGroupFrameRenderer.FrameAction.RESIZE && button == 0) {
            return startFrameResize(frame, canvasMouseX, canvasMouseY);
        }
        if (action == CanvasGroupFrameRenderer.FrameAction.DELETE && button == 0) {
            return deleteFrame(frame, screen);
        }
        if (action == CanvasGroupFrameRenderer.FrameAction.COLOR && button == 0) {
            frame.cycleColor();
            screen.markSummaryDirty();
            return true;
        }
        if (action == CanvasGroupFrameRenderer.FrameAction.COLLAPSE && button == 0) {
            screen.collapseFrameIntoModule(frame);
            return true;
        }
        if (action == CanvasGroupFrameRenderer.FrameAction.AUTOFIT && button == 0) {
            return autoFitFrame(frame, screen);
        }
        if (action == CanvasGroupFrameRenderer.FrameAction.CONFIG && button == 0) {
            screen.openSharedFrameConfigDialog(frame);
            return true;
        }
        return false;
    }

    private boolean startFrameResize(CanvasGroupFrame frame, double canvasMouseX, double canvasMouseY) {
        CanvasGroupFrameRenderer.ResizeDirection dir = CanvasGroupFrameRenderer.getResizeDirection(frame, canvasMouseX, canvasMouseY);
        if (dir == CanvasGroupFrameRenderer.ResizeDirection.NONE) return false;

        resizingFrame = frame;
        resizeFrameDir = dir;
        resizeFrameStartX = canvasMouseX;
        resizeFrameStartY = canvasMouseY;
        origFrameX = frame.getPosX();
        origFrameY = frame.getPosY();
        origFrameWidth = frame.getWidth();
        origFrameHeight = frame.getHeight();
        return true;
    }

    private boolean deleteFrame(CanvasGroupFrame frame, BoardScreen screen) {
        FlowGraph graph = screen.getGraph();
        if (graph == null) return false;

        if (frame.isCompoundFrame()) {
            deleteCompoundFrame(frame, graph, screen);
        } else {
            graph.removeFrame(frame);
            screen.recordCommand(new BoardCommand.RemoveFramesCommand(List.of(frame), "Delete frame " + frame.getTitle()));
        }

        screen.rebuildWidgets();
        screen.markSummaryDirty();
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 0.9F)
        );
        return true;
    }

    private void deleteCompoundFrame(CanvasGroupFrame frame, FlowGraph graph, BoardScreen screen) {
        List<RecipeNode> siblings = graph.findCompoundSiblingNodes(frame.getCompoundGroupId());
        List<FlowGraph.ConnectionEdge> removedEdges = new ArrayList<>();
        Set<String> siblingIds = new HashSet<>();
        for (RecipeNode s : siblings) siblingIds.add(s.getId());
        for (FlowGraph.ConnectionEdge e : graph.getConnections()) {
            if (siblingIds.contains(e.fromNodeId()) || siblingIds.contains(e.toNodeId())) {
                removedEdges.add(e);
            }
        }
        graph.deleteCompoundGroup(frame.getCompoundGroupId());
        List<BoardCommand> cmds = new ArrayList<>();
        if (!siblings.isEmpty() || !removedEdges.isEmpty()) {
            cmds.add(new BoardCommand.RemoveNodesCommand(siblings, removedEdges, "Delete compound " + frame.getTitle()));
        }
        cmds.add(new BoardCommand.RemoveFramesCommand(List.of(frame), "Delete compound frame"));
        screen.recordCommand(new BoardCommand.CompoundCommand(cmds, "Delete " + frame.getTitle()));
    }

    private boolean autoFitFrame(CanvasGroupFrame frame, BoardScreen screen) {
        FlowGraph graph = screen.getGraph();
        if (graph == null) return false;

        double oldX = frame.getPosX();
        double oldY = frame.getPosY();
        double oldW = frame.getWidth();
        double oldH = frame.getHeight();
        boolean fitted = frame.autoFit(graph, CanvasGroupFrame.DEFAULT_PADDING);
        if (fitted) {
            screen.recordCommand(new BoardCommand.ResizeFrameCommand(
                    frame.getId(), oldX, oldY, oldW, oldH, frame.getPosX(), frame.getPosY(), frame.getWidth(), frame.getHeight(), "Auto-fit frame " + frame.getTitle()
            ));
            screen.markSummaryDirty();
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
            );
        }
        return true;
    }

    private boolean handleFrameHeaderClick(
            CanvasGroupFrame frame,
            double canvasMouseX,
            double canvasMouseY,
            BoardScreen screen,
            Map<String, double[]> dragStartPositions
    ) {
        long now = System.currentTimeMillis();
        if (now - lastFrameHeaderClickTime < 350 && frame.equals(lastClickedFrame)) {
            screen.openFrameEditDialog(frame);
            lastFrameHeaderClickTime = 0;
            lastClickedFrame = null;
            return true;
        }
        lastFrameHeaderClickTime = now;
        lastClickedFrame = frame;

        boolean isMultiSelect = Screen.hasShiftDown() || Screen.hasControlDown();
        if (isMultiSelect) {
            screen.toggleSelectFrame(frame.getId());
        } else if (!screen.isFrameSelected(frame.getId())) {
            screen.selectFrame(frame.getId(), false);
        }

        startFrameDrag(frame, canvasMouseX, canvasMouseY, screen, dragStartPositions);
        return true;
    }

    private void startFrameDrag(
            CanvasGroupFrame frame,
            double canvasMouseX,
            double canvasMouseY,
            BoardScreen screen,
            Map<String, double[]> dragStartPositions
    ) {
        draggingFrame = frame;
        dragStartPositions.clear();

        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        Set<String> selFrames = screen.getSelectedFrameIds();
        if (selFrames.contains(frame.getId())) {
            captureMultiSelectionPositions(graph, screen, dragStartPositions);
        } else {
            captureSingleFramePositions(frame, graph, dragStartPositions);
        }
    }

    private void captureMultiSelectionPositions(
            FlowGraph graph,
            BoardScreen screen,
            Map<String, double[]> dragStartPositions
    ) {
        for (String fid : screen.getSelectedFrameIds()) {
            CanvasGroupFrame f = graph.findFrameById(fid);
            if (f != null) captureSingleFramePositions(f, graph, dragStartPositions);
        }
        for (String nid : screen.getSelectedNodeIds()) {
            RecipeNode n = graph.findNodeById(nid);
            if (n != null) {
                dragStartPositions.put(n.getId(), new double[]{n.getPosX(), n.getPosY()});
                if (n.isCompoundNode()) {
                    for (RecipeNode sib : graph.findCompoundSiblingNodes(n.getCompoundGroupId())) {
                        dragStartPositions.put(sib.getId(), new double[]{sib.getPosX(), sib.getPosY()});
                    }
                    CanvasGroupFrame cf = graph.findCompoundFrame(n.getCompoundGroupId());
                    if (cf != null) dragStartPositions.put(cf.getId(), new double[]{cf.getPosX(), cf.getPosY()});
                }
            }
        }
        for (String noteId : screen.getSelectedNoteIds()) {
            CanvasStickyNote note = graph.findStickyNoteById(noteId);
            if (note != null) {
                dragStartPositions.put(note.getId(), new double[]{note.getPosX(), note.getPosY()});
            }
        }
    }

    private void captureSingleFramePositions(
            CanvasGroupFrame frame,
            FlowGraph graph,
            Map<String, double[]> dragStartPositions
    ) {
        dragStartPositions.put(frame.getId(), new double[]{frame.getPosX(), frame.getPosY()});
        if (frame.isCompoundFrame()) {
            for (RecipeNode sn : graph.findCompoundSiblingNodes(frame.getCompoundGroupId())) {
                dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
            }
        } else {
            for (RecipeNode sn : frame.getEnclosedNodes(graph)) {
                if (!sn.isCompoundNode()) {
                    dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
                }
            }
            for (CanvasStickyNote sn : frame.getEnclosedNotes(graph)) {
                dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
            }
        }
    }

    public boolean handleMouseDragged(
            double canvasMouseX,
            double canvasMouseY,
            BoardScreen screen,
            double lastDragCanvasX,
            double lastDragCanvasY,
            Map<String, double[]> dragStartPositions
    ) {
        if (resizingFrame != null) {
            applyFrameResize(canvasMouseX, canvasMouseY);
            return true;
        }

        if (draggingFrame != null) {
            applyFrameDrag(canvasMouseX - lastDragCanvasX, canvasMouseY - lastDragCanvasY, screen, dragStartPositions);
            return true;
        }
        return false;
    }

    private void applyFrameResize(double canvasMouseX, double canvasMouseY) {
        double deltaX = canvasMouseX - resizeFrameStartX;
        double deltaY = canvasMouseY - resizeFrameStartY;
        double minWidth = CanvasGroupFrame.MIN_WIDTH;
        double minHeight = CanvasGroupFrame.MIN_HEIGHT;

        switch (resizeFrameDir) {
            case EAST, NORTH_EAST, SOUTH_EAST -> resizingFrame.setWidth(Math.max(minWidth, origFrameWidth + deltaX));
            case WEST, NORTH_WEST, SOUTH_WEST -> {
                double clampedDeltaX = Math.min(deltaX, origFrameWidth - minWidth);
                resizingFrame.setPosX(origFrameX + clampedDeltaX);
                resizingFrame.setWidth(origFrameWidth - clampedDeltaX);
            }
            default -> {}
        }

        switch (resizeFrameDir) {
            case SOUTH, SOUTH_WEST, SOUTH_EAST -> resizingFrame.setHeight(Math.max(minHeight, origFrameHeight + deltaY));
            case NORTH, NORTH_WEST, NORTH_EAST -> {
                double clampedDeltaY = Math.min(deltaY, origFrameHeight - minHeight);
                resizingFrame.setPosY(origFrameY + clampedDeltaY);
                resizingFrame.setHeight(origFrameHeight - clampedDeltaY);
            }
            default -> {}
        }
    }

    private void applyFrameDrag(
            double dx,
            double dy,
            BoardScreen screen,
            Map<String, double[]> dragStartPositions
    ) {
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        for (String id : dragStartPositions.keySet()) {
            CanvasGroupFrame f = graph.findFrameById(id);
            if (f != null) {
                f.setPosX(f.getPosX() + dx);
                f.setPosY(f.getPosY() + dy);
                continue;
            }
            RecipeNode n = graph.findNodeById(id);
            if (n != null) {
                n.setPosX(n.getPosX() + dx);
                n.setPosY(n.getPosY() + dy);
                continue;
            }
            CanvasStickyNote note = graph.findStickyNoteById(id);
            if (note != null) {
                note.setPosX(note.getPosX() + dx);
                note.setPosY(note.getPosY() + dy);
            }
        }
    }

    public boolean handleMouseReleased(
            double canvasMouseX,
            double canvasMouseY,
            int button,
            BoardScreen screen,
            Map<String, double[]> dragStartPositions
    ) {
        if (resizingFrame != null && button == 0) {
            recordResizeCommand(screen);
            resizingFrame = null;
            resizeFrameDir = CanvasGroupFrameRenderer.ResizeDirection.NONE;
            return true;
        }

        if (draggingFrame != null && button == 0) {
            recordDragCommand(screen, dragStartPositions);
            draggingFrame = null;
            return true;
        }
        return false;
    }

    private void recordResizeCommand(BoardScreen screen) {
        if (origFrameX != resizingFrame.getPosX() || origFrameY != resizingFrame.getPosY() ||
                origFrameWidth != resizingFrame.getWidth() || origFrameHeight != resizingFrame.getHeight()) {
            screen.recordCommand(new BoardCommand.ResizeFrameCommand(
                    resizingFrame.getId(),
                    origFrameX, origFrameY, origFrameWidth, origFrameHeight,
                    resizingFrame.getPosX(), resizingFrame.getPosY(), resizingFrame.getWidth(), resizingFrame.getHeight(),
                    "Resize frame " + resizingFrame.getTitle()
            ));
            screen.markSummaryDirty();
        }
    }

    private void recordDragCommand(BoardScreen screen, Map<String, double[]> dragStartPositions) {
        FlowGraph graph = screen.getGraph();
        if (graph == null || dragStartPositions.isEmpty()) return;

        Map<String, double[]> nodeDeltas = new java.util.HashMap<>();
        Map<String, double[]> noteDeltas = new java.util.HashMap<>();
        Map<String, double[]> frameDeltas = new java.util.HashMap<>();

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
            screen.markSummaryDirty();
        }
        dragStartPositions.clear();
    }
}
