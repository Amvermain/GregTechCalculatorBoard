package com.gtceu.calcboard.client.gui.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.compat.GenericModGuiHandler;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.util.BoardScissorHelper;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GTCEu implementation of {@link com.gtceu.calcboard.client.gui.compat.IModGuiHandler}.
 */
@OnlyIn(Dist.CLIENT)
public class GTCEuModGuiHandler extends GenericModGuiHandler {

    private static int mbControllerScroll = 0;
    private double headerRow1ScrollX = 0;
    private double headerRow2ScrollX = 0;
    private double maxHeaderRow1ScrollX = 0;
    private double maxHeaderRow2ScrollX = 0;

    private boolean isDraggingHeader = false;
    private int draggingRow = 0;
    private double dragStartX = 0;
    private double dragStartScrollX = 0;
    private boolean hasDraggedHeader = false;

    public static void resetControllerScroll() {
        mbControllerScroll = 0;
    }

    private static boolean isBoiler(RecipeNode node) {
        if (node == null) return false;
        if (node.isLiquidBoilerRecipe()) return true;
        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        return adapter != null && adapter.isBoilerRecipe(node);
    }

    private static boolean isFusionMachine(RecipeNode node) {
        if (node == null) return false;
        if (node.isFusion()) return true;
        if (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("fusion")) return true;
        if (node.getRecipeCategoryId() != null && node.getRecipeCategoryId().getPath().contains("fusion")) return true;
        return false;
    }

    private static boolean isCoilMultiblock(RecipeNode node) {
        if (node == null || !node.isMultiblock()) return false;
        if (isFusionMachine(node)) return false;
        if (node.getMachineIcon() != null) {
            return MultiblockDetector.isCoilMultiblock(node.getMachineIcon())
                    || com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper.getCoilMachineSpec(node.getMachineIcon()).kind() != com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper.CoilMachineKind.GENERIC;
        }
        if (node.getMultiblockWorkstation() != null) {
            return MultiblockDetector.isCoilMultiblock(node.getMultiblockWorkstation())
                    || com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper.getCoilMachineSpec(node.getMultiblockWorkstation()).kind() != com.gtceu.calcboard.compat.gtceu.helper.GTCEuCoilModifierHelper.CoilMachineKind.GENERIC;
        }
        if (MultiblockDetector.isCoilRecipeCategory(node.getRecipeCategoryId())) return true;
        if (node.getRecipeTemperature() > 0) return true;
        if (node.getProperties().get(GTCEuProperties.EBF_TEMPERATURE) > 0) return true;
        return false;
    }

    @Override
    public boolean handleControlClick(com.gtceu.calcboard.client.gui.widget.NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        if (node == null) return false;

        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int cardW = node.getCardWidth();
        int nextCtrlX = x + 42;

        if (isBoiler(node)) {
            com.gtceu.calcboard.api.type.GTBoilerTier boilerTier = com.gtceu.calcboard.api.type.GTBoilerTier.getBoilerTier(node);
            String boilerText = boilerTier.getDisplayName();
            if (boilerTier.isMultiblock() && node.getBoilerThrottle() < 100) {
                boilerText += " (" + node.getBoilerThrottle() + "%)";
            }
            int tierBtnW = Math.max(54, safeFontWidth(boilerText, 46) + 8);
            nextCtrlX = x + 6 + tierBtnW + 4;
        } else if (!node.isMultiblock() && node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            String steamText = node.getSteamMode().getDisplayName();
            int tierBtnW = Math.max(48, safeFontWidth(steamText, 40) + 8);
            nextCtrlX = x + 6 + tierBtnW + 4;
        } else {
            GTVoltageTier tier = node.getTargetTier();
            String tierText = (tier != null ? tier.getName() : "LV");
            if (node.isMultiblock()) tierText = "🏛 " + tierText;
            int tierBtnW = Math.max(32, safeFontWidth(tierText, 24) + 8);
            nextCtrlX = x + 6 + tierBtnW + 4;
        }

        List<com.gtceu.calcboard.api.property.NodeBadge> badges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(node);
        for (com.gtceu.calcboard.api.property.NodeBadge badge : badges) {
            int badgeW = safeFontWidth(badge.text(), 30) + 8;
            if (nextCtrlX + badgeW > x + cardW - 46) {
                break;
            }
            if (mouseX >= nextCtrlX && mouseX <= nextCtrlX + badgeW && mouseY >= row2Y && mouseY <= row2Y + 14) {
                widget.commitCountEdit();
                if (badge.text().startsWith("♨")) {
                    com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.cycleCoil(node);
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
                    );
                    widget.invalidateCache();
                    if (widget.getParent() != null) {
                        var frame = widget.getParent().getGraph().findFrameEnclosingNode(node);
                        if (frame != null && frame.isSharedMachineFrame()) {
                            frame.syncHardwareConfig(node, widget.getParent().getGraph());
                            widget.getParent().rebuildWidgets();
                        }
                        widget.getParent().markSummaryDirty();
                    }
                    return true;
                } else if (badge.text().startsWith("✦") || (badge.text().contains("T") && badge.text().contains("⚠"))) {
                    com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper.cycleReflector(node);
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F)
                    );
                    widget.invalidateCache();
                    if (widget.getParent() != null) {
                        var frame = widget.getParent().getGraph().findFrameEnclosingNode(node);
                        if (frame != null && frame.isSharedMachineFrame()) {
                            frame.syncHardwareConfig(node, widget.getParent().getGraph());
                            widget.getParent().rebuildWidgets();
                        }
                        widget.getParent().markSummaryDirty();
                    }
                    return true;
                }
            }
            nextCtrlX += badgeW + 3;
        }

        return super.handleControlClick(widget, node, mouseX, mouseY, button);
    }

    @Override
    public boolean isSecondaryControlHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node == null || node.isGenerator() || node.isFusion() || node.getEnergyType() == com.gtceu.calcboard.api.type.EnergyType.HEAT_OR_SELF || node.getEnergyType() == com.gtceu.calcboard.api.type.EnergyType.NONE || (!node.isMultiblock() && node.getSteamMode() != null && node.getSteamMode().isSteam())) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int cardW = node.getCardWidth();
        int nextCtrlX = x + 42;

        if (isBoiler(node)) {
            com.gtceu.calcboard.api.type.GTBoilerTier boilerTier = com.gtceu.calcboard.api.type.GTBoilerTier.getBoilerTier(node);
            String boilerText = boilerTier.getDisplayName();
            if (boilerTier.isMultiblock() && node.getBoilerThrottle() < 100) {
                boilerText += " (" + node.getBoilerThrottle() + "%)";
            }
            int tierBtnW = Math.max(54, safeFontWidth(boilerText, 46) + 8);
            nextCtrlX = x + 6 + tierBtnW + 4;
        }

        List<com.gtceu.calcboard.api.property.NodeBadge> badges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(node);
        for (com.gtceu.calcboard.api.property.NodeBadge badge : badges) {
            int badgeW = safeFontWidth(badge.text(), 30) + 8;
            if (nextCtrlX + badgeW > x + cardW - 46) {
                break;
            }
            nextCtrlX += badgeW + 3;
        }

        String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
        String ocText = Component.translatable(ocKey).getString();
        int ocW = Math.max(50, safeFontWidth(ocText, 44) + 6);
        if (nextCtrlX + ocW > x + cardW - 46) return false;

        return mouseX >= nextCtrlX && mouseX <= nextCtrlX + ocW && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        if (node == null || node.getEnergyType() == com.gtceu.calcboard.api.type.EnergyType.NONE) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + 20 + 6;
        int row2Y = ctrlY + 18;
        int cardW = node.getCardWidth();
        int nextCtrlX = x + 42;

        if (isBoiler(node)) {
            com.gtceu.calcboard.api.type.GTBoilerTier boilerTier = com.gtceu.calcboard.api.type.GTBoilerTier.getBoilerTier(node);
            String boilerText = boilerTier.getDisplayName();
            if (boilerTier.isMultiblock() && node.getBoilerThrottle() < 100) {
                boilerText += " (" + node.getBoilerThrottle() + "%)";
            }
            int tierBtnW = Math.max(54, safeFontWidth(boilerText, 46) + 8);
            nextCtrlX = x + 6 + tierBtnW + 4;
        } else if (!node.isMultiblock() && node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            String steamText = node.getSteamMode().getDisplayName();
            int tierBtnW = Math.max(48, safeFontWidth(steamText, 40) + 8);
            nextCtrlX = x + 6 + tierBtnW + 4;
        } else {
            GTVoltageTier tier = node.getTargetTier();
            String tierText = (tier != null ? tier.getName() : "LV");
            if (node.isMultiblock()) tierText = "🏛 " + tierText;
            int tierBtnW = Math.max(32, safeFontWidth(tierText, 24) + 8);
            nextCtrlX = x + 6 + tierBtnW + 4;
        }

        List<com.gtceu.calcboard.api.property.NodeBadge> badges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(node);
        for (com.gtceu.calcboard.api.property.NodeBadge badge : badges) {
            int badgeW = safeFontWidth(badge.text(), 30) + 8;
            if (nextCtrlX + badgeW > x + cardW - 46) {
                break;
            }
            nextCtrlX += badgeW + 3;
        }

        if (node.isGenerator()) {
            if (!node.isFusion()) {
                String genBadge = Component.translatable("gui.gtcalcboard.gen_badge").getString();
                int genW = Math.max(28, safeFontWidth(genBadge, 24) + 4);
                if (nextCtrlX + genW <= x + cardW - 46) {
                    nextCtrlX += genW + 3;
                }
            }
        } else {
            if (!node.isFusion() && node.getEnergyType() != com.gtceu.calcboard.api.type.EnergyType.HEAT_OR_SELF && (node.isMultiblock() || node.getSteamMode() == null || !node.getSteamMode().isSteam())) {
                String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
                String ocText = Component.translatable(ocKey).getString();
                int ocW = Math.max(50, safeFontWidth(ocText, 44) + 6);
                if (nextCtrlX + ocW <= x + cardW - 46) {
                    nextCtrlX += ocW + 3;
                }
            }
        }

        return mouseX >= nextCtrlX && mouseX <= x + cardW - 6 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    @Override
    public String getModId() {
        return "gtceu";
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font,
                                  RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                  boolean isGlowing) {
        boolean isOperational = node.isOperational();
        int tierBtnW = 32;
        int nextCtrlX = x + 42;

        if (node.getEnergyType() == com.gtceu.calcboard.api.type.EnergyType.NONE) {
            String bannerText = "🍃 " + Component.translatable("gui.gtcalcboard.energy_passive_banner").getString();
            int bannerW = cardW - 12;
            NodeCardRenderer.drawBtn(graphics, font, bannerText, x + 6, row2Y, bannerW, 14, mouseX, mouseY, 0xFF88D49E, false, false);
            return;
        } else if (isBoiler(node)) {
            com.gtceu.calcboard.api.type.GTBoilerTier boilerTier = com.gtceu.calcboard.api.type.GTBoilerTier.getBoilerTier(node);
            String boilerText = boilerTier.getDisplayName();
            if (boilerTier.isMultiblock() && node.getBoilerThrottle() < 100) {
                boilerText += " (" + node.getBoilerThrottle() + "%)";
            }
            tierBtnW = Math.max(54, font.width(boilerText) + 8);
            int boilerColor = !isOperational ? 0xFFFF8888 : boilerTier.getColor();
            NodeCardRenderer.drawBtn(graphics, font, boilerText, x + 6, row2Y, tierBtnW, 14, mouseX, mouseY, boilerColor, !isOperational, false);
            nextCtrlX = x + 6 + tierBtnW + 4;
        } else if (!node.isMultiblock() && node.getSteamMode() != null && node.getSteamMode().isSteam()) {
            String steamText = node.getSteamMode().getDisplayName();
            tierBtnW = Math.max(48, font.width(steamText) + 8);
            int steamColor = !isOperational ? 0xFFFF8888 : node.getSteamMode().getColor();
            NodeCardRenderer.drawBtn(graphics, font, steamText, x + 6, row2Y, tierBtnW, 14, mouseX, mouseY, steamColor, !isOperational, false);
            nextCtrlX = x + 6 + tierBtnW + 4;
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
                tierName = "🏛 " + tierName;
            }
            int btnW = Math.max(32, font.width(tierName) + 8);
            NodeCardRenderer.drawBtn(graphics, font, tierName, x + 6, row2Y, btnW, 14, mouseX, mouseY, tierColor, !isOperational, false);
            nextCtrlX = x + 6 + btnW + 4;
        }

        // 1. Render declarative node badges (Fusion, Cleanroom, etc.) via NodeBadgeRegistry
        List<com.gtceu.calcboard.api.property.NodeBadge> badges = com.gtceu.calcboard.api.property.NodeBadgeRegistry.getBadgesForNode(node);
        for (com.gtceu.calcboard.api.property.NodeBadge badge : badges) {
            int badgeW = font.width(badge.text()) + 8;
            if (nextCtrlX + badgeW > x + cardW - 46) {
                break;
            }
            int badgeColor = (!isOperational && !badge.isWarning()) ? 0xFFFF8888 : badge.outlineColor();
            NodeCardRenderer.drawBtn(graphics, font, badge.text(), nextCtrlX, row2Y, badgeW, 14, mouseX, mouseY, badgeColor, !isOperational || badge.isWarning(), false);
            nextCtrlX += badgeW + 3;
        }

        // 2. Render standard GT Generator / Overclock / Rotor controls if room permits
        if (node.isGenerator()) {
            if (!node.isFusion()) {
                String genBadge = Component.translatable("gui.gtcalcboard.gen_badge").getString();
                int genW = Math.max(28, font.width(genBadge) + 4);
                if (nextCtrlX + genW <= x + cardW - 46) {
                    int genColor = !isOperational ? 0xFFFF8888 : 0xFF55FF88;
                    NodeCardRenderer.drawBtn(graphics, font, genBadge, nextCtrlX, row2Y, genW, 14, mouseX, mouseY, genColor, !isOperational, false);
                    nextCtrlX += genW + 3;
                }
            }

            if (node.isLargeTurbine()) {
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
                int rotorW = Math.max(46, (x + cardW - 6) - nextCtrlX);
                int rotorColor = !isOperational ? 0xFFFF4444 : 0xFFFFAA00;
                NodeCardRenderer.drawBtn(graphics, font, rotorText, nextCtrlX, row2Y, rotorW, 14, mouseX, mouseY, rotorColor, !isOperational, isGlowing);
            } else {
                String dynamoPar = "⚙ " + node.getParallel() + "x";
                if (!node.getAddons().isEmpty()) {
                    dynamoPar += " (+" + node.getAddons().size() + ")";
                }
                int parW = Math.max(46, (x + cardW - 6) - nextCtrlX);
                int configColor = !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF;
                NodeCardRenderer.drawBtn(graphics, font, dynamoPar, nextCtrlX, row2Y, parW, 14, mouseX, mouseY, configColor, !isOperational, isGlowing);
            }
        } else {
            if (!node.isFusion() && node.getEnergyType() != com.gtceu.calcboard.api.type.EnergyType.HEAT_OR_SELF && (node.isMultiblock() || node.getSteamMode() == null || !node.getSteamMode().isSteam())) {
                String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
                String ocText = Component.translatable(ocKey).getString();
                int ocColor = !isOperational ? 0xFFFF8888 : (node.getOverclockMode() == OverclockMode.PERFECT ? 0xFF55FF55 : 0xFFAAAAAA);
                int ocW = Math.max(50, font.width(ocText) + 6);
                if (nextCtrlX + ocW <= x + cardW - 46) {
                    NodeCardRenderer.drawBtn(graphics, font, ocText, nextCtrlX, row2Y, ocW, 14, mouseX, mouseY, ocColor, !isOperational, false);
                    nextCtrlX += ocW + 3;
                }
            }

            String parLabel = "⚙ " + node.getTotalParallel() + "x";
            if (!node.getAddons().isEmpty()) {
                parLabel += " (+" + node.getAddons().size() + ")";
            }
            int parW = Math.max(46, (x + cardW - 6) - nextCtrlX);
            int configColor = !node.getAddons().isEmpty() ? 0xFF55FFFF : 0xFF58D3FF;
            NodeCardRenderer.drawBtn(graphics, font, parLabel, nextCtrlX, row2Y, parW, 14, mouseX, mouseY, configColor, !isOperational, isGlowing);
        }
    }

    private static void showTooltip(MachineConfigDialog dialog, GuiGraphics graphics, Font font, List<Component> tooltip, int mouseX, int mouseY) {
        if (dialog != null) {
            dialog.setDeferredTooltip(tooltip);
        } else {
            com.gtceu.calcboard.client.gui.render.BoardTooltipRenderer.renderComponentTooltip(graphics, font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public void renderDialogHeader(GuiGraphics graphics, Font font, RecipeNode node,
                                   int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                   EditBox parallelBox, BoardScreen parent) {
        renderDialogHeader(null, graphics, font, node, x, y, dialogW, mouseX, mouseY, partialTicks, parallelBox, parent);
    }

    @Override
    public void renderDialogHeader(MachineConfigDialog dialog, GuiGraphics graphics, Font font, RecipeNode node,
                                   int x, int y, int dialogW, int mouseX, int mouseY, float partialTicks,
                                   EditBox parallelBox, BoardScreen parent) {
        if (MachineAddon.isTurbineMachine(node) && node.isMultiblock()) {
            String rName = node.getRotorName();
            if (rName == null || rName.isEmpty() || rName.startsWith("Standard")) {
                rName = Component.translatable("gui.gtcalcboard.rotor.standard").getString();
            }
            int eff = node.getRotorEfficiency();
            int pwr = node.getRotorPower();
            int holderBonus = GTTurbineHelper.getTurbineHolderEfficiencyBonus(node);
            int totalEff = GTTurbineHelper.getTotalTurbineEfficiency(node);

            GTVoltageTier holderTier = GTTurbineHelper.getRotorHolderTier(node);
            GTVoltageTier dynamoTier = GTTurbineHelper.getDynamoTier(node);
            int dynamoAmps = GTTurbineHelper.getDynamoAmperage(node);

            // Row 1: Rotor Info & Lifespan & Pmax button & Reset button
            int pmax = GTPowerCalculator.getMaxParallelCapacity(node);
            boolean isDynamoBottleneck = GTTurbineHelper.isDynamoBottleneck(node);
            String pmaxText = isDynamoBottleneck ? ("⚡ Pmax: " + pmax + " ⚠") : ("⚡ Pmax: " + pmax);
            int pmaxBtnW = Math.max(70, font.width(pmaxText) + 8);
            int pmaxBtnX = x + dialogW - 10 - pmaxBtnW;
            boolean pmaxHover = mouseX >= pmaxBtnX && mouseX <= pmaxBtnX + pmaxBtnW && mouseY >= y + 28 && mouseY <= y + 42;

            int pmaxBg = isDynamoBottleneck ? (pmaxHover ? 0xFF5A3C1A : 0xFF3D2A14) : (pmaxHover ? 0xFF2A5288 : 0xFF1C304A);
            int pmaxBorder = isDynamoBottleneck ? (pmaxHover ? 0xFFFFCC00 : 0xFFFFAA00) : (pmaxHover ? 0xFF58D3FF : 0xFF35587A);
            int pmaxTextColor = isDynamoBottleneck ? (pmaxHover ? 0xFFFFF0A0 : 0xFFFFD080) : (pmaxHover ? 0xFF58D3FF : 0xFFB0D0FF);

            graphics.fill(pmaxBtnX, y + 28, pmaxBtnX + pmaxBtnW, y + 42, pmaxBg);
            graphics.renderOutline(pmaxBtnX, y + 28, pmaxBtnW, 14, pmaxBorder);
            graphics.drawCenteredString(font, pmaxText, pmaxBtnX + pmaxBtnW / 2, y + 31, pmaxTextColor);

            int resetBtnW = Math.max(48, font.width("↺ " + Component.translatable("gui.gtcalcboard.rotor.reset_btn").getString()) + 8);
            int resetBtnX = pmaxBtnX - 4 - resetBtnW;
            boolean resetHover = mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= y + 28 && mouseY <= y + 42;
            graphics.fill(resetBtnX, y + 28, resetBtnX + resetBtnW, y + 42, resetHover ? 0xFF3E485A : 0xFF242A35);
            graphics.renderOutline(resetBtnX, y + 28, resetBtnW, 14, resetHover ? 0xFF58D3FF : 0xFF4A556B);
            graphics.drawCenteredString(font, "↺ " + Component.translatable("gui.gtcalcboard.rotor.reset_btn").getString(), resetBtnX + resetBtnW / 2, y + 31, 0xFFFFFFFF);

            int maxRotorInfoW = resetBtnX - (x + 10) - 6;
            String rotorInfo = "§6🌀 §f" + rName + " §7| §b⏱ " + eff + "% §e⚡ " + pwr + "%";
            if (holderBonus > 0) {
                rotorInfo += " §a(+" + holderBonus + "% -> " + totalEff + "%)";
            }
            if (GTTurbineHelper.hasRotorAddon(node)) {
                double lifespanHours = GTTurbineHelper.calculateRotorLifespanHours(node);
                if (!Double.isInfinite(lifespanHours) && lifespanHours > 0) {
                    rotorInfo += String.format(Locale.ROOT, " §7| §6⌛ %.1fh", lifespanHours);
                }
            }
            if (font.width(rotorInfo) > maxRotorInfoW) {
                String shortRName = rName.replace("Turbine Rotor", "Rotor");
                rotorInfo = "§6🌀 §f" + shortRName + " §7| §b⏱" + eff + "% §e⚡" + pwr + "%";
                if (holderBonus > 0) {
                    rotorInfo += " §a(+" + holderBonus + "%→" + totalEff + "%)";
                }
                if (GTTurbineHelper.hasRotorAddon(node)) {
                    double lifespanHours = GTTurbineHelper.calculateRotorLifespanHours(node);
                    if (!Double.isInfinite(lifespanHours) && lifespanHours > 0) {
                        rotorInfo += String.format(Locale.ROOT, " §7| §6⌛%.1fh", lifespanHours);
                    }
                }
                if (font.width(rotorInfo) > maxRotorInfoW) {
                    rotorInfo = font.plainSubstrByWidth(rotorInfo, Math.max(16, maxRotorInfoW - font.width("..."))) + "...";
                }
            }
            boolean rotorInfoHover = mouseX >= x + 10 && mouseX <= resetBtnX - 4 && mouseY >= y + 28 && mouseY <= y + 42;
            graphics.drawString(font, rotorInfo, x + 10, y + 31, 0xFFFFFFFF, false);

            // Row 2: Plasma model selector OR Decoupled Holder / Dynamo Tier / Dynamo Amps / Boost controls
            int btnY = y + 46;
            boolean dynamoHover = false;
            boolean ampsHover = false;
            boolean boostHover = false;
            int curX = x + 10;
            int gap = 4;

            int holderBtnW = 100;
            boolean holderHover = mouseX >= curX && mouseX <= curX + holderBtnW && mouseY >= btnY && mouseY <= btnY + 16;
            graphics.fill(curX, btnY, curX + holderBtnW, btnY + 16, holderHover ? 0xFF2A3548 : 0xFF1E2430);
            graphics.renderOutline(curX, btnY, holderBtnW, 16, holderHover ? 0xFF58D3FF : 0xFF3D4B60);
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.turbine_holder_tier", holderTier.getFormatCode() + holderTier.getName()).getString(), curX + holderBtnW / 2, btnY + 4, 0xFFFFFFFF);
            curX += holderBtnW + gap;

            // Dynamo Voltage Tier Button (Scroll / Left-Click: +1, Right-Click: -1)
            int dynamoTierBtnW = 100;
            dynamoHover = mouseX >= curX && mouseX <= curX + dynamoTierBtnW && mouseY >= btnY && mouseY <= btnY + 16;
            int dynamoBorder = isDynamoBottleneck ? (dynamoHover ? 0xFFFFCC00 : 0xFFFFAA00) : (dynamoHover ? 0xFF58D3FF : 0xFF3D4B60);
            graphics.fill(curX, btnY, curX + dynamoTierBtnW, btnY + 16, dynamoHover ? 0xFF2A3548 : 0xFF1E2430);
            graphics.renderOutline(curX, btnY, dynamoTierBtnW, 16, dynamoBorder);
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.turbine_dynamo_tier", dynamoTier.getFormatCode() + dynamoTier.getName()).getString(), curX + dynamoTierBtnW / 2, btnY + 4, 0xFFFFFFFF);
            curX += dynamoTierBtnW + gap;

            // Dynamo Amperage Button (Scroll / Left-Click: +1, Right-Click: -1)
            int dynamoAmpsBtnW = 70;
            ampsHover = mouseX >= curX && mouseX <= curX + dynamoAmpsBtnW && mouseY >= btnY && mouseY <= btnY + 16;
            int ampsBorder = isDynamoBottleneck ? (ampsHover ? 0xFFFFCC00 : 0xFFFFAA00) : (ampsHover ? 0xFF58D3FF : 0xFF3D4B60);
            graphics.fill(curX, btnY, curX + dynamoAmpsBtnW, btnY + 16, ampsHover ? 0xFF2A3548 : 0xFF1E2430);
            graphics.renderOutline(curX, btnY, dynamoAmpsBtnW, 16, ampsBorder);
            graphics.drawCenteredString(font, Component.translatable("gui.gtcalcboard.config.turbine_dynamo_amps", dynamoAmps).getString(), curX + dynamoAmpsBtnW / 2, btnY + 4, 0xFFFFE066);
            curX += dynamoAmpsBtnW + gap;

            // Boost Multiplier Button (via IModAdapter SPI)
            com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
            boolean supportsBoost = adapter != null && adapter.supportsBoosterControl(node);
            if (supportsBoost) {
                Component boostComp = adapter.getBoosterDisplayComponent(node);
                String boostText = boostComp != null ? boostComp.getString() : "";

                int boostBtnW = Math.max(105, font.width(boostText) + 8);
                boostHover = mouseX >= curX && mouseX <= curX + boostBtnW && mouseY >= btnY && mouseY <= btnY + 16;
                int boostBg = adapter.getBoosterBackgroundColor(node, boostHover);
                int boostBorder = adapter.getBoosterBorderColor(node, boostHover);
                int boostTextColor = adapter.getBoosterTextColor(node, boostHover);
                graphics.fill(curX, btnY, curX + boostBtnW, btnY + 16, boostBg);
                graphics.renderOutline(curX, btnY, boostBtnW, 16, boostBorder);
                graphics.drawCenteredString(font, boostText, curX + boostBtnW / 2, btnY + 4, boostTextColor);
            }

            if (pmaxHover) {
                List<Component> tt = new ArrayList<>();
                tt.add(Component.literal("§6⚡ " + Component.translatable("gui.gtcalcboard.config.pmax_title").getString() + ": §f" + pmax + "x"));
                if (isDynamoBottleneck) {
                    double holderCap = GTTurbineHelper.getRotorHolderCapacity(node);
                    double dynamoCap = GTTurbineHelper.getDynamoMaxCapacity(node);
                    int holderPmax = GTTurbineHelper.getRotorHolderMaxParallel(node);
                    tt.add(Component.literal("§c" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dynamo_limited").getString()));
                    tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.pmax_holder_cap").getString(), com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(holderCap), holderPmax)));
                    tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dynamo_cap").getString(), com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(dynamoCap), pmax)));
                    tt.add(Component.literal("§a" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dynamo_tip").getString()));
                } else {
                    double cap = GTTurbineHelper.getGeneratorMaxEUt(node);
                    tt.add(Component.literal("§a" + Component.translatable("gui.gtcalcboard.tooltip.pmax_fully_utilized").getString()));
                    tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.pmax_max_cap").getString(), com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(cap))));
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            } else if ((dynamoHover || ampsHover) && isDynamoBottleneck) {
                List<Component> tt = new ArrayList<>();
                double holderCap = GTTurbineHelper.getRotorHolderCapacity(node);
                double dynamoCap = GTTurbineHelper.getDynamoMaxCapacity(node);
                tt.add(Component.literal(String.format(Locale.ROOT, "§c" + Component.translatable("gui.gtcalcboard.tooltip.pmax_dynamo_bottleneck_hatch").getString(), com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(dynamoCap), com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(holderCap))));
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            } else if (rotorInfoHover) {
                List<Component> tt = new ArrayList<>();
                tt.add(Component.literal("§6🌀 §f" + rName));
                tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.addon.rotor.efficiency").getString(), eff + "%")));
                tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.addon.rotor.power").getString(), pwr + "%")));
                if (holderBonus > 0) {
                    tt.add(Component.literal("§a+ " + Component.translatable("gui.gtcalcboard.config.holder_bonus_tooltip", holderBonus + "%", totalEff + "%").getString()));
                }
                if (GTTurbineHelper.hasRotorAddon(node)) {
                    double wearPerSec = GTTurbineHelper.calculateRotorWearPerSecond(node);
                    double lifespanHours = GTTurbineHelper.calculateRotorLifespanHours(node);
                    double replacementRate = GTTurbineHelper.calculateRotorReplacementRatePerHour(node);
                    tt.add(Component.literal("§8§m------------------------"));
                    tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.rotor_wear_rate").getString() + ": §c-%,.2f dmg/s", wearPerSec)));
                    if (!Double.isInfinite(lifespanHours) && lifespanHours > 0) {
                        tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.rotor_lifespan").getString() + ": §e%,.2f h", lifespanHours)));
                        if (replacementRate > 0) {
                            tt.add(Component.literal(String.format(Locale.ROOT, "§7" + Component.translatable("gui.gtcalcboard.tooltip.rotor_replacement_rate").getString() + ": §6%,.4f /h", replacementRate)));
                        }
                    }
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            } else if (boostHover && supportsBoost && adapter != null) {
                List<Component> tt = new ArrayList<>();
                adapter.buildBoosterTooltip(node, tt);
                if (!tt.isEmpty()) {
                    showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
                }
            }
        } else if (node.isLiquidBoilerRecipe() || (com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node) != null && com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).isBoilerRecipe(node))) {
            graphics.drawString(font, "§6♨ " + Component.translatable("gui.gtcalcboard.boiler_type_title").getString(), x + 10, y + 30, 0xFFFFFFFF, false);
            com.gtceu.calcboard.api.type.GTBoilerTier curTier = com.gtceu.calcboard.api.type.GTBoilerTier.getBoilerTier(node);

            if (curTier.isMultiblock()) {
                int curThrottle = node.getBoilerThrottle();
                int thrX = x + dialogW - 250;
                String thrTitle = "§e⚡ " + Component.translatable("gui.gtcalcboard.boiler_throttle").getString() + ":";
                graphics.drawString(font, thrTitle, thrX, y + 30, 0xFFFFFFFF, false);
                int titleW = font.width(thrTitle);

                int minusX = thrX + titleW + 6;
                boolean minusHover = mouseX >= minusX && mouseX <= minusX + 14 && mouseY >= y + 28 && mouseY <= y + 40;
                graphics.fill(minusX, y + 28, minusX + 14, y + 40, minusHover ? 0xFF3D4558 : 0xFF242A35);
                graphics.renderOutline(minusX, y + 28, 14, 12, minusHover ? 0xFF58D3FF : 0xFF3F4658);
                graphics.drawCenteredString(font, "-", minusX + 7, y + 30, 0xFFFFFFFF);

                int valX = minusX + 16;
                graphics.fill(valX, y + 28, valX + 32, y + 40, 0xFF1B202A);
                graphics.renderOutline(valX, y + 28, 32, 12, 0xFF3F4658);
                graphics.drawCenteredString(font, curThrottle + "%", valX + 16, y + 30, 0xFF58D3FF);

                int plusX = valX + 34;
                boolean plusHover = mouseX >= plusX && mouseX <= plusX + 14 && mouseY >= y + 28 && mouseY <= y + 40;
                graphics.fill(plusX, y + 28, plusX + 14, y + 40, plusHover ? 0xFF3D4558 : 0xFF242A35);
                graphics.renderOutline(plusX, y + 28, 14, 12, plusHover ? 0xFF58D3FF : 0xFF3F4658);
                graphics.drawCenteredString(font, "+", plusX + 7, y + 30, 0xFFFFFFFF);

                int[] presets = {25, 50, 75, 100};
                int curPreX = plusX + 18;
                for (int pre : presets) {
                    int preW = pre == 100 ? 28 : 24;
                    boolean active = curThrottle == pre;
                    boolean preHover = mouseX >= curPreX && mouseX <= curPreX + preW && mouseY >= y + 28 && mouseY <= y + 40;
                    graphics.fill(curPreX, y + 28, curPreX + preW, y + 40, active ? 0xFF2A5288 : (preHover ? 0xFF3D4558 : 0xFF242A35));
                    graphics.renderOutline(curPreX, y + 28, preW, 12, active ? 0xFF589CFF : 0xFF3F4658);
                    graphics.drawCenteredString(font, pre + "%", curPreX + preW / 2, y + 30, active ? 0xFF58D3FF : 0xFFB0B8C8);
                    curPreX += preW + 3;
                }
            }

            com.gtceu.calcboard.api.type.GTBoilerTier[] bTiers = com.gtceu.calcboard.api.type.GTBoilerTier.values();
            boolean isLiquid = node.isLiquidBoilerRecipe();
            int btnW = 70;
            int gap = 4;
            int btnY = y + 44;
            com.gtceu.calcboard.api.type.GTBoilerTier hoveredTier = null;
            for (int i = 0; i < bTiers.length; i++) {
                com.gtceu.calcboard.api.type.GTBoilerTier bt = bTiers[i];
                boolean active = curTier == bt;
                int btnX = x + 10 + i * (btnW + gap);
                boolean hov = mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16;
                if (hov) hoveredTier = bt;
                graphics.fill(btnX, btnY, btnX + btnW, btnY + 16, active ? 0xFF5D3E1A : (hov ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, btnY, btnW, 16, active ? bt.getColor() : 0xFF3F4658);
                String speedLabel = String.format(Locale.ROOT, "%.1fx", bt.getSpeedMultiplier(isLiquid)).replace(".0x", "x");
                String label = (bt.isMultiblock() ? "🏛 " : "♨ ") + (i == 0 ? "LP (" + speedLabel + ")" : (i == 1 ? "HP (" + speedLabel + ")" : (i == 2 ? "L-Brz" : (i == 3 ? "L-Stl" : (i == 4 ? "L-Ti" : "L-W")))));
                graphics.drawCenteredString(font, label, btnX + btnW / 2, btnY + 4, active ? bt.getColor() : 0xFFB0B8C8);
            }
            if (hoveredTier != null) {
                List<Component> tooltip = new ArrayList<>();
                tooltip.add(Component.literal((hoveredTier.isMultiblock() ? "§6🏛 " : "§6♨ ") + hoveredTier.getDisplayName()));
                double thrMult = hoveredTier.isMultiblock() ? (node.getBoilerThrottle() / 100.0) : 1.0;
                double speed = hoveredTier.getSpeedMultiplier(isLiquid) * thrMult;
                double steamRate = hoveredTier.getSteamRatePerSec(isLiquid) * thrMult;
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Fuel Burn Speed: §e%.2fx%s", speed, hoveredTier.isMultiblock() && node.getBoilerThrottle() < 100 ? " §8(" + node.getBoilerThrottle() + "% Throttle)" : "")));
                tooltip.add(Component.literal(String.format(Locale.ROOT, "§7Steam Output: §b%,.0f mB/s §7(%,.0f mB/t)", steamRate, steamRate / 20.0)));
                showTooltip(dialog, graphics, font, tooltip, mouseX, mouseY);
            }
        } else if (!node.isMultiblock()) {
            if (node.supportsSteamMode()) {
                graphics.drawString(font, "§6♨ " + Component.translatable("gui.gtcalcboard.config.steam_mode_title").getString(), x + 10, y + 30, 0xFFFFFFFF, false);
                int btnX = x + 10;
                com.gtceu.calcboard.api.type.SteamMode curSteam = node.getSteamMode();

                boolean lpActive = curSteam == com.gtceu.calcboard.api.type.SteamMode.LOW_PRESSURE;
                boolean lpHover = mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 110, y + 60, lpActive ? 0xFF5D3E1A : (lpHover ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 110, 16, lpActive ? 0xFFD28C38 : 0xFF3F4658);
                graphics.drawCenteredString(font, "♨ LP Steam (0.5x)", btnX + 55, y + 48, lpActive ? 0xFFFFD28C : 0xFFB0B8C8);
                btnX += 116;

                boolean hpActive = curSteam == com.gtceu.calcboard.api.type.SteamMode.HIGH_PRESSURE;
                boolean hpHover = mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 110, y + 60, hpActive ? 0xFF4A4A4A : (hpHover ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 110, 16, hpActive ? 0xFFAAAAAA : 0xFF3F4658);
                graphics.drawCenteredString(font, "♨ HP Steam (1.0x)", btnX + 55, y + 48, hpActive ? 0xFFFFFFFF : 0xFFB0B8C8);
                btnX += 116;

                boolean elecActive = curSteam == com.gtceu.calcboard.api.type.SteamMode.NONE;
                boolean elecHover = mouseX >= btnX && mouseX <= btnX + 90 && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(btnX, y + 44, btnX + 90, y + 60, elecActive ? 0xFF2A5288 : (elecHover ? 0xFF3D4558 : 0xFF282D3B));
                graphics.renderOutline(btnX, y + 44, 90, 16, elecActive ? 0xFF589CFF : 0xFF3F4658);
                graphics.drawCenteredString(font, "⚡ Electric", btnX + 45, y + 48, elecActive ? 0xFF58D3FF : 0xFFB0B8C8);
            } else {
                graphics.drawString(font, "§b" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_fixed").getString(), x + 10, y + 32, 0xFFFFFFFF, false);
                graphics.drawString(font, "§8" + Component.translatable("gui.gtcalcboard.config.singleblock_parallel_desc").getString(), x + 10, y + 48, 0xFF888888, false);
            }
        } else if (isFusionMachine(node)) {
            List<ResourceLocation> mbWorkstations = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
            if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
                mbWorkstations = List.of(node.getMachineIcon());
            }

            int totalCount = mbWorkstations.size();
            int curReflectorTier = node.getInstalledReflectorTier();
            int reqReflectorTier = node.getProperties().get(GTCEuProperties.REQUIRED_REFLECTOR_TIER);
            long reqStartEU = node.getProperties().get(GTCEuProperties.FUSION_START_EU);
            GTVoltageTier minTier = node.getMinFusionVoltageTier();

            String mbHeader = "§b⚛ " + Component.translatable("gui.gtcalcboard.config.multiblock_controller_title").getString() + " & " + Component.translatable("gui.gtcalcboard.config.reflector_tier_title").getString();
            graphics.drawString(font, mbHeader, x + 10, y + 28, 0xFFFFFFFF, false);

            String parSummary = (reqStartEU > 0) ? ("§e⚡ " + com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(reqStartEU) + " EU Start") : ("§7⚡ " + node.getTotalParallel() + "x Par");
            int parSummaryW = font.width(parSummary);
            graphics.drawString(font, parSummary, x + dialogW - 10 - parSummaryW, y + 28, 0xFFFFFFFF, false);

            int controllersAreaW = dialogW - 20;
            List<Integer> ctrlWidths = new ArrayList<>();
            int totalCtrlW = 0;
            for (ResourceLocation ws : mbWorkstations) {
                String label = getMultiblockShortLabel(ws);
                int w = Math.max(68, font.width(label) + 12);
                ctrlWidths.add(w);
                totalCtrlW += w + 3;
            }
            if (!ctrlWidths.isEmpty()) totalCtrlW -= 3;

            this.maxHeaderRow1ScrollX = Math.max(0, totalCtrlW - controllersAreaW);
            this.headerRow1ScrollX = Math.max(0, Math.min(maxHeaderRow1ScrollX, headerRow1ScrollX));

            ResourceLocation hoveredController = null;
            boolean hoveredControllerLocked = false;
            String hoveredControllerReq = "";

            BoardScissorHelper.enableScissor(graphics, x + 10, y + 36, x + 10 + controllersAreaW, y + 51);
            graphics.pose().pushPose();
            graphics.pose().translate((float) -headerRow1ScrollX, 0, 0);

            int curX = x + 10;
            for (int i = 0; i < totalCount; i++) {
                ResourceLocation mbWs = mbWorkstations.get(i);
                int w = ctrlWidths.get(i);
                boolean isSelected = mbWs.equals(node.getMachineIcon());
                double vMouseX = mouseX + headerRow1ScrollX;
                boolean hov = mouseX >= x + 10 && mouseX <= x + 10 + controllersAreaW && vMouseX >= curX && vMouseX <= curX + w && mouseY >= y + 38 && mouseY <= y + 50;

                GTVoltageTier ctrlTier = GTCEuModAdapter.extractVoltageTierFromIcon(mbWs);
                boolean isTierSufficient = (ctrlTier == null || minTier == null || ctrlTier.getVoltage() >= minTier.getVoltage());

                if (hov) {
                    hoveredController = mbWs;
                    hoveredControllerLocked = !isTierSufficient;
                    if (!isTierSufficient) {
                        hoveredControllerReq = Component.translatable("gui.gtcalcboard.config.fusion_mk_req_tooltip", minTier != null ? minTier.getName() : "Mk2", com.gtceu.calcboard.client.gui.util.FormatUtil.formatCompactNumber(reqStartEU)).getString();
                    }
                }

                int fill = !isTierSufficient ? (hov ? 0xFF4A1E24 : 0xFF3A1C22) : (isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B));
                int border = !isTierSufficient ? 0xFFFF5555 : (isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658));

                graphics.fill(curX, y + 38, curX + w, y + 50, fill);
                graphics.renderOutline(curX, y + 38, w, 12, border);

                String label = getMultiblockShortLabel(mbWs);
                if (!isTierSufficient) {
                    label = "🔒 " + label;
                }
                int textCol = !isTierSufficient ? 0xFFFF8888 : (isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8));
                graphics.drawCenteredString(font, font.plainSubstrByWidth(label, w - 4), curX + w / 2, y + 40, textCol);
                curX += w + 3;
            }

            graphics.pose().popPose();
            BoardScissorHelper.disableScissor(graphics);

            if (maxHeaderRow1ScrollX > 0) {
                if (headerRow1ScrollX > 2) {
                    graphics.fill(x + 10, y + 38, x + 18, y + 50, 0xCC181C26);
                    graphics.drawCenteredString(font, "◀", x + 14, y + 40, 0xFF80D0FF);
                }
                if (headerRow1ScrollX < maxHeaderRow1ScrollX - 2) {
                    graphics.fill(x + 10 + controllersAreaW - 8, y + 38, x + 10 + controllersAreaW, y + 50, 0xCC181C26);
                    graphics.drawCenteredString(font, "▶", x + 10 + controllersAreaW - 4, y + 40, 0xFF80D0FF);
                }
            }

            List<Integer> availableReflectorTiers = ReflectorHelper.getAvailableReflectorTiers();
            int reflAreaW = dialogW - 20;
            List<Integer> reflWidths = new ArrayList<>();
            int totalReflW = 0;

            for (int t : availableReflectorTiers) {
                String rLabel = (t == 0) ? Component.translatable("gui.gtcalcboard.reflector.none").getString() : ("✦ T" + t);
                int w = Math.max(48, font.width(rLabel) + 12);
                reflWidths.add(w);
                totalReflW += w + 3;
            }
            if (!reflWidths.isEmpty()) totalReflW -= 3;

            this.maxHeaderRow2ScrollX = Math.max(0, totalReflW - reflAreaW);
            this.headerRow2ScrollX = Math.max(0, Math.min(maxHeaderRow2ScrollX, headerRow2ScrollX));

            int hoveredReflectorTier = -1;
            boolean hoveredReflectorLocked = false;

            BoardScissorHelper.enableScissor(graphics, x + 10, y + 51, x + 10 + reflAreaW, y + 66);
            graphics.pose().pushPose();
            graphics.pose().translate((float) -headerRow2ScrollX, 0, 0);

            int rCurX = x + 10;
            for (int i = 0; i < availableReflectorTiers.size(); i++) {
                int t = availableReflectorTiers.get(i);
                int w = reflWidths.get(i);
                boolean isSelected = (t == curReflectorTier);
                boolean isSufficient = (reqReflectorTier <= 0 || t >= reqReflectorTier);

                double vMouseX = mouseX + headerRow2ScrollX;
                boolean hov = mouseX >= x + 10 && mouseX <= x + 10 + reflAreaW && vMouseX >= rCurX && vMouseX <= rCurX + w && mouseY >= y + 52 && mouseY <= y + 64;

                if (hov) {
                    hoveredReflectorTier = t;
                    hoveredReflectorLocked = !isSufficient;
                }

                int fill = !isSufficient ? (hov ? 0xFF4A1E24 : 0xFF3A1C22) : (isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B));
                int border = !isSufficient ? 0xFFFF5555 : (isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658));

                graphics.fill(rCurX, y + 52, rCurX + w, y + 64, fill);
                graphics.renderOutline(rCurX, y + 52, w, 12, border);

                String rLabel = (t == 0) ? Component.translatable("gui.gtcalcboard.reflector.none").getString() : ("✦ T" + t);
                if (!isSufficient) {
                    rLabel = "🔒 " + rLabel;
                }
                int textCol = !isSufficient ? 0xFFFF8888 : (isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8));
                graphics.drawCenteredString(font, font.plainSubstrByWidth(rLabel, w - 4), rCurX + w / 2, y + 54, textCol);

                rCurX += w + 3;
            }

            graphics.pose().popPose();
            BoardScissorHelper.disableScissor(graphics);

            if (maxHeaderRow2ScrollX > 0) {
                if (headerRow2ScrollX > 2) {
                    graphics.fill(x + 10, y + 52, x + 18, y + 64, 0xCC181C26);
                    graphics.drawCenteredString(font, "◀", x + 14, y + 54, 0xFF80D0FF);
                }
                if (headerRow2ScrollX < maxHeaderRow2ScrollX - 2) {
                    graphics.fill(x + 10 + reflAreaW - 8, y + 52, x + 10 + reflAreaW, y + 64, 0xCC181C26);
                    graphics.drawCenteredString(font, "▶", x + 10 + reflAreaW - 4, y + 54, 0xFF80D0FF);
                }
            }

            if (hoveredController != null) {
                List<Component> tt = new ArrayList<>();
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(hoveredController);
                String fullName = (item != null && item != net.minecraft.world.item.Items.AIR) ? item.getDescription().getString() : hoveredController.getPath();
                tt.add(Component.literal("§e🏛 " + fullName));
                tt.add(Component.literal("§8" + hoveredController));
                if (hoveredControllerLocked) {
                    tt.add(Component.literal("§c❌ " + hoveredControllerReq));
                } else if (hoveredController.equals(node.getMachineIcon())) {
                    tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.config.active_controller").getString()));
                } else {
                    tt.add(Component.literal("§7[Click to select]"));
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            } else if (hoveredReflectorTier >= 0) {
                List<Component> tt = new ArrayList<>();
                if (hoveredReflectorTier == 0) {
                    tt.add(Component.literal("§e✦ " + Component.translatable("gui.gtcalcboard.reflector.none").getString()));
                    tt.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.reflector.none_desc").getString()));
                } else {
                    MachineAddon refAddon = ReflectorHelper.getReflectorForTier(hoveredReflectorTier);
                    String titleName = (refAddon != null) ? refAddon.getName() : Component.translatable("gui.gtcalcboard.addon.reflector_tier_name", hoveredReflectorTier).getString();
                    tt.add(Component.literal("§b✦ " + titleName));
                    if (refAddon != null && refAddon.getDescription() != null) {
                        for (String line : refAddon.getDescription().split("\n")) {
                            tt.add(Component.literal("§7" + line));
                        }
                    } else {
                        tt.add(Component.literal("§7" + Component.translatable("gui.gtcalcboard.addon.reflector_desc", hoveredReflectorTier).getString()));
                    }
                }
                if (hoveredReflectorLocked) {
                    tt.add(Component.literal("§c❌ " + Component.translatable("gui.gtcalcboard.config.reflector_req_tooltip", reqReflectorTier).getString()));
                } else if (hoveredReflectorTier == curReflectorTier) {
                    tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.reflector_met").getString()));
                } else {
                    tt.add(Component.literal("§7[Click to equip]"));
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            }
        } else if (isCoilMultiblock(node)) {
            List<ResourceLocation> mbWorkstations = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
            if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
                mbWorkstations = List.of(node.getMachineIcon());
            }

            int defPar = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getDefaultParallel(node);
            int totalCount = mbWorkstations.size();
            boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
            int parBtnW = supportsParHatch ? 120 : 0;
            int parBtnX = x + dialogW - 10 - parBtnW;
            int controllersAreaW = supportsParHatch ? (parBtnX - (x + 10) - 6) : (dialogW - 20);

            MachineAddon equippedParallel = null;
            for (MachineAddon a : node.getAddons()) {
                if (a != null && a.getCategory() == MachineAddon.Category.PARALLEL) {
                    equippedParallel = a;
                    break;
                }
            }

            int curCoilTemp = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getInstalledCoilTemperature(node);
            int reqCoilTemp = node.getProperties().get(GTCEuProperties.EBF_TEMPERATURE);
            if (reqCoilTemp <= 0) reqCoilTemp = node.getRecipeTemperature();

            String mbHeader = "§b🏛 " + Component.translatable("gui.gtcalcboard.config.multiblock_controller_title").getString() + " & " + Component.translatable("gui.gtcalcboard.config.coil_tier_title").getString();
            graphics.drawString(font, mbHeader, x + 10, y + 28, 0xFFFFFFFF, false);

            String parSummary = "§7⚡ " + node.getTotalParallel() + "x Par" + (defPar > 1 ? " (Default " + defPar + "x)" : (node.getTotalParallel() > 1 ? " (Base " + node.getParallel() + "x)" : " (Default 1x)"));
            int parSummaryW = font.width(parSummary);
            graphics.drawString(font, parSummary, x + dialogW - 10 - parSummaryW, y + 28, 0xFFFFFFFF, false);

            List<Integer> ctrlWidths = new ArrayList<>();
            int totalCtrlW = 0;
            for (ResourceLocation ws : mbWorkstations) {
                String label = getMultiblockShortLabel(ws);
                int w = Math.max(64, font.width(label) + 12);
                ctrlWidths.add(w);
                totalCtrlW += w + 3;
            }
            if (!ctrlWidths.isEmpty()) totalCtrlW -= 3;

            this.maxHeaderRow1ScrollX = Math.max(0, totalCtrlW - controllersAreaW);
            this.headerRow1ScrollX = Math.max(0, Math.min(maxHeaderRow1ScrollX, headerRow1ScrollX));

            ResourceLocation hoveredController = null;

            BoardScissorHelper.enableScissor(graphics, x + 10, y + 36, x + 10 + controllersAreaW, y + 51);
            graphics.pose().pushPose();
            graphics.pose().translate((float) -headerRow1ScrollX, 0, 0);

            int curX = x + 10;
            for (int i = 0; i < totalCount; i++) {
                ResourceLocation mbWs = mbWorkstations.get(i);
                int w = ctrlWidths.get(i);
                boolean isSelected = mbWs.equals(node.getMachineIcon());
                double vMouseX = mouseX + headerRow1ScrollX;
                boolean hov = mouseX >= x + 10 && mouseX <= x + 10 + controllersAreaW && vMouseX >= curX && vMouseX <= curX + w && mouseY >= y + 38 && mouseY <= y + 50;
                if (hov) hoveredController = mbWs;

                int fill = isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B);
                int border = isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658);

                graphics.fill(curX, y + 38, curX + w, y + 50, fill);
                graphics.renderOutline(curX, y + 38, w, 12, border);

                String label = getMultiblockShortLabel(mbWs);
                int textCol = isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8);
                graphics.drawCenteredString(font, font.plainSubstrByWidth(label, w - 4), curX + w / 2, y + 40, textCol);
                curX += w + 3;
            }

            graphics.pose().popPose();
            BoardScissorHelper.disableScissor(graphics);

            if (maxHeaderRow1ScrollX > 0) {
                if (headerRow1ScrollX > 2) {
                    graphics.fill(x + 10, y + 38, x + 18, y + 50, 0xCC181C26);
                    graphics.drawCenteredString(font, "◀", x + 14, y + 40, 0xFF80D0FF);
                }
                if (headerRow1ScrollX < maxHeaderRow1ScrollX - 2) {
                    graphics.fill(x + 10 + controllersAreaW - 8, y + 38, x + 10 + controllersAreaW, y + 50, 0xCC181C26);
                    graphics.drawCenteredString(font, "▶", x + 10 + controllersAreaW - 4, y + 40, 0xFF80D0FF);
                }
            }

            if (supportsParHatch) {
                boolean parHov = mouseX >= parBtnX && mouseX <= parBtnX + parBtnW && mouseY >= y + 38 && mouseY <= y + 50;
                if (equippedParallel != null) {
                    graphics.fill(parBtnX, y + 38, parBtnX + parBtnW, y + 50, parHov ? 0xFF3A1C22 : 0xFF202B38);
                    graphics.renderOutline(parBtnX, y + 38, parBtnW, 12, parHov ? 0xFFFF6B6B : 0xFF45B074);
                    String parText = parHov ? ("✕ " + Component.translatable("gui.gtcalcboard.config.remove").getString())
                            : ("⚡ " + equippedParallel.getParallelMultiplier() + "x " + Component.translatable("gui.gtcalcboard.addon_cat.parallel").getString());
                    graphics.drawCenteredString(font, font.plainSubstrByWidth(parText, parBtnW - 4), parBtnX + parBtnW / 2, y + 40, parHov ? 0xFFFF8888 : 0xFF55FF88);
                } else {
                    graphics.fill(parBtnX, y + 38, parBtnX + parBtnW, y + 50, parHov ? 0xFF2B3A50 : 0xFF202633);
                    graphics.renderOutline(parBtnX, y + 38, parBtnW, 12, parHov ? 0xFF589CFF : 0xFF3F506B);
                    String pLabel = Component.translatable("gui.gtcalcboard.config.install_parallel_hatch").getString();
                    graphics.drawCenteredString(font, font.plainSubstrByWidth(pLabel, parBtnW - 4), parBtnX + parBtnW / 2, y + 40, parHov ? 0xFF80D0FF : 0xFF58A6FF);
                }
            }

            List<MachineAddon> allCoils = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getAllCoils();
            int coilAreaW = dialogW - 20;

            List<Integer> coilWidths = new ArrayList<>();
            int totalCoilsW = 0;
            for (MachineAddon coil : allCoils) {
                String label = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getCoilShortLabel(coil);
                int w = Math.max(54, font.width(label) + 12);
                coilWidths.add(w);
                totalCoilsW += w + 3;
            }
            if (!coilWidths.isEmpty()) totalCoilsW -= 3;

            this.maxHeaderRow2ScrollX = Math.max(0, totalCoilsW - coilAreaW);
            this.headerRow2ScrollX = Math.max(0, Math.min(maxHeaderRow2ScrollX, headerRow2ScrollX));

            MachineAddon hoveredCoil = null;
            boolean hoveredCoilLocked = false;

            BoardScissorHelper.enableScissor(graphics, x + 10, y + 51, x + 10 + coilAreaW, y + 66);
            graphics.pose().pushPose();
            graphics.pose().translate((float) -headerRow2ScrollX, 0, 0);

            int cCurX = x + 10;
            for (int cIdx = 0; cIdx < allCoils.size(); cIdx++) {
                MachineAddon coil = allCoils.get(cIdx);
                int w = coilWidths.get(cIdx);
                int cTemp = (coil instanceof com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon gtCoil) ? gtCoil.getCoilTemperature() : 1800;
                boolean isSelected = (cTemp == curCoilTemp);
                boolean isSufficient = (reqCoilTemp <= 0 || cTemp >= reqCoilTemp);

                double vMouseX = mouseX + headerRow2ScrollX;
                boolean hov = mouseX >= x + 10 && mouseX <= x + 10 + coilAreaW && vMouseX >= cCurX && vMouseX <= cCurX + w && mouseY >= y + 52 && mouseY <= y + 64;

                if (hov) {
                    hoveredCoil = coil;
                    hoveredCoilLocked = !isSufficient;
                }

                int fill = !isSufficient ? (hov ? 0xFF4A1E24 : 0xFF3A1C22) : (isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B));
                int border = !isSufficient ? 0xFFFF5555 : (isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658));

                graphics.fill(cCurX, y + 52, cCurX + w, y + 64, fill);
                graphics.renderOutline(cCurX, y + 52, w, 12, border);

                String cLabel = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getCoilShortLabel(coil);
                if (!isSufficient) {
                    cLabel = "🔒 " + cLabel;
                }
                int textCol = !isSufficient ? 0xFFFF8888 : (isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8));
                graphics.drawCenteredString(font, font.plainSubstrByWidth(cLabel, w - 4), cCurX + w / 2, y + 54, textCol);

                cCurX += w + 3;
            }

            graphics.pose().popPose();
            BoardScissorHelper.disableScissor(graphics);

            if (maxHeaderRow2ScrollX > 0) {
                if (headerRow2ScrollX > 2) {
                    graphics.fill(x + 10, y + 52, x + 18, y + 64, 0xCC181C26);
                    graphics.drawCenteredString(font, "◀", x + 14, y + 54, 0xFF80D0FF);
                }
                if (headerRow2ScrollX < maxHeaderRow2ScrollX - 2) {
                    graphics.fill(x + 10 + coilAreaW - 8, y + 52, x + 10 + coilAreaW, y + 64, 0xCC181C26);
                    graphics.drawCenteredString(font, "▶", x + 10 + coilAreaW - 4, y + 54, 0xFF80D0FF);
                }
            }

            if (hoveredController != null) {
                List<Component> tt = new ArrayList<>();
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(hoveredController);
                String fullName = (item != null && item != net.minecraft.world.item.Items.AIR) ? item.getDescription().getString() : hoveredController.getPath();
                tt.add(Component.literal("§e🏛 " + fullName));
                tt.add(Component.literal("§8" + hoveredController));
                if (hoveredController.equals(node.getMachineIcon())) {
                    tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.config.active_controller").getString()));
                } else {
                    tt.add(Component.literal("§7[Click to select]"));
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            } else if (hoveredCoil != null) {
                List<Component> tt = new ArrayList<>();
                tt.add(Component.literal("§6♨ " + hoveredCoil.getName()));
                for (String line : hoveredCoil.getDescription().split("\n")) {
                    tt.add(Component.literal("§7" + line));
                }
                if (hoveredCoilLocked) {
                    tt.add(Component.literal("§c❌ " + Component.translatable("gui.gtcalcboard.config.coil_req_tooltip", reqCoilTemp).getString()));
                } else if (hoveredCoil instanceof com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon gtCoil && gtCoil.getCoilTemperature() == curCoilTemp) {
                    tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.coil_met").getString()));
                } else {
                    tt.add(Component.literal("§7[Click to equip]"));
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            }
        } else {
            List<ResourceLocation> mbWorkstations = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
            if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
                mbWorkstations = List.of(node.getMachineIcon());
            }

            MachineAddon equippedParallel = null;
            for (MachineAddon a : node.getAddons()) {
                if (a != null && a.getCategory() == MachineAddon.Category.PARALLEL) {
                    equippedParallel = a;
                    break;
                }
            }

            int defPar = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getDefaultParallel(node);
            int totalCount = mbWorkstations.size();
            boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
            int parBtnW = supportsParHatch ? 130 : 0;
            int parBtnX = x + dialogW - 10 - parBtnW;
            int controllersAreaW = supportsParHatch ? (parBtnX - (x + 10) - 8) : (dialogW - 20);

            int minBtnW = 80;
            int maxFitWithoutNav = Math.max(1, (controllersAreaW + 4) / (minBtnW + 4));
            boolean showNav = totalCount > maxFitWithoutNav;

            int visibleCount = showNav ? Math.max(1, (controllersAreaW - 40 + 4) / (minBtnW + 4)) : totalCount;
            int maxScroll = Math.max(0, totalCount - visibleCount);
            if (mbControllerScroll > maxScroll) mbControllerScroll = maxScroll;

            String navIndicator = showNav ? " (" + (mbControllerScroll + 1) + "-" + Math.min(totalCount, mbControllerScroll + visibleCount) + "/" + totalCount + ")" : "";
            String mbHeader = "§b🏛 " + Component.translatable("gui.gtcalcboard.config.multiblock_controller_title").getString() + "§7" + navIndicator;
            graphics.drawString(font, mbHeader, x + 10, y + 30, 0xFFFFFFFF, false);

            String parSummary = "§7⚡ " + node.getTotalParallel() + "x Par" + (defPar > 1 ? " (Default " + defPar + "x)" : (node.getTotalParallel() > 1 ? " (Base " + node.getParallel() + "x)" : " (Default 1x)"));
            int parSummaryW = font.width(parSummary);
            graphics.drawString(font, parSummary, x + dialogW - 10 - parSummaryW, y + 30, 0xFFFFFFFF, false);

            int curX = x + 10;
            int btnW;

            if (showNav) {
                int navBtnW = 16;
                boolean leftHov = mouseX >= curX && mouseX <= curX + navBtnW && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(curX, y + 44, curX + navBtnW, y + 60, leftHov ? 0xFF3D4558 : 0xFF282D3B);
                graphics.renderOutline(curX, y + 44, navBtnW, 16, leftHov ? 0xFF58D3FF : 0xFF3F4658);
                graphics.drawCenteredString(font, "◀", curX + navBtnW / 2, y + 48, mbControllerScroll > 0 ? 0xFFFFFFFF : 0xFF666666);
                curX += navBtnW + 4;
                btnW = (controllersAreaW - 40 - (visibleCount - 1) * 4) / visibleCount;
            } else {
                btnW = (controllersAreaW - (visibleCount - 1) * 4) / Math.max(1, visibleCount);
            }

            ResourceLocation hoveredController = null;
            int startIdx = showNav ? mbControllerScroll : 0;
            int endIdx = showNav ? Math.min(totalCount, mbControllerScroll + visibleCount) : totalCount;

            for (int i = startIdx; i < endIdx; i++) {
                ResourceLocation mbWs = mbWorkstations.get(i);
                boolean isSelected = mbWs.equals(node.getMachineIcon());
                boolean hov = mouseX >= curX && mouseX <= curX + btnW && mouseY >= y + 44 && mouseY <= y + 60;
                if (hov) hoveredController = mbWs;

                int fill = isSelected ? 0xFF1C3A2A : (hov ? 0xFF3D4558 : 0xFF282D3B);
                int border = isSelected ? 0xFF45B074 : (hov ? 0xFF589CFF : 0xFF3F4658);

                graphics.fill(curX, y + 44, curX + btnW, y + 60, fill);
                graphics.renderOutline(curX, y + 44, btnW, 16, border);

                String label = getMultiblockShortLabel(mbWs);
                int textCol = isSelected ? 0xFF55FF88 : (hov ? 0xFFFFFFFF : 0xFFB0B8C8);
                graphics.drawCenteredString(font, font.plainSubstrByWidth(label, btnW - 4), curX + btnW / 2, y + 48, textCol);
                curX += btnW + 4;
            }

            if (showNav) {
                int navBtnW = 16;
                boolean rightHov = mouseX >= curX && mouseX <= curX + navBtnW && mouseY >= y + 44 && mouseY <= y + 60;
                graphics.fill(curX, y + 44, curX + navBtnW, y + 60, rightHov ? 0xFF3D4558 : 0xFF282D3B);
                graphics.renderOutline(curX, y + 44, navBtnW, 16, rightHov ? 0xFF58D3FF : 0xFF3F4658);
                graphics.drawCenteredString(font, "▶", curX + navBtnW / 2, y + 48, mbControllerScroll < maxScroll ? 0xFFFFFFFF : 0xFF666666);
            }

            if (supportsParHatch) {
                boolean parHov = mouseX >= parBtnX && mouseX <= parBtnX + parBtnW && mouseY >= y + 44 && mouseY <= y + 60;
                if (equippedParallel != null) {
                    graphics.fill(parBtnX, y + 44, parBtnX + parBtnW, y + 60, parHov ? 0xFF3A1C22 : 0xFF202B38);
                    graphics.renderOutline(parBtnX, y + 44, parBtnW, 16, parHov ? 0xFFFF6B6B : 0xFF45B074);
                    String parText = parHov ? ("✕ " + Component.translatable("gui.gtcalcboard.config.remove").getString())
                            : ("⚡ " + equippedParallel.getParallelMultiplier() + "x " + Component.translatable("gui.gtcalcboard.addon_cat.parallel").getString());
                    graphics.drawCenteredString(font, font.plainSubstrByWidth(parText, parBtnW - 4), parBtnX + parBtnW / 2, y + 48, parHov ? 0xFFFF8888 : 0xFF55FF88);
                } else {
                    graphics.fill(parBtnX, y + 44, parBtnX + parBtnW, y + 60, parHov ? 0xFF2B3A50 : 0xFF202633);
                    graphics.renderOutline(parBtnX, y + 44, parBtnW, 16, parHov ? 0xFF589CFF : 0xFF3F506B);
                    String pLabel = Component.translatable("gui.gtcalcboard.config.install_parallel_hatch").getString();
                    graphics.drawCenteredString(font, font.plainSubstrByWidth(pLabel, parBtnW - 4), parBtnX + parBtnW / 2, y + 48, parHov ? 0xFF80D0FF : 0xFF58A6FF);
                }
            }

            if (hoveredController != null) {
                List<Component> tt = new ArrayList<>();
                var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(hoveredController);
                String fullName = (item != null && item != net.minecraft.world.item.Items.AIR) ? item.getDescription().getString() : hoveredController.getPath();
                tt.add(Component.literal("§e🏛 " + fullName));
                tt.add(Component.literal("§8" + hoveredController));
                boolean active = hoveredController.equals(node.getMachineIcon());
                if (active) {
                    tt.add(Component.literal("§a✔ " + Component.translatable("gui.gtcalcboard.config.active_controller").getString()));
                } else {
                    tt.add(Component.literal("§7[Click to select]"));
                }
                showTooltip(dialog, graphics, font, tt, mouseX, mouseY);
            }
        }
    }

    private static String getMultiblockShortLabel(ResourceLocation id) {
        if (id == null) return "🏛 Multi";
        String path = id.getPath().toLowerCase(Locale.ROOT);
        if (path.contains("auxiliary_booster_fusion") || path.contains("auxiliary_fusion") || path.contains("aux_booster")) {
            if (path.contains("mk2") || path.contains("mk_2") || path.contains("ii") || path.contains("aux2") || path.contains("aux_2") || path.contains("uiv")) return "⚡ Aux Mk2";
            if (path.contains("mk3") || path.contains("mk_3") || path.contains("iii") || path.contains("aux3") || path.contains("aux_3") || path.contains("opv")) return "⚡ Aux Mk3";
            return "⚡ Aux Mk1";
        }
        if (path.contains("reflector_fusion")) return "⚛ Reflector";
        if (path.contains("luv_fusion") || path.contains("fusion_reactor_mk1") || path.contains("fusion_mk1") || path.contains("mk_1") || path.contains("mk1") || path.contains("mki")) return "⚛ Fusion Mk1";
        if (path.contains("zpm_fusion") || path.contains("fusion_reactor_mk2") || path.contains("fusion_mk2") || path.contains("mk_2") || path.contains("mk2") || path.contains("mkii")) return "⚛ Fusion Mk2";
        if (path.contains("uv_fusion") || path.contains("fusion_reactor_mk3") || path.contains("fusion_mk3") || path.contains("mk_3") || path.contains("mk3") || path.contains("mkiii")) return "⚛ Fusion Mk3";
        if (path.contains("uev_fusion") || path.contains("fusion_reactor_mk4") || path.contains("fusion_mk4") || path.contains("mk_4") || path.contains("mk4") || path.contains("mkiv")) return "⚛ Fusion Mk4";
        if (path.contains("uxv_fusion") || path.contains("fusion_reactor_mk5") || path.contains("fusion_mk5") || path.contains("mk_5") || path.contains("mk5") || path.contains("mkv")) return "⚛ Fusion Mk5";
        if (path.contains("max_fusion") || path.contains("fusion_reactor_mk6") || path.contains("fusion_mk6") || path.contains("mk_6") || path.contains("mk6") || path.contains("mkvi")) return "⚛ Fusion Mk6";
        if (path.contains("extreme_chemical_reactor") || path.equals("ecr")) return "⚡ ECR";
        if (path.contains("incomprehensible_chemical_reactor") || path.equals("icr")) return "⚡ ICR";
        if (path.contains("large_chemical_reactor") || path.equals("lcr")) return "🏛 LCR";
        if (path.contains("super_cracker") || path.contains("sdf")) return "⚡ SDF Cracker";
        if (path.contains("cracker")) return "🏛 Cracker";
        if (path.contains("supreme")) return "⚡ Supreme";
        if (path.contains("nyinsane")) return "⚡ Nyinsane";
        if (path.contains("large_fluid_distillation") || path.contains("large_distillation")) return "🏛 Large DT";
        if (path.contains("distillation_tower")) return "🏛 Distillation";
        if (path.contains("yielding_exhaustor") || path.contains("yeast")) return "🧬 Yeast";

        var item = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id);
        if (item != null && item != net.minecraft.world.item.Items.AIR) {
            String name = item.getDescription().getString();
            name = name.replaceAll("\\[.*?\\]", "").trim();
            if (name.contains("Fusion Reactor")) {
                if (name.contains("MK VI") || name.contains("Mk 6") || name.contains("Mk.6") || name.contains("MK 6") || name.contains("VI")) return "⚛ Fusion Mk6";
                if (name.contains("MK V") || name.contains("Mk 5") || name.contains("Mk.5") || name.contains("MK 5") || name.contains("V")) return "⚛ Fusion Mk5";
                if (name.contains("MK IV") || name.contains("Mk 4") || name.contains("Mk.4") || name.contains("MK 4") || name.contains("IV")) return "⚛ Fusion Mk4";
                if (name.contains("MK III") || name.contains("Mk 3") || name.contains("Mk.3") || name.contains("MK 3") || name.contains("III")) return "⚛ Fusion Mk3";
                if (name.contains("MK II") || name.contains("Mk 2") || name.contains("Mk.2") || name.contains("MK 2") || name.contains("II")) return "⚛ Fusion Mk2";
                if (name.contains("MK I") || name.contains("Mk 1") || name.contains("Mk.1") || name.contains("MK 1") || name.contains("I")) return "⚛ Fusion Mk1";
                return "⚛ Fusion";
            }
            if (name.contains("Auxiliary Booster") || name.contains("Auxiliary Fusion")) {
                if (name.contains("III") || name.contains("3")) return "⚡ Aux Mk3";
                if (name.contains("II") || name.contains("2")) return "⚡ Aux Mk2";
                return "⚡ Aux Mk1";
            }
            if (name.contains("Reflector Fusion")) return "⚛ Reflector";
            if (name.startsWith("Advanced ")) name = "Adv. " + name.substring(9);
            else if (name.startsWith("Elite ")) name = "Elite " + name.substring(6);
            else if (name.startsWith("Ultimate ")) name = "Ult. " + name.substring(9);
            else if (name.startsWith("Material Processing ")) name = "Mat. Proc. " + name.substring(20);
            return "🏛 " + name;
        }
        return "🏛 " + id.getPath();
    }

    @Override
    public boolean handleDialogHeaderClick(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                           double mouseX, double mouseY, int button, EditBox parallelBox, BoardScreen parent) {
        if (MachineAddon.isTurbineMachine(node) && node.isMultiblock()) {
            int pmax = GTPowerCalculator.getMaxParallelCapacity(node);
            int pmaxBtnW = 74;
            int pmaxBtnX = x + dialogW - 10 - pmaxBtnW;
            if (mouseX >= pmaxBtnX && mouseX <= pmaxBtnX + pmaxBtnW && mouseY >= y + 28 && mouseY <= y + 42) {
                node.setParallel(pmax);
                if (parallelBox != null) parallelBox.setValue(String.valueOf(pmax));
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }

            int resetBtnW = 54;
            int resetBtnX = pmaxBtnX - 4 - resetBtnW;
            if (mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= y + 28 && mouseY <= y + 42) {
                List<MachineAddon> rotors = new ArrayList<>();
                for (MachineAddon a : node.getAddons()) {
                    if (a.getCategory() == MachineAddon.Category.ROTOR) rotors.add(a);
                }
                com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
                for (MachineAddon r : rotors) {
                    if (adapter != null) adapter.handleUninstallAddon(node, r);
                    else node.getAddons().remove(r);
                }
                node.setRotorEfficiency(100);
                node.setRotorPower(100);
                node.setRotorName(null);
                if (dialog != null) {
                    dialog.invalidateFilteredCatalog();
                }
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }

            int btnY = y + 46;
            int curX = x + 10;
            int gap = 4;

            int holderBtnW = 100;
            if (mouseX >= curX && mouseX <= curX + holderBtnW && mouseY >= btnY && mouseY <= btnY + 16) {
                GTTurbineHelper.cycleRotorHolderTier(node, button == 1 ? -1 : 1);
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
            curX += holderBtnW + gap;

            // Dynamo Voltage Tier Button click
            int dynamoTierBtnW = 100;
            if (mouseX >= curX && mouseX <= curX + dynamoTierBtnW && mouseY >= btnY && mouseY <= btnY + 16) {
                GTTurbineHelper.cycleDynamoTier(node, button == 1 ? -1 : 1);
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
            curX += dynamoTierBtnW + gap;

            // Dynamo Amperage Button click
            int dynamoAmpsBtnW = 70;
            if (mouseX >= curX && mouseX <= curX + dynamoAmpsBtnW && mouseY >= btnY && mouseY <= btnY + 16) {
                GTTurbineHelper.cycleDynamoAmperage(node, button == 1 ? -1 : 1);
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
            curX += dynamoAmpsBtnW + gap;

            // Boost Button click (via IModAdapter SPI)
            com.gtceu.calcboard.compat.IModAdapter clickAdapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
            if (clickAdapter != null && clickAdapter.supportsBoosterControl(node)) {
                int boostBtnW = 110;
                if (mouseX >= curX && mouseX <= curX + boostBtnW && mouseY >= btnY && mouseY <= btnY + 16) {
                    clickAdapter.cycleBooster(node, button == 1 ? -1 : 1);
                    if (parent != null) parent.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
            }
        } else if (node.isLiquidBoilerRecipe() || (com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node) != null && com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).isBoilerRecipe(node))) {
            com.gtceu.calcboard.api.type.GTBoilerTier curTier = com.gtceu.calcboard.api.type.GTBoilerTier.getBoilerTier(node);
            if (curTier.isMultiblock()) {
                int curThrottle = node.getBoilerThrottle();
                int thrX = x + dialogW - 250;
                String thrTitle = "§e⚡ " + Component.translatable("gui.gtcalcboard.boiler_throttle").getString() + ":";
                int titleW = Minecraft.getInstance().font.width(thrTitle);
                int minusX = thrX + titleW + 6;
                if (mouseX >= minusX && mouseX <= minusX + 14 && mouseY >= y + 28 && mouseY <= y + 40) {
                    node.setBoilerThrottle(Math.max(25, curThrottle - 5));
                    if (parent != null) parent.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
                int valX = minusX + 16;
                int plusX = valX + 34;
                if (mouseX >= plusX && mouseX <= plusX + 14 && mouseY >= y + 28 && mouseY <= y + 40) {
                    node.setBoilerThrottle(Math.min(100, curThrottle + 5));
                    if (parent != null) parent.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
                int[] presets = {25, 50, 75, 100};
                int curPreX = plusX + 18;
                for (int pre : presets) {
                    int preW = pre == 100 ? 28 : 24;
                    if (mouseX >= curPreX && mouseX <= curPreX + preW && mouseY >= y + 28 && mouseY <= y + 40) {
                        node.setBoilerThrottle(pre);
                        if (parent != null) parent.markSummaryDirty();
                        Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                        );
                        return true;
                    }
                    curPreX += preW + 3;
                }
            }

            com.gtceu.calcboard.api.type.GTBoilerTier[] bTiers = com.gtceu.calcboard.api.type.GTBoilerTier.values();
            int btnW = 70;
            int gap = 4;
            int btnY = y + 44;
            for (int i = 0; i < bTiers.length; i++) {
                com.gtceu.calcboard.api.type.GTBoilerTier bt = bTiers[i];
                int btnX = x + 10 + i * (btnW + gap);
                if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16) {
                    node.setMachineIcon(bt.getDefaultIcon(node.isLiquidBoilerRecipe()));
                    node.setMultiblock(bt.isMultiblock());
                    if (parent != null) parent.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
            }
        } else if (!node.isMultiblock()) {
            if (node.supportsSteamMode()) {
                int btnX = x + 10;
                if (mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60) {
                    node.setSteamMode(com.gtceu.calcboard.api.type.SteamMode.LOW_PRESSURE);
                    if (parent != null) parent.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
                btnX += 116;
                if (mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60) {
                    node.setSteamMode(com.gtceu.calcboard.api.type.SteamMode.HIGH_PRESSURE);
                    if (parent != null) parent.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
                btnX += 116;
                if (mouseX >= btnX && mouseX <= btnX + 90 && mouseY >= y + 44 && mouseY <= y + 60) {
                    node.setSteamMode(com.gtceu.calcboard.api.type.SteamMode.NONE);
                    if (parent != null) parent.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
            }
        } else if (isFusionMachine(node) || isCoilMultiblock(node)) {
            if (isCoilMultiblock(node)) {
                boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
                int parBtnW = supportsParHatch ? 120 : 0;
                int parBtnX = x + dialogW - 10 - parBtnW;
                if (supportsParHatch && mouseX >= parBtnX && mouseX <= parBtnX + parBtnW && mouseY >= y + 38 && mouseY <= y + 50) {
                    MachineAddon equippedParallel = null;
                    for (MachineAddon a : node.getAddons()) {
                        if (a != null && a.getCategory() == MachineAddon.Category.PARALLEL) {
                            equippedParallel = a;
                            break;
                        }
                    }
                    if (equippedParallel != null) {
                        com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).handleUninstallAddon(node, equippedParallel);
                    } else if (dialog != null) {
                        dialog.setSelectedCategory(MachineAddon.Category.PARALLEL);
                    }
                    if (dialog != null) {
                        dialog.invalidateFilteredCatalog();
                    }
                    if (parent != null) parent.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
            }

            if (mouseY >= y + 36 && mouseY <= y + 50 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
                isDraggingHeader = true;
                draggingRow = 1;
                dragStartX = mouseX;
                dragStartScrollX = headerRow1ScrollX;
                hasDraggedHeader = false;
                return true;
            } else if (mouseY >= y + 51 && mouseY <= y + 66 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
                isDraggingHeader = true;
                draggingRow = 2;
                dragStartX = mouseX;
                dragStartScrollX = headerRow2ScrollX;
                hasDraggedHeader = false;
                return true;
            }
        } else {
            List<ResourceLocation> mbWorkstations = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
            if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
                mbWorkstations = List.of(node.getMachineIcon());
            }

            MachineAddon equippedParallel = null;
            for (MachineAddon a : node.getAddons()) {
                if (a != null && a.getCategory() == MachineAddon.Category.PARALLEL) {
                    equippedParallel = a;
                    break;
                }
            }

            int totalCount = mbWorkstations.size();
            boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
            int parBtnW = supportsParHatch ? 130 : 0;
            int parBtnX = x + dialogW - 10 - parBtnW;
            int controllersAreaW = supportsParHatch ? (parBtnX - (x + 10) - 8) : (dialogW - 20);

            int minBtnW = 80;
            int maxFitWithoutNav = Math.max(1, (controllersAreaW + 4) / (minBtnW + 4));
            boolean showNav = totalCount > maxFitWithoutNav;

            int visibleCount = showNav ? Math.max(1, (controllersAreaW - 40 + 4) / (minBtnW + 4)) : totalCount;
            int maxScroll = Math.max(0, totalCount - visibleCount);

            int curX = x + 10;
            int btnW;

            if (showNav) {
                int navBtnW = 16;
                if (mouseX >= curX && mouseX <= curX + navBtnW && mouseY >= y + 44 && mouseY <= y + 60) {
                    if (mbControllerScroll > 0) {
                        mbControllerScroll--;
                        Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                        );
                    }
                    return true;
                }
                curX += navBtnW + 4;
                btnW = (controllersAreaW - 40 - (visibleCount - 1) * 4) / visibleCount;
            } else {
                btnW = (controllersAreaW - (visibleCount - 1) * 4) / Math.max(1, visibleCount);
            }

            int startIdx = showNav ? Math.min(mbControllerScroll, maxScroll) : 0;
            int endIdx = Math.min(totalCount, startIdx + visibleCount);

            for (int i = startIdx; i < endIdx; i++) {
                ResourceLocation mbWs = mbWorkstations.get(i);
                if (mouseX >= curX && mouseX <= curX + btnW && mouseY >= y + 44 && mouseY <= y + 60) {
                    boolean isThreading = MultiblockDetector.isThreadingMultiblock(mbWs);
                    node.setMachineIcon(mbWs);
                    node.setThreadingActive(isThreading);
                    if (!MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations())) {
                        node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.PARALLEL);
                        node.setParallel(1);
                        if (dialog != null && dialog.getSelectedCategory() == MachineAddon.Category.PARALLEL) {
                            dialog.setSelectedCategory(null);
                        }
                    }
                    if (dialog != null) {
                        if (!isThreading && dialog.getSelectedCategory() == com.gtceu.calcboard.api.catalog.AddonCategory.THREADING) {
                            dialog.setSelectedCategory(null);
                        }
                        dialog.invalidateFilteredCatalog();
                    }
                    if (parent != null) parent.markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(
                            SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                    );
                    return true;
                }
                curX += btnW + 4;
            }

            if (showNav) {
                int navBtnW = 16;
                if (mouseX >= curX && mouseX <= curX + navBtnW && mouseY >= y + 44 && mouseY <= y + 60) {
                    if (mbControllerScroll < maxScroll) {
                        mbControllerScroll++;
                        Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                        );
                    }
                    return true;
                }
            }

            if (supportsParHatch && mouseX >= parBtnX && mouseX <= parBtnX + parBtnW && mouseY >= y + 44 && mouseY <= y + 60) {
                if (equippedParallel != null) {
                    com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).handleUninstallAddon(node, equippedParallel);
                } else if (dialog != null) {
                    dialog.setSelectedCategory(MachineAddon.Category.PARALLEL);
                }
                if (dialog != null) {
                    dialog.invalidateFilteredCatalog();
                }
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(
                        SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                );
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean handleDialogHeaderDrag(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                           double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (isDraggingHeader) {
            if (Math.abs(mouseX - dragStartX) > 2) {
                hasDraggedHeader = true;
            }
            if (draggingRow == 1) {
                headerRow1ScrollX = Math.max(0, Math.min(maxHeaderRow1ScrollX, headerRow1ScrollX - dragX));
            } else if (draggingRow == 2) {
                headerRow2ScrollX = Math.max(0, Math.min(maxHeaderRow2ScrollX, headerRow2ScrollX - dragX));
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean handleDialogHeaderRelease(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                              double mouseX, double mouseY, int button,
                                              EditBox parallelBox, BoardScreen parent) {
        if (isDraggingHeader) {
            boolean wasDragging = hasDraggedHeader;
            int row = draggingRow;
            isDraggingHeader = false;
            hasDraggedHeader = false;

            if (!wasDragging) {
                return executeHeaderSelection(dialog, node, x, y, dialogW, mouseX, mouseY, button, parallelBox, parent, row);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean handleDialogHeaderScroll(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                             double mouseX, double mouseY, double delta) {
        if (MachineAddon.isTurbineMachine(node) && node.isMultiblock()) {
            int btnY = y + 46;
            if (mouseY >= btnY && mouseY <= btnY + 16) {
                int dir = delta > 0 ? 1 : -1;
                int curX = x + 10;
                int gap = 4;

                int holderBtnW = 100;
                int dynamoTierBtnW = 100;
                int dynamoAmpsBtnW = 70;
                int boostBtnW = 110;

                if (mouseX >= curX && mouseX <= curX + holderBtnW) {
                    GTTurbineHelper.cycleRotorHolderTier(node, dir);
                    if (dialog != null && dialog.getParent() != null) dialog.getParent().markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
                    return true;
                }
                curX += holderBtnW + gap;

                if (mouseX >= curX && mouseX <= curX + dynamoTierBtnW) {
                    GTTurbineHelper.cycleDynamoTier(node, dir);
                    if (dialog != null && dialog.getParent() != null) dialog.getParent().markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
                    return true;
                }
                curX += dynamoTierBtnW + gap;

                if (mouseX >= curX && mouseX <= curX + dynamoAmpsBtnW) {
                    GTTurbineHelper.cycleDynamoAmperage(node, dir);
                    if (dialog != null && dialog.getParent() != null) dialog.getParent().markSummaryDirty();
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
                    return true;
                }
                curX += dynamoAmpsBtnW + gap;

                com.gtceu.calcboard.compat.IModAdapter scrollAdapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
                if (scrollAdapter != null && scrollAdapter.supportsBoosterControl(node)) {
                    if (mouseX >= curX && mouseX <= curX + boostBtnW) {
                        scrollAdapter.cycleBooster(node, dir);
                        if (dialog != null && dialog.getParent() != null) dialog.getParent().markSummaryDirty();
                        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
                        return true;
                    }
                }
            }
        }
        if (isFusionMachine(node) || isCoilMultiblock(node)) {
            if (mouseY >= y + 36 && mouseY <= y + 50 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
                headerRow1ScrollX = Math.max(0, Math.min(maxHeaderRow1ScrollX, headerRow1ScrollX - delta * 24.0));
                return true;
            } else if (mouseY >= y + 51 && mouseY <= y + 66 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
                headerRow2ScrollX = Math.max(0, Math.min(maxHeaderRow2ScrollX, headerRow2ScrollX - delta * 24.0));
                return true;
            }
        }
        return handleControllerScroll(node, delta);
    }

    private boolean executeHeaderSelection(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                            double mouseX, double mouseY, int button,
                                            EditBox parallelBox, BoardScreen parent, int row) {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;

        if (isFusionMachine(node)) {
            List<ResourceLocation> mbWorkstations = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
            if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
                mbWorkstations = List.of(node.getMachineIcon());
            }

            int controllersAreaW = dialogW - 20;

            if (row == 1 && mouseY >= y + 38 && mouseY <= y + 50 && mouseX >= x + 10 && mouseX <= x + 10 + controllersAreaW) {
                double vMouseX = mouseX + headerRow1ScrollX;
                int curX = x + 10;
                for (ResourceLocation mbWs : mbWorkstations) {
                    String label = getMultiblockShortLabel(mbWs);
                    int w = Math.max(68, font.width(label) + 12);
                    if (vMouseX >= curX && vMouseX <= curX + w) {
                        node.setMachineIcon(mbWs);
                        GTVoltageTier tier = GTCEuModAdapter.extractVoltageTierFromIcon(mbWs);
                        if (tier != null) {
                            node.setTargetTier(tier);
                        }
                        if (dialog != null) dialog.invalidateFilteredCatalog();
                        if (parent != null) parent.markSummaryDirty();
                        Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                        );
                        return true;
                    }
                    curX += w + 3;
                }
            } else if (row == 2 && mouseY >= y + 52 && mouseY <= y + 64 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
                List<Integer> availableReflectorTiers = ReflectorHelper.getAvailableReflectorTiers();
                double vMouseX = mouseX + headerRow2ScrollX;
                int rCurX = x + 10;
                for (int t : availableReflectorTiers) {
                    String rLabel = (t == 0) ? Component.translatable("gui.gtcalcboard.reflector.none").getString() : ("✦ T" + t);
                    int w = Math.max(48, font.width(rLabel) + 12);
                    if (vMouseX >= rCurX && vMouseX <= rCurX + w) {
                        com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper.installReflector(node, t);
                        if (dialog != null) dialog.invalidateFilteredCatalog();
                        if (parent != null) parent.markSummaryDirty();
                        Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                        );
                        return true;
                    }
                    rCurX += w + 3;
                }
            }
        } else if (isCoilMultiblock(node)) {
            List<ResourceLocation> mbWorkstations = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
            if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
                mbWorkstations = List.of(node.getMachineIcon());
            }

            boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
            int parBtnW = supportsParHatch ? 120 : 0;
            int parBtnX = x + dialogW - 10 - parBtnW;
            int controllersAreaW = supportsParHatch ? (parBtnX - (x + 10) - 6) : (dialogW - 20);

            if (row == 1 && mouseY >= y + 36 && mouseY <= y + 51 && mouseX >= x + 10 && mouseX <= x + 10 + controllersAreaW) {
                double vMouseX = mouseX + headerRow1ScrollX;
                int curX = x + 10;
                for (ResourceLocation mbWs : mbWorkstations) {
                    String label = getMultiblockShortLabel(mbWs);
                    int w = Math.max(64, font.width(label) + 12);
                    if (vMouseX >= curX && vMouseX <= curX + w) {
                        boolean isThreading = MultiblockDetector.isThreadingMultiblock(mbWs);
                        node.setMachineIcon(mbWs);
                        node.setThreadingActive(isThreading);
                        if (!MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations())) {
                            node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.PARALLEL);
                            node.setParallel(1);
                            if (dialog != null && dialog.getSelectedCategory() == MachineAddon.Category.PARALLEL) {
                                dialog.setSelectedCategory(null);
                            }
                        }
                        if (dialog != null) {
                            if (!isThreading && dialog.getSelectedCategory() == com.gtceu.calcboard.api.catalog.AddonCategory.THREADING) {
                                dialog.setSelectedCategory(null);
                            }
                            dialog.invalidateFilteredCatalog();
                        }
                        if (parent != null) parent.markSummaryDirty();
                        Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                        );
                        return true;
                    }
                    curX += w + 3;
                }
            } else if (row == 2 && mouseY >= y + 51 && mouseY <= y + 66 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
                List<MachineAddon> allCoils = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getAllCoils();
                double vMouseX = mouseX + headerRow2ScrollX;
                int cCurX = x + 10;
                for (MachineAddon coil : allCoils) {
                    String label = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getCoilShortLabel(coil);
                    int w = Math.max(54, font.width(label) + 12);
                    if (vMouseX >= cCurX && vMouseX <= cCurX + w) {
                        com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.installCoil(node, coil);
                        if (dialog != null) dialog.invalidateFilteredCatalog();
                        if (parent != null) parent.markSummaryDirty();
                        Minecraft.getInstance().getSoundManager().play(
                                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
                        );
                        return true;
                    }
                    cCurX += w + 3;
                }
            }
        }

        return false;
    }

    public static boolean handleControllerScroll(RecipeNode node, double delta) {
        if (node == null || !node.isMultiblock()) return false;
        List<ResourceLocation> mbWorkstations = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
        if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
            mbWorkstations = List.of(node.getMachineIcon());
        }
        int totalCount = mbWorkstations.size();
        boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
        int parBtnW = supportsParHatch ? 130 : 0;
        int controllersAreaW = supportsParHatch ? (460 - 20 - parBtnW - 8) : (460 - 20);
        int minBtnW = 80;
        int maxFitWithoutNav = Math.max(1, (controllersAreaW + 4) / (minBtnW + 4));
        if (totalCount <= maxFitWithoutNav) return false;

        int visibleCount = Math.max(1, (controllersAreaW - 40 + 4) / (minBtnW + 4));
        int maxScroll = Math.max(0, totalCount - visibleCount);
        if (maxScroll <= 0) return false;

        if (delta > 0) {
            if (mbControllerScroll > 0) {
                mbControllerScroll--;
                return true;
            }
        } else if (delta < 0) {
            if (mbControllerScroll < maxScroll) {
                mbControllerScroll++;
                return true;
            }
        }
        return false;
    }
}
