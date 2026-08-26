package com.gtceu.calcboard.integration.emi;

import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Standard EMI Recipe Transfer Handler for GTCalcBoard.
 * Enables the native EMI [+] button on recipe tabs when viewing recipes from the Calculator Board.
 */
public class BoardEmiRecipeHandler implements EmiRecipeHandler<BoardMenu> {

    @Override
    public EmiPlayerInventory getInventory(AbstractContainerScreen<BoardMenu> screen) {
        return new EmiPlayerInventory(List.of());
    }

    @Override
    public boolean supportsRecipe(EmiRecipe recipe) {
        return recipe != null;
    }

    @Override
    public boolean canCraft(EmiRecipe recipe, EmiCraftContext<BoardMenu> context) {
        return true;
    }

    @Override
    public boolean craft(EmiRecipe recipe, EmiCraftContext<BoardMenu> context) {
        CalcBoardEmiPlugin.addRecipeToBoard(recipe, false);
        return true;
    }

    @Override
    public List<ClientTooltipComponent> getTooltip(EmiRecipe recipe, EmiCraftContext<BoardMenu> context) {
        return List.of(
            ClientTooltipComponent.create(Component.translatable("gui.gtcalcboard.emi.add_button_title").getVisualOrderText()),
            ClientTooltipComponent.create(Component.translatable("gui.gtcalcboard.emi.add_button_desc1").getVisualOrderText())
        );
    }
}
