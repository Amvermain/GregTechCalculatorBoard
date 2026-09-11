package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.event.FlowGraphEvent;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.editor.NodeCountEditor;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.common.MinecraftForge;

public final class CanvasSingleWireHandler {

    private CanvasSingleWireHandler() {}

    public static void handleSingleWireDrop(
            NodeWidget wireStartNode,
            int wireStartPortIdx,
            boolean wireStartIsInput,
            double canvasMouseX,
            double canvasMouseY,
            BoardScreen screen,
            FlowGraph graph,
            CanvasQuickAddMarkerHandler quickAddMarkerHandler
    ) {
        boolean connected = false;
        for (NodeWidget targetWidget : screen.getNodeWidgets()) {
            if (targetWidget != wireStartNode && targetWidget.isPointInside(canvasMouseX, canvasMouseY)) {
                if (graph != null && graph.isNodeInFoldedFrame(targetWidget.getNode().getId())) {
                    continue;
                }
                connected = tryConnectToPort(wireStartNode, wireStartPortIdx, wireStartIsInput, targetWidget, canvasMouseX, canvasMouseY, graph, screen);
                if (connected) break;
            }
        }

        if (!connected && graph != null) {
            connected = tryConnectToFoldedPort(wireStartNode, wireStartPortIdx, wireStartIsInput, canvasMouseX, canvasMouseY, graph, screen);
        }

        if (!connected && screen.getSearchDialog() != null) {
            handleContextualWireDrag(wireStartNode, wireStartPortIdx, wireStartIsInput, canvasMouseX, canvasMouseY, quickAddMarkerHandler);
        }
        screen.clearPortSelection();
    }

    private static boolean tryConnectToPort(
            NodeWidget wireStartNode,
            int wireStartPortIdx,
            boolean wireStartIsInput,
            NodeWidget targetWidget,
            double canvasMouseX,
            double canvasMouseY,
            FlowGraph graph,
            BoardScreen screen
    ) {
        if (!wireStartIsInput) {
            int inPortIdx = targetWidget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
            if (inPortIdx >= 0) {
                handleForwardWireConnect(wireStartNode, wireStartPortIdx, targetWidget, graph, inPortIdx, screen);
                return true;
            }
        } else {
            int outPortIdx = targetWidget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
            if (outPortIdx >= 0) {
                handleReverseWireConnect(wireStartNode, wireStartPortIdx, targetWidget, graph, outPortIdx, screen);
                return true;
            }
        }
        return false;
    }

    private static void handleForwardWireConnect(NodeWidget wireStartNode, int wireStartPortIdx, NodeWidget targetWidget, FlowGraph graph, int inPortIdx, BoardScreen screen) {
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
        MinecraftForge.EVENT_BUS.post(new FlowGraphEvent.WireConnected(graph, newEdge, shiftDown));
    }

    private static void handleReverseWireConnect(NodeWidget wireStartNode, int wireStartPortIdx, NodeWidget targetWidget, FlowGraph graph, int outPortIdx, BoardScreen screen) {
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
        MinecraftForge.EVENT_BUS.post(new FlowGraphEvent.WireConnected(graph, newEdge, shiftDown));
    }

    private static void bindRerouteStacks(RecipeNode fromNode, RecipeNode toNode, int outIdx, int inIdx) {
        if (fromNode.isReroute() && inIdx < toNode.getInputs().size()) {
            fromNode.bindRerouteIngredient(toNode.getInputs().get(inIdx));
        } else if (toNode.isReroute() && outIdx < fromNode.getOutputs().size()) {
            toNode.bindRerouteIngredient(fromNode.getOutputs().get(outIdx));
        }
    }

    private static void alignAlternativeFluids(
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

        boolean matched = CanvasWireConnectionHelper.matchesIngredient(outStack, inStack);
        if (matched) {
            if (inStack.isFluid() && SysteamsRecipeHandler.isDynamoToBoilerConvertible(toNode)) {
                SysteamsRecipeHandler.updateBoilerFluidRecipe(toNode, inStack.getId());
            }
            if (w1 != null) w1.invalidateCache();
            if (w2 != null) w2.invalidateCache();
        }
    }

    private static void handleContextualWireDrag(
            NodeWidget wireStartNode,
            int wireStartPortIdx,
            boolean wireStartIsInput,
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
            IngredientStack stack = extractWireStartStack(wireStartNode, wireStartPortIdx, wireStartIsInput, srcNode);
            quickAddMarkerHandler.triggerContextualMarker(canvasMouseX, canvasMouseY, srcNode, wireStartPortIdx, wireStartIsInput, stack, shiftDown);
        }
    }

    private static IngredientStack extractWireStartStack(NodeWidget wireStartNode, int wireStartPortIdx, boolean wireStartIsInput, RecipeNode srcNode) {
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

    private static boolean tryConnectToFoldedPort(
            NodeWidget wireStartNode,
            int wireStartPortIdx,
            boolean wireStartIsInput,
            double canvasMouseX,
            double canvasMouseY,
            FlowGraph graph,
            BoardScreen screen
    ) {
        var hit = com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer.findHoveredFoldedPort(graph, canvasMouseX, canvasMouseY);
        if (hit == null || hit.port().internalOrigins().isEmpty()) return false;

        RecipeNode startNode = wireStartNode.getNode();
        if (!wireStartIsInput) {
            if (!hit.isInput()) return false;
            RecipeNode.PortOrigin targetOrigin = findBestMatchingOrigin(graph, startNode, wireStartPortIdx, false, hit.port());
            if (targetOrigin == null) return false;

            RecipeNode toNode = graph.findNodeById(targetOrigin.internalNodeId());
            if (toNode == null) return false;

            int inPortIdx = targetOrigin.internalPortIndex();
            bindRerouteStacks(startNode, toNode, wireStartPortIdx, inPortIdx);
            graph.addConnection(startNode.getId(), wireStartPortIdx, toNode.getId(), inPortIdx);
            screen.recordCommand(new BoardCommand.ConnectWireCommand(new FlowGraph.ConnectionEdge(startNode.getId(), wireStartPortIdx, toNode.getId(), inPortIdx)));
            screen.markSummaryDirty();
            if (screen.getWireRenderer() != null) {
                screen.getWireRenderer().markDirty();
            }
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
            return true;
        } else {
            if (hit.isInput()) return false;
            RecipeNode.PortOrigin sourceOrigin = findBestMatchingOrigin(graph, startNode, wireStartPortIdx, true, hit.port());
            if (sourceOrigin == null) return false;

            RecipeNode fromNode = graph.findNodeById(sourceOrigin.internalNodeId());
            if (fromNode == null) return false;

            int outPortIdx = sourceOrigin.internalPortIndex();
            bindRerouteStacks(fromNode, startNode, outPortIdx, wireStartPortIdx);
            graph.addConnection(fromNode.getId(), outPortIdx, startNode.getId(), wireStartPortIdx);
            screen.recordCommand(new BoardCommand.ConnectWireCommand(new FlowGraph.ConnectionEdge(fromNode.getId(), outPortIdx, startNode.getId(), wireStartPortIdx)));
            screen.markSummaryDirty();
            if (screen.getWireRenderer() != null) {
                screen.getWireRenderer().markDirty();
            }
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
            return true;
        }
    }

    private static RecipeNode.PortOrigin findBestMatchingOrigin(
            FlowGraph graph,
            RecipeNode otherNode,
            int otherPortIdx,
            boolean otherIsInput,
            com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.AggregatedFoldedPort foldedPort
    ) {
        IngredientStack otherStack = otherIsInput
                ? (otherPortIdx >= 0 && otherPortIdx < otherNode.getInputs().size() ? otherNode.getInputs().get(otherPortIdx) : null)
                : (otherPortIdx >= 0 && otherPortIdx < otherNode.getOutputs().size() ? otherNode.getOutputs().get(otherPortIdx) : null);

        for (RecipeNode.PortOrigin origin : foldedPort.internalOrigins()) {
            RecipeNode internalNode = graph.findNodeById(origin.internalNodeId());
            if (internalNode == null) continue;
            IngredientStack internalStack = foldedPort.isInput()
                    ? (origin.internalPortIndex() >= 0 && origin.internalPortIndex() < internalNode.getInputs().size() ? internalNode.getInputs().get(origin.internalPortIndex()) : null)
                    : (origin.internalPortIndex() >= 0 && origin.internalPortIndex() < internalNode.getOutputs().size() ? internalNode.getOutputs().get(origin.internalPortIndex()) : null);

            if (otherStack != null && internalStack != null && otherStack.matchesOrAlternative(internalStack)) {
                return origin;
            }
        }
        return foldedPort.internalOrigins().get(0);
    }
}
