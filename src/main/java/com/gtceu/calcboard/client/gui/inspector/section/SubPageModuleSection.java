package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class SubPageModuleSection implements IInspectorSection {

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
        return node != null && node.isModule();
    }

    @Override
    public int getHeight(RecipeNode node) {
        return 86;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        if (node == null) return;

        String headerLabel = Component.translatable("gui.gtcalcboard.inspector.subpage_module").getString();
        graphics.drawString(font, headerLabel, x, y, 0xFF94A3B8, false);

        int targetY = y + 12;
        renderSubPageTarget(graphics, font, x, targetY, w);

        int btnY = targetY + 24;
        renderOpenSubPageButton(graphics, font, x, btnY, w, mouseX, mouseY);

        int pinY = btnY + 26;
        renderPinSummary(graphics, font, x, pinY, w);
    }

    private void renderSubPageTarget(GuiGraphics graphics, Font font, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 20, 0xFF0F172A);
        graphics.renderOutline(x, y, w, 20, 0xFF334155);

        var subPage = node.getDedicatedSubPage();
        String path = subPage != null ? subPage.getName() : (node.getSubPageId().isEmpty() ? "/" : node.getSubPageId());
        graphics.drawString(font, "📁 " + font.plainSubstrByWidth(path, w - 20), x + 6, y + 6, 0xFFE2E8F0, false);
    }

    private void renderOpenSubPageButton(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 22;
        graphics.fill(x, y, x + w, y + 22, hov ? 0xFF0369A1 : 0xFF0C4A6E);
        graphics.renderOutline(x, y, w, 22, hov ? 0xFF38BDF8 : 0xFF0284C7);

        String btnLabel = Component.translatable("gui.gtcalcboard.inspector.open_subpage").getString();
        graphics.drawCenteredString(font, btnLabel, x + w / 2, y + 7, 0xFFFFFFFF);
    }

    private void renderPinSummary(GuiGraphics graphics, Font font, int x, int y, int w) {
        graphics.fill(x, y, x + w, y + 20, 0xFF0B1120);
        graphics.renderOutline(x, y, w, 20, 0xFF1E293B);

        int inCount = node.getInputPinNodeIds().size();
        int outCount = node.getOutputPinNodeIds().size();
        String pinsLabel = Component.translatable("gui.gtcalcboard.inspector.module_pins", inCount, outCount).getString();
        graphics.drawCenteredString(font, pinsLabel, x + w / 2, y + 6, 0xFF94A3B8);
    }

    @Override
    public boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button) {
        if (button != 0 || node == null) return false;

        int btnY = y + 12 + 24;
        if (mouseX >= x && mouseX <= x + w && mouseY >= btnY && mouseY <= btnY + 22) {
            if (screen != null && !node.getSubPageId().isEmpty()) {
                screen.openModuleSubPage(node);
                Minecraft mc = Minecraft.getInstance();
                if (mc != null && mc.getSoundManager() != null) {
                    mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
            }
            return true;
        }
        return false;
    }
}
