package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.EnergyType;
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
        return convert(recipe, null);
    }

    public static RecipeNode convert(EmiRecipe recipe, ResourceLocation preferredWorkstation) {
        if (recipe instanceof KineticGenerationEmiRecipe kg) {
            return kg.toRecipeNode();
        }
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
        if (preferredWorkstation != null) {
            String wsName = formatName(preferredWorkstation.getPath());
            if (inputItemName != null && !inputItemName.isEmpty()) {
                name = wsName + " (" + inputItemName + ")";
            } else {
                name = wsName;
            }
        } else if (catName != null && !catName.isEmpty()) {
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

        RecipeDetails details = extractRecipeDetails(recipe, preferredWorkstation);

        RecipeNode node = RecipeNode.create(name, details.durationTicks, details.eut, details.tier);
        node.setGenerator(details.isGenerator);
        if (details.energyType != null && details.energyType != EnergyType.ELECTRIC_EU) {
            node.setEnergyType(details.energyType);
        }
        ResourceLocation catId = recipe.getCategory() != null ? recipe.getCategory().getId() : null;
        if (catId != null) {
            node.setRecipeCategoryId(catId);
        }

        List<ResourceLocation> allWs = findAllWorkstations(recipe);
        node.getAvailableWorkstations().clear();
        for (ResourceLocation ws : allWs) {
            if (ws != null && !isDummyConditionMarker(ws)) {
                node.getAvailableWorkstations().add(ws);
            }
        }
        ResourceLocation icon = preferredWorkstation != null ? preferredWorkstation : findMachineIcon(recipe);
        if (icon != null) {
            node.setMachineIcon(icon);
            if (!node.getAvailableWorkstations().contains(icon)) {
                node.getAvailableWorkstations().add(0, icon);
            }
        } else if (!node.getAvailableWorkstations().isEmpty()) {
            node.setMachineIcon(node.getAvailableWorkstations().get(0));
        }

        // Run Extensible Recipe Property Extractor Pipeline (RFC-002)
        CompoundTag recipeDataTag = null;
        Object backing = recipe.getBackingRecipe();
        if (backing != null) {
            try {
                Field dataField = backing.getClass().getField("data");
                Object dataObj = dataField.get(backing);
                if (dataObj instanceof CompoundTag tag) recipeDataTag = tag;
            } catch (Throwable ignored) {
                try {
                    Method dataMethod = backing.getClass().getMethod("data");
                    Object dataObj = dataMethod.invoke(backing);
                    if (dataObj instanceof CompoundTag tag) recipeDataTag = tag;
                } catch (Throwable ignored2) {}
            }
        }
        com.gtceu.calcboard.api.property.RecipePropertyExtractorPipeline.extractAll(backing, recipeDataTag, catId, node.getProperties());

        if (details.backingRecipeTemp > 0) {
            node.setRecipeTemperature(details.backingRecipeTemp);
        }

        if (node.isFusion()) {
            GTVoltageTier minTier = node.getMinFusionVoltageTier();
            if (node.getTargetTier().ordinal() < minTier.ordinal()) {
                node.setRecipeTier(minTier);
                node.setTargetTier(minTier);
            }
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

        Map<ResourceLocation, Double> baseChances = extractOutputChances(recipe);
        Map<ResourceLocation, Double> tierBoosts = extractTierChanceBoosts(recipe);
        boolean isGT = ModCompatHelper.isGTLoaded() && (recipe.getBackingRecipe() != null && recipe.getBackingRecipe().getClass().getName().contains("GTRecipe"));

        // Convert Outputs
        for (EmiStack outStack : recipe.getOutputs()) {
            if (outStack == null || outStack.isEmpty()) continue;
            if (isDummyConditionMarker(outStack.getId())) continue;

            float chance = outStack.getChance();
            ResourceLocation outId = outStack.getId();
            if (outId != null && baseChances.containsKey(outId)) {
                chance = baseChances.get(outId).floatValue();
            }

            IngredientStack os = convertEmiStack(outStack, outStack.getAmount(), chance);
            if (os != null && !isDummyConditionMarker(os.getId())) {
                if (os.getChance() < 1.0) {
                    if (tierBoosts.containsKey(os.getId())) {
                        os.setTierChanceBoost(tierBoosts.get(os.getId()));
                    } else {
                        // Deductive default: If recipe does not specify a tier chance boost, it does not increase with tier
                        os.setTierChanceBoost(0.0);
                    }
                }
                node.addOutput(os);
            }
        }

        // Apply Composite Recipe Overrides / Additions from Adapters (e.g. Systeams Water + Fuel -> Steam)
        if (details.overrideOutputs && !details.customOutputs.isEmpty()) {
            node.getOutputs().clear();
            for (IngredientStack cos : details.customOutputs) {
                node.addOutput(cos.copy());
            }
        }
        for (IngredientStack ein : details.extraInputs) {
            node.addInput(ein.copy());
        }
        for (IngredientStack eout : details.extraOutputs) {
            node.addOutput(eout.copy());
        }

        if (catId != null) {
            node.setRecipeCategoryId(catId);
            com.gtceu.calcboard.api.CategoryCapability cap = com.gtceu.calcboard.api.CategoryCapabilityMatrix.getInstance().getCapability(catId);
            if (cap != null && !cap.availableWorkstations().isEmpty()) {
                node.setAvailableWorkstations(new ArrayList<>(cap.availableWorkstations()));
                if (cap.hasMultiblockOption() && !cap.hasSingleblockOption()) {
                    node.setMultiblock(true);
                }
            }
        }

        if (node.getAvailableWorkstations().isEmpty()) {
            node.setAvailableWorkstations(findAllWorkstations(recipe));
        }

        // If ALL available workstations are multiblock controllers, default to Multiblock mode
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
        node.autoCalculateTurbineParallel();
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

        // 1. Try all workstations from recipe and category
        List<ResourceLocation> allWs = findAllWorkstations(recipe);
        for (ResourceLocation ws : allWs) {
            if (ws != null && !isDummyConditionMarker(ws)) {
                return ws;
            }
        }

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

    private static Map<ResourceLocation, Double> extractOutputChances(EmiRecipe recipe) {
        Map<ResourceLocation, Double> map = new HashMap<>();
        if (recipe == null) return map;
        try {
            Object backing = recipe.getBackingRecipe();
            if (backing == null) return map;

            // 1. Create ProcessingRecipe (e.g. Fan Washing, Crushing, Milling, Cutting, etc.)
            try {
                Method m = backing.getClass().getMethod("getRollableResults");
                Object res = m.invoke(backing);
                if (res instanceof List<?> list) {
                    for (Object po : list) {
                        if (po == null) continue;
                        Method getStackM = po.getClass().getMethod("getStack");
                        Method getChanceM = po.getClass().getMethod("getChance");
                        Object stackObj = getStackM.invoke(po);
                        Object chanceObj = getChanceM.invoke(po);
                        if (stackObj instanceof net.minecraft.world.item.ItemStack is && chanceObj instanceof Number n) {
                            ResourceLocation id = ForgeRegistries.ITEMS.getKey(is.getItem());
                            if (id != null) {
                                map.put(id, n.doubleValue());
                            }
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 2. GTCEu GTRecipe output chances
            if (ModCompatHelper.isGTLoaded() && backing.getClass().getName().contains("GTRecipe")) {
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
                                    double chance = 1.0;
                                    try {
                                        Field f = contentObj.getClass().getField("chance");
                                        Object v = f.get(contentObj);
                                        if (v instanceof Number n) chance = n.doubleValue();
                                    } catch (Throwable ignored) {
                                        try {
                                            Method m = contentObj.getClass().getMethod("chance");
                                            Object v = m.invoke(contentObj);
                                            if (v instanceof Number n) chance = n.doubleValue();
                                        } catch (Throwable ignored2) {
                                            try {
                                                Method m = contentObj.getClass().getMethod("getChance");
                                                Object v = m.invoke(contentObj);
                                                if (v instanceof Number n) chance = n.doubleValue();
                                            } catch (Throwable ignored3) {}
                                        }
                                    }
                                    if (chance > 1.0) {
                                        chance = chance / 10000.0; // GTCEu uses 10000 = 100%
                                    }
                                    ResourceLocation resId = extractContentResourceId(contentObj);
                                    if (resId != null) {
                                        map.put(resId, Math.max(0.0, Math.min(1.0, chance)));
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
                                    ResourceLocation resId = extractContentResourceId(contentObj);
                                    if (resId != null) {
                                        map.put(resId, Math.max(0.0, boost));
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
            Object inner = contentObj;
            for (int depth = 0; depth < 5 && inner != null; depth++) {
                if (inner instanceof net.minecraft.world.item.ItemStack is) {
                    return is.isEmpty() ? null : ForgeRegistries.ITEMS.getKey(is.getItem());
                } else if (inner instanceof net.minecraft.world.item.Item it) {
                    return ForgeRegistries.ITEMS.getKey(it);
                } else if (inner instanceof net.minecraft.world.item.crafting.Ingredient ing) {
                    net.minecraft.world.item.ItemStack[] items = ing.getItems();
                    if (items != null && items.length > 0 && !items[0].isEmpty()) {
                        return ForgeRegistries.ITEMS.getKey(items[0].getItem());
                    }
                } else if (inner instanceof Fluid fl) {
                    return ForgeRegistries.FLUIDS.getKey(fl);
                } else if (inner instanceof net.minecraft.world.item.ItemStack[] arr) {
                    if (arr.length > 0 && !arr[0].isEmpty()) {
                        return ForgeRegistries.ITEMS.getKey(arr[0].getItem());
                    }
                } else if (inner instanceof List<?> list && !list.isEmpty()) {
                    inner = list.get(0);
                    continue;
                }

                Object next = null;
                String clName = inner.getClass().getName();
                if (clName.contains("FluidStack")) {
                    try {
                        Method gm = inner.getClass().getMethod("getFluid");
                        Object flObj = gm.invoke(inner);
                        if (flObj instanceof Fluid fl) {
                            return ForgeRegistries.FLUIDS.getKey(fl);
                        }
                    } catch (Throwable ignored) {}
                }

                for (String mName : new String[]{"getContent", "getInner", "getStack", "getItems", "getMatchingStacks", "getItemStack", "getFluid", "getRawFluid", "getIngredient"}) {
                    try {
                        Method m = inner.getClass().getMethod(mName);
                        next = m.invoke(inner);
                        if (next != null && next != inner) break;
                    } catch (Throwable ignored) {}
                }
                if (next == null) {
                    for (String fName : new String[]{"content", "inner", "stack", "itemStack", "ingredient", "fluid"}) {
                        try {
                            Field f = null;
                            try { f = inner.getClass().getField(fName); } catch (Throwable ignored) {
                                f = inner.getClass().getDeclaredField(fName);
                                f.setAccessible(true);
                            }
                            if (f != null) {
                                next = f.get(inner);
                                if (next != null && next != inner) break;
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                if (next == null || next == inner) break;
                inner = next;
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

    public static class RecipeDetails {
        public double durationTicks = 20.0;
        public double eut = 0.0;
        public GTVoltageTier tier = GTVoltageTier.ULV;
        public boolean isGenerator = false;
        public com.gtceu.calcboard.api.EnergyType energyType = com.gtceu.calcboard.api.EnergyType.NONE;
        public int backingRecipeTemp = 0;
        public List<IngredientStack> extraInputs = new ArrayList<>();
        public List<IngredientStack> extraOutputs = new ArrayList<>();
        public boolean overrideOutputs = false;
        public List<IngredientStack> customOutputs = new ArrayList<>();
    }

    public static RecipeDetails extractRecipeDetails(EmiRecipe recipe, ResourceLocation preferredWorkstation) {
        RecipeDetails details = new RecipeDetails();
        try {
            var backing = recipe.getBackingRecipe();
            ResourceLocation catId = recipe.getCategory() != null ? recipe.getCategory().getId() : null;

            if (preferredWorkstation != null && preferredWorkstation.getNamespace().equals("gtceu")) {
                com.gtceu.calcboard.compat.IModAdapter gtAdapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForModId("gtceu");
                if (gtAdapter != null && gtAdapter.adaptRecipeDetails(recipe, backing, details)) {
                    return details;
                }
            } else if (preferredWorkstation != null && preferredWorkstation.getNamespace().equals("systeams")) {
                if (com.gtceu.calcboard.compat.systeams.SysteamsModAdapter.adaptBoilerRecipe(backing, details, catId)) {
                    return details;
                }
            }

            // Route through ModAdapterRegistry
            com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForCategory(catId);
            boolean handled = adapter.adaptRecipeDetails(recipe, backing, details);

            if (!handled && backing != null) {
                if (ModCompatHelper.isGTLoaded() && backing.getClass().getName().contains("GTRecipe")) {
                    extractGTRecipeDetails(backing, details);
                } else {
                    for (com.gtceu.calcboard.compat.IModAdapter a : com.gtceu.calcboard.compat.ModAdapterRegistry.getAllLoadedAdapters()) {
                        if (a != adapter && a.adaptRecipeDetails(recipe, backing, details)) {
                            handled = true;
                            break;
                        }
                    }
                    if (!handled) {
                        if (backing instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe acr) {
                            details.durationTicks = acr.getCookingTime();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (!details.isGenerator && recipe.getCategory() != null && recipe.getCategory().getId() != null) {
            details.isGenerator = isGeneratorCategory(recipe.getCategory().getId());
        }

        return details;
    }

    private static void extractGTRecipeDetails(Object backing, RecipeDetails details) {
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
                        details.energyType = com.gtceu.calcboard.api.EnergyType.ELECTRIC_EU;
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
                            details.energyType = com.gtceu.calcboard.api.EnergyType.ELECTRIC_EU;
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

    private static void extractThermalRecipeDetails(EmiRecipe recipe, Object backing, RecipeDetails details) {
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
            ResourceLocation catId = recipe.getCategory() != null ? recipe.getCategory().getId() : null;
            boolean isDynamo = false;
            if (catId != null) {
                String cp = catId.getPath().toLowerCase();
                if (cp.contains("dynamo") || cp.contains("fuel") || cp.contains("lapidary") || cp.contains("compression")
                        || cp.contains("magmatic") || cp.contains("gourmand") || cp.contains("numismatic") || cp.contains("stirling") || cp.contains("disenchantment")) {
                    isDynamo = true;
                }
            }

            if (isDynamo) {
                details.isGenerator = true;
                double basePowerRF = com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper.getThermalDynamoBasePowerRF(catId);
                details.eut = basePowerRF / 4.0; // Standard 200 RF/t -> 50 EU/t
                details.durationTicks = Math.max(1.0, (double) energyRF / basePowerRF);
                details.tier = GTVoltageTier.LV;
            } else {
                details.isGenerator = false;
                double baseMachPowerRF = com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper.getThermalMachineBasePowerRF(catId);
                details.eut = baseMachPowerRF / 4.0; // Standard 20 RF/t -> 5 EU/t
                details.durationTicks = Math.max(1.0, (double) energyRF / baseMachPowerRF);
                details.tier = GTVoltageTier.LV;
            }
        }
    }

    private static boolean isGeneratorCategory(ResourceLocation catId) {
        String catPath = catId.getPath().toLowerCase();
        String catNs = catId.getNamespace().toLowerCase();
        return catPath.contains("dynamo") || catPath.contains("turbine")
                || catPath.equals("generator") || catPath.endsWith("_generator")
                || catPath.equals("combustion_generator") || catPath.equals("semi_fluid_generator")
                || catPath.equals("gas_turbine") || catPath.equals("steam_turbine") || catPath.equals("plasma_generator")
                || ((catNs.equals("thermal") || catNs.equals("thermal_expansion") || catNs.equals("systeams")) && catPath.contains("fuel"));
    }
}
