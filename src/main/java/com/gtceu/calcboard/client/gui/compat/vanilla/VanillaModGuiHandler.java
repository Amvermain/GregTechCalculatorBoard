package com.gtceu.calcboard.client.gui.compat.vanilla;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.compat.IModGuiHandler;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Vanilla Minecraft implementation of {@link IModGuiHandler}.
 */
@OnlyIn(Dist.CLIENT)
public class VanillaModGuiHandler implements IModGuiHandler {

    @Override
    public String getModId() {
        return "minecraft";
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing) {
        String bannerText = "🍃 " + Component.translatable("gui.gtcalcboard.energy_passive_banner").getString();
        int bannerW = cardW - 12;
        NodeCardRenderer.drawBtn(graphics, font, bannerText, x + 6, row2Y, bannerW, 14, mouseX, mouseY, 0xFF88D49E, false, false);
    }

    @Override
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node == null || node.isModule()) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + NodeWidget.HEADER_HEIGHT + 6;
        int row2Y = ctrlY + 18;
        int cardW = node.getCardWidth();
        return mouseX >= x + 6 && mouseX <= x + cardW - 6 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            return widget.changeTier(button == 1 ? -1 : 1);
        }
        return false;
    }

    @Override
    public void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int dialogW,
                                   int mouseX, int mouseY, float partialTicks, EditBox parallelBox, BoardScreen parent) {
        graphics.drawString(font, "§b" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_fixed").getString(), x + 10, y + 32, 0xFFFFFFFF, false);
        graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_desc").getString(), x + 10, y + 48, 0xFF888888, false);
    }

    @Override
    public boolean handleDialogHeaderClick(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                           double mouseX, double mouseY, int button, EditBox parallelBox, BoardScreen parent) {
        return false;
    }
}
