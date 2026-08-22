package com.gtceu.calcboard.compat.thermal;

import com.gtceu.calcboard.api.PowerDisplayMode;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.client.gui.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles UI rendering and tooltip generation for Thermal machine and dynamo nodes.
 */
public class ThermalGuiHandler {

    public static String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        double rfRate = node.getEffectiveTotalEUt();
        return node.isGenerator()
                ? String.format("§a+%,.0f RF/t", rfRate)
                : String.format("§e%,.0f RF/t", rfRate);
    }

    public static List<Component> buildEnergyTooltip(RecipeNode node) {
        List<Component> tooltipLines = new ArrayList<>();
        double totPower = node.getEffectiveTotalEUt();
        if (node.isGenerator()) {
            tooltipLines.add(Component.literal("§a⚡ " + Component.translatable("gui.gtcalcboard.dynamo_badge").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Generation: §a+%,.2f RF/t §7(§a+%,.2f EU/t eq§7)", totPower, totPower / 4.0)));
        } else {
            tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Consumption: §c%,.2f RF/t §7(§c%,.2f EU/t eq§7)", totPower, totPower / 4.0)));
        }
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        return tooltipLines;
    }

    public static void renderCardControls(GuiGraphics graphics, Font font,
                                          RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                          boolean isGlowing) {
        String badge = node.isGenerator()
                ? Component.translatable("gui.gtcalcboard.dynamo_badge").getString()
                : "⚡ Thermal";
        int badgeW = Math.max(38, font.width(badge) + 6);
        NodeCardRenderer.drawBtn(graphics, font, badge, x + 6, row2Y, badgeW, 14, mouseX, mouseY, 0xFFFF5555);

        int nextCtrlX = x + 6 + badgeW + 3;
        String parLabel = "⚙ " + node.getTotalParallel() + "x";
        if (!node.getAddons().isEmpty()) {
            parLabel += " (+" + node.getAddons().size() + ")";
        }
        int parW = Math.max(46, (x + cardW - 6) - nextCtrlX);
        NodeCardRenderer.drawBtn(graphics, font, parLabel, nextCtrlX, row2Y, parW, 14, mouseX, mouseY, !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF, isGlowing);
    }

    public static boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        String badge = node.isGenerator()
                ? Component.translatable("gui.gtcalcboard.dynamo_badge").getString()
                : "⚡ Thermal";
        int badgeW = Math.max(38, Minecraft.getInstance().font.width(badge) + 6);
        int configStartX = x + 6 + badgeW + 3;
        return mouseX >= configStartX && mouseX <= x + node.getCardWidth() - 6 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    public static boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (isMachineConfigHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            if (widget.getParent() != null) widget.getParent().openMachineConfigDialog(node);
            return true;
        }
        return false;
    }
}
