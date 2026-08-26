package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.EnergyType;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Handles GTCEu recipe reflection and detail extraction (EUt, duration, voltage tier, ebf temp, steam boilers).
 */
public class GTCEuRecipeHandler {

    public static boolean adaptRecipeDetails(Object emiRecipeObj, Object backing, EmiRecipeConverter.RecipeDetails details) {
        if (backing == null) return false;
        String clName = backing.getClass().getName();
        if (!clName.contains("GTRecipe")) return false;

        ResourceLocation catId = null;
        if (emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe emiRecipe && emiRecipe.getCategory() != null) {
            catId = emiRecipe.getCategory().getId();
        }

        boolean isGTBoiler = false;
        if (catId != null && (catId.getPath().contains("boiler") || catId.getPath().contains("steam_boiler"))) {
            isGTBoiler = true;
        }
        if (!isGTBoiler && emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe er) {
            if (er.getId() != null && er.getId().getPath().contains("boiler")) {
                isGTBoiler = true;
            }
            if (!isGTBoiler && er.getCategory() != null && er.getCategory().getId() != null && er.getCategory().getId().getPath().contains("boiler")) {
                isGTBoiler = true;
            }
        }
        if (!isGTBoiler && backing != null && backing.getClass().getName().contains("GTRecipe")) {
            if (catId != null && catId.getPath().contains("boiler")) {
                isGTBoiler = true;
            }
        }

        if (isGTBoiler) {
            extractGTRecipeDetails(backing, details);
            details.energyType = EnergyType.HEAT_OR_SELF;
            details.isGenerator = false;
            details.eut = 0.0;
            details.tier = GTVoltageTier.ULV;

            // In GTCEu:
            // Solid fuel in Small Bronze Boiler produces 6.0 mB/t steam baseline (120 L/s).
            // Liquid fuel in Small Bronze Boiler produces 15.0 mB/t steam baseline (300 L/s).
            // Both consume Water at 1:160 ratio (1 mB Water = 160 mB Steam).
            boolean isLiquidFuel = false;
            if (backing != null) {
                try {
                    Method mInputs = backing.getClass().getMethod("inputs");
                    Object inMap = mInputs.invoke(backing);
                    if (inMap instanceof Map<?, ?> map) {
                        for (Map.Entry<?, ?> entry : map.entrySet()) {
                            if (entry.getKey() != null && entry.getKey().toString().toLowerCase(Locale.ROOT).contains("fluid")) {
                                if (entry.getValue() instanceof List<?> flList && !flList.isEmpty()) {
                                    isLiquidFuel = true;
                                    break;
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
            if (!isLiquidFuel && emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe emiRecipe) {
                if (emiRecipe.getId() != null) {
                    String rPath = emiRecipe.getId().getPath().toLowerCase(Locale.ROOT);
                    if (rPath.contains("lava") || rPath.contains("diesel") || rPath.contains("creosote") || rPath.contains("liquid") || rPath.contains("ethanol") || rPath.contains("biomass")
                            || (rPath.contains("oil") && !rPath.contains("boiler")) || (rPath.contains("fuel") && !rPath.contains("solid"))) {
                        isLiquidFuel = true;
                    }
                }
                if (!isLiquidFuel && emiRecipe.getInputs() != null) {
                    for (var in : emiRecipe.getInputs()) {
                        if (in != null && in.getEmiStacks() != null) {
                            for (var st : in.getEmiStacks()) {
                                if (st != null) {
                                    if (st.getClass().getName().contains("Fluid") || st.getClass().getSimpleName().contains("Fluid")) {
                                        isLiquidFuel = true;
                                        break;
                                    }
                                    Object key = st.getKey();
                                    if (key instanceof net.minecraft.world.level.material.Fluid || (key != null && key.getClass().getName().contains("Fluid"))) {
                                        isLiquidFuel = true;
                                        break;
                                    }
                                    ResourceLocation sId = st.getId();
                                    if (sId != null) {
                                        if (net.minecraftforge.registries.ForgeRegistries.FLUIDS.containsKey(sId)) {
                                            isLiquidFuel = true;
                                            break;
                                        }
                                        String p = sId.getPath().toLowerCase(Locale.ROOT);
                                        if (p.contains("lava") || p.contains("diesel") || p.contains("creosote") || p.contains("ethanol") || p.contains("biomass")
                                                || (p.contains("oil") && !p.contains("boiler")) || (p.contains("fuel") && !p.contains("solid"))) {
                                            isLiquidFuel = true;
                                            break;
                                        }
                                    }
                                }
                            }
                        }
                        if (isLiquidFuel) break;
                    }
                }
            }

            boolean isLargeBoiler = false;
            if (catId != null && catId.getPath().contains("large_boiler")) {
                isLargeBoiler = true;
            }
            if (!isLargeBoiler && emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe er) {
                if (er.getId() != null && er.getId().getPath().contains("large_boiler")) {
                    isLargeBoiler = true;
                }
                if (!isLargeBoiler && er.getCategory() != null && er.getCategory().getId() != null && er.getCategory().getId().getPath().contains("large_boiler")) {
                    isLargeBoiler = true;
                }
            }
            if (!isLargeBoiler && backing != null && backing.getClass().getName().contains("GTRecipe")) {
                try {
                    Field recipeTypeField = backing.getClass().getField("recipeType");
                    Object rt = recipeTypeField.get(backing);
                    if (rt != null && rt.toString().toLowerCase(Locale.ROOT).contains("large_boiler")) {
                        isLargeBoiler = true;
                    }
                } catch (Throwable ignored) {}
            }

            double baseSteamPerTick;
            if (isLargeBoiler) {
                baseSteamPerTick = 800.0;
            } else {
                baseSteamPerTick = isLiquidFuel ? 15.0 : 6.0;
            }

            double durationTicks = Math.max(1.0, details.durationTicks);
            double totalSteam = baseSteamPerTick * durationTicks;
            double totalWater = totalSteam / 160.0; // 1 mB Water = 160 mB Steam

            details.overrideOutputs = true;
            details.customOutputs.clear();
            details.customOutputs.add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", totalSteam));

            // If input contains a container item (e.g. lava bucket -> bucket), add empty bucket to outputs
            if (emiRecipeObj instanceof dev.emi.emi.api.recipe.EmiRecipe emiRecipe) {
                if (emiRecipe.getInputs() != null) {
                    for (var in : emiRecipe.getInputs()) {
                        if (in != null && in.getEmiStacks() != null) {
                            for (var st : in.getEmiStacks()) {
                                if (st != null && st.getId() != null) {
                                    String itemPath = st.getId().getPath();
                                    if (itemPath.equals("lava_bucket") || itemPath.endsWith("_bucket")) {
                                        details.customOutputs.add(IngredientStack.item(ResourceLocation.tryParse("minecraft:bucket"), "Bucket", 1.0));
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            details.extraInputs.add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", totalWater));
            return true;
        }

        extractGTRecipeDetails(backing, details);
        return true;
    }

    public static void extractGTRecipeDetails(Object backing, EmiRecipeConverter.RecipeDetails details) {
        try {
            var durationField = backing.getClass().getField("duration");
            details.durationTicks = durationField.getInt(backing);

            try {
                var getOutputEUtMethod = backing.getClass().getMethod("getOutputEUt");
                Object outEnergy = getOutputEUtMethod.invoke(backing);
                if (outEnergy != null) {
                    var voltageMethod = outEnergy.getClass().getMethod("voltage");
                    var amperageMethod = outEnergy.getClass().getMethod("amperage");
                    long voltage = (long) voltageMethod.invoke(outEnergy);
                    long amperage = (long) amperageMethod.invoke(outEnergy);
                    if (voltage > 0) {
                        details.eut = Math.max(1.0, voltage * Math.max(1L, amperage));
                        details.tier = GTVoltageTier.getTierForVoltage(voltage);
                        details.energyType = EnergyType.ELECTRIC_EU;
                        details.isGenerator = true;
                    }
                }
            } catch (Throwable ignored) {}

            if (!details.isGenerator) {
                try {
                    var getInputEUtMethod = backing.getClass().getMethod("getInputEUt");
                    Object energyStack = getInputEUtMethod.invoke(backing);
                    if (energyStack != null) {
                        var voltageMethod = energyStack.getClass().getMethod("voltage");
                        var amperageMethod = energyStack.getClass().getMethod("amperage");
                        long voltage = (long) voltageMethod.invoke(energyStack);
                        long amperage = (long) amperageMethod.invoke(energyStack);
                        if (voltage > 0) {
                            details.eut = Math.max(1.0, voltage * Math.max(1L, amperage));
                            details.tier = GTVoltageTier.getTierForVoltage(voltage);
                            details.energyType = EnergyType.ELECTRIC_EU;
                        }
                    }
                } catch (Throwable ignored) {}
            }

            int recipeTemp = 0;
            try {
                Field dataField = backing.getClass().getField("data");
                Object dataObj = dataField.get(backing);
                if (dataObj instanceof CompoundTag tag) {
                    if (tag.contains("ebf_temp")) recipeTemp = tag.getInt("ebf_temp");
                    else if (tag.contains("temp")) recipeTemp = tag.getInt("temp");
                    else if (tag.contains("temperature")) recipeTemp = tag.getInt("temperature");
                }
            } catch (Throwable ignored) {
                try {
                    Method dataMethod = backing.getClass().getMethod("data");
                    Object dataObj = dataMethod.invoke(backing);
                    if (dataObj instanceof CompoundTag tag) {
                        if (tag.contains("ebf_temp")) recipeTemp = tag.getInt("ebf_temp");
                        else if (tag.contains("temp")) recipeTemp = tag.getInt("temp");
                        else if (tag.contains("temperature")) recipeTemp = tag.getInt("temperature");
                    }
                } catch (Throwable ignored2) {}
            }
            if (recipeTemp > 0) {
                details.backingRecipeTemp = recipeTemp;
            }
        } catch (Throwable ignored) {}
    }
}
