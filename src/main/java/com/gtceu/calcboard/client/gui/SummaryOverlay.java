package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.BalanceSummary;
import com.gtceu.calcboard.api.IngredientStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Map;

public class SummaryOverlay {
    public static final int WIDTH = 220;
    private boolean collapsed = false;

    public boolean isCollapsed() {
        return collapsed;
    }

    public void setCollapsed(boolean collapsed) {
        this.collapsed = collapsed;
    }

    public void toggle() {
        this.collapsed = !this.collapsed;
    }

    public void render(GuiGraphics graphics, int screenWidth, int screenHeight, BalanceSummary summary, int mouseX, int mouseY) {
        Font font = Minecraft.getInstance().font;
        int x = screenWidth - WIDTH - 10;
        int y = 36;
        int height = screenHeight - 46;

        if (collapsed) {
            // Mini collapsed tab
            int tabW = 24;
            int tabH = 60;
            int tabX = screenWidth - tabW - 4;
            graphics.fill(tabX, y, tabX + tabW, y + tabH, 0xEE1E2430);
            graphics.renderOutline(tabX, y, tabW, tabH, 0xFF3D4B66);
            graphics.drawCenteredString(font, "⚡", tabX + tabW / 2, y + 8, 0xFFFFAA00);
            graphics.drawCenteredString(font, "«", tabX + tabW / 2, y + 24, 0xFFAAAAAA);
            return;
        }

        // Panel Background
        graphics.fill(x, y, x + WIDTH, y + height, 0xEE1A1E26);
        graphics.renderOutline(x, y, WIDTH, height, 0xFF3D4455);

        // Header Title
        graphics.fill(x, y, x + WIDTH, y + 22, 0xFF242934);
        graphics.drawString(font, "§6⚡ " + Component.translatable("gui.gtcalcboard.summary").getString(), x + 8, y + 7, 0xFFFFFFFF, false);

        // Collapse button [>>]
        graphics.drawString(font, "»", x + WIDTH - 16, y + 7, 0xFFAAAAAA, false);

        int curY = y + 28;

        // 1. Total Power Section
        String pLabel = "§e" + Component.translatable("gui.gtcalcboard.total_power").getString();
        graphics.drawString(font, pLabel, x + 8, curY, 0xFFFFFFFF, false);
        String eutStr = String.format("§a%,.1f EU/t §7(%s)", summary.totalEUt(), summary.highestVoltageTier().getName());
        int pLabelW = font.width(pLabel) + 6;
        graphics.drawString(font, eutStr, x + 8 + pLabelW, curY, 0xFFFFFFFF, false);
        curY += 16;

        // Separator
        graphics.fill(x + 8, curY, x + WIDTH - 8, curY + 1, 0xFF353C4D);
        curY += 6;

        // 2. Raw Inputs (Deficits/External supplies needed)
        graphics.drawString(font, "§c📥 " + Component.translatable("gui.gtcalcboard.raw_inputs").getString(), x + 8, curY, 0xFFFFFFFF, false);
        curY += 14;

        if (summary.rawInputs().isEmpty()) {
            graphics.drawString(font, "  §7" + Component.translatable("gui.gtcalcboard.none").getString(), x + 8, curY, 0xFF888888, false);
            curY += 14;
        } else {
            for (Map.Entry<IngredientStack, Double> entry : summary.rawInputs().entrySet()) {
                if (curY > y + height - 20) break;
                renderSummaryRow(graphics, font, x, curY, entry.getKey(), -entry.getValue(), 0xFFFF5555);
                curY += 16;
            }
        }

        // Separator
        graphics.fill(x + 8, curY, x + WIDTH - 8, curY + 1, 0xFF353C4D);
        curY += 6;

        // 3. Net Outputs (Surplus)
        graphics.drawString(font, "§a📤 " + Component.translatable("gui.gtcalcboard.net_outputs").getString(), x + 8, curY, 0xFFFFFFFF, false);
        curY += 14;

        if (summary.netOutputs().isEmpty()) {
            graphics.drawString(font, "  §7" + Component.translatable("gui.gtcalcboard.none").getString(), x + 8, curY, 0xFF888888, false);
            curY += 14;
        } else {
            for (Map.Entry<IngredientStack, Double> entry : summary.netOutputs().entrySet()) {
                if (curY > y + height - 20) break;
                renderSummaryRow(graphics, font, x, curY, entry.getKey(), entry.getValue(), 0xFF55FF55);
                curY += 16;
            }
        }
    }

    private void renderSummaryRow(GuiGraphics graphics, Font font, int x, int y, IngredientStack stack, double rate, int rateColor) {
        stack.render(graphics, x + 8, y - 2);

        String name = stack.getDisplayName();
        if (name.length() > 13) {
            name = name.substring(0, 11) + "..";
        }
        graphics.drawString(font, "§f" + name, x + 26, y + 2, 0xFFFFFFFF, false);

        String ratePrefix = rate > 0 ? "+" : "";
        String rateStr = ratePrefix + formatRate(rate, stack.isFluid());
        int rateW = font.width(rateStr);
        graphics.drawString(font, rateStr, x + WIDTH - 8 - rateW, y + 2, rateColor, false);
    }

    private String formatRate(double rate, boolean isFluid) {
        double absRate = Math.abs(rate);
        if (isFluid) {
            if (absRate >= 1000.0) {
                return String.format("%.2f B/s", rate / 1000.0);
            }
            return String.format("%.0f mB/s", rate);
        } else {
            return String.format("%.2f/s", rate).replaceAll("\\.?0+$", "/s");
        }
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button, int screenWidth, int screenHeight) {
        if (collapsed) {
            int tabW = 24;
            int tabH = 60;
            int tabX = screenWidth - tabW - 4;
            int y = 36;
            if (mouseX >= tabX && mouseX <= tabX + tabW && mouseY >= y && mouseY <= y + tabH) {
                toggle();
                return true;
            }
            return false;
        }

        int x = screenWidth - WIDTH - 10;
        int y = 36;
        if (mouseX >= x && mouseX <= x + WIDTH && mouseY >= y && mouseY <= y + 22) {
            toggle();
            return true;
        }
        return false;
    }
}
