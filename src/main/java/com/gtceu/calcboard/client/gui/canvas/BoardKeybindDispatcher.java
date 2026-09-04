package com.gtceu.calcboard.client.gui.canvas;

import com.gtceu.calcboard.client.gui.BoardHotkeyHandler;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;

/**
 * Handles keyboard event dispatching, shortcut routing, and dialog input focus prioritization.
 */
public class BoardKeybindDispatcher {

    public static boolean handleKeyPressed(BoardScreen screen, int keyCode, int scanCode, int modifiers, int lastMouseX, int lastMouseY) {
        if (screen.getDialogManager() != null && screen.getDialogManager().handleKeyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (screen.getPageTabBar() != null && screen.getPageTabBar().isEditing()) {
            return screen.getPageTabBar().keyPressed(keyCode, scanCode, modifiers);
        }
        if (screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen()) {
            if (screen.getPageBrowserDrawer().keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        if (screen.getPageTabBar() != null && screen.getPageTabBar().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (BoardHotkeyHandler.handleKeyPressed(screen, keyCode, scanCode, modifiers, lastMouseX, lastMouseY)) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.options != null && mc.options.keyInventory.matches(keyCode, scanCode)) {
            return false;
        }
        return false;
    }

    public static boolean handleCharTyped(BoardScreen screen, char codePoint, int modifiers) {
        if (screen.getDialogManager() != null && screen.getDialogManager().handleCharTyped(codePoint, modifiers)) {
            return true;
        }
        if (screen.getPageTabBar() != null && screen.getPageTabBar().isEditing()) {
            return screen.getPageTabBar().charTyped(codePoint, modifiers);
        }
        if (screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen()) {
            if (screen.getPageBrowserDrawer().charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        if (screen.getPageTabBar() != null && screen.getPageTabBar().charTyped(codePoint, modifiers)) {
            return true;
        }
        for (NodeWidget w : screen.getNodeWidgets()) {
            if (w.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }
}
