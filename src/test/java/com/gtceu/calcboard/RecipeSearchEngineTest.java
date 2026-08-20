package com.gtceu.calcboard;

import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.ParsedQuery;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.SearchableRecipe;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RecipeSearchEngineTest {

    private SearchableRecipe createMockRecipe(
            String displayName,
            String modId,
            String categoryId,
            String categoryName,
            List<String> outputNames,
            List<String> inputNames,
            List<String> tags
    ) {
        StringBuilder outSb = new StringBuilder();
        outputNames.forEach(o -> outSb.append(o.toLowerCase()).append(" "));
        StringBuilder inSb = new StringBuilder();
        inputNames.forEach(i -> inSb.append(i.toLowerCase()).append(" "));

        StringBuilder fullSb = new StringBuilder();
        fullSb.append(displayName.toLowerCase()).append(" ");
        fullSb.append(modId.toLowerCase()).append(" ");
        fullSb.append(categoryId.toLowerCase()).append(" ");
        fullSb.append(categoryName.toLowerCase()).append(" ");
        fullSb.append(outSb).append(" ").append(inSb);

        return new SearchableRecipe(
                null,
                displayName,
                modId.toLowerCase(),
                categoryId.toLowerCase(),
                categoryName.toLowerCase(),
                outputNames,
                inputNames,
                outputNames,
                inputNames,
                List.of(),
                List.of(),
                tags,
                outSb.toString(),
                inSb.toString(),
                fullSb.toString()
        );
    }

    @Test
    public void testMultiTokenAndSearch() {
        SearchableRecipe pyrolyseOil = createMockRecipe(
                "Heavy Oil from Logs",
                "gtceu",
                "pyrolyse_oven",
                "Pyrolyse Oven",
                List.of("Heavy Oil", "Charcoal"),
                List.of("Oak Log", "Nitrogen"),
                List.of("minecraft:logs")
        );

        SearchableRecipe blastFurnaceSteel = createMockRecipe(
                "Steel Ingot",
                "gtceu",
                "electric_blast_furnace",
                "Electric Blast Furnace",
                List.of("Steel Ingot", "Slag"),
                List.of("Iron Ingot", "Oxygen"),
                List.of("forge:ingots/iron")
        );

        ParsedQuery q1 = RecipeSearchEngine.parseQuery("pyrolyse oil");
        assertTrue(RecipeSearchEngine.matches(pyrolyseOil, q1));
        assertFalse(RecipeSearchEngine.matches(blastFurnaceSteel, q1));

        ParsedQuery q2 = RecipeSearchEngine.parseQuery("pyrolyse & charcoal");
        assertTrue(RecipeSearchEngine.matches(pyrolyseOil, q2));
        assertFalse(RecipeSearchEngine.matches(blastFurnaceSteel, q2));
    }

    @Test
    public void testOrCondition() {
        SearchableRecipe pyrolyseOil = createMockRecipe(
                "Heavy Oil", "gtceu", "pyrolyse_oven", "Pyrolyse Oven",
                List.of("Heavy Oil"), List.of("Oak Log"), List.of()
        );
        SearchableRecipe pyrolyseCreosote = createMockRecipe(
                "Creosote Oil", "gtceu", "pyrolyse_oven", "Pyrolyse Oven",
                List.of("Creosote Oil"), List.of("Oak Log"), List.of()
        );
        SearchableRecipe steel = createMockRecipe(
                "Steel Ingot", "gtceu", "electric_blast_furnace", "Electric Blast Furnace",
                List.of("Steel Ingot"), List.of("Iron Ingot"), List.of()
        );

        ParsedQuery q = RecipeSearchEngine.parseQuery("oil | creosote");
        assertTrue(RecipeSearchEngine.matches(pyrolyseOil, q));
        assertTrue(RecipeSearchEngine.matches(pyrolyseCreosote, q));
        assertFalse(RecipeSearchEngine.matches(steel, q));
    }

    @Test
    public void testNotCondition() {
        SearchableRecipe recipeWithCharcoal = createMockRecipe(
                "Heavy Oil from Logs", "gtceu", "pyrolyse_oven", "Pyrolyse Oven",
                List.of("Heavy Oil", "Charcoal"), List.of("Oak Log"), List.of()
        );
        SearchableRecipe recipeWithoutCharcoal = createMockRecipe(
                "Heavy Oil from Biomass", "gtceu", "distillery", "Distillery",
                List.of("Heavy Oil"), List.of("Biomass"), List.of()
        );

        ParsedQuery q = RecipeSearchEngine.parseQuery("oil !charcoal");
        assertFalse(RecipeSearchEngine.matches(recipeWithCharcoal, q));
        assertTrue(RecipeSearchEngine.matches(recipeWithoutCharcoal, q));
    }

    @Test
    public void testPrefixFilters() {
        SearchableRecipe gtceuRecipe = createMockRecipe(
                "Copper Ingot", "gtceu", "electric_blast_furnace", "Electric Blast Furnace",
                List.of("Copper Ingot"), List.of("Copper Dust"), List.of("forge:dusts/copper")
        );
        SearchableRecipe thermalRecipe = createMockRecipe(
                "Copper Ingot", "thermal", "furnace", "Induction Smelter",
                List.of("Copper Ingot"), List.of("Copper Ore"), List.of("forge:ores/copper")
        );

        // Mod ID filter
        ParsedQuery qMod = RecipeSearchEngine.parseQuery("@gtceu copper");
        assertTrue(RecipeSearchEngine.matches(gtceuRecipe, qMod));
        assertFalse(RecipeSearchEngine.matches(thermalRecipe, qMod));

        // Category filter
        ParsedQuery qCat = RecipeSearchEngine.parseQuery("[electric_blast_furnace]");
        assertTrue(RecipeSearchEngine.matches(gtceuRecipe, qCat));
        assertFalse(RecipeSearchEngine.matches(thermalRecipe, qCat));

        // Tag filter
        ParsedQuery qTag = RecipeSearchEngine.parseQuery("#forge:dusts/copper");
        assertTrue(RecipeSearchEngine.matches(gtceuRecipe, qTag));
        assertFalse(RecipeSearchEngine.matches(thermalRecipe, qTag));
    }

    @Test
    public void testQuotedPhrase() {
        SearchableRecipe r1 = createMockRecipe(
                "Heavy Fuel Processing", "gtceu", "chemical_reactor", "Chemical Reactor",
                List.of("Heavy Fuel"), List.of("Hydrogen"), List.of()
        );
        SearchableRecipe r2 = createMockRecipe(
                "Heavy Iron with Fuel", "gtceu", "chemical_reactor", "Chemical Reactor",
                List.of("Iron"), List.of("Heavy Block", "Fuel Pellets"), List.of()
        );

        ParsedQuery q = RecipeSearchEngine.parseQuery("\"Heavy Fuel\"");
        assertTrue(RecipeSearchEngine.matches(r1, q));
        // r2 has "Heavy" and "Fuel" but not contiguous "heavy fuel"
        assertFalse(RecipeSearchEngine.matches(r2, q));
    }
}
