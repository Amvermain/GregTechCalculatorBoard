package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;

import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Mod Adapter facade for Create kinetic generators and processing machinery.
 */
public class CreateModAdapter extends AbstractKineticModAdapter {

    public static final String MOD_ID = "create";
    public static final String MOD_ID_ADDITION = "createaddition";

    static {
        CreateProperties.init();
    }

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public int getPriority() {
        return 90;
    }

    @Override
    public boolean isLoaded() {
        try {
            if (ModList.get() != null) {
                return ModList.get().isLoaded(MOD_ID)
                        || ModList.get().isLoaded(MOD_ID_ADDITION)
                        || !FMLLoader.isProduction();
            }
        } catch (Throwable t) {
            return true; // Test environment fallback
        }
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        String ns = categoryId.getNamespace();
        if (ns.equals("create_new_age")) return false; // Dedicated CreateNewAgeModAdapter handles this
        if (ns.equals("greate")) return false; // Dedicated GreateModAdapter handles this
        if (com.gtceu.calcboard.api.util.ModCompatHelper.isCreateFamilyNamespace(ns)) return true;
        return "gtcalcboard".equals(ns) && "kinetic_generation".equals(categoryId.getPath());
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getMachineIcon() != null && "greate".equals(node.getMachineIcon().getNamespace())) return false;
        if (node.getRecipeCategoryId() != null && "greate".equals(node.getRecipeCategoryId().getNamespace())) return false;
        return com.gtceu.calcboard.api.util.ModCompatHelper.isCreateMachine(node);
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        return false;
    }

    @Override
    public List<AddonCategory> getApplicableAddonCategories(RecipeNode node) {
        return List.of();
    }

    @Override
    public boolean isAddonCompatible(RecipeNode node, MachineAddon addon) {
        return false;
    }

    @Override
    public void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        // Create kinetic machines do not use GUI addon slots
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        return CreateRecipeHandler.adaptRecipeDetails(emiRecipe, backingRecipe, details);
    }

    @Override
    public com.gtceu.calcboard.api.model.CompoundRecipeBuilder.CompoundCluster buildCompoundRecipe(
            Object recipeObj,
            Object backingRecipe,
            ResourceLocation preferredWorkstation,
            double startX,
            double startY
    ) {
        if (backingRecipe == null) return null;
        if (CreateSequencedRecipeExtractor.isSequencedRecipe(backingRecipe)) {
            String machineName = preferredWorkstation != null ? EmiRecipeConverter.formatName(preferredWorkstation.getPath()) : "Sequenced Assembly";
            ResourceLocation icon = preferredWorkstation != null ? preferredWorkstation : ResourceLocation.tryParse("create:sequenced_assembly");
            return CreateSequencedRecipeExtractor.buildCompoundCluster(
                    backingRecipe, machineName, icon, GTVoltageTier.ULV, startX, startY
            );
        }
        return null;
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
    }

    @Override
    public void registerSyntheticEmiRecipes(Object emiRegistry, Object emiCategory, java.util.Set<net.minecraft.world.item.Item> activeRecipeItems) {
        CreateRecipeHandler.registerSyntheticEmiRecipes(emiRegistry, emiCategory, activeRecipeItems);
    }

    public static RecipeNode createKineticGeneratorNode(ItemStack stack) {
        RecipeNode node = CreateRecipeHandler.createKineticGeneratorNode(stack);
        if (node != null) return node;
        return com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler.createKineticGeneratorNode(stack);
    }

    public static RecipeNode createKineticGeneratorNode(ResourceLocation itemId, String displayName) {
        RecipeNode node = CreateRecipeHandler.createKineticGeneratorNode(itemId, displayName);
        if (node != null) return node;
        return com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler.createKineticGeneratorNode(itemId, displayName);
    }

    public static List<SearchableRecipe> getVirtualKineticSearchRecipes() {
        List<SearchableRecipe> list = new ArrayList<>(CreateRecipeHandler.getVirtualKineticSearchRecipes());
        list.addAll(com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler.getVirtualSearchRecipes());
        return list;
    }
}




