package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.Locale;

public class SteamModeSection implements IInspectorSection {

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
        return isSteamNode(node);
    }

    public static boolean isSteamNode(RecipeNode node) {
        if (node == null || BoilerThrottleSection.isBoiler(node)) return false;
        if (node.isMultiblock()) {
            return MultiblockDetector.isSteamMultiblock(node);
        }
        return node.getSteamMode() != null && node.getSteamMode().isSteam();
    }

    @Override
    public int getHeight(RecipeNode node) {
        return 52;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        if (node == null) return;

        String headerLabel = "♨ " + Component.translatable("gui.gtcalcboard.inspector.steam_mode").getString();
        graphics.drawString(font, headerLabel, x, y, 0xFF94A3B8, false);

        int btnY = y + 12;
        int btnW = (w - 4) / 2;
        SteamMode curMode = (node.getSteamMode() != null && node.getSteamMode().isSteam()) ? node.getSteamMode() : SteamMode.HIGH_PRESSURE;

        renderSteamButton(graphics, font, x, btnY, btnW, SteamMode.LOW_PRESSURE, curMode == SteamMode.LOW_PRESSURE, mouseX, mouseY);
        renderSteamButton(graphics, font, x + btnW + 4, btnY, btnW, SteamMode.HIGH_PRESSURE, curMode == SteamMode.HIGH_PRESSURE, mouseX, mouseY);

        int infoY = btnY + 22;
        renderSteamRateInfo(graphics, font, x, infoY, w, curMode);
    }

    private void renderSteamButton(GuiGraphics graphics, Font font, int x, int y, int w,
                                   SteamMode mode, boolean isActive, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 18;
        int bg = isActive ? 0xFF0284C7 : (hov ? 0xFF334155 : 0xFF1E293B);
        int border = isActive ? 0xFF38BDF8 : (hov ? 0xFF64748B : 0xFF334155);
        int textColor = isActive ? 0xFFFFFFFF : 0xFF94A3B8;

        graphics.fill(x, y, x + w, y + 18, bg);
        graphics.renderOutline(x, y, w, 18, border);
        graphics.drawCenteredString(font, mode.getDisplayName(), x + w / 2, y + 5, textColor);
    }

    private void renderSteamRateInfo(GuiGraphics graphics, Font font, int x, int y, int w, SteamMode mode) {
        graphics.fill(x, y, x + w, y + 18, 0xFF0F172A);
        graphics.renderOutline(x, y, w, 18, 0xFF334155);

        double ratePerTick = computeSteamRate(node, mode);
        String tickStr = String.format(Locale.ROOT, "%.1f", ratePerTick);
        String secStr = String.format(Locale.ROOT, "%,.0f", ratePerTick * 20.0);
        String flowText = Component.translatable("gui.gtcalcboard.inspector.steam_flow", tickStr, secStr).getString();
        graphics.drawCenteredString(font, flowText, x + w / 2, y + 5, 0xFFFCD34D);
    }

    private double computeSteamRate(RecipeNode node, SteamMode mode) {
        if (node.isMultiblock() || MultiblockDetector.isSteamMultiblock(node.getMachineIcon())) {
            return MultiblockDetector.getSteamMultiblockConsumption(node.getMachineIcon(), mode);
        }
        double baseEu = node.getBaseEUt() > 0 ? node.getBaseEUt() : 4.0;
        return baseEu * 2.0;
    }

    @Override
    public boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button) {
        if (button != 0 || node == null) return false;

        int btnY = y + 12;
        int btnW = (w - 4) / 2;

        if (mouseX >= x && mouseX <= x + btnW && mouseY >= btnY && mouseY <= btnY + 18) {
            applySteamMode(SteamMode.LOW_PRESSURE);
            return true;
        }

        int hpX = x + btnW + 4;
        if (mouseX >= hpX && mouseX <= hpX + btnW && mouseY >= btnY && mouseY <= btnY + 18) {
            applySteamMode(SteamMode.HIGH_PRESSURE);
            return true;
        }

        return false;
    }

    private void applySteamMode(SteamMode mode) {
        node.setSteamMode(mode);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        if (widget != null) widget.invalidateCache();
        if (screen != null) screen.markSummaryDirty();
    }
}
