package com.gtceu.calcboard.client.gui.compat.createnewage;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.compat.IModGuiHandler;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Locale;

/**
 * Create New Age implementation of {@link IModGuiHandler}.
 */
@OnlyIn(Dist.CLIENT)
public class CreateNewAgeModGuiHandler implements IModGuiHandler {

    @Override
    public String getModId() {
        return "create_new_age";
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing) {
        boolean supportsAddons = ModAdapterRegistry.getAdapterForNode(node).supportsAddons(node);

        int rpm = node.getRpm();
        String rpmText = rpm + " RPM";
        int rpmW = Math.max(50, font.width(rpmText) + 10);
        NodeCardRenderer.drawBtn(graphics, font, rpmText, x + 6, row2Y, rpmW, 14, mouseX, mouseY, 0xFFFFAA00);

        int nextCtrlX = x + 6 + rpmW + 3;
        int nextW = (x + cardW - 6) - nextCtrlX;

        if (supportsAddons) {
            int magnetForce = 0;
            for (MachineAddon addon : node.getAddons()) {
                if (addon.getCategory().equals(AddonCategory.MAGNET) || addon.getMagneticForce() > 0) {
                    magnetForce += addon.getMagneticForce();
                }
            }

            String addonLabel = "🧲 0x";
            if (magnetForce > 0) {
                addonLabel = "🧲 " + magnetForce + "x";
            }
            if (!node.getAddons().isEmpty()) {
                addonLabel += " (+" + node.getAddons().size() + ")";
            }

            int color = !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF;
            NodeCardRenderer.drawBtn(graphics, font, addonLabel, nextCtrlX, row2Y, nextW, 14, mouseX, mouseY, color, isGlowing);
        } else if (node.isGenerator()) {
            String genBadge = "⚡ " + Component.translatable("gui.gtcalcboard.kinetic_generator").getString();
            if (font.width(genBadge) > nextW - 4) {
                genBadge = "⚡ Gen";
            }
            NodeCardRenderer.drawBtn(graphics, font, genBadge, nextCtrlX, row2Y, nextW, 14, mouseX, mouseY, 0xFF55FF88);
        } else {
            String rscText = "⚡ Motor";
            NodeCardRenderer.drawBtn(graphics, font, rscText, nextCtrlX, row2Y, nextW, 14, mouseX, mouseY, 0xFFE07A28);
        }
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        if (!ModAdapterRegistry.getAdapterForNode(node).supportsAddons(node)) {
            return false;
        }
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;

        int rpmW = Math.max(50, Minecraft.getInstance().font.width(node.getRpm() + " RPM") + 10);
        int configStartX = x + 6 + rpmW + 3;
        return mouseX >= configStartX && mouseX <= x + node.getCardWidth() - 6 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int rpmW = Math.max(50, Minecraft.getInstance().font.width(node.getRpm() + " RPM") + 10);
        return mouseX >= x + 6 && mouseX <= x + 6 + rpmW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (isMachineConfigHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            if (widget.getParent() != null) {
                widget.getParent().openMachineConfigDialog(node);
            }
            return true;
        }
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

    @Override
    public boolean handleControlScroll(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, double delta) {
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

    @Override
    public void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node,
                                   int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                   EditBox parallelBox, BoardScreen parent) {
        int totalStrength = 0;
        int magnetCount = 0;
        for (MachineAddon addon : node.getAddons()) {
            if (addon.getCategory().equals(AddonCategory.MAGNET) || addon.getMagneticForce() > 0) {
                totalStrength += addon.getMagneticForce();
                magnetCount++;
            }
        }

        if (node.isGenerator()) {
            String magnetHeader = String.format(Locale.ROOT, "§6🧲 %s: §e%d/12 §7- Total Force: §e%d",
                    Component.translatable("gui.gtcalcboard.addon_cat.magnet").getString(), magnetCount, totalStrength);
            graphics.drawString(font, magnetHeader, x + 10, y + 30, 0xFFFFFFFF, false);

            int rpm = node.getRpm();
            int[] rpmPresets = {16, 32, 64, 128, 256};
            int btnX = x + 10;
            for (int r : rpmPresets) {
                boolean active = rpm == r;
                boolean hov = mouseX >= btnX && mouseX <= btnX + 44 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 44, y + 60, active ? 0xFF5D3E1A : (hov ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 44, 16, active ? 0xFFFFAA00 : 0xFF3F4658);
                graphics.drawCenteredString(font, r + " RPM", btnX + 22, y + 48, active ? 0xFFFFD28C : 0xFFB0B8C8);
                btnX += 48;
            }

            int clearBtnX = x + dialogW - 108;
            boolean clearHov = mouseX >= clearBtnX && mouseX <= clearBtnX + 100 && mouseY >= y + 44 && mouseY <= y + 60;
            graphics.fill(clearBtnX, y + 44, clearBtnX + 100, y + 60, clearHov ? 0xFF882222 : 0xFF442222);
            graphics.renderOutline(clearBtnX, y + 44, 100, 16, clearHov ? 0xFFFF5555 : 0xFF552222);
            graphics.drawCenteredString(font, "✕ Clear Magnets", clearBtnX + 50, y + 48, 0xFFFFFFFF);
        } else {
            graphics.drawString(font, "§6⚡ Motor Speed: §e" + node.getRpm() + " RPM", x + 10, y + 30, 0xFFFFFFFF, false);
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
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
            btnX += 48;
        }

        if (node.isGenerator()) {
            int clearBtnX = x + dialogW - 108;
            if (mouseX >= clearBtnX && mouseX <= clearBtnX + 100 && mouseY >= y + 44 && mouseY <= y + 60) {
                node.getAddons().removeIf(a -> a.getCategory().equals(AddonCategory.MAGNET) || a.getMagneticForce() > 0);
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
        }
        return false;
    }
}
