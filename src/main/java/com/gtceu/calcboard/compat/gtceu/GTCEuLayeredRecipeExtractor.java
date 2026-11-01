package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.CompoundRecipeBuilder;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeDetails;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;
import com.gtceu.calcboard.api.preset.CategoryMachinePresetManager;
import com.gtceu.calcboard.api.property.RecipePropertyExtractorPipeline;
import com.gtceu.calcboard.api.spi.viewer.IRecipeViewerBridge;
import com.gtceu.calcboard.api.spi.viewer.RecipeViewerBridgeRegistry;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.RecipeConversionHelper;
import com.gtceu.calcboard.compat.gtceu.handler.GTWorkstationSelector;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCEuWorkstationResolver;
import com.gtceu.calcboard.compat.gtceu.helper.ReflectorHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Decomposes multi-step progressive (layered) recipes into sequential compound clusters.
 * Utilizes static reflection and multi-tier deterministic NBT deduction to extract individual
 * processing layers without merging composite steps into a single monolithic recipe card.
 */
public final class GTCEuLayeredRecipeExtractor {

    private static final Method HAS_LAYERED_STEPS;
    private static final Method GET_LAYERED_STEPS_RECIPE;
    private static final Method GET_LAYERED_STEPS_TAG;
    private static final Method CALCULATE_RECIPE_STEPS;
    private static final Method IS_LAYERED_TYPE;
    private static final Field RECIPE_FIELD_IN_EMI;

    private static final ClassValue<Method> DIRECT_STEPS_METHOD_CACHE = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                Method m = type.getMethod("getLayeredSteps");
                m.setAccessible(true);
                return m;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    };

    private static final ClassValue<Method> IS_LAYERED_METHOD_CACHE = new ClassValue<>() {
        @Override
        protected Method computeValue(Class<?> type) {
            try {
                Method m = type.getMethod("isLayered");
                m.setAccessible(true);
                return m;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    };

    private static final ClassValue<Field> RECIPE_TYPE_FIELD_CACHE = new ClassValue<>() {
        @Override
        protected Field computeValue(Class<?> type) {
            try {
                Field f = type.getField("recipeType");
                f.setAccessible(true);
                return f;
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
    };

    static {
        Method hasSteps = null;
        Method getStepsRecipe = null;
        Method getStepsTag = null;
        Method calcSteps = null;
        try {
            Class<?> helperClass = Class.forName("com.gregtechceu.gtceu.api.recipe.LayeredRecipeHelper");
            for (Method m : helperClass.getMethods()) {
                if (m.getParameterCount() != 1) continue;
                Class<?> pType = m.getParameterTypes()[0];
                if ("hasLayeredSteps".equals(m.getName()) && !CompoundTag.class.isAssignableFrom(pType)) {
                    hasSteps = m;
                } else if ("getLayeredSteps".equals(m.getName())) {
                    if (CompoundTag.class.isAssignableFrom(pType)) {
                        getStepsTag = m;
                    } else {
                        getStepsRecipe = m;
                    }
                } else if ("calculateRecipeSteps".equals(m.getName())) {
                    calcSteps = m;
                }
            }
        } catch (Throwable ignored) {}
        HAS_LAYERED_STEPS = hasSteps;
        GET_LAYERED_STEPS_RECIPE = getStepsRecipe;
        GET_LAYERED_STEPS_TAG = getStepsTag;
        CALCULATE_RECIPE_STEPS = calcSteps;

        Method isLayered = null;
        try {
            Class<?> typeClass = Class.forName("com.gregtechceu.gtceu.api.recipe.GTRecipeType");
            isLayered = typeClass.getMethod("isLayered");
        } catch (Throwable ignored) {}
        IS_LAYERED_TYPE = isLayered;

        Field emiRecipe = null;
        try {
            Class<?> emiClass = Class.forName("com.gregtechceu.gtceu.integration.emi.recipe.GTEmiRecipe");
            emiRecipe = emiClass.getDeclaredField("recipe");
            emiRecipe.setAccessible(true);
        } catch (Throwable ignored) {}
        RECIPE_FIELD_IN_EMI = emiRecipe;
    }

    private GTCEuLayeredRecipeExtractor() {}

    public static boolean isLayeredRecipe(Object backingRecipe) {
        return isLayeredRecipe(backingRecipe, null);
    }

    public static boolean isLayeredRecipe(Object backingRecipe, Object recipeObj) {
        Object candidate = resolveBackingRecipe(backingRecipe, recipeObj);
        if (candidate == null) return false;

        if (checkHasLayeredSteps(candidate)) return true;

        CompoundTag dataTag = GTCEuRecipeHandler.extractRecipeDataTag(candidate);
        if (dataTag != null && (dataTag.contains("layered_steps") || dataTag.contains("layered_info") || dataTag.contains("is_layer"))) {
            return true;
        }

        return checkRecipeTypeIsLayered(candidate);
    }

    public static List<Object> extractLayeredSteps(Object backingRecipe) {
        return extractLayeredSteps(backingRecipe, null);
    }

    public static List<Object> extractLayeredSteps(Object backingRecipe, Object recipeObj) {
        Object candidate = resolveBackingRecipe(backingRecipe, recipeObj);
        if (candidate == null) return Collections.emptyList();

        List<Object> steps = invokeGetLayeredStepsRecipe(candidate);
        if (!steps.isEmpty()) return steps;

        CompoundTag dataTag = GTCEuRecipeHandler.extractRecipeDataTag(candidate);
        if (dataTag != null) {
            steps = invokeGetLayeredStepsTag(dataTag);
            if (!steps.isEmpty()) return steps;

            if (dataTag.contains("layered_info")) {
                steps = invokeCalculateRecipeSteps(candidate);
                if (!steps.isEmpty()) return steps;
            }
        }

        return invokeDirectGetLayeredSteps(candidate);
    }

    public static CompoundRecipeBuilder.CompoundCluster buildCompoundCluster(
            Object backingRecipe,
            String machineName,
            ResourceLocation machineIcon,
            GTVoltageTier tier,
            double startX,
            double startY
    ) {
        return buildCompoundCluster(backingRecipe, null, machineName, machineIcon, null, tier, startX, startY);
    }

    public static CompoundRecipeBuilder.CompoundCluster buildCompoundCluster(
            Object backingRecipe,
            Object recipeObj,
            String machineName,
            ResourceLocation machineIcon,
            GTVoltageTier tier,
            double startX,
            double startY
    ) {
        return buildCompoundCluster(backingRecipe, recipeObj, machineName, machineIcon, null, tier, startX, startY);
    }

    public static CompoundRecipeBuilder.CompoundCluster buildCompoundCluster(
            Object backingRecipe,
            Object recipeObj,
            String machineName,
            ResourceLocation machineIcon,
            ResourceLocation preferredWorkstation,
            GTVoltageTier tier,
            double startX,
            double startY
    ) {
        Object resolvedRecipe = resolveBackingRecipe(backingRecipe, recipeObj);
        if (resolvedRecipe == null || !isLayeredRecipe(resolvedRecipe, recipeObj)) return null;

        List<Object> stepRecipes = extractLayeredSteps(resolvedRecipe, recipeObj);
        if (stepRecipes.size() < 2) return null;

        RecipeDetails parentDetails = new RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(resolvedRecipe, parentDetails);

        double totalDurationTicks = parentDetails.durationTicks;
        double baseEUt = parentDetails.eut;
        GTVoltageTier effectiveTier = tier != null ? tier : parentDetails.tier;
        double fallbackDurationPerStep = totalDurationTicks > 0.0 ? totalDurationTicks / stepRecipes.size() : 20.0;

        List<CompoundRecipeBuilder.LayerSpec> layers = new ArrayList<>();
        List<Object> validSteps = new ArrayList<>();
        double accumulatedDuration = 0.0;
        int stepIndex = 1;

        for (Object stepObj : stepRecipes) {
            if (stepObj == null) continue;

            RecipeDetails stepDetails = new RecipeDetails();
            GTCEuRecipeHandler.extractGTRecipeDetails(stepObj, stepDetails);

            double stepDuration = stepDetails.durationTicks > 0.0 ? stepDetails.durationTicks : fallbackDurationPerStep;
            double stepEUt = stepDetails.eut > 0.0 ? stepDetails.eut : baseEUt;
            accumulatedDuration += stepDuration;

            List<IngredientStack> stepInputs = GTCEuRecipeHandler.extractGTRecipeContents(stepObj, "inputs");
            List<IngredientStack> stepOutputs = GTCEuRecipeHandler.extractGTRecipeContents(stepObj, "outputs");

            String roman = CompoundRecipeBuilder.formatRoman(stepIndex);
            layers.add(new CompoundRecipeBuilder.LayerSpec("Layer " + roman, stepDuration, stepEUt, stepInputs, stepOutputs));
            validSteps.add(stepObj);
            stepIndex++;
        }

        if (layers.size() < 2) return null;

        double finalDuration = accumulatedDuration > 0.0 ? accumulatedDuration : totalDurationTicks;
        CompoundRecipeBuilder.CompoundCluster cluster = CompoundRecipeBuilder.build(
                machineName, machineIcon, finalDuration, baseEUt, effectiveTier, layers, startX, startY
        );
        configureLayerNodes(cluster, validSteps, resolvedRecipe, recipeObj, machineIcon, preferredWorkstation);
        return cluster;
    }

    private static void configureLayerNodes(
            CompoundRecipeBuilder.CompoundCluster cluster,
            List<Object> stepRecipes,
            Object resolvedRecipe,
            Object recipeObj,
            ResourceLocation initialIcon,
            ResourceLocation preferredWorkstation
    ) {
        if (cluster == null || cluster.nodes().isEmpty()) return;

        ResourceLocation categoryId = GTCEuRecipeHandler.extractRecipeCategoryId(recipeObj, resolvedRecipe);
        List<ResourceLocation> workstations = resolveLayeredWorkstations(categoryId, recipeObj, initialIcon);
        boolean multiblockOnly = isMultiblockOnly(categoryId, workstations);

        List<RecipeNode> nodes = cluster.nodes();
        for (int i = 0; i < nodes.size(); i++) {
            Object stepObj = i < stepRecipes.size() ? stepRecipes.get(i) : resolvedRecipe;
            configureSingleLayerNode(nodes.get(i), stepObj, categoryId, workstations, multiblockOnly, preferredWorkstation);
        }
    }

    private static void configureSingleLayerNode(
            RecipeNode node,
            Object stepObj,
            ResourceLocation categoryId,
            List<ResourceLocation> workstations,
            boolean multiblockOnly,
            ResourceLocation preferredWorkstation
    ) {
        if (categoryId != null) {
            node.setRecipeCategoryId(categoryId);
        }
        if (!workstations.isEmpty()) {
            node.setAvailableWorkstations(new ArrayList<>(workstations));
        }

        if (multiblockOnly) {
            node.setMultiblock(true);
            ResourceLocation targetWs = preferredWorkstation != null
                    ? preferredWorkstation
                    : GTWorkstationSelector.getPreferredMultiblockWorkstation(node, node.getAvailableWorkstations());
            if (targetWs != null) {
                node.setMachineIcon(targetWs);
            }
        }

        CompoundTag stepDataTag = GTCEuRecipeHandler.extractRecipeDataTag(stepObj);
        RecipePropertyExtractorPipeline.extractAll(stepObj, stepDataTag, categoryId, node.getProperties());
        autoProvisionLayerHardware(node, preferredWorkstation);
        refreshLayerBaseSpec(node);
    }

    private static void autoProvisionLayerHardware(RecipeNode node, ResourceLocation preferredWorkstation) {
        int reqTemp = node.getProperties().get(GTCEuProperties.EBF_TEMPERATURE);
        if (reqTemp <= 0) reqTemp = node.getRecipeTemperature();
        if (reqTemp > 0) {
            var coil = CoilHelper.getCoilForTemperature(reqTemp);
            if (coil != null) {
                CoilHelper.installCoil(node, coil);
            }
        }

        int reqReflector = node.getProperties().get(GTCEuProperties.REQUIRED_REFLECTOR_TIER);
        if (reqReflector > 0) {
            ReflectorHelper.installReflector(node, reqReflector);
        }

        com.gtceu.calcboard.compat.gtceu.helper.GTCEuMachineLifecycleHandler.ensureDefaultMachineTraits(node, node.getMachineIcon());

        if (preferredWorkstation == null) {
            CategoryMachinePresetManager.getInstance().applyPresetIfPresent(node);
        }

        GTVoltageTier currentTier = node.getTargetTier() != null ? node.getTargetTier() : node.getRecipeTier();
        if (currentTier != null) {
            node.setTargetTier(GTCEuWorkstationResolver.sanitizeTargetTier(node, currentTier));
        }
    }

    private static void refreshLayerBaseSpec(RecipeNode node) {
        node.setBaseSpec(RecipeSpec.of(
                node.getId(),
                node.getRecipeCategoryId(),
                node.getBaseDurationTicks(),
                node.getBaseEUt(),
                node.getInputs(),
                node.getOutputs()
        ));
    }

    private static List<ResourceLocation> resolveLayeredWorkstations(
            ResourceLocation categoryId,
            Object recipeObj,
            ResourceLocation initialIcon
    ) {
        List<ResourceLocation> workstations = new ArrayList<>();
        CategoryCapability cap = categoryId != null ? CategoryCapabilityMatrix.getInstance().getCapability(categoryId) : null;
        if (cap != null && cap.availableWorkstations() != null) {
            for (ResourceLocation ws : cap.availableWorkstations()) {
                addUniqueWorkstation(workstations, ws);
            }
        }

        IRecipeViewerBridge bridge = RecipeViewerBridgeRegistry.getActiveBridge();
        if (bridge != null && recipeObj != null) {
            for (ResourceLocation ws : bridge.findAllWorkstations(recipeObj)) {
                addUniqueWorkstation(workstations, ws);
            }
        }

        if (categoryId != null) {
            for (ResourceLocation ws : GTCEuWorkstationResolver.deductMultiblocksFromGTRegistries(categoryId)) {
                addUniqueWorkstation(workstations, ws);
            }
        }

        if (cap != null && cap.hasMultiblockOption() && !cap.hasSingleblockOption()) {
            workstations.removeIf(ws -> !MultiblockDetector.isMultiblock(ws));
        }

        if (workstations.isEmpty() && initialIcon != null) {
            addUniqueWorkstation(workstations, initialIcon);
        }
        return workstations;
    }

    private static void addUniqueWorkstation(List<ResourceLocation> workstations, ResourceLocation ws) {
        if (ws == null || RecipeConversionHelper.isIgnoredWorkstation(ws)) return;
        if (!workstations.contains(ws)) {
            workstations.add(ws);
        }
    }

    private static boolean isMultiblockOnly(ResourceLocation categoryId, List<ResourceLocation> workstations) {
        if (categoryId != null) {
            CategoryCapability cap = CategoryCapabilityMatrix.getInstance().getCapability(categoryId);
            if (cap != null && cap.hasMultiblockOption() && !cap.hasSingleblockOption()) {
                return true;
            }
        }
        boolean hasMulti = false;
        boolean hasSingle = false;
        for (ResourceLocation ws : workstations) {
            if (MultiblockDetector.isMultiblock(ws) || RecipeNode.isMultiblockWorkstation(ws)) {
                hasMulti = true;
            } else {
                hasSingle = true;
            }
        }
        return hasMulti && !hasSingle;
    }

    private static Object resolveBackingRecipe(Object backingRecipe, Object recipeObj) {
        if (backingRecipe != null) {
            Object unwrapped = GTCEuRecipeHandler.unwrapRecipe(backingRecipe);
            if (unwrapped != null) return unwrapped;
        }

        if (recipeObj != null && RECIPE_FIELD_IN_EMI != null) {
            try {
                Object inner = RECIPE_FIELD_IN_EMI.get(recipeObj);
                if (inner != null) {
                    return GTCEuRecipeHandler.unwrapRecipe(inner);
                }
            } catch (Throwable ignored) {}
        }

        return backingRecipe;
    }

    private static boolean checkHasLayeredSteps(Object recipe) {
        if (HAS_LAYERED_STEPS == null || recipe == null) return false;
        try {
            Object res = HAS_LAYERED_STEPS.invoke(null, recipe);
            return res instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean checkRecipeTypeIsLayered(Object recipe) {
        if (recipe == null) return false;
        Field typeField = RECIPE_TYPE_FIELD_CACHE.get(recipe.getClass());
        if (typeField == null) return false;
        try {
            Object recipeType = typeField.get(recipe);
            if (recipeType == null) return false;
            Method method = IS_LAYERED_TYPE != null ? IS_LAYERED_TYPE : IS_LAYERED_METHOD_CACHE.get(recipeType.getClass());
            if (method == null) return false;
            Object res = method.invoke(recipeType);
            return res instanceof Boolean b && b;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Object> invokeGetLayeredStepsRecipe(Object recipe) {
        if (GET_LAYERED_STEPS_RECIPE == null || recipe == null) return Collections.emptyList();
        try {
            Object res = GET_LAYERED_STEPS_RECIPE.invoke(null, recipe);
            if (res instanceof List<?> list && !list.isEmpty()) {
                return (List<Object>) list;
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> invokeGetLayeredStepsTag(CompoundTag tag) {
        if (GET_LAYERED_STEPS_TAG == null || tag == null) return Collections.emptyList();
        try {
            Object res = GET_LAYERED_STEPS_TAG.invoke(null, tag);
            if (res instanceof List<?> list && !list.isEmpty()) {
                return (List<Object>) list;
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> invokeCalculateRecipeSteps(Object recipe) {
        if (CALCULATE_RECIPE_STEPS == null || recipe == null) return Collections.emptyList();
        try {
            Object res = CALCULATE_RECIPE_STEPS.invoke(null, recipe);
            if (res instanceof List<?> list && !list.isEmpty()) {
                return (List<Object>) list;
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> invokeDirectGetLayeredSteps(Object recipe) {
        if (recipe == null) return Collections.emptyList();
        Method directMethod = DIRECT_STEPS_METHOD_CACHE.get(recipe.getClass());
        if (directMethod == null) return Collections.emptyList();
        try {
            Object res = directMethod.invoke(recipe);
            if (res instanceof List<?> list && !list.isEmpty()) {
                return (List<Object>) list;
            }
        } catch (Throwable ignored) {}
        return Collections.emptyList();
    }
}
