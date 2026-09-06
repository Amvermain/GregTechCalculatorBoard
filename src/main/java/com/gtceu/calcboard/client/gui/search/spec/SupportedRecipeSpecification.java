package com.gtceu.calcboard.client.gui.search.spec;

import com.gtceu.calcboard.api.model.SearchableRecipe;

/**
 * Filter specification checking whether unsupported recipes are permitted.
 */
public final class SupportedRecipeSpecification implements RecipeSpecification {

    @Override
    public boolean isSatisfiedBy(SearchableRecipe recipe, SearchExecutionContext context) {
        if (recipe == null) return false;
        if (context == null || context.filterConfig() == null) return recipe.isSupported();
        if (context.filterConfig().isIncludeUnsupported()) return true;
        return recipe.isSupported();
    }

    @Override
    public int getCost() {
        return 10;
    }
}
