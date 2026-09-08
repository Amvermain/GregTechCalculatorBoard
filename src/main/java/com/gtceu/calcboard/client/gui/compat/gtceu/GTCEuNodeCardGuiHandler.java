package com.gtceu.calcboard.client.gui.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeBadge;
import com.gtceu.calcboard.api.property.NodeBadgeRegistry;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.client.gui.compat.GenericModGuiHandler;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTFusionHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

/**
 * Dedicated GUI handler for GTCEu node card row 2 controls, badges, and click interactions.
 */
@OnlyIn(Dist.CLIENT)
public class GTCEuNodeCardGuiHandler {

    private final GenericModGuiHandler fallbackHandler;

    public GTCEuNodeCardGuiHandler(GenericModGuiHandler fallbackHandler) {
        this.fallbackHandler = fallbackHandler;
    }

    public static boolean isBoiler(RecipeNode node) {
        if (node == null) return false;
        if (node.isLiquidBoilerRecipe()) return true;
        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        return adapter != null && adapter.isBoilerRecipe(node);
    }

    public static boolean isFusionMachine(RecipeNode node) {
        if (node == null) return false;
        return node.isFusion() || GTFusionHelper.isFusion(node);
    }

    public static boolean isCoilMultiblock(RecipeNode node) {
        if (node == null || !node.isMultiblock()) return false;
        if (isFusionMachine(node)) return false;
        if (node.getMachineIcon() != null) {
            return MultiblockDetector.isCoilMultiblock(node.getMachineIcon())
                    || GTCEuCoilModifierHelper.getCoilMachineSpec(node.getMachineIcon()).kind() != GTCEuCoilModifierHelper.CoilMachineKind.GENERIC;
        }
        if (node.getMultiblockWorkstation() != null) {
            return MultiblockDetector.isCoilMultiblock(node.getMultiblockWorkstation())
                    || GTCEuCoilModifierHelper.getCoilMachineSpec(node.getMultiblockWorkstation()).kind() != GTCEuCoilModifierHelper.CoilMachineKind.GENERIC;
        }
        if (MultiblockDetector.isCoilRecipeCategory(node.getRecipeCategoryId())) return true;
        if (node.getRecipeTemperature() > 0) return true;
        return node.getProperties().get(GTCEuProperties.EBF_TEMPERATURE) > 0;
    }

    public void renderCardControls(GuiGraphics graphics, Font font,
                                   RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                   boolean isGlowing) {
        renderCardControls(null, graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    public void renderCardControls(NodeWidget widget, GuiGraphics graphics, Font font,
                                   RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                   boolean isGlowing) {
        List<com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button> buttons = (widget != null && widget.getTextCache() != null)
                ? widget.getTextCache().getRow2Buttons()
                : null;
        if (buttons != null && !buttons.isEmpty()) {
            for (com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button btn : buttons) {
                int btnX = x + btn.relX();
                boolean glow = btn.isGlowing() || (isGlowing && btn.role() == com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.CONFIG);
                NodeCardRenderer.drawBtn(graphics, font, btn.text(), btn.textWidth(), btnX, row2Y, btn.width(), 14, mouseX, mouseY, btn.color(), btn.isAlert(), glow);
            }
            return;
        }
        fallbackHandler.renderCardControls(widget, graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    public void populateRow2Buttons(NodeWidget widget, Font font, RecipeNode node, int cardW, boolean isOperational, List<com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button> buttons) {
        if (node.getEnergyType() == EnergyType.NONE) {
            String bannerText = "~ " + Component.translatable("gui.gtcalcboard.energy_passive_banner").getString();
            int bannerW = cardW - 12;
            int textW = font.width(bannerText);
            buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(6, bannerW, bannerText, textW, 0xFF88D49E, false, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.BANNER, null));
            return;
        }

        int nextRelX = 6;
        if (isBoiler(node)) {
            GTBoilerTier boilerTier = GTBoilerTier.getBoilerTier(node);
            String boilerText = boilerTier.getDisplayName();
            if (boilerTier.isMultiblock() && node.getBoilerThrottle() < 100) {
                boilerText += " (" + node.getBoilerThrottle() + "%)";
            }
            int textW = font.width(boilerText);
            int tierBtnW = Math.max(54, textW + 8);
            int boilerColor = !isOperational ? 0xFFFF8888 : boilerTier.getColor();
            buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, tierBtnW, boilerText, textW, boilerColor, !isOperational, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.TIER, null));
            nextRelX += tierBtnW + 4;
        } else if (!node.isMultiblock() && node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            String steamText = node.getSteamMode().getDisplayName();
            int textW = font.width(steamText);
            int tierBtnW = Math.max(48, textW + 8);
            int steamColor = !isOperational ? 0xFFFF8888 : node.getSteamMode().getColor();
            buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, tierBtnW, steamText, textW, steamColor, !isOperational, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.TIER, null));
            nextRelX += tierBtnW + 4;
        } else {
            GTVoltageTier tier = node.getTargetTier();
            if (node.isLargeTurbine()) {
                tier = GTTurbineHelper.getRotorHolderTier(node);
                if (tier != null && (node.getTargetTier() == null || node.getTargetTier().ordinal() < tier.ordinal())) {
                    node.setTargetTier(tier);
                }
            }
            int tierColor = !isOperational ? 0xFFFF8888 : (tier != null ? tier.getColor() : 0xFFFFFFFF);
            String tierName = (tier != null ? tier.getName() : "LV");
            if (node.isMultiblock()) {
                tierName = "▦ " + tierName;
            }
            int textW = font.width(tierName);
            int btnW = Math.max(32, textW + 8);
            buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, btnW, tierName, textW, tierColor, !isOperational, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.TIER, null));
            nextRelX += btnW + 4;
        }

        List<NodeBadge> badges = NodeBadgeRegistry.getBadgesForNode(node);
        for (NodeBadge badge : badges) {
            int textW = font.width(badge.text());
            int badgeW = textW + 8;
            if (nextRelX + badgeW > cardW - 46) break;
            int badgeColor = (!isOperational && !badge.isWarning()) ? 0xFFFF8888 : badge.outlineColor();
            buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, badgeW, badge.text(), textW, badgeColor, !isOperational || badge.isWarning(), false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.BADGE, badge));
            nextRelX += badgeW + 3;
        }

        if (node.isGenerator()) {
            populateGeneratorButtons(node, cardW, isOperational, nextRelX, font, buttons);
        } else {
            populateConsumerButtons(node, cardW, isOperational, nextRelX, font, buttons);
        }
    }

    private void populateGeneratorButtons(RecipeNode node, int cardW, boolean isOperational, int nextRelX, Font font, List<com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button> buttons) {
        if (!node.isFusion()) {
            String genBadge = Component.translatable("gui.gtcalcboard.gen_badge").getString();
            int genTextW = font.width(genBadge);
            int genW = Math.max(28, genTextW + 4);
            if (nextRelX + genW <= cardW - 46) {
                int genColor = !isOperational ? 0xFFFF8888 : 0xFF55FF88;
                buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, genW, genBadge, genTextW, genColor, !isOperational, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.GEN, null));
                nextRelX += genW + 3;
            }
        }
        if (node.isLargeTurbine()) {
            populateRotorButtons(node, cardW, isOperational, nextRelX, font, buttons);
        } else {
            String dynamoPar = "⚙ " + node.getParallel() + "x";
            if (!node.getAddons().isEmpty()) {
                dynamoPar += " (+" + node.getAddons().size() + ")";
            }
            int parTextW = font.width(dynamoPar);
            int parW = Math.max(46, (cardW - 6) - nextRelX);
            int configColor = !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF;
            buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, parW, dynamoPar, parTextW, configColor, !isOperational, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.CONFIG, null));
        }
    }

    private void populateRotorButtons(RecipeNode node, int cardW, boolean isOperational, int nextRelX, Font font, List<com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button> buttons) {
        int activeEff = node.getRotorEfficiency();
        for (MachineAddon a : node.getAddons()) {
            if (a.getCategory() == MachineAddon.Category.ROTOR) {
                activeEff = (int) Math.round(a.getDurationMultiplier() * 100.0);
                break;
            }
        }
        String rotorText = "⚙ " + activeEff + "%";
        if (!isOperational) {
            rotorText = "⚙ ⚠ " + activeEff + "%";
        } else if (node.getTotalParallel() > 1) {
            rotorText = "⚙ " + node.getTotalParallel() + "x (" + activeEff + "%)";
        } else {
            long nonRotorAddons = node.getAddons().stream().filter(a -> a.getCategory() != MachineAddon.Category.ROTOR).count();
            if (nonRotorAddons > 0) {
                rotorText += " (+" + nonRotorAddons + ")";
            }
        }
        int rotorTextW = font.width(rotorText);
        int rotorW = Math.max(46, (cardW - 6) - nextRelX);
        int rotorColor = !isOperational ? 0xFFFF4444 : 0xFFFFAA00;
        buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, rotorW, rotorText, rotorTextW, rotorColor, !isOperational, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.CONFIG, null));
    }

    private void populateConsumerButtons(RecipeNode node, int cardW, boolean isOperational, int nextRelX, Font font, List<com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button> buttons) {
        if (!node.isFusion() && node.getEnergyType() != EnergyType.HEAT_OR_SELF && (node.isMultiblock() || node.getSteamMode() == null || !node.getSteamMode().isSteam())) {
            String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
            String ocText = Component.translatable(ocKey).getString();
            int ocTextW = font.width(ocText);
            int ocW = Math.max(50, ocTextW + 6);
            int ocColor = !isOperational ? 0xFFFF8888 : (node.getOverclockMode() == OverclockMode.PERFECT ? 0xFF55FF55 : 0xFFAAAAAA);
            if (nextRelX + ocW <= cardW - 46) {
                buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, ocW, ocText, ocTextW, ocColor, !isOperational, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.OC, null));
                nextRelX += ocW + 3;
            }
        }
        String parLabel = GTCombustionHelper.isCombustionEngine(node)
                ? "⚙ 1x"
                : "⚙ " + node.getTotalParallel() + "x";
        if (!node.getAddons().isEmpty()) {
            parLabel += " (+" + node.getAddons().size() + ")";
        }
        int parTextW = font.width(parLabel);
        int parW = Math.max(46, (cardW - 6) - nextRelX);
        int configColor = !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF;
        buttons.add(new com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button(nextRelX, parW, parLabel, parTextW, configColor, !isOperational, false, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.CONFIG, null));
    }

    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (node == null) return false;
        if (handleClickCachedButtons(widget, node, mouseX, mouseY)) {
            return true;
        }
        return fallbackHandler.handleControlClick(widget, node, mouseX, mouseY, button);
    }

    private boolean handleClickCachedButtons(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        if (widget == null || widget.getTextCache() == null) return false;
        int x = (int) node.getPosX();
        int row2Y = (int) node.getPosY() + 20 + 6 + 18;
        for (var btn : widget.getTextCache().getRow2Buttons()) {
            if (tryClickRow2Button(widget, node, btn, x, row2Y, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private boolean tryClickRow2Button(NodeWidget widget, RecipeNode node, com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button btn, int x, int row2Y, double mouseX, double mouseY) {
        int btnX = x + btn.relX();
        if (mouseX < btnX || mouseX > btnX + btn.width() || mouseY < row2Y || mouseY > row2Y + 14) {
            return false;
        }
        if (btn.role() != com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.BADGE || btn.badge() == null) {
            return false;
        }
        return triggerBadgeClick(widget, btn.badge()) || handleSpecialBadgeClick(widget, node, btn.badge());
    }

    private int calculateTierButtonEndX(RecipeNode node, int x) {
        if (isBoiler(node)) {
            GTBoilerTier boilerTier = GTBoilerTier.getBoilerTier(node);
            String boilerText = boilerTier.getDisplayName();
            if (boilerTier.isMultiblock() && node.getBoilerThrottle() < 100) {
                boilerText += " (" + node.getBoilerThrottle() + "%)";
            }
            int tierBtnW = Math.max(54, safeFontWidth(boilerText, 46) + 8);
            return x + 6 + tierBtnW + 4;
        } else if (!node.isMultiblock() && node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            String steamText = node.getSteamMode().getDisplayName();
            int tierBtnW = Math.max(48, safeFontWidth(steamText, 40) + 8);
            return x + 6 + tierBtnW + 4;
        } else {
            GTVoltageTier tier = node.getTargetTier();
            String tierText = (tier != null ? tier.getName() : "LV");
            if (node.isMultiblock()) tierText = "▦ " + tierText;
            int tierBtnW = Math.max(32, safeFontWidth(tierText, 24) + 8);
            return x + 6 + tierBtnW + 4;
        }
    }

    private boolean handleSpecialBadgeClick(NodeWidget widget, RecipeNode node, NodeBadge badge) {
        widget.commitCountEdit();
        if (badge.text().startsWith("♨")) {
            CoilHelper.cycleCoil(node);
            playButtonClickSound();
            refreshWidgetAndFrame(widget, node);
            return true;
        } else if (badge.text().startsWith("✦") || (badge.text().contains("T") && badge.text().contains("⚠"))) {
            ReflectorHelper.cycleReflector(node);
            playButtonClickSound();
            refreshWidgetAndFrame(widget, node);
            return true;
        }
        return false;
    }

    private void playButtonClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
        );
    }

    private void refreshWidgetAndFrame(NodeWidget widget, RecipeNode node) {
        widget.invalidateCache();
        if (widget.getParent() != null) {
            var frame = widget.getParent().getGraph().findFrameEnclosingNode(node);
            if (frame != null && frame.isSharedMachineFrame()) {
                frame.syncHardwareConfig(node, widget.getParent().getGraph());
                widget.getParent().rebuildWidgets();
            }
            widget.getParent().markSummaryDirty();
        }
    }

    private boolean triggerBadgeClick(NodeWidget widget, NodeBadge badge) {
        if (badge.onClick() == null) return false;
        widget.commitCountEdit();
        badge.onClick().run();
        playButtonClickSound();
        widget.invalidateCache();
        notifyWidgetParentUpdated(widget);
        return true;
    }

    private void notifyWidgetParentUpdated(NodeWidget widget) {
        if (widget.getParent() == null) return;
        if (widget.getNode() != null && widget.getNode().isBaseNode() && widget.getParent().getGraph() != null) {
            widget.getParent().getGraph().setBaseNode(widget.getNode());
        }
        widget.getParent().rebuildWidgets();
        widget.getParent().markSummaryDirty();
    }

    public boolean isTierOrSpeedControlHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        if (node == null) return false;
        if (hasCachedButtons(widget)) {
            return isCachedButtonHovered(widget, node, mouseX, mouseY,
                    btn -> btn.role() == com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.TIER
                            || btn.role() == com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.BANNER);
        }
        return fallbackHandler.isTierOrSpeedControlHovered(widget, node, mouseX, mouseY);
    }

    public boolean isSecondaryControlHovered(RecipeNode node, double mouseX, double mouseY) {
        return isSecondaryControlHovered(null, node, mouseX, mouseY);
    }

    public boolean isSecondaryControlHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        if (node == null || node.isGenerator() || node.isFusion() || node.getEnergyType() == EnergyType.HEAT_OR_SELF || node.getEnergyType() == EnergyType.NONE || (!node.isMultiblock() && node.getSteamMode() != null && node.getSteamMode().isSteam())) return false;
        if (hasCachedButtons(widget)) {
            return isCachedButtonHovered(widget, node, mouseX, mouseY,
                    btn -> btn.role() == com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.OC);
        }
        return fallbackHandler.isSecondaryControlHovered(widget, node, mouseX, mouseY);
    }

    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        return isMachineConfigHovered(null, node, mouseX, mouseY);
    }

    public boolean isMachineConfigHovered(NodeWidget widget, RecipeNode node, double mouseX, double mouseY) {
        if (node == null || node.getEnergyType() == EnergyType.NONE) return false;
        if (hasCachedButtons(widget)) {
            return isCachedButtonHovered(widget, node, mouseX, mouseY,
                    btn -> btn.role() == com.gtceu.calcboard.client.gui.render.NodeCardTextCache.Row2Button.ButtonRole.CONFIG);
        }
        return fallbackHandler.isMachineConfigHovered(widget, node, mouseX, mouseY);
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

    protected static int safeFontWidth(String text, int defaultWidth) {
        try {
            if (Minecraft.getInstance() != null && Minecraft.getInstance().font != null) {
                return Minecraft.getInstance().font.width(text);
            }
        } catch (Throwable ignored) {
        }
        return defaultWidth;
    }
}
