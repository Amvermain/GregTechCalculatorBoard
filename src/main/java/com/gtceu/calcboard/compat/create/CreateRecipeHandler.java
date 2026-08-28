package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.catalog.DynamicAddonCrawler;
import com.gtceu.calcboard.api.util.ModCompatHelper;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
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
        if (catId != null && (catId.getNamespace().equals(MOD_ID) || catId.getNamespace().equals("createaddition"))) {
            if (catId.getPath().contains("liquid_burning")) return false; // Liquid burner produces FE from fuel
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
                    } catch (Throwable ignored2) {}
                }
            }

            String path = catId.getPath().toLowerCase(Locale.ROOT);
            if (duration <= 0) {
                if (path.contains("splashing") || path.contains("washing") || path.contains("haunting") || path.contains("smoking") || path.contains("blasting")) {
                    duration = 150; // Create FanProcessing standard default is 150 ticks (7.5s)
                } else if (path.contains("pressing") || path.contains("compacting")) {
                    duration = 200; // Create Press default is 200 ticks (10.0s)
                } else if (path.contains("crushing") || path.contains("milling")) {
                    duration = 100; // Create Crushing/Milling default is 100 ticks (5.0s)
                } else {
                    duration = 100;
                }
            }
            details.durationTicks = duration;

            if (path.contains("crushing") || path.contains("pressing") || path.contains("compacting") || path.contains("rolling")) {
                details.eut = 256.0; // 8x RPM at 32 RPM
            } else if (path.contains("polishing")) {
                details.eut = 64.0;  // 2x RPM at 32 RPM
            } else {
                details.eut = 128.0; // 4x RPM at 32 RPM (milling, mixing, cutting, fan washing, etc.)
            }
            double durationSec = details.durationTicks / 20.0;
            double suPerBatch = details.eut * durationSec;
            details.extraInputs.add(IngredientStack.stressUnit(suPerBatch));
            return true;
        }
        return false;
    }

    public static RecipeNode createKineticGeneratorNode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
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

    public static RecipeNode createKineticGeneratorNode(ResourceLocation itemId, String displayName) {
        if (itemId == null) return null;
        String path = itemId.getPath();
        String namespace = itemId.getNamespace();
        String name = getItemDisplayName(itemId, displayName);

        if (namespace.equals("create")) {
            if (path.equals("large_water_wheel")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Large Water Wheel", 20.0, 512.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
                node.addOutput(IngredientStack.stressUnit(512.0));
                return node;
            } else if (path.equals("water_wheel")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Water Wheel", 20.0, 256.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
                node.addOutput(IngredientStack.stressUnit(256.0));
                return node;
            } else if (path.equals("windmill_bearing")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Windmill Bearing", 20.0, 512.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
                node.addOutput(IngredientStack.stressUnit(512.0));
                return node;
            } else if (path.equals("steam_engine")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Steam Engine", 20.0, 2048.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
                node.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 200.0, 1.0));
                node.addOutput(IngredientStack.stressUnit(2048.0));
                return node;
            } else if (path.equals("hand_crank")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Hand Crank", 20.0, 256.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
                node.addOutput(IngredientStack.stressUnit(256.0));
                return node;
            } else if (path.equals("creative_motor")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Creative Motor", 20.0, 16384.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
                node.addOutput(IngredientStack.stressUnit(16384.0));
                return node;
            }
        } else if (namespace.equals("createaddition")) {
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
        }
        return null;
    }

    public static List<RecipeSearchEngine.SearchableRecipe> getVirtualKineticSearchRecipes() {
        if (!ModCompatHelper.isCreateLoaded() && !ModCompatHelper.isCreateAdditionsLoaded()) {
            return Collections.emptyList();
        }
        List<RecipeSearchEngine.SearchableRecipe> list = new ArrayList<>();
        String catId = "create:kinetic_generation";
        String catName = Component.translatable("category.gtcalcboard.create_kinetic").getString();
        if (catName.isEmpty() || catName.startsWith("category.gtcalcboard")) {
            catName = "Create Kinetic";
        }

        ResourceLocation[] items = {
                ResourceLocation.tryParse("create:large_water_wheel"),
                ResourceLocation.tryParse("create:water_wheel"),
                ResourceLocation.tryParse("create:windmill_bearing"),
                ResourceLocation.tryParse("create:steam_engine"),
                ResourceLocation.tryParse("create:hand_crank"),
                ResourceLocation.tryParse("create:creative_motor"),
                ResourceLocation.tryParse("createaddition:alternator"),
                ResourceLocation.tryParse("createaddition:electric_motor")
        };

        String[] fallbackNames = {
                "Large Water Wheel",
                "Water Wheel",
                "Windmill Bearing",
                "Steam Engine",
                "Hand Crank",
                "Creative Motor",
                "Alternator",
                "Electric Motor"
        };

        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) continue;
            String itemMod = items[i].getNamespace();
            if ("create".equals(itemMod) && !ModCompatHelper.isCreateLoaded()) continue;
            if ("createaddition".equals(itemMod) && !ModCompatHelper.isCreateAdditionsLoaded()) continue;
            try {
                var regItem = net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(items[i]);
                if (regItem != null && regItem != net.minecraft.world.item.Items.AIR && com.gtceu.calcboard.api.catalog.DynamicAddonCrawler.isItemDisabledOrHidden(regItem, null)) {
                    continue;
                }
            } catch (Throwable ignored) {}
            RecipeNode node = createKineticGeneratorNode(items[i], fallbackNames[i]);
            if (node != null) {
                String displayName = node.getName();
                String modId = items[i].getNamespace();

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
                outputNames.add(fallbackNames[i].toLowerCase(Locale.ROOT));
                if (items[i] != null) {
                    outputIds.add(items[i].toString().toLowerCase(Locale.ROOT));
                    outputIds.add(items[i].getPath().toLowerCase(Locale.ROOT));
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
                inputNames.add(fallbackNames[i].toLowerCase(Locale.ROOT));
                if (items[i] != null) {
                    inputIds.add(items[i].toString().toLowerCase(Locale.ROOT));
                    inputIds.add(items[i].getPath().toLowerCase(Locale.ROOT));
                }

                String outputSearchIndex = (String.join(" ", outputNames) + " " + String.join(" ", outputIds)).trim();
                String inputSearchIndex = (String.join(" ", inputNames) + " " + String.join(" ", inputIds) + " " + fallbackNames[i].toLowerCase(Locale.ROOT) + " " + displayName.toLowerCase(Locale.ROOT) + " kinetic stress units generator create su").trim();

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

                list.add(new RecipeSearchEngine.SearchableRecipe(
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
                ));
            }
        }
        return list;
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
                var itemId = new ResourceLocation(c.modId, c.path);
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
                        new ResourceLocation("gtcalcboard", "kinetic_gen/" + c.modId + "/" + c.path),
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



