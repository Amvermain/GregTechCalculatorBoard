package com.gtceu.calcboard.integration.emi;

import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class EmiRecipeConverter {

    private static final Class<?> CREATE_BASIN_BLOCK_CLASS;
    private static final Class<?> CREATE_BLAZE_BURNER_BLOCK_CLASS;

    static {
        Class<?> basinCls = null;
        Class<?> burnerCls = null;
        try {
            basinCls = Class.forName("com.simibubi.create.content.processing.basin.BasinBlock");
        } catch (Throwable ignored) {}
        try {
            burnerCls = Class.forName("com.simibubi.create.content.processing.burner.BlazeBurnerBlock");
        } catch (Throwable ignored) {}
        CREATE_BASIN_BLOCK_CLASS = basinCls;
        CREATE_BLAZE_BURNER_BLOCK_CLASS = burnerCls;
    }

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
            if (ws != null && !isIgnoredWorkstation(ws)) {
                node.getAvailableWorkstations().add(ws);
            }
        }
        ResourceLocation icon = preferredWorkstation != null ? preferredWorkstation : findMachineIcon(recipe);
        if (icon != null && !isIgnoredWorkstation(icon)) {
            node.setMachineIcon(icon);
            if (!node.getAvailableWorkstations().contains(icon)) {
                node.getAvailableWorkstations().add(0, icon);
            }
        } else if (!node.getAvailableWorkstations().isEmpty()) {
            node.setMachineIcon(node.getAvailableWorkstations().get(0));
        }

        // Run Extensible Recipe Property Extractor Pipeline (RFC-002)
        Object backing = unwrapBackingRecipe(recipe);
        CompoundTag recipeDataTag = com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.extractRecipeDataTag(backing);
        com.gtceu.calcboard.api.property.RecipePropertyExtractorPipeline.extractAll(backing, recipeDataTag, catId, node.getProperties());

        boolean isSupported = com.gtceu.calcboard.compat.ModAdapterRegistry.isCategorySupported(catId);
        String rModId = (recipe.getId() != null) ? recipe.getId().getNamespace() : null;
        if (!isSupported && rModId != null) {
            isSupported = com.gtceu.calcboard.compat.ModAdapterRegistry.isRecipeSupported(rModId, catId);
        }
        if (!isSupported) {
            node.getProperties().set(com.gtceu.calcboard.api.property.NodeProperties.IS_GENERIC_UNSUPPORTED, true);
        }

        if (details.backingRecipeTemp > 0) {
            node.setRecipeTemperature(details.backingRecipeTemp);
        }

        if (details.energyType == EnergyType.ELECTRIC_FE || (catId != null && (catId.getNamespace().equals("thermal") || catId.getNamespace().equals("systeams")))) {
            long energyRF = com.gtceu.calcboard.compat.thermal.ThermalModAdapter.extractEnergyRF(backing);
            if (energyRF > 0) {
                node.getProperties().set(com.gtceu.calcboard.compat.thermal.ThermalProperties.THERMAL_BASE_ENERGY_RF, (double) energyRF);
            } else if (details.durationTicks > 0 && details.eut > 0) {
                node.getProperties().set(com.gtceu.calcboard.compat.thermal.ThermalProperties.THERMAL_BASE_ENERGY_RF, details.durationTicks * details.eut);
            }
        }

        boolean isGreate = (catId != null && catId.getNamespace().equals("greate"))
                || (recipe.getId() != null && recipe.getId().getNamespace().equals("greate"))
                || details.circuitNumber >= 0
                || !"NONE".equalsIgnoreCase(details.heatCondition);

        if (isGreate) {
            int initTier = details.tier != null ? details.tier.ordinal() : 0;
            node.getProperties().set(com.gtceu.calcboard.compat.greate.GreateProperties.IS_GREATE, true);
            node.getProperties().set(com.gtceu.calcboard.compat.greate.GreateProperties.MACHINE_TIER, initTier);
            node.getProperties().set(com.gtceu.calcboard.compat.greate.GreateProperties.REQUIRED_RECIPE_TIER, initTier);
            node.getProperties().set(com.gtceu.calcboard.compat.greate.GreateProperties.CIRCUIT_NUMBER, details.circuitNumber);
            node.getProperties().set(com.gtceu.calcboard.compat.greate.GreateProperties.HEAT_CONDITION, details.heatCondition);
            node.setTargetTier(GTVoltageTier.getByIndex(initTier));
            node.setRpm(256);
            com.gtceu.calcboard.compat.greate.GreateMachineHelper.syncMachineIconToTier(node, initTier);
        }

        List<SlotChance> extractedInputChances = extractSlotChances(recipe, true);
        boolean[] usedInputChances = new boolean[extractedInputChances.size()];

        for (int inIdx = 0; inIdx < recipe.getInputs().size(); inIdx++) {
            EmiIngredient input = recipe.getInputs().get(inIdx);
            long reqAmount = input.getAmount();
            float reqChance = input.getChance();
            IngredientStack primaryStack = null;
            List<ResourceLocation> altIds = new ArrayList<>();

            for (EmiStack stack : input.getEmiStacks()) {
                if (stack == null || stack.isEmpty()) continue;
                if (isIgnoredInput(stack.getId(), reqChance > 0 ? reqChance : stack.getChance())) continue;

                long finalAmount = reqAmount > 0 ? reqAmount : stack.getAmount();
                float finalChance = reqChance > 0 ? reqChance : stack.getChance();
                IngredientStack is = convertEmiStack(stack, finalAmount, finalChance);
                if (is != null && is.getId() != null) {
                    if (isIgnoredInput(is.getId(), is.getChance())) continue;
                    if (primaryStack == null) {
                        primaryStack = is;
                    }
                    if (!altIds.contains(is.getId())) {
                        altIds.add(is.getId());
                    }
                }
            }

            if (primaryStack != null) {
                try {
                    if (primaryStack.isFluid() && primaryStack.getId() != null) {
                        var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, primaryStack.getId());
                        if (ForgeRegistries.FLUIDS.tags().isKnownTagName(tagKey)) {
                            for (Fluid fluid : ForgeRegistries.FLUIDS.tags().getTag(tagKey)) {
                                ResourceLocation fId = ForgeRegistries.FLUIDS.getKey(fluid);
                                if (fId != null && !altIds.contains(fId)) {
                                    altIds.add(fId);
                                }
                            }
                        }
                    }
                } catch (Throwable ignored) {}

                applySlotChance(primaryStack, inIdx, extractedInputChances, usedInputChances);
                primaryStack.setAlternatives(altIds);
                node.addInput(primaryStack);
            }
        }

        List<SlotChance> extractedChances = extractSlotChances(recipe, false);
        boolean[] usedChances = new boolean[extractedChances.size()];

        for (int i = 0; i < recipe.getOutputs().size(); i++) {
            EmiStack outStack = recipe.getOutputs().get(i);
            if (outStack == null || outStack.isEmpty()) continue;
            if (isDummyConditionMarker(outStack.getId())) continue;

            IngredientStack os = convertEmiStack(outStack, outStack.getAmount(), outStack.getChance());
            if (os != null && !isDummyConditionMarker(os.getId())) {
                applySlotChance(os, i, extractedChances, usedChances);
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
            com.gtceu.calcboard.api.catalog.CategoryCapability cap = com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix.getInstance().getCapability(catId);
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
            var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
            if (adapter != null) {
                var preferredWs = adapter.getPreferredMultiblockWorkstation(node, node.getAvailableWorkstations());
                if (preferredWs != null) {
                    node.setMachineIcon(preferredWs);
                }
            }
        }

        if (node.isFusion()) {
            node.setMultiblock(true);
            GTVoltageTier minTier = node.getMinFusionVoltageTier();
            node.setTargetTier(minTier);
            var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
            if (adapter != null) {
                var preferredWs = adapter.getPreferredMultiblockWorkstation(node, node.getAvailableWorkstations());
                if (preferredWs != null) {
                    node.setMachineIcon(preferredWs);
                }
            }
        }

        // Auto-provision Heating Coil if temperature is required
        int reqTemp = node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.EBF_TEMPERATURE);
        if (reqTemp <= 0) reqTemp = node.getRecipeTemperature();
        if (reqTemp > 0) {
            var coil = com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.getCoilForTemperature(reqTemp);
            if (coil != null) {
                com.gtceu.calcboard.compat.gtceu.helper.CoilHelper.installCoil(node, coil);
            }
        }

        // Auto-provision Fusion Reflector if required
        int reqReflector = node.getProperties().get(com.gtceu.calcboard.compat.gtceu.GTCEuProperties.REQUIRED_REFLECTOR_TIER);
        if (reqReflector > 0) {
            com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper.installReflector(node, reqReflector);
        }

        node.autoCalculateTurbineParallel();
        if (preferredWorkstation == null) {
            com.gtceu.calcboard.api.preset.CategoryMachinePresetManager.getInstance().applyPresetIfPresent(node);
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

    public static boolean isIgnoredInput(ResourceLocation id, double chance) {
        if (id == null) return true;
        if (isDummyConditionMarker(id)) return true;
        if (isProgrammedCircuit(id)) return true;
        if (chance <= 0.0) return true;
        return false;
    }

    public static boolean isProgrammedCircuit(ResourceLocation id) {
        if (id == null) return false;
        String path = id.getPath().toLowerCase(Locale.ROOT);
        String ns = id.getNamespace().toLowerCase(Locale.ROOT);
        return ("gtceu".equals(ns) || "gtce".equals(ns) || "gregtech".equals(ns))
                && (path.equals("programmed_circuit") || path.equals("integrated_circuit") || path.startsWith("circuit_config"));
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

    public static boolean isIgnoredWorkstation(ResourceLocation id) {
        if (id == null) return true;
        if (isDummyConditionMarker(id)) return true;
        try {
            net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(id);
            if (item instanceof net.minecraft.world.item.BlockItem bi) {
                net.minecraft.world.level.block.Block block = bi.getBlock();
                if (CREATE_BASIN_BLOCK_CLASS != null && CREATE_BASIN_BLOCK_CLASS.isInstance(block)) {
                    return true;
                }
                if (CREATE_BLAZE_BURNER_BLOCK_CLASS != null && CREATE_BLAZE_BURNER_BLOCK_CLASS.isInstance(block)) {
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static ResourceLocation findMachineIcon(EmiRecipe recipe) {
        if (recipe == null) return null;

        List<ResourceLocation> allWs = findAllWorkstations(recipe);
        for (ResourceLocation ws : allWs) {
            if (ws != null && !isDummyConditionMarker(ws) && !isIgnoredWorkstation(ws)) {
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

    public static IngredientStack convertEmiStack(EmiStack stack, long amount, float chance) {
        if (stack.isEmpty()) return null;

        ResourceLocation id = stack.getId();
        String displayName = stack.getName().getString();

        Object key = stack.getKey();
        if (key instanceof Fluid fluid && fluid != Fluids.EMPTY) {
            ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
            return IngredientStack.fluid(fluidId != null ? fluidId : id, displayName, amount, chance);
        } else if (key != null && key.getClass().getName().contains("FluidStack")) {
            try {
                Method getFluidMethod = key.getClass().getMethod("getFluid");
                Object fl = getFluidMethod.invoke(key);
                if (fl instanceof Fluid fluid && fluid != Fluids.EMPTY) {
                    ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(fluid);
                    return IngredientStack.fluid(fluidId != null ? fluidId : id, displayName, amount, chance);
                }
            } catch (Throwable ignored) {}
            return IngredientStack.fluid(id, displayName, amount, chance);
        } else if (id != null) {
            if (ForgeRegistries.FLUIDS.containsKey(id)) {
                return IngredientStack.fluid(id, displayName, amount, chance);
            }
            try {
                var tagKey = net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.FLUID, id);
                if (ForgeRegistries.FLUIDS.tags().isKnownTagName(tagKey)) {
                    var it = ForgeRegistries.FLUIDS.tags().getTag(tagKey).iterator();
                    if (it.hasNext()) {
                        Fluid firstFluid = it.next();
                        if (firstFluid != null && firstFluid != Fluids.EMPTY) {
                            ResourceLocation fluidId = ForgeRegistries.FLUIDS.getKey(firstFluid);
                            return IngredientStack.fluid(fluidId != null ? fluidId : id, displayName, amount, chance);
                        }
                    }
                    return IngredientStack.fluid(id, displayName, amount, chance);
                }
            } catch (Throwable ignored) {}
            return IngredientStack.item(id, displayName, amount, chance);
        } else {
            return IngredientStack.item(id, displayName, amount, chance);
        }
    }

    public record SlotChance(ResourceLocation id, double chance, double tierChanceBoost) {}

    public record OutputSlotChance(ResourceLocation id, double chance, double tierChanceBoost) {}

    private static void applySlotChance(IngredientStack stack, int index, List<SlotChance> chances, boolean[] used) {
        if (stack == null || stack.getId() == null) return;
        ResourceLocation id = stack.getId();
        if (index < chances.size() && !used[index] && id.equals(chances.get(index).id())) {
            stack.setChance(chances.get(index).chance());
            stack.setTierChanceBoost(chances.get(index).tierChanceBoost());
            used[index] = true;
            return;
        }
        for (int j = 0; j < chances.size(); j++) {
            if (used[j] || !id.equals(chances.get(j).id())) continue;
            stack.setChance(chances.get(j).chance());
            stack.setTierChanceBoost(chances.get(j).tierChanceBoost());
            used[j] = true;
            return;
        }
    }

    private static List<SlotChance> extractSlotChances(EmiRecipe recipe, boolean isInput) {
        List<SlotChance> list = new ArrayList<>();
        if (recipe == null) return list;
        Object backing = unwrapBackingRecipe(recipe);
        if (backing == null) backing = recipe.getBackingRecipe();
        if (backing == null) return list;

        String key = isInput ? "inputs" : "outputs";

        if (ModCompatHelper.isGTLoaded() && com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.isGTRecipe(backing)) {
            List<SlotChance> gtChances = extractGTRecipeSlotChances(backing, key);
            if (!gtChances.isEmpty()) return gtChances;
        }

        if (!isInput) {
            List<SlotChance> createChances = extractCreateRollableChances(backing);
            if (!createChances.isEmpty()) return createChances;
        }

        if (ModCompatHelper.isGTLoaded() && backing.getClass().getName().contains("GTRecipe")) {
            list.addAll(extractGTFallbackSlotChances(backing, key));
        }
        return list;
    }

    private static List<SlotChance> extractGTRecipeSlotChances(Object backing, String key) {
        List<SlotChance> list = new ArrayList<>();
        List<IngredientStack> gtStacks = com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.extractGTRecipeContents(backing, key);
        if (gtStacks == null || gtStacks.isEmpty()) return list;
        for (IngredientStack is : gtStacks) {
            if (is == null || is.getId() == null) continue;
            list.add(new SlotChance(is.getId(), is.getChance(), is.getTierChanceBoost()));
        }
        return list;
    }

    private static List<SlotChance> extractCreateRollableChances(Object backing) {
        List<SlotChance> list = new ArrayList<>();
        try {
            Method m = backing.getClass().getMethod("getRollableResults");
            Object res = m.invoke(backing);
            if (!(res instanceof List<?> rollableList)) return list;
            for (Object po : rollableList) {
                SlotChance sc = parseCreateRollableSlot(po);
                if (sc != null) list.add(sc);
            }
        } catch (Throwable ignored) {}
        return list;
    }

    private static SlotChance parseCreateRollableSlot(Object po) {
        if (po == null) return null;
        try {
            Method getStackM = po.getClass().getMethod("getStack");
            Method getChanceM = po.getClass().getMethod("getChance");
            Object stackObj = getStackM.invoke(po);
            Object chanceObj = getChanceM.invoke(po);
            if (stackObj instanceof net.minecraft.world.item.ItemStack is && chanceObj instanceof Number n) {
                ResourceLocation id = ForgeRegistries.ITEMS.getKey(is.getItem());
                if (id != null) {
                    double ch = Math.max(0.0, Math.min(1.0, n.doubleValue()));
                    return new SlotChance(id, ch, 0.0);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static List<SlotChance> extractGTFallbackSlotChances(Object backing, String key) {
        List<SlotChance> list = new ArrayList<>();
        try {
            Field slotsField = getGTField(backing, key);
            if (slotsField == null) return list;
            Object slotsObj = slotsField.get(backing);
            if (!(slotsObj instanceof Map<?, ?> slotMap)) return list;
            for (Object listObj : slotMap.values()) {
                if (listObj instanceof List<?> contentList) {
                    parseGTContentList(contentList, list);
                }
            }
        } catch (Throwable ignored) {}
        return list;
    }

    private static Field getGTField(Object backing, String name) {
        try {
            return backing.getClass().getField(name);
        } catch (Throwable ignored) {
            try {
                Field f = backing.getClass().getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (Throwable ignored2) {
                return null;
            }
        }
    }

    private static void parseGTContentList(List<?> contentList, List<SlotChance> target) {
        for (Object contentObj : contentList) {
            SlotChance sc = parseGTContentSlot(contentObj);
            if (sc != null) target.add(sc);
        }
    }

    private static SlotChance parseGTContentSlot(Object contentObj) {
        if (contentObj == null) return null;
        double chance = extractGTContentChance(contentObj);
        double boost = extractGTContentBoost(contentObj);
        ResourceLocation resId = extractContentResourceId(contentObj);
        return resId != null ? new SlotChance(resId, chance, boost) : null;
    }

    private static double extractGTContentChance(Object contentObj) {
        double chance = 1.0;
        try {
            Field f = contentObj.getClass().getField("chance");
            Object v = f.get(contentObj);
            if (v instanceof Number n) chance = n.doubleValue();
        } catch (Throwable ignored) {
            chance = invokeChanceMethod(contentObj);
        }
        if (chance > 1.0) chance = chance / 10000.0;
        return Math.max(0.0, Math.min(1.0, chance));
    }

    private static double invokeChanceMethod(Object contentObj) {
        for (String mName : new String[]{"chance", "getChance"}) {
            try {
                Method m = contentObj.getClass().getMethod(mName);
                Object v = m.invoke(contentObj);
                if (v instanceof Number n) return n.doubleValue();
            } catch (Throwable ignored) {}
        }
        return 1.0;
    }

    private static double extractGTContentBoost(Object contentObj) {
        double boost = 0.0;
        try {
            Field f = contentObj.getClass().getField("tierChanceBoost");
            Object v = f.get(contentObj);
            if (v instanceof Number n) boost = n.doubleValue();
        } catch (Throwable ignored) {
            boost = invokeBoostMethod(contentObj);
        }
        if (Math.abs(boost) > 1.0) boost = boost / 10000.0;
        return boost;
    }

    private static double invokeBoostMethod(Object contentObj) {
        for (String mName : new String[]{"tierChanceBoost", "getTierChanceBoost"}) {
            try {
                Method m = contentObj.getClass().getMethod(mName);
                Object v = m.invoke(contentObj);
                if (v instanceof Number n) return n.doubleValue();
            } catch (Throwable ignored) {}
        }
        return 0.0;
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

                for (String mName : new String[]{"content", "getContent", "getInner", "getStack", "getItems", "getMatchingStacks", "getItemStack", "getFluid", "getRawFluid", "getIngredient", "inner"}) {
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

    public static Object unwrapBackingRecipe(EmiRecipe recipe) {
        if (recipe == null) return null;
        Object backing = recipe.getBackingRecipe();
        if (backing != null) {
            return unwrapInnerRecipe(backing);
        }

        Object unwrapped = scanFieldsForRecipe(recipe);
        if (unwrapped != null) {
            return unwrapped;
        }

        return lookupRecipeFromRecipeManager(recipe);
    }

    private static Object scanFieldsForRecipe(EmiRecipe recipe) {
        Class<?> cur = recipe.getClass();
        while (cur != null && cur != Object.class) {
            for (String fName : new String[]{"recipe", "gtRecipe", "backingRecipe", "originalRecipe", "target", "source", "value", "delegate"}) {
                try {
                    Field f = cur.getDeclaredField(fName);
                    f.setAccessible(true);
                    Object res = f.get(recipe);
                    if (res != null && res != recipe) {
                        return unwrapInnerRecipe(res);
                    }
                } catch (Throwable ignored) {}
            }
            for (String mName : new String[]{"getRecipe", "recipe", "getGTRecipe", "gtRecipe", "getOriginalRecipe", "originalRecipe", "getValue", "value"}) {
                try {
                    Method m = cur.getDeclaredMethod(mName);
                    m.setAccessible(true);
                    Object res = m.invoke(recipe);
                    if (res != null && res != recipe) {
                        return unwrapInnerRecipe(res);
                    }
                } catch (Throwable ignored) {}
            }
            for (Field f : cur.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(recipe);
                    if (val != null && val != recipe && val instanceof net.minecraft.world.item.crafting.Recipe<?>) {
                        return unwrapInnerRecipe(val);
                    }
                } catch (Throwable ignored) {}
            }
            cur = cur.getSuperclass();
        }
        return null;
    }

    private static Object lookupRecipeFromRecipeManager(EmiRecipe recipe) {
        ResourceLocation id = recipe.getId();
        if (id == null) return null;

        try {
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc == null || mc.level == null) return null;
            net.minecraft.world.item.crafting.RecipeManager rm = mc.level.getRecipeManager();
            if (rm == null) return null;

            var direct = rm.byKey(id);
            if (direct.isPresent()) {
                return unwrapInnerRecipe(direct.get());
            }

            String path = id.getPath();
            if (path.contains("automatic_packing/")) {
                String stripped = path.replace("automatic_packing/", "");
                ResourceLocation cleanId = ResourceLocation.tryParse(id.getNamespace() + ":" + stripped);
                if (cleanId != null) {
                    var cleanRecipe = rm.byKey(cleanId);
                    if (cleanRecipe.isPresent()) {
                        return unwrapInnerRecipe(cleanRecipe.get());
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static Object unwrapInnerRecipe(Object obj) {
        if (obj == null) return null;
        Object cur = obj;
        for (int i = 0; i < 3; i++) {
            boolean unwrapped = false;
            Class<?> cl = cur.getClass();
            for (String mName : new String[]{"getRecipe", "value", "recipe"}) {
                try {
                    Method m = cl.getMethod(mName);
                    Object next = m.invoke(cur);
                    if (next != null && next != cur) {
                        cur = next;
                        unwrapped = true;
                        break;
                    }
                } catch (Throwable ignored) {}
            }
            if (!unwrapped) break;
        }
        return cur;
    }

    public static class RecipeDetails {
        public double durationTicks = 20.0;
        public double eut = 0.0;
        public GTVoltageTier tier = GTVoltageTier.ULV;
        public boolean isGenerator = false;
        public com.gtceu.calcboard.api.type.EnergyType energyType = com.gtceu.calcboard.api.type.EnergyType.NONE;
        public int backingRecipeTemp = 0;
        public int circuitNumber = -1;
        public String heatCondition = "NONE";
        public List<IngredientStack> extraInputs = new ArrayList<>();
        public List<IngredientStack> extraOutputs = new ArrayList<>();
        public boolean overrideOutputs = false;
        public List<IngredientStack> customOutputs = new ArrayList<>();
    }

    public static RecipeDetails extractRecipeDetails(EmiRecipe recipe, ResourceLocation preferredWorkstation) {
        RecipeDetails details = new RecipeDetails();
        try {
            var backing = unwrapBackingRecipe(recipe);
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
                if (com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.isGTRecipe(backing)) {
                    com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.extractGTRecipeDetails(backing, details);
                    handled = true;
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



