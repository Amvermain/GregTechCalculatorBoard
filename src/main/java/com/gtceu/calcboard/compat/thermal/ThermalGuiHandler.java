package com.gtceu.calcboard.compat.thermal;

import com.gtceu.calcboard.api.PowerDisplayMode;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.client.gui.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles UI rendering and tooltip generation for Thermal machine and dynamo nodes.
 */
public class ThermalGuiHandler {

    public static String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        double rfRate = node.getEffectiveTotalEUt();
        return node.isGenerator()
                ? String.format("§a+%,.0f RF/t", rfRate)
                : String.format("§e%,.0f RF/t", rfRate);
    }

    public static List<Component> buildEnergyTooltip(RecipeNode node) {
        List<Component> tooltipLines = new ArrayList<>();
        double totPower = node.getEffectiveTotalEUt();
        if (node.isGenerator()) {
            tooltipLines.add(Component.literal("§a⚡ " + Component.translatable("gui.gtcalcboard.dynamo_badge").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Generation: §a+%,.2f RF/t §7(§a+%,.2f EU/t eq§7)", totPower, totPower / 4.0)));
        } else {
            tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Consumption: §c%,.2f RF/t §7(§c%,.2f EU/t eq§7)", totPower, totPower / 4.0)));
        }
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        return tooltipLines;
    }

    public static void renderCardControls(GuiGraphics graphics, Font font,
                                          RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                          boolean isGlowing) {
        String badge = node.isGenerator()
                ? Component.translatable("gui.gtcalcboard.dynamo_badge").getString()
                : "⚡ Thermal";
        int badgeW = Math.max(38, font.width(badge) + 6);
        NodeCardRenderer.drawBtn(graphics, font, badge, x + 6, row2Y, badgeW, 14, mouseX, mouseY, 0xFFFF5555);

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
        String badge = node.isGenerator()
                ? Component.translatable("gui.gtcalcboard.dynamo_badge").getString()
                : "⚡ Thermal";
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

    public static void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node,
                                           int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                           net.minecraft.client.gui.components.EditBox parallelBox,
                                           com.gtceu.calcboard.client.gui.BoardScreen parent) {
        if (!node.isMultiblock()) {
            graphics.drawString(font, "§b" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_fixed").getString(), x + 10, y + 32, 0xFFFFFFFF, false);
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_desc").getString(), x + 10, y + 48, 0xFF888888, false);
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
        }
    }

    public static boolean handleDialogHeaderClick(com.gtceu.calcboard.client.gui.MachineConfigDialog dialog,
                                                 RecipeNode node, int x, int y, int dialogW,
                                                 double mouseX, double mouseY, int button,
                                                 net.minecraft.client.gui.components.EditBox parallelBox,
                                                 com.gtceu.calcboard.client.gui.BoardScreen parent) {
        if (!node.isMultiblock()) return false;
        int[] quickPars = {1, 4, 16, 64, 256};
        int btnX = x + 64;
        for (int qp : quickPars) {
            if (mouseX >= btnX && mouseX <= btnX + 34 && mouseY >= y + 44 && mouseY <= y + 60) {
                node.setParallel(qp);
                if (parallelBox != null) parallelBox.setValue(String.valueOf(qp));
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
            btnX += 38;
        }
        return false;
    }
}
