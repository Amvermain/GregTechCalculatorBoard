package com.gtceu.calcboard.client.gui.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import com.gtceu.calcboard.compat.gtceu.GTTurbineHelper;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;

/**
 * Dedicated event and interaction handler for GTCEu machine configuration dialog headers.
 */
@OnlyIn(Dist.CLIENT)
public class GTCEuMachineDialogHeaderHandler {

    private final GTCEuMachineDialogState state;

    public GTCEuMachineDialogHeaderHandler(GTCEuMachineDialogState state) {
        this.state = state;
    }
    private boolean handleCombustionDialogHeaderClick(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                                     double mouseX, double mouseY, int button, EditBox parallelBox, BoardScreen parent) {
        int resetBtnW = 54;
        int resetBtnX = x + dialogW - 10 - resetBtnW;
        if (mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= y + 28 && mouseY <= y + 42) {
            clearCombustionBoosts(node);
            if (dialog != null) dialog.invalidateFilteredCatalog();
            if (parent != null) parent.markSummaryDirty();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
            return true;
        }

        int btnY = y + 46;
        int curX = x + 10;
        Font font = Minecraft.getInstance().font;
        if (font == null) {
            return false;
        }

        if (GTCombustionHelper.isLargeCombustionEngine(node)) {
            return handleLceBoostClick(dialog, node, curX, btnY, font, mouseX, mouseY, parent);
        }
        if (GTCombustionHelper.isExtremeCombustionEngine(node)) {
            return handleEceBoostClick(dialog, node, curX, btnY, font, mouseX, mouseY, parent);
        }
        if (GTCombustionHelper.isStarTModule(node) || GTCombustionHelper.isModularCombustionFrame(node)) {
            return handleStarTHeaderClick(dialog, node, curX, btnY, font, mouseX, mouseY, parent);
        }
        return false;
    }

    private boolean handleStarTHeaderClick(MachineConfigDialog dialog, RecipeNode node, int curX, int btnY,
                                          Font font, double mouseX, double mouseY, BoardScreen parent) {
        int gap = 4;
        if (GTCombustionHelper.isStarTModule(node)) {
            String ox = node.getProperties().get(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE);
            boolean oxActive = ox != null && !ox.isEmpty() && !"none".equalsIgnoreCase(ox);
            String oxLabel = "💨 " + (oxActive ? ("§b" + GTCombustionHelper.getOxidizerDisplayName(ox) + " §a(2x Fuel, Amp Boost)") : "§7Oxidizer: None");
            int oxBtnW = Math.max(120, font.width(oxLabel) + 12);

            if (mouseX >= curX && mouseX <= curX + oxBtnW && mouseY >= btnY && mouseY <= btnY + 16) {
                toggleStarTOxidizer(node);
                if (dialog != null) dialog.invalidateFilteredCatalog();
                if (parent != null) parent.markSummaryDirty();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
                return true;
            }
            curX += oxBtnW + gap;
        }

        String cl = node.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE);
        boolean clActive = cl != null && !cl.isEmpty() && !"none".equalsIgnoreCase(cl);
        String clLabel = "❄ " + (clActive ? ("§b" + GTCombustionHelper.getCoolantDisplayName(cl)) : "§7Coolant: None");
        int clBtnW = Math.max(110, font.width(clLabel) + 12);

        if (mouseX >= curX && mouseX <= curX + clBtnW && mouseY >= btnY && mouseY <= btnY + 16) {
            cycleStarTCoolant(node);
            if (dialog != null) dialog.invalidateFilteredCatalog();
            if (parent != null) parent.markSummaryDirty();
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
            return true;
        }

        return false;
    }

    private void toggleStarTOxidizer(RecipeNode node) {
        boolean cur = GTCombustionHelper.isOxidizerBoosted(node);
        if (cur) {
            node.getAddons().removeIf(GTAddonCompatibilityHandler::isOxidizerAddon);
            node.getProperties().set(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE, "none");
        } else {
            String addonId = GTCombustionHelper.getExpectedOxidizerAddonId(node);
            String oxType = GTCombustionHelper.getExpectedOxidizerPropertyType(node);
            if (addonId != null && oxType != null) {
                node.getAddons().removeIf(GTAddonCompatibilityHandler::isOxidizerAddon);
                MachineAddon addon = MachineAddonCatalog.getInstance().getAddon(addonId);
                if (addon != null) {
                    node.getAddons().add(addon);
                }
                node.getProperties().set(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE, oxType);
            }
        }
        GTCombustionHelper.syncCombustionInputs(node);
    }

    private void cycleStarTCoolant(RecipeNode node) {
        String cur = node.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE);
        node.getAddons().removeIf(GTAddonCompatibilityHandler::isCoolantAddon);
        if (cur == null || "none".equalsIgnoreCase(cur) || cur.isEmpty()) {
            MachineAddon dist = MachineAddonCatalog.getInstance().getAddon("start_core:distilled_water_coolant");
            if (dist != null) {
                node.getAddons().add(dist);
            }
            node.getProperties().set(GTCEuProperties.COMBUSTION_COOLANT_TYPE, "distilled_water");
        } else if ("distilled_water".equalsIgnoreCase(cur)) {
            MachineAddon deion = MachineAddonCatalog.getInstance().getAddon("start_core:deionized_water_coolant");
            if (deion != null) {
                node.getAddons().add(deion);
            }
            node.getProperties().set(GTCEuProperties.COMBUSTION_COOLANT_TYPE, "deionized_water");
        } else {
            node.getProperties().set(GTCEuProperties.COMBUSTION_COOLANT_TYPE, "none");
        }
        GTCombustionHelper.syncCombustionInputs(node);
    }

    private boolean handleLceBoostClick(MachineConfigDialog dialog, RecipeNode node, int curX, int btnY,
                                       Font font, double mouseX, double mouseY, BoardScreen parent) {
        boolean o2 = GTCombustionHelper.isOxygenBoosted(node);
        String label = (o2 ? "§b💨 " : "§7💨 ") + Component.translatable("gui.gtcalcboard.addon.oxygen_boost").getString() + (o2 ? " §a[ON]" : " §7[OFF]");
        int btnW = Math.max(140, font.width(label) + 12);
        if (mouseX < curX || mouseX > curX + btnW || mouseY < btnY || mouseY > btnY + 16) {
            return false;
        }
        toggleOxygenBoost(node);
        if (dialog != null) dialog.invalidateFilteredCatalog();
        if (parent != null) parent.markSummaryDirty();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
        return true;
    }

    private boolean handleEceBoostClick(MachineConfigDialog dialog, RecipeNode node, int curX, int btnY,
                                       Font font, double mouseX, double mouseY, BoardScreen parent) {
        boolean lox = GTCombustionHelper.isLiquidOxygenBoosted(node);
        String label = (lox ? "§b💨 " : "§7💨 ") + Component.translatable("gui.gtcalcboard.addon.liquid_oxygen_boost").getString() + (lox ? " §a[ON]" : " §7[OFF]");
        int btnW = Math.max(140, font.width(label) + 12);
        if (mouseX < curX || mouseX > curX + btnW || mouseY < btnY || mouseY > btnY + 16) {
            return false;
        }
        toggleLiquidOxygenBoost(node);
        if (dialog != null) dialog.invalidateFilteredCatalog();
        if (parent != null) parent.markSummaryDirty();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
        return true;
    }

    private void clearCombustionBoosts(RecipeNode node) {
        node.getAddons().removeIf(a -> a.getCategory() == MachineAddon.Category.MULTIBLOCK_TRAIT);
        node.getProperties().set(GTCEuProperties.OXYGEN_BOOST, false);
        node.getProperties().set(GTCEuProperties.LIQUID_OXYGEN_BOOST, false);
        node.getProperties().set(GTCEuProperties.COMBUSTION_OXIDIZER_TYPE, "none");
        node.getProperties().set(GTCEuProperties.COMBUSTION_COOLANT_TYPE, "none");
        node.setParallel(1);
        node.setCustomParallel(0);
        GTCombustionHelper.syncCombustionInputs(node);
    }

    private void toggleOxygenBoost(RecipeNode node) {
        boolean cur = GTCombustionHelper.isOxygenBoosted(node);
        if (cur) {
            node.getAddons().removeIf(a -> "gtceu:oxygen_boost".equals(a.getId()));
            node.getProperties().set(GTCEuProperties.OXYGEN_BOOST, false);
        } else {
            MachineAddon addon = MachineAddonCatalog.getInstance().getAddon("gtceu:oxygen_boost");
            if (addon != null) {
                node.getAddons().removeIf(a -> "gtceu:oxygen_boost".equals(a.getId()));
                node.getAddons().add(addon);
            }
            node.getProperties().set(GTCEuProperties.OXYGEN_BOOST, true);
        }
        GTCombustionHelper.syncCombustionInputs(node);
    }

    private void toggleLiquidOxygenBoost(RecipeNode node) {
        boolean cur = GTCombustionHelper.isLiquidOxygenBoosted(node);
        if (cur) {
            node.getAddons().removeIf(a -> "gtceu:liquid_oxygen_boost".equals(a.getId()));
            node.getProperties().set(GTCEuProperties.LIQUID_OXYGEN_BOOST, false);
        } else {
            MachineAddon addon = MachineAddonCatalog.getInstance().getAddon("gtceu:liquid_oxygen_boost");
            if (addon != null) {
                node.getAddons().removeIf(a -> "gtceu:liquid_oxygen_boost".equals(a.getId()));
                node.getAddons().add(addon);
            }
            node.getProperties().set(GTCEuProperties.LIQUID_OXYGEN_BOOST, true);
        }
        GTCombustionHelper.syncCombustionInputs(node);
    }

    public boolean handleDialogHeaderClick(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                           double mouseX, double mouseY, int button, EditBox parallelBox, BoardScreen parent) {
        if (GTCombustionHelper.isCombustionEngine(node)) {
            return handleCombustionDialogHeaderClick(dialog, node, x, y, dialogW, mouseX, mouseY, button, parallelBox, parent);
        }
        if (MachineAddon.isTurbineMachine(node) && node.isMultiblock()) {
            return handleTurbineHeaderClick(dialog, node, x, y, dialogW, mouseX, mouseY, button, parallelBox, parent);
        }
        if (node.isLiquidBoilerRecipe() || (ModAdapterRegistry.getAdapterForNode(node) != null && ModAdapterRegistry.getAdapterForNode(node).isBoilerRecipe(node))) {
            return handleBoilerHeaderClick(node, x, y, dialogW, mouseX, mouseY, parent);
        }
        if (!node.isMultiblock()) {
            return handleSingleBlockHeaderClick(node, x, y, mouseX, mouseY, parent);
        }
        if (GTCEuNodeCardGuiHandler.isFusionMachine(node) || GTCEuNodeCardGuiHandler.isCoilMultiblock(node)) {
            return handleFusionOrCoilHeaderClick(dialog, node, x, y, dialogW, mouseX, mouseY, parent);
        }
        return handleGenericMultiblockHeaderClick(dialog, node, x, y, dialogW, mouseX, mouseY, parent);
    }

    private boolean handleTurbineHeaderClick(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                             double mouseX, double mouseY, int button, EditBox parallelBox, BoardScreen parent) {
        int pmax = GTPowerCalculator.getMaxParallelCapacity(node);
        int pmaxBtnW = 74;
        int pmaxBtnX = x + dialogW - 10 - pmaxBtnW;
        if (mouseX >= pmaxBtnX && mouseX <= pmaxBtnX + pmaxBtnW && mouseY >= y + 28 && mouseY <= y + 42) {
            node.setParallel(pmax);
            if (parallelBox != null) parallelBox.setValue(String.valueOf(pmax));
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }

        int resetBtnW = 54;
        int resetBtnX = pmaxBtnX - 4 - resetBtnW;
        if (mouseX >= resetBtnX && mouseX <= resetBtnX + resetBtnW && mouseY >= y + 28 && mouseY <= y + 42) {
            resetRotorAddons(node);
            if (dialog != null) dialog.invalidateFilteredCatalog();
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }

        int btnY = y + 46;
        int curX = x + 10;
        int gap = 4;

        int holderBtnW = 100;
        if (mouseX >= curX && mouseX <= curX + holderBtnW && mouseY >= btnY && mouseY <= btnY + 16) {
            GTTurbineHelper.cycleRotorHolderTier(node, button == 1 ? -1 : 1);
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        curX += holderBtnW + gap;

        int dynamoTierBtnW = 100;
        if (mouseX >= curX && mouseX <= curX + dynamoTierBtnW && mouseY >= btnY && mouseY <= btnY + 16) {
            GTTurbineHelper.cycleDynamoTier(node, button == 1 ? -1 : 1);
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        curX += dynamoTierBtnW + gap;

        int dynamoAmpsBtnW = 70;
        if (mouseX >= curX && mouseX <= curX + dynamoAmpsBtnW && mouseY >= btnY && mouseY <= btnY + 16) {
            GTTurbineHelper.cycleDynamoAmperage(node, button == 1 ? -1 : 1);
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        curX += dynamoAmpsBtnW + gap;

        var clickAdapter = ModAdapterRegistry.getAdapterForNode(node);
        if (clickAdapter != null && clickAdapter.supportsBoosterControl(node)) {
            int boostBtnW = 110;
            if (mouseX >= curX && mouseX <= curX + boostBtnW && mouseY >= btnY && mouseY <= btnY + 16) {
                clickAdapter.cycleBooster(node, button == 1 ? -1 : 1);
                if (parent != null) parent.markSummaryDirty();
                playClickSound();
                return true;
            }
        }
        return false;
    }

    private boolean handleBoilerHeaderClick(RecipeNode node, int x, int y, int dialogW,
                                            double mouseX, double mouseY, BoardScreen parent) {
        var curTier = com.gtceu.calcboard.api.type.GTBoilerTier.getBoilerTier(node);
        if (curTier.isMultiblock()) {
            if (handleBoilerThrottleClick(node, x, y, dialogW, mouseX, mouseY, parent)) {
                return true;
            }
        }

        var bTiers = com.gtceu.calcboard.api.type.GTBoilerTier.values();
        int btnW = 70;
        int gap = 4;
        int btnY = y + 44;
        for (int i = 0; i < bTiers.length; i++) {
            var bt = bTiers[i];
            int btnX = x + 10 + i * (btnW + gap);
            if (mouseX >= btnX && mouseX <= btnX + btnW && mouseY >= btnY && mouseY <= btnY + 16) {
                node.setMachineIcon(bt.getDefaultIcon(node.isLiquidBoilerRecipe()));
                node.setMultiblock(bt.isMultiblock());
                if (parent != null) parent.markSummaryDirty();
                playClickSound();
                return true;
            }
        }
        return false;
    }

    private boolean handleBoilerThrottleClick(RecipeNode node, int x, int y, int dialogW,
                                              double mouseX, double mouseY, BoardScreen parent) {
        int curThrottle = node.getBoilerThrottle();
        int thrX = x + dialogW - 250;
        String thrTitle = "§e⚡ " + Component.translatable("gui.gtcalcboard.boiler_throttle").getString() + ":";
        int titleW = Minecraft.getInstance().font.width(thrTitle);
        int minusX = thrX + titleW + 6;
        if (mouseX >= minusX && mouseX <= minusX + 14 && mouseY >= y + 28 && mouseY <= y + 40) {
            node.setBoilerThrottle(Math.max(25, curThrottle - 5));
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        int valX = minusX + 16;
        int plusX = valX + 34;
        if (mouseX >= plusX && mouseX <= plusX + 14 && mouseY >= y + 28 && mouseY <= y + 40) {
            node.setBoilerThrottle(Math.min(100, curThrottle + 5));
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        return handleBoilerPresets(node, plusX, mouseX, mouseY, y, parent);
    }

    private boolean handleSingleBlockHeaderClick(RecipeNode node, int x, int y, double mouseX, double mouseY, BoardScreen parent) {
        if (!node.supportsSteamMode()) return false;
        int btnX = x + 10;
        if (mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60) {
            node.setSteamMode(com.gtceu.calcboard.api.type.SteamMode.LOW_PRESSURE);
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        btnX += 116;
        if (mouseX >= btnX && mouseX <= btnX + 110 && mouseY >= y + 44 && mouseY <= y + 60) {
            node.setSteamMode(com.gtceu.calcboard.api.type.SteamMode.HIGH_PRESSURE);
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        btnX += 116;
        if (mouseX >= btnX && mouseX <= btnX + 90 && mouseY >= y + 44 && mouseY <= y + 60) {
            node.setSteamMode(com.gtceu.calcboard.api.type.SteamMode.NONE);
            if (parent != null) parent.markSummaryDirty();
            playClickSound();
            return true;
        }
        return false;
    }

    private boolean handleFusionOrCoilHeaderClick(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                                  double mouseX, double mouseY, BoardScreen parent) {
        if (GTCEuNodeCardGuiHandler.isCoilMultiblock(node)) {
            boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
            int parBtnW = supportsParHatch ? 120 : 0;
            int parBtnX = x + dialogW - 10 - parBtnW;
            if (supportsParHatch && mouseX >= parBtnX && mouseX <= parBtnX + parBtnW && mouseY >= y + 38 && mouseY <= y + 50) {
                return toggleParallelHatch(dialog, node, parent);
            }
        }

        if (mouseY >= y + 36 && mouseY <= y + 50 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
            state.setDraggingHeader(true);
            state.setDraggingRow(1);
            state.setDragStartX(mouseX);
            state.setDragStartScrollX(state.getHeaderRow1ScrollX());
            state.setHasDraggedHeader(false);
            return true;
        }
        if (mouseY >= y + 51 && mouseY <= y + 66 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
            state.setDraggingHeader(true);
            state.setDraggingRow(2);
            state.setDragStartX(mouseX);
            state.setDragStartScrollX(state.getHeaderRow2ScrollX());
            state.setHasDraggedHeader(false);
            return true;
        }
        return false;
    }

    private boolean handleGenericMultiblockHeaderClick(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                                       double mouseX, double mouseY, BoardScreen parent) {
        var mbWorkstations = ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
        if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
            mbWorkstations = List.of(node.getMachineIcon());
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
                if (GTCEuMachineDialogState.getMbControllerScroll() > 0) {
                    GTCEuMachineDialogState.setMbControllerScroll(GTCEuMachineDialogState.getMbControllerScroll() - 1);
                    playClickSound();
                }
                return true;
            }
            curX += navBtnW + 4;
            btnW = (controllersAreaW - 40 - (visibleCount - 1) * 4) / visibleCount;
        } else {
            btnW = (controllersAreaW - (visibleCount - 1) * 4) / Math.max(1, visibleCount);
        }

        int startIdx = showNav ? Math.min(GTCEuMachineDialogState.getMbControllerScroll(), maxScroll) : 0;
        int endIdx = Math.min(totalCount, startIdx + visibleCount);

        for (int i = startIdx; i < endIdx; i++) {
            ResourceLocation mbWs = mbWorkstations.get(i);
            if (mouseX >= curX && mouseX <= curX + btnW && mouseY >= y + 44 && mouseY <= y + 60) {
                applyMultiblockControllerSelection(node, mbWs, dialog, parent);
                return true;
            }
            curX += btnW + 4;
        }

        if (showNav) {
            int navBtnW = 16;
            if (mouseX >= curX && mouseX <= curX + navBtnW && mouseY >= y + 44 && mouseY <= y + 60) {
                if (GTCEuMachineDialogState.getMbControllerScroll() < maxScroll) {
                    GTCEuMachineDialogState.setMbControllerScroll(GTCEuMachineDialogState.getMbControllerScroll() + 1);
                    playClickSound();
                }
                return true;
            }
        }

        if (supportsParHatch && mouseX >= parBtnX && mouseX <= parBtnX + parBtnW && mouseY >= y + 44 && mouseY <= y + 60) {
            return toggleParallelHatch(dialog, node, parent);
        }
        return false;
    }

    public boolean handleDialogHeaderDrag(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                           double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (state.isDraggingHeader()) {
            if (Math.abs(mouseX - state.getDragStartX()) > 2) {
                state.setHasDraggedHeader(true);
            }
            if (state.getDraggingRow() == 1) {
                state.setHeaderRow1ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow1ScrollX(), state.getHeaderRow1ScrollX() - dragX)));
            } else if (state.getDraggingRow() == 2) {
                state.setHeaderRow2ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow2ScrollX(), state.getHeaderRow2ScrollX() - dragX)));
            }
            return true;
        }
        return false;
    }

    public boolean handleDialogHeaderRelease(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                              double mouseX, double mouseY, int button,
                                              EditBox parallelBox, BoardScreen parent) {
        if (state.isDraggingHeader()) {
            boolean wasDragging = state.hasDraggedHeader();
            int row = state.getDraggingRow();
            state.setDraggingHeader(false);
            state.setHasDraggedHeader(false);

            if (!wasDragging) {
                return executeHeaderSelection(dialog, node, x, y, dialogW, mouseX, mouseY, button, parallelBox, parent, row);
            }
            return true;
        }
        return false;
    }

    public boolean handleDialogHeaderScroll(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                             double mouseX, double mouseY, double delta) {
        if (GTCombustionHelper.isCombustionEngine(node)) {
            return false;
        }
        if (MachineAddon.isTurbineMachine(node) && node.isMultiblock()) {
            if (handleTurbineScroll(dialog, node, x, y, mouseX, mouseY, delta)) {
                return true;
            }
        }
        if (GTCEuNodeCardGuiHandler.isFusionMachine(node) || GTCEuNodeCardGuiHandler.isCoilMultiblock(node)) {
            if (mouseY >= y + 36 && mouseY <= y + 50 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
                state.setHeaderRow1ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow1ScrollX(), state.getHeaderRow1ScrollX() - delta * 24.0)));
                return true;
            } else if (mouseY >= y + 51 && mouseY <= y + 66 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
                state.setHeaderRow2ScrollX(Math.max(0, Math.min(state.getMaxHeaderRow2ScrollX(), state.getHeaderRow2ScrollX() - delta * 24.0)));
                return true;
            }
        }
        return handleControllerScroll(node, delta);
    }

    private boolean executeHeaderSelection(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                            double mouseX, double mouseY, int button,
                                            EditBox parallelBox, BoardScreen parent, int row) {
        if (GTCEuNodeCardGuiHandler.isFusionMachine(node)) {
            return executeFusionSelection(dialog, node, x, y, dialogW, mouseX, mouseY, parent, row);
        }
        if (GTCEuNodeCardGuiHandler.isCoilMultiblock(node)) {
            return executeCoilSelection(dialog, node, x, y, dialogW, mouseX, mouseY, parent, row);
        }
        return false;
    }

    private boolean executeFusionSelection(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                           double mouseX, double mouseY, BoardScreen parent, int row) {
        if (row == 1 && mouseY >= y + 38 && mouseY <= y + 50 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
            return selectFusionController(dialog, node, x, mouseX, parent);
        }
        if (row == 2 && mouseY >= y + 52 && mouseY <= y + 64 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
            return selectReflector(dialog, node, x, mouseX, parent);
        }
        return false;
    }

    private boolean selectFusionController(MachineConfigDialog dialog, RecipeNode node, int x, double mouseX, BoardScreen parent) {
        var font = Minecraft.getInstance().font;
        var mbWorkstations = ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
        if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
            mbWorkstations = List.of(node.getMachineIcon());
        }
        double vMouseX = mouseX + state.getHeaderRow1ScrollX();
        int curX = x + 10;
        for (ResourceLocation mbWs : mbWorkstations) {
            String label = GTCEuMachineDialogHeaderRenderer.getMultiblockShortLabel(mbWs);
            int w = Math.max(68, font.width(label) + 12);
            if (vMouseX >= curX && vMouseX <= curX + w) {
                node.setMachineIcon(mbWs);
                GTVoltageTier tier = GTCEuModAdapter.extractVoltageTierFromIcon(mbWs);
                if (tier != null) node.setTargetTier(tier);
                if (dialog != null) dialog.invalidateFilteredCatalog();
                if (parent != null) parent.markSummaryDirty();
                playClickSound();
                return true;
            }
            curX += w + 3;
        }
        return false;
    }

    private boolean selectReflector(MachineConfigDialog dialog, RecipeNode node, int x, double mouseX, BoardScreen parent) {
        var font = Minecraft.getInstance().font;
        var availableReflectorTiers = ReflectorHelper.getAvailableReflectorTiers();
        double vMouseX = mouseX + state.getHeaderRow2ScrollX();
        int rCurX = x + 10;
        for (int t : availableReflectorTiers) {
            String rLabel = (t == 0) ? Component.translatable("gui.gtcalcboard.reflector.none").getString() : ("✦ T" + t);
            int w = Math.max(48, font.width(rLabel) + 12);
            if (vMouseX >= rCurX && vMouseX <= rCurX + w) {
                ReflectorHelper.installReflector(node, t);
                if (dialog != null) dialog.invalidateFilteredCatalog();
                if (parent != null) parent.markSummaryDirty();
                playClickSound();
                return true;
            }
            rCurX += w + 3;
        }
        return false;
    }

    private boolean executeCoilSelection(MachineConfigDialog dialog, RecipeNode node, int x, int y, int dialogW,
                                         double mouseX, double mouseY, BoardScreen parent, int row) {
        boolean supportsParHatch = MultiblockDetector.supportsParallelHatch(node.getMachineIcon(), node.getAvailableWorkstations());
        int parBtnW = supportsParHatch ? 120 : 0;
        int controllersAreaW = supportsParHatch ? (dialogW - 10 - parBtnW - (x + 10) - 6) : (dialogW - 20);

        if (row == 1 && mouseY >= y + 36 && mouseY <= y + 51 && mouseX >= x + 10 && mouseX <= x + 10 + controllersAreaW) {
            return selectCoilController(dialog, node, x, mouseX, parent);
        }
        if (row == 2 && mouseY >= y + 51 && mouseY <= y + 66 && mouseX >= x + 10 && mouseX <= x + dialogW - 10) {
            return selectCoil(dialog, node, x, mouseX, parent);
        }
        return false;
    }

    private boolean selectCoilController(MachineConfigDialog dialog, RecipeNode node, int x, double mouseX, BoardScreen parent) {
        var font = Minecraft.getInstance().font;
        var mbWorkstations = ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
        if (mbWorkstations.isEmpty() && node.getMachineIcon() != null) {
            mbWorkstations = List.of(node.getMachineIcon());
        }
        double vMouseX = mouseX + state.getHeaderRow1ScrollX();
        int curX = x + 10;
        for (ResourceLocation mbWs : mbWorkstations) {
            String label = GTCEuMachineDialogHeaderRenderer.getMultiblockShortLabel(mbWs);
            int w = Math.max(64, font.width(label) + 12);
            if (vMouseX >= curX && vMouseX <= curX + w) {
                applyMultiblockControllerSelection(node, mbWs, dialog, parent);
                return true;
            }
            curX += w + 3;
        }
        return false;
    }

    private boolean selectCoil(MachineConfigDialog dialog, RecipeNode node, int x, double mouseX, BoardScreen parent) {
        var font = Minecraft.getInstance().font;
        var allCoils = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getAllCoils();
        double vMouseX = mouseX + state.getHeaderRow2ScrollX();
        int cCurX = x + 10;
        for (MachineAddon coil : allCoils) {
            String label = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getCoilShortLabel(coil);
            int w = Math.max(54, font.width(label) + 12);
            if (vMouseX >= cCurX && vMouseX <= cCurX + w) {
                com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.installCoil(node, coil);
                if (dialog != null) dialog.invalidateFilteredCatalog();
                if (parent != null) parent.markSummaryDirty();
                playClickSound();
                return true;
            }
            cCurX += w + 3;
        }
        return false;
    }

    private boolean handleTurbineScroll(MachineConfigDialog dialog, RecipeNode node, int x, int y,
                                        double mouseX, double mouseY, double delta) {
        int btnY = y + 46;
        if (mouseY < btnY || mouseY > btnY + 16) return false;

        int dir = delta > 0 ? 1 : -1;
        int curX = x + 10;
        int gap = 4;
        int holderBtnW = 100;
        int dynamoTierBtnW = 100;
        int dynamoAmpsBtnW = 70;
        int boostBtnW = 110;

        if (mouseX >= curX && mouseX <= curX + holderBtnW) {
            GTTurbineHelper.cycleRotorHolderTier(node, dir);
            markParentDirty(dialog);
            playClickSound();
            return true;
        }
        curX += holderBtnW + gap;

        if (mouseX >= curX && mouseX <= curX + dynamoTierBtnW) {
            GTTurbineHelper.cycleDynamoTier(node, dir);
            markParentDirty(dialog);
            playClickSound();
            return true;
        }
        curX += dynamoTierBtnW + gap;

        if (mouseX >= curX && mouseX <= curX + dynamoAmpsBtnW) {
            GTTurbineHelper.cycleDynamoAmperage(node, dir);
            markParentDirty(dialog);
            playClickSound();
            return true;
        }
        curX += dynamoAmpsBtnW + gap;

        var scrollAdapter = ModAdapterRegistry.getAdapterForNode(node);
        if (scrollAdapter != null && scrollAdapter.supportsBoosterControl(node) && mouseX >= curX && mouseX <= curX + boostBtnW) {
            scrollAdapter.cycleBooster(node, dir);
            markParentDirty(dialog);
            playClickSound();
            return true;
        }
        return false;
    }

    public static boolean handleControllerScroll(RecipeNode node, double delta) {
        if (node == null || !node.isMultiblock()) return false;
        List<ResourceLocation> mbWorkstations = ModAdapterRegistry.getAdapterForNode(node).getMultiblockWorkstations(node);
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
            if (GTCEuMachineDialogState.getMbControllerScroll() > 0) {
                GTCEuMachineDialogState.setMbControllerScroll(GTCEuMachineDialogState.getMbControllerScroll() - 1);
                return true;
            }
        } else if (delta < 0) {
            if (GTCEuMachineDialogState.getMbControllerScroll() < maxScroll) {
                GTCEuMachineDialogState.setMbControllerScroll(GTCEuMachineDialogState.getMbControllerScroll() + 1);
                return true;
            }
        }
        return false;
    }

    private static void markParentDirty(MachineConfigDialog dialog) {
        if (dialog != null && dialog.getParent() != null) {
            dialog.getParent().markSummaryDirty();
        }
    }

    private static void playClickSound() {
        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F)
        );
    }

    private void resetRotorAddons(RecipeNode node) {
        List<MachineAddon> rotors = new ArrayList<>();
        for (MachineAddon a : node.getAddons()) {
            if (a.getCategory() == MachineAddon.Category.ROTOR) rotors.add(a);
        }
        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        for (MachineAddon r : rotors) {
            if (adapter != null) adapter.handleUninstallAddon(node, r);
            else node.getAddons().remove(r);
        }
        node.setRotorEfficiency(100);
        node.setRotorPower(100);
        node.setRotorName(null);
    }

    private boolean handleBoilerPresets(RecipeNode node, int plusX, double mouseX, double mouseY, int y, BoardScreen parent) {
        int[] presets = {25, 50, 75, 100};
        int curPreX = plusX + 18;
        for (int pre : presets) {
            int preW = pre == 100 ? 28 : 24;
            if (mouseX >= curPreX && mouseX <= curPreX + preW && mouseY >= y + 28 && mouseY <= y + 40) {
                node.setBoilerThrottle(pre);
                if (parent != null) parent.markSummaryDirty();
                playClickSound();
                return true;
            }
            curPreX += preW + 3;
        }
        return false;
    }

    private boolean toggleParallelHatch(MachineConfigDialog dialog, RecipeNode node, BoardScreen parent) {
        MachineAddon equippedParallel = null;
        for (MachineAddon a : node.getAddons()) {
            if (a != null && a.getCategory() == MachineAddon.Category.PARALLEL) {
                equippedParallel = a;
                break;
            }
        }
        if (equippedParallel != null) {
            var adapter = ModAdapterRegistry.getAdapterForNode(node);
            if (adapter != null) adapter.handleUninstallAddon(node, equippedParallel);
            else node.getAddons().remove(equippedParallel);
        } else if (dialog != null) {
            dialog.setSelectedCategory(MachineAddon.Category.PARALLEL);
        }
        if (dialog != null) {
            dialog.invalidateFilteredCatalog();
        }
        if (parent != null) parent.markSummaryDirty();
        playClickSound();
        return true;
    }

    private void applyMultiblockControllerSelection(RecipeNode node, ResourceLocation mbWs, MachineConfigDialog dialog, BoardScreen parent) {
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
        playClickSound();
    }

}
