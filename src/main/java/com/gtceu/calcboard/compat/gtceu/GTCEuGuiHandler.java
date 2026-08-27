package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.MachineAddon;
import com.gtceu.calcboard.api.MultiblockDetector;
import com.gtceu.calcboard.api.OverclockMode;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.client.gui.NodeCardRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles UI rendering and tooltip generation for GTCEu machine nodes.
 */
public class GTCEuGuiHandler {

    private static int mbControllerScroll = 0;

    public static void resetControllerScroll() {
        mbControllerScroll = 0;
    }

    private static boolean isBoiler(RecipeNode node) {
        if (node == null) return false;
        if (node.isLiquidBoilerRecipe()) return true;
        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        return adapter != null && adapter.isBoilerRecipe(node);
    }

    public static void renderCardControls(GuiGraphics graphics, Font font,
                                          RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                          boolean isGlowing) {
        boolean isOperational = node.isOperational();
        int tierBtnW = 32;
        int nextCtrlX = x + 42;

        if (node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.NONE) {
            String bannerText = "🍃 " + Component.translatable("gui.gtcalcboard.energy_passive_banner").getString();
            int bannerW = cardW - 12;
            NodeCardRenderer.drawBtn(graphics, font, bannerText, x + 6, row2Y, bannerW, 14, mouseX, mouseY, 0xFF88D49E, false, false);
            return;
        } else if (isBoiler(node)) {
            com.gtceu.calcboard.api.GTBoilerTier boilerTier = com.gtceu.calcboard.api.GTBoilerTier.getBoilerTier(node);
            String boilerText = boilerTier.getDisplayName();
            if (boilerTier.isMultiblock() && node.getBoilerThrottle() < 100) {
                boilerText += " (" + node.getBoilerThrottle() + "%)";
            }
            tierBtnW = Math.max(54, font.width(boilerText) + 8);
            int boilerColor = !isOperational ? 0xFFFF8888 : boilerTier.getColor();
            NodeCardRenderer.drawBtn(graphics, font, boilerText, x + 6, row2Y, tierBtnW, 14, mouseX, mouseY, boilerColor, !isOperational, false);
            nextCtrlX = x + 6 + tierBtnW + 4;
        } else if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            String steamText = node.isMultiblock() ? ("🏛 " + node.getSteamMode().getShortName()) : node.getSteamMode().getDisplayName();
            tierBtnW = Math.max(48, font.width(steamText) + 8);
            int steamColor = !isOperational ? 0xFFFF8888 : node.getSteamMode().getColor();
            NodeCardRenderer.drawBtn(graphics, font, steamText, x + 6, row2Y, tierBtnW, 14, mouseX, mouseY, steamColor, !isOperational, false);
            nextCtrlX = x + 6 + tierBtnW + 4;
        } else {
            GTVoltageTier tier = node.getTargetTier();
            int tierColor = !isOperational ? 0xFFFF8888 : tier.getColor();
            NodeCardRenderer.drawBtn(graphics, font, tier.getName(), x + 6, row2Y, 32, 14, mouseX, mouseY, tierColor, !isOperational, false);
        }

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
            if (!node.isFusion() && node.getEnergyType() != com.gtceu.calcboard.api.EnergyType.HEAT_OR_SELF && (node.getSteamMode() == null || !node.getSteamMode().isSteam())) {
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
        if (node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.NONE) {
            tooltipLines.add(Component.literal("§7- " + Component.translatable("gui.gtcalcboard.energy_passive").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.2fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            return tooltipLines;
        }

        if (isBoiler(node)) {
            com.gtceu.calcboard.api.GTBoilerTier boilerTier = com.gtceu.calcboard.api.GTBoilerTier.getBoilerTier(node);
            tooltipLines.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.boiler_title").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Boiler Type: %s%s", boilerTier.getFormatCode(), boilerTier.getDisplayName())));
            if (boilerTier.isMultiblock()) {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Throttle: §b%d%%", node.getBoilerThrottle())));
            }
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Speed Multiplier: §e%.2fx", GTCEuModAdapter.getBoilerSpeedMultiplier(node))));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.2fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            return tooltipLines;
        }

        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            tooltipLines.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.steam_machine_title").getString()));
            double steamRate = node.getBaseEUt() * 2.0 * 20.0 * node.getMachineCount();
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Steam Consumption: §b♨ %,.1f L/s", steamRate)));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Operating Mode: %s%s", node.getSteamMode().getFormatCode(), node.getSteamMode().getDisplayName())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§8* Steam Ratio: 1 EU = 2 mB Steam (Speed: %s)", node.getSteamMode() == com.gtceu.calcboard.api.SteamMode.LOW_PRESSURE ? "0.5x" : "1.0x")));
            return tooltipLines;
        }

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
            if (node.getEfficiency() < 0.999) {
                tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§c⚠ %s: %.1f%%", Component.translatable("gui.gtcalcboard.tooltip.bottleneck_eff").getString(), node.getEfficiency() * 100.0)));
            }
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
            double singleEUt = node.getSingleMachineEUt();
            double totEUt = node.getEffectiveTotalEUt();
            var tier = node.getTargetTier();
            if (tier == null) tier = GTVoltageTier.LV;
            double amps = totEUt / (double) tier.getVoltage();
            tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        }
        return tooltipLines;
    }

    public static void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node,
                                           int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                           net.minecraft.client.gui.components.EditBox parallelBox,
                                           com.gtceu.calcboard.client.gui.BoardScreen parent) {
        if (MachineAddon.isTurbineMachine(node) && node.isMultiblock()) {
            if (com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.isPlasmaTurbine(node)) {
                com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel curModel = com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.getModel(node);
                com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel[] models = com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.values();
                int btnW = 82;
                int gap = 4;
                int btnY = y + 44;
                for (int i = 0; i < models.length; i++) {
                    com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel m = models[i];
                    int btnX = x + 10 + i * (btnW + gap);
                    boolean isSelected = m == curModel;
                    boolean hover = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16;
                    graphics.fill(btnX, btnY, btnX + btnW, btnY + 16, isSelected ? 0xFF35445E : (hover ? 0xFF2A303C : 0xFF1E222D));
                    graphics.renderOutline(btnX, btnY, btnW, 16, isSelected ? 0xFF58D3FF : (hover ? 0xFF6B7B96 : 0xFF353C4D));
                    graphics.drawCenteredString(font, m.getFormatCode() + m.getShortName() + " §7(" + m.getParallelMultiplier() + "x)", btnX + btnW / 2, btnY + 4, 0xFFFFFFFF);
                }

                // Rotor & Holder Info string on line 1 (y + 30)
                String rName = node.getRotorName();
                if (rName == null || rName.isEmpty() || rName.startsWith("Standard")) {
                    rName = Component.translatable("gui.gtcalcboard.rotor.standard").getString();
                }
                int eff = node.getRotorEfficiency();
                int pwr = node.getRotorPower();
                String rotorInfo = "§6🌀 §f" + rName + " §7| §b⏱ " + eff + "% §e⚡ " + pwr + "%";
                int holderBonus = GTTurbineHelper.getTurbineHolderEfficiencyBonus(node);
                if (holderBonus > 0) {
                    rotorInfo += " §a(+" + holderBonus + "% Holder)";
                }
                graphics.drawString(font, rotorInfo, x + 10, y + 30, 0xFFFFFFFF, false);

                // Reset Rotor Button [↺ Standard] (y + 44, right side)
                int resetBtnX = x + dialogW - 90;
                boolean resetHover = mouseX >= resetBtnX && mouseX <= resetBtnX + 82 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(resetBtnX, y + 44, resetBtnX + 82, y + 60, resetHover ? 0xFF3E485A : 0xFF242A35);
                graphics.renderOutline(resetBtnX, y + 44, 82, 16, resetHover ? 0xFF58D3FF : 0xFF4A556B);
                graphics.drawCenteredString(font, "↺ " + Component.translatable("gui.gtcalcboard.rotor.reset_btn").getString(), resetBtnX + 41, y + 48, 0xFFFFFFFF);
            } else {
                String rName = node.getRotorName();
                if (rName == null || rName.isEmpty() || rName.startsWith("Standard")) {
                    rName = Component.translatable("gui.gtcalcboard.rotor.standard").getString();
                }
                int eff = node.getRotorEfficiency();
                int pwr = node.getRotorPower();
                String rotorHeader = "§6🌀 " + Component.translatable("gui.gtcalcboard.addon_cat.rotor").getString() + " §7- §f" + rName;
                graphics.drawString(font, rotorHeader, x + 10, y + 30, 0xFFFFFFFF, false);

                String specStr = String.format(Locale.ROOT, "§b⏱ %s: §f%d%%   §e⚡ %s: §f%d%%",
                        Component.translatable("gui.gtcalcboard.rotor.eff").getString(), eff,
                        Component.translatable("gui.gtcalcboard.rotor.power").getString(), pwr);
                if (node.isLargeTurbine()) {
                    int holderBonus = GTTurbineHelper.getTurbineHolderEfficiencyBonus(node);
                    if (holderBonus > 0) {
                        specStr += String.format(Locale.ROOT, "   §a(+%d%% Holder)", holderBonus);
                    }
                }
                graphics.drawString(font, specStr, x + 10, y + 46, 0xFFD0D6E4, false);

                // Reset Rotor Button [↺ Standard 100%]
                int resetBtnX = x + dialogW - 118;
                boolean resetHover = mouseX >= resetBtnX && mouseX <= resetBtnX + 110 && mouseY >= y + 38 && mouseY <= y + 54;
                graphics.fill(resetBtnX, y + 38, resetBtnX + 110, y + 54, resetHover ? 0xFF3E485A : 0xFF242A35);
                graphics.renderOutline(resetBtnX, y + 38, 110, 16, resetHover ? 0xFF58D3FF : 0xFF4A556B);
                graphics.drawCenteredString(font, "↺ " + Component.translatable("gui.gtcalcboard.rotor.reset_btn").getString(), resetBtnX + 55, y + 42, 0xFFFFFFFF);
            }
        } else if (node.isLiquidBoilerRecipe() || (com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node) != null && com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).isBoilerRecipe(node))) {
            graphics.drawString(font, "§6♨ " + Component.translatable("gui.gtcalcboard.boiler_type_title").getString(), x + 10, y + 30, 0xFFFFFFFF, false);
            com.gtceu.calcboard.api.GTBoilerTier curTier = com.gtceu.calcboard.api.GTBoilerTier.getBoilerTier(node);

            // Multiblock Boiler Throttle Control Bar (Top-Right)
            if (curTier.isMultiblock()) {
                int curThrottle = node.getBoilerThrottle();
                int thrX = x + dialogW - 250;
                String thrTitle = "§e⚡ " + Component.translatable("gui.gtcalcboard.boiler_throttle").getString() + ":";
                graphics.drawString(font, thrTitle, thrX, y + 30, 0xFFFFFFFF, false);
                int titleW = font.width(thrTitle);

                // [-]
                int minusX = thrX + titleW + 6;
                boolean minusHover = mouseX >= minusX && mouseX <= minusX + 14 && mouseY >= y + 28 && mouseY <= y + 40;
                graphics.fill(minusX, y + 28, minusX + 14, y + 40, minusHover ? 0xFF3D4558 : 0xFF242A35);
                graphics.renderOutline(minusX, y + 28, 14, 12, minusHover ? 0xFF58D3FF : 0xFF3F4658);
                graphics.drawCenteredString(font, "-", minusX + 7, y + 30, 0xFFFFFFFF);

                // Value Display [ 100% ]
                int valX = minusX + 16;
                graphics.fill(valX, y + 28, valX + 32, y + 40, 0xFF1B202A);
                graphics.renderOutline(valX, y + 28, 32, 12, 0xFF3F4658);
                graphics.drawCenteredString(font, curThrottle + "%", valX + 16, y + 30, 0xFF58D3FF);

                // [+]
                int plusX = valX + 34;
                boolean plusHover = mouseX >= plusX && mouseX <= plusX + 14 && mouseY >= y + 28 && mouseY <= y + 40;
                graphics.fill(plusX, y + 28, plusX + 14, y + 40, plusHover ? 0xFF3D4558 : 0xFF242A35);
                graphics.renderOutline(plusX, y + 28, 14, 12, plusHover ? 0xFF58D3FF : 0xFF3F4658);
                graphics.drawCenteredString(font, "+", plusX + 7, y + 30, 0xFFFFFFFF);

                // Presets: [25%] [50%] [75%] [100%]
                int[] presets = {25, 50, 75, 100};
                int curPreX = plusX + 18;
                for (int pre : presets) {
                    int preW = pre == 100 ? 28 : 24;
                    boolean active = curThrottle == pre;
                    boolean preHover = mouseX >= curPreX && mouseX <= curPreX + preW && mouseY >= y + 28 && mouseY <= y + 40;
                    graphics.fill(curPreX, y + 28, curPreX + preW, y + 40, active ? 0xFF2A5288 : (preHover ? 0xFF3D4558 : 0xFF242A35));
                    graphics.renderOutline(curPreX, y + 28, preW, 12, active ? 0xFF589CFF : 0xFF3F4658);
                    graphics.drawCenteredString(font, pre + "%", curPreX + preW / 2, y + 30, active ? 0xFF58D3FF : 0xFFB0B8C8);
                    curPreX += preW + 3;
                }
            }

            com.gtceu.calcboard.api.GTBoilerTier[] bTiers = com.gtceu.calcboard.api.GTBoilerTier.values();
            boolean isLiquid = node.isLiquidBoilerRecipe();
            int btnW = 70;
            int gap = 4;
            int btnY = y + 44;
            com.gtceu.calcboard.api.GTBoilerTier hoveredTier = null;
            for (int i = 0; i < bTiers.length; i++) {
                com.gtceu.calcboard.api.GTBoilerTier bt = bTiers[i];
                boolean active = curTier == bt;
                int btnX = x + 10 + i * (btnW + gap);
                boolean hov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16;
                if (hov) hoveredTier = bt;
                graphics.fill(btnX, btnY, btnX + btnW, btnY + 16, active ? 0xFF5D3E1A : (hov ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, btnY, btnW, 16, active ? bt.getColor() : 0xFF3F4658);
                String speedLabel = String.format(Locale.ROOT, "%.1fx", bt.getSpeedMultiplier(isLiquid)).replace(".0x", "x");
                String label = (bt.isMultiblock() ? "🏛 " : "♨ ") + (i == 0 ? "LP (" + speedLabel + ")" : (i == 1 ? "HP (" + speedLabel + ")" : (i == 2 ? "L-Brz" : (i == 3 ? "L-Stl" : (i == 4 ? "L-Ti" : "L-W")))));
                graphics.drawCenteredString(font, label, btnX + btnW / 2, btnY + 4, active ? bt.getColor() : 0xFFB0B8C8);
            }
            if (hoveredTier != null) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal((hoveredTier.isMultiblock() ? "§6🏛 " : "§6♨ ") + hoveredTier.getDisplayName()));
                double thrMult = hoveredTier.isMultiblock() ? (node.getBoilerThrottle() / 100.0) : 1.0;
                double speed = hoveredTier.getSpeedMultiplier(isLiquid) * thrMult;
                double steamRate = hoveredTier.getSteamRatePerSec(isLiquid) * thrMult;
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Fuel Burn Speed: §e%.2fx%s", speed, hoveredTier.isMultiblock() && node.getBoilerThrottle() < 100 ? " §8(" + node.getBoilerThrottle() + "% Throttle)" : "")));
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Steam Output: §b%,.0f mB/s §7(%,.0f mB/t)", steamRate, steamRate / 20.0)));
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Water Input: §9%,.2f mB/s §7(%,.3f mB/t)", steamRate / 160.0, (steamRate / 20.0) / 160.0)));
                graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            }
        } else if (!node.isMultiblock()) {
            if (node.supportsSteamMode()) {
                graphics.drawString(font, "§6♨ " + Component.translatable("gui.gtcalcboard.config.steam_mode_title").getString(), x + 10, y + 30, 0xFFFFFFFF, false);
                int btnX = x + 10;
                com.gtceu.calcboard.api.SteamMode curSteam = node.getSteamMode();

                boolean lpActive = curSteam == com.gtceu.calcboard.api.SteamMode.LOW_PRESSURE;
                boolean lpHover = mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 110, y + 60, lpActive ? 0xFF5D3E1A : (lpHover ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 110, 16, lpActive ? 0xFFD28C38 : 0xFF3F4658);
                graphics.drawCenteredString(font, "♨ LP Steam (0.5x)", btnX + 55, y + 48, lpActive ? 0xFFFFD28C : 0xFFB0B8C8);
                btnX += 116;

                boolean hpActive = curSteam == com.gtceu.calcboard.api.SteamMode.HIGH_PRESSURE;
                boolean hpHover = mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 110, y + 60, hpActive ? 0xFF4A4A4A : (hpHover ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 110, 16, hpActive ? 0xFFAAAAAA : 0xFF3F4658);
                graphics.drawCenteredString(font, "♨ HP Steam (1.0x)", btnX + 55, y + 48, hpActive ? 0xFFFFFFFF : 0xFFB0B8C8);
                btnX += 116;

                boolean elecActive = curSteam == com.gtceu.calcboard.api.SteamMode.NONE;
                boolean elecHover = mouseX >= btnX && mouseX <= btnX + 90 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 90, y + 60, elecActive ? 0xFF2A5288 : (elecHover ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 90, 16, elecActive ? 0xFF589CFF : 0xFF3F4658);
                graphics.drawCenteredString(font, "⚡ Electric", btnX + 45, y + 48, elecActive ? 0xFF58D3FF : 0xFFB0B8C8);
            } else {
                graphics.drawString(font, "§b" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_fixed").getString(), x + 10, y + 32, 0xFFFFFFFF, false);
                graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_desc").getString(), x + 10, y + 48, 0xFF888888, false);
            }
        } else {
            List<ResourceLocation> mbWorkstations = new ArrayList<>();
            for (ResourceLocation ws : node.getAvailableWorkstations()) {
                if (ws != null && MultiblockDetector.isMultiblock(ws) && !mbWorkstations.contains(ws)) {
                    mbWorkstations.add(ws);
                }
            }
            if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
                mbWorkstations.add(node.getMachineIcon());
            }

            // Find equipped parallel addon
            MachineAddon equippedParallel = null;
            for (MachineAddon a : node.getAddons()) {
                if (a != null && a.getCategory() == MachineAddon.Category.PARALLEL) {
                    equippedParallel = a;
                    break;
                }
            }

            int defPar = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getDefaultParallel(node);
            int totalCount = mbWorkstations.size();
            boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
            int parBtnW = supportsParHatch ? 130 : 0;
            int parBtnX = x + dialogW - 10 - parBtnW;
            int controllersAreaW = supportsParHatch ? (parBtnX - (x + 10) - 8) : (dialogW - 20);

            int minBtnW = 80;
            int maxFitWithoutNav = Math.max(1, (controllersAreaW + 4) / (minBtnW + 4));
            boolean showNav = totalCount > maxFitWithoutNav;

            int visibleCount = showNav ? Math.max(1, (controllersAreaW - 40 + 4) / (minBtnW + 4)) : totalCount;
            int maxScroll = Math.max(0, totalCount - visibleCount);
            if (mbControllerScroll > maxScroll) mbControllerScroll = maxScroll;

            // Header line (y + 30)
            String navIndicator = showNav ? " (" + (mbControllerScroll + 1) + "-" + Math.min(totalCount, mbControllerScroll + visibleCount) + "/" + totalCount + ")" : "";
            String mbHeader = "§b🏛 " + Component.translatable("gui.gtcalcboard.config.multiblock_controller_title").getString() + "§7" + navIndicator;
            graphics.drawString(font, mbHeader, x + 10, y + 30, 0xFFFFFFFF, false);

            // Right side parallel summary badge on line 1 (y + 30)
            String parSummary = "§7⚡ " + node.getTotalParallel() + "x Par" + (defPar > 1 ? " (Default " + defPar + "x)" : (node.getTotalParallel() > 1 ? " (Base " + node.getParallel() + "x)" : " (Default 1x)"));
            int parSummaryW = font.width(parSummary);
            graphics.drawString(font, parSummary, x + dialogW - 10 - parSummaryW, y + 30, 0xFFFFFFFF, false);

            int curX = x + 10;
            int btnW;

            if (showNav) {
                int navBtnW = 16;
                // Left Nav Arrow ◀
                boolean leftHov = mouseX >= curX && mouseX <= curX + navBtnW && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(curX, y + 44, curX + navBtnW, y + 60, leftHov ? 0xFF3D4558 : 0xFF282D3B);
                graphics.renderOutline(curX, y + 44, navBtnW, 16, leftHov ? 0xFF58D3FF : 0xFF3F4658);
                graphics.drawCenteredString(font, "◀", curX + navBtnW / 2, y + 48, mbControllerScroll > 0 ? 0xFFFFFFFF : 0xFF666666);
                curX += navBtnW + 4;
                btnW = (controllersAreaW - 40 - (visibleCount - 1) * 4) / visibleCount;
            } else {
                btnW = (controllersAreaW - (visibleCount - 1) * 4) / Math.max(1, visibleCount);
            }

            ResourceLocation hoveredController = null;
            int startIdx = showNav ? mbControllerScroll : 0;
            int endIdx = showNav ? Math.min(totalCount, mbControllerScroll + visibleCount) : totalCount;

            for (int i = startIdx; i < endIdx; i++) {
                ResourceLocation mbWs = mbWorkstations.get(i);
                boolean isSelected = mbWs.equals(node.getMachineIcon());
                boolean hov = mouseX >= curX && mouseX <= curX + btnW && mouseY >= y + 44 && mouseY <= y + 60;
                if (hov) hoveredController = mbWs;

                int fill = isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B);
                int border = isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658);

                graphics.fill(curX, y + 44, curX + btnW, y + 60, fill);
                graphics.renderOutline(curX, y + 44, btnW, 16, border);

                String label = getMultiblockShortLabel(mbWs);
                int textCol = isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8);
                graphics.drawCenteredString(font, font.plainSubstrByWidth(label, btnW - 4), curX + btnW / 2, y + 48, textCol);
                curX += btnW + 4;
            }

            if (showNav) {
                int navBtnW = 16;
                // Right Nav Arrow ▶
                boolean rightHov = mouseX >= curX && mouseX <= curX + navBtnW && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(curX, y + 44, curX + navBtnW, y + 60, rightHov ? 0xFF3D4558 : 0xFF282D3B);
                graphics.renderOutline(curX, y + 44, navBtnW, 16, rightHov ? 0xFF58D3FF : 0xFF3F4658);
                graphics.drawCenteredString(font, "▶", curX + navBtnW / 2, y + 48, mbControllerScroll < maxScroll ? 0xFFFFFFFF : 0xFF666666);
            }

            // Right side: + Parallel Hatch / Installed Parallel Badge (only if supported)
            if (supportsParHatch) {
                boolean parHov = mouseX >= parBtnX && mouseX <= parBtnX + parBtnW && mouseY >= y + 44 && mouseY <= y + 60;
                if (equippedParallel != null) {
                    graphics.fill(parBtnX, y + 44, parBtnX + parBtnW, y + 60, parHov ? 0xFF3A1C22 : 0xFF202B38);
                    graphics.renderOutline(parBtnX, y + 44, parBtnW, 16, parHov ? 0xFFFF6B6B : 0xFF45B074);
                    String parText = parHov ? ("✕ " + Component.translatable("gui.gtcalcboard.config.remove").getString())
                            : ("⚡ " + equippedParallel.getParallelMultiplier() + "x " + Component.translatable("gui.gtcalcboard.addon_cat.parallel").getString());
                    graphics.drawCenteredString(font, font.plainSubstrByWidth(parText, parBtnW - 4), parBtnX + parBtnW / 2, y + 48, parHov ? 0xFFFF8888 : 0xFF55FF88);
                } else {
                    graphics.fill(parBtnX, y + 44, parBtnX + parBtnW, y + 60, parHov ? 0xFF2B3A50 : 0xFF202633);
                    graphics.renderOutline(parBtnX, y + 44, parBtnW, 16, parHov ? 0xFF589CFF : 0xFF3F506B);
                    String pLabel = Component.translatable("gui.gtcalcboard.config.install_parallel_hatch").getString();
                    graphics.drawCenteredString(font, font.plainSubstrByWidth(pLabel, parBtnW - 4), parBtnX + parBtnW / 2, y + 48, parHov ? 0xFF80D0FF : 0xFF58A6FF);
                }
            }

            if (hoveredController != null) {
                List<Component> tt = new ArrayList<>();
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(hoveredController);
                String fullName = (item != null && item != net.minecraft.world.item.Items.AIR) ? item.getDescription().getString() : hoveredController.getPath();
                tt.add(Component.literal("§e🏛 " + fullName));
                tt.add(Component.literal("§8" + hoveredController));
                boolean active = hoveredController.equals(node.getMachineIcon());
                if (active) {
                    tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.config.active_controller").getString()));
                } else {
                    tt.add(Component.literal("§7[Click to select]"));
                }
                graphics.renderTooltip(font, tt, java.util.Optional.empty(), mouseX, mouseY);
            }
        }
    }

    private static String getMultiblockShortLabel(ResourceLocation id) {
        if (id == null) return "🏛 Multi";
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("extreme_chemical_reactor") || path.equals("ecr")) return "⚡ ECR";
        if (path.contains("incomprehensible_chemical_reactor") || path.equals("icr")) return "⚡ ICR";
        if (path.contains("large_chemical_reactor") || path.equals("lcr")) return "🏛 LCR";
        if (path.contains("super_cracker") || path.contains("sdf")) return "⚡ SDF Cracker";
        if (path.contains("cracker")) return "🏛 Cracker";
        if (path.contains("supreme")) return "⚡ Supreme";
        if (path.contains("nyinsane")) return "⚡ Nyinsane";
        if (path.contains("large_fluid_distillation") || path.contains("large_distillation")) return "🏛 Large DT";
        if (path.contains("distillation_tower")) return "🏛 Distillation";
        if (path.contains("yielding_exhaustor") || path.contains("yeast")) return "🧬 Yeast";
        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            String name = item.getDescription().getString();
            if (name.startsWith("Advanced ")) name = "Adv. " + name.substring(9);
            else if (name.startsWith("Elite ")) name = "Elite " + name.substring(6);
            else if (name.startsWith("Ultimate ")) name = "Ult. " + name.substring(9);
            else if (name.startsWith("Material Processing ")) name = "Mat. Proc. " + name.substring(20);
            return "🏛 " + name;
        }
        return "🏛 " + id.getPath();
    }

    public static boolean handleDialogHeaderClick(com.gtceu.calcboard.client.gui.MachineConfigDialog dialog,
                                                 RecipeNode node, int x, int y, int dialogW,
                                                 double mouseX, double mouseY, int button,
                                                 net.minecraft.client.gui.components.EditBox parallelBox,
                                                 com.gtceu.calcboard.client.gui.BoardScreen parent) {
        if (MachineAddon.isTurbineMachine(node) && node.isMultiblock()) {
            if (com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.isPlasmaTurbine(node)) {
                com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel[] models = com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.values();
                int btnW = 82;
                int gap = 4;
                int btnY = y + 44;
                for (int i = 0; i < models.length; i++) {
                    com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel m = models[i];
                    int btnX = x + 10 + i * (btnW + gap);
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16) {
                        node.setMachineIcon(m.getMachineIcon());
                        // Remove incompatible traits if switching models
                        List<MachineAddon> toRemove = new ArrayList<>();
                        for (MachineAddon a : node.getAddons()) {
                            if (a.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT) {
                                if (m == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.SPT && a.getId().contains("npt_")) toRemove.add(a);
                                if (m == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.NPT && a.getId().contains("spt_")) toRemove.add(a);
                                if (m == com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.LPT && (a.getId().contains("spt_") || a.getId().contains("npt_"))) toRemove.add(a);
                            }
                        }
                        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
                        for (MachineAddon a : toRemove) {
                            if (adapter != null) adapter.handleUninstallAddon(node, a);
                            else node.getAddons().remove(a);
                        }
                        if (dialog != null) {
                            dialog.invalidateFilteredCatalog();
                        }
                        if (parent != null) parent.markSummaryDirty();
                        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                        );
                        return true;
                    }
                }
                int resetBtnX = x + dialogW - 90;
                if (mouseX >= resetBtnX && mouseX <= resetBtnX + 82 && mouseY >= y + 44 && mouseY <= y + 60) {
                    List<MachineAddon> rotors = new ArrayList<>();
                    for (MachineAddon a : node.getAddons()) {
                        if (a.getCategory() == MachineAddon.Category.ROTOR) rotors.add(a);
                    }
                    com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
                    for (MachineAddon r : rotors) {
                        if (adapter != null) adapter.handleUninstallAddon(node, r);
                        else node.getAddons().remove(r);
                    }
                    node.setRotorEfficiency(100);
                    node.setRotorPower(100);
                    node.setRotorName(null);
                    if (parent != null) parent.markSummaryDirty();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
            } else {
                int resetBtnX = x + dialogW - 118;
                if (mouseX >= resetBtnX && mouseX <= resetBtnX + 110 && mouseY >= y + 38 && mouseY <= y + 54) {
                    List<MachineAddon> rotors = new ArrayList<>();
                    for (MachineAddon a : node.getAddons()) {
                        if (a.getCategory() == MachineAddon.Category.ROTOR) rotors.add(a);
                    }
                    com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
                    for (MachineAddon r : rotors) {
                        if (adapter != null) adapter.handleUninstallAddon(node, r);
                        else node.getAddons().remove(r);
                    }
                    node.setRotorEfficiency(100);
                    node.setRotorPower(100);
                    node.setRotorName(null);
                    if (parent != null) parent.markSummaryDirty();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
            }
        } else if (node.isLiquidBoilerRecipe() || (com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node) != null && com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).isBoilerRecipe(node))) {
            com.gtceu.calcboard.api.GTBoilerTier curTier = com.gtceu.calcboard.api.GTBoilerTier.getBoilerTier(node);

            // Multiblock Boiler Throttle Click Handling
            if (curTier.isMultiblock()) {
                int curThrottle = node.getBoilerThrottle();
                int thrX = x + dialogW - 250;
                Font font = net.minecraft.client.Minecraft.getInstance().font;
                String thrTitle = "§e⚡ " + Component.translatable("gui.gtcalcboard.boiler_throttle").getString() + ":";
                int titleW = font.width(thrTitle);
                boolean shift = net.minecraft.client.gui.screens.Screen.hasShiftDown();
                int step = shift ? 25 : 5;

                // [-]
                int minusX = thrX + titleW + 6;
                if (mouseX >= minusX && mouseX <= minusX + 14 && mouseY >= y + 28 && mouseY <= y + 40) {
                    node.setBoilerThrottle(Math.max(25, curThrottle - step));
                    if (parent != null) parent.markSummaryDirty();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }

                // [+]
                int valX = minusX + 16;
                int plusX = valX + 34;
                if (mouseX >= plusX && mouseX <= plusX + 14 && mouseY >= y + 28 && mouseY <= y + 40) {
                    node.setBoilerThrottle(Math.min(100, curThrottle + step));
                    if (parent != null) parent.markSummaryDirty();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }

                // Presets: [25%] [50%] [75%] [100%]
                int[] presets = {25, 50, 75, 100};
                int curPreX = plusX + 18;
                for (int pre : presets) {
                    int preW = pre == 100 ? 28 : 24;
                    if (mouseX >= curPreX && mouseX <= curPreX + preW && mouseY >= y + 28 && mouseY <= y + 40) {
                        node.setBoilerThrottle(pre);
                        if (parent != null) parent.markSummaryDirty();
                        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                        );
                        return true;
                    }
                    curPreX += preW + 3;
                }
            }

            com.gtceu.calcboard.api.GTBoilerTier[] bTiers = com.gtceu.calcboard.api.GTBoilerTier.values();
            int btnW = 70;
            int gap = 4;
            int btnY = y + 44;
            for (int i = 0; i < bTiers.length; i++) {
                com.gtceu.calcboard.api.GTBoilerTier bt = bTiers[i];
                int btnX = x + 10 + i * (btnW + gap);
                if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16) {
                    boolean isLiquid = node.isLiquidBoilerRecipe();
                    node.setMachineIcon(bt.getDefaultIcon(isLiquid));
                    node.setMultiblock(bt.isMultiblock());
                    if (parent != null) parent.markSummaryDirty();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
            }
        } else if (!node.isMultiblock()) {
            if (node.supportsSteamMode()) {
                int btnX = x + 10;
                if (mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60) {
                    node.setSteamMode(com.gtceu.calcboard.api.SteamMode.LOW_PRESSURE);
                    if (parent != null) parent.markSummaryDirty();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
                btnX += 116;
                if (mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60) {
                    node.setSteamMode(com.gtceu.calcboard.api.SteamMode.HIGH_PRESSURE);
                    if (parent != null) parent.markSummaryDirty();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
                btnX += 116;
                if (mouseX >= btnX && mouseX <= btnX + 90 && mouseY >= y + 44 && mouseY <= y + 60) {
                    node.setSteamMode(com.gtceu.calcboard.api.SteamMode.NONE);
                    if (parent != null) parent.markSummaryDirty();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
            }
        } else {
            List<ResourceLocation> mbWorkstations = new ArrayList<>();
            for (ResourceLocation ws : node.getAvailableWorkstations()) {
                if (ws != null && MultiblockDetector.isMultiblock(ws) && !mbWorkstations.contains(ws)) {
                    mbWorkstations.add(ws);
                }
            }
            if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
                mbWorkstations.add(node.getMachineIcon());
            }

            // Find equipped parallel addon
            MachineAddon equippedParallel = null;
            for (MachineAddon a : node.getAddons()) {
                if (a != null && a.getCategory() == MachineAddon.Category.PARALLEL) {
                    equippedParallel = a;
                    break;
                }
            }

            int totalCount = mbWorkstations.size();
            boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
            int parBtnW = supportsParHatch ? 130 : 0;
            int parBtnX = x + dialogW - 10 - parBtnW;
            int controllersAreaW = supportsParHatch ? (parBtnX - (x + 10) - 8) : (dialogW - 20);

            int minBtnW = 80;
            int maxFitWithoutNav = Math.max(1, (controllersAreaW + 4) / (minBtnW + 4));
            boolean showNav = totalCount > maxFitWithoutNav;

            int visibleCount = showNav ? Math.max(1, (controllersAreaW - 40 + 4) / (minBtnW + 4)) : totalCount;
            int maxScroll = Math.max(0, totalCount - visibleCount);

            int curX = x + 10;
            int btnW;

            if (showNav) {
                int navBtnW = 16;
                // Click Left Nav Arrow ◀
                if (mouseX >= curX && mouseX <= curX + navBtnW && mouseY >= y + 44 && mouseY <= y + 60) {
                    if (mbControllerScroll > 0) {
                        mbControllerScroll--;
                        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                        );
                    }
                    return true;
                }
                curX += navBtnW + 4;
                btnW = (controllersAreaW - 40 - (visibleCount - 1) * 4) / visibleCount;
            } else {
                btnW = (controllersAreaW - (visibleCount - 1) * 4) / Math.max(1, visibleCount);
            }

            int startIdx = showNav ? mbControllerScroll : 0;
            int endIdx = showNav ? Math.min(totalCount, mbControllerScroll + visibleCount) : totalCount;

            // 1. Controller selection click
            for (int i = startIdx; i < endIdx; i++) {
                ResourceLocation mbWs = mbWorkstations.get(i);
                if (mouseX >= curX && mouseX <= curX + btnW && mouseY >= y + 44 && mouseY <= y + 60) {
                    boolean isThreading = MultiblockDetector.isThreadingMultiblock(mbWs);
                    node.setMachineIcon(mbWs);
                    node.setThreadingActive(isThreading);
                    if (!MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations())) {
                        node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.PARALLEL);
                        node.setParallel(1);
                        if (dialog != null && dialog.getSelectedCategory() == MachineAddon.Category.PARALLEL) {
                            dialog.setSelectedCategory(null);
                        }
                    }
                    if (dialog != null) {
                        if (!isThreading && dialog.getSelectedCategory() == com.gtceu.calcboard.api.AddonCategory.THREADING) {
                            dialog.setSelectedCategory(null);
                        }
                        dialog.invalidateFilteredCatalog();
                    }
                    if (parent != null) parent.markSummaryDirty();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
                curX += btnW + 4;
            }

            if (showNav) {
                int navBtnW = 16;
                // Click Right Nav Arrow ▶
                if (mouseX >= curX && mouseX <= curX + navBtnW && mouseY >= y + 44 && mouseY <= y + 60) {
                    if (mbControllerScroll < maxScroll) {
                        mbControllerScroll++;
                        net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                        );
                    }
                    return true;
                }
            }

            // 2. Parallel hatch action click (only if supported)
            if (supportsParHatch && mouseX >= parBtnX && mouseX <= parBtnX + parBtnW && mouseY >= y + 44 && mouseY <= y + 60) {
                if (equippedParallel != null) {
                    com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).handleUninstallAddon(node, equippedParallel);
                } else if (dialog != null) {
                    dialog.setSelectedCategory(MachineAddon.Category.PARALLEL);
                }
                if (dialog != null) {
                    dialog.invalidateFilteredCatalog();
                }
                if (parent != null) parent.markSummaryDirty();
                net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
        }
        return false;
    }

    public static boolean handleControllerScroll(RecipeNode node, double delta) {
        if (node == null || !node.isMultiblock()) return false;
        List<ResourceLocation> mbWorkstations = new ArrayList<>();
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (ws != null && MultiblockDetector.isMultiblock(ws) && !mbWorkstations.contains(ws)) {
                mbWorkstations.add(ws);
            }
        }
        if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
            mbWorkstations.add(node.getMachineIcon());
        }
        int totalCount = mbWorkstations.size();
        boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
        int parBtnW = supportsParHatch ? 130 : 0;
        int controllersAreaW = supportsParHatch ? (460 - 20 - parBtnW - 8) : (460 - 20);
        int minBtnW = 80;
        int maxFitWithoutNav = Math.max(1, (controllersAreaW + 4) / (minBtnW + 4));
        if (totalCount <= maxFitWithoutNav) return false;

        int visibleCount = Math.max(1, (controllersAreaW - 40 + 4) / (minBtnW + 4));
        int maxScroll = Math.max(0, totalCount - visibleCount);
        if (maxScroll <= 0) return false;

        if (delta > 0) {
            if (mbControllerScroll > 0) {
                mbControllerScroll--;
                return true;
            }
        } else if (delta < 0) {
            if (mbControllerScroll < maxScroll) {
                mbControllerScroll++;
                return true;
            }
        }
        return false;
    }
}
