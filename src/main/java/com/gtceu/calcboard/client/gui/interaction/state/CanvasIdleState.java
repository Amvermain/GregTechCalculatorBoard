package com.gtceu.calcboard.client.gui.interaction.state;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.SupplyMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/**
 * Default canvas state when idle, listening for click/hover gestures to trigger state transitions.
 */
public final class CanvasIdleState implements CanvasInteractionState {

    public static final String STATE_NAME = "IDLE";

    @Override
    public String getStateName() {
        return STATE_NAME;
    }

    @Override
    public void cancel(CanvasInteractionContext ctx) {
        ctx.getSelectionHandler().stopBoxSelection();
        ctx.getWireHandler().cancelWireDrag();
        ctx.getPanZoomHandler().stopPan();
        ctx.getDragStartPositions().clear();
        ctx.setDraggingNode(null);
        ctx.setResizingNode(null);
        ctx.setPotentialRightClick(false);
    }

    @Override
    public boolean onMouseDown(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        BoardScreen screen = ctx.getScreen();
        if (ctx.getContextMenuManager().isOpen()) {
            if (screen != null && ctx.getContextMenuManager().mouseClicked(screen.toScreenX(canvasX), screen.toScreenY(canvasY), button)) {
                return true;
            }
            ctx.getContextMenuManager().close();
        }

        if (handleHiddenPortsPopupClick(ctx, canvasX, canvasY, button)) {
            return true;
        }

        if (handleNodeWidgetsClick(ctx, canvasX, canvasY, button)) {
            return true;
        }

        if (screen != null && !isPointInsideAnyNode(ctx, canvasX, canvasY)
                && ctx.getWireHandler().handleWireClick(canvasX, canvasY, button, screen)) {
            return true;
        }

        if (screen != null && ctx.getFrameHandler().handleMouseClicked(canvasX, canvasY, button, screen, ctx.getDragStartPositions())) {
            ctx.setLastDragCanvasX(canvasX);
            ctx.setLastDragCanvasY(canvasY);
            ctx.getStateMachine().transitionTo(new CanvasFrameInteractingState());
            return true;
        }

        if (screen != null && ctx.getNoteHandler().handleMouseClicked(canvasX, canvasY, button, screen, ctx.getDragStartPositions())) {
            ctx.setLastDragCanvasX(canvasX);
            ctx.setLastDragCanvasY(canvasY);
            ctx.getStateMachine().transitionTo(new CanvasNoteInteractingState());
            return true;
        }

        commitActiveNodeWidgetEdits(ctx);

        if (handleQuickAddButtonsClick(ctx, canvasX, canvasY, button)) {
            return true;
        }

        if (button == 0) {
            return handleEmptySpaceClick(ctx, canvasX, canvasY);
        }

        if (button == 1 || button == 2) {
            double screenX = screen != null ? screen.toScreenX(canvasX) : canvasX;
            double screenY = screen != null ? screen.toScreenY(canvasY) : canvasY;
            ctx.setRightClickStartMouseX(screenX);
            ctx.setRightClickStartMouseY(screenY);
            ctx.setRightClickStartCanvasX(canvasX);
            ctx.setRightClickStartCanvasY(canvasY);
            ctx.setPotentialRightClick(button == 1);
            ctx.getPanZoomHandler().startPan(screenX, screenY);
            ctx.getStateMachine().transitionTo(new CanvasPanningState());
            return true;
        }

        return false;
    }

    private boolean isPointInsideAnyNode(CanvasInteractionContext ctx, double canvasX, double canvasY) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null) return false;
        for (NodeWidget nw : screen.getNodeWidgets()) {
            if (nw.isPointInside(canvasX, canvasY)) {
                return true;
            }
        }
        return false;
    }

    private boolean handleHiddenPortsPopupClick(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null) return false;
        List<NodeWidget> nodeWidgets = screen.getNodeWidgets();
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            var popup = widget.getHiddenPortsPopup();
            if (popup == null || !popup.isVisible()) continue;

            if (popup.isPointInside(canvasX, canvasY)) {
                return popup.mouseClicked(canvasX, canvasY, button);
            }
            if (button == 0) {
                popup.close();
            }
        }
        return false;
    }

    private boolean handleNodeWidgetsClick(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null) return false;
        List<NodeWidget> nodeWidgets = screen.getNodeWidgets();
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            if (!widget.isPointInside(canvasX, canvasY)) continue;

            if (button == 0) {
                screen.bringNodeToFront(widget.getNode());
            }

            if (ctx.getWireHandler().handlePortClick(widget, canvasX, canvasY, button, screen)) {
                if (ctx.getWireHandler().isDraggingWire()) {
                    ctx.getStateMachine().transitionTo(new CanvasWireConnectingState());
                }
                return true;
            }

            if (button == 0 && widget.isResizeHandleHovered(canvasX, canvasY)) {
                return startNodeResize(ctx, widget, canvasX, canvasY);
            }

            if (button == 0 && widget.checkHeaderDoubleClick(canvasX, canvasY)) {
                return screen.ensureEditPermission();
            }

            if (widget.mouseClicked(canvasX, canvasY, button)) {
                return true;
            }

            if (button == 0) {
                handleNodeSelectionClick(ctx, widget);
            }

            if (widget.isHeaderHovered(canvasX, canvasY) && button == 0 && !widget.getNameEditor().isEditing()) {
                startNodeDrag(ctx, widget, canvasX, canvasY);
                return true;
            }
            if (button == 0) {
                return true;
            }
        }
        return false;
    }

    private boolean startNodeResize(CanvasInteractionContext ctx, NodeWidget widget, double canvasX, double canvasY) {
        BoardScreen screen = ctx.getScreen();
        if (screen != null && !screen.ensureEditPermission()) return true;
        ctx.setResizingNode(widget);
        ctx.setResizeStartCanvasX(canvasX);
        ctx.setResizeStartCanvasY(canvasY);
        ctx.setOrigNodeWidth(widget.getWidth());
        ctx.setOrigNodeHeight(widget.getHeight());
        ctx.getStateMachine().transitionTo(new CanvasNodeResizingState());
        return true;
    }

    private void handleNodeSelectionClick(CanvasInteractionContext ctx, NodeWidget widget) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null || !screen.ensureEditPermission()) return;
        boolean shift = Screen.hasShiftDown();
        if (shift) {
            screen.toggleSelectNode(widget.getNode().getId());
        } else if (!screen.isNodeSelected(widget.getNode().getId())) {
            screen.selectNode(widget.getNode().getId(), false);
        }
    }

    private void startNodeDrag(CanvasInteractionContext ctx, NodeWidget widget, double canvasX, double canvasY) {
        ctx.setDraggingNode(widget);
        ctx.setLastDragCanvasX(canvasX);
        ctx.setLastDragCanvasY(canvasY);
        ctx.setDragStartMouseCanvasX(canvasX);
        ctx.setDragStartMouseCanvasY(canvasY);
        ctx.getDragStartPositions().clear();

        BoardScreen screen = ctx.getScreen();
        if (screen != null) {
            FlowGraph graph = screen.getGraph();
            if (screen.isNodeSelected(widget.getNode().getId())) {
                captureMultiSelectionPositions(ctx, graph, screen);
            } else {
                captureSingleNodePositions(ctx, widget.getNode(), graph);
            }
        }
        ctx.getStateMachine().transitionTo(new CanvasNodeDraggingState());
    }

    private void captureMultiSelectionPositions(CanvasInteractionContext ctx, FlowGraph graph, BoardScreen screen) {
        for (String selId : screen.getSelectedNodeIds()) {
            RecipeNode sn = graph.findNodeById(selId);
            if (sn != null) captureSingleNodePositions(ctx, sn, graph);
        }
        for (String selNoteId : screen.getSelectedNoteIds()) {
            CanvasStickyNote sn = graph.findStickyNoteById(selNoteId);
            if (sn != null) ctx.getDragStartPositions().put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
        }
        for (String selFrameId : screen.getSelectedFrameIds()) {
            CanvasGroupFrame sf = graph.findFrameById(selFrameId);
            if (sf != null) ctx.getDragStartPositions().put(sf.getId(), new double[]{sf.getPosX(), sf.getPosY()});
        }
    }

    private void captureSingleNodePositions(CanvasInteractionContext ctx, RecipeNode targetNode, FlowGraph graph) {
        ctx.getDragStartPositions().put(targetNode.getId(), new double[]{targetNode.getPosX(), targetNode.getPosY()});
        if (!targetNode.isCompoundNode()) return;

        for (RecipeNode sib : graph.findCompoundSiblingNodes(targetNode.getCompoundGroupId())) {
            ctx.getDragStartPositions().put(sib.getId(), new double[]{sib.getPosX(), sib.getPosY()});
        }
        CanvasGroupFrame cf = graph.findCompoundFrame(targetNode.getCompoundGroupId());
        if (cf != null) {
            ctx.getDragStartPositions().put(cf.getId(), new double[]{cf.getPosX(), cf.getPosY()});
        }
    }

    private void commitActiveNodeWidgetEdits(CanvasInteractionContext ctx) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null) return;
        for (NodeWidget w : screen.getNodeWidgets()) {
            w.commitCountEdit();
        }
    }

    private boolean handleQuickAddButtonsClick(CanvasInteractionContext ctx, double canvasX, double canvasY, int button) {
        if (button != 0 || !ctx.getQuickAddMarkerHandler().hasQuickAddMarker()) return false;

        double markerX = ctx.getQuickAddMarkerHandler().getQuickAddMarkerCanvasX();
        double markerY = ctx.getQuickAddMarkerHandler().getQuickAddMarkerCanvasY();

        if (handleQuickAddFlyoutClick(ctx, markerX, markerY, canvasX, canvasY)) {
            return true;
        }

        boolean inSearchBtn = canvasX >= markerX - 44 && canvasX <= markerX - 24 && canvasY >= markerY - 10 && canvasY <= markerY + 10;
        boolean inJunctionBtn = canvasX >= markerX - 21 && canvasX <= markerX - 1 && canvasY >= markerY - 10 && canvasY <= markerY + 10;
        boolean inFrameBtn = canvasX >= markerX + 2 && canvasX <= markerX + 22 && canvasY >= markerY - 10 && canvasY <= markerY + 10;
        boolean inNoteBtn = canvasX >= markerX + 25 && canvasX <= markerX + 45 && canvasY >= markerY - 10 && canvasY <= markerY + 10;

        if (inSearchBtn) {
            openSearchFromMarker(ctx, markerX, markerY);
            return true;
        }
        if (inJunctionBtn) {
            insertJunctionFromMarker(ctx, markerX, markerY);
            return true;
        }
        if (inFrameBtn) {
            if (ctx.getScreen() != null) ctx.getScreen().createFrameAt(markerX, markerY);
            ctx.getQuickAddMarkerHandler().clearQuickAddMarker();
            return true;
        }
        if (inNoteBtn) {
            if (ctx.getScreen() != null) ctx.getScreen().createNoteAt(markerX, markerY);
            ctx.getQuickAddMarkerHandler().clearQuickAddMarker();
            return true;
        }
        return false;
    }

    private boolean handleQuickAddFlyoutClick(CanvasInteractionContext ctx, double markerX, double markerY, double canvasX, double canvasY) {
        var markerHandler = ctx.getQuickAddMarkerHandler();
        if (!markerHandler.hasQuickAddWireContext()) return false;

        boolean inFlyoutColumn = canvasX >= markerX - 21 && canvasX <= markerX - 1;
        if (!inFlyoutColumn) return false;

        boolean inSub1 = canvasY >= markerY - 34 && canvasY <= markerY - 14;
        boolean inSub2 = canvasY >= markerY - 58 && canvasY <= markerY - 38;
        if (!inSub1 && !inSub2) return false;

        RecipeNode srcNode = markerHandler.getQuickAddWireSourceNode();
        int portIdx = markerHandler.getQuickAddWirePortIdx();
        boolean isInput = markerHandler.isQuickAddWireInput();
        FlowGraph graph = ctx.getScreen() != null ? ctx.getScreen().getGraph() : null;

        if (isInput) {
            return handleInputFlyoutClick(ctx, markerX, markerY, inSub1, inSub2, srcNode, portIdx, graph);
        } else {
            return handleOutputFlyoutClick(ctx, markerX, markerY, inSub1, inSub2, srcNode, portIdx, graph);
        }
    }

    private boolean handleOutputFlyoutClick(
            CanvasInteractionContext ctx,
            double markerX,
            double markerY,
            boolean inSub1,
            boolean inSub2,
            RecipeNode srcNode,
            int portIdx,
            FlowGraph graph
    ) {
        FlowGraphSolver.PortFlowStats stats = (graph != null && srcNode != null) ? graph.getOutputPortStats(srcNode, portIdx) : null;
        double surplus = stats != null ? Math.max(0.0, stats.requiredOrProducedRate() - stats.connectedRate()) : 0.0;

        if (surplus > 0.0001) {
            if (inSub1) {
                createAndWireJunction(ctx, markerX, markerY, SupplyMode.FIXED_DRAIN, surplus, false);
                return true;
            }
            if (inSub2) {
                createAndWireJunction(ctx, markerX, markerY, SupplyMode.VOID_SINK, 0.0, false);
                return true;
            }
        } else if (inSub1) {
            createAndWireJunction(ctx, markerX, markerY, SupplyMode.VOID_SINK, 0.0, false);
            return true;
        }
        return false;
    }

    private boolean handleInputFlyoutClick(
            CanvasInteractionContext ctx,
            double markerX,
            double markerY,
            boolean inSub1,
            boolean inSub2,
            RecipeNode srcNode,
            int portIdx,
            FlowGraph graph
    ) {
        FlowGraphSolver.PortFlowStats stats = (graph != null && srcNode != null) ? graph.getInputPortStats(srcNode, portIdx) : null;
        double deficit = stats != null ? Math.max(0.0, stats.requiredOrProducedRate() - stats.connectedRate()) : 0.0;

        if (deficit > 0.0001) {
            if (inSub1) {
                createAndWireJunction(ctx, markerX, markerY, SupplyMode.FIXED_RATE, deficit, true);
                return true;
            }
            if (inSub2) {
                createAndWireJunction(ctx, markerX, markerY, SupplyMode.INFINITE, 0.0, true);
                return true;
            }
        } else if (inSub1) {
            createAndWireJunction(ctx, markerX, markerY, SupplyMode.INFINITE, 0.0, true);
            return true;
        }
        return false;
    }

    private void createAndWireJunction(
            CanvasInteractionContext ctx,
            double markerX,
            double markerY,
            SupplyMode mode,
            double rate,
            boolean isInput
    ) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null || !screen.ensureEditPermission()) return;
        var markerHandler = ctx.getQuickAddMarkerHandler();

        RecipeNode reroute = RecipeNode.createReroute(markerX - 16, markerY - 16);
        if (markerHandler.getQuickAddWireStack() != null) {
            reroute.bindRerouteIngredient(markerHandler.getQuickAddWireStack());
        }
        reroute.setSupplyMode(mode);
        if (mode == SupplyMode.FIXED_RATE) {
            reroute.setExternalSupplyRate(rate);
        } else if (mode == SupplyMode.FIXED_DRAIN) {
            reroute.setExternalDrainRate(rate);
        }

        screen.getGraph().addNode(reroute);
        if (isInput) {
            screen.getGraph().addConnection(reroute.getId(), 0, markerHandler.getQuickAddWireSourceNode().getId(), markerHandler.getQuickAddWirePortIdx());
        } else {
            screen.getGraph().addConnection(markerHandler.getQuickAddWireSourceNode().getId(), markerHandler.getQuickAddWirePortIdx(), reroute.getId(), 0);
        }

        screen.recordCommand(new BoardCommand.AddNodesCommand(List.of(reroute), List.of(), "Add " + mode.name() + " Junction Node"));
        screen.rebuildWidgets();
        screen.markSummaryDirty();
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.gtceu.calcboard.api.event.FlowGraphEvent.JunctionInserted(screen.getGraph(), null, reroute));
        markerHandler.clearQuickAddMarker();
    }

    private void openSearchFromMarker(CanvasInteractionContext ctx, double markerX, double markerY) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null) return;
        var markerHandler = ctx.getQuickAddMarkerHandler();
        if (markerHandler.hasQuickAddWireContext()) {
            screen.getSearchDialog().openForContextualWire(
                    markerHandler.getQuickAddWireSourceNode(),
                    markerHandler.getQuickAddWirePortIdx(),
                    markerHandler.isQuickAddWireInput(),
                    markerHandler.getQuickAddWireStack(),
                    markerX,
                    markerY,
                    markerHandler.isQuickAddWireShiftAutoRatio()
            );
        } else {
            screen.getSearchDialog().openAt(markerX, markerY);
        }
        markerHandler.clearQuickAddMarker();
    }

    private void insertJunctionFromMarker(CanvasInteractionContext ctx, double markerX, double markerY) {
        BoardScreen screen = ctx.getScreen();
        if (screen == null) return;
        var markerHandler = ctx.getQuickAddMarkerHandler();
        if (screen.ensureEditPermission()) {
            if (markerHandler.hasQuickAddWireContext()) {
                RecipeNode reroute = RecipeNode.createReroute(markerX - 16, markerY - 16);
                if (markerHandler.getQuickAddWireStack() != null) {
                    reroute.bindRerouteIngredient(markerHandler.getQuickAddWireStack());
                }
                screen.getGraph().addNode(reroute);
                if (markerHandler.isQuickAddWireInput()) {
                    screen.getGraph().addConnection(reroute.getId(), 0, markerHandler.getQuickAddWireSourceNode().getId(), markerHandler.getQuickAddWirePortIdx());
                } else {
                    screen.getGraph().addConnection(markerHandler.getQuickAddWireSourceNode().getId(), markerHandler.getQuickAddWirePortIdx(), reroute.getId(), 0);
                }
                screen.recordCommand(new BoardCommand.AddNodesCommand(List.of(reroute), List.of(), "Add Junction Node"));
                screen.rebuildWidgets();
                screen.markSummaryDirty();
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.gtceu.calcboard.api.event.FlowGraphEvent.JunctionInserted(screen.getGraph(), null, reroute));
            } else {
                screen.addRerouteNodeAt(markerX, markerY);
            }
        }
        markerHandler.clearQuickAddMarker();
    }

    private boolean handleEmptySpaceClick(CanvasInteractionContext ctx, double canvasX, double canvasY) {
        ctx.getQuickAddMarkerHandler().clearQuickAddMarker();
        ctx.getSelectionHandler().startBoxSelection(canvasX, canvasY);
        if (!Screen.hasShiftDown() && ctx.getScreen() != null) {
            ctx.getScreen().clearSelection();
        }
        ctx.getStateMachine().transitionTo(new CanvasBoxSelectingState());
        return true;
    }
}
