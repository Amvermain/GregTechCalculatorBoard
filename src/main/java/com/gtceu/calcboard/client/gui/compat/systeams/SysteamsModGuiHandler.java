package com.gtceu.calcboard.client.gui.compat.systeams;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.client.gui.compat.IModGuiHandler;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Systeams implementation of {@link IModGuiHandler}.
 */
@OnlyIn(Dist.CLIENT)
public class SysteamsModGuiHandler implements IModGuiHandler {

    @Override
    public String getModId() {
        return "systeams";
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing) {
        boolean isGenOrFE = node.isGenerator() || node.getEnergyType() == EnergyType.ELECTRIC_FE;
        String badge = isGenOrFE
                ? Component.translatable("gui.gtcalcboard.dynamo_badge").getString()
                : Component.translatable("gui.gtcalcboard.boiler_badge").getString();
        int badgeColor = isGenOrFE ? 0xFFFF5555 : 0xFFFFAA33;
        int badgeW = Math.max(38, font.width(badge) + 6);
        NodeCardRenderer.drawBtn(graphics, font, badge, x + 6, row2Y, badgeW, 14, mouseX, mouseY, badgeColor);

        int nextCtrlX = x + 6 + badgeW + 3;
        String parLabel = "⚙ " + node.getTotalParallel() + "x";
        if (!node.getAddons().isEmpty()) {
            parLabel += " (+" + node.getAddons().size() + ")";
        }
        int parW = Math.max(46, (x + cardW - 6) - nextCtrlX);
        NodeCardRenderer.drawBtn(graphics, font, parLabel, nextCtrlX, row2Y, parW, 14, mouseX, mouseY, !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF, isGlowing);
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        boolean isGenOrFE = node.isGenerator() || node.getEnergyType() == EnergyType.ELECTRIC_FE;
        String badge = isGenOrFE
                ? Component.translatable("gui.gtcalcboard.dynamo_badge").getString()
                : Component.translatable("gui.gtcalcboard.boiler_badge").getString();
        int badgeW = Math.max(38, Minecraft.getInstance().font.width(badge) + 6);
        int configStartX = x + 6 + badgeW + 3;
        return mouseX >= configStartX && mouseX <= x + node.getCardWidth() - 6 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (isMachineConfigHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            if (widget.getParent() != null) widget.getParent().openMachineConfigDialog(node);
            return true;
        }
        return false;
    }
}
