package com.gtceu.calcboard.compat.create;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.util.FormatUtil;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.SearchableRecipe;
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
        Assertions.assertEquals(4, node.getRpm());
        Assertions.assertEquals(512.0, node.getBaseEUt(), 0.001);
        Assertions.assertEquals(512.0, node.getSingleMachineEUt(), 0.001);
        Assertions.assertEquals(1.0, node.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(ResourceLocation.tryParse("create:large_water_wheel"), node.getMachineIcon());

        node.setRpm(8);
        Assertions.assertEquals(1024.0, node.getSingleMachineEUt(), 0.001);
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
    public void testKineticGeneratorNodeCreation() {
        RecipeNode node = CreateRecipeHandler.createKineticGeneratorNode(
                ResourceLocation.tryParse("create:large_water_wheel"),
                "Large Water Wheel"
        );
        Assertions.assertNotNull(node);
        Assertions.assertEquals("Large Water Wheel", node.getName());
        Assertions.assertEquals(EnergyType.KINETIC_SU, node.getEnergyType());
        Assertions.assertTrue(node.isGenerator());
        Assertions.assertEquals(512.0, node.getBaseEUt(), 1e-4);
        Assertions.assertFalse(node.getOutputs().isEmpty());
        Assertions.assertTrue(node.getOutputs().get(0).isStressUnit());
    }

    @Test
    public void testRpmSpeedAndStressScaling() {
        RecipeNode millstone = RecipeNode.create("Millstone (Wheat)", 100.0, 256.0, com.gtceu.calcboard.api.type.GTVoltageTier.LV);
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
        RecipeNode fanBlasting = RecipeNode.create("Fan Blasting (Copper Dust)", 150.0, 128.0, com.gtceu.calcboard.api.type.GTVoltageTier.LV);
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
        RecipeNode node = RecipeNode.create("Mechanical Press", 60.0, 256.0, com.gtceu.calcboard.api.type.GTVoltageTier.LV);
        node.setEnergyType(EnergyType.KINETIC_SU);
        node.setRpm(32);

        CreateProperties.cycleRpm(node, 1);
        Assertions.assertEquals(64, node.getRpm());

        CreateProperties.cycleRpm(node, 1);
        Assertions.assertEquals(128, node.getRpm());

        CreateProperties.cycleRpm(node, 1);
        Assertions.assertEquals(256, node.getRpm());

        CreateProperties.cycleRpm(node, 1);
        Assertions.assertEquals(4, node.getRpm());

        CreateProperties.cycleRpm(node, -1);
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
        RecipeNode press = RecipeNode.create("Mechanical Press", 100.0, 256.0, com.gtceu.calcboard.api.type.GTVoltageTier.LV);
        press.setEnergyType(EnergyType.KINETIC_SU);
        press.setRpm(32);
        press.addInput(com.gtceu.calcboard.api.model.IngredientStack.stressUnit(1280.0)); // 256 * 5s

        // At 32 RPM: CPS = 0.2 /s -> 1280 * 0.2 = 256 SU/s
        Assertions.assertEquals(256.0, press.getInputSlotRate(0, true), 0.001);

        // At 64 RPM: CPS = 0.4 /s -> 1280 * 0.4 = 512 SU/s
        press.setRpm(64);
        Assertions.assertEquals(512.0, press.getInputSlotRate(0, true), 0.001);
    }

    @Test
    public void testKineticGraphBalancing() {
        com.gtceu.calcboard.api.model.FlowGraph graph = new com.gtceu.calcboard.api.model.FlowGraph();

        RecipeNode waterWheel = CreateModAdapter.createKineticGeneratorNode(
                ResourceLocation.tryParse("create:large_water_wheel"),
                "Large Water Wheel"
        );
        RecipeNode press = RecipeNode.create("Mechanical Press", 100.0, 256.0, com.gtceu.calcboard.api.type.GTVoltageTier.LV);
        press.setEnergyType(EnergyType.KINETIC_SU);
        press.setRpm(32);
        press.addInput(com.gtceu.calcboard.api.model.IngredientStack.stressUnit(1280.0)); // 256 SU/s

        graph.addNode(waterWheel);
        graph.addNode(press);

        // Wire Water Wheel (Output 0: SU) -> Mechanical Press (Input 0: SU)
        graph.addConnection(waterWheel.getId(), 0, press.getId(), 0);

        // 1 Large Water Wheel (+512 SU/s) powers 2 Mechanical Presses (-256 SU/s each)
        com.gtceu.calcboard.api.solver.FlowGraphSolver.autoRatioFromAnchor(graph, waterWheel, true);

        Assertions.assertEquals(1.0, waterWheel.getMachineCount(), 0.001);
        Assertions.assertEquals(2.0, press.getMachineCount(), 0.001); // 2 presses needed to absorb 512 SU
    }

    @Test
    public void testFanWashingChanceOutput() {
        // Fan Washing: Crushed Iron Ore (150 ticks = 7.5s at 32 RPM standard Create default)
        // Output 1: Crushed Iron Ore (100% chance, 1.0) -> 1.0 * (20/150) = 0.1333/s
        // Output 2: Nickel Dust (7% chance, 1.0) -> 0.07 * (20/150) = 0.00933/s
        RecipeNode washing = RecipeNode.create("Fan Washing (Crushed Iron Ore)", 150.0, 128.0, com.gtceu.calcboard.api.type.GTVoltageTier.LV);
        washing.setEnergyType(EnergyType.KINETIC_SU);
        washing.setRpm(32);
        washing.addInput(com.gtceu.calcboard.api.model.IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 1.0));
        washing.addInput(com.gtceu.calcboard.api.model.IngredientStack.stressUnit(960.0));
        washing.addOutput(com.gtceu.calcboard.api.model.IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 1.0, 1.0));
        washing.addOutput(com.gtceu.calcboard.api.model.IngredientStack.item(ResourceLocation.tryParse("gtceu:nickel_dust"), "Nickel Dust", 1.0, 0.07));

        Assertions.assertEquals(0.1333, washing.getEffectiveCyclesPerSecond(), 0.001);
        Assertions.assertEquals(0.1333, washing.getOutputSlotRate(0, true), 0.001); // 0.1333/s Crushed Iron Ore
        Assertions.assertEquals(0.00933, washing.getOutputSlotRate(1, true), 0.001); // 0.00933/s Nickel Dust (7% of 0.1333)

        // Verify formatted string
        String nickelRateStr = com.gtceu.calcboard.client.gui.util.FormatUtil.formatRate(washing.getOutputSlotRate(1, true), false);
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

    }

    @Test
    public void testKineticRecipeSearchAndFavoriteMatching() {
        var waterWheelId = ResourceLocation.tryParse("create:large_water_wheel");
        var node = CreateRecipeHandler.createKineticGeneratorNode(waterWheelId, "Large Water Wheel");
        SearchableRecipe sr = com.gtceu.calcboard.api.catalog.NativeCatalogSearchHelper.createRecipe(
                node,
                waterWheelId,
                "gtcalcboard:kinetic_source",
                "Kinetic Source",
                () -> CreateRecipeHandler.createKineticGeneratorNode(waterWheelId, "Large Water Wheel")
        );
        Assertions.assertNotNull(sr);

        // 1. Search by "<su"
        RecipeSearchEngine.ParsedQuery querySu = RecipeSearchEngine.parseQuery("<su");
        Assertions.assertTrue(RecipeSearchEngine.matches(sr, querySu));

        // 2. Search by "<stress"
        RecipeSearchEngine.ParsedQuery queryStress = RecipeSearchEngine.parseQuery("<stress");
        Assertions.assertTrue(RecipeSearchEngine.matches(sr, queryStress));

        // 3. Search by "large water wheel"
        RecipeSearchEngine.ParsedQuery queryLww = RecipeSearchEngine.parseQuery("large water wheel");
        Assertions.assertTrue(RecipeSearchEngine.matches(sr, queryLww));
    }

    @Test
    public void testVintageImprovementsKineticProcessing() {
        RecipeNode node = RecipeNode.create(
                ResourceLocation.tryParse("vintageimprovements:vibrating_table"),
                "Vibrating Table (Purified Sphalerite)",
                100.0,
                128.0,
                GTVoltageTier.ULV
        );
        node.setRecipeCategoryId(ResourceLocation.tryParse("vintageimprovements:vibrating"));
        node.setMachineIcon(ResourceLocation.tryParse("vintageimprovements:vibrating_table"));

        // 1. Must be recognized as Create Kinetic Machine
        Assertions.assertTrue(com.gtceu.calcboard.api.util.ModCompatHelper.isCreateMachine(node), "Vintage Improvements must be recognized as Create kinetic machine");
        Assertions.assertEquals(EnergyType.KINETIC_SU, node.getEnergyType());

        // 2. Default RPM is 32 RPM (speedFactor 1.0x) -> 128 SU Impact, 5.0s duration
        Assertions.assertEquals(32, node.getRpm());
        Assertions.assertEquals(5.0, node.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(128.0, node.getSingleMachineEUt(), 0.001);

        // 3. Overclock / RPM scaling: 64 RPM (2.0x speed) -> 256 SU Impact, 2.5s duration
        node.setRpm(64);
        Assertions.assertEquals(2.5, node.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(256.0, node.getSingleMachineEUt(), 0.001);

        // 4. Lathe / Turning machine: 256 SU at 32 RPM
        RecipeNode lathe = RecipeNode.create(
                ResourceLocation.tryParse("vintageimprovements:lathe"),
                "Lathe (Iron Rod)",
                100.0,
                256.0,
                GTVoltageTier.ULV
        );
        lathe.setRecipeCategoryId(ResourceLocation.tryParse("vintageimprovements:lathe"));
        lathe.setMachineIcon(ResourceLocation.tryParse("vintageimprovements:lathe"));

        Assertions.assertTrue(com.gtceu.calcboard.api.util.ModCompatHelper.isCreateMachine(lathe));
        Assertions.assertEquals(EnergyType.KINETIC_SU, lathe.getEnergyType());
        Assertions.assertEquals(256.0, lathe.getSingleMachineEUt(), 0.001);
    }

    @Test
    public void testCreateStressHelperFallbacks() {
        CreateStressHelper.KineticStats stats = CreateStressHelper.deduceGeneratorStats(
                ResourceLocation.tryParse("create:large_water_wheel"),
                128.0,
                4
        );
        Assertions.assertEquals(128.0, stats.capacityPerRpm(), 0.001);
        Assertions.assertEquals(4, stats.rpm());
        Assertions.assertEquals(512.0, stats.totalSu(), 0.001);
    }

    @Test
    public void testSteamEngineTierCalculations() {
        Assertions.assertEquals(2048.0, CreateStressHelper.calculateSteamEngineTotalSu(-1), 0.001);
        Assertions.assertEquals(320.0, CreateStressHelper.calculateSteamConsumption(-1), 0.001);

        Assertions.assertEquals(32.0, CreateStressHelper.calculateSteamEngineTotalSu(0), 0.001);
        Assertions.assertEquals(80.0, CreateStressHelper.calculateSteamConsumption(0), 0.001);

        Assertions.assertEquals(128.0, CreateStressHelper.calculateSteamEngineTotalSu(1), 0.001);
        Assertions.assertEquals(320.0, CreateStressHelper.calculateSteamConsumption(1), 0.001);

        Assertions.assertEquals(512.0, CreateStressHelper.calculateSteamEngineTotalSu(2), 0.001);
        Assertions.assertEquals(1280.0, CreateStressHelper.calculateSteamConsumption(2), 0.001);

        Assertions.assertEquals(2048.0, CreateStressHelper.calculateSteamEngineTotalSu(3), 0.001);
        Assertions.assertEquals(5120.0, CreateStressHelper.calculateSteamConsumption(3), 0.001);

        Assertions.assertEquals(8192.0, CreateStressHelper.calculateSteamEngineTotalSu(4), 0.001);
        Assertions.assertEquals(20480.0, CreateStressHelper.calculateSteamConsumption(4), 0.001);
    }

    @Test
    public void testCreateBoilerLevelScaling() {
        RecipeNode boiler = CreateRecipeHandler.createCreateBoilerNode();
        Assertions.assertNotNull(boiler);
        Assertions.assertTrue(CreateProperties.isCreateBoiler(boiler));
        Assertions.assertEquals(0, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertFalse(boiler.getProperties().get(CreateProperties.BOILER_WATER_MODE));
        Assertions.assertEquals(2048.0, boiler.getBaseEUt(), 0.001);
        Assertions.assertEquals(16, boiler.getRpm());
        Assertions.assertEquals("gtceu:steam", boiler.getInputs().get(0).getId().toString());

        CreateProperties.applyBoilerLevel(boiler, 9);
        Assertions.assertEquals(9, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertEquals(147456.0, boiler.getBaseEUt(), 0.001);
        Assertions.assertEquals(64, boiler.getRpm());
        Assertions.assertEquals(2880.0, boiler.getInputs().get(0).getAmount(), 0.001);

        CreateProperties.applyBoilerLevel(boiler, 18);
        Assertions.assertEquals(18, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertEquals(294912.0, boiler.getBaseEUt(), 0.001);
        Assertions.assertEquals(64, boiler.getRpm());
        Assertions.assertEquals(5760.0, boiler.getInputs().get(0).getAmount(), 0.001);

        CreateProperties.toggleBoilerFluidMode(boiler);
        Assertions.assertTrue(boiler.getProperties().get(CreateProperties.BOILER_WATER_MODE));
        Assertions.assertEquals("minecraft:water", boiler.getInputs().get(0).getId().toString());
        Assertions.assertEquals(3600.0, boiler.getInputs().get(0).getAmount(), 0.001);

        CreateProperties.toggleBoilerFluidMode(boiler);
        Assertions.assertFalse(boiler.getProperties().get(CreateProperties.BOILER_WATER_MODE));
        Assertions.assertEquals("gtceu:steam", boiler.getInputs().get(0).getId().toString());
        Assertions.assertEquals(5760.0, boiler.getInputs().get(0).getAmount(), 0.001);
    }

    @Test
    public void testCreateBoilerHeaterAddons() {
        CreateModAdapter adapter = new CreateModAdapter();
        List<com.gtceu.calcboard.api.catalog.MachineAddon> collector = new java.util.ArrayList<>();
        adapter.discoverAddons(collector, List.of());

        Assertions.assertEquals(2, collector.size());
        com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon heated = (com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon) collector.get(0);
        com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon superheated = (com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon) collector.get(1);

        Assertions.assertEquals(1, heated.getHeatLevel());
        Assertions.assertFalse(heated.isSuperheated());
        Assertions.assertEquals(2, superheated.getHeatLevel());
        Assertions.assertTrue(superheated.isSuperheated());

        RecipeNode boiler = CreateRecipeHandler.createCreateBoilerNode();
        Assertions.assertTrue(adapter.supportsAddons(boiler));

        for (int i = 0; i < 9; i++) {
            Assertions.assertTrue(adapter.canInstallAddon(boiler, heated));
            adapter.onAddonInstalled(boiler, heated.copy());
        }
        Assertions.assertEquals(9, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertEquals(147456.0, boiler.getBaseEUt(), 0.001);
        Assertions.assertFalse(adapter.canInstallAddon(boiler, heated));

        boiler.getAddons().clear();
        CreateProperties.setBoilerSize(boiler, 72);
        CreateProperties.setBoilerWater(boiler, 180);
        CreateProperties.setBoilerHeat(boiler, 0);

        for (int i = 0; i < 9; i++) {
            Assertions.assertTrue(adapter.canInstallAddon(boiler, superheated));
            adapter.onAddonInstalled(boiler, superheated.copy());
        }
        Assertions.assertEquals(18, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertEquals(294912.0, boiler.getBaseEUt(), 0.001);
        Assertions.assertFalse(adapter.canInstallAddon(boiler, superheated));

        com.gtceu.calcboard.api.catalog.MachineAddon removed = boiler.getAddons().remove(0);
        adapter.onAddonRemoved(boiler, removed);
        Assertions.assertEquals(16, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertEquals(262144.0, boiler.getBaseEUt(), 0.001);
    }

    @Test
    public void testCreateBoilerThreeAxisBottleneckCalculation() {
        RecipeNode boiler = CreateRecipeHandler.createCreateBoilerNode();

        Assertions.assertEquals(CreateProperties.BoilerBottleneck.NONE,
                CreateProperties.getBottleneck(16, 0, 40));
        Assertions.assertEquals(0, CreateProperties.calculateEffectiveLevel(16, 0, 40));

        CreateProperties.setBoilerSize(boiler, 16);
        CreateProperties.setBoilerHeat(boiler, 9);
        CreateProperties.setBoilerWater(boiler, 180);
        Assertions.assertEquals(CreateProperties.BoilerBottleneck.SIZE,
                CreateProperties.getBottleneck(16, 9, 180));
        Assertions.assertEquals(4, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertEquals(65536.0, boiler.getBaseEUt(), 0.001);
        Assertions.assertEquals(64, boiler.getRpm());

        CreateProperties.setBoilerSize(boiler, 72);
        CreateProperties.setBoilerHeat(boiler, 9);
        CreateProperties.setBoilerWater(boiler, 40);
        Assertions.assertEquals(CreateProperties.BoilerBottleneck.WATER,
                CreateProperties.getBottleneck(72, 9, 40));
        Assertions.assertEquals(4, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertEquals(65536.0, boiler.getBaseEUt(), 0.001);

        CreateProperties.setBoilerSize(boiler, 72);
        CreateProperties.setBoilerHeat(boiler, 4);
        CreateProperties.setBoilerWater(boiler, 180);
        Assertions.assertEquals(CreateProperties.BoilerBottleneck.HEAT,
                CreateProperties.getBottleneck(72, 4, 180));
        Assertions.assertEquals(4, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertEquals(65536.0, boiler.getBaseEUt(), 0.001);

        CreateProperties.setBoilerSize(boiler, 36);
        CreateProperties.setBoilerHeat(boiler, 9);
        CreateProperties.setBoilerWater(boiler, 90);
        Assertions.assertEquals(CreateProperties.BoilerBottleneck.NONE,
                CreateProperties.getBottleneck(36, 9, 90));
        Assertions.assertEquals(9, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertEquals(147456.0, boiler.getBaseEUt(), 0.001);

        CreateProperties.setBoilerSize(boiler, 2);
        Assertions.assertEquals(4, (int) boiler.getProperties().get(CreateProperties.BOILER_SIZE_BLOCKS));

        boiler.getProperties().set(CreateProperties.BOILER_SIZE_BLOCKS, 2);
        CreateProperties.recalculateAndApplyBoiler(boiler);
        Assertions.assertEquals(CreateProperties.BoilerBottleneck.INACTIVE,
                CreateProperties.getBottleneck(2, 0, 40));
        Assertions.assertEquals(-1, CreateProperties.calculateEffectiveLevel(2, 0, 40));
        Assertions.assertEquals(0, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertEquals(0.0, boiler.getBaseEUt(), 0.001);
    }

    @Test
    public void testCreateBoilerAddonHeatSync() {
        CreateModAdapter adapter = new CreateModAdapter();
        RecipeNode boiler = CreateRecipeHandler.createCreateBoilerNode();

        CreateProperties.setBoilerSize(boiler, 72);
        CreateProperties.setBoilerWater(boiler, 180);

        List<com.gtceu.calcboard.api.catalog.MachineAddon> collector = new java.util.ArrayList<>();
        adapter.discoverAddons(collector, List.of());
        com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon heated =
                (com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon) collector.get(0);
        com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon superheated =
                (com.gtceu.calcboard.compat.create.addon.CreateHeaterAddon) collector.get(1);

        for (int i = 0; i < 9; i++) {
            adapter.onAddonInstalled(boiler, heated.copy());
        }

        Assertions.assertEquals(72, (int) boiler.getProperties().get(CreateProperties.BOILER_SIZE_BLOCKS));
        Assertions.assertEquals(180, (int) boiler.getProperties().get(CreateProperties.BOILER_WATER_MB_TICK));
        Assertions.assertEquals(9, (int) boiler.getProperties().get(CreateProperties.BOILER_HEAT_LEVEL));
        Assertions.assertEquals(9, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertEquals(147456.0, boiler.getBaseEUt(), 0.001);

        com.gtceu.calcboard.api.catalog.MachineAddon removed = boiler.getAddons().remove(0);
        adapter.onAddonRemoved(boiler, removed);
        adapter.onAddonInstalled(boiler, superheated.copy());

        Assertions.assertEquals(72, (int) boiler.getProperties().get(CreateProperties.BOILER_SIZE_BLOCKS));
        Assertions.assertEquals(180, (int) boiler.getProperties().get(CreateProperties.BOILER_WATER_MB_TICK));
        Assertions.assertEquals(10, (int) boiler.getProperties().get(CreateProperties.BOILER_HEAT_LEVEL));
        Assertions.assertEquals(10, (int) boiler.getProperties().get(CreateProperties.BOILER_LEVEL));
        Assertions.assertEquals(163840.0, boiler.getBaseEUt(), 0.001);
    }
}



