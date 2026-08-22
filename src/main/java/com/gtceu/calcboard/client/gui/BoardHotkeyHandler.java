package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import dev.emi.emi.api.EmiApi;
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

        // Priority ESC handlers (Active wire drag, Welcome dialog, active tutorial, modals)
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (screen.getCanvasHandler() != null && screen.getCanvasHandler().isDraggingWire()) {
                screen.getCanvasHandler().cancelWireDrag();
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

        if (screen.getGlobalBalanceDialog() != null && screen.getGlobalBalanceDialog().isVisible()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                screen.getGlobalBalanceDialog().close();
                return true;
            }
            return screen.getGlobalBalanceDialog().keyPressed(keyCode, scanCode, modifiers);
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

        // 3. Active inline editing in Node Widgets (Backspace, Enter, Esc, Digits)
        for (NodeWidget w : screen.getNodeWidgets()) {
            if (w.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }

        // 4. Global Hotkeys (Ctrl+Z, Ctrl+Y, Ctrl+C, Ctrl+X, Ctrl+V, Ctrl+D, Ctrl+A, Ctrl+G)
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) {
            boolean shift = (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
            if (keyCode == GLFW.GLFW_KEY_Z) {
                if (shift) {
                    screen.redo();
                } else {
                    screen.undo();
                }
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_Y) {
                screen.redo();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_X) {
                screen.cutSelection();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_C) {
                screen.copySelection();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_V) {
                screen.pasteSelection(screen.toCanvasX(lastMouseX), screen.toCanvasY(lastMouseY));
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_D) {
                screen.duplicateSelection();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_A) {
                screen.selectAll();
                return true;
            } else if (keyCode == GLFW.GLFW_KEY_G) {
                if (shift) {
                    screen.performGroupIntoModule();
                } else {
                    screen.createFrameFromSelection();
                }
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

        // 6. EMI recipe lookup under cursor [R] / [U]
        if (keyCode == GLFW.GLFW_KEY_R || keyCode == GLFW.GLFW_KEY_U) {
            double canvasMouseX = screen.toCanvasX(lastMouseX);
            double canvasMouseY = screen.toCanvasY(lastMouseY);
            List<NodeWidget> nodeWidgets = screen.getNodeWidgets();

            for (int i = nodeWidgets.size() - 1; i >= 0; i--) {
                NodeWidget widget = nodeWidgets.get(i);
                if (widget.isPointInside(canvasMouseX, canvasMouseY)) {
                    IngredientStack hovered = widget.getHoveredIngredient(canvasMouseX, canvasMouseY);
                    if (hovered != null) {
                        var emiStack = IngredientRenderer.toEmiStack(hovered);
                        if (!emiStack.isEmpty()) {
                            if (keyCode == GLFW.GLFW_KEY_R) {
                                EmiApi.displayRecipes(emiStack);
                            } else {
                                EmiApi.displayUses(emiStack);
                            }
                            TutorialManager.getInstance().onRecipeLookup();
                            return true;
                        }
                    }
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

        // 9. Toggle Global Balance Dashboard: B
        if (keyCode == GLFW.GLFW_KEY_B && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            if (screen.getGlobalBalanceDialog() != null) {
                if (screen.getGlobalBalanceDialog().isVisible()) {
                    screen.getGlobalBalanceDialog().close();
                } else {
                    screen.getGlobalBalanceDialog().open();
                }
                return true;
            }
        }

        // 10. Cycle Time Unit (/t, /s, /min, /h, /d): T
        if (keyCode == GLFW.GLFW_KEY_T && (modifiers & GLFW.GLFW_MOD_CONTROL) == 0) {
            com.gtceu.calcboard.api.RateTimeUnit curUnit = FormatUtil.getActiveTimeUnit();
            com.gtceu.calcboard.api.RateTimeUnit next = curUnit.next();
            FormatUtil.setActiveTimeUnit(next);
            BoardToast.show(net.minecraft.network.chat.Component.literal("§e⏱ ").append(
                net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.toast.time_unit_changed", next.getSuffix(), net.minecraft.network.chat.Component.translatable(next.getTranslationKey()).getString())
            ));
            net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.0F)
            );
            screen.markSummaryDirty();
            return true;
        }

        return false;
    }
}
