package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.GTCEuRecipeHandler;
import com.gtceu.calcboard.integration.emi.EmiRecipeConverter;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GTRecipeExtractionTest {

    public static class StructuralGTRecipeFixture {
        public final Object recipeType = new Object();
        public final Map<Object, List<Object>> inputs = new HashMap<>();
        public final Map<Object, List<Object>> outputs = new HashMap<>();
        public final Map<Object, List<Object>> tickInputs = new HashMap<>();
        public final Map<Object, List<Object>> tickOutputs = new HashMap<>();
        public int duration;
    }

    public static class MockGTRecipe extends StructuralGTRecipeFixture {
        public int duration = 160;
        public CompoundTag data = new CompoundTag();

        public long getInputEUt() {
            return 65536L;
        }

        public long getOutputEUt() {
            return 0L;
        }
    }

    public static class MockGTGeneratorRecipe extends StructuralGTRecipeFixture {
        public long duration = 40L;

        public long getInputEUt() {
            return 0L;
        }

        public long getOutputEUt() {
            return 2048L;
        }
    }

    public static class BacterialHarvestingCustomRecipe {
        private final int dur = 160;

        public int duration() {
            return dur;
        }

        public long getInputEUt() {
            return 65536L;
        }
    }

    public static class MockMapEnergyRecipe extends StructuralGTRecipeFixture {

        public MockMapEnergyRecipe() {
            duration = 100;
            tickInputs.put("gtceu:eur_capability", List.of(65536L));
        }
    }

    public static class MockEnergyContent {
        private final Long content;

        public MockEnergyContent(long content) {
            this.content = content;
        }

        public Object getContent() {
            return content;
        }
    }

    public static class GTRecipeWrapperFixture {
        public final Object recipe;

        public GTRecipeWrapperFixture(Object recipe) {
            this.recipe = recipe;
        }
    }

    public static class PrivateBackingRecipeWrapperFixture {
        private final Object backing;

        public PrivateBackingRecipeWrapperFixture(Object backing) {
            this.backing = backing;
        }
    }

    public static class UnrelatedGTRecipeWrapperFixture {
        public final Object recipe = new Object();

        public long getInputEUt() {
            throw new NoClassDefFoundError("optional/dependency/ShouldNotBeLoaded");
        }
    }

    public static class ShadowedBackingBaseFixture {
        public final Object recipe;

        public ShadowedBackingBaseFixture(Object recipe) {
            this.recipe = recipe;
        }
    }

    public static class ShadowedBackingSubFixture extends ShadowedBackingBaseFixture {
        public Object recipe = null;

        public ShadowedBackingSubFixture(Object recipe) {
            super(recipe);
        }
    }

    public static class MockVoltageAmperageObject {
        public long voltage() {
            return 8192L;
        }

        public long amperage() {
            return 2L;
        }
    }

    public static class MockObjectEnergyRecipe {
        public int duration = 80;

        public Object getInputEUt() {
            return new MockVoltageAmperageObject();
        }
    }

    @Test
    public void testExtractGTRecipeWithLongInputEUt() {
        MockGTRecipe recipe = new MockGTRecipe();
        recipe.data.putInt("ebf_temp", 1800);

        Assertions.assertTrue(GTCEuRecipeHandler.isGTRecipe(recipe));

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(recipe, details);

        Assertions.assertEquals(160.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(65536.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.ZPM, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
        Assertions.assertFalse(details.isGenerator);
        Assertions.assertEquals(1800, details.backingRecipeTemp);
    }

    @Test
    public void testExtractGTRecipeGeneratorWithOutputEUt() {
        MockGTGeneratorRecipe recipe = new MockGTGeneratorRecipe();

        Assertions.assertTrue(GTCEuRecipeHandler.isGTRecipe(recipe));

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(recipe, details);

        Assertions.assertEquals(40.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(2048.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.EV, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
        Assertions.assertTrue(details.isGenerator);
    }

    @Test
    public void testExtractGTRecipeWithCustomSubclass() {
        BacterialHarvestingCustomRecipe recipe = new BacterialHarvestingCustomRecipe();

        Assertions.assertTrue(GTCEuRecipeHandler.isGTRecipe(recipe));

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(recipe, details);

        Assertions.assertEquals(160.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(65536.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.ZPM, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
        Assertions.assertFalse(details.isGenerator);
    }

    @Test
    public void testExtractEnergyFromMapInspection() {
        MockMapEnergyRecipe recipe = new MockMapEnergyRecipe();

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(recipe, details);

        Assertions.assertEquals(100.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(65536.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.ZPM, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
    }

    @Test
    public void testUnwrapsJeiWrapperBeforeExtractingInputDetails() {
        StructuralGTRecipeFixture recipe = new StructuralGTRecipeFixture();
        recipe.duration = 120;
        recipe.tickInputs.put("gtceu:eu_recipe_capability", List.of(new MockEnergyContent(8192L)));
        GTRecipeWrapperFixture wrapper = new GTRecipeWrapperFixture(recipe);

        Assertions.assertTrue(GTCEuRecipeHandler.isGTRecipe(recipe));
        Assertions.assertTrue(GTCEuRecipeHandler.isGTRecipe(wrapper));
        Assertions.assertSame(recipe, GTCEuRecipeHandler.unwrapRecipe(wrapper));
        Assertions.assertNotSame(wrapper, GTCEuRecipeHandler.unwrapRecipe(wrapper), "JEI wrapper must not be treated as the recipe body");

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(wrapper, details);

        Assertions.assertEquals(120.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(8192.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.IV, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
        Assertions.assertFalse(details.isGenerator);
    }

    @Test
    public void testUnwrapsPrivateBackingFieldAndExtractsOutputDetails() {
        StructuralGTRecipeFixture recipe = new StructuralGTRecipeFixture();
        recipe.duration = 40;
        recipe.tickOutputs.put("gtceu:eu_recipe_capability", List.of(new MockEnergyContent(2048L)));
        PrivateBackingRecipeWrapperFixture wrapper = new PrivateBackingRecipeWrapperFixture(recipe);

        Assertions.assertSame(recipe, GTCEuRecipeHandler.unwrapRecipe(wrapper));

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(wrapper, details);

        Assertions.assertEquals(40.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(2048.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.EV, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
        Assertions.assertTrue(details.isGenerator);
    }

    @Test
    public void testUnrelatedRecipeIsNotAcceptedByNameOrEnergyMethod() {
        UnrelatedGTRecipeWrapperFixture unrelated = new UnrelatedGTRecipeWrapperFixture();

        Assertions.assertFalse(GTCEuRecipeHandler.isGTRecipe(unrelated));
        Assertions.assertSame(unrelated, GTCEuRecipeHandler.unwrapRecipe(unrelated));
    }

    @Test
    public void testUnwrapsShadowedBackingFieldFromSuperclass() {
        StructuralGTRecipeFixture recipe = new StructuralGTRecipeFixture();
        recipe.duration = 90;
        recipe.tickInputs.put("gtceu:eu_recipe_capability", List.of(new MockEnergyContent(128L)));
        ShadowedBackingSubFixture wrapper = new ShadowedBackingSubFixture(recipe);

        Assertions.assertTrue(GTCEuRecipeHandler.isGTRecipe(wrapper));
        Assertions.assertSame(recipe, GTCEuRecipeHandler.unwrapRecipe(wrapper));

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(wrapper, details);

        Assertions.assertEquals(90.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(128.0, details.eut, 1e-6);
        Assertions.assertEquals(GTVoltageTier.MV, details.tier);
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
    }

    @Test
    public void testGTCategoryNamespaceDetection() {
        Assertions.assertTrue(GTCEuRecipeHandler.isGTCategoryNamespace("gtceu"));
        Assertions.assertTrue(GTCEuRecipeHandler.isGTCategoryNamespace("start_core"));
        Assertions.assertTrue(GTCEuRecipeHandler.isGTCategoryNamespace("gtceu_start"));
        Assertions.assertTrue(GTCEuRecipeHandler.isGTCategoryNamespace("start"));
        Assertions.assertTrue(GTCEuRecipeHandler.isGTCategoryNamespace("star_technology"));
        Assertions.assertTrue(GTCEuRecipeHandler.isGTCategoryNamespace("GTCEU"));
        Assertions.assertFalse(GTCEuRecipeHandler.isGTCategoryNamespace("minecraft"));
        Assertions.assertFalse(GTCEuRecipeHandler.isGTCategoryNamespace("create"));
        Assertions.assertFalse(GTCEuRecipeHandler.isGTCategoryNamespace(""));
        Assertions.assertFalse(GTCEuRecipeHandler.isGTCategoryNamespace(null));
    }

    @Test
    public void testExtractEnergyFromObjectWithVoltageAndAmperage() {
        MockObjectEnergyRecipe recipe = new MockObjectEnergyRecipe();

        EmiRecipeConverter.RecipeDetails details = new EmiRecipeConverter.RecipeDetails();
        GTCEuRecipeHandler.extractGTRecipeDetails(recipe, details);

        Assertions.assertEquals(80.0, details.durationTicks, 1e-6);
        Assertions.assertEquals(16384.0, details.eut, 1e-6); // 8192 * 2
        Assertions.assertEquals(GTVoltageTier.LuV, details.tier); // 16384 <= 32768 (LuV)
        Assertions.assertEquals(EnergyType.ELECTRIC_EU, details.energyType);
    }

    @Test
    public void testExtractDurationFromTagAndMethods() {
        MockGTRecipe recipe = new MockGTRecipe();
        recipe.duration = 0;
        recipe.data.putDouble("duration", 240.0);

        double dur = GTCEuRecipeHandler.extractDuration(recipe);
        Assertions.assertEquals(240.0, dur, 1e-6);
    }

    public static class MockMultipleOutputGTRecipe {
        public int duration = 100;
        public Map<String, List<Object>> outputs = new HashMap<>();

        public static class MockContent {
            public Object content;
            public int chance;
            public int tierChanceBoost;

            public MockContent(Object content, int chance, int tierChanceBoost) {
                this.content = content;
                this.chance = chance;
                this.tierChanceBoost = tierChanceBoost;
            }
        }

        public MockMultipleOutputGTRecipe() {
            outputs.put("gtceu:item", List.of(
                    new MockContent(IngredientStack.item(net.minecraft.resources.ResourceLocation.tryParse("minecraft:diamond"), "Diamond", 1.0), 5000, 0),
                    new MockContent(IngredientStack.item(net.minecraft.resources.ResourceLocation.tryParse("minecraft:diamond"), "Diamond", 1.0), 4500, 0)
            ));
        }
    }

    @Test
    public void testExtractMultipleOutputsSameItemDifferentChances() {
        MockMultipleOutputGTRecipe recipe = new MockMultipleOutputGTRecipe();
        List<com.gtceu.calcboard.api.model.IngredientStack> extracted = GTCEuRecipeHandler.extractGTRecipeContents(recipe, "outputs");

        Assertions.assertEquals(2, extracted.size());
        Assertions.assertEquals(0.50, extracted.get(0).getChance(), 1e-4, "First slot must be 50%");
        Assertions.assertEquals(0.45, extracted.get(1).getChance(), 1e-4, "Second slot must be 45%");
    }
}

