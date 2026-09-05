package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.CanvasStickyNoteRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CanvasNoteInteractionHandler {

    private CanvasStickyNote draggingNote = null;
    private CanvasStickyNote resizingNote = null;
    private double resizeNoteStartX, resizeNoteStartY;
    private double origNoteWidth, origNoteHeight;
    private double dragStartMouseCanvasX, dragStartMouseCanvasY;
    private long lastNoteHeaderClickTime = 0;
    private CanvasStickyNote lastClickedNote = null;

    public CanvasStickyNote getDraggingNote() {
        return draggingNote;
    }

    public CanvasStickyNote getResizingNote() {
        return resizingNote;
    }

    public boolean isInteracting() {
        return draggingNote != null || resizingNote != null;
    }

    public void cancel() {
        draggingNote = null;
        resizingNote = null;
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

        for (int i = graph.getStickyNotes().size() - 1; i >= 0; i--) {
            CanvasStickyNote note = graph.getStickyNotes().get(i);
            CanvasStickyNoteRenderer.NoteAction action = CanvasStickyNoteRenderer.getClickedAction(note, canvasMouseX, canvasMouseY);

            if (action != CanvasStickyNoteRenderer.NoteAction.NONE) {
                return executeNoteAction(action, note, canvasMouseX, canvasMouseY, button, screen);
            }

            if (note.isPointInside(canvasMouseX, canvasMouseY) && button == 0) {
                return handleNoteBodyClick(note, canvasMouseX, canvasMouseY, screen, dragStartPositions);
            }
        }
        return false;
    }

    private boolean executeNoteAction(
            CanvasStickyNoteRenderer.NoteAction action,
            CanvasStickyNote note,
            double canvasMouseX,
            double canvasMouseY,
            int button,
            BoardScreen screen
    ) {
        if (!screen.ensureEditPermission()) return true;

        if (action == CanvasStickyNoteRenderer.NoteAction.RESIZE && button == 0) {
            resizingNote = note;
            resizeNoteStartX = canvasMouseX;
            resizeNoteStartY = canvasMouseY;
            origNoteWidth = note.getWidth();
            origNoteHeight = note.getHeight();
            return true;
        }
        if (action == CanvasStickyNoteRenderer.NoteAction.DELETE && button == 0) {
            screen.getGraph().removeStickyNote(note);
            screen.recordCommand(new BoardCommand.RemoveStickyNotesCommand(List.of(note), "Delete sticky note"));
            screen.markSummaryDirty();
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
            );
            return true;
        }
        if (action == CanvasStickyNoteRenderer.NoteAction.COLOR && button == 0) {
            int oldColor = note.getColor();
            note.cycleColor();
            screen.recordCommand(new BoardCommand.ModifyNotePropertiesCommand(
                    note.getId(),
                    note.getTitle(), note.getTitle(),
                    note.getContent(), note.getContent(),
                    oldColor, note.getColor()
            ));
            screen.markSummaryDirty();
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
            );
            return true;
        }
        return false;
    }

    private boolean handleNoteBodyClick(
            CanvasStickyNote note,
            double canvasMouseX,
            double canvasMouseY,
            BoardScreen screen,
            Map<String, double[]> dragStartPositions
    ) {
        long now = System.currentTimeMillis();
        if (now - lastNoteHeaderClickTime < 350 && note.equals(lastClickedNote)) {
            screen.openNoteEditDialog(note);
            lastNoteHeaderClickTime = 0;
            lastClickedNote = null;
            return true;
        }
        lastNoteHeaderClickTime = now;
        lastClickedNote = note;

        boolean shift = Screen.hasShiftDown();
        if (shift) {
            screen.toggleSelectNote(note.getId());
        } else if (!screen.isNoteSelected(note.getId())) {
            screen.selectNote(note.getId(), false);
        }

        if (note.isPointInHeader(canvasMouseX, canvasMouseY)) {
            startNoteDrag(note, canvasMouseX, canvasMouseY, screen, dragStartPositions);
        }
        return true;
    }

    private void startNoteDrag(
            CanvasStickyNote note,
            double canvasMouseX,
            double canvasMouseY,
            BoardScreen screen,
            Map<String, double[]> dragStartPositions
    ) {
        draggingNote = note;
        dragStartPositions.clear();
        dragStartMouseCanvasX = canvasMouseX;
        dragStartMouseCanvasY = canvasMouseY;

        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        if (screen.isNoteSelected(note.getId())) {
            captureSelectionPositions(graph, screen, dragStartPositions);
        } else {
            dragStartPositions.put(note.getId(), new double[]{note.getPosX(), note.getPosY()});
        }
    }

    private void captureSelectionPositions(
            FlowGraph graph,
            BoardScreen screen,
            Map<String, double[]> dragStartPositions
    ) {
        for (String selId : screen.getSelectedNodeIds()) {
            RecipeNode sn = graph.findNodeById(selId);
            if (sn != null) dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
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

    public boolean handleMouseDragged(
            double canvasMouseX,
            double canvasMouseY,
            BoardScreen screen,
            double lastDragCanvasX,
            double lastDragCanvasY,
            Map<String, double[]> dragStartPositions
    ) {
        if (resizingNote != null) {
            double rawDeltaX = canvasMouseX - resizeNoteStartX;
            double rawDeltaY = canvasMouseY - resizeNoteStartY;
            boolean isSnap = net.minecraft.client.gui.screens.Screen.hasControlDown() || com.gtceu.calcboard.api.storage.BoardManager.getInstance().isGridSnapEnabled();
            int gridSize = com.gtceu.calcboard.api.storage.BoardManager.getInstance().getGridSnapSize();
            if (gridSize <= 0) gridSize = 16;

            double targetRightX = resizingNote.getPosX() + origNoteWidth + rawDeltaX;
            double targetBottomY = resizingNote.getPosY() + origNoteHeight + rawDeltaY;

            if (isSnap) {
                targetRightX = Math.round(targetRightX / (double) gridSize) * (double) gridSize;
                targetBottomY = Math.round(targetBottomY / (double) gridSize) * (double) gridSize;
            }
            resizingNote.setWidth(Math.max(100, targetRightX - resizingNote.getPosX()));
            resizingNote.setHeight(Math.max(60, targetBottomY - resizingNote.getPosY()));
            return true;
        }

        if (draggingNote != null) {
            applyNoteDrag(canvasMouseX, canvasMouseY, lastDragCanvasX, lastDragCanvasY, screen, dragStartPositions);
            return true;
        }
        return false;
    }

    private void applyNoteDrag(
            double curCanvasX,
            double curCanvasY,
            double lastDragCanvasX,
            double lastDragCanvasY,
            BoardScreen screen,
            Map<String, double[]> dragStartPositions
    ) {
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        boolean isSnap = net.minecraft.client.gui.screens.Screen.hasControlDown() || com.gtceu.calcboard.api.storage.BoardManager.getInstance().isGridSnapEnabled();
        int gridSize = com.gtceu.calcboard.api.storage.BoardManager.getInstance().getGridSnapSize();
        if (gridSize <= 0) gridSize = 16;

        if (isSnap) {
            String primaryId = draggingNote.getId();
            double[] primaryStartPos = dragStartPositions.get(primaryId);
            if (primaryStartPos == null) {
                primaryStartPos = new double[]{draggingNote.getPosX(), draggingNote.getPosY()};
            }
            double targetPrimaryX = primaryStartPos[0] + (curCanvasX - dragStartMouseCanvasX);
            double targetPrimaryY = primaryStartPos[1] + (curCanvasY - dragStartMouseCanvasY);
            double snappedPrimaryX = Math.round(targetPrimaryX / (double) gridSize) * (double) gridSize;
            double snappedPrimaryY = Math.round(targetPrimaryY / (double) gridSize) * (double) gridSize;
            double effectiveDeltaX = snappedPrimaryX - primaryStartPos[0];
            double effectiveDeltaY = snappedPrimaryY - primaryStartPos[1];

            for (Map.Entry<String, double[]> entry : dragStartPositions.entrySet()) {
                String id = entry.getKey();
                double[] startPos = entry.getValue();
                CanvasStickyNote note = graph.findStickyNoteById(id);
                if (note != null) {
                    note.setPos(startPos[0] + effectiveDeltaX, startPos[1] + effectiveDeltaY);
                    continue;
                }
                RecipeNode n = graph.findNodeById(id);
                if (n != null) {
                    n.setPos(startPos[0] + effectiveDeltaX, startPos[1] + effectiveDeltaY);
                    continue;
                }
                CanvasGroupFrame f = graph.findFrameById(id);
                if (f != null) {
                    f.setPos(startPos[0] + effectiveDeltaX, startPos[1] + effectiveDeltaY);
                }
            }
        } else {
            double dx = curCanvasX - lastDragCanvasX;
            double dy = curCanvasY - lastDragCanvasY;
            for (String id : dragStartPositions.keySet()) {
                CanvasStickyNote note = graph.findStickyNoteById(id);
                if (note != null) {
                    note.setPos(note.getPosX() + dx, note.getPosY() + dy);
                    continue;
                }
                RecipeNode n = graph.findNodeById(id);
                if (n != null) {
                    n.setPos(n.getPosX() + dx, n.getPosY() + dy);
                    continue;
                }
                CanvasGroupFrame f = graph.findFrameById(id);
                if (f != null) {
                    f.setPos(f.getPosX() + dx, f.getPosY() + dy);
                }
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
        if (resizingNote != null && button == 0) {
            double newW = resizingNote.getWidth();
            double newH = resizingNote.getHeight();
            if (Math.abs(origNoteWidth - newW) > 0.1 || Math.abs(origNoteHeight - newH) > 0.1) {
                screen.recordCommand(new BoardCommand.ResizeStickyNoteCommand(
                        resizingNote.getId(), origNoteWidth, origNoteHeight, newW, newH, "Resize sticky note"
                ));
            }
            resizingNote = null;
            screen.markSummaryDirty();
            return true;
        }

        if (draggingNote != null && button == 0) {
            recordDragCommand(screen, dragStartPositions);
            draggingNote = null;
            return true;
        }
        return false;
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

            CanvasStickyNote note = graph.findStickyNoteById(id);
            if (note != null) {
                double dx = note.getPosX() - origX;
                double dy = note.getPosY() - origY;
                if (Math.abs(dx) > 0.001 || Math.abs(dy) > 0.001) noteDeltas.put(id, new double[]{dx, dy});
                continue;
            }

            CanvasGroupFrame f = graph.findFrameById(id);
            if (f != null) {
                double dx = f.getPosX() - origX;
                double dy = f.getPosY() - origY;
                if (Math.abs(dx) > 0.001 || Math.abs(dy) > 0.001) frameDeltas.put(id, new double[]{dx, dy});
            }
        }

        if (!nodeDeltas.isEmpty() || !noteDeltas.isEmpty() || !frameDeltas.isEmpty()) {
            screen.recordCommand(new BoardCommand.MoveComponentsCommand(nodeDeltas, noteDeltas, frameDeltas));
            screen.markSummaryDirty();
        }
        dragStartPositions.clear();
    }
}
