package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.client.gui.tutorial.TutorialOverlay;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;

/**
 * Handles cascading mouse event dispatching for BoardScreen widgets, dialogs, and canvas interactions.
 */
public class BoardInputRouter {
    private final BoardScreen screen;

    public BoardInputRouter(BoardScreen screen) {
        this.screen = screen;
    }

    public boolean handleMouseClicked(double mouseX, double mouseY, int button) {
        double vx = screen.getViewportTransform().toVirtualX(mouseX);
        double vy = screen.getViewportTransform().toVirtualY(mouseY);
        if (screen.getDialogManager().handleMouseClicked(vx, vy, button, screen.width, screen.height)) return true;
        if (TutorialOverlay.mouseClicked(screen, screen.width, screen.height, vx, vy, button)) return true;
        if (screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen() && screen.getPageBrowserDrawer().mouseClicked(vx, vy, button)) return true;
        if (screen.getLeftActivityBar().mouseClicked(vx, vy, button)) return true;
        if (screen.getWorkspaceTabBar().mouseClicked(vx, vy, button)) return true;
        if (screen.getPageTabBar().mouseClicked(vx, vy, button)) return true;
        if (screen.getFavoritesDockWidget().mouseClicked(vx, vy, button)) return true;
        if (BoardManager.getInstance().isShowHotkeyHud() && screen.getHotkeyHudWidget().mouseClicked(vx, vy, button)) return true;
        screen.getSummaryOverlay().setRightOffset(screen.getSummaryRightOffset());
        if (screen.getNodeInspectorPanel().mouseClicked(vx, vy, button)) return true;
        if (screen.getSummaryOverlay().mouseClicked(vx, vy, button, screen.width, screen.height)) return true;
        if (screen.getStatusBar().mouseClicked(vx, vy, button)) return true;
        if (screen.getToolbarWidget().mouseClicked(vx, vy, button)) return true;
        if (screen.getSelectionToolbarWidget().mouseClicked(vx, vy, button)) return true;
        if (screen.getCanvasHandler().mouseClicked(vx, vy, button)) return true;
        return false;
    }

    public boolean handleMouseReleased(double mouseX, double mouseY, int button) {
        double vx = screen.getViewportTransform().toVirtualX(mouseX);
        double vy = screen.getViewportTransform().toVirtualY(mouseY);
        if (screen.getDialogManager().handleMouseReleased(vx, vy, button)) return true;
        if (screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen() && screen.getPageBrowserDrawer().mouseReleased(vx, vy, button)) return true;
        if (screen.getFavoritesDockWidget().mouseReleased(vx, vy, button)) return true;
        if (screen.getPageTabBar().mouseReleased(vx, vy, button)) return true;
        if (screen.getToolbarWidget().mouseReleased(vx, vy, button)) return true;
        if (screen.getCanvasHandler().mouseReleased(vx, vy, button)) return true;
        return false;
    }

    public boolean handleMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        double vx = screen.getViewportTransform().toVirtualX(mouseX);
        double vy = screen.getViewportTransform().toVirtualY(mouseY);
        double vdx = screen.getViewportTransform().toVirtualX(dragX);
        double vdy = screen.getViewportTransform().toVirtualY(dragY);
        if (screen.getDialogManager().handleMouseDragged(vx, vy, button, vdx, vdy, screen.width, screen.height)) return true;
        if (screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen() && screen.getPageBrowserDrawer().mouseDragged(vx, vy, button, vdx, vdy)) return true;
        if (screen.getFavoritesDockWidget().mouseDragged(vx, vy, button, vdx, vdy)) return true;
        if (screen.getPageTabBar().mouseDragged(vx, vy, button, vdx, vdy)) return true;
        if (screen.getToolbarWidget().mouseDragged(vx, vy, button, vdx, vdy)) return true;
        if (screen.getCanvasHandler().mouseDragged(vx, vy, button, vdx, vdy)) {
            if (screen.getWireRenderer() != null) screen.getWireRenderer().markDirty();
            return true;
        }
        return false;
    }

    public boolean handleMouseScrolled(double mouseX, double mouseY, double delta) {
        double vx = screen.getViewportTransform().toVirtualX(mouseX);
        double vy = screen.getViewportTransform().toVirtualY(mouseY);
        if (screen.getDialogManager().handleMouseScrolled(vx, vy, delta)) return true;
        if (screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen() && screen.getPageBrowserDrawer().mouseScrolled(vx, vy, delta)) return true;
        if (screen.getFavoritesDockWidget().mouseScrolled(vx, vy, delta)) return true;
        if (screen.getPageTabBar().mouseScrolled(vx, vy, delta)) return true;
        screen.getSummaryOverlay().setRightOffset(screen.getSummaryRightOffset());
        if (screen.getNodeInspectorPanel().mouseScrolled(vx, vy, delta)) return true;
        if (screen.getSummaryOverlay().mouseScrolled(vx, vy, delta, screen.width, screen.height)) return true;
        if (screen.getToolbarWidget().mouseScrolled(vx, vy, delta)) return true;
        if (BoardManager.getInstance().isShowHotkeyHud() && screen.getHotkeyHudWidget().mouseScrolled(vx, vy, delta)) return true;

        double canvasMouseX = screen.toCanvasX(vx);
        double canvasMouseY = screen.toCanvasY(vy);
        for (int i = screen.getNodeWidgets().size() - 1; i >= 0; i--) {
            if (screen.getNodeWidgets().get(i).mouseScrolled(canvasMouseX, canvasMouseY, delta)) return true;
        }
        if (screen.getCanvasHandler().getWireHandler().handleWireScroll(canvasMouseX, canvasMouseY, delta, screen)) {
            return true;
        }
        if (screen.getCanvasHandler().mouseScrolled(vx, vy, delta)) return true;
        return false;
    }
}
