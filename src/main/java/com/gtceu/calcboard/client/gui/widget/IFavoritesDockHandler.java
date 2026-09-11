package com.gtceu.calcboard.client.gui.widget;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Interface representing favorites dock implementation actions.
 * Decouples client widget logic from mod-specific recipe viewer APIs like EMI.
 */
public interface IFavoritesDockHandler {

    void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick);

    void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY);

    boolean keyPressed(int keyCode, int scanCode, int modifiers);

    boolean mouseClicked(double mouseX, double mouseY, int button);

    boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY);

    boolean mouseReleased(double mouseX, double mouseY, int button);

    boolean mouseScrolled(double mouseX, double mouseY, double delta);

    void closeFlyout();

    void resetScrollBarDrag();

    boolean isEmiLoading();
}
