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

import java.util.ArrayList;
import java.util.List;

/**
 * Mod Adapter facade for Create kinetic generators and processing machinery.
 */
public class CreateModAdapter implements IModAdapter {

    public static final String MOD_ID = "create";
    public static final String MOD_ID_ADDITION = "createaddition";

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
        return ns.equals(MOD_ID) || ns.equals(MOD_ID_ADDITION);
    }

    @Override
    public boolean handlesNode(RecipeNode node) {
        if (node == null) return false;
        if (node.getEnergyType() == EnergyType.KINETIC_SU) return true;
        if (node.getMachineIcon() != null) {
            String ns = node.getMachineIcon().getNamespace();
            if (ns.equals(MOD_ID) || ns.equals(MOD_ID_ADDITION)) return true;
        }
        if (node.getRecipeCategoryId() != null) {
            String ns = node.getRecipeCategoryId().getNamespace();
            return ns.equals(MOD_ID) || ns.equals(MOD_ID_ADDITION);
        }
        return false;
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
        RecipeNode node = CreateRecipeHandler.createKineticGeneratorNode(stack);
        if (node != null) return node;
        return com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler.createKineticGeneratorNode(stack);
    }

    public static RecipeNode createKineticGeneratorNode(ResourceLocation itemId, String displayName) {
        RecipeNode node = CreateRecipeHandler.createKineticGeneratorNode(itemId, displayName);
        if (node != null) return node;
        return com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler.createKineticGeneratorNode(itemId, displayName);
    }

    public static List<RecipeSearchEngine.SearchableRecipe> getVirtualKineticSearchRecipes() {
        List<RecipeSearchEngine.SearchableRecipe> list = new ArrayList<>(CreateRecipeHandler.getVirtualKineticSearchRecipes());
        list.addAll(com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler.getVirtualSearchRecipes());
        return list;
    }
}
