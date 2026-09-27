package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public interface IInspectorSection {

    void bind(NodeWidget widget, RecipeNode node, IBoardScreenContext screen);

    boolean isApplicable(RecipeNode node);

    int getHeight(RecipeNode node);

    void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY);

    boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button);

    default Component getPendingTooltip() {
        return null;
    }

    default boolean isFullWidth() {
        return false;
    }
}
