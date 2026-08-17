package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles canvas panning, zooming, node dragging, wire connections, and line cut interactions.
 */
public class CanvasInteractionHandler {
    private final BoardScreen screen;

    // Canvas Panning State
    private boolean isPanning = false;
    private double panStartX, panStartY;

    // Node Dragging State
    private NodeWidget draggingNode = null;
    private double dragOffsetNodeX, dragOffsetNodeY;

    // Interactive Wire Dragging State
    private NodeWidget wireStartNode = null;
    private int wireStartOutputIdx = -1;

    public CanvasInteractionHandler(BoardScreen screen) {
        this.screen = screen;
    }

    public NodeWidget getWireStartNode() {
        return wireStartNode;
    }

    public int getWireStartOutputIdx() {
        return wireStartOutputIdx;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        double canvasMouseX = screen.toCanvasX(mouseX);
        double canvasMouseY = screen.toCanvasY(mouseY);
        List<NodeWidget> nodeWidgets = screen.getNodeWidgets();
        FlowGraph graph = screen.getGraph();

        // 1. Check Node Widgets from top to bottom
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            if (widget.isPointInside(canvasMouseX, canvasMouseY)) {

                // Output Port: Left click to start wire, Right click to disconnect
                int outPortIdx = widget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
                if (outPortIdx >= 0) {
                    if (button == 0) {
                        wireStartNode = widget;
                        wireStartOutputIdx = outPortIdx;
                        return true;
                    } else if (button == 1) {
                        boolean removed = graph.getConnections().removeIf(e -> e.fromNodeId().equals(widget.getNode().getId()) && e.outputIndex() == outPortIdx);
                        if (removed) {
                            notifyDisconnect("message.gtcalcboard.disconnect_out");
                        }
                        return true;
                    }
                }

                // Input Port: Right click to disconnect
                int inPortIdx = widget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
                if (inPortIdx >= 0 && button == 1) {
                    boolean removed = graph.getConnections().removeIf(e -> e.toNodeId().equals(widget.getNode().getId()) && e.inputIndex() == inPortIdx);
                    if (removed) {
                        notifyDisconnect("message.gtcalcboard.disconnect_in");
                    }
                    return true;
                }

                // Widget internal controls (Count, Tier, Base button, Close button)
                if (widget.mouseClicked(canvasMouseX, canvasMouseY, button)) {
                    return true;
                }

                // Title bar Header click -> Start node dragging
                if (widget.isHeaderHovered(canvasMouseX, canvasMouseY) && button == 0) {
                    draggingNode = widget;
                    dragOffsetNodeX = canvasMouseX - widget.getNode().getPosX();
                    dragOffsetNodeY = canvasMouseY - widget.getNode().getPosY();
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
                            graph.getConnections().remove(edge);
                            notifyDisconnect("message.gtcalcboard.disconnect_wire");
                            return true;
                        }
                    }
                }
            }
        }

        // 3. Click outside -> Commit count edits
        for (NodeWidget w : nodeWidgets) {
            w.commitCountEdit();
        }

        // 4. Canvas Pan start (Right click or Middle click)
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

                for (NodeWidget targetWidget : nodeWidgets) {
                    if (targetWidget != wireStartNode && targetWidget.isPointInside(canvasMouseX, canvasMouseY)) {
                        int inPortIdx = targetWidget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
                        if (inPortIdx >= 0) {
                            RecipeNode fromNode = wireStartNode.getNode();
                            RecipeNode toNode = targetWidget.getNode();
                            graph.addConnection(fromNode.getId(), wireStartOutputIdx, toNode.getId(), inPortIdx);

                            boolean shiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                            if (shiftDown && wireStartOutputIdx < fromNode.getOutputs().size() && inPortIdx < toNode.getInputs().size()) {
                                IngredientStack outStack = fromNode.getOutputs().get(wireStartOutputIdx);
                                double producedRate = fromNode.getCyclesPerSecond() * outStack.getExpectedAmount();

                                IngredientStack inStack = toNode.getInputs().get(inPortIdx);
                                double singleInRate = toNode.getOverclockResult().getCyclesPerSecond() * toNode.getParallel() * inStack.getAmount();

                                if (singleInRate > 0.0001) {
                                    double matchedCount = Math.max(0.01, Math.round((producedRate / singleInRate) * 100.0) / 100.0);
                                    toNode.setMachineCount(matchedCount);
                                    targetWidget.updateCountBuffer();
                                    targetWidget.invalidateCache();

                                    Minecraft mc = Minecraft.getInstance();
                                    if (mc.player != null) {
                                        mc.player.displayClientMessage(Component.literal("§a✔ ").append(
                                            Component.translatable("message.gtcalcboard.shift_connect_matched", toNode.getName(), String.format("%.2f", matchedCount))
                                        ), true);
                                    }
                                    mc.getSoundManager().play(
                                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                            SoundEvents.PLAYER_LEVELUP, 1.2F
                                        )
                                    );
                                }
                            } else {
                                Minecraft.getInstance().getSoundManager().play(
                                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                                        SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F
                                    )
                                );
                            }
                            screen.markSummaryDirty();
                            break;
                        }
                    }
                }
                wireStartNode = null;
                wireStartOutputIdx = -1;
                return true;
            }

            if (draggingNode != null) {
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
        if (draggingNode != null && button == 0) {
            double canvasMouseX = screen.toCanvasX(mouseX);
            double canvasMouseY = screen.toCanvasY(mouseY);
            draggingNode.getNode().setPosX(canvasMouseX - dragOffsetNodeX);
            draggingNode.getNode().setPosY(canvasMouseY - dragOffsetNodeY);
            return true;
        }

        if (isPanning) {
            screen.setPanX(mouseX - panStartX);
            screen.setPanY(mouseY - panStartY);
            BoardScreen.lastPanX = screen.getPanX();
            BoardScreen.lastPanY = screen.getPanY();
            return true;
        }

        return false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double canvasMouseX = screen.toCanvasX(mouseX);
        double canvasMouseY = screen.toCanvasY(mouseY);

        // Check if any NodeWidget handles the scroll event (e.g. tier selector button)
        List<NodeWidget> nodeWidgets = screen.getNodeWidgets();
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            if (widget.isPointInside(canvasMouseX, canvasMouseY)) {
                if (widget.mouseScrolled(canvasMouseX, canvasMouseY, delta)) {
                    return true;
                }
            }
        }

        double oldZoom = screen.getZoom();
        double newZoom = oldZoom;
        if (delta > 0) {
            newZoom = Math.min(2.0, oldZoom * 1.15);
        } else if (delta < 0) {
            newZoom = Math.max(0.4, oldZoom / 1.15);
        }

        // Zoom toward mouse pointer
        screen.setPanX(mouseX - (mouseX - screen.getPanX()) * (newZoom / oldZoom));
        screen.setPanY(mouseY - (mouseY - screen.getPanY()) * (newZoom / oldZoom));
        screen.setZoom(newZoom);
        BoardScreen.lastPanX = screen.getPanX();
        BoardScreen.lastPanY = screen.getPanY();
        BoardScreen.lastZoom = screen.getZoom();
        return true;
    }

    private void notifyDisconnect(String translationKey) {
        screen.markSummaryDirty();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§e✂ ").append(Component.translatable(translationKey)), true);
        }
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.LEASH_KNOT_BREAK, 1.2F));
    }
}
