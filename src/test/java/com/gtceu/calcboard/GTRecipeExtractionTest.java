package com.gtceu.calcboard;

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

    public static class MockGTRecipe {
        public int duration = 160;
        public CompoundTag data = new CompoundTag();

        public long getInputEUt() {
            return 65536L;
        }

        public long getOutputEUt() {
            return 0L;
        }
    }

    public static class MockGTGeneratorRecipe {
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

    public static class MockMapEnergyRecipe {
        public int duration = 100;
        public Map<String, List<Object>> tickInputs = new HashMap<>();

        public MockMapEnergyRecipe() {
            tickInputs.put("gtceu:eur_capability", List.of(65536L));
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
}

