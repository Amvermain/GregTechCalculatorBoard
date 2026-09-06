package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.NodeRateCalculator;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Regression test for GTCEu tickInputs and tickOutputs extraction.
 * Verifies that fluids and items specified per-tick (e.g. Greenhouse nitrate solution,
 * Pisciculture fishery breeding fluids) are properly normalized by durationTicks
 * so that rate calculations (mB/t, items/t) match the actual per-tick values rather
 * than being erroneously divided by duration.
 */
public class GTRecipeTickFluidExtractionTest {

    public static class MockTickContent {
        public Object content;
        public int chance;
        public int tierChanceBoost;

        public MockTickContent(Object content, int chance, int tierChanceBoost) {
            this.content = content;
            this.chance = chance;
            this.tierChanceBoost = tierChanceBoost;
        }
    }

    public static class MockGreenhouseRecipe extends GTRecipeExtractionTest.StructuralGTRecipeFixture {
        public int duration = 6000; // 300 seconds

        public MockGreenhouseRecipe() {
            this.duration = 6000;
            // tickInputs contains 1 mB nitrate solution with 10% chance (1000 in GTCEu 10000-scale)
            tickInputs.put("gtceu:fluid", List.of(
                    new MockTickContent(
                            IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrate_solution"), "Nitrate Solution", 1.0),
                            1000,
                            0
                    )
            ));
        }

        public long getInputEUt() {
            return 15L;
        }
    }

    public static class MockPiscicultureRecipe extends GTRecipeExtractionTest.StructuralGTRecipeFixture {
        public int duration = 6000; // 300 seconds

        public MockPiscicultureRecipe() {
            this.duration = 6000;
            // tickOutputs contains 8 mB fluid with 100% chance (10000)
            tickOutputs.put("gtceu:fluid", List.of(
                    new MockTickContent(
                            IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrate_solution"), "Nitrate Solution", 8.0),
                            10000,
                            0
                    )
            ));
        }

        public long getInputEUt() {
            return 15L;
        }
    }

    @Test
    @DisplayName("GTCEuRecipeHandler must extract tickInputs fluids scaled by durationTicks")
    public void testExtractTickInputsFluidsScaledByDuration() {
        MockGreenhouseRecipe recipe = new MockGreenhouseRecipe();

        List<IngredientStack> tickInputs = GTCEuRecipeHandler.extractTickIngredients(recipe, "tickInputs", recipe.duration);
        Assertions.assertNotNull(tickInputs, "Tick inputs must not be null");
        Assertions.assertEquals(1, tickInputs.size(), "Must extract 1 tick input fluid");

        IngredientStack nitrate = tickInputs.get(0);
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:nitrate_solution"), nitrate.getId());
        Assertions.assertTrue(nitrate.isFluid());
        Assertions.assertEquals(0.10, nitrate.getChance(), 1e-4, "Chance must be 10%");

        // 1 mB/t * 6000 ticks = 6000 mB batch amount
        Assertions.assertEquals(6000.0, nitrate.getAmount(), 1e-4, "Batch amount must be scaled by duration (1 mB/t * 6000t = 6000 mB)");

        // Rate check in RecipeNode: 1 machine, duration 6000 ticks (0.00333 cps)
        RecipeNode node = new RecipeNode("greenhouse-1", "Greenhouse", 6000, 15, GTVoltageTier.LV);
        node.addInput(nitrate);

        // Effective input rate: 6000 mB * 0.10 chance * (20 / 6000 cps) = 2.0 mB/s = 0.1 mB/t
        double ratePerSec = NodeRateCalculator.getInputSlotRate(node, 0, false);
        Assertions.assertEquals(2.0, ratePerSec, 1e-4, "Nominal input rate must be 2.0 mB/s");
        double ratePerTick = ratePerSec / 20.0;
        Assertions.assertEquals(0.1, ratePerTick, 1e-6, "Input rate per tick must be 0.1 mB/t (1 mB/t * 10% chance)");
    }

    @Test
    @DisplayName("GTCEuRecipeHandler must extract tickOutputs fluids scaled by durationTicks")
    public void testExtractTickOutputsFluidsScaledByDuration() {
        MockPiscicultureRecipe recipe = new MockPiscicultureRecipe();

        List<IngredientStack> tickOutputs = GTCEuRecipeHandler.extractTickIngredients(recipe, "tickOutputs", recipe.duration);
        Assertions.assertNotNull(tickOutputs, "Tick outputs must not be null");
        Assertions.assertEquals(1, tickOutputs.size(), "Must extract 1 tick output fluid");

        IngredientStack nitrate = tickOutputs.get(0);
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:nitrate_solution"), nitrate.getId());
        Assertions.assertTrue(nitrate.isFluid());
        Assertions.assertEquals(1.0, nitrate.getChance(), 1e-4, "Chance must be 100%");

        // 8 mB/t * 6000 ticks = 48000 mB batch amount
        Assertions.assertEquals(48000.0, nitrate.getAmount(), 1e-4, "Batch amount must be scaled by duration (8 mB/t * 6000t = 48000 mB)");

        // Rate check in RecipeNode: 1 machine, duration 6000 ticks
        RecipeNode node = new RecipeNode("pisciculture-1", "Pisciculture Fishery", 6000, 15, GTVoltageTier.LV);
        node.addOutput(nitrate);

        // Effective output rate: 48000 mB * 1.0 chance * (20 / 6000 cps) = 160.0 mB/s = 8.0 mB/t
        double ratePerSec = NodeRateCalculator.getOutputSlotRate(node, 0, false);
        Assertions.assertEquals(160.0, ratePerSec, 1e-4, "Nominal output rate must be 160.0 mB/s");
        double ratePerTick = ratePerSec / 20.0;
        Assertions.assertEquals(8.0, ratePerTick, 1e-6, "Output rate per tick must be 8.0 mB/t");
    }

    @Test
    @DisplayName("Pisciculture produces 8 times more fluid than Greenhouse uses (verification of reporter ratio)")
    public void testPiscicultureToGreenhouseRatio() {
        MockGreenhouseRecipe ghRecipe = new MockGreenhouseRecipe();
        MockPiscicultureRecipe pfRecipe = new MockPiscicultureRecipe();

        IngredientStack ghInput = GTCEuRecipeHandler.extractTickIngredients(ghRecipe, "tickInputs", ghRecipe.duration).get(0);
        IngredientStack pfOutput = GTCEuRecipeHandler.extractTickIngredients(pfRecipe, "tickOutputs", pfRecipe.duration).get(0);

        RecipeNode greenhouse = new RecipeNode("gh", "Greenhouse", 6000, 15, GTVoltageTier.LV);
        greenhouse.addInput(ghInput);

        RecipeNode pisciculture = new RecipeNode("pf", "Pisciculture Fishery", 6000, 15, GTVoltageTier.LV);
        pisciculture.addOutput(pfOutput);

        double ghDemandPerTick = NodeRateCalculator.getInputSlotRate(greenhouse, 0, false) / 20.0; // 0.1 mB/t
        double pfSupplyPerTick = NodeRateCalculator.getOutputSlotRate(pisciculture, 0, false) / 20.0; // 8.0 mB/t

        double ratio = pfSupplyPerTick / ghDemandPerTick;
        Assertions.assertEquals(80.0, ratio, 1e-4, "8.0 mB/t / 0.1 mB/t = 80x ratio when considering 10% chance, or 8x when comparing base 8 mB/t vs 1 mB/t");
    }
}
