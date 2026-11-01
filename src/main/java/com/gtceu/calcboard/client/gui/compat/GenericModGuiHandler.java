package com.gtceu.calcboard.client.gui.compat;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

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
        renderCardControls(null, graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    @Override
    public void populateRow2Buttons(NodeWidget widget, Font font, RecipeNode node, int cardW, boolean isOperational, List<com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button> buttons) {
        if (node.getEnergyType() == EnergyType.NONE) {
            String bannerText = "~ " + Component.translatable("gui.gtcalcboard.energy_passive_banner").getString();
            int bannerW = cardW - 12;
            int textW = font.width(bannerText);
            buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(6, bannerW, bannerText, textW, 0xFF88D49E, false, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.BANNER, null));
            return;
        }

        GTVoltageTier tier = node.getTargetTier();
        String tierName = tier != null ? tier.getName() : "LV";
        int tierTextW = font.width(tierName);
        int tierBtnW = 32;
        int tierColor = tier != null ? tier.getColor() : 0xFFFFFFFF;
        buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(6, tierBtnW, tierName, tierTextW, tierColor, false, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.TIER, null));

        int nextRelX = 6 + tierBtnW + 4;
        FlowGraph graph = (widget != null && widget.getParent() != null) ? widget.getParent().getGraph() : (node != null ? node.getParentGraph() : null);
        List<com.gtceu.calcboard.api.property.NodeBadge> badges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(node, graph);
        for (com.gtceu.calcboard.api.property.NodeBadge badge : badges) {
            int textW = font.width(badge.text());
            int badgeW = textW + 8;
            if (nextRelX + badgeW > cardW - 46) break;
            buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, badgeW, badge.text(), textW, badge.outlineColor(), badge.isWarning(), false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.BADGE, badge));
            nextRelX += badgeW + 3;
        }

        if (node.isGenerator()) {
            String genBadge = Component.translatable("gui.gtcalcboard.gen_badge").getString();
            int genTextW = font.width(genBadge);
            int genW = Math.max(28, genTextW + 4);
            buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, genW, genBadge, genTextW, 0xFF55FF88, false, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.GEN, null));
            nextRelX += genW + 3;

            String dynamoPar = "⚙ " + node.getParallel() + "x";
            if (!node.getAddons().isEmpty()) {
                dynamoPar += " (+" + node.getAddons().size() + ")";
            }
            int parTextW = font.width(dynamoPar);
            int parW = Math.max(46, (cardW - 6) - nextRelX);
            int parCol = !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF;
            buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, parW, dynamoPar, parTextW, parCol, false, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.CONFIG, null));
        } else {
            String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
            String ocText = Component.translatable(ocKey).getString();
            int ocColor = node.getOverclockMode() == OverclockMode.PERFECT ? 0xFF55FF55 : 0xFFAAAAAA;
            int ocTextW = font.width(ocText);
            int ocW = Math.max(50, ocTextW + 6);
            buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, ocW, ocText, ocTextW, ocColor, false, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.OC, null));
            nextRelX += ocW + 3;

            String parLabel = "⚙ " + node.getTotalParallel() + "x";
            if (!node.getAddons().isEmpty()) {
                parLabel += " (+" + node.getAddons().size() + ")";
            }
            int parTextW = font.width(parLabel);
            int parW = Math.max(46, (cardW - 6) - nextRelX);
            int parCol = !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF;
            buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, parW, parLabel, parTextW, parCol, false, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.CONFIG, null));
        }
    }

    @Override
    public void renderCardControls(NodeWidget widget, GuiGraphics graphics, Font font, RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY, boolean isGlowing) {
        IModGuiHandler.super.renderCardControls(widget, graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
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
        int btnW = computeTierButtonWidth(node);
        return mouseX >= x + 6 && mouseX <= x + 6 + btnW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    private static int computeTierButtonWidth(RecipeNode node) {
        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        if (node.isLiquidBoilerRecipe() || (adapter != null && adapter.isBoilerRecipe(node))) {
            GTBoilerTier bTier = GTBoilerTier.getBoilerTier(node);
            return Math.max(54, safeFontWidth(bTier.getDisplayName(), 46) + 8);
        }
        if (node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            String prefix = node.isMultiblock() ? "▦ " : "";
            String steamText = prefix + node.getSteamMode().getDisplayName();
            return Math.max(48, safeFontWidth(steamText, 40) + 8);
        }
        GTVoltageTier tier = node.getTargetTier();
        String tierText = (tier != null ? tier.getName() : "LV");
        if (node.isMultiblock()) tierText = "▦ " + tierText;
        return Math.max(32, safeFontWidth(tierText, 24) + 8);
    }

    private static int computePostTierStartX(RecipeNode node, int x) {
        return x + 6 + computeTierButtonWidth(node) + 4;
    }

    private static int computePostBadgeStartX(RecipeNode node, FlowGraph graph, int x) {
        int nextCtrlX = computePostTierStartX(node, x);
        FlowGraph effectiveGraph = graph != null ? graph : (node != null ? node.getParentGraph() : null);
        List<com.gtceu.calcboard.api.property.NodeBadge> badges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(node, effectiveGraph);
        for (com.gtceu.calcboard.api.property.NodeBadge badge : badges) {
            int badgeW = safeFontWidth(badge.text(), 30) + 8;
            if (nextCtrlX + badgeW > x + node.getCardWidth() - 46) break;
            nextCtrlX += badgeW + 3;
        }
        return nextCtrlX;
    }

    @Override
    public boolean isTierOrSpeedControlHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        if (node == null) return false;
        if (hasCachedButtons(widget)) {
            return isCachedButtonHovered(widget, node, mouseX, mouseY,
                    btn -> btn.role() == com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.TIER
                            || btn.role() == com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.BANNER);
        }
        return isTierOrSpeedControlHovered(node, mouseX, mouseY);
    }

    @Override
    public boolean isSecondaryControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node == null || node.getEnergyType() == EnergyType.NONE || node.isGenerator() || node.isFusion() || node.getEnergyType() == EnergyType.HEAT_OR_SELF || (node.getSteamMode() != null && node.getSteamMode().isSteam())) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int ocX = computePostBadgeStartX(node, null, x);
        String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
        String ocText = Component.translatable(ocKey).getString();
        int ocW = Math.max(50, safeFontWidth(ocText, 44) + 6);
        if (ocX + ocW > x + node.getCardWidth() - 46) return false;
        return mouseX >= ocX && mouseX <= ocX + ocW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean isSecondaryControlHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        if (node == null) return false;
        if (hasCachedButtons(widget)) {
            return isCachedButtonHovered(widget, node, mouseX, mouseY,
                    btn -> btn.role() == com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.OC);
        }
        return isSecondaryControlHovered(node, mouseX, mouseY);
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node == null || node.getEnergyType() == EnergyType.NONE) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int postBadgeX = computePostBadgeStartX(node, null, x);
        int configStartX = computeConfigStartX(node, x, postBadgeX);
        return mouseX >= configStartX && mouseX <= x + node.getCardWidth() - 6 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    private static int computeConfigStartX(RecipeNode node, int x, int postBadgeX) {
        int maxMidEndX = x + node.getCardWidth() - 46;
        if (node.isGenerator()) {
            if (node.isFusion()) return postBadgeX;
            String genBadge = Component.translatable("gui.gtcalcboard.gen_badge").getString();
            int genW = Math.max(28, safeFontWidth(genBadge, 24) + 4);
            return (postBadgeX + genW <= maxMidEndX) ? postBadgeX + genW + 3 : postBadgeX;
        }
        boolean isSteam = node.getSteamMode() != null && node.getSteamMode().isSteam();
        if (node.isFusion() || node.getEnergyType() == EnergyType.HEAT_OR_SELF || isSteam) {
            return postBadgeX;
        }
        String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
        String ocText = Component.translatable(ocKey).getString();
        int ocW = Math.max(50, safeFontWidth(ocText, 44) + 6);
        return (postBadgeX + ocW <= maxMidEndX) ? postBadgeX + ocW + 3 : postBadgeX;
    }

    @Override
    public boolean isMachineConfigHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        if (node == null) return false;
        if (hasCachedButtons(widget)) {
            return isCachedButtonHovered(widget, node, mouseX, mouseY,
                    btn -> btn.role() == com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.CONFIG);
        }
        return isMachineConfigHovered(node, mouseX, mouseY);
    }

    private static boolean hasCachedButtons(NodeWidget widget) {
        return widget != null && widget.getTextCache() != null && !widget.getTextCache().getRow2Buttons().isEmpty();
    }

    private static boolean isCachedButtonHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY,
                                                 java.util.function.Predicate<com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button> filter) {
        int x = (int) node.getPosX();
        int row2Y = (int) node.getPosY() + 20 + 6 + 18;
        for (var btn : widget.getTextCache().getRow2Buttons()) {
            if (!filter.test(btn)) continue;
            int bx = x + btn.relX();
            if (mouseX >= bx && mouseX <= bx + btn.width() && mouseY >= row2Y && mouseY <= row2Y + 14) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (node == null) return false;
        if (isBadgeHovered(widget, node, mouseX, mouseY)) {
            return handleHoveredBadgeClick(widget, node, mouseX, mouseY);
        }

        if (isTierOrSpeedControlHovered(widget, node, mouseX, mouseY)) {
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

        if (isSecondaryControlHovered(widget, node, mouseX, mouseY)) {
            widget.commitCountEdit();
            OverclockMode oldOc = node.getOverclockMode();
            OverclockMode newOc = (oldOc == OverclockMode.STANDARD) ? OverclockMode.PERFECT : OverclockMode.STANDARD;
            node.setOverclockMode(newOc);
            if (widget.getParent() != null) {
                widget.getParent().recordCommand(BoardCommand.ModifyPropertyCommand.overclockMode(node.getId(), oldOc, newOc));
                var frame = widget.getParent().getGraph().findFrameEnclosingNode(node);
                if (frame != null && frame.isSharedMachineFrame()) {
                    frame.syncHardwareConfig(node, widget.getParent().getGraph());
                    widget.getParent().rebuildBoardWidgets();
                }
                widget.getParent().markSummaryDirty();
            }
            widget.invalidateCache();
            return true;
        }

        if (isMachineConfigHovered(widget, node, mouseX, mouseY)) {
            widget.commitCountEdit();
            if (widget.getParent() != null) {
                widget.getParent().openMachineConfigDialog(node);
            }
            return true;
        }

        return false;
    }

    private boolean isBadgeHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        if (hasCachedButtons(widget)) {
            return isCachedButtonHovered(widget, node, mouseX, mouseY,
                    btn -> btn.role() == com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.BADGE);
        }
        return findHoveredUncachedBadge(widget, node, mouseX, mouseY) != null;
    }

    private boolean handleHoveredBadgeClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        if (hasCachedButtons(widget)) {
            int x = (int) node.getPosX();
            int row2Y = (int) node.getPosY() + 20 + 6 + 18;
            for (var btn : widget.getTextCache().getRow2Buttons()) {
                if (btn.role() != com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.BADGE || btn.badge() == null) continue;
                int bx = x + btn.relX();
                if (mouseX >= bx && mouseX <= bx + btn.width() && mouseY >= row2Y && mouseY <= row2Y + 14) {
                    return triggerBadgeClick(widget, btn.badge());
                }
            }
            return false;
        }
        com.gtceu.calcboard.api.property.NodeBadge badge = findHoveredUncachedBadge(widget, node, mouseX, mouseY);
        return badge != null && triggerBadgeClick(widget, badge);
    }

    private com.gtceu.calcboard.api.property.NodeBadge findHoveredUncachedBadge(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        int x = (int) node.getPosX();
        int row2Y = (int) node.getPosY() + 20 + 6 + 18;
        if (mouseY < row2Y || mouseY > row2Y + 14) return null;
        int nextCtrlX = computePostTierStartX(node, x);
        FlowGraph graph = (widget != null && widget.getParent() != null) ? widget.getParent().getGraph() : node.getParentGraph();
        List<com.gtceu.calcboard.api.property.NodeBadge> badges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(node, graph);
        for (com.gtceu.calcboard.api.property.NodeBadge badge : badges) {
            int badgeW = safeFontWidth(badge.text(), 30) + 8;
            if (nextCtrlX + badgeW > x + node.getCardWidth() - 46) break;
            if (mouseX >= nextCtrlX && mouseX <= nextCtrlX + badgeW) {
                return badge;
            }
            nextCtrlX += badgeW + 3;
        }
        return null;
    }

    private boolean triggerBadgeClick(NodeWidget widget, com.gtceu.calcboard.api.property.NodeBadge badge) {
        if (badge.onClick() == null) return false;
        widget.commitCountEdit();
        badge.onClick().run();
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
        );
        widget.invalidateCache();
        notifyWidgetParentUpdated(widget);
        return true;
    }

    private void notifyWidgetParentUpdated(NodeWidget widget) {
        if (widget.getParent() == null) return;
        if (widget.getNode() != null && widget.getNode().isBaseNode() && widget.getParent().getGraph() != null) {
            widget.getParent().getGraph().setBaseNode(widget.getNode());
        }
        widget.getParent().rebuildBoardWidgets();
        widget.getParent().markSummaryDirty();
    }

    @Override
    public boolean handleControlScroll(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, double delta) {
        if (isTierOrSpeedControlHovered(widget, node, mouseX, mouseY)) {
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
