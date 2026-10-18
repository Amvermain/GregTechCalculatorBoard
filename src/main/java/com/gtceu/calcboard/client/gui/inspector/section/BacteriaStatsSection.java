package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.dialog.config.BacteriaStatsConfigView;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.start.StarTBacterialProperties;
import com.gtceu.calcboard.compat.start.projection.StarTPortProjector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * Declarative node inspector section for configuring Bacterial Hydrocarbon Harvester colony stats directly from the side panel.
 */
public class BacteriaStatsSection implements IInspectorSection {

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
        return StarTPortProjector.isBacterialHarvester(node);
    }

    @Override
    public int getHeight(RecipeNode node) {
        return 74;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        if (node == null) return;

        String title = "✦ " + Component.translatable("gui.gtcalcboard.bacteria_stats.title").getString();
        graphics.drawString(font, "§d" + title, x, y, 0xFFFFFFFF, false);

        int prod = StarTBacterialProperties.getProduction(node);
        int meta = StarTBacterialProperties.getMetabolism(node);

        int prodY = y + 12;
        renderStatRow(graphics, font, "P", prod, true, x, prodY, w, mouseX, mouseY);

        int metaY = prodY + 18;
        renderStatRow(graphics, font, "M", meta, false, x, metaY, w, mouseX, mouseY);

        int presetY = metaY + 18;
        renderPresetButtons(graphics, font, x, presetY, w, mouseX, mouseY);
    }

    private void renderStatRow(GuiGraphics graphics, Font font, String label, int val, boolean highIsGood,
                               int x, int y, int w, int mouseX, int mouseY) {
        graphics.drawString(font, "§7" + label + ":", x, y + 2, 0xFFFFFFFF, false);

        int btnMinusX = x + 16;
        renderMiniButton(graphics, font, "-", btnMinusX, y, 14, 12, mouseX, mouseY);

        String valStr = "§e" + val;
        int valX = btnMinusX + 16;
        graphics.drawString(font, valStr, valX, y + 2, 0xFFFFFFFF, false);

        int btnPlusX = valX + 8;
        renderMiniButton(graphics, font, "+", btnPlusX, y, 14, 12, mouseX, mouseY);

        String bar = BacteriaStatsConfigView.formatStatBar(val, highIsGood);
        int barX = btnPlusX + 18;
        graphics.drawString(font, bar, barX, y + 2, 0xFFFFFFFF, false);
    }

    private void renderPresetButtons(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        int gap = 3;
        int btnW = (w - gap * 2) / 3;

        renderPresetChip(graphics, font, "★ 5/1", x, y, btnW, 14, mouseX, mouseY);
        renderPresetChip(graphics, font, "5/5", x + btnW + gap, y, btnW, 14, mouseX, mouseY);
        renderPresetChip(graphics, font, "1/5", x + (btnW + gap) * 2, y, btnW, 14, mouseX, mouseY);
    }

    private void renderPresetChip(GuiGraphics graphics, Font font, String label,
                                  int bx, int by, int bw, int bh, int mouseX, int mouseY) {
        boolean hov = isMouseOver(mouseX, mouseY, bx, by, bw, bh);
        graphics.fill(bx, by, bx + bw, by + bh, hov ? 0xFF35445E : 0xFF1C273A);
        graphics.renderOutline(bx, by, bw, bh, hov ? 0xFF5890FF : 0xFF2A3448);
        graphics.drawCenteredString(font, label, bx + bw / 2, by + (bh - 8) / 2, 0xFFE2E8F0);
    }

    private void renderMiniButton(GuiGraphics graphics, Font font, String text,
                                  int bx, int by, int bw, int bh, int mouseX, int mouseY) {
        boolean hov = isMouseOver(mouseX, mouseY, bx, by, bw, bh);
        graphics.fill(bx, by, bx + bw, by + bh, hov ? 0xFF35445E : 0xFF1E293B);
        graphics.renderOutline(bx, by, bw, bh, hov ? 0xFF5890FF : 0xFF334155);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, 0xFFFFFFFF);
    }

    @Override
    public boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button) {
        if (button != 0 || node == null) return false;

        int prodY = y + 12;
        int btnMinusX = x + 16;
        if (isMouseOver(mouseX, mouseY, btnMinusX, prodY, 14, 12)) {
            changeStat(-1, 0);
            return true;
        }
        int btnPlusX = btnMinusX + 16 + 8;
        if (isMouseOver(mouseX, mouseY, btnPlusX, prodY, 14, 12)) {
            changeStat(1, 0);
            return true;
        }

        int metaY = prodY + 18;
        if (isMouseOver(mouseX, mouseY, btnMinusX, metaY, 14, 12)) {
            changeStat(0, -1);
            return true;
        }
        if (isMouseOver(mouseX, mouseY, btnPlusX, metaY, 14, 12)) {
            changeStat(0, 1);
            return true;
        }

        int presetY = metaY + 18;
        int gap = 3;
        int btnW = (w - gap * 2) / 3;

        if (isMouseOver(mouseX, mouseY, x, presetY, btnW, 14)) {
            applyPreset(5, 1);
            return true;
        }
        if (isMouseOver(mouseX, mouseY, x + btnW + gap, presetY, btnW, 14)) {
            applyPreset(5, 5);
            return true;
        }
        if (isMouseOver(mouseX, mouseY, x + (btnW + gap) * 2, presetY, btnW, 14)) {
            applyPreset(1, 5);
            return true;
        }

        return false;
    }

    private void changeStat(int prodDelta, int metaDelta) {
        playClickSound();
        int p = StarTBacterialProperties.getProduction(node) + prodDelta;
        int m = StarTBacterialProperties.getMetabolism(node) + metaDelta;
        StarTBacterialProperties.setProduction(node, p);
        StarTBacterialProperties.setMetabolism(node, m);
        node.syncProjectedPorts();
        if (widget != null) widget.invalidateCache();
        if (screen != null) screen.markSummaryDirty();
    }

    private void applyPreset(int prod, int meta) {
        playClickSound();
        StarTBacterialProperties.setProduction(node, prod);
        StarTBacterialProperties.setMetabolism(node, meta);
        node.syncProjectedPorts();
        if (widget != null) widget.invalidateCache();
        if (screen != null) screen.markSummaryDirty();
    }

    private static void playClickSound() {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
    }

    private static boolean isMouseOver(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
    }
}
