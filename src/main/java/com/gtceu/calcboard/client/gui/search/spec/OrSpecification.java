package com.gtceu.calcboard.client.gui.search.spec;

import com.gtceu.calcboard.api.model.SearchableRecipe;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Composite specification representing logical OR with short-circuit evaluation.
 */
public final class OrSpecification implements RecipeSpecification {

    private final List<RecipeSpecification> specifications;
    private final int totalCost;

    public OrSpecification(RecipeSpecification... specs) {
        this(Arrays.asList(specs));
    }

    public OrSpecification(List<RecipeSpecification> specifications) {
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
            if (spec.isSatisfiedBy(recipe, context)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int getCost() {
        return totalCost;
    }

    public List<RecipeSpecification> getSpecifications() {
        return specifications;
    }
}
