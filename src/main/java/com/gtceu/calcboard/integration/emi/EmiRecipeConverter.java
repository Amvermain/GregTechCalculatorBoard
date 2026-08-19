package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.ModCompatHelper;
import com.gtceu.calcboard.api.MultiblockDetector;
import com.gtceu.calcboard.api.RecipeNode;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        int backingRecipeTemp = 0;

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
                        // Store recipe temperature on temporary variable to assign to node
                        backingRecipeTemp = recipeTemp;
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

                        // Mark as generator if category is a Thermal Dynamo or Fuel type
                        ResourceLocation catId = recipe.getCategory() != null ? recipe.getCategory().getId() : null;
                        if (catId != null) {
                            String cp = catId.getPath().toLowerCase();
                            if (cp.contains("dynamo") || cp.contains("fuel") || cp.contains("lapidary") || cp.contains("compression")
                                    || cp.contains("magmatic") || cp.contains("gourmand") || cp.contains("numismatic") || cp.contains("stirling")) {
                                isGenerator = true;
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        // 2. Secondary Generator Check: Strictly check Category ID (Machine type), NEVER check recipe item/output IDs!
        if (!isGenerator && recipe.getCategory() != null && recipe.getCategory().getId() != null) {
            String catPath = recipe.getCategory().getId().getPath().toLowerCase();
            String catNs = recipe.getCategory().getId().getNamespace().toLowerCase();
            if (catPath.contains("dynamo") || catPath.contains("turbine")
                    || catPath.equals("generator") || catPath.endsWith("_generator")
                    || catPath.equals("combustion_generator") || catPath.equals("semi_fluid_generator")
                    || catPath.equals("gas_turbine") || catPath.equals("steam_turbine") || catPath.equals("plasma_generator")
                    || ((catNs.equals("thermal") || catNs.equals("thermal_expansion") || catNs.equals("systeams")) && catPath.contains("fuel"))) {
                isGenerator = true;
            }
        }

        RecipeNode node = RecipeNode.create(name, baseDurationTicks, baseEUt, tier);
        node.setGenerator(isGenerator);
        node.setMachineIcon(findMachineIcon(recipe));
        if (backingRecipeTemp > 0) {
            node.setRecipeTemperature(backingRecipeTemp);
        }

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

        Map<ResourceLocation, Double> tierBoosts = extractTierChanceBoosts(recipe);
        boolean isGT = ModCompatHelper.isGTLoaded() && (recipe.getBackingRecipe() != null && recipe.getBackingRecipe().getClass().getName().contains("GTRecipe"));

        // Convert Outputs
        for (EmiStack outStack : recipe.getOutputs()) {
            if (outStack == null || outStack.isEmpty()) continue;
            if (isDummyConditionMarker(outStack.getId())) continue;

            IngredientStack os = convertEmiStack(outStack, outStack.getAmount(), outStack.getChance());
            if (os != null && !isDummyConditionMarker(os.getId())) {
                if (os.getChance() < 1.0) {
                    if (tierBoosts.containsKey(os.getId())) {
                        os.setTierChanceBoost(tierBoosts.get(os.getId()));
                    } else if (isGT) {
                        // Standard GregTech chanced output default (+5% per tier above base recipe tier)
                        os.setTierChanceBoost(0.05);
                    }
                }
                node.addOutput(os);
            }
        }

        node.setAvailableWorkstations(findAllWorkstations(recipe));

        // If ALL available workstations are multiblock controllers (e.g. EBF, Pyrolyse, Cracking, Fusion, Distillation Tower), default to Multiblock mode
        boolean hasAnySingle = false;
        boolean hasAnyMulti = false;
        for (ResourceLocation ws : node.getAvailableWorkstations()) {
            if (MultiblockDetector.isMultiblock(ws) || RecipeNode.isMultiblockWorkstation(ws)) {
                hasAnyMulti = true;
            } else {
                hasAnySingle = true;
            }
        }
        if (hasAnyMulti && !hasAnySingle) {
            node.setMultiblock(true);
        }
        return node;
    }

    public static List<ResourceLocation> findAllWorkstations(EmiRecipe recipe) {
        List<ResourceLocation> list = new ArrayList<>();
        if (recipe == null) return list;

        // 1. Check recipe.getWorkstations()
        try {
            Method m = recipe.getClass().getMethod("getWorkstations");
            Object res = m.invoke(recipe);
            if (res instanceof List<?> workstations && !workstations.isEmpty()) {
                for (Object ws : workstations) {
                    addEmiIngredientToWorkstations(ws, list);
                }
            }
        } catch (Throwable ignored) {}

        // 2. Check recipe.getCategory() workstations
        try {
            if (recipe.getCategory() != null) {
                try {
                    Method mCat = recipe.getCategory().getClass().getMethod("getWorkstations");
                    Object catRes = mCat.invoke(recipe.getCategory());
                    if (catRes instanceof List<?> catWorkstations && !catWorkstations.isEmpty()) {
                        for (Object ws : catWorkstations) {
                            addEmiIngredientToWorkstations(ws, list);
                        }
                    }
                } catch (Throwable ignored) {}

                try {
                    var rm = dev.emi.emi.api.EmiApi.getRecipeManager();
                    if (rm != null) {
                        var catWs = rm.getWorkstations(recipe.getCategory());
                        if (catWs != null && !catWs.isEmpty()) {
                            for (Object ws : catWs) {
                                addEmiIngredientToWorkstations(ws, list);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        return list;
    }

    private static void addEmiIngredientToWorkstations(Object ws, List<ResourceLocation> list) {
        if (ws instanceof EmiIngredient ei) {
            for (EmiStack es : ei.getEmiStacks()) {
                if (es != null && !es.isEmpty() && es.getId() != null) {
                    if (!list.contains(es.getId())) {
                        list.add(es.getId());
                    }
                }
            }
        }
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

    private static Map<ResourceLocation, Double> extractTierChanceBoosts(EmiRecipe recipe) {
        Map<ResourceLocation, Double> map = new HashMap<>();
        if (recipe == null) return map;
        try {
            Object backing = recipe.getBackingRecipe();
            if (backing != null && ModCompatHelper.isGTLoaded() && backing.getClass().getName().contains("GTRecipe")) {
                Field outputsField = null;
                try {
                    outputsField = backing.getClass().getField("outputs");
                } catch (Throwable ignored) {
                    try {
                        outputsField = backing.getClass().getDeclaredField("outputs");
                        outputsField.setAccessible(true);
                    } catch (Throwable ignored2) {}
                }
                if (outputsField != null) {
                    Object outputsObj = outputsField.get(backing);
                    if (outputsObj instanceof Map<?, ?> outMap) {
                        for (Object listObj : outMap.values()) {
                            if (listObj instanceof List<?> list) {
                                for (Object contentObj : list) {
                                    if (contentObj == null) continue;
                                    double boost = 0.0;
                                    try {
                                        Field f = contentObj.getClass().getField("tierChanceBoost");
                                        Object v = f.get(contentObj);
                                        if (v instanceof Number n) boost = n.doubleValue();
                                    } catch (Throwable ignored) {
                                        try {
                                            Method m = contentObj.getClass().getMethod("tierChanceBoost");
                                            Object v = m.invoke(contentObj);
                                            if (v instanceof Number n) boost = n.doubleValue();
                                        } catch (Throwable ignored2) {
                                            try {
                                                Method m = contentObj.getClass().getMethod("getTierChanceBoost");
                                                Object v = m.invoke(contentObj);
                                                if (v instanceof Number n) boost = n.doubleValue();
                                            } catch (Throwable ignored3) {}
                                        }
                                    }
                                    if (boost > 1.0) {
                                        boost = boost / 10000.0; // e.g. 500 = 5% = 0.05
                                    }
                                    if (boost > 0.0) {
                                        ResourceLocation resId = extractContentResourceId(contentObj);
                                        if (resId != null) {
                                            map.put(resId, boost);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return map;
    }

    private static ResourceLocation extractContentResourceId(Object contentObj) {
        if (contentObj == null) return null;
        try {
            Field f = null;
            try { f = contentObj.getClass().getField("content"); } catch (Throwable ignored) {
                try { f = contentObj.getClass().getDeclaredField("content"); f.setAccessible(true); } catch (Throwable ignored2) {}
            }
            Object inner = f != null ? f.get(contentObj) : null;
            if (inner == null) {
                try {
                    Method m = contentObj.getClass().getMethod("getContent");
                    inner = m.invoke(contentObj);
                } catch (Throwable ignored) {}
            }
            if (inner instanceof net.minecraft.world.item.ItemStack is) {
                return ForgeRegistries.ITEMS.getKey(is.getItem());
            } else if (inner instanceof net.minecraft.world.item.Item it) {
                return ForgeRegistries.ITEMS.getKey(it);
            } else if (inner instanceof Fluid fl) {
                return ForgeRegistries.FLUIDS.getKey(fl);
            } else if (inner != null && inner.getClass().getName().contains("FluidStack")) {
                try {
                    Method gm = inner.getClass().getMethod("getFluid");
                    Object flObj = gm.invoke(inner);
                    if (flObj instanceof Fluid fl) {
                        return ForgeRegistries.FLUIDS.getKey(fl);
                    }
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
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
