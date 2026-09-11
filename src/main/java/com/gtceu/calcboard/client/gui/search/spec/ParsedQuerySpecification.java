package com.gtceu.calcboard.client.gui.search.spec;

import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.ParsedQuery;

import java.util.Objects;

/**
 * Filter specification delegating search matching to the parsed query AST.
 */
public final class ParsedQuerySpecification implements RecipeSpecification {

    private final ParsedQuery parsedQuery;

    public ParsedQuerySpecification(ParsedQuery parsedQuery) {
        this.parsedQuery = Objects.requireNonNull(parsedQuery);
    }

    @Override
    public boolean isSatisfiedBy(SearchableRecipe recipe, SearchExecutionContext context) {
        if (parsedQuery.isEmpty()) return true;
        return RecipeSearchEngine.matches(recipe, parsedQuery);
    }

    @Override
    public int getCost() {
        return 100;
    }

    public ParsedQuery getParsedQuery() {
        return parsedQuery;
    }
}
