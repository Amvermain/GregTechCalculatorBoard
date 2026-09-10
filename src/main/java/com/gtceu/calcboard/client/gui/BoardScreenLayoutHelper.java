package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.client.gui.widget.LeftActivityBarWidget;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;

/**
 * Calculates dynamic layout bounds, margin offsets, and header dimensions for BoardScreen.
 */
public final class BoardScreenLayoutHelper {
    private BoardScreenLayoutHelper() {}

    public static int getDynamicLeftMargin(BoardScreen screen) {
        int maxRight = LeftActivityBarWidget.BAR_WIDTH + 6;
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && !(widget instanceof EditBox)) {
                if (widget.visible && widget.getY() < 60 && widget.getX() >= 0 && widget.getX() < screen.width / 3) {
                    maxRight = Math.max(maxRight, widget.getX() + widget.getWidth() + 6);
                }
            }
        }
        return maxRight;
    }

    public static int getPageTabY() {
        return ClientWorkspaceState.getInstance().isCollaborationEnabled() ? 22 : 2;
    }

    public static int getToolbarY() {
        return ClientWorkspaceState.getInstance().isCollaborationEnabled() ? 42 : 22;
    }

    public static int getHeaderBottomY() {
        return ClientWorkspaceState.getInstance().isCollaborationEnabled() ? 64 : 44;
    }

    public static int getFavoritesDockY(BoardScreen screen) {
        int maxBottom = getHeaderBottomY() + 6;
        for (var child : screen.children()) {
            if (child instanceof AbstractWidget widget && !(widget instanceof EditBox)) {
                if (widget.visible && widget.getX() < 160 && widget.getY() < 120) {
                    maxBottom = Math.max(maxBottom, widget.getY() + widget.getHeight());
                }
            }
        }
        return maxBottom;
    }

    public static int getSummaryRightOffset(BoardScreen screen) {
        return (screen.getNodeInspectorPanel() != null && screen.getNodeInspectorPanel().isVisible())
                ? (screen.getNodeInspectorPanel().getPanelWidth() + 6)
                : 0;
    }
}
