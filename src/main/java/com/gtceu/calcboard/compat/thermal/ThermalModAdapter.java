package com.gtceu.calcboard.compat.thermal;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.client.gui.NodeWidget;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * Compatibility adapter facade for Thermal Series (Thermal Expansion, Foundation, Innovation).
 * Delegates to ThermalAddonCrawler, ThermalGuiHandler, and ThermalRecipeHandler.
 */
public class ThermalModAdapter implements IModAdapter {

    @Override
    public String getModId() {
        return "thermal";
    }

    @Override
    public int getPriority() {
        return 100;
    }

    @Override
    public boolean isLoaded() {
        try {
            if (ModList.get() != null) {
                return ModList.get().isLoaded("thermal") || ModList.get().isLoaded("thermal_expansion") || ModList.get().isLoaded("cofh_core");
            }
        } catch (Throwable ignored) {}
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        String ns = categoryId.getNamespace().toLowerCase();
        return ns.equals("thermal") || ns.equals("thermal_expansion") || ns.equals("cofh_core");
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        return ThermalAugmentHelper.isThermalMachine(node);
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        return true;
    }

    @Override
    public List<MachineAddon.Category> getApplicableAddonCategories(RecipeNode node) {
        return List.of(MachineAddon.Category.THERMAL_AUGMENT, MachineAddon.Category.CUSTOM);
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory() == MachineAddon.Category.CUSTOM) return true;
        return addon.getCategory() == MachineAddon.Category.THERMAL_AUGMENT;
    }

    @Override
    public void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        ThermalAddonCrawler.discoverAddons(collector, recipeOutputStacks);
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
        // Enriched through CategoryCapabilityMatrix Thermal dynamo/machine detection
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, EmiRecipeConverter.RecipeDetails details) {
        return ThermalRecipeHandler.adaptRecipeDetails(emiRecipeObj, backing, details);
    }

    public static long extractEnergyRF(Object backing) {
        return ThermalRecipeHandler.extractEnergyRF(backing);
    }

    @Override
    public OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        OverclockMode.OverclockResult baseRes = new OverclockMode.OverclockResult(node.getBaseDurationTicks(), node.getBaseEUt(), 1.0, 0);
        double scaleFactor = Math.max(1, node.getCombinedParallelMultiplier());
        double powerMult = Math.max(0.01, node.getCombinedEutMultiplier());
        double fuelEnergyMult = Math.max(0.01, node.getCombinedDurationMultiplier());

        if (isGenerator) {
            double rawDuration = baseRes.durationTicks() * fuelEnergyMult / (scaleFactor * powerMult);
            double finalDuration = Math.max(1.0, rawDuration);
            double batchesPerTick = (rawDuration < 1.0 && rawDuration > 0.0) ? (1.0 / rawDuration) : 1.0;
            double finalEut = Math.max(1.0, baseRes.eut() * scaleFactor * powerMult);
            return new OverclockMode.OverclockResult(finalDuration, finalEut, batchesPerTick, 0);
        } else {
            double rawDuration = baseRes.durationTicks() / (scaleFactor * node.getCombinedDurationMultiplier());
            double finalDuration = Math.max(1.0, rawDuration);
            double batchesPerTick = (rawDuration < 1.0 && rawDuration > 0.0) ? (1.0 / rawDuration) : 1.0;
            double finalEut = Math.max(1.0, baseRes.eut() * scaleFactor * powerMult);
            return new OverclockMode.OverclockResult(finalDuration, finalEut, batchesPerTick, 0);
        }
    }

    @Override
    public String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        return ThermalGuiHandler.formatEnergyStats(node, displayMode);
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        return ThermalGuiHandler.buildEnergyTooltip(node);
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font,
                                   RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                   boolean isGlowing) {
        ThermalGuiHandler.renderCardControls(graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    @Override
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        return ThermalGuiHandler.isMachineConfigHovered(node, mouseX, mouseY);
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        return ThermalGuiHandler.handleControlClick(widget, node, mouseX, mouseY, button);
    }
}
