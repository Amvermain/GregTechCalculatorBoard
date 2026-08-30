package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.render.IngredientRenderer;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.util.FormatUtil;

import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.model.IngredientStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SummaryOverlay {
    public static final int WIDTH = 240;
    private boolean collapsed = false;
    private double scrollY = 0;
    private double maxScrollY = 0;

    private IngredientStack hoveredStack = null;
    private double hoveredRate = 0.0;
    private boolean hoveredMachines = false;
    private boolean hoveredPower = false;
    private boolean hoveredStress = false;
    private boolean hoveredFusion = false;
    private BalanceSummary lastSummary = null;

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
        BoardManager.getInstance().setSummaryOverlayCollapsed(collapsed);
    }

    public void toggle() {
        this.collapsed = !this.collapsed;
        if (collapsed) {
            scrollY = 0;
        }
        BoardManager.getInstance().setSummaryOverlayCollapsed(this.collapsed);
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, BalanceSummary summary, int mouseX, int mouseY) {
        this.lastSummary = summary;
        Font font = Minecraft.getInstance().font;

        graphics.pose().pushPose();
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();

        int x = screenWidth - WIDTH - 10;
        int y = 66;
        int height = screenHeight - 74;
        hoveredStack = null;
        hoveredMachines = false;

        if (collapsed) {
            // Mini collapsed tab
            int tabW = 24;
            int tabH = 50;
            int tabX = screenWidth - tabW - 4;
            graphics.fill(tabX, y, tabX + tabW, y + tabH, 0xEE1E2430);
            graphics.renderOutline(tabX, y, tabW, tabH, 0xFF3D4B66);
            graphics.drawCenteredString(font, "⚡", tabX + tabW / 2, y + 8, 0xFFFFAA00);
            graphics.drawCenteredString(font, "«", tabX + tabW / 2, y + 24, 0xFFAAAAAA);
            graphics.pose().popPose();
            return;
        }

        // Panel Background
        graphics.fill(x, y, x + WIDTH, y + height, 0xEE1A1E26);
        graphics.renderOutline(x, y, WIDTH, height, 0xFF3D4455);

        // 1. Fixed Header Title
        graphics.fill(x, y, x + WIDTH, y + 22, 0xFF242934);
        graphics.drawString(font, "§6⚡ " + Component.translatable("gui.gtcalcboard.summary").getString(), x + 8, y + 7, 0xFFFFFFFF, false);

        // Collapse button [>>]
        graphics.drawString(font, "»", x + WIDTH - 16, y + 7, 0xFFAAAAAA, false);

        // 2. Fixed Total Power, Stress & Machines Section
        int curHeaderY = y + 26;

        // EU Power Line
        boolean showEU = Math.abs(summary.totalEUt()) > 0.001 || (summary.totalSU() == 0 && summary.totalFE() == 0);
        int powerY = curHeaderY;
        int powerH = 13;
        if (showEU) {
            boolean isGen = summary.totalEUt() < -0.001;
            String pLabel = (isGen ? "§a" : "§e") + Component.translatable(isGen ? "gui.gtcalcboard.total_gen" : "gui.gtcalcboard.total_power").getString();
            String eutStr = BoardManager.getInstance().getPowerDisplayMode().formatSummaryPower(summary.totalEUt(), summary.highestVoltageTier());
            int pLabelW = font.width(pLabel) + 6;
            int eutW = font.width(eutStr);
            if (pLabelW + eutW <= WIDTH - 16) {
                graphics.drawString(font, pLabel, x + 8, curHeaderY, 0xFFFFFFFF, false);
                graphics.drawString(font, eutStr, x + WIDTH - 8 - eutW, curHeaderY, 0xFFFFFFFF, false);
                curHeaderY += 13;
                powerH = 13;
            } else {
                graphics.drawString(font, pLabel, x + 8, curHeaderY, 0xFFFFFFFF, false);
                curHeaderY += 11;
                graphics.drawString(font, "  " + eutStr, x + 8, curHeaderY, 0xFFFFFFFF, false);
                curHeaderY += 13;
                powerH = 24;
            }
        }

        // Kinetic Stress (SU) Line
        int stressY = curHeaderY;
        boolean showSU = Math.abs(summary.totalSU()) > 0.001;
        if (showSU) {
            String suLabel = "§6" + Component.translatable("gui.gtcalcboard.total_stress").getString();
            String suValStr = summary.totalSU() >= 0
                    ? String.format("§a+%,.0f SU", summary.totalSU())
                    : String.format("§c-%,.0f SU", -summary.totalSU());
            int suLabelW = font.width(suLabel) + 6;
            graphics.drawString(font, suLabel, x + 8, curHeaderY, 0xFFFFAA00, false);
            graphics.drawString(font, suValStr, x + 8 + suLabelW, curHeaderY, 0xFFFFFFFF, false);
            curHeaderY += 13;
        }

        // Fusion Startup Ignition Energy Line
        int fusionY = curHeaderY;
        boolean showFusion = summary.totalFusionStartupEU() > 0;
        if (showFusion) {
            String fLabel = "§d⚛ " + Component.translatable("gui.gtcalcboard.fusion_start_buffer").getString();
            String fValStr = "§f" + FormatUtil.formatCompactNumber(summary.totalFusionStartupEU()) + " EU";
            int fLabelW = font.width(fLabel) + 6;
            graphics.drawString(font, fLabel, x + 8, curHeaderY, 0xFFFFFFFF, false);
            graphics.drawString(font, fValStr, x + 8 + fLabelW, curHeaderY, 0xFFFFFFFF, false);
            curHeaderY += 13;
        }

        int machinesY = curHeaderY;
        String mLabel = "§6" + Component.translatable("gui.gtcalcboard.total_machines").getString();
        String mCountStr = String.format("§f%d%s §7(%s)", summary.totalMachineCount(), Component.translatable("gui.gtcalcboard.machine_unit").getString(), Component.translatable("gui.gtcalcboard.hover_details").getString());
        int mLabelW = font.width(mLabel) + 6;
        graphics.drawString(font, mLabel, x + 8, machinesY, 0xFFFFFFFF, false);
        graphics.drawString(font, mCountStr, x + 8 + mLabelW, machinesY, 0xFFFFFFFF, false);

        hoveredMachines = mouseX >= x + 8 && mouseX <= x + WIDTH - 8 && mouseY >= machinesY - 2 && mouseY <= machinesY + 12;
        hoveredPower = showEU && mouseX >= x + 8 && mouseX <= x + WIDTH - 8 && mouseY >= powerY - 2 && mouseY <= powerY + powerH;
        hoveredStress = showSU && mouseX >= x + 8 && mouseX <= x + WIDTH - 8 && mouseY >= stressY - 2 && mouseY <= stressY + 12;
        hoveredFusion = showFusion && mouseX >= x + 8 && mouseX <= x + WIDTH - 8 && mouseY >= fusionY - 2 && mouseY <= fusionY + 12;

        // Top separator below power & machines
        int headerBottom = machinesY + 14;
        graphics.fill(x + 8, headerBottom, x + WIDTH - 8, headerBottom + 1, 0xFF353C4D);

        // 3. Scrollable Content Area (Raw Inputs + Net Outputs)
        int contentY = headerBottom + 4;
        int contentH = (y + height) - contentY - 4;

        // Calculate total content height
        int rawCount = summary.rawInputs().isEmpty() ? 1 : summary.rawInputs().size();
        int netCount = summary.netOutputs().isEmpty() ? 1 : summary.netOutputs().size();
        int totalContentH = 16 + (rawCount * 16) + 12 + 16 + (netCount * 16) + 8;

        maxScrollY = Math.max(0, totalContentH - contentH);
        scrollY = Math.max(0, Math.min(maxScrollY, scrollY));

        graphics.enableScissor(x + 1, contentY, x + WIDTH - 1, contentY + contentH);

        int curY = contentY - (int) scrollY;

        // Section A: Raw Inputs
        graphics.drawString(font, "§c📥 " + Component.translatable("gui.gtcalcboard.raw_inputs").getString(), x + 8, curY, 0xFFFFFFFF, false);
        curY += 14;

        if (summary.rawInputs().isEmpty()) {
            graphics.drawString(font, "  §7" + Component.translatable("gui.gtcalcboard.none").getString(), x + 8, curY, 0xFF888888, false);
            curY += 16;
        } else {
            for (Map.Entry<IngredientStack, Double> entry : summary.rawInputs().entrySet()) {
                if (curY >= contentY - 16 && curY <= contentY + contentH) {
                    renderSummaryRow(graphics, font, x, curY, entry.getKey(), -entry.getValue(), 0xFFFF5555, mouseX, mouseY, contentY, contentH);
                }
                curY += 16;
            }
        }

        // Section B: Net Outputs
        curY += 8;
        graphics.drawString(font, "§a📤 " + Component.translatable("gui.gtcalcboard.net_outputs").getString(), x + 8, curY, 0xFFFFFFFF, false);
        curY += 14;

        if (summary.netOutputs().isEmpty()) {
            graphics.drawString(font, "  §7" + Component.translatable("gui.gtcalcboard.none").getString(), x + 8, curY, 0xFF888888, false);
            curY += 16;
        } else {
            for (Map.Entry<IngredientStack, Double> entry : summary.netOutputs().entrySet()) {
                if (curY >= contentY - 16 && curY <= contentY + contentH) {
                    renderSummaryRow(graphics, font, x, curY, entry.getKey(), entry.getValue(), 0xFF55FF55, mouseX, mouseY, contentY, contentH);
                }
                curY += 16;
            }
        }

        graphics.disableScissor();

        // 4. Render Scrollbar if needed
        if (maxScrollY > 0) {
            int sbX = x + WIDTH - 5;
            int sbTrackY = contentY;
            int sbTrackH = contentH;
            graphics.fill(sbX, sbTrackY, sbX + 3, sbTrackY + sbTrackH, 0x55000000);

            int thumbH = Math.max(16, (int) ((double) contentH / totalContentH * sbTrackH));
            int thumbY = sbTrackY + (int) ((scrollY / maxScrollY) * (sbTrackH - thumbH));
            graphics.fill(sbX, thumbY, sbX + 3, thumbY + thumbH, 0xFFAAAAAA);
        }

        graphics.pose().popPose();
    }

    private void renderSummaryRow(GuiGraphics graphics, Font font, int x, int y, IngredientStack stack, double rate, int rateColor, int mouseX, int mouseY, int contentY, int contentH) {
        IngredientRenderer.render(graphics, stack, x + 8, y - 2);

        String name = stack.getDisplayName();
        if (name.length() > 13) {
            name = name.substring(0, 11) + "..";
        }
        graphics.drawString(font, "§f" + name, x + 26, y + 2, 0xFFFFFFFF, false);

        String ratePrefix = rate > 0 ? "+" : "";
        String rateStr = ratePrefix + formatRate(rate, stack.isFluid());
        int rateW = font.width(rateStr);
        graphics.drawString(font, rateStr, x + WIDTH - 10 - rateW, y + 2, rateColor, false);

        // Check if hovered
        if (mouseX >= x + 8 && mouseX <= x + WIDTH - 8 && mouseY >= y && mouseY <= y + 14 && mouseY >= contentY && mouseY <= contentY + contentH) {
            hoveredStack = stack;
            hoveredRate = rate;
        }
    }

    public void renderTooltips(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (hoveredPower && lastSummary != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§6⚡ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
            double totEUt = lastSummary.totalEUt();
            var tier = lastSummary.highestVoltageTier();
            if (tier == null) tier = com.gtceu.calcboard.api.type.GTVoltageTier.LV;
            double amps = Math.abs(totEUt) / (double) tier.getVoltage();
            tooltip.add(Component.literal(String.format(java.util.Locale.ROOT, "§7EU/t: §f%,.2f EU/t", totEUt)));
            tooltip.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Current: §f%,.4fA %s", amps, tier.getName())));
            tooltip.add(Component.literal("§8" + Component.translatable("gui.gtcalcboard.tooltip.power_mode_hint").getString()));
            graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            return;
        }

        if (hoveredStress && lastSummary != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§6⚙ " + Component.translatable("gui.gtcalcboard.total_stress").getString()));
            double totSU = lastSummary.totalSU();
            if (totSU >= 0) {
                tooltip.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Capacity Surplus: §a+%,.0f SU", totSU)));
            } else {
                tooltip.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Stress Deficit: §c-%,.0f SU", -totSU)));
                tooltip.add(Component.literal("§4⚠ " + Component.translatable("gui.gtcalcboard.tooltip.overstressed").getString()));
            }
            graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            return;
        }

        if (hoveredFusion && lastSummary != null && lastSummary.totalFusionStartupEU() > 0) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§d⚛ " + Component.translatable("gui.gtcalcboard.fusion_start_buffer_title").getString()));
            tooltip.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Total Required Startup Energy: §e%,d EU", lastSummary.totalFusionStartupEU())));
            tooltip.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Formatted: §f%s EU", FormatUtil.formatCompactNumber(lastSummary.totalFusionStartupEU()))));
            tooltip.add(Component.literal("§8§m------------------------"));
            tooltip.add(Component.literal("§b" + Component.translatable("gui.gtcalcboard.fusion_breakdown_title").getString()));
            for (Map.Entry<Integer, Integer> entry : lastSummary.fusionTierCounts().entrySet()) {
                int fTier = entry.getKey();
                int count = entry.getValue();
                long tierStartEU = lastSummary.fusionTierStartupEU().getOrDefault(fTier, 0L);
                tooltip.add(Component.literal(String.format(java.util.Locale.ROOT, "§7• Fusion Mk%d: §f%d%s §7(%s EU)",
                        fTier, count, Component.translatable("gui.gtcalcboard.machine_unit").getString(), FormatUtil.formatCompactNumber(tierStartEU))));
            }
            tooltip.add(Component.literal("§8§m------------------------"));
            tooltip.add(Component.literal("§e" + Component.translatable("gui.gtcalcboard.fusion_start_buffer_desc").getString()));
            graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            return;
        }

        if (hoveredMachines && lastSummary != null && !lastSummary.machineBreakdown().isEmpty()) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal("§6🏭 " + Component.translatable("gui.gtcalcboard.total_machines_breakdown").getString()));
            for (Map.Entry<String, Integer> entry : lastSummary.machineBreakdown().entrySet()) {
                tooltip.add(Component.literal("§7• " + entry.getKey() + ": §f" + entry.getValue() + Component.translatable("gui.gtcalcboard.machine_unit").getString()));
            }
            graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
            return;
        }

        if (hoveredStack != null) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.literal(hoveredStack.getDisplayName()));
            String exactRateStr = FormatUtil.formatExactRate(hoveredRate, hoveredStack.isFluid());
            String ratePrefix = hoveredRate > 0 ? "+" : "";
            tooltip.add(Component.literal("§7Rate: §f" + ratePrefix + exactRateStr));
            tooltip.add(Component.literal("§8").append(Component.translatable("gui.gtcalcboard.tooltip.recipes_uses")));
            graphics.renderTooltip(font, tooltip, java.util.Optional.empty(), mouseX, mouseY);
        }
    }

    private String formatRate(double rate, boolean isFluid) {
        return NodeCardRenderer.formatRate(rate, isFluid);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta, int screenWidth, int screenHeight) {
        if (collapsed) return false;

        int x = screenWidth - WIDTH - 10;
        int y = 66;
        int height = screenHeight - 74;

        if (mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + height) {
            if (maxScrollY > 0) {
                scrollY = Math.max(0, Math.min(maxScrollY, scrollY - (delta * 18.0)));
                return true;
            }
        }
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (collapsed) {
            int tabW = 24;
            int tabH = 50;
            int tabX = screenWidth - tabW - 4;
            int y = 66;
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= y && mouseY <= y + tabH) {
                toggle();
                return true;
            }
            return false;
        }

        int x = screenWidth - WIDTH - 10;
        int y = 66;

        // Header click -> collapse/expand
        if (mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + 22) {
            toggle();
            return true;
        }

        // Total Power line click -> cycle power display mode (EU/t <-> Amps <-> Both)
        if (hoveredPower && button == 0) {
            var newMode = BoardManager.getInstance().cyclePowerDisplayMode();
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
            BoardToast.show(Component.literal("§e⚡ ").append(Component.translatable("message.gtcalcboard.power_mode_changed", newMode.getDisplayName())));
            return true;
        }
        return false;
    }
}




