package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.start.StarTHellForgeHelper;
import com.gtceu.calcboard.compat.start.StarTHellForgeHelper.HeatingFluid;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

/**
 * Declarative node inspector section for configuring Star Technology Hellforge crucible heating fluid and infernal parallels.
 */
public class HellForgeHeatSection implements IInspectorSection {

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
        return StarTHellForgeHelper.isHellForge(node);
    }

    @Override
    public int getHeight(RecipeNode node) {
        return 54;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        if (node == null) return;
        StarTHellForgeHelper.ensureDefaultHeat(node);

        String title = "🔥 " + Component.translatable("gui.gtcalcboard.hellforge.crucible_heat").getString();
        graphics.drawString(font, "§6" + title, x, y, 0xFFFFFFFF, false);

        int curTemp = StarTHellForgeHelper.getOperatingTemperature(node);
        int reqTemp = StarTHellForgeHelper.getRequiredTemperature(node);
        HeatingFluid fluid = StarTHellForgeHelper.getSelectedFluid(node);
        boolean isDeficit = reqTemp > 0 && curTemp < reqTemp;

        int rowY = y + 12;
        renderMiniButton(graphics, font, "-", x, rowY, 16, 14, mouseX, mouseY);

        int valBtnX = x + 19;
        int valBtnW = 68;
        String tempLabel = String.format(Locale.ROOT, "%,d MK", curTemp);
        renderValueChip(graphics, font, tempLabel, valBtnX, rowY, valBtnW, 14, isDeficit, mouseX, mouseY);

        int plusBtnX = valBtnX + valBtnW + 3;
        renderMiniButton(graphics, font, "+", plusBtnX, rowY, 16, 14, mouseX, mouseY);

        int statusX = plusBtnX + 20;
        String statusStr = isDeficit
                ? String.format(Locale.ROOT, "§c⚠ <%,d", reqTemp)
                : String.format(Locale.ROOT, "§a⚡ %dx", StarTHellForgeHelper.computeInfernalParallel(node));
        graphics.drawString(font, statusStr, statusX, rowY + 3, 0xFFFFFFFF, false);

        String fluidLine = "§6" + fluid.formulaSymbol() + " §e" + fluid.getDisplayName();
        graphics.drawString(font, font.plainSubstrByWidth(fluidLine, w), x, y + 30, 0xFFFFFFFF, false);
    }

    private void renderMiniButton(GuiGraphics graphics, Font font, String text,
                                  int bx, int by, int bw, int bh, int mouseX, int mouseY) {
        boolean hov = isMouseOver(mouseX, mouseY, bx, by, bw, bh);
        graphics.fill(bx, by, bx + bw, by + bh, hov ? 0xFF35445E : 0xFF1E293B);
        graphics.renderOutline(bx, by, bw, bh, hov ? 0xFF5890FF : 0xFF334155);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, 0xFFFFFFFF);
    }

    private void renderValueChip(GuiGraphics graphics, Font font, String text,
                                 int bx, int by, int bw, int bh, boolean isDeficit, int mouseX, int mouseY) {
        boolean hov = isMouseOver(mouseX, mouseY, bx, by, bw, bh);
        int bg = isDeficit ? (hov ? 0xFF4A1E24 : 0xFF3A1C22) : (hov ? 0xFF4A2E1A : 0xFF3A2416);
        int border = isDeficit ? 0xFFFF5555 : (hov ? 0xFFFFAA44 : 0xFFFF8833);
        int textCol = isDeficit ? 0xFFFF8888 : 0xFFFFAA44;
        graphics.fill(bx, by, bx + bw, by + bh, bg);
        graphics.renderOutline(bx, by, bw, bh, border);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, textCol);
    }

    @Override
    public boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button) {
        if (button != 0 || node == null) return false;

        int rowY = y + 12;
        if (isMouseOver(mouseX, mouseY, x, rowY, 16, 14)) {
            stepTemperature(-1);
            return true;
        }
        int valBtnX = x + 19;
        int valBtnW = 68;
        if (isMouseOver(mouseX, mouseY, valBtnX, rowY, valBtnW, 14)) {
            resetToRequiredMinimum();
            return true;
        }
        int plusBtnX = valBtnX + valBtnW + 3;
        if (isMouseOver(mouseX, mouseY, plusBtnX, rowY, 16, 14)) {
            stepTemperature(1);
            return true;
        }
        return false;
    }

    private void stepTemperature(int delta) {
        playClickSound();
        StarTHellForgeHelper.cycleTemperature(node, delta);
        notifyChanged();
    }

    private void resetToRequiredMinimum() {
        playClickSound();
        int req = StarTHellForgeHelper.getRequiredTemperature(node);
        StarTHellForgeHelper.setOperatingTemperature(node, StarTHellForgeHelper.getFluidForTemperature(req).maxTempMK());
        notifyChanged();
    }

    private void notifyChanged() {
        if (widget != null) widget.invalidateCache();
        if (screen == null) return;
        if (node != null && node.isCompoundNode() && screen.getGraph() != null) {
            screen.getGraph().syncCompoundParameters(node);
            screen.rebuildBoardWidgets();
        }
        screen.markSummaryDirty();
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
