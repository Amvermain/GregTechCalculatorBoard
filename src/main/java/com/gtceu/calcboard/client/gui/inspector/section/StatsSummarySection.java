package com.gtceu.calcboard.client.gui.inspector.section;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.client.gui.api.IBoardScreenContext;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Locale;

public class StatsSummarySection implements IInspectorSection {

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
        return node != null;
    }

    @Override
    public int getHeight(RecipeNode node) {
        return 56;
    }

    @Override
    public void render(GuiGraphics graphics, Font font, int x, int y, int w, int mouseX, int mouseY) {
        if (node == null) return;

        graphics.fill(x, y, x + w, y + 48, 0xFF0B1120);
        graphics.renderOutline(x, y, w, 48, 0xFF1E293B);

        renderSinglePower(graphics, font, x, y, w);
        renderTotalPower(graphics, font, x, y, w);
        renderDuration(graphics, font, x, y, w);
    }

    private void renderSinglePower(GuiGraphics graphics, Font font, int x, int y, int w) {
        double power = node.getSingleMachineEUt();
        String powerStr = formatPowerValue(node, power);
        int powerCol = power > 0 ? 0xFF10B981 : (power < 0 ? 0xFFF59E0B : 0xFF94A3B8);

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.single_power").getString(), x + 6, y + 6, 0xFF64748B, false);
        graphics.drawString(font, powerStr, x + w - font.width(powerStr) - 6, y + 6, powerCol, false);
    }

    private void renderTotalPower(GuiGraphics graphics, Font font, int x, int y, int w) {
        double totalPower = node.getTotalEUt();
        String totalStr = formatPowerValue(node, totalPower);
        int powerCol = totalPower > 0 ? 0xFF10B981 : (totalPower < 0 ? 0xFFF59E0B : 0xFF94A3B8);

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.total_power").getString(), x + 6, y + 20, 0xFF64748B, false);
        graphics.drawString(font, totalStr, x + w - font.width(totalStr) - 6, y + 20, powerCol, false);
    }

    private void renderDuration(GuiGraphics graphics, Font font, int x, int y, int w) {
        double duration = node.getEffectiveDurationSeconds();
        String durStr = String.format(Locale.ROOT, "%.2f s", duration);

        graphics.drawString(font, Component.translatable("gui.gtcalcboard.inspector.duration").getString(), x + 6, y + 34, 0xFF64748B, false);
        graphics.drawString(font, durStr, x + w - font.width(durStr) - 6, y + 34, 0xFFCBD5E1, false);
    }

    private String formatPowerValue(RecipeNode node, double power) {
        EnergyType type = node.getEnergyType();
        if (type == EnergyType.NONE) {
            return Component.translatable("gui.gtcalcboard.energy_passive_stat").getString();
        }
        String unit = switch (type) {
            case KINETIC_SU -> "SU";
            case ELECTRIC_FE -> "FE/t";
            default -> "EU/t";
        };
        if (power > 0 || node.isGenerator()) {
            return String.format(Locale.ROOT, "+%,.0f %s", Math.abs(power), unit);
        }
        return String.format(Locale.ROOT, "%,.0f %s", power, unit);
    }

    @Override
    public boolean mouseClicked(int x, int y, int w, double mouseX, double mouseY, int button) {
        return false;
    }
}
