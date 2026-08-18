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
                        baseEUt = 32.0; // Standard LV generator output
                        baseDurationTicks = Math.max(20.0, totalEU / baseEUt);
                        tier = GTVoltageTier.LV;
                        isGenerator = true;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Secondary Generator Check: Category / ID keywords (Dynamo, Generator, Turbine, Fuel, Lapidary)
        if (!isGenerator) {
            String checkStr = "";
            if (recipe.getCategory() != null && recipe.getCategory().getId() != null) {
                checkStr += " " + recipe.getCategory().getId().toString();
            }
            if (recipe.getId() != null) {
                checkStr += " " + recipe.getId().toString();
            }
            checkStr = checkStr.toLowerCase();

            if (checkStr.contains("dynamo") || checkStr.contains("generator") || checkStr.contains("turbine")
                    || checkStr.contains("fuel") || checkStr.contains("combustion") || checkStr.contains("lapidary")
                    || checkStr.contains("stirling") || checkStr.contains("compression") || checkStr.contains("magmatic")
                    || checkStr.contains("numismatic") || checkStr.contains("gourmand") || checkStr.contains("disenchantment")) {
                isGenerator = true;
            }
        }

        RecipeNode node = RecipeNode.create(name, baseDurationTicks, baseEUt, tier);
        node.setGenerator(isGenerator);
        node.setMachineIcon(findMachineIcon(recipe));

        // Convert Inputs
        for (EmiIngredient input : recipe.getInputs()) {
            for (EmiStack stack : input.getEmiStacks()) {
                IngredientStack is = convertEmiStack(stack, stack.getAmount(), stack.getChance());
                if (is != null) {
                    node.addInput(is);
                    break;
                }
            }
        }

        // Convert Outputs
        for (EmiStack outStack : recipe.getOutputs()) {
            IngredientStack os = convertEmiStack(outStack, outStack.getAmount(), outStack.getChance());
            if (os != null) {
                node.addOutput(os);
            }
        }

        return node;
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

            ResourceLocation lvId = new ResourceLocation(ns, "lv_" + path);
            if (ForgeRegistries.ITEMS.containsKey(lvId)) {
                return lvId;
            }

            ResourceLocation lvId2 = new ResourceLocation(ns, path + "_lv");
            if (ForgeRegistries.ITEMS.containsKey(lvId2)) {
                return lvId2;
            }

            ResourceLocation gtLvId = new ResourceLocation("gtceu", "lv_" + path);
            if (ForgeRegistries.ITEMS.containsKey(gtLvId)) {
                return gtLvId;
            }

            ResourceLocation gtId = new ResourceLocation("gtceu", path);
            if (ForgeRegistries.ITEMS.containsKey(gtId)) {
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
