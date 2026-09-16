package com.gtceu.calcboard.client.gui.inspector;

import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public interface INodeSubInspector {

    void bind(NodeWidget targetWidget);

    void render(GuiGraphics graphics, Font font, int px, int py, int ph, int mouseX, int mouseY);

    boolean mouseClicked(int px, int py, double mouseX, double mouseY, int button);

    int getContentHeight();

    Component getPendingTooltip();
}
