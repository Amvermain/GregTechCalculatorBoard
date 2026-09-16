package com.gtceu.calcboard.compat.tfg;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.compat.gtceu.GTCEuProperties;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Thermodynamic physical simulation model for TerraFirmaGreg (TFG) Large Boilers.
 * Covers non-linear water vapor penalty, exponential fuel burn acceleration,
 * 9 booster catalyst fluids, and water quality scaling.
 */
public final class TFGBoilerPhysics {

    public static final ResourceLocation LARGE_BRONZE_BOILER = ResourceLocation.tryParse("tfg:large_bronze_boiler");
    public static final ResourceLocation LARGE_STEEL_BOILER = ResourceLocation.tryParse("tfg:large_steel_boiler");
    public static final ResourceLocation SUPER_BOILER_RECIPE = ResourceLocation.tryParse("gtceu:super_boiler");
    public static final ResourceLocation STANDARD_WATER = ResourceLocation.tryParse("minecraft:water");
    public static final ResourceLocation DISTILLED_WATER = ResourceLocation.tryParse("gtceu:distilled_water");
    public static final ResourceLocation STEAM = ResourceLocation.tryParse("gtceu:steam");

    public static final int LBB_BASE_PRESSURE = 480;
    public static final int LSB_BASE_PRESSURE = 1280;

    public record BoosterFluid(
            int index,
            ResourceLocation fluidId,
            String translationKey,
            double consumptionMbPerSec,
            int pressureBonus,
            int minBoilerPressure,
            String tier
    ) {
        public double consumptionMbPerTick() {
            return consumptionMbPerSec / 20.0;
        }
    }

    private static final List<BoosterFluid> BOOSTERS = List.of(
            new BoosterFluid(0, null, "tfg.multiblock.large_boiler.booster_none", 0.0, 0, 0, "NONE"),
            new BoosterFluid(1, ResourceLocation.tryParse("gtceu:creosote"), "block.gtceu.creosote", 32.0, 300, 0, "ULV"),
            new BoosterFluid(2, ResourceLocation.tryParse("tfg:conifer_pitch"), "material.tfg.conifer_pitch", 5.0, 300, 0, "ULV"),
            new BoosterFluid(3, ResourceLocation.tryParse("afc:maple_sap"), "fluid.afc.maple_sap", 5.0, 300, 0, "ULV"),
            new BoosterFluid(4, ResourceLocation.tryParse("afc:birch_sap"), "fluid.afc.birch_sap", 5.0, 300, 0, "ULV"),
            new BoosterFluid(5, ResourceLocation.tryParse("gtceu:wood_gas"), "material.gtceu.wood_gas", 52.0, 600, 0, "LV"),
            new BoosterFluid(6, ResourceLocation.tryParse("tfc:olive_oil"), "fluid.tfc.olive_oil", 1.0, 600, 0, "ULV"),
            new BoosterFluid(7, ResourceLocation.tryParse("tfg:raw_aromatic_mix"), "material.tfg.raw_aromatic_mix", 300.0, 1200, 1280, "MV"),
            new BoosterFluid(8, ResourceLocation.tryParse("gtceu:rocket_fuel"), "material.gtceu.rocket_fuel", 200.0, 5000, 1280, "HV"),
            new BoosterFluid(9, ResourceLocation.tryParse("tfg:radioactive_effluent"), "material.tfg.radioactive_effluent", 2.0, 16000, 1280, "EV")
    );

    private static final Set<ResourceLocation> BOOSTER_FLUID_IDS = Set.of(
            ResourceLocation.tryParse("gtceu:creosote"),
            ResourceLocation.tryParse("tfg:conifer_pitch"),
            ResourceLocation.tryParse("afc:maple_sap"),
            ResourceLocation.tryParse("afc:birch_sap"),
            ResourceLocation.tryParse("gtceu:wood_gas"),
            ResourceLocation.tryParse("tfc:olive_oil"),
            ResourceLocation.tryParse("tfg:raw_aromatic_mix"),
            ResourceLocation.tryParse("gtceu:rocket_fuel"),
            ResourceLocation.tryParse("tfg:radioactive_effluent")
    );

    static {
        TFGBoilerProperties.init();
    }

    private TFGBoilerPhysics() {}

    public static List<BoosterFluid> getAllBoosters() {
        return Collections.unmodifiableList(BOOSTERS);
    }

    public static BoosterFluid getBooster(int index) {
        if (index < 0 || index >= BOOSTERS.size()) {
            return BOOSTERS.get(0);
        }
        return BOOSTERS.get(index);
    }

    public static boolean isBoosterFluid(ResourceLocation fluidId) {
        return fluidId != null && BOOSTER_FLUID_IDS.contains(fluidId);
    }

    public static boolean isTFGLargeBoiler(RecipeNode node) {
        if (node == null) return false;
        ResourceLocation icon = node.getMachineIcon();
        if (LARGE_BRONZE_BOILER.equals(icon) || LARGE_STEEL_BOILER.equals(icon)) {
            return true;
        }
        if (SUPER_BOILER_RECIPE.equals(node.getRecipeCategoryId())) {
            return true;
        }
        return node.getProperties().get(TFGBoilerProperties.BOOSTER_INDEX) > 0
                || node.getProperties().get(TFGBoilerProperties.WATER_TIER) > 0
                || node.getProperties().get(TFGBoilerProperties.BOILER_MODE) > 0;
    }

    public static boolean isSteelBoiler(RecipeNode node) {
        if (node == null) return false;
        ResourceLocation icon = node.getMachineIcon();
        if (LARGE_BRONZE_BOILER.equals(icon)) return false;
        if (LARGE_STEEL_BOILER.equals(icon)) return true;
        return SUPER_BOILER_RECIPE.equals(node.getRecipeCategoryId());
    }

    public static boolean isSuperBoilerMode(RecipeNode node) {
        if (node == null || !isSteelBoiler(node)) return false;
        if (SUPER_BOILER_RECIPE.equals(node.getRecipeCategoryId())) return true;
        return node.getProperties().get(TFGBoilerProperties.BOILER_MODE) == 1;
    }

    public static int getBasePressure(RecipeNode node) {
        return isSteelBoiler(node) ? LSB_BASE_PRESSURE : LBB_BASE_PRESSURE;
    }

    public static BoosterFluid getActiveBooster(RecipeNode node) {
        if (node == null) return BOOSTERS.get(0);
        int idx = node.getProperties().get(TFGBoilerProperties.BOOSTER_INDEX);
        BoosterFluid booster = getBooster(idx);
        if (booster.minBoilerPressure() > getBasePressure(node)) {
            return BOOSTERS.get(0);
        }
        return booster;
    }

    public static int getEffectivePressure(RecipeNode node) {
        int base = getBasePressure(node);
        BoosterFluid booster = getActiveBooster(node);
        return base + booster.pressureBonus();
    }

    public static double getWaterMultiplier(RecipeNode node) {
        if (node == null) return 1.0;
        int tier = node.getProperties().get(TFGBoilerProperties.WATER_TIER);
        return tier == 1 ? 1.5 : 1.0;
    }

    public static int getThrottle(RecipeNode node) {
        if (node == null) return 100;
        int thr = node.getProperties().get(GTCEuProperties.BOILER_THROTTLE);
        return Math.max(25, Math.min(100, thr));
    }

    public static double getSteamRatePerTick(RecipeNode node) {
        return getEffectivePressure(node) * (getThrottle(node) / 100.0) * getWaterMultiplier(node);
    }

    public static double getSteamRatePerSec(RecipeNode node) {
        return getSteamRatePerTick(node) * 20.0;
    }

    public static double getWaterTempFactor(double effectivePressure) {
        if (effectivePressure <= 480.0) return 1.0;
        double delta = (effectivePressure - 480.0) / 100.0;
        return 1.0 + 0.035 * Math.pow(delta, 1.5);
    }

    public static double getWaterConsumptionPerSec(RecipeNode node) {
        double pEff = getEffectivePressure(node);
        double rWaterBase = (20.0 * pEff * (getThrottle(node) / 100.0)) / 160.0;
        return rWaterBase * getWaterTempFactor(pEff);
    }

    public static double getWaterConsumptionPerTick(RecipeNode node) {
        return getWaterConsumptionPerSec(node) / 20.0;
    }

    public static double getFuelReduction(double effectivePressure) {
        if (effectivePressure <= 480.0) return 0.0;
        return 0.6 * (1.0 - Math.exp(-0.8 * (effectivePressure - 480.0) / 1000.0));
    }

    public static double getFuelEfficiency(RecipeNode node) {
        return (1.0 - getFuelReduction(getEffectivePressure(node))) * 100.0;
    }

    public static double getBoilerSpeedMultiplier(RecipeNode node) {
        double muTemp = 1.0 - getFuelReduction(getEffectivePressure(node));
        double throttleRatio = getThrottle(node) / 100.0;
        return throttleRatio / muTemp;
    }

    public static void cycleBooster(RecipeNode node, int direction) {
        if (node == null) return;
        int basePressure = getBasePressure(node);
        int cur = node.getProperties().get(TFGBoilerProperties.BOOSTER_INDEX);
        int max = basePressure >= LSB_BASE_PRESSURE ? 9 : 6;
        if (cur < 0 || cur > max) {
            cur = 0;
        }
        int next = cur + direction;
        if (next > max) {
            next = 0;
        } else if (next < 0) {
            next = max;
        }
        node.getProperties().set(TFGBoilerProperties.BOOSTER_INDEX, next);
        syncDynamicPorts(node);
    }

    public static void cycleWaterTier(RecipeNode node) {
        if (node == null) return;
        int cur = node.getProperties().get(TFGBoilerProperties.WATER_TIER);
        node.getProperties().set(TFGBoilerProperties.WATER_TIER, cur == 1 ? 0 : 1);
        syncDynamicPorts(node);
    }

    public static void cycleBoilerMode(RecipeNode node) {
        if (node == null) return;
        if (!isSteelBoiler(node)) {
            node.getProperties().set(TFGBoilerProperties.BOILER_MODE, 0);
            return;
        }
        int cur = node.getProperties().get(TFGBoilerProperties.BOILER_MODE);
        int next = cur == 1 ? 0 : 1;
        node.getProperties().set(TFGBoilerProperties.BOILER_MODE, next);
        if (next == 1) {
            node.setRecipeCategoryId(SUPER_BOILER_RECIPE);
        } else {
            node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:large_boiler"));
        }
        syncDynamicPorts(node);
    }

    public static void toggleBoilerTier(RecipeNode node) {
        if (node == null) return;
        boolean currentlySteel = isSteelBoiler(node);
        if (currentlySteel) {
            node.setMachineIcon(LARGE_BRONZE_BOILER);
            node.getProperties().set(TFGBoilerProperties.BOILER_MODE, 0);
            node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:large_boiler"));
            int boosterIdx = node.getProperties().get(TFGBoilerProperties.BOOSTER_INDEX);
            if (boosterIdx >= 7) {
                node.getProperties().set(TFGBoilerProperties.BOOSTER_INDEX, 0);
            }
        } else {
            node.setMachineIcon(LARGE_STEEL_BOILER);
        }
        node.setMultiblock(true);
        syncDynamicPorts(node);
    }

    public static void syncDynamicPorts(RecipeNode node) {
        if (node == null) return;
        node.markPortsDirty();
        node.syncProjectedPorts();
    }

    public static double computeEffectiveIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (node == null || stack == null || stack.getId() == null) {
            return defaultRate;
        }
        if (isInput) {
            if (STANDARD_WATER.equals(stack.getId()) || DISTILLED_WATER.equals(stack.getId())) {
                return node.isOperational() ? getWaterConsumptionPerSec(node) * node.getMachineCount() : 0.0;
            }
            BoosterFluid booster = getActiveBooster(node);
            if (booster != null && booster.index() > 0 && booster.fluidId().equals(stack.getId())) {
                return node.isOperational() ? booster.consumptionMbPerSec() * node.getMachineCount() : 0.0;
            }
            return defaultRate;
        } else {
            if (STEAM.equals(stack.getId())) {
                return node.isOperational() ? getSteamRatePerSec(node) * node.getMachineCount() : 0.0;
            }
            return defaultRate;
        }
    }

    public static double computeSingleMachineIngredientRate(RecipeNode node, IngredientStack stack, boolean isInput, double defaultRate) {
        if (node == null || stack == null || stack.getId() == null) {
            return defaultRate;
        }
        if (isInput) {
            if (STANDARD_WATER.equals(stack.getId()) || DISTILLED_WATER.equals(stack.getId())) {
                return node.isOperational() ? getWaterConsumptionPerSec(node) : 0.0;
            }
            BoosterFluid booster = getActiveBooster(node);
            if (booster != null && booster.index() > 0 && booster.fluidId().equals(stack.getId())) {
                return node.isOperational() ? booster.consumptionMbPerSec() : 0.0;
            }
            return defaultRate;
        } else {
            if (STEAM.equals(stack.getId())) {
                return node.isOperational() ? getSteamRatePerSec(node) : 0.0;
            }
            return defaultRate;
        }
    }

    public static void buildBoosterTooltip(RecipeNode node, List<Component> tooltip) {
        BoosterFluid booster = getActiveBooster(node);
        if (booster.index() <= 0) {
            tooltip.add(Component.translatable("tfg.multiblock.large_boiler.booster_none"));
        } else {
            tooltip.add(Component.translatable("tfg.multiblock.large_boiler.booster_active",
                    Component.translatable(booster.translationKey()),
                    "+" + booster.pressureBonus() + "PU"));
            tooltip.add(Component.literal("§7Booster: §b" + String.format(Locale.ROOT, "%,.0f mB/s", booster.consumptionMbPerSec())
                    + " §8(" + String.format(Locale.ROOT, "%.2f mB/t", booster.consumptionMbPerTick()) + ") " + resolveBoosterName(booster)));
            tooltip.add(Component.literal("§7Required Tier: §e" + booster.tier()));
        }

        int baseP = getBasePressure(node);
        int effP = getEffectivePressure(node);
        double steamRate = getSteamRatePerSec(node);
        double waterRate = getWaterConsumptionPerSec(node);
        double tempFactor = getWaterTempFactor(effP);
        double fuelEff = getFuelEfficiency(node);
        double speedMult = getBoilerSpeedMultiplier(node);
        boolean isDistilled = node != null && node.getProperties().get(TFGBoilerProperties.WATER_TIER) == 1;

        tooltip.add(Component.literal("§8§m------------------------"));
        tooltip.add(Component.literal("§6Thermodynamic Simulation:"));
        tooltip.add(Component.literal(String.format(Locale.ROOT, "§7• Pressure: §f%,dPU §7➔ §e%,dPU %s",
                baseP, effP, booster.index() > 0 ? ("(+" + booster.pressureBonus() + "PU)") : "")));
        tooltip.add(Component.literal(String.format(Locale.ROOT, "§7• Steam Output: §b%,.0f mB/s §8(%,.0f mB/t)%s",
                steamRate, steamRate / 20.0, isDistilled ? " §d[1.5x Boost]" : "")));
        tooltip.add(Component.literal(String.format(Locale.ROOT, "§7• Water Usage: §3%,.1f mB/s §8(%,.1f mB/t) §8[%.2fx]",
                waterRate, waterRate / 20.0, tempFactor)));
        tooltip.add(Component.literal(String.format(Locale.ROOT, "§7• Fuel Efficiency: §a%.1f%% §8(%.2fx burn rate)",
                fuelEff, speedMult)));
        tooltip.add(Component.literal("§8Click to cycle available booster fluids"));
    }

    public static String resolveBoosterName(BoosterFluid booster) {
        if (booster == null || booster.fluidId() == null) return "None";
        return switch (booster.index()) {
            case 1 -> "Creosote";
            case 2 -> "Conifer Pitch";
            case 3 -> "Maple Sap";
            case 4 -> "Birch Sap";
            case 5 -> "Wood Gas";
            case 6 -> "Olive Oil";
            case 7 -> "Raw Aromatic Mix";
            case 8 -> "Rocket Fuel";
            case 9 -> "Radioactive Effluent";
            default -> booster.fluidId().getPath();
        };
    }
}
