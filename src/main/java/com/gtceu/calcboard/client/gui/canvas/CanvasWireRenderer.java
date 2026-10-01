package com.gtceu.calcboard.client.gui.canvas;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.ConnectionRenderer;
import com.gtceu.calcboard.client.gui.render.EmbeddedPanelRenderer;
import com.gtceu.calcboard.client.gui.render.ExportRenderScope;
import com.gtceu.calcboard.client.gui.render.ParticleBatchingEngine;
import com.gtceu.calcboard.client.gui.render.WireSpatialIndex;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import com.gtceu.calcboard.client.gui.util.OklabColorUtil;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles spatial indexing, Bezier wire batch rendering, viewport culling, and animated pulse dots on the canvas.
 */
public class CanvasWireRenderer {

    public record WirePriorityBadge(float x, float y, int priority) {}

    private final WireSpatialIndex wireSpatialIndex = new WireSpatialIndex();
    private final FloatArrayList visibleWiresBuffer = new FloatArrayList();
    private final float[] scratchCp = new float[4];
    private boolean spatialDirty = true;

    public void markDirty() {
        this.spatialDirty = true;
        ParticleBatchingEngine.clearCache();
    }

    public WireSpatialIndex getWireSpatialIndex() {
        return wireSpatialIndex;
    }

    public FlowGraph.ConnectionEdge findHoveredWire(double canvasMouseX, double canvasMouseY, double maxDist) {
        List<WireSpatialIndex.IndexedWire> candidates = wireSpatialIndex.queryCandidates(canvasMouseX, canvasMouseY, maxDist);
        for (WireSpatialIndex.IndexedWire iw : candidates) {
            if (ConnectionRenderer.isPointNearBezier(iw.x1(), iw.y1(), iw.x2(), iw.y2(), iw.fromDirX(), iw.toDirX(), canvasMouseX, canvasMouseY, maxDist)) {
                return iw.edge();
            }
        }
        return null;
    }

    public record ResolvedWireEndpoints(
            float x1, float y1, float x2, float y2,
            float fromDirX, float toDirX,
            boolean isInternalCull
    ) {}

    public static ResolvedWireEndpoints resolveWireEndpoints(FlowGraph graph, BoardScreen screen, FlowGraph.ConnectionEdge edge) {
        return resolveWireEndpointsForWidgets(graph, n -> screen != null ? screen.findWidgetForNode(n) : null, edge);
    }

    public record PortAnchor(float x, float y, float dirX) {}

    public static PortAnchor resolvePortAnchor(
            FlowGraph graph,
            java.util.function.Function<RecipeNode, NodeWidget> widgets,
            RecipeNode node,
            int portIndex,
            boolean isInput
    ) {
        if (node == null) {
            return new PortAnchor(0, 0, isInput ? -1.0f : 1.0f);
        }
        if (graph != null) {
            CanvasGroupFrame folded = graph.getFoldedFrameForNode(node.getId());
            if (folded != null) {
                return resolveFoldedPortAnchor(graph, folded, node, portIndex, isInput);
            }

            CanvasGroupFrame emb = graph.getEmbeddedFrameForNode(node.getId());
            if (emb != null) {
                double[] pt = EmbeddedPanelRenderer.getEmbeddedPortAnchor(emb, node, portIndex, isInput);
                return new PortAnchor((float) pt[0], (float) pt[1], isInput ? -1.0f : 1.0f);
            }
        }

        NodeWidget widget = widgets != null ? widgets.apply(node) : null;
        return resolveWidgetPortAnchor(widget, node, portIndex, isInput);
    }

    private static PortAnchor resolveFoldedPortAnchor(
            FlowGraph graph,
            CanvasGroupFrame folded,
            RecipeNode node,
            int portIndex,
            boolean isInput
    ) {
        FlowGraphTopologyAnalyzer.FoldedPortSummary summary = FlowGraphTopologyAnalyzer.aggregateFoldedPorts(graph, folded);
        if (isInput) {
            int inIdx = summary.findInputIndexForOrigin(node.getId(), portIndex);
            if (inIdx < 0) inIdx = 0;
            float x = (float) (folded.getPosX() + 5.0);
            float y = (float) (folded.getPosY() + 64.0 + inIdx * 18.0 + 8.0);
            return new PortAnchor(x, y, -1.0f);
        }
        int outIdx = summary.findOutputIndexForOrigin(node.getId(), portIndex);
        if (outIdx < 0) outIdx = 0;
        float x = (float) (folded.getPosX() + folded.getWidth() - 5.0);
        float y = (float) (folded.getPosY() + 64.0 + outIdx * 18.0 + 8.0);
        return new PortAnchor(x, y, 1.0f);
    }

    private static PortAnchor resolveWidgetPortAnchor(
            NodeWidget widget,
            RecipeNode node,
            int portIndex,
            boolean isInput
    ) {
        if (isInput) {
            return resolveInputWidgetPortAnchor(widget, node, portIndex);
        }
        return resolveOutputWidgetPortAnchor(widget, node, portIndex);
    }

    private static PortAnchor resolveInputWidgetPortAnchor(NodeWidget widget, RecipeNode node, int portIndex) {
        if (widget != null) {
            return new PortAnchor(widget.getInputPortX(portIndex), widget.getInputPortY(portIndex), node.isFlipped() ? 1.0f : -1.0f);
        }
        if (node.isReroute() || node.isBoundaryPin()) {
            float x = (float) (node.getPosX() + (node.isFlipped() ? 32 : 0));
            float y = (float) (node.getPosY() + 16.0);
            return new PortAnchor(x, y, node.isFlipped() ? 1.0f : -1.0f);
        }
        float x = (float) node.getPosX();
        float y = (float) (node.getPosY() + 20.0);
        return new PortAnchor(x, y, node.isFlipped() ? 1.0f : -1.0f);
    }

    private static PortAnchor resolveOutputWidgetPortAnchor(NodeWidget widget, RecipeNode node, int portIndex) {
        if (widget != null) {
            return new PortAnchor(widget.getOutputPortX(portIndex), widget.getOutputPortY(portIndex), node.isFlipped() ? -1.0f : 1.0f);
        }
        if (node.isReroute() || node.isBoundaryPin()) {
            float x = (float) (node.getPosX() + (node.isFlipped() ? 0 : 32));
            float y = (float) (node.getPosY() + 16.0);
            return new PortAnchor(x, y, node.isFlipped() ? -1.0f : 1.0f);
        }
        float x = (float) (node.getPosX() + node.getCardWidth());
        float y = (float) (node.getPosY() + 20.0);
        return new PortAnchor(x, y, node.isFlipped() ? -1.0f : 1.0f);
    }

    public static ResolvedWireEndpoints resolveWireEndpointsForWidgets(FlowGraph graph,
            java.util.function.Function<RecipeNode, NodeWidget> widgets, FlowGraph.ConnectionEdge edge) {
        RecipeNode fromNode = graph.findNodeById(edge.fromNodeId());
        RecipeNode toNode = graph.findNodeById(edge.toNodeId());
        if (fromNode == null || toNode == null) return null;

        CanvasGroupFrame fromFolded = graph.getFoldedFrameForNode(fromNode.getId());
        CanvasGroupFrame toFolded = graph.getFoldedFrameForNode(toNode.getId());

        if (fromFolded != null && toFolded != null && fromFolded.equals(toFolded)) {
            return new ResolvedWireEndpoints(0, 0, 0, 0, 0, 0, true);
        }

        PortAnchor fromAnchor = resolvePortAnchor(graph, widgets, fromNode, edge.outputIndex(), false);
        PortAnchor toAnchor = resolvePortAnchor(graph, widgets, toNode, edge.inputIndex(), true);

        return new ResolvedWireEndpoints(fromAnchor.x(), fromAnchor.y(), toAnchor.x(), toAnchor.y(), fromAnchor.dirX(), toAnchor.dirX(), false);
    }

    public void updateSpatialIndex(BoardScreen screen, FlowGraph graph) {
        if (!spatialDirty) return;
        wireSpatialIndex.clear();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            ResolvedWireEndpoints pts = resolveWireEndpoints(graph, screen, edge);
            if (pts == null || pts.isInternalCull()) continue;
            wireSpatialIndex.insert(edge, pts.x1(), pts.y1(), pts.x2(), pts.y2(), pts.fromDirX(), pts.toDirX());
        }
        spatialDirty = false;
    }

    public void renderWires(GuiGraphics graphics, BoardScreen screen, FlowGraph graph,
                            double canvasMouseX, double canvasMouseY,
                            double screenLeft, double screenRight, double screenTop, double screenBottom,
                            double zoom) {
        renderWires(graphics, screen, graph, canvasMouseX, canvasMouseY, screenLeft, screenRight,
                screenTop, screenBottom, zoom, screen::findWidgetForNode);
    }

    public void renderWires(GuiGraphics graphics, BoardScreen screen, FlowGraph graph,
                            double canvasMouseX, double canvasMouseY,
                            double screenLeft, double screenRight, double screenTop, double screenBottom,
                            double zoom, java.util.function.Function<RecipeNode, NodeWidget> widgets) {
        boolean exporting = ExportRenderScope.isActive();
        if (!exporting) updateSpatialIndex(screen, graph);
        FlowGraph.ConnectionEdge hoveredEdge = exporting ? null : findHoveredWire(canvasMouseX, canvasMouseY, 6.0);

        ConnectionRenderer.beginBatch(graphics);
        visibleWiresBuffer.clear();
        List<WirePriorityBadge> priorityBadges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            ResolvedWireEndpoints pts = resolveWireEndpointsForWidgets(graph, widgets, edge);
            if (pts == null || pts.isInternalCull()) continue;

            float x1 = pts.x1();
            float y1 = pts.y1();
            float x2 = pts.x2();
            float y2 = pts.y2();
            float fromDirX = pts.fromDirX();
            float toDirX = pts.toDirX();
            RecipeNode fromNode = graph.findNodeById(edge.fromNodeId());
            RecipeNode toNode = graph.findNodeById(edge.toNodeId());
            if (fromNode == null || toNode == null) continue;

            ConnectionRenderer.computeControlPoints(x1, y1, x2, y2, fromDirX, toDirX, scratchCp);

            float minX = Math.min(Math.min(x1, x2), Math.min(scratchCp[0], scratchCp[2])) - 16.0f;
            float maxX = Math.max(Math.max(x1, x2), Math.max(scratchCp[0], scratchCp[2])) + 16.0f;
            float minY = Math.min(Math.min(y1, y2), Math.min(scratchCp[1], scratchCp[3])) - 16.0f;
            float maxY = Math.max(Math.max(y1, y2), Math.max(scratchCp[1], scratchCp[3])) + 16.0f;
            if (maxX < screenLeft || minX > screenRight || maxY < screenTop || minY > screenBottom) {
                continue;
            }

            if (edge.priority() > 0) {
                float mx = (x1 + 3 * scratchCp[0] + 3 * scratchCp[2] + x2) * 0.125f;
                float my = (y1 + 3 * scratchCp[1] + 3 * scratchCp[3] + y2) * 0.125f;
                priorityBadges.add(new WirePriorityBadge(mx, my, edge.priority()));
            }

            float satRatio = calculateSaturationRatio(graph, toNode, edge.inputIndex());
            boolean isHovered = edge.equals(hoveredEdge);
            boolean isWireGlowing = !exporting && TutorialManager.getInstance().isWireGlowing(fromNode.getId(), toNode.getId());
            int defWireColor = BoardManager.getInstance().getWireColor();
            int matchedWireColor = BoardManager.getInstance().getMatchedWireColor();
            WireStyle wireStyle = resolveWireStyle(isHovered, isWireGlowing, satRatio, defWireColor, matchedWireColor);
            ConnectionRenderer.addBezierToBatch(x1, y1, x2, y2, fromDirX, toDirX, wireStyle.color(), wireStyle.thickness());

            float fromEff = resolveFromEfficiency(graph, fromNode);
            float badgeCode = resolveBadgeCode(fromNode);

            visibleWiresBuffer.add(x1);
            visibleWiresBuffer.add(y1);
            visibleWiresBuffer.add(x2);
            visibleWiresBuffer.add(y2);
            visibleWiresBuffer.add(fromDirX);
            visibleWiresBuffer.add(toDirX);
            visibleWiresBuffer.add(satRatio);
            visibleWiresBuffer.add(fromEff);
            visibleWiresBuffer.add(badgeCode);
        }

        NodeWidget wireStart = exporting ? null : screen.getCanvasHandler().getWireStartNode();
        if (wireStart != null) {
            renderActiveWireDragging(screen, graph, wireStart, canvasMouseX, canvasMouseY);
        }
        ConnectionRenderer.endBatch();

        var animMode = BoardManager.getInstance().getWireAnimationMode();
        if (!exporting && zoom >= 0.28 && animMode != com.gtceu.calcboard.api.type.WireAnimationMode.DISABLED) {
            ConnectionRenderer.renderPulseDotsBatch(graphics, visibleWiresBuffer, animMode);
        }

        if (!priorityBadges.isEmpty() && zoom >= 0.28) {
            renderPriorityBadges(graphics, priorityBadges);
        }
    }

    private static void renderPriorityBadges(GuiGraphics graphics, List<WirePriorityBadge> badges) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.font == null) return;
        Font font = mc.font;

        for (WirePriorityBadge b : badges) {
            graphics.pose().pushPose();
            graphics.pose().translate(b.x(), b.y(), 0.0f);
            graphics.pose().scale(0.65f, 0.65f, 1.0f);
            String text = "P" + b.priority();
            int tw = font.width(text);
            int halfW = tw / 2;
            graphics.fill(-halfW - 3, -6, halfW + 3, 6, 0xEE181A22);
            graphics.renderOutline(-halfW - 3, -6, tw + 6, 12, 0xFFEAB308);
            graphics.drawString(font, text, -halfW, -4, 0xFFFACC15, false);
            graphics.pose().popPose();
        }
    }

    private static float calculateSaturationRatio(FlowGraph graph, RecipeNode toNode, int inputIndex) {
        if (graph == null || toNode == null) return 1.0f;
        var stats = graph.getInputPortStats(toNode, inputIndex);
        if (stats == null || !stats.isConnected()) return 1.0f;
        double targetReq = stats.effectiveRate() > 0.0001 ? stats.effectiveRate() : stats.requiredOrProducedRate();
        if (targetReq <= 0.0001) return 1.0f;
        if (stats.connectedRate() <= 0.0001) return 0.0f;
        return (float) Math.min(1.0, stats.connectedRate() / targetReq);
    }

    private record WireStyle(int color, float thickness) {}

    private static WireStyle resolveWireStyle(
            boolean isHovered,
            boolean isWireGlowing,
            float satRatio,
            int defWireColor,
            int matchedWireColor
    ) {
        if (isHovered) {
            return new WireStyle(0xFFFF3366, 2.0f);
        }
        if (isWireGlowing) {
            return new WireStyle(TutorialManager.getGlowBorderColor(0xFF55FF88), 3.5f);
        }
        int color = OklabColorUtil.getInterpolatedWireColor(defWireColor, matchedWireColor, satRatio);
        return new WireStyle(color, 2.0f);
    }

    private static float resolveFromEfficiency(FlowGraph graph, RecipeNode fromNode) {
        if (fromNode.isJunctionBuffer()) {
            return (float) fromNode.getJunctionChargeDuration(graph);
        }
        if (fromNode.isReroute()) {
            return 1.0f;
        }
        return (float) fromNode.getEfficiency();
    }

    private static float resolveBadgeCode(RecipeNode fromNode) {
        if (fromNode.isJunctionBuffer()) {
            return -1.0f;
        }
        if (!fromNode.isReroute()) {
            int mult = ParticleBatchingEngine.getBatchMultiplier(fromNode);
            if (mult > 1) {
                return (float) mult;
            }
        }
        return 1.0f;
    }

    private static void renderActiveWireDragging(BoardScreen screen, FlowGraph graph, NodeWidget wireStart, double canvasMouseX, double canvasMouseY) {
        var canvasHandler = screen.getCanvasHandler();
        int matchedColor = BoardManager.getInstance().getMatchedWireColor();
        int dragWireColor = Screen.hasShiftDown() ? 0xFFFFD700 : matchedColor;

        boolean isCurrentPortSelected = screen.isPortSelected(wireStart.getNode().getId(), canvasHandler.isWireStartInput(), canvasHandler.getWireStartPortIdx());
        java.util.Set<com.gtceu.calcboard.client.gui.model.PortRef> selectedPorts = (isCurrentPortSelected && screen.getSelectedPorts().size() > 1) ? screen.getSelectedPorts() : null;

        if (selectedPorts != null) {
            renderMultiPortDragging(screen, graph, selectedPorts, canvasMouseX, canvasMouseY);
            return;
        }

        renderSinglePortDragging(screen, graph, wireStart, canvasHandler, canvasMouseX, canvasMouseY, dragWireColor);
    }

    private static void renderMultiPortDragging(BoardScreen screen, FlowGraph graph, java.util.Set<com.gtceu.calcboard.client.gui.model.PortRef> selectedPorts, double canvasMouseX, double canvasMouseY) {
        for (com.gtceu.calcboard.client.gui.model.PortRef p : selectedPorts) {
            renderSelectedPortDragWire(screen, graph, p, canvasMouseX, canvasMouseY);
        }
    }

    private static void renderSelectedPortDragWire(BoardScreen screen, FlowGraph graph, com.gtceu.calcboard.client.gui.model.PortRef p, double canvasMouseX, double canvasMouseY) {
        RecipeNode pNode = graph.findNodeById(p.nodeId());
        if (pNode == null) return;
        PortAnchor anchor = resolvePortAnchor(graph, screen::findWidgetForNode, pNode, p.portIndex(), p.isInput());
        if (p.isInput()) {
            ConnectionRenderer.addBezierToBatch((float) canvasMouseX, (float) canvasMouseY, anchor.x(), anchor.y(), 1.0f, anchor.dirX(), 0xFF38BDF8, 2.5f);
            return;
        }
        ConnectionRenderer.addBezierToBatch(anchor.x(), anchor.y(), (float) canvasMouseX, (float) canvasMouseY, anchor.dirX(), -1.0f, 0xFF38BDF8, 2.5f);
    }

    private static void renderSinglePortDragging(
            BoardScreen screen,
            FlowGraph graph,
            NodeWidget wireStart,
            com.gtceu.calcboard.client.gui.CanvasInteractionHandler canvasHandler,
            double canvasMouseX,
            double canvasMouseY,
            int dragWireColor
    ) {
        RecipeNode node = wireStart.getNode();
        boolean isInput = canvasHandler.isWireStartInput();
        int portIdx = canvasHandler.getWireStartPortIdx();
        PortAnchor anchor = resolvePortAnchor(graph, screen::findWidgetForNode, node, portIdx, isInput);

        if (isInput) {
            ConnectionRenderer.addBezierToBatch((float) canvasMouseX, (float) canvasMouseY, anchor.x(), anchor.y(), 1.0f, anchor.dirX(), dragWireColor, 3.0f);
            return;
        }
        ConnectionRenderer.addBezierToBatch(anchor.x(), anchor.y(), (float) canvasMouseX, (float) canvasMouseY, anchor.dirX(), -1.0f, dragWireColor, 3.0f);
    }
}
