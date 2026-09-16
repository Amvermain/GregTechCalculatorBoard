package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.canvas.BoardKeybindDispatcher;
import com.gtceu.calcboard.client.gui.dialog.modal.IBoardModal;
import com.gtceu.calcboard.client.gui.dialog.modal.ModalRenderContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

public class ModalHotkeyIsolationTest {

    private static class PassiveModal implements IBoardModal {
        private boolean visible = true;

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public void close() {
            this.visible = false;
        }

        @Override
        public void renderModal(ModalRenderContext context) {}

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            return false;
        }
    }

    @BeforeEach
    @AfterEach
    public void cleanup() {
        BoardManager.getInstance().resetToDefault();
    }

    @Test
    @DisplayName("RFC-058: Modal active blocks canvas hotkeys from deleting or modifying selected nodes")
    public void testModalBlocksCanvasHotkeys() {
        BoardScreen screen = new BoardScreen();
        RecipeNode node = RecipeNode.create("Target Node", 20.0, 30.0, GTVoltageTier.LV);
        screen.getGraph().addNode(node);
        screen.rebuildWidgets();
        screen.selectNode(node.getId(), false);
        Assertions.assertTrue(screen.isNodeSelected(node.getId()));

        PassiveModal modal = new PassiveModal();
        screen.getDialogManager().getModalStack().push(modal);
        Assertions.assertTrue(screen.isAnyModalOpen());

        boolean handled = BoardKeybindDispatcher.handleKeyPressed(screen, GLFW.GLFW_KEY_DELETE, 0, 0, 0, 0);
        Assertions.assertTrue(handled);
        Assertions.assertNotNull(screen.getGraph().findNodeById(node.getId()), "Node must not be deleted while modal is open");

        handled = BoardKeybindDispatcher.handleKeyPressed(screen, GLFW.GLFW_KEY_Z, 0, GLFW.GLFW_MOD_CONTROL, 0, 0);
        Assertions.assertTrue(handled);

        screen.getDialogManager().getModalStack().pop();
        Assertions.assertFalse(screen.isAnyModalOpen());

        handled = BoardKeybindDispatcher.handleKeyPressed(screen, GLFW.GLFW_KEY_DELETE, 0, 0, 0, 0);
        Assertions.assertTrue(handled);
        Assertions.assertNull(screen.getGraph().findNodeById(node.getId()), "Node should be deleted when no modal is open");
    }

    @Test
    @DisplayName("RFC-058: Modal active blocks dialog hotkeys (M for BOM, B for Balance, Space for Search)")
    public void testModalBlocksDialogHotkeys() {
        BoardScreen screen = new BoardScreen();
        screen.getDialogManager().init();
        PassiveModal modal = new PassiveModal();
        screen.getDialogManager().getModalStack().push(modal);
        Assertions.assertTrue(screen.isAnyModalOpen());

        // While modal is open, M, B, and Space hotkeys must be consumed early and not open other dialogs
        boolean handledM = BoardKeybindDispatcher.handleKeyPressed(screen, GLFW.GLFW_KEY_M, 0, 0, 0, 0);
        Assertions.assertTrue(handledM);
        Assertions.assertFalse(screen.getMultiblockBOMDialog().isVisible(), "BOM dialog must not open while another modal is active");

        boolean handledB = BoardKeybindDispatcher.handleKeyPressed(screen, GLFW.GLFW_KEY_B, 0, 0, 0, 0);
        Assertions.assertTrue(handledB);
        Assertions.assertFalse(screen.getGlobalBalanceDialog().isVisible(), "Global balance dashboard must not open while another modal is active");

        boolean handledSpace = BoardKeybindDispatcher.handleKeyPressed(screen, GLFW.GLFW_KEY_SPACE, 0, 0, 0, 0);
        Assertions.assertTrue(handledSpace);
        Assertions.assertFalse(screen.getSearchDialog().isVisible(), "Recipe search dialog must not open while another modal is active");
    }
}
