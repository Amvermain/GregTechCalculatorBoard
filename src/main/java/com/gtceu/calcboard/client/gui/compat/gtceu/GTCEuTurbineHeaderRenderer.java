package com.gtceu.calcboard.client.gui.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated renderer for GTCEu large turbine header controls, rotor parameters, and lifespans.
 */
@OnlyIn(Dist.CLIENT)
public final class GTCEuTurbineHeaderRenderer {

    private GTCEuTurbineHeaderRenderer() {}

    private static void showTooltip(MachineConfigDialog dialog, GuiGraphics graphics, Font font, List<Component> tooltip, int mouseX, int mouseY) {
        if (dialog != null) {
            dialog.setDeferredTooltip(tooltip);
        } else {
            BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltip, mouseX, mouseY);
        }
    }

    public static void renderTurbineDialogHeader(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
                                                 int x, int y, int dialogW, int mouseX, int mouseY) {
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

        int btnY = y + 46;
        int curX = x + 10;
        int gap = 4;

        int holderBtnW = 100;
        boolean holderHover = mouseX >= curX && mouseX <= curX + holderBtnW && mouseY >= btnY && mouseY <= btnY + 16;
        graphics.fill(curX, btnY, curX + holderBtnW, btnY + 16, holderHover ? 0xFF2A3548 : 0xFF1E2430);
        graphics.renderOutline(curX, btnY, holderBtnW, 16, holderHover ? 0xFF58D3FF : 0xFF3D4B60);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.turbine_holder_tier", holderTier.getFormatCode() + holderTier.getName()).getString(), curX + holderBtnW / 2, btnY + 4, 0xFFFFFFFF);
        curX += holderBtnW + gap;

        int dynamoTierBtnW = 100;
        boolean dynamoHover = mouseX >= curX && mouseX <= curX + dynamoTierBtnW && mouseY >= btnY && mouseY <= btnY + 16;
        int dynamoBorder = isDynamoBottleneck ? (dynamoHover ? 0xFFFFCC00 : 0xFFFFAA00) : (dynamoHover ? 0xFF58D3FF : 0xFF3D4B60);
        graphics.fill(curX, btnY, curX + dynamoTierBtnW, btnY + 16, dynamoHover ? 0xFF2A3548 : 0xFF1E2430);
        graphics.renderOutline(curX, btnY, dynamoTierBtnW, 16, dynamoBorder);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.turbine_dynamo_tier", dynamoTier.getFormatCode() + dynamoTier.getName()).getString(), curX + dynamoTierBtnW / 2, btnY + 4, 0xFFFFFFFF);
        curX += dynamoTierBtnW + gap;

        int dynamoAmpsBtnW = 70;
        boolean ampsHover = mouseX >= curX && mouseX <= curX + dynamoAmpsBtnW && mouseY >= btnY && mouseY <= btnY + 16;
        int ampsBorder = isDynamoBottleneck ? (ampsHover ? 0xFFFFCC00 : 0xFFFFAA00) : (ampsHover ? 0xFF58D3FF : 0xFF3D4B60);
        graphics.fill(curX, btnY, curX + dynamoAmpsBtnW, btnY + 16, ampsHover ? 0xFF2A3548 : 0xFF1E2430);
        graphics.renderOutline(curX, btnY, dynamoAmpsBtnW, 16, ampsBorder);
        graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.turbine_dynamo_amps", dynamoAmps).getString(), curX + dynamoAmpsBtnW / 2, btnY + 4, 0xFFFFE066);
        curX += dynamoAmpsBtnW + gap;

        IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
        boolean supportsBoost = adapter != null && adapter.supportsBoosterControl(node);
        boolean boostHover = false;
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

        renderTurbineTooltips(dialog, graphics, font, node, mouseX, mouseY, pmax, isDynamoBottleneck,
                pmaxHover, dynamoHover, ampsHover, rotorInfoHover, boostHover, supportsBoost, adapter,
                rName, eff, pwr, holderBonus, totalEff);
    }

    private static void renderTurbineTooltips(
            MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
            int mouseX, int mouseY, int pmax, boolean isDynamoBottleneck,
            boolean pmaxHover, boolean dynamoHover, boolean ampsHover, boolean rotorInfoHover,
            boolean boostHover, boolean supportsBoost, IModAdapter adapter,
            String rName, int eff, int pwr, int holderBonus, int totalEff) {
        if (pmaxHover) {
            List<Component> tt = new ArrayList<>();
            tt.add(Component.literal("§6⚡ " + Component.translatable("gui.gtcalcboard.config.pmax_title").getString() + ": §f" + pmax + "x"));
            if (isDynamoBottleneck) {
                double holderCap = GTTurbineHelper.getRotorHolderCapacity(node);
                double dynamoCap = GTTurbineHelper.getDynamoMaxCapacity(node);
                int holderPmax = GTTurbineHelper.getRotorHolderMaxParallel(node);
                tt.add(Component.literal("§c" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dynamo_limited").getString()));
                tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.pmax_holder_cap").getString(), FormatUtil.formatCompactNumber(holderCap), holderPmax)));
                tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dynamo_cap").getString(), FormatUtil.formatCompactNumber(dynamoCap), pmax)));
                tt.add(Component.literal("§a" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dynamo_tip").getString()));
            } else {
                double cap = GTTurbineHelper.getGeneratorMaxEUt(node);
                tt.add(Component.literal("§a" + Component.translatable("gui.gtcalcboard.tooltip.pmax_fully_utilized").getString()));
                tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.pmax_max_cap").getString(), FormatUtil.formatCompactNumber(cap))));
            }
            showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
        } else if ((dynamoHover || ampsHover) && isDynamoBottleneck) {
            List<Component> tt = new ArrayList<>();
            double holderCap = GTTurbineHelper.getRotorHolderCapacity(node);
            double dynamoCap = GTTurbineHelper.getDynamoMaxCapacity(node);
            tt.add(Component.literal(String.format(Locale.ROOT, "§c" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dynamo_bottleneck_hatch").getString(), FormatUtil.formatCompactNumber(dynamoCap), FormatUtil.formatCompactNumber(holderCap))));
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
    }

    public static String formatRotorLifespan(RecipeNode node, boolean compact) {
        if (!GTTurbineHelper.hasRotorAddon(node)) return "";
        double lifespanHours = GTTurbineHelper.calculateRotorLifespanHours(node);
        if (Double.isInfinite(lifespanHours) || lifespanHours <= 0) return "";
        return compact
                ? String.format(Locale.ROOT, " §7| §6⌛%.1fh", lifespanHours)
                : String.format(Locale.ROOT, " §7| §6⌛ %.1fh", lifespanHours);
    }

    public static void appendRotorLifespanTooltip(List<Component> tt, RecipeNode node) {
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
}
