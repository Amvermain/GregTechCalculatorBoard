package com.gtceu.calcboard.client.gui.compat.greate;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.compat.IModGuiHandler;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.create.CreateProperties;
import com.gtceu.calcboard.compat.greate.GreateProperties;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Client GUI handler for Greate tiered kinetic machines.
 * Displays ULS-UHS stress tiers, operating RPM, and speed controls.
 */
@OnlyIn(Dist.CLIENT)
public class GreateModGuiHandler implements IModGuiHandler {

    @Override
    public String getModId() {
        return "greate";
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing) {
        int machineTier = node.getProperties().get(GreateProperties.MACHINE_TIER);
        String tierName = GreateProperties.getTierName(machineTier);
        int tierColor = GreateProperties.getTierColor(machineTier);

        int tierW = 32;
        NodeCardRenderer.drawBtn(graphics, font, tierName, x + 6, row2Y, tierW, 14, mouseX, mouseY, tierColor);

        int rpm = node.getRpm();
        String rpmText = rpm + " RPM";
        int rpmW = Math.max(48, font.width(rpmText) + 8);
        int rpmX = x + 6 + tierW + 3;
        NodeCardRenderer.drawBtn(graphics, font, rpmText, rpmX, row2Y, rpmW, 14, mouseX, mouseY, 0xFFFFAA00);

        int rscX = rpmX + rpmW + 3;
        int rscW = (x + cardW - 6) - rscX;
        String rscText = "⚙ RSC";
        NodeCardRenderer.drawBtn(graphics, font, rscText, rscX, row2Y, rscW, 14, mouseX, mouseY, 0xFFE07A28);
    }

    @Override
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node == null) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int row2Y = y + 20 + 6 + 18;
        return mouseX >= x + 6 && mouseX <= x + 6 + 32 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean isSecondaryControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node == null) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int row2Y = y + 20 + 6 + 18;
        int rpmW = Math.max(48, safeFontWidth(node.getRpm() + " RPM", 40) + 8);
        int rpmX = x + 6 + 32 + 3;
        return mouseX >= rpmX && mouseX <= rpmX + rpmW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node == null) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int row2Y = y + 20 + 6 + 18;
        int rpmW = Math.max(48, safeFontWidth(node.getRpm() + " RPM", 40) + 8);
        int rscX = x + 6 + 32 + 3 + rpmW + 3;
        int rscW = (x + node.getCardWidth() - 6) - rscX;
        return mouseX >= rscX && mouseX <= rscX + rscW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            int direction = (button == 1) ? -1 : 1;
            GreateProperties.cycleTier(node, direction);
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            playClickSound(1.2F);
            return true;
        }

        if (isSecondaryControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            CreateProperties.cycleRpm(node, button == 1 ? -1 : 1);
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            playClickSound(1.2F);
            return true;
        }

        if (isMachineConfigHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            int curRpm = node.getRpm();
            node.setRpm(curRpm == 256 ? 32 : 256);
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            playClickSound(1.4F);
            return true;
        }

        return false;
    }

    @Override
    public boolean handleControlScroll(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, double delta) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            GreateProperties.cycleTier(node, delta > 0 ? 1 : -1);
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            playClickSound(1.2F);
            return true;
        }

        if (isSecondaryControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            CreateProperties.cycleRpm(node, delta > 0 ? 1 : -1);
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            playClickSound(1.2F);
            return true;
        }

        return false;
    }

    private static int safeFontWidth(String text, int defaultWidth) {
        try {
            var mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) {
                return mc.font.width(text);
            }
        } catch (Throwable ignored) {}
        return defaultWidth;
    }

    private static void playClickSound(float pitch) {
        try {
            var mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), pitch));
            }
        } catch (Throwable ignored) {}
    }

    @Override
    public void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int dialogW,
                                   int mouseX, int mouseY, float partialTicks, EditBox parallelBox, BoardScreen parent) {
        int machineTier = node.getProperties().get(GreateProperties.MACHINE_TIER);
        String tierName = GreateProperties.getTierName(machineTier);
        double capacity = GreateProperties.getShaftCapacityForTier(machineTier);
        graphics.drawString(font, String.format(java.util.Locale.ROOT, "§6⚙ Greate [%s] §e%d RPM §7(Limit: %,.0f SU)", tierName, node.getRpm(), capacity), x + 10, y + 30, 0xFFFFFFFF, false);
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
    }

    @Override
    public boolean handleDialogHeaderClick(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                           double mouseX, double mouseY, int button, EditBox parallelBox, BoardScreen parent) {
        int[] rpmPresets = {16, 32, 64, 128, 256};
        int btnX = x + 10;
        for (int r : rpmPresets) {
            if (mouseX >= btnX && mouseX <= btnX + 44 && mouseY >= y + 44 && mouseY <= y + 60) {
                node.setRpm(r);
                if (parent != null) parent.markSummaryDirty();
                playClickSound(1.0F);
                return true;
            }
            btnX += 48;
        }
        return false;
    }
}
