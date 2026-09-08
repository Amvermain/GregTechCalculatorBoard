package com.gtceu.calcboard.client.gui.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import com.gtceu.calcboard.compat.gtceu.physics.GTFusionHelper;
import com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated renderer for GTCEu machine configuration dialog headers.
 */
@OnlyIn(Dist.CLIENT)
public class GTCEuMachineDialogHeaderRenderer {

    private final GTCEuMachineDialogState state;

    public GTCEuMachineDialogHeaderRenderer(GTCEuMachineDialogState state) {
        this.state = state;
    }
    private static void showTooltip(MachineConfigDialog dialog, GuiGraphics graphics, Font font, List<Component> tooltip, int mouseX, int mouseY) {
        if (dialog != null) {
            dialog.setDeferredTooltip(tooltip);
        } else {
            com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltip, mouseX, mouseY);
        }
    }

    public void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node,
                                   int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                   EditBox parallelBox, BoardScreen parent) {
        renderDialogHeader(null, graphics, font, node, x, y, dialogW, mouseX, mouseY, partialTicks, parallelBox, parent);
    }

    public void renderDialogHeader(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
                                   int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                   EditBox parallelBox, BoardScreen parent) {
        if (MachineAddon.isTurbineMachine(node) && node.isMultiblock()) {
            String rName = node.getRotorName();
            if (rName == null || rName.isEmpty() || rName.startsWith("Standard")) {
                rName = Component.translatable("gui.gtcalcboard.rotor.standard").getString();
            }
            int eff = node.getRotorEfficiency();
            int pwr = node.getRotorPower();
            int holderBonus = GTTurbineHelper.getTurbineHolderEfficiencyBonus(node);
            int totalEff = GTTurbineHelper.getTotalTurbineEfficiency(node);

            GTVoltageTier holderTier = GTTurbineHelper.getRotorHolderTier(node);
            GTVoltageTier dynamoTier = GTTurbineHelper.getDynamoTier(node);
            int dynamoAmps = GTTurbineHelper.getDynamoAmperage(node);

            // Row 1: Rotor Info & Lifespan & Pmax button & Reset button
            int pmax = GTPowerCalculator.getMaxParallelCapacity(node);
            boolean isDynamoBottleneck = GTTurbineHelper.isDynamoBottleneck(node);
            String pmaxText = isDynamoBottleneck ? ("⚡ Pmax: " + pmax + " ⚠") : ("⚡ Pmax: " + pmax);
            int pmaxBtnW = Math.max(70, font.width(pmaxText) + 8);
            int pmaxBtnX = x + dialogW - 10 - pmaxBtnW;
            boolean pmaxHover = mouseX >= pmaxBtnX && mouseX <= pmaxBtnX + pmaxBtnW && mouseY >= y + 28 && mouseY <= y + 42;

            int pmaxBg = isDynamoBottleneck ? (pmaxHover ? 0xFF5A3C1A : 0xFF3D2A14) : (pmaxHover ? 0xFF2A5288 : 0xFF1C304A);
            int pmaxBorder = isDynamoBottleneck ? (pmaxHover ? 0xFFFFCC00 : 0xFFFFAA00) : (pmaxHover ? 0xFF58D3FF : 0xFF35587A);
            int pmaxTextColor = isDynamoBottleneck ? (pmaxHover ? 0xFFFFF0A0 : 0xFFFFD080) : (pmaxHover ? 0xFF58D3FF : 0xFFB0D0FF);

            graphics.fill(pmaxBtnX, y + 28, pmaxBtnX + pmaxBtnW, y + 42, pmaxBg);
            graphics.renderOutline(pmaxBtnX, y + 28, pmaxBtnW, 14, pmaxBorder);
            graphics.drawCenteredString(font, pmaxText, pmaxBtnX + pmaxBtnW / 2, y + 31, pmaxTextColor);

            int resetBtnW = Math.max(48, font.width("↺ " + Component.translatable("gui.gtcalcboard.rotor.reset_btn").getString()) + 8);
            int resetBtnX = pmaxBtnX - 4 - resetBtnW;
            boolean resetHover = mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= y + 28 && mouseY <= y + 42;
            graphics.fill(resetBtnX, y + 28, resetBtnX + resetBtnW, y + 42, resetHover ? 0xFF3E485A : 0xFF242A35);
            graphics.renderOutline(resetBtnX, y + 28, resetBtnW, 14, resetHover ? 0xFF58D3FF : 0xFF4A556B);
            graphics.drawCenteredString(font, "↺ " + Component.translatable("gui.gtcalcboard.rotor.reset_btn").getString(), resetBtnX + resetBtnW / 2, y + 31, 0xFFFFFFFF);

            int maxRotorInfoW = resetBtnX - (x + 10) - 6;
            String rotorInfo = "§6~ §f" + rName + " §7| §b⏱ " + eff + "% §e⚡ " + pwr + "%";
            if (holderBonus > 0) {
                rotorInfo += " §a(+" + holderBonus + "% -> " + totalEff + "%)";
            }
            rotorInfo += formatRotorLifespan(node, false);
            if (font.width(rotorInfo) > maxRotorInfoW) {
                String shortRName = rName.replace("Turbine Rotor", "Rotor");
                rotorInfo = "§6~ §f" + shortRName + " §7| §b⏱" + eff + "% §e⚡" + pwr + "%";
                if (holderBonus > 0) {
                    rotorInfo += " §a(+" + holderBonus + "%→" + totalEff + "%)";
                }
                rotorInfo += formatRotorLifespan(node, true);
                if (font.width(rotorInfo) > maxRotorInfoW) {
                    rotorInfo = font.plainSubstrByWidth(rotorInfo, Math.max(16, maxRotorInfoW - font.width("..."))) + "...";
                }
            }
            boolean rotorInfoHover = mouseX >= x + 10 && mouseX <= resetBtnX - 4 && mouseY >= y + 28 && mouseY <= y + 42;
            graphics.drawString(font, rotorInfo, x + 10, y + 31, 0xFFFFFFFF, false);

            // Row 2: Plasma model selector OR Decoupled Holder / Dynamo Tier / Dynamo Amps / Boost controls
            int btnY = y + 46;
            boolean dynamoHover = false;
            boolean ampsHover = false;
            boolean boostHover = false;
            int curX = x + 10;
            int gap = 4;

            int holderBtnW = 100;
            boolean holderHover = mouseX >= curX && mouseX <= curX + holderBtnW && mouseY >= btnY && mouseY <= btnY + 16;
            graphics.fill(curX, btnY, curX + holderBtnW, btnY + 16, holderHover ? 0xFF2A3548 : 0xFF1E2430);
            graphics.renderOutline(curX, btnY, holderBtnW, 16, holderHover ? 0xFF58D3FF : 0xFF3D4B60);
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.turbine_holder_tier", holderTier.getFormatCode() + holderTier.getName()).getString(), curX + holderBtnW / 2, btnY + 4, 0xFFFFFFFF);
            curX += holderBtnW + gap;

            // Dynamo Voltage Tier Button (Scroll / Left-Click: +1, Right-Click: -1)
            int dynamoTierBtnW = 100;
            dynamoHover = mouseX >= curX && mouseX <= curX + dynamoTierBtnW && mouseY >= btnY && mouseY <= btnY + 16;
            int dynamoBorder = isDynamoBottleneck ? (dynamoHover ? 0xFFFFCC00 : 0xFFFFAA00) : (dynamoHover ? 0xFF58D3FF : 0xFF3D4B60);
            graphics.fill(curX, btnY, curX + dynamoTierBtnW, btnY + 16, dynamoHover ? 0xFF2A3548 : 0xFF1E2430);
            graphics.renderOutline(curX, btnY, dynamoTierBtnW, 16, dynamoBorder);
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.turbine_dynamo_tier", dynamoTier.getFormatCode() + dynamoTier.getName()).getString(), curX + dynamoTierBtnW / 2, btnY + 4, 0xFFFFFFFF);
            curX += dynamoTierBtnW + gap;

            // Dynamo Amperage Button (Scroll / Left-Click: +1, Right-Click: -1)
            int dynamoAmpsBtnW = 70;
            ampsHover = mouseX >= curX && mouseX <= curX + dynamoAmpsBtnW && mouseY >= btnY && mouseY <= btnY + 16;
            int ampsBorder = isDynamoBottleneck ? (ampsHover ? 0xFFFFCC00 : 0xFFFFAA00) : (ampsHover ? 0xFF58D3FF : 0xFF3D4B60);
            graphics.fill(curX, btnY, curX + dynamoAmpsBtnW, btnY + 16, ampsHover ? 0xFF2A3548 : 0xFF1E2430);
            graphics.renderOutline(curX, btnY, dynamoAmpsBtnW, 16, ampsBorder);
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.turbine_dynamo_amps", dynamoAmps).getString(), curX + dynamoAmpsBtnW / 2, btnY + 4, 0xFFFFE066);
            curX += dynamoAmpsBtnW + gap;

            // Boost Multiplier Button (via IModAdapter SPI)
            com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
            boolean supportsBoost = adapter != null && adapter.supportsBoosterControl(node);
            if (supportsBoost) {
                Component boostComp = adapter.getBoosterDisplayComponent(node);
                String boostText = boostComp != null ? boostComp.getString() : "";

                int boostBtnW = Math.max(105, font.width(boostText) + 8);
                boostHover = mouseX >= curX && mouseX <= curX + boostBtnW && mouseY >= btnY && mouseY <= btnY + 16;
                int boostBg = adapter.getBoosterBackgroundColor(node, boostHover);
                int boostBorder = adapter.getBoosterBorderColor(node, boostHover);
                int boostTextColor = adapter.getBoosterTextColor(node, boostHover);
                graphics.fill(curX, btnY, curX + boostBtnW, btnY + 16, boostBg);
                graphics.renderOutline(curX, btnY, boostBtnW, 16, boostBorder);
                graphics.drawCenteredString(font, boostText, curX + boostBtnW / 2, btnY + 4, boostTextColor);
            }

            if (pmaxHover) {
                List<Component> tt = new ArrayList<>();
                tt.add(Component.literal("§6⚡ " + Component.translatable("gui.gtcalcboard.config.pmax_title").getString() + ": §f" + pmax + "x"));
                if (isDynamoBottleneck) {
                    double holderCap = GTTurbineHelper.getRotorHolderCapacity(node);
                    double dynamoCap = GTTurbineHelper.getDynamoMaxCapacity(node);
                    int holderPmax = GTTurbineHelper.getRotorHolderMaxParallel(node);
                    tt.add(Component.literal("§c" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dynamo_limited").getString()));
                    tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.pmax_holder_cap").getString(), com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(holderCap), holderPmax)));
                    tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dynamo_cap").getString(), com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(dynamoCap), pmax)));
                    tt.add(Component.literal("§a" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dynamo_tip").getString()));
                } else {
                    double cap = GTTurbineHelper.getGeneratorMaxEUt(node);
                    tt.add(Component.literal("§a" + Component.translatable("gui.gtcalcboard.tooltip.pmax_fully_utilized").getString()));
                    tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.pmax_max_cap").getString(), com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(cap))));
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            } else if ((dynamoHover || ampsHover) && isDynamoBottleneck) {
                List<Component> tt = new ArrayList<>();
                double holderCap = GTTurbineHelper.getRotorHolderCapacity(node);
                double dynamoCap = GTTurbineHelper.getDynamoMaxCapacity(node);
                tt.add(Component.literal(String.format(Locale.ROOT, "§c" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dynamo_bottleneck_hatch").getString(), com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(dynamoCap), com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(holderCap))));
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            } else if (rotorInfoHover) {
                List<Component> tt = new ArrayList<>();
                tt.add(Component.literal("§6~ §f" + rName));
                tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.addon.rotor.efficiency").getString(), eff + "%")));
                tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.addon.rotor.power").getString(), pwr + "%")));
                if (holderBonus > 0) {
                    tt.add(Component.literal("§a+ " + Component.translatable("gui.gtcalcboard.config.holder_bonus_tooltip", holderBonus + "%", totalEff + "%").getString()));
                }
                if (GTTurbineHelper.hasRotorAddon(node)) {
                    appendRotorLifespanTooltip(tt, node);
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            } else if (boostHover && supportsBoost && adapter != null) {
                List<Component> tt = new ArrayList<>();
                adapter.buildBoosterTooltip(node, tt);
                if (!tt.isEmpty()) {
                    showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
                }
            }
        } else if (GTCombustionHelper.isCombustionEngine(node)) {
            renderCombustionDialogHeader(dialog, graphics, font, node, x, y, dialogW, mouseX, mouseY, partialTicks, parallelBox, parent);
        } else if (node.isLiquidBoilerRecipe() || (com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node) != null && com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).isBoilerRecipe(node))) {
            graphics.drawString(font, "§6♨ " + Component.translatable("gui.gtcalcboard.boiler_type_title").getString(), x + 10, y + 30, 0xFFFFFFFF, false);
            com.gtceu.calcboard.api.type.GTBoilerTier curTier = com.gtceu.calcboard.api.type.GTBoilerTier.getBoilerTier(node);

            if (curTier.isMultiblock()) {
                int curThrottle = node.getBoilerThrottle();
                int thrX = x + dialogW - 250;
                String thrTitle = "§e⚡ " + Component.translatable("gui.gtcalcboard.boiler_throttle").getString() + ":";
                graphics.drawString(font, thrTitle, thrX, y + 30, 0xFFFFFFFF, false);
                int titleW = font.width(thrTitle);

                int minusX = thrX + titleW + 6;
                boolean minusHover = mouseX >= minusX && mouseX <= minusX + 14 && mouseY >= y + 28 && mouseY <= y + 40;
                graphics.fill(minusX, y + 28, minusX + 14, y + 40, minusHover ? 0xFF3D4558 : 0xFF242A35);
                graphics.renderOutline(minusX, y + 28, 14, 12, minusHover ? 0xFF58D3FF : 0xFF3F4658);
                graphics.drawCenteredString(font, "-", minusX + 7, y + 30, 0xFFFFFFFF);

                int valX = minusX + 16;
                graphics.fill(valX, y + 28, valX + 32, y + 40, 0xFF1B202A);
                graphics.renderOutline(valX, y + 28, 32, 12, 0xFF3F4658);
                graphics.drawCenteredString(font, curThrottle + "%", valX + 16, y + 30, 0xFF58D3FF);

                int plusX = valX + 34;
                boolean plusHover = mouseX >= plusX && mouseX <= plusX + 14 && mouseY >= y + 28 && mouseY <= y + 40;
                graphics.fill(plusX, y + 28, plusX + 14, y + 40, plusHover ? 0xFF3D4558 : 0xFF242A35);
                graphics.renderOutline(plusX, y + 28, 14, 12, plusHover ? 0xFF58D3FF : 0xFF3F4658);
                graphics.drawCenteredString(font, "+", plusX + 7, y + 30, 0xFFFFFFFF);

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

            com.gtceu.calcboard.api.type.GTBoilerTier[] bTiers = com.gtceu.calcboard.api.type.GTBoilerTier.values();
            boolean isLiquid = node.isLiquidBoilerRecipe();
            int btnW = 70;
            int gap = 4;
            int btnY = y + 44;
            com.gtceu.calcboard.api.type.GTBoilerTier hoveredTier = null;
            for (int i = 0; i < bTiers.length; i++) {
                com.gtceu.calcboard.api.type.GTBoilerTier bt = bTiers[i];
                boolean active = curTier == bt;
                int btnX = x + 10 + i * (btnW + gap);
                boolean hov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16;
                if (hov) hoveredTier = bt;
                graphics.fill(btnX, btnY, btnX + btnW, btnY + 16, active ? 0xFF5D3E1A : (hov ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, btnY, btnW, 16, active ? bt.getColor() : 0xFF3F4658);
                String speedLabel = String.format(Locale.ROOT, "%.1fx", bt.getSpeedMultiplier(isLiquid)).replace(".0x", "x");
                String label = (bt.isMultiblock() ? "▦ " : "♨ ") + (i == 0 ? "LP (" + speedLabel + ")" : (i == 1 ? "HP (" + speedLabel + ")" : (i == 2 ? "L-Brz" : (i == 3 ? "L-Stl" : (i == 4 ? "L-Ti" : "L-W")))));
                graphics.drawCenteredString(font, label, btnX + btnW / 2, btnY + 4, active ? bt.getColor() : 0xFFB0B8C8);
            }
            if (hoveredTier != null) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal((hoveredTier.isMultiblock() ? "§6▦ " : "§6♨ ") + hoveredTier.getDisplayName()));
                double thrMult = hoveredTier.isMultiblock() ? (node.getBoilerThrottle() / 100.0) : 1.0;
                double speed = hoveredTier.getSpeedMultiplier(isLiquid) * thrMult;
                double steamRate = hoveredTier.getSteamRatePerSec(isLiquid) * thrMult;
                String thrSuffix = hoveredTier.isMultiblock() && node.getBoilerThrottle() < 100 ? " §8(" + node.getBoilerThrottle() + "% Throttle)" : "";
                tooltip.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.summary.fuel_burn_speed", String.format(Locale.ROOT, "§e%.2f", speed), thrSuffix)));
                tooltip.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.summary.steam_output", String.format(Locale.ROOT, "§b%,.0f", steamRate), String.format(Locale.ROOT, "%,.0f", steamRate / 20.0))));
                showTooltip(dialog, graphics, font, tooltip, mouseX, mouseY);
            }
        } else if (!node.isMultiblock()) {
            if (node.supportsSteamMode()) {
                graphics.drawString(font, "§6♨ " + Component.translatable("gui.gtcalcboard.config.steam_mode_title").getString(), x + 10, y + 30, 0xFFFFFFFF, false);
                int btnX = x + 10;
                com.gtceu.calcboard.api.type.SteamMode curSteam = node.getSteamMode();

                boolean lpActive = curSteam == com.gtceu.calcboard.api.type.SteamMode.LOW_PRESSURE;
                boolean lpHover = mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 110, y + 60, lpActive ? 0xFF5D3E1A : (lpHover ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 110, 16, lpActive ? 0xFFD28C38 : 0xFF3F4658);
                graphics.drawCenteredString(font, "♨ LP Steam (0.5x)", btnX + 55, y + 48, lpActive ? 0xFFFFD28C : 0xFFB0B8C8);
                btnX += 116;

                boolean hpActive = curSteam == com.gtceu.calcboard.api.type.SteamMode.HIGH_PRESSURE;
                boolean hpHover = mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 110, y + 60, hpActive ? 0xFF4A4A4A : (hpHover ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 110, 16, hpActive ? 0xFFAAAAAA : 0xFF3F4658);
                graphics.drawCenteredString(font, "♨ HP Steam (1.0x)", btnX + 55, y + 48, hpActive ? 0xFFFFFFFF : 0xFFB0B8C8);
                btnX += 116;

                boolean elecActive = curSteam == com.gtceu.calcboard.api.type.SteamMode.NONE;
                boolean elecHover = mouseX >= btnX && mouseX <= btnX + 90 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 90, y + 60, elecActive ? 0xFF2A5288 : (elecHover ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 90, 16, elecActive ? 0xFF589CFF : 0xFF3F4658);
                graphics.drawCenteredString(font, "⚡ Electric", btnX + 45, y + 48, elecActive ? 0xFF58D3FF : 0xFFB0B8C8);
            } else {
                graphics.drawString(font, "§b" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_fixed").getString(), x + 10, y + 32, 0xFFFFFFFF, false);
                graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_desc").getString(), x + 10, y + 48, 0xFF888888, false);
            }
        } else if (GTCEuNodeCardGuiHandler.isFusionMachine(node)) {
            List<ResourceLocation> mbWorkstations = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
            if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
                mbWorkstations = List.of(node.getMachineIcon());
            }

            int totalCount = mbWorkstations.size();
            int curReflectorTier = node.getInstalledReflectorTier();
            int reqReflectorTier = node.getProperties().get(GTCEuProperties.REQUIRED_REFLECTOR_TIER);
            long reqStartEU = node.getProperties().get(GTCEuProperties.FUSION_START_EU);
            GTVoltageTier minTier = node.getMinFusionVoltageTier();

            String mbHeader = "§b⚛ " + Component.translatable("gui.gtcalcboard.config.multiblock_controller_title").getString() + " & " + Component.translatable("gui.gtcalcboard.config.reflector_tier_title").getString();
            graphics.drawString(font, mbHeader, x + 10, y + 28, 0xFFFFFFFF, false);

            String parSummary = (reqStartEU > 0) ? ("§e⚡ " + com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(reqStartEU) + " EU Start") : ("§7⚡ " + node.getTotalParallel() + "x Par");
            int parSummaryW = font.width(parSummary);
            graphics.drawString(font, parSummary, x + dialogW - 10 - parSummaryW, y + 28, 0xFFFFFFFF, false);

            int controllersAreaW = dialogW - 20;
            List<Integer> ctrlWidths = new ArrayList<>();
            int totalCtrlW = 0;
            for (ResourceLocation ws : mbWorkstations) {
                String label = getMultiblockShortLabel(ws);
                int w = Math.max(68, font.width(label) + 12);
                ctrlWidths.add(w);
                totalCtrlW += w + 3;
            }
            if (!ctrlWidths.isEmpty()) totalCtrlW -= 3;

            state.setMaxHeaderRow1ScrollX(Math.max(0, totalCtrlW - controllersAreaW));
            state.setHeaderRow1ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow1ScrollX(), state.getHeaderRow1ScrollX())));

            ResourceLocation hoveredController = null;
            boolean hoveredControllerLocked = false;
            String hoveredControllerReq = "";

            BoardScissorHelper.enableScissor(graphics, x + 10, y + 36, x + 10 + controllersAreaW, y + 51);
            graphics.pose().pushPose();
            graphics.pose().translate((float) -state.getHeaderRow1ScrollX(), 0, 0);

            int curX = x + 10;
            for (int i = 0; i < totalCount; i++) {
                ResourceLocation mbWs = mbWorkstations.get(i);
                int w = ctrlWidths.get(i);
                boolean isSelected = mbWs.equals(node.getMachineIcon());
                double vMouseX = mouseX + state.getHeaderRow1ScrollX();
                boolean hov = mouseX >= x + 10 && mouseX <= x + 10 + controllersAreaW && vMouseX >= curX && vMouseX <= curX + w && mouseY >= y + 38 && mouseY <= y + 50;

                GTVoltageTier ctrlTier = GTCEuModAdapter.extractVoltageTierFromIcon(mbWs);
                boolean isTierSufficient = (ctrlTier == null || minTier == null || ctrlTier.getVoltage() >= minTier.getVoltage());

                if (hov) {
                    hoveredController = mbWs;
                    hoveredControllerLocked = !isTierSufficient;
                    hoveredControllerReq = !isTierSufficient ? Component.translatable("gui.gtcalcboard.config.fusion_mk_req_tooltip", minTier != null ? minTier.getName() : "Mk2", com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(reqStartEU)).getString() : null;
                }

                int fill = !isTierSufficient ? (hov ? 0xFF4A1E24 : 0xFF3A1C22) : (isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B));
                int border = !isTierSufficient ? 0xFFFF5555 : (isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658));

                graphics.fill(curX, y + 38, curX + w, y + 50, fill);
                graphics.renderOutline(curX, y + 38, w, 12, border);

                String label = getMultiblockShortLabel(mbWs);
                if (!isTierSufficient) {
                    label = "✕ " + label;
                }
                int textCol = !isTierSufficient ? 0xFFFF8888 : (isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8));
                graphics.drawCenteredString(font, font.plainSubstrByWidth(label, w - 4), curX + w / 2, y + 40, textCol);
                curX += w + 3;
            }

            graphics.pose().popPose();
            BoardScissorHelper.disableScissor(graphics);

            if (state.getMaxHeaderRow1ScrollX() > 0) {
                if (state.getHeaderRow1ScrollX() > 2) {
                    graphics.fill(x + 10, y + 38, x + 18, y + 50, 0xCC181C26);
                    graphics.drawCenteredString(font, "◀", x + 14, y + 40, 0xFF80D0FF);
                }
                if (state.getHeaderRow1ScrollX() < state.getMaxHeaderRow1ScrollX() - 2) {
                    graphics.fill(x + 10 + controllersAreaW - 8, y + 38, x + 10 + controllersAreaW, y + 50, 0xCC181C26);
                    graphics.drawCenteredString(font, "▶", x + 10 + controllersAreaW - 4, y + 40, 0xFF80D0FF);
                }
            }

            List<Integer> availableReflectorTiers = ReflectorHelper.getAvailableReflectorTiers();
            int reflAreaW = dialogW - 20;
            List<Integer> reflWidths = new ArrayList<>();
            int totalReflW = 0;

            for (int t : availableReflectorTiers) {
                String rLabel = (t == 0) ? Component.translatable("gui.gtcalcboard.reflector.none").getString() : ("✦ T" + t);
                int w = Math.max(48, font.width(rLabel) + 12);
                reflWidths.add(w);
                totalReflW += w + 3;
            }
            if (!reflWidths.isEmpty()) totalReflW -= 3;

            state.setMaxHeaderRow2ScrollX(Math.max(0, totalReflW - reflAreaW));
            state.setHeaderRow2ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow2ScrollX(), state.getHeaderRow2ScrollX())));

            int hoveredReflectorTier = -1;
            boolean hoveredReflectorLocked = false;

            BoardScissorHelper.enableScissor(graphics, x + 10, y + 51, x + 10 + reflAreaW, y + 66);
            graphics.pose().pushPose();
            graphics.pose().translate((float) -state.getHeaderRow2ScrollX(), 0, 0);

            int rCurX = x + 10;
            for (int i = 0; i < availableReflectorTiers.size(); i++) {
                int t = availableReflectorTiers.get(i);
                int w = reflWidths.get(i);
                boolean isSelected = (t == curReflectorTier);
                boolean isSufficient = (reqReflectorTier <= 0 || t >= reqReflectorTier);

                double vMouseX = mouseX + state.getHeaderRow2ScrollX();
                boolean hov = mouseX >= x + 10 && mouseX <= x + 10 + reflAreaW && vMouseX >= rCurX && vMouseX <= rCurX + w && mouseY >= y + 52 && mouseY <= y + 64;

                if (hov) {
                    hoveredReflectorTier = t;
                    hoveredReflectorLocked = !isSufficient;
                }

                int fill = !isSufficient ? (hov ? 0xFF4A1E24 : 0xFF3A1C22) : (isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B));
                int border = !isSufficient ? 0xFFFF5555 : (isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658));

                graphics.fill(rCurX, y + 52, rCurX + w, y + 64, fill);
                graphics.renderOutline(rCurX, y + 52, w, 12, border);

                String rLabel = (t == 0) ? Component.translatable("gui.gtcalcboard.reflector.none").getString() : ("✦ T" + t);
                if (!isSufficient) {
                    rLabel = "✕ " + rLabel;
                }
                int textCol = !isSufficient ? 0xFFFF8888 : (isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8));
                graphics.drawCenteredString(font, font.plainSubstrByWidth(rLabel, w - 4), rCurX + w / 2, y + 54, textCol);

                rCurX += w + 3;
            }

            graphics.pose().popPose();
            BoardScissorHelper.disableScissor(graphics);

            if (state.getMaxHeaderRow2ScrollX() > 0) {
                if (state.getHeaderRow2ScrollX() > 2) {
                    graphics.fill(x + 10, y + 52, x + 18, y + 64, 0xCC181C26);
                    graphics.drawCenteredString(font, "◀", x + 14, y + 54, 0xFF80D0FF);
                }
                if (state.getHeaderRow2ScrollX() < state.getMaxHeaderRow2ScrollX() - 2) {
                    graphics.fill(x + 10 + reflAreaW - 8, y + 52, x + 10 + reflAreaW, y + 64, 0xCC181C26);
                    graphics.drawCenteredString(font, "▶", x + 10 + reflAreaW - 4, y + 54, 0xFF80D0FF);
                }
            }

            if (hoveredController != null) {
                List<Component> tt = new ArrayList<>();
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(hoveredController);
                String fullName = (item != null && item != net.minecraft.world.item.Items.AIR) ? item.getDescription().getString() : hoveredController.getPath();
                tt.add(Component.literal("§e▦ " + fullName));
                tt.add(Component.literal("§8" + hoveredController));
                if (hoveredControllerLocked) {
                    tt.add(Component.literal("§c❌ " + hoveredControllerReq));
                } else if (hoveredController.equals(node.getMachineIcon())) {
                    tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.config.active_controller").getString()));
                } else {
                    tt.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.action.click_to_select")));
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            } else if (hoveredReflectorTier >= 0) {
                List<Component> tt = new ArrayList<>();
                if (hoveredReflectorTier == 0) {
                    tt.add(Component.literal("§e✦ " + Component.translatable("gui.gtcalcboard.reflector.none").getString()));
                    tt.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.reflector.none_desc").getString()));
                } else {
                    appendReflectorDescription(tt, hoveredReflectorTier);
                }
                if (hoveredReflectorLocked) {
                    tt.add(Component.literal("§c❌ " + Component.translatable("gui.gtcalcboard.config.reflector_req_tooltip", reqReflectorTier).getString()));
                } else if (hoveredReflectorTier == curReflectorTier) {
                    tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.reflector_met").getString()));
                } else {
                    tt.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.action.click_to_equip")));
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            }
        } else if (GTCEuNodeCardGuiHandler.isCoilMultiblock(node)) {
            List<ResourceLocation> mbWorkstations = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
            if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
                mbWorkstations = List.of(node.getMachineIcon());
            }

            int defPar = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getDefaultParallel(node);
            int totalCount = mbWorkstations.size();
            boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
            int parBtnW = supportsParHatch ? 120 : 0;
            int parBtnX = x + dialogW - 10 - parBtnW;
            int controllersAreaW = supportsParHatch ? (parBtnX - (x + 10) - 6) : (dialogW - 20);

            MachineAddon equippedParallel = null;
            for (MachineAddon a : node.getAddons()) {
                if (a != null && a.getCategory() == MachineAddon.Category.PARALLEL) {
                    equippedParallel = a;
                    break;
                }
            }

            int curCoilTemp = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getInstalledCoilTemperature(node);
            int reqCoilTemp = node.getProperties().get(GTCEuProperties.EBF_TEMPERATURE);
            if (reqCoilTemp <= 0) reqCoilTemp = node.getRecipeTemperature();

            String mbHeader = "§b▦ " + Component.translatable("gui.gtcalcboard.config.multiblock_controller_title").getString() + " & " + Component.translatable("gui.gtcalcboard.config.coil_tier_title").getString();
            graphics.drawString(font, mbHeader, x + 10, y + 28, 0xFFFFFFFF, false);

            String parSummary = "§7⚡ " + node.getTotalParallel() + "x Par" + (defPar > 1 ? " (Default " + defPar + "x)" : (node.getTotalParallel() > 1 ? " (Base " + node.getParallel() + "x)" : " (Default 1x)"));
            int parSummaryW = font.width(parSummary);
            graphics.drawString(font, parSummary, x + dialogW - 10 - parSummaryW, y + 28, 0xFFFFFFFF, false);

            List<Integer> ctrlWidths = new ArrayList<>();
            int totalCtrlW = 0;
            for (ResourceLocation ws : mbWorkstations) {
                String label = getMultiblockShortLabel(ws);
                int w = Math.max(64, font.width(label) + 12);
                ctrlWidths.add(w);
                totalCtrlW += w + 3;
            }
            if (!ctrlWidths.isEmpty()) totalCtrlW -= 3;

            state.setMaxHeaderRow1ScrollX(Math.max(0, totalCtrlW - controllersAreaW));
            state.setHeaderRow1ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow1ScrollX(), state.getHeaderRow1ScrollX())));

            ResourceLocation hoveredController = null;

            BoardScissorHelper.enableScissor(graphics, x + 10, y + 36, x + 10 + controllersAreaW, y + 51);
            graphics.pose().pushPose();
            graphics.pose().translate((float) -state.getHeaderRow1ScrollX(), 0, 0);

            int curX = x + 10;
            for (int i = 0; i < totalCount; i++) {
                ResourceLocation mbWs = mbWorkstations.get(i);
                int w = ctrlWidths.get(i);
                boolean isSelected = mbWs.equals(node.getMachineIcon());
                double vMouseX = mouseX + state.getHeaderRow1ScrollX();
                boolean hov = mouseX >= x + 10 && mouseX <= x + 10 + controllersAreaW && vMouseX >= curX && vMouseX <= curX + w && mouseY >= y + 38 && mouseY <= y + 50;
                if (hov) hoveredController = mbWs;

                int fill = isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B);
                int border = isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658);

                graphics.fill(curX, y + 38, curX + w, y + 50, fill);
                graphics.renderOutline(curX, y + 38, w, 12, border);

                String label = getMultiblockShortLabel(mbWs);
                int textCol = isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8);
                graphics.drawCenteredString(font, font.plainSubstrByWidth(label, w - 4), curX + w / 2, y + 40, textCol);
                curX += w + 3;
            }

            graphics.pose().popPose();
            BoardScissorHelper.disableScissor(graphics);

            if (state.getMaxHeaderRow1ScrollX() > 0) {
                if (state.getHeaderRow1ScrollX() > 2) {
                    graphics.fill(x + 10, y + 38, x + 18, y + 50, 0xCC181C26);
                    graphics.drawCenteredString(font, "◀", x + 14, y + 40, 0xFF80D0FF);
                }
                if (state.getHeaderRow1ScrollX() < state.getMaxHeaderRow1ScrollX() - 2) {
                    graphics.fill(x + 10 + controllersAreaW - 8, y + 38, x + 10 + controllersAreaW, y + 50, 0xCC181C26);
                    graphics.drawCenteredString(font, "▶", x + 10 + controllersAreaW - 4, y + 40, 0xFF80D0FF);
                }
            }

            if (supportsParHatch) {
                boolean parHov = mouseX >= parBtnX && mouseX <= parBtnX + parBtnW && mouseY >= y + 38 && mouseY <= y + 50;
                if (equippedParallel != null) {
                    graphics.fill(parBtnX, y + 38, parBtnX + parBtnW, y + 50, parHov ? 0xFF3A1C22 : 0xFF202B38);
                    graphics.renderOutline(parBtnX, y + 38, parBtnW, 12, parHov ? 0xFFFF6B6B : 0xFF45B074);
                    String parText = parHov ? ("✕ " + Component.translatable("gui.gtcalcboard.config.remove").getString())
                            : ("⚡ " + equippedParallel.getParallelMultiplier() + "x " + Component.translatable("gui.gtcalcboard.addon_cat.parallel").getString());
                    graphics.drawCenteredString(font, font.plainSubstrByWidth(parText, parBtnW - 4), parBtnX + parBtnW / 2, y + 40, parHov ? 0xFFFF8888 : 0xFF55FF88);
                } else {
                    graphics.fill(parBtnX, y + 38, parBtnX + parBtnW, y + 50, parHov ? 0xFF2B3A50 : 0xFF202633);
                    graphics.renderOutline(parBtnX, y + 38, parBtnW, 12, parHov ? 0xFF589CFF : 0xFF3F506B);
                    String pLabel = Component.translatable("gui.gtcalcboard.config.install_parallel_hatch").getString();
                    graphics.drawCenteredString(font, font.plainSubstrByWidth(pLabel, parBtnW - 4), parBtnX + parBtnW / 2, y + 40, parHov ? 0xFF80D0FF : 0xFF58A6FF);
                }
            }

            List<MachineAddon> allCoils = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getAllCoils();
            int coilAreaW = dialogW - 20;

            List<Integer> coilWidths = new ArrayList<>();
            int totalCoilsW = 0;
            for (MachineAddon coil : allCoils) {
                String label = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getCoilShortLabel(coil);
                int w = Math.max(54, font.width(label) + 12);
                coilWidths.add(w);
                totalCoilsW += w + 3;
            }
            if (!coilWidths.isEmpty()) totalCoilsW -= 3;

            state.setMaxHeaderRow2ScrollX(Math.max(0, totalCoilsW - coilAreaW));
            state.setHeaderRow2ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow2ScrollX(), state.getHeaderRow2ScrollX())));

            MachineAddon hoveredCoil = null;
            boolean hoveredCoilLocked = false;

            BoardScissorHelper.enableScissor(graphics, x + 10, y + 51, x + 10 + coilAreaW, y + 66);
            graphics.pose().pushPose();
            graphics.pose().translate((float) -state.getHeaderRow2ScrollX(), 0, 0);

            int cCurX = x + 10;
            for (int cIdx = 0; cIdx < allCoils.size(); cIdx++) {
                MachineAddon coil = allCoils.get(cIdx);
                int w = coilWidths.get(cIdx);
                int cTemp = (coil instanceof com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon gtCoil) ? gtCoil.getCoilTemperature() : 1800;
                boolean isSelected = (cTemp == curCoilTemp);
                boolean isSufficient = (reqCoilTemp <= 0 || cTemp >= reqCoilTemp);

                double vMouseX = mouseX + state.getHeaderRow2ScrollX();
                boolean hov = mouseX >= x + 10 && mouseX <= x + 10 + coilAreaW && vMouseX >= cCurX && vMouseX <= cCurX + w && mouseY >= y + 52 && mouseY <= y + 64;

                if (hov) {
                    hoveredCoil = coil;
                    hoveredCoilLocked = !isSufficient;
                }

                int fill = !isSufficient ? (hov ? 0xFF4A1E24 : 0xFF3A1C22) : (isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B));
                int border = !isSufficient ? 0xFFFF5555 : (isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658));

                graphics.fill(cCurX, y + 52, cCurX + w, y + 64, fill);
                graphics.renderOutline(cCurX, y + 52, w, 12, border);

                String cLabel = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getCoilShortLabel(coil);
                if (!isSufficient) {
                    cLabel = "✕ " + cLabel;
                }
                int textCol = !isSufficient ? 0xFFFF8888 : (isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8));
                graphics.drawCenteredString(font, font.plainSubstrByWidth(cLabel, w - 4), cCurX + w / 2, y + 54, textCol);

                cCurX += w + 3;
            }

            graphics.pose().popPose();
            BoardScissorHelper.disableScissor(graphics);

            if (state.getMaxHeaderRow2ScrollX() > 0) {
                if (state.getHeaderRow2ScrollX() > 2) {
                    graphics.fill(x + 10, y + 52, x + 18, y + 64, 0xCC181C26);
                    graphics.drawCenteredString(font, "◀", x + 14, y + 54, 0xFF80D0FF);
                }
                if (state.getHeaderRow2ScrollX() < state.getMaxHeaderRow2ScrollX() - 2) {
                    graphics.fill(x + 10 + coilAreaW - 8, y + 52, x + 10 + coilAreaW, y + 64, 0xCC181C26);
                    graphics.drawCenteredString(font, "▶", x + 10 + coilAreaW - 4, y + 54, 0xFF80D0FF);
                }
            }

            if (hoveredController != null) {
                List<Component> tt = new ArrayList<>();
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(hoveredController);
                String fullName = (item != null && item != net.minecraft.world.item.Items.AIR) ? item.getDescription().getString() : hoveredController.getPath();
                tt.add(Component.literal("§e▦ " + fullName));
                tt.add(Component.literal("§8" + hoveredController));
                if (hoveredController.equals(node.getMachineIcon())) {
                    tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.config.active_controller").getString()));
                } else {
                    tt.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.action.click_to_select")));
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            } else if (hoveredCoil != null) {
                List<Component> tt = new ArrayList<>();
                tt.add(Component.literal("§6♨ " + hoveredCoil.getName()));
                for (String line : hoveredCoil.getDescription().split("\n")) {
                    tt.add(Component.literal("§7" + line));
                }
                if (hoveredCoilLocked) {
                    tt.add(Component.literal("§c❌ " + Component.translatable("gui.gtcalcboard.config.coil_req_tooltip", reqCoilTemp).getString()));
                } else if (hoveredCoil instanceof com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon gtCoil && gtCoil.getCoilTemperature() == curCoilTemp) {
                    tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.coil_met").getString()));
                } else {
                    tt.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.action.click_to_equip")));
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            }
        } else {
            List<ResourceLocation> mbWorkstations = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
            if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
                mbWorkstations = List.of(node.getMachineIcon());
            }

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
            if (GTCEuMachineDialogState.getMbControllerScroll() > maxScroll) GTCEuMachineDialogState.setMbControllerScroll(maxScroll);

            String navIndicator = showNav ? " (" + (GTCEuMachineDialogState.getMbControllerScroll() + 1) + "-" + Math.min(totalCount, GTCEuMachineDialogState.getMbControllerScroll() + visibleCount) + "/" + totalCount + ")" : "";
            String mbHeader = "§b▦ " + Component.translatable("gui.gtcalcboard.config.multiblock_controller_title").getString() + "§7" + navIndicator;
            graphics.drawString(font, mbHeader, x + 10, y + 30, 0xFFFFFFFF, false);

            String parSummary = "§7⚡ " + node.getTotalParallel() + "x Par" + (defPar > 1 ? " (Default " + defPar + "x)" : (node.getTotalParallel() > 1 ? " (Base " + node.getParallel() + "x)" : " (Default 1x)"));
            int parSummaryW = font.width(parSummary);
            graphics.drawString(font, parSummary, x + dialogW - 10 - parSummaryW, y + 30, 0xFFFFFFFF, false);

            int curX = x + 10;
            int btnW;

            if (showNav) {
                int navBtnW = 16;
                boolean leftHov = mouseX >= curX && mouseX <= curX + navBtnW && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(curX, y + 44, curX + navBtnW, y + 60, leftHov ? 0xFF3D4558 : 0xFF282D3B);
                graphics.renderOutline(curX, y + 44, navBtnW, 16, leftHov ? 0xFF58D3FF : 0xFF3F4658);
                graphics.drawCenteredString(font, "◀", curX + navBtnW / 2, y + 48, GTCEuMachineDialogState.getMbControllerScroll() > 0 ? 0xFFFFFFFF : 0xFF666666);
                curX += navBtnW + 4;
                btnW = (controllersAreaW - 40 - (visibleCount - 1) * 4) / visibleCount;
            } else {
                btnW = (controllersAreaW - (visibleCount - 1) * 4) / Math.max(1, visibleCount);
            }

            ResourceLocation hoveredController = null;
            int startIdx = showNav ? GTCEuMachineDialogState.getMbControllerScroll() : 0;
            int endIdx = showNav ? Math.min(totalCount, GTCEuMachineDialogState.getMbControllerScroll() + visibleCount) : totalCount;

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
                boolean rightHov = mouseX >= curX && mouseX <= curX + navBtnW && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(curX, y + 44, curX + navBtnW, y + 60, rightHov ? 0xFF3D4558 : 0xFF282D3B);
                graphics.renderOutline(curX, y + 44, navBtnW, 16, rightHov ? 0xFF58D3FF : 0xFF3F4658);
                graphics.drawCenteredString(font, "▶", curX + navBtnW / 2, y + 48, GTCEuMachineDialogState.getMbControllerScroll() < maxScroll ? 0xFFFFFFFF : 0xFF666666);
            }

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
                tt.add(Component.literal("§e▦ " + fullName));
                tt.add(Component.literal("§8" + hoveredController));
                boolean active = hoveredController.equals(node.getMachineIcon());
                if (active) {
                    tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.config.active_controller").getString()));
                } else {
                    tt.add(Component.literal("§7").append(Component.translatable("gui.gtcalcboard.action.click_to_select")));
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            }
        }
    }

    public static String getMultiblockShortLabel(ResourceLocation id) {
        if (id == null) return "▦ Multi";
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("auxiliary_booster_fusion") || path.contains("auxiliary_fusion") || path.contains("aux_booster")) {
            if (path.contains("mk2") || path.contains("mk_2") || path.contains("ii") || path.contains("aux2") || path.contains("aux_2") || path.contains("uiv")) return "⚡ Aux Mk2";
            if (path.contains("mk3") || path.contains("mk_3") || path.contains("iii") || path.contains("aux3") || path.contains("aux_3") || path.contains("opv")) return "⚡ Aux Mk3";
            return "⚡ Aux Mk1";
        }
        if (path.contains("reflector_fusion")) return "⚛ Reflector";
        if (path.contains("luv_fusion") || path.contains("fusion_reactor_mk1") || path.contains("fusion_mk1") || path.contains("mk_1") || path.contains("mk1") || path.contains("mki")) return "⚛ Fusion Mk1";
        if (path.contains("zpm_fusion") || path.contains("fusion_reactor_mk2") || path.contains("fusion_mk2") || path.contains("mk_2") || path.contains("mk2") || path.contains("mkii")) return "⚛ Fusion Mk2";
        if (path.contains("uv_fusion") || path.contains("fusion_reactor_mk3") || path.contains("fusion_mk3") || path.contains("mk_3") || path.contains("mk3") || path.contains("mkiii")) return "⚛ Fusion Mk3";
        if (path.contains("uev_fusion") || path.contains("fusion_reactor_mk4") || path.contains("fusion_mk4") || path.contains("mk_4") || path.contains("mk4") || path.contains("mkiv")) return "⚛ Fusion Mk4";
        if (path.contains("uxv_fusion") || path.contains("fusion_reactor_mk5") || path.contains("fusion_mk5") || path.contains("mk_5") || path.contains("mk5") || path.contains("mkv")) return "⚛ Fusion Mk5";
        if (path.contains("max_fusion") || path.contains("fusion_reactor_mk6") || path.contains("fusion_mk6") || path.contains("mk_6") || path.contains("mk6") || path.contains("mkvi")) return "⚛ Fusion Mk6";
        if (path.contains("extreme_chemical_reactor") || path.equals("ecr")) return "⚡ ECR";
        if (path.contains("incomprehensible_chemical_reactor") || path.equals("icr")) return "⚡ ICR";
        if (path.contains("large_chemical_reactor") || path.equals("lcr")) return "▦ LCR";
        if (path.contains("super_cracker") || path.contains("sdf")) return "⚡ SDF Cracker";
        if (path.contains("cracker")) return "▦ Cracker";
        if (path.contains("supreme")) return "⚡ Supreme";
        if (path.contains("nyinsane")) return "⚡ Nyinsane";
        if (path.contains("large_fluid_distillation") || path.contains("large_distillation")) return "▦ Large DT";
        if (path.contains("distillation_tower")) return "▦ Distillation";
        if (path.contains("yielding_exhaustor") || path.contains("yeast")) return "✦ Yeast";

        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            String name = item.getDescription().getString();
            name = name.replaceAll("\\[.*?\\]", "").trim();
            if (name.contains("Fusion Reactor")) {
                if (name.contains("MK VI") || name.contains("Mk 6") || name.contains("Mk.6") || name.contains("MK 6") || name.contains("VI")) return "⚛ Fusion Mk6";
                if (name.contains("MK V") || name.contains("Mk 5") || name.contains("Mk.5") || name.contains("MK 5") || name.contains("V")) return "⚛ Fusion Mk5";
                if (name.contains("MK IV") || name.contains("Mk 4") || name.contains("Mk.4") || name.contains("MK 4") || name.contains("IV")) return "⚛ Fusion Mk4";
                if (name.contains("MK III") || name.contains("Mk 3") || name.contains("Mk.3") || name.contains("MK 3") || name.contains("III")) return "⚛ Fusion Mk3";
                if (name.contains("MK II") || name.contains("Mk 2") || name.contains("Mk.2") || name.contains("MK 2") || name.contains("II")) return "⚛ Fusion Mk2";
                if (name.contains("MK I") || name.contains("Mk 1") || name.contains("Mk.1") || name.contains("MK 1") || name.contains("I")) return "⚛ Fusion Mk1";
                return "⚛ Fusion";
            }
            if (name.contains("Auxiliary Booster") || name.contains("Auxiliary Fusion")) {
                if (name.contains("III") || name.contains("3")) return "⚡ Aux Mk3";
                if (name.contains("II") || name.contains("2")) return "⚡ Aux Mk2";
                return "⚡ Aux Mk1";
            }
            if (name.contains("Reflector Fusion")) return "⚛ Reflector";
            if (name.startsWith("Advanced ")) name = "Adv. " + name.substring(9);
            else if (name.startsWith("Elite ")) name = "Elite " + name.substring(6);
            else if (name.startsWith("Ultimate ")) name = "Ult. " + name.substring(9);
            else if (name.startsWith("Material Processing ")) name = "Mat. Proc. " + name.substring(20);
            return "▦ " + name;
        }
        return "▦ " + id.getPath();
    }

    private void renderCombustionDialogHeader(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
                                              int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                              EditBox parallelBox, BoardScreen parent) {
        double totEUt = node.getEffectiveTotalEUt();
        GTVoltageTier tier = node.getTargetTier() != null ? node.getTargetTier() : GTVoltageTier.EV;
        double amps = totEUt / (double) Math.max(1L, tier.getVoltage());
        String name = node.getName() != null && !node.getName().isEmpty() ? node.getName() : "Combustion Engine";

        int resetBtnW = Math.max(48, font.width("↺ " + Component.translatable("gui.gtcalcboard.rotor.reset_btn").getString()) + 8);
        int resetBtnX = x + dialogW - 10 - resetBtnW;
        boolean resetHover = mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= y + 28 && mouseY <= y + 42;
        graphics.fill(resetBtnX, y + 28, resetBtnX + resetBtnW, y + 42, resetHover ? 0xFF3E485A : 0xFF242A35);
        graphics.renderOutline(resetBtnX, y + 28, resetBtnW, 14, resetHover ? 0xFF58D3FF : 0xFF4A556B);
        graphics.drawCenteredString(font, "↺ " + Component.translatable("gui.gtcalcboard.rotor.reset_btn").getString(), resetBtnX + resetBtnW / 2, y + 31, 0xFFFFFFFF);

        String info = String.format(Locale.ROOT, "§6⚙ §f%s §7| §a⚡ +%,.1f EU/t §7(§e%.2fA %s§7)", name, totEUt, amps, tier.getName());
        graphics.drawString(font, info, x + 10, y + 31, 0xFFFFFFFF, false);

        int btnY = y + 46;
        int curX = x + 10;
        int gap = 4;

        boolean isLCE = GTCombustionHelper.isLargeCombustionEngine(node);
        boolean isECE = GTCombustionHelper.isExtremeCombustionEngine(node);
        boolean isStarT = GTCombustionHelper.isStarTModule(node) || GTCombustionHelper.isModularCombustionFrame(node);

        boolean boostBtnHover = false;
        boolean coolantBtnHover = false;

        if (isLCE) {
            boolean o2 = GTCombustionHelper.isOxygenBoosted(node);
            String label = (o2 ? "§b💨 " : "§7💨 ") + Component.translatable("gui.gtcalcboard.addon.oxygen_boost").getString() + (o2 ? " §a[ON]" : " §7[OFF]");
            int btnW = Math.max(140, font.width(label) + 12);
            boostBtnHover = mouseX >= curX && mouseX <= curX + btnW && mouseY >= btnY && mouseY <= btnY + 16;
            graphics.fill(curX, btnY, curX + btnW, btnY + 16, o2 ? (boostBtnHover ? 0xFF1C4535 : 0xFF143025) : (boostBtnHover ? 0xFF2A3548 : 0xFF1E2430));
            graphics.renderOutline(curX, btnY, btnW, 16, o2 ? (boostBtnHover ? 0xFF55FFAA : 0xFF33CC88) : (boostBtnHover ? 0xFF58D3FF : 0xFF3D4B60));
            graphics.drawCenteredString(font, label, curX + btnW / 2, btnY + 4, o2 ? 0xFF55FFAA : 0xFF8FA0B8);
            curX += btnW + gap;
        } else if (isECE) {
            boolean lox = GTCombustionHelper.isLiquidOxygenBoosted(node);
            String label = (lox ? "§b💨 " : "§7💨 ") + Component.translatable("gui.gtcalcboard.addon.liquid_oxygen_boost").getString() + (lox ? " §a[ON]" : " §7[OFF]");
            int btnW = Math.max(140, font.width(label) + 12);
            boostBtnHover = mouseX >= curX && mouseX <= curX + btnW && mouseY >= btnY && mouseY <= btnY + 16;
            graphics.fill(curX, btnY, curX + btnW, btnY + 16, lox ? (boostBtnHover ? 0xFF1C4535 : 0xFF143025) : (boostBtnHover ? 0xFF2A3548 : 0xFF1E2430));
            graphics.renderOutline(curX, btnY, btnW, 16, lox ? (boostBtnHover ? 0xFF55FFAA : 0xFF33CC88) : (boostBtnHover ? 0xFF58D3FF : 0xFF3D4B60));
            graphics.drawCenteredString(font, label, curX + btnW / 2, btnY + 4, lox ? 0xFF55FFAA : 0xFF8FA0B8);
            curX += btnW + gap;
        } else if (isStarT) {
            if (GTCombustionHelper.isStarTModule(node)) {
                String ox = node.getProperties().get(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE);
                boolean oxActive = ox != null && !ox.isEmpty() && !"none".equalsIgnoreCase(ox);
                String oxLabel = "💨 " + (oxActive ? ("§b" + GTCombustionHelper.getOxidizerDisplayName(ox) + " §a(2x Fuel, Amp Boost)") : "§7Oxidizer: None");
                int oxBtnW = Math.max(120, font.width(oxLabel) + 12);
                boostBtnHover = mouseX >= curX && mouseX <= curX + oxBtnW && mouseY >= btnY && mouseY <= btnY + 16;
                graphics.fill(curX, btnY, curX + oxBtnW, btnY + 16, oxActive ? (boostBtnHover ? 0xFF1C4535 : 0xFF143025) : (boostBtnHover ? 0xFF2A3548 : 0xFF1E2430));
                graphics.renderOutline(curX, btnY, oxBtnW, 16, oxActive ? 0xFF33CC88 : 0xFF3D4B60);
                graphics.drawCenteredString(font, oxLabel, curX + oxBtnW / 2, btnY + 4, oxActive ? 0xFF55FFAA : 0xFF8FA0B8);
                curX += oxBtnW + gap;
            }

            String cl = node.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE);
            boolean clActive = cl != null && !cl.isEmpty() && !"none".equalsIgnoreCase(cl);
            String clLabel = "❄ " + (clActive ? ("§b" + GTCombustionHelper.getCoolantDisplayName(cl)) : "§7Coolant: None");
            int clBtnW = Math.max(110, font.width(clLabel) + 12);
            coolantBtnHover = mouseX >= curX && mouseX <= curX + clBtnW && mouseY >= btnY && mouseY <= btnY + 16;
            graphics.fill(curX, btnY, curX + clBtnW, btnY + 16, clActive ? (coolantBtnHover ? 0xFF1B3854 : 0xFF14273D) : (coolantBtnHover ? 0xFF2A3548 : 0xFF1E2430));
            graphics.renderOutline(curX, btnY, clBtnW, 16, clActive ? 0xFF58D3FF : 0xFF3D4B60);
            graphics.drawCenteredString(font, clLabel, curX + clBtnW / 2, btnY + 4, clActive ? 0xFF58D3FF : 0xFF8FA0B8);
        }

        if (boostBtnHover) {
            List<Component> tt = new ArrayList<>();
            if (isLCE) {
                tt.add(Component.literal("§b💨 " + Component.translatable("gui.gtcalcboard.addon.oxygen_boost").getString()));
                tt.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.addon.oxygen_boost.desc").getString()));
                tt.add(Component.literal("§8* " + Component.translatable("gui.gtcalcboard.tooltip.click_toggle").getString()));
            } else if (isECE) {
                tt.add(Component.literal("§b💨 " + Component.translatable("gui.gtcalcboard.addon.liquid_oxygen_boost").getString()));
                tt.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.addon.liquid_oxygen_boost.desc").getString()));
                tt.add(Component.literal("§8* " + Component.translatable("gui.gtcalcboard.tooltip.click_toggle").getString()));
            } else if (isStarT) {
                tt.add(Component.literal("§b💨 " + Component.translatable("gui.gtcalcboard.addon_cat.trait").getString() + ": " + Component.translatable("gui.gtcalcboard.tooltip.oxidizer_boost").getString()));
                tt.add(Component.literal("§8* " + Component.translatable("gui.gtcalcboard.tooltip.click_toggle").getString()));
            }
            if (!tt.isEmpty()) {
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            }
        } else if (coolantBtnHover && isStarT) {
            List<Component> tt = new ArrayList<>();
            tt.add(Component.literal("§b❄ " + Component.translatable("gui.gtcalcboard.tooltip.coolant_boost").getString()));
            tt.add(Component.literal("§8* " + Component.translatable("gui.gtcalcboard.tooltip.click_toggle").getString()));
            showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
        }
    }

    private static String formatRotorLifespan(RecipeNode node, boolean compact) {
        if (!GTTurbineHelper.hasRotorAddon(node)) return "";
        double lifespanHours = GTTurbineHelper.calculateRotorLifespanHours(node);
        if (Double.isInfinite(lifespanHours) || lifespanHours <= 0) return "";
        return compact
                ? String.format(Locale.ROOT, " §7| §6⌛%.1fh", lifespanHours)
                : String.format(Locale.ROOT, " §7| §6⌛ %.1fh", lifespanHours);
    }

    private static void appendRotorLifespanTooltip(List<Component> tt, RecipeNode node) {
        double wearPerSec = GTTurbineHelper.calculateRotorWearPerSecond(node);
        double lifespanHours = GTTurbineHelper.calculateRotorLifespanHours(node);
        double replacementRate = GTTurbineHelper.calculateRotorReplacementRatePerHour(node);
        tt.add(Component.literal("§8§m------------------------"));
        tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.rotor_wear_rate").getString() + ": §c-%,.2f dmg/s", wearPerSec)));
        if (Double.isInfinite(lifespanHours) || lifespanHours <= 0) return;
        tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.rotor_lifespan").getString() + ": §e%,.2f h", lifespanHours)));
        if (replacementRate > 0) {
            tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.rotor_replacement_rate").getString() + ": §6%,.4f /h", replacementRate)));
        }
    }

    private static void appendReflectorDescription(List<Component> tt, int hoveredReflectorTier) {
        MachineAddon refAddon = ReflectorHelper.getReflectorForTier(hoveredReflectorTier);
        String titleName = (refAddon != null) ? refAddon.getName() : Component.translatable("gui.gtcalcboard.addon.reflector_tier_name", hoveredReflectorTier).getString();
        tt.add(Component.literal("§b✦ " + titleName));
        if (refAddon != null && refAddon.getDescription() != null) {
            for (String line : refAddon.getDescription().split("\n")) {
                tt.add(Component.literal("§7" + line));
            }
            return;
        }
        tt.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.addon.reflector_desc", hoveredReflectorTier).getString()));
    }
}
