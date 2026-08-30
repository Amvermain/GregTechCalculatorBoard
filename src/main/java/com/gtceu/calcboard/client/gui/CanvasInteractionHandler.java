package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.client.gui.interaction.CanvasPanZoomHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasQuickAddMarkerHandler;
import com.gtceu.calcboard.client.gui.interaction.CanvasSelectionHandler;
import com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer;
import com.gtceu.calcboard.client.gui.render.CanvasStickyNoteRenderer;
import com.gtceu.calcboard.client.gui.render.ConnectionRenderer;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Handles canvas panning, zooming, node dragging, bidirectional wire connections, and line cut interactions.
 */
public class CanvasInteractionHandler {
    private final BoardScreen screen;

    // Specialized Sub-Handlers
    private final CanvasPanZoomHandler panZoomHandler = new CanvasPanZoomHandler();
    private final CanvasSelectionHandler selectionHandler = new CanvasSelectionHandler();
    private final CanvasQuickAddMarkerHandler quickAddMarkerHandler = new CanvasQuickAddMarkerHandler();

    // Node Dragging State
    private NodeWidget draggingNode = null;
    private double lastDragCanvasX, lastDragCanvasY;

    // Node Resizing State
    private NodeWidget resizingNode = null;
    private double resizeStartCanvasX, resizeStartCanvasY;
    private int origNodeWidth, origNodeHeight;

    // Wire Connection Dragging State
    private NodeWidget wireStartNode = null;
    private int wireStartPortIdx = -1;
    private boolean wireStartIsInput = false;

    // Frame Dragging & Resizing State (RFC-002)
    private com.gtceu.calcboard.api.model.CanvasGroupFrame draggingFrame = null;
    private com.gtceu.calcboard.api.model.CanvasGroupFrame resizingFrame = null;
    private CanvasGroupFrameRenderer.ResizeDirection resizeFrameDir = CanvasGroupFrameRenderer.ResizeDirection.NONE;
    private double resizeFrameStartX, resizeFrameStartY;
    private double origFrameX, origFrameY;
    private double origFrameWidth, origFrameHeight;
    private long lastFrameHeaderClickTime = 0;
    private com.gtceu.calcboard.api.model.CanvasGroupFrame lastClickedFrame = null;

    // Sticky Note Dragging & Resizing State
    private com.gtceu.calcboard.api.model.CanvasStickyNote draggingNote = null;
    private com.gtceu.calcboard.api.model.CanvasStickyNote resizingNote = null;
    private double resizeNoteStartX, resizeNoteStartY;
    private double origNoteWidth, origNoteHeight;
    private long lastNoteHeaderClickTime = 0;
    private com.gtceu.calcboard.api.model.CanvasStickyNote lastClickedNote = null;

    // Wire Double-Click Split State (RFC-001)
    private long lastWireClickTime = 0;
    private FlowGraph.ConnectionEdge lastClickedEdge = null;

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

    public NodeWidget getWireStartNode() {
        return wireStartNode;
    }

    public int getWireStartPortIdx() {
        return wireStartPortIdx;
    }

    public boolean isWireStartInput() {
        return wireStartIsInput;
    }

    public boolean isDraggingWire() {
        return wireStartNode != null;
    }

    public boolean isPanning() {
        return panZoomHandler.isPanning();
    }

    public void cancelWireDrag() {
        this.wireStartNode = null;
        this.wireStartPortIdx = -1;
        this.wireStartIsInput = false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (wireStartNode != null && button == 1) {
            cancelWireDrag();
            return true;
        }

        double canvasMouseX = screen.toCanvasX(mouseX);
        double canvasMouseY = screen.toCanvasY(mouseY);
        List<NodeWidget> nodeWidgets = screen.getNodeWidgets();
        FlowGraph graph = screen.getGraph();

        // 1. Check Node Widgets from top to bottom
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            if (widget.isPointInside(canvasMouseX, canvasMouseY)) {
                if (button == 0) {
                    screen.bringNodeToFront(widget.getNode());
                }

                // Output Port: Left click to start forward wire, Right click to disconnect
                int outPortIdx = widget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
                if (outPortIdx >= 0) {
                    if (!screen.ensureEditPermission()) return true;
                    if (button == 0) {
                        wireStartNode = widget;
                        wireStartPortIdx = outPortIdx;
                        wireStartIsInput = false;
                        return true;
                    } else if (button == 1) {
                        List<FlowGraph.ConnectionEdge> toRemove = new ArrayList<>();
                        for (FlowGraph.ConnectionEdge e : graph.getConnections()) {
                            if (e.fromNodeId().equals(widget.getNode().getId()) && e.outputIndex() == outPortIdx) {
                                toRemove.add(e);
                            }
                        }
                        if (!toRemove.isEmpty()) {
                            graph.getConnections().removeAll(toRemove);
                            for (FlowGraph.ConnectionEdge e : toRemove) {
                                screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.DisconnectWireCommand(e));
                            }
                            notifyDisconnect("message.gtcalcboard.disconnect_out");
                        }
                        return true;
                    }
                }

                // Input Port: Left click to start reverse wire, Right click to disconnect
                int inPortIdx = widget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
                if (inPortIdx >= 0) {
                    if (!screen.ensureEditPermission()) return true;
                    if (button == 0) {
                        wireStartNode = widget;
                        wireStartPortIdx = inPortIdx;
                        wireStartIsInput = true;
                        return true;
                    } else if (button == 1) {
                        List<FlowGraph.ConnectionEdge> toRemove = new ArrayList<>();
                        for (FlowGraph.ConnectionEdge e : graph.getConnections()) {
                            if (e.toNodeId().equals(widget.getNode().getId()) && e.inputIndex() == inPortIdx) {
                                toRemove.add(e);
                            }
                        }
                        if (!toRemove.isEmpty()) {
                            graph.getConnections().removeAll(toRemove);
                            for (FlowGraph.ConnectionEdge e : toRemove) {
                                screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.DisconnectWireCommand(e));
                            }
                            notifyDisconnect("message.gtcalcboard.disconnect_in");
                        }
                        return true;
                    }
                }

                // Resize Handle Dragging
                if (button == 0 && widget.isResizeHandleHovered(canvasMouseX, canvasMouseY)) {
                    if (!screen.ensureEditPermission()) return true;
                    resizingNode = widget;
                    resizeStartCanvasX = canvasMouseX;
                    resizeStartCanvasY = canvasMouseY;
                    origNodeWidth = widget.getWidth();
                    origNodeHeight = widget.getHeight();
                    return true;
                }

                // Header Double Click -> Start inline title renaming
                if (button == 0 && widget.checkHeaderDoubleClick(canvasMouseX, canvasMouseY)) {
                    if (!screen.ensureEditPermission()) return true;
                    return true;
                }

                // Widget internal controls (Count, Tier, Base button, Close button)
                if (widget.mouseClicked(canvasMouseX, canvasMouseY, button)) {
                    return true;
                }

                // Selection handling on Node click
                if (button == 0) {
                    if (!screen.ensureEditPermission()) return true;
                    boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                    if (shift) {
                        screen.toggleSelectNode(widget.getNode().getId());
                    } else {
                        if (!screen.isNodeSelected(widget.getNode().getId())) {
                            screen.selectNode(widget.getNode().getId(), false);
                        }
                    }
                }

                // Title bar Header click -> Start node dragging (Single or Multi)
                if (widget.isHeaderHovered(canvasMouseX, canvasMouseY) && button == 0) {
                    draggingNode = widget;
                    lastDragCanvasX = canvasMouseX;
                    lastDragCanvasY = canvasMouseY;
                    dragStartPositions.clear();
                    if (screen.isNodeSelected(widget.getNode().getId())) {
                        for (String selId : screen.getSelectedNodeIds()) {
                            RecipeNode sn = screen.getGraph().findNodeById(selId);
                            if (sn != null) {
                                dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
                                if (sn.isCompoundNode()) {
                                    for (RecipeNode sib : graph.findCompoundSiblingNodes(sn.getCompoundGroupId())) {
                                        dragStartPositions.put(sib.getId(), new double[]{sib.getPosX(), sib.getPosY()});
                                    }
                                    com.gtceu.calcboard.api.model.CanvasGroupFrame cf = graph.findCompoundFrame(sn.getCompoundGroupId());
                                    if (cf != null) {
                                        dragStartPositions.put(cf.getId(), new double[]{cf.getPosX(), cf.getPosY()});
                                    }
                                }
                            }
                        }
                        for (String selNoteId : screen.getSelectedNoteIds()) {
                            com.gtceu.calcboard.api.model.CanvasStickyNote sn = screen.getGraph().findStickyNoteById(selNoteId);
                            if (sn != null) {
                                dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
                            }
                        }
                        for (String selFrameId : screen.getSelectedFrameIds()) {
                            com.gtceu.calcboard.api.model.CanvasGroupFrame sf = screen.getGraph().findFrameById(selFrameId);
                            if (sf != null) {
                                dragStartPositions.put(sf.getId(), new double[]{sf.getPosX(), sf.getPosY()});
                            }
                        }
                    } else {
                        RecipeNode targetNode = widget.getNode();
                        dragStartPositions.put(targetNode.getId(), new double[]{targetNode.getPosX(), targetNode.getPosY()});
                        if (targetNode.isCompoundNode()) {
                            for (RecipeNode sib : graph.findCompoundSiblingNodes(targetNode.getCompoundGroupId())) {
                                dragStartPositions.put(sib.getId(), new double[]{sib.getPosX(), sib.getPosY()});
                            }
                            com.gtceu.calcboard.api.model.CanvasGroupFrame cf = graph.findCompoundFrame(targetNode.getCompoundGroupId());
                            if (cf != null) {
                                dragStartPositions.put(cf.getId(), new double[]{cf.getPosX(), cf.getPosY()});
                            }
                        }
                    }
                    return true;
                }
                return true;
            }
        }

        // 2. Left-Click on Wire -> Check Double Click for Split Junction (RFC-001)
        if (button == 0) {
            FlowGraph.ConnectionEdge clickedEdge = screen.findHoveredWire(canvasMouseX, canvasMouseY, 8.0);
            if (clickedEdge != null) {
                RecipeNode fromNode = graph.findNodeById(clickedEdge.fromNodeId());
                RecipeNode toNode = graph.findNodeById(clickedEdge.toNodeId());
                if (fromNode != null && toNode != null) {
                    long now = System.currentTimeMillis();
                    if (now - lastWireClickTime < 350 && clickedEdge.equals(lastClickedEdge)) {
                        if (screen.ensureEditPermission()) {
                            RecipeNode reroute = RecipeNode.createReroute(canvasMouseX - 16, canvasMouseY - 16);
                            if (clickedEdge.outputIndex() < fromNode.getOutputs().size()) {
                                reroute.bindRerouteIngredient(fromNode.getOutputs().get(clickedEdge.outputIndex()));
                            }
                            graph.addNode(reroute);
                            graph.getConnections().remove(clickedEdge);
                            graph.addConnection(fromNode.getId(), clickedEdge.outputIndex(), reroute.getId(), 0);
                            graph.addConnection(reroute.getId(), 0, toNode.getId(), clickedEdge.inputIndex());
                            screen.rebuildWidgets();
                            screen.markSummaryDirty();
                            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onJunctionInserted();
                            Minecraft.getInstance().getSoundManager().play(
                                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
                            );
                            lastWireClickTime = 0;
                            lastClickedEdge = null;
                            return true;
                        }
                    }
                    lastWireClickTime = now;
                    lastClickedEdge = clickedEdge;
                    return true;
                }
            }
        }

        // 2.5 Frame Actions & Header Dragging (RFC-002)
        for (int i = graph.getFrames().size() - 1; i >= 0; i--) {
            com.gtceu.calcboard.api.model.CanvasGroupFrame frame = graph.getFrames().get(i);
            CanvasGroupFrameRenderer.FrameAction action = CanvasGroupFrameRenderer.getClickedAction(frame, canvasMouseX, canvasMouseY);
            if (action != CanvasGroupFrameRenderer.FrameAction.NONE) {
                if (!screen.ensureEditPermission()) return true;

                if (action == CanvasGroupFrameRenderer.FrameAction.RESIZE && button == 0) {
                    CanvasGroupFrameRenderer.ResizeDirection dir = CanvasGroupFrameRenderer.getResizeDirection(frame, canvasMouseX, canvasMouseY);
                    if (dir != CanvasGroupFrameRenderer.ResizeDirection.NONE) {
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
                } else if (action == CanvasGroupFrameRenderer.FrameAction.DELETE && button == 0) {
                    if (frame.isCompoundFrame()) {
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
                        List<com.gtceu.calcboard.api.history.BoardCommand> cmds = new ArrayList<>();
                        if (!siblings.isEmpty() || !removedEdges.isEmpty()) {
                            cmds.add(new com.gtceu.calcboard.api.history.BoardCommand.RemoveNodesCommand(siblings, removedEdges, "Delete compound " + frame.getTitle()));
                        }
                        cmds.add(new com.gtceu.calcboard.api.history.BoardCommand.RemoveFramesCommand(List.of(frame), "Delete compound frame"));
                        screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.CompoundCommand(cmds, "Delete " + frame.getTitle()));
                    } else {
                        graph.removeFrame(frame);
                        screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.RemoveFramesCommand(List.of(frame), "Delete frame " + frame.getTitle()));
                    }
                    screen.rebuildWidgets();
                    screen.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 0.9F)
                    );
                    return true;
                } else if (action == CanvasGroupFrameRenderer.FrameAction.COLOR && button == 0) {
                    frame.cycleColor();
                    screen.markSummaryDirty();
                    return true;
                } else if (action == CanvasGroupFrameRenderer.FrameAction.COLLAPSE && button == 0) {
                    screen.collapseFrameIntoModule(frame);
                    return true;
                }
            } else if (frame.isPointInHeader(canvasMouseX, canvasMouseY) && button == 0) {
                long now = System.currentTimeMillis();
                if (now - lastFrameHeaderClickTime < 350 && frame.equals(lastClickedFrame)) {
                    screen.openFrameEditDialog(frame);
                    lastFrameHeaderClickTime = 0;
                    lastClickedFrame = null;
                    return true;
                }
                lastFrameHeaderClickTime = now;
                lastClickedFrame = frame;

                boolean isMultiSelect = net.minecraft.client.gui.screens.Screen.hasShiftDown() || net.minecraft.client.gui.screens.Screen.hasControlDown();
                if (isMultiSelect) {
                    screen.toggleSelectFrame(frame.getId());
                } else if (!screen.isFrameSelected(frame.getId())) {
                    screen.selectFrame(frame.getId(), false);
                }

                draggingFrame = frame;
                lastDragCanvasX = canvasMouseX;
                lastDragCanvasY = canvasMouseY;
                dragStartPositions.clear();

                Set<String> selFrames = screen.getSelectedFrameIds();
                Set<String> selNodes = screen.getSelectedNodeIds();
                Set<String> selNotes = screen.getSelectedNoteIds();

                if (selFrames.contains(frame.getId())) {
                    for (String fid : selFrames) {
                        com.gtceu.calcboard.api.model.CanvasGroupFrame f = graph.findFrameById(fid);
                        if (f != null) {
                            dragStartPositions.put(f.getId(), new double[]{f.getPosX(), f.getPosY()});
                            if (f.isCompoundFrame()) {
                                for (RecipeNode sn : graph.findCompoundSiblingNodes(f.getCompoundGroupId())) {
                                    dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
                                }
                            } else {
                                for (RecipeNode sn : f.getEnclosedNodes(graph)) {
                                    if (!sn.isCompoundNode()) {
                                        dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
                                    }
                                }
                                for (com.gtceu.calcboard.api.model.CanvasStickyNote sn : f.getEnclosedNotes(graph)) {
                                    dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
                                }
                            }
                        }
                    }
                    for (String nid : selNodes) {
                        RecipeNode n = graph.findNodeById(nid);
                        if (n != null) {
                            dragStartPositions.put(n.getId(), new double[]{n.getPosX(), n.getPosY()});
                            if (n.isCompoundNode()) {
                                for (RecipeNode sib : graph.findCompoundSiblingNodes(n.getCompoundGroupId())) {
                                    dragStartPositions.put(sib.getId(), new double[]{sib.getPosX(), sib.getPosY()});
                                }
                                com.gtceu.calcboard.api.model.CanvasGroupFrame cf = graph.findCompoundFrame(n.getCompoundGroupId());
                                if (cf != null) {
                                    dragStartPositions.put(cf.getId(), new double[]{cf.getPosX(), cf.getPosY()});
                                }
                            }
                        }
                    }
                    for (String noteId : selNotes) {
                        com.gtceu.calcboard.api.model.CanvasStickyNote note = graph.findStickyNoteById(noteId);
                        if (note != null) {
                            dragStartPositions.put(note.getId(), new double[]{note.getPosX(), note.getPosY()});
                        }
                    }
                } else {
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
                        for (com.gtceu.calcboard.api.model.CanvasStickyNote sn : frame.getEnclosedNotes(graph)) {
                            dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
                        }
                    }
                }
                return true;
            }
        }

        // 2.6 Sticky Note Actions & Header Dragging
        for (int i = graph.getStickyNotes().size() - 1; i >= 0; i--) {
            com.gtceu.calcboard.api.model.CanvasStickyNote note = graph.getStickyNotes().get(i);
            CanvasStickyNoteRenderer.NoteAction action = CanvasStickyNoteRenderer.getClickedAction(note, canvasMouseX, canvasMouseY);
            if (action != CanvasStickyNoteRenderer.NoteAction.NONE) {
                if (!screen.ensureEditPermission()) return true;

                if (action == CanvasStickyNoteRenderer.NoteAction.RESIZE && button == 0) {
                    resizingNote = note;
                    resizeNoteStartX = canvasMouseX;
                    resizeNoteStartY = canvasMouseY;
                    origNoteWidth = note.getWidth();
                    origNoteHeight = note.getHeight();
                    return true;
                } else if (action == CanvasStickyNoteRenderer.NoteAction.DELETE && button == 0) {
                    graph.removeStickyNote(note);
                    screen.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
                    return true;
                } else if (action == CanvasStickyNoteRenderer.NoteAction.COLOR && button == 0) {
                    note.cycleColor();
                    screen.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F));
                    return true;
                }
            } else if (note.isPointInside(canvasMouseX, canvasMouseY) && button == 0) {
                long now = System.currentTimeMillis();
                if (now - lastNoteHeaderClickTime < 350 && note.equals(lastClickedNote)) {
                    screen.openNoteEditDialog(note);
                    lastNoteHeaderClickTime = 0;
                    lastClickedNote = null;
                    return true;
                }
                lastNoteHeaderClickTime = now;
                lastClickedNote = note;

                boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                if (shift) {
                    screen.toggleSelectNote(note.getId());
                } else {
                    if (!screen.isNoteSelected(note.getId())) {
                        screen.selectNote(note.getId(), false);
                    }
                }

                if (note.isPointInHeader(canvasMouseX, canvasMouseY)) {
                    draggingNote = note;
                    lastDragCanvasX = canvasMouseX;
                    lastDragCanvasY = canvasMouseY;
                    dragStartPositions.clear();
                    if (screen.isNoteSelected(note.getId())) {
                        for (String selId : screen.getSelectedNodeIds()) {
                            RecipeNode sn = graph.findNodeById(selId);
                            if (sn != null) dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
                        }
                        for (String selNoteId : screen.getSelectedNoteIds()) {
                            com.gtceu.calcboard.api.model.CanvasStickyNote sn = graph.findStickyNoteById(selNoteId);
                            if (sn != null) dragStartPositions.put(sn.getId(), new double[]{sn.getPosX(), sn.getPosY()});
                        }
                        for (String selFrameId : screen.getSelectedFrameIds()) {
                            com.gtceu.calcboard.api.model.CanvasGroupFrame sf = graph.findFrameById(selFrameId);
                            if (sf != null) dragStartPositions.put(sf.getId(), new double[]{sf.getPosX(), sf.getPosY()});
                        }
                    } else {
                        dragStartPositions.put(note.getId(), new double[]{note.getPosX(), note.getPosY()});
                    }
                }
                return true;
            }
        }

        // 3. Right-Click directly on any wire to cut/disconnect it
        if (button == 1) {
            FlowGraph.ConnectionEdge cutEdge = screen.findHoveredWire(canvasMouseX, canvasMouseY, 8.0);
            if (cutEdge != null) {
                if (!screen.ensureEditPermission()) return true;
                graph.getConnections().remove(cutEdge);
                screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.DisconnectWireCommand(cutEdge));
                notifyDisconnect("message.gtcalcboard.disconnect_wire");
                return true;
            }
        }

        // 4. Click outside -> Commit all edits
        for (NodeWidget w : nodeWidgets) {
            w.commitCountEdit();
        }

        // Check clicking on existing Quick Action Marker ([🔍] Search, [🔀] Junction, [🖼] Frame, [📝] Note)
        if (button == 0 && hasQuickAddMarker()) {
            double quickAddMarkerCanvasX = quickAddMarkerHandler.getQuickAddMarkerCanvasX();
            double quickAddMarkerCanvasY = quickAddMarkerHandler.getQuickAddMarkerCanvasY();
            boolean inSearchBtn = canvasMouseX >= quickAddMarkerCanvasX - 44 && canvasMouseX <= quickAddMarkerCanvasX - 24
                    && canvasMouseY >= quickAddMarkerCanvasY - 10 && canvasMouseY <= quickAddMarkerCanvasY + 10;
            boolean inJunctionBtn = canvasMouseX >= quickAddMarkerCanvasX - 21 && canvasMouseX <= quickAddMarkerCanvasX - 1
                    && canvasMouseY >= quickAddMarkerCanvasY - 10 && canvasMouseY <= quickAddMarkerCanvasY + 10;
            boolean inFrameBtn = canvasMouseX >= quickAddMarkerCanvasX + 2 && canvasMouseX <= quickAddMarkerCanvasX + 22
                    && canvasMouseY >= quickAddMarkerCanvasY - 10 && canvasMouseY <= quickAddMarkerCanvasY + 10;
            boolean inNoteBtn = canvasMouseX >= quickAddMarkerCanvasX + 25 && canvasMouseX <= quickAddMarkerCanvasX + 45
                    && canvasMouseY >= quickAddMarkerCanvasY - 10 && canvasMouseY <= quickAddMarkerCanvasY + 10;

            if (inSearchBtn) {
                if (hasQuickAddWireContext()) {
                    screen.getSearchDialog().openForContextualWire(
                            quickAddMarkerHandler.getQuickAddWireSourceNode(),
                            quickAddMarkerHandler.getQuickAddWirePortIdx(),
                            quickAddMarkerHandler.isQuickAddWireInput(),
                            quickAddMarkerHandler.getQuickAddWireStack(),
                            quickAddMarkerCanvasX,
                            quickAddMarkerCanvasY,
                            quickAddMarkerHandler.isQuickAddWireShiftAutoRatio()
                    );
                } else {
                    screen.getSearchDialog().openAt(quickAddMarkerCanvasX, quickAddMarkerCanvasY);
                }
                clearQuickAddMarker();
                return true;
            } else if (inJunctionBtn) {
                if (screen.ensureEditPermission()) {
                    if (hasQuickAddWireContext()) {
                        RecipeNode reroute = RecipeNode.createReroute(quickAddMarkerCanvasX - 16, quickAddMarkerCanvasY - 16);
                        if (quickAddMarkerHandler.getQuickAddWireStack() != null) {
                            reroute.bindRerouteIngredient(quickAddMarkerHandler.getQuickAddWireStack());
                        }
                        screen.getGraph().addNode(reroute);
                        if (quickAddMarkerHandler.isQuickAddWireInput()) {
                            screen.getGraph().addConnection(reroute.getId(), 0, quickAddMarkerHandler.getQuickAddWireSourceNode().getId(), quickAddMarkerHandler.getQuickAddWirePortIdx());
                        } else {
                            screen.getGraph().addConnection(quickAddMarkerHandler.getQuickAddWireSourceNode().getId(), quickAddMarkerHandler.getQuickAddWirePortIdx(), reroute.getId(), 0);
                        }
                        screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.AddNodesCommand(java.util.List.of(reroute), java.util.Collections.emptyList(), "Add Junction Node"));
                        screen.rebuildWidgets();
                        screen.markSummaryDirty();
                        com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onJunctionInserted();
                        Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
                        );
                    } else {
                        screen.addRerouteNodeAt(quickAddMarkerCanvasX, quickAddMarkerCanvasY);
                        com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onJunctionInserted();
                    }
                }
                clearQuickAddMarker();
                return true;
            } else if (inFrameBtn) {
                screen.createFrameAt(quickAddMarkerCanvasX, quickAddMarkerCanvasY);
                clearQuickAddMarker();
                return true;
            } else if (inNoteBtn) {
                screen.createNoteAt(quickAddMarkerCanvasX, quickAddMarkerCanvasY);
                clearQuickAddMarker();
                return true;
            }
        }

        // 4. Empty Space Left Click -> Check Double Click or Start Box Select / Click
        if (button == 0) {
            if (quickAddMarkerHandler.handleEmptyCanvasClick(canvasMouseX, canvasMouseY, screen)) {
                selectionHandler.stopBoxSelection();
                return true;
            }

            selectionHandler.startBoxSelection(canvasMouseX, canvasMouseY);
            if (!net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                screen.clearSelection();
            }
            return true;
        }

        // 5. Canvas Pan start (Right click or Middle click)
        if (button == 1 || button == 2) {
            panZoomHandler.startPan(mouseX, mouseY);
            return true;
        }

        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (wireStartNode != null) {
                double canvasMouseX = screen.toCanvasX(mouseX);
                double canvasMouseY = screen.toCanvasY(mouseY);
                List<NodeWidget> nodeWidgets = screen.getNodeWidgets();
                FlowGraph graph = screen.getGraph();
                boolean connected = false;

                for (NodeWidget targetWidget : nodeWidgets) {
                    if (targetWidget != wireStartNode && targetWidget.isPointInside(canvasMouseX, canvasMouseY)) {
                        if (!wireStartIsInput) {
                            int inPortIdx = targetWidget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
                            if (inPortIdx >= 0) {
                                handleForwardWireConnect(targetWidget, graph, inPortIdx);
                                connected = true;
                                break;
                            }
                        } else {
                            int outPortIdx = targetWidget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
                            if (outPortIdx >= 0) {
                                handleReverseWireConnect(targetWidget, graph, outPortIdx);
                                connected = true;
                                break;
                            }
                        }
                    }
                }

                if (!connected && screen.getSearchDialog() != null) {
                    handleContextualWireDrag(canvasMouseX, canvasMouseY);
                }

                wireStartNode = null;
                wireStartPortIdx = -1;
                wireStartIsInput = false;
                return true;
            }

            if (selectionHandler.isBoxSelecting()) {
                double minX = Math.min(selectionHandler.getBoxSelectStartX(), selectionHandler.getBoxSelectCurX());
                double maxX = Math.max(selectionHandler.getBoxSelectStartX(), selectionHandler.getBoxSelectCurX());
                double minY = Math.min(selectionHandler.getBoxSelectStartY(), selectionHandler.getBoxSelectCurY());
                double maxY = Math.max(selectionHandler.getBoxSelectStartY(), selectionHandler.getBoxSelectCurY());

                if (Math.abs(maxX - minX) > 6 || Math.abs(maxY - minY) > 6) {
                    clearQuickAddMarker();
                    selectionHandler.finishBoxSelection(screen, net.minecraft.client.gui.screens.Screen.hasShiftDown());
                } else {
                    selectionHandler.stopBoxSelection();
                    quickAddMarkerHandler.triggerContextualMarker(selectionHandler.getBoxSelectStartX(), selectionHandler.getBoxSelectStartY(), null, -1, false, null, false);
                }
                return true;
            }

            if (resizingNode != null) {
                resizingNode = null;
                return true;
            }

            if (resizingNote != null) {
                resizingNote = null;
                return true;
            }

            if (draggingNote != null) {
                recordDragMoveCommand();
                draggingNote = null;
                return true;
            }

            if (resizingFrame != null) {
                resizingFrame = null;
                resizeFrameDir = CanvasGroupFrameRenderer.ResizeDirection.NONE;
                screen.markSummaryDirty();
                return true;
            }

            if (draggingFrame != null) {
                recordDragMoveCommand();
                draggingFrame = null;
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

    private void handleForwardWireConnect(NodeWidget targetWidget, FlowGraph graph, int inPortIdx) {
        RecipeNode fromNode = wireStartNode.getNode();
        RecipeNode toNode = targetWidget.getNode();

        if (fromNode.isReroute() && inPortIdx < toNode.getInputs().size()) {
            fromNode.bindRerouteIngredient(toNode.getInputs().get(inPortIdx));
        } else if (toNode.isReroute() && wireStartPortIdx < fromNode.getOutputs().size()) {
            toNode.bindRerouteIngredient(fromNode.getOutputs().get(wireStartPortIdx));
        }

        if (wireStartPortIdx < fromNode.getOutputs().size() && inPortIdx < toNode.getInputs().size()) {
            IngredientStack outStack = fromNode.getOutputs().get(wireStartPortIdx);
            IngredientStack inStack = toNode.getInputs().get(inPortIdx);
            if (inStack.isFluid() && com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.isDynamoToBoilerConvertible(toNode)) {
                var allFluids = com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.getAllBoilingFluidInputs();
                inStack.setAlternatives(allFluids);
            }
            boolean matched = false;
            if (outStack.getId().equals(inStack.getId())) {
                matched = true;
            } else if (inStack.getAlternatives() != null && inStack.getAlternatives().contains(outStack.getId())) {
                inStack.selectAlternative(outStack.getId());
                matched = true;
            } else if (outStack.getAlternatives() != null && outStack.getAlternatives().contains(inStack.getId())) {
                outStack.selectAlternative(inStack.getId());
                matched = true;
            } else if (inStack.getAlternatives() != null && outStack.getAlternatives() != null) {
                for (ResourceLocation alt : inStack.getAlternatives()) {
                    if (outStack.getAlternatives().contains(alt)) {
                        inStack.selectAlternative(alt);
                        outStack.selectAlternative(alt);
                        matched = true;
                        break;
                    }
                }
            } else if (inStack.isFluid() && outStack.isFluid() && com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.isDynamoToBoilerConvertible(toNode)) {
                inStack.addAlternative(outStack.getId());
                inStack.selectAlternative(outStack.getId());
                matched = true;
            }

            if (matched) {
                if (inStack.isFluid() && com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.isDynamoToBoilerConvertible(toNode)) {
                    com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.updateBoilerFluidRecipe(toNode, inStack.getId());
                }
                targetWidget.invalidateCache();
                wireStartNode.invalidateCache();
            }
        }

        FlowGraph.ConnectionEdge newEdge = new FlowGraph.ConnectionEdge(fromNode.getId(), wireStartPortIdx, toNode.getId(), inPortIdx);
        graph.addConnection(fromNode.getId(), wireStartPortIdx, toNode.getId(), inPortIdx);

        boolean shiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        Double oldMachineCount = shiftDown ? toNode.getMachineCount() : null;
        Double newMachineCount = null;

        if (shiftDown && wireStartPortIdx < fromNode.getOutputs().size() && inPortIdx < toNode.getInputs().size()) {
            double matchedCount = FlowGraphSolver.calculateConsumerMatchCount(graph, fromNode, wireStartPortIdx, toNode, inPortIdx);
            newMachineCount = matchedCount;
            toNode.setMachineCount(matchedCount);
            targetWidget.updateCountBuffer();
            targetWidget.invalidateCache();

            BoardToast.show(Component.literal("§a✔ ").append(
                Component.translatable("message.gtcalcboard.shift_connect_matched", toNode.getName(), String.format("%.0f", matchedCount))
            ));
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F)
            );
        } else {
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
            );
        }
        screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.ConnectWireCommand(newEdge, shiftDown ? toNode.getId() : null, oldMachineCount, newMachineCount));
        screen.markSummaryDirty();
        com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onWireConnected(shiftDown);
    }

    private void handleReverseWireConnect(NodeWidget targetWidget, FlowGraph graph, int outPortIdx) {
        RecipeNode fromNode = targetWidget.getNode();
        RecipeNode toNode = wireStartNode.getNode();

        if (fromNode.isReroute() && wireStartPortIdx < toNode.getInputs().size()) {
            fromNode.bindRerouteIngredient(toNode.getInputs().get(wireStartPortIdx));
        } else if (toNode.isReroute() && outPortIdx < fromNode.getOutputs().size()) {
            toNode.bindRerouteIngredient(fromNode.getOutputs().get(outPortIdx));
        }

        if (outPortIdx < fromNode.getOutputs().size() && wireStartPortIdx < toNode.getInputs().size()) {
            IngredientStack outStack = fromNode.getOutputs().get(outPortIdx);
            IngredientStack inStack = toNode.getInputs().get(wireStartPortIdx);
            if (inStack.isFluid() && com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.isDynamoToBoilerConvertible(toNode)) {
                var allFluids = com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.getAllBoilingFluidInputs();
                inStack.setAlternatives(allFluids);
            }
            boolean matched = false;
            if (outStack.getId().equals(inStack.getId())) {
                matched = true;
            } else if (inStack.getAlternatives() != null && inStack.getAlternatives().contains(outStack.getId())) {
                inStack.selectAlternative(outStack.getId());
                matched = true;
            } else if (outStack.getAlternatives() != null && outStack.getAlternatives().contains(inStack.getId())) {
                outStack.selectAlternative(inStack.getId());
                matched = true;
            } else if (inStack.getAlternatives() != null && outStack.getAlternatives() != null) {
                for (ResourceLocation alt : inStack.getAlternatives()) {
                    if (outStack.getAlternatives().contains(alt)) {
                        inStack.selectAlternative(alt);
                        outStack.selectAlternative(alt);
                        matched = true;
                        break;
                    }
                }
            } else if (inStack.isFluid() && outStack.isFluid() && com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.isDynamoToBoilerConvertible(toNode)) {
                inStack.addAlternative(outStack.getId());
                inStack.selectAlternative(outStack.getId());
                matched = true;
            }

            if (matched) {
                if (inStack.isFluid() && com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.isDynamoToBoilerConvertible(toNode)) {
                    com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.updateBoilerFluidRecipe(toNode, inStack.getId());
                }
                wireStartNode.invalidateCache();
                targetWidget.invalidateCache();
            }
        }

        FlowGraph.ConnectionEdge newEdge = new FlowGraph.ConnectionEdge(fromNode.getId(), outPortIdx, toNode.getId(), wireStartPortIdx);
        graph.addConnection(fromNode.getId(), outPortIdx, toNode.getId(), wireStartPortIdx);

        boolean shiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();
        Double oldMachineCount = shiftDown ? fromNode.getMachineCount() : null;
        Double newMachineCount = null;

        if (shiftDown && outPortIdx < fromNode.getOutputs().size() && wireStartPortIdx < toNode.getInputs().size()) {
            double matchedCount = FlowGraphSolver.calculateProducerMatchCount(graph, fromNode, outPortIdx, toNode, wireStartPortIdx);
            newMachineCount = matchedCount;
            fromNode.setMachineCount(matchedCount);
            targetWidget.updateCountBuffer();
            targetWidget.invalidateCache();

            BoardToast.show(Component.literal("§a✔ ").append(
                Component.translatable("message.gtcalcboard.shift_connect_matched", fromNode.getName(), String.format("%.0f", matchedCount))
            ));
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F)
            );
        } else {
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
            );
        }
        screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.ConnectWireCommand(newEdge, shiftDown ? fromNode.getId() : null, oldMachineCount, newMachineCount));
        screen.markSummaryDirty();
        com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onWireConnected(shiftDown);
    }

    private void handleContextualWireDrag(double canvasMouseX, double canvasMouseY) {
        double startPortX = wireStartIsInput ? wireStartNode.getInputPortX(wireStartPortIdx) : wireStartNode.getOutputPortX(wireStartPortIdx);
        double startPortY = wireStartIsInput ? wireStartNode.getInputPortY(wireStartPortIdx) : wireStartNode.getOutputPortY(wireStartPortIdx);
        double dragDist = Math.hypot(canvasMouseX - startPortX, canvasMouseY - startPortY);

        if (dragDist >= 15.0) {
            RecipeNode srcNode = wireStartNode.getNode();
            boolean shiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();
            IngredientStack stack = null;
            if (wireStartIsInput) {
                if (wireStartPortIdx >= 0 && wireStartPortIdx < srcNode.getInputs().size()) {
                    stack = srcNode.getInputs().get(wireStartPortIdx);
                }
            } else {
                if (wireStartPortIdx >= 0 && wireStartPortIdx < srcNode.getOutputs().size()) {
                    stack = srcNode.getOutputs().get(wireStartPortIdx);
                }
            }

            quickAddMarkerHandler.triggerContextualMarker(canvasMouseX, canvasMouseY, srcNode, wireStartPortIdx, wireStartIsInput, stack, shiftDown);
        }
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (selectionHandler.isBoxSelecting() && button == 0) {
            selectionHandler.updateBoxSelection(screen.toCanvasX(mouseX), screen.toCanvasY(mouseY));
            return true;
        }

        if (panZoomHandler.handleMouseDragged(mouseX, mouseY, button, screen)) {
            return true;
        }

        if (resizingNote != null && button == 0) {
            double canvasMouseX = screen.toCanvasX(mouseX);
            double canvasMouseY = screen.toCanvasY(mouseY);
            double deltaX = canvasMouseX - resizeNoteStartX;
            double deltaY = canvasMouseY - resizeNoteStartY;
            resizingNote.setWidth(Math.max(com.gtceu.calcboard.api.model.CanvasStickyNote.MIN_WIDTH, origNoteWidth + deltaX));
            resizingNote.setHeight(Math.max(com.gtceu.calcboard.api.model.CanvasStickyNote.MIN_HEIGHT, origNoteHeight + deltaY));
            screen.markSummaryDirty();
            return true;
        }

        if (draggingNote != null && button == 0) {
            double curCanvasX = screen.toCanvasX(mouseX);
            double curCanvasY = screen.toCanvasY(mouseY);
            double deltaX = curCanvasX - lastDragCanvasX;
            double deltaY = curCanvasY - lastDragCanvasY;
            lastDragCanvasX = curCanvasX;
            lastDragCanvasY = curCanvasY;

            if (screen.isNoteSelected(draggingNote.getId())) {
                for (String selId : screen.getSelectedNodeIds()) {
                    RecipeNode sn = screen.getGraph().findNodeById(selId);
                    if (sn != null) {
                        sn.setPos(sn.getPosX() + deltaX, sn.getPosY() + deltaY);
                    }
                }
                for (String selNoteId : screen.getSelectedNoteIds()) {
                    com.gtceu.calcboard.api.model.CanvasStickyNote sn = screen.getGraph().findStickyNoteById(selNoteId);
                    if (sn != null) {
                        sn.moveBy(deltaX, deltaY);
                    }
                }
                for (String selFrameId : screen.getSelectedFrameIds()) {
                    com.gtceu.calcboard.api.model.CanvasGroupFrame sf = screen.getGraph().findFrameById(selFrameId);
                    if (sf != null) {
                        sf.moveBy(deltaX, deltaY);
                    }
                }
            } else {
                draggingNote.moveBy(deltaX, deltaY);
            }
            screen.markSummaryDirty();
            return true;
        }

        if (resizingFrame != null && button == 0) {
            double canvasMouseX = screen.toCanvasX(mouseX);
            double canvasMouseY = screen.toCanvasY(mouseY);
            double deltaX = canvasMouseX - resizeFrameStartX;
            double deltaY = canvasMouseY - resizeFrameStartY;

            double minW = com.gtceu.calcboard.api.model.CanvasGroupFrame.MIN_WIDTH;
            double minH = com.gtceu.calcboard.api.model.CanvasGroupFrame.MIN_HEIGHT;

            // Horizontal Resizing
            switch (resizeFrameDir) {
                case EAST, NORTH_EAST, SOUTH_EAST -> {
                    resizingFrame.setWidth(Math.max(minW, origFrameWidth + deltaX));
                }
                case WEST, NORTH_WEST, SOUTH_WEST -> {
                    double clampedDeltaX = Math.min(deltaX, origFrameWidth - minW);
                    resizingFrame.setPosX(origFrameX + clampedDeltaX);
                    resizingFrame.setWidth(origFrameWidth - clampedDeltaX);
                }
                default -> {}
            }

            // Vertical Resizing
            switch (resizeFrameDir) {
                case SOUTH, SOUTH_WEST, SOUTH_EAST -> {
                    resizingFrame.setHeight(Math.max(minH, origFrameHeight + deltaY));
                }
                case NORTH, NORTH_WEST, NORTH_EAST -> {
                    double clampedDeltaY = Math.min(deltaY, origFrameHeight - minH);
                    resizingFrame.setPosY(origFrameY + clampedDeltaY);
                    resizingFrame.setHeight(origFrameHeight - clampedDeltaY);
                }
                default -> {}
            }
            return true;
        }

        if (draggingFrame != null && button == 0) {
            double curCanvasX = screen.toCanvasX(mouseX);
            double curCanvasY = screen.toCanvasY(mouseY);
            double deltaX = curCanvasX - lastDragCanvasX;
            double deltaY = curCanvasY - lastDragCanvasY;
            lastDragCanvasX = curCanvasX;
            lastDragCanvasY = curCanvasY;

            for (String id : dragStartPositions.keySet()) {
                com.gtceu.calcboard.api.model.CanvasGroupFrame f = screen.getGraph().findFrameById(id);
                if (f != null) {
                    f.moveBy(deltaX, deltaY);
                    continue;
                }
                RecipeNode n = screen.getGraph().findNodeById(id);
                if (n != null) {
                    n.setPos(n.getPosX() + deltaX, n.getPosY() + deltaY);
                    continue;
                }
                com.gtceu.calcboard.api.model.CanvasStickyNote note = screen.getGraph().findStickyNoteById(id);
                if (note != null) {
                    note.moveBy(deltaX, deltaY);
                }
            }
            screen.markSummaryDirty();
            return true;
        }

        if (resizingNode != null && button == 0) {
            double canvasMouseX = screen.toCanvasX(mouseX);
            double canvasMouseY = screen.toCanvasY(mouseY);
            double deltaX = canvasMouseX - resizeStartCanvasX;
            double deltaY = canvasMouseY - resizeStartCanvasY;
            int newWidth = (int) Math.max(190, Math.min(500, origNodeWidth + deltaX));
            int newHeight = (int) Math.max(resizingNode.calculateAutoHeight(), Math.min(600, origNodeHeight + deltaY));
            resizingNode.getNode().setCardWidth(newWidth);
            resizingNode.getNode().setCardHeight(newHeight);
            return true;
        }

        if (draggingNode != null && button == 0) {
            double curCanvasX = screen.toCanvasX(mouseX);
            double curCanvasY = screen.toCanvasY(mouseY);
            double deltaX = curCanvasX - lastDragCanvasX;
            double deltaY = curCanvasY - lastDragCanvasY;
            lastDragCanvasX = curCanvasX;
            lastDragCanvasY = curCanvasY;

            for (String id : dragStartPositions.keySet()) {
                RecipeNode n = screen.getGraph().findNodeById(id);
                if (n != null) {
                    n.setPos(n.getPosX() + deltaX, n.getPosY() + deltaY);
                    continue;
                }
                com.gtceu.calcboard.api.model.CanvasGroupFrame f = screen.getGraph().findFrameById(id);
                if (f != null) {
                    f.moveBy(deltaX, deltaY);
                    continue;
                }
                com.gtceu.calcboard.api.model.CanvasStickyNote note = screen.getGraph().findStickyNoteById(id);
                if (note != null) {
                    note.moveBy(deltaX, deltaY);
                }
            }
            screen.markSummaryDirty();
            return true;
        }

        if (panZoomHandler.isPanning() && (button == 1 || button == 2)) {
            screen.setPanX(screen.getPanX() + dragX);
            screen.setPanY(screen.getPanY() + dragY);
            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onPanOrZoom();
            return true;
        }

        return false;
    }

    private void recordDragMoveCommand() {
        if (dragStartPositions.isEmpty()) return;
        Map<String, double[]> nodeDeltas = new HashMap<>();
        Map<String, double[]> noteDeltas = new HashMap<>();
        Map<String, double[]> frameDeltas = new HashMap<>();

        for (Map.Entry<String, double[]> entry : dragStartPositions.entrySet()) {
            String id = entry.getKey();
            double origX = entry.getValue()[0];
            double origY = entry.getValue()[1];

            RecipeNode n = screen.getGraph().findNodeById(id);
            if (n != null) {
                double dx = n.getPosX() - origX;
                double dy = n.getPosY() - origY;
                if (Math.abs(dx) > 0.001 || Math.abs(dy) > 0.001) {
                    nodeDeltas.put(id, new double[]{dx, dy});
                }
                continue;
            }

            com.gtceu.calcboard.api.model.CanvasGroupFrame f = screen.getGraph().findFrameById(id);
            if (f != null) {
                double dx = f.getPosX() - origX;
                double dy = f.getPosY() - origY;
                if (Math.abs(dx) > 0.001 || Math.abs(dy) > 0.001) {
                    frameDeltas.put(id, new double[]{dx, dy});
                }
                continue;
            }

            com.gtceu.calcboard.api.model.CanvasStickyNote note = screen.getGraph().findStickyNoteById(id);
            if (note != null) {
                double dx = note.getPosX() - origX;
                double dy = note.getPosY() - origY;
                if (Math.abs(dx) > 0.001 || Math.abs(dy) > 0.001) {
                    noteDeltas.put(id, new double[]{dx, dy});
                }
            }
        }

        if (!nodeDeltas.isEmpty() || !noteDeltas.isEmpty() || !frameDeltas.isEmpty()) {
            screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.MoveComponentsCommand(nodeDeltas, noteDeltas, frameDeltas));
        }
        dragStartPositions.clear();
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        boolean handled = panZoomHandler.handleMouseScrolled(mouseX, mouseY, delta, screen);
        if (handled) {
            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onPanOrZoom();
        }
        return handled;
    }

    private void notifyDisconnect(String translatableKey) {
        screen.markSummaryDirty();
        com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onWireDisconnected();
        BoardToast.show(Component.literal("§c✕ ").append(Component.translatable(translatableKey)));
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 1.2F));
    }

    public void renderMarquee(net.minecraft.client.gui.GuiGraphics graphics) {
        selectionHandler.renderMarquee(graphics, screen);
    }
}



