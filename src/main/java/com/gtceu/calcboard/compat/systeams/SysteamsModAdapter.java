package com.gtceu.calcboard.compat.systeams;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;

import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
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
            if (ModList.get() != null) {
                return ModList.get().isLoaded("systeams");
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
    public boolean canInstallAddon(RecipeNode node, MachineAddon addon) {
        return ThermalAugmentHelper.canInstallThermalAddon(node, addon, this::isAddonCompatible);
    }

    @Override
    public void onAddonInstalled(RecipeNode node, MachineAddon addon) {
        ThermalAugmentHelper.onThermalAddonInstalled(node, addon);
    }

    @Override
    public void handleInstallAddon(RecipeNode node, MachineAddon addon, boolean shiftClick) {
        ThermalAugmentHelper.handleInstallThermalAddon(node, addon, shiftClick);
    }

    @Override
    public void handleUninstallAddon(RecipeNode node, MachineAddon addon) {
        ThermalAugmentHelper.handleUninstallThermalAddon(node, addon, this::onAddonRemoved);
    }

    @Override
    public void buildAddonTooltip(RecipeNode node, MachineAddon addon, boolean isActiveAddon, List<Component> tooltip) {
        ThermalAugmentHelper.buildThermalAddonTooltip(node, addon, isActiveAddon, tooltip);
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
        if (node == null) return "";
        if (node.isGenerator() || node.getEnergyType() == EnergyType.ELECTRIC_FE) {
            double rfRate = node.getEffectiveTotalEUt();
            String rfStr = node.isGenerator()
                    ? String.format(java.util.Locale.ROOT, "§a+%,.0f RF/t", rfRate)
                    : String.format(java.util.Locale.ROOT, "§e%,.0f RF/t", rfRate);
            if (node.getEfficiency() < 0.999) {
                return String.format(java.util.Locale.ROOT, "§e⚡%.0f%% %s", node.getEfficiency() * 100.0, rfStr);
            }
            return rfStr;
        }
        if (node.getEfficiency() < 0.999) {
            return String.format(java.util.Locale.ROOT, "§e♨%.0f%%", node.getEfficiency() * 100.0);
        }
        double steamRate = 0.0;
        for (var entry : node.calculateEffectiveOutputRates().entrySet()) {
            if (entry.getKey().isFluid() && entry.getKey().getId() != null && entry.getKey().getId().getPath().contains("steam")) {
                steamRate += entry.getValue();
            }
        }
        if (steamRate > 0) {
            return String.format(java.util.Locale.ROOT, "§b♨ +%,.1f/s Steam", steamRate);
        }
        return "§6♨";
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        List<Component> tooltipLines = new java.util.ArrayList<>();
        if (node == null) return tooltipLines;
        if (node.isGenerator() || node.getEnergyType() == EnergyType.ELECTRIC_FE) {
            double totPower = node.getEffectiveTotalEUt();
            tooltipLines.add(Component.literal("§a⚡ " + Component.translatable("gui.gtcalcboard.dynamo_badge").getString()));
            tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Total Generation: §a+%,.2f RF/t §7(§a+%,.2f EU/t eq§7)", totPower, totPower / 4.0)));
            tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            return tooltipLines;
        }
        tooltipLines.add(Component.literal("§6♨ " + Component.translatable("gui.gtcalcboard.boiler_badge").getString()));
        double steamRate = 0.0;
        for (var entry : node.calculateEffectiveOutputRates().entrySet()) {
            if (entry.getKey().isFluid() && entry.getKey().getId() != null && entry.getKey().getId().getPath().contains("steam")) {
                steamRate += entry.getValue();
            }
        }
        if (steamRate > 0) {
            tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Total Steam: §b+%,.2f mB/s §7(§b+%,.2f mB/t§7)", steamRate, steamRate / 20.0)));
        }
        tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        return tooltipLines;
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



