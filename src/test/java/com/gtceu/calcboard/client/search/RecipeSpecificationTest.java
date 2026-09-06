package com.gtceu.calcboard.client.search;

import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.client.gui.search.RecipeFilterConfig;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.ParsedQuery;
import com.gtceu.calcboard.client.gui.search.spec.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

public class RecipeSpecificationTest {

    private SearchableRecipe recipeA;
    private SearchableRecipe recipeB;
    private SearchableRecipe unsupportedRecipe;
    private ResourceLocation recipeIdA;
    private ResourceLocation recipeIdB;

    @BeforeEach
    public void setup() {
        recipeIdA = ResourceLocation.tryParse("gtceu:recipe_a");
        recipeIdB = ResourceLocation.tryParse("gtceu:recipe_b");

        recipeA = new SearchableRecipe(
                null,
                recipeIdA,
                "Chemical Reactor Nitrogen",
                "gtceu",
                "gtceu:chemical_reactor",
                "Chemical Reactor",
                "nitrogen",
                "ammonia",
                new ResourceLocation[]{ResourceLocation.tryParse("gtceu:nitrogen")},
                new ResourceLocation[]{ResourceLocation.tryParse("gtceu:ammonia")},
                new String[]{"Nitrogen"},
                new String[]{"Ammonia"},
                true
        );

        recipeB = new SearchableRecipe(
                null,
                recipeIdB,
                "Electric Blast Furnace Steel",
                "gtceu",
                "gtceu:electric_blast_furnace",
                "Electric Blast Furnace",
                "iron oxygen",
                "steel slag",
                new ResourceLocation[]{ResourceLocation.tryParse("minecraft:iron_ingot")},
                new ResourceLocation[]{ResourceLocation.tryParse("gtceu:steel_ingot")},
                new String[]{"Iron Ingot"},
                new String[]{"Steel Ingot"},
                true
        );

        unsupportedRecipe = new SearchableRecipe(
                null,
                ResourceLocation.tryParse("custom:unsupported"),
                "Unsupported Magic Craft",
                "custom",
                "custom:altar",
                "Altar",
                "dust",
                "gem",
                null,
                null,
                null,
                null,
                false
        );
    }

    @Test
    public void testCategoryBlacklistSpecification() {
        RecipeFilterConfig config = RecipeFilterConfig.getInstance();
        config.resetDefaults();
        config.setCategoryExcluded("gtceu:chemical_reactor", true);

        SearchExecutionContext context = new SearchExecutionContext(
                null, false, Set.of(), config, false
        );

        CategoryBlacklistSpecification spec = new CategoryBlacklistSpecification();
        assertFalse(spec.isSatisfiedBy(recipeA, context));
        assertTrue(spec.isSatisfiedBy(recipeB, context));

        config.setCategoryExcluded("gtceu:chemical_reactor", false);
        assertTrue(spec.isSatisfiedBy(recipeA, context));
    }

    @Test
    public void testSupportedRecipeSpecification() {
        RecipeFilterConfig config = RecipeFilterConfig.getInstance();
        config.setIncludeUnsupported(false);

        SearchExecutionContext context = new SearchExecutionContext(
                null, false, Set.of(), config, false
        );

        SupportedRecipeSpecification spec = new SupportedRecipeSpecification();
        assertTrue(spec.isSatisfiedBy(recipeA, context));
        assertFalse(spec.isSatisfiedBy(unsupportedRecipe, context));

        config.setIncludeUnsupported(true);
        assertTrue(spec.isSatisfiedBy(unsupportedRecipe, context));
        config.setIncludeUnsupported(false);
    }

    @Test
    public void testFavoriteOnlySpecification() {
        SearchExecutionContext contextFavOnly = new SearchExecutionContext(
                null, true, Set.of(recipeIdA), null, false
        );
        SearchExecutionContext contextAll = new SearchExecutionContext(
                null, false, Set.of(recipeIdA), null, false
        );

        FavoriteOnlySpecification spec = new FavoriteOnlySpecification();
        assertTrue(spec.isSatisfiedBy(recipeA, contextFavOnly));
        assertFalse(spec.isSatisfiedBy(recipeB, contextFavOnly));

        assertTrue(spec.isSatisfiedBy(recipeA, contextAll));
        assertTrue(spec.isSatisfiedBy(recipeB, contextAll));
    }

    @Test
    public void testParsedQuerySpecification() {
        ParsedQuery queryNitrogen = RecipeSearchEngine.parseQuery("nitrogen");
        ParsedQuery querySteel = RecipeSearchEngine.parseQuery("steel");

        ParsedQuerySpecification specNitrogen = new ParsedQuerySpecification(queryNitrogen);
        ParsedQuerySpecification specSteel = new ParsedQuerySpecification(querySteel);

        assertTrue(specNitrogen.isSatisfiedBy(recipeA, null));
        assertFalse(specNitrogen.isSatisfiedBy(recipeB, null));

        assertFalse(specSteel.isSatisfiedBy(recipeA, null));
        assertTrue(specSteel.isSatisfiedBy(recipeB, null));
    }

    @Test
    public void testAndShortCircuitEvaluation() {
        AtomicBoolean secondEvaluated = new AtomicBoolean(false);

        RecipeSpecification falseSpec = (r, c) -> false;
        RecipeSpecification probeSpec = (r, c) -> {
            secondEvaluated.set(true);
            return true;
        };

        AndSpecification andSpec = new AndSpecification(falseSpec, probeSpec);
        boolean result = andSpec.isSatisfiedBy(recipeA, null);

        assertFalse(result);
        assertFalse(secondEvaluated.get());
    }

    @Test
    public void testOrShortCircuitEvaluation() {
        AtomicBoolean secondEvaluated = new AtomicBoolean(false);

        RecipeSpecification trueSpec = (r, c) -> true;
        RecipeSpecification probeSpec = (r, c) -> {
            secondEvaluated.set(true);
            return false;
        };

        OrSpecification orSpec = new OrSpecification(trueSpec, probeSpec);
        boolean result = orSpec.isSatisfiedBy(recipeA, null);

        assertTrue(result);
        assertFalse(secondEvaluated.get());
    }

    @Test
    public void testNotSpecification() {
        RecipeSpecification trueSpec = (r, c) -> true;
        NotSpecification notSpec = new NotSpecification(trueSpec);

        assertFalse(notSpec.isSatisfiedBy(recipeA, null));
    }

    @Test
    public void testRecipeQuerySpecificationBuilderCostOrdering() {
        RecipeFilterConfig config = RecipeFilterConfig.getInstance();
        config.setCategoryExcluded("gtceu:chemical_reactor", true);

        SearchExecutionContext context = new SearchExecutionContext(
                null, true, Set.of(recipeIdB), config, false
        );

        ParsedQuery query = RecipeSearchEngine.parseQuery("steel");
        RecipeSpecification composite = RecipeQuerySpecificationBuilder.buildDefault(query, context);

        assertInstanceOf(AndSpecification.class, composite);
        AndSpecification andSpec = (AndSpecification) composite;
        List<RecipeSpecification> specs = andSpec.getSpecifications();

        assertTrue(specs.size() >= 2);
        for (int i = 0; i < specs.size() - 1; i++) {
            assertTrue(specs.get(i).getCost() <= specs.get(i + 1).getCost());
        }

        assertFalse(composite.isSatisfiedBy(recipeA, context));
        assertTrue(composite.isSatisfiedBy(recipeB, context));

        config.resetDefaults();
    }
}
