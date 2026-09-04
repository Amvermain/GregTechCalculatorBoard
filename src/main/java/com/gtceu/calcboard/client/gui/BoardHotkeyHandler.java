package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.FluidUnitMode;
import com.gtceu.calcboard.api.type.RateTimeUnit;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Handles keyboard input routing, dialog escapes, global shortcuts (Undo/Redo, Cut/Copy/Paste/Duplicate/Group),
 * and EMI recipe lookups for BoardScreen.
 */
public final class BoardHotkeyHandler {

    private BoardHotkeyHandler() {}

    public static boolean handleKeyPressed(BoardScreen screen, int keyCode, int scanCode, int modifiers, double lastMouseX, double lastMouseY) {
        if (screen == null) return false;

        // Priority ESC handlers (Active wire drag, QuickPageSwitcher, TemplateClone, Drawer, Welcome dialog, active tutorial, modals)
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (screen.getCanvasHandler() != null && screen.getCanvasHandler().isDraggingWire()) {
                screen.getCanvasHandler().cancelWireDrag();
                return true;
            }
            if (screen.getQuickPageSwitcherDialog() != null && screen.getQuickPageSwitcherDialog().isVisible()) {
                screen.getQuickPageSwitcherDialog().close();
                return true;
            }
            if (screen.getTemplateCloneDialog() != null && screen.getTemplateCloneDialog().isVisible()) {
                screen.getTemplateCloneDialog().close();
                return true;
            }
            if (screen.getPageBrowserDrawer() != null && screen.getPageBrowserDrawer().isOpen()) {
                screen.getPageBrowserDrawer().setOpen(false);
                return true;
            }
            if (screen.getWelcomeDialog().isVisible()) {
                screen.getWelcomeDialog().hide();
                return true;
            }
            if (TutorialManager.getInstance().isActive()) {
                TutorialManager.getInstance().stopTutorial();
                return true;
            }
        }

        // 2. Open Modal Dialog key handling
        if (screen.getDeletePageDialog() != null && screen.getDeletePageDialog().isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                screen.getDeletePageDialog().close();
                return true;
            }
            return screen.getDeletePageDialog().keyPressed(keyCode, scanCode, modifiers);
        }

        if (screen.getTutorialExitDialog() != null && screen.getTutorialExitDialog().isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                screen.getTutorialExitDialog().close();
                return true;
            }
            return screen.getTutorialExitDialog().keyPressed(keyCode, scanCode, modifiers);
        }

        if (screen.getGlobalBalanceDialog() != null && screen.getGlobalBalanceDialog().isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                screen.getGlobalBalanceDialog().close();
                return true;
            }
            return screen.getGlobalBalanceDialog().keyPressed(keyCode, scanCode, modifiers);
        }

        if (screen.getMultiblockBOMDialog() != null && screen.getMultiblockBOMDialog().isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                screen.getMultiblockBOMDialog().close();
                return true;
            }
            return screen.getMultiblockBOMDialog().keyPressed(keyCode, scanCode, modifiers);
        }

        if (screen.getGuideDialog() != null && screen.getGuideDialog().isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                screen.getGuideDialog().close();
                return true;
            }
            return screen.getGuideDialog().keyPressed(keyCode, scanCode, modifiers);
        }

        if (screen.getPageTabBar().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (screen.getFavoritesDockWidget() != null && screen.getFavoritesDockWidget().keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        if (screen.getMachineConfigDialog() != null && screen.getMachineConfigDialog().isVisible()) {
            return screen.getMachineConfigDialog().keyPressed(keyCode, scanCode, modifiers);
        }

        if (screen.getSearchDialog() != null && screen.getSearchDialog().isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                screen.getSearchDialog().setVisible(false);
                return true;
            }
            return screen.getSearchDialog().keyPressed(keyCode, scanCode, modifiers);
        }

        if (screen.getFrameEditDialog() != null && screen.getFrameEditDialog().isVisible()) {
            return screen.getFrameEditDialog().keyPressed(keyCode, scanCode, modifiers);
        }

        if (screen.getNoteEditDialog() != null && screen.getNoteEditDialog().isVisible()) {
            return screen.getNoteEditDialog().keyPressed(keyCode, scanCode, modifiers);
        }

        for (NodeWidget w : screen.getNodeWidgets()) {
            if (w.isAnyEditorActive()) {
                w.keyPressed(keyCode, scanCode, modifiers);
                return true;
            }
        }

        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            if (handleControlHotkeys(screen, keyCode, modifiers, lastMouseX, lastMouseY)) {
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_TAB && modifiers == 0) {
            if (screen.getPageBrowserDrawer() != null) {
                screen.getPageBrowserDrawer().toggle();
                return true;
            }
        }

        // 4.5 Insert Junction at Cursor: J
        if (keyCode == GLFW.GLFW_KEY_J && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            screen.addRerouteNodeAt(screen.toCanvasX(lastMouseX), screen.toCanvasY(lastMouseY));
            return true;
        }

        // 5. Delete / Backspace: Delete selected nodes
        if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!screen.getSelectionModel().isEmpty()) {
                screen.deleteSelection();
                return true;
            }
        }

        // 6. Recipe lookup under cursor [R] / [U] (EMI, JEI, etc.)
        if (keyCode == GLFW.GLFW_KEY_R || keyCode == GLFW.GLFW_KEY_U) {
            double canvasMouseX = screen.toCanvasX(lastMouseX);
            double canvasMouseY = screen.toCanvasY(lastMouseY);
            List<NodeWidget> nodeWidgets = screen.getNodeWidgets();

            for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
                NodeWidget widget = nodeWidgets.get(i);
                if (handleHoveredRecipeLookup(widget, canvasMouseX, canvasMouseY, keyCode)) {
                    return true;
                }
            }
        }

        // 7. Quick Recipe Search at Cursor: Space
        if (keyCode == GLFW.GLFW_KEY_SPACE && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            if (screen.getSearchDialog() != null && !screen.getSearchDialog().isVisible()) {
                screen.getSearchDialog().openAt(screen.toCanvasX(lastMouseX), screen.toCanvasY(lastMouseY));
                return true;
            }
        }

        // 8. Toggle Hotkey HUD: H
        if (keyCode == GLFW.GLFW_KEY_H && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            screen.getHotkeyHudWidget().toggle();
            return true;
        }

        // 8.5 Toggle Multiblock BOM Dialog: Shift + B or M
        if (((keyCode == GLFW.GLFW_KEY_B && (modifiers & GLFW.GLFW_MOD_SHIFT) != 0) || (keyCode == GLFW.GLFW_KEY_M && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0))) {
            if (!com.gtceu.calcboard.api.util.ModCompatHelper.isBoMSupported()) {
                return false;
            }
            if (screen.getMultiblockBOMDialog() != null) {
                if (screen.getMultiblockBOMDialog().isVisible()) {
                    screen.getMultiblockBOMDialog().close();
                } else {
                    screen.getMultiblockBOMDialog().open();
                }
                return true;
            }
        }

        // 9. Toggle Global Balance Dashboard: B
        if (keyCode == GLFW.GLFW_KEY_B && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0 && (modifiers & GLFW.GLFW_MOD_SHIFT) == 0) {
            if (screen.getGlobalBalanceDialog() != null) {
                if (screen.getGlobalBalanceDialog().isVisible()) {
                    screen.getGlobalBalanceDialog().close();
                } else {
                    screen.getGlobalBalanceDialog().open();
                }
                return true;
            }
        }

        // 10. Cycle Time Unit (T) or Fluid Unit (Shift+T)
        if (keyCode == GLFW.GLFW_KEY_T && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (shift) {
                com.gtceu.calcboard.api.type.FluidUnitMode curFluid = FormatUtil.getActiveFluidUnitMode();
                com.gtceu.calcboard.api.type.FluidUnitMode next = curFluid.next();
                FormatUtil.setActiveFluidUnitMode(next);
                BoardManager.getInstance().setFluidUnitMode(next);
                BoardManager.getInstance().saveForCurrentContext();
                BoardToast.show(net.minecraft.network.chat.Component.literal("§b~ ").append(
                    net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.toast.fluid_unit_changed", net.minecraft.network.chat.Component.translatable(next.getTranslationKey()).getString())
                ));
            } else {
                com.gtceu.calcboard.api.type.RateTimeUnit curUnit = FormatUtil.getActiveTimeUnit();
                com.gtceu.calcboard.api.type.RateTimeUnit next = curUnit.next();
                FormatUtil.setActiveTimeUnit(next);
                BoardManager.getInstance().setTimeUnit(next);
                BoardManager.getInstance().saveForCurrentContext();
                BoardToast.show(net.minecraft.network.chat.Component.literal("§e⏱ ").append(
                    net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.toast.time_unit_changed", next.getSuffix(), net.minecraft.network.chat.Component.translatable(next.getTranslationKey()).getString())
                ));
            }
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            screen.markSummaryDirty();
            return true;
        }

        // 11. Fit to View / Focus Content: Home / Shift+F / Ctrl+0
        if (keyCode == GLFW.GLFW_KEY_HOME || (keyCode == GLFW.GLFW_KEY_0 && (modifiers & GLFW.GLFW_MOD_CONTROL) != 0)) {
            screen.fitToView();
            return true;
        }

        // 12. F key: Flip Selected Nodes (if selection exists) or Fit to View (if Shift is held or no selection)
        if (keyCode == GLFW.GLFW_KEY_F && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (shift || screen.getSelectionModel().isEmpty()) {
                screen.fitToView();
            } else {
                screen.flipSelectedNodes();
            }
            return true;
        }

        return false;
    }

    private static boolean handleControlHotkeys(BoardScreen screen, int keyCode, int modifiers, double lastMouseX, double lastMouseY) {
        boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
        if (keyCode == GLFW.GLFW_KEY_K || keyCode == GLFW.GLFW_KEY_P) {
            screen.openQuickPageSwitcher();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Z) {
            if (shift) screen.redo();
            else screen.undo();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_Y) {
            screen.redo();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_X) {
            screen.cutSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_C) {
            screen.copySelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_V) {
            screen.pasteSelection(screen.toCanvasX(lastMouseX), screen.toCanvasY(lastMouseY));
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_D) {
            screen.duplicateSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_A) {
            screen.selectAll();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_G) {
            if (shift) screen.performGroupIntoModule();
            else screen.createFrameFromSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_S && shift) {
            screen.createSharedMachineFrameFromSelection();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_F3) {
            BoardManager.getInstance().toggleDebugInfo();
            BoardManager.getInstance().saveForCurrentContext();
            boolean enabled = BoardManager.getInstance().isShowDebugInfo();
            String toastKey = enabled ? "gui.gtcalcboard.toast.debug_enabled" : "gui.gtcalcboard.toast.debug_disabled";
            BoardToast.show(net.minecraft.network.chat.Component.literal(enabled ? "§a⚙ " : "§7⚙ ")
                    .append(net.minecraft.network.chat.Component.translatable(toastKey)));
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            return true;
        }
        return false;
    }

    private static boolean handleHoveredRecipeLookup(NodeWidget widget, double canvasX, double canvasY, int keyCode) {
        if (widget == null || !widget.isPointInside(canvasX, canvasY)) return false;
        IngredientStack hovered = widget.getHoveredIngredient(canvasX, canvasY);
        if (hovered == null) return false;

        var adapter = com.gtceu.calcboard.integration.spi.RecipeViewerRegistry.getActiveAdapter();
        if (adapter == null) return false;

        boolean success = (keyCode == GLFW.GLFW_KEY_R) ? adapter.displayRecipes(hovered) : adapter.displayUses(hovered);
        if (success) {
            TutorialManager.getInstance().onRecipeLookup();
            return true;
        }
        return false;
    }
}



