package com.gtceu.calcboard.client.gui.search.spec;

import com.gtceu.calcboard.api.model.SearchableRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Composite specification representing logical AND with short-circuit evaluation.
 */
public final class AndSpecification implements RecipeSpecification {

    private final List<RecipeSpecification> specifications;
    private final int totalCost;

    public AndSpecification(RecipeSpecification... specs) {
        this(Arrays.asList(specs));
    }

    public AndSpecification(List<RecipeSpecification> specifications) {
        this.specifications = new ArrayList<>(specifications);
        int cost = 0;
        for (RecipeSpecification spec : this.specifications) {
            cost += spec.getCost();
        }
        this.totalCost = cost;
    }

    @Override
    public boolean isSatisfiedBy(SearchableRecipe recipe, SearchExecutionContext context) {
        for (RecipeSpecification spec : specifications) {
            if (!spec.isSatisfiedBy(recipe, context)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int getCost() {
        return totalCost;
    }

    public List<RecipeSpecification> getSpecifications() {
        return specifications;
    }
}
