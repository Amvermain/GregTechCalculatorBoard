package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.OverclockMode;
import com.gtceu.calcboard.api.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * Dedicated visual renderer for Recipe Node cards in the Calculator Board.
 */
public class NodeCardRenderer {
    public static void render(NodeWidget widget, GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        RecipeNode node = widget.getNode();
        Font font = Minecraft.getInstance().font;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int height = widget.getHeight();

        // 1. Card Background & Golden/Metallic Outlines
        graphics.fill(x, y, x + NodeWidget.WIDTH, y + height, 0xF01E222B);
        int headerColor = widget.isHeaderHovered(mouseX, mouseY) ? 0xFF353C4D : 0xFF2A2E39;
        graphics.fill(x, y, x + NodeWidget.WIDTH, y + NodeWidget.HEADER_HEIGHT, headerColor);

        int outlineColor = node.isBaseNode() ? 0xFFFFD700 : 0xFF3D4455;
        graphics.renderOutline(x, y, NodeWidget.WIDTH, height, outlineColor);
        if (node.isBaseNode()) {
            graphics.renderOutline(x + 1, y + 1, NodeWidget.WIDTH - 2, height - 2, 0x88FFD700);
        }

        // 2. Header Title
        String title = (node.isBaseNode() ? "§6★ " : "") + node.getName();
        if (title.length() > 22) title = title.substring(0, 20) + "...";
        graphics.drawString(font, title, x + 6, y + 6, node.isBaseNode() ? 0xFFFFE066 : 0xFFE0E0E0, false);

        // 3. Target Base Node Toggle Button [🎯]
        int targetX = x + NodeWidget.WIDTH - 36;
        int targetY = y + 2;
        boolean targetHover = widget.isTargetButtonHovered(mouseX, mouseY);
        int targetBg = node.isBaseNode() ? 0xFF886600 : (targetHover ? 0xFF3A4456 : 0xFF222834);
        int targetBorder = node.isBaseNode() ? 0xFFFFD700 : (targetHover ? 0xFF88AAFF : 0xFF4A5568);
        graphics.fill(targetX, targetY, targetX + 16, targetY + 16, targetBg);
        graphics.renderOutline(targetX, targetY, 16, 16, targetBorder);
        graphics.drawCenteredString(font, "🎯", targetX + 8, targetY + 4, node.isBaseNode() ? 0xFFFFEE55 : 0xFFAAAAAA);

        // 4. Close/Delete Button [X]
        int closeX = x + NodeWidget.WIDTH - 18;
        int closeY = y + 2;
        boolean closeHover = widget.isCloseButtonHovered(mouseX, mouseY);
        graphics.fill(closeX, closeY, closeX + 16, closeY + 16, closeHover ? 0xFFFF4444 : 0x44FF4444);
        graphics.drawString(font, "x", closeX + 5, closeY + 3, 0xFFFFFFFF, false);

        // 5. Machine Count Controls: Count: [-] [Input Box] [+] [/2] [x2]
        int ctrlY = y + NodeWidget.HEADER_HEIGHT + 6;
        graphics.drawString(font, Component.translatable("gui.gtcalcboard.count"), x + 6, ctrlY + 3, 0xFFAAAAAA, false);

        drawBtn(graphics, font, "-", x + 44, ctrlY, 14, 14, mouseX, mouseY);

        // Interactive Numeric Count Box
        int countBoxX = x + 60;
        int countBoxW = 36;
        boolean countHover = mouseX >= countBoxX && mouseX <= countBoxX + countBoxW && mouseY >= ctrlY && mouseY <= ctrlY + 14;
        boolean isEditing = widget.getCountEditor().isEditing();
        int boxBg = isEditing ? 0xFF0D1B2A : (countHover ? 0xFF252A36 : 0xFF14171E);
        int boxBorder = isEditing ? 0xFF55FFFF : (countHover ? 0xFF5577AA : 0xFF3D4455);
        graphics.fill(countBoxX, ctrlY, countBoxX + countBoxW, ctrlY + 14, boxBg);
        graphics.renderOutline(countBoxX, ctrlY, countBoxW, 14, boxBorder);

        String displayText = widget.getCountEditor().getDisplayText();
        graphics.drawCenteredString(font, displayText, countBoxX + countBoxW / 2, ctrlY + 3, isEditing ? 0xFF55FFFF : 0xFFFFFF55);

        drawBtn(graphics, font, "+", x + 98, ctrlY, 14, 14, mouseX, mouseY);
        drawBtn(graphics, font, "/2", x + 115, ctrlY, 16, 14, mouseX, mouseY);
        drawBtn(graphics, font, "x2", x + 133, ctrlY, 16, 14, mouseX, mouseY);

        // 6. Voltage Tier Selector: [Tier] button
        int tierBtnX = x + 154;
        GTVoltageTier tier = node.getTargetTier();
        drawBtn(graphics, font, tier.getName(), tierBtnX, ctrlY, 34, 14, mouseX, mouseY, tier.getColor());

        // 7. Second Row Controls: Overclock Mode & Parallel
        int row2Y = ctrlY + 18;
        String ocText = node.getOverclockMode() == OverclockMode.PERFECT ? "PERF OC" : "STD OC";
        int ocColor = node.getOverclockMode() == OverclockMode.PERFECT ? 0xFF55FF55 : 0xFFAAAAAA;
        drawBtn(graphics, font, ocText, x + 6, row2Y, 54, 14, mouseX, mouseY, ocColor);

        String parText = "Par: " + node.getParallel() + "x";
        drawBtn(graphics, font, parText, x + 64, row2Y, 48, 14, mouseX, mouseY);

        // 8. Recipe Energy & Duration Info
        int infoY = row2Y + 18;
        double totalEUt = node.getTotalEUt();
        double durationSec = node.getEffectiveDurationSeconds();
        double cyclesPerSec = node.getCyclesPerSecond();

        String eutStr = String.format("§e%.1f EU/t §7(%s)", totalEUt, tier.getName());
        graphics.drawString(font, eutStr, x + 6, infoY, 0xFFFFFFFF, false);

        String cycleStr = String.format("§b%.2fs §7(§f%.2f/s§7)", durationSec, cyclesPerSec);
        graphics.drawString(font, cycleStr, x + 120, infoY, 0xFFFFFFFF, false);

        // Separator Line
        int sepY = infoY + 14;
        graphics.fill(x + 4, sepY, x + NodeWidget.WIDTH - 4, sepY + 1, 0xFF353C4D);

        // 9. Input & Output Ports Listing
        int contentY = sepY + 4;
        Map<IngredientStack, Double> inRates = widget.getCachedInputRates();
        Map<IngredientStack, Double> outRates = widget.getCachedOutputRates();

        List<IngredientStack> inputs = node.getInputs();
        List<IngredientStack> outputs = node.getOutputs();
        int maxRows = Math.max(inputs.size(), outputs.size());

        for (int i = 0; i < maxRows; i++) {
            int rowY = contentY + i * 18;

            // Render Input (Left side)
            if (i < inputs.size()) {
                IngredientStack in = inputs.get(i);
                double rate = inRates.getOrDefault(in, 0.0);
                int inPortX = x + 4;
                int inPortY = rowY + 5;

                boolean portHover = mouseX >= x && mouseX <= x + 28 && mouseY >= rowY - 2 && mouseY <= rowY + 16;
                graphics.fill(inPortX, inPortY, inPortX + 6, inPortY + 6, portHover ? 0xFF88CCFF : 0xFF5599FF);

                in.render(graphics, x + 12, rowY - 1);

                String rateStr = formatRate(rate, in.isFluid());
                graphics.drawString(font, rateStr, x + 30, rowY + 4, 0xFFFFAAAA, false);
            }

            // Render Output (Right side)
            if (i < outputs.size()) {
                IngredientStack out = outputs.get(i);
                double rate = outRates.getOrDefault(out, 0.0);
                int outPortX = x + NodeWidget.WIDTH - 10;
                int outPortY = rowY + 5;

                boolean portHover = mouseX >= x + NodeWidget.WIDTH - 28 && mouseX <= x + NodeWidget.WIDTH && mouseY >= rowY - 2 && mouseY <= rowY + 16;
                graphics.fill(outPortX, outPortY, outPortX + 6, outPortY + 6, portHover ? 0xFF88FFAA : 0xFF55FF99);

                String rateStr = formatRate(rate, out.isFluid());
                int textW = font.width(rateStr);
                graphics.drawString(font, rateStr, x + NodeWidget.WIDTH - 30 - textW, rowY + 4, 0xFFAAFFAA, false);

                out.render(graphics, x + NodeWidget.WIDTH - 28, rowY - 1);
            }
        }
    }

    private static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my) {
        drawBtn(graphics, font, text, bx, by, bw, bh, mx, my, 0xFFFFFFFF);
    }

    private static void drawBtn(GuiGraphics graphics, Font font, String text, int bx, int by, int bw, int bh, int mx, int my, int textColor) {
        boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
        graphics.fill(bx, by, bx + bw, by + bh, hover ? 0xFF3E475A : 0xFF282E3B);
        graphics.renderOutline(bx, by, bw, bh, hover ? 0xFF657595 : 0xFF3D4455);
        graphics.drawCenteredString(font, text, bx + bw / 2, by + (bh - 8) / 2, textColor);
    }

    private static String formatRate(double rate, boolean isFluid) {
        if (isFluid) {
            if (rate >= 1000.0) {
                return String.format("%.2f B/s", rate / 1000.0);
            }
            return String.format("%.0f mB/s", rate);
        } else {
            return String.format("%.2f/s", rate).replaceAll("\\.?0+$", "/s");
        }
    }
}
