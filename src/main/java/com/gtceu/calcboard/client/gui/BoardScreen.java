package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BalanceSummary;
import com.gtceu.calcboard.api.BoardManager;
import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import dev.emi.emi.api.EmiApi;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Main GUI Screen for GregTech Calculator Board.
 * Acts as the master orchestrator coordinating canvas rendering, node widgets, and sub-components.
 */
public class BoardScreen extends Screen {
    public static double lastPanX = 40.0;
    public static double lastPanY = 40.0;
    public static double lastZoom = 1.0;

    private final FlowGraph graph;
    private final List<NodeWidget> nodeWidgets = new ArrayList<>();

    // Sub-components
    private final SummaryOverlay summaryOverlay = new SummaryOverlay();
    private final ToolbarWidget toolbarWidget = new ToolbarWidget(this);
    private final CanvasInteractionHandler canvasHandler = new CanvasInteractionHandler(this);
    private RecipeSearchDialog searchDialog;

    // Viewport Coordinates
    private double panX = lastPanX;
    private double panY = lastPanY;
    private double zoom = lastZoom;

    // Cached Summary
    private BalanceSummary cachedSummary = null;
    private boolean summaryDirty = true;

    // Mouse tracking for hotkeys
    private double lastMouseX, lastMouseY;

    public BoardScreen() {
        super(Component.literal("GregTech Calculator Board"));
        this.graph = BoardManager.getInstance().getActiveGraph();
        this.panX = lastPanX;
        this.panY = lastPanY;
        this.zoom = lastZoom;
    }

    @Override
    protected void init() {
        super.init();
        this.searchDialog = new RecipeSearchDialog(this);
        rebuildWidgets();
    }

    public void rebuildWidgets() {
        nodeWidgets.clear();
        for (RecipeNode node : graph.getNodes()) {
            nodeWidgets.add(new NodeWidget(node, this));
        }
        markSummaryDirty();
    }

    public void addNode(RecipeNode node) {
        graph.addNode(node);
        rebuildWidgets();
    }

    public static double[] getNextNodeCenterPosition(int screenW, int screenH) {
        double canvasCenterX = (-lastPanX + screenW / 2.0) / lastZoom;
        double canvasCenterY = (-lastPanY + screenH / 2.0) / lastZoom;
        return new double[]{canvasCenterX - NodeWidget.WIDTH / 2.0, canvasCenterY - 40.0};
    }

    public void removeNode(NodeWidget widget) {
        graph.removeNode(widget.getNode());
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
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            RecipeNode fromNode = graph.findNodeById(edge.fromNodeId());
            RecipeNode toNode = graph.findNodeById(edge.toNodeId());
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
            float x1 = wireStart.getOutputPortX(canvasHandler.getWireStartOutputIdx());
            float y1 = wireStart.getOutputPortY(canvasHandler.getWireStartOutputIdx());
            ConnectionRenderer.renderBezier(graphics, x1, y1, (float) canvasMouseX, (float) canvasMouseY, 0xFF00E676, 3.0f);
        }

        // 5. Render Node Widgets with Viewport Culling
        double screenLeft = -panX / zoom - 50;
        double screenRight = (-panX + width) / zoom + 50;
        double screenTop = -panY / zoom - 50;
        double screenBottom = (-panY + height) / zoom + 50;

        for (NodeWidget widget : nodeWidgets) {
            double nx = widget.getNode().getPosX();
            double ny = widget.getNode().getPosY();
            int nw = NodeWidget.WIDTH;
            int nh = widget.getHeight();

            if (nx + nw >= screenLeft && nx <= screenRight && ny + nh >= screenTop && ny <= screenBottom) {
                widget.render(graphics, (int) canvasMouseX, (int) canvasMouseY, partialTicks);
            }
        }

        graphics.pose().popPose();

        // 6. Render Top Toolbar and Summary Overlay
        toolbarWidget.render(graphics, mouseX, mouseY);

        if (summaryDirty || cachedSummary == null) {
            cachedSummary = graph.computeSummary();
            summaryDirty = false;
        }
        summaryOverlay.render(graphics, width, height, cachedSummary, mouseX, mouseY);

        // 7. Render Search Dialog or Tooltips
        if (searchDialog != null && searchDialog.isVisible()) {
            searchDialog.render(graphics, width, height, mouseX, mouseY);
        } else {
            renderTooltips(graphics, canvasMouseX, canvasMouseY, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);
    }

    private void renderTooltips(GuiGraphics graphics, double canvasMouseX, double canvasMouseY, int mouseX, int mouseY) {
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = nodeWidgets.get(i);
            if (widget.isPointInside(canvasMouseX, canvasMouseY)) {
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

                IngredientStack hovered = widget.getHoveredIngredient(canvasMouseX, canvasMouseY);
                if (hovered != null) {
                    List<Component> tooltipLines = new ArrayList<>();
                    tooltipLines.add(Component.literal(hovered.getDisplayName()));
                    if (hovered.isFluid()) {
                        tooltipLines.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.amount")).append(String.format(" §f%.0f mB", hovered.getAmount())));
                    } else {
                        tooltipLines.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.count")).append(String.format(" §f%.0f", hovered.getAmount())));
                    }
                    if (hovered.getChance() < 1.0) {
                        tooltipLines.add(Component.literal("§e").append(Component.translatable("gui.gtcalcboard.chance", String.format("%.1f", hovered.getChance() * 100.0))));
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
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.mouseClicked(mouseX, mouseY, button, width, height);
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
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (summaryOverlay.mouseScrolled(mouseX, mouseY, delta, width, height)) {
            return true;
        }
        if (toolbarWidget.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        if (canvasHandler.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
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

        // Global Hotkeys: Ctrl+C (Share), Ctrl+V (Import)
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (keyCode == GLFW.GLFW_KEY_C) {
                toolbarWidget.copyBlueprintToClipboard();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_V) {
                toolbarWidget.importBlueprintFromClipboard();
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

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
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

    public double toCanvasX(double screenX) {
        return (screenX - panX) / zoom;
    }

    public double toCanvasY(double screenY) {
        return (screenY - panY) / zoom;
    }

    public FlowGraph getGraph() { return graph; }
    public List<NodeWidget> getNodeWidgets() { return nodeWidgets; }
    public RecipeSearchDialog getSearchDialog() { return searchDialog; }
    public ToolbarWidget getToolbarWidget() { return toolbarWidget; }
    public void performAutoRatio() { toolbarWidget.performAutoRatio(); }

    public double getPanX() { return panX; }
    public void setPanX(double panX) { this.panX = panX; }
    public double getPanY() { return panY; }
    public void setPanY(double panY) { this.panY = panY; }
    public double getZoom() { return zoom; }
    public void setZoom(double zoom) { this.zoom = zoom; }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
