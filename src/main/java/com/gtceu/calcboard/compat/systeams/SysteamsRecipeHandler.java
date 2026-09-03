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
import java.util.Map;

/**
 * Handles Systeams recipe adaptation, boiler boiling physics, and steam dynamo statistics.
 */
public class SysteamsRecipeHandler {

    private static final Map<String, ResourceLocation> DYNAMO_BOILER_TYPES = Map.of(
            "lapidary", ResourceLocation.tryParse("systeams:lapidary"),
            "stirling", ResourceLocation.tryParse("systeams:stirling"),
            "compression", ResourceLocation.tryParse("systeams:compression"),
            "gourmand", ResourceLocation.tryParse("systeams:gourmand"),
            "magmatic", ResourceLocation.tryParse("systeams:magmatic"),
            "pneumatic", ResourceLocation.tryParse("systeams:pneumatic"),
            "disenchantment", ResourceLocation.tryParse("systeams:disenchantment")
    );

    private static final Map<ResourceLocation, String> SYSTEAMS_FLUID_NAMES = Map.of(
            ResourceLocation.tryParse("systeams:steamier"), "Warm Steam",
            ResourceLocation.tryParse("systeams:steamiest"), "Hot Steam",
            ResourceLocation.tryParse("systeams:steamiester"), "Superhot Steam",
            ResourceLocation.tryParse("systeams:steamiestest"), "Plasma",
            ResourceLocation.tryParse("minecraft:water"), "Water",
            ResourceLocation.tryParse("gtceu:steam"), "Steam"
    );

    private static final double DEFAULT_STEAM_DYNAMO_BASE_POWER = 400.0;
    private static final double DEFAULT_STEAM_RATIO = 0.5;
    private static final double DEFAULT_SPEED_MULTIPLIER = 15.0;
    private static final double DEFAULT_WATER_TO_STEAM_RATIO = 0.25;

    private static java.util.function.DoubleSupplier steamDynamoBasePowerSupplier = () -> DEFAULT_STEAM_DYNAMO_BASE_POWER;
    private static final Map<String, java.util.function.DoubleSupplier> STEAM_RATIO_SUPPLIERS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<String, java.util.function.DoubleSupplier> SPEED_MULTIPLIER_SUPPLIERS = new java.util.concurrent.ConcurrentHashMap<>();

    private static Object boilingRecipeManagerInstance;
    private static Method boilingRecipeManagerBoilMethod;
    private static Method boiledFluidRatioMethod;
    private static Method boiledFluidOutputMethod;
    private static Method configValueGetMethod;
    private static Class<?> systeamsConfigClass;

    static {
        initReflectionCache();
    }

    private static void initReflectionCache() {
        try {
            systeamsConfigClass = Class.forName("chiefarug.mods.systeams.SysteamsConfig");
            Field basePowerField = systeamsConfigClass.getDeclaredField("STEAM_DYNAMO_BASE_POWER");
            Object basePowerVal = basePowerField.get(null);
            if (basePowerVal != null) {
                configValueGetMethod = basePowerVal.getClass().getMethod("get");
                steamDynamoBasePowerSupplier = () -> {
                    try {
                        Object res = configValueGetMethod.invoke(basePowerVal);
                        if (res instanceof Number n) return n.doubleValue();
                    } catch (Throwable ignored) {}
                    return DEFAULT_STEAM_DYNAMO_BASE_POWER;
                };
            }
        } catch (Throwable ignored) {}

        try {
            Class<?> brmCls = Class.forName("chiefarug.mods.systeams.recipe.BoilingRecipeManager");
            Method instanceM = brmCls.getMethod("instance");
            boilingRecipeManagerInstance = instanceM.invoke(null);
            if (boilingRecipeManagerInstance != null) {
                try {
                    boilingRecipeManagerBoilMethod = brmCls.getMethod("boil", FluidStack.class);
                } catch (NoSuchMethodException e) {
                    boilingRecipeManagerBoilMethod = brmCls.getMethod("getBoiledFluid", FluidStack.class);
                }
            }
        } catch (Throwable ignored) {}
    }

    private static java.util.function.DoubleSupplier resolveConfigFieldSupplier(String fieldName, double defaultVal) {
        if (systeamsConfigClass == null || configValueGetMethod == null) return () -> defaultVal;
        try {
            Field f = systeamsConfigClass.getDeclaredField(fieldName);
            Object val = f.get(null);
            if (val != null) {
                return () -> {
                    try {
                        Object res = configValueGetMethod.invoke(val);
                        if (res instanceof Number n) return n.doubleValue();
                    } catch (Throwable ignored) {}
                    return defaultVal;
                };
            }
        } catch (Throwable ignored) {}
        return () -> defaultVal;
    }

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
        if (effectiveCat == null || effectiveCat.getPath().contains("boil")) { // lint:allow-heuristic: Systeams boiler category discriminator
            effectiveCat = resolveEffectiveBoilerCategory(backing, catId);
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

    private static ResourceLocation resolveEffectiveBoilerCategory(Object backing, ResourceLocation fallbackCatId) {
        if (backing instanceof net.minecraft.world.item.crafting.Recipe<?> recipe) {
            ResourceLocation recipeId = recipe.getId();
            for (Map.Entry<String, ResourceLocation> entry : DYNAMO_BOILER_TYPES.entrySet()) {
                if (recipeId.getPath().contains(entry.getKey())) { // lint:allow-heuristic: Systeams recipe ID naming
                    return entry.getValue();
                }
            }
        }
        if (fallbackCatId != null) {
            for (Map.Entry<String, ResourceLocation> entry : DYNAMO_BOILER_TYPES.entrySet()) {
                if (fallbackCatId.getPath().contains(entry.getKey())) { // lint:allow-heuristic: Systeams category ID naming
                    return entry.getValue();
                }
            }
        }
        return fallbackCatId;
    }

    public record BoiledFluidResult(ResourceLocation outputFluidId, String outputName, double waterToSteamRatio, double customRatioMult) {}

    public static List<ResourceLocation> getAllBoilingFluidInputs() {
        List<ResourceLocation> list = new java.util.ArrayList<>();
        list.add(ResourceLocation.tryParse("minecraft:water"));

        if (com.gtceu.calcboard.api.util.ModCompatHelper.isThermalLoaded()) {
            ResourceLocation steam = ResourceLocation.tryParse("thermal:steam");
            if (!list.contains(steam)) list.add(steam);
        }
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isSysteamsLoaded()) {
            ResourceLocation sysSteam = ResourceLocation.tryParse("systeams:steam");
            ResourceLocation steamier = ResourceLocation.tryParse("systeams:steamier");
            ResourceLocation steamiest = ResourceLocation.tryParse("systeams:steamiest");
            ResourceLocation steamiester = ResourceLocation.tryParse("systeams:steamiester");
            if (!list.contains(sysSteam)) list.add(sysSteam);
            if (!list.contains(steamier)) list.add(steamier);
            if (!list.contains(steamiest)) list.add(steamiest);
            if (!list.contains(steamiester)) list.add(steamiester);
        }

        return list;
    }

    public static BoiledFluidResult getBoiledResult(ResourceLocation inputFluidId, ResourceLocation boilerCatId) {
        if (inputFluidId == null) {
            inputFluidId = ResourceLocation.tryParse("minecraft:water");
        }

        BoiledFluidResult modResult = extractBoiledResultFromMod(inputFluidId);
        if (modResult != null) {
            return modResult;
        }

        if (com.gtceu.calcboard.api.util.ModCompatHelper.isSysteamsLoaded()) {
            String path = inputFluidId.getPath();
            if ("steam".equals(path)) {
                return new BoiledFluidResult(ResourceLocation.tryParse("systeams:steamier"), "Warm Steam", getWaterToSteamRatio(), 1.0);
            }
            if ("steamier".equals(path) || "warm_steam".equals(path)) {
                return new BoiledFluidResult(ResourceLocation.tryParse("systeams:steamiest"), "Hot Steam", getWaterToSteamRatio(), 1.0);
            }
            if ("steamiest".equals(path) || "hot_steam".equals(path)) {
                return new BoiledFluidResult(ResourceLocation.tryParse("systeams:steamiester"), "Superhot Steam", getWaterToSteamRatio(), 1.0);
            }
            if ("steamiester".equals(path) || "superhot_steam".equals(path)) {
                return new BoiledFluidResult(ResourceLocation.tryParse("systeams:steamiestest"), "Plasma", getWaterToSteamRatio(), 1.0);
            }
        }

        ResourceLocation steamId = ResourceLocation.tryParse("gtceu:steam");
        return new BoiledFluidResult(steamId, "Steam", getWaterToSteamRatio(), 1.0);
    }

    private static BoiledFluidResult extractBoiledResultFromMod(ResourceLocation inputFluidId) {
        if (boilingRecipeManagerInstance == null || boilingRecipeManagerBoilMethod == null) return null;
        try {
            net.minecraft.world.level.material.Fluid fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(inputFluidId);
            if (fluid != null && fluid != Fluids.EMPTY) {
                Object boiled = boilingRecipeManagerBoilMethod.invoke(boilingRecipeManagerInstance, new FluidStack(fluid, 1000));
                if (boiled != null) {
                    if (boiledFluidRatioMethod == null) {
                        boiledFluidRatioMethod = boiled.getClass().getMethod("inToOutRatio");
                    }
                    Object ratioObj = boiledFluidRatioMethod.invoke(boiled);
                    double ratio = (ratioObj instanceof Number n) ? n.doubleValue() : DEFAULT_WATER_TO_STEAM_RATIO;

                    if (boiledFluidOutputMethod == null) {
                        try {
                            boiledFluidOutputMethod = boiled.getClass().getMethod("fluidOut");
                        } catch (NoSuchMethodException e) {
                            boiledFluidOutputMethod = boiled.getClass().getMethod("fluid");
                        }
                    }
                    Object outFluidObj = boiledFluidOutputMethod.invoke(boiled);
                    if (outFluidObj instanceof FluidStack fs && !fs.isEmpty()) {
                        ResourceLocation outId = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getKey(fs.getFluid());
                        String outName = fs.getDisplayName().getString();
                        return new BoiledFluidResult(outId, outName, ratio, 1.0);
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static String getFluidDisplayName(ResourceLocation fluidId) {
        if (fluidId == null) return "";
        try {
            var fluid = net.minecraftforge.registries.ForgeRegistries.FLUIDS.getValue(fluidId);
            if (fluid != null && fluid != Fluids.EMPTY) {
                String name = fluid.getFluidType().getDescription().getString();
                if (name != null && !name.isEmpty()) {
                    return name;
                }
            }
        } catch (Throwable ignored) {}

        String staticName = SYSTEAMS_FLUID_NAMES.get(fluidId);
        if (staticName != null) {
            return staticName;
        }
        return fluidId.getPath();
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
        if (node.getMachineIcon() != null && node.getMachineIcon().getPath().contains("steam_dynamo")) return true; // lint:allow-heuristic: Systeams icon name matching
        if (node.getRecipeCategoryId() != null && (node.getRecipeCategoryId().getPath().equals("steam") || node.getRecipeCategoryId().getPath().contains("steam_dynamo"))) return true; // lint:allow-heuristic: Systeams category name matching
        return false;
    }

    public static String getDynamoBoilerType(RecipeNode node) {
        if (node == null) return null;
        if (node.getMachineIcon() != null) {
            String p = node.getMachineIcon().getPath().toLowerCase(java.util.Locale.ROOT);
            for (String type : DYNAMO_BOILER_TYPES.keySet()) {
                if (p.contains(type)) return type; // lint:allow-heuristic: Systeams icon type matching
            }
        }
        if (node.getRecipeCategoryId() != null) {
            String p = node.getRecipeCategoryId().getPath().toLowerCase(java.util.Locale.ROOT);
            for (String type : DYNAMO_BOILER_TYPES.keySet()) {
                if (p.contains(type)) return type;
            }
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

            if (type != null && !type.isEmpty()) {
                String boilerName = Character.toUpperCase(type.charAt(0)) + type.substring(1) + " Boiler";
                node.setName(boilerName);
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

            if (type != null && !type.isEmpty()) {
                String dynamoName = Character.toUpperCase(type.charAt(0)) + type.substring(1) + " Dynamo";
                node.setName(dynamoName);
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
                String inName = getFluidDisplayName(selectedFluidId);
                IngredientStack updatedIn = IngredientStack.fluid(selectedFluidId, inName, totalInputFluid);
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
        return steamDynamoBasePowerSupplier.getAsDouble();
    }

    public static double getSteamRatio(ResourceLocation catId) {
        if (catId == null) return DEFAULT_STEAM_RATIO;
        String path = catId.getPath().toLowerCase(java.util.Locale.ROOT);
        String cleaned = path.replace("_fuel", "").replace("dynamo_", "").replace("_boiler", "").replace("boiler_", "").replace("dynamo", "");
        return STEAM_RATIO_SUPPLIERS.computeIfAbsent(cleaned, k -> {
            String fieldName = "STEAM_RATIO_" + k.toUpperCase(java.util.Locale.ROOT);
            return resolveConfigFieldSupplier(fieldName, DEFAULT_STEAM_RATIO);
        }).getAsDouble();
    }

    public static double getSpeedMultiplier(ResourceLocation catId) {
        if (catId == null) return DEFAULT_SPEED_MULTIPLIER;
        String path = catId.getPath().toLowerCase(java.util.Locale.ROOT);
        String cleaned = path.replace("_fuel", "").replace("dynamo_", "").replace("_boiler", "").replace("boiler_", "").replace("dynamo", "");
        double fallback = cleaned.contains("stirling") ? 5.0 : DEFAULT_SPEED_MULTIPLIER;
        return SPEED_MULTIPLIER_SUPPLIERS.computeIfAbsent(cleaned, k -> {
            String fieldName = "SPEED_" + k.toUpperCase(java.util.Locale.ROOT);
            return resolveConfigFieldSupplier(fieldName, fallback);
        }).getAsDouble();
    }

    public static double getWaterToSteamRatio() {
        BoiledFluidResult boiled = extractBoiledResultFromMod(ResourceLocation.tryParse("minecraft:water"));
        return boiled != null && boiled.waterToSteamRatio() > 0 ? boiled.waterToSteamRatio() : DEFAULT_WATER_TO_STEAM_RATIO;
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



