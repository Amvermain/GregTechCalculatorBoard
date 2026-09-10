package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.FolderBlueprintPackage;
import com.gtceu.calcboard.client.gui.action.BoardActionHandler;
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
import com.gtceu.calcboard.integration.emi.BoardMenu;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.*;

/**
 * Main GUI Screen for GregTech Calculator Board.
 * Acts as the master orchestrator coordinating canvas rendering, dialog management, and editor actions.
 */
public class BoardScreen extends AbstractContainerScreen<BoardMenu> {
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
    private final SummaryOverlay summaryOverlay = new SummaryOverlay();
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
    private boolean summaryDirty = true;
    private double lastMouseX, lastMouseY;
    private boolean summaryAutoCollapsedForInspector = false;

    public BoardScreen() {
        this(new BoardMenu(0, Minecraft.getInstance() != null && Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getInventory() : null));
    }

    public BoardScreen(BoardMenu menu) {
        super(menu, Minecraft.getInstance() != null && Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getInventory() : new Inventory(null), Component.translatable("gui.gtcalcboard.title"));
        this.imageWidth = 0;
        this.imageHeight = 0;
        BoardPage activePage = BoardManager.getInstance().getActivePage();
        this.panX = activePage.getPanX();
        this.panY = activePage.getPanY();
        this.zoom = activePage.getZoom();
        lastPanX = this.panX;
        lastPanY = this.panY;
        lastZoom = this.zoom;
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
        super.removed();
        if (this.dialogManager != null) {
            this.dialogManager.destroy();
        }
        teamSyncCoordinator.onScreenRemoved();
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
        if (wireRenderer != null) {
            wireRenderer.markDirty();
        }
        markSummaryDirty();
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
            cachedSummary = FlowGraphSolver.computeSummary(getGraph());
            summaryDirty = false;
        }
    }

    public boolean isAnyModalOpen() {
        return dialogManager.isAnyModalOpen();
    }

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

    public boolean scaleLoopToSteadyState(String targetNodeId) {
        return actionHandler.scaleLoopToSteadyState(targetNodeId);
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

    public void performAutoRatio() { toolbarWidget.performAutoRatio(); }
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

    public void openSettingsDialog() { dialogManager.openSettingsDialog(); }
    public void openExportFolderDialog(String folderPath) { dialogManager.openExportFolderDialog(folderPath); }
    public void openImportFolderDialog() { dialogManager.openImportFolderDialog(); }
    public void openImportFolderDialog(FolderBlueprintPackage pkg) { dialogManager.openImportFolderDialog(pkg); }
    public void openDeletePageDialog(int pageIndex, String pageName) { dialogManager.openDeletePageDialog(pageIndex, pageName); }
    public void openDeleteMultiplePagesDialog(List<String> pageIds) { dialogManager.openDeleteMultiplePagesDialog(pageIds); }
    public void openDeleteTeamPageDialog(String pageId, String pageName) { dialogManager.openDeleteTeamPageDialog(pageId, pageName); }
    public void openJunctionSupplyDialog(RecipeNode node) { dialogManager.openJunctionSupplyDialog(node); }
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
    public void openFrameEditDialog(CanvasGroupFrame frame) { dialogManager.openFrameEditDialog(frame); }
    public void openNoteEditDialog(CanvasStickyNote note) { dialogManager.openNoteEditDialog(note); }
    public void openTargetOutputRateDialog(RecipeNode node, int outputIndex) { dialogManager.openTargetOutputRateDialog(node, outputIndex); }

    public void openModuleSubPage(RecipeNode moduleNode) { navigationHandler.openModuleSubPage(moduleNode); }
    public void returnToParentPage() { navigationHandler.returnToParentPage(); }

    public double getPanX() { return panX; }
    public void setPanX(double panX) {
        this.panX = panX;
        lastPanX = panX;
        BoardPage active = BoardManager.getInstance().getActivePage();
        if (active != null) active.setPanX(panX);
    }
    public double getPanY() { return panY; }
    public void setPanY(double panY) {
        this.panY = panY;
        lastPanY = panY;
        BoardPage active = BoardManager.getInstance().getActivePage();
        if (active != null) active.setPanY(panY);
    }
    public double getZoom() { return zoom; }
    public void setZoom(double zoom) {
        this.zoom = zoom;
        lastZoom = zoom;
        BoardPage active = BoardManager.getInstance().getActivePage();
        if (active != null) active.setZoom(zoom);
    }

    @Override
    public void onClose() {
        teamSyncCoordinator.onScreenClosed();
        lastPanX = this.panX;
        lastPanY = this.panY;
        lastZoom = this.zoom;
        BoardPage active = BoardManager.getInstance().getActivePage();
        if (active != null) {
            active.setPanX(this.panX);
            active.setPanY(this.panY);
            active.setZoom(this.zoom);
        }
        BoardManager.getInstance().setSummaryOverlayCollapsed(this.summaryOverlay.isCollapsed());
        BoardManager.getInstance().setHotkeyHudExpanded(this.hotkeyHudWidget.isExpanded());
        BoardManager.getInstance().setFavoritesDockExpanded(this.favoritesDockWidget.isExpanded());
        BoardManager.getInstance().saveToFile(BoardManager.getInstance().getDefaultSaveFile(), this.panX, this.panY, this.zoom);
        lastBoardScreenActiveTime = 0;
        GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [UI] BoardScreen closed. State saved.");
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return BoardManager.getInstance().isPauseGameInSingleplayer();
    }
}
