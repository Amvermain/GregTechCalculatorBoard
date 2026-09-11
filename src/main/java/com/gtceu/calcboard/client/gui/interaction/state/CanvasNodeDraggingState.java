package com.gtceu.calcboard.client.gui.interaction.state;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * State active while dragging single or multiple nodes/frames/notes across the canvas.
 */
public final class CanvasNodeDraggingState implements CanvasInteractionState {

    public static final String STATE_NAME = "NODE_DRAGGING";

    @Override
    public String getStateName() {
        return STATE_NAME;
    }

    @Override
    public void onExit(CanvasInteractionContext ctx) {
        ctx.setDraggingNode(null);
    }

    @Override
    public void cancel(CanvasInteractionContext ctx) {
        revertPositionsToStart(ctx);
        ctx.getDragStartPositions().clear();
        ctx.setDraggingNode(null);
        ctx.getStateMachine().returnToIdle();
    }

    @Override
    public boolean onMouseDrag(CanvasInteractionContext ctx, double canvasX, double canvasY, int button, double dx, double dy) {
        if (ctx.getDraggingNode() != null && button == 0) {
            applyNodeDrag(ctx, canvasX, canvasY);
            return true;
        }
        return false;
    }

    @Override
    public boolean onMouseUp(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        if (button == 0 && ctx.getDraggingNode() != null) {
            recordDragMoveCommand(ctx);
            ctx.setDraggingNode(null);
            ctx.getStateMachine().returnToIdle();
            return true;
        }
        return false;
    }

    @Override
    public boolean onKeyPressed(CanvasInteractionContext ctx, int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel(ctx);
            return true;
        }
        return false;
    }

    private void revertPositionsToStart(CanvasInteractionContext ctx) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null) return;
        FlowGraph graph = screen.getGraph();
        for (Map.Entry<String, double[]> entry : ctx.getDragStartPositions().entrySet()) {
            String id = entry.getKey();
            double[] pos = entry.getValue();
            RecipeNode n = graph.findNodeById(id);
            if (n != null) {
                n.setPos(pos[0], pos[1]);
                continue;
            }
            CanvasGroupFrame f = graph.findFrameById(id);
            if (f != null) {
                f.setPos(pos[0], pos[1]);
                continue;
            }
            CanvasStickyNote note = graph.findStickyNoteById(id);
            if (note != null) {
                note.setPos(pos[0], pos[1]);
            }
        }
        if (screen.getWireRenderer() != null) {
            screen.getWireRenderer().markDirty();
        }
    }

    private void applyNodeDrag(CanvasInteractionContext ctx, double curCanvasX, double curCanvasY) {
        boolean isSnap = Screen.hasControlDown() || BoardManager.getInstance().isGridSnapEnabled();
        int gridSize = Math.max(16, BoardManager.getInstance().getGridSnapSize());

        if (isSnap) {
            applySnappedDrag(ctx, curCanvasX, curCanvasY, gridSize);
        } else {
            applySmoothDrag(ctx, curCanvasX, curCanvasY);
        }

        ctx.setLastDragCanvasX(curCanvasX);
        ctx.setLastDragCanvasY(curCanvasY);
        BoardScreen screen = ctx.getScreen();
        if (screen != null && screen.getWireRenderer() != null) {
            screen.getWireRenderer().markDirty();
        }
    }

    private void applySnappedDrag(CanvasInteractionContext ctx, double curCanvasX, double curCanvasY, int gridSize) {
        NodeWidget draggingNode = ctx.getDraggingNode();
        if (draggingNode == null) return;
        String primaryId = draggingNode.getNode().getId();
        double[] primaryStartPos = ctx.getDragStartPositions().computeIfAbsent(
                primaryId, k -> new double[]{draggingNode.getNode().getPosX(), draggingNode.getNode().getPosY()});

        double targetPrimaryX = primaryStartPos[0] + (curCanvasX - ctx.getDragStartMouseCanvasX());
        double targetPrimaryY = primaryStartPos[1] + (curCanvasY - ctx.getDragStartMouseCanvasY());
        double snappedPrimaryX = Math.round(targetPrimaryX / (double) gridSize) * (double) gridSize;
        double snappedPrimaryY = Math.round(targetPrimaryY / (double) gridSize) * (double) gridSize;
        double effectiveDeltaX = snappedPrimaryX - primaryStartPos[0];
        double effectiveDeltaY = snappedPrimaryY - primaryStartPos[1];

        updatePositionsWithDelta(ctx, effectiveDeltaX, effectiveDeltaY, true);
    }

    private void applySmoothDrag(CanvasInteractionContext ctx, double curCanvasX, double curCanvasY) {
        double deltaX = curCanvasX - ctx.getLastDragCanvasX();
        double deltaY = curCanvasY - ctx.getLastDragCanvasY();
        updatePositionsWithDelta(ctx, deltaX, deltaY, false);
    }

    private void updatePositionsWithDelta(CanvasInteractionContext ctx, double deltaX, double deltaY, boolean isAbsoluteDeltaFromStart) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null) return;
        FlowGraph graph = screen.getGraph();

        for (Map.Entry<String, double[]> entry : ctx.getDragStartPositions().entrySet()) {
            String id = entry.getKey();
            double baseX = isAbsoluteDeltaFromStart ? entry.getValue()[0] : 0;
            double baseY = isAbsoluteDeltaFromStart ? entry.getValue()[1] : 0;

            RecipeNode n = graph.findNodeById(id);
            if (n != null) {
                n.setPos(baseX + (isAbsoluteDeltaFromStart ? deltaX : n.getPosX() + deltaX),
                        baseY + (isAbsoluteDeltaFromStart ? deltaY : n.getPosY() + deltaY));
                continue;
            }
            CanvasGroupFrame f = graph.findFrameById(id);
            if (f != null) {
                if (isAbsoluteDeltaFromStart) f.setPos(baseX + deltaX, baseY + deltaY);
                else f.moveBy(deltaX, deltaY);
                continue;
            }
            CanvasStickyNote note = graph.findStickyNoteById(id);
            if (note != null) {
                if (isAbsoluteDeltaFromStart) note.setPos(baseX + deltaX, baseY + deltaY);
                else note.moveBy(deltaX, deltaY);
            }
        }
    }

    private void recordDragMoveCommand(CanvasInteractionContext ctx) {
        if (ctx.getDragStartPositions().isEmpty()) return;
        Map<String, double[]> nodeDeltas = new HashMap<>();
        Map<String, double[]> noteDeltas = new HashMap<>();
        Map<String, double[]> frameDeltas = new HashMap<>();
        BoardScreen screen = ctx.getScreen();
        if (screen == null) return;
        FlowGraph graph = screen.getGraph();

        for (Map.Entry<String, double[]> entry : ctx.getDragStartPositions().entrySet()) {
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
        ctx.getDragStartPositions().clear();
    }
}
