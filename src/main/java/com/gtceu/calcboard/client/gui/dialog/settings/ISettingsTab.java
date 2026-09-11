package com.gtceu.calcboard.client.gui.dialog.settings;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Interface representing an individual tab within the BoardSettingsDialog.
 */
public interface ISettingsTab {

    void render(GuiGraphics graphics, Font font, int x, int y, int w, int h, int mouseX, int mouseY);

    boolean mouseClicked(double mouseX, double mouseY, int x, int y, int w, int h, int button);

    default boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return false;
    }

    default boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return false;
    }

    default boolean charTyped(char codePoint, int modifiers) {
        return false;
    }

    default void onOpen() {}

    default void onClose() {}
}
