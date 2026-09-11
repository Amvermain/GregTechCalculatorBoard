package com.gtceu.calcboard.client.gui.search.spec;

import com.gtceu.calcboard.api.model.SearchableRecipe;

/**
 * Filter specification enforcing user category exclusions.
 */
public final class CategoryBlacklistSpecification implements RecipeSpecification {

    @Override
    public boolean isSatisfiedBy(SearchableRecipe recipe, SearchExecutionContext context) {
        if (recipe == null || context == null || context.filterConfig() == null) {
            return true;
        }
        return !context.filterConfig().isCategoryExcluded(recipe.categoryId());
    }

    @Override
    public int getCost() {
        return 10;
    }
}
