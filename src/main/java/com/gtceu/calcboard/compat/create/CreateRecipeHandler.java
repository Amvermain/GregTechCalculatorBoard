package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.EnergyType;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

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
        if (emiRecipe instanceof dev.emi.emi.api.recipe.EmiRecipe recipe) {
            ResourceLocation catId = recipe.getCategory() != null ? recipe.getCategory().getId() : null;
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
        }
        return false;
    }

    public static RecipeNode createKineticGeneratorNode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (itemId == null) return null;
        return createKineticGeneratorNode(itemId, stack.getHoverName().getString());
    }

    public static RecipeNode createKineticGeneratorNode(ResourceLocation itemId, String displayName) {
        if (itemId == null) return null;
        String path = itemId.getPath();
        String namespace = itemId.getNamespace();

        if (namespace.equals("create")) {
            if (path.equals("large_water_wheel")) {
                RecipeNode node = RecipeNode.create(displayName != null ? displayName : "Large Water Wheel", 20.0, 512.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
                node.addOutput(IngredientStack.stressUnit(512.0));
                return node;
            } else if (path.equals("water_wheel")) {
                RecipeNode node = RecipeNode.create(displayName != null ? displayName : "Water Wheel", 20.0, 256.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
                node.addOutput(IngredientStack.stressUnit(256.0));
                return node;
            } else if (path.equals("windmill_bearing")) {
                RecipeNode node = RecipeNode.create(displayName != null ? displayName : "Windmill Bearing", 20.0, 512.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
                node.addOutput(IngredientStack.stressUnit(512.0));
                return node;
            } else if (path.equals("steam_engine")) {
                RecipeNode node = RecipeNode.create(displayName != null ? displayName : "Steam Engine", 20.0, 2048.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
                node.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 200.0, 1.0));
                node.addOutput(IngredientStack.stressUnit(2048.0));
                return node;
            } else if (path.equals("hand_crank")) {
                RecipeNode node = RecipeNode.create(displayName != null ? displayName : "Hand Crank", 20.0, 256.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
                node.addOutput(IngredientStack.stressUnit(256.0));
                return node;
            } else if (path.equals("creative_motor")) {
                RecipeNode node = RecipeNode.create(displayName != null ? displayName : "Creative Motor", 20.0, 16384.0, GTVoltageTier.LV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create:kinetic_generation"));
                node.addOutput(IngredientStack.stressUnit(16384.0));
                return node;
            }
        } else if (namespace.equals("createaddition")) {
            if (path.equals("alternator")) {
                RecipeNode node = RecipeNode.create(displayName != null ? displayName : "Alternator", 20.0, 256.0, GTVoltageTier.ULV);
                node.setEnergyType(EnergyType.ELECTRIC_FE);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("createaddition:alternator"));
                node.addInput(IngredientStack.stressUnit(256.0));
                return node;
            } else if (path.equals("electric_motor")) {
                RecipeNode node = RecipeNode.create(displayName != null ? displayName : "Electric Motor", 20.0, 512.0, GTVoltageTier.ULV);
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
        List<RecipeSearchEngine.SearchableRecipe> list = new ArrayList<>();
        String catId = "create:kinetic_generation";
        String catName = "Create Kinetic";

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

        String[] names = {
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
            RecipeNode node = createKineticGeneratorNode(items[i], names[i]);
            if (node != null) {
                String displayName = node.getName();
                String modId = items[i].getNamespace();

                List<String> outputNames = new ArrayList<>();
                List<String> outputIds = new ArrayList<>();
                for (IngredientStack out : node.getOutputs()) {
                    outputNames.add(out.getDisplayName().toLowerCase(Locale.ROOT));
                    if (out.getId() != null) outputIds.add(out.getId().toString().toLowerCase(Locale.ROOT));
                }

                List<String> inputNames = new ArrayList<>();
                List<String> inputIds = new ArrayList<>();
                for (IngredientStack in : node.getInputs()) {
                    inputNames.add(in.getDisplayName().toLowerCase(Locale.ROOT));
                    if (in.getId() != null) inputIds.add(in.getId().toString().toLowerCase(Locale.ROOT));
                }

                String outputSearchIndex = (String.join(" ", outputNames) + " " + String.join(" ", outputIds)).trim();
                String inputSearchIndex = (String.join(" ", inputNames) + " " + String.join(" ", inputIds) + " kinetic stress units generator create su").trim();

                list.add(new RecipeSearchEngine.SearchableRecipe(
                        node,
                        displayName,
                        modId.intern(),
                        catId.intern(),
                        catName.intern(),
                        inputSearchIndex,
                        outputSearchIndex
                ));
            }
        }
        return list;
    }
}
