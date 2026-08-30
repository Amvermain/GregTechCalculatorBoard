package com.gtceu.calcboard.client.gui.compat.systeams;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.client.gui.compat.IModGuiHandler;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler;
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
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        boolean isGenOrFE = node.isGenerator() || node.getEnergyType() == EnergyType.ELECTRIC_FE;
        String badge = isGenOrFE
                ? Component.translatable("gui.gtcalcboard.dynamo_badge").getString()
                : Component.translatable("gui.gtcalcboard.boiler_badge").getString();
        int badgeW = Math.max(38, Minecraft.getInstance().font.width(badge) + 6);
        return mouseX >= x + 6 && mouseX <= x + 6 + badgeW && mouseY >= row2Y && mouseY <= row2Y + 14;
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
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            if (SysteamsRecipeHandler.isDynamoToBoilerConvertible(node)) {
                widget.commitCountEdit();
                SysteamsRecipeHandler.toggleDynamoBoilerMode(node);
                widget.invalidateCache();
                if (widget.getParent() != null) {
                    widget.getParent().rebuildWidgets();
                    widget.getParent().markSummaryDirty();
                }
                Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
                );
                return true;
            }
        }
        if (isMachineConfigHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            if (widget.getParent() != null) widget.getParent().openMachineConfigDialog(node);
            return true;
        }
        return false;
    }

    @Override
    public void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node,
                                   int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                   net.minecraft.client.gui.components.EditBox parallelBox, com.gtceu.calcboard.client.gui.BoardScreen parent) {
        if (!node.isMultiblock()) {
            int scale = node.getCombinedParallelMultiplier();
            String scaleInfo = scale > 1 ? " §7(§d⚡ " + scale + "x Scale§7)" : "";
            graphics.drawString(font, "§b" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_fixed").getString() + scaleInfo, x + 10, y + 32, 0xFFFFFFFF, false);
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

    @Override
    public boolean handleDialogHeaderClick(com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                           double mouseX, double mouseY, int button, net.minecraft.client.gui.components.EditBox parallelBox, com.gtceu.calcboard.client.gui.BoardScreen parent) {
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
