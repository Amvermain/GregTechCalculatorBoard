package com.gtceu.calcboard.compat.createdieselgenerators;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.compat.create.CreateProperties;
import com.gtceu.calcboard.compat.create.CreateStressHelper;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Recipe and specification deduction handler for Create: Diesel Generators.
 * Provides deterministic engine metrics extraction via dynamic registries and reflection caching.
 */
public class CDGRecipeHandler {

    public static final String MOD_ID = "createdieselgenerators";

    public static final ResourceLocation CAT_BASIN_FERMENTING = ResourceLocation.tryParse("createdieselgenerators:basin_fermenting");
    public static final ResourceLocation CAT_BULK_FERMENTING = ResourceLocation.tryParse("createdieselgenerators:bulk_fermenting");
    public static final ResourceLocation CAT_COMPRESSION_MOLDING = ResourceLocation.tryParse("createdieselgenerators:compression_molding");
    public static final ResourceLocation CAT_CASTING = ResourceLocation.tryParse("createdieselgenerators:casting");
    public static final ResourceLocation CAT_DISTILLATION = ResourceLocation.tryParse("createdieselgenerators:distillation");
    public static final ResourceLocation CAT_DIESEL_COMBUSTION = ResourceLocation.tryParse("createdieselgenerators:diesel_combustion");
    public static final ResourceLocation CAT_HAMMERING = ResourceLocation.tryParse("createdieselgenerators:hammering");
    public static final ResourceLocation CAT_WIRE_CUTTING = ResourceLocation.tryParse("createdieselgenerators:wire_cutting");

    public static final ResourceLocation ITEM_BASIN_LID = ResourceLocation.tryParse("createdieselgenerators:basin_lid");
    public static final ResourceLocation ITEM_BULK_FERMENTER = ResourceLocation.tryParse("createdieselgenerators:bulk_fermenter");
    public static final ResourceLocation ITEM_DISTILLATION_CONTROLLER = ResourceLocation.tryParse("createdieselgenerators:distillation_controller");
    public static final ResourceLocation ITEM_DIESEL_ENGINE = ResourceLocation.tryParse("createdieselgenerators:diesel_engine");
    public static final ResourceLocation ITEM_MODULAR_DIESEL_ENGINE = ResourceLocation.tryParse("createdieselgenerators:large_diesel_engine");
    public static final ResourceLocation ITEM_HUGE_DIESEL_ENGINE = ResourceLocation.tryParse("createdieselgenerators:huge_diesel_engine");

    public static final ResourceLocation FLUID_DIESEL = ResourceLocation.tryParse("createdieselgenerators:diesel");

    private static final Method GET_PROCESSING_DURATION_METHOD;
    private static final Method GET_PROCESSING_TIME_METHOD;
    private static final Method GET_REQUIRED_HEAT_METHOD;

    private static final Method FUEL_NORMAL_METHOD;
    private static final Method FUEL_MODULAR_METHOD;
    private static final Method FUEL_HUGE_METHOD;
    private static final Method PROP_SPEED_METHOD;
    private static final Method PROP_STRENGTH_METHOD;
    private static final Method PROP_BURN_METHOD;
    private static final ResourceKey<? extends Registry<?>> FUEL_TYPE_REGISTRY_KEY;

    static {
        Method durationMethod = null;
        Method timeMethod = null;
        Method heatMethod = null;
        try {
            Class<?> processingRecipeCls = Class.forName("com.simibubi.create.content.processing.recipe.ProcessingRecipe");
            durationMethod = processingRecipeCls.getMethod("getProcessingDuration");
            heatMethod = processingRecipeCls.getMethod("getRequiredHeat");
        } catch (Throwable ignored) {}
        try {
            Class<?> recipeCls = Class.forName("net.minecraft.world.item.crafting.Recipe");
            timeMethod = recipeCls.getMethod("getProcessingTime");
        } catch (Throwable ignored) {}
        GET_PROCESSING_DURATION_METHOD = durationMethod;
        GET_PROCESSING_TIME_METHOD = timeMethod;
        GET_REQUIRED_HEAT_METHOD = heatMethod;

        Method normM = null;
        Method modM = null;
        Method hugeM = null;
        Method spdM = null;
        Method strM = null;
        Method brnM = null;
        ResourceKey<? extends Registry<?>> fuelKey = null;
        try {
            Class<?> fuelTypeCls = Class.forName("com.jesz.createdieselgenerators.fuel_type.FuelType");
            normM = fuelTypeCls.getMethod("normal");
            modM = fuelTypeCls.getMethod("modular");
            hugeM = fuelTypeCls.getMethod("huge");
            Class<?> propCls = Class.forName("com.jesz.createdieselgenerators.fuel_type.FuelType$PerEngineProperties");
            spdM = propCls.getMethod("speed");
            strM = propCls.getMethod("strength");
            brnM = propCls.getMethod("burn");
            fuelKey = ResourceKey.createRegistryKey(ResourceLocation.tryParse("createdieselgenerators:fuel_type"));
        } catch (Throwable ignored) {}
        FUEL_NORMAL_METHOD = normM;
        FUEL_MODULAR_METHOD = modM;
        FUEL_HUGE_METHOD = hugeM;
        PROP_SPEED_METHOD = spdM;
        PROP_STRENGTH_METHOD = strM;
        PROP_BURN_METHOD = brnM;
        FUEL_TYPE_REGISTRY_KEY = fuelKey;
    }

    public static boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        ResourceLocation catId = extractCategoryId(emiRecipe, backingRecipe);
        if (catId == null || !MOD_ID.equals(catId.getNamespace())) {
            return false;
        }

        int duration = extractDuration(backingRecipe);
        String heatCondition = extractHeatCondition(backingRecipe);
        if (heatCondition != null && !heatCondition.isEmpty()) {
            details.heatCondition = heatCondition;
        }

        String path = catId.getPath().toLowerCase(Locale.ROOT);
        return switch (path) {
            case "basin_fermenting" -> adaptBasinFermenting(details, duration);
            case "bulk_fermenting" -> adaptBulkFermenting(details, duration);
            case "compression_molding" -> adaptCompressionMolding(details, duration);
            case "casting" -> adaptCasting(details, duration);
            case "distillation" -> adaptDistillation(details, duration);
            case "diesel_combustion" -> adaptDieselCombustion(details, duration);
            case "hammering", "wire_cutting" -> adaptManualTool(details, duration);
            default -> adaptGenericCDG(details, duration);
        };
    }

    private static boolean adaptBasinFermenting(EmiRecipeConverter.RecipeDetails details, int duration) {
        details.durationTicks = duration > 0 ? duration : 200;
        details.energyType = EnergyType.NONE;
        details.eut = 0.0;
        details.tier = GTVoltageTier.ULV;
        return true;
    }

    private static boolean adaptBulkFermenting(EmiRecipeConverter.RecipeDetails details, int duration) {
        details.durationTicks = duration > 0 ? duration : 200;
        details.energyType = EnergyType.NONE;
        details.eut = 0.0;
        details.tier = GTVoltageTier.ULV;
        return true;
    }

    private static boolean adaptCompressionMolding(EmiRecipeConverter.RecipeDetails details, int duration) {
        details.durationTicks = duration > 0 ? duration : 100;
        details.energyType = EnergyType.KINETIC_SU;
        net.minecraft.world.level.block.Block pressBlock = CreateStressHelper.findBlock(ResourceLocation.tryParse("create:mechanical_press"));
        double impact = CreateStressHelper.getImpact(pressBlock, 8.0);
        details.eut = impact * 32.0;
        details.tier = GTVoltageTier.ULV;
        double durationSec = details.durationTicks / 20.0;
        details.extraInputs.add(IngredientStack.stressUnit(details.eut * durationSec));
        return true;
    }

    private static boolean adaptCasting(EmiRecipeConverter.RecipeDetails details, int duration) {
        details.durationTicks = duration > 0 ? duration : 100;
        details.energyType = EnergyType.NONE;
        details.eut = 0.0;
        details.tier = GTVoltageTier.ULV;
        return true;
    }

    private static boolean adaptDistillation(EmiRecipeConverter.RecipeDetails details, int duration) {
        details.durationTicks = duration > 0 ? duration : 100;
        details.energyType = EnergyType.NONE;
        details.eut = 0.0;
        details.tier = GTVoltageTier.ULV;
        return true;
    }

    private static boolean adaptDieselCombustion(EmiRecipeConverter.RecipeDetails details, int duration) {
        details.durationTicks = duration > 0 ? duration : 20;
        details.energyType = EnergyType.KINETIC_SU;
        details.isGenerator = true;
        EngineSpec spec = deduceEngineSpec(ITEM_DIESEL_ENGINE, 96, 6144.0, 1.0);
        details.eut = spec.totalSu();
        details.tier = GTVoltageTier.LV;
        details.extraOutputs.add(IngredientStack.stressUnit(details.eut));
        return true;
    }

    private static boolean adaptManualTool(EmiRecipeConverter.RecipeDetails details, int duration) {
        details.durationTicks = duration > 0 ? duration : 20;
        details.energyType = EnergyType.NONE;
        details.eut = 0.0;
        details.tier = GTVoltageTier.ULV;
        return true;
    }

    private static boolean adaptGenericCDG(EmiRecipeConverter.RecipeDetails details, int duration) {
        details.durationTicks = duration > 0 ? duration : 100;
        details.energyType = EnergyType.NONE;
        details.eut = 0.0;
        details.tier = GTVoltageTier.ULV;
        return true;
    }

    private static ResourceLocation extractCategoryId(Object emiRecipe, Object backingRecipe) {
        if (emiRecipe instanceof dev.emi.emi.api.recipe.EmiRecipe er && er.getCategory() != null) {
            return er.getCategory().getId();
        }
        return null;
    }

    private static int extractDuration(Object backingRecipe) {
        if (backingRecipe == null) return 0;
        if (GET_PROCESSING_DURATION_METHOD != null) {
            try {
                Object res = GET_PROCESSING_DURATION_METHOD.invoke(backingRecipe);
                if (res instanceof Number n) return n.intValue();
            } catch (Throwable ignored) {}
        }
        if (GET_PROCESSING_TIME_METHOD != null) {
            try {
                Object res = GET_PROCESSING_TIME_METHOD.invoke(backingRecipe);
                if (res instanceof Number n) return n.intValue();
            } catch (Throwable ignored) {}
        }
        return 0;
    }

    private static String extractHeatCondition(Object backingRecipe) {
        if (backingRecipe == null || GET_REQUIRED_HEAT_METHOD == null) return "NONE";
        try {
            Object heatObj = GET_REQUIRED_HEAT_METHOD.invoke(backingRecipe);
            if (heatObj instanceof Enum<?> e) {
                return e.name();
            }
        } catch (Throwable ignored) {}
        return "NONE";
    }

    public record EngineSpec(int rpm, double totalSu, double fuelMbPerSec) {}

    private static RegistryAccess getClientRegistryAccess() {
        try {
            var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                return server.registryAccess();
            }
        } catch (Throwable ignored) {}
        try {
            Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
            Object mc = mcClass.getMethod("getInstance").invoke(null);
            if (mc != null) {
                Object level = mcClass.getField("level").get(mc);
                if (level instanceof net.minecraft.world.level.Level lvl) {
                    return lvl.registryAccess();
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static EngineSpec deduceEngineSpec(ResourceLocation itemId, int fallbackRpm, double fallbackSu, double fallbackBurnMbPerSec) {
        return deduceEngineSpec(itemId, FLUID_DIESEL, fallbackRpm, fallbackSu, fallbackBurnMbPerSec);
    }

    public static EngineSpec deduceEngineSpec(ResourceLocation itemId, ResourceLocation fuelId, int fallbackRpm, double fallbackSu, double fallbackBurnMbPerSec) {
        if (itemId == null || !ModCompatHelper.isCreateDieselGeneratorsLoaded()) {
            return new EngineSpec(fallbackRpm, fallbackSu, fallbackBurnMbPerSec);
        }
        RegistryAccess regAccess = getClientRegistryAccess();
        return deduceEngineSpec(regAccess, itemId, fuelId, fallbackRpm, fallbackSu, fallbackBurnMbPerSec);
    }

    public static EngineSpec deduceEngineSpec(RegistryAccess regAccess, ResourceLocation itemId, ResourceLocation fuelId, int fallbackRpm, double fallbackSu, double fallbackBurnMbPerSec) {
        if (itemId == null || regAccess == null || FUEL_TYPE_REGISTRY_KEY == null || FUEL_NORMAL_METHOD == null || PROP_SPEED_METHOD == null) {
            return new EngineSpec(fallbackRpm, fallbackSu, fallbackBurnMbPerSec);
        }
        try {
            var opt = regAccess.registry(FUEL_TYPE_REGISTRY_KEY);
            if (opt.isEmpty()) {
                return new EngineSpec(fallbackRpm, fallbackSu, fallbackBurnMbPerSec);
            }
            Registry<?> reg = opt.get();
            Object fuelObj = resolveFuelObject(reg, fuelId);
            if (fuelObj == null) {
                return new EngineSpec(fallbackRpm, fallbackSu, fallbackBurnMbPerSec);
            }
            Method propMethod = resolveEnginePropertyMethod(itemId);
            if (propMethod == null) {
                return new EngineSpec(fallbackRpm, fallbackSu, fallbackBurnMbPerSec);
            }
            Object propObj = propMethod.invoke(fuelObj);
            if (propObj == null) {
                return new EngineSpec(fallbackRpm, fallbackSu, fallbackBurnMbPerSec);
            }
            Number speedNum = (Number) PROP_SPEED_METHOD.invoke(propObj);
            Number strengthNum = (Number) PROP_STRENGTH_METHOD.invoke(propObj);
            Number burnNum = (Number) PROP_BURN_METHOD.invoke(propObj);
            int rpm = speedNum != null ? Math.round(speedNum.floatValue()) : fallbackRpm;
            double su = strengthNum != null ? strengthNum.doubleValue() : fallbackSu;
            double burnMbPerSec = burnNum != null ? (burnNum.doubleValue() * 20.0) : fallbackBurnMbPerSec;
            return new EngineSpec(rpm, su, burnMbPerSec);
        } catch (Throwable ignored) {
            return new EngineSpec(fallbackRpm, fallbackSu, fallbackBurnMbPerSec);
        }
    }

    private static Object resolveFuelObject(Registry<?> reg, ResourceLocation fuelId) {
        if (fuelId != null && reg.containsKey(fuelId)) {
            return reg.get(fuelId);
        }
        if (FLUID_DIESEL != null && reg.containsKey(FLUID_DIESEL)) {
            return reg.get(FLUID_DIESEL);
        }
        return reg.size() > 0 ? reg.iterator().next() : null;
    }

    private static Method resolveEnginePropertyMethod(ResourceLocation itemId) {
        if (ITEM_MODULAR_DIESEL_ENGINE.equals(itemId)) {
            return FUEL_MODULAR_METHOD;
        }
        if (ITEM_HUGE_DIESEL_ENGINE.equals(itemId)) {
            return FUEL_HUGE_METHOD;
        }
        return FUEL_NORMAL_METHOD;
    }

    public static RecipeNode createKineticGeneratorNode(ResourceLocation itemId, String displayName) {
        if (itemId == null) return null;
        String path = itemId.getPath();
        return switch (path) {
            case "diesel_engine" -> {
                EngineSpec spec = deduceEngineSpec(itemId, 96, 6144.0, 1.0);
                yield buildEngineNode(itemId, displayName, "Diesel Engine", spec.rpm(), spec.totalSu(), spec.fuelMbPerSec());
            }
            case "large_diesel_engine" -> {
                EngineSpec spec = deduceEngineSpec(itemId, 96, 8192.0, 1.0);
                yield buildEngineNode(itemId, displayName, "Modular Diesel Engine", spec.rpm(), spec.totalSu(), spec.fuelMbPerSec());
            }
            case "huge_diesel_engine" -> {
                EngineSpec spec = deduceEngineSpec(itemId, 224, 16384.0, 1.0);
                yield buildEngineNode(itemId, displayName, "Huge Diesel Engine", spec.rpm(), spec.totalSu(), spec.fuelMbPerSec());
            }
            default -> null;
        };
    }

    private static RecipeNode buildEngineNode(ResourceLocation itemId, String displayName, String fallbackName, int rpm, double totalSu, double fuelMbPerSec) {
        String name = resolveDisplayName(itemId, displayName, fallbackName);
        RecipeNode node = RecipeNode.create(name, 20.0, totalSu, GTVoltageTier.LV);
        node.setEnergyType(EnergyType.KINETIC_SU);
        node.setGenerator(true);
        node.setRpm(rpm);
        node.getProperties().set(CreateProperties.BASE_GENERATOR_RPM, rpm);
        node.setMachineIcon(itemId);
        node.setRecipeCategoryId(CAT_DIESEL_COMBUSTION);
        node.addOutput(IngredientStack.stressUnit(totalSu));
        node.addInput(IngredientStack.fluid(FLUID_DIESEL, "Diesel", fuelMbPerSec, 1.0));
        return node;
    }

    private static String resolveDisplayName(ResourceLocation itemId, String displayName, String fallback) {
        if (displayName != null && !displayName.isEmpty()) return displayName;
        if (itemId != null) {
            try {
                var item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    String name = new ItemStack(item).getHoverName().getString();
                    if (name != null && !name.isEmpty()) return name;
                }
            } catch (Throwable ignored) {}
        }
        return fallback;
    }

    public static List<SearchableRecipe> getVirtualKineticSearchRecipes() {
        if (!ModCompatHelper.isCreateDieselGeneratorsLoaded()) {
            return Collections.emptyList();
        }
        List<SearchableRecipe> list = new ArrayList<>();
        String catId = CAT_DIESEL_COMBUSTION.toString();
        String catName = getCombustionCategoryName();

        ResourceLocation[] items = { ITEM_DIESEL_ENGINE, ITEM_MODULAR_DIESEL_ENGINE, ITEM_HUGE_DIESEL_ENGINE };
        String[] fallbackNames = { "Diesel Engine", "Modular Diesel Engine", "Huge Diesel Engine" };

        for (int i = 0; i < items.length; i++) {
            RecipeNode node = createKineticGeneratorNode(items[i], fallbackNames[i]);
            if (node != null) {
                list.add(buildSearchableRecipe(node, items[i], catId, catName));
            }
        }
        return list;
    }

    private static String getCombustionCategoryName() {
        String catName = Component.translatable("category.gtcalcboard.createdieselgenerators_combustion").getString();
        if (catName.isEmpty() || catName.startsWith("category.gtcalcboard")) {
            return "Diesel Engine Combustion";
        }
        return catName;
    }

    private static SearchableRecipe buildSearchableRecipe(RecipeNode node, ResourceLocation itemId, String catId, String catName) {
        String displayName = node.getName();
        List<String> outputNames = new ArrayList<>();
        List<String> outputIds = new ArrayList<>();
        for (IngredientStack out : node.getOutputs()) {
            outputNames.add(out.getDisplayName().toLowerCase(Locale.ROOT));
            if (out.getId() != null) outputIds.add(out.getId().toString().toLowerCase(Locale.ROOT));
            if (out.isStressUnit()) {
                outputNames.add("stress");
                outputNames.add("unit");
                outputNames.add("su");
                outputNames.add("su/s");
                outputNames.add("kinetic");
                outputNames.add("스트레스");
            }
        }
        outputNames.add(displayName.toLowerCase(Locale.ROOT));
        if (itemId != null) {
            outputIds.add(itemId.toString().toLowerCase(Locale.ROOT));
            outputIds.add(itemId.getPath().toLowerCase(Locale.ROOT));
        }

        List<String> inputNames = new ArrayList<>();
        List<String> inputIds = new ArrayList<>();
        for (IngredientStack in : node.getInputs()) {
            inputNames.add(in.getDisplayName().toLowerCase(Locale.ROOT));
            if (in.getId() != null) inputIds.add(in.getId().toString().toLowerCase(Locale.ROOT));
        }
        inputNames.add("diesel");
        inputNames.add("디젤");
        inputNames.add("fuel");
        inputNames.add("연료");

        String modId = itemId != null ? itemId.getNamespace() : MOD_ID;
        String outputSearchIndex = (String.join(" ", outputNames) + " " + String.join(" ", outputIds)).trim();
        String inputSearchIndex = (String.join(" ", inputNames) + " " + String.join(" ", inputIds) + " " + displayName.toLowerCase(Locale.ROOT) + " kinetic stress units generator create diesel combustion su").trim();

        List<ResourceLocation> inIdsList = new ArrayList<>();
        for (IngredientStack in : node.getInputs()) {
            if (in.getId() != null) inIdsList.add(in.getId());
        }
        List<ResourceLocation> outIdsList = new ArrayList<>();
        for (IngredientStack out : node.getOutputs()) {
            if (out.getId() != null) outIdsList.add(out.getId());
        }
        ResourceLocation[] inArr = inIdsList.isEmpty() ? null : inIdsList.toArray(new ResourceLocation[0]);
        ResourceLocation[] outArr = outIdsList.isEmpty() ? null : outIdsList.toArray(new ResourceLocation[0]);
        String[] inNamesArr = inputNames.isEmpty() ? null : inputNames.toArray(new String[0]);
        String[] outNamesArr = outputNames.isEmpty() ? null : outputNames.toArray(new String[0]);

        return new SearchableRecipe(
                node,
                displayName,
                modId.intern(),
                catId.intern(),
                catName.intern(),
                inputSearchIndex,
                outputSearchIndex,
                inArr,
                outArr,
                inNamesArr,
                outNamesArr
        );
    }

    public static void registerSyntheticEmiRecipes(Object emiRegistryObj, Object emiCategoryObj, java.util.Set<net.minecraft.world.item.Item> activeRecipeItems) {
        if (!ModCompatHelper.isEmiLoaded()) return;
        EmiCDGHelper.registerSyntheticEmiRecipes(emiRegistryObj, emiCategoryObj, activeRecipeItems);
    }

    private static class EmiCDGHelper {
        private static void registerSyntheticEmiRecipes(Object emiRegistryObj, Object emiCategoryObj, java.util.Set<net.minecraft.world.item.Item> activeRecipeItems) {
            if (!(emiRegistryObj instanceof dev.emi.emi.api.EmiRegistry registry)) {
                return;
            }

            var iconItem = ForgeRegistries.ITEMS.getValue(ITEM_DIESEL_ENGINE);
            var iconStack = (iconItem != null && iconItem != net.minecraft.world.item.Items.AIR)
                    ? dev.emi.emi.api.stack.EmiStack.of(iconItem)
                    : dev.emi.emi.api.stack.EmiStack.of(net.minecraft.world.item.Items.FURNACE);
            var category = new dev.emi.emi.api.recipe.EmiRecipeCategory(
                    ResourceLocation.tryParse("gtcalcboard:createdieselgenerators_combustion"),
                    iconStack
            );
            registry.addCategory(category);

            ResourceLocation[] items = { ITEM_DIESEL_ENGINE, ITEM_MODULAR_DIESEL_ENGINE, ITEM_HUGE_DIESEL_ENGINE };
            String[] defaultNames = { "Diesel Engine", "Modular Diesel Engine", "Huge Diesel Engine" };

            for (int i = 0; i < items.length; i++) {
                ResourceLocation itemId = items[i];
                var item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item == null || item == net.minecraft.world.item.Items.AIR) continue;

                if (com.gtceu.calcboard.api.catalog.DynamicAddonCrawler.isItemDisabledOrHidden(
                        item,
                        (activeRecipeItems != null && !activeRecipeItems.isEmpty()) ? activeRecipeItems : null
                )) {
                    continue;
                }

                RecipeNode node = createKineticGeneratorNode(itemId, defaultNames[i]);
                if (node == null) continue;

                var stack = new ItemStack(item);
                String name = stack.getHoverName().getString();
                if (name == null || name.isEmpty()) name = defaultNames[i];

                double amount = node.getBaseEUt();
                List<IngredientStack> outStacks = new ArrayList<>();
                outStacks.add(IngredientStack.stressUnit(amount));

                final String finalName = name;
                var recipe = new com.gtceu.calcboard.integration.emi.KineticGenerationEmiRecipe(
                        ResourceLocation.tryParse("gtcalcboard:kinetic_gen/" + itemId.getNamespace() + "/" + itemId.getPath()),
                        category,
                        itemId,
                        name,
                        20.0,
                        amount,
                        GTVoltageTier.LV,
                        EnergyType.KINETIC_SU,
                        true,
                        node.getInputs(),
                        outStacks,
                        stack,
                        () -> createKineticGeneratorNode(itemId, finalName)
                );

                registry.addWorkstation(category, dev.emi.emi.api.stack.EmiStack.of(stack));
                registry.addRecipe(recipe);
            }
        }
    }
}
