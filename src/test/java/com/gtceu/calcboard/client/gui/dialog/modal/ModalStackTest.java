package com.gtceu.calcboard.client.gui.dialog.modal;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.BoardDialogManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicInteger;

public class ModalStackTest {

    private static class DummyModal implements IBoardModal {
        private boolean visible = true;
        private boolean closed = false;
        private boolean outsideClickClose = false;
        private boolean needsDim = false;
        final AtomicInteger clickCount = new AtomicInteger(0);
        final AtomicInteger keyCount = new AtomicInteger(0);

        DummyModal() {}

        DummyModal(boolean outsideClickClose, boolean needsDim) {
            this.outsideClickClose = outsideClickClose;
            this.needsDim = needsDim;
        }

        @Override
        public boolean isVisible() {
            return visible;
        }

        @Override
        public void close() {
            this.visible = false;
            this.closed = true;
        }

        @Override
        public void renderModal(ModalRenderContext context) {}

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            clickCount.incrementAndGet();
            return !outsideClickClose;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            keyCount.incrementAndGet();
            return IBoardModal.super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override
        public boolean closesOnOutsideClick() {
            return outsideClickClose;
        }

        @Override
        public boolean requiresBackdropDim() {
            return needsDim;
        }
    }

    @Test
    @DisplayName("RFC-026: ModalStack handles LIFO push, pop, and top modal retrieval")
    void testLifoPushAndPop() {
        ModalStack stack = new ModalStack();
        DummyModal modalA = new DummyModal();
        DummyModal modalB = new DummyModal();

        stack.push(modalA);
        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(modalA, stack.getTopModal());
        Assertions.assertTrue(stack.hasActiveModal());
        Assertions.assertTrue(stack.contains(modalA));

        stack.push(modalB);
        Assertions.assertEquals(2, stack.size());
        Assertions.assertEquals(modalB, stack.getTopModal());
        Assertions.assertTrue(stack.contains(modalB));

        IBoardModal popped = stack.pop();
        Assertions.assertEquals(modalB, popped);
        Assertions.assertTrue(modalB.closed);
        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(modalA, stack.getTopModal());

        stack.closeAll();
        Assertions.assertEquals(0, stack.size());
        Assertions.assertFalse(stack.hasActiveModal());
        Assertions.assertTrue(modalA.closed);
    }

    @Test
    @DisplayName("RFC-026: ModalStack moves already present modal to top when re-pushed")
    void testRepushMovesToTop() {
        ModalStack stack = new ModalStack();
        DummyModal modalA = new DummyModal();
        DummyModal modalB = new DummyModal();

        stack.push(modalA);
        stack.push(modalB);
        Assertions.assertEquals(modalB, stack.getTopModal());

        stack.push(modalA);
        Assertions.assertEquals(2, stack.size());
        Assertions.assertEquals(modalA, stack.getTopModal());
    }

    @Test
    @DisplayName("RFC-026: ModalStack automatically prunes inactive/invisible modals")
    void testPruneInactiveModals() {
        ModalStack stack = new ModalStack();
        DummyModal modalA = new DummyModal();
        DummyModal modalB = new DummyModal();

        stack.push(modalA);
        stack.push(modalB);

        modalB.visible = false;

        Assertions.assertTrue(stack.hasActiveModal());
        Assertions.assertEquals(1, stack.size());
        Assertions.assertEquals(modalA, stack.getTopModal());
    }

    @Test
    @DisplayName("RFC-026: Input events are dispatched exclusively to the topmost modal")
    void testEventDispatchToTopModalOnly() {
        ModalStack stack = new ModalStack();
        DummyModal modalA = new DummyModal();
        DummyModal modalB = new DummyModal();

        stack.push(modalA);
        stack.push(modalB);

        boolean consumed = stack.dispatchMouseClicked(100, 100, 0, 800, 600);
        Assertions.assertTrue(consumed);
        Assertions.assertEquals(0, modalA.clickCount.get());
        Assertions.assertEquals(1, modalB.clickCount.get());

        stack.dispatchKeyPressed(GLFW.GLFW_KEY_A, 0, 0);
        Assertions.assertEquals(0, modalA.keyCount.get());
        Assertions.assertEquals(1, modalB.keyCount.get());
    }

    @Test
    @DisplayName("RFC-026: Default Escape key closes the active modal")
    void testEscapeClosesModal() {
        ModalStack stack = new ModalStack();
        DummyModal modal = new DummyModal();

        stack.push(modal);
        boolean handled = stack.dispatchKeyPressed(GLFW.GLFW_KEY_ESCAPE, 0, 0);

        Assertions.assertTrue(handled);
        Assertions.assertTrue(modal.closed);
        Assertions.assertFalse(stack.hasActiveModal());
    }

    @Test
    @DisplayName("RFC-026: Outside click auto-closes modals configured with closesOnOutsideClick")
    void testOutsideClickClosesModal() {
        ModalStack stack = new ModalStack();
        DummyModal modal = new DummyModal(true, false);

        stack.push(modal);
        boolean handled = stack.dispatchMouseClicked(50, 50, 0, 800, 600);

        Assertions.assertTrue(handled);
        Assertions.assertTrue(modal.closed);
        Assertions.assertFalse(stack.hasActiveModal());
    }

    @Test
    @DisplayName("RFC-026: BoardDialogManager integrates ModalStack and tracks open dialogs")
    void testBoardDialogManagerIntegration() {
        BoardScreen screen = new BoardScreen();
        BoardDialogManager manager = new BoardDialogManager(screen);
        manager.init();

        Assertions.assertFalse(manager.isAnyModalOpen());

        manager.openQuickPageSwitcher();
        Assertions.assertTrue(manager.isAnyModalOpen());
        Assertions.assertEquals(manager.getQuickPageSwitcherDialog(), manager.getModalStack().getTopModal());

        manager.closeAllDialogs();
        Assertions.assertFalse(manager.isAnyModalOpen());
        Assertions.assertEquals(0, manager.getModalStack().size());
    }
}
