package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.client.gui.search.RecipeFilterConfig;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Unit tests for UI state, Number & TimeUnit Formatting, RecipeFilterConfig, and i18n localization consistency.
 */
public class UiFormattingTest {

    @Test
    public void testTutorialStepEnumProperties() {
        Assertions.assertEquals(1, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_1_ADD_RECIPE.getStepNumber());
        Assertions.assertEquals(2, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_2_DRAG_TO_SEARCH.getStepNumber());
        Assertions.assertEquals(3, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_3_DELETE_WIRING.getStepNumber());
        Assertions.assertEquals(4, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_4_SHIFT_WIRING.getStepNumber());
        Assertions.assertEquals(5, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.STEP_5_SUMMARY_MODULE.getStepNumber());
        Assertions.assertEquals(6, com.gtceu.calcboard.client.gui.tutorial.TutorialStep.COMPLETED.getStepNumber());
    }

    @Test
    public void testBoardManagerWelcomePromptFlag() {
        BoardManager mgr = BoardManager.getInstance();
        mgr.setHasSeenWelcomePrompt(true);
        Assertions.assertTrue(mgr.hasSeenWelcomePrompt());

        mgr.setHasSeenWelcomePrompt(false);
        Assertions.assertFalse(mgr.hasSeenWelcomePrompt());
    }

    @Test
    public void testPowerDisplayModeConversions() {
        // LV (32V)
        Assertions.assertEquals("2A LV", PowerDisplayMode.formatAmps(2.0, GTVoltageTier.LV));
        Assertions.assertEquals("0.5A LV", PowerDisplayMode.formatAmps(0.5, GTVoltageTier.LV));
        Assertions.assertEquals("1.25A LV", PowerDisplayMode.formatAmps(1.25, GTVoltageTier.LV));

        // HV (512V)
        Assertions.assertEquals("1A HV", PowerDisplayMode.formatAmps(1.0, GTVoltageTier.HV));
        Assertions.assertEquals("5A HV", PowerDisplayMode.formatAmps(5.0, GTVoltageTier.HV));

        // Node card formatting in different modes
        RecipeNode turbine = RecipeNode.create("Gas Turbine", 100.0, 512.0, GTVoltageTier.HV);
        turbine.setGenerator(true);

        String eutStr = PowerDisplayMode.EUT.formatNodePower(turbine);
        Assertions.assertTrue(eutStr.contains("512.0 EU/t"));

        String ampsStr = PowerDisplayMode.AMPS.formatNodePower(turbine);
        Assertions.assertTrue(ampsStr.contains("1.0A HV"));

        String bothStr = PowerDisplayMode.BOTH.formatNodePower(turbine);
        Assertions.assertTrue(bothStr.contains("512.0 EU/t") && bothStr.contains("1A HV"));
    }

    @Test
    public void testSmallNumberPrecisionFormatting() {
        // Fluid rates: 0.05 mB/s, 0.005 mB/s must not become "0 mB/s"
        Assertions.assertEquals("0.05 mB/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.05, true));
        Assertions.assertEquals("0.005 mB/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.005, true));
        Assertions.assertEquals("0.25 mB/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.25, true));
        Assertions.assertEquals("0 mB/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.0, true));

        // Item rates: 0.005/s, 0.001/s must not become "0/s"
        Assertions.assertEquals("0.05/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.05, false));
        Assertions.assertEquals("0.005/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.005, false));
        Assertions.assertEquals("0.001/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.001, false));
        Assertions.assertEquals("0/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(0.0, false));

        // Compact number
        Assertions.assertEquals("0.005", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatCompactNumber(0.005));
        Assertions.assertEquals("0.0005", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatCompactNumber(0.0005));

        // Scientific E-notation for extremely small numbers (< 0.0001)
        Assertions.assertEquals("1.01E-24", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatCompactNumber(1.01e-24));
        Assertions.assertEquals("5E-5", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatCompactNumber(0.00005));
        Assertions.assertEquals("1.25E-6/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(1.25e-6, false));
        Assertions.assertEquals("1.01E-24 mB/s", com.gtceu.calcboard.client.gui.NodeCardRenderer.formatRate(1.01e-24, true));

        // Small amperes with E-notation
        Assertions.assertEquals("0.004A LV", PowerDisplayMode.formatAmps(0.004, GTVoltageTier.LV));
        Assertions.assertEquals("1.01E-24A LV", PowerDisplayMode.formatAmps(1.01e-24, GTVoltageTier.LV));

        // High SI Prefixes (k, M, G, T, P, E, Z, Y)
        Assertions.assertEquals("1.5k", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(1500));
        Assertions.assertEquals("2.5M", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(2_500_000));
        Assertions.assertEquals("3.5G", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(3_500_000_000.0));
        Assertions.assertEquals("11.8T", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(11_796_470_000_000.0));
        Assertions.assertEquals("5.2P", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(5.2e15));
        Assertions.assertEquals("8E", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(8.0e18));
        Assertions.assertEquals("9.1Z", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(9.1e21));
        Assertions.assertEquals("4.2Y", com.gtceu.calcboard.client.gui.FormatUtil.formatCompactNumber(4.2e24));
    }

    @Test
    public void testContinuousWireCollisionDetection() {
        float x1 = 100f, y1 = 100f;
        float x2 = 500f, y2 = 300f;

        Assertions.assertTrue(com.gtceu.calcboard.client.gui.ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, 100, 100, 8.0));
        Assertions.assertTrue(com.gtceu.calcboard.client.gui.ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, 500, 300, 8.0));

        for (int i = 0; i <= 50; i++) {
            float t = i / 50.0f;
            float it = 1.0f - t;
            float dx = Math.max(Math.abs(x2 - x1) * 0.5f, 40f);
            float cx1 = x1 + dx, cy1 = y1, cx2 = x2 - dx, cy2 = y2;
            float bx = it * it * it * x1 + 3 * it * it * t * cx1 + 3 * it * t * t * cx2 + t * t * t * x2;
            float by = it * it * it * y1 + 3 * it * it * t * cy1 + 3 * it * t * t * cy2 + t * t * t * y2;

            Assertions.assertTrue(com.gtceu.calcboard.client.gui.ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, bx, by, 8.0),
                    "Collision detection must succeed at t=" + t);
        }

        Assertions.assertFalse(com.gtceu.calcboard.client.gui.ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, 100, 500, 8.0));
        Assertions.assertFalse(com.gtceu.calcboard.client.gui.ConnectionRenderer.isPointNearBezier(x1, y1, x2, y2, 800, 100, 8.0));
    }

    @Test
    public void testBoardManagerPowerDisplayModeCycling() {
        BoardManager mgr = BoardManager.getInstance();
        mgr.setPowerDisplayMode(PowerDisplayMode.EUT);
        Assertions.assertEquals(PowerDisplayMode.EUT, mgr.getPowerDisplayMode());

        PowerDisplayMode m1 = mgr.cyclePowerDisplayMode();
        Assertions.assertEquals(PowerDisplayMode.AMPS, m1);

        PowerDisplayMode m2 = mgr.cyclePowerDisplayMode();
        Assertions.assertEquals(PowerDisplayMode.BOTH, m2);

        PowerDisplayMode m3 = mgr.cyclePowerDisplayMode();
        Assertions.assertEquals(PowerDisplayMode.EUT, m3);

        double largeEUt = 18_186_905_076_480.0;
        String formattedSummary = PowerDisplayMode.EUT.formatSummaryPower(largeEUt, GTVoltageTier.UEV);
        Assertions.assertTrue(formattedSummary.contains("18.2T EU/t") || formattedSummary.contains("18.19T EU/t") || formattedSummary.contains("18.18T EU/t"));
    }

    @Test
    public void testTagAlternativesAndAutoMatching() {
        IngredientStack input = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:diesel"), "Diesel", 1000, 1.0);
        input.addAlternative(ResourceLocation.tryParse("gtceu:bio_diesel"));
        input.addAlternative(ResourceLocation.tryParse("thermal:refined_fuel"));

        Assertions.assertTrue(input.hasAlternatives());
        Assertions.assertEquals(3, input.getAlternatives().size());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:diesel"), input.getId());

        input.cycleAlternative(1);
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:bio_diesel"), input.getId());

        IngredientStack bioDieselOutput = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:bio_diesel"), "Bio Diesel", 1000, 1.0);
        Assertions.assertTrue(input.matchesOrAlternative(bioDieselOutput));

        input.selectAlternative(bioDieselOutput.getId());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:bio_diesel"), input.getId());
        Assertions.assertTrue(input.equals(bioDieselOutput));
    }

    @Test
    public void testDuplicateOutputsWithDifferentChances() {
        RecipeNode greenhouse = RecipeNode.create("Crop Greenhouse", 600.0, 15.0, GTVoltageTier.LV);
        greenhouse.setMachineCount(1.0);
        greenhouse.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:potato"), "Potato", 16.0, 1.0));
        greenhouse.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:potato"), "Potato", 8.0, 0.5));

        FlowGraph graph = new FlowGraph();
        graph.addNode(greenhouse);

        FlowGraphSolver.PortFlowStats stats0 = graph.getOutputPortStats(greenhouse, 0);
        FlowGraphSolver.PortFlowStats stats1 = graph.getOutputPortStats(greenhouse, 1);

        Assertions.assertEquals(16.0 / 30.0, stats0.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(4.0 / 30.0, stats1.requiredOrProducedRate(), 0.001);
        Assertions.assertNotEquals(stats0.requiredOrProducedRate(), stats1.requiredOrProducedRate());
    }

    @Test
    public void testDummyConditionMarkerFiltering() {
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:overworld_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:nether_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:the_end_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:dimension_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:biome_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:altitude_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("start_core:abydos_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("sgjourney:chulak_marker")));
        Assertions.assertTrue(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("kubejs:custom_planet_marker")));

        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:programmed_circuit")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:integrated_circuit")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("minecraft:potato")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:lv_electric_motor")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("gtceu:enderium_ingot")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("minecraft:ender_pearl")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("minecraft:end_stone")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("thermal:enderium_dust")));
        Assertions.assertFalse(com.gtceu.calcboard.integration.emi.EmiRecipeConverter.isDummyConditionMarker(ResourceLocation.tryParse("create:blender")));
    }

    @Test
    public void testConnectedRateFormattingPlusMinusWithoutDoubleSlash() {
        String inSurplus = com.gtceu.calcboard.client.gui.FormatUtil.formatConnectedInput(3_480_000, 3_200_000, false, false);
        Assertions.assertTrue(inSurplus.contains("+3.48M"));
        Assertions.assertTrue(inSurplus.contains("-3.2M/s"));
        Assertions.assertTrue(inSurplus.contains("+"));
        Assertions.assertFalse(inSurplus.contains("/3.2M/s"));

        String inDeficit = com.gtceu.calcboard.client.gui.FormatUtil.formatConnectedInput(2_500_000, 3_200_000, false, true);
        Assertions.assertTrue(inDeficit.contains("+2.5M"));
        Assertions.assertTrue(inDeficit.contains("-3.2M/s"));
        Assertions.assertTrue(inDeficit.contains("⚠"));

        String outSurplus = com.gtceu.calcboard.client.gui.FormatUtil.formatConnectedOutput(3_200_000, 2_000_000, true, false);
        Assertions.assertTrue(outSurplus.contains("+3.2k"));
        Assertions.assertTrue(outSurplus.contains("-2k B/s"));
        Assertions.assertTrue(outSurplus.contains("+"));
        Assertions.assertFalse(outSurplus.contains("/2"));

        String outDeficit = com.gtceu.calcboard.client.gui.FormatUtil.formatConnectedOutput(3_200_000, 4_500_000, true, true);
        Assertions.assertTrue(outDeficit.contains("+3.2k"));
        Assertions.assertTrue(outDeficit.contains("-4.5k B/s"));
        Assertions.assertTrue(outDeficit.contains("⚠"));
    }

    @Test
    public void testRecipeFilterConfigAndCategoryDiscovery() {
        RecipeFilterConfig config = RecipeFilterConfig.getInstance();
        config.resetDefaults();

        Assertions.assertTrue(config.isCategoryExcluded("gtceu:world_interaction"));
        Assertions.assertTrue(config.isCategoryExcluded("world_interaction"));
        Assertions.assertTrue(config.isCategoryExcluded("gtceu:fluid_canning"));
        Assertions.assertTrue(config.isCategoryExcluded("gtceu:fluid_encapsulation"));
        Assertions.assertFalse(config.isCategoryExcluded("gtceu:chemical_reactor"));

        config.setCategoryExcluded("gtceu:chemical_reactor", true);
        Assertions.assertTrue(config.isCategoryExcluded("gtceu:chemical_reactor"));
        config.setCategoryExcluded("gtceu:chemical_reactor", false);
        Assertions.assertFalse(config.isCategoryExcluded("gtceu:chemical_reactor"));

        RecipeSearchEngine.SearchableRecipe r1 = new RecipeSearchEngine.SearchableRecipe(
                new Object(), "Reaction 1", "gtceu", "chemical_reactor", "Chemical Reactor",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", "", ""
        );
        RecipeSearchEngine.SearchableRecipe r2 = new RecipeSearchEngine.SearchableRecipe(
                new Object(), "Reaction 2", "gtceu", "chemical_reactor", "Chemical Reactor",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", "", ""
        );
        RecipeSearchEngine.SearchableRecipe r3 = new RecipeSearchEngine.SearchableRecipe(
                new Object(), "Distillation 1", "gtceu", "distillation_tower", "Distillation Tower",
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), "", "", ""
        );

        var discovered = RecipeSearchEngine.discoverCategories(List.of(r1, r2, r3));
        Assertions.assertEquals(2, discovered.size());
        Assertions.assertEquals(2, discovered.get("chemical_reactor").count());
        Assertions.assertEquals("Chemical Reactor", discovered.get("chemical_reactor").displayName());
        Assertions.assertEquals(1, discovered.get("distillation_tower").count());
        Assertions.assertEquals("Distillation Tower", discovered.get("distillation_tower").displayName());

        config.resetDefaults();
    }

    @Test
    public void testRateTimeUnitScaling() {
        try {
            com.gtceu.calcboard.client.gui.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_SECOND);
            Assertions.assertEquals("50 mB/s", com.gtceu.calcboard.client.gui.FormatUtil.formatRate(50.0, true));
            Assertions.assertEquals("10/s", com.gtceu.calcboard.client.gui.FormatUtil.formatRate(10.0, false));
            Assertions.assertEquals("1.5k B/s", com.gtceu.calcboard.client.gui.FormatUtil.formatRate(1_500_000.0, true));

            com.gtceu.calcboard.client.gui.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_TICK);
            Assertions.assertEquals("2.5 mB/t", com.gtceu.calcboard.client.gui.FormatUtil.formatRate(50.0, true));
            Assertions.assertEquals("0.5/t", com.gtceu.calcboard.client.gui.FormatUtil.formatRate(10.0, false));
            Assertions.assertEquals("75 B/t", com.gtceu.calcboard.client.gui.FormatUtil.formatRate(1_500_000.0, true));

            com.gtceu.calcboard.client.gui.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_MINUTE);
            Assertions.assertEquals("3 B/min", com.gtceu.calcboard.client.gui.FormatUtil.formatRate(50.0, true));
            Assertions.assertEquals("600/min", com.gtceu.calcboard.client.gui.FormatUtil.formatRate(10.0, false));

            com.gtceu.calcboard.client.gui.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_HOUR);
            Assertions.assertEquals("180 B/h", com.gtceu.calcboard.client.gui.FormatUtil.formatRate(50.0, true));
            Assertions.assertEquals("36k/h", com.gtceu.calcboard.client.gui.FormatUtil.formatRate(10.0, false));

            com.gtceu.calcboard.client.gui.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_DAY);
            Assertions.assertEquals("4.32k B/d", com.gtceu.calcboard.client.gui.FormatUtil.formatRate(50.0, true));
            Assertions.assertEquals("864k/d", com.gtceu.calcboard.client.gui.FormatUtil.formatRate(10.0, false));

            String inPerDay = com.gtceu.calcboard.client.gui.FormatUtil.formatConnectedInput(10.0, 10.0, false, false);
            Assertions.assertTrue(inPerDay.contains("864k/d"));

            String exactPerMin = com.gtceu.calcboard.client.gui.FormatUtil.formatExactRate(50.0, true);
            Assertions.assertTrue(exactPerMin.contains("4,320.00 B/d"));

            Assertions.assertEquals(RateTimeUnit.PER_TICK, RateTimeUnit.PER_DAY.next());
            Assertions.assertEquals(RateTimeUnit.PER_SECOND, RateTimeUnit.PER_TICK.next());
        } finally {
            com.gtceu.calcboard.client.gui.FormatUtil.setActiveTimeUnit(RateTimeUnit.PER_SECOND);
        }
    }

    @Test
    public void testI18nCompletenessAndConsistency() throws Exception {
        java.nio.file.Path enPath = java.nio.file.Paths.get("src/main/resources/assets/gtcalcboard/lang/en_us.json");
        java.nio.file.Path koPath = java.nio.file.Paths.get("src/main/resources/assets/gtcalcboard/lang/ko_kr.json");

        Assertions.assertTrue(java.nio.file.Files.exists(enPath), "en_us.json must exist!");
        Assertions.assertTrue(java.nio.file.Files.exists(koPath), "ko_kr.json must exist!");

        JsonObject enJson = JsonParser.parseString(java.nio.file.Files.readString(enPath)).getAsJsonObject();
        JsonObject koJson = JsonParser.parseString(java.nio.file.Files.readString(koPath)).getAsJsonObject();

        Set<String> enKeys = new TreeSet<>(enJson.keySet());
        Set<String> koKeys = new TreeSet<>(koJson.keySet());

        Set<String> missingInKo = new TreeSet<>(enKeys);
        missingInKo.removeAll(koKeys);

        Set<String> missingInEn = new TreeSet<>(koKeys);
        missingInEn.removeAll(enKeys);

        Assertions.assertTrue(missingInKo.isEmpty(), "Keys present in en_us.json but missing in ko_kr.json: " + missingInKo);
        Assertions.assertTrue(missingInEn.isEmpty(), "Keys present in ko_kr.json but missing in en_us.json: " + missingInEn);

        for (String key : enKeys) {
            String enVal = enJson.get(key).getAsString();
            String koVal = koJson.get(key).getAsString();

            int enTokens = countFormatTokens(enVal);
            int koTokens = countFormatTokens(koVal);

            Assertions.assertEquals(enTokens, koTokens, "Format token (%s, %d, etc) count mismatch for key '" + key + "'. EN: " + enVal + " | KO: " + koVal);
        }
    }

    private int countFormatTokens(String s) {
        if (s == null) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = s.indexOf('%', idx)) != -1) {
            if (idx + 1 < s.length()) {
                char c = s.charAt(idx + 1);
                if (c == 's' || c == 'd' || c == 'f' || c == 'x') {
                    count++;
                }
            }
            idx++;
        }
        return count;
    }
}
