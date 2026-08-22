package com.gtceu.calcboard.compat.systeams;

import com.gtceu.calcboard.api.EnergyType;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.ThermalAugmentHelper;
import com.gtceu.calcboard.compat.thermal.ThermalModAdapter;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import dev.emi.emi.api.recipe.EmiRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Handles Systeams recipe adaptation, boiler boiling physics, and steam dynamo statistics.
 */
public class SysteamsRecipeHandler {

    public static boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, EmiRecipeConverter.RecipeDetails details, SysteamsModAdapter adapter) {
        if (!(emiRecipeObj instanceof EmiRecipe recipe)) return false;

        ResourceLocation catId = recipe.getCategory() != null ? recipe.getCategory().getId() : null;
        if (catId == null || !adapter.handlesCategory(catId)) {
            return false;
        }

        if (isSteamDynamo(backing, catId)) {
            return adaptSteamDynamoRecipe(backing, details, catId);
        }

        return adaptBoilerRecipe(backing, details, catId);
    }

    public static boolean isSteamDynamo(Object backing, ResourceLocation catId) {
        if (ThermalAugmentHelper.isDynamoRecipe(backing)) {
            return true;
        }
        if (catId != null && ThermalAugmentHelper.isDynamoItem(catId)) {
            return true;
        }
        return false;
    }

    public static boolean adaptSteamDynamoRecipe(Object backing, EmiRecipeConverter.RecipeDetails details, ResourceLocation catId) {
        long energyRF = ThermalModAdapter.extractEnergyRF(backing);
        if (energyRF <= 0) return false;

        double basePowerRF = getSteamDynamoBasePowerRF(); // 400.0 RF/t
        details.isGenerator = true;
        details.energyType = EnergyType.ELECTRIC_FE;
        details.eut = basePowerRF; // 400.0 RF/t
        details.durationTicks = Math.max(1.0, (double) energyRF / basePowerRF);
        details.tier = GTVoltageTier.LV;
        details.overrideOutputs = false; // Pure energy generator
        return true;
    }

    public static boolean adaptBoilerRecipe(Object backing, EmiRecipeConverter.RecipeDetails details, ResourceLocation catId) {
        long energyRF = ThermalModAdapter.extractEnergyRF(backing);
        if (energyRF <= 0) return false;

        ResourceLocation effectiveCat = catId;
        if (effectiveCat == null || effectiveCat.getPath().contains("boil")) {
            if (backing != null) {
                String bStr = backing.toString().toLowerCase();
                String bCls = backing.getClass().getName().toLowerCase();
                if (bStr.contains("lapidary") || bCls.contains("lapidary")) effectiveCat = ResourceLocation.tryParse("systeams:lapidary");
                else if (bStr.contains("stirling") || bCls.contains("stirling")) effectiveCat = ResourceLocation.tryParse("systeams:stirling");
                else if (bStr.contains("compression") || bCls.contains("compression")) effectiveCat = ResourceLocation.tryParse("systeams:compression");
                else if (bStr.contains("gourmand") || bCls.contains("gourmand")) effectiveCat = ResourceLocation.tryParse("systeams:gourmand");
                else if (bStr.contains("magmatic") || bCls.contains("magmatic")) effectiveCat = ResourceLocation.tryParse("systeams:magmatic");
                else if (bStr.contains("pneumatic") || bCls.contains("pneumatic")) effectiveCat = ResourceLocation.tryParse("systeams:pneumatic");
                else if (bStr.contains("disenchantment") || bCls.contains("disenchantment")) effectiveCat = ResourceLocation.tryParse("systeams:disenchantment");
            }
        }

        double steamRatio = getSteamRatio(effectiveCat);        // Dynamically reflects SysteamsConfig.STEAM_RATIO_*
        double waterToSteamRatio = getWaterToSteamRatio(); // Dynamically reflects BoilingRecipeManager.inToOutRatio()
        double baseSteamPerTick = getBaseSteamPerTick(effectiveCat); // Dynamically reflects basePower * SPEED_* * STEAM_RATIO_*

        double totalSteam = (double) energyRF * steamRatio;
        double totalWater = totalSteam * waterToSteamRatio;

        details.overrideOutputs = true;
        details.customOutputs.clear();
        details.customOutputs.add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", totalSteam));
        details.extraInputs.add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", totalWater));

        details.isGenerator = false;
        details.eut = 0.0; // Self-combustion fluid generator
        details.energyType = EnergyType.HEAT_OR_SELF;
        details.durationTicks = Math.max(1.0, totalSteam / baseSteamPerTick);
        details.tier = GTVoltageTier.LV;

        return true;
    }

    public static double getSteamDynamoBasePowerRF() {
        try {
            Class<?> cfg = Class.forName("chiefarug.mods.systeams.SysteamsConfig");
            Field f = cfg.getDeclaredField("STEAM_DYNAMO_BASE_POWER");
            Object val = f.get(null);
            if (val != null) {
                Method getM = val.getClass().getMethod("get");
                Object res = getM.invoke(val);
                if (res instanceof Number num) {
                    return num.doubleValue();
                }
            }
        } catch (Throwable ignored) {}
        return 400.0; // Default Steam Dynamo power: 400 RF/t = 100 EU/t (MV)
    }

    public static double getSteamRatio(ResourceLocation catId) {
        if (catId != null) {
            String path = catId.getPath().toLowerCase();
            String cleaned = path.replace("_fuel", "").replace("dynamo_", "").replace("_boiler", "").replace("boiler_", "").replace("dynamo", "");
            String fieldName = "STEAM_RATIO_" + cleaned.toUpperCase();
            try {
                Class<?> cfg = Class.forName("chiefarug.mods.systeams.SysteamsConfig");
                Field f = cfg.getDeclaredField(fieldName);
                Object val = f.get(null);
                if (val != null) {
                    var getM = val.getClass().getMethod("get");
                    Object res = getM.invoke(val);
                    if (res instanceof Number num) {
                        return num.doubleValue();
                    }
                }
            } catch (Throwable ignored) {}
        }
        return 0.5; // Official Systeams default: 0.5 mB Steam per RF
    }

    public static double getSpeedMultiplier(ResourceLocation catId) {
        if (catId != null) {
            String path = catId.getPath().toLowerCase();
            String cleaned = path.replace("_fuel", "").replace("dynamo_", "").replace("_boiler", "").replace("boiler_", "").replace("dynamo", "");
            String fieldName = "SPEED_" + cleaned.toUpperCase();
            try {
                Class<?> cfg = Class.forName("chiefarug.mods.systeams.SysteamsConfig");
                Field f = cfg.getDeclaredField(fieldName);
                Object val = f.get(null);
                if (val != null) {
                    var getM = val.getClass().getMethod("get");
                    Object res = getM.invoke(val);
                    if (res instanceof Number num) {
                        return num.doubleValue();
                    }
                }
            } catch (Throwable ignored) {}

            if (cleaned.contains("stirling")) return 5.0;   // SysteamsConfig: 5.0
        }
        return 15.0; // Systeams default speed multiplier for Lapidary, Compression, Gourmand, Magmatic, etc. is 15.0
    }

    public static double getWaterToSteamRatio() {
        try {
            Class<?> brmCls = Class.forName("chiefarug.mods.systeams.recipe.BoilingRecipeManager");
            Method instanceM = brmCls.getMethod("instance");
            Object brm = instanceM.invoke(null);
            if (brm != null) {
                Method getBoiledM = brmCls.getMethod("getBoiledFluid", FluidStack.class);
                Object boiled = getBoiledM.invoke(brm, new FluidStack(Fluids.WATER, 1000));
                if (boiled != null) {
                    Method ratioM = boiled.getClass().getMethod("inToOutRatio");
                    Object res = ratioM.invoke(boiled);
                    if (res instanceof Number num) {
                        return num.doubleValue();
                    }
                }
            }
        } catch (Throwable ignored) {}
        return 0.25; // Standard Systeams Water -> Steam ratio (100 mB Water -> 400 mB Steam)
    }

    public static double getBaseSteamPerTick(ResourceLocation catId) {
        // In CoFH / Systeams bytecode:
        // Boiler baseEnergyPerTick = AugmentableBlockEntity.getBaseProcessTick() (20 RF/t) * SysteamsConfig.SPEED_* (15.0) = 300 RF/t
        // Boiler baseSteamPerTick = baseEnergyPerTick * STEAM_RATIO_* (0.5) = 150 mB/t
        double baseProcessTick = 20.0;
        double speedMult = getSpeedMultiplier(catId);
        double steamRatio = getSteamRatio(catId);
        return Math.max(1.0, baseProcessTick * speedMult * steamRatio);
    }
}
