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
        if (screen.getSaveToTeamDialog() != null && screen.getSaveToTeamDialog().isVisible()) {
            return screen.getSaveToTeamDialog().keyPressed(keyCode, scanCode, modifiers);
        }
        if (screen.getSettingsDialog() != null && screen.getSettingsDialog().isVisible()) {
            return screen.getSettingsDialog().keyPressed(keyCode, scanCode, modifiers);
        }
        if (screen.getExportToTeamDialog() != null && screen.getExportToTeamDialog().isVisible()) {
            return screen.getExportToTeamDialog().keyPressed(keyCode, scanCode, modifiers);
        }
        if (screen.getExportBlueprintDialog() != null && screen.getExportBlueprintDialog().isVisible()) {
            return screen.getExportBlueprintDialog().keyPressed(keyCode, scanCode, modifiers);
        }
        if (screen.getImportBlueprintDialog() != null && screen.getImportBlueprintDialog().isVisible()) {
            return screen.getImportBlueprintDialog().keyPressed(keyCode, scanCode, modifiers);
        }
        if (screen.getDiskBlueprintsDialog() != null && screen.getDiskBlueprintsDialog().isVisible()) {
            return screen.getDiskBlueprintsDialog().keyPressed(keyCode, scanCode, modifiers);
        }
        if (screen.getRecentSavesDialog() != null && screen.getRecentSavesDialog().isVisible()) {
            return screen.getRecentSavesDialog().keyPressed(keyCode, scanCode, modifiers);
        }
        if (screen.getFrameEditDialog() != null && screen.getFrameEditDialog().isVisible()) {
            return screen.getFrameEditDialog().keyPressed(keyCode, scanCode, modifiers);
        }
        if (screen.getNoteEditDialog() != null && screen.getNoteEditDialog().isVisible()) {
            return screen.getNoteEditDialog().keyPressed(keyCode, scanCode, modifiers);
        }
        if (screen.getMachineConfigDialog() != null && screen.getMachineConfigDialog().isVisible()) {
            return screen.getMachineConfigDialog().keyPressed(keyCode, scanCode, modifiers);
        }
        if (screen.getAutoConnectDialog() != null && screen.getAutoConnectDialog().isVisible()) {
            return screen.getAutoConnectDialog().keyPressed(keyCode, scanCode, modifiers);
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
        if (screen.getSaveToTeamDialog() != null && screen.getSaveToTeamDialog().isVisible()) {
            return screen.getSaveToTeamDialog().charTyped(codePoint, modifiers);
        }
        if (screen.getExportBlueprintDialog() != null && screen.getExportBlueprintDialog().isVisible()) {
            return screen.getExportBlueprintDialog().charTyped(codePoint, modifiers);
        }
        if (screen.getDiskBlueprintsDialog() != null && screen.getDiskBlueprintsDialog().isVisible()) {
            return screen.getDiskBlueprintsDialog().charTyped(codePoint, modifiers);
        }
        if (screen.getSettingsDialog() != null && screen.getSettingsDialog().isVisible()) {
            return screen.getSettingsDialog().charTyped(codePoint, modifiers);
        }
        if (screen.getGlobalBalanceDialog() != null && screen.getGlobalBalanceDialog().isVisible()) {
            return screen.getGlobalBalanceDialog().charTyped(codePoint, modifiers);
        }
        if (screen.getMultiblockBOMDialog() != null && screen.getMultiblockBOMDialog().isVisible()) {
            return screen.getMultiblockBOMDialog().charTyped(codePoint, modifiers);
        }
        if (screen.getPageTabBar() != null && screen.getPageTabBar().charTyped(codePoint, modifiers)) {
            return true;
        }
        if (screen.getMachineConfigDialog() != null && screen.getMachineConfigDialog().isVisible()) {
            return screen.getMachineConfigDialog().charTyped(codePoint, modifiers);
        }
        if (screen.getSearchDialog() != null && screen.getSearchDialog().isVisible()) {
            return screen.getSearchDialog().charTyped(codePoint, modifiers);
        }
        if (screen.getFrameEditDialog() != null && screen.getFrameEditDialog().isVisible()) {
            return screen.getFrameEditDialog().charTyped(codePoint, modifiers);
        }
        if (screen.getNoteEditDialog() != null && screen.getNoteEditDialog().isVisible()) {
            return screen.getNoteEditDialog().charTyped(codePoint, modifiers);
        }

        for (NodeWidget w : screen.getNodeWidgets()) {
            if (w.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return false;
    }
}
