package com.gtceu.calcboard.client.gui.compat;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
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

/**
 * Generic fallback implementation of {@link IModGuiHandler}.
 */
@OnlyIn(Dist.CLIENT)
public class GenericModGuiHandler implements IModGuiHandler {

    @Override
    public String getModId() {
        return "generic";
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing) {
        if (node.getEnergyType() == EnergyType.NONE) {
            String bannerText = "~ " + Component.translatable("gui.gtcalcboard.energy_passive_banner").getString();
            int bannerW = cardW - 12;
            NodeCardRenderer.drawBtn(graphics, font, bannerText, x + 6, row2Y, bannerW, 14, mouseX, mouseY, 0xFF88D49E, false, false);
            return;
        }

        GTVoltageTier tier = node.getTargetTier();
        NodeCardRenderer.drawBtn(graphics, font, tier.getName(), x + 6, row2Y, 32, 14, mouseX, mouseY, tier.getColor());

        int nextCtrlX = x + 42;
        if (node.isGenerator()) {
            String genBadge = Component.translatable("gui.gtcalcboard.gen_badge").getString();
            int genW = Math.max(28, font.width(genBadge) + 4);
            NodeCardRenderer.drawBtn(graphics, font, genBadge, nextCtrlX, row2Y, genW, 14, mouseX, mouseY, 0xFF55FF88);
            nextCtrlX += genW + 3;

            String dynamoPar = "⚙ " + node.getParallel() + "x";
            if (!node.getAddons().isEmpty()) {
                dynamoPar += " (+" + node.getAddons().size() + ")";
            }
            int parW = Math.max(46, (x + cardW - 6) - nextCtrlX);
            NodeCardRenderer.drawBtn(graphics, font, dynamoPar, nextCtrlX, row2Y, parW, 14, mouseX, mouseY, !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF, isGlowing);
        } else {
            String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
            String ocText = Component.translatable(ocKey).getString();
            int ocColor = node.getOverclockMode() == OverclockMode.PERFECT ? 0xFF55FF55 : 0xFFAAAAAA;
            int ocW = Math.max(50, font.width(ocText) + 6);
            NodeCardRenderer.drawBtn(graphics, font, ocText, nextCtrlX, row2Y, ocW, 14, mouseX, mouseY, ocColor);
            nextCtrlX += ocW + 3;

            String parLabel = "⚙ " + node.getTotalParallel() + "x";
            if (!node.getAddons().isEmpty()) {
                parLabel += " (+" + node.getAddons().size() + ")";
            }
            int parW = Math.max(46, (x + cardW - 6) - nextCtrlX);
            NodeCardRenderer.drawBtn(graphics, font, parLabel, nextCtrlX, row2Y, parW, 14, mouseX, mouseY, !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF, isGlowing);
        }
    }

    @Override
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node == null) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        if (node.getEnergyType() == EnergyType.NONE) {
            int cardW = node.getCardWidth();
            return mouseX >= x + 6 && mouseX <= x + cardW - 6 && mouseY >= row2Y && mouseY <= row2Y + 14;
        }
        int btnW = 32;
        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (node.isLiquidBoilerRecipe() || (adapter != null && adapter.isBoilerRecipe(node))) {
            GTBoilerTier bTier = GTBoilerTier.getBoilerTier(node);
            btnW = Math.max(54, safeFontWidth(bTier.getDisplayName(), 46) + 8);
        } else if (!node.isMultiblock() && node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            String steamText = node.getSteamMode().getDisplayName();
            btnW = Math.max(48, safeFontWidth(steamText, 40) + 8);
        } else {
            GTVoltageTier tier = node.getTargetTier();
            String tierText = (tier != null ? tier.getName() : "LV");
            if (node.isMultiblock()) tierText = "▦ " + tierText;
            btnW = Math.max(32, safeFontWidth(tierText, 24) + 8);
        }
        return mouseX >= x + 6 && mouseX <= x + 6 + btnW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean isSecondaryControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node == null || node.getEnergyType() == EnergyType.NONE || node.isGenerator() || node.isFusion() || node.getEnergyType() == EnergyType.HEAT_OR_SELF || (!node.isMultiblock() && node.getSteamMode() != null && node.getSteamMode().isSteam())) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int ocX = x + 42;
        int ocW = Math.max(50, safeFontWidth("STD OC", 44) + 6);
        return mouseX >= ocX && mouseX <= ocX + ocW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node == null || node.getEnergyType() == EnergyType.NONE) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int configStartX;
        if (node.isGenerator()) {
            String genBadge = Component.translatable("gui.gtcalcboard.gen_badge").getString();
            int genW = Math.max(28, safeFontWidth(genBadge, 24) + 4);
            configStartX = x + 42 + genW + 3;
        } else {
            String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
            String ocText = Component.translatable(ocKey).getString();
            int ocW = Math.max(50, safeFontWidth(ocText, 44) + 6);
            configStartX = x + 42 + ocW + 3;
        }
        return mouseX >= configStartX && mouseX <= x + node.getCardWidth() - 6 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            var adapter = ModAdapterRegistry.getAdapterForNode(node);
            if (node.isMultiblock() && !node.isGenerator() && !node.isTurbine() && adapter != null && adapter.supportsAddons(node)) {
                if (widget.getParent() != null) {
                    widget.getParent().openMachineConfigDialog(node, AddonCategory.ENERGY_HATCH);
                }
                return true;
            }
            int direction = (button == 1) ? -1 : 1;
            return widget.changeTier(direction);
        }

        if (isSecondaryControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            OverclockMode oldOc = node.getOverclockMode();
            OverclockMode newOc = (oldOc == OverclockMode.STANDARD) ? OverclockMode.PERFECT : OverclockMode.STANDARD;
            node.setOverclockMode(newOc);
            if (widget.getParent() != null) {
                widget.getParent().recordCommand(BoardCommand.ModifyPropertyCommand.overclockMode(node.getId(), oldOc, newOc));
                var frame = widget.getParent().getGraph().findFrameEnclosingNode(node);
                if (frame != null && frame.isSharedMachineFrame()) {
                    frame.syncHardwareConfig(node, widget.getParent().getGraph());
                    widget.getParent().rebuildWidgets();
                }
                widget.getParent().markSummaryDirty();
            }
            widget.invalidateCache();
            return true;
        }

        if (isMachineConfigHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            if (widget.getParent() != null) {
                widget.getParent().openMachineConfigDialog(node);
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean handleControlScroll(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, double delta) {
        if (isTierOrSpeedControlHovered(node, mouseX, mouseY)) {
            widget.commitCountEdit();
            return widget.changeTier(delta > 0 ? 1 : -1);
        }
        return false;
    }

    @Override
    public void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node, int x, int y, int dialogW,
                                   int mouseX, int mouseY, float partialTicks,
                                   EditBox parallelBox, BoardScreen parent) {
        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (!node.isMultiblock() && (adapter == null || !adapter.supportsAddons(node))) {
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

            if (node.isGenerator()) {
                int autoBtnX = x + dialogW - 98;
                boolean autoHov = mouseX >= autoBtnX && mouseX <= autoBtnX + 90 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(autoBtnX, y + 44, autoBtnX + 90, y + 60, autoHov ? 0xFF285078 : 0xFF282D3B);
                graphics.renderOutline(autoBtnX, y + 44, 90, 16, autoHov ? 0xFF589CFF : 0xFF3F4658);
                graphics.drawCenteredString(font, "⚡ " + Component.translatable("gui.gtcalcboard.config.auto_parallel").getString(), autoBtnX + 45, y + 48, 0xFF58D3FF);
            }
        }
    }

    @Override
    public boolean handleDialogHeaderClick(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                           double mouseX, double mouseY, int button,
                                           EditBox parallelBox, BoardScreen parent) {
        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (!node.isMultiblock() && (adapter == null || !adapter.supportsAddons(node))) {
            return false;
        }
        int[] quickPars = {1, 4, 16, 64, 256};
        int btnX = x + 64;
        for (int qp : quickPars) {
            if (mouseX >= btnX && mouseX <= btnX + 34 && mouseY >= y + 44 && mouseY <= y + 60) {
                node.setParallel(qp);
                if (parallelBox != null) parallelBox.setValue(String.valueOf(qp));
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
            btnX += 38;
        }
        if (node.isGenerator()) {
            int autoBtnX = x + dialogW - 98;
            if (mouseX >= autoBtnX && mouseX <= autoBtnX + 90 && mouseY >= y + 44 && mouseY <= y + 60) {
                node.autoCalculateTurbineParallel();
                if (parallelBox != null) parallelBox.setValue(String.valueOf(node.getParallel()));
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
        }
        return false;
    }

    protected static int safeFontWidth(String text, int defaultWidth) {
        try {
            var mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) {
                return mc.font.width(text);
            }
        } catch (Throwable ignored) {}
        return defaultWidth;
    }
}
