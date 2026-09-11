package com.gtceu.calcboard.client.gui.search.spec;

import com.gtceu.calcboard.api.model.SearchableRecipe;

/**
 * Filter specification restricting candidates to bookmarked favorites.
 */
public final class FavoriteOnlySpecification implements RecipeSpecification {

    @Override
    public boolean isSatisfiedBy(SearchableRecipe recipe, SearchExecutionContext context) {
        if (recipe == null || context == null) return false;
        if (!context.showFavoritesOnly()) return true;
        if (recipe.recipeId() == null) return false;
        return context.favoriteRecipeIds().contains(recipe.recipeId());
    }

    @Override
    public int getCost() {
        return 10;
    }
}
