package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.event.FlowGraphEvent;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.ConnectionRenderer;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.common.MinecraftForge;

public class CanvasWireInteractionHandler {

    private NodeWidget wireStartNode = null;
    private int wireStartPortIdx = -1;
    private boolean wireStartIsInput = false;
    private long lastWireClickTime = 0;
    private FlowGraph.ConnectionEdge lastClickedEdge = null;

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

    public void cancelWireDrag() {
        this.wireStartNode = null;
        this.wireStartPortIdx = -1;
        this.wireStartIsInput = false;
    }

    public boolean handlePortClick(
            NodeWidget widget,
            double canvasMouseX,
            double canvasMouseY,
            int button,
            BoardScreen screen
    ) {
        int outPortIdx = widget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
        if (outPortIdx >= 0) {
            return handleOutputPortClick(widget, outPortIdx, button, screen);
        }

        int inPortIdx = widget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
        if (inPortIdx >= 0) {
            return handleInputPortClick(widget, inPortIdx, button, screen);
        }

        return false;
    }

    private boolean handleOutputPortClick(NodeWidget widget, int outPortIdx, int button, BoardScreen screen) {
        if (!screen.ensureEditPermission()) return true;

        if (button == 1) {
            if (NodeWidget.isVoidToggleModifier()) {
                widget.toggleOutputPortVoid(outPortIdx);
                return true;
            }
            widget.hidePortAndDisconnectWires(false, outPortIdx);
            return true;
        }

        if (button != 0) return false;

        boolean ctrl = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();
        String nodeId = widget.getNode().getId();

        if (ctrl) {
            screen.openTargetOutputRateDialog(widget.getNode(), outPortIdx);
            return true;
        }
        if (shift) {
            screen.selectPortRange(nodeId, false, outPortIdx);
            return true;
        }

        if (!screen.isPortSelected(nodeId, false, outPortIdx) || screen.getSelectedPorts().size() <= 1) {
            screen.clearPortSelection();
            screen.selectPort(nodeId, false, outPortIdx, false);
        }

        wireStartNode = widget;
        wireStartPortIdx = outPortIdx;
        wireStartIsInput = false;
        return true;
    }

    private boolean handleInputPortClick(NodeWidget widget, int inPortIdx, int button, BoardScreen screen) {
        if (!screen.ensureEditPermission()) return true;

        if (button == 1) {
            widget.hidePortAndDisconnectWires(true, inPortIdx);
            return true;
        }

        if (button != 0) return false;

        boolean ctrl = Screen.hasControlDown();
        boolean shift = Screen.hasShiftDown();
        String nodeId = widget.getNode().getId();

        if (ctrl) {
            screen.toggleSelectPort(nodeId, true, inPortIdx);
            return true;
        }
        if (shift) {
            screen.selectPortRange(nodeId, true, inPortIdx);
            return true;
        }

        if (!screen.isPortSelected(nodeId, true, inPortIdx) || screen.getSelectedPorts().size() <= 1) {
            screen.clearPortSelection();
            screen.selectPort(nodeId, true, inPortIdx, false);
        }

        wireStartNode = widget;
        wireStartPortIdx = inPortIdx;
        wireStartIsInput = true;
        return true;
    }

    public boolean handleWireClick(
            double canvasMouseX,
            double canvasMouseY,
            int button,
            BoardScreen screen
    ) {
        FlowGraph.ConnectionEdge hoveredEdge = screen.findHoveredWire(canvasMouseX, canvasMouseY, 8.0);
        if (hoveredEdge == null) return false;

        if (button == 0) {
            return handleWireDoubleClick(hoveredEdge, canvasMouseX, canvasMouseY, screen);
        }
        if (button == 1) {
            return handleWireCut(hoveredEdge, screen);
        }
        return false;
    }

    private boolean handleWireDoubleClick(
            FlowGraph.ConnectionEdge clickedEdge,
            double canvasMouseX,
            double canvasMouseY,
            BoardScreen screen
    ) {
        FlowGraph graph = screen.getGraph();
        if (graph == null) return false;

        RecipeNode fromNode = graph.findNodeById(clickedEdge.fromNodeId());
        RecipeNode toNode = graph.findNodeById(clickedEdge.toNodeId());
        if (fromNode == null || toNode == null) return false;

        long now = System.currentTimeMillis();
        if (now - lastWireClickTime < 350 && clickedEdge.equals(lastClickedEdge)) {
            if (screen.ensureEditPermission()) {
                insertJunctionNode(clickedEdge, fromNode, toNode, canvasMouseX, canvasMouseY, screen);
                lastWireClickTime = 0;
                lastClickedEdge = null;
                return true;
            }
        }
        lastWireClickTime = now;
        lastClickedEdge = clickedEdge;
        return true;
    }

    private void insertJunctionNode(
            FlowGraph.ConnectionEdge clickedEdge,
            RecipeNode fromNode,
            RecipeNode toNode,
            double canvasMouseX,
            double canvasMouseY,
            BoardScreen screen
    ) {
        FlowGraph graph = screen.getGraph();
        RecipeNode reroute = RecipeNode.createReroute(canvasMouseX - 16, canvasMouseY - 16);
        if (clickedEdge.outputIndex() < fromNode.getOutputs().size()) {
            reroute.bindRerouteIngredient(fromNode.getOutputs().get(clickedEdge.outputIndex()));
        }
        graph.addNode(reroute);
        graph.removeConnection(clickedEdge);
        graph.addConnection(fromNode.getId(), clickedEdge.outputIndex(), reroute.getId(), 0, clickedEdge.fixedFlowLimit(), clickedEdge.priority());
        graph.addConnection(reroute.getId(), 0, toNode.getId(), clickedEdge.inputIndex(), clickedEdge.fixedFlowLimit(), clickedEdge.priority());

        screen.rebuildWidgets();
        screen.markSummaryDirty();
        MinecraftForge.EVENT_BUS.post(new FlowGraphEvent.JunctionInserted(graph, clickedEdge, reroute));
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }

    private boolean handleWireCut(FlowGraph.ConnectionEdge cutEdge, BoardScreen screen) {
        if (!screen.ensureEditPermission()) return true;

        FlowGraph graph = screen.getGraph();
        if (graph == null) return false;

        graph.removeConnection(cutEdge);
        screen.recordCommand(new BoardCommand.DisconnectWireCommand(cutEdge));
        notifyDisconnect("message.gtcalcboard.disconnect_wire", screen);
        MinecraftForge.EVENT_BUS.post(new FlowGraphEvent.WireDisconnected(graph, cutEdge));
        return true;
    }

    private void notifyDisconnect(String translatableKey, BoardScreen screen) {
        screen.markSummaryDirty();
        BoardToast.show(Component.literal("§c✕ ").append(Component.translatable(translatableKey)));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 1.2F));
    }

    public boolean handleWireScroll(
            double canvasMouseX,
            double canvasMouseY,
            double delta,
            BoardScreen screen
    ) {
        if (screen == null) return false;
        FlowGraph.ConnectionEdge hoveredEdge = screen.findHoveredWire(canvasMouseX, canvasMouseY, 8.0);
        if (hoveredEdge == null) return false;

        if (!screen.ensureEditPermission()) return true;
        FlowGraph graph = screen.getGraph();
        if (graph == null) return true;

        int deltaPri = delta > 0 ? 1 : -1;
        int currentPri = hoveredEdge.priority();
        int newPri = Math.max(0, Math.min(99, currentPri + deltaPri));

        if (newPri != currentPri) {
            graph.setConnectionPriority(
                    hoveredEdge.fromNodeId(),
                    hoveredEdge.outputIndex(),
                    hoveredEdge.toNodeId(),
                    hoveredEdge.inputIndex(),
                    newPri
            );
            screen.markSummaryDirty();
            if (screen.getWireRenderer() != null) {
                screen.getWireRenderer().markDirty();
            }
            playPriorityChangeFeedback(newPri);
        }
        return true;
    }

    private void playPriorityChangeFeedback(int newPri) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            float pitch = (float) Math.min(2.0, 1.0 + (newPri * 0.05));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch));
        }
        BoardToast.show(
                Component.literal("§6★ ").append(
                        Component.translatable("gui.gtcalcboard.toast.priority_changed", String.valueOf(newPri))
                )
        );
    }

    public boolean handleWireReleased(
            double mouseX,
            double mouseY,
            int button,
            BoardScreen screen,
            CanvasQuickAddMarkerHandler quickAddMarkerHandler
    ) {
        if (wireStartNode == null || button != 0) return false;

        double canvasMouseX = screen.toCanvasX(mouseX);
        double canvasMouseY = screen.toCanvasY(mouseY);
        FlowGraph graph = screen.getGraph();

        boolean isBundle = screen.hasSelectedPorts()
                && screen.isPortSelected(wireStartNode.getNode().getId(), wireStartIsInput, wireStartPortIdx)
                && screen.getSelectedPorts().size() > 1;

        if (isBundle) {
            CanvasBundleWiringHandler.handleBundleDrop(canvasMouseX, canvasMouseY, wireStartNode, screen);
        } else {
            CanvasSingleWireHandler.handleSingleWireDrop(wireStartNode, wireStartPortIdx, wireStartIsInput, canvasMouseX, canvasMouseY, screen, graph, quickAddMarkerHandler);
        }

        cancelWireDrag();
        return true;
    }

    public void renderWireDrag(GuiGraphics graphics, BoardScreen screen, double mouseX, double mouseY) {
        if (wireStartNode == null) return;

        double canvasMouseX = screen.toCanvasX(mouseX);
        double canvasMouseY = screen.toCanvasY(mouseY);

        double startX = wireStartIsInput ? wireStartNode.getInputPortX(wireStartPortIdx) : wireStartNode.getOutputPortX(wireStartPortIdx);
        double startY = wireStartIsInput ? wireStartNode.getInputPortY(wireStartPortIdx) : wireStartNode.getOutputPortY(wireStartPortIdx);

        int wireColor = 0xFF38BDF8;
        if (wireStartIsInput) {
            if (wireStartPortIdx >= 0 && wireStartPortIdx < wireStartNode.getNode().getInputs().size()) {
                wireColor = wireStartNode.getNode().getInputs().get(wireStartPortIdx).isFluid() ? 0xFF0284C7 : 0xFFF59E0B;
            }
            ConnectionRenderer.renderBezier(graphics, (float) canvasMouseX, (float) canvasMouseY, (float) startX, (float) startY, wireColor, 2.5F);
        } else {
            if (wireStartPortIdx >= 0 && wireStartPortIdx < wireStartNode.getNode().getOutputs().size()) {
                wireColor = wireStartNode.getNode().getOutputs().get(wireStartPortIdx).isFluid() ? 0xFF0284C7 : 0xFFF59E0B;
            }
            ConnectionRenderer.renderBezier(graphics, (float) startX, (float) startY, (float) canvasMouseX, (float) canvasMouseY, wireColor, 2.5F);
        }
    }
}
