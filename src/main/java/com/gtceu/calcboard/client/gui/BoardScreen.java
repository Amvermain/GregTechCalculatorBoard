package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BalanceSummary;
import com.gtceu.calcboard.api.BoardManager;
import com.gtceu.calcboard.api.BoardPage;
import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.NodeClipboard;
import com.gtceu.calcboard.api.RecipeNode;
import dev.emi.emi.api.EmiApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Main GUI Screen for GregTech Calculator Board.
 * Acts as the master orchestrator coordinating canvas rendering, node widgets, and sub-components.
 */
public class BoardScreen extends Screen {
    public static double lastPanX = 40.0;
    public static double lastPanY = 40.0;
    public static double lastZoom = 1.0;

    private final List<NodeWidget> nodeWidgets = new ArrayList<>();

    // Sub-components
    private final PageTabBarWidget pageTabBar = new PageTabBarWidget(this);
    private final SummaryOverlay summaryOverlay = new SummaryOverlay();
    private final ToolbarWidget toolbarWidget = new ToolbarWidget(this);
    private final CanvasInteractionHandler canvasHandler = new CanvasInteractionHandler(this);
    private final com.gtceu.calcboard.client.gui.tutorial.WelcomeTutorialDialog welcomeDialog = new com.gtceu.calcboard.client.gui.tutorial.WelcomeTutorialDialog();
    private RecipeSearchDialog searchDialog;
    private TurbineRotorDialog rotorDialog;
    private GuideDialog guideDialog;

    // Viewport Coordinates
    private double panX = lastPanX;
    private double panY = lastPanY;
    private double zoom = lastZoom;

    // Cached Summary
    private BalanceSummary cachedSummary = null;
    private boolean summaryDirty = true;

    // Multi-Selection State
    private final Set<String> selectedNodeIds = new HashSet<>();

    // Mouse tracking for hotkeys
    private double lastMouseX, lastMouseY;

    public BoardScreen() {
        super(Component.translatable("gui.gtcalcboard.title"));
        var activePage = BoardManager.getInstance().getActivePage();
        this.panX = activePage.getPanX();
        this.panY = activePage.getPanY();
        this.zoom = activePage.getZoom();
        lastPanX = this.panX;
        lastPanY = this.panY;
        lastZoom = this.zoom;
    }

    public FlowGraph getGraph() {
        return BoardManager.getInstance().getActiveGraph();
    }

    public Set<String> getSelectedNodeIds() {
        return selectedNodeIds;
    }

    public boolean isNodeSelected(String id) {
        return selectedNodeIds.contains(id);
    }

    public void selectNode(String id, boolean multi) {
        if (!multi) selectedNodeIds.clear();
        if (id != null) selectedNodeIds.add(id);
    }

    public void toggleSelectNode(String id) {
        if (selectedNodeIds.contains(id)) selectedNodeIds.remove(id);
        else selectedNodeIds.add(id);
    }

    public void clearSelection() {
        selectedNodeIds.clear();
    }

    public void selectAll() {
        selectedNodeIds.clear();
        for (RecipeNode n : getGraph().getNodes()) {
            selectedNodeIds.add(n.getId());
        }
    }

    public void recordCommand(com.gtceu.calcboard.api.history.BoardCommand cmd) {
        BoardPage page = BoardManager.getInstance().getActivePage();
        if (page != null) {
            page.getHistoryManager().record(cmd);
        }
    }

    public void undo() {
        BoardPage page = BoardManager.getInstance().getActivePage();
        if (page != null) {
            com.gtceu.calcboard.api.history.BoardCommand cmd = page.getHistoryManager().undo(getGraph());
            if (cmd != null) {
                rebuildWidgets();
                markSummaryDirty();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§e↶ ").append(Component.translatable("message.gtcalcboard.undo", cmd.getDescription())), true);
                }
            }
        }
    }

    public void redo() {
        BoardPage page = BoardManager.getInstance().getActivePage();
        if (page != null) {
            com.gtceu.calcboard.api.history.BoardCommand cmd = page.getHistoryManager().redo(getGraph());
            if (cmd != null) {
                rebuildWidgets();
                markSummaryDirty();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§a↷ ").append(Component.translatable("message.gtcalcboard.redo", cmd.getDescription())), true);
                }
            }
        }
    }

    public void deleteSelection() {
        if (selectedNodeIds.isEmpty()) return;
        int count = selectedNodeIds.size();
        List<RecipeNode> removedNodes = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> removedEdges = new ArrayList<>();
        for (RecipeNode n : getGraph().getNodes()) {
            if (selectedNodeIds.contains(n.getId())) {
                removedNodes.add(n);
            }
        }
        for (FlowGraph.ConnectionEdge e : getGraph().getConnections()) {
            if (selectedNodeIds.contains(e.fromNodeId()) || selectedNodeIds.contains(e.toNodeId())) {
                removedEdges.add(e);
            }
        }
        getGraph().getNodes().removeAll(removedNodes);
        getGraph().getConnections().removeAll(removedEdges);
        recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.RemoveNodesCommand(removedNodes, removedEdges, "Delete " + count + " components"));
        selectedNodeIds.clear();
        rebuildWidgets();
        markSummaryDirty();

        for (RecipeNode n : removedNodes) {
            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onNodeRemoved(n);
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§c🗑 ").append(Component.translatable("message.gtcalcboard.deleted_components", String.valueOf(count))), true);
        }
    }

    public void copySelection() {
        if (selectedNodeIds.isEmpty()) {
            toolbarWidget.copyBlueprintToClipboard();
            return;
        }
        NodeClipboard.getInstance().copy(getGraph(), selectedNodeIds);
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.copied_components", String.valueOf(selectedNodeIds.size()))), true);
        }
    }

    public void pasteSelection(double canvasX, double canvasY) {
        if (!NodeClipboard.getInstance().hasContent()) {
            toolbarWidget.importBlueprintFromClipboard();
            return;
        }
        List<RecipeNode> newNodes = NodeClipboard.getInstance().paste(getGraph(), canvasX, canvasY);
        Set<String> newIds = new HashSet<>();
        for (RecipeNode n : newNodes) {
            newIds.add(n.getId());
        }
        List<FlowGraph.ConnectionEdge> newEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge e : getGraph().getConnections()) {
            if (newIds.contains(e.fromNodeId()) && newIds.contains(e.toNodeId())) {
                newEdges.add(e);
            }
        }
        recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.AddNodesCommand(newNodes, newEdges, "Paste " + newNodes.size() + " components"));
        selectedNodeIds.clear();
        for (RecipeNode n : newNodes) {
            selectedNodeIds.add(n.getId());
        }
        rebuildWidgets();
        markSummaryDirty();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.pasted_components", String.valueOf(newNodes.size()))), true);
        }
    }

    public void cutSelection() {
        if (selectedNodeIds.isEmpty()) return;
        int count = selectedNodeIds.size();
        NodeClipboard.getInstance().copy(getGraph(), selectedNodeIds);
        List<RecipeNode> removedNodes = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> removedEdges = new ArrayList<>();
        for (RecipeNode n : getGraph().getNodes()) {
            if (selectedNodeIds.contains(n.getId())) {
                removedNodes.add(n);
            }
        }
        for (FlowGraph.ConnectionEdge e : getGraph().getConnections()) {
            if (selectedNodeIds.contains(e.fromNodeId()) || selectedNodeIds.contains(e.toNodeId())) {
                removedEdges.add(e);
            }
        }
        getGraph().getNodes().removeAll(removedNodes);
        getGraph().getConnections().removeAll(removedEdges);
        recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.RemoveNodesCommand(removedNodes, removedEdges, "Cut " + count + " components"));
        selectedNodeIds.clear();
        rebuildWidgets();
        markSummaryDirty();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal("§6✂ ").append(Component.translatable("message.gtcalcboard.cut_components", String.valueOf(count))), true);
        }
    }

    public void duplicateSelection() {
        if (selectedNodeIds.isEmpty()) return;
        NodeClipboard.getInstance().copy(getGraph(), selectedNodeIds);
        double canvasX = toCanvasX(lastMouseX) + 30.0;
        double canvasY = toCanvasY(lastMouseY) + 30.0;
        pasteSelection(canvasX, canvasY);
    }

    public void bringNodeToFront(RecipeNode node) {
        if (node == null) return;
        List<RecipeNode> nodes = getGraph().getNodes();
        if (nodes.remove(node)) {
            nodes.add(node);
        }
        for (int i = 0; i < nodeWidgets.size(); i++) {
            if (nodeWidgets.get(i).getNode() == node) {
                NodeWidget w = nodeWidgets.remove(i);
                nodeWidgets.add(w);
                break;
            }
        }
    }

    @Override
    protected void init() {
        super.init();
        this.searchDialog = new RecipeSearchDialog(this);
        this.rotorDialog = new TurbineRotorDialog(this);
        this.guideDialog = new GuideDialog(this);
        rebuildWidgets();

        if (this.width < 640) {
            summaryOverlay.setCollapsed(true);
        }

        if (!BoardManager.getInstance().hasSeenWelcomePrompt() && getGraph().getNodes().isEmpty()) {
            welcomeDialog.show();
            summaryOverlay.setCollapsed(true);
            BoardManager.getInstance().setHasSeenWelcomePrompt(true);
            BoardManager.getInstance().saveToFile(BoardManager.getInstance().getDefaultSaveFile());
        }
    }

    public GuideDialog getGuideDialog() {
        return guideDialog;
    }

    public void openTurbineRotorDialog(RecipeNode node) {
        if (rotorDialog != null) {
            rotorDialog.open(node);
        }
    }

    public void rebuildWidgets() {
        nodeWidgets.clear();
        for (RecipeNode node : getGraph().getNodes()) {
            nodeWidgets.add(new NodeWidget(node, this));
        }
        markSummaryDirty();
    }

    public void addNode(RecipeNode node) {
        getGraph().addNode(node);
        rebuildWidgets();
        com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onNodeAdded(node);
    }

    public static double[] getNextNodeCenterPosition(int screenW, int screenH) {
        double canvasCenterX = (screenW / 2.0 - lastPanX) / lastZoom - 100.0;
        double canvasCenterY = (screenH / 2.0 - lastPanY) / lastZoom - 40.0;

        FlowGraph graph = BoardManager.getInstance().getActiveGraph();
        double candX = canvasCenterX;
        double candY = canvasCenterY;
        while (isNodeAt(graph, candX, candY)) {
            candX += 24.0;
            candY += 24.0;
        }
        return new double[]{candX, candY};
    }

    public double[] getScreenCenterCanvasPosition() {
        double canvasCenterX = (this.width / 2.0 - this.panX) / this.zoom - 100.0;
        double canvasCenterY = (this.height / 2.0 - this.panY) / this.zoom - 40.0;

        FlowGraph graph = getGraph();
        double candX = canvasCenterX;
        double candY = canvasCenterY;
        while (isNodeAt(graph, candX, candY)) {
            candX += 24.0;
            candY += 24.0;
        }
        return new double[]{candX, candY};
    }

    private static boolean isNodeAt(FlowGraph graph, double x, double y) {
        if (graph == null) return false;
        for (RecipeNode n : graph.getNodes()) {
            if (Math.abs(n.getPosX() - x) < 20.0 && Math.abs(n.getPosY() - y) < 20.0) {
                return true;
            }
        }
        return false;
    }

    public void removeNode(NodeWidget widget) {
        getGraph().removeNode(widget.getNode());
        rebuildWidgets();
    }

    public void markSummaryDirty() {
        this.summaryDirty = true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        // 1. Render Dark Background and Grid
        renderBackground(graphics);
        renderGridBackground(graphics);

        // 2. Begin Zoomed / Panned Canvas Space
        graphics.pose().pushPose();
        graphics.pose().translate((float) panX, (float) panY, 0.0f);
        graphics.pose().scale((float) zoom, (float) zoom, 1.0f);

        double canvasMouseX = toCanvasX(mouseX);
        double canvasMouseY = toCanvasY(mouseY);

        // 3. Render Static Connection Lines
        for (FlowGraph.ConnectionEdge edge : getGraph().getConnections()) {
            RecipeNode fromNode = getGraph().findNodeById(edge.fromNodeId());
            RecipeNode toNode = getGraph().findNodeById(edge.toNodeId());
            if (fromNode != null && toNode != null) {
                NodeWidget fromWidget = findWidgetForNode(fromNode);
                NodeWidget toWidget = findWidgetForNode(toNode);
                if (fromWidget != null && toWidget != null) {
                    float x1 = fromWidget.getOutputPortX(edge.outputIndex());
                    float y1 = fromWidget.getOutputPortY(edge.outputIndex());
                    float x2 = toWidget.getInputPortX(edge.inputIndex());
                    float y2 = toWidget.getInputPortY(edge.inputIndex());

                    boolean isHovered = ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, canvasMouseX, canvasMouseY, 6.0);
                    int lineColor = isHovered ? 0xFFFF3366 : 0xFF00E5FF;
                    ConnectionRenderer.renderBezier(graphics, x1, y1, x2, y2, lineColor, 2.0f);
                }
            }
        }

        // 4. Render Active Wire Dragging
        NodeWidget wireStart = canvasHandler.getWireStartNode();
        if (wireStart != null) {
            float x1, y1;
            if (canvasHandler.isWireStartInput()) {
                x1 = wireStart.getInputPortX(canvasHandler.getWireStartPortIdx());
                y1 = wireStart.getInputPortY(canvasHandler.getWireStartPortIdx());
                ConnectionRenderer.renderBezier(graphics, (float) canvasMouseX, (float) canvasMouseY, x1, y1, 0xFF00E676, 3.0f);
            } else {
                x1 = wireStart.getOutputPortX(canvasHandler.getWireStartPortIdx());
                y1 = wireStart.getOutputPortY(canvasHandler.getWireStartPortIdx());
                ConnectionRenderer.renderBezier(graphics, x1, y1, (float) canvasMouseX, (float) canvasMouseY, 0xFF00E676, 3.0f);
            }
        }

        // 5. Render Node Widgets with Viewport Culling
        double screenLeft = -panX / zoom - 50;
        double screenRight = (-panX + width) / zoom + 50;
        double screenTop = -panY / zoom - 50;
        double screenBottom = (-panY + height) / zoom + 50;

        graphics.flush();
        for (int i = 0; i < nodeWidgets.size(); i++) {
            NodeWidget widget = nodeWidgets.get(i);
            double nx = widget.getNode().getPosX();
            double ny = widget.getNode().getPosY();
            int nw = widget.getWidth();
            int nh = widget.getHeight();

            if (nx + nw >= screenLeft && nx <= screenRight && ny + nh >= screenTop && ny <= screenBottom) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, (i + 1) * 10.0f);
                widget.render(graphics, (int) canvasMouseX, (int) canvasMouseY, partialTicks);
                graphics.flush();
                graphics.pose().popPose();
            }
        }

        // Render Active Marquee Box Selection
        canvasHandler.renderMarquee(graphics);

        graphics.pose().popPose();

        // 6. Render Page Tab Bar, Top Toolbar and Summary Overlay
        pageTabBar.render(graphics, mouseX, mouseY, partialTicks);
        toolbarWidget.render(graphics, mouseX, mouseY);

        if (summaryDirty || cachedSummary == null) {
            cachedSummary = getGraph().computeSummary();
            summaryDirty = false;
        }
        summaryOverlay.render(graphics, width, height, cachedSummary, mouseX, mouseY);

        // 7. Render Tooltips only if no modal dialog is open
        if ((guideDialog == null || !guideDialog.isVisible()) && (searchDialog == null || !searchDialog.isVisible()) && (rotorDialog == null || !rotorDialog.isVisible())) {
            renderTooltips(graphics, canvasMouseX, canvasMouseY, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);

        // 8. Top-level Modal Dialogs (Highest Layer)
        if (guideDialog != null && guideDialog.isVisible()) {
            guideDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (searchDialog != null && searchDialog.isVisible()) {
            searchDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (rotorDialog != null && rotorDialog.isVisible()) {
            rotorDialog.render(graphics, mouseX, mouseY, partialTicks, width, height);
        }

        // 9. Interactive Tutorial Overlay and Welcome Dialog
        com.gtceu.calcboard.client.gui.tutorial.TutorialOverlay.render(graphics, font, this, width, height, mouseX, mouseY);
        welcomeDialog.render(graphics, width, height, mouseX, mouseY);
    }

    private void renderTooltips(GuiGraphics graphics, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            if (widget.isPointInside(canvasMouseX, canvasMouseY)) {
                if (widget.isExpandButtonHovered(canvasMouseX, canvasMouseY)) {
                    graphics.renderTooltip(font, Component.literal("§d⤢ ").append(Component.translatable("gui.gtcalcboard.tooltip.expand_module")), mouseX, mouseY);
                    return;
                }
                if (widget.isTargetButtonHovered(canvasMouseX, canvasMouseY)) {
                    List<Component> tooltip = new ArrayList<>();
                    if (widget.getNode().isBaseNode()) {
                        tooltip.add(Component.literal("§6🎯 ").append(Component.translatable("gui.gtcalcboard.tooltip.base_active")));
                        tooltip.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.tooltip.base_active_desc")));
                    } else {
                        tooltip.add(Component.literal("§e🎯 ").append(Component.translatable("gui.gtcalcboard.tooltip.base_set")));
                        tooltip.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.tooltip.base_set_desc")));
                    }
                    graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                }
                if (widget.isCloseButtonHovered(canvasMouseX, canvasMouseY)) {
                    graphics.renderTooltip(font, Component.literal("§c✕ ").append(Component.translatable("gui.gtcalcboard.tooltip.remove_node")), mouseX, mouseY);
                    return;
                }

                int inIdx = widget.getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
                int outIdx = widget.getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);

                if (inIdx >= 0 && inIdx < widget.getNode().getInputs().size()) {
                    IngredientStack in = widget.getNode().getInputs().get(inIdx);
                    FlowGraph.PortFlowStats stats = getGraph().getInputPortStats(widget.getNode(), inIdx);

                    List<Component> tooltipLines = new ArrayList<>();
                    tooltipLines.add(Component.literal("§b[📥 " + Component.translatable("gui.gtcalcboard.input").getString() + "] §f" + in.getDisplayName()));

                    String reqStr = NodeCardRenderer.formatRate(stats.requiredOrProducedRate(), in.isFluid());
                    tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.demand").getString() + ": §f" + reqStr));

                    if (stats.isConnected()) {
                        String supStr = NodeCardRenderer.formatRate(stats.connectedRate(), in.isFluid());
                        String percentCol = stats.isBalanced() ? "§a" : (stats.isDeficit() ? "§c" : "§b");
                        String statusStr = stats.isBalanced()
                            ? "§a✔ 100%"
                            : (stats.isDeficit()
                                ? String.format("§c⚠ %.1f%% (%s)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.deficit").getString())
                                : String.format("§b+ %.1f%% (%s)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.surplus").getString()));

                        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.supply").getString() + ": " + percentCol + supStr + " §7(" + statusStr + "§7)"));
                        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.connected_producers", String.valueOf(stats.connectionCount())).getString()));
                    } else {
                        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.unconnected_raw").getString()));
                    }

                    if (in.getChance() < 1.0) {
                        tooltipLines.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.chance", String.format("%.1f", in.getChance() * 100.0))));
                    }
                    tooltipLines.add(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.tooltip.recipes_uses")));
                    graphics.renderTooltip(font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                }

                if (outIdx >= 0 && outIdx < widget.getNode().getOutputs().size()) {
                    IngredientStack out = widget.getNode().getOutputs().get(outIdx);
                    FlowGraph.PortFlowStats stats = getGraph().getOutputPortStats(widget.getNode(), outIdx);

                    List<Component> tooltipLines = new ArrayList<>();
                    tooltipLines.add(Component.literal("§a[📤 " + Component.translatable("gui.gtcalcboard.output").getString() + "] §f" + out.getDisplayName()));

                    String prodStr = NodeCardRenderer.formatRate(stats.requiredOrProducedRate(), out.isFluid());
                    tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.production").getString() + ": §f" + prodStr));

                    if (stats.isConnected()) {
                        String demStr = NodeCardRenderer.formatRate(stats.connectedRate(), out.isFluid());
                        String percentCol = stats.isBalanced() ? "§a" : (stats.isSurplus() ? "§e" : "§c");
                        String statusStr = stats.isBalanced()
                            ? "§a✔ 100%"
                            : (stats.isSurplus()
                                ? String.format("§e↓ %.1f%% (%s)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.surplus").getString())
                                : String.format("§c⚠ %.1f%% (%s)", stats.getPercent(), Component.translatable("gui.gtcalcboard.tooltip.deficit").getString()));

                        tooltipLines.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.tooltip.consumed").getString() + ": " + percentCol + demStr + " §7(" + statusStr + "§7)"));
                        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.connected_consumers", String.valueOf(stats.connectionCount())).getString()));
                    } else {
                        tooltipLines.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.unconnected_final").getString()));
                    }

                    if (out.getChance() < 1.0) {
                        tooltipLines.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.chance", String.format("%.1f", out.getChance() * 100.0))));
                    }
                    tooltipLines.add(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.tooltip.recipes_uses")));
                    graphics.renderTooltip(font, tooltipLines, java.util.Optional.empty(), mouseX, mouseY);
                    return;
                }
            }
        }

        summaryOverlay.renderTooltips(graphics, font, mouseX, mouseY);
    }

    private void renderGridBackground(GuiGraphics graphics) {
        int dotSpacing = (int) (24 * zoom);
        if (dotSpacing < 10) return;

        int startX = (int) (panX % dotSpacing);
        int startY = (int) (panY % dotSpacing);

        for (int x = startX; x < width; x += dotSpacing) {
            for (int y = startY; y < height; y += dotSpacing) {
                graphics.fill(x, y, x + 1, y + 1, 0x22FFFFFF);
            }
        }
    }

    public NodeWidget findWidgetForNode(RecipeNode node) {
        for (NodeWidget w : nodeWidgets) {
            if (w.getNode() == node) return w;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (welcomeDialog.mouseClicked(this, width, height, mouseX, mouseY, button)) {
            return true;
        }
        if (com.gtceu.calcboard.client.gui.tutorial.TutorialOverlay.mouseClicked(this, width, height, mouseX, mouseY, button)) {
            return true;
        }
        if (guideDialog != null && guideDialog.isVisible()) {
            return guideDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (rotorDialog != null && rotorDialog.isVisible()) {
            return rotorDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (pageTabBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (summaryOverlay.mouseClicked(mouseX, mouseY, button, width, height)) {
            return true;
        }
        if (toolbarWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (canvasHandler.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (toolbarWidget.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        if (canvasHandler.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (toolbarWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        if (canvasHandler.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (guideDialog != null && guideDialog.isVisible()) {
            return guideDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (rotorDialog != null && rotorDialog.isVisible()) {
            return rotorDialog.mouseScrolled(mouseX, mouseY, delta, width, height);
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (summaryOverlay.mouseScrolled(mouseX, mouseY, delta, width, height)) {
            return true;
        }
        if (toolbarWidget.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }

        double canvasMouseX = toCanvasX(mouseX);
        double canvasMouseY = toCanvasY(mouseY);
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            if (nodeWidgets.get(i).mouseScrolled(canvasMouseX, canvasMouseY, delta)) {
                return true;
            }
        }

        if (canvasHandler.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (pageTabBar.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (rotorDialog != null && rotorDialog.isVisible()) {
            return rotorDialog.charTyped(codePoint, modifiers);
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.charTyped(codePoint, modifiers);
        }

        for (NodeWidget w : nodeWidgets) {
            if (w.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (welcomeDialog.isVisible()) {
                welcomeDialog.hide();
                return true;
            }
            if (com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().isActive()) {
                com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().stopTutorial();
                return true;
            }
        }
        if (guideDialog != null && guideDialog.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                guideDialog.close();
                return true;
            }
            return guideDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (pageTabBar.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (rotorDialog != null && rotorDialog.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                rotorDialog.close();
                return true;
            }
            return rotorDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                searchDialog.setVisible(false);
                return true;
            }
            return searchDialog.keyPressed(keyCode, scanCode, modifiers);
        }

        // Active inline editing in Node Widgets (Backspace, Enter, Esc, Digits)
        for (NodeWidget w : nodeWidgets) {
            if (w.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        // Global Hotkeys: Ctrl+Z (Undo), Ctrl+Y / Ctrl+Shift+Z (Redo), Ctrl+C (Copy), Ctrl+X (Cut), Ctrl+V (Paste), Ctrl+D (Duplicate), Ctrl+A (Select All)
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (keyCode == GLFW.GLFW_KEY_Z) {
                if (shift) {
                    redo();
                } else {
                    undo();
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_Y) {
                redo();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_X) {
                cutSelection();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_C) {
                copySelection();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_V) {
                pasteSelection(toCanvasX(lastMouseX), toCanvasY(lastMouseY));
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_D) {
                duplicateSelection();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_A) {
                selectAll();
                return true;
            }
        }

        // Delete / Backspace: Delete selected nodes
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!selectedNodeIds.isEmpty()) {
                deleteSelection();
                return true;
            }
        }

        // EMI recipe lookup under cursor [R] / [U]
        if (keyCode == GLFW.GLFW_KEY_R || keyCode == GLFW.GLFW_KEY_U) {
            double canvasMouseX = toCanvasX(lastMouseX);
            double canvasMouseY = toCanvasY(lastMouseY);

            for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
                NodeWidget widget = nodeWidgets.get(i);
                if (widget.isPointInside(canvasMouseX, canvasMouseY)) {
                    IngredientStack hovered = widget.getHoveredIngredient(canvasMouseX, canvasMouseY);
                    if (hovered != null) {
                        var emiStack = hovered.getEmiStack();
                        if (!emiStack.isEmpty()) {
                            if (keyCode == GLFW.GLFW_KEY_R) {
                                EmiApi.displayRecipes(emiStack);
                            } else {
                                EmiApi.displayUses(emiStack);
                            }
                            return true;
                        }
                    }
                }
            }
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public double toCanvasX(double screenX) {
        return (screenX - panX) / zoom;
    }

    public double toCanvasY(double screenY) {
        return (screenY - panY) / zoom;
    }

    public List<NodeWidget> getNodeWidgets() { return nodeWidgets; }
    public RecipeSearchDialog getSearchDialog() { return searchDialog; }
    public ToolbarWidget getToolbarWidget() { return toolbarWidget; }
    public SummaryOverlay getSummaryOverlay() { return summaryOverlay; }
    public void performAutoRatio() { toolbarWidget.performAutoRatio(); }

    public double getPanX() { return panX; }
    public void setPanX(double panX) {
        this.panX = panX;
        lastPanX = panX;
        var active = BoardManager.getInstance().getActivePage();
        if (active != null) active.setPanX(panX);
    }
    public double getPanY() { return panY; }
    public void setPanY(double panY) {
        this.panY = panY;
        lastPanY = panY;
        var active = BoardManager.getInstance().getActivePage();
        if (active != null) active.setPanY(panY);
    }
    public double getZoom() { return zoom; }
    public void setZoom(double zoom) {
        this.zoom = zoom;
        lastZoom = zoom;
        var active = BoardManager.getInstance().getActivePage();
        if (active != null) active.setZoom(zoom);
    }

    @Override
    public void onClose() {
        lastPanX = this.panX;
        lastPanY = this.panY;
        lastZoom = this.zoom;
        var active = BoardManager.getInstance().getActivePage();
        if (active != null) {
            active.setPanX(this.panX);
            active.setPanY(this.panY);
            active.setZoom(this.zoom);
        }
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
