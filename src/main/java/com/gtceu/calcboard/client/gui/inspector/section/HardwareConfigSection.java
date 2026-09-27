package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class HardwareConfigSection implements IInspectorSection {

    private NodeWidget widget;
    private RecipeNode node;
    private IBoardScreenContext screen;

    @Override
    public void bind(NodeWidget widget, RecipeNode node, IBoardScreenContext screen) {
        this.widget = widget;
        this.node = node;
        this.screen = screen;
    }

    @Override
    public boolean isApplicable(RecipeNode node) {
        return node != null && !node.isModule();
    }

    @Override
    public int getHeight(RecipeNode node) {
        return 44;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        if (node == null) return;

        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 36;
        graphics.fill(x, y, x + w, y + 36, hov ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(x, y, w, 36, hov ? 0xFF38BDF8 : 0xFF334155);

        String title = "⚙ " + Component.translatable("gui.gtcalcboard.inspector.machine_config").getString();
        graphics.drawString(font, title, x + 6, y + 5, 0xFF38BDF8, false);

        String subText = node.isMultiblock()
                ? Component.translatable("gui.gtcalcboard.inspector.multiblock").getString()
                : Component.translatable("gui.gtcalcboard.inspector.singleblock").getString();
        graphics.drawString(font, subText, x + 6, y + 20, 0xFF94A3B8, false);
        graphics.drawString(font, "»", x + w - 12, y + 12, 0xFF64748B, false);
    }

    @Override
    public boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button) {
        if (button != 0 || node == null) return false;

        if (mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 36) {
            if (widget != null) widget.commitCountEdit();
            if (screen != null) {
                screen.openMachineConfigDialog(node);
            }
            return true;
        }
        return false;
    }
}
