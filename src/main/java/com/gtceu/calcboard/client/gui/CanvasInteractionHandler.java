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
 * Handles canvas panning, zooming, node dragging, bidirectional wire connections, and line cut interactions.
 */
public class CanvasInteractionHandler {
    private final BoardScreen screen;

    // Canvas Panning State
    private boolean isPanning = false;
    private double panStartX, panStartY;

    // Node Dragging State
    private NodeWidget draggingNode = null;
    private double dragOffsetNodeX, dragOffsetNodeY;

    // Wire Connection Dragging State
    private NodeWidget wireStartNode = null;
    private int wireStartPortIdx = -1;
    private boolean wireStartIsInput = false;

    public CanvasInteractionHandler(BoardScreen screen) {
        this.screen = screen;
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
        double canvasMouseX = screen.toCanvasX(mouseX);
        double canvasMouseY = screen.toCanvasY(mouseY);
        List<NodeWidget> nodeWidgets = screen.getNodeWidgets();
        FlowGraph graph = screen.getGraph();

        // 1. Check Node Widgets from top to bottom
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            if (widget.isPointInside(canvasMouseX, canvasMouseY)) {

                // Output Port: Left click to start forward wire, Right click to disconnect
                int outPortIdx = widget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
                if (outPortIdx >= 0) {
                    if (button == 0) {
                        wireStartNode = widget;
                        wireStartPortIdx = outPortIdx;
                        wireStartIsInput = false;
                        return true;
                    } else if (button == 1) {
                        boolean removed = graph.getConnections().removeIf(e -> e.fromNodeId().equals(widget.getNode().getId()) && e.outputIndex() == outPortIdx);
                        if (removed) {
                            notifyDisconnect("message.gtcalcboard.disconnect_out");
                        }
                        return true;
                    }
                }

                // Input Port: Left click to start reverse wire, Right click to disconnect
                int inPortIdx = widget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
                if (inPortIdx >= 0) {
                    if (button == 0) {
                        wireStartNode = widget;
                        wireStartPortIdx = inPortIdx;
                        wireStartIsInput = true;
                        return true;
                    } else if (button == 1) {
                        boolean removed = graph.getConnections().removeIf(e -> e.toNodeId().equals(widget.getNode().getId()) && e.inputIndex() == inPortIdx);
                        if (removed) {
                            notifyDisconnect("message.gtcalcboard.disconnect_in");
                        }
                        return true;
                    }
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
                        
                        // Case A: Forward drag (Output -> Input)
                        if (!wireStartIsInput) {
                            int inPortIdx = targetWidget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
                            if (inPortIdx >= 0) {
                                RecipeNode fromNode = wireStartNode.getNode();
                                RecipeNode toNode = targetWidget.getNode();
                                graph.addConnection(fromNode.getId(), wireStartPortIdx, toNode.getId(), inPortIdx);

                                boolean shiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                                if (shiftDown && wireStartPortIdx < fromNode.getOutputs().size() && inPortIdx < toNode.getInputs().size()) {
                                    IngredientStack outStack = fromNode.getOutputs().get(wireStartPortIdx);
                                    double producedRate = fromNode.getCyclesPerSecond() * outStack.getExpectedAmount();

                                    IngredientStack inStack = toNode.getInputs().get(inPortIdx);
                                    double singleInRate = toNode.getOverclockResult().getCyclesPerSecond() * toNode.getParallel() * inStack.getAmount();

                                    if (singleInRate > 0.0001) {
                                        double matchedCount = Math.max(1.0, Math.ceil((producedRate / singleInRate) - 0.00001));
                                        toNode.setMachineCount(matchedCount);
                                        targetWidget.updateCountBuffer();
                                        targetWidget.invalidateCache();

                                        Minecraft mc = Minecraft.getInstance();
                                        if (mc.player != null) {
                                            mc.player.displayClientMessage(Component.literal("§a✔ ").append(
                                                Component.translatable("message.gtcalcboard.shift_connect_matched", toNode.getName(), String.format("%.0f", matchedCount))
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
                        // Case B: Reverse drag (Input -> Output) - match upstream producer count to downstream consumer requirement
                        else {
                            int outPortIdx = targetWidget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
                            if (outPortIdx >= 0) {
                                RecipeNode fromNode = targetWidget.getNode();   // Producer
                                RecipeNode toNode = wireStartNode.getNode();    // Consumer
                                graph.addConnection(fromNode.getId(), outPortIdx, toNode.getId(), wireStartPortIdx);

                                boolean shiftDown = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                                if (shiftDown && outPortIdx < fromNode.getOutputs().size() && wireStartPortIdx < toNode.getInputs().size()) {
                                    IngredientStack inStack = toNode.getInputs().get(wireStartPortIdx);
                                    double neededRate = toNode.getCyclesPerSecond() * inStack.getAmount();

                                    IngredientStack outStack = fromNode.getOutputs().get(outPortIdx);
                                    double singleOutRate = fromNode.getOverclockResult().getCyclesPerSecond() * fromNode.getParallel() * outStack.getExpectedAmount();

                                    if (singleOutRate > 0.0001) {
                                        double matchedCount = Math.max(1.0, Math.ceil((neededRate / singleOutRate) - 0.00001));
                                        fromNode.setMachineCount(matchedCount);
                                        targetWidget.updateCountBuffer();
                                        targetWidget.invalidateCache();

                                        Minecraft mc = Minecraft.getInstance();
                                        if (mc.player != null) {
                                            mc.player.displayClientMessage(Component.literal("§a✔ ").append(
                                                Component.translatable("message.gtcalcboard.shift_connect_matched", fromNode.getName(), String.format("%.0f", matchedCount))
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
                }
                wireStartNode = null;
                wireStartPortIdx = -1;
                wireStartIsInput = false;
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

        if (isPanning && (button == 1 || button == 2)) {
            screen.setPanX(screen.getPanX() + dragX);
            screen.setPanY(screen.getPanY() + dragY);
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
            return true;
        }

        return false;
    }

    private void notifyDisconnect(String translatableKey) {
        screen.markSummaryDirty();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§c✕ ").append(Component.translatable(translatableKey)), true);
        }
        mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 1.2F));
    }
}
