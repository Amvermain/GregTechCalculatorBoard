package com.gtceu.calcboard.compat.thermal;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.AddonFactoryRegistry;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;
import com.gtceu.calcboard.api.util.ModCompatHelper;

import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.thermal.addon.ThermalAugmentAddon;
import com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import java.util.List;

/**
 * Mod Adapter facade for Thermal Series (Thermal Expansion, Foundation, Innovation).
 * Manages Thermal augments, upgrade kit discovery, slot validation, and recipe scaling.
 */
public class ThermalModAdapter implements IModAdapter {

    static {
        ThermalProperties.init();
        com.gtceu.calcboard.api.catalog.AddonFactoryRegistry.register(com.gtceu.calcboard.api.catalog.AddonCategory.THERMAL_AUGMENT, (id, name, desc, icon, tag) -> new com.gtceu.calcboard.compat.thermal.addon.ThermalAugmentAddon(id, name, desc, icon));
    }

    @Override
    public String getModId() {
        return "thermal";
    }

    @Override
    public int getPriority() {
        return 101;
    }

    @Override
    public boolean isLoaded() {
        try {
            if (ModList.get() != null) {
                return ModCompatHelper.isThermalLoaded() || ModList.get().isLoaded("cofh_core");
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
        if (node == null) return false;
        if (node.getMachineIcon() != null && "gtceu".equalsIgnoreCase(node.getMachineIcon().getNamespace())) {
            if (com.gtceu.calcboard.compat.gtceu.physics.GTBoilerPhysics.isBoilerRecipe(node)) {
                return false;
            }
        }
        return ThermalAugmentHelper.isThermalMachine(node);
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
        if (!addon.getCategory().equals(AddonCategory.THERMAL_AUGMENT)) return false;

        if (addon instanceof ThermalAugmentAddon ta) {
            boolean isDynamo = ThermalAugmentHelper.isDynamoNode(node);
            if (ta.getTarget() == ThermalAugmentAddon.AugmentTarget.DYNAMO && !isDynamo) {
                return false;
            }
            if (ta.getTarget() == ThermalAugmentAddon.AugmentTarget.MACHINE && isDynamo) {
                return false;
            }
        }
        return true;
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
    public void onAddonRemoved(RecipeNode node, MachineAddon addon) {
        // Lifecycle hook
    }

    @Override
    public String formatAddonBadge(RecipeNode node, MachineAddon addon) {
        if (addon == null) return "";
        boolean isKit = (addon instanceof ThermalAugmentAddon ta && ta.isUpgradeKit()) || addon.getParallelMultiplier() > 1;
        if (isKit) {
            return String.format("§d⚡%dx", addon.getParallelMultiplier());
        }
        if (addon.getDurationMultiplier() != 1.0 && addon.getEutMultiplier() != 1.0) {
            return String.format("§e⚡%.1f ⏱%.1f", addon.getEutMultiplier(), addon.getDurationMultiplier());
        }
        if (addon.getEutMultiplier() != 1.0) {
            return String.format("§e⚡%.1fx", addon.getEutMultiplier());
        }
        if (addon.getDurationMultiplier() != 1.0) {
            return String.format("§a⏱%.1fx", addon.getDurationMultiplier());
        }
        return "";
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
    public int computeEffectiveParallel(RecipeNode node) {
        return Math.max(1, node.getParallel());
    }

    @Override
    public String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        if (node == null) return "";
        if (node.getEnergyType() == EnergyType.HEAT_OR_SELF) {
            if (node.getEfficiency() < 0.999) {
                return String.format(java.util.Locale.ROOT, "§e♨%.0f%%", node.getEfficiency() * 100.0);
            }
            return "§6♨";
        }
        double rfRate = node.getEffectiveTotalEUt();
        String rfStr = node.isGenerator()
                ? String.format(java.util.Locale.ROOT, "§a+%,.0f RF/t", rfRate)
                : String.format(java.util.Locale.ROOT, "§e%,.0f RF/t", rfRate);
        if (node.getEfficiency() < 0.999) {
            return String.format(java.util.Locale.ROOT, "§e⚡%.0f%% %s", node.getEfficiency() * 100.0, rfStr);
        }
        return rfStr;
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        List<Component> tooltipLines = new java.util.ArrayList<>();
        if (node == null) return tooltipLines;
        double totPower = node.getEffectiveTotalEUt();
        if (node.isGenerator()) {
            tooltipLines.add(Component.literal("§a⚡ " + Component.translatable("gui.gtcalcboard.dynamo_badge").getString()));
            tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Total Generation: §a+%,.2f RF/t §7(§a+%,.2f EU/t eq§7)", totPower, totPower / 4.0)));
        } else {
            tooltipLines.add(Component.literal("§e⚡ " + Component.translatable("gui.gtcalcboard.total_power").getString()));
            tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Total Consumption: §c%,.2f RF/t §7(§c%,.2f EU/t eq§7)", totPower, totPower / 4.0)));
        }
        tooltipLines.add(Component.literal(String.format(java.util.Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
        return tooltipLines;
    }

    @Override
    public EnergyType getEnergyType(RecipeNode node) {
        return EnergyType.ELECTRIC_FE;
    }

    private static final java.util.Set<ResourceLocation> WATER_FLUID_IDS = java.util.Set.of(
            ResourceLocation.tryParse("minecraft:water"),
            ResourceLocation.tryParse("minecraft:flowing_water")
    );
    private static final java.util.Set<ResourceLocation> STEAM_FLUID_IDS = java.util.Set.of(
            ResourceLocation.tryParse("gtceu:steam"),
            ResourceLocation.tryParse("thermal:steam"),
            ResourceLocation.tryParse("systeams:steamier"),
            ResourceLocation.tryParse("systeams:steamiest"),
            ResourceLocation.tryParse("systeams:steamiester"),
            ResourceLocation.tryParse("systeams:steamiestest")
    );

    private static boolean isWaterFluid(ResourceLocation id) {
        if (id == null) return false;
        return WATER_FLUID_IDS.contains(id) || id.getPath().equals("water");
    }

    private static boolean isSteamFluid(ResourceLocation id) {
        if (id == null) return false;
        return STEAM_FLUID_IDS.contains(id) || id.getPath().equals("steam");
    }

    @Override
    public double computeEffectiveIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (node == null || stack == null) return defaultRate;
        double fuelEnergyMult = Math.max(0.01, node.getCombinedDurationMultiplier());
        if (isInput && stack.isFluid() && isWaterFluid(stack.getId())) {
            return defaultRate * fuelEnergyMult;
        }
        if (!isInput && stack.isFluid() && isSteamFluid(stack.getId())) {
            return defaultRate * fuelEnergyMult;
        }
        return defaultRate;
    }

    @Override
    public double computeSingleMachineIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (node == null || stack == null) return defaultRate;
        double fuelEnergyMult = Math.max(0.01, node.getCombinedDurationMultiplier());
        if (isInput && stack.isFluid() && isWaterFluid(stack.getId())) {
            return defaultRate * fuelEnergyMult;
        }
        if (!isInput && stack.isFluid() && isSteamFluid(stack.getId())) {
            return defaultRate * fuelEnergyMult;
        }
        return defaultRate;
    }
}




