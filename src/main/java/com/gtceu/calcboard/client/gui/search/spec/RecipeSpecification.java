package com.gtceu.calcboard.client.gui.search.spec;

import com.gtceu.calcboard.api.model.SearchableRecipe;

/**
 * Standard specification predicate contract for searchable recipes.
 */
public interface RecipeSpecification {

    boolean isSatisfiedBy(SearchableRecipe recipe, SearchExecutionContext context);

    default int getCost() {
        return 50;
    }

    default RecipeSpecification and(RecipeSpecification other) {
        return new AndSpecification(this, other);
    }

    default RecipeSpecification or(RecipeSpecification other) {
        return new OrSpecification(this, other);
    }

    default RecipeSpecification not() {
        return new NotSpecification(this);
    }
}
