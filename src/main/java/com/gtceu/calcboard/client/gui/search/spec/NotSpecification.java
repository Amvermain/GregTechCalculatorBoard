package com.gtceu.calcboard.client.gui.search.spec;

import com.gtceu.calcboard.api.model.SearchableRecipe;

import java.util.Objects;

/**
 * Composite specification representing logical NOT inversion.
 */
public final class NotSpecification implements RecipeSpecification {

    private final RecipeSpecification target;

    public NotSpecification(RecipeSpecification target) {
        this.target = Objects.requireNonNull(target);
    }

    @Override
    public boolean isSatisfiedBy(SearchableRecipe recipe, SearchExecutionContext context) {
        return !target.isSatisfiedBy(recipe, context);
    }

    @Override
    public int getCost() {
        return target.getCost();
    }

    public RecipeSpecification getTarget() {
        return target;
    }
}
