package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.util.ModCompatHelper;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.compat.create.CreateModAdapter;
import com.gtceu.calcboard.compat.create.CreateRecipeHandler;
import com.gtceu.calcboard.compat.createnewage.CreateNewAgeRecipeHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class CreateKineticModCheckTest {

    @BeforeEach
    public void setUp() {
        ModCompatHelper.clearTestOverrides();
    }

    @AfterEach
    public void tearDown() {
        ModCompatHelper.clearTestOverrides();
    }

    @Test
    public void testVirtualRecipesDisabledWhenCreateNotLoaded() {
        ModCompatHelper.setTestOverride("create", false);
        ModCompatHelper.setTestOverride("createaddition", false);
        ModCompatHelper.setTestOverride("create_new_age", false);

        List<RecipeSearchEngine.SearchableRecipe> createRecipes = CreateRecipeHandler.getVirtualKineticSearchRecipes();
        Assertions.assertTrue(createRecipes.isEmpty(), "Virtual Create recipes should be empty when Create is not loaded");

        List<RecipeSearchEngine.SearchableRecipe> cnaRecipes = CreateNewAgeRecipeHandler.getVirtualSearchRecipes();
        Assertions.assertTrue(cnaRecipes.isEmpty(), "Virtual Create New Age recipes should be empty when Create New Age is not loaded");

        List<RecipeSearchEngine.SearchableRecipe> combined = CreateModAdapter.getVirtualKineticSearchRecipes();
        Assertions.assertTrue(combined.isEmpty(), "Combined virtual recipes should be empty when mods are not loaded");
    }

    @Test
    public void testVirtualRecipesEnabledWhenCreateLoaded() {
        ModCompatHelper.setTestOverride("create", true);
        ModCompatHelper.setTestOverride("createaddition", true);
        ModCompatHelper.setTestOverride("create_new_age", true);

        List<RecipeSearchEngine.SearchableRecipe> combined = CreateModAdapter.getVirtualKineticSearchRecipes();
        Assertions.assertFalse(combined.isEmpty(), "Virtual recipes should be present when Create mods are loaded");
        Assertions.assertTrue(combined.size() >= 8, "Expected at least 8 virtual recipes");
    }
}
