package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.FolderBlueprintPackage;
import com.gtceu.calcboard.client.gui.action.BoardActionHandler;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.canvas.BoardHudRenderer;
import com.gtceu.calcboard.client.gui.canvas.BoardKeybindDispatcher;
import com.gtceu.calcboard.client.gui.canvas.CanvasWireRenderer;
import com.gtceu.calcboard.client.gui.dialog.*;
import com.gtceu.calcboard.client.gui.compat.InventoryProfilesNextCompat;
import com.gtceu.calcboard.client.gui.interaction.CanvasContextMenuManager;
import com.gtceu.calcboard.client.gui.model.PortRef;
import com.gtceu.calcboard.client.gui.render.BoardCanvasRenderer;
import com.gtceu.calcboard.client.gui.render.WireSpatialIndex;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.tutorial.WelcomeTutorialDialog;
import com.gtceu.calcboard.client.gui.util.BoardViewportTransform;
import com.gtceu.calcboard.client.gui.widget.*;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.client.web.IconPrewarmer;
import com.gtceu.calcboard.integration.emi.BoardMenu;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.*;

/**
 * Main GUI Screen for GregTech Calculator Board.
 * Acts as the master orchestrator coordinating canvas rendering, dialog management, and editor actions.
 */
public class BoardScreen extends AbstractContainerScreen<BoardMenu> implements IBoardScreenContext {
    public static final int LEFT_MARGIN = 48;
    private static long lastBoardScreenActiveTime = 0;

    public static boolean isBoardContext() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) return false;
        if (mc.screen instanceof BoardScreen) return true;
        if (RecipeViewerRegistry.isAnyViewerScreen(mc.screen)) {
            return (System.currentTimeMillis() - lastBoardScreenActiveTime) < 30000;
        }
        return false;
    }

    public static double lastPanX = 0, lastPanY = 0;
    public static double lastZoom = 1.0;

    private double panX = lastPanX;
    private double panY = lastPanY;
    private double zoom = lastZoom;

    private final List<NodeWidget> nodeWidgets = new ArrayList<>();
    private final Map<RecipeNode, NodeWidget> widgetByNode = new HashMap<>();
    private final Map<String, NodeWidget> widgetByNodeId = new HashMap<>();

    private final BoardSelectionModel selectionModel = new BoardSelectionModel();
    private final BoardViewportTransform viewportTransform = new BoardViewportTransform();
    private final WorkspaceTabBarWidget workspaceTabBar = new WorkspaceTabBarWidget(this);
    private final PageTabBarWidget pageTabBar = new PageTabBarWidget(this);
    private final SummaryOverlay summaryOverlay = new SummaryOverlay(this);
    private final ToolbarWidget toolbarWidget = new ToolbarWidget(this);
    private final HotkeyHudWidget hotkeyHudWidget = new HotkeyHudWidget(this);
    private final FavoritesDockWidget favoritesDockWidget = new FavoritesDockWidget(this);
    private final PageBrowserDrawer pageBrowserDrawer = new PageBrowserDrawer(this);
    private final CanvasInteractionHandler canvasHandler = new CanvasInteractionHandler(this);
    private final CanvasWireRenderer wireRenderer = new CanvasWireRenderer();
    private final NodeInspectorPanel nodeInspectorPanel = new NodeInspectorPanel(this);
    private final AdaptiveStatusBar statusBar = new AdaptiveStatusBar(this);
    private final LeftActivityBarWidget leftActivityBar = new LeftActivityBarWidget(this);
    private final SelectionFloatingToolbarWidget selectionToolbarWidget = new SelectionFloatingToolbarWidget(this);

    private final BoardDialogManager dialogManager = new BoardDialogManager(this);
    private final BoardCanvasRenderer canvasRenderer = new BoardCanvasRenderer();
    private final BoardActionHandler actionHandler = new BoardActionHandler(this);
    private final BoardNavigationHandler navigationHandler = new BoardNavigationHandler(this);
    private final BoardInputRouter inputRouter = new BoardInputRouter(this);
    private final BoardWidgetLayerRenderer widgetLayerRenderer = new BoardWidgetLayerRenderer(this);
    private final BoardTeamSyncCoordinator teamSyncCoordinator = new BoardTeamSyncCoordinator(this);

    private BalanceSummary cachedSummary = null;
    private BalanceSummary cachedSelectionSummary = null;
    private Set<String> lastSelectedNodeIds = java.util.Collections.emptySet();
    private boolean summaryDirty = true;
    private double lastMouseX, lastMouseY;
    private boolean summaryAutoCollapsedForInspector = false;
    private int nudgeScanTicks = 0;

    private Screen previousScreen = null;
    private boolean returningToPreviousScreen = false;

    public BoardScreen() {
        this((Screen) null);
    }

    public BoardScreen(Screen previousScreen) {
        this(new BoardMenu(0, Minecraft.getInstance() != null && Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getInventory() : null), previousScreen);
    }

    public BoardScreen(BoardMenu menu) {
        this(menu, null);
    }

    public BoardScreen(BoardMenu menu, Screen previousScreen) {
        super(menu, Minecraft.getInstance() != null && Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getInventory() : new Inventory(null), Component.translatable("gui.gtcalcboard.title"));
        this.previousScreen = previousScreen;
        this.imageWidth = 0;
        this.imageHeight = 0;
        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        if (teamState.isTeamMode()) {
            restoreTeamViewport(teamState, teamState.getActiveTeamPageId());
        } else {
            BoardPage activePage = BoardManager.getInstance().getActivePage();
            if (activePage != null) {
                this.panX = activePage.getPanX();
                this.panY = activePage.getPanY();
                this.zoom = activePage.getZoom();
            }
        }
        lastPanX = this.panX;
        lastPanY = this.panY;
        lastZoom = this.zoom;
    }

    public static void openScreen(Screen previousScreen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        if (previousScreen instanceof BoardScreen existingBoard) {
            previousScreen = existingBoard.getPreviousScreen();
        }
        BoardScreen newScreen = new BoardScreen(previousScreen);
        if (previousScreen != null) {
            mc.screen = null;
        }
        mc.setScreen(newScreen);
    }

    public static void openScreen() {
        Minecraft mc = Minecraft.getInstance();
        openScreen(mc != null ? mc.screen : null);
    }

    public Screen getPreviousScreen() {
        return previousScreen;
    }

    public void setPreviousScreen(Screen previousScreen) {
        this.previousScreen = previousScreen;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {}

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {}

    public FlowGraph getGraph() {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        return state.isTeamMode() ? state.getActiveTeamGraph() : BoardManager.getInstance().getActiveGraph();
    }

    @Override
    protected void init() {
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) {
            if (this.minecraft != null) this.minecraft.setScreen(null);
            return;
        }
        super.init();
        clearForeignWidgets();

        viewportTransform.update(this.minecraft);
        if (viewportTransform.isScaled()) {
            this.width = viewportTransform.getVirtualWidth();
            this.height = viewportTransform.getVirtualHeight();
        }

        dialogManager.init();

        BoardManager.getInstance().setPageRemovalListener(page -> {
            if (TutorialManager.getInstance().isTutorialPage(page.getId())) {
                TutorialManager.getInstance().stopTutorial();
            }
        });
        rebuildWidgets();
        IconPrewarmer.getInstance().enqueue(getGraph());

        teamSyncCoordinator.initNetworkPresence();
        RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);

        this.summaryOverlay.setCollapsed(BoardManager.getInstance().isSummaryOverlayCollapsed() || this.width < 640);
        this.hotkeyHudWidget.setExpanded(BoardManager.getInstance().isHotkeyHudExpanded());
        this.favoritesDockWidget.setExpanded(BoardManager.getInstance().isFavoritesDockExpanded());

        checkWelcomePrompt();
        clearForeignWidgets();
        InventoryProfilesNextCompat.ensureIntegrationHintInstalled();
        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [UI] BoardScreen opened. (Active Page: '{}', Nodes: {}, Wires: {}, TeamMode: {})",
                BoardManager.getInstance().getActivePage() != null ? BoardManager.getInstance().getActivePage().getName() : "Main",
                getGraph().getNodes().size(), getGraph().getConnections().size(), ClientWorkspaceState.getInstance().isTeamMode());
    }

    @Override
    public void removed() {
        if (this.dialogManager != null) {
            this.dialogManager.destroy();
        }
        teamSyncCoordinator.onScreenRemoved();
        if (!this.returningToPreviousScreen) {
            super.removed();
        }
    }

    public void clearForeignWidgets() {
        this.clearWidgets();
    }

    private void checkWelcomePrompt() {
        if (!BoardManager.getInstance().hasSeenWelcomePrompt() && getGraph().getNodes().isEmpty()) {
            dialogManager.getWelcomeDialog().show();
            summaryOverlay.setCollapsed(true);
            BoardManager.getInstance().setHasSeenWelcomePrompt(true);
            BoardManager.getInstance().saveToFile(BoardManager.getInstance().getDefaultSaveFile());
        }
    }

    public void rebuildWidgets() {
        nodeWidgets.clear();
        widgetByNode.clear();
        widgetByNodeId.clear();
        for (RecipeNode node : getGraph().getNodes()) {
            NodeWidget nw = new NodeWidget(node, this);
            nodeWidgets.add(nw);
            widgetByNode.put(node, nw);
            widgetByNodeId.put(node.getId(), nw);
        }
        if (nodeInspectorPanel != null && nodeInspectorPanel.isVisible()) {
            NodeWidget oldTarget = nodeInspectorPanel.getTargetWidget();
            if (oldTarget != null) {
                NodeWidget newTarget = widgetByNodeId.get(oldTarget.getNode().getId());
                nodeInspectorPanel.setTargetWidget(newTarget);
            }
        }
        if (wireRenderer != null) {
            wireRenderer.markDirty();
        }
        markSummaryDirty();
    }

    @Override
    public void rebuildBoardWidgets() {
        rebuildWidgets();
    }

    public void markSummaryDirty() {
        this.summaryDirty = true;
        if (getGraph() != null) {
            getGraph().invalidatePortStatsCache();
        }
        if (wireRenderer != null) {
            wireRenderer.markDirty();
        }
        if (dialogManager != null) {
            dialogManager.markDirty();
        }
        if (nodeWidgets != null) {
            for (NodeWidget nw : nodeWidgets) {
                nw.getTextCache().markDirty();
            }
        }
    }

    public void markTeamDirty() {
        teamSyncCoordinator.markTeamDirty();
    }

    public boolean ensureEditPermission() {
        return teamSyncCoordinator.ensureEditPermission();
    }

    @Override
    public void containerTick() {
        if (this.minecraft == null || this.minecraft.player == null) {
            if (this.minecraft != null) this.minecraft.setScreen(null);
            return;
        }
        if (!this.minecraft.player.isAlive() || this.minecraft.player.isRemoved()) {
            this.onClose();
            return;
        }
        super.containerTick();
        teamSyncCoordinator.tick();
        dialogManager.tick();
        if (++nudgeScanTicks % 20 == 0) {
            com.gtceu.calcboard.client.gui.tutorial.ContextualNudgeManager.getInstance().checkTriggers(com.gtceu.calcboard.api.storage.BoardManager.getInstance().getActivePage());
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        boolean showDebug = BoardManager.getInstance().isShowDebugInfo();
        com.gtceu.calcboard.client.gui.util.RenderProfiler profiler = com.gtceu.calcboard.client.gui.util.RenderProfiler.getInstance();
        if (showDebug) {
            profiler.startFrame();
            profiler.startSection("Pan / Viewport");
        }

        navigationHandler.updateSmoothPan();
        if (this.minecraft != null) {
            viewportTransform.update(this.minecraft);
            if (viewportTransform.isScaled()) {
                this.width = viewportTransform.getVirtualWidth();
                this.height = viewportTransform.getVirtualHeight();
            }
        }

        int localMouseX = (int) Math.round(viewportTransform.toVirtualX(mouseX));
        int localMouseY = (int) Math.round(viewportTransform.toVirtualY(mouseY));

        lastBoardScreenActiveTime = System.currentTimeMillis();
        this.lastMouseX = localMouseX;
        this.lastMouseY = localMouseY;

        if (showDebug) profiler.startSection("Summary Solver");
        updateGraphSummaryIfDirty();
        if (pngCaptureRequested) {
            pngCaptureRequested = false;
            graphics.flush();
            com.gtceu.calcboard.client.gui.export.FlowPngExporter.capture(this);
        }

        graphics.pose().pushPose();
        viewportTransform.applyPose(graphics.pose());

        if (showDebug) profiler.startSection("Background");
        renderBackground(graphics);
        BoardHudRenderer.renderGridBackground(graphics, width, height, panX, panY, zoom);
        BoardHudRenderer.renderEmptyCanvasWatermark(graphics, font, width, height, getGraph().getNodes().size());

        canvasRenderer.renderCanvasScene(graphics, this, getGraph(), nodeWidgets, wireRenderer, canvasHandler, panX, panY, zoom, width, height, localMouseX, localMouseY, partialTicks);

        if (showDebug) profiler.startSection("UI Widgets");
        widgetLayerRenderer.renderWidgets(graphics, localMouseX, localMouseY, partialTicks);
        clearForeignWidgets();

        if (showDebug) profiler.startSection("Overlays / Modals");
        widgetLayerRenderer.renderTopOverlays(graphics, localMouseX, localMouseY, partialTicks);

        if (showDebug) {
            profiler.endSection();
            profiler.render(graphics, font, width, height, AdaptiveStatusBar.BAR_HEIGHT);
            profiler.endFrame();
        }

        graphics.pose().popPose();
    }

    public void updateGraphSummaryIfDirty() {
        if (summaryDirty || cachedSummary == null) {
            getGraph().cleanupInvalidConnections();
            coordinateActiveWorkspaceFlow();
            cachedSummary = FlowGraphSolver.computeSummary(getGraph());
            summaryDirty = false;
            com.gtceu.calcboard.client.gui.tutorial.ContextualNudgeManager.getInstance().checkTriggers(com.gtceu.calcboard.api.storage.BoardManager.getInstance().getActivePage());
        }
    }

    private void coordinateActiveWorkspaceFlow() {
        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        if (teamState.isTeamMode()) {
            List<BoardPage> teamPages = teamState.getTeamPagesAsBoardPages();
            if (!teamPages.isEmpty()) {
                com.gtceu.calcboard.api.solver.WorkspaceFlowCoordinator.coordinate(teamPages);
            }
            return;
        }
        BoardManager bm = BoardManager.getInstance();
        if (bm != null && bm.getPages() != null && !bm.getPages().isEmpty()) {
            com.gtceu.calcboard.api.solver.WorkspaceFlowCoordinator.coordinate(bm.getPages());
        }
    }

    public boolean isAnyModalOpen() {
        return dialogManager.isAnyModalOpen();
    }

    @Override
    public int getScreenWidth() {
        return this.width;
    }

    @Override
    public int getScreenHeight() {
        return this.height;
    }

    @Override
    public BoardViewportTransform getViewportTransform() {
        return viewportTransform;
    }

    public static BoardViewportTransform getCurrentTransform() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof BoardScreen bs) {
            return bs.getViewportTransform();
        }
        return null;
    }

    public void onGuiScaleChanged() {
        if (this.minecraft != null) {
            viewportTransform.update(this.minecraft);
            this.width = viewportTransform.isScaled() ? viewportTransform.getVirtualWidth() : this.minecraft.getWindow().getGuiScaledWidth();
            this.height = viewportTransform.isScaled() ? viewportTransform.getVirtualHeight() : this.minecraft.getWindow().getGuiScaledHeight();
            rebuildWidgets();
            markSummaryDirty();
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (inputRouter.handleMouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (inputRouter.handleMouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (inputRouter.handleMouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (inputRouter.handleMouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (BoardKeybindDispatcher.handleCharTyped(this, codePoint, modifiers)) return true;
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (BoardKeybindDispatcher.handleKeyPressed(this, keyCode, scanCode, modifiers, (int) lastMouseX, (int) lastMouseY)) return true;
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    public GuiEventListener getActiveFocusedWidget() {
        if (dialogManager != null) {
            GuiEventListener w = dialogManager.getActiveFocusedWidget();
            if (w != null) return w;
        }
        if (pageTabBar != null && pageTabBar.isEditing()) {
            EditBox rb = pageTabBar.getRenameBox();
            if (rb != null && rb.isFocused()) return rb;
        }
        if (pageBrowserDrawer != null && pageBrowserDrawer.isOpen()) {
            EditBox eb = pageBrowserDrawer.getFocusedEditBox();
            if (eb != null) return eb;
        }
        return null;
    }

    @Override
    public GuiEventListener getFocused() {
        GuiEventListener active = getActiveFocusedWidget();
        return active != null ? active : super.getFocused();
    }

    @Override
    public List<? extends GuiEventListener> children() {
        List<GuiEventListener> all = new ArrayList<>(super.children());
        GuiEventListener active = getActiveFocusedWidget();
        if (active != null && !all.contains(active)) {
            all.add(active);
        }
        return all;
    }

    public double toCanvasX(double screenX) { return navigationHandler.toCanvasX(screenX); }
    public double toCanvasY(double screenY) { return navigationHandler.toCanvasY(screenY); }
    public double toScreenX(double canvasX) { return navigationHandler.toScreenX(canvasX); }
    public double toScreenY(double canvasY) { return navigationHandler.toScreenY(canvasY); }
    public boolean isSmoothPanBlocked() { return navigationHandler.isSmoothPanBlocked(); }

    public double getLastMouseX() { return lastMouseX; }
    public double getLastMouseY() { return lastMouseY; }

    public static double[] getNextNodeCenterPosition() { return BoardNavigationHandler.getNextNodeCenterPosition(); }
    public static double[] getNextNodeCenterPosition(int screenW, int screenH) { return BoardNavigationHandler.getNextNodeCenterPosition(screenW, screenH); }
    public double[] getScreenCenterCanvasPosition() { return navigationHandler.getScreenCenterCanvasPosition(); }

    public int getDynamicLeftMargin() { return BoardScreenLayoutHelper.getDynamicLeftMargin(this); }
    public int getPageTabY() { return BoardScreenLayoutHelper.getPageTabY(); }
    public int getToolbarY() { return BoardScreenLayoutHelper.getToolbarY(); }
    public int getHeaderBottomY() { return BoardScreenLayoutHelper.getHeaderBottomY(); }
    public int getFavoritesDockY() { return BoardScreenLayoutHelper.getFavoritesDockY(this); }
    public int getSummaryRightOffset() { return BoardScreenLayoutHelper.getSummaryRightOffset(this); }

    public void recordCommand(BoardCommand cmd) {
        BoardPage page = BoardManager.getInstance().getActivePage();
        if (page != null) page.getHistoryManager().record(cmd);
    }

    @Override
    public void showToast(Component message) {
        BoardToast.show(message);
    }

    public boolean scaleLoopToSteadyState(String targetNodeId) {
        return actionHandler.scaleLoopToSteadyState(targetNodeId);
    }

    @Override
    public void batchApplyPageTargetVoltage() {
        actionHandler.batchApplyPageTargetVoltage();
    }

    public FlowGraph.ConnectionEdge findHoveredWire(double canvasMouseX, double canvasMouseY, double maxDist) {
        return wireRenderer.findHoveredWire(canvasMouseX, canvasMouseY, maxDist);
    }

    public WireSpatialIndex getWireSpatialIndex() { return wireRenderer.getWireSpatialIndex(); }
    public CanvasWireRenderer getWireRenderer() { return wireRenderer; }
    public CanvasContextMenuManager getContextMenuManager() { return canvasHandler != null ? canvasHandler.getContextMenuManager() : null; }
    public List<NodeWidget> getNodeWidgets() { return nodeWidgets; }
    public NodeWidget findWidgetForNode(RecipeNode node) {
        if (node == null) return null;
        NodeWidget w = widgetByNode.get(node);
        return w != null ? w : widgetByNodeId.get(node.getId());
    }
    public NodeWidget findWidgetByNodeId(String nodeId) { return nodeId != null ? widgetByNodeId.get(nodeId) : null; }

    public BoardSelectionModel getSelectionModel() { return selectionModel; }
    public Set<String> getSelectedNodeIds() { return selectionModel.getSelectedNodeIds(); }
    public Set<String> getSelectedNoteIds() { return selectionModel.getSelectedNoteIds(); }
    public Set<String> getSelectedFrameIds() { return selectionModel.getSelectedFrameIds(); }
    public boolean isNodeSelected(String id) { return selectionModel.isSelected(id); }
    public boolean isNoteSelected(String id) { return selectionModel.isNoteSelected(id); }
    public boolean isFrameSelected(String id) { return selectionModel.isFrameSelected(id); }
    public void selectNode(String id, boolean multi) {
        selectionModel.select(id, multi);
        if (!multi && id != null) {
            nodeInspectorPanel.setTargetWidget(widgetByNodeId.get(id));
        }
    }
    public void deselectNode(String id) {
        selectionModel.deselectNode(id);
        if (nodeInspectorPanel.isVisible() && nodeInspectorPanel.getTargetWidget() != null && id.equals(nodeInspectorPanel.getTargetWidget().getNode().getId())) {
            nodeInspectorPanel.close();
        }
    }
    public void openNodeInspector(NodeWidget widget) { nodeInspectorPanel.setTargetWidget(widget); }
    public NodeInspectorPanel getNodeInspectorPanel() { return nodeInspectorPanel; }

    public void onNodeInspectorOpened() {
        if (this.width < 760 && !summaryOverlay.isCollapsed()) {
            summaryOverlay.setCollapsed(true);
            summaryAutoCollapsedForInspector = true;
        }
    }

    public void onNodeInspectorClosed() {
        if (summaryAutoCollapsedForInspector) {
            summaryOverlay.setCollapsed(false);
            summaryAutoCollapsedForInspector = false;
        }
    }

    public void onSummaryOverlayToggled() {
        summaryAutoCollapsedForInspector = false;
    }
    public AdaptiveStatusBar getStatusBar() { return statusBar; }
    public SelectionFloatingToolbarWidget getSelectionToolbarWidget() { return selectionToolbarWidget; }
    public void selectNote(String id, boolean multi) { selectionModel.selectNote(id, multi); }
    public void selectFrame(String id, boolean multi) { selectionModel.selectFrame(id, multi); }
    public void toggleSelectNode(String id) { selectionModel.toggle(id); }
    public void toggleSelectNote(String id) { selectionModel.toggleNote(id); }
    public void toggleSelectFrame(String id) { selectionModel.toggleFrame(id); }

    public Set<PortRef> getSelectedPorts() { return selectionModel.getSelectedPorts(); }
    public boolean isPortSelected(String nodeId, boolean isInput, int portIndex) { return selectionModel.isPortSelected(nodeId, isInput, portIndex); }
    public boolean isPortSelected(PortRef port) { return selectionModel.isPortSelected(port); }
    public boolean hasSelectedPorts() { return selectionModel.hasSelectedPorts(); }
    public void selectPort(String nodeId, boolean isInput, int portIndex, boolean multi) { selectionModel.selectPort(nodeId, isInput, portIndex, multi); }
    public void toggleSelectPort(String nodeId, boolean isInput, int portIndex) { selectionModel.togglePort(nodeId, isInput, portIndex); }
    public void selectPortRange(String nodeId, boolean isInput, int targetPortIndex) { selectionModel.selectPortRange(nodeId, isInput, targetPortIndex); }
    public void clearPortSelection() { selectionModel.clearPorts(); }
    public void clearSelection() {
        selectionModel.clear();
        nodeInspectorPanel.close();
    }
    public void selectAll() { selectionModel.selectAll(this); }
    public void deleteSelection() { selectionModel.deleteSelection(this); }
    public void copySelection() { selectionModel.copySelection(this); }
    public void pasteSelection(double canvasX, double canvasY) { selectionModel.pasteSelection(this, canvasX, canvasY); }
    public void cutSelection() { selectionModel.cutSelection(this); }
    public void duplicateSelection() { selectionModel.duplicateSelection(this, lastMouseX, lastMouseY); }
    @Override
    public boolean isBoxSelecting() {
        return canvasHandler != null && canvasHandler.getSelectionHandler().isBoxSelecting();
    }

    public void addNode(RecipeNode node) { actionHandler.addNode(node); }
    public void removeNode(NodeWidget widget) { actionHandler.removeNode(widget); }
    public void flipSelectedNodes() { actionHandler.flipSelectedNodes(lastMouseX, lastMouseY); }
    public void switchMachineWorkstation(RecipeNode node, ResourceLocation newWs) { actionHandler.switchMachineWorkstation(node, newWs); }
    public void switchMachineWorkstation(RecipeNode node, ResourceLocation newWs, String newMachineDisplayName) { actionHandler.switchMachineWorkstation(node, newWs, newMachineDisplayName); }
    public void switchNodeRecipe(RecipeNode targetNode, RecipeNode newRecipeTemplate) { actionHandler.switchNodeRecipe(targetNode, newRecipeTemplate); }
    public void createFrameFromSelection() { actionHandler.createFrameFromSelection(); }
    public void createSharedMachineFrameFromSelection() { actionHandler.createSharedMachineFrameFromSelection(); }
    public void createFrameAt(double canvasX, double canvasY) { actionHandler.createFrameAt(canvasX, canvasY); }
    public void createNoteAt(double canvasX, double canvasY) { actionHandler.createNoteAt(canvasX, canvasY); }
    public void addRerouteNodeAt(double canvasX, double canvasY) { actionHandler.addRerouteNodeAt(canvasX, canvasY); }
    public void groupNodesIntoModule(Set<String> targetNodeIds, String moduleName) { actionHandler.groupNodesIntoModule(targetNodeIds, moduleName, null); }
    public void groupNodesIntoModule(Set<String> targetNodeIds, String moduleName, CanvasGroupFrame primaryFrame) { actionHandler.groupNodesIntoModule(targetNodeIds, moduleName, primaryFrame); }
    public void collapseFrameIntoModule(CanvasGroupFrame frame) { actionHandler.collapseFrameIntoModule(frame); }
    public void bringNodeToFront(RecipeNode node) { actionHandler.bringNodeToFront(node); }
    public void undo() { actionHandler.undo(); }
    public void redo() { actionHandler.redo(); }
    public void fitToView() { actionHandler.fitToView(); }

    private boolean pngCaptureRequested;

    @Override
    public void copyFlowAsPng() {
        com.gtceu.calcboard.client.gui.export.FlowPngExporter.request(this);
    }

    @Override
    public void openWebDashboard() {
        if (com.gtceu.calcboard.config.CalcBoardClientConfig.ENABLE_LOCAL_WEB_SERVER != null
                && !com.gtceu.calcboard.config.CalcBoardClientConfig.ENABLE_LOCAL_WEB_SERVER.get()) {
            com.gtceu.calcboard.client.gui.widget.BoardToast.show(
                    Component.literal("⚠ ").append(Component.translatable("gui.gtcalcboard.web.disabled_hint"))
            );
            openSettingsDialog(com.gtceu.calcboard.client.gui.dialog.BoardSettingsDialog.SettingsTab.UPDATES);
            return;
        }

        var daemon = com.gtceu.calcboard.client.web.LocalWebServerDaemon.getInstance();
        if (!daemon.isRunning()) {
            daemon.start();
        }
        if (!daemon.isRunning()) {
            com.gtceu.calcboard.client.gui.widget.BoardToast.show(
                    Component.literal("⚠ ").append(Component.translatable("gui.gtcalcboard.web.bind_failed"))
            );
            return;
        }
        com.gtceu.calcboard.client.web.WebSyncEventBus.publishCurrentBoard();
        String url = daemon.getUrl();
        try {
            net.minecraft.Util.getPlatform().openUri(java.net.URI.create(url));
        } catch (Throwable t) {
            GregTechCalcBoard.LOGGER.warn("[GTCalcBoard] Failed to open system browser for web dashboard: {}", t.getMessage());
        }
        try {
            Minecraft.getInstance().keyboardHandler.setClipboard(url);
        } catch (Throwable ignored) {}
        com.gtceu.calcboard.client.gui.widget.BoardToast.show(
                Component.literal("🌐 ").append(Component.translatable("gui.gtcalcboard.web.opened", url))
        );
    }

    public void requestPngCapture() { pngCaptureRequested = true; }

    public BoardDialogManager getDialogManager() { return dialogManager; }
    public BoardCanvasRenderer getCanvasRenderer() { return canvasRenderer; }
    public BoardActionHandler getActionHandler() { return actionHandler; }
    public BoardNavigationHandler getNavigationHandler() { return navigationHandler; }
    public BoardInputRouter getInputRouter() { return inputRouter; }
    public BoardWidgetLayerRenderer getWidgetLayerRenderer() { return widgetLayerRenderer; }
    public BoardTeamSyncCoordinator getTeamSyncCoordinator() { return teamSyncCoordinator; }
    public CanvasInteractionHandler getCanvasHandler() { return canvasHandler; }
    public WorkspaceTabBarWidget getWorkspaceTabBar() { return workspaceTabBar; }
    public PageTabBarWidget getPageTabBar() { return pageTabBar; }
    public ToolbarWidget getToolbarWidget() { return toolbarWidget; }
    public SummaryOverlay getSummaryOverlay() { return summaryOverlay; }
    public HotkeyHudWidget getHotkeyHudWidget() { return hotkeyHudWidget; }
    public FavoritesDockWidget getFavoritesDockWidget() { return favoritesDockWidget; }
    public PageBrowserDrawer getPageBrowserDrawer() { return pageBrowserDrawer; }
    public LeftActivityBarWidget getLeftActivityBar() { return leftActivityBar; }
    public Font getMinecraftFont() { return this.font; }
    public BalanceSummary getCachedSummary() { return cachedSummary; }

    @Override
    public BalanceSummary getActiveSummary() {
        Set<String> selected = getSelectedNodeIds();
        if (selected.isEmpty()) {
            return getCachedSummary();
        }
        if (cachedSelectionSummary == null || summaryDirty || !selected.equals(lastSelectedNodeIds)) {
            cachedSelectionSummary = FlowGraphSolver.computeSubsetSummary(getGraph(), selected);
            lastSelectedNodeIds = new java.util.HashSet<>(selected);
        }
        return cachedSelectionSummary;
    }

    public void performAutoRatio() { toolbarWidget.performAutoRatio(); }
    public void performAutoConnectForSelection() {
        if (getSelectedNodeIds().size() >= 2) {
            if (this.toolbarWidget != null) {
                this.toolbarWidget.performAutoConnectForSelection(getSelectedNodeIds());
            } else {
                ToolbarWidget.performAutoConnectForSelection(this, getSelectedNodeIds());
            }
        }
    }
    public void performGroupIntoModule() { toolbarWidget.performGroupIntoModule(); }

    public WelcomeTutorialDialog getWelcomeDialog() { return dialogManager.getWelcomeDialog(); }
    public QuickPageSwitcherDialog getQuickPageSwitcherDialog() { return dialogManager.getQuickPageSwitcherDialog(); }
    public TemplateCloneDialog getTemplateCloneDialog() { return dialogManager.getTemplateCloneDialog(); }
    public RecipeSearchDialog getSearchDialog() { return dialogManager.getSearchDialog(); }
    public MachineConfigDialog getMachineConfigDialog() { return dialogManager.getMachineConfigDialog(); }
    public MachineSelectorDialog getMachineSelectorDialog() { return dialogManager.getMachineSelectorDialog(); }
    public GuideDialog getGuideDialog() { return dialogManager.getGuideDialog(); }
    public DeletePageConfirmDialog getDeletePageDialog() { return dialogManager.getDeletePageDialog(); }
    public TutorialExitConfirmDialog getTutorialExitDialog() { return dialogManager.getTutorialExitDialog(); }
    public GlobalBalanceDashboardDialog getGlobalBalanceDialog() { return dialogManager.getGlobalBalanceDialog(); }
    public MultiblockBOMDialog getMultiblockBOMDialog() { return dialogManager.getMultiblockBOMDialog(); }
    public SaveToTeamDialog getSaveToTeamDialog() { return dialogManager.getSaveToTeamDialog(); }
    public ExportToTeamDialog getExportToTeamDialog() { return dialogManager.getExportToTeamDialog(); }
    public ExportBlueprintDialog getExportBlueprintDialog() { return dialogManager.getExportBlueprintDialog(); }
    public ImportBlueprintDialog getImportBlueprintDialog() { return dialogManager.getImportBlueprintDialog(); }
    public ExportFolderDialog getExportFolderDialog() { return dialogManager.getExportFolderDialog(); }
    public ImportFolderDialog getImportFolderDialog() { return dialogManager.getImportFolderDialog(); }
    public DiskBlueprintsDialog getDiskBlueprintsDialog() { return dialogManager.getDiskBlueprintsDialog(); }
    public RecentSavesDialog getRecentSavesDialog() { return dialogManager.getRecentSavesDialog(); }
    public FrameEditDialog getFrameEditDialog() { return dialogManager.getFrameEditDialog(); }
    public NoteEditDialog getNoteEditDialog() { return dialogManager.getNoteEditDialog(); }
    public BoardSettingsDialog getSettingsDialog() { return dialogManager.getSettingsDialog(); }
    public AutoConnectFilterDialog getAutoConnectDialog() { return dialogManager.getAutoConnectDialog(); }
    public PatternBindingDialog getPatternBindingDialog() { return dialogManager.getPatternBindingDialog(); }
    public JunctionSupplyDialog getJunctionSupplyDialog() { return dialogManager.getJunctionSupplyDialog(); }
    public BatchRunCalculatorDialog getBatchRunDialog() { return dialogManager.getBatchRunDialog(); }

    public void openBatchRunCalculator(IngredientStack preselected, boolean isInput) { dialogManager.openBatchRunCalculator(preselected, isInput); }
    public void openSettingsDialog() { dialogManager.openSettingsDialog(); }
    public void openSettingsDialog(com.gtceu.calcboard.client.gui.dialog.BoardSettingsDialog.SettingsTab tab) { dialogManager.openSettingsDialog(tab); }
    public void openExportFolderDialog(String folderPath) { dialogManager.openExportFolderDialog(folderPath); }
    public void openImportFolderDialog() { dialogManager.openImportFolderDialog(); }
    public void openImportFolderDialog(FolderBlueprintPackage pkg) { dialogManager.openImportFolderDialog(pkg); }
    public void openDeletePageDialog(int pageIndex, String pageName) { dialogManager.openDeletePageDialog(pageIndex, pageName); }
    public void openDeleteMultiplePagesDialog(List<String> pageIds) { dialogManager.openDeleteMultiplePagesDialog(pageIds); }
    public void openDeleteTeamPageDialog(String pageId, String pageName) { dialogManager.openDeleteTeamPageDialog(pageId, pageName); }
    public void openJunctionSupplyDialog(RecipeNode node) { dialogManager.openJunctionSupplyDialog(node); }
    @Override
    public void openCrossPageSourceSearchDialog(RecipeNode consumerNode, String initialPageId, String initialNodeId, java.util.function.BiConsumer<String, String> onSelect) {
        dialogManager.openCrossPageSourceSearchDialog(consumerNode, initialPageId, initialNodeId, onSelect);
    }
    public void openTutorialExitDialog(int targetPageIndex) { dialogManager.openTutorialExitDialog(targetPageIndex); }
    public void openTutorialExitDialogForNewPage() { dialogManager.openTutorialExitDialogForNewPage(); }
    public void openTutorialExitDialogForTeamPage(String teamPageId) { dialogManager.openTutorialExitDialogForTeamPage(teamPageId); }
    public void openQuickPageSwitcher() { dialogManager.openQuickPageSwitcher(); }
    public void openTemplateCloneDialog(BoardPage page) { dialogManager.openTemplateCloneDialog(page); }
    public void openMachineSelectorDialog(RecipeNode node) { dialogManager.openMachineSelectorDialog(node); }
    public void openAutoConnectDialog() { dialogManager.openAutoConnectDialog(); }
    public void openRecipeSwitchDialog(RecipeNode node) { dialogManager.openRecipeSwitchDialog(node); }
    public void openMachineConfigDialog(RecipeNode node) { dialogManager.openMachineConfigDialog(node); }
    public void openMachineConfigDialog(RecipeNode node, AddonCategory initialCategory) { dialogManager.openMachineConfigDialog(node, initialCategory); }
    public void openMachineConfigDialog(RecipeNode node, AddonCategory initialCategory, Runnable onCloseCallback) { dialogManager.openMachineConfigDialog(node, initialCategory, onCloseCallback); }
    public void openSharedFrameConfigDialog(CanvasGroupFrame frame) { dialogManager.openSharedFrameConfigDialog(frame); }
    @Override
    public void openRecipeSearchForSharedFrame(CanvasGroupFrame frame) { dialogManager.openRecipeSearchForSharedFrame(frame); }
    @Override
    public void openRecipeSearchForSharedFrameWithWireContext(CanvasGroupFrame frame, RecipeNode sourceNode, int sourcePortIdx, boolean sourceIsInput, com.gtceu.calcboard.api.model.IngredientStack sourceStack, boolean shiftAutoRatio) {
        dialogManager.openRecipeSearchForSharedFrameWithWireContext(frame, sourceNode, sourcePortIdx, sourceIsInput, sourceStack, shiftAutoRatio);
    }
    public void openFrameEditDialog(CanvasGroupFrame frame) { dialogManager.openFrameEditDialog(frame); }
    public void openNoteEditDialog(CanvasStickyNote note) { dialogManager.openNoteEditDialog(note); }
    public void openTargetOutputRateDialog(RecipeNode node, int outputIndex) { dialogManager.openTargetOutputRateDialog(node, outputIndex); }
    public void openPageSettingsDialog(BoardPage page) { dialogManager.openPageSettingsDialog(page); }
    public void openPageSettingsDialog() { dialogManager.openPageSettingsDialog(com.gtceu.calcboard.api.storage.BoardManager.getInstance().getActivePage()); }
    public PageSettingsDialog getPageSettingsDialog() { return dialogManager.getPageSettingsDialog(); }
    @Override
    public void openTutorialLauncher() { dialogManager.openTutorialLauncher(); }

    public void openModuleSubPage(RecipeNode moduleNode) { navigationHandler.openModuleSubPage(moduleNode); }
    public void returnToParentPage() { navigationHandler.returnToParentPage(); }

    public void openPage(String pageId) {
        if (pageId == null || pageId.isEmpty()) return;
        if (this.canvasHandler != null) {
            this.canvasHandler.getStateMachine().returnToIdle();
            this.canvasHandler.getWireHandler().cancelWireDrag();
        }
        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        if (teamState.isTeamMode()) {
            openTeamPage(teamState, pageId);
            return;
        }
        openLocalPage(pageId);
    }

    private void openTeamPage(ClientWorkspaceState teamState, String pageId) {
        if (teamState.getRemotePage(pageId) == null) return;
        if (pageId.equals(teamState.getActiveTeamPageId())) return;

        teamState.setPageViewport(teamState.getActiveTeamPageId(), this.panX, this.panY, this.zoom);
        teamState.autoCommitAndRelease(this, teamState.getActiveTeamPageId());
        teamState.setActiveTeamPageId(pageId);
        restoreTeamViewport(teamState, pageId);
        com.gtceu.calcboard.network.NetworkHandler.sendToServer(
            new com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket(teamState.getCurrentTeamId(), pageId, true)
        );
        rebuildBoardWidgets();
        markSummaryDirty();
    }

    private void openLocalPage(String pageId) {
        BoardManager bm = BoardManager.getInstance();
        BoardPage cur = bm.getActivePage();
        if (cur != null) {
            cur.setPanX(this.panX);
            cur.setPanY(this.panY);
            cur.setZoom(this.zoom);
        }
        if (!bm.openPage(pageId)) return;
        BoardPage next = bm.getActivePage();
        if (next != null) {
            this.panX = next.getPanX();
            this.panY = next.getPanY();
            this.zoom = next.getZoom();
            lastPanX = this.panX;
            lastPanY = this.panY;
            lastZoom = this.zoom;
        }
        rebuildBoardWidgets();
        markSummaryDirty();
    }

    public void restoreTeamViewport(ClientWorkspaceState teamState, String pageId) {
        ClientWorkspaceState.TeamPageViewport vp = teamState.getPageViewport(pageId);
        if (vp != null) {
            this.panX = vp.panX();
            this.panY = vp.panY();
            this.zoom = vp.zoom();
        } else {
            this.panX = 40.0;
            this.panY = 40.0;
            this.zoom = 1.0;
        }
        lastPanX = this.panX;
        lastPanY = this.panY;
        lastZoom = this.zoom;
    }

    public void openPage(UUID pageId) {
        if (pageId != null) {
            openPage(pageId.toString());
        }
    }

    public void openPageAndFocusNode(String pageId, String nodeId) {
        if (pageId == null || pageId.isEmpty()) return;
        openPage(pageId);
        if (nodeId == null || nodeId.isEmpty()) return;
        FlowGraph graph = getGraph();
        if (graph != null) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node != null) {
                setPanX((this.width / 2.0) - ((node.getPosX() + 16.0) * this.zoom));
                setPanY((this.height / 2.0) - ((node.getPosY() + 16.0) * this.zoom));
            }
        }
    }

    public void switchToWorkspaceMode(ClientWorkspaceState.WorkspaceMode targetMode) {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (state.getCurrentMode() == targetMode) return;

        if (state.isTeamMode()) {
            state.setPageViewport(state.getActiveTeamPageId(), this.panX, this.panY, this.zoom);
            state.autoCommitAndRelease(this, state.getActiveTeamPageId());
            state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
            com.gtceu.calcboard.api.solver.WorkspaceFlowCoordinator.invalidate();
            com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                new com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket(state.getCurrentTeamId(), state.getActiveTeamPageId(), false)
            );

            BoardPage activeLocal = BoardManager.getInstance().getActivePage();
            if (activeLocal != null) {
                this.panX = activeLocal.getPanX();
                this.panY = activeLocal.getPanY();
                this.zoom = activeLocal.getZoom();
            } else {
                this.panX = 40.0;
                this.panY = 40.0;
                this.zoom = 1.0;
            }
        } else {
            if (state.getCurrentTeamId() == null) {
                com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                    new com.gtceu.calcboard.network.packet.c2s.C2SRequestWorkspacePacket(new java.util.UUID(0L, 0L), "page_main")
                );
                com.gtceu.calcboard.client.gui.widget.BoardToast.show("gui.gtcalcboard.toast.team_no_party");
                return;
            }
            BoardPage activeLocal = BoardManager.getInstance().getActivePage();
            if (activeLocal != null) {
                activeLocal.setPanX(this.panX);
                activeLocal.setPanY(this.panY);
                activeLocal.setZoom(this.zoom);
            }
            state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
            com.gtceu.calcboard.api.solver.WorkspaceFlowCoordinator.invalidate();
            java.util.UUID teamId = state.getCurrentTeamId();
            String activePageId = state.getActiveTeamPageId() != null ? state.getActiveTeamPageId() : "page_main";
            com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                new com.gtceu.calcboard.network.packet.c2s.C2SRequestWorkspacePacket(teamId, activePageId)
            );
            com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                new com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket(teamId, activePageId, true)
            );

            restoreTeamViewport(state, activePageId);
        }
        rebuildBoardWidgets();
        markSummaryDirty();
    }

    public double getPanX() { return panX; }
    public void setPanX(double panX) {
        this.panX = panX;
        lastPanX = panX;
        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        if (teamState.isTeamMode()) {
            teamState.setPageViewport(teamState.getActiveTeamPageId(), panX, this.panY, this.zoom);
        } else {
            BoardPage active = BoardManager.getInstance().getActivePage();
            if (active != null) active.setPanX(panX);
        }
    }
    public double getPanY() { return panY; }
    public void setPanY(double panY) {
        this.panY = panY;
        lastPanY = panY;
        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        if (teamState.isTeamMode()) {
            teamState.setPageViewport(teamState.getActiveTeamPageId(), this.panX, panY, this.zoom);
        } else {
            BoardPage active = BoardManager.getInstance().getActivePage();
            if (active != null) active.setPanY(panY);
        }
    }
    public double getZoom() { return zoom; }
    public void setZoom(double zoom) {
        this.zoom = zoom;
        lastZoom = zoom;
        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        if (teamState.isTeamMode()) {
            teamState.setPageViewport(teamState.getActiveTeamPageId(), this.panX, this.panY, zoom);
        } else {
            BoardPage active = BoardManager.getInstance().getActivePage();
            if (active != null) active.setZoom(zoom);
        }
    }

    @Override
    public void onClose() {
        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        if (teamState.isTeamMode()) {
            teamState.setPageViewport(teamState.getActiveTeamPageId(), this.panX, this.panY, this.zoom);
        }
        teamSyncCoordinator.onScreenClosed();
        lastPanX = this.panX;
        lastPanY = this.panY;
        lastZoom = this.zoom;
        if (!teamState.isTeamMode()) {
            BoardPage active = BoardManager.getInstance().getActivePage();
            if (active != null) {
                active.setPanX(this.panX);
                active.setPanY(this.panY);
                active.setZoom(this.zoom);
            }
        }
        BoardManager.getInstance().setSummaryOverlayCollapsed(this.summaryOverlay.isCollapsed());
        BoardManager.getInstance().setHotkeyHudExpanded(this.hotkeyHudWidget.isExpanded());
        BoardManager.getInstance().setFavoritesDockExpanded(this.favoritesDockWidget.isExpanded());
        BoardManager.getInstance().saveToFile(BoardManager.getInstance().getDefaultSaveFile(), this.panX, this.panY, this.zoom);
        lastBoardScreenActiveTime = 0;
        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [UI] BoardScreen closed. State saved.");
        com.gtceu.calcboard.client.web.WebSyncEventBus.publishCurrentBoard();
        if (this.previousScreen != null && this.minecraft != null && this.minecraft.player != null && this.minecraft.player.isAlive()) {
            Screen prev = this.previousScreen;
            this.returningToPreviousScreen = true;
            this.previousScreen = null;
            this.minecraft.setScreen(prev);
        } else {
            if (this.minecraft != null) {
                super.onClose();
            }
        }
    }

    @Override
    public void closeScreen() {
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return BoardManager.getInstance().isPauseGameInSingleplayer();
    }
}
