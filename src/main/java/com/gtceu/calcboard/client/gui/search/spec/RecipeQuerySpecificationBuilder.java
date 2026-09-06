package com.gtceu.calcboard.client.gui.search.spec;

import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.ParsedQuery;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Constructs an optimized, cost-sorted composite specification tree for recipe filtering.
 */
public final class RecipeQuerySpecificationBuilder {

    private final List<RecipeSpecification> specifications = new ArrayList<>();

    public RecipeQuerySpecificationBuilder withSpecification(RecipeSpecification specification) {
        if (specification != null) {
            this.specifications.add(specification);
        }
        return this;
    }

    public RecipeQuerySpecificationBuilder withDefaultFilters(ParsedQuery parsedQuery, SearchExecutionContext context) {
        if (context != null) {
            if (context.filterConfig() != null && !context.filterConfig().getExcludedCategories().isEmpty()) {
                this.specifications.add(new CategoryBlacklistSpecification());
            }
            if (context.filterConfig() != null && !context.filterConfig().isIncludeUnsupported()) {
                this.specifications.add(new SupportedRecipeSpecification());
            }
            if (context.showFavoritesOnly()) {
                this.specifications.add(new FavoriteOnlySpecification());
            }
        }
        if (parsedQuery != null && !parsedQuery.isEmpty()) {
            this.specifications.add(new ParsedQuerySpecification(parsedQuery));
        }
        return this;
    }

    public RecipeSpecification build() {
        if (specifications.isEmpty()) {
            return (recipe, context) -> true;
        }
        if (specifications.size() == 1) {
            return specifications.get(0);
        }

        List<RecipeSpecification> sorted = new ArrayList<>(specifications);
        sorted.sort(Comparator.comparingInt(RecipeSpecification::getCost));
        return new AndSpecification(sorted);
    }

    public static RecipeSpecification buildDefault(ParsedQuery parsedQuery, SearchExecutionContext context) {
        return new RecipeQuerySpecificationBuilder()
                .withDefaultFilters(parsedQuery, context)
                .build();
    }
}
