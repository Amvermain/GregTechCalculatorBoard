package com.gtceu.calcboard.compat.systeams;

import com.gtceu.calcboard.api.util.ModCompatHelper;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.compat.thermal.ThermalModAdapter;
import com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Handles Systeams recipe adaptation, boiler boiling physics, and steam dynamo statistics.
 */
public class SysteamsRecipeHandler {

    public static boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, EmiRecipeConverter.RecipeDetails details, SysteamsModAdapter adapter) {
        ResourceLocation catId = null;
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            catId = EmiSysteamsHelper.getCategoryId(emiRecipeObj);
        }
        if (catId == null || !adapter.handlesCategory(catId)) {
            return false;
        }

        if (isSteamDynamo(backing, catId)) {
            return adaptSteamDynamoRecipe(backing, details, catId);
        }

        return adaptBoilerRecipe(backing, details, catId);
    }

    private static class EmiSysteamsHelper {
        private static ResourceLocation getCategoryId(Object emiRecipeObj) {
            if (emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe recipe && recipe.getCategory() != null) {
                return recipe.getCategory().getId();
            }
            return null;
        }
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

        double basePowerRF = getSteamDynamoBasePowerRF();
        details.isGenerator = true;
        details.energyType = EnergyType.ELECTRIC_FE;
        details.eut = basePowerRF;
        details.durationTicks = Math.max(1.0, (double) energyRF / basePowerRF);
        details.tier = GTVoltageTier.LV;
        details.overrideOutputs = false;
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

        double steamRatio = getSteamRatio(effectiveCat);
        double waterToSteamRatio = getWaterToSteamRatio();
        double baseSteamPerTick = getBaseSteamPerTick(effectiveCat);

        double totalSteam = (double) energyRF * steamRatio;
        double totalWater = totalSteam * waterToSteamRatio;

        details.overrideOutputs = true;
        details.customOutputs.clear();
        details.customOutputs.add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", totalSteam));
        IngredientStack waterIn = IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", totalWater);
        waterIn.setAlternatives(getAllBoilingFluidInputs());
        details.extraInputs.add(waterIn);

        details.isGenerator = false;
        details.eut = 0.0;
        details.energyType = EnergyType.HEAT_OR_SELF;
        details.durationTicks = Math.max(1.0, totalSteam / baseSteamPerTick);
        details.tier = GTVoltageTier.LV;

        return true;
    }

    public record BoiledFluidResult(ResourceLocation outputFluidId, String outputName, double waterToSteamRatio, double customRatioMult) {}

    public static List<ResourceLocation> getAllBoilingFluidInputs() {
        List<ResourceLocation> list = new java.util.ArrayList<>();
        list.add(ResourceLocation.tryParse("minecraft:water"));

        try {
            for (var sr : com.gtceu.calcboard.client.gui.search.RecipeSearchCacheManager.getGlobalRecipes()) {
                if (sr.categoryId().equals("systeams:boiling") || sr.categoryId().equals("boiling") || "systeams".equals(sr.modId())) {
                    if (sr.inputIds() != null) {
                        for (ResourceLocation inId : sr.inputIds()) {
                            if (inId != null && !list.contains(inId)) {
                                list.add(inId);
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (list.size() <= 1) {
            ResourceLocation distWater = ResourceLocation.tryParse("gtceu:distilled_water");
            ResourceLocation steam = ResourceLocation.tryParse("gtceu:steam");
            if (com.gtceu.calcboard.api.util.ModCompatHelper.isGTLoaded()) {
                if (!list.contains(distWater)) list.add(distWater);
                if (!list.contains(steam)) list.add(steam);
            }
        }

        return list;
    }

    public static BoiledFluidResult getBoiledResult(ResourceLocation inputFluidId, ResourceLocation catId) {
        if (inputFluidId != null) {
            BoiledFluidResult reflResult = extractBoiledResultFromMod(inputFluidId);
            if (reflResult != null) {
                return reflResult;
            }

            try {
                for (var sr : com.gtceu.calcboard.client.gui.search.RecipeSearchCacheManager.getGlobalRecipes()) {
                    if (sr.categoryId().equals("systeams:boiling") || sr.categoryId().equals("boiling") || "systeams".equals(sr.modId())) {
                        boolean inputMatches = false;
                        if (sr.inputIds() != null) {
                            for (ResourceLocation inId : sr.inputIds()) {
                                if (inId != null && (inId.equals(inputFluidId) || inId.getPath().equals(inputFluidId.getPath()))) {
                                    inputMatches = true;
                                    break;
                                }
                            }
                        }
                        if (inputMatches && sr.outputIds() != null && sr.outputIds().length > 0) {
                            ResourceLocation outId = sr.outputIds()[0];
                            String outName = sr.outputNames().length > 0 ? sr.outputNames()[0] : "Steam";
                            double ratio = getWaterToSteamRatio();
                            return new BoiledFluidResult(outId, outName, ratio, 1.0);
                        }
                    }
                }
            } catch (Throwable ignored) {}
        }

        ResourceLocation steamId = ResourceLocation.tryParse("gtceu:steam");
        return new BoiledFluidResult(steamId, "Steam", getWaterToSteamRatio(), 1.0);
    }

    private static BoiledFluidResult extractBoiledResultFromMod(ResourceLocation inputFluidId) {
        try {
            net.minecraft.world.level.material.Fluid fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(inputFluidId);
            if (fluid != null && fluid != Fluids.EMPTY) {
                Class<?> brmCls = Class.forName("chiefarug.mods.systeams.recipe.BoilingRecipeManager");
                Method instanceM = brmCls.getMethod("instance");
                Object brm = instanceM.invoke(null);
                if (brm != null) {
                    Method getBoiledM = brmCls.getMethod("getBoiledFluid", FluidStack.class);
                    Object boiled = getBoiledM.invoke(brm, new FluidStack(fluid, 1000));
                    if (boiled != null) {
                        Method ratioM = boiled.getClass().getMethod("inToOutRatio");
                        Object ratioObj = ratioM.invoke(boiled);
                        double ratio = (ratioObj instanceof Number n) ? n.doubleValue() : 0.25;

                        Method getOutputM = boiled.getClass().getMethod("fluid");
                        Object outFluidObj = getOutputM.invoke(boiled);
                        if (outFluidObj instanceof FluidStack fs && !fs.isEmpty()) {
                            ResourceLocation outId = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(fs.getFluid());
                            String outName = fs.getDisplayName().getString();
                            return new BoiledFluidResult(outId, outName, ratio, 1.0);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static boolean isDynamoToBoilerConvertible(RecipeNode node) {
        if (node == null) return false;
        if (!com.gtceu.calcboard.api.util.ModCompatHelper.isSysteamsLoaded()) return false;
        if (isSteamDynamoNode(node)) return false;

        String type = getDynamoBoilerType(node);
        return type != null && !type.isEmpty();
    }

    public static boolean isSteamDynamoNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("steam_dynamo")) return true;
        if (node.getRecipeCategoryId() != null && (node.getRecipeCategoryId().getPath().equals("steam") || node.getRecipeCategoryId().getPath().contains("steam_dynamo"))) return true;
        if (node.getName() != null && node.getName().toLowerCase().contains("steam dynamo")) return true;
        return false;
    }

    public static String getDynamoBoilerType(RecipeNode node) {
        if (node == null) return null;
        if (node.getMachineIcon() != null) {
            String p = node.getMachineIcon().getPath().toLowerCase();
            if (p.contains("lapidary")) return "lapidary";
            if (p.contains("stirling")) return "stirling";
            if (p.contains("compression")) return "compression";
            if (p.contains("gourmand")) return "gourmand";
            if (p.contains("magmatic")) return "magmatic";
            if (p.contains("pneumatic")) return "pneumatic";
            if (p.contains("disenchantment")) return "disenchantment";
        }
        if (node.getRecipeCategoryId() != null) {
            String p = node.getRecipeCategoryId().getPath().toLowerCase();
            if (p.contains("lapidary")) return "lapidary";
            if (p.contains("stirling")) return "stirling";
            if (p.contains("compression")) return "compression";
            if (p.contains("gourmand")) return "gourmand";
            if (p.contains("magmatic")) return "magmatic";
            if (p.contains("pneumatic")) return "pneumatic";
            if (p.contains("disenchantment")) return "disenchantment";
        }
        if (node.getName() != null) {
            String n = node.getName().toLowerCase();
            if (n.contains("lapidary")) return "lapidary";
            if (n.contains("stirling")) return "stirling";
            if (n.contains("compression")) return "compression";
            if (n.contains("gourmand")) return "gourmand";
            if (n.contains("magmatic")) return "magmatic";
            if (n.contains("pneumatic")) return "pneumatic";
            if (n.contains("disenchantment")) return "disenchantment";
        }
        return null;
    }

    public static void toggleDynamoBoilerMode(RecipeNode node) {
        if (!isDynamoToBoilerConvertible(node)) return;

        boolean isDynamoMode = node.isGenerator() || node.getEnergyType() == EnergyType.ELECTRIC_FE;
        String type = getDynamoBoilerType(node);
        ResourceLocation catRef = ResourceLocation.tryParse("systeams:" + type);

        if (isDynamoMode) {
            double energyRF = node.getProperties().get(com.gtceu.calcboard.compat.thermal.ThermalProperties.THERMAL_BASE_ENERGY_RF);
            if (energyRF <= 0) {
                energyRF = node.getBaseDurationTicks() * node.getBaseEUt();
                if (energyRF <= 0) {
                    energyRF = 200.0 * 20.0;
                }
                node.getProperties().set(com.gtceu.calcboard.compat.thermal.ThermalProperties.THERMAL_BASE_ENERGY_RF, energyRF);
            }

            double steamRatio = getSteamRatio(catRef);
            double baseSteamPerTick = getBaseSteamPerTick(catRef);
            ResourceLocation defFluid = ResourceLocation.tryParse("minecraft:water");
            BoiledFluidResult boiled = getBoiledResult(defFluid, catRef);

            double totalSteam = energyRF * steamRatio * boiled.customRatioMult();
            double totalWater = totalSteam * boiled.waterToSteamRatio();
            double durationTicks = Math.max(1.0, totalSteam / baseSteamPerTick);

            List<IngredientStack> fuelInputs = new java.util.ArrayList<>();
            for (IngredientStack in : node.getInputs()) {
                if (!in.isFluid()) {
                    fuelInputs.add(in);
                }
            }
            node.getInputs().clear();
            node.getInputs().addAll(fuelInputs);

            IngredientStack fluidIn = IngredientStack.fluid(defFluid, "Water", totalWater);
            fluidIn.setAlternatives(getAllBoilingFluidInputs());
            node.addInput(fluidIn);

            node.getOutputs().clear();
            node.addOutput(IngredientStack.fluid(boiled.outputFluidId(), boiled.outputName(), totalSteam));

            node.setMachineIcon(ResourceLocation.tryParse("systeams:" + type + "_boiler"));
            if (node.getRecipeCategoryId() == null || node.getRecipeCategoryId().getPath().equals("boiling")) {
                node.setRecipeCategoryId(ResourceLocation.tryParse("thermal:" + type + "_fuel"));
            }
            node.setGenerator(false);
            node.setEnergyType(EnergyType.HEAT_OR_SELF);
            node.setBaseEUt(0.0);
            node.setBaseDurationTicks(durationTicks);

            if (node.getName() != null && node.getName().contains("Dynamo")) {
                node.setName(node.getName().replace("Dynamo", "Boiler").replace("dynamo", "boiler"));
            }
        } else {
            double energyRF = node.getProperties().get(com.gtceu.calcboard.compat.thermal.ThermalProperties.THERMAL_BASE_ENERGY_RF);
            if (energyRF <= 0) {
                double totalSteam = 0.0;
                for (IngredientStack out : node.getOutputs()) {
                    if (out.isFluid()) totalSteam += out.getAmount();
                }
                double steamRatio = getSteamRatio(catRef);
                energyRF = steamRatio > 0 ? (totalSteam / steamRatio) : 300000.0;
                node.getProperties().set(com.gtceu.calcboard.compat.thermal.ThermalProperties.THERMAL_BASE_ENERGY_RF, energyRF);
            }

            double basePowerRF = ThermalAugmentHelper.getThermalDynamoBasePowerRF(null);
            double durationTicks = Math.max(1.0, energyRF / basePowerRF);

            List<IngredientStack> fuelInputs = new java.util.ArrayList<>();
            for (IngredientStack in : node.getInputs()) {
                if (!in.isFluid()) {
                    fuelInputs.add(in);
                }
            }
            node.getInputs().clear();
            node.getInputs().addAll(fuelInputs);

            node.getOutputs().clear();

            node.setMachineIcon(ResourceLocation.tryParse("thermal:dynamo_" + type));
            node.setRecipeCategoryId(ResourceLocation.tryParse("thermal:" + type + "_fuel"));
            node.setGenerator(true);
            node.setEnergyType(EnergyType.ELECTRIC_FE);
            node.setBaseEUt(basePowerRF);
            node.setBaseDurationTicks(durationTicks);

            if (node.getName() != null && node.getName().contains("Boiler")) {
                node.setName(node.getName().replace("Boiler", "Dynamo").replace("boiler", "dynamo"));
            }
        }
    }

    public static void updateBoilerFluidRecipe(RecipeNode node, ResourceLocation selectedFluidId) {
        if (node == null || selectedFluidId == null) {
            return;
        }

        String type = getDynamoBoilerType(node);
        ResourceLocation catRef = ResourceLocation.tryParse("systeams:" + type);

        double energyRF = node.getProperties().get(com.gtceu.calcboard.compat.thermal.ThermalProperties.THERMAL_BASE_ENERGY_RF);
        if (energyRF <= 0) {
            double totalSteam = 0.0;
            for (IngredientStack out : node.getOutputs()) {
                if (out.isFluid()) totalSteam += out.getAmount();
            }
            double steamRatio = getSteamRatio(catRef);
            energyRF = steamRatio > 0 ? (totalSteam / steamRatio) : 300000.0;
            node.getProperties().set(com.gtceu.calcboard.compat.thermal.ThermalProperties.THERMAL_BASE_ENERGY_RF, energyRF);
        }

        double steamRatio = getSteamRatio(catRef);
        double baseSteamPerTick = getBaseSteamPerTick(catRef);
        BoiledFluidResult boiled = getBoiledResult(selectedFluidId, catRef);

        double totalBoiledFluid = energyRF * steamRatio * boiled.customRatioMult();
        double totalInputFluid = totalBoiledFluid * boiled.waterToSteamRatio();
        double durationTicks = Math.max(1.0, totalBoiledFluid / baseSteamPerTick);

        for (int i = 0; i < node.getInputs().size(); i++) {
            IngredientStack in = node.getInputs().get(i);
            if (in.isFluid()) {
                IngredientStack updatedIn = IngredientStack.fluid(selectedFluidId, "", totalInputFluid);
                updatedIn.setAlternatives(in.getAlternatives());
                updatedIn.selectAlternative(selectedFluidId);
                node.getInputs().set(i, updatedIn);
                break;
            }
        }

        node.getOutputs().clear();
        node.addOutput(IngredientStack.fluid(boiled.outputFluidId(), boiled.outputName(), totalBoiledFluid));
        node.setBaseDurationTicks(durationTicks);
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
        return 400.0;
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
        return 0.5;
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

            if (cleaned.contains("stirling")) return 5.0;
        }
        return 15.0;
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
        return 0.25;
    }

    public static double getBaseSteamPerTick(ResourceLocation catId) {
        double baseProcessTick = 20.0;
        double speedMult = getSpeedMultiplier(catId);
        if (speedMult > 50.0) {
            speedMult = speedMult / 10.0;
        }
        double steamRatio = getSteamRatio(catId);
        return Math.max(1.0, baseProcessTick * speedMult * steamRatio);
    }
}



