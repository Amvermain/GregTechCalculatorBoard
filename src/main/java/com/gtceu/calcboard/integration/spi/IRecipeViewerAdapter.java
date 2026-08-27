package com.gtceu.calcboard.integration.spi;

import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.SearchableRecipe;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Set;

/**
 * Service Provider Interface for external recipe viewers (EMI, JEI, REI, and Vanilla Fallback).
 */
public interface IRecipeViewerAdapter {

    String getViewerId();

    int getPriority();

    boolean isAvailable();

    boolean isRecipeBakingComplete();

    void runWhenReady(Runnable callback);

    // 1. Recipe Discovery & Indexing
    List<SearchableRecipe> collectSearchableRecipes();

    // 2. Conversion
    RecipeNode convertToNode(Object viewerRecipe);

    // 3. Hotkey Lookup ([R] / [U])
    boolean displayRecipes(IngredientStack ingredient);

    boolean displayUses(IngredientStack ingredient);

    // 4. Favorites & Bookmarks
    Set<ResourceLocation> getFavoriteRecipeIds();

    boolean isFavorite(Object viewerRecipe);

    void toggleFavorite(Object viewerRecipe);

    // 5. Preview & UI Rendering
    int[] calculatePreviewBounds(SearchableRecipe sr, int dialogX, int dialogY, int dialogW, int dialogH, int hoveredRowY, int screenW, int screenH);

    void renderPreviewCard(GuiGraphics graphics, Font font, SearchableRecipe sr, int dialogX, int dialogY, int dialogW, int dialogH, int hoveredRowY, int mouseX, int mouseY, float partialTick, int screenW, int screenH);

    Object getHoveredPreviewIngredient(SearchableRecipe sr, int dialogX, int dialogY, int dialogW, int dialogH, int hoveredRowY, int mouseX, int mouseY, int screenW, int screenH);

    boolean handleHoveredIngredientClick(Object hoveredIngredient, EditBox searchBox);

    boolean handleHoveredIngredientLookup(Object hoveredIngredient, boolean isRecipes);

    default int renderRowIcon(GuiGraphics graphics, Font font, Object viewerRecipe, int listX, int rowY) {
        return renderRowIcon(graphics, font, viewerRecipe, listX, rowY, null, null);
    }

    int renderRowIcon(GuiGraphics graphics, Font font, Object viewerRecipe, int listX, int rowY, ResourceLocation matchedOutputId, String matchedOutputName);

    // 6. Multiblock BoM Integration
    boolean isBoMGoalRegistrationSupported();

    void registerBoMGoal(MultiblockBOMSummary summary);
}


