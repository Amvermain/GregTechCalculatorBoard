package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.ModCompatHelper;
import com.gtceu.calcboard.api.RecipeNode;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public class EmiRecipeConverter {

    public static RecipeNode convert(EmiRecipe recipe) {
        String catName = null;
        if (recipe.getCategory() != null && recipe.getCategory().getId() != null) {
            String catPath = recipe.getCategory().getId().getPath();
            if (!catPath.equals("default") && !catPath.isEmpty()) {
                catName = formatName(catPath);
            }
        }

        String inputItemName = null;
        if (!recipe.getInputs().isEmpty() && !recipe.getInputs().get(0).getEmiStacks().isEmpty()) {
            inputItemName = recipe.getInputs().get(0).getEmiStacks().get(0).getName().getString();
        }

        String rawIdPath = recipe.getId() != null ? recipe.getId().getPath() : "";
        if (rawIdPath.contains("/")) {
            rawIdPath = rawIdPath.substring(rawIdPath.lastIndexOf('/') + 1);
        }

        boolean isHash = rawIdPath.length() > 16 && !rawIdPath.contains("_") && rawIdPath.matches("^[a-zA-Z0-9]+$");

        String name;
        if (catName != null && !catName.isEmpty()) {
            if (inputItemName != null && !inputItemName.isEmpty()) {
                name = catName + " (" + inputItemName + ")";
            } else {
                name = catName;
            }
        } else if (!isHash && !rawIdPath.isEmpty()) {
            name = formatName(rawIdPath);
        } else if (inputItemName != null && !inputItemName.isEmpty()) {
            name = inputItemName + " Recipe";
        } else {
            name = "Recipe";
        }

        double baseDurationTicks = 100.0; // Default 5s
        double baseEUt = 32.0;           // Default LV 32 EU/t
        GTVoltageTier tier = GTVoltageTier.LV;
        boolean isGenerator = false;

        // 1. Try extracting real GTRecipe details (only when GT is loaded)
        try {
            var backing = recipe.getBackingRecipe();
            if (backing != null) {
                // If GT is loaded and it's a GTRecipe
                if (ModCompatHelper.isGTLoaded() && backing.getClass().getName().contains("GTRecipe")) {
                    var durationField = backing.getClass().getField("duration");
                    baseDurationTicks = durationField.getInt(backing);

                    // Check getOutputEUt (GT Generator recipes)
                    try {
                        var getOutputEUtMethod = backing.getClass().getMethod("getOutputEUt");
                        Object outEnergy = getOutputEUtMethod.invoke(backing);
                        if (outEnergy != null) {
                            var voltageMethod = outEnergy.getClass().getMethod("voltage");
                            var amperageMethod = outEnergy.getClass().getMethod("amperage");
                            long voltage = (long) voltageMethod.invoke(outEnergy);
                            long amperage = (long) amperageMethod.invoke(outEnergy);
                            if (voltage > 0) {
                                baseEUt = Math.max(1.0, voltage * Math.max(1L, amperage));
                                tier = GTVoltageTier.getTierForVoltage(voltage);
                                isGenerator = true;
                            }
                        }
                    } catch (Throwable ignored) {}

                    // If not generator, extract input EU/t
                    if (!isGenerator) {
                        try {
                            var getInputEUtMethod = backing.getClass().getMethod("getInputEUt");
                            Object energyStack = getInputEUtMethod.invoke(backing);
                            if (energyStack != null) {
                                var voltageMethod = energyStack.getClass().getMethod("voltage");
                                var amperageMethod = energyStack.getClass().getMethod("amperage");
                                long voltage = (long) voltageMethod.invoke(energyStack);
                                long amperage = (long) amperageMethod.invoke(energyStack);
                                if (voltage > 0) {
                                    baseEUt = Math.max(1.0, voltage * Math.max(1L, amperage));
                                    tier = GTVoltageTier.getTierForVoltage(voltage);
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                } else if (ModCompatHelper.isThermalLoaded() || ModCompatHelper.isModLoaded("thermal_expansion")) {
                    // Thermal Recipe reflection (Energy in RF / FE)
                    long energyRF = 0;
                    try {
                        Method getEnergyMethod = backing.getClass().getMethod("getEnergy");
                        Object res = getEnergyMethod.invoke(backing);
                        if (res instanceof Number num) {
                            energyRF = num.longValue();
                        }
                    } catch (Throwable ignored) {
                        try {
                            Field energyField = backing.getClass().getDeclaredField("energy");
                            energyField.setAccessible(true);
                            Object res = energyField.get(backing);
                            if (res instanceof Number num) {
                                energyRF = num.longValue();
                            }
                        } catch (Throwable ignored2) {}
                    }

                    if (energyRF > 0) {
                        // Standard conversion: 4 RF = 1 EU
                        double totalEU = (double) energyRF / 4.0;
                        baseEUt = 32.0; // Standard LV power
                        baseDurationTicks = Math.max(20.0, totalEU / baseEUt);
                        tier = GTVoltageTier.LV;

                        // Only mark as generator if the category is a Thermal Dynamo
                        ResourceLocation catId = recipe.getCategory() != null ? recipe.getCategory().getId() : null;
                        if (catId != null && catId.getPath().toLowerCase().contains("dynamo")) {
                            isGenerator = true;
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Secondary Generator Check: Strictly check Category ID (Machine type), NEVER check recipe item/output IDs!
        if (!isGenerator && recipe.getCategory() != null && recipe.getCategory().getId() != null) {
            String catPath = recipe.getCategory().getId().getPath().toLowerCase();
            if (catPath.contains("dynamo") || catPath.contains("turbine")
                    || catPath.equals("generator") || catPath.endsWith("_generator")
                    || catPath.equals("combustion_generator") || catPath.equals("semi_fluid_generator")
                    || catPath.equals("gas_turbine") || catPath.equals("steam_turbine") || catPath.equals("plasma_generator")) {
                isGenerator = true;
            }
        }

        RecipeNode node = RecipeNode.create(name, baseDurationTicks, baseEUt, tier);
        node.setGenerator(isGenerator);
        node.setMachineIcon(findMachineIcon(recipe));

        // Convert Inputs
        for (EmiIngredient input : recipe.getInputs()) {
            long reqAmount = input.getAmount();
            float reqChance = input.getChance();
            IngredientStack primaryStack = null;
            List<ResourceLocation> altIds = new ArrayList<>();

            for (EmiStack stack : input.getEmiStacks()) {
                if (stack == null || stack.isEmpty()) continue;
                if (isDummyConditionMarker(stack.getId())) continue;

                long finalAmount = reqAmount > 0 ? reqAmount : stack.getAmount();
                float finalChance = reqChance > 0 ? reqChance : stack.getChance();
                IngredientStack is = convertEmiStack(stack, finalAmount, finalChance);
                if (is != null && is.getId() != null) {
                    if (isDummyConditionMarker(is.getId())) continue;
                    if (primaryStack == null) {
                        primaryStack = is;
                    }
                    if (!altIds.contains(is.getId())) {
                        altIds.add(is.getId());
                    }
                }
            }

            if (primaryStack != null) {
                primaryStack.setAlternatives(altIds);
                node.addInput(primaryStack);
            }
        }

        // Convert Outputs
        for (EmiStack outStack : recipe.getOutputs()) {
            if (outStack == null || outStack.isEmpty()) continue;
            if (isDummyConditionMarker(outStack.getId())) continue;

            IngredientStack os = convertEmiStack(outStack, outStack.getAmount(), outStack.getChance());
            if (os != null && !isDummyConditionMarker(os.getId())) {
                node.addOutput(os);
            }
        }

        return node;
    }

    public static boolean isDummyConditionMarker(ResourceLocation id) {
        if (id == null) return false;
        String path = id.getPath().toLowerCase();

        // Any dummy condition/dimension/planet marker across all mods (gtceu, start_core, kubejs, etc.)
        if (path.endsWith("_marker") || path.endsWith("_marker_item") || path.endsWith("_marker_block")
                || path.contains("dimension_marker") || path.contains("biome_marker")
                || path.contains("planet_marker") || path.contains("environmental_marker")
                || path.contains("altitude_marker") || path.contains("temperature_marker")) {
            return true;
        }

        return false;
    }

    private static ResourceLocation findMachineIcon(EmiRecipe recipe) {
        if (recipe == null) return null;

        // 1. Try getWorkstations
        try {
            Method m = recipe.getClass().getMethod("getWorkstations");
            Object res = m.invoke(recipe);
            if (res instanceof List<?> workstations && !workstations.isEmpty()) {
                for (Object ws : workstations) {
                    if (ws instanceof EmiIngredient ei) {
                        for (EmiStack es : ei.getEmiStacks()) {
                            if (es != null && !es.isEmpty() && es.getId() != null) {
                                return es.getId();
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Try Category ID matching in ForgeRegistries.ITEMS
        if (recipe.getCategory() != null && recipe.getCategory().getId() != null) {
            ResourceLocation catId = recipe.getCategory().getId();
            String ns = catId.getNamespace();
            String path = catId.getPath();

            if (ForgeRegistries.ITEMS.containsKey(catId)) {
                return catId;
            }

            ResourceLocation lvId = ResourceLocation.tryParse(ns + ":lv_" + path);
            if (lvId != null && ForgeRegistries.ITEMS.containsKey(lvId)) {
                return lvId;
            }

            ResourceLocation lvId2 = ResourceLocation.tryParse(ns + ":" + path + "_lv");
            if (lvId2 != null && ForgeRegistries.ITEMS.containsKey(lvId2)) {
                return lvId2;
            }

            ResourceLocation gtLvId = ResourceLocation.tryParse("gtceu:lv_" + path);
            if (gtLvId != null && ForgeRegistries.ITEMS.containsKey(gtLvId)) {
                return gtLvId;
            }

            ResourceLocation gtId = ResourceLocation.tryParse("gtceu:" + path);
            if (gtId != null && ForgeRegistries.ITEMS.containsKey(gtId)) {
                return gtId;
            }
        }

        return null;
    }

    private static IngredientStack convertEmiStack(EmiStack stack, long amount, float chance) {
        if (stack.isEmpty()) return null;

        ResourceLocation id = stack.getId();
        String displayName = stack.getName().getString();

        Object key = stack.getKey();
        if (key instanceof Fluid fluid) {
            ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
            return IngredientStack.fluid(fluidId != null ? fluidId : id, displayName, amount, chance);
        } else if (key != null && key.getClass().getName().contains("FluidStack")) {
            try {
                Method getFluidMethod = key.getClass().getMethod("getFluid");
                Object fl = getFluidMethod.invoke(key);
                if (fl instanceof Fluid fluid) {
                    ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
                    return IngredientStack.fluid(fluidId != null ? fluidId : id, displayName, amount, chance);
                }
            } catch (Throwable ignored) {}
            return IngredientStack.fluid(id, displayName, amount, chance);
        } else if (id != null && ForgeRegistries.FLUIDS.containsKey(id)) {
            return IngredientStack.fluid(id, displayName, amount, chance);
        } else {
            return IngredientStack.item(id, displayName, amount, chance);
        }
    }

    public static String formatName(String raw) {
        if (raw == null || raw.isEmpty()) return "Unknown Machine";
        String[] parts = raw.split("[_\\-.]");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
