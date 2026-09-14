package com.gtceu.calcboard.compat;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockMachineInspector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.create.CreateSequencedRecipeExtractor;
import com.gtceu.calcboard.compat.gtceu.helper.EnergyHatchHelper;
import com.gtceu.calcboard.compat.start.StarTAddonCrawler;
import com.gtceu.calcboard.compat.thermal.helper.ThermalAugmentHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

public class RFC047ExactMatchNormalizationTest {

    @BeforeEach
    void setUp() {
        com.gtceu.calcboard.testutil.TestMultiblockFixtures.initTestEnvironmentDefaults();
    }

    @Test
    void testEnergyHatchExactTokenMatchingAndEvBugPrevention() {
        ResourceLocation evHatchId = ResourceLocation.tryParse("gtceu:ev_energy_input_hatch");
        EnergyHatchHelper.EnergyHatchStats evStats = EnergyHatchHelper.getEnergyHatchStats(evHatchId);
        Assertions.assertNotNull(evStats);
        Assertions.assertEquals(GTVoltageTier.EV, evStats.tier());

        ResourceLocation uevHatchId = ResourceLocation.tryParse("gtceu:uev_energy_input_hatch_16a");
        EnergyHatchHelper.EnergyHatchStats uevStats = EnergyHatchHelper.getEnergyHatchStats(uevHatchId);
        Assertions.assertNotNull(uevStats);
        Assertions.assertEquals(GTVoltageTier.UEV, uevStats.tier());
        Assertions.assertEquals(16, uevStats.amperage());

        ResourceLocation uhvHatchId = ResourceLocation.tryParse("gtceu:uhv_energy_input_hatch_4a");
        EnergyHatchHelper.EnergyHatchStats uhvStats = EnergyHatchHelper.getEnergyHatchStats(uhvHatchId);
        Assertions.assertNotNull(uhvStats);
        Assertions.assertEquals(GTVoltageTier.UHV, uhvStats.tier());
        Assertions.assertEquals(4, uhvStats.amperage());

        ResourceLocation uivHatchId = ResourceLocation.tryParse("start_core:uiv_256a_dream_link_energy_hatch");
        EnergyHatchHelper.EnergyHatchStats uivStats = EnergyHatchHelper.getEnergyHatchStats(uivHatchId);
        Assertions.assertNotNull(uivStats);
        Assertions.assertEquals(GTVoltageTier.UIV, uivStats.tier());

        ResourceLocation dreamLinkWithoutEnergy = ResourceLocation.tryParse("start_core:uiv_256a_dream_link_hatch");
        EnergyHatchHelper.EnergyHatchStats dlStats = EnergyHatchHelper.getEnergyHatchStats(dreamLinkWithoutEnergy);
        Assertions.assertNotNull(dlStats, "Dream-link hatch without 'energy' keyword in path must be recognized");
        Assertions.assertEquals(GTVoltageTier.UIV, dlStats.tier());
        Assertions.assertEquals(256, dlStats.amperage());

        ResourceLocation devHatchId = ResourceLocation.tryParse("gtceu:developer_energy_hatch");
        EnergyHatchHelper.EnergyHatchStats devStats = EnergyHatchHelper.getEnergyHatchStats(devHatchId);
        Assertions.assertNotNull(devStats);
        Assertions.assertEquals(GTVoltageTier.LV, devStats.tier());
    }

    @Test
    void testIsLikelyEnergyHatchPathNormalization() {
        Assertions.assertTrue(EnergyHatchHelper.isLikelyEnergyHatchPath("ev_energy_input_hatch"));
        Assertions.assertTrue(EnergyHatchHelper.isLikelyEnergyHatchPath("uiv_256a_dream_link_hatch"));
        Assertions.assertTrue(EnergyHatchHelper.isLikelyEnergyHatchPath("iv_substation_input_hatch_64a"));
        Assertions.assertTrue(EnergyHatchHelper.isLikelyEnergyHatchPath("zpm_laser_target_hatch"));

        Assertions.assertFalse(EnergyHatchHelper.isLikelyEnergyHatchPath("ev_energy_output_hatch"));
        Assertions.assertFalse(EnergyHatchHelper.isLikelyEnergyHatchPath("ev_dynamo_hatch"));
        Assertions.assertFalse(EnergyHatchHelper.isLikelyEnergyHatchPath("tin_single_cable"));
        Assertions.assertFalse(EnergyHatchHelper.isLikelyEnergyHatchPath("cover_energy_detector"));
    }

    @Test
    void testThermalDynamoExactCategoryAndIconMatching() {
        RecipeNode stirlingNode = RecipeNode.create("Stirling", 100.0, 40.0, GTVoltageTier.LV);
        stirlingNode.setRecipeCategoryId(ResourceLocation.tryParse("thermal:stirling_fuel"));
        Assertions.assertTrue(ThermalAugmentHelper.isDynamoNode(stirlingNode));

        RecipeNode magmaticNode = RecipeNode.create("Magmatic", 100.0, 40.0, GTVoltageTier.LV);
        magmaticNode.setMachineIcon(ResourceLocation.tryParse("thermal:dynamo_magmatic"));
        Assertions.assertTrue(ThermalAugmentHelper.isDynamoNode(magmaticNode));

        RecipeNode chemicalNode = RecipeNode.create("Biofuel Mixer Unit", 100.0, 30.0, GTVoltageTier.LV);
        chemicalNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:chemical_reactor"));
        chemicalNode.setMachineIcon(ResourceLocation.tryParse("gtceu:chemical_reactor"));
        Assertions.assertFalse(ThermalAugmentHelper.isDynamoNode(chemicalNode));

        RecipeNode gtNode = RecipeNode.create("Dynamo Test Bench", 100.0, 30.0, GTVoltageTier.LV);
        gtNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:assembler"));
        Assertions.assertFalse(ThermalAugmentHelper.isDynamoNode(gtNode));
    }

    @Test
    void testThermalDynamoAndBoilerRecipeDeterministicChecks() {
        DummyThermalFuelRecipe fuelRecipe = new DummyThermalFuelRecipe();
        Assertions.assertTrue(ThermalAugmentHelper.isDynamoRecipe(fuelRecipe));

        DummyBoilingRecipe boilingRecipe = new DummyBoilingRecipe();
        Assertions.assertTrue(ThermalAugmentHelper.isBoilerRecipe(boilingRecipe));

        DummyUnrelatedRecipe unrelatedRecipe = new DummyUnrelatedRecipe();
        Assertions.assertFalse(ThermalAugmentHelper.isDynamoRecipe(unrelatedRecipe));
        Assertions.assertFalse(ThermalAugmentHelper.isBoilerRecipe(unrelatedRecipe));
    }

    @Test
    void testCreateSequencedAssemblyExactMatchingWithoutHeuristics() {
        DeployerApplicationRecipe deployerRecipe = new DeployerApplicationRecipe();
        ResourceLocation deployerIcon = CreateSequencedRecipeExtractor.extractStepMachineIcon(deployerRecipe);
        Assertions.assertEquals(ResourceLocation.tryParse("create:deployer"), deployerIcon);

        FillingRecipe fillingRecipe = new FillingRecipe();
        ResourceLocation spoutIcon = CreateSequencedRecipeExtractor.extractStepMachineIcon(fillingRecipe);
        Assertions.assertEquals(ResourceLocation.tryParse("create:spout"), spoutIcon);

        PressingRecipe pressingRecipe = new PressingRecipe();
        ResourceLocation pressIcon = CreateSequencedRecipeExtractor.extractStepMachineIcon(pressingRecipe);
        Assertions.assertEquals(ResourceLocation.tryParse("create:mechanical_press"), pressIcon);

        CuttingRecipe cuttingRecipe = new CuttingRecipe();
        ResourceLocation sawIcon = CreateSequencedRecipeExtractor.extractStepMachineIcon(cuttingRecipe);
        Assertions.assertEquals(ResourceLocation.tryParse("create:mechanical_saw"), sawIcon);

        DummyUnknownRecipe unknownRecipe = new DummyUnknownRecipe();
        ResourceLocation fallbackIcon = CreateSequencedRecipeExtractor.extractStepMachineIcon(unknownRecipe);
        Assertions.assertEquals(ResourceLocation.tryParse("create:deployer"), fallbackIcon);
    }

    @Test
    void testMultiblockMachineInspectorDeterministicThreadingDetection() {
        DummyThreadingMachineDef defWithModifier = new DummyThreadingMachineDef(new ThreadingMachineRecipeModifier());
        Assertions.assertTrue(MultiblockMachineInspector.hasThreadingModifier(DummyThreadingMachineDef.class, defWithModifier));

        DummyThreadingMachineDef defWithInnerModifier = new DummyThreadingMachineDef(new StartRecipeModifiers.Threading());
        Assertions.assertTrue(MultiblockMachineInspector.hasThreadingModifier(DummyThreadingMachineDef.class, defWithInnerModifier));

        DummyThreadingMachineDef defWithList = new DummyThreadingMachineDef(List.of(new ThreadingMachineRecipeModifier()));
        Assertions.assertTrue(MultiblockMachineInspector.hasThreadingModifier(DummyThreadingMachineDef.class, defWithList));

        DummyThreadingMachineDef defWithToStringTrick = new DummyThreadingMachineDef(new NonThreadingModifier());
        Assertions.assertFalse(MultiblockMachineInspector.hasThreadingModifier(DummyThreadingMachineDef.class, defWithToStringTrick));

        Assertions.assertTrue(MultiblockMachineInspector.isThreadingMachineClass(StarTThreadingCapableMachine.class));
        Assertions.assertFalse(MultiblockMachineInspector.isThreadingMachineClass(NormalMachineClass.class));
    }

    @Test
    void testStarTMaintenanceHatchExactIdMatching() {
        ResourceLocation validHatch = ResourceLocation.tryParse("start_core:sterile_cleaning_maintenance_hatch");
        MachineAddon addon = StarTAddonCrawler.parseStarTMaintenanceHatch(ItemStack.EMPTY, validHatch);
        Assertions.assertNotNull(addon);
        Assertions.assertEquals(MachineAddon.Category.MAINTENANCE, addon.getCategory());

        ResourceLocation fakeHatch = ResourceLocation.tryParse("start_core:maintenance_pipe_casing");
        MachineAddon fakeAddon = StarTAddonCrawler.parseStarTMaintenanceHatch(ItemStack.EMPTY, fakeHatch);
        Assertions.assertNull(fakeAddon);
    }

    private static class DeployerApplicationRecipe {}
    private static class FillingRecipe {}
    private static class PressingRecipe {}
    private static class CuttingRecipe {}
    private static class DummyUnknownRecipe {}

    public static class DummyThermalFuelRecipe {}
    public static class DummyBoilingRecipe {}
    public static class DummyUnrelatedRecipe {}

    public static class ThreadingMachineRecipeModifier {}

    public static class StartRecipeModifiers {
        public static class Threading {}
    }

    public static class NonThreadingModifier {
        @Override
        public String toString() {
            return "threading_machine_heuristic_string";
        }
    }

    public static class DummyThreadingMachineDef {
        private final Object modifiers;

        public DummyThreadingMachineDef(Object modifiers) {
            this.modifiers = modifiers;
        }

        public Object getRecipeModifiers() {
            return modifiers;
        }
    }

    public static class StarTThreadingCapableMachine {}
    public static class NormalMachineClass {}
}
