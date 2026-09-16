package com.gtceu.calcboard.client.gui.compat.tfg;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.compat.tfg.TFGBoilerPhysics;
import com.gtceu.calcboard.compat.tfg.TFGBoilerProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Dedicated GUI renderer and interaction handler for TFG Large Boiler headers in {@link MachineConfigDialog}.
 * Fits within the 40px Section 1 area (y + 26 to y + 66) without overlapping adjacent dialog components.
 */
@OnlyIn(Dist.CLIENT)
public final class TFGBoilerDialogRenderer {

    private TFGBoilerDialogRenderer() {}

    public static void renderBoilerDialogHeader(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
                                                int x, int y, int dialogW, int mouseX, int mouseY) {
        boolean isSteel = TFGBoilerPhysics.isSteelBoiler(node);
        renderRow1Controls(dialog, graphics, font, node, x, y, dialogW, isSteel, mouseX, mouseY);
        renderRow2Controls(dialog, graphics, font, node, x, y, dialogW, mouseX, mouseY);
    }

    private static void renderRow1Controls(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
                                           int x, int y, int dialogW, boolean isSteel, int mouseX, int mouseY) {
        int r1Y = y + 28;
        int curX = x + 10;

        int tierW = 120;
        boolean tierHover = mouseX >= curX && mouseX <= curX + tierW && mouseY >= r1Y && mouseY <= r1Y + 14;
        graphics.fill(curX, r1Y, curX + tierW, r1Y + 14, isSteel ? 0xFF384656 : 0xFF5D3E1A);
        graphics.renderOutline(curX, r1Y, tierW, 14, isSteel ? (tierHover ? 0xFFFFFFFF : 0xFFAAAAAA) : (tierHover ? 0xFFFFD28C : 0xFFD28C38));
        String tierText = isSteel ? "★ L-Steel (1280PU)" : "♨ L-Bronze (480PU)";
        graphics.drawCenteredString(font, tierText, curX + tierW / 2, r1Y + 3, isSteel ? 0xFFEEEEEE : 0xFFFFD28C);
        if (tierHover && dialog != null) {
            dialog.setDeferredTooltip(List.of(
                    Component.literal(isSteel ? "§6★ Large Steel Boiler" : "§6♨ Large Bronze Boiler"),
                    Component.literal(isSteel
                            ? "§7Base Pressure: §f1,280 PU §8(25,600 mB/s)\n§eEnables Advanced Boosters (+1200..+16000PU) & Super Boiler Dual Fuel"
                            : "§7Base Pressure: §f480 PU §8(9,600 mB/s)\n§7Supports standard boosters (Creosote, Pitch, Sap, Olive Oil)"),
                    Component.literal("§8Click to toggle boiler tier")
            ));
        }
        curX += tierW + 4;

        if (isSteel) {
            boolean isSuper = TFGBoilerPhysics.isSuperBoilerMode(node);
            int modeW = 95;
            boolean modeHover = mouseX >= curX && mouseX <= curX + modeW && mouseY >= r1Y && mouseY <= r1Y + 14;
            graphics.fill(curX, r1Y, curX + modeW, r1Y + 14, isSuper ? 0xFF5A2E1A : 0xFF224422);
            graphics.renderOutline(curX, r1Y, modeW, 14, isSuper ? (modeHover ? 0xFFFFAA33 : 0xFFFF8800) : (modeHover ? 0xFF88FF88 : 0xFF55FF55));
            String modeText = isSuper ? "🔥 Super Boiler" : "● Standard";
            graphics.drawCenteredString(font, modeText, curX + modeW / 2, r1Y + 3, isSuper ? 0xFFFF8800 : 0xFF55FF55);
            if (modeHover && dialog != null) {
                dialog.setDeferredTooltip(List.of(
                        Component.literal(isSuper ? "§6🔥 Super Boiler (Dual Fuel)" : "§a● Standard Mode"),
                        Component.literal(isSuper
                                ? "§7Burns 1x Item Binder (Bio Chaff / Coal) + 20..80k mB Liquid Fuel.\n§7Cycles are accelerated by elevated pressure."
                                : "§7Standard single-fuel steam generation mode."),
                        Component.literal("§8Click to switch mode")
                ));
            }
        }

        renderThrottleControls(dialog, graphics, font, node, x, y, dialogW, mouseX, mouseY);
    }

    private static void renderThrottleControls(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
                                               int x, int y, int dialogW, int mouseX, int mouseY) {
        int curThrottle = node.getBoilerThrottle();
        int thrX = x + dialogW - 215;
        String thrTitle = "§e⚡ Thr:";
        graphics.drawString(font, thrTitle, thrX, y + 31, 0xFFFFFFFF, false);
        int titleW = font.width(thrTitle);

        int minusX = thrX + titleW + 4;
        boolean minusHover = mouseX >= minusX && mouseX <= minusX + 14 && mouseY >= y + 28 && mouseY <= y + 42;
        graphics.fill(minusX, y + 28, minusX + 14, y + 42, minusHover ? 0xFF3D4558 : 0xFF242A35);
        graphics.renderOutline(minusX, y + 28, 14, 14, minusHover ? 0xFF58D3FF : 0xFF3F4658);
        graphics.drawCenteredString(font, "-", minusX + 7, y + 31, 0xFFFFFFFF);

        int valX = minusX + 16;
        graphics.fill(valX, y + 28, valX + 30, y + 42, 0xFF1B202A);
        graphics.renderOutline(valX, y + 28, 30, 14, 0xFF3F4658);
        graphics.drawCenteredString(font, curThrottle + "%", valX + 15, y + 31, 0xFF58D3FF);

        int plusX = valX + 32;
        boolean plusHover = mouseX >= plusX && mouseX <= plusX + 14 && mouseY >= y + 28 && mouseY <= y + 42;
        graphics.fill(plusX, y + 28, plusX + 14, y + 42, plusHover ? 0xFF3D4558 : 0xFF242A35);
        graphics.renderOutline(plusX, y + 28, 14, 14, plusHover ? 0xFF58D3FF : 0xFF3F4658);
        graphics.drawCenteredString(font, "+", plusX + 7, y + 31, 0xFFFFFFFF);

        int[] presets = {25, 50, 75, 100};
        int curPreX = plusX + 16;
        for (int pre : presets) {
            int preW = pre == 100 ? 26 : 22;
            boolean active = curThrottle == pre;
            boolean preHover = mouseX >= curPreX && mouseX <= curPreX + preW && mouseY >= y + 28 && mouseY <= y + 42;
            graphics.fill(curPreX, y + 28, curPreX + preW, y + 42, active ? 0xFF2A5288 : (preHover ? 0xFF3D4558 : 0xFF242A35));
            graphics.renderOutline(curPreX, y + 28, preW, 14, active ? 0xFF589CFF : 0xFF3F4658);
            graphics.drawCenteredString(font, pre + "%", curPreX + preW / 2, y + 31, active ? 0xFF58D3FF : 0xFFB0B8C8);
            curPreX += preW + 2;
        }

        boolean anyThrHover = mouseX >= thrX && mouseX <= curPreX && mouseY >= y + 28 && mouseY <= y + 42;
        if (anyThrHover && dialog != null && dialog.getDeferredTooltip() == null) {
            dialog.setDeferredTooltip(List.of(
                    Component.literal("§e⚡ Boiler Throttle: §f" + curThrottle + "%"),
                    Component.literal("§7Scales steam generation and water consumption."),
                    Component.literal("§8Click [-]/[+] for 5% steps or click preset buttons")
            ));
        }
    }

    private static void renderRow2Controls(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
                                           int x, int y, int dialogW, int mouseX, int mouseY) {
        int r2Y = y + 46;
        int waterX = x + 10;
        int waterW = 120;
        int waterTier = node.getProperties().get(TFGBoilerProperties.WATER_TIER);
        boolean waterHover = mouseX >= waterX && mouseX <= waterX + waterW && mouseY >= r2Y && mouseY <= r2Y + 16;
        graphics.fill(waterX, r2Y, waterX + waterW, r2Y + 16, waterTier == 1 ? 0xFF1B4D4B : 0xFF1C344D);
        graphics.renderOutline(waterX, r2Y, waterW, 16, waterTier == 1 ? (waterHover ? 0xFF66E0FF : 0xFF38BDF8) : (waterHover ? 0xFF88AAFF : 0xFF589CFF));
        String waterText = waterTier == 1 ? "✨ Distilled (1.5x)" : "💧 Water (1.0x)";
        graphics.drawCenteredString(font, waterText, waterX + waterW / 2, r2Y + 4, waterTier == 1 ? 0xFF38BDF8 : 0xFF589CFF);
        if (waterHover && dialog != null) {
            dialog.setDeferredTooltip(List.of(
                    Component.literal(waterTier == 1 ? "§b✨ Distilled Water Supply" : "§9💧 Standard Water Supply"),
                    Component.literal(waterTier == 1
                            ? "§7Steam Output: §a1.5x Boost\n§7Water Consumption: §fUnchanged (Identical to Standard)"
                            : "§7Steam Output: §f1.0x (Standard Rate)\n§7Standard water input requirement."),
                    Component.literal("§8Click to toggle water quality")
            ));
        }

        int boosterX = waterX + waterW + 4;
        int boosterW = x + dialogW - 10 - boosterX;
        TFGBoilerPhysics.BoosterFluid booster = TFGBoilerPhysics.getActiveBooster(node);
        boolean active = booster.index() > 0;
        boolean boosterHover = mouseX >= boosterX && mouseX <= boosterX + boosterW && mouseY >= r2Y && mouseY <= r2Y + 16;
        graphics.fill(boosterX, r2Y, boosterX + boosterW, r2Y + 16, active ? 0xFF3A2E1C : (boosterHover ? 0xFF3D4558 : 0xFF242A35));
        graphics.renderOutline(boosterX, r2Y, boosterW, 16, active ? (boosterHover ? 0xFFFFF080 : 0xFFFFD700) : (boosterHover ? 0xFF58D3FF : 0xFF3F4658));

        String boosterLabel = active
                ? String.format(Locale.ROOT, "🚀 %s (+%dPU) — %,.0f mB/s (%.2f mB/t)",
                TFGBoilerPhysics.resolveBoosterName(booster), booster.pressureBonus(), booster.consumptionMbPerSec(), booster.consumptionMbPerTick())
                : "🚀 Booster: [None] (Click to cycle catalysts)";
        graphics.drawString(font, boosterLabel, boosterX + 6, r2Y + 4, active ? 0xFFFFD700 : 0xFFCCCCCC, false);

        if (boosterHover && dialog != null) {
            List<Component> tt = new ArrayList<>();
            TFGBoilerPhysics.buildBoosterTooltip(node, tt);
            tt.add(Component.literal("§8[Left-Click]: Next booster / [Right-Click]: Previous booster"));
            dialog.setDeferredTooltip(tt);
        }
    }

    public static boolean handleBoilerHeaderClick(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                                  double mouseX, double mouseY, int button, BoardScreen parent) {
        if (handleThrottleClick(node, x, y, dialogW, mouseX, mouseY, parent)) {
            return true;
        }

        int r1Y = y + 28;
        int curX = x + 10;
        int tierW = 120;

        if (mouseX >= curX && mouseX <= curX + tierW && mouseY >= r1Y && mouseY <= r1Y + 14) {
            TFGBoilerPhysics.toggleBoilerTier(node);
            notifyParent(parent);
            return true;
        }
        curX += tierW + 4;

        boolean isSteel = TFGBoilerPhysics.isSteelBoiler(node);
        if (isSteel) {
            int modeW = 95;
            if (mouseX >= curX && mouseX <= curX + modeW && mouseY >= r1Y && mouseY <= r1Y + 14) {
                TFGBoilerPhysics.cycleBoilerMode(node);
                notifyParent(parent);
                return true;
            }
        }

        int r2Y = y + 46;
        int waterX = x + 10;
        int waterW = 120;
        if (mouseX >= waterX && mouseX <= waterX + waterW && mouseY >= r2Y && mouseY <= r2Y + 16) {
            TFGBoilerPhysics.cycleWaterTier(node);
            notifyParent(parent);
            return true;
        }

        int boosterX = waterX + waterW + 4;
        int boosterW = x + dialogW - 10 - boosterX;
        if (mouseX >= boosterX && mouseX <= boosterX + boosterW && mouseY >= r2Y && mouseY <= r2Y + 16) {
            int direction = (button == 1) ? -1 : 1;
            TFGBoilerPhysics.cycleBooster(node, direction);
            notifyParent(parent);
            return true;
        }

        return false;
    }

    private static boolean handleThrottleClick(RecipeNode node, int x, int y, int dialogW,
                                                double mouseX, double mouseY, BoardScreen parent) {
        int curThrottle = node.getBoilerThrottle();
        int thrX = x + dialogW - 215;
        String thrTitle = "§e⚡ Thr:";
        int titleW = (Minecraft.getInstance() != null && Minecraft.getInstance().font != null)
                ? Minecraft.getInstance().font.width(thrTitle) : 40;
        int minusX = thrX + titleW + 4;

        if (mouseX >= minusX && mouseX <= minusX + 14 && mouseY >= y + 28 && mouseY <= y + 42) {
            node.setBoilerThrottle(Math.max(25, curThrottle - 5));
            TFGBoilerPhysics.syncDynamicPorts(node);
            notifyParent(parent);
            return true;
        }

        int valX = minusX + 16;
        int plusX = valX + 32;
        if (mouseX >= plusX && mouseX <= plusX + 14 && mouseY >= y + 28 && mouseY <= y + 42) {
            node.setBoilerThrottle(Math.min(100, curThrottle + 5));
            TFGBoilerPhysics.syncDynamicPorts(node);
            notifyParent(parent);
            return true;
        }

        int[] presets = {25, 50, 75, 100};
        int curPreX = plusX + 16;
        for (int pre : presets) {
            int preW = pre == 100 ? 26 : 22;
            if (mouseX >= curPreX && mouseX <= curPreX + preW && mouseY >= y + 28 && mouseY <= y + 42) {
                node.setBoilerThrottle(pre);
                TFGBoilerPhysics.syncDynamicPorts(node);
                notifyParent(parent);
                return true;
            }
            curPreX += preW + 2;
        }
        return false;
    }

    private static void notifyParent(BoardScreen parent) {
        if (parent != null) {
            parent.markSummaryDirty();
        }
        try {
            if (Minecraft.getInstance() != null && Minecraft.getInstance().getSoundManager() != null) {
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
            }
        } catch (Throwable ignored) {}
    }
}
