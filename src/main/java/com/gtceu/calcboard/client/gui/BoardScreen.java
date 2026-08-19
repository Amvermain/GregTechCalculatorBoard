package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BalanceSummary;
import com.gtceu.calcboard.api.BoardManager;
import com.gtceu.calcboard.api.BoardPage;
import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.FlowGraphSolver;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.tutorial.TutorialOverlay;
import com.gtceu.calcboard.client.gui.tutorial.WelcomeTutorialDialog;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
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

    // Sub-components & Handlers
    private final BoardSelectionModel selectionModel = new BoardSelectionModel();
    private final PageTabBarWidget pageTabBar = new PageTabBarWidget(this);
    private final SummaryOverlay summaryOverlay = new SummaryOverlay();
    private final ToolbarWidget toolbarWidget = new ToolbarWidget(this);
    private final HotkeyHudWidget hotkeyHudWidget = new HotkeyHudWidget(this);
    private final CanvasInteractionHandler canvasHandler = new CanvasInteractionHandler(this);
    private final WelcomeTutorialDialog welcomeDialog = new WelcomeTutorialDialog();
    private RecipeSearchDialog searchDialog;
    private MachineConfigDialog machineConfigDialog;
    private GuideDialog guideDialog;
    private DeletePageConfirmDialog deletePageDialog;

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

    public BoardSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public Set<String> getSelectedNodeIds() {
        return selectionModel.getSelectedNodeIds();
    }

    public boolean isNodeSelected(String id) {
        return selectionModel.isSelected(id);
    }

    public void selectNode(String id, boolean multi) {
        selectionModel.select(id, multi);
    }

    public void toggleSelectNode(String id) {
        selectionModel.toggle(id);
    }

    public void clearSelection() {
        selectionModel.clear();
    }

    public void selectAll() {
        selectionModel.selectAll(this);
    }

    public void deleteSelection() {
        selectionModel.deleteSelection(this);
    }

    public void copySelection() {
        selectionModel.copySelection(this);
    }

    public void pasteSelection(double canvasX, double canvasY) {
        selectionModel.pasteSelection(this, canvasX, canvasY);
    }

    public void cutSelection() {
        selectionModel.cutSelection(this);
    }

    public void duplicateSelection() {
        selectionModel.duplicateSelection(this, lastMouseX, lastMouseY);
    }

    public void recordCommand(BoardCommand cmd) {
        BoardPage page = BoardManager.getInstance().getActivePage();
        if (page != null) {
            page.getHistoryManager().record(cmd);
        }
    }

    public void undo() {
        BoardPage page = BoardManager.getInstance().getActivePage();
        if (page != null) {
            BoardCommand cmd = page.getHistoryManager().undo(getGraph());
            if (cmd != null) {
                rebuildWidgets();
                markSummaryDirty();
                TutorialManager.getInstance().onUndo();
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
            BoardCommand cmd = page.getHistoryManager().redo(getGraph());
            if (cmd != null) {
                rebuildWidgets();
                markSummaryDirty();
                TutorialManager.getInstance().onRedo();
                Minecraft mc = Minecraft.getInstance();
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§a↷ ").append(Component.translatable("message.gtcalcboard.redo", cmd.getDescription())), true);
                }
            }
        }
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
        this.machineConfigDialog = new MachineConfigDialog(this);
        this.guideDialog = new GuideDialog(this);
        this.deletePageDialog = new DeletePageConfirmDialog(this);
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

    public void openDeletePageDialog(int pageIndex, String pageName) {
        if (deletePageDialog != null) {
            deletePageDialog.open(pageIndex, pageName);
        }
    }

    public GuideDialog getGuideDialog() {
        return guideDialog;
    }

    public DeletePageConfirmDialog getDeletePageDialog() {
        return deletePageDialog;
    }

    public WelcomeTutorialDialog getWelcomeDialog() {
        return welcomeDialog;
    }

    public CanvasInteractionHandler getCanvasHandler() {
        return canvasHandler;
    }

    public PageTabBarWidget getPageTabBar() {
        return pageTabBar;
    }

    public HotkeyHudWidget getHotkeyHudWidget() {
        return hotkeyHudWidget;
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
        TutorialManager.getInstance().onNodeAdded(node);
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
        if (widget == null || widget.getNode() == null) return;
        RecipeNode n = widget.getNode();
        List<FlowGraph.ConnectionEdge> removedEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge e : getGraph().getConnections()) {
            if (e.fromNodeId().equals(n.getId()) || e.toNodeId().equals(n.getId())) {
                removedEdges.add(e);
            }
        }
        getGraph().removeNode(n);
        recordCommand(new BoardCommand.RemoveNodesCommand(List.of(n), removedEdges, "Delete " + n.getName()));
        selectionModel.getSelectedNodeIds().remove(n.getId());
        rebuildWidgets();
        markSummaryDirty();
        TutorialManager.getInstance().onNodeRemoved(n);
    }

    public void markSummaryDirty() {
        this.summaryDirty = true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        // 0. Update graph calculations and efficiencies if dirty
        if (summaryDirty || cachedSummary == null) {
            cachedSummary = FlowGraphSolver.computeSummary(getGraph());
            summaryDirty = false;
        }

        // 1. Dark Background and Grid
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
            int dragWireColor = Screen.hasShiftDown() ? 0xFFFFD700 : 0xFF00E676;
            if (canvasHandler.isWireStartInput()) {
                x1 = wireStart.getInputPortX(canvasHandler.getWireStartPortIdx());
                y1 = wireStart.getInputPortY(canvasHandler.getWireStartPortIdx());
                ConnectionRenderer.renderBezier(graphics, (float) canvasMouseX, (float) canvasMouseY, x1, y1, dragWireColor, 3.0f);
            } else {
                x1 = wireStart.getOutputPortX(canvasHandler.getWireStartPortIdx());
                y1 = wireStart.getOutputPortY(canvasHandler.getWireStartPortIdx());
                ConnectionRenderer.renderBezier(graphics, x1, y1, (float) canvasMouseX, (float) canvasMouseY, dragWireColor, 3.0f);
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
                graphics.flush();
                RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                RenderSystem.disableDepthTest();
                graphics.pose().pushPose();
                graphics.pose().translate(0, 0, (i + 1) * 300.0f);
                widget.render(graphics, (int) canvasMouseX, (int) canvasMouseY, partialTicks);
                graphics.flush();
                graphics.pose().popPose();
            }
        }

        // 4.5 Render Quick Add Marker if active
        if (canvasHandler.hasQuickAddMarker()) {
            double qx = canvasHandler.getQuickAddMarkerCanvasX();
            double qy = canvasHandler.getQuickAddMarkerCanvasY();
            boolean isMarkerHovered = Math.hypot(canvasMouseX - qx, canvasMouseY - qy) <= 14.0;

            int markerBg = isMarkerHovered ? 0xEE10B981 : 0x99059669;
            int markerBorder = isMarkerHovered ? 0xFF6EE7B7 : 0xCC10B981;

            graphics.fill((int)(qx - 10), (int)(qy - 10), (int)(qx + 10), (int)(qy + 10), markerBg);
            graphics.renderOutline((int)(qx - 10), (int)(qy - 10), 20, 20, markerBorder);

            String plus = "+";
            int pw = font.width(plus);
            graphics.drawString(font, plus, (int)(qx - pw / 2.0f), (int)(qy - 4), 0xFFFFFFFF, false);
        }

        // Render Active Marquee Box Selection
        canvasHandler.renderMarquee(graphics);

        graphics.pose().popPose();

        graphics.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableDepthTest();

        // 6. Render Page Tab Bar, Top Toolbar, Hotkey HUD and Summary Overlay
        pageTabBar.render(graphics, mouseX, mouseY, partialTicks);
        toolbarWidget.render(graphics, mouseX, mouseY);
        hotkeyHudWidget.render(graphics, mouseX, mouseY, partialTicks);

        if (summaryDirty || cachedSummary == null) {
            cachedSummary = FlowGraphSolver.computeSummary(getGraph());
            summaryDirty = false;
        }
        summaryOverlay.render(graphics, width, height, cachedSummary, mouseX, mouseY);

        // 7. Render Tooltips only if no modal dialog is open
        if ((welcomeDialog == null || !welcomeDialog.isVisible())
            && (guideDialog == null || !guideDialog.isVisible())
            && (searchDialog == null || !searchDialog.isVisible())
            && (machineConfigDialog == null || !machineConfigDialog.isVisible())
            && (deletePageDialog == null || !deletePageDialog.isVisible())) {
            BoardTooltipRenderer.renderTooltips(this, graphics, font, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);

        // 8. Top-level Modal Dialogs (Highest Layer)
        if (deletePageDialog != null && deletePageDialog.isVisible()) {
            deletePageDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (guideDialog != null && guideDialog.isVisible()) {
            guideDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (searchDialog != null && searchDialog.isVisible()) {
            searchDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            machineConfigDialog.render(graphics, mouseX, mouseY, partialTicks, width, height);
        }

        // 9. Interactive Tutorial Overlay and Welcome Dialog
        TutorialOverlay.render(graphics, font, this, width, height, mouseX, mouseY);
        if (welcomeDialog != null && welcomeDialog.isVisible()) {
            welcomeDialog.render(graphics, width, height, mouseX, mouseY);
        }
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
        if (TutorialOverlay.mouseClicked(this, width, height, mouseX, mouseY, button)) {
            return true;
        }
        if (deletePageDialog != null && deletePageDialog.isVisible()) {
            return deletePageDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (guideDialog != null && guideDialog.isVisible()) {
            return guideDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            return machineConfigDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (pageTabBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (hotkeyHudWidget.mouseClicked(mouseX, mouseY, button)) {
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
        if (pageTabBar.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
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
        if (pageTabBar.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
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
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            return machineConfigDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (guideDialog != null && guideDialog.isVisible()) {
            return guideDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (pageTabBar.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
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
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            return machineConfigDialog.charTyped(codePoint, modifiers);
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
        if (BoardHotkeyHandler.handleKeyPressed(this, keyCode, scanCode, modifiers, lastMouseX, lastMouseY)) {
            return true;
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
    public MachineConfigDialog getMachineConfigDialog() { return machineConfigDialog; }

    public void openMachineConfigDialog(RecipeNode node) {
        if (machineConfigDialog == null) {
            machineConfigDialog = new MachineConfigDialog(this);
        }
        machineConfigDialog.open(node);
    }

    public ToolbarWidget getToolbarWidget() { return toolbarWidget; }
    public SummaryOverlay getSummaryOverlay() { return summaryOverlay; }
    public void performAutoRatio() { toolbarWidget.performAutoRatio(); }
    public void performGroupIntoModule() { toolbarWidget.performGroupIntoModule(); }

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
        BoardManager.getInstance().saveToFile(BoardManager.getInstance().getDefaultSaveFile(), this.panX, this.panY, this.zoom);
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
