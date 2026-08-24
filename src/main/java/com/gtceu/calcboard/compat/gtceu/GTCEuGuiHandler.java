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
        int tierBtnW = 32;
        int nextCtrlX = x + 42;

        if (node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.NONE) {
            String bannerText = "🍃 " + Component.translatable("gui.gtcalcboard.energy_passive_banner").getString();
            int bannerW = cardW - 12;
            NodeCardRenderer.drawBtn(graphics, font, bannerText, x + 6, row2Y, bannerW, 14, mouseX, mouseY, 0xFF88D49E, false, false);
            return;
        } else if (node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.HEAT_OR_SELF) {
            com.gtceu.calcboard.api.GTBoilerTier boilerTier = com.gtceu.calcboard.api.GTBoilerTier.getBoilerTier(node);
            String boilerText = boilerTier.getDisplayName();
            tierBtnW = Math.max(54, font.width(boilerText) + 8);
            int boilerColor = !isOperational ? 0xFFFF8888 : boilerTier.getColor();
            NodeCardRenderer.drawBtn(graphics, font, boilerText, x + 6, row2Y, tierBtnW, 14, mouseX, mouseY, boilerColor, !isOperational, false);
            nextCtrlX = x + 6 + tierBtnW + 4;
        } else if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            String steamText = node.getSteamMode().getDisplayName();
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

        if (node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.HEAT_OR_SELF) {
            com.gtceu.calcboard.api.GTBoilerTier boilerTier = com.gtceu.calcboard.api.GTBoilerTier.getBoilerTier(node);
            tooltipLines.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.boiler_title").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Boiler Type: %s%s", boilerTier.getFormatCode(), boilerTier.getDisplayName())));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Speed Multiplier: §e%.1fx", boilerTier.getSpeedMultiplier())));
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
            if (com.gtceu.calcboard.api.GTPlasmaTurbineModel.isPlasmaTurbine(node)) {
                com.gtceu.calcboard.api.GTPlasmaTurbineModel curModel = com.gtceu.calcboard.api.GTPlasmaTurbineModel.getModel(node);
                com.gtceu.calcboard.api.GTPlasmaTurbineModel[] models = com.gtceu.calcboard.api.GTPlasmaTurbineModel.values();
                int btnW = 82;
                int gap = 4;
                int btnY = y + 44;
                for (int i = 0; i < models.length; i++) {
                    com.gtceu.calcboard.api.GTPlasmaTurbineModel m = models[i];
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
                int holderBonus = node.getTurbineHolderEfficiencyBonus();
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
                    int holderBonus = node.getTurbineHolderEfficiencyBonus();
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
        } else if (node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.HEAT_OR_SELF) {
            graphics.drawString(font, "§6♨ " + Component.translatable("gui.gtcalcboard.boiler_type_title").getString(), x + 10, y + 30, 0xFFFFFFFF, false);
            com.gtceu.calcboard.api.GTBoilerTier curTier = com.gtceu.calcboard.api.GTBoilerTier.getBoilerTier(node);
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
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Fuel Burn Speed: §e%.2fx", hoveredTier.getSpeedMultiplier(isLiquid))));
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Steam Output: §b%,.0f mB/s §7(%,.0f mB/t)", hoveredTier.getSteamRatePerSec(isLiquid), hoveredTier.getSteamRatePerSec(isLiquid) / 20.0)));
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Water Input: §9%,.2f mB/s §7(%,.3f mB/t)", hoveredTier.getSteamRatePerSec(isLiquid) / 160.0, (hoveredTier.getSteamRatePerSec(isLiquid) / 20.0) / 160.0)));
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
            int curPar = node.getParallel();
            String parLabel = "§b" + Component.translatable("gui.gtcalcboard.config.base_parallel", String.valueOf(curPar)).getString()
                    + " §7(" + Component.translatable("gui.gtcalcboard.config.total_effective", String.valueOf(node.getTotalParallel())).getString() + "§7)";
            graphics.drawString(font, parLabel, x + 10, y + 30, 0xFFFFFFFF, false);

            if (parallelBox != null) {
                parallelBox.setX(x + 10);
                parallelBox.setY(y + 44);
                parallelBox.render(graphics, mouseX, mouseY, partialTicks);
            }

            int[] quickPars = {1, 4, 16, 64, 256};
            int btnX = x + 64;
            for (int qp : quickPars) {
                boolean active = curPar == qp;
                boolean hov = mouseX >= btnX && mouseX <= btnX + 34 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 34, y + 60, active ? 0xFF285078 : (hov ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 34, 16, active ? 0xFF589CFF : 0xFF3F4658);
                graphics.drawCenteredString(font, qp + "x", btnX + 17, y + 48, active ? 0xFF58D3FF : 0xFFB0B8C8);
                btnX += 38;
            }

            if (node.isGenerator()) {
                int autoBtnX = x + dialogW - 98;
                boolean autoHov = mouseX >= autoBtnX && mouseX <= autoBtnX + 90 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(autoBtnX, y + 44, autoBtnX + 90, y + 60, autoHov ? 0xFF285078 : 0xFF282D3B);
                graphics.renderOutline(autoBtnX, y + 44, 90, 16, autoHov ? 0xFF589CFF : 0xFF3F4658);
                graphics.drawCenteredString(font, "⚡ " + Component.translatable("gui.gtcalcboard.config.auto_parallel").getString(), autoBtnX + 45, y + 48, 0xFF58D3FF);
            }
        }
    }

    public static boolean handleDialogHeaderClick(com.gtceu.calcboard.client.gui.MachineConfigDialog dialog,
                                                 RecipeNode node, int x, int y, int dialogW,
                                                 double mouseX, double mouseY, int button,
                                                 net.minecraft.client.gui.components.EditBox parallelBox,
                                                 com.gtceu.calcboard.client.gui.BoardScreen parent) {
        if (MachineAddon.isTurbineMachine(node) && node.isMultiblock()) {
            if (com.gtceu.calcboard.api.GTPlasmaTurbineModel.isPlasmaTurbine(node)) {
                com.gtceu.calcboard.api.GTPlasmaTurbineModel[] models = com.gtceu.calcboard.api.GTPlasmaTurbineModel.values();
                int btnW = 82;
                int gap = 4;
                int btnY = y + 44;
                for (int i = 0; i < models.length; i++) {
                    com.gtceu.calcboard.api.GTPlasmaTurbineModel m = models[i];
                    int btnX = x + 10 + i * (btnW + gap);
                    if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16) {
                        node.setMachineIcon(m.getMachineIcon());
                        // Remove incompatible traits if switching models
                        List<MachineAddon> toRemove = new ArrayList<>();
                        for (MachineAddon a : node.getAddons()) {
                            if (a.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT) {
                                if (m == com.gtceu.calcboard.api.GTPlasmaTurbineModel.SPT && a.getId().contains("npt_")) toRemove.add(a);
                                if (m == com.gtceu.calcboard.api.GTPlasmaTurbineModel.NPT && a.getId().contains("spt_")) toRemove.add(a);
                                if (m == com.gtceu.calcboard.api.GTPlasmaTurbineModel.LPT && (a.getId().contains("spt_") || a.getId().contains("npt_"))) toRemove.add(a);
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
        } else if (node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.HEAT_OR_SELF) {
            com.gtceu.calcboard.api.GTBoilerTier[] bTiers = com.gtceu.calcboard.api.GTBoilerTier.values();
            int btnW = 70;
            int gap = 4;
            int btnY = y + 44;
            for (int i = 0; i < bTiers.length; i++) {
                com.gtceu.calcboard.api.GTBoilerTier bt = bTiers[i];
                int btnX = x + 10 + i * (btnW + gap);
                if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16) {
                    node.setMachineIcon(bt.getDefaultIcon());
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
            int[] quickPars = {1, 4, 16, 64, 256};
            int btnX = x + 64;
            for (int qp : quickPars) {
                if (mouseX >= btnX && mouseX <= btnX + 34 && mouseY >= y + 44 && mouseY <= y + 60) {
                    node.setParallel(qp);
                    if (parallelBox != null) parallelBox.setValue(String.valueOf(qp));
                    if (parent != null) parent.markSummaryDirty();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
                btnX += 38;
            }

            if (node.isGenerator()) {
                int autoBtnX = x + dialogW - 98;
                if (mouseX >= autoBtnX && mouseX <= autoBtnX + 90 && mouseY >= y + 44 && mouseY <= y + 60) {
                    node.autoCalculateTurbineParallel();
                    if (parallelBox != null) parallelBox.setValue(String.valueOf(node.getParallel()));
                    if (parent != null) parent.markSummaryDirty();
                    net.minecraft.client.Minecraft.getInstance().getSoundManager().play(
                            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
            }
        }
        return false;
    }
}
