package com.gtceu.calcboard.compat.vanilla;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Generic fallback compatibility adapter for Vanilla Minecraft and non-specialized mods.
 */
public class VanillaModAdapter implements IModAdapter {

    @Override
    public String getModId() {
        return "minecraft";
    }

    @Override
    public int getPriority() {
        return 0; // Lowest priority, acts as universal fallback
    }

    @Override
    public boolean isLoaded() {
        return true;
    }

    @Override
    public boolean handlesCategory(ResourceLocation categoryId) {
        return true;
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        return true;
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        return false;
    }

    @Override
    public void discoverAddons(List<MachineAddon> collector, List<ItemStack> recipeOutputStacks) {
        // Vanilla has no hardware addons
    }

    @Override
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
        // Standard singleblock capability
    }

    @Override
    public boolean adaptRecipeDetails(Object emiRecipe, Object backingRecipe, EmiRecipeConverter.RecipeDetails details) {
        return false;
    }

    @Override
    public MachineAddon tailorAddon(MachineAddon addon, RecipeNode targetNode) {
        return addon;
    }

    @Override
    public OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        if (isGenerator) {
            return new OverclockMode.OverclockResult(node.getBaseDurationTicks(), node.getBaseEUt(), 1.0, 0);
        }
        return node.getOverclockMode().calculate(node.getBaseDurationTicks(), node.getBaseEUt(), node.getTierDelta());
    }
}
