package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.editor.NodeCountEditor;
import com.gtceu.calcboard.client.gui.model.PortRef;
import com.gtceu.calcboard.client.gui.render.ConnectionRenderer;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
            screen.toggleSelectPort(nodeId, false, outPortIdx);
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
        graph.getConnections().remove(clickedEdge);
        graph.addConnection(fromNode.getId(), clickedEdge.outputIndex(), reroute.getId(), 0);
        graph.addConnection(reroute.getId(), 0, toNode.getId(), clickedEdge.inputIndex());

        screen.rebuildWidgets();
        screen.markSummaryDirty();
        TutorialManager.getInstance().onJunctionInserted();
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F)
        );
    }

    private boolean handleWireCut(FlowGraph.ConnectionEdge cutEdge, BoardScreen screen) {
        if (!screen.ensureEditPermission()) return true;

        FlowGraph graph = screen.getGraph();
        if (graph == null) return false;

        graph.getConnections().remove(cutEdge);
        screen.recordCommand(new BoardCommand.DisconnectWireCommand(cutEdge));
        notifyDisconnect("message.gtcalcboard.disconnect_wire", screen);
        return true;
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
            handleBundleDrop(canvasMouseX, canvasMouseY, screen);
        } else {
            handleSingleWireDrop(canvasMouseX, canvasMouseY, screen, graph, quickAddMarkerHandler);
        }

        cancelWireDrag();
        return true;
    }

    private void handleBundleDrop(double canvasMouseX, double canvasMouseY, BoardScreen screen) {
        Set<PortRef> bundlePorts = new HashSet<>(screen.getSelectedPorts());
        FlowGraph graph = screen.getGraph();

        CanvasGroupFrame targetFrame = findTargetFrame(canvasMouseX, canvasMouseY, graph);
        if (targetFrame != null) {
            handleBundleConnectToFrame(targetFrame, bundlePorts, screen);
            screen.clearPortSelection();
            return;
        }

        NodeWidget targetWidget = findTargetNodeWidget(canvasMouseX, canvasMouseY, screen);
        if (targetWidget != null) {
            handleBundleConnectToNode(targetWidget, bundlePorts, screen);
            screen.clearPortSelection();
            return;
        }

        handleBundleCreateJunctions(canvasMouseX, canvasMouseY, bundlePorts, screen);
        screen.clearPortSelection();
    }

    private CanvasGroupFrame findTargetFrame(double canvasMouseX, double canvasMouseY, FlowGraph graph) {
        if (graph == null) return null;
        for (CanvasGroupFrame f : graph.getFrames()) {
            if (canvasMouseX >= f.getPosX() && canvasMouseX <= f.getPosX() + f.getWidth() &&
                    canvasMouseY >= f.getPosY() && canvasMouseY <= f.getPosY() + f.getHeight()) {
                return f;
            }
        }
        return null;
    }

    private NodeWidget findTargetNodeWidget(double canvasMouseX, double canvasMouseY, BoardScreen screen) {
        for (NodeWidget targetWidget : screen.getNodeWidgets()) {
            if (targetWidget != wireStartNode && targetWidget.isPointInside(canvasMouseX, canvasMouseY)) {
                return targetWidget;
            }
        }
        return null;
    }

    private void handleSingleWireDrop(
            double canvasMouseX,
            double canvasMouseY,
            BoardScreen screen,
            FlowGraph graph,
            CanvasQuickAddMarkerHandler quickAddMarkerHandler
    ) {
        boolean connected = false;
        for (NodeWidget targetWidget : screen.getNodeWidgets()) {
            if (targetWidget != wireStartNode && targetWidget.isPointInside(canvasMouseX, canvasMouseY)) {
                connected = tryConnectToPort(targetWidget, canvasMouseX, canvasMouseY, graph, screen);
                if (connected) break;
            }
        }

        if (!connected && screen.getSearchDialog() != null) {
            handleContextualWireDrag(canvasMouseX, canvasMouseY, quickAddMarkerHandler);
        }
        screen.clearPortSelection();
    }

    private boolean tryConnectToPort(
            NodeWidget targetWidget,
            double canvasMouseX,
            double canvasMouseY,
            FlowGraph graph,
            BoardScreen screen
    ) {
        if (!wireStartIsInput) {
            int inPortIdx = targetWidget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
            if (inPortIdx >= 0) {
                handleForwardWireConnect(targetWidget, graph, inPortIdx, screen);
                return true;
            }
        } else {
            int outPortIdx = targetWidget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
            if (outPortIdx >= 0) {
                handleReverseWireConnect(targetWidget, graph, outPortIdx, screen);
                return true;
            }
        }
        return false;
    }

    private void handleForwardWireConnect(NodeWidget targetWidget, FlowGraph graph, int inPortIdx, BoardScreen screen) {
        RecipeNode fromNode = wireStartNode.getNode();
        RecipeNode toNode = targetWidget.getNode();

        bindRerouteStacks(fromNode, toNode, wireStartPortIdx, inPortIdx);
        alignAlternativeFluids(fromNode, toNode, wireStartPortIdx, inPortIdx, targetWidget, wireStartNode);

        FlowGraph.ConnectionEdge newEdge = new FlowGraph.ConnectionEdge(fromNode.getId(), wireStartPortIdx, toNode.getId(), inPortIdx);
        graph.addConnection(fromNode.getId(), wireStartPortIdx, toNode.getId(), inPortIdx);

        boolean shiftDown = Screen.hasShiftDown();
        Double oldMachineCount = shiftDown ? toNode.getMachineCount() : null;
        Double newMachineCount = null;

        if (shiftDown && wireStartPortIdx < fromNode.getOutputs().size() && inPortIdx < toNode.getInputs().size()) {
            double matchedCount = FlowGraphSolver.calculateConsumerMatchCount(graph, fromNode, wireStartPortIdx, toNode, inPortIdx);
            newMachineCount = matchedCount;
            toNode.setMachineCount(matchedCount);
            targetWidget.updateCountBuffer();
            targetWidget.invalidateCache();

            BoardToast.show(Component.literal("§a✔ ").append(
                    Component.translatable("message.gtcalcboard.shift_connect_matched", toNode.getName(), NodeCountEditor.formatCount(matchedCount))
            ));
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F)
            );
        } else {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
            );
        }

        screen.recordCommand(new BoardCommand.ConnectWireCommand(newEdge, shiftDown ? toNode.getId() : null, oldMachineCount, newMachineCount));
        screen.markSummaryDirty();
        TutorialManager.getInstance().onWireConnected(shiftDown);
    }

    private void handleReverseWireConnect(NodeWidget targetWidget, FlowGraph graph, int outPortIdx, BoardScreen screen) {
        RecipeNode fromNode = targetWidget.getNode();
        RecipeNode toNode = wireStartNode.getNode();

        bindRerouteStacks(fromNode, toNode, outPortIdx, wireStartPortIdx);
        alignAlternativeFluids(fromNode, toNode, outPortIdx, wireStartPortIdx, targetWidget, wireStartNode);

        FlowGraph.ConnectionEdge newEdge = new FlowGraph.ConnectionEdge(fromNode.getId(), outPortIdx, toNode.getId(), wireStartPortIdx);
        graph.addConnection(fromNode.getId(), outPortIdx, toNode.getId(), wireStartPortIdx);

        boolean shiftDown = Screen.hasShiftDown();
        Double oldMachineCount = shiftDown ? fromNode.getMachineCount() : null;
        Double newMachineCount = null;

        if (shiftDown && outPortIdx < fromNode.getOutputs().size() && wireStartPortIdx < toNode.getInputs().size()) {
            double matchedCount = FlowGraphSolver.calculateProducerMatchCount(graph, fromNode, outPortIdx, toNode, wireStartPortIdx);
            newMachineCount = matchedCount;
            fromNode.setMachineCount(matchedCount);
            targetWidget.updateCountBuffer();
            targetWidget.invalidateCache();

            BoardToast.show(Component.literal("§a✔ ").append(
                    Component.translatable("message.gtcalcboard.shift_connect_matched", fromNode.getName(), NodeCountEditor.formatCount(matchedCount))
            ));
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F)
            );
        } else {
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
            );
        }

        screen.recordCommand(new BoardCommand.ConnectWireCommand(newEdge, shiftDown ? fromNode.getId() : null, oldMachineCount, newMachineCount));
        screen.markSummaryDirty();
        TutorialManager.getInstance().onWireConnected(shiftDown);
    }

    private void bindRerouteStacks(RecipeNode fromNode, RecipeNode toNode, int outIdx, int inIdx) {
        if (fromNode.isReroute() && inIdx < toNode.getInputs().size()) {
            fromNode.bindRerouteIngredient(toNode.getInputs().get(inIdx));
        } else if (toNode.isReroute() && outIdx < fromNode.getOutputs().size()) {
            toNode.bindRerouteIngredient(fromNode.getOutputs().get(outIdx));
        }
    }

    private void alignAlternativeFluids(
            RecipeNode fromNode,
            RecipeNode toNode,
            int outIdx,
            int inIdx,
            NodeWidget w1,
            NodeWidget w2
    ) {
        if (outIdx >= fromNode.getOutputs().size() || inIdx >= toNode.getInputs().size()) return;

        IngredientStack outStack = fromNode.getOutputs().get(outIdx);
        IngredientStack inStack = toNode.getInputs().get(inIdx);

        if (inStack.isFluid() && SysteamsRecipeHandler.isDynamoToBoilerConvertible(toNode)) {
            inStack.setAlternatives(SysteamsRecipeHandler.getAllBoilingFluidInputs());
        }

        boolean matched = matchesIngredient(outStack, inStack);
        if (matched) {
            if (inStack.isFluid() && SysteamsRecipeHandler.isDynamoToBoilerConvertible(toNode)) {
                SysteamsRecipeHandler.updateBoilerFluidRecipe(toNode, inStack.getId());
            }
            if (w1 != null) w1.invalidateCache();
            if (w2 != null) w2.invalidateCache();
        }
    }

    private void handleContextualWireDrag(
            double canvasMouseX,
            double canvasMouseY,
            CanvasQuickAddMarkerHandler quickAddMarkerHandler
    ) {
        double startPortX = wireStartIsInput ? wireStartNode.getInputPortX(wireStartPortIdx) : wireStartNode.getOutputPortX(wireStartPortIdx);
        double startPortY = wireStartIsInput ? wireStartNode.getInputPortY(wireStartPortIdx) : wireStartNode.getOutputPortY(wireStartPortIdx);
        double dragDist = Math.hypot(canvasMouseX - startPortX, canvasMouseY - startPortY);

        if (dragDist >= 15.0) {
            RecipeNode srcNode = wireStartNode.getNode();
            boolean shiftDown = Screen.hasShiftDown();
            IngredientStack stack = extractWireStartStack(srcNode);
            quickAddMarkerHandler.triggerContextualMarker(canvasMouseX, canvasMouseY, srcNode, wireStartPortIdx, wireStartIsInput, stack, shiftDown);
        }
    }

    private IngredientStack extractWireStartStack(RecipeNode srcNode) {
        if (wireStartIsInput) {
            if (wireStartPortIdx >= 0 && wireStartPortIdx < srcNode.getInputs().size()) {
                return srcNode.getInputs().get(wireStartPortIdx);
            }
        } else {
            if (wireStartPortIdx >= 0 && wireStartPortIdx < srcNode.getOutputs().size()) {
                return srcNode.getOutputs().get(wireStartPortIdx);
            }
        }
        return null;
    }

    private boolean matchesIngredient(IngredientStack out, IngredientStack in) {
        if (out == null || in == null) return false;
        if (out.getId().equals(in.getId())) return true;
        if (in.getAlternatives() != null && in.getAlternatives().contains(out.getId())) {
            in.selectAlternative(out.getId());
            return true;
        }
        if (out.getAlternatives() != null && out.getAlternatives().contains(in.getId())) {
            out.selectAlternative(in.getId());
            return true;
        }
        if (in.getAlternatives() != null && out.getAlternatives() != null) {
            for (ResourceLocation alt : in.getAlternatives()) {
                if (out.getAlternatives().contains(alt)) {
                    in.selectAlternative(alt);
                    out.selectAlternative(alt);
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAlreadyConnected(FlowGraph graph, String fromNodeId, int outIdx, String toNodeId, int inIdx) {
        for (FlowGraph.ConnectionEdge e : graph.getConnections()) {
            if (e.fromNodeId().equals(fromNodeId) && e.outputIndex() == outIdx &&
                    e.toNodeId().equals(toNodeId) && e.inputIndex() == inIdx) {
                return true;
            }
        }
        return false;
    }

    private void notifyDisconnect(String translatableKey, BoardScreen screen) {
        screen.markSummaryDirty();
        TutorialManager.getInstance().onWireDisconnected();
        BoardToast.show(Component.literal("§c✕ ").append(Component.translatable(translatableKey)));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ITEM_BREAK, 1.2F));
    }

    private void handleBundleCreateJunctions(double canvasX, double canvasY, Set<PortRef> ports, BoardScreen screen) {
        FlowGraph graph = screen.getGraph();
        if (graph == null || ports == null || ports.isEmpty()) return;

        List<RecipeNode> createdNodes = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> createdEdges = new ArrayList<>();

        List<PortRef> sortedPorts = sortBundlePorts(ports, graph);
        int count = sortedPorts.size();
        double startY = canvasY - ((count - 1) * 36.0 / 2.0);
        screen.clearSelection();

        for (int i = 0; i < sortedPorts.size(); i++) {
            PortRef pref = sortedPorts.get(i);
            RecipeNode srcNode = graph.findNodeById(pref.nodeId());
            if (srcNode == null) continue;

            double jy = startY + (i * 36.0);
            createSingleJunction(canvasX, jy, pref, srcNode, graph, createdNodes, createdEdges);
        }

        if (!createdNodes.isEmpty()) {
            screen.rebuildWidgets();
            for (RecipeNode junction : createdNodes) {
                screen.selectNode(junction.getId(), true);
            }
            screen.recordCommand(new BoardCommand.AddNodesCommand(createdNodes, createdEdges, "Create " + count + " Junction Hubs"));
            screen.markSummaryDirty();
            BoardToast.show(Component.literal("§a🔀 ").append(
                    Component.translatable("gui.gtcalcboard.toast.bundle_junctions_created", count)
            ));
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
            );
        }
    }

    private List<PortRef> sortBundlePorts(Set<PortRef> ports, FlowGraph graph) {
        List<PortRef> sorted = new ArrayList<>(ports);
        sorted.sort((p1, p2) -> {
            RecipeNode n1 = graph.findNodeById(p1.nodeId());
            RecipeNode n2 = graph.findNodeById(p2.nodeId());
            if (n1 != null && n2 != null && !n1.getId().equals(n2.getId())) {
                int cmpY = Double.compare(n1.getPosY(), n2.getPosY());
                if (cmpY != 0) return cmpY;
            }
            return Integer.compare(p1.portIndex(), p2.portIndex());
        });
        return sorted;
    }

    private void createSingleJunction(
            double canvasX,
            double jy,
            PortRef pref,
            RecipeNode srcNode,
            FlowGraph graph,
            List<RecipeNode> createdNodes,
            List<FlowGraph.ConnectionEdge> createdEdges
    ) {
        RecipeNode junction = RecipeNode.createReroute(canvasX - 16, jy - 16);
        if (pref.isInput()) {
            if (pref.portIndex() < srcNode.getInputs().size()) {
                IngredientStack stack = srcNode.getInputs().get(pref.portIndex());
                junction.bindRerouteIngredient(stack);
                graph.addNode(junction);
                graph.addConnection(junction.getId(), 0, srcNode.getId(), pref.portIndex());
                createdNodes.add(junction);
                createdEdges.add(new FlowGraph.ConnectionEdge(junction.getId(), 0, srcNode.getId(), pref.portIndex()));
            }
        } else {
            if (pref.portIndex() < srcNode.getOutputs().size()) {
                IngredientStack stack = srcNode.getOutputs().get(pref.portIndex());
                junction.bindRerouteIngredient(stack);
                graph.addNode(junction);
                graph.addConnection(srcNode.getId(), pref.portIndex(), junction.getId(), 0);
                createdNodes.add(junction);
                createdEdges.add(new FlowGraph.ConnectionEdge(srcNode.getId(), pref.portIndex(), junction.getId(), 0));
            }
        }
    }

    private void handleBundleConnectToFrame(CanvasGroupFrame frame, Set<PortRef> ports, BoardScreen screen) {
        FlowGraph graph = screen.getGraph();
        if (graph == null || frame == null || ports == null || ports.isEmpty()) return;

        List<RecipeNode> frameNodes = new ArrayList<>(frame.getEnclosedNodes(graph));
        if (frameNodes.isEmpty()) {
            for (String nid : frame.getContainedNodeIds()) {
                RecipeNode n = graph.findNodeById(nid);
                if (n != null && !n.isReroute()) frameNodes.add(n);
            }
        }
        if (frameNodes.isEmpty()) return;

        RecipeNode templateNode = frame.getFirstOperationalNode(graph);
        if (templateNode == null) templateNode = frameNodes.get(0);

        double origFrameX = frame.getPosX();
        double origFrameY = frame.getPosY();
        double origFrameW = frame.getWidth();
        double origFrameH = frame.getHeight();

        double curMaxY = calculateFrameMaxY(frame, frameNodes);
        double targetPosX = frameNodes.get(0).getPosX();

        List<RecipeNode> createdNodes = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> createdEdges = new ArrayList<>();
        List<BoardCommand> subCmds = new ArrayList<>();
        int connectedCount = 0;
        int spawnedCount = 0;
        boolean shiftDown = Screen.hasShiftDown();

        for (PortRef pref : ports) {
            RecipeNode srcNode = graph.findNodeById(pref.nodeId());
            if (srcNode == null) continue;

            if (!pref.isInput()) {
                connectedCount += connectBundleOutputToFrame(pref, srcNode, frame, frameNodes, templateNode, targetPosX, curMaxY, graph, screen, shiftDown, subCmds, createdNodes, createdEdges);
            } else {
                connectedCount += connectBundleInputToFrame(pref, srcNode, frameNodes, graph, screen, shiftDown, subCmds);
            }
        }

        finalizeBundleFrameConnection(frame, frameNodes, createdNodes, createdEdges, subCmds, connectedCount, spawnedCount, origFrameX, origFrameY, origFrameW, origFrameH, screen);
    }

    private double calculateFrameMaxY(CanvasGroupFrame frame, List<RecipeNode> frameNodes) {
        double curMaxY = -Double.MAX_VALUE;
        for (RecipeNode n : frameNodes) {
            double nh = n.getCardHeight() > 0 ? n.getCardHeight() : (n.isReroute() ? 32 : 160);
            curMaxY = Math.max(curMaxY, n.getPosY() + nh);
        }
        if (curMaxY <= -100000) {
            return frame.getPosY() + CanvasGroupFrame.HEADER_HEIGHT + 16;
        }
        return curMaxY + 16;
    }

    private int connectBundleOutputToFrame(
            PortRef pref,
            RecipeNode srcNode,
            CanvasGroupFrame frame,
            List<RecipeNode> frameNodes,
            RecipeNode templateNode,
            double targetPosX,
            double curMaxY,
            FlowGraph graph,
            BoardScreen screen,
            boolean shiftDown,
            List<BoardCommand> subCmds,
            List<RecipeNode> createdNodes,
            List<FlowGraph.ConnectionEdge> createdEdges
    ) {
        if (pref.portIndex() >= srcNode.getOutputs().size()) return 0;
        IngredientStack srcOut = srcNode.getOutputs().get(pref.portIndex());

        for (RecipeNode tn : frameNodes) {
            if (tn.getId().equals(srcNode.getId())) continue;
            for (int inIdx = 0; inIdx < tn.getInputs().size(); inIdx++) {
                IngredientStack tnIn = tn.getInputs().get(inIdx);
                if (matchesIngredient(srcOut, tnIn) && !isAlreadyConnected(graph, srcNode.getId(), pref.portIndex(), tn.getId(), inIdx)) {
                    connectDirectWire(srcNode, pref.portIndex(), tn, inIdx, graph, screen, shiftDown, subCmds);
                    return 1;
                }
            }
        }

        if (templateNode != null) {
            return spawnAndConnectRecipe(srcNode, pref.portIndex(), srcOut, templateNode, targetPosX, curMaxY, frame, frameNodes, graph, shiftDown, subCmds, createdNodes, createdEdges);
        }
        return 0;
    }

    private void connectDirectWire(
            RecipeNode srcNode,
            int srcPortIdx,
            RecipeNode tn,
            int inIdx,
            FlowGraph graph,
            BoardScreen screen,
            boolean shiftDown,
            List<BoardCommand> subCmds
    ) {
        graph.addConnection(srcNode.getId(), srcPortIdx, tn.getId(), inIdx);
        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(srcNode.getId(), srcPortIdx, tn.getId(), inIdx);
        Double oldMachineCount = shiftDown ? tn.getMachineCount() : null;
        Double newMachineCount = null;

        if (shiftDown) {
            double matchedCount = FlowGraphSolver.calculateConsumerMatchCount(graph, srcNode, srcPortIdx, tn, inIdx);
            newMachineCount = matchedCount;
            tn.setMachineCount(matchedCount);
            NodeWidget tw = screen.findWidgetForNode(tn);
            if (tw != null) {
                tw.updateCountBuffer();
                tw.invalidateCache();
            }
        }
        subCmds.add(new BoardCommand.ConnectWireCommand(edge, shiftDown ? tn.getId() : null, oldMachineCount, newMachineCount));
    }

    private int spawnAndConnectRecipe(
            RecipeNode srcNode,
            int srcPortIdx,
            IngredientStack srcOut,
            RecipeNode templateNode,
            double targetPosX,
            double curMaxY,
            CanvasGroupFrame frame,
            List<RecipeNode> frameNodes,
            FlowGraph graph,
            boolean shiftDown,
            List<BoardCommand> subCmds,
            List<RecipeNode> createdNodes,
            List<FlowGraph.ConnectionEdge> createdEdges
    ) {
        SearchableRecipe sr = RecipeSearchEngine.findRecipeForInput(templateNode, srcOut.getId());
        if (sr == null || sr.recipe() == null) return 0;

        RecipeNode newNode = RecipeViewerRegistry.getActiveAdapter().convertToNode(sr.recipe());
        if (newNode == null) return 0;

        copyTemplateProperties(templateNode, newNode);
        newNode.setPosX(targetPosX);
        newNode.setPosY(curMaxY);

        graph.addNode(newNode);
        frame.addNode(newNode.getId());
        frameNodes.add(newNode);
        createdNodes.add(newNode);

        for (int inIdx = 0; inIdx < newNode.getInputs().size(); inIdx++) {
            IngredientStack tnIn = newNode.getInputs().get(inIdx);
            if (matchesIngredient(srcOut, tnIn)) {
                graph.addConnection(srcNode.getId(), srcPortIdx, newNode.getId(), inIdx);
                FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(srcNode.getId(), srcPortIdx, newNode.getId(), inIdx);
                Double oldMachineCount = shiftDown ? newNode.getMachineCount() : null;
                Double newMachineCount = null;

                if (shiftDown) {
                    double matchedCount = FlowGraphSolver.calculateConsumerMatchCount(graph, srcNode, srcPortIdx, newNode, inIdx);
                    newMachineCount = matchedCount;
                    newNode.setMachineCount(matchedCount);
                }

                subCmds.add(new BoardCommand.ConnectWireCommand(edge, shiftDown ? newNode.getId() : null, oldMachineCount, newMachineCount));
                createdEdges.add(edge);
                return 1;
            }
        }
        return 0;
    }

    private void copyTemplateProperties(RecipeNode template, RecipeNode target) {
        if (template.getMachineIcon() != null) target.setMachineIcon(template.getMachineIcon());
        target.setMultiblock(template.isMultiblock());
        target.setTargetTier(template.getTargetTier());
        target.setSteamMode(template.getSteamMode());
        target.setOverclockMode(template.getOverclockMode());
        target.setParallel(template.getParallel());
        target.getAddons().clear();
        for (var addon : template.getAddons()) {
            if (addon != null) target.getAddons().add(addon.copy());
        }
        target.getProperties().copyFrom(template.getProperties());
    }

    private int connectBundleInputToFrame(
            PortRef pref,
            RecipeNode srcNode,
            List<RecipeNode> frameNodes,
            FlowGraph graph,
            BoardScreen screen,
            boolean shiftDown,
            List<BoardCommand> subCmds
    ) {
        if (pref.portIndex() >= srcNode.getInputs().size()) return 0;
        IngredientStack srcIn = srcNode.getInputs().get(pref.portIndex());

        for (RecipeNode tn : frameNodes) {
            if (tn.getId().equals(srcNode.getId())) continue;
            for (int outIdx = 0; outIdx < tn.getOutputs().size(); outIdx++) {
                IngredientStack tnOut = tn.getOutputs().get(outIdx);
                if (matchesIngredient(tnOut, srcIn) && !isAlreadyConnected(graph, tn.getId(), outIdx, srcNode.getId(), pref.portIndex())) {
                    connectDirectReverseWire(tn, outIdx, srcNode, pref.portIndex(), graph, screen, shiftDown, subCmds);
                    return 1;
                }
            }
        }
        return 0;
    }

    private void connectDirectReverseWire(
            RecipeNode tn,
            int outIdx,
            RecipeNode srcNode,
            int inPortIdx,
            FlowGraph graph,
            BoardScreen screen,
            boolean shiftDown,
            List<BoardCommand> subCmds
    ) {
        graph.addConnection(tn.getId(), outIdx, srcNode.getId(), inPortIdx);
        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(tn.getId(), outIdx, srcNode.getId(), inPortIdx);
        Double oldMachineCount = shiftDown ? srcNode.getMachineCount() : null;
        Double newMachineCount = null;

        if (shiftDown) {
            double matchedCount = FlowGraphSolver.calculateConsumerMatchCount(graph, tn, outIdx, srcNode, inPortIdx);
            newMachineCount = matchedCount;
            srcNode.setMachineCount(matchedCount);
            NodeWidget sw = screen.findWidgetForNode(srcNode);
            if (sw != null) {
                sw.updateCountBuffer();
                sw.invalidateCache();
            }
        }
        subCmds.add(new BoardCommand.ConnectWireCommand(edge, shiftDown ? srcNode.getId() : null, oldMachineCount, newMachineCount));
    }

    private void finalizeBundleFrameConnection(
            CanvasGroupFrame frame,
            List<RecipeNode> frameNodes,
            List<RecipeNode> createdNodes,
            List<FlowGraph.ConnectionEdge> createdEdges,
            List<BoardCommand> subCmds,
            int connectedCount,
            int spawnedCount,
            double origFrameX,
            double origFrameY,
            double origFrameW,
            double origFrameH,
            BoardScreen screen
    ) {
        if (!createdNodes.isEmpty()) {
            frame.recomputeBounds(frameNodes, CanvasGroupFrame.DEFAULT_PADDING);
            screen.rebuildWidgets();
            subCmds.add(0, new BoardCommand.AddNodesCommand(createdNodes, createdEdges, "Spawn " + spawnedCount + " Missing Recipes"));
            if (origFrameX != frame.getPosX() || origFrameY != frame.getPosY() || origFrameW != frame.getWidth() || origFrameH != frame.getHeight()) {
                subCmds.add(new BoardCommand.ResizeFrameCommand(
                        frame.getId(), origFrameX, origFrameY, origFrameW, origFrameH, frame.getPosX(), frame.getPosY(), frame.getWidth(), frame.getHeight(), "Auto-fit Frame"
                ));
            }
        }

        if (!subCmds.isEmpty()) {
            screen.recordCommand(new BoardCommand.CompoundCommand(subCmds, "Batch connect " + connectedCount + " wires to " + frame.getTitle()));
            screen.markSummaryDirty();
            for (NodeWidget nw : screen.getNodeWidgets()) {
                nw.invalidateCache();
            }
            BoardToast.show(Component.literal("§a🔗 ").append(
                    Component.translatable("gui.gtcalcboard.toast.bundle_frame_connected", connectedCount, frame.getTitle())
            ));
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F)
            );
        }
    }

    private void handleBundleConnectToNode(NodeWidget targetWidget, Set<PortRef> ports, BoardScreen screen) {
        FlowGraph graph = screen.getGraph();
        if (graph == null || targetWidget == null || ports == null || ports.isEmpty()) return;

        RecipeNode tn = targetWidget.getNode();
        List<BoardCommand> subCmds = new ArrayList<>();
        int connectedCount = 0;
        boolean shiftDown = Screen.hasShiftDown();

        for (PortRef pref : ports) {
            RecipeNode srcNode = graph.findNodeById(pref.nodeId());
            if (srcNode == null || srcNode.getId().equals(tn.getId())) continue;

            if (!pref.isInput()) {
                connectedCount += connectBundleOutputToNode(pref, srcNode, tn, targetWidget, graph, shiftDown, subCmds);
            } else {
                connectedCount += connectBundleInputToNode(pref, srcNode, tn, screen, graph, shiftDown, subCmds);
            }
        }

        if (!subCmds.isEmpty()) {
            screen.recordCommand(new BoardCommand.CompoundCommand(subCmds, "Batch connect " + connectedCount + " wires to " + tn.getName()));
            screen.markSummaryDirty();
            targetWidget.invalidateCache();
            for (NodeWidget nw : screen.getNodeWidgets()) {
                nw.invalidateCache();
            }
            BoardToast.show(Component.literal("§a🔗 ").append(
                    Component.translatable("gui.gtcalcboard.toast.bundle_node_connected", connectedCount, tn.getName())
            ));
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F)
            );
        }
    }

    private int connectBundleOutputToNode(
            PortRef pref,
            RecipeNode srcNode,
            RecipeNode tn,
            NodeWidget targetWidget,
            FlowGraph graph,
            boolean shiftDown,
            List<BoardCommand> subCmds
    ) {
        if (pref.portIndex() >= srcNode.getOutputs().size()) return 0;
        IngredientStack srcOut = srcNode.getOutputs().get(pref.portIndex());

        if (tn.isReroute()) {
            tn.bindRerouteIngredient(srcOut);
            if (!isAlreadyConnected(graph, srcNode.getId(), pref.portIndex(), tn.getId(), 0)) {
                graph.addConnection(srcNode.getId(), pref.portIndex(), tn.getId(), 0);
                subCmds.add(new BoardCommand.ConnectWireCommand(new FlowGraph.ConnectionEdge(srcNode.getId(), pref.portIndex(), tn.getId(), 0)));
                return 1;
            }
            return 0;
        }

        for (int inIdx = 0; inIdx < tn.getInputs().size(); inIdx++) {
            IngredientStack tnIn = tn.getInputs().get(inIdx);
            if (matchesIngredient(srcOut, tnIn) && !isAlreadyConnected(graph, srcNode.getId(), pref.portIndex(), tn.getId(), inIdx)) {
                graph.addConnection(srcNode.getId(), pref.portIndex(), tn.getId(), inIdx);
                FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(srcNode.getId(), pref.portIndex(), tn.getId(), inIdx);
                Double oldMachineCount = shiftDown ? tn.getMachineCount() : null;
                Double newMachineCount = null;

                if (shiftDown) {
                    double matchedCount = FlowGraphSolver.calculateConsumerMatchCount(graph, srcNode, pref.portIndex(), tn, inIdx);
                    newMachineCount = matchedCount;
                    tn.setMachineCount(matchedCount);
                    targetWidget.updateCountBuffer();
                    targetWidget.invalidateCache();
                }

                subCmds.add(new BoardCommand.ConnectWireCommand(edge, shiftDown ? tn.getId() : null, oldMachineCount, newMachineCount));
                return 1;
            }
        }
        return 0;
    }

    private int connectBundleInputToNode(
            PortRef pref,
            RecipeNode srcNode,
            RecipeNode tn,
            BoardScreen screen,
            FlowGraph graph,
            boolean shiftDown,
            List<BoardCommand> subCmds
    ) {
        if (pref.portIndex() >= srcNode.getInputs().size()) return 0;
        IngredientStack srcIn = srcNode.getInputs().get(pref.portIndex());

        if (tn.isReroute()) {
            tn.bindRerouteIngredient(srcIn);
            if (!isAlreadyConnected(graph, tn.getId(), 0, srcNode.getId(), pref.portIndex())) {
                graph.addConnection(tn.getId(), 0, srcNode.getId(), pref.portIndex());
                subCmds.add(new BoardCommand.ConnectWireCommand(new FlowGraph.ConnectionEdge(tn.getId(), 0, srcNode.getId(), pref.portIndex())));
                return 1;
            }
            return 0;
        }

        for (int outIdx = 0; outIdx < tn.getOutputs().size(); outIdx++) {
            IngredientStack tnOut = tn.getOutputs().get(outIdx);
            if (matchesIngredient(tnOut, srcIn) && !isAlreadyConnected(graph, tn.getId(), outIdx, srcNode.getId(), pref.portIndex())) {
                graph.addConnection(tn.getId(), outIdx, srcNode.getId(), pref.portIndex());
                FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(tn.getId(), outIdx, srcNode.getId(), pref.portIndex());
                Double oldMachineCount = shiftDown ? srcNode.getMachineCount() : null;
                Double newMachineCount = null;

                if (shiftDown) {
                    double matchedCount = FlowGraphSolver.calculateConsumerMatchCount(graph, tn, outIdx, srcNode, pref.portIndex());
                    newMachineCount = matchedCount;
                    srcNode.setMachineCount(matchedCount);
                    NodeWidget sw = screen.findWidgetForNode(srcNode);
                    if (sw != null) {
                        sw.updateCountBuffer();
                        sw.invalidateCache();
                    }
                }

                subCmds.add(new BoardCommand.ConnectWireCommand(edge, shiftDown ? srcNode.getId() : null, oldMachineCount, newMachineCount));
                return 1;
            }
        }
        return 0;
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
