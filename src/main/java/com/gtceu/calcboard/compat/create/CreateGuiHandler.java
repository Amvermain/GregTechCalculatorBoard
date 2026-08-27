package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;

import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Handles UI rendering and mouse interactions for Create kinetic nodes.
 */
public class CreateGuiHandler {

    public static String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        double suRate = node.getEffectiveTotalEUt();
        return node.isGenerator()
                ? String.format("§6+%,.0f SU", suRate)
                : String.format("§e%,.0f SU", suRate);
    }

    public static List<Component> buildEnergyTooltip(RecipeNode node) {
        List<Component> tooltipLines = new ArrayList<>();
        double totSU = node.getEffectiveTotalEUt();
        if (node.isGenerator()) {
            tooltipLines.add(Component.literal("§6⚙ " + Component.translatable("gui.gtcalcboard.total_gen").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Capacity: §6+%,.0f SU", totSU)));
        } else {
            tooltipLines.add(Component.literal("§e⚙ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
            tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Total Stress Impact: §e%,.0f SU", totSU)));
        }
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Rotation Speed: §6%d RPM", node.getRpm())));
        tooltipLines.add(Component.literal(String.format(Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        if (CreateModAdapter.isFanProcessingRecipe(node)) {
            tooltipLines.add(Component.translatable("gui.gtcalcboard.tooltip.fan_fixed_duration_hint"));
        }
        return tooltipLines;
    }

    public static void renderCardControls(GuiGraphics graphics, Font font,
                                          RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                          boolean isGlowing) {
        if (node.isGenerator()) {
            // Generators have fixed generation output (no variable RPM control, no addon slots)
            String genBadge = "⚡ " + Component.translatable("gui.gtcalcboard.kinetic_generator").getString();
            int badgeW = cardW - 12;
            NodeCardRenderer.drawBtn(graphics, font, genBadge, x + 6, row2Y, badgeW, 14, mouseX, mouseY, 0xFF55FF88);
        } else {
            // Consumers have RPM speed control via Rotation Speed Controller (RSC) (no addon slots)
            int rpm = node.getRpm();
            String rpmText = rpm + " RPM";
            int rpmW = Math.max(50, font.width(rpmText) + 10);
            NodeCardRenderer.drawBtn(graphics, font, rpmText, x + 6, row2Y, rpmW, 14, mouseX, mouseY, 0xFFFFAA00);

            int rscX = x + 6 + rpmW + 3;
            int rscW = (x + cardW - 6) - rscX;
            String rscText = "⚙ " + Component.translatable("gui.gtcalcboard.rotation_speed_controller").getString();
            if (font.width(rscText) > rscW - 4) {
                rscText = "⚙ RSC";
            }
            NodeCardRenderer.drawBtn(graphics, font, rscText, rscX, row2Y, rscW, 14, mouseX, mouseY, 0xFFE07A28);
        }
    }

    public static boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node.isGenerator()) return false; // Generator has fixed output speed and cannot cycle RPM
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int rpmW = Math.max(50, Minecraft.getInstance().font.width(node.getRpm() + " RPM") + 10);
        return mouseX >= x + 6 && mouseX <= x + 6 + rpmW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    public static boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        return false; // Create machines do not have addon slots
    }

    public static boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            node.cycleRpm(button == 1 ? -1 : 1);
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
            );
            return true;
        }
        return false;
    }

    public static boolean handleControlScroll(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, double delta) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            node.cycleRpm(delta > 0 ? 1 : -1);
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
            );
            return true;
        }
        return false;
    }

    public static void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node,
                                           int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                           net.minecraft.client.gui.components.EditBox parallelBox,
                                           com.gtceu.calcboard.client.gui.BoardScreen parent) {
        if (CreateModAdapter.isFanProcessingRecipe(node)) {
            graphics.drawString(font, "§6⚙ " + Component.translatable("gui.gtcalcboard.encased_fan").getString(), x + 10, y + 32, 0xFFFFFFFF, false);
            graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.tooltip.fan_fixed_duration_hint").getString(), x + 10, y + 48, 0xFF888888, false);
        } else if (!node.isGenerator()) {
            graphics.drawString(font, "§6⚙ " + Component.translatable("gui.gtcalcboard.rotation_speed_controller").getString() + ": §e" + node.getRpm() + " RPM", x + 10, y + 30, 0xFFFFFFFF, false);
            int[] rpmPresets = {16, 32, 64, 128, 256};
            int btnX = x + 10;
            for (int r : rpmPresets) {
                boolean active = node.getRpm() == r;
                boolean hov = mouseX >= btnX && mouseX <= btnX + 44 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 44, y + 60, active ? 0xFF5D3E1A : (hov ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 44, 16, active ? 0xFFFFAA00 : 0xFF3F4658);
                graphics.drawCenteredString(font, r + " RPM", btnX + 22, y + 48, active ? 0xFFFFD28C : 0xFFB0B8C8);
                btnX += 48;
            }
        } else {
            graphics.drawString(font, "§a⚡ " + Component.translatable("gui.gtcalcboard.kinetic_generator").getString(), x + 10, y + 32, 0xFFFFFFFF, false);
            graphics.drawString(font, "§8Fixed generation output (No speed modification)", x + 10, y + 48, 0xFF888888, false);
        }
    }

    public static boolean handleDialogHeaderClick(com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog dialog,
                                                 RecipeNode node, int x, int y, int dialogW,
                                                 double mouseX, double mouseY, int button,
                                                 net.minecraft.client.gui.components.EditBox parallelBox,
                                                 com.gtceu.calcboard.client.gui.BoardScreen parent) {
        if (!CreateModAdapter.isFanProcessingRecipe(node) && !node.isGenerator()) {
            int[] rpmPresets = {16, 32, 64, 128, 256};
            int btnX = x + 10;
            for (int r : rpmPresets) {
                if (mouseX >= btnX && mouseX <= btnX + 44 && mouseY >= y + 44 && mouseY <= y + 60) {
                    node.setRpm(r);
                    if (parent != null) parent.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
                btnX += 48;
            }
        }
        return false;
    }
}



