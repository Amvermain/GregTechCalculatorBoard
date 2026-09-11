package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.model.PortRef;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CanvasBundleWiringHandler {

    private CanvasBundleWiringHandler() {}

    public static void handleBundleDrop(double canvasMouseX, double canvasMouseY, NodeWidget wireStartNode, BoardScreen screen) {
        Set<PortRef> bundlePorts = new HashSet<>(screen.getSelectedPorts());
        FlowGraph graph = screen.getGraph();

        CanvasGroupFrame targetFrame = findTargetFrame(canvasMouseX, canvasMouseY, graph);
        if (targetFrame != null) {
            handleBundleConnectToFrame(targetFrame, bundlePorts, screen);
            screen.clearPortSelection();
            return;
        }

        NodeWidget targetWidget = findTargetNodeWidget(canvasMouseX, canvasMouseY, wireStartNode, screen);
        if (targetWidget != null) {
            handleBundleConnectToNode(targetWidget, bundlePorts, screen);
            screen.clearPortSelection();
            return;
        }

        handleBundleCreateJunctions(canvasMouseX, canvasMouseY, bundlePorts, screen);
        screen.clearPortSelection();
    }

    private static CanvasGroupFrame findTargetFrame(double canvasMouseX, double canvasMouseY, FlowGraph graph) {
        if (graph == null) return null;
        for (CanvasGroupFrame f : graph.getFrames()) {
            if (canvasMouseX >= f.getPosX() && canvasMouseX <= f.getPosX() + f.getWidth() &&
                    canvasMouseY >= f.getPosY() && canvasMouseY <= f.getPosY() + f.getHeight()) {
                return f;
            }
        }
        return null;
    }

    private static NodeWidget findTargetNodeWidget(double canvasMouseX, double canvasMouseY, NodeWidget wireStartNode, BoardScreen screen) {
        for (NodeWidget targetWidget : screen.getNodeWidgets()) {
            if (targetWidget != wireStartNode && targetWidget.isPointInside(canvasMouseX, canvasMouseY)) {
                return targetWidget;
            }
        }
        return null;
    }

    private static void handleBundleCreateJunctions(double canvasX, double canvasY, Set<PortRef> ports, BoardScreen screen) {
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

    private static List<PortRef> sortBundlePorts(Set<PortRef> ports, FlowGraph graph) {
        List<PortRef> sorted = new ArrayList<>(ports);
        sorted.sort((p1, p2) -> {
            RecipeNode n1 = graph.findNodeById(p1.nodeId());
            RecipeNode n2 = graph.findNodeById(p2.nodeId());
            double y1 = n1 != null ? n1.getPosY() : 0.0;
            double y2 = n2 != null ? n2.getPosY() : 0.0;
            int cmpY = Double.compare(y1, y2);
            if (cmpY != 0) return cmpY;
            int nodeCmp = p1.nodeId().compareTo(p2.nodeId());
            if (nodeCmp != 0) return nodeCmp;
            return Integer.compare(p1.portIndex(), p2.portIndex());
        });
        return sorted;
    }

    private static void createSingleJunction(
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

    private static void handleBundleConnectToFrame(CanvasGroupFrame frame, Set<PortRef> ports, BoardScreen screen) {
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

    private static double calculateFrameMaxY(CanvasGroupFrame frame, List<RecipeNode> frameNodes) {
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

    private static int connectBundleOutputToFrame(
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
                if (CanvasWireConnectionHelper.matchesIngredient(srcOut, tnIn) && !CanvasWireConnectionHelper.isAlreadyConnected(graph, srcNode.getId(), pref.portIndex(), tn.getId(), inIdx)) {
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

    private static void connectDirectWire(
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

    private static int spawnAndConnectRecipe(
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
            if (CanvasWireConnectionHelper.matchesIngredient(srcOut, tnIn)) {
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

    private static void copyTemplateProperties(RecipeNode template, RecipeNode target) {
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

    private static int connectBundleInputToFrame(
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
                if (CanvasWireConnectionHelper.matchesIngredient(tnOut, srcIn) && !CanvasWireConnectionHelper.isAlreadyConnected(graph, tn.getId(), outIdx, srcNode.getId(), pref.portIndex())) {
                    connectDirectReverseWire(tn, outIdx, srcNode, pref.portIndex(), graph, screen, shiftDown, subCmds);
                    return 1;
                }
            }
        }
        return 0;
    }

    private static void connectDirectReverseWire(
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

    private static void finalizeBundleFrameConnection(
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

    private static void handleBundleConnectToNode(NodeWidget targetWidget, Set<PortRef> ports, BoardScreen screen) {
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

    private static int connectBundleOutputToNode(
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
            if (!CanvasWireConnectionHelper.isAlreadyConnected(graph, srcNode.getId(), pref.portIndex(), tn.getId(), 0)) {
                graph.addConnection(srcNode.getId(), pref.portIndex(), tn.getId(), 0);
                subCmds.add(new BoardCommand.ConnectWireCommand(new FlowGraph.ConnectionEdge(srcNode.getId(), pref.portIndex(), tn.getId(), 0)));
                return 1;
            }
            return 0;
        }

        for (int inIdx = 0; inIdx < tn.getInputs().size(); inIdx++) {
            IngredientStack tnIn = tn.getInputs().get(inIdx);
            if (!CanvasWireConnectionHelper.matchesIngredient(srcOut, tnIn) || CanvasWireConnectionHelper.isAlreadyConnected(graph, srcNode.getId(), pref.portIndex(), tn.getId(), inIdx)) {
                continue;
            }
            return linkBundleOutputEdge(pref, srcNode, tn, targetWidget, graph, shiftDown, subCmds, inIdx);
        }
        return 0;
    }

    private static int linkBundleOutputEdge(
            PortRef pref,
            RecipeNode srcNode,
            RecipeNode tn,
            NodeWidget targetWidget,
            FlowGraph graph,
            boolean shiftDown,
            List<BoardCommand> subCmds,
            int inIdx
    ) {
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

    private static int connectBundleInputToNode(
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
            if (!CanvasWireConnectionHelper.isAlreadyConnected(graph, tn.getId(), 0, srcNode.getId(), pref.portIndex())) {
                graph.addConnection(tn.getId(), 0, srcNode.getId(), pref.portIndex());
                subCmds.add(new BoardCommand.ConnectWireCommand(new FlowGraph.ConnectionEdge(tn.getId(), 0, srcNode.getId(), pref.portIndex())));
                return 1;
            }
            return 0;
        }

        for (int outIdx = 0; outIdx < tn.getOutputs().size(); outIdx++) {
            IngredientStack tnOut = tn.getOutputs().get(outIdx);
            if (!CanvasWireConnectionHelper.matchesIngredient(tnOut, srcIn) || CanvasWireConnectionHelper.isAlreadyConnected(graph, tn.getId(), outIdx, srcNode.getId(), pref.portIndex())) {
                continue;
            }
            return linkBundleInputEdge(pref, srcNode, tn, screen, graph, shiftDown, subCmds, outIdx);
        }
        return 0;
    }

    private static int linkBundleInputEdge(
            PortRef pref,
            RecipeNode srcNode,
            RecipeNode tn,
            BoardScreen screen,
            FlowGraph graph,
            boolean shiftDown,
            List<BoardCommand> subCmds,
            int outIdx
    ) {
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
