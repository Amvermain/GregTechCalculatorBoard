package com.gtceu.calcboard.client.gui.dialog.modal;

import net.minecraft.client.gui.components.events.GuiEventListener;
import org.lwjgl.glfw.GLFW;

/**
 * Standard contract for all interactive modal dialogs on the board canvas.
 */
public interface IBoardModal {

    boolean isVisible();

    void close();

    default GuiEventListener getFocusedWidget() {
        return null;
    }

    default void onOpen() {}

    default void onClose() {}

    void renderModal(ModalRenderContext context);

    default boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        return mouseClicked(mouseX, mouseY, button);
    }

    default boolean mouseReleased(double mouseX, double mouseY, int button) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY, int screenWidth, int screenHeight) {
        return false;
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        return false;
    }

    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    default void tick() {}

    default boolean requiresBackdropDim() {
        return false;
    }

    default boolean closesOnOutsideClick() {
        return false;
    }

    default int getZOrder() {
        return 100;
    }
}
