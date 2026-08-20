package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.FlowGraphSolver;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles canvas panning, zooming, node dragging, bidirectional wire connections, and line cut interactions.
 */
public class CanvasInteractionHandler {
    private final BoardScreen screen;

    // Canvas Panning State
    private boolean isPanning = false;
    private double panStartX, panStartY;

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

    // Box (Marquee) Selection State
    private boolean isBoxSelecting = false;
    private double boxSelectStartX, boxSelectStartY;
    private double boxSelectCurX, boxSelectCurY;
    private final Map<String, double[]> dragStartPositions = new HashMap<>();

    // Quick Add Marker State
    private boolean hasQuickAddMarker = false;
    private double quickAddMarkerCanvasX = 0;
    private double quickAddMarkerCanvasY = 0;
    private long quickAddMarkerTime = 0;
    private long lastEmptyClickTime = 0;
    private double lastEmptyClickCanvasX = 0;
    private double lastEmptyClickCanvasY = 0;

    public CanvasInteractionHandler(BoardScreen screen) {
        this.screen = screen;
    }

    public boolean hasQuickAddMarker() {
        return hasQuickAddMarker && (System.currentTimeMillis() - quickAddMarkerTime < 15000);
    }

    public double getQuickAddMarkerCanvasX() {
        return quickAddMarkerCanvasX;
    }

    public double getQuickAddMarkerCanvasY() {
        return quickAddMarkerCanvasY;
    }

    public void clearQuickAddMarker() {
        this.hasQuickAddMarker = false;
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

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (mouseY < screen.getHeaderBottomY()) {
            return false;
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
                            }
                        }
                    } else {
                        dragStartPositions.put(widget.getNode().getId(), new double[]{widget.getNode().getPosX(), widget.getNode().getPosY()});
                    }
                    return true;
                }
                return true;
            }
        }

        // 2. Right-Click directly on any wire to cut/disconnect it
        if (button == 1) {
            for (FlowGraph.ConnectionEdge edge : new ArrayList<>(graph.getConnections())) {
                RecipeNode fromNode = graph.findNodeById(edge.fromNodeId());
                RecipeNode toNode = graph.findNodeById(edge.toNodeId());
                if (fromNode != null && toNode != null) {
                    NodeWidget fromWidget = screen.findWidgetForNode(fromNode);
                    NodeWidget toWidget = screen.findWidgetForNode(toNode);
                    if (fromWidget != null && toWidget != null) {
                        float x1 = fromWidget.getOutputPortX(edge.outputIndex());
                        float y1 = fromWidget.getOutputPortY(edge.outputIndex());
                        float x2 = toWidget.getInputPortX(edge.inputIndex());
                        float y2 = toWidget.getInputPortY(edge.inputIndex());

                        if (ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, canvasMouseX, canvasMouseY, 8.0)) {
                            if (!screen.ensureEditPermission()) return true;
                            graph.getConnections().remove(edge);
                            screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.DisconnectWireCommand(edge));
                            notifyDisconnect("message.gtcalcboard.disconnect_wire");
                            return true;
                        }
                    }
                }
            }
        }

        // 3. Click outside -> Commit all edits
        for (NodeWidget w : nodeWidgets) {
            w.commitCountEdit();
        }

        // Check clicking on existing Quick Add Marker
        if (button == 0 && hasQuickAddMarker()) {
            double dist = Math.hypot(canvasMouseX - quickAddMarkerCanvasX, canvasMouseY - quickAddMarkerCanvasY);
            if (dist <= 16.0) {
                screen.getSearchDialog().openAt(quickAddMarkerCanvasX, quickAddMarkerCanvasY);
                hasQuickAddMarker = false;
                return true;
            }
        }

        // 4. Empty Space Left Click -> Check Double Click or Start Box Select / Click
        if (button == 0) {
            long now = System.currentTimeMillis();
            if (now - lastEmptyClickTime < 350 && Math.hypot(canvasMouseX - lastEmptyClickCanvasX, canvasMouseY - lastEmptyClickCanvasY) < 25) {
                screen.getSearchDialog().openAt(canvasMouseX, canvasMouseY);
                hasQuickAddMarker = false;
                isBoxSelecting = false;
                lastEmptyClickTime = 0;
                return true;
            }
            lastEmptyClickTime = now;
            lastEmptyClickCanvasX = canvasMouseX;
            lastEmptyClickCanvasY = canvasMouseY;

            hasQuickAddMarker = false;

            isBoxSelecting = true;
            boxSelectStartX = canvasMouseX;
            boxSelectStartY = canvasMouseY;
            boxSelectCurX = canvasMouseX;
            boxSelectCurY = canvasMouseY;
            if (!net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
                screen.clearSelection();
            }
            return true;
        }

        // 5. Canvas Pan start (Right click or Middle click)
        if (button == 1 || button == 2) {
            isPanning = true;
            panStartX = mouseX - screen.getPanX();
            panStartY = mouseY - screen.getPanY();
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
                        
                        // Case A: Forward drag (Output -> Input)
                        if (!wireStartIsInput) {
                            int inPortIdx = targetWidget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
                            if (inPortIdx >= 0) {
                                RecipeNode fromNode = wireStartNode.getNode();
                                RecipeNode toNode = targetWidget.getNode();

                                if (wireStartPortIdx < fromNode.getOutputs().size() && inPortIdx < toNode.getInputs().size()) {
                                    IngredientStack outStack = fromNode.getOutputs().get(wireStartPortIdx);
                                    IngredientStack inStack = toNode.getInputs().get(inPortIdx);
                                    if (!outStack.equals(inStack) && inStack.matchesOrAlternative(outStack)) {
                                        inStack.selectAlternative(outStack.getId());
                                        targetWidget.invalidateCache();
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
                                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                            SoundEvents.PLAYER_LEVELUP, 1.2F
                                        )
                                    );
                                } else {
                                    Minecraft.getInstance().getSoundManager().play(
                                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                            SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F
                                        )
                                    );
                                }
                                screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.ConnectWireCommand(newEdge, shiftDown ? toNode.getId() : null, oldMachineCount, newMachineCount));
                                screen.markSummaryDirty();
                                com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onWireConnected(shiftDown);
                                connected = true;
                                break;
                            }
                        } 
                        // Case B: Reverse drag (Input -> Output) - match upstream producer count to downstream consumer requirement
                        else {
                            int outPortIdx = targetWidget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
                            if (outPortIdx >= 0) {
                                RecipeNode fromNode = targetWidget.getNode();   // Producer
                                RecipeNode toNode = wireStartNode.getNode();    // Consumer

                                if (outPortIdx < fromNode.getOutputs().size() && wireStartPortIdx < toNode.getInputs().size()) {
                                    IngredientStack outStack = fromNode.getOutputs().get(outPortIdx);
                                    IngredientStack inStack = toNode.getInputs().get(wireStartPortIdx);
                                    if (!outStack.equals(inStack) && inStack.matchesOrAlternative(outStack)) {
                                        inStack.selectAlternative(outStack.getId());
                                        wireStartNode.invalidateCache();
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
                                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                            SoundEvents.PLAYER_LEVELUP, 1.2F
                                        )
                                    );
                                } else {
                                    Minecraft.getInstance().getSoundManager().play(
                                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                            SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F
                                        )
                                    );
                                }
                                screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.ConnectWireCommand(newEdge, shiftDown ? fromNode.getId() : null, oldMachineCount, newMachineCount));
                                screen.markSummaryDirty();
                                com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onWireConnected(shiftDown);
                                connected = true;
                                break;
                            }
                        }
                    }
                }

                // If not connected to an existing port, check if dropped on empty canvas (Drag-to-Search / Contextual Wire)
                if (!connected && screen.getSearchDialog() != null) {
                    double startPortX = wireStartIsInput ? wireStartNode.getInputPortX(wireStartPortIdx) : wireStartNode.getOutputPortX(wireStartPortIdx);
                    double startPortY = wireStartIsInput ? wireStartNode.getInputPortY(wireStartPortIdx) : wireStartNode.getOutputPortY(wireStartPortIdx);
                    double dragDist = Math.hypot(canvasMouseX - startPortX, canvasMouseY - startPortY);

                    if (dragDist >= 15.0) {
                        RecipeNode srcNode = wireStartNode.getNode();
                        boolean shiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                        if (wireStartIsInput) {
                            if (wireStartPortIdx >= 0 && wireStartPortIdx < srcNode.getInputs().size()) {
                                IngredientStack stack = srcNode.getInputs().get(wireStartPortIdx);
                                screen.getSearchDialog().openForContextualWire(srcNode, wireStartPortIdx, true, stack, canvasMouseX, canvasMouseY, shiftDown);
                            }
                        } else {
                            if (wireStartPortIdx >= 0 && wireStartPortIdx < srcNode.getOutputs().size()) {
                                IngredientStack stack = srcNode.getOutputs().get(wireStartPortIdx);
                                screen.getSearchDialog().openForContextualWire(srcNode, wireStartPortIdx, false, stack, canvasMouseX, canvasMouseY, shiftDown);
                            }
                        }
                    }
                }

                wireStartNode = null;
                wireStartPortIdx = -1;
                wireStartIsInput = false;
                return true;
            }

            if (isBoxSelecting) {
                isBoxSelecting = false;
                double minX = Math.min(boxSelectStartX, boxSelectCurX);
                double maxX = Math.max(boxSelectStartX, boxSelectCurX);
                double minY = Math.min(boxSelectStartY, boxSelectCurY);
                double maxY = Math.max(boxSelectStartY, boxSelectCurY);

                if (Math.abs(maxX - minX) > 6 || Math.abs(maxY - minY) > 6) {
                    hasQuickAddMarker = false; // User dragged a selection box, don't leave marker
                    for (NodeWidget w : screen.getNodeWidgets()) {
                        double nx = w.getNode().getPosX();
                        double ny = w.getNode().getPosY();
                        int nw = w.getWidth();
                        int nh = w.getHeight();

                        if (nx < maxX && nx + nw > minX && ny < maxY && ny + nh > minY) {
                            screen.getSelectedNodeIds().add(w.getNode().getId());
                        }
                    }
                } else {
                    // Stationary click release -> spawn [+] Quick-Add marker!
                    quickAddMarkerCanvasX = boxSelectStartX;
                    quickAddMarkerCanvasY = boxSelectStartY;
                    quickAddMarkerTime = System.currentTimeMillis();
                    hasQuickAddMarker = true;
                }
                return true;
            }

            if (resizingNode != null) {
                resizingNode = null;
                return true;
            }

            if (draggingNode != null) {
                if (!dragStartPositions.isEmpty()) {
                    Map<String, double[]> deltas = new HashMap<>();
                    for (Map.Entry<String, double[]> entry : dragStartPositions.entrySet()) {
                        RecipeNode n = screen.getGraph().findNodeById(entry.getKey());
                        if (n != null) {
                            double dx = n.getPosX() - entry.getValue()[0];
                            double dy = n.getPosY() - entry.getValue()[1];
                            if (Math.abs(dx) > 0.001 || Math.abs(dy) > 0.001) {
                                deltas.put(entry.getKey(), new double[]{dx, dy});
                            }
                        }
                    }
                    if (!deltas.isEmpty()) {
                        screen.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.MoveNodesCommand(deltas));
                    }
                    dragStartPositions.clear();
                }
                draggingNode = null;
                return true;
            }
        }

        if (isPanning && (button == 1 || button == 2)) {
            isPanning = false;
            return true;
        }

        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isBoxSelecting && button == 0) {
            boxSelectCurX = screen.toCanvasX(mouseX);
            boxSelectCurY = screen.toCanvasY(mouseY);
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

            if (screen.isNodeSelected(draggingNode.getNode().getId())) {
                // Move all selected nodes together
                for (String selId : screen.getSelectedNodeIds()) {
                    RecipeNode sn = screen.getGraph().findNodeById(selId);
                    if (sn != null) {
                        sn.setPos(sn.getPosX() + deltaX, sn.getPosY() + deltaY);
                    }
                }
            } else {
                draggingNode.getNode().setPos(draggingNode.getNode().getPosX() + deltaX, draggingNode.getNode().getPosY() + deltaY);
            }
            return true;
        }

        if (isPanning && (button == 1 || button == 2)) {
            screen.setPanX(screen.getPanX() + dragX);
            screen.setPanY(screen.getPanY() + dragY);
            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onPanOrZoom();
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double zoomFactor = delta > 0 ? 1.15 : 0.87;
        double oldZoom = screen.getZoom();
        double newZoom = Math.max(0.2, Math.min(3.0, oldZoom * zoomFactor));

        if (newZoom != oldZoom) {
            double canvasX = (mouseX - screen.getPanX()) / oldZoom;
            double canvasY = (mouseY - screen.getPanY()) / oldZoom;

            screen.setZoom(newZoom);
            screen.setPanX(mouseX - canvasX * newZoom);
            screen.setPanY(mouseY - canvasY * newZoom);
            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onPanOrZoom();
            return true;
        }

        return false;
    }

    private void notifyDisconnect(String translatableKey) {
        screen.markSummaryDirty();
        com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onWireDisconnected();
        BoardToast.show(Component.literal("§c✕ ").append(Component.translatable(translatableKey)));
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 1.2F));
    }

    public void renderMarquee(net.minecraft.client.gui.GuiGraphics graphics) {
        if (isBoxSelecting) {
            int minX = (int) Math.min(boxSelectStartX, boxSelectCurX);
            int maxX = (int) Math.max(boxSelectStartX, boxSelectCurX);
            int minY = (int) Math.min(boxSelectStartY, boxSelectCurY);
            int maxY = (int) Math.max(boxSelectStartY, boxSelectCurY);
            int w = Math.max(1, maxX - minX);
            int h = Math.max(1, maxY - minY);

            graphics.fill(minX, minY, maxX, maxY, 0x3300E5FF);
            graphics.renderOutline(minX, minY, w, h, 0xAA00E5FF);
        }
    }
}
