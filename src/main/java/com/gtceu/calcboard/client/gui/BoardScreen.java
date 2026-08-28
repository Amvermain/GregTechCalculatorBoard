package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.client.gui.dialog.BoardSettingsDialog;
import com.gtceu.calcboard.client.gui.dialog.DeletePageConfirmDialog;
import com.gtceu.calcboard.client.gui.dialog.TutorialExitConfirmDialog;
import com.gtceu.calcboard.client.gui.dialog.ExportToTeamDialog;
import com.gtceu.calcboard.client.gui.dialog.FrameEditDialog;
import com.gtceu.calcboard.client.gui.dialog.GlobalBalanceDashboardDialog;
import com.gtceu.calcboard.client.gui.dialog.GuideDialog;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.dialog.MultiblockBOMDialog;
import com.gtceu.calcboard.client.gui.dialog.NoteEditDialog;
import com.gtceu.calcboard.client.gui.dialog.RecentSavesDialog;
import com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog;
import com.gtceu.calcboard.client.gui.dialog.SaveToTeamDialog;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.render.CanvasGroupFrameRenderer;
import com.gtceu.calcboard.client.gui.render.CanvasStickyNoteRenderer;
import com.gtceu.calcboard.client.gui.render.ConnectionRenderer;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.FavoritesDockWidget;
import com.gtceu.calcboard.client.gui.widget.HotkeyHudWidget;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.client.gui.widget.PageTabBarWidget;
import com.gtceu.calcboard.client.gui.widget.SummaryOverlay;
import com.gtceu.calcboard.client.gui.widget.ToolbarWidget;
import com.gtceu.calcboard.client.gui.widget.WorkspaceTabBarWidget;

import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.tutorial.TutorialOverlay;
import com.gtceu.calcboard.client.gui.tutorial.WelcomeTutorialDialog;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Main GUI Screen for GregTech Calculator Board.
 * Acts as the master orchestrator coordinating canvas rendering, node widgets, and sub-components.
 */
public class BoardScreen extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<com.gtceu.calcboard.integration.emi.BoardMenu> {
    public static final int LEFT_MARGIN = 48;
    public static double lastPanX = 40.0;
    public static double lastPanY = 40.0;
    public static double lastZoom = 1.0;
    private static long lastBoardScreenActiveTime = 0;

    public static boolean isBoardContext() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.screen == null) return false;
        if (mc.screen instanceof BoardScreen) return true;
        if (mc.screen.getClass().getName().contains("RecipeScreen") || mc.screen.getClass().getName().contains("Emi")) {
            return (System.currentTimeMillis() - lastBoardScreenActiveTime) < 30000;
        }
        return false;
    }

    private final List<NodeWidget> nodeWidgets = new ArrayList<>();

    // Sub-components & Handlers
    private final BoardSelectionModel selectionModel = new BoardSelectionModel();
    private final WorkspaceTabBarWidget workspaceTabBar = new WorkspaceTabBarWidget(this);
    private final PageTabBarWidget pageTabBar = new PageTabBarWidget(this);
    private final SummaryOverlay summaryOverlay = new SummaryOverlay();
    private final ToolbarWidget toolbarWidget = new ToolbarWidget(this);
    private final HotkeyHudWidget hotkeyHudWidget = new HotkeyHudWidget(this);
    private final FavoritesDockWidget favoritesDockWidget = new FavoritesDockWidget(this);
    private final CanvasInteractionHandler canvasHandler = new CanvasInteractionHandler(this);
    private WelcomeTutorialDialog welcomeDialog = new WelcomeTutorialDialog();
    private RecipeSearchDialog searchDialog;
    private MachineConfigDialog machineConfigDialog;
    private GuideDialog guideDialog;
    private DeletePageConfirmDialog deletePageDialog;
    private TutorialExitConfirmDialog tutorialExitDialog;
    private GlobalBalanceDashboardDialog globalBalanceDialog;
    private MultiblockBOMDialog multiblockBOMDialog;
    private SaveToTeamDialog saveToTeamDialog;
    private ExportToTeamDialog exportToTeamDialog;
    private RecentSavesDialog recentSavesDialog;
    private FrameEditDialog frameEditDialog;
    private NoteEditDialog noteEditDialog;
    private BoardSettingsDialog settingsDialog;

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
        this(new com.gtceu.calcboard.integration.emi.BoardMenu(0, Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getInventory() : null));
    }

    public BoardScreen(com.gtceu.calcboard.integration.emi.BoardMenu menu) {
        super(menu, Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getInventory() : new net.minecraft.world.entity.player.Inventory(null), Component.translatable("gui.gtcalcboard.title"));
        this.imageWidth = 0;
        this.imageHeight = 0;
        var activePage = BoardManager.getInstance().getActivePage();
        this.panX = activePage.getPanX();
        this.panY = activePage.getPanY();
        this.zoom = activePage.getZoom();
        lastPanX = this.panX;
        lastPanY = this.panY;
        lastZoom = this.zoom;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
    }

    public FlowGraph getGraph() {
        com.gtceu.calcboard.client.team.ClientWorkspaceState state = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance();
        if (state.isTeamMode()) {
            return state.getActiveTeamGraph();
        }
        return BoardManager.getInstance().getActiveGraph();
    }

    public BoardSelectionModel getSelectionModel() {
        return selectionModel;
    }

    public Set<String> getSelectedNodeIds() {
        return selectionModel.getSelectedNodeIds();
    }

    public Set<String> getSelectedNoteIds() {
        return selectionModel.getSelectedNoteIds();
    }

    public Set<String> getSelectedFrameIds() {
        return selectionModel.getSelectedFrameIds();
    }

    public boolean isNodeSelected(String id) {
        return selectionModel.isSelected(id);
    }

    public boolean isNoteSelected(String id) {
        return selectionModel.isNoteSelected(id);
    }

    public boolean isFrameSelected(String id) {
        return selectionModel.isFrameSelected(id);
    }

    public void selectNode(String id, boolean multi) {
        selectionModel.select(id, multi);
    }

    public void selectNote(String id, boolean multi) {
        selectionModel.selectNote(id, multi);
    }

    public void selectFrame(String id, boolean multi) {
        selectionModel.selectFrame(id, multi);
    }

    public void toggleSelectNode(String id) {
        selectionModel.toggle(id);
    }

    public void toggleSelectNote(String id) {
        selectionModel.toggleNote(id);
    }

    public void toggleSelectFrame(String id) {
        selectionModel.toggleFrame(id);
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

    public void flipSelectedNodes() {
        if (!ensureEditPermission()) return;
        List<String> nodeIds = new ArrayList<>(selectionModel.getSelectedNodeIds());
        if (nodeIds.isEmpty()) {
            double canvasX = toCanvasX(lastMouseX);
            double canvasY = toCanvasY(lastMouseY);
            for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
                NodeWidget w = nodeWidgets.get(i);
                if (w.isPointInside(canvasX, canvasY)) {
                    nodeIds.add(w.getNode().getId());
                    break;
                }
            }
        }
        if (nodeIds.isEmpty()) return;

        Map<String, Boolean> prevStates = new HashMap<>();
        Map<String, Boolean> newStates = new HashMap<>();
        for (String id : nodeIds) {
            RecipeNode n = getGraph().findNodeById(id);
            if (n != null) {
                prevStates.put(id, n.isFlipped());
                newStates.put(id, !n.isFlipped());
                n.setFlipped(!n.isFlipped());
            }
        }
        if (!newStates.isEmpty()) {
            recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.FlipNodesCommand(prevStates, newStates));
            markSummaryDirty();
            for (NodeWidget w : nodeWidgets) {
                if (newStates.containsKey(w.getNode().getId())) {
                    w.invalidateCache();
                }
            }
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.1F));
            BoardToast.show(Component.literal("§b⇄ ").append(Component.translatable("message.gtcalcboard.flipped_nodes", newStates.size())));
        }
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
                BoardToast.show(Component.literal("§e↶ ").append(Component.translatable("message.gtcalcboard.undo", cmd.getDescription())));
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
                BoardToast.show(Component.literal("§a↷ ").append(Component.translatable("message.gtcalcboard.redo", cmd.getDescription())));
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
        if (this.minecraft == null || this.minecraft.player == null || this.minecraft.level == null) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
            return;
        }
        super.init();
        this.searchDialog = new RecipeSearchDialog(this);
        this.machineConfigDialog = new MachineConfigDialog(this);
        this.guideDialog = new GuideDialog(this);
        this.deletePageDialog = new DeletePageConfirmDialog(this);
        this.tutorialExitDialog = new TutorialExitConfirmDialog(this);
        this.globalBalanceDialog = new GlobalBalanceDashboardDialog(this);
        this.multiblockBOMDialog = new MultiblockBOMDialog(this);
        this.saveToTeamDialog = new SaveToTeamDialog(this);
        this.exportToTeamDialog = new ExportToTeamDialog(this);
        this.recentSavesDialog = new RecentSavesDialog(this);
        this.frameEditDialog = new FrameEditDialog(this);
        this.noteEditDialog = new NoteEditDialog(this);
        this.settingsDialog = new BoardSettingsDialog(this);

        BoardManager.getInstance().setPageRemovalListener(page -> {
            if (TutorialManager.getInstance().isTutorialPage(page.getId())) {
                TutorialManager.getInstance().stopTutorial();
            }
        });
        rebuildWidgets();

        if (minecraft != null && minecraft.getConnection() != null) {
            UUID teamId = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance().getCurrentTeamId();
            String pageId = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance().getActiveTeamPageId();
            boolean isTeamMode = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance().isTeamMode();
            com.gtceu.calcboard.network.NetworkHandler.sendToServer(new com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket(teamId, pageId, isTeamMode));
            com.gtceu.calcboard.network.NetworkHandler.sendToServer(new com.gtceu.calcboard.network.packet.c2s.C2SRequestWorkspacePacket(teamId, pageId != null ? pageId : "page_main"));
        }

        RecipeSearchDialog.ensureGlobalRecipesCachedAsync(null);
        this.summaryOverlay.setCollapsed(BoardManager.getInstance().isSummaryOverlayCollapsed());
        this.hotkeyHudWidget.setExpanded(BoardManager.getInstance().isHotkeyHudExpanded());
        this.favoritesDockWidget.setExpanded(BoardManager.getInstance().isFavoritesDockExpanded());

        if (this.width < 640) {
            summaryOverlay.setCollapsed(true);
        }

        if (!BoardManager.getInstance().hasSeenWelcomePrompt() && getGraph().getNodes().isEmpty()) {
            welcomeDialog.show();
            summaryOverlay.setCollapsed(true);
            BoardManager.getInstance().setHasSeenWelcomePrompt(true);
            BoardManager.getInstance().saveToFile(BoardManager.getInstance().getDefaultSaveFile());
        }

        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info(
                "[GTCalcBoard] [UI] BoardScreen opened. (Active Page: '{}', Nodes: {}, Wires: {}, TeamMode: {})",
                BoardManager.getInstance().getActivePage() != null ? BoardManager.getInstance().getActivePage().getName() : "Main",
                getGraph().getNodes().size(),
                getGraph().getConnections().size(),
                com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance().isTeamMode()
        );
    }

    public BoardSettingsDialog getSettingsDialog() {
        return settingsDialog;
    }

    public void openSettingsDialog() {
        if (settingsDialog != null) {
            settingsDialog.open();
        }
    }

    public SaveToTeamDialog getSaveToTeamDialog() {
        return saveToTeamDialog;
    }

    public ExportToTeamDialog getExportToTeamDialog() {
        return exportToTeamDialog;
    }

    public RecentSavesDialog getRecentSavesDialog() {
        return recentSavesDialog;
    }

    public WorkspaceTabBarWidget getWorkspaceTabBar() {
        return workspaceTabBar;
    }

    public void openDeletePageDialog(int pageIndex, String pageName) {
        if (deletePageDialog != null) {
            deletePageDialog.open(pageIndex, pageName);
        }
    }

    public void openDeleteTeamPageDialog(String pageId, String pageName) {
        if (deletePageDialog != null) {
            deletePageDialog.openTeamPage(pageId, pageName);
        }
    }

    public GlobalBalanceDashboardDialog getGlobalBalanceDialog() {
        return globalBalanceDialog;
    }

    public MultiblockBOMDialog getMultiblockBOMDialog() {
        return multiblockBOMDialog;
    }

    public GuideDialog getGuideDialog() {
        return guideDialog;
    }

    public DeletePageConfirmDialog getDeletePageDialog() {
        return deletePageDialog;
    }

    public TutorialExitConfirmDialog getTutorialExitDialog() {
        return tutorialExitDialog;
    }

    public void openTutorialExitDialog(int targetPageIndex) {
        if (tutorialExitDialog != null) {
            tutorialExitDialog.openForSwitch(targetPageIndex);
        }
    }

    public void openTutorialExitDialogForNewPage() {
        if (tutorialExitDialog != null) {
            tutorialExitDialog.openForCreateNewPage();
        }
    }

    public void openTutorialExitDialogForTeamPage(String teamPageId) {
        if (tutorialExitDialog != null) {
            tutorialExitDialog.openForTeamPage(teamPageId);
        }
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

    public int getDynamicLeftMargin() {
        int maxRight = 8;
        try {
            for (var child : this.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    if (widget instanceof net.minecraft.client.gui.components.EditBox) continue;
                    if (widget.visible && widget.getY() < 60 && widget.getX() >= 0 && widget.getX() < this.width / 3) {
                        int right = widget.getX() + widget.getWidth();
                        if (right > maxRight) {
                            maxRight = right;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        return maxRight > 8 ? (maxRight + 6) : 8;
    }

    public int getPageTabY() {
        return com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance().isCollaborationEnabled() ? 22 : 2;
    }

    public int getToolbarY() {
        return com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance().isCollaborationEnabled() ? 42 : 22;
    }

    public int getHeaderBottomY() {
        return com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance().isCollaborationEnabled() ? 64 : 44;
    }

    public int getFavoritesDockY() {
        int maxBottom = getHeaderBottomY() + 6;
        try {
            for (var child : this.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget widget) {
                    if (widget instanceof net.minecraft.client.gui.components.EditBox) continue;
                    if (widget.visible && widget.getX() < 160 && widget.getY() < 120) {
                        int bottom = widget.getY() + widget.getHeight();
                        if (bottom > maxBottom) {
                            maxBottom = bottom;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return maxBottom;
    }

    private long lastEditTimestamp = 0;
    private int presencePingTicks = 0;

    public void markTeamDirty() {
        com.gtceu.calcboard.client.team.ClientWorkspaceState state = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance();
        if (state.isTeamMode()) {
            state.markPageDirty(state.getActiveTeamPageId());
            this.lastEditTimestamp = System.currentTimeMillis();
        }
    }

    public boolean ensureEditPermission() {
        com.gtceu.calcboard.client.team.ClientWorkspaceState state = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance();
        if (!state.isTeamMode()) {
            return true;
        }

        String activePageId = state.getActiveTeamPageId();
        state.markPageDirty(activePageId);
        this.lastEditTimestamp = System.currentTimeMillis();

        if (state.doesHoldLock(activePageId)) {
            return true;
        }

        com.gtceu.calcboard.server.storage.TeamWorkspacePage page = state.getRemotePage(activePageId);
        if (page != null && page.isLocked() && !state.doesHoldLock(activePageId)) {
            String lockHolder = page.getLockHolderName() != null && !page.getLockHolderName().isEmpty()
                ? page.getLockHolderName()
                : state.resolvePlayerName(page.getLockHolderUUID());
            com.gtceu.calcboard.client.gui.widget.BoardToast.show(Component.literal("§c🔒 ").append(Component.translatable("gui.gtcalcboard.lock.locked_by", lockHolder)));
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.VILLAGER_NO, 1.0F)
            );
            return false;
        }
        // Auto-acquire lock seamlessly upon editing
        UUID teamId = state.getCurrentTeamId();
        com.gtceu.calcboard.network.NetworkHandler.sendToServer(new com.gtceu.calcboard.network.packet.c2s.C2SAcquireLockPacket(teamId, activePageId));
        state.setLockHeld(activePageId, true);
        return true;
    }

    @Override
    public void containerTick() {
        if (this.minecraft == null || this.minecraft.player == null) {
            if (this.minecraft != null) {
                this.minecraft.setScreen(null);
            }
            return;
        }
        if (!this.minecraft.player.isAlive() || this.minecraft.player.isRemoved()) {
            this.onClose();
            return;
        }
        super.containerTick();
        com.gtceu.calcboard.client.team.ClientWorkspaceState state = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance();
        if (state.isCollaborationEnabled() && state.isTeamMode()) {
            presencePingTicks++;
            if (presencePingTicks >= 40) { // Every 2 seconds
                presencePingTicks = 0;
                com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                    new com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket(state.getCurrentTeamId(), state.getActiveTeamPageId(), true)
                );
            }
        }
        if (state.isTeamMode()) {
            String activePageId = state.getActiveTeamPageId();
            if (state.isPageDirty(activePageId) && lastEditTimestamp > 0 && (System.currentTimeMillis() - lastEditTimestamp > 3000)) {
                state.autoCommitAndRelease(this, activePageId);
                rebuildWidgets();
                markSummaryDirty();
            }
        }
    }

    public void addNode(RecipeNode node) {
        if (!ensureEditPermission()) return;
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
        List<RecipeNode> removedNodes = new ArrayList<>();
        List<CanvasGroupFrame> removedFrames = new ArrayList<>();
        Set<String> targetNodeIds = new HashSet<>();

        if (n.isCompoundNode()) {
            List<RecipeNode> siblings = getGraph().findCompoundSiblingNodes(n.getCompoundGroupId());
            removedNodes.addAll(siblings);
            for (RecipeNode sib : siblings) {
                targetNodeIds.add(sib.getId());
            }
            CanvasGroupFrame cFrame = getGraph().findCompoundFrame(n.getCompoundGroupId());
            if (cFrame != null) {
                removedFrames.add(cFrame);
            }
        } else {
            removedNodes.add(n);
            targetNodeIds.add(n.getId());
        }

        List<FlowGraph.ConnectionEdge> removedEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge e : getGraph().getConnections()) {
            if (targetNodeIds.contains(e.fromNodeId()) || targetNodeIds.contains(e.toNodeId())) {
                removedEdges.add(e);
            }
        }

        getGraph().removeNode(n);

        List<BoardCommand> cmds = new ArrayList<>();
        cmds.add(new BoardCommand.RemoveNodesCommand(removedNodes, removedEdges, "Delete " + n.getName()));
        if (!removedFrames.isEmpty()) {
            cmds.add(new BoardCommand.RemoveFramesCommand(removedFrames, "Delete compound frame"));
        }
        if (cmds.size() == 1) {
            recordCommand(cmds.get(0));
        } else {
            recordCommand(new BoardCommand.CompoundCommand(cmds, "Delete compound " + n.getName()));
        }

        for (String nid : targetNodeIds) {
            selectionModel.getSelectedNodeIds().remove(nid);
        }
        rebuildWidgets();
        markSummaryDirty();
        for (RecipeNode rn : removedNodes) {
            TutorialManager.getInstance().onNodeRemoved(rn);
        }
    }

    public void markSummaryDirty() {
        this.summaryDirty = true;
        if (globalBalanceDialog != null) {
            globalBalanceDialog.markDirty();
        }
        if (multiblockBOMDialog != null) {
            multiblockBOMDialog.markDirty();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        lastBoardScreenActiveTime = System.currentTimeMillis();
        this.lastMouseX = mouseX;
        this.lastMouseY = mouseY;

        // 0. Update graph calculations and efficiencies if dirty
        if (summaryDirty || cachedSummary == null) {
            getGraph().cleanupInvalidConnections();
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

        // 2.5 Render Visual Canvas Group Frames & Sticky Notes (Layer below wires and nodes)
        CanvasGroupFrameRenderer.renderFrames(graphics, getGraph(), canvasMouseX, canvasMouseY, null, getSelectedFrameIds());
        CanvasStickyNoteRenderer.renderNotes(graphics, getGraph(), canvasMouseX, canvasMouseY, getSelectedNoteIds());

        // 5. Render Node Widgets with Viewport Culling
        double screenLeft = -panX / zoom - 100;
        double screenRight = (-panX + width) / zoom + 100;
        double screenTop = -panY / zoom - 100;
        double screenBottom = (-panY + height) / zoom + 100;

        // 3. Render Static Connection Lines with Batching and Viewport Culling
        ConnectionRenderer.beginBatch(graphics);
        List<float[]> visibleWires = new ArrayList<>();
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

                    float minX = Math.min(x1, x2) - 40;
                    float maxX = Math.max(x1, x2) + 40;
                    float minY = Math.min(y1, y2) - 20;
                    float maxY = Math.max(y1, y2) + 20;
                    if (maxX < screenLeft || minX > screenRight || maxY < screenTop || minY > screenBottom) {
                        continue;
                    }

                    float fromDirX = fromNode.isFlipped() ? -1.0f : 1.0f;
                    float toDirX = toNode.isFlipped() ? 1.0f : -1.0f;

                    boolean isHovered = ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, fromDirX, toDirX, canvasMouseX, canvasMouseY, 6.0);
                    boolean isWireGlowing = TutorialManager.getInstance().isWireGlowing(fromNode.getId(), toNode.getId());
                    int defWireColor = BoardManager.getInstance().getWireColor();
                    int lineColor = isHovered ? 0xFFFF3366 : (isWireGlowing ? TutorialManager.getGlowBorderColor(0xFF55FF88) : defWireColor);
                    float wireThick = isWireGlowing ? 3.5f : 2.0f;
                    ConnectionRenderer.addBezierToBatch(x1, y1, x2, y2, fromDirX, toDirX, lineColor, wireThick);
                    visibleWires.add(new float[]{x1, y1, x2, y2, fromDirX, toDirX});
                }
            }
        }

        // 4. Render Active Wire Dragging
        NodeWidget wireStart = canvasHandler.getWireStartNode();
        if (wireStart != null) {
            float x1, y1;
            int matchedColor = BoardManager.getInstance().getMatchedWireColor();
            int dragWireColor = Screen.hasShiftDown() ? 0xFFFFD700 : matchedColor;
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
        ConnectionRenderer.endBatch();

        // Draw animated flow pulse dots (Single-batch GPU rendering)
        if (zoom >= 0.28 && BoardManager.getInstance().isShowWirePulseAnimation()) {
            ConnectionRenderer.renderPulseDotsBatch(graphics, visibleWires);
        }

        // 5. Render Node Widgets with Viewport Culling & Isolated Layering
        boolean isLOD = (zoom < 0.28);
        if (!isLOD) {
            graphics.flush();
            Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.disableDepthTest();
        }

        for (int i = 0; i < nodeWidgets.size(); i++) {
            NodeWidget widget = nodeWidgets.get(i);
            double nx = widget.getNode().getPosX();
            double ny = widget.getNode().getPosY();
            int nw = widget.getWidth();
            int nh = widget.getHeight();

            if (nx + nw >= screenLeft && nx <= screenRight && ny + nh >= screenTop && ny <= screenBottom) {
                widget.render(graphics, (int) canvasMouseX, (int) canvasMouseY, partialTicks);
                if (!isLOD) {
                    graphics.flush();
                    Minecraft.getInstance().renderBuffers().bufferSource().endBatch();
                    RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                    RenderSystem.disableDepthTest();
                }
            }
        }
        if (isLOD) {
            graphics.flush();
        }

        // 4.5 Render Quick Action Marker
        canvasHandler.checkMarkerCursorDistance(canvasMouseX, canvasMouseY);
        if (canvasHandler.hasQuickAddMarker()) {
            double qx = canvasHandler.getQuickAddMarkerCanvasX();
            double qy = canvasHandler.getQuickAddMarkerCanvasY();

            boolean searchHovered = canvasMouseX >= qx - 44 && canvasMouseX <= qx - 24 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
            boolean junctionHovered = canvasMouseX >= qx - 21 && canvasMouseX <= qx - 1 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
            boolean frameHovered = canvasMouseX >= qx + 2 && canvasMouseX <= qx + 22 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;
            boolean noteHovered = canvasMouseX >= qx + 25 && canvasMouseX <= qx + 45 && canvasMouseY >= qy - 10 && canvasMouseY <= qy + 10;

            // Outer capsule background
            graphics.fill((int)(qx - 47), (int)(qy - 13), (int)(qx + 48), (int)(qy + 13), 0xDD0F172A);
            graphics.renderOutline((int)(qx - 47), (int)(qy - 13), 95, 26, 0x88334155);

            // 1. [🔍] Search Recipe (Emerald)
            int searchBg = searchHovered ? 0xEE10B981 : 0xBB059669;
            int searchBorder = searchHovered ? 0xFF6EE7B7 : 0xCC10B981;
            graphics.fill((int)(qx - 44), (int)(qy - 10), (int)(qx - 24), (int)(qy + 10), searchBg);
            graphics.renderOutline((int)(qx - 44), (int)(qy - 10), 20, 20, searchBorder);
            String searchIcon = "🔍";
            graphics.drawString(font, searchIcon, (int)(qx - 34 - font.width(searchIcon) / 2.0f), (int)(qy - 4), 0xFFFFFFFF, false);

            // 2. [🔀] Insert Junction (Sky/Cyan)
            int juncBg = junctionHovered ? 0xEE0284C7 : 0xBB0369A1;
            int juncBorder = junctionHovered ? 0xFF7DD3FC : 0xCC0284C7;
            graphics.fill((int)(qx - 21), (int)(qy - 10), (int)(qx - 1), (int)(qy + 10), juncBg);
            graphics.renderOutline((int)(qx - 21), (int)(qy - 10), 20, 20, juncBorder);
            String juncIcon = "🔀";
            graphics.drawString(font, juncIcon, (int)(qx - 11 - font.width(juncIcon) / 2.0f), (int)(qy - 4), 0xFFFFFFFF, false);

            // 3. [🖼] Group Frame (Purple/Violet)
            int frameBg = frameHovered ? 0xEE8B5CF6 : 0xBB6D28D9;
            int frameBorder = frameHovered ? 0xFFC4B5FD : 0xCC7C3AED;
            graphics.fill((int)(qx + 2), (int)(qy - 10), (int)(qx + 22), (int)(qy + 10), frameBg);
            graphics.renderOutline((int)(qx + 2), (int)(qy - 10), 20, 20, frameBorder);
            String frameIcon = "🖼";
            graphics.drawString(font, frameIcon, (int)(qx + 12 - font.width(frameIcon) / 2.0f), (int)(qy - 4), 0xFFFFFFFF, false);

            // 4. [📝] Sticky Note / Memo (Amber/Gold)
            int noteBg = noteHovered ? 0xEEF59E0B : 0xBBB45309;
            int noteBorder = noteHovered ? 0xFFFDE68A : 0xCCD97706;
            graphics.fill((int)(qx + 25), (int)(qy - 10), (int)(qx + 45), (int)(qy + 10), noteBg);
            graphics.renderOutline((int)(qx + 25), (int)(qy - 10), 20, 20, noteBorder);
            String noteIcon = "📝";
            graphics.drawString(font, noteIcon, (int)(qx + 35 - font.width(noteIcon) / 2.0f), (int)(qy - 4), 0xFFFFFFFF, false);
        }

        // Render Active Marquee Box Selection
        canvasHandler.renderMarquee(graphics);

        graphics.pose().popPose();

        graphics.flush();
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        RenderSystem.disableDepthTest();

        // 6. Render Workspace Mode Tab Bar, Page Tab Bar, Top Toolbar, Hotkey HUD, Favorites Dock and Summary Overlay
        workspaceTabBar.render(graphics, mouseX, mouseY, partialTicks);
        pageTabBar.render(graphics, mouseX, mouseY, partialTicks);
        toolbarWidget.render(graphics, mouseX, mouseY);
        if (BoardManager.getInstance().isShowHotkeyHud()) {
            hotkeyHudWidget.render(graphics, mouseX, mouseY, partialTicks);
        }
        favoritesDockWidget.render(graphics, mouseX, mouseY, partialTicks);

        if (summaryDirty || cachedSummary == null) {
            cachedSummary = FlowGraphSolver.computeSummary(getGraph());
            summaryDirty = false;
        }
        summaryOverlay.render(graphics, width, height, cachedSummary, mouseX, mouseY);

        // 6.5 Render bottom-center background loading status HUD (disappears when 100% complete)
        renderCentralLoadingCard(graphics, font, width, height);

        // 7. Render Tooltips only if no modal dialog is open
        if (!isAnyModalOpen()) {
            BoardTooltipRenderer.renderTooltips(this, graphics, font, mouseX, mouseY);
            favoritesDockWidget.renderTooltips(graphics, font, mouseX, mouseY);
            workspaceTabBar.renderTooltips(graphics, font, mouseX, mouseY);
        }

        super.render(graphics, mouseX, mouseY, partialTicks);

        // 8. Top-level Modal Dialogs (Highest Layer: Clear depth buffer for complete modal isolation)
        if (isAnyModalOpen()) {
            graphics.flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.disableDepthTest();
        }

        if (welcomeDialog != null && welcomeDialog.isVisible()) {
            welcomeDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (settingsDialog != null && settingsDialog.isVisible()) {
            settingsDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (globalBalanceDialog != null && globalBalanceDialog.isVisible()) {
            globalBalanceDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (multiblockBOMDialog != null && multiblockBOMDialog.isVisible()) {
            multiblockBOMDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (guideDialog != null && guideDialog.isVisible()) {
            guideDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (deletePageDialog != null && deletePageDialog.isVisible()) {
            deletePageDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (tutorialExitDialog != null && tutorialExitDialog.isVisible()) {
            tutorialExitDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (saveToTeamDialog != null && saveToTeamDialog.isVisible()) {
            saveToTeamDialog.render(graphics, mouseX, mouseY, partialTicks);
        } else if (exportToTeamDialog != null && exportToTeamDialog.isVisible()) {
            exportToTeamDialog.render(graphics, mouseX, mouseY, partialTicks);
        } else if (recentSavesDialog != null && recentSavesDialog.isVisible()) {
            recentSavesDialog.render(graphics, mouseX, mouseY, partialTicks);
        } else if (searchDialog != null && searchDialog.isVisible()) {
            searchDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            machineConfigDialog.render(graphics, mouseX, mouseY, partialTicks, width, height);
        } else if (frameEditDialog != null && frameEditDialog.isVisible()) {
            frameEditDialog.render(graphics, width, height, mouseX, mouseY);
        } else if (noteEditDialog != null && noteEditDialog.isVisible()) {
            noteEditDialog.render(graphics, width, height, mouseX, mouseY);
        }

        // 9. Interactive Tutorial Overlay
        TutorialOverlay.render(graphics, font, this, width, height, mouseX, mouseY);

        // 10. Global Toast Notifications (Top layer)
        BoardToast.render(graphics, font, width, height);
    }

    public boolean isAnyModalOpen() {
        return (welcomeDialog != null && welcomeDialog.isVisible())
            || (settingsDialog != null && settingsDialog.isVisible())
            || (globalBalanceDialog != null && globalBalanceDialog.isVisible())
            || (multiblockBOMDialog != null && multiblockBOMDialog.isVisible())
            || (guideDialog != null && guideDialog.isVisible())
            || (searchDialog != null && searchDialog.isVisible())
            || (machineConfigDialog != null && machineConfigDialog.isVisible())
            || (deletePageDialog != null && deletePageDialog.isVisible())
            || (tutorialExitDialog != null && tutorialExitDialog.isVisible())
            || (saveToTeamDialog != null && saveToTeamDialog.isVisible())
            || (exportToTeamDialog != null && exportToTeamDialog.isVisible())
            || (recentSavesDialog != null && recentSavesDialog.isVisible())
            || (frameEditDialog != null && frameEditDialog.isVisible())
            || (noteEditDialog != null && noteEditDialog.isVisible());
    }

    public record BackgroundLoadingTask(String title, int percent, boolean indeterminate, String statusText, String subtitle) {}

    private void renderCentralLoadingCard(GuiGraphics graphics, Font font, int screenWidth, int screenHeight) {
        if (isAnyModalOpen()) return;

        List<BackgroundLoadingTask> tasks = new ArrayList<>();

        if (RecipeSearchDialog.isCaching()) {
            var prog = RecipeSearchDialog.getCachingProgress();
            int pct = (prog != null && prog.totalPhases() > 0) ? (int) (prog.currentPhase() * 100.0 / prog.totalPhases()) : 0;
            String sub = (prog != null && prog.phaseKey() != null) ? Component.translatable(prog.phaseKey()).getString() : "";
            tasks.add(new BackgroundLoadingTask(
                Component.translatable("gui.gtcalcboard.status.task_recipes").getString(),
                pct,
                false,
                pct + "%",
                sub
            ));
        } else if (favoritesDockWidget != null && favoritesDockWidget.isEmiLoading()) {
            tasks.add(new BackgroundLoadingTask(
                Component.translatable("gui.gtcalcboard.status.task_recipes").getString(),
                30,
                true,
                Component.translatable("gui.gtcalcboard.status.in_progress").getString(),
                Component.translatable("gui.gtcalcboard.favorites_dock.loading_emi").getString()
            ));
        }

        if (MachineAddonCatalog.getInstance().isExhaustiveScanRunning()) {
            double prog = MachineAddonCatalog.getInstance().getExhaustiveProgress();
            int pct = (int) Math.round(prog * 100.0);
            tasks.add(new BackgroundLoadingTask(
                Component.translatable("gui.gtcalcboard.status.task_addons").getString(),
                pct,
                false,
                pct + "%",
                Component.translatable("gui.gtcalcboard.status.indexing_addons", pct).getString()
            ));
        }

        if (!com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.isInitialized()) {
            tasks.add(new BackgroundLoadingTask(
                Component.translatable("gui.gtcalcboard.status.task_multiblocks").getString(),
                50,
                true,
                Component.translatable("gui.gtcalcboard.status.in_progress").getString(),
                Component.translatable("gui.gtcalcboard.status.indexing_multiblocks").getString()
            ));
        }

        if (tasks.isEmpty()) return;

        int cardW = 340;
        int rowH = 34;
        int cardH = 26 + (tasks.size() * rowH) + 4;
        int cardX = (screenWidth - cardW) / 2;
        int cardY = screenHeight - cardH - 24;

        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 450);

        // Container Background & Border
        graphics.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xF0101520);
        graphics.renderOutline(cardX, cardY, cardW, cardH, 0xFF35445E);

        // Title Header
        graphics.fill(cardX, cardY, cardX + cardW, cardY + 20, 0xEE182232);
        graphics.fill(cardX, cardY + 19, cardX + cardW, cardY + 20, 0xFF2A364D);
        String headerTitle = "§e⏳ " + Component.translatable("gui.gtcalcboard.status.loading_header").getString();
        graphics.drawString(font, headerTitle, cardX + 8, cardY + 6, 0xFFFFFFFF, false);

        int curY = cardY + 24;
        for (BackgroundLoadingTask task : tasks) {
            // Line 1: Title (Left) & Status (Right)
            graphics.drawString(font, "§f" + task.title, cardX + 10, curY, 0xFFE0E0E0, false);
            String status = "§7" + task.statusText;
            int statusW = font.width(status);
            graphics.drawString(font, status, cardX + cardW - 10 - statusW, curY, 0xFFAAAAAA, false);

            // Line 2: Subtitle
            if (task.subtitle != null && !task.subtitle.isEmpty()) {
                String sub = font.plainSubstrByWidth("§8" + task.subtitle, cardW - 20);
                graphics.drawString(font, sub, cardX + 10, curY + 10, 0xFF888888, false);
            }

            // Line 3: Progress Bar
            int barX = cardX + 10;
            int barY = curY + 22;
            int barW = cardW - 20;
            int barH = 3;

            graphics.fill(barX, barY, barX + barW, barY + barH, 0xFF222733);
            graphics.renderOutline(barX, barY, barW, barH, 0xFF3D4659);

            if (task.indeterminate) {
                long time = System.currentTimeMillis() % 1500;
                float pos = (time / 1500.0f);
                int segW = 45;
                int segX = barX + (int) (pos * (barW - segW));
                graphics.fill(segX, barY, segX + segW, barY + barH, 0xFF4A90E2);
            } else {
                float fillRatio = Math.max(0.05f, Math.min(1.0f, task.percent / 100.0f));
                int fillW = (int) (barW * fillRatio);
                graphics.fill(barX, barY, barX + fillW, barY + barH, 0xFF4A90E2);
            }

            curY += rowH;
        }

        graphics.pose().popPose();
    }

    private void renderGridBackground(GuiGraphics graphics) {
        int dotSpacing = (int) (24 * zoom);
        if (dotSpacing < 10) return;

        int startX = (int) (panX % dotSpacing);
        int startY = (int) (panY % dotSpacing);

        Matrix4f pose = graphics.pose().last().pose();
        graphics.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        float a = 0x22 / 255.0f;
        float r = 1.0f, g = 1.0f, b = 1.0f;

        for (int x = startX; x < width; x += dotSpacing) {
            for (int y = startY; y < height; y += dotSpacing) {
                buffer.vertex(pose, x, y, 0.0f).color(r, g, b, a).endVertex();
                buffer.vertex(pose, x + 1, y, 0.0f).color(r, g, b, a).endVertex();
                buffer.vertex(pose, x + 1, y + 1, 0.0f).color(r, g, b, a).endVertex();
                buffer.vertex(pose, x, y + 1, 0.0f).color(r, g, b, a).endVertex();
            }
        }

        tesselator.end();
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    public NodeWidget findWidgetForNode(RecipeNode node) {
        for (NodeWidget w : nodeWidgets) {
            if (w.getNode() == node) return w;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (saveToTeamDialog != null && saveToTeamDialog.isVisible()) {
            return saveToTeamDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (exportToTeamDialog != null && exportToTeamDialog.isVisible()) {
            return exportToTeamDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (recentSavesDialog != null && recentSavesDialog.isVisible()) {
            return recentSavesDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (welcomeDialog.mouseClicked(this, width, height, mouseX, mouseY, button)) {
            return true;
        }
        if (settingsDialog != null && settingsDialog.isVisible()) {
            return settingsDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (TutorialOverlay.mouseClicked(this, width, height, mouseX, mouseY, button)) {
            return true;
        }
        if (deletePageDialog != null && deletePageDialog.isVisible()) {
            return deletePageDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (tutorialExitDialog != null && tutorialExitDialog.isVisible()) {
            return tutorialExitDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (globalBalanceDialog != null && globalBalanceDialog.isVisible()) {
            return globalBalanceDialog.mouseClicked(mouseX, mouseY, button, width, height);
        }
        if (multiblockBOMDialog != null && multiblockBOMDialog.isVisible()) {
            return multiblockBOMDialog.mouseClicked(mouseX, mouseY, button);
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
        if (frameEditDialog != null && frameEditDialog.isVisible()) {
            return frameEditDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (noteEditDialog != null && noteEditDialog.isVisible()) {
            return noteEditDialog.mouseClicked(mouseX, mouseY, button);
        }
        if (workspaceTabBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (pageTabBar.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (favoritesDockWidget.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (BoardManager.getInstance().isShowHotkeyHud() && hotkeyHudWidget.mouseClicked(mouseX, mouseY, button)) {
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
        if (favoritesDockWidget.mouseReleased(mouseX, mouseY, button)) {
            return true;
        }
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
        if (favoritesDockWidget.mouseDragged(mouseX, mouseY, button, dragX, dragY)) {
            return true;
        }
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
        if (recentSavesDialog != null && recentSavesDialog.isVisible()) {
            return recentSavesDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (globalBalanceDialog != null && globalBalanceDialog.isVisible()) {
            return globalBalanceDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (multiblockBOMDialog != null && multiblockBOMDialog.isVisible()) {
            return multiblockBOMDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            return machineConfigDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (guideDialog != null && guideDialog.isVisible()) {
            return guideDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.mouseScrolled(mouseX, mouseY, delta);
        }
        if (favoritesDockWidget.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
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
        if (saveToTeamDialog != null && saveToTeamDialog.isVisible()) {
            return saveToTeamDialog.charTyped(codePoint, modifiers);
        }
        if (globalBalanceDialog != null && globalBalanceDialog.isVisible()) {
            return globalBalanceDialog.charTyped(codePoint, modifiers);
        }
        if (multiblockBOMDialog != null && multiblockBOMDialog.isVisible()) {
            return multiblockBOMDialog.charTyped(codePoint, modifiers);
        }
        if (pageTabBar.charTyped(codePoint, modifiers)) {
            return true;
        }
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            return machineConfigDialog.charTyped(codePoint, modifiers);
        }
        if (searchDialog != null && searchDialog.isVisible()) {
            return searchDialog.charTyped(codePoint, modifiers);
        }
        if (frameEditDialog != null && frameEditDialog.isVisible()) {
            return frameEditDialog.charTyped(codePoint, modifiers);
        }
        if (noteEditDialog != null && noteEditDialog.isVisible()) {
            return noteEditDialog.charTyped(codePoint, modifiers);
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
        if (saveToTeamDialog != null && saveToTeamDialog.isVisible()) {
            return saveToTeamDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (settingsDialog != null && settingsDialog.isVisible()) {
            return settingsDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (exportToTeamDialog != null && exportToTeamDialog.isVisible()) {
            return exportToTeamDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (recentSavesDialog != null && recentSavesDialog.isVisible()) {
            return recentSavesDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (frameEditDialog != null && frameEditDialog.isVisible()) {
            return frameEditDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (noteEditDialog != null && noteEditDialog.isVisible()) {
            return noteEditDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (machineConfigDialog != null && machineConfigDialog.isVisible()) {
            return machineConfigDialog.keyPressed(keyCode, scanCode, modifiers);
        }
        if (pageTabBar.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (BoardHotkeyHandler.handleKeyPressed(this, keyCode, scanCode, modifiers, lastMouseX, lastMouseY)) {
            return true;
        }
        if (this.minecraft != null && this.minecraft.options.keyInventory.matches(keyCode, scanCode)) {
            return false;
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
    public FrameEditDialog getFrameEditDialog() { return frameEditDialog; }

    public void openRecipeSwitchDialog(RecipeNode node) {
        if (!ensureEditPermission()) return;
        if (searchDialog != null) {
            searchDialog.openForSwitch(node);
        }
    }

    public void switchNodeRecipe(RecipeNode targetNode, RecipeNode newRecipeTemplate) {
        if (!ensureEditPermission()) return;
        if (targetNode == null || newRecipeTemplate == null) return;

        var cmd = getGraph().switchNodeRecipe(targetNode, newRecipeTemplate);
        if (cmd != null) {
            recordCommand(cmd);
            markSummaryDirty();
            for (NodeWidget w : nodeWidgets) {
                if (w.getNode().getId().equals(targetNode.getId())) {
                    w.invalidateCache();
                }
            }
            BoardToast.show(Component.literal("§e🔄 ").append(Component.translatable("message.gtcalcboard.recipe_switched", targetNode.getName())));
        }
    }

    public void openMachineConfigDialog(RecipeNode node) {
        openMachineConfigDialog(node, null);
    }

    public void openMachineConfigDialog(RecipeNode node, com.gtceu.calcboard.api.catalog.AddonCategory initialCategory) {
        if (machineConfigDialog == null) {
            machineConfigDialog = new MachineConfigDialog(this);
        }
        TutorialManager.getInstance().onMachineConfigOpened();
        machineConfigDialog.open(node, initialCategory);
    }

    public void openFrameEditDialog(com.gtceu.calcboard.api.model.CanvasGroupFrame frame) {
        if (frameEditDialog == null) {
            frameEditDialog = new FrameEditDialog(this);
        }
        frameEditDialog.open(frame);
    }

    public void createFrameFromSelection() {
        if (!ensureEditPermission()) return;
        FlowGraph graph = getGraph();
        Set<String> selectedNodeIds = getSelectedNodeIds();
        Set<String> selectedNoteIds = getSelectedNoteIds();

        List<RecipeNode> selectedNodes = new ArrayList<>();
        if (selectedNodeIds != null && !selectedNodeIds.isEmpty()) {
            for (RecipeNode n : graph.getNodes()) {
                if (selectedNodeIds.contains(n.getId())) {
                    selectedNodes.add(n);
                }
            }
        }
        List<com.gtceu.calcboard.api.model.CanvasStickyNote> selectedNotes = new ArrayList<>();
        if (selectedNoteIds != null && !selectedNoteIds.isEmpty()) {
            for (com.gtceu.calcboard.api.model.CanvasStickyNote note : graph.getStickyNotes()) {
                if (selectedNoteIds.contains(note.getId())) {
                    selectedNotes.add(note);
                }
            }
        }

        String defaultTitle = Component.translatable("gui.gtcalcboard.default_frame_name").getString();
        com.gtceu.calcboard.api.model.CanvasGroupFrame frame;
        if (!selectedNodes.isEmpty() || !selectedNotes.isEmpty()) {
            frame = com.gtceu.calcboard.api.model.CanvasGroupFrame.createFromElements(defaultTitle, selectedNodes, selectedNotes, com.gtceu.calcboard.api.model.CanvasGroupFrame.COLOR_BLUE);
        } else {
            double cx = toCanvasX(width / 2.0) - 100;
            double cy = toCanvasY(height / 2.0) - 60;
            frame = new com.gtceu.calcboard.api.model.CanvasGroupFrame(UUID.randomUUID().toString(), defaultTitle, com.gtceu.calcboard.api.model.CanvasGroupFrame.COLOR_BLUE, cx, cy, 200, 120);
        }
        graph.addFrame(frame);
        clearSelection();
        rebuildWidgets();
        markSummaryDirty();
        openFrameEditDialog(frame);
        TutorialManager.getInstance().onGroupFramed();
        BoardToast.show(Component.literal("§b").append(Component.translatable("message.gtcalcboard.frame_created")));
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.1F));
    }

    public void createFrameAt(double canvasX, double canvasY) {
        if (!ensureEditPermission()) return;
        if (!getSelectedNodeIds().isEmpty() || !getSelectedNoteIds().isEmpty()) {
            createFrameFromSelection();
            return;
        }

        String defaultTitle = Component.translatable("gui.gtcalcboard.default_frame_name").getString();
        com.gtceu.calcboard.api.model.CanvasGroupFrame frame = new com.gtceu.calcboard.api.model.CanvasGroupFrame(UUID.randomUUID().toString(), defaultTitle, com.gtceu.calcboard.api.model.CanvasGroupFrame.COLOR_BLUE, canvasX - 100, canvasY - 60, 200, 120);
        getGraph().addFrame(frame);
        clearSelection();
        rebuildWidgets();
        markSummaryDirty();
        openFrameEditDialog(frame);
        TutorialManager.getInstance().onGroupFramed();
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.1F));
    }

    public void createNoteAt(double canvasX, double canvasY) {
        if (!ensureEditPermission()) return;
        FlowGraph graph = getGraph();
        String defaultTitle = Component.translatable("gui.gtcalcboard.default_note_name").getString();
        com.gtceu.calcboard.api.model.CanvasStickyNote note = com.gtceu.calcboard.api.model.CanvasStickyNote.create(
            defaultTitle,
            "",
            com.gtceu.calcboard.api.model.CanvasStickyNote.COLOR_AMBER,
            canvasX - 80, canvasY - 50
        );
        graph.addStickyNote(note);
        clearSelection();
        rebuildWidgets();
        markSummaryDirty();
        openNoteEditDialog(note);
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.1F));
    }

    public void openNoteEditDialog(com.gtceu.calcboard.api.model.CanvasStickyNote note) {
        if (noteEditDialog == null) {
            noteEditDialog = new NoteEditDialog(this);
        }
        noteEditDialog.open(note);
    }

    public NoteEditDialog getNoteEditDialog() {
        return noteEditDialog;
    }

    public void addRerouteNodeAt(double canvasX, double canvasY) {
        if (!ensureEditPermission()) return;
        RecipeNode reroute = RecipeNode.createReroute(canvasX - 16, canvasY - 16);
        getGraph().addNode(reroute);
        recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.AddNodesCommand(java.util.List.of(reroute), java.util.Collections.emptyList(), "Add Junction Node"));
        rebuildWidgets();
        markSummaryDirty();
        Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
    }

    public void groupNodesIntoModule(Set<String> targetNodeIds, String moduleName) {
        groupNodesIntoModule(targetNodeIds, moduleName, null);
    }

    public void groupNodesIntoModule(Set<String> targetNodeIds, String moduleName, com.gtceu.calcboard.api.model.CanvasGroupFrame primaryFrame) {
        if (!ensureEditPermission()) return;
        FlowGraph graph = getGraph();
        if (targetNodeIds == null || targetNodeIds.isEmpty()) return;

        List<RecipeNode> origNodes = new ArrayList<>(graph.getNodes());
        List<FlowGraph.ConnectionEdge> origEdges = new ArrayList<>(graph.getConnections());

        String name = (moduleName != null && !moduleName.trim().isEmpty()) ? moduleName.trim() : Component.translatable("gui.gtcalcboard.default_compound_name").getString();
        RecipeNode moduleNode = graph.groupIntoModule(targetNodeIds, name, primaryFrame);

        if (moduleNode != null) {
            List<RecipeNode> groupedNodes = new ArrayList<>();
            for (RecipeNode n : origNodes) {
                if (!graph.getNodes().contains(n)) {
                    groupedNodes.add(n);
                }
            }
            List<FlowGraph.ConnectionEdge> rewires = new ArrayList<>(graph.getConnections());
            recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.GroupModuleCommand(groupedNodes, moduleNode, origEdges, rewires));

            clearSelection();
            rebuildWidgets();
            markSummaryDirty();
            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onModuleGrouped();

            BoardToast.show(Component.literal("§d📦 ").append(Component.translatable("message.gtcalcboard.group_success", String.valueOf(moduleNode.getContainedMachineCount()))));
            Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1.2F));
        }
    }

    public void collapseFrameIntoModule(com.gtceu.calcboard.api.model.CanvasGroupFrame frame) {
        if (!ensureEditPermission()) return;
        FlowGraph graph = getGraph();
        if (frame == null) return;

        Set<String> nodeIds = new HashSet<>();
        for (RecipeNode n : frame.getEnclosedNodes(graph)) {
            nodeIds.add(n.getId());
        }
        if (nodeIds.isEmpty()) {
            nodeIds.addAll(frame.getContainedNodeIds());
        }
        if (nodeIds.isEmpty()) return;

        groupNodesIntoModule(nodeIds, frame.getTitle(), frame);
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
        com.gtceu.calcboard.client.team.ClientWorkspaceState state = com.gtceu.calcboard.client.team.ClientWorkspaceState.getInstance();
        if (state.isCollaborationEnabled() && state.isTeamMode()) {
            com.gtceu.calcboard.network.NetworkHandler.sendToServer(
                new com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket(state.getCurrentTeamId(), state.getActiveTeamPageId(), false)
            );
        }
        if (state.isTeamMode()) {
            state.autoCommitAndRelease(this, state.getActiveTeamPageId());
        }
        lastPanX = this.panX;
        lastPanY = this.panY;
        lastZoom = this.zoom;
        var active = BoardManager.getInstance().getActivePage();
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
        com.gtceu.calcboard.GregTechCalcBoard.LOGGER.info("[GTCalcBoard] [UI] BoardScreen closed. State saved.");
        super.onClose();
    }

    public FavoritesDockWidget getFavoritesDockWidget() {
        return favoritesDockWidget;
    }

    @Override
    public boolean isPauseScreen() {
        return BoardManager.getInstance().isPauseGameInSingleplayer();
    }
}



