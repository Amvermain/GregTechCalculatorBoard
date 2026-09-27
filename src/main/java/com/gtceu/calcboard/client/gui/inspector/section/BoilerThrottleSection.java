package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeTierChangeHandler;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.tfg.TFGBoilerPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class BoilerThrottleSection implements IInspectorSection {

    private static final int[] PRESETS = new int[]{25, 50, 75, 100};

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
        return isBoiler(node);
    }

    public static boolean isBoiler(RecipeNode node) {
        if (node == null) return false;
        if (node.isLiquidBoilerRecipe()) return true;
        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        return adapter != null && adapter.isBoilerRecipe(node);
    }

    @Override
    public int getHeight(RecipeNode node) {
        return 68;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        if (node == null) return;

        String headerLabel = "♨ " + Component.translatable("gui.gtcalcboard.inspector.boiler_control").getString();
        graphics.drawString(font, headerLabel, x, y, 0xFF94A3B8, false);

        int tierY = y + 12;
        renderTierButton(graphics, font, x, tierY, w, mouseX, mouseY);

        int throttleLabelY = tierY + 24;
        String throttleLabel = Component.translatable("gui.gtcalcboard.inspector.boiler_throttle", node.getBoilerThrottle()).getString();
        graphics.drawString(font, throttleLabel, x, throttleLabelY, 0xFF94A3B8, false);

        int presetY = throttleLabelY + 12;
        renderThrottlePresets(graphics, font, x, presetY, w, mouseX, mouseY);
    }

    private void renderTierButton(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        boolean hov = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + 20;
        graphics.fill(x, y, x + w, y + 20, hov ? 0xFF1E293B : 0xFF0F172A);
        graphics.renderOutline(x, y, w, 20, hov ? 0xFF38BDF8 : 0xFF334155);

        GTBoilerTier boilerTier = GTBoilerTier.getBoilerTier(node);
        String tierName = boilerTier.getDisplayName();
        graphics.drawString(font, tierName, x + 6, y + 6, 0xFFE2E8F0, false);
        graphics.drawString(font, "»", x + w - 12, y + 6, 0xFF64748B, false);
    }

    private void renderThrottlePresets(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        int cur = node.getBoilerThrottle();
        int cols = 4;
        int gap = 4;
        int chipW = (w - gap * (cols - 1)) / cols;

        for (int i = 0; i < cols; i++) {
            int preset = PRESETS[i];
            int cx = x + i * (chipW + gap);
            boolean isCur = (preset == cur);
            boolean hov = mouseX >= cx && mouseX <= cx + chipW && mouseY >= y && mouseY <= y + 16;

            int bg = isCur ? 0xFF0284C7 : (hov ? 0xFF334155 : 0xFF1E293B);
            int border = isCur ? 0xFF38BDF8 : (hov ? 0xFF64748B : 0xFF334155);
            int textColor = isCur ? 0xFFFFFFFF : 0xFF94A3B8;

            graphics.fill(cx, y, cx + chipW, y + 16, bg);
            graphics.renderOutline(cx, y, chipW, 16, border);
            graphics.drawCenteredString(font, preset + "%", cx + chipW / 2, y + 4, textColor);
        }
    }

    @Override
    public boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button) {
        if (button != 0 || node == null) return false;

        int tierY = y + 12;
        if (mouseX >= x && mouseX <= x + w && mouseY >= tierY && mouseY <= tierY + 20) {
            NodeTierChangeHandler.changeTier(widget, node, screen, 1);
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
            }
            return true;
        }

        int presetY = tierY + 24 + 12;
        int cols = 4;
        int gap = 4;
        int chipW = (w - gap * (cols - 1)) / cols;

        for (int i = 0; i < cols; i++) {
            int preset = PRESETS[i];
            int cx = x + i * (chipW + gap);
            if (mouseX >= cx && mouseX <= cx + chipW && mouseY >= presetY && mouseY <= presetY + 16) {
                applyThrottle(preset);
                return true;
            }
        }
        return false;
    }

    private void applyThrottle(int throttle) {
        node.setBoilerThrottle(throttle);
        TFGBoilerPhysics.syncDynamicPorts(node);
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        }
        if (widget != null) widget.invalidateCache();
        if (screen != null) screen.markSummaryDirty();
    }
}
