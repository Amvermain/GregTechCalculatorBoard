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
        tags.forEach(t -> outSb.append(t.toLowerCase()).append(" "));

        return new SearchableRecipe(
                null,
                displayName,
                modId.toLowerCase(),
                categoryId.toLowerCase(),
                categoryName.toLowerCase(),
                inSb.toString().trim(),
                outSb.toString().trim()
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

    @Test
    public void testLongComplexMultiTokenQuery() {
        SearchableRecipe advancedRecipe = createMockRecipe(
                "Superconducting Wire Processing",
                "gtceu",
                "wiremill",
                "Wiremill",
                List.of("Superconductor Wire", "Tiny Pile of Ash"),
                List.of("Superconducting Alloy Ingot", "Liquid Helium"),
                List.of("forge:wires/superconducting", "forge:ingots/alloy")
        );

        SearchableRecipe wrongModRecipe = createMockRecipe(
                "Superconductor Wire Processing",
                "create",
                "mechanical_press",
                "Mechanical Press",
                List.of("Superconductor Wire"),
                List.of("Superconducting Alloy Ingot"),
                List.of()
        );

        // 5+ token complex query with @mod, [cat], negation, tag, and keyword
        ParsedQuery longQuery = RecipeSearchEngine.parseQuery("@gtceu [wiremill] wire #forge:wires/superconducting !nitrogen");
        assertTrue(RecipeSearchEngine.matches(advancedRecipe, longQuery));
        assertFalse(RecipeSearchEngine.matches(wrongModRecipe, longQuery));

        // Negation fails when excluded term is present
        ParsedQuery negatedFailQuery = RecipeSearchEngine.parseQuery("@gtceu wire !ash");
        assertFalse(RecipeSearchEngine.matches(advancedRecipe, negatedFailQuery));
    }

    @Test
    public void testCalculateRankScoringAndShortCircuit() {
        SearchableRecipe ebfSteel = createMockRecipe(
                "Steel Ingot",
                "gtceu",
                "electric_blast_furnace",
                "Electric Blast Furnace",
                List.of("Steel Ingot", "Slag"),
                List.of("Iron Ingot", "Oxygen"),
                List.of()
        );

        // 1. Top score (3): matches directly in output or display name
        ParsedQuery qOutput = RecipeSearchEngine.parseQuery("Steel Ingot");
        assertEquals(3, RecipeSearchEngine.calculateRank(ebfSteel, qOutput));

        // 2. Medium score (2): matches in input or category
        ParsedQuery qInput = RecipeSearchEngine.parseQuery("Oxygen");
        assertEquals(2, RecipeSearchEngine.calculateRank(ebfSteel, qInput));

        ParsedQuery qCategory = RecipeSearchEngine.parseQuery("electric_blast_furnace");
        assertEquals(2, RecipeSearchEngine.calculateRank(ebfSteel, qCategory));

        // 3. General score (1): empty query or general index
        ParsedQuery qEmpty = RecipeSearchEngine.parseQuery("");
        assertEquals(1, RecipeSearchEngine.calculateRank(ebfSteel, qEmpty));
    }

    @Test
    public void testMalformedAndEdgeCaseQueries() {
        SearchableRecipe recipe = createMockRecipe(
                "Hydrofluoric Acid", "gtceu", "chemical_reactor", "Chemical Reactor",
                List.of("Hydrofluoric Acid"), List.of("Fluorite Dust", "Sulfuric Acid"), List.of()
        );

        // Unbalanced quotes
        ParsedQuery unclosedQuote = RecipeSearchEngine.parseQuery("\"Hydrofluoric Acid");
        assertTrue(RecipeSearchEngine.matches(recipe, unclosedQuote));

        // Multiple pipes and trailing spaces
        ParsedQuery multiPipes = RecipeSearchEngine.parseQuery("   ||| hydrofluoric |||   ");
        assertTrue(RecipeSearchEngine.matches(recipe, multiPipes));

        // Only special characters
        ParsedQuery specialChars = RecipeSearchEngine.parseQuery("! @ # [ ] & |");
        assertTrue(specialChars.isEmpty() || RecipeSearchEngine.matches(recipe, specialChars));

        // Null query
        ParsedQuery nullQuery = RecipeSearchEngine.parseQuery(null);
        assertTrue(nullQuery.isEmpty());
        assertTrue(RecipeSearchEngine.matches(recipe, nullQuery));
    }

    @Test
    public void testFormattingColorStripAndCaseInsensitivity() {
        String coloredName = "§6§lLV§r §bUpgrade§r §aKit§r";
        String stripped = RecipeSearchEngine.stripFormatting(coloredName);
        assertEquals("LV Upgrade Kit", stripped);

        // Null safe
        assertNull(RecipeSearchEngine.stripFormatting(null));
        assertEquals("Plain Text", RecipeSearchEngine.stripFormatting("Plain Text"));

        SearchableRecipe coloredRecipe = createMockRecipe(
                "§7LV§r Upgrade Kit", "kubejs", "assembler", "Assembler",
                List.of("§7LV§r Upgrade Kit"), List.of("LV Machine Hull"), List.of()
        );

        // Uppercase / lowercase case insensitivity
        ParsedQuery q1 = RecipeSearchEngine.parseQuery("UPGRADE KIT");
        assertTrue(RecipeSearchEngine.matches(coloredRecipe, q1));

        ParsedQuery q2 = RecipeSearchEngine.parseQuery("lv kit");
        assertTrue(RecipeSearchEngine.matches(coloredRecipe, q2));
    }

    @Test
    public void testCategoryBracketQueries() {
        SearchableRecipe smeltingRecipe = createMockRecipe(
                "Iron Ingot", "minecraft", "smelting", "Smelting",
                List.of("Iron Ingot"), List.of("Raw Iron"), List.of()
        );

        // [smelting] full bracket query
        ParsedQuery qBracket = RecipeSearchEngine.parseQuery("[smelting]");
        assertTrue(RecipeSearchEngine.matches(smeltingRecipe, qBracket));

        // [smelt open bracket query during typing
        ParsedQuery qOpenBracket = RecipeSearchEngine.parseQuery("[smelt");
        assertTrue(RecipeSearchEngine.matches(smeltingRecipe, qOpenBracket));

        // %smelting percent prefix query
        ParsedQuery qPercent = RecipeSearchEngine.parseQuery("%smelting");
        assertTrue(RecipeSearchEngine.matches(smeltingRecipe, qPercent));
    }

    @Test
    public void testContextualSearchFiltering() {
        SearchableRecipe tetrafluoroethyleneRecipe = createMockRecipe(
                "Tetrafluoroethylene", "gtceu", "chemical_reactor", "Chemical Reactor",
                List.of("Tetrafluoroethylene"), List.of("Chloroform", "Hydrofluoric Acid"), List.of("gtceu:tetrafluoroethylene")
        );

        SearchableRecipe ashesRecipe = createMockRecipe(
                "Ashes", "gtceu", "chemical_reactor", "Chemical Reactor",
                List.of("Ashes", "Carbon Monoxide"), List.of("Coal", "Oxygen"), List.of("gtceu:ashes")
        );

        // When looking for producers of Tetrafluoroethylene, ashes recipe does not have it in outputs
        assertTrue(tetrafluoroethyleneRecipe.hasOutput("Tetrafluoroethylene"));
        assertFalse(ashesRecipe.hasOutput("Tetrafluoroethylene"));

        // When searching "[chemical reactor]", both match category
        ParsedQuery qCategory = RecipeSearchEngine.parseQuery("[chemical reactor]");
        assertTrue(RecipeSearchEngine.matches(tetrafluoroethyleneRecipe, qCategory));
        assertTrue(RecipeSearchEngine.matches(ashesRecipe, qCategory));
    }

    @Test
    public void testInputAndOutputPrefixes() {
        SearchableRecipe steelRecipe = createMockRecipe(
                "Steel Ingot", "gtceu", "electric_blast_furnace", "Electric Blast Furnace",
                List.of("Steel Ingot", "Slag"), List.of("Iron Ingot", "Oxygen"), List.of("forge:ingots/steel")
        );

        SearchableRecipe ironRecipe = createMockRecipe(
                "Iron Ingot", "minecraft", "smelting", "Smelting",
                List.of("Iron Ingot"), List.of("Hematite Chunk"), List.of("forge:ingots/iron")
        );

        SearchableRecipe chemicalRecipe = createMockRecipe(
                "Heavy Fuel", "gtceu", "chemical_reactor", "Chemical Reactor",
                List.of("Heavy Fuel"), List.of("Heavy Oil", "Hydrogen"), List.of()
        );

        // 1. in: prefix
        ParsedQuery qIn = RecipeSearchEngine.parseQuery("in:iron");
        assertTrue(RecipeSearchEngine.matches(steelRecipe, qIn), "steelRecipe has Iron Ingot in inputs");
        assertFalse(RecipeSearchEngine.matches(ironRecipe, qIn), "ironRecipe has Iron Ingot in outputs, not inputs");

        // 2. input: prefix
        ParsedQuery qInput = RecipeSearchEngine.parseQuery("input:iron");
        assertTrue(RecipeSearchEngine.matches(steelRecipe, qInput));
        assertFalse(RecipeSearchEngine.matches(ironRecipe, qInput));

        // 3. > prefix (shortcut for input)
        ParsedQuery qArrowIn = RecipeSearchEngine.parseQuery(">iron");
        assertTrue(RecipeSearchEngine.matches(steelRecipe, qArrowIn));
        assertFalse(RecipeSearchEngine.matches(ironRecipe, qArrowIn));

        // 4. out: prefix
        ParsedQuery qOut = RecipeSearchEngine.parseQuery("out:steel");
        assertTrue(RecipeSearchEngine.matches(steelRecipe, qOut));
        assertFalse(RecipeSearchEngine.matches(ironRecipe, qOut));

        // 5. output: prefix
        ParsedQuery qOutput = RecipeSearchEngine.parseQuery("output:iron");
        assertTrue(RecipeSearchEngine.matches(ironRecipe, qOutput));
        assertFalse(RecipeSearchEngine.matches(steelRecipe, qOutput));

        // 6. < prefix (shortcut for output)
        ParsedQuery qArrowOut = RecipeSearchEngine.parseQuery("<iron");
        assertTrue(RecipeSearchEngine.matches(ironRecipe, qArrowOut));
        assertFalse(RecipeSearchEngine.matches(steelRecipe, qArrowOut));

        // 7. Combined in: and out:
        ParsedQuery qBoth = RecipeSearchEngine.parseQuery("in:iron out:steel");
        assertTrue(RecipeSearchEngine.matches(steelRecipe, qBoth));
        assertFalse(RecipeSearchEngine.matches(ironRecipe, qBoth));

        // 8. Negated in / out
        ParsedQuery qNegOut = RecipeSearchEngine.parseQuery("in:iron !out:slag");
        assertFalse(RecipeSearchEngine.matches(steelRecipe, qNegOut), "steelRecipe outputs slag, so should be excluded");

        // 9. Quoted phrases in input / output
        ParsedQuery qQuotedIn = RecipeSearchEngine.parseQuery("in:\"Heavy Oil\"");
        assertTrue(RecipeSearchEngine.matches(chemicalRecipe, qQuotedIn));
        assertFalse(RecipeSearchEngine.matches(steelRecipe, qQuotedIn));

        ParsedQuery qQuotedOut = RecipeSearchEngine.parseQuery("out:\"Heavy Fuel\"");
        assertTrue(RecipeSearchEngine.matches(chemicalRecipe, qQuotedOut));
        assertFalse(RecipeSearchEngine.matches(steelRecipe, qQuotedOut));
    }

    @Test
    public void testSteamProducerExactMatching() {
        net.minecraft.resources.ResourceLocation steamId = net.minecraft.resources.ResourceLocation.tryParse("gtceu:steam");
        net.minecraft.resources.ResourceLocation lavaId = net.minecraft.resources.ResourceLocation.tryParse("minecraft:lava");
        net.minecraft.resources.ResourceLocation steamPipeId = net.minecraft.resources.ResourceLocation.tryParse("gtceu:small_steam_pipe");
        net.minecraft.resources.ResourceLocation boilerBlockId = net.minecraft.resources.ResourceLocation.tryParse("gtceu:hp_steam_liquid_boiler");

        // 1. Steam Boiler (Lava) recipe: produces Steam fluid
        SearchableRecipe steamBoilerRecipe = new SearchableRecipe(
                null,
                "Steam Boiler (Lava)",
                "gtceu",
                "steam_boiler",
                "Steam Boiler",
                "minecraft:lava lava gtceu:steam_boiler steam_boiler",
                "gtceu:steam steam",
                new net.minecraft.resources.ResourceLocation[]{lavaId},
                new net.minecraft.resources.ResourceLocation[]{steamId},
                new String[]{"Lava"},
                new String[]{"Steam"}
        );

        // 2. Steam Pipe crafting recipe: produces Steam Pipe item (NOT steam fluid)
        SearchableRecipe steamPipeCrafting = new SearchableRecipe(
                null,
                "Steam Pipe",
                "gtceu",
                "crafting",
                "Crafting",
                "gtceu:bronze_ingot bronze_ingot",
                "gtceu:small_steam_pipe small_steam_pipe steam pipe",
                null,
                new net.minecraft.resources.ResourceLocation[]{steamPipeId},
                null,
                new String[]{"Small Steam Pipe"}
        );

        // 3. High Pressure Steam Liquid Boiler crafting recipe: produces Boiler block (NOT steam fluid)
        SearchableRecipe boilerBlockCrafting = new SearchableRecipe(
                null,
                "High Pressure Steam Liquid Boiler",
                "gtceu",
                "crafting",
                "Crafting",
                "gtceu:steel_plate steel_plate",
                "gtceu:hp_steam_liquid_boiler hp_steam_liquid_boiler high pressure steam liquid boiler",
                null,
                new net.minecraft.resources.ResourceLocation[]{boilerBlockId},
                null,
                new String[]{"High Pressure Steam Liquid Boiler"}
        );

        // Exact match verification for Producer search of Steam (gtceu:steam)
        assertTrue(steamBoilerRecipe.hasExactOutput(steamId));
        assertFalse(steamPipeCrafting.hasExactOutput(steamId));
        assertFalse(boilerBlockCrafting.hasExactOutput(steamId));

        // Exact path verification
        assertTrue(steamBoilerRecipe.hasOutputPath("steam"));
        assertFalse(steamPipeCrafting.hasOutputPath("steam"));
        assertFalse(boilerBlockCrafting.hasOutputPath("steam"));

        // Exact display name verification
        assertTrue(steamBoilerRecipe.hasExactOutputName("steam"));
        assertFalse(steamPipeCrafting.hasExactOutputName("steam"));
        assertFalse(boilerBlockCrafting.hasExactOutputName("steam"));
    }

    @Test
    public void testStressUnitsProducerConsumerMatch() {
        var stressId = new net.minecraft.resources.ResourceLocation("create", "stress_units");
        var waterWheelId = new net.minecraft.resources.ResourceLocation("create", "large_water_wheel");
        var mixerId = new net.minecraft.resources.ResourceLocation("create", "mechanical_mixer");

        // Create virtual kinetic recipes
        List<SearchableRecipe> kineticRecipes = com.gtceu.calcboard.compat.create.CreateRecipeHandler.getVirtualKineticSearchRecipes();
        assertFalse(kineticRecipes.isEmpty());

        SearchableRecipe waterWheel = kineticRecipes.stream()
                .filter(r -> r.displayName().contains("Large Water Wheel"))
                .findFirst()
                .orElse(null);
        assertNotNull(waterWheel);

        // Verify water wheel produces Stress Units
        assertTrue(waterWheel.hasExactOutput(stressId));
        assertTrue(waterWheel.hasOutputPath("stress_units"));
        assertTrue(waterWheel.hasExactOutputName("stress units"));

        // Search query verification
        ParsedQuery qStress = RecipeSearchEngine.parseQuery("<su");
        assertTrue(RecipeSearchEngine.matches(waterWheel, qStress));

        ParsedQuery qStressName = RecipeSearchEngine.parseQuery("<\"Stress Units\"");
        assertTrue(RecipeSearchEngine.matches(waterWheel, qStressName));
    }
}
