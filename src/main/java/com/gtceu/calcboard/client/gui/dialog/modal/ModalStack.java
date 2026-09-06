package com.gtceu.calcboard.client.gui.dialog.modal;

import net.minecraft.client.gui.GuiGraphics;

import java.util.*;

/**
 * LIFO modal dialog stack managing active dialogs, input event priority, and single-pass background dimming.
 */
public class ModalStack {

    private final Deque<IBoardModal> activeModals = new ArrayDeque<>();

    public synchronized void push(IBoardModal modal) {
        if (modal == null) return;
        activeModals.remove(modal);
        activeModals.push(modal);
        modal.onOpen();
    }

    public synchronized IBoardModal pop() {
        if (activeModals.isEmpty()) return null;
        IBoardModal top = activeModals.pop();
        top.close();
        top.onClose();
        return top;
    }

    public synchronized boolean remove(IBoardModal modal) {
        if (modal == null) return false;
        boolean removed = activeModals.remove(modal);
        if (removed) {
            modal.close();
            modal.onClose();
        }
        return removed;
    }

    public synchronized IBoardModal getTopModal() {
        return activeModals.peekFirst();
    }

    public synchronized boolean contains(IBoardModal modal) {
        return modal != null && activeModals.contains(modal);
    }

    public synchronized boolean hasActiveModal() {
        pruneInactiveModals();
        return !activeModals.isEmpty();
    }

    public synchronized int size() {
        pruneInactiveModals();
        return activeModals.size();
    }

    public synchronized void closeAll() {
        while (!activeModals.isEmpty()) {
            pop();
        }
    }

    public synchronized void pruneInactiveModals() {
        activeModals.removeIf(modal -> modal == null || !modal.isVisible());
    }

    public synchronized void render(GuiGraphics graphics, int screenWidth, int screenHeight, int mouseX, int mouseY, float partialTicks) {
        pruneInactiveModals();
        if (activeModals.isEmpty()) return;

        boolean needsBackdrop = false;
        for (IBoardModal modal : activeModals) {
            if (modal.requiresBackdropDim()) {
                needsBackdrop = true;
                break;
            }
        }

        if (needsBackdrop) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 500.0f);
            graphics.fill(0, 0, screenWidth, screenHeight, 0x88000000);
            graphics.pose().popPose();
        }

        ModalRenderContext context = new ModalRenderContext(graphics, screenWidth, screenHeight, mouseX, mouseY, partialTicks);
        Iterator<IBoardModal> it = activeModals.descendingIterator();
        while (it.hasNext()) {
            IBoardModal modal = it.next();
            if (modal.isVisible()) {
                modal.renderModal(context);
            }
        }
    }

    public synchronized boolean dispatchMouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        pruneInactiveModals();
        IBoardModal top = getTopModal();
        if (top == null) return false;

        boolean consumed = top.mouseClicked(mouseX, mouseY, button, screenWidth, screenHeight);
        if (consumed) return true;

        if (top.closesOnOutsideClick()) {
            pop();
            return true;
        }

        return true;
    }

    public synchronized boolean dispatchMouseReleased(double mouseX, double mouseY, int button) {
        pruneInactiveModals();
        IBoardModal top = getTopModal();
        if (top == null) return false;
        return top.mouseReleased(mouseX, mouseY, button);
    }

    public synchronized boolean dispatchMouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int screenWidth, int screenHeight) {
        pruneInactiveModals();
        IBoardModal top = getTopModal();
        if (top == null) return false;
        return top.mouseDragged(mouseX, mouseY, button, dragX, dragY, screenWidth, screenHeight);
    }

    public synchronized boolean dispatchMouseScrolled(double mouseX, double mouseY, double delta) {
        pruneInactiveModals();
        IBoardModal top = getTopModal();
        if (top == null) return false;
        return top.mouseScrolled(mouseX, mouseY, delta);
    }

    public synchronized boolean dispatchKeyPressed(int keyCode, int scanCode, int modifiers) {
        pruneInactiveModals();
        IBoardModal top = getTopModal();
        if (top == null) return false;
        return top.keyPressed(keyCode, scanCode, modifiers);
    }

    public synchronized boolean dispatchCharTyped(char codePoint, int modifiers) {
        pruneInactiveModals();
        IBoardModal top = getTopModal();
        if (top == null) return false;
        return top.charTyped(codePoint, modifiers);
    }

    public synchronized void tick() {
        pruneInactiveModals();
        for (IBoardModal modal : activeModals) {
            modal.tick();
        }
    }
}
