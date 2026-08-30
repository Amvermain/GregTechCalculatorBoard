package com.gtceu.calcboard.compat.createnewage;

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
 * Handles Create: New Age recipe parsing, energising processing, and generator/motor node creation.
 */
public class CreateNewAgeRecipeHandler {

    public static final String MOD_ID = "create_new_age";

    public static boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        ResourceLocation catId = null;
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            catId = EmiCreateNewAgeHelper.getCategoryId(emiRecipe);
        }
        if (catId != null && catId.getNamespace().equals(MOD_ID)) {
            String path = catId.getPath().toLowerCase(Locale.ROOT);

            if (path.contains("energising") || path.contains("energizing")) {
                details.energyType = EnergyType.ELECTRIC_FE;
                int duration = 100;
                double energy = 5000.0;
                if (backingRecipe != null) {
                    try {
                        var getEnergyMethod = backingRecipe.getClass().getMethod("getEnergy");
                        energy = ((Number) getEnergyMethod.invoke(backingRecipe)).doubleValue();
                    } catch (Throwable ignored) {
                        try {
                            var energyField = backingRecipe.getClass().getField("energy");
                            energy = ((Number) energyField.get(backingRecipe)).doubleValue();
                        } catch (Throwable ignored2) {}
                    }
                    try {
                        var getDurationMethod = backingRecipe.getClass().getMethod("getDuration");
                        duration = (int) getDurationMethod.invoke(backingRecipe);
                    } catch (Throwable ignored) {
                        try {
                            var durationField = backingRecipe.getClass().getField("duration");
                            duration = (int) durationField.get(backingRecipe);
                        } catch (Throwable ignored2) {}
                    }
                }
                details.durationTicks = duration > 0 ? duration : 100;
                details.eut = energy / (double) details.durationTicks;
                details.tier = GTVoltageTier.getTierForVoltage((long) (details.eut / 4.0));
                return true;
            }

            details.energyType = EnergyType.KINETIC_SU;
            details.tier = GTVoltageTier.ULV;
            details.durationTicks = 100;
            details.eut = 128.0;
            details.extraInputs.add(IngredientStack.stressUnit(128.0 * (100.0 / 20.0)));
            return true;
        }
        return false;
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

    public static RecipeNode createKineticGeneratorNode(ResourceLocation itemId, String displayName) {
        if (itemId == null) return null;
        String path = itemId.getPath();
        String namespace = itemId.getNamespace();
        String name = getItemDisplayName(itemId, displayName);

        if (namespace.equals(MOD_ID)) {
            if (path.contains("generator_coil")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Generator Coil", 20.0, 512.0, GTVoltageTier.ULV);
                node.setEnergyType(EnergyType.ELECTRIC_FE);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create_new_age:generator"));
                node.addInput(IngredientStack.stressUnit(768.0)); // 24.0 base stress * 32 RPM
                return node;
            } else if (path.contains("carbon_brushes")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Carbon Brushes", 20.0, 256.0, GTVoltageTier.ULV);
                node.setEnergyType(EnergyType.ELECTRIC_FE);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create_new_age:generator"));
                node.addInput(IngredientStack.stressUnit(768.0));
                return node;
            } else if (path.equals("basic_motor")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Basic Motor", 20.0, 256.0, GTVoltageTier.ULV);
                node.setEnergyType(EnergyType.ELECTRIC_FE);
                node.setGenerator(false);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create_new_age:motor"));
                node.addOutput(IngredientStack.stressUnit(512.0));
                return node;
            } else if (path.equals("advanced_motor")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Advanced Motor", 20.0, 1024.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.ELECTRIC_FE);
                node.setGenerator(false);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create_new_age:motor"));
                node.addOutput(IngredientStack.stressUnit(2048.0));
                return node;
            } else if (path.equals("reinforced_motor")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Reinforced Motor", 20.0, 4096.0, GTVoltageTier.MV);
                node.setEnergyType(EnergyType.ELECTRIC_FE);
                node.setGenerator(false);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create_new_age:motor"));
                node.addOutput(IngredientStack.stressUnit(8192.0));
                return node;
            } else if (path.equals("stirling_engine")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Stirling Engine", 20.0, 1024.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create_new_age:stirling_engine"));
                node.addOutput(IngredientStack.stressUnit(1024.0));
                return node;
            } else if (path.contains("solar_heating_plate")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Solar Heating Plate", 20.0, 256.0, GTVoltageTier.ULV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create_new_age:solar_heat"));
                node.addOutput(IngredientStack.stressUnit(256.0));
                return node;
            } else if (path.contains("energiser")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Energiser", 20.0, 256.0, GTVoltageTier.ULV);
                node.setEnergyType(EnergyType.ELECTRIC_FE);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create_new_age:energising"));
                return node;
            }
        }
        return null;
    }

    public static List<RecipeSearchEngine.SearchableRecipe> getVirtualSearchRecipes() {
        if (!ModCompatHelper.isCreateNewAgeLoaded()) {
            return Collections.emptyList();
        }
        List<RecipeSearchEngine.SearchableRecipe> list = new ArrayList<>();
        String catId = "create_new_age:generation";
        String catName = Component.translatable("category.gtcalcboard.create_new_age").getString();
        if (catName.isEmpty() || catName.startsWith("category.gtcalcboard")) {
            catName = "Create: New Age";
        }

        ResourceLocation[] items = {
                ResourceLocation.tryParse("create_new_age:generator_coil"),
                ResourceLocation.tryParse("create_new_age:carbon_brushes"),
                ResourceLocation.tryParse("create_new_age:basic_motor"),
                ResourceLocation.tryParse("create_new_age:advanced_motor"),
                ResourceLocation.tryParse("create_new_age:reinforced_motor"),
                ResourceLocation.tryParse("create_new_age:stirling_engine"),
                ResourceLocation.tryParse("create_new_age:solar_heating_plate"),
                ResourceLocation.tryParse("create_new_age:energiser_t1")
        };

        String[] fallbackNames = {
                "Generator Coil",
                "Carbon Brushes",
                "Basic Motor",
                "Advanced Motor",
                "Reinforced Motor",
                "Stirling Engine",
                "Solar Heating Plate",
                "Energiser T1"
        };

        for (int i = 0; i < items.length; i++) {
            if (items[i] == null) continue;
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
                String inputSearchIndex = (String.join(" ", inputNames) + " " + String.join(" ", inputIds) + " " + fallbackNames[i].toLowerCase(Locale.ROOT) + " " + displayName.toLowerCase(Locale.ROOT) + " kinetic stress units generator create su fe electricity new age magnet coil").trim();

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

    public static void registerSyntheticEmiRecipes(Object emiRegistryObj, Object emiCategoryObj, java.util.Set<net.minecraft.world.item.Item> activeRecipeItems) {
        if (!com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) return;
        EmiCreateNewAgeHelper.registerSyntheticEmiRecipes(emiRegistryObj, emiCategoryObj, activeRecipeItems);
    }

    private static class EmiCreateNewAgeHelper {
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

            record CNACandidate(String path, String defaultName, double amount, boolean isGen, List<IngredientStack> inputs, EnergyType energyType) {}

            List<CNACandidate> candidates = List.of(
                    new CNACandidate("generator_coil", "Generator Coil", 512.0, true,
                            List.of(IngredientStack.stressUnit(768.0)), EnergyType.ELECTRIC_FE),
                    new CNACandidate("carbon_brushes", "Carbon Brushes", 256.0, true,
                            List.of(IngredientStack.stressUnit(768.0)), EnergyType.ELECTRIC_FE),
                    new CNACandidate("basic_motor", "Basic Motor", 512.0, false,
                            List.of(), EnergyType.ELECTRIC_FE),
                    new CNACandidate("advanced_motor", "Advanced Motor", 2048.0, false,
                            List.of(), EnergyType.ELECTRIC_FE),
                    new CNACandidate("reinforced_motor", "Reinforced Motor", 8192.0, false,
                            List.of(), EnergyType.ELECTRIC_FE),
                    new CNACandidate("stirling_engine", "Stirling Engine", 1024.0, true,
                            null, EnergyType.KINETIC_SU)
            );

            for (CNACandidate c : candidates) {
                var itemId = ResourceLocation.tryParse(MOD_ID + ":" + c.path);
                var item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item == null || item == net.minecraft.world.item.Items.AIR) continue;

                // Strict check: if the item is disabled or hidden from recipe viewers or has no recipes in modpack, do NOT register!
                if (com.gtceu.calcboard.api.catalog.DynamicAddonCrawler.isItemDisabledOrHidden(item, (activeRecipeItems != null && !activeRecipeItems.isEmpty()) ? activeRecipeItems : null)) {
                    continue;
                }

                var block = ForgeRegistries.BLOCKS.getValue(itemId);
                double amount = com.gtceu.calcboard.compat.create.CreateRecipeHandler.getDynamicStressCapacity(block, c.amount);

                var stack = new ItemStack(item);
                String name = stack.getHoverName().getString();
                if (name == null || name.isEmpty()) name = c.defaultName;

                List<IngredientStack> outStacks = new ArrayList<>();
                if (c.energyType == EnergyType.KINETIC_SU) {
                    outStacks.add(IngredientStack.stressUnit(amount));
                }

                var recipe = new com.gtceu.calcboard.integration.emi.KineticGenerationEmiRecipe(
                        ResourceLocation.tryParse("gtcalcboard:kinetic_gen/" + MOD_ID + "/" + c.path),
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
