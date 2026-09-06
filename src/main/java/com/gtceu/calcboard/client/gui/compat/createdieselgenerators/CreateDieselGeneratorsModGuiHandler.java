package com.gtceu.calcboard.client.gui.compat.createdieselgenerators;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.client.gui.compat.IModGuiHandler;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class CreateDieselGeneratorsModGuiHandler implements IModGuiHandler {

    @Override
    public String getModId() {
        return "createdieselgenerators";
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing) {
        if (node.getEnergyType() == EnergyType.NONE) {
            String bannerText = "~ " + Component.translatable("gui.gtcalcboard.energy_passive_banner").getString();
            int bannerW = cardW - 12;
            NodeCardRenderer.drawBtn(graphics, font, bannerText, x + 6, row2Y, bannerW, 14, mouseX, mouseY, 0xFF88D49E, false, false);
            return;
        }

        if (node.isGenerator()) {
            String genBadge = "⚡ " + Component.translatable("gui.gtcalcboard.kinetic_generator").getString();
            int badgeW = cardW - 12;
            NodeCardRenderer.drawBtn(graphics, font, genBadge, x + 6, row2Y, badgeW, 14, mouseX, mouseY, 0xFF55FF88);
        } else {
            int rpm = node.getRpm() > 0 ? node.getRpm() : 32;
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

    @Override
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node.isGenerator() || node.getEnergyType() == EnergyType.NONE) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int rpm = node.getRpm() > 0 ? node.getRpm() : 32;
        int rpmW = Math.max(50, Minecraft.getInstance().font.width(rpm + " RPM") + 10);
        return mouseX >= x + 6 && mouseX <= x + 6 + rpmW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean isSecondaryControlHovered(RecipeNode node, double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            com.gtceu.calcboard.compat.create.CreateProperties.cycleRpm(node, button == 1 ? -1 : 1);
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
            com.gtceu.calcboard.compat.create.CreateProperties.cycleRpm(node, delta > 0 ? 1 : -1);
            if (widget.getParent() != null) widget.getParent().markSummaryDirty();
            widget.invalidateCache();
            Minecraft.getInstance().getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
            );
            return true;
        }
        return false;
    }
}
