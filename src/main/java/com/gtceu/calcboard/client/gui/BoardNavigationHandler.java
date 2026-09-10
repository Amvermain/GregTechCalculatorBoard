package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphModuleHandler;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.BoardGuiScale;
import com.gtceu.calcboard.client.gui.util.BoardViewportTransform;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

/**
 * Coordinates viewport navigation, WASD smooth panning, coordinate transforms,
 * and page transitions for BoardScreen.
 */
public class BoardNavigationHandler {
    private final BoardScreen screen;
    private double wasdVelX = 0.0;
    private double wasdVelY = 0.0;
    private long lastFrameTimeNano = 0;

    public BoardNavigationHandler(BoardScreen screen) {
        this.screen = screen;
    }

    public void updateSmoothPan() {
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

        double speed = (Screen.hasShiftDown() ? 850.0 : 420.0) / Math.max(0.2, screen.getZoom());
        applyVelocityDamping(dirX, dirY, speed, dt);
    }

    public boolean isSmoothPanBlocked() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getWindow() != null && (Screen.hasControlDown() || Screen.hasAltDown())) {
            return true;
        }
        if (screen.isAnyModalOpen()) return true;
        if (screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen()) return true;
        if (screen.getPageTabBar() != null && screen.getPageTabBar().isEditing()) return true;
        if (screen.getDialogManager() != null && screen.getDialogManager().getSearchDialog() != null && screen.getDialogManager().getSearchDialog().isVisible()) return true;
        if (RecipeViewerRegistry.isAnySearchFocused()) return true;
        if (isScreenTextTypingActive()) return true;
        if (screen.getNodeWidgets() != null) {
            for (var nw : screen.getNodeWidgets()) {
                if (nw.isAnyEditorActive()) return true;
            }
        }
        return false;
    }

    private boolean isScreenTextTypingActive() {
        return screen.getFocused() instanceof EditBox editBox && editBox.canConsumeInput();
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
            screen.setPanX(screen.getPanX() + wasdVelX * dt);
            screen.setPanY(screen.getPanY() + wasdVelY * dt);
        }
    }

    public double toCanvasX(double screenX) { return (screenX - screen.getPanX()) / screen.getZoom(); }
    public double toCanvasY(double screenY) { return (screenY - screen.getPanY()) / screen.getZoom(); }
    public double toScreenX(double canvasX) { return canvasX * screen.getZoom() + screen.getPanX(); }
    public double toScreenY(double canvasY) { return canvasY * screen.getZoom() + screen.getPanY(); }

    public static double[] getNextNodeCenterPosition() {
        BoardViewportTransform transform = BoardScreen.getCurrentTransform();
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
        double canvasCenterX = (screenW / 2.0 - BoardScreen.lastPanX) / BoardScreen.lastZoom - 100.0;
        double canvasCenterY = (screenH / 2.0 - BoardScreen.lastPanY) / BoardScreen.lastZoom - 40.0;
        FlowGraph graph = BoardManager.getInstance().getActiveGraph();
        double candX = canvasCenterX, candY = canvasCenterY;
        while (isNodeAt(graph, candX, candY)) { candX += 24.0; candY += 24.0; }
        return new double[]{candX, candY};
    }

    public double[] getScreenCenterCanvasPosition() {
        double canvasCenterX = (screen.width / 2.0 - screen.getPanX()) / screen.getZoom() - 100.0;
        double canvasCenterY = (screen.height / 2.0 - screen.getPanY()) / screen.getZoom() - 40.0;
        FlowGraph graph = screen.getGraph();
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

    public void openModuleSubPage(RecipeNode moduleNode) {
        if (moduleNode == null || !moduleNode.isModule()) return;
        String subPageId = moduleNode.getSubPageId();
        if (subPageId == null || subPageId.isEmpty()) return;

        BoardPage current = BoardManager.getInstance().getActivePage();
        if (current != null) {
            current.setPanX(screen.getPanX());
            current.setPanY(screen.getPanY());
            current.setZoom(screen.getZoom());
        }

        if (BoardManager.getInstance().openPage(subPageId)) {
            BoardPage sub = BoardManager.getInstance().getActivePage();
            if (sub != null) {
                screen.setPanX(sub.getPanX());
                screen.setPanY(sub.getPanY());
                screen.setZoom(sub.getZoom());
            }
            screen.rebuildWidgets();
            screen.markSummaryDirty();
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1F)
            );
        }
    }

    public void returnToParentPage() {
        BoardPage current = BoardManager.getInstance().getActivePage();
        if (current == null || !current.isModuleSubPage()) return;

        current.setPanX(screen.getPanX());
        current.setPanY(screen.getPanY());
        current.setZoom(screen.getZoom());

        String parentPageId = current.getParentPageId();
        String parentModuleNodeId = current.getParentModuleNodeId();

        if (parentPageId == null || parentPageId.isEmpty()) return;
        if (!BoardManager.getInstance().openPage(parentPageId)) return;

        BoardPage parentPage = BoardManager.getInstance().getActivePage();
        if (parentPage != null) {
            screen.setPanX(parentPage.getPanX());
            screen.setPanY(parentPage.getPanY());
            screen.setZoom(parentPage.getZoom());
            RecipeNode parentNode = parentPage.getGraph().findNodeById(parentModuleNodeId);
            if (parentNode != null) {
                FlowGraphModuleHandler.syncModulePortsFromSubPage(parentNode, current);
                screen.setPanX((screen.width / 2.0) - (parentNode.getPosX() * screen.getZoom()));
                screen.setPanY((screen.height / 2.0) - (parentNode.getPosY() * screen.getZoom()));
            }
        }
        screen.rebuildWidgets();
        screen.markSummaryDirty();
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 0.9F)
        );
    }
}
