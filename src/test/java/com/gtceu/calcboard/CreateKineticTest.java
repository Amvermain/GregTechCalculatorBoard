package com.gtceu.calcboard;

import com.gtceu.calcboard.api.EnergyType;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import com.gtceu.calcboard.compat.create.CreateModAdapter;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class CreateKineticTest {

    @Test
    public void testLargeWaterWheelKineticGenerator() {
        RecipeNode node = CreateModAdapter.createKineticGeneratorNode(
                ResourceLocation.tryParse("create:large_water_wheel"),
                "Large Water Wheel"
        );

        Assertions.assertNotNull(node);
        Assertions.assertEquals("Large Water Wheel", node.getName());
        Assertions.assertEquals(EnergyType.KINETIC_SU, node.getEnergyType());
        Assertions.assertTrue(node.isGenerator());
        Assertions.assertEquals(512.0, node.getBaseEUt(), 0.001);
        Assertions.assertEquals(512.0, node.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(1.0, node.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(ResourceLocation.tryParse("create:large_water_wheel"), node.getMachineIcon());
    }

    @Test
    public void testWaterWheelKineticGenerator() {
        RecipeNode node = CreateModAdapter.createKineticGeneratorNode(
                ResourceLocation.tryParse("create:water_wheel"),
                "Water Wheel"
        );

        Assertions.assertNotNull(node);
        Assertions.assertEquals("Water Wheel", node.getName());
        Assertions.assertEquals(EnergyType.KINETIC_SU, node.getEnergyType());
        Assertions.assertTrue(node.isGenerator());
        Assertions.assertEquals(256.0, node.getBaseEUt(), 0.001);
    }

    @Test
    public void testSteamEngineKineticGenerator() {
        RecipeNode node = CreateModAdapter.createKineticGeneratorNode(
                ResourceLocation.tryParse("create:steam_engine"),
                "Steam Engine"
        );

        Assertions.assertNotNull(node);
        Assertions.assertEquals("Steam Engine", node.getName());
        Assertions.assertEquals(EnergyType.KINETIC_SU, node.getEnergyType());
        Assertions.assertTrue(node.isGenerator());
        Assertions.assertEquals(2048.0, node.getBaseEUt(), 0.001);
        Assertions.assertFalse(node.getInputs().isEmpty());
        Assertions.assertEquals("gtceu:steam", node.getInputs().get(0).getId().toString());
    }

    @Test
    public void testVirtualKineticSearchRecipes() {
        List<RecipeSearchEngine.SearchableRecipe> virtualRecipes = CreateModAdapter.getVirtualKineticSearchRecipes();
        Assertions.assertFalse(virtualRecipes.isEmpty());

        boolean foundLargeWaterWheel = false;
        for (RecipeSearchEngine.SearchableRecipe sr : virtualRecipes) {
            if (sr.displayName().equals("Large Water Wheel")) {
                foundLargeWaterWheel = true;
                Assertions.assertTrue(sr.inputSearchIndex().contains("kinetic") || sr.outputSearchIndex().contains("kinetic"));
                Assertions.assertTrue(sr.recipe() instanceof RecipeNode);
            }
        }
        Assertions.assertTrue(foundLargeWaterWheel);
    }

    @Test
    public void testRpmSpeedAndStressScaling() {
        RecipeNode millstone = RecipeNode.create("Millstone (Wheat)", 100.0, 256.0, com.gtceu.calcboard.api.GTVoltageTier.LV);
        millstone.setEnergyType(EnergyType.KINETIC_SU);
        millstone.setMachineIcon(ResourceLocation.tryParse("create:millstone"));
        millstone.setRpm(32);

        // At 32 RPM (Standard base)
        Assertions.assertEquals(5.00, millstone.getEffectiveDurationSeconds(), 0.001); // 100 / 20 = 5s
        Assertions.assertEquals(256.0, millstone.getSingleMachineEUt(), 0.001); // 256 SU

        // At 64 RPM (2x speed, 2x stress impact)
        millstone.setRpm(64);
        Assertions.assertEquals(2.50, millstone.getEffectiveDurationSeconds(), 0.001); // 2.5s
        Assertions.assertEquals(512.0, millstone.getSingleMachineEUt(), 0.001); // 512 SU

        // At 128 RPM (4x speed, 4x stress impact)
        millstone.setRpm(128);
        Assertions.assertEquals(1.25, millstone.getEffectiveDurationSeconds(), 0.001); // 1.25s
        Assertions.assertEquals(1024.0, millstone.getSingleMachineEUt(), 0.001); // 1024 SU

        // At 256 RPM (8x speed, 8x stress impact)
        millstone.setRpm(256);
        Assertions.assertEquals(0.625, millstone.getEffectiveDurationSeconds(), 0.001); // 0.625s
        Assertions.assertEquals(2048.0, millstone.getSingleMachineEUt(), 0.001); // 2048 SU
    }

    @Test
    public void testFanProcessingFixedDuration() {
        RecipeNode fanBlasting = RecipeNode.create("Fan Blasting (Copper Dust)", 150.0, 128.0, com.gtceu.calcboard.api.GTVoltageTier.LV);
        fanBlasting.setEnergyType(EnergyType.KINETIC_SU);
        fanBlasting.setRecipeCategoryId(ResourceLocation.tryParse("create:blasting"));
        fanBlasting.setMachineIcon(ResourceLocation.tryParse("create:encased_fan"));
        fanBlasting.setRpm(32);

        // At 32 RPM: duration is 150 ticks = 7.50s, 128 SU
        Assertions.assertEquals(7.50, fanBlasting.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(128.0, fanBlasting.getSingleMachineEUt(), 0.001);

        // At 256 RPM: Fan processing duration remains fixed at 7.50s in Create mod (RPM only extends airflow distance)
        fanBlasting.setRpm(256);
        Assertions.assertEquals(7.50, fanBlasting.getEffectiveDurationSeconds(), 0.001); // Still 7.50s!
        Assertions.assertEquals(1024.0, fanBlasting.getSingleMachineEUt(), 0.001); // Stress scales with RPM (1024 SU)

        // Multiple fans (machine count 4): speed scales with fan count
        fanBlasting.setMachineCount(4);
        Assertions.assertEquals(0.5333, fanBlasting.getNominalCyclesPerSecond(), 0.001); // 4 * (1/7.5) = 0.5333/s
        Assertions.assertEquals(4096.0, fanBlasting.getTotalEUt(), 0.001); // 4 * 1024 SU = 4096 SU
    }

    @Test
    public void testRpmCycling() {
        RecipeNode node = RecipeNode.create("Mechanical Press", 60.0, 256.0, com.gtceu.calcboard.api.GTVoltageTier.LV);
        node.setEnergyType(EnergyType.KINETIC_SU);
        node.setRpm(32);

        node.cycleRpm(1);
        Assertions.assertEquals(64, node.getRpm());

        node.cycleRpm(1);
        Assertions.assertEquals(128, node.getRpm());

        node.cycleRpm(1);
        Assertions.assertEquals(256, node.getRpm());

        node.cycleRpm(1);
        Assertions.assertEquals(4, node.getRpm());

        node.cycleRpm(-1);
        Assertions.assertEquals(256, node.getRpm());
    }

    @Test
    public void testVirtualStressUnitPorts() {
        RecipeNode waterWheel = CreateModAdapter.createKineticGeneratorNode(
                ResourceLocation.tryParse("create:large_water_wheel"),
                "Large Water Wheel"
        );
        Assertions.assertNotNull(waterWheel);
        Assertions.assertFalse(waterWheel.getOutputs().isEmpty());
        Assertions.assertTrue(waterWheel.getOutputs().get(0).isStressUnit());
        Assertions.assertEquals(512.0, waterWheel.getOutputs().get(0).getAmount(), 0.001);
        Assertions.assertEquals(512.0, waterWheel.getOutputSlotRate(0, true), 0.001); // 512 SU/s

        // Consumer node: Mechanical Press (100 ticks = 5s, 256 SU impact @ 32 RPM -> 1280 SU per batch)
        RecipeNode press = RecipeNode.create("Mechanical Press", 100.0, 256.0, com.gtceu.calcboard.api.GTVoltageTier.LV);
        press.setEnergyType(EnergyType.KINETIC_SU);
        press.setRpm(32);
        press.addInput(com.gtceu.calcboard.api.IngredientStack.stressUnit(1280.0)); // 256 * 5s

        // At 32 RPM: CPS = 0.2 /s -> 1280 * 0.2 = 256 SU/s
        Assertions.assertEquals(256.0, press.getInputSlotRate(0, true), 0.001);

        // At 64 RPM: CPS = 0.4 /s -> 1280 * 0.4 = 512 SU/s
        press.setRpm(64);
        Assertions.assertEquals(512.0, press.getInputSlotRate(0, true), 0.001);
    }

    @Test
    public void testKineticGraphBalancing() {
        com.gtceu.calcboard.api.FlowGraph graph = new com.gtceu.calcboard.api.FlowGraph();

        RecipeNode waterWheel = CreateModAdapter.createKineticGeneratorNode(
                ResourceLocation.tryParse("create:large_water_wheel"),
                "Large Water Wheel"
        );
        RecipeNode press = RecipeNode.create("Mechanical Press", 100.0, 256.0, com.gtceu.calcboard.api.GTVoltageTier.LV);
        press.setEnergyType(EnergyType.KINETIC_SU);
        press.setRpm(32);
        press.addInput(com.gtceu.calcboard.api.IngredientStack.stressUnit(1280.0)); // 256 SU/s

        graph.addNode(waterWheel);
        graph.addNode(press);

        // Wire Water Wheel (Output 0: SU) -> Mechanical Press (Input 0: SU)
        graph.addConnection(waterWheel.getId(), 0, press.getId(), 0);

        // 1 Large Water Wheel (+512 SU/s) powers 2 Mechanical Presses (-256 SU/s each)
        com.gtceu.calcboard.api.FlowGraphSolver.autoRatioFromAnchor(graph, waterWheel, true);

        Assertions.assertEquals(1.0, waterWheel.getMachineCount(), 0.001);
        Assertions.assertEquals(2.0, press.getMachineCount(), 0.001); // 2 presses needed to absorb 512 SU
    }

    @Test
    public void testFanWashingChanceOutput() {
        // Fan Washing: Crushed Iron Ore (150 ticks = 7.5s at 32 RPM standard Create default)
        // Output 1: Crushed Iron Ore (100% chance, 1.0) -> 1.0 * (20/150) = 0.1333/s
        // Output 2: Nickel Dust (7% chance, 1.0) -> 0.07 * (20/150) = 0.00933/s
        RecipeNode washing = RecipeNode.create("Fan Washing (Crushed Iron Ore)", 150.0, 128.0, com.gtceu.calcboard.api.GTVoltageTier.LV);
        washing.setEnergyType(EnergyType.KINETIC_SU);
        washing.setRpm(32);
        washing.addInput(com.gtceu.calcboard.api.IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 1.0));
        washing.addInput(com.gtceu.calcboard.api.IngredientStack.stressUnit(960.0));
        washing.addOutput(com.gtceu.calcboard.api.IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 1.0, 1.0));
        washing.addOutput(com.gtceu.calcboard.api.IngredientStack.item(ResourceLocation.tryParse("gtceu:nickel_dust"), "Nickel Dust", 1.0, 0.07));

        Assertions.assertEquals(0.1333, washing.getEffectiveCyclesPerSecond(), 0.001);
        Assertions.assertEquals(0.1333, washing.getOutputSlotRate(0, true), 0.001); // 0.1333/s Crushed Iron Ore
        Assertions.assertEquals(0.00933, washing.getOutputSlotRate(1, true), 0.001); // 0.00933/s Nickel Dust (7% of 0.1333)

        // Verify formatted string
        String nickelRateStr = com.gtceu.calcboard.client.gui.FormatUtil.formatRate(washing.getOutputSlotRate(1, true), false);
        Assertions.assertEquals("0.0093/s", nickelRateStr);
    }

    @Test
    public void testCreateNewAgeGeneratorsAndMotors() {
        // 1. Generator Coil (Converts SU to FE)
        RecipeNode coil = CreateModAdapter.createKineticGeneratorNode(
                ResourceLocation.tryParse("create_new_age:generator_coil"),
                "Generator Coil"
        );
        Assertions.assertNotNull(coil);
        Assertions.assertEquals("Generator Coil", coil.getName());
        Assertions.assertEquals(EnergyType.ELECTRIC_FE, coil.getEnergyType());
        Assertions.assertTrue(coil.isGenerator());
        Assertions.assertEquals(512.0, coil.getBaseEUt(), 0.001);
        Assertions.assertTrue(coil.getInputs().get(0).isStressUnit());
        Assertions.assertTrue(coil.getOutputs().isEmpty());

        // 2. Carbon Brushes
        RecipeNode brushes = CreateModAdapter.createKineticGeneratorNode(
                ResourceLocation.tryParse("create_new_age:carbon_brushes"),
                "Carbon Brushes"
        );
        Assertions.assertNotNull(brushes);
        Assertions.assertEquals(EnergyType.ELECTRIC_FE, brushes.getEnergyType());
        Assertions.assertTrue(brushes.isGenerator());

        // 3. Basic & Advanced & Reinforced Motors (Converts FE to SU)
        RecipeNode basicMotor = CreateModAdapter.createKineticGeneratorNode(
                ResourceLocation.tryParse("create_new_age:basic_motor"),
                "Basic Motor"
        );
        Assertions.assertNotNull(basicMotor);
        Assertions.assertEquals(EnergyType.ELECTRIC_FE, basicMotor.getEnergyType());
        Assertions.assertFalse(basicMotor.isGenerator());
        Assertions.assertEquals(256.0, basicMotor.getBaseEUt(), 0.001);
        Assertions.assertTrue(basicMotor.getInputs().isEmpty());
        Assertions.assertTrue(basicMotor.getOutputs().get(0).isStressUnit());

        RecipeNode advMotor = CreateModAdapter.createKineticGeneratorNode(
                ResourceLocation.tryParse("create_new_age:advanced_motor"),
                "Advanced Motor"
        );
        Assertions.assertEquals(1024.0, advMotor.getBaseEUt(), 0.001);

        RecipeNode reinfMotor = CreateModAdapter.createKineticGeneratorNode(
                ResourceLocation.tryParse("create_new_age:reinforced_motor"),
                "Reinforced Motor"
        );
        Assertions.assertEquals(4096.0, reinfMotor.getBaseEUt(), 0.001);

        // 4. Stirling Engine
        RecipeNode stirling = CreateModAdapter.createKineticGeneratorNode(
                ResourceLocation.tryParse("create_new_age:stirling_engine"),
                "Stirling Engine"
        );
        Assertions.assertNotNull(stirling);
        Assertions.assertEquals(EnergyType.KINETIC_SU, stirling.getEnergyType());
        Assertions.assertEquals(1024.0, stirling.getBaseEUt(), 0.001);

        // 5. Virtual Search includes Create New Age items
        List<RecipeSearchEngine.SearchableRecipe> virtualRecipes = CreateModAdapter.getVirtualKineticSearchRecipes();
        boolean foundCoil = virtualRecipes.stream().anyMatch(sr -> sr.displayName().equals("Generator Coil"));
        boolean foundBrushes = virtualRecipes.stream().anyMatch(sr -> sr.displayName().equals("Carbon Brushes"));
        boolean foundMotor = virtualRecipes.stream().anyMatch(sr -> sr.displayName().equals("Basic Motor"));

        Assertions.assertTrue(foundCoil);
        Assertions.assertTrue(foundBrushes);
        Assertions.assertTrue(foundMotor);
    }

    @Test
    public void testKineticRecipeSearchAndFavoriteMatching() {
        List<RecipeSearchEngine.SearchableRecipe> virtualRecipes = CreateModAdapter.getVirtualKineticSearchRecipes();

        // 1. Search by "<su"
        RecipeSearchEngine.ParsedQuery querySu = RecipeSearchEngine.parseQuery("<su");
        List<RecipeSearchEngine.SearchableRecipe> matchesSu = virtualRecipes.stream()
                .filter(sr -> RecipeSearchEngine.matches(sr, querySu))
                .toList();
        Assertions.assertTrue(matchesSu.stream().anyMatch(sr -> sr.displayName().equals("Large Water Wheel")));

        // 2. Search by "<stress"
        RecipeSearchEngine.ParsedQuery queryStress = RecipeSearchEngine.parseQuery("<stress");
        List<RecipeSearchEngine.SearchableRecipe> matchesStress = virtualRecipes.stream()
                .filter(sr -> RecipeSearchEngine.matches(sr, queryStress))
                .toList();
        Assertions.assertTrue(matchesStress.stream().anyMatch(sr -> sr.displayName().equals("Large Water Wheel")));

        // 3. Search by "large water wheel"
        RecipeSearchEngine.ParsedQuery queryLww = RecipeSearchEngine.parseQuery("large water wheel");
        List<RecipeSearchEngine.SearchableRecipe> matchesLww = virtualRecipes.stream()
                .filter(sr -> RecipeSearchEngine.matches(sr, queryLww))
                .toList();
        Assertions.assertTrue(matchesLww.stream().anyMatch(sr -> sr.displayName().equals("Large Water Wheel")));
    }
}
