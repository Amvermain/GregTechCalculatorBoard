package com.gtceu.calcboard.client.gui.canvas;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.render.ConnectionRenderer;
import com.gtceu.calcboard.client.gui.render.ParticleBatchingEngine;
import com.gtceu.calcboard.client.gui.render.WireSpatialIndex;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import it.unimi.dsi.fastutil.floats.FloatArrayList;

import java.util.List;

/**
 * Handles spatial indexing, Bezier wire batch rendering, viewport culling, and animated pulse dots on the canvas.
 */
public class CanvasWireRenderer {

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

    public void updateSpatialIndex(BoardScreen screen, FlowGraph graph) {
        if (!spatialDirty) return;
        wireSpatialIndex.clear();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
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
                    float fromDirX = fromNode.isFlipped() ? -1.0f : 1.0f;
                    float toDirX = toNode.isFlipped() ? 1.0f : -1.0f;
                    wireSpatialIndex.insert(edge, x1, y1, x2, y2, fromDirX, toDirX);
                }
            }
        }
        spatialDirty = false;
    }

    public void renderWires(GuiGraphics graphics, BoardScreen screen, FlowGraph graph,
                            double canvasMouseX, double canvasMouseY,
                            double screenLeft, double screenRight, double screenTop, double screenBottom,
                            double zoom) {
        updateSpatialIndex(screen, graph);

        FlowGraph.ConnectionEdge hoveredEdge = findHoveredWire(canvasMouseX, canvasMouseY, 6.0);

        ConnectionRenderer.beginBatch(graphics);
        visibleWiresBuffer.clear();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
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

                    float fromDirX = fromNode.isFlipped() ? -1.0f : 1.0f;
                    float toDirX = toNode.isFlipped() ? 1.0f : -1.0f;

                    ConnectionRenderer.computeControlPoints(x1, y1, x2, y2, fromDirX, toDirX, scratchCp);

                    float minX = Math.min(Math.min(x1, x2), Math.min(scratchCp[0], scratchCp[2])) - 16.0f;
                    float maxX = Math.max(Math.max(x1, x2), Math.max(scratchCp[0], scratchCp[2])) + 16.0f;
                    float minY = Math.min(Math.min(y1, y2), Math.min(scratchCp[1], scratchCp[3])) - 16.0f;
                    float maxY = Math.max(Math.max(y1, y2), Math.max(scratchCp[1], scratchCp[3])) + 16.0f;
                    if (maxX < screenLeft || minX > screenRight || maxY < screenTop || minY > screenBottom) {
                        continue;
                    }

                    float satRatio = calculateSaturationRatio(graph, toNode, edge.inputIndex());
                    boolean isHovered = edge.equals(hoveredEdge);
                    boolean isWireGlowing = TutorialManager.getInstance().isWireGlowing(fromNode.getId(), toNode.getId());
                    int defWireColor = BoardManager.getInstance().getWireColor();
                    WireStyle wireStyle = resolveWireStyle(isHovered, isWireGlowing, satRatio, defWireColor);
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
            }
        }

        // Render Active Wire Dragging (Single or Multi-Port Bundle)
        var canvasHandler = screen.getCanvasHandler();
        NodeWidget wireStart = canvasHandler.getWireStartNode();
        if (wireStart != null) {
            int matchedColor = BoardManager.getInstance().getMatchedWireColor();
            int dragWireColor = Screen.hasShiftDown() ? 0xFFFFD700 : matchedColor;

            boolean isCurrentPortSelected = screen.isPortSelected(wireStart.getNode().getId(), canvasHandler.isWireStartInput(), canvasHandler.getWireStartPortIdx());
            java.util.Set<com.gtceu.calcboard.client.gui.model.PortRef> selectedPorts = (isCurrentPortSelected && screen.getSelectedPorts().size() > 1) ? screen.getSelectedPorts() : null;

            if (selectedPorts != null) {
                for (com.gtceu.calcboard.client.gui.model.PortRef p : selectedPorts) {
                    RecipeNode pNode = graph.findNodeById(p.nodeId());
                    NodeWidget pWidget = screen.findWidgetForNode(pNode);
                    if (pWidget == null) continue;
                    float px, py;
                    if (p.isInput()) {
                        px = pWidget.getInputPortX(p.portIndex());
                        py = pWidget.getInputPortY(p.portIndex());
                        float startDirX = pNode.isFlipped() ? 1.0f : -1.0f;
                        ConnectionRenderer.addBezierToBatch((float) canvasMouseX, (float) canvasMouseY, px, py, 1.0f, startDirX, 0xFF38BDF8, 2.5f);
                    } else {
                        px = pWidget.getOutputPortX(p.portIndex());
                        py = pWidget.getOutputPortY(p.portIndex());
                        float startDirX = pNode.isFlipped() ? -1.0f : 1.0f;
                        ConnectionRenderer.addBezierToBatch(px, py, (float) canvasMouseX, (float) canvasMouseY, startDirX, -1.0f, 0xFF38BDF8, 2.5f);
                    }
                }
            } else {
                float x1, y1;
                if (canvasHandler.isWireStartInput()) {
                    x1 = wireStart.getInputPortX(canvasHandler.getWireStartPortIdx());
                    y1 = wireStart.getInputPortY(canvasHandler.getWireStartPortIdx());
                    float startDirX = wireStart.getNode().isFlipped() ? 1.0f : -1.0f;
                    ConnectionRenderer.addBezierToBatch((float) canvasMouseX, (float) canvasMouseY, x1, y1, 1.0f, startDirX, dragWireColor, 3.0f);
                } else {
                    x1 = wireStart.getOutputPortX(canvasHandler.getWireStartPortIdx());
                    y1 = wireStart.getOutputPortY(canvasHandler.getWireStartPortIdx());
                    float startDirX = wireStart.getNode().isFlipped() ? -1.0f : 1.0f;
                    ConnectionRenderer.addBezierToBatch(x1, y1, (float) canvasMouseX, (float) canvasMouseY, startDirX, -1.0f, dragWireColor, 3.0f);
                }
            }
        }
        ConnectionRenderer.endBatch();

        // Draw animated flow pulse dots (Single-batch GPU rendering)
        var animMode = BoardManager.getInstance().getWireAnimationMode();
        if (zoom >= 0.28 && animMode != com.gtceu.calcboard.api.type.WireAnimationMode.DISABLED) {
            ConnectionRenderer.renderPulseDotsBatch(graphics, visibleWiresBuffer, animMode);
        }
    }

    private static float calculateSaturationRatio(FlowGraph graph, RecipeNode toNode, int inputIndex) {
        if (graph == null || toNode == null) return 1.0f;
        var stats = graph.getInputPortStats(toNode, inputIndex);
        if (stats == null || !stats.isConnected()) return 1.0f;
        if (stats.requiredOrProducedRate() <= 0.0001) return 1.0f;
        if (stats.connectedRate() <= 0.0001) return 0.0f;
        return (float) Math.min(1.0, stats.connectedRate() / stats.requiredOrProducedRate());
    }

    private record WireStyle(int color, float thickness) {}

    private static WireStyle resolveWireStyle(
            boolean isHovered,
            boolean isWireGlowing,
            float satRatio,
            int defWireColor
    ) {
        if (isHovered) {
            return new WireStyle(0xFFFF3366, 2.0f);
        }
        if (isWireGlowing) {
            return new WireStyle(TutorialManager.getGlowBorderColor(0xFF55FF88), 3.5f);
        }
        if (satRatio < 0.9999f && BoardManager.getInstance().getWireAnimationMode() == com.gtceu.calcboard.api.type.WireAnimationMode.RATE_MODULATED) {
            float timeSec = (System.currentTimeMillis() % 60000L) / 1000.0f;
            float alpha = ParticleBatchingEngine.computePulseAlpha(satRatio, timeSec);
            int alphaInt = Math.max(30, Math.min(255, (int) (alpha * 255.0f)));
            int rgb = (satRatio < 0.5f) ? 0xEF4444 : 0xF59E0B;
            return new WireStyle((alphaInt << 24) | rgb, 2.5f);
        }
        return new WireStyle(defWireColor, 2.0f);
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
}
