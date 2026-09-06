package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MachineAddonCatalog;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MinecraftBootstrapExtension.class)
public class CombustionAndGeneratorOverclockTest {

    private GTCEuModAdapter adapter;

    @BeforeEach
    public void setUp() {
        adapter = new GTCEuModAdapter();
        MachineAddonCatalog.getInstance().reset();
        MachineAddonCatalog.getInstance().refresh();
    }

    @Test
    @DisplayName("Test EBF Perfect Overclock from Excess Temperature (1800K per POC)")
    public void testEbfPerfectOverclockCalculation() {
        RecipeNode node = new RecipeNode("node-ebf", "EBF Node", 100.0, 120.0, GTVoltageTier.MV);
        node.setTargetTier(GTVoltageTier.IV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
        node.setRecipeTemperature(1200);

        CoilHelper.CoilStats stats = new CoilHelper.CoilStats(2700, 200, 80, 125, 80, 32);
        GTCoilAddon coil = new GTCoilAddon("gtceu:kanthal_coil", "Kanthal Coil", "2700K", null, stats);
        adapter.onAddonInstalled(node, coil);

        Assertions.assertEquals(1, node.getProperties().get(GTCEuProperties.EBF_PERFECT_OC_COUNT));

        OverclockMode.OverclockResult result = GTPowerCalculator.computeOverclock(node, GTVoltageTier.IV, false);
        Assertions.assertTrue(result.durationTicks() <= 30.0);
    }

    @Test
    @DisplayName("Test Simple Generator Parallel Scaling Without Duration Reduction")
    public void testSimpleGeneratorNoDurationReduction() {
        RecipeNode node = new RecipeNode("node-gen", "Generator Node", 8.0, -32.0, GTVoltageTier.MV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:combustion_generator"));
        node.setGenerator(true);

        Assertions.assertTrue(node.isGenerator());
        Assertions.assertEquals(8.0, node.getOverclockResult().durationTicks(), 0.001);

        int effectiveParallel = GTPowerCalculator.computeEffectiveParallel(node);
        Assertions.assertEquals(4, effectiveParallel);

        double power = GTPowerCalculator.computeSingleMachinePower(node);
        Assertions.assertEquals(128.0, power, 0.001);
    }

    @Test
    @DisplayName("Test Large Combustion Engine Oxygen Boosting")
    public void testLargeCombustionEngineOxygenBoost() {
        RecipeNode node = new RecipeNode("node-lce", "LCE Node", 20.0, -160.0, GTVoltageTier.EV);
        node.setMachineIcon(GTCombustionHelper.LARGE_COMBUSTION_ENGINE);
        adapter.onMachineIconChanged(node, null, GTCombustionHelper.LARGE_COMBUSTION_ENGINE);

        Assertions.assertTrue(node.isGenerator());
        int baseParallel = GTPowerCalculator.computeEffectiveParallel(node);
        Assertions.assertEquals(12, baseParallel);
        Assertions.assertEquals(1920.0, GTPowerCalculator.computeSingleMachinePower(node), 0.001);

        MachineAddon boostAddon = MachineAddonCatalog.getInstance().getAddon("gtceu:oxygen_boost");
        Assertions.assertNotNull(boostAddon);
        adapter.onAddonInstalled(node, boostAddon);

        Assertions.assertTrue(Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.OXYGEN_BOOST)));
        int boostedParallel = GTPowerCalculator.computeEffectiveParallel(node);
        Assertions.assertEquals(24, boostedParallel);
        Assertions.assertEquals(5760.0, GTPowerCalculator.computeSingleMachinePower(node), 0.001);

        adapter.onAddonRemoved(node, boostAddon);
        Assertions.assertFalse(Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.OXYGEN_BOOST)));
        Assertions.assertEquals(12, GTPowerCalculator.computeEffectiveParallel(node));
        Assertions.assertEquals(1920.0, GTPowerCalculator.computeSingleMachinePower(node), 0.001);
    }

    @Test
    @DisplayName("Test Extreme Combustion Engine Liquid Oxygen Boosting")
    public void testExtremeCombustionEngineLiquidOxygenBoost() {
        RecipeNode node = new RecipeNode("node-ece", "ECE Node", 20.0, -160.0, GTVoltageTier.IV);
        node.setMachineIcon(GTCombustionHelper.EXTREME_COMBUSTION_ENGINE);
        adapter.onMachineIconChanged(node, null, GTCombustionHelper.EXTREME_COMBUSTION_ENGINE);

        Assertions.assertTrue(node.isGenerator());
        int baseParallel = GTPowerCalculator.computeEffectiveParallel(node);
        Assertions.assertEquals(51, baseParallel);
        Assertions.assertEquals(8160.0, GTPowerCalculator.computeSingleMachinePower(node), 0.001);

        MachineAddon boostAddon = MachineAddonCatalog.getInstance().getAddon("gtceu:liquid_oxygen_boost");
        Assertions.assertNotNull(boostAddon);
        adapter.onAddonInstalled(node, boostAddon);

        Assertions.assertTrue(Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.LIQUID_OXYGEN_BOOST)));
        int boostedParallel = GTPowerCalculator.computeEffectiveParallel(node);
        Assertions.assertEquals(102, boostedParallel);
        Assertions.assertEquals(32640.0, GTPowerCalculator.computeSingleMachinePower(node), 0.001);
    }

    @Test
    @DisplayName("Test StarT Combustion Module Oxidizer Boosting")
    public void testStarTCombustionModuleBoosting() {
        RecipeNode t1Node = new RecipeNode("node-t1", "T1 Module", 20.0, -160.0, GTVoltageTier.LuV);
        t1Node.setMachineIcon(GTCombustionHelper.START_T1_COMBUSTION);
        adapter.onMachineIconChanged(t1Node, null, GTCombustionHelper.START_T1_COMBUSTION);

        Assertions.assertEquals(204, GTPowerCalculator.computeEffectiveParallel(t1Node));
        Assertions.assertEquals(32640.0, GTPowerCalculator.computeSingleMachinePower(t1Node), 0.001);

        MachineAddon t1Boost = MachineAddonCatalog.getInstance().getAddon("start_core:t1_oxidizer_boost");
        Assertions.assertNotNull(t1Boost);
        adapter.onAddonInstalled(t1Node, t1Boost);

        Assertions.assertEquals(408, GTPowerCalculator.computeEffectiveParallel(t1Node));
        Assertions.assertEquals(163200.0, GTPowerCalculator.computeSingleMachinePower(t1Node), 0.001);

        RecipeNode t2Node = new RecipeNode("node-t2", "T2 Module", 20.0, -160.0, GTVoltageTier.ZPM);
        t2Node.setMachineIcon(GTCombustionHelper.START_T2_COMBUSTION);
        adapter.onMachineIconChanged(t2Node, null, GTCombustionHelper.START_T2_COMBUSTION);

        Assertions.assertEquals(819, GTPowerCalculator.computeEffectiveParallel(t2Node));
        Assertions.assertEquals(131040.0, GTPowerCalculator.computeSingleMachinePower(t2Node), 0.001);

        MachineAddon t2Boost = MachineAddonCatalog.getInstance().getAddon("start_core:t2_oxidizer_boost");
        Assertions.assertNotNull(t2Boost);
        adapter.onAddonInstalled(t2Node, t2Boost);

        Assertions.assertEquals(1638, GTPowerCalculator.computeEffectiveParallel(t2Node));
        Assertions.assertEquals(786240.0, GTPowerCalculator.computeSingleMachinePower(t2Node), 0.001);
    }

    @Test
    @DisplayName("Test StarT Modular Combustion Frame Coolant Multipliers")
    public void testStarTModularCombustionFrameCoolant() {
        RecipeNode mcfNode = new RecipeNode("node-mcf", "MCF", 20.0, -160.0, GTVoltageTier.LuV);
        mcfNode.setMachineIcon(GTCombustionHelper.START_MCF);
        adapter.onMachineIconChanged(mcfNode, null, GTCombustionHelper.START_MCF);

        Assertions.assertEquals(0.9, GTCombustionHelper.getFrameCoolantMultiplier(mcfNode), 0.001);

        MachineAddon distWater = MachineAddonCatalog.getInstance().getAddon("start_core:distilled_water_coolant");
        Assertions.assertNotNull(distWater);
        adapter.onAddonInstalled(mcfNode, distWater);

        Assertions.assertEquals(1.2, GTCombustionHelper.getFrameCoolantMultiplier(mcfNode), 0.001);

        MachineAddon deionWater = MachineAddonCatalog.getInstance().getAddon("start_core:deionized_water_coolant");
        Assertions.assertNotNull(deionWater);
        adapter.onAddonInstalled(mcfNode, deionWater);

        Assertions.assertEquals(1.4, GTCombustionHelper.getFrameCoolantMultiplier(mcfNode), 0.001);

        adapter.onAddonRemoved(mcfNode, deionWater);
        Assertions.assertEquals(0.9, GTCombustionHelper.getFrameCoolantMultiplier(mcfNode), 0.001);
    }

    @Test
    @DisplayName("Test Singleblock Combustion Generator Power Capping (1A Rated Voltage Max)")
    public void testSingleblockCombustionGeneratorPowerCapping() {
        RecipeNode mvNode = new RecipeNode("node-cg-mv", "MV Combustion Generator", 8.0, -7680.0, GTVoltageTier.MV);
        mvNode.setMachineIcon(GTCombustionHelper.MV_COMBUSTION_GENERATOR);
        mvNode.setGenerator(true);
        mvNode.setMultiblock(false);

        Assertions.assertTrue(GTCombustionHelper.isCombustionFamily(mvNode));
        double power = GTPowerCalculator.computeSingleMachinePower(mvNode);
        Assertions.assertEquals(128.0, power, 0.001);

        RecipeNode hvNode = new RecipeNode("node-cg-hv", "HV Combustion Generator", 8.0, -7680.0, GTVoltageTier.HV);
        hvNode.setMachineIcon(GTCombustionHelper.HV_COMBUSTION_GENERATOR);
        hvNode.setGenerator(true);
        hvNode.setMultiblock(false);

        double hvPower = GTPowerCalculator.computeSingleMachinePower(hvNode);
        Assertions.assertEquals(512.0, hvPower, 0.001);
    }

    @Test
    @DisplayName("Test Combustion Generator Tier Progression Up and Down")
    public void testCombustionGeneratorTierProgressionUpAndDown() {
        RecipeNode node = new RecipeNode("node-cg-prog", "Combustion Generator", 8.0, -32.0, GTVoltageTier.LV);
        node.setMachineIcon(GTCombustionHelper.LV_COMBUSTION_GENERATOR);
        node.setGenerator(true);
        node.setMultiblock(false);

        com.gtceu.calcboard.client.gui.widget.NodeWidget widget = new com.gtceu.calcboard.client.gui.widget.NodeWidget(node, null);

        // LV -> MV
        Assertions.assertTrue(widget.changeTier(1));
        Assertions.assertEquals(GTVoltageTier.MV, node.getTargetTier());
        Assertions.assertEquals(GTCombustionHelper.MV_COMBUSTION_GENERATOR, node.getMachineIcon());
        Assertions.assertFalse(node.isMultiblock());

        // MV -> HV
        Assertions.assertTrue(widget.changeTier(1));
        Assertions.assertEquals(GTVoltageTier.HV, node.getTargetTier());
        Assertions.assertEquals(GTCombustionHelper.HV_COMBUSTION_GENERATOR, node.getMachineIcon());
        Assertions.assertFalse(node.isMultiblock());

        // HV -> EV (Auto-promote to Large Combustion Engine multiblock!)
        Assertions.assertTrue(widget.changeTier(1));
        Assertions.assertEquals(GTVoltageTier.EV, node.getTargetTier());
        Assertions.assertEquals(GTCombustionHelper.LARGE_COMBUSTION_ENGINE, node.getMachineIcon());
        Assertions.assertTrue(node.isMultiblock());

        // EV -> IV (Auto-promote to Extreme Combustion Engine multiblock!)
        Assertions.assertTrue(widget.changeTier(1));
        Assertions.assertEquals(GTVoltageTier.IV, node.getTargetTier());
        Assertions.assertEquals(GTCombustionHelper.EXTREME_COMBUSTION_ENGINE, node.getMachineIcon());
        Assertions.assertTrue(node.isMultiblock());

        // IV -> EV (Step down)
        Assertions.assertTrue(widget.changeTier(-1));
        Assertions.assertEquals(GTVoltageTier.EV, node.getTargetTier());
        Assertions.assertEquals(GTCombustionHelper.LARGE_COMBUSTION_ENGINE, node.getMachineIcon());
        Assertions.assertTrue(node.isMultiblock());

        // EV -> HV (Auto-demote to HV Combustion Generator singleblock!)
        Assertions.assertTrue(widget.changeTier(-1));
        Assertions.assertEquals(GTVoltageTier.HV, node.getTargetTier());
        Assertions.assertEquals(GTCombustionHelper.HV_COMBUSTION_GENERATOR, node.getMachineIcon());
        Assertions.assertFalse(node.isMultiblock());

        // HV -> MV
        Assertions.assertTrue(widget.changeTier(-1));
        Assertions.assertEquals(GTVoltageTier.MV, node.getTargetTier());
        Assertions.assertEquals(GTCombustionHelper.MV_COMBUSTION_GENERATOR, node.getMachineIcon());
        Assertions.assertFalse(node.isMultiblock());

        // MV -> LV
        Assertions.assertTrue(widget.changeTier(-1));
        Assertions.assertEquals(GTVoltageTier.LV, node.getTargetTier());
        Assertions.assertEquals(GTCombustionHelper.LV_COMBUSTION_GENERATOR, node.getMachineIcon());
        Assertions.assertFalse(node.isMultiblock());

        // LV -> lower (Should not step below LV)
        Assertions.assertFalse(widget.changeTier(-1));
        Assertions.assertEquals(GTVoltageTier.LV, node.getTargetTier());
    }

    @Test
    @DisplayName("Test High Octane Gasoline (HOG) Fuel Rates Across All Combustion Tiers")
    public void testHOGCombustionFuelConsumptionRates() {
        // HOG: 1 mB, 100 ticks (5s, 0.2/s), 32 EU/t
        com.gtceu.calcboard.api.model.IngredientStack hogInput = com.gtceu.calcboard.api.model.IngredientStack.fluid(
                ResourceLocation.tryParse("gtceu:high_octane_gasoline"), "High Octane Gasoline", 1.0
        );

        // LV
        RecipeNode lv = new RecipeNode("n-lv", "LV Gen", 100.0, -32.0, GTVoltageTier.LV);
        lv.setMachineIcon(GTCombustionHelper.LV_COMBUSTION_GENERATOR);
        lv.setGenerator(true);
        lv.getInputs().add(hogInput);
        Assertions.assertEquals(1, GTPowerCalculator.computeEffectiveParallel(lv));
        Assertions.assertEquals(32.0, GTPowerCalculator.computeSingleMachinePower(lv), 0.001);
        Assertions.assertEquals(0.01, lv.getInputSlotRate(0, false) / 20.0, 0.0001); // mB/t

        // MV
        RecipeNode mv = new RecipeNode("n-mv", "MV Gen", 100.0, -32.0, GTVoltageTier.MV);
        mv.setMachineIcon(GTCombustionHelper.MV_COMBUSTION_GENERATOR);
        mv.setGenerator(true);
        mv.getInputs().add(hogInput);
        Assertions.assertEquals(4, GTPowerCalculator.computeEffectiveParallel(mv));
        Assertions.assertEquals(128.0, GTPowerCalculator.computeSingleMachinePower(mv), 0.001);
        Assertions.assertEquals(0.04, mv.getInputSlotRate(0, false) / 20.0, 0.0001); // mB/t

        // HV
        RecipeNode hv = new RecipeNode("n-hv", "HV Gen", 100.0, -32.0, GTVoltageTier.HV);
        hv.setMachineIcon(GTCombustionHelper.HV_COMBUSTION_GENERATOR);
        hv.setGenerator(true);
        hv.getInputs().add(hogInput);
        Assertions.assertEquals(16, GTPowerCalculator.computeEffectiveParallel(hv));
        Assertions.assertEquals(512.0, GTPowerCalculator.computeSingleMachinePower(hv), 0.001);
        Assertions.assertEquals(0.16, hv.getInputSlotRate(0, false) / 20.0, 0.0001); // mB/t

        // EV (Large Combustion Engine)
        RecipeNode ev = new RecipeNode("n-ev", "LCE", 100.0, -32.0, GTVoltageTier.EV);
        ev.setMachineIcon(GTCombustionHelper.LARGE_COMBUSTION_ENGINE);
        ev.setGenerator(true);
        ev.setMultiblock(true);
        ev.getInputs().add(hogInput);
        Assertions.assertEquals(64, GTPowerCalculator.computeEffectiveParallel(ev));
        Assertions.assertEquals(2048.0, GTPowerCalculator.computeSingleMachinePower(ev), 0.001);
        Assertions.assertEquals(0.64, ev.getInputSlotRate(0, false) / 20.0, 0.0001); // mB/t

        // IV (Extreme Combustion Engine)
        RecipeNode iv = new RecipeNode("n-iv", "ECE", 100.0, -32.0, GTVoltageTier.IV);
        iv.setMachineIcon(GTCombustionHelper.EXTREME_COMBUSTION_ENGINE);
        iv.setGenerator(true);
        iv.setMultiblock(true);
        iv.getInputs().add(hogInput);
        Assertions.assertEquals(256, GTPowerCalculator.computeEffectiveParallel(iv));
        Assertions.assertEquals(8192.0, GTPowerCalculator.computeSingleMachinePower(iv), 0.001);
        Assertions.assertEquals(2.56, iv.getInputSlotRate(0, false) / 20.0, 0.0001); // mB/t

        // LuV (Unreal Combustion Module)
        RecipeNode luv = new RecipeNode("n-luv", "UCM", 100.0, -32.0, GTVoltageTier.LuV);
        luv.setMachineIcon(GTCombustionHelper.START_T1_COMBUSTION);
        luv.setGenerator(true);
        luv.setMultiblock(true);
        luv.getInputs().add(hogInput);
        Assertions.assertEquals(1024, GTPowerCalculator.computeEffectiveParallel(luv));
        Assertions.assertEquals(32768.0, GTPowerCalculator.computeSingleMachinePower(luv), 0.001);
        Assertions.assertEquals(10.24, luv.getInputSlotRate(0, false) / 20.0, 0.0001); // mB/t

        // ZPM (Supreme Combustion Module)
        RecipeNode zpm = new RecipeNode("n-zpm", "SCM", 100.0, -32.0, GTVoltageTier.ZPM);
        zpm.setMachineIcon(GTCombustionHelper.START_T2_COMBUSTION);
        zpm.setGenerator(true);
        zpm.setMultiblock(true);
        zpm.getInputs().add(hogInput);
        Assertions.assertEquals(4096, GTPowerCalculator.computeEffectiveParallel(zpm));
        Assertions.assertEquals(131072.0, GTPowerCalculator.computeSingleMachinePower(zpm), 0.001);
        Assertions.assertEquals(40.96, zpm.getInputSlotRate(0, false) / 20.0, 0.0001); // mB/t
    }

    @Test
    @DisplayName("Test Combustion Generator Tier Limits Cap at ZPM (No Roll to Rocket Module)")
    public void testCombustionGeneratorMaxTierCapAtZpm() {
        Assertions.assertNull(GTCombustionHelper.getCombustionMachineForTier(GTVoltageTier.UV));
        Assertions.assertNull(GTCombustionHelper.getCombustionMachineForTier(GTVoltageTier.UEV));

        RecipeNode zpmNode = new RecipeNode("node-zpm", "SCM", 100.0, -32.0, GTVoltageTier.ZPM);
        zpmNode.setMachineIcon(GTCombustionHelper.START_T2_COMBUSTION);
        zpmNode.setGenerator(true);

        boolean syncedToUv = GTCombustionHelper.syncCombustionMachine(zpmNode, GTVoltageTier.UV);
        Assertions.assertFalse(syncedToUv);
        Assertions.assertEquals(GTCombustionHelper.START_T2_COMBUSTION, zpmNode.getMachineIcon());
        Assertions.assertEquals(GTVoltageTier.ZPM, zpmNode.getTargetTier());
    }

    @Test
    @DisplayName("Test StarT Combustion Module MCF Coolant Boosting Compatibility")
    public void testStarTCombustionModuleCoolantAddonCompatibility() {
        RecipeNode t1Node = new RecipeNode("node-t1-coolant", "T1 Module", 100.0, -32.0, GTVoltageTier.LuV);
        t1Node.setMachineIcon(GTCombustionHelper.START_T1_COMBUSTION);
        adapter.onMachineIconChanged(t1Node, null, GTCombustionHelper.START_T1_COMBUSTION);

        MachineAddon distWater = MachineAddonCatalog.getInstance().getAddon("start_core:distilled_water_coolant");
        MachineAddon deionWater = MachineAddonCatalog.getInstance().getAddon("start_core:deionized_water_coolant");

        Assertions.assertNotNull(distWater);
        Assertions.assertNotNull(deionWater);

        Assertions.assertTrue(adapter.isAddonCompatible(t1Node, distWater));
        Assertions.assertTrue(adapter.isAddonCompatible(t1Node, deionWater));

        adapter.onAddonInstalled(t1Node, deionWater);
        Assertions.assertEquals("deionized_water", t1Node.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE));
        Assertions.assertEquals(1.4, GTCombustionHelper.getCombustionPowerMultiplier(t1Node), 0.001);
        Assertions.assertEquals(32768.0 * 1.4, GTPowerCalculator.computeSingleMachinePower(t1Node), 0.001);

        adapter.onAddonRemoved(t1Node, deionWater);
        Assertions.assertEquals("none", t1Node.getProperties().get(GTCEuProperties.COMBUSTION_COOLANT_TYPE));
        Assertions.assertEquals(1.0, GTCombustionHelper.getCombustionPowerMultiplier(t1Node), 0.001);
    }

    @Test
    @DisplayName("Test Combustion Generator Exact In-Game Machine IDs and Legacy Migration")
    public void testCombustionGeneratorExactMachineIdsAndLegacyMigration() {
        Assertions.assertEquals("gtceu:lv_combustion", GTCombustionHelper.LV_COMBUSTION.toString());
        Assertions.assertEquals("gtceu:mv_combustion", GTCombustionHelper.MV_COMBUSTION.toString());
        Assertions.assertEquals("gtceu:hv_combustion", GTCombustionHelper.HV_COMBUSTION.toString());

        RecipeNode node = new RecipeNode("legacy-node", "Legacy Combustion", 100.0, -32.0, GTVoltageTier.LV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_combustion_generator"));

        Assertions.assertTrue(GTCombustionHelper.isCombustionFamily(node));
        Assertions.assertEquals(GTVoltageTier.LV, GTCombustionHelper.getCombustionTierForMachine(node.getMachineIcon()));

        adapter.onMachineIconChanged(node, null, node.getMachineIcon());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:lv_combustion"), node.getMachineIcon());

        boolean synced = GTCombustionHelper.syncCombustionMachine(node, GTVoltageTier.MV);
        Assertions.assertTrue(synced);
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:mv_combustion"), node.getMachineIcon());
    }

    @Test
    @DisplayName("Test Singleblock Combustion Generator Rejects Boosts and Multiblock Traits")
    public void testSingleblockCombustionGeneratorRejectsBoostAndTraits() {
        RecipeNode lvNode = new RecipeNode("lv-comb", "Basic Combustion", 100.0, -32.0, GTVoltageTier.LV);
        lvNode.setMachineIcon(GTCombustionHelper.LV_COMBUSTION);
        lvNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:combustion_generator"));
        lvNode.getAvailableWorkstations().add(GTCombustionHelper.LV_COMBUSTION);
        lvNode.getAvailableWorkstations().add(GTCombustionHelper.LARGE_COMBUSTION_ENGINE);
        lvNode.getAvailableWorkstations().add(GTCombustionHelper.EXTREME_COMBUSTION_ENGINE);
        adapter.onMachineIconChanged(lvNode, null, GTCombustionHelper.LV_COMBUSTION);

        Assertions.assertFalse(lvNode.isMultiblock());
        Assertions.assertFalse(adapter.supportsAddons(lvNode));

        var applicableCats = adapter.getApplicableAddonCategories(lvNode);
        Assertions.assertFalse(applicableCats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.MULTIBLOCK_TRAIT));
        Assertions.assertFalse(applicableCats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.MAINTENANCE));
        Assertions.assertFalse(applicableCats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.HATCH_BUS));
        Assertions.assertTrue(applicableCats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.CUSTOM));

        MachineAddon oxygenBoost = MachineAddonCatalog.getInstance().getAddon("gtceu:oxygen_boost");
        MachineAddon liquidOxygenBoost = MachineAddonCatalog.getInstance().getAddon("gtceu:liquid_oxygen_boost");
        Assertions.assertNotNull(oxygenBoost);
        Assertions.assertNotNull(liquidOxygenBoost);

        Assertions.assertFalse(adapter.isAddonCompatible(lvNode, oxygenBoost));
        Assertions.assertFalse(adapter.isAddonCompatible(lvNode, liquidOxygenBoost));

        RecipeNode lceNode = new RecipeNode("lce-node", "Large Combustion Engine", 100.0, -160.0, GTVoltageTier.EV);
        lceNode.setMachineIcon(GTCombustionHelper.LARGE_COMBUSTION_ENGINE);
        lceNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:combustion_generator"));
        adapter.onMachineIconChanged(lceNode, null, GTCombustionHelper.LARGE_COMBUSTION_ENGINE);

        Assertions.assertTrue(lceNode.isMultiblock());
        Assertions.assertTrue(adapter.supportsAddons(lceNode));
        Assertions.assertTrue(adapter.isAddonCompatible(lceNode, oxygenBoost));
        Assertions.assertFalse(adapter.isAddonCompatible(lceNode, liquidOxygenBoost));

        RecipeNode eceNode = new RecipeNode("ece-node", "Extreme Combustion Engine", 100.0, -160.0, GTVoltageTier.IV);
        eceNode.setMachineIcon(GTCombustionHelper.EXTREME_COMBUSTION_ENGINE);
        eceNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:combustion_generator"));
        adapter.onMachineIconChanged(eceNode, null, GTCombustionHelper.EXTREME_COMBUSTION_ENGINE);

        Assertions.assertTrue(eceNode.isMultiblock());
        Assertions.assertTrue(adapter.supportsAddons(eceNode));
        Assertions.assertFalse(adapter.isAddonCompatible(eceNode, oxygenBoost));
        Assertions.assertTrue(adapter.isAddonCompatible(eceNode, liquidOxygenBoost));
    }
}
