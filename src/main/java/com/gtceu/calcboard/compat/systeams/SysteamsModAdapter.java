package com.gtceu.calcboard.compat.systeams;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.client.gui.NodeWidget;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * Mod Adapter facade for Thermal Systeams (boilers, steam generation, steam dynamos).
 * Manages boiler recipe parsing, water-to-steam scaling, and steam dynamo conversions.
 */
public class SysteamsModAdapter implements IModAdapter {

    @Override
    public String getModId() {
        return "systeams";
    }

    @Override
    public int getPriority() {
        return 110;
    }

    @Override
    public boolean isLoaded() {
        try {
            Class<?> mlClass = Class.forName("net.minecraftforge.fml.ModList");
            Object ml = mlClass.getMethod("get").invoke(null);
            if (ml != null) {
                return (boolean) mlClass.getMethod("isLoaded", String.class).invoke(ml, "systeams");
            }
        } catch (Throwable ignored) {}
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        String ns = categoryId.getNamespace().toLowerCase();
        if (ns.equals("systeams")) return true;
        if (ns.equals("thermal") && ThermalAugmentHelper.isBoilerItem(categoryId)) return true;
        return false;
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getMachineIcon() != null) {
            String ns = node.getMachineIcon().getNamespace().toLowerCase();
            if (ns.equals("gtceu")) return false;
            if (ns.equals("systeams")) return true;
            if (ns.equals("thermal") && ThermalAugmentHelper.isBoilerItem(node.getMachineIcon())) {
                return true;
            }
        }
        if (node.getRecipeCategoryId() != null && handlesCategory(node.getRecipeCategoryId())) {
            return true;
        }
        return false;
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        return true;
    }

    @Override
    public List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        return List.of(AddonCategory.THERMAL_AUGMENT, AddonCategory.CUSTOM);
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        if (node == null || addon == null) return false;
        if (addon.getCategory().equals(AddonCategory.CUSTOM)) return true;
        return addon.getCategory().equals(AddonCategory.THERMAL_AUGMENT);
    }

    @Override
    public void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        // Systeams uses Thermal Augments
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
        // Enriched through CategoryCapabilityMatrix Thermal dynamo/machine detection
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, EmiRecipeConverter.RecipeDetails details) {
        return SysteamsRecipeHandler.adaptRecipeDetails(emiRecipeObj, backing, details, this);
    }

    public static boolean isSteamDynamo(Object backing, ResourceLocation catId) {
        return SysteamsRecipeHandler.isSteamDynamo(backing, catId);
    }

    public static boolean adaptSteamDynamoRecipe(Object backing, EmiRecipeConverter.RecipeDetails details, ResourceLocation catId) {
        return SysteamsRecipeHandler.adaptSteamDynamoRecipe(backing, details, catId);
    }

    public static boolean adaptBoilerRecipe(Object backing, EmiRecipeConverter.RecipeDetails details, ResourceLocation catId) {
        return SysteamsRecipeHandler.adaptBoilerRecipe(backing, details, catId);
    }

    public static double getSteamDynamoBasePowerRF() {
        return SysteamsRecipeHandler.getSteamDynamoBasePowerRF();
    }

    public static double getSteamRatio(ResourceLocation catId) {
        return SysteamsRecipeHandler.getSteamRatio(catId);
    }

    public static double getSpeedMultiplier(ResourceLocation catId) {
        return SysteamsRecipeHandler.getSpeedMultiplier(catId);
    }

    public static double getWaterToSteamRatio() {
        return SysteamsRecipeHandler.getWaterToSteamRatio();
    }

    public static double getBaseSteamPerTick(ResourceLocation catId) {
        return SysteamsRecipeHandler.getBaseSteamPerTick(catId);
    }

    @Override
    public OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        double scaleFactor = Math.max(1, node.getCombinedParallelMultiplier());
        double powerMult = Math.max(0.01, node.getCombinedEutMultiplier());
        double fuelEnergyMult = Math.max(0.01, node.getCombinedDurationMultiplier());

        if (node.isGenerator()) {
            // Steam Dynamo Generator (Scales EU/t with scaleFactor * powerMult, duration with fuelEnergyMult / (scaleFactor * powerMult))
            double finalEut = node.getBaseEUt() * scaleFactor * powerMult;
            double rawDuration = node.getBaseDurationTicks() * fuelEnergyMult / (scaleFactor * powerMult);
            double finalDuration = Math.max(1.0, rawDuration);
            double batchesPerTick = (rawDuration < 1.0 && rawDuration > 0.0) ? (1.0 / rawDuration) : 1.0;
            return new OverclockMode.OverclockResult(finalDuration, finalEut, batchesPerTick, 0);
        } else {
            // Systeams Boiler (0 EU/t Fluid Producer, duration scales with fuelEnergyMult / (scaleFactor * powerMult))
            double rawDuration = node.getBaseDurationTicks() * fuelEnergyMult / (scaleFactor * powerMult);
            double finalDuration = Math.max(1.0, rawDuration);
            double batchesPerTick = (rawDuration < 1.0 && rawDuration > 0.0) ? (1.0 / rawDuration) : 1.0;
            return new OverclockMode.OverclockResult(finalDuration, 0.0, batchesPerTick, 0);
        }
    }

    @Override
    public int computeEffectiveParallel(RecipeNode node) {
        return Math.max(1, node.getParallel());
    }

    @Override
    public String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        return SysteamsGuiHandler.formatEnergyStats(node, displayMode);
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        return SysteamsGuiHandler.buildEnergyTooltip(node);
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font,
                                   RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                   boolean isGlowing) {
        SysteamsGuiHandler.renderCardControls(graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    @Override
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        return false;
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        return SysteamsGuiHandler.isMachineConfigHovered(node, mouseX, mouseY);
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        return SysteamsGuiHandler.handleControlClick(widget, node, mouseX, mouseY, button);
    }

    @Override
    public double computeEffectiveIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (node == null || stack == null) return defaultRate;
        double fuelEnergyMult = Math.max(0.01, node.getCombinedDurationMultiplier());
        if (isInput && stack.isFluid() && stack.getId() != null && stack.getId().getPath().contains("water")) {
            return defaultRate * fuelEnergyMult;
        }
        if (!isInput && stack.isFluid() && stack.getId() != null && stack.getId().getPath().contains("steam")) {
            return defaultRate * fuelEnergyMult;
        }
        return defaultRate;
    }

    @Override
    public double computeSingleMachineIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (node == null || stack == null) return defaultRate;
        double fuelEnergyMult = Math.max(0.01, node.getCombinedDurationMultiplier());
        if (isInput && stack.isFluid() && stack.getId() != null && stack.getId().getPath().contains("water")) {
            return defaultRate * fuelEnergyMult;
        }
        if (!isInput && stack.isFluid() && stack.getId() != null && stack.getId().getPath().contains("steam")) {
            return defaultRate * fuelEnergyMult;
        }
        return defaultRate;
    }
}
