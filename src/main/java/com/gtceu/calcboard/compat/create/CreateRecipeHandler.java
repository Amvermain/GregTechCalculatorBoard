package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.catalog.DynamicAddonCrawler;
import com.gtceu.calcboard.api.util.ModCompatHelper;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.api.model.RecipeDetails;
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

    public static boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, RecipeDetails details) {
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

            details.extraInputs.add(IngredientStack.stressUnit(details.eut));
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

    public static RecipeNode createCreateBoilerNode() {
        return createKineticGeneratorNode(ResourceLocation.tryParse("create:steam_engine"), "Steam Boiler (Create)");
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
            node.getProperties().set(CreateProperties.IS_CREATE_BOILER, true);
            node.getProperties().set(CreateProperties.BOILER_LEVEL, 0);
            node.getProperties().set(CreateProperties.BOILER_WATER_MODE, true);
            node.setRpm(16);
            node.getProperties().set(CreateProperties.BASE_GENERATOR_RPM, 16);
            node.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 200.0, 1.0));
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

        if ("create".equals(namespace) && "windmill_bearing".equals(path)) {
            RecipeNode node = buildDynamicKineticNode(itemId, name, 512.0, 1, -1, false);
            CreateProperties.applyWindmillSails(node, 8);
            return node;
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
            RecipeNode node = buildKineticNode(itemId, name != null ? name : "Windmill Bearing", 512.0, 1, GTVoltageTier.LV);
            CreateProperties.applyWindmillSails(node, 8);
            return node;
        } else if (path.equals("steam_engine")) {
            return buildDynamicKineticNode(itemId, name != null ? name : "Steam Engine", 32.0, 64, -1, true);
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
    public static void collectNativeCatalogRecipes(List<SearchableRecipe> collector) {
        if (!ModCompatHelper.isCreateLoaded() && !ModCompatHelper.isCreateAdditionsLoaded()) return;

        record KineticCandidate(String modId, String path, String defaultName, KineticCategory category) {}

        List<KineticCandidate> candidates = List.of(
                new KineticCandidate("create", "large_water_wheel", "Large Water Wheel", KineticCategory.SOURCE),
                new KineticCandidate("create", "water_wheel", "Water Wheel", KineticCategory.SOURCE),
                new KineticCandidate("create", "windmill_bearing", "Windmill Bearing", KineticCategory.SOURCE),
                new KineticCandidate("create", "hand_crank", "Hand Crank", KineticCategory.SOURCE),
                new KineticCandidate("create", "creative_motor", "Creative Motor", KineticCategory.SOURCE),
                new KineticCandidate("create", "steam_engine", "Steam Engine", KineticCategory.FUEL_ENGINE),
                new KineticCandidate("createaddition", "alternator", "Alternator", KineticCategory.ALTERNATOR),
                new KineticCandidate("createaddition", "electric_motor", "Electric Motor", KineticCategory.MOTOR)
        );

        for (KineticCandidate c : candidates) {
            ResourceLocation itemId = ResourceLocation.tryParse(c.modId + ":" + c.path);
            String name = c.defaultName;

            if (isRealModLoaded(c.modId) && ForgeRegistries.ITEMS != null) {
                var item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item == null || item == net.minecraft.world.item.Items.AIR) continue;
                if (DynamicAddonCrawler.isItemDisabledOrHidden(item, null)) continue;
                String hover = new ItemStack(item).getHoverName().getString();
                if (hover != null && !hover.isEmpty()) {
                    name = hover;
                }
            }

            final String finalName = name;
            RecipeNode templateNode = createKineticGeneratorNode(itemId, finalName);
            if (templateNode == null) continue;

            String catId = c.category.getCategoryId().toString();
            String catName = Component.translatable(c.category.getLangKey()).getString();

            collector.add(com.gtceu.calcboard.api.catalog.NativeCatalogSearchHelper.createRecipe(
                    templateNode,
                    itemId,
                    catId,
                    catName,
                    () -> createKineticGeneratorNode(itemId, finalName)
            ));
        }
    }

    private static boolean isRealModLoaded(String modId) {
        try {
            var list = net.minecraftforge.fml.ModList.get();
            return list != null && list.isLoaded(modId);
        } catch (Throwable t) {
            return false;
        }
    }

    private static class EmiCreateHelper {
        private static ResourceLocation getCategoryId(Object emiRecipe) {
            if (emiRecipe instanceof dev.emi.emi.api.recipe.EmiRecipe recipe && recipe.getCategory() != null) {
                return recipe.getCategory().getId();
            }
            return null;
        }
    }
}



