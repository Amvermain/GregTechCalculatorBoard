package com.gtceu.calcboard.client.gui.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated renderer for hardware status controls: combustion engines, boilers, steam mode,
 * fusion reflectors, and multiblock coils.
 */
@OnlyIn(Dist.CLIENT)
public final class GTCEuHardwareStatusRenderer {

    private GTCEuHardwareStatusRenderer() {}

    private static void showTooltip(MachineConfigDialog dialog, GuiGraphics graphics, Font font, List<Component> tooltip, int mouseX, int mouseY) {
        if (dialog != null) {
            dialog.setDeferredTooltip(tooltip);
        } else {
            BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltip, mouseX, mouseY);
        }
    }

    public static void renderCombustionDialogHeader(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
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

    public static void renderBoilerDialogHeader(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
                                                int x, int y, int dialogW, int mouseX, int mouseY) {
        graphics.drawString(font, "§6♨ " + Component.translatable("gui.gtcalcboard.boiler_type_title").getString(), x + 10, y + 30, 0xFFFFFFFF, false);
        GTBoilerTier curTier = GTBoilerTier.getBoilerTier(node);

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

        GTBoilerTier[] bTiers = GTBoilerTier.values();
        boolean isLiquid = node.isLiquidBoilerRecipe();
        int btnW = 70;
        int gap = 4;
        int btnY = y + 44;
        GTBoilerTier hoveredTier = null;
        for (int i = 0; i < bTiers.length; i++) {
            GTBoilerTier bt = bTiers[i];
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
    }

    public static void renderSteamModeDialogHeader(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int mouseX, int mouseY) {
        if (node.supportsSteamMode()) {
            graphics.drawString(font, "§6♨ " + Component.translatable("gui.gtcalcboard.config.steam_mode_title").getString(), x + 10, y + 30, 0xFFFFFFFF, false);
            int btnX = x + 10;
            SteamMode curSteam = node.getSteamMode();

            boolean lpActive = curSteam == SteamMode.LOW_PRESSURE;
            boolean lpHover = mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60;
            graphics.fill(btnX, y + 44, btnX + 110, y + 60, lpActive ? 0xFF5D3E1A : (lpHover ? 0xFF3D4558 : 0xFF282D3B));
            graphics.renderOutline(btnX, y + 44, 110, 16, lpActive ? 0xFFD28C38 : 0xFF3F4658);
            graphics.drawCenteredString(font, "♨ LP Steam (0.5x)", btnX + 55, y + 48, lpActive ? 0xFFFFD28C : 0xFFB0B8C8);
            btnX += 116;

            boolean hpActive = curSteam == SteamMode.HIGH_PRESSURE;
            boolean hpHover = mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60;
            graphics.fill(btnX, y + 44, btnX + 110, y + 60, hpActive ? 0xFF4A4A4A : (hpHover ? 0xFF3D4558 : 0xFF282D3B));
            graphics.renderOutline(btnX, y + 44, 110, 16, hpActive ? 0xFFAAAAAA : 0xFF3F4658);
            graphics.drawCenteredString(font, "♨ HP Steam (1.0x)", btnX + 55, y + 48, hpActive ? 0xFFFFFFFF : 0xFFB0B8C8);
            btnX += 116;

            boolean elecActive = curSteam == SteamMode.NONE;
            boolean elecHover = mouseX >= btnX && mouseX <= btnX + 90 && mouseY >= y + 44 && mouseY <= y + 60;
            graphics.fill(btnX, y + 44, btnX + 90, y + 60, elecActive ? 0xFF2A5288 : (elecHover ? 0xFF3D4558 : 0xFF282D3B));
            graphics.renderOutline(btnX, y + 44, 90, 16, elecActive ? 0xFF589CFF : 0xFF3F4658);
            graphics.drawCenteredString(font, "⚡ Electric", btnX + 45, y + 48, elecActive ? 0xFF58D3FF : 0xFFB0B8C8);
        } else {
            graphics.drawString(font, "§b" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_fixed").getString(), x + 10, y + 32, 0xFFFFFFFF, false);
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_desc").getString(), x + 10, y + 48, 0xFF888888, false);
        }
    }

    public static void renderGenericMultiblockControllerHeader(
            MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
            int x, int y, int dialogW, int mouseX, int mouseY) {
        List<ResourceLocation> mbWorkstations = ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
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

        int defPar = ModAdapterRegistry.getAdapterForNode(node).getDefaultParallel(node);
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
            var item = ForgeRegistries.ITEMS.getValue(hoveredController);
            String fullName = (item != null && item != Items.AIR) ? item.getDescription().getString() : hoveredController.getPath();
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

        var item = ForgeRegistries.ITEMS.getValue(id);
        if (item != null && item != Items.AIR) {
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
}
