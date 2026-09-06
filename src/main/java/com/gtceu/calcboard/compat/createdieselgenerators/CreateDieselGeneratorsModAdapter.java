package com.gtceu.calcboard.compat.createdieselgenerators;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.compat.create.AbstractKineticModAdapter;
import com.gtceu.calcboard.compat.extension.IEnergySimulationProvider;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

import java.util.List;
import java.util.Set;

/**
 * Mod Adapter for Create: Diesel Generators (createdieselgenerators).
 * Encapsulates diesel combustion kinetic generation, distillation towers, and basin fermenting.
 */
public class CreateDieselGeneratorsModAdapter extends AbstractKineticModAdapter {

    private static final Set<Class<? extends com.gtceu.calcboard.compat.extension.IModExtension>> SUPPORTED_EXTENSIONS = Set.of(
            IEnergySimulationProvider.class
    );

    @Override
    public Set<Class<? extends com.gtceu.calcboard.compat.extension.IModExtension>> getSupportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }

    public static final String MOD_ID = "createdieselgenerators";

    @Override
    public String getModId() {
        return MOD_ID;
    }

    @Override
    public int getPriority() {
        return 95;
    }

    @Override
    public boolean isLoaded() {
        try {
            if (ModList.get() != null) {
                return ModList.get().isLoaded(MOD_ID) || !FMLLoader.isProduction();
            }
        } catch (Throwable t) {
            return true;
        }
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        if (categoryId == null) return false;
        return MOD_ID.equals(categoryId.getNamespace());
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getMachineIcon() != null && MOD_ID.equals(node.getMachineIcon().getNamespace())) {
            return true;
        }
        return node.getRecipeCategoryId() != null && MOD_ID.equals(node.getRecipeCategoryId().getNamespace());
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        return CDGRecipeHandler.adaptRecipeDetails(emiRecipe, backingRecipe, details);
    }

    @Override
    public EnergyType getEnergyType(RecipeNode node) {
        if (node != null && node.getRecipeCategoryId() != null) {
            String path = node.getRecipeCategoryId().getPath();
            if (path.equals("basin_fermenting") || path.equals("bulk_fermenting") || path.equals("casting") || path.equals("distillation") || path.equals("hammering") || path.equals("wire_cutting")) {
                return EnergyType.NONE;
            }
        }
        return EnergyType.KINETIC_SU;
    }

    @Override
    public String formatEnergyStats(RecipeNode node, com.gtceu.calcboard.api.type.PowerDisplayMode displayMode) {
        if (node != null && getEnergyType(node) == EnergyType.NONE) {
            return net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.energy_passive_stat").getString();
        }
        return super.formatEnergyStats(node, displayMode);
    }

    @Override
    public List<net.minecraft.network.chat.Component> buildEnergyTooltip(RecipeNode node) {
        if (node != null && getEnergyType(node) == EnergyType.NONE) {
            List<net.minecraft.network.chat.Component> tooltip = new java.util.ArrayList<>();
            tooltip.add(net.minecraft.network.chat.Component.literal("§a~ " + net.minecraft.network.chat.Component.translatable("gui.gtcalcboard.energy_passive_stat").getString()));
            tooltip.add(net.minecraft.network.chat.Component.literal(String.format(java.util.Locale.ROOT, "§7Duration: §f%.4fs §7(§f%,.4f cycles/s§7)", node.getEffectiveDurationSeconds(), node.getEffectiveCyclesPerSecond())));
            return tooltip;
        }
        return super.buildEnergyTooltip(node);
    }

    public static List<SearchableRecipe> getVirtualSearchRecipes() {
        return CDGRecipeHandler.getVirtualKineticSearchRecipes();
    }

    @Override
    public void registerSyntheticEmiRecipes(Object emiRegistry, Object emiCategory, java.util.Set<net.minecraft.world.item.Item> activeRecipeItems) {
        CDGRecipeHandler.registerSyntheticEmiRecipes(emiRegistry, emiCategory, activeRecipeItems);
    }
}
