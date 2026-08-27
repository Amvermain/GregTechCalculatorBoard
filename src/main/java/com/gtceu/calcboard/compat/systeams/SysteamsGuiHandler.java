package com.gtceu.calcboard.compat.systeams;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles UI rendering and tooltip generation for Systeams boiler and steam dynamo nodes.
 */
public class SysteamsGuiHandler {

    public static String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        if (node.isGenerator() || node.getEnergyType() == EnergyType.ELECTRIC_FE) {
            double rfRate = node.getEffectiveTotalEUt();
            return node.isGenerator()
                    ? String.format("§a+%,.0f RF/t", rfRate)
                    : String.format("§e%,.0f RF/t", rfRate);
        }
        double steamRate = 0.0;
        for (Map.Entry<IngredientStack, Double> entry : node.calculateEffectiveOutputRates().entrySet()) {
            if (entry.getKey().isFluid() && entry.getKey().getId() != null && entry.getKey().getId().getPath().contains("steam")) {
                steamRate += entry.getValue();
            }
        }
        if (steamRate > 0) {
            return String.format("§b♨ +%s/s Steam", FormatUtil.formatCompactNumber(steamRate));
        }
        return "§b" + Component.translatable("gui.gtcalcboard.boiler_badge").getString();
    }

    public static List<Component> buildEnergyTooltip(RecipeNode node) {
        if (node.isGenerator() || node.getEnergyType() == EnergyType.ELECTRIC_FE) {
            List<Component> tooltipLines = new ArrayList<>();
            double totPower = node.getEffectiveTotalEUt();
            tooltipLines.add(Component.literal("§a⚡ " + Component.translatable("gui.gtcalcboard.dynamo_badge").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Generation: §a+%,.2f RF/t §7(§a+%,.2f EU/t eq§7)", totPower, totPower / 4.0)));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            return tooltipLines;
        }
        List<Component> tooltipLines = new ArrayList<>();
        tooltipLines.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.boiler_badge").getString()));
        double steamRate = 0.0;
        for (Map.Entry<IngredientStack, Double> entry : node.calculateEffectiveOutputRates().entrySet()) {
            if (entry.getKey().isFluid() && entry.getKey().getId() != null && entry.getKey().getId().getPath().contains("steam")) {
                steamRate += entry.getValue();
            }
        }
        if (steamRate > 0) {
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Steam: §b+%,.2f mB/s §7(§b+%,.2f mB/t§7)", steamRate, steamRate / 20.0)));
        }
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        return tooltipLines;
    }

    public static void renderCardControls(GuiGraphics graphics, Font font,
                                          RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                          boolean isGlowing) {
        boolean isGenOrFE = node.isGenerator() || node.getEnergyType() == EnergyType.ELECTRIC_FE;
        String badge = isGenOrFE
                ? Component.translatable("gui.gtcalcboard.dynamo_badge").getString()
                : Component.translatable("gui.gtcalcboard.boiler_badge").getString();
        int badgeColor = isGenOrFE ? 0xFFFF5555 : 0xFFFFAA33;
        int badgeW = Math.max(38, font.width(badge) + 6);
        NodeCardRenderer.drawBtn(graphics, font, badge, x + 6, row2Y, badgeW, 14, mouseX, mouseY, badgeColor);

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
        boolean isGenOrFE = node.isGenerator() || node.getEnergyType() == EnergyType.ELECTRIC_FE;
        String badge = isGenOrFE
                ? Component.translatable("gui.gtcalcboard.dynamo_badge").getString()
                : Component.translatable("gui.gtcalcboard.boiler_badge").getString();
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

