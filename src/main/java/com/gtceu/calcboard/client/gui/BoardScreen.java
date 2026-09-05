package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.GregTechCalcBoard;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.type.BoardGuiScale;
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
import com.gtceu.calcboard.client.gui.model.PortRef;
import com.gtceu.calcboard.client.gui.render.BoardCanvasRenderer;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.render.WireSpatialIndex;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.tutorial.TutorialOverlay;
import com.gtceu.calcboard.client.gui.tutorial.WelcomeTutorialDialog;
import com.gtceu.calcboard.client.gui.util.BoardViewportTransform;
import com.gtceu.calcboard.client.gui.widget.*;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import com.gtceu.calcboard.integration.emi.BoardMenu;
import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.c2s.C2SAcquireLockPacket;
import com.gtceu.calcboard.network.packet.c2s.C2SPingPresencePacket;
import com.gtceu.calcboard.network.packet.c2s.C2SRequestWorkspacePacket;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.lwjgl.glfw.GLFW;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import org.lwjgl.opengl.GL11;

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
        if (mc.screen.getClass().getName().contains("RecipeScreen") || mc.screen.getClass().getName().contains("Emi")) {
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

    private final BoardDialogManager dialogManager = new BoardDialogManager(this);
    private final BoardCanvasRenderer canvasRenderer = new BoardCanvasRenderer();
    private final BoardActionHandler actionHandler = new BoardActionHandler(this);

    private BalanceSummary cachedSummary = null;
    private boolean summaryDirty = true;
    private double lastMouseX, lastMouseY;
    private long lastEditTimestamp = 0;
    private int presencePingTicks = 0;
    private double wasdVelX = 0.0;
    private double wasdVelY = 0.0;
    private long lastFrameTimeNano = 0;

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

        initNetworkPresence();
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

    public void clearForeignWidgets() {
        this.clearWidgets();
    }

    private void initNetworkPresence() {
        if (minecraft == null || minecraft.getConnection() == null) return;
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        UUID teamId = state.getCurrentTeamId();
        String pageId = state.getActiveTeamPageId();
        boolean isTeamMode = state.isTeamMode();
        NetworkHandler.sendToServer(new C2SPingPresencePacket(teamId, pageId, isTeamMode));
        NetworkHandler.sendToServer(new C2SRequestWorkspacePacket(teamId, pageId != null ? pageId : "page_main"));
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
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (state.isTeamMode()) {
            state.markPageDirty(state.getActiveTeamPageId());
            this.lastEditTimestamp = System.currentTimeMillis();
        }
    }

    public boolean ensureEditPermission() {
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (!state.isTeamMode()) return true;

        String activePageId = state.getActiveTeamPageId();
        state.markPageDirty(activePageId);
        this.lastEditTimestamp = System.currentTimeMillis();

        if (state.doesHoldLock(activePageId)) return true;

        TeamWorkspacePage page = state.getRemotePage(activePageId);
        if (page != null && page.isLocked() && !state.doesHoldLock(activePageId)) {
            String lockHolder = page.getLockHolderName() != null && !page.getLockHolderName().isEmpty()
                    ? page.getLockHolderName() : state.resolvePlayerName(page.getLockHolderUUID());
            BoardToast.show(Component.literal("§c✕ ").append(Component.translatable("gui.gtcalcboard.lock.locked_by", lockHolder)));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F));
            return false;
        }

        UUID teamId = state.getCurrentTeamId();
        NetworkHandler.sendToServer(new C2SAcquireLockPacket(teamId, activePageId));
        state.setLockHeld(activePageId, true);
        return true;
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

        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (state.isCollaborationEnabled() && state.isTeamMode()) {
            presencePingTicks++;
            if (presencePingTicks >= 40) {
                presencePingTicks = 0;
                NetworkHandler.sendToServer(new C2SPingPresencePacket(state.getCurrentTeamId(), state.getActiveTeamPageId(), true));
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        updateSmoothPan();
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

        updateGraphSummaryIfDirty();

        graphics.pose().pushPose();
        viewportTransform.applyPose(graphics.pose());

        renderBackground(graphics);
        BoardHudRenderer.renderGridBackground(graphics, width, height, panX, panY, zoom);
        BoardHudRenderer.renderEmptyCanvasWatermark(graphics, font, width, height, getGraph().getNodes().size());

        canvasRenderer.renderCanvasScene(graphics, this, getGraph(), nodeWidgets, wireRenderer, canvasHandler, panX, panY, zoom, width, height, localMouseX, localMouseY, partialTicks);

        renderScreenWidgets(graphics, localMouseX, localMouseY, partialTicks);
        clearForeignWidgets();
        renderTopOverlays(graphics, localMouseX, localMouseY, partialTicks);

        graphics.pose().popPose();
    }

    private void updateSmoothPan() {
        long now = System.nanoTime();
        if (lastFrameTimeNano == 0) {
            lastFrameTimeNano = now;
            return;
        }
        double dt = (now - lastFrameTimeNano) / 1_000_000_000.0;
        lastFrameTimeNano = now;
        if (dt <= 0.0 || dt > 0.1) {
            dt = 0.016;
        }

        if (isSmoothPanBlocked()) {
            wasdVelX = 0.0;
            wasdVelY = 0.0;
            return;
        }

        long window = Minecraft.getInstance().getWindow().getWindow();
        boolean isW = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_UP);
        boolean isS = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_DOWN);
        boolean isA = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT);
        boolean isD = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT);

        double dirX = 0.0;
        double dirY = 0.0;
        if (isA) dirX += 1.0;
        if (isD) dirX -= 1.0;
        if (isW) dirY += 1.0;
        if (isS) dirY -= 1.0;

        if (dirX != 0.0 && dirY != 0.0) {
            double norm = 1.0 / Math.sqrt(2.0);
            dirX *= norm;
            dirY *= norm;
        }

        double speed = (net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 850.0 : 420.0) / Math.max(0.2, zoom);
        applyVelocityDamping(dirX, dirY, speed, dt);
    }

    private boolean isSmoothPanBlocked() {
        if (net.minecraft.client.gui.screens.Screen.hasControlDown() || net.minecraft.client.gui.screens.Screen.hasAltDown()) return true;
        if (isAnyModalOpen()) return true;
        if (pageBrowserDrawer != null && pageBrowserDrawer.isOpen()) return true;
        if (dialogManager.getSearchDialog() != null && dialogManager.getSearchDialog().isVisible()) return true;
        for (NodeWidget nw : nodeWidgets) {
            if (nw.isAnyEditorActive()) return true;
        }
        return false;
    }

    private void applyVelocityDamping(double dirX, double dirY, double speed, double dt) {
        if (dirX != 0.0 || dirY != 0.0) {
            double targetVelX = dirX * speed;
            double targetVelY = dirY * speed;
            double blend = Math.min(1.0, dt * 22.0);
            wasdVelX += (targetVelX - wasdVelX) * blend;
            wasdVelY += (targetVelY - wasdVelY) * blend;
        } else {
            wasdVelX *= Math.max(0.0, 1.0 - dt * 20.0);
            wasdVelY *= Math.max(0.0, 1.0 - dt * 20.0);
            if (Math.abs(wasdVelX) < 0.2) wasdVelX = 0.0;
            if (Math.abs(wasdVelY) < 0.2) wasdVelY = 0.0;
        }

        if (wasdVelX != 0.0 || wasdVelY != 0.0) {
            setPanX(panX + wasdVelX * dt);
            setPanY(panY + wasdVelY * dt);
        }
    }

    private void updateGraphSummaryIfDirty() {
        if (summaryDirty || cachedSummary == null) {
            getGraph().cleanupInvalidConnections();
            cachedSummary = FlowGraphSolver.computeSummary(getGraph());
            summaryDirty = false;
        }
    }

    private void renderScreenWidgets(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        workspaceTabBar.render(graphics, mouseX, mouseY, partialTicks);
        pageTabBar.render(graphics, mouseX, mouseY, partialTicks);
        toolbarWidget.render(graphics, mouseX, mouseY);
        leftActivityBar.render(graphics, mouseX, mouseY, partialTicks);
        if (BoardManager.getInstance().isShowHotkeyHud()) {
            hotkeyHudWidget.render(graphics, mouseX, mouseY, partialTicks);
        }
        favoritesDockWidget.render(graphics, mouseX, mouseY, partialTicks);
        pageBrowserDrawer.render(graphics, mouseX, mouseY, partialTicks);
        nodeInspectorPanel.render(graphics, mouseX, mouseY, partialTicks);
        statusBar.render(graphics, mouseX, mouseY, partialTicks);

        if (summaryDirty || cachedSummary == null) {
            cachedSummary = FlowGraphSolver.computeSummary(getGraph());
            summaryDirty = false;
        }
        summaryOverlay.render(graphics, width, height, cachedSummary, mouseX, mouseY);
        BoardHudRenderer.renderCentralLoadingCard(graphics, font, width, height, isAnyModalOpen(), favoritesDockWidget);

        if (!isAnyModalOpen() && !pageBrowserDrawer.isOpen()) {
            graphics.flush();
            com.mojang.blaze3d.systems.RenderSystem.clear(org.lwjgl.opengl.GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
            BoardTooltipRenderer.renderTooltips(this, graphics, font, mouseX, mouseY);
            favoritesDockWidget.renderTooltips(graphics, font, mouseX, mouseY);
            workspaceTabBar.renderTooltips(graphics, font, mouseX, mouseY);
            leftActivityBar.renderTooltips(graphics, font, mouseX, mouseY);
        }
    }

    private void renderTopOverlays(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (isAnyModalOpen()) {
            graphics.flush();
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            RenderSystem.disableDepthTest();
            dialogManager.renderModals(graphics, width, height, mouseX, mouseY, partialTicks);
        }
        if (canvasHandler != null && canvasHandler.getContextMenuManager() != null) {
            canvasHandler.getContextMenuManager().render(graphics, font, mouseX, mouseY);
        }
        TutorialOverlay.render(graphics, font, this, width, height, mouseX, mouseY);
        BoardToast.render(graphics, font, width, height);
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
        double vx = viewportTransform.toVirtualX(mouseX);
        double vy = viewportTransform.toVirtualY(mouseY);
        if (dialogManager.handleMouseClicked(vx, vy, button, width, height)) return true;
        if (TutorialOverlay.mouseClicked(this, width, height, vx, vy, button)) return true;
        if (pageBrowserDrawer != null && pageBrowserDrawer.isOpen() && pageBrowserDrawer.mouseClicked(vx, vy, button)) return true;
        if (leftActivityBar.mouseClicked(vx, vy, button)) return true;
        if (workspaceTabBar.mouseClicked(vx, vy, button)) return true;
        if (pageTabBar.mouseClicked(vx, vy, button)) return true;
        if (favoritesDockWidget.mouseClicked(vx, vy, button)) return true;
        if (BoardManager.getInstance().isShowHotkeyHud() && hotkeyHudWidget.mouseClicked(vx, vy, button)) return true;
        if (summaryOverlay.mouseClicked(vx, vy, button, width, height)) return true;
        if (nodeInspectorPanel.mouseClicked(vx, vy, button)) return true;
        if (statusBar.mouseClicked(vx, vy, button)) return true;
        if (toolbarWidget.mouseClicked(vx, vy, button)) return true;
        if (canvasHandler.mouseClicked(vx, vy, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        double vx = viewportTransform.toVirtualX(mouseX);
        double vy = viewportTransform.toVirtualY(mouseY);
        if (dialogManager.handleMouseReleased(vx, vy, button)) return true;
        if (pageBrowserDrawer != null && pageBrowserDrawer.isOpen() && pageBrowserDrawer.mouseReleased(vx, vy, button)) return true;
        if (favoritesDockWidget.mouseReleased(vx, vy, button)) return true;
        if (pageTabBar.mouseReleased(vx, vy, button)) return true;
        if (toolbarWidget.mouseReleased(vx, vy, button)) return true;
        if (canvasHandler.mouseReleased(vx, vy, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double vx = viewportTransform.toVirtualX(mouseX);
        double vy = viewportTransform.toVirtualY(mouseY);
        double vdx = viewportTransform.toVirtualX(dragX);
        double vdy = viewportTransform.toVirtualY(dragY);
        if (dialogManager.handleMouseDragged(vx, vy, button, vdx, vdy, width, height)) return true;
        if (pageBrowserDrawer != null && pageBrowserDrawer.isOpen() && pageBrowserDrawer.mouseDragged(vx, vy, button, vdx, vdy)) return true;
        if (favoritesDockWidget.mouseDragged(vx, vy, button, vdx, vdy)) return true;
        if (pageTabBar.mouseDragged(vx, vy, button, vdx, vdy)) return true;
        if (toolbarWidget.mouseDragged(vx, vy, button, vdx, vdy)) return true;
        if (canvasHandler.mouseDragged(vx, vy, button, vdx, vdy)) {
            if (wireRenderer != null) wireRenderer.markDirty();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        double vx = viewportTransform.toVirtualX(mouseX);
        double vy = viewportTransform.toVirtualY(mouseY);
        if (dialogManager.handleMouseScrolled(vx, vy, delta)) return true;
        if (pageBrowserDrawer != null && pageBrowserDrawer.isOpen() && pageBrowserDrawer.mouseScrolled(vx, vy, delta)) return true;
        if (favoritesDockWidget.mouseScrolled(vx, vy, delta)) return true;
        if (pageTabBar.mouseScrolled(vx, vy, delta)) return true;
        if (summaryOverlay.mouseScrolled(vx, vy, delta, width, height)) return true;
        if (toolbarWidget.mouseScrolled(vx, vy, delta)) return true;
        if (BoardManager.getInstance().isShowHotkeyHud() && hotkeyHudWidget.mouseScrolled(vx, vy, delta)) return true;

        double canvasMouseX = toCanvasX(vx);
        double canvasMouseY = toCanvasY(vy);
        for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
            if (nodeWidgets.get(i).mouseScrolled(canvasMouseX, canvasMouseY, delta)) return true;
        }
        if (canvasHandler.mouseScrolled(vx, vy, delta)) return true;
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

    public double toCanvasX(double screenX) { return (screenX - panX) / zoom; }
    public double toCanvasY(double screenY) { return (screenY - panY) / zoom; }
    public double toScreenX(double canvasX) { return canvasX * zoom + panX; }
    public double toScreenY(double canvasY) { return canvasY * zoom + panY; }

    public double getLastMouseX() { return lastMouseX; }
    public double getLastMouseY() { return lastMouseY; }

    public static double[] getNextNodeCenterPosition() {
        BoardViewportTransform transform = getCurrentTransform();
        if (transform != null && transform.isScaled()) {
            return getNextNodeCenterPosition(transform.getVirtualWidth(), transform.getVirtualHeight());
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.screen instanceof BoardScreen bs) {
            return getNextNodeCenterPosition(bs.width, bs.height);
        }
        if (mc != null && mc.getWindow() != null) {
            BoardGuiScale pref = BoardManager.getInstance().getBoardGuiScale();
            int gameGuiScale = Math.max(1, (int) Math.round(mc.getWindow().getGuiScale()));
            int eff = pref.resolveEffectiveScale(gameGuiScale, mc.getWindow().getWidth(), mc.getWindow().getHeight());
            int sw = Math.max(320, (int) Math.floor((double) mc.getWindow().getWidth() / eff));
            int sh = Math.max(240, (int) Math.floor((double) mc.getWindow().getHeight() / eff));
            return getNextNodeCenterPosition(sw, sh);
        }
        return getNextNodeCenterPosition(800, 600);
    }

    public static double[] getNextNodeCenterPosition(int screenW, int screenH) {
        double canvasCenterX = (screenW / 2.0 - lastPanX) / lastZoom - 100.0;
        double canvasCenterY = (screenH / 2.0 - lastPanY) / lastZoom - 40.0;
        FlowGraph graph = BoardManager.getInstance().getActiveGraph();
        double candX = canvasCenterX, candY = canvasCenterY;
        while (isNodeAt(graph, candX, candY)) { candX += 24.0; candY += 24.0; }
        return new double[]{candX, candY};
    }

    public double[] getScreenCenterCanvasPosition() {
        double canvasCenterX = (this.width / 2.0 - this.panX) / this.zoom - 100.0;
        double canvasCenterY = (this.height / 2.0 - this.panY) / this.zoom - 40.0;
        FlowGraph graph = getGraph();
        double candX = canvasCenterX, candY = canvasCenterY;
        while (isNodeAt(graph, candX, candY)) { candX += 24.0; candY += 24.0; }
        return new double[]{candX, candY};
    }

    private static boolean isNodeAt(FlowGraph graph, double x, double y) {
        if (graph == null) return false;
        for (RecipeNode n : graph.getNodes()) {
            if (Math.abs(n.getPosX() - x) < 20.0 && Math.abs(n.getPosY() - y) < 20.0) return true;
        }
        return false;
    }

    public int getDynamicLeftMargin() {
        int maxRight = LeftActivityBarWidget.BAR_WIDTH + 6;
        for (var child : this.children()) {
            if (child instanceof AbstractWidget widget && !(widget instanceof EditBox)) {
                if (widget.visible && widget.getY() < 60 && widget.getX() >= 0 && widget.getX() < this.width / 3) {
                    maxRight = Math.max(maxRight, widget.getX() + widget.getWidth() + 6);
                }
            }
        }
        return maxRight;
    }

    public int getPageTabY() { return ClientWorkspaceState.getInstance().isCollaborationEnabled() ? 22 : 2; }
    public int getToolbarY() { return ClientWorkspaceState.getInstance().isCollaborationEnabled() ? 42 : 22; }
    public int getHeaderBottomY() { return ClientWorkspaceState.getInstance().isCollaborationEnabled() ? 64 : 44; }

    public int getFavoritesDockY() {
        int maxBottom = getHeaderBottomY() + 6;
        for (var child : this.children()) {
            if (child instanceof AbstractWidget widget && !(widget instanceof EditBox)) {
                if (widget.visible && widget.getX() < 160 && widget.getY() < 120) {
                    maxBottom = Math.max(maxBottom, widget.getY() + widget.getHeight());
                }
            }
        }
        return maxBottom;
    }

    public void recordCommand(BoardCommand cmd) {
        BoardPage page = BoardManager.getInstance().getActivePage();
        if (page != null) page.getHistoryManager().record(cmd);
    }

    public FlowGraph.ConnectionEdge findHoveredWire(double canvasMouseX, double canvasMouseY, double maxDist) {
        return wireRenderer.findHoveredWire(canvasMouseX, canvasMouseY, maxDist);
    }

    public WireSpatialIndex getWireSpatialIndex() { return wireRenderer.getWireSpatialIndex(); }
    public CanvasWireRenderer getWireRenderer() { return wireRenderer; }
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
    public void openNodeInspector(NodeWidget widget) { nodeInspectorPanel.setTargetWidget(widget); }
    public NodeInspectorPanel getNodeInspectorPanel() { return nodeInspectorPanel; }
    public AdaptiveStatusBar getStatusBar() { return statusBar; }
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
    public CanvasInteractionHandler getCanvasHandler() { return canvasHandler; }
    public WorkspaceTabBarWidget getWorkspaceTabBar() { return workspaceTabBar; }
    public PageTabBarWidget getPageTabBar() { return pageTabBar; }
    public ToolbarWidget getToolbarWidget() { return toolbarWidget; }
    public SummaryOverlay getSummaryOverlay() { return summaryOverlay; }
    public HotkeyHudWidget getHotkeyHudWidget() { return hotkeyHudWidget; }
    public FavoritesDockWidget getFavoritesDockWidget() { return favoritesDockWidget; }
    public PageBrowserDrawer getPageBrowserDrawer() { return pageBrowserDrawer; }
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
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        if (state.isCollaborationEnabled() && state.isTeamMode()) {
            NetworkHandler.sendToServer(new C2SPingPresencePacket(state.getCurrentTeamId(), state.getActiveTeamPageId(), false));
        }
        if (state.isTeamMode()) {
            state.autoCommitAndRelease(this, state.getActiveTeamPageId());
        }
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
