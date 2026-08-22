package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.client.gui.NodeWidget;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLLoader;

import java.util.List;

/**
 * Mod adapter facade for Create kinetic generators and machines.
 * Delegates to CreateGuiHandler and CreateRecipeHandler.
 */
public class CreateModAdapter implements IModAdapter {

    public static final String MOD_ID = "create";

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
                        || ModList.get().isLoaded("createaddition")
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
        return categoryId.getNamespace().equals(MOD_ID) || categoryId.getNamespace().equals("createaddition");
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getEnergyType() == EnergyType.KINETIC_SU) return true;
        if (node.getMachineIcon() != null && (node.getMachineIcon().getNamespace().equals(MOD_ID) || node.getMachineIcon().getNamespace().equals("createaddition"))) return true;
        return node.getRecipeCategoryId() != null && (node.getRecipeCategoryId().getNamespace().equals(MOD_ID) || node.getRecipeCategoryId().getNamespace().equals("createaddition"));
    }

    @Override
    public boolean supportsAddons(RecipeNode node) {
        return false;
    }

    @Override
    public List<MachineAddon.Category> getApplicableAddonCategories(RecipeNode node) {
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
    public void enrichCapabilities(CategoryCapabilityMatrix matrix, Object emiRecipeManager) {
    }

    @Override
    public OverclockMode.OverclockResult computeOverclock(RecipeNode node, GTVoltageTier targetTier, boolean isGenerator) {
        int rpm = node.getRpm();
        double baseDuration = node.getBaseDurationTicks();
        double basePower = node.getBaseEUt();

        // 32 RPM is the baseline standard speed (1.0x)
        double speedFactor = Math.max(0.01, rpm / 32.0);
        double rawDuration = baseDuration / speedFactor;
        double durationTicks = Math.max(1.0, rawDuration);
        double batchesPerTick = (rawDuration < 1.0 && rawDuration > 0.0) ? (1.0 / rawDuration) : 1.0;

        double effectivePower;
        if (isGenerator) {
            effectivePower = basePower;
        } else {
            effectivePower = basePower * speedFactor;
        }

        return new OverclockMode.OverclockResult(durationTicks, effectivePower, batchesPerTick, 0);
    }

    @Override
    public String formatEnergyStats(RecipeNode node, PowerDisplayMode displayMode) {
        return CreateGuiHandler.formatEnergyStats(node, displayMode);
    }

    @Override
    public List<Component> buildEnergyTooltip(RecipeNode node) {
        return CreateGuiHandler.buildEnergyTooltip(node);
    }

    @Override
    public void renderCardControls(GuiGraphics graphics, Font font,
                                   RecipeNode node, int x, int row2Y, int cardW, int mouseX, int mouseY,
                                   boolean isGlowing) {
        CreateGuiHandler.renderCardControls(graphics, font, node, x, row2Y, cardW, mouseX, mouseY, isGlowing);
    }

    @Override
    public boolean isTierOrSpeedControlHovered(RecipeNode node, double mouseX, double mouseY) {
        return CreateGuiHandler.isTierOrSpeedControlHovered(node, mouseX, mouseY);
    }

    @Override
    public boolean isMachineConfigHovered(RecipeNode node, double mouseX, double mouseY) {
        return CreateGuiHandler.isMachineConfigHovered(node, mouseX, mouseY);
    }

    @Override
    public boolean handleControlClick(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, int button) {
        return CreateGuiHandler.handleControlClick(widget, node, mouseX, mouseY, button);
    }

    @Override
    public boolean handleControlScroll(NodeWidget widget, RecipeNode node, double mouseX, double mouseY, double delta) {
        return CreateGuiHandler.handleControlScroll(widget, node, mouseX, mouseY, delta);
    }

    public static RecipeNode createKineticGeneratorNode(ItemStack stack) {
        return CreateRecipeHandler.createKineticGeneratorNode(stack);
    }

    public static RecipeNode createKineticGeneratorNode(ResourceLocation itemId, String displayName) {
        return CreateRecipeHandler.createKineticGeneratorNode(itemId, displayName);
    }

    public static List<RecipeSearchEngine.SearchableRecipe> getVirtualKineticSearchRecipes() {
        return CreateRecipeHandler.getVirtualKineticSearchRecipes();
    }
}
