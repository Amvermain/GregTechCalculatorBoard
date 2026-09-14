package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.bom.MultiblockBOMCalculator;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.dialog.MachineConfigDialog;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
import com.gtceu.calcboard.compat.gtceu.handler.GTCombustionAddonHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFFuel;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFModuleSlot;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFModuleType;
import com.gtceu.calcboard.compat.gtceu.model.mcf.MCFSlotConfiguration;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import com.gtceu.calcboard.testutil.SimulatedGTEnvironment;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import com.gtceu.calcboard.api.model.IngredientStack;

import static org.junit.jupiter.api.Assertions.*;

public class MCFComplexIntegrationTest {

    @BeforeEach
    void setUp() {
        SimulatedGTEnvironment.setupFullEnvironment();
        GTCombustionHelper.setForceStarTForTesting(true);
    }

    @AfterEach
    void tearDown() {
        GTCombustionHelper.setForceStarTForTesting(false);
        SimulatedGTEnvironment.tearDownEnvironment();
    }

    private RecipeNode createMCFNode() {
        RecipeNode node = new RecipeNode("mcf_test_node", "Modular Combustion Frame", 0.0, 0.0, GTVoltageTier.EV);
        node.setMachineIcon(GTCombustionHelper.START_MCF);
        node.setMultiblock(true);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        node.setGenerator(true);
        node.setRecipeTier(GTVoltageTier.EV);
        node.setTargetTier(GTVoltageTier.EV);
        node.setBaseDurationTicks(72);
        node.setBaseEUt(160.0);
        return node;
    }

    private RecipeNode createStandaloneModuleNode(ResourceLocation moduleIcon, GTVoltageTier tier) {
        RecipeNode node = new RecipeNode("ucm_test_node", "LuV Combustion Module", 0.0, 0.0, tier);
        node.setMachineIcon(moduleIcon);
        node.setMultiblock(true);
        node.setEnergyType(EnergyType.ELECTRIC_EU);
        node.setGenerator(true);
        node.setRecipeTier(tier);
        node.setTargetTier(tier);
        node.setBaseDurationTicks(72);
        node.setBaseEUt(160.0);
        return node;
    }

    @Test
    @DisplayName("REQ-MCF-01: Slot configuration serialization and preset management")
    void testSlotConfigurationAndPresets() {
        RecipeNode node = createMCFNode();

        MCFSlotConfiguration initialCfg = GTCombustionHelper.getMCFConfiguration(node);
        assertEquals(0, initialCfg.getActiveSlotCount());

        initialCfg.applyPreset8xUCM();
        assertEquals(8, initialCfg.getActiveSlotCount());
        for (MCFModuleSlot slot : initialCfg.getActiveSlots()) {
            assertEquals(MCFModuleType.UCM, slot.getModuleType());
            assertEquals(MCFFuel.CETANE_DIESEL, slot.getFuel());
            assertTrue(slot.isOxidizerBoosted());
        }

        initialCfg.saveToNode(node);

        MCFSlotConfiguration loadedCfg = GTCombustionHelper.getMCFConfiguration(node);
        assertEquals(8, loadedCfg.getActiveSlotCount());
        assertEquals(MCFModuleType.UCM, loadedCfg.getSlot(0).getModuleType());

        loadedCfg.applyPreset8xSCM();
        assertEquals(8, loadedCfg.getActiveSlotCount());
        assertEquals(MCFModuleType.SCM, loadedCfg.getSlot(0).getModuleType());

        loadedCfg.clearAll();
        assertEquals(0, loadedCfg.getActiveSlotCount());
    }

    @Test
    @DisplayName("REQ-MCF-02: Physics formula exact match for 8x UCM with Deionized Water")
    void test8xUCMDeionizedPhysicsExactMatch() {
        RecipeNode node = createMCFNode();

        MCFSlotConfiguration cfg = new MCFSlotConfiguration();
        cfg.applyPreset8xUCM();
        cfg.saveToNode(node);

        GTCombustionHelper.setMCFCoolantType(node, "deionized_water");
        assertEquals("deionized_water", GTCombustionHelper.getMCFCoolantType(node));

        double totalPower = GTPowerCalculator.computeCombustionPower(node);
        assertEquals(1835008.0, totalPower, 0.001);

        GTCombustionHelper.LaserHatchRecommendation rec = GTCombustionHelper.getLaserHatchRecommendation(totalPower);
        assertEquals(GTVoltageTier.UV, rec.tier());
        assertEquals(3.5, rec.amps(), 0.001);
        assertEquals("UV Laser Hatch, 3.5A", rec.label());
    }

    @Test
    @DisplayName("REQ-MCF-03: Physics formula verification across Distilled Water and No Coolant")
    void testCoolantMultipliersPhysics() {
        RecipeNode node = createMCFNode();

        MCFSlotConfiguration cfg = new MCFSlotConfiguration();
        cfg.applyPreset8xUCM();
        cfg.saveToNode(node);

        GTCombustionHelper.setMCFCoolantType(node, "distilled_water");
        double distilledPower = GTPowerCalculator.computeCombustionPower(node);
        assertEquals(1572864.0, distilledPower, 0.001);

        GTCombustionHelper.setMCFCoolantType(node, "none");
        double noCoolantPower = GTPowerCalculator.computeCombustionPower(node);
        assertEquals(1179648.0, noCoolantPower, 0.001);
    }

    @Test
    @DisplayName("REQ-MCF-04: Central coolant demand scales with active docked modules (N * 500 B/hr)")
    void testCentralCoolantDemand() {
        RecipeNode node = createMCFNode();

        MCFSlotConfiguration cfg = new MCFSlotConfiguration();
        cfg.applyPreset8xUCM();
        cfg.saveToNode(node);

        GTCombustionHelper.setMCFCoolantType(node, "deionized_water");
        double demand8 = GTCombustionHelper.getCentralCoolantDemandMbPerSec(node);
        assertEquals(8.0 * (500000.0 / 3600.0), demand8, 0.01);

        cfg.getSlot(6).setEnabled(false);
        cfg.getSlot(7).setEnabled(false);
        cfg.saveToNode(node);

        double demand6 = GTCombustionHelper.getCentralCoolantDemandMbPerSec(node);
        assertEquals(6.0 * (500000.0 / 3600.0), demand6, 0.01);

        GTCombustionHelper.setMCFCoolantType(node, "none");
        double demandNone = GTCombustionHelper.getCentralCoolantDemandMbPerSec(node);
        assertEquals(0.0, demandNone, 0.001);
    }

    @Test
    @DisplayName("REQ-MCF-05: Input synchronization injects single central coolant and aggregates module fluids")
    void testInputSynchronization() {
        RecipeNode node = createMCFNode();

        MCFSlotConfiguration cfg = new MCFSlotConfiguration();
        cfg.applyPreset8xUCM();
        cfg.saveToNode(node);
        GTCombustionHelper.setMCFCoolantType(node, "deionized_water");

        GTCombustionHelper.syncCombustionInputs(node);

        long deionizedCount = node.getInputs().stream()
                .filter(in -> GTCombustionHelper.DEIONIZED_WATER.equals(in.getId()))
                .count();
        assertEquals(1, deionizedCount, "Must have exactly 1 central coolant input");

        long distilledCount = node.getInputs().stream()
                .filter(in -> GTCombustionHelper.DISTILLED_WATER.equals(in.getId()))
                .count();
        assertEquals(0, distilledCount, "Must have no distilled water when deionized is chosen");

        long lubeCount = node.getInputs().stream()
                .filter(in -> GTCombustionHelper.LUBRICANT.equals(in.getId()))
                .count();
        assertEquals(1, lubeCount, "Must aggregate lubricant into single port");

        long oxCount = node.getInputs().stream()
                .filter(in -> GTCombustionHelper.WHITE_FUMING_NITRIC_ACID.equals(in.getId()))
                .count();
        assertEquals(1, oxCount, "Must aggregate oxidizer into single port");
    }

    @Test
    @DisplayName("REQ-MCF-06: Standalone module normalization isolates local coolant and runs at 1.0x base")
    void testStandaloneModuleIsolation() {
        RecipeNode ucm = createStandaloneModuleNode(GTCombustionHelper.START_T1_COMBUSTION, GTVoltageTier.LuV);

        assertFalse(GTCombustionHelper.isModularCombustionFrame(ucm));
        assertTrue(GTCombustionHelper.isStarTCombustionModule(ucm));

        MachineAddon coolantAddon = new MachineAddon(
                "start_core:deionized_water_coolant",
                "Deionized Water Coolant",
                AddonCategory.MULTIBLOCK_TRAIT,
                "+40% power",
                null
        );
        assertFalse(GTCombustionAddonHelper.isCombustionBoostCompatible(ucm, coolantAddon),
                "Coolant addon must be rejected on standalone module");

        GTCombustionHelper.syncCombustionInputs(ucm);
        boolean hasCoolantInput = ucm.getInputs().stream()
                .anyMatch(in -> GTCombustionHelper.DEIONIZED_WATER.equals(in.getId())
                        || GTCombustionHelper.DISTILLED_WATER.equals(in.getId()));
        assertFalse(hasCoolantInput, "Standalone module must NOT receive local coolant inputs");

        double mult = GTCombustionHelper.getCombustionPowerMultiplier(ucm);
        assertEquals(1.0, mult, 0.001, "Standalone unboosted module power multiplier must be 1.0x base");
    }

    @Test
    @DisplayName("REQ-MCF-07: Addon category and dialog default category routing")
    void testCategoryRouting() {
        RecipeNode mcf = createMCFNode();
        List<AddonCategory> cats = GTAddonCompatibilityHandler.getApplicableAddonCategories(mcf);

        assertTrue(cats.contains(AddonCategory.MCF_MODULE));
        assertFalse(cats.contains(AddonCategory.MULTIBLOCK_TRAIT));

        AddonCategory defaultCat = MachineConfigDialog.getDefaultCategoryForNode(mcf);
        assertEquals(AddonCategory.MCF_MODULE, defaultCat);

        List<AddonCategory> filterChips = com.gtceu.calcboard.client.gui.dialog.config.AddonCategoryChipRenderer.getAllCategoriesForFilter(mcf);
        assertTrue(filterChips.contains(AddonCategory.MCF_MODULE), "Category filter chips bar must include MCF_MODULE tab");

        RecipeNode ucm = createStandaloneModuleNode(GTCombustionHelper.START_T1_COMBUSTION, GTVoltageTier.LuV);
        List<AddonCategory> ucmCats = GTAddonCompatibilityHandler.getApplicableAddonCategories(ucm);
        assertFalse(ucmCats.contains(AddonCategory.MCF_MODULE));
        assertTrue(ucmCats.contains(AddonCategory.MULTIBLOCK_TRAIT));
    }

    @Test
    @DisplayName("REQ-MCF-08: Multiblock BOM aggregation counts 1 + N multiblocks and includes module parts")
    void testMultiblockBOMAggregation() {
        RecipeNode mcf = createMCFNode();
        MCFSlotConfiguration cfg = new MCFSlotConfiguration();
        cfg.applyPreset8xUCM();
        cfg.saveToNode(mcf);
        GTCombustionHelper.setMCFCoolantType(mcf, "deionized_water");

        MultiblockStructureCatalog.registerManualStructure(new MultiblockStructureDef(
                GTCombustionHelper.START_MCF,
                "Modular Combustion Frame",
                List.of(
                        new MultiblockStructurePart(GTCombustionHelper.START_MCF, "Modular Combustion Frame", 1, PartCategory.CONTROLLER),
                        new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:solid_machine_casing"), "Solid Casing", 50, PartCategory.CASING)
                ),
                0, 0, 0, 0, 1, 0, 1
        ));

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(mcf), false);

        assertEquals(9, summary.totalMultiblockCount(), "1x MCF Frame + 8x UCM modules must equal 9 total multiblocks");

        boolean hasMCFController = summary.aggregatedItems().stream()
                .anyMatch(i -> GTCombustionHelper.START_MCF.equals(i.itemId()));
        assertTrue(hasMCFController, "BOM must contain MCF Frame Controller");

        boolean hasUCMController = summary.aggregatedItems().stream()
                .anyMatch(i -> GTCombustionHelper.START_T1_COMBUSTION.equals(i.itemId()) && i.totalAmount() == 8);
        assertTrue(hasUCMController, "BOM must contain 8x UCM Controllers");

        ResourceLocation expectedLaser = ResourceLocation.tryParse("gtceu:uv_laser_source_hatch");
        boolean hasLaserHatch = summary.aggregatedItems().stream()
                .anyMatch(i -> expectedLaser != null && expectedLaser.equals(i.itemId()));
        assertTrue(hasLaserHatch, "BOM must contain recommended Laser Hatch");
    }

    @Test
    @DisplayName("REQ-MCF-09: Idle MCF with zero active modules consumes zero coolant and generates zero power")
    void testZeroModulesCoolantDemandAndPowerZero() {
        RecipeNode mcf = createMCFNode();
        MCFSlotConfiguration cfg = new MCFSlotConfiguration();
        cfg.saveToNode(mcf);

        GTCombustionHelper.setMCFCoolantType(mcf, "deionized_water");

        double demand = GTCombustionHelper.getCentralCoolantDemandMbPerSec(mcf);
        assertEquals(0.0, demand, 0.001);

        double totalPower = GTCombustionHelper.computeMCFTotalPower(mcf);
        assertEquals(0.0, totalPower, 0.001);

        double combustionPower = GTPowerCalculator.computeCombustionPower(mcf);
        assertEquals(0.0, combustionPower, 0.001);

        GTCombustionHelper.syncCombustionInputs(mcf);
        assertFalse(GTCombustionHelper.areCombustionInputsOutOfSync(mcf));
    }

    @Test
    @DisplayName("REQ-MCF-10: Multiblock BOM avoids duplicate module controllers when structure catalog already includes controller")
    void testBOMAggregationNoDuplicateControllers() {
        RecipeNode mcf = createMCFNode();
        MCFSlotConfiguration cfg = new MCFSlotConfiguration();
        cfg.applyPreset8xUCM();
        cfg.saveToNode(mcf);

        MultiblockStructureCatalog.registerManualStructure(new MultiblockStructureDef(
                GTCombustionHelper.START_MCF,
                "Modular Combustion Frame",
                List.of(
                        new MultiblockStructurePart(GTCombustionHelper.START_MCF, "Modular Combustion Frame", 1, PartCategory.CONTROLLER),
                        new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:solid_machine_casing"), "Solid Casing", 50, PartCategory.CASING)
                ),
                0, 0, 0, 0, 1, 0, 1
        ));

        MultiblockStructureCatalog.registerManualStructure(new MultiblockStructureDef(
                GTCombustionHelper.START_T1_COMBUSTION,
                "LuV Combustion Module",
                List.of(
                        new MultiblockStructurePart(GTCombustionHelper.START_T1_COMBUSTION, "LuV Combustion Module", 1, PartCategory.CONTROLLER),
                        new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:solid_machine_casing"), "Solid Casing", 20, PartCategory.CASING)
                ),
                0, 0, 0, 0, 0, 0, 0
        ));

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(List.of(mcf), false);

        int totalUcmControllers = summary.aggregatedItems().stream()
                .filter(i -> GTCombustionHelper.START_T1_COMBUSTION.equals(i.itemId()))
                .mapToInt(MultiblockBOMSummary.BOMItemEntry::totalAmount)
                .sum();
        assertEquals(8, totalUcmControllers, "BOM must contain exactly 8 UCM controllers without duplication");
    }

    @Test
    @DisplayName("REQ-MCF-11: Coolant addon installation and removal synchronizes MCF coolant properties")
    void testCoolantAddonMCFPropertySynchronization() {
        RecipeNode mcf = createMCFNode();

        MachineAddon deionizedAddon = new MachineAddon(
                "start_core:deionized_water_coolant",
                "Deionized Coolant",
                AddonCategory.MULTIBLOCK_TRAIT,
                "+40%",
                null
        );
        GTCombustionAddonHelper.applyCombustionBoostInstallation(mcf, deionizedAddon);

        assertEquals("deionized_water", mcf.getProperties().get(GTCEuProperties.MCF_COOLANT_TYPE));
        assertEquals("deionized_water", mcf.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE));
        assertEquals("deionized_water", GTCombustionHelper.getMCFCoolantType(mcf));

        MachineAddon distilledAddon = new MachineAddon(
                "start_core:distilled_water_coolant",
                "Distilled Coolant",
                AddonCategory.MULTIBLOCK_TRAIT,
                "+20%",
                null
        );
        GTCombustionAddonHelper.applyCombustionBoostInstallation(mcf, distilledAddon);

        assertEquals("distilled_water", mcf.getProperties().get(GTCEuProperties.MCF_COOLANT_TYPE));
        assertEquals("distilled_water", mcf.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE));
        assertEquals("distilled_water", GTCombustionHelper.getMCFCoolantType(mcf));

        GTCombustionAddonHelper.applyCombustionBoostRemoval(mcf, distilledAddon);

        assertEquals("none", mcf.getProperties().get(GTCEuProperties.MCF_COOLANT_TYPE));
        assertEquals("none", mcf.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE));
        assertEquals("none", GTCombustionHelper.getMCFCoolantType(mcf));
    }

    @Test
    @DisplayName("REQ-MCF-12: Switching machine away from MCF purges coolant properties and addons")
    void testMachineSwitchClearsCoolantOnStandaloneModule() {
        RecipeNode node = createMCFNode();
        MCFSlotConfiguration cfg = new MCFSlotConfiguration();
        cfg.applyPreset8xUCM();
        cfg.saveToNode(node);

        MachineAddon coolantAddon = new MachineAddon(
                "start_core:deionized_water_coolant",
                "Deionized Coolant",
                AddonCategory.MULTIBLOCK_TRAIT,
                "+40%",
                null
        );
        com.gtceu.calcboard.api.spi.IModAdapter adapter = com.gtceu.calcboard.api.spi.ModAdapterRegistry.getAdapterForNode(node);
        assertNotNull(adapter);
        adapter.onAddonInstalled(node, coolantAddon);

        assertEquals("deionized_water", node.getProperties().get(GTCEuProperties.MCF_COOLANT_TYPE));
        assertFalse(node.getAddons().isEmpty());

        adapter.onMachineIconChanged(node, GTCombustionHelper.START_MCF, GTCombustionHelper.START_T1_COMBUSTION);

        assertEquals("none", node.getProperties().get(GTCEuProperties.MCF_COOLANT_TYPE));
        assertEquals("none", node.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE));
        assertEquals("[]", node.getProperties().get(GTCEuProperties.MCF_SLOTS_DATA));
        boolean hasCoolantAddon = node.getAddons().stream().anyMatch(GTCombustionAddonHelper::isCoolantAddon);
        assertFalse(hasCoolantAddon);
    }

    @Test
    @DisplayName("REQ-MCF-13: Out-of-sync detection accurately detects coolant switching and slot modifications")
    void testOutOfSyncDetectionOnCoolantSwitchAndClear() {
        RecipeNode mcf = createMCFNode();
        MCFSlotConfiguration cfg = new MCFSlotConfiguration();
        cfg.applyPreset8xUCM();
        cfg.saveToNode(mcf);
        GTCombustionHelper.setMCFCoolantType(mcf, "distilled_water");
        GTCombustionHelper.syncCombustionInputs(mcf);

        assertFalse(GTCombustionHelper.areCombustionInputsOutOfSync(mcf));

        mcf.getProperties().set(GTCEuProperties.MCF_COOLANT_TYPE, "deionized_water");
        mcf.getProperties().set(GTCEuProperties.COMBUSTION_COOLANT_TYPE, "deionized_water");
        assertTrue(GTCombustionHelper.areCombustionInputsOutOfSync(mcf));

        GTCombustionHelper.syncCombustionInputs(mcf);
        assertFalse(GTCombustionHelper.areCombustionInputsOutOfSync(mcf));

        cfg.clearAll();
        cfg.saveToNode(mcf);
        assertTrue(GTCombustionHelper.areCombustionInputsOutOfSync(mcf));

        GTCombustionHelper.syncCombustionInputs(mcf);
        assertFalse(GTCombustionHelper.areCombustionInputsOutOfSync(mcf));
    }

    @Test
    @DisplayName("REQ-MCF-14: Multiblock workstation resolver includes MCF for combustion and rocket recipes")
    void testMultiblockWorkstationsIncludesMCF() {
        com.gtceu.calcboard.api.spi.IModAdapter adapter = com.gtceu.calcboard.api.spi.ModAdapterRegistry.getAdapterForModId("gtceu");
        assertNotNull(adapter);

        // 1. Rocket Fuel Node (like user's screenshot)
        RecipeNode rocketNode = new RecipeNode("rocket_test", "Modular Rocket Module", 0.0, 0.0, GTVoltageTier.UEV);
        rocketNode.setRecipeCategoryId(ResourceLocation.tryParse("start_core:modular_rocket_module"));
        rocketNode.setMachineIcon(GTCombustionHelper.START_T4_ROCKET);
        rocketNode.getAvailableWorkstations().add(GTCombustionHelper.START_T3_ROCKET);
        rocketNode.getAvailableWorkstations().add(GTCombustionHelper.START_T4_ROCKET);

        List<ResourceLocation> rocketMbs = adapter.getMultiblockWorkstations(rocketNode);
        assertTrue(rocketMbs.contains(GTCombustionHelper.START_MCF), "Rocket node must include Modular Combustion Frame in multiblock options");

        // 2. Combustion Generator Node
        RecipeNode combustionNode = new RecipeNode("diesel_test", "Combustion Generator", 0.0, 0.0, GTVoltageTier.EV);
        combustionNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:combustion_generator"));
        combustionNode.setMachineIcon(GTCombustionHelper.LARGE_COMBUSTION_ENGINE);

        List<ResourceLocation> combustionMbs = adapter.getMultiblockWorkstations(combustionNode);
        assertTrue(combustionMbs.contains(GTCombustionHelper.START_MCF), "Combustion node must include Modular Combustion Frame in multiblock options");

        // 3. Unrelated Macerator Node
        RecipeNode maceratorNode = new RecipeNode("macerator_test", "Macerator", 0.0, 0.0, GTVoltageTier.EV);
        maceratorNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        List<ResourceLocation> maceratorMbs = adapter.getMultiblockWorkstations(maceratorNode);
        assertFalse(maceratorMbs.contains(GTCombustionHelper.START_MCF), "Macerator node must not include Modular Combustion Frame");
    }

    @Test
    @DisplayName("REQ-MCF-15: MCF fuel inputs dynamically synchronize with docked module configurations and purge stale recipe fuels")
    void testMCFFuelInputsExact() {
        RecipeNode mcf = createMCFNode();
        ResourceLocation rocketFuelId = ResourceLocation.tryParse("gtceu:rocket_fuel");
        mcf.addInput(IngredientStack.fluid(rocketFuelId, "Rocket Fuel", 1000.0));
        assertEquals(1, mcf.getInputs().size());
        assertEquals(rocketFuelId, mcf.getInputs().get(0).getId());

        MCFSlotConfiguration cfg = new MCFSlotConfiguration();
        for (int i = 0; i < 5; i++) {
            cfg.getSlot(i).setEnabled(true);
            cfg.getSlot(i).setModuleType(MCFModuleType.UCM);
            cfg.getSlot(i).setFuel(MCFFuel.HIGH_OCTANE_GASOLINE);
            cfg.getSlot(i).setOxidizerBoosted(true);
        }
        cfg.saveToNode(mcf);
        GTCombustionHelper.setMCFCoolantType(mcf, "deionized_water");

        GTCombustionHelper.syncCombustionInputs(mcf);

        boolean hasRocketFuel = mcf.getInputs().stream().anyMatch(in -> rocketFuelId.equals(in.getId()));
        assertFalse(hasRocketFuel, "Stale recipe Rocket Fuel must be purged from inputs");

        Map<MCFFuel, Double> fuelDemands = GTCombustionHelper.getMCFFuelDemandMbPerSec(mcf);
        assertEquals(1, fuelDemands.size());
        assertEquals(2048.0, fuelDemands.get(MCFFuel.HIGH_OCTANE_GASOLINE), 0.001);

        assertEquals(4, mcf.getInputs().size());

        IngredientStack hogInput = mcf.getInputs().stream()
                .filter(in -> MCFFuel.HIGH_OCTANE_GASOLINE.getFluidId().equals(in.getId()))
                .findFirst().orElse(null);
        assertNotNull(hogInput);
        assertEquals(2048.0, mcf.getInputSlotRate(mcf.getInputs().indexOf(hogInput), false), 0.01);
        assertEquals(102.4, mcf.getInputSlotRate(mcf.getInputs().indexOf(hogInput), false) / 20.0, 0.001);

        IngredientStack coolantInput = mcf.getInputs().stream()
                .filter(in -> GTCombustionHelper.DEIONIZED_WATER.equals(in.getId()))
                .findFirst().orElse(null);
        assertNotNull(coolantInput);
        assertEquals(5 * (500000.0 / 3600.0), mcf.getInputSlotRate(mcf.getInputs().indexOf(coolantInput), false), 0.01);

        IngredientStack lubeInput = mcf.getInputs().stream()
                .filter(in -> GTCombustionHelper.LUBRICANT.equals(in.getId()))
                .findFirst().orElse(null);
        assertNotNull(lubeInput);
        assertEquals(5 * (100.0 / 3.6), mcf.getInputSlotRate(mcf.getInputs().indexOf(lubeInput), false), 0.01);

        IngredientStack oxInput = mcf.getInputs().stream()
                .filter(in -> GTCombustionHelper.WHITE_FUMING_NITRIC_ACID.equals(in.getId()))
                .findFirst().orElse(null);
        assertNotNull(oxInput);
        assertEquals(5 * (324.0 / 3.6), mcf.getInputSlotRate(mcf.getInputs().indexOf(oxInput), false), 0.01);

        cfg.getSlot(0).setFuel(MCFFuel.CETANE_DIESEL);
        cfg.saveToNode(mcf);
        GTCombustionHelper.syncCombustionInputs(mcf);

        Map<MCFFuel, Double> dualFuelDemands = GTCombustionHelper.getMCFFuelDemandMbPerSec(mcf);
        assertEquals(2, dualFuelDemands.size());
        assertEquals(1638.4, dualFuelDemands.get(MCFFuel.HIGH_OCTANE_GASOLINE), 0.001);
        assertEquals(1820.444, dualFuelDemands.get(MCFFuel.CETANE_DIESEL), 0.01);

        cfg.clearAll();
        cfg.saveToNode(mcf);
        GTCombustionHelper.syncCombustionInputs(mcf);
        assertTrue(mcf.getInputs().isEmpty());
    }
}
