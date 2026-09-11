package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.MultiLineEditBox;
import net.minecraft.network.chat.Component;

public class BoardMultiLineEditBox extends MultiLineEditBox {

    public BoardMultiLineEditBox(Font font, int x, int y, int width, int height, Component placeholder, Component narration) {
        super(font, x, y, width, height, placeholder, narration);
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (!this.visible) return;

        this.renderBackground(graphics);
        BoardScissorHelper.enableScissor(graphics, this.getX() + 1, this.getY() + 1, this.getX() + this.width - 1, this.getY() + this.height - 1);
        graphics.pose().pushPose();
        graphics.pose().translate(0.0D, -this.scrollAmount(), 0.0D);
        this.renderContents(graphics, mouseX, mouseY, partialTicks);
        graphics.pose().popPose();
        BoardScissorHelper.disableScissor(graphics);
        this.renderDecorations(graphics);
    }
}
