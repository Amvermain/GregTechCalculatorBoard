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
import com.gtceu.calcboard.integration.emi.EmiSlotChanceExtractor.SlotChance;
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
import java.util.Objects;

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
        ResourceLocation icon = preferredWorkstation;
        if (icon == null) {
            GTVoltageTier initialTier = details.tier != null ? details.tier : GTVoltageTier.LV;
            ResourceLocation tieredWs = node.getWorkstationForTier(initialTier);
            if (tieredWs != null && (node.getAvailableWorkstations().contains(tieredWs) || ForgeRegistries.ITEMS.containsKey(tieredWs))) {
                icon = tieredWs;
            } else {
                icon = findMachineIcon(recipe);
            }
        }
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

        List<IngredientStack> gtTickInputs = null;
        List<IngredientStack> gtTickOutputs = null;
        if (backing != null && com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.isGTRecipe(backing)) {
            gtTickInputs = com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.extractTickIngredients(backing, "tickInputs", details.durationTicks);
            gtTickOutputs = com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.extractTickIngredients(backing, "tickOutputs", details.durationTicks);
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

                primaryStack = applyTickIngredientScaling(primaryStack, gtTickInputs);
                applySlotChance(primaryStack, inIdx, extractedInputChances, usedInputChances);
                primaryStack.setAlternatives(altIds);
                node.addInput(primaryStack);
            }
        }

        if (gtTickInputs != null) {
            for (IngredientStack tickIn : gtTickInputs) {
                if (tickIn != null && tickIn.getId() != null && !containsIngredient(node.getInputs(), tickIn)) {
                    node.addInput(tickIn.copy());
                }
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
                os = applyTickIngredientScaling(os, gtTickOutputs);
                applySlotChance(os, i, extractedChances, usedChances);
                node.addOutput(os);
            }
        }

        if (gtTickOutputs != null) {
            for (IngredientStack tickOut : gtTickOutputs) {
                if (tickOut != null && tickOut.getId() != null && !containsIngredient(node.getOutputs(), tickOut)) {
                    node.addOutput(tickOut.copy());
                }
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
        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        GTVoltageTier effectiveTier = node.getTargetTier() != null ? node.getTargetTier() : (node.getRecipeTier() != null ? node.getRecipeTier() : GTVoltageTier.LV);
        if (adapter != null) {
            effectiveTier = adapter.sanitizeTargetTier(node, effectiveTier);
        }
        node.setTargetTier(effectiveTier);
        com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.ensureCombustionInputs(node);
        return node;
    }

    public static List<ResourceLocation> findAllWorkstations(EmiRecipe recipe) {
        List<ResourceLocation> list = new ArrayList<>();
        if (recipe == null) return list;

        try {
            Method m = recipe.getClass().getMethod("getWorkstations");
            Object res = m.invoke(recipe);
            if (res instanceof List<?> workstations && !workstations.isEmpty()) {
                for (Object ws : workstations) {
                    addEmiIngredientToWorkstations(ws, list);
                }
            }
        } catch (Throwable ignored) {}

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
                || path.startsWith("dimension_marker") || path.startsWith("biome_marker")
                || path.startsWith("planet_marker") || path.startsWith("environmental_marker")
                || path.startsWith("altitude_marker") || path.startsWith("temperature_marker")) {
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

    private static void applySlotChance(IngredientStack stack, int index, List<SlotChance> chances, boolean[] used) {
        EmiSlotChanceExtractor.applySlotChance(stack, index, chances, used);
    }

    private static List<SlotChance> extractSlotChances(EmiRecipe recipe, boolean isInput) {
        return EmiSlotChanceExtractor.extractSlotChances(recipe, isInput);
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
        return EmiRecipeDetailsExtractor.unwrapBackingRecipe(recipe);
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
        return EmiRecipeDetailsExtractor.extractRecipeDetails(recipe, preferredWorkstation);
    }

    private static IngredientStack applyTickIngredientScaling(IngredientStack stack, List<IngredientStack> tickList) {
        if (stack == null || tickList == null || tickList.isEmpty()) {
            return stack;
        }
        IngredientStack matchingTick = com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler.findMatchingTickIngredient(tickList, stack);
        if (matchingTick == null) {
            return stack;
        }
        IngredientStack scaled = stack.withAmount(matchingTick.getAmount());
        if (matchingTick.getChance() > 0) {
            scaled.setChance(matchingTick.getChance());
            scaled.setTierChanceBoost(matchingTick.getTierChanceBoost());
        }
        return scaled;
    }

    private static boolean containsIngredient(List<IngredientStack> list, IngredientStack target) {
        if (list == null || target == null || target.getId() == null) {
            return false;
        }
        for (IngredientStack s : list) {
            if (s != null && Objects.equals(s.getId(), target.getId()) && s.isFluid() == target.isFluid()) {
                return true;
            }
        }
        return false;
    }
}
