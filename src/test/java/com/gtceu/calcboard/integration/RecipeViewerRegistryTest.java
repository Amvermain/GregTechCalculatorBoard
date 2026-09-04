package com.gtceu.calcboard.integration;

import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;

import com.gtceu.calcboard.integration.spi.IRecipeViewerAdapter;
import com.gtceu.calcboard.integration.spi.RecipeViewerRegistry;
import com.gtceu.calcboard.integration.vanilla.VanillaRecipeViewerAdapter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RecipeViewerRegistryTest {

    @BeforeEach
    public void setup() {
        RecipeViewerRegistry.reset();
    }

    @Test
    public void testActiveAdapterNonNull() {
        IRecipeViewerAdapter active = RecipeViewerRegistry.getActiveAdapter();
        Assertions.assertNotNull(active, "Active recipe viewer adapter must not be null");
    }

    @Test
    public void testCustomAdapterPriorityRegistration() {
        IRecipeViewerAdapter mockAdapter = new IRecipeViewerAdapter() {
            @Override
            public String getViewerId() {
                return "mock_viewer";
            }

            @Override
            public int getPriority() {
                return 9999;
            }

            @Override
            public boolean isAvailable() {
                return true;
            }

            @Override
            public boolean isRecipeBakingComplete() {
                return true;
            }

            @Override
            public void runWhenReady(Runnable callback) {
                if (callback != null) callback.run();
            }

            @Override
            public java.util.List<SearchableRecipe> collectSearchableRecipes() {
                return java.util.Collections.emptyList();
            }

            @Override
            public com.gtceu.calcboard.api.model.RecipeNode convertToNode(Object viewerRecipe) {
                return null;
            }

            @Override
            public boolean displayRecipes(com.gtceu.calcboard.api.model.IngredientStack ingredient) {
                return false;
            }

            @Override
            public boolean displayUses(com.gtceu.calcboard.api.model.IngredientStack ingredient) {
                return false;
            }

            @Override
            public java.util.Set<net.minecraft.resources.ResourceLocation> getFavoriteRecipeIds() {
                return java.util.Collections.emptySet();
            }

            @Override
            public boolean isFavorite(Object viewerRecipe) {
                return false;
            }

            @Override
            public void toggleFavorite(Object viewerRecipe) {}

            @Override
            public int[] calculatePreviewBounds(SearchableRecipe sr, int dialogX, int dialogY, int dialogW, int dialogH, int hoveredRowY, int screenW, int screenH) {
                return new int[]{0, 0, 100, 100};
            }

            @Override
            public void renderPreviewCard(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, SearchableRecipe sr, int dialogX, int dialogY, int dialogW, int dialogH, int hoveredRowY, int mouseX, int mouseY, float partialTick, int screenW, int screenH) {}

            @Override
            public Object getHoveredPreviewIngredient(SearchableRecipe sr, int dialogX, int dialogY, int dialogW, int dialogH, int hoveredRowY, int mouseX, int mouseY, int screenW, int screenH) {
                return null;
            }

            @Override
            public boolean handleHoveredIngredientClick(Object hoveredIngredient, net.minecraft.client.gui.components.EditBox searchBox) {
                return false;
            }

            @Override
            public boolean handleHoveredIngredientLookup(Object hoveredIngredient, boolean isRecipes) {
                return false;
            }

            @Override
            public int renderRowIcon(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, Object viewerRecipe, int listX, int rowY, net.minecraft.resources.ResourceLocation matchedOutputId, String matchedOutputName) {
                return 58;
            }

            @Override
            public boolean isBoMGoalRegistrationSupported() {
                return true;
            }

            @Override
            public void registerBoMGoal(com.gtceu.calcboard.api.bom.MultiblockBOMSummary summary) {}
        };

        RecipeViewerRegistry.register(mockAdapter);
        IRecipeViewerAdapter active = RecipeViewerRegistry.getActiveAdapter();
        Assertions.assertEquals("mock_viewer", active.getViewerId(), "Mock adapter with priority 9999 should be selected as active adapter");
    }

    @Test
    public void testSearchFieldFocusDetection() {
        // By default, no search is focused
        Assertions.assertFalse(RecipeViewerRegistry.isAnySearchFocused());

        // Register adapter with search focused
        IRecipeViewerAdapter focusedAdapter = new IRecipeViewerAdapter() {
            @Override public String getViewerId() { return "focused_viewer"; }
            @Override public int getPriority() { return 10000; }
            @Override public boolean isAvailable() { return true; }
            @Override public boolean isRecipeBakingComplete() { return true; }
            @Override public void runWhenReady(Runnable callback) { if (callback != null) callback.run(); }
            @Override public java.util.List<SearchableRecipe> collectSearchableRecipes() { return java.util.Collections.emptyList(); }
            @Override public RecipeNode convertToNode(Object viewerRecipe) { return null; }
            @Override public boolean displayRecipes(IngredientStack ingredient) { return false; }
            @Override public boolean displayUses(IngredientStack ingredient) { return false; }
            @Override public java.util.Set<net.minecraft.resources.ResourceLocation> getFavoriteRecipeIds() { return java.util.Collections.emptySet(); }
            @Override public boolean isFavorite(Object viewerRecipe) { return false; }
            @Override public void toggleFavorite(Object viewerRecipe) {}
            @Override public int[] calculatePreviewBounds(SearchableRecipe sr, int dialogX, int dialogY, int dialogW, int dialogH, int hoveredRowY, int screenW, int screenH) { return new int[]{0, 0, 100, 100}; }
            @Override public void renderPreviewCard(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, SearchableRecipe sr, int dialogX, int dialogY, int dialogW, int dialogH, int hoveredRowY, int mouseX, int mouseY, float partialTick, int screenW, int screenH) {}
            @Override public Object getHoveredPreviewIngredient(SearchableRecipe sr, int dialogX, int dialogY, int dialogW, int dialogH, int hoveredRowY, int mouseX, int mouseY, int screenW, int screenH) { return null; }
            @Override public boolean handleHoveredIngredientClick(Object hoveredIngredient, net.minecraft.client.gui.components.EditBox searchBox) { return false; }
            @Override public boolean handleHoveredIngredientLookup(Object hoveredIngredient, boolean isRecipes) { return false; }
            @Override public int renderRowIcon(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.gui.Font font, Object viewerRecipe, int listX, int rowY, net.minecraft.resources.ResourceLocation matchedOutputId, String matchedOutputName) { return 0; }
            @Override public boolean isBoMGoalRegistrationSupported() { return false; }
            @Override public void registerBoMGoal(MultiblockBOMSummary summary) {}
            @Override public boolean isSearchFieldFocused() { return true; }
        };
        RecipeViewerRegistry.register(focusedAdapter);
        Assertions.assertTrue(RecipeViewerRegistry.isAnySearchFocused(), "isAnySearchFocused should return true when a registered viewer has search focused");
    }
}


