package com.gtceu.calcboard.compat.createnewage;

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
 * Handles Create: New Age recipe parsing, energising processing, and generator/motor node creation.
 */
public class CreateNewAgeRecipeHandler {

    public static final String MOD_ID = "create_new_age";

    public static boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, RecipeDetails details) {
        ResourceLocation catId = null;
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isEmiLoaded()) {
            catId = EmiCreateNewAgeHelper.getCategoryId(emiRecipe);
        }
        if (catId != null && catId.getNamespace().equals(MOD_ID)) {
            String path = catId.getPath().toLowerCase(Locale.ROOT);

            if ("energising".equals(path) || "energizing".equals(path)) {
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
            if ("generator_coil".equals(path)) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Generator Coil", 20.0, 512.0, GTVoltageTier.ULV);
                node.setEnergyType(EnergyType.ELECTRIC_FE);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create_new_age:generator"));
                node.addInput(IngredientStack.stressUnit(768.0)); // 24.0 base stress * 32 RPM
                return node;
            } else if ("carbon_brushes".equals(path)) {
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
            } else if ("solar_heating_plate".equals(path)) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Solar Heating Plate", 20.0, 256.0, GTVoltageTier.ULV);
                node.setEnergyType(EnergyType.KINETIC_SU);
                node.setGenerator(true);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create_new_age:solar_heat"));
                node.addOutput(IngredientStack.stressUnit(256.0));
                return node;
            } else if (path.startsWith("energiser") || path.startsWith("energizer")) {
                RecipeNode node = RecipeNode.create(name != null ? name : "Energiser", 20.0, 256.0, GTVoltageTier.ULV);
                node.setEnergyType(EnergyType.ELECTRIC_FE);
                node.setMachineIcon(itemId);
                node.setRecipeCategoryId(ResourceLocation.tryParse("create_new_age:energising"));
                return node;
            }
        }
        return null;
    }

    public static void collectNativeCatalogRecipes(List<SearchableRecipe> collector) {
        if (!ModCompatHelper.isCreateNewAgeLoaded()) return;

        record CNACandidate(String path, String defaultName, com.gtceu.calcboard.compat.create.KineticCategory category) {}

        List<CNACandidate> candidates = List.of(
                new CNACandidate("generator_coil", "Generator Coil", com.gtceu.calcboard.compat.create.KineticCategory.ALTERNATOR),
                new CNACandidate("carbon_brushes", "Carbon Brushes", com.gtceu.calcboard.compat.create.KineticCategory.ALTERNATOR),
                new CNACandidate("basic_motor", "Basic Motor", com.gtceu.calcboard.compat.create.KineticCategory.MOTOR),
                new CNACandidate("advanced_motor", "Advanced Motor", com.gtceu.calcboard.compat.create.KineticCategory.MOTOR),
                new CNACandidate("reinforced_motor", "Reinforced Motor", com.gtceu.calcboard.compat.create.KineticCategory.MOTOR),
                new CNACandidate("stirling_engine", "Stirling Engine", com.gtceu.calcboard.compat.create.KineticCategory.FUEL_ENGINE)
        );

        for (CNACandidate c : candidates) {
            ResourceLocation itemId = ResourceLocation.tryParse(MOD_ID + ":" + c.path);
            String name = c.defaultName;

            if (isRealModLoaded(MOD_ID) && ForgeRegistries.ITEMS != null) {
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

    private static class EmiCreateNewAgeHelper {
        private static ResourceLocation getCategoryId(Object emiRecipe) {
            if (emiRecipe instanceof dev.emi.emi.api.recipe.EmiRecipe recipe && recipe.getCategory() != null) {
                return recipe.getCategory().getId();
            }
            return null;
        }
    }
}
