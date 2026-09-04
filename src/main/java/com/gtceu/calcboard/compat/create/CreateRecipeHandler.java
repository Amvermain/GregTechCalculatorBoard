package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.catalog.DynamicAddonCrawler;
import com.gtceu.calcboard.api.util.ModCompatHelper;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Handles Create recipe adaptation and kinetic generator definitions.
 */
public class CreateRecipeHandler {

    public static final String MOD_ID = "create";

    public static boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        ResourceLocation catId = null;
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            catId = EmiCreateHelper.getCategoryId(emiRecipe);
        }
        if (catId != null && com.gtceu.calcboard.api.util.ModCompatHelper.isCreateFamilyNamespace(catId.getNamespace())) {
            if ("create_new_age".equals(catId.getNamespace())) return false; // Handled by CreateNewAge
            if ("liquid_burning".equals(catId.getPath())) return false; // Liquid burner produces FE from fuel
            details.energyType = EnergyType.KINETIC_SU;
            details.tier = GTVoltageTier.ULV;

            int duration = 0;
            if (backingRecipe != null) {
                try {
                    var getProcessingDurationMethod = backingRecipe.getClass().getMethod("getProcessingDuration");
                    duration = (int) getProcessingDurationMethod.invoke(backingRecipe);
                } catch (Throwable ignored) {
                    try {
                        var getProcessingTimeMethod = backingRecipe.getClass().getMethod("getProcessingTime");
                        duration = (int) getProcessingTimeMethod.invoke(backingRecipe);
                    } catch (Throwable ignored2) {
                        try {
                            var getDurationMethod = backingRecipe.getClass().getMethod("getDuration");
                            duration = (int) getDurationMethod.invoke(backingRecipe);
                        } catch (Throwable ignored3) {}
                    }
                }
            }

            KineticCategorySpec spec = resolveCategorySpec(catId.getPath().toLowerCase(Locale.ROOT));
            if (duration <= 0) {
                duration = spec.defaultDurationTicks();
            }
            details.durationTicks = duration;
            details.eut = spec.baseStressAt32Rpm();

            double durationSec = details.durationTicks / 20.0;
            double suPerBatch = details.eut * durationSec;
            details.extraInputs.add(IngredientStack.stressUnit(suPerBatch));
            return true;
        }
        return false;
    }

    private record KineticCategorySpec(int defaultDurationTicks, double baseStressAt32Rpm) {}

    private static final java.util.Map<String, KineticCategorySpec> CATEGORY_SPEC_MAP = new java.util.HashMap<>();
    static {
        KineticCategorySpec fanSpec = new KineticCategorySpec(150, 128.0);
        CATEGORY_SPEC_MAP.put("splashing", fanSpec);
        CATEGORY_SPEC_MAP.put("washing", fanSpec);
        CATEGORY_SPEC_MAP.put("haunting", fanSpec);
        CATEGORY_SPEC_MAP.put("smoking", fanSpec);
        CATEGORY_SPEC_MAP.put("blasting", fanSpec);

        KineticCategorySpec pressSpec = new KineticCategorySpec(200, 256.0);
        CATEGORY_SPEC_MAP.put("pressing", pressSpec);
        CATEGORY_SPEC_MAP.put("compacting", pressSpec);
        CATEGORY_SPEC_MAP.put("curving", pressSpec);

        CATEGORY_SPEC_MAP.put("hammering", new KineticCategorySpec(100, 512.0));

        KineticCategorySpec heavySpec = new KineticCategorySpec(100, 256.0);
        CATEGORY_SPEC_MAP.put("crushing", heavySpec);
        CATEGORY_SPEC_MAP.put("rolling", heavySpec);
        CATEGORY_SPEC_MAP.put("lathe", heavySpec);
        CATEGORY_SPEC_MAP.put("turning", heavySpec);
        CATEGORY_SPEC_MAP.put("laser_cutting", heavySpec);
        CATEGORY_SPEC_MAP.put("pressurizing", heavySpec);

        CATEGORY_SPEC_MAP.put("polishing", new KineticCategorySpec(100, 64.0));

        KineticCategorySpec mediumSpec = new KineticCategorySpec(100, 128.0);
        CATEGORY_SPEC_MAP.put("milling", mediumSpec);
        CATEGORY_SPEC_MAP.put("vibrating", mediumSpec);
        CATEGORY_SPEC_MAP.put("centrifugation", mediumSpec);
    }

    private static KineticCategorySpec resolveCategorySpec(String categoryPath) {
        if (categoryPath != null) {
            for (java.util.Map.Entry<String, KineticCategorySpec> entry : CATEGORY_SPEC_MAP.entrySet()) {
                if (categoryPath.contains(entry.getKey())) {
                    return entry.getValue();
                }
            }
        }
        return new KineticCategorySpec(100, 128.0);
    }

    public static RecipeNode createKineticGeneratorNode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId == null) return null;
        return createKineticGeneratorNode(itemId, stack.getHoverName().getString());
    }

    public static String getItemDisplayName(ResourceLocation itemId, String fallback) {
        if (itemId != null) {
            try {
                var item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item != null && item != net.minecraft.world.item.Items.AIR) {
                    String name = new ItemStack(item).getHoverName().getString();
                    if (name != null && !name.isEmpty()) {
                        return name;
                    }
                }
            } catch (Throwable ignored) {}
        }
        return fallback != null ? fallback : (itemId != null ? itemId.getPath() : "");
    }

    private static RecipeNode buildKineticNode(ResourceLocation itemId, String name, double fallbackCap, int fallbackRpm, GTVoltageTier tier) {
        CreateStressHelper.KineticStats stats = CreateStressHelper.deduceGeneratorStats(itemId, fallbackCap, fallbackRpm);
        return buildDynamicKineticNode(itemId, name, stats.capacityPerRpm(), stats.rpm(), stats.greateTier(), false);
    }

    private static RecipeNode buildDynamicKineticNode(ResourceLocation itemId, String name, double capPerRpm, int rpm, int tier, boolean isSteam) {
        double totalSu = capPerRpm * rpm;
        RecipeNode node = RecipeNode.create(name != null ? name : (itemId != null ? itemId.getPath() : ""), 20.0, totalSu, GTVoltageTier.LV);
        node.setEnergyType(EnergyType.KINETIC_SU);
        node.setGenerator(true);
        node.setRpm(rpm);
        node.getProperties().set(CreateProperties.BASE_GENERATOR_RPM, rpm);
        node.setMachineIcon(itemId);
        node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
        node.addOutput(IngredientStack.stressUnit(totalSu));
        if (tier >= 0) {
            node.getProperties().set(com.gtceu.calcboard.compat.greate.GreateProperties.IS_GREATE, true);
            node.getProperties().set(com.gtceu.calcboard.compat.greate.GreateProperties.MACHINE_TIER, tier);
        }
        if (isSteam) {
            double steamMbPerSec = CreateStressHelper.calculateSteamConsumption(tier);
            node.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", steamMbPerSec, 1.0));
        }
        return node;
    }

    public static RecipeNode createKineticGeneratorNode(ResourceLocation itemId, String displayName) {
        if (itemId == null) return null;
        String path = itemId.getPath();
        String namespace = itemId.getNamespace();
        String name = getItemDisplayName(itemId, displayName);

        net.minecraft.world.level.block.Block block = CreateStressHelper.findBlock(itemId);
        int tier = CreateStressHelper.getGreateTier(block);
        boolean isSteam = CreateStressHelper.isSteamEngine(block);

        if (isSteam) {
            double totalSu = CreateStressHelper.calculateSteamEngineTotalSu(tier);
            int rpm = (tier < 0) ? 64 : 16;
            double cap = totalSu / rpm;
            return buildDynamicKineticNode(itemId, name, cap, rpm, tier, true);
        }

        double cap = CreateStressHelper.getCapacity(block, 0.0);
        if (cap > 0.0) {
            int rpm = CreateStressHelper.getGeneratedRpm(block, 16);
            return buildDynamicKineticNode(itemId, name, cap, rpm, tier, false);
        }

        if (namespace.equals("create")) {
            return createCreateFallbackNode(itemId, path, name);
        } else if (namespace.equals("createaddition")) {
            return createCreateAdditionNode(itemId, path, name);
        }
        return null;
    }

    private static RecipeNode createCreateFallbackNode(ResourceLocation itemId, String path, String name) {
        if (path.equals("large_water_wheel")) {
            return buildKineticNode(itemId, name != null ? name : "Large Water Wheel", 128.0, 4, GTVoltageTier.LV);
        } else if (path.equals("water_wheel")) {
            return buildKineticNode(itemId, name != null ? name : "Water Wheel", 32.0, 8, GTVoltageTier.LV);
        } else if (path.equals("windmill_bearing")) {
            return buildKineticNode(itemId, name != null ? name : "Windmill Bearing", 32.0, 16, GTVoltageTier.LV);
        } else if (path.equals("steam_engine")) {
            RecipeNode node = buildKineticNode(itemId, name != null ? name : "Steam Engine", 32.0, 64, GTVoltageTier.LV);
            node.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 320.0, 1.0));
            return node;
        } else if (path.equals("hand_crank")) {
            return buildKineticNode(itemId, name != null ? name : "Hand Crank", 16.0, 16, GTVoltageTier.LV);
        } else if (path.equals("creative_motor")) {
            return buildKineticNode(itemId, name != null ? name : "Creative Motor", 1024.0, 16, GTVoltageTier.LV);
        }
        return null;
    }

    private static RecipeNode createCreateAdditionNode(ResourceLocation itemId, String path, String name) {
        if (path.equals("alternator")) {
            RecipeNode node = RecipeNode.create(name != null ? name : "Alternator", 20.0, 256.0, GTVoltageTier.ULV);
            node.setEnergyType(EnergyType.ELECTRIC_FE);
            node.setGenerator(true);
            node.setMachineIcon(itemId);
            node.setRecipeCategoryId(ResourceLocation.tryParse("createaddition:alternator"));
            node.addInput(IngredientStack.stressUnit(256.0));
            return node;
        } else if (path.equals("electric_motor")) {
            RecipeNode node = RecipeNode.create(name != null ? name : "Electric Motor", 20.0, 512.0, GTVoltageTier.ULV);
            node.setEnergyType(EnergyType.ELECTRIC_FE);
            node.setGenerator(false);
            node.setMachineIcon(itemId);
            node.setRecipeCategoryId(ResourceLocation.tryParse("createaddition:electric_motor"));
            node.addOutput(IngredientStack.stressUnit(1024.0));
            return node;
        }
        return null;
    }

    public static List<SearchableRecipe> getVirtualKineticSearchRecipes() {
        if (!ModCompatHelper.isCreateLoaded() && !ModCompatHelper.isCreateAdditionsLoaded()) {
            return Collections.emptyList();
        }
        List<SearchableRecipe> list = new ArrayList<>();
        String catId = "create:kinetic_generation";
        String catName = getKineticCategoryName();

        List<CreateStressHelper.DiscoveredGenerator> discovered = CreateStressHelper.discoverGenerators();
        if (!discovered.isEmpty()) {
            for (CreateStressHelper.DiscoveredGenerator gen : discovered) {
                RecipeNode node = createKineticGeneratorNode(gen.id(), null);
                if (node != null) {
                    list.add(buildSearchableRecipe(node, gen.id(), catId, catName));
                }
            }
        } else {
            addFallbackSearchRecipes(list, catId, catName);
        }

        addCreateAdditionSearchRecipes(list, catId, catName);
        return list;
    }

    private static String getKineticCategoryName() {
        String catName = Component.translatable("category.gtcalcboard.create_kinetic").getString();
        if (catName.isEmpty() || catName.startsWith("category.gtcalcboard")) {
            return "Create Kinetic";
        }
        return catName;
    }

    private static void addFallbackSearchRecipes(List<SearchableRecipe> list, String catId, String catName) {
        ResourceLocation[] items = {
                ResourceLocation.tryParse("create:large_water_wheel"),
                ResourceLocation.tryParse("create:water_wheel"),
                ResourceLocation.tryParse("create:windmill_bearing"),
                ResourceLocation.tryParse("create:steam_engine"),
                ResourceLocation.tryParse("create:hand_crank"),
                ResourceLocation.tryParse("create:creative_motor")
        };
        String[] fallbackNames = {
                "Large Water Wheel",
                "Water Wheel",
                "Windmill Bearing",
                "Steam Engine",
                "Hand Crank",
                "Creative Motor"
        };
        for (int i = 0; i < items.length; i++) {
            RecipeNode node = createKineticGeneratorNode(items[i], fallbackNames[i]);
            if (node != null) {
                list.add(buildSearchableRecipe(node, items[i], catId, catName));
            }
        }
    }

    private static void addCreateAdditionSearchRecipes(List<SearchableRecipe> list, String catId, String catName) {
        if (!ModCompatHelper.isCreateAdditionsLoaded()) return;
        ResourceLocation[] caItems = {
                ResourceLocation.tryParse("createaddition:alternator"),
                ResourceLocation.tryParse("createaddition:electric_motor")
        };
        String[] caNames = {"Alternator", "Electric Motor"};
        for (int i = 0; i < caItems.length; i++) {
            RecipeNode node = createKineticGeneratorNode(caItems[i], caNames[i]);
            if (node != null) {
                list.add(buildSearchableRecipe(node, caItems[i], catId, catName));
            }
        }
    }

    private static SearchableRecipe buildSearchableRecipe(RecipeNode node, ResourceLocation itemId, String catId, String catName) {
        String displayName = node.getName();
        String modId = itemId != null ? itemId.getNamespace() : "create";

        List<String> outputNames = new ArrayList<>();
        List<String> outputIds = new ArrayList<>();
        for (IngredientStack out : node.getOutputs()) {
            outputNames.add(out.getDisplayName().toLowerCase(Locale.ROOT));
            if (out.getId() != null) outputIds.add(out.getId().toString().toLowerCase(Locale.ROOT));
            if (out.isStressUnit()) {
                outputNames.add("stress");
                outputNames.add("unit");
                outputNames.add("units");
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
            if (in.isStressUnit()) {
                inputNames.add("stress");
                inputNames.add("unit");
                inputNames.add("units");
                inputNames.add("su");
                inputNames.add("su/s");
                inputNames.add("kinetic");
                inputNames.add("스트레스");
            }
        }
        inputNames.add(displayName.toLowerCase(Locale.ROOT));
        if (itemId != null) {
            inputIds.add(itemId.toString().toLowerCase(Locale.ROOT));
            inputIds.add(itemId.getPath().toLowerCase(Locale.ROOT));
        }

        String outputSearchIndex = (String.join(" ", outputNames) + " " + String.join(" ", outputIds)).trim();
        String inputSearchIndex = (String.join(" ", inputNames) + " " + String.join(" ", inputIds) + " " + displayName.toLowerCase(Locale.ROOT) + " kinetic stress units generator create su").trim();

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


    public static double getDynamicStressCapacity(net.minecraft.world.level.block.Block block, double fallback) {
        if (block == null) return fallback;
        try {
            Class<?> bsvClass = Class.forName("com.simibubi.create.content.kinetics.BlockStressValues");
            java.lang.reflect.Method getCapacityMethod = bsvClass.getMethod("getCapacity", net.minecraft.world.level.block.Block.class);
            Object res = getCapacityMethod.invoke(null, block);
            if (res instanceof Number num) {
                double cap = num.doubleValue();
                if (cap > 0) return cap;
            }
        } catch (Throwable ignored) {}
        return fallback;
    }

    public static void registerSyntheticEmiRecipes(Object emiRegistryObj, Object emiCategoryObj, java.util.Set<net.minecraft.world.item.Item> activeRecipeItems) {
        if (!com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) return;
        EmiCreateHelper.registerSyntheticEmiRecipes(emiRegistryObj, emiCategoryObj, activeRecipeItems);
    }

    private static class EmiCreateHelper {
        private static ResourceLocation getCategoryId(Object emiRecipe) {
            if (emiRecipe instanceof dev.emi.emi.api.recipe.EmiRecipe recipe && recipe.getCategory() != null) {
                return recipe.getCategory().getId();
            }
            return null;
        }

        private static void registerSyntheticEmiRecipes(Object emiRegistryObj, Object emiCategoryObj, java.util.Set<net.minecraft.world.item.Item> activeRecipeItems) {
            if (!(emiRegistryObj instanceof dev.emi.emi.api.EmiRegistry registry) || !(emiCategoryObj instanceof dev.emi.emi.api.recipe.EmiRecipeCategory category)) {
                return;
            }

            record KineticCandidate(String modId, String path, String defaultName, double defaultAmount, boolean isGen, List<IngredientStack> inputs, EnergyType energyType) {}

            List<KineticCandidate> candidates = List.of(
                    new KineticCandidate("create", "large_water_wheel", "Large Water Wheel", 512.0, true, null, EnergyType.KINETIC_SU),
                    new KineticCandidate("create", "water_wheel", "Water Wheel", 256.0, true, null, EnergyType.KINETIC_SU),
                    new KineticCandidate("create", "windmill_bearing", "Windmill Bearing", 512.0, true, null, EnergyType.KINETIC_SU),
                    new KineticCandidate("create", "steam_engine", "Steam Engine", 2048.0, true,
                            List.of(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 200.0)), EnergyType.KINETIC_SU),
                    new KineticCandidate("create", "hand_crank", "Hand Crank", 256.0, true, null, EnergyType.KINETIC_SU),
                    new KineticCandidate("create", "creative_motor", "Creative Motor", 16384.0, true, null, EnergyType.KINETIC_SU),
                    new KineticCandidate("createaddition", "alternator", "Alternator", 256.0, true,
                            List.of(IngredientStack.stressUnit(256.0)), EnergyType.ELECTRIC_FE),
                    new KineticCandidate("createaddition", "electric_motor", "Electric Motor", 1024.0, false,
                            List.of(), EnergyType.ELECTRIC_FE)
            );

            for (KineticCandidate c : candidates) {
                var itemId = ResourceLocation.tryParse(c.modId + ":" + c.path);
                var item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item == null || item == net.minecraft.world.item.Items.AIR) continue;

                boolean skip = com.gtceu.calcboard.api.catalog.DynamicAddonCrawler.isItemDisabledOrHidden(
                        item,
                        (c.path.contains("creative") || c.path.equals("hand_crank") || activeRecipeItems == null || activeRecipeItems.isEmpty()) ? null : activeRecipeItems
                );
                if (skip) continue;

                var block = ForgeRegistries.BLOCKS.getValue(itemId);
                double amount = getDynamicStressCapacity(block, c.defaultAmount);

                var stack = new ItemStack(item);
                String name = stack.getHoverName().getString();
                if (name == null || name.isEmpty()) name = c.defaultName;

                List<IngredientStack> outStacks = new ArrayList<>();
                if (c.energyType == EnergyType.KINETIC_SU) {
                    outStacks.add(IngredientStack.stressUnit(amount));
                }

                var recipe = new com.gtceu.calcboard.integration.emi.KineticGenerationEmiRecipe(
                        ResourceLocation.tryParse("gtcalcboard:kinetic_gen/" + c.modId + "/" + c.path),
                        category,
                        itemId,
                        name,
                        20.0,
                        amount,
                        GTVoltageTier.LV,
                        c.energyType,
                        c.isGen,
                        c.inputs != null ? c.inputs : List.of(),
                        outStacks,
                        stack
                );

                registry.addWorkstation(category, dev.emi.emi.api.stack.EmiStack.of(stack));
                registry.addRecipe(recipe);
            }
        }
    }
}



