package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.api.OverclockMode;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.client.gui.NodeCardRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles UI rendering and tooltip generation for GTCEu machine nodes.
 */
public class GTCEuGuiHandler {

    public static void renderCardControls(GuiGraphics graphics, Font font,
                                          RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                          boolean isGlowing) {
        boolean isOperational = node.isOperational();
        GTVoltageTier tier = node.getTargetTier();
        int tierColor = !isOperational ? 0xFFFF8888 : tier.getColor();
        NodeCardRenderer.drawBtn(graphics, font, tier.getName(), x + 6, row2Y, 32, 14, mouseX, mouseY, tierColor, !isOperational, false);

        int nextCtrlX = x + 42;

        // 1. Render declarative node badges (Fusion, Cleanroom, etc.) via NodeBadgeRegistry
        List<com.gtceu.calcboard.api.property.NodeBadge> badges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(node);
        for (com.gtceu.calcboard.api.property.NodeBadge badge : badges) {
            int badgeW = font.width(badge.text()) + 8;
            if (nextCtrlX + badgeW > x + cardW - 46) {
                break;
            }
            int badgeColor = (!isOperational && !badge.isWarning()) ? 0xFFFF8888 : badge.outlineColor();
            NodeCardRenderer.drawBtn(graphics, font, badge.text(), nextCtrlX, row2Y, badgeW, 14, mouseX, mouseY, badgeColor, !isOperational || badge.isWarning(), false);
            nextCtrlX += badgeW + 3;
        }

        // 2. Render standard GT Generator / Overclock / Rotor controls if room permits
        if (node.isGenerator()) {
            if (!node.isFusion()) {
                String genBadge = Component.translatable("gui.gtcalcboard.gen_badge").getString();
                int genW = Math.max(28, font.width(genBadge) + 4);
                if (nextCtrlX + genW <= x + cardW - 46) {
                    int genColor = !isOperational ? 0xFFFF8888 : 0xFF55FF88;
                    NodeCardRenderer.drawBtn(graphics, font, genBadge, nextCtrlX, row2Y, genW, 14, mouseX, mouseY, genColor, !isOperational, false);
                    nextCtrlX += genW + 3;
                }
            }

            if (node.isLargeTurbine()) {
                int activeEff = node.getRotorEfficiency();
                for (MachineAddon a : node.getAddons()) {
                    if (a.getCategory() == MachineAddon.Category.ROTOR) {
                        activeEff = (int) Math.round(a.getDurationMultiplier() * 100.0);
                        break;
                    }
                }
                String rotorText = "⚙ " + activeEff + "%";
                if (!isOperational) {
                    rotorText = "⚙ ⚠ " + activeEff + "%";
                } else if (node.getTotalParallel() > 1) {
                    rotorText = "⚙ " + node.getTotalParallel() + "x (" + activeEff + "%)";
                } else {
                    long nonRotorAddons = node.getAddons().stream().filter(a -> a.getCategory() != MachineAddon.Category.ROTOR).count();
                    if (nonRotorAddons > 0) {
                        rotorText += " (+" + nonRotorAddons + ")";
                    }
                }
                int rotorW = Math.max(46, (x + cardW - 6) - nextCtrlX);
                int rotorColor = !isOperational ? 0xFFFF4444 : 0xFFFFAA00;
                NodeCardRenderer.drawBtn(graphics, font, rotorText, nextCtrlX, row2Y, rotorW, 14, mouseX, mouseY, rotorColor, !isOperational, isGlowing);
            } else {
                String dynamoPar = "⚙ " + node.getParallel() + "x";
                if (!node.getAddons().isEmpty()) {
                    dynamoPar += " (+" + node.getAddons().size() + ")";
                }
                int parW = Math.max(46, (x + cardW - 6) - nextCtrlX);
                int configColor = !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF;
                NodeCardRenderer.drawBtn(graphics, font, dynamoPar, nextCtrlX, row2Y, parW, 14, mouseX, mouseY, configColor, !isOperational, isGlowing);
            }
        } else {
            if (!node.isFusion()) {
                String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
                String ocText = Component.translatable(ocKey).getString();
                int ocColor = !isOperational ? 0xFFFF8888 : (node.getOverclockMode() == OverclockMode.PERFECT ? 0xFF55FF55 : 0xFFAAAAAA);
                int ocW = Math.max(50, font.width(ocText) + 6);
                if (nextCtrlX + ocW <= x + cardW - 46) {
                    NodeCardRenderer.drawBtn(graphics, font, ocText, nextCtrlX, row2Y, ocW, 14, mouseX, mouseY, ocColor, !isOperational, false);
                    nextCtrlX += ocW + 3;
                }
            }

            String parLabel = "⚙ " + node.getTotalParallel() + "x";
            if (!node.getAddons().isEmpty()) {
                parLabel += " (+" + node.getAddons().size() + ")";
            }
            int parW = Math.max(46, (x + cardW - 6) - nextCtrlX);
            int configColor = !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF;
            NodeCardRenderer.drawBtn(graphics, font, parLabel, nextCtrlX, row2Y, parW, 14, mouseX, mouseY, configColor, !isOperational, isGlowing);
        }
    }

    public static List<Component> buildEnergyTooltip(RecipeNode node) {
        List<Component> tooltipLines = new ArrayList<>();
        if (node.isFusion()) {
            int fTier = node.getFusionTier();
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§d⚛ Fusion Reactor Mk%d", fTier)));
            long startEU = node.getEuToStart();
            if (startEU > 0) {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Start Ignition Energy: §e%,d EU §7(§f%s EU§7)", startEU, com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(startEU))));
            }
            double totEUt = node.getEffectiveTotalEUt();
            var tier = node.getTargetTier();
            if (tier == null) tier = node.getMinFusionVoltageTier();
            double amps = totEUt / (double) tier.getVoltage();
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Running Power: §c%,.2f EU/t", totEUt)));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Current: §e%,.4fA %s", amps, tier.getName())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Minimum Voltage Tier: §f%s", node.getMinFusionVoltageTier().getName())));
            return tooltipLines;
        }

        if (node.isGenerator()) {
            tooltipLines.add(Component.literal("§a⚡ " + Component.translatable("gui.gtcalcboard.total_gen").getString()));
            double totEUt = node.getEffectiveTotalEUt();
            var tier = node.getTargetTier();
            if (tier == null) tier = GTVoltageTier.LV;
            double amps = totEUt / (double) tier.getVoltage();
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Generation: §a+%,.2f EU/t", totEUt)));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Current: §a+%,.4fA %s", amps, tier.getName())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            if (node.getEfficiency() < 0.999) {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§e⚡ Rotor Efficiency: §f%.1f%%", node.getEfficiency() * 100.0)));
            }
        } else {
            tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
            double totEUt = node.getEffectiveTotalEUt();
            var tier = node.getTargetTier();
            if (tier == null) tier = GTVoltageTier.LV;
            double amps = totEUt / (double) tier.getVoltage();
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Consumption: §c%,.2f EU/t", totEUt)));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Current: §e%,.4fA %s", amps, tier.getName())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        }
        return tooltipLines;
    }
}
