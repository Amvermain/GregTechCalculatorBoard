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
        Assertions.assertEquals(48, baseParallel);
        Assertions.assertEquals(7680.0, GTPowerCalculator.computeSingleMachinePower(node), 0.001);

        MachineAddon boostAddon = MachineAddonCatalog.getInstance().getAddon("gtceu:liquid_oxygen_boost");
        Assertions.assertNotNull(boostAddon);
        adapter.onAddonInstalled(node, boostAddon);

        Assertions.assertTrue(Boolean.TRUE.equals(node.getProperties().get(GTCEuProperties.LIQUID_OXYGEN_BOOST)));
        int boostedParallel = GTPowerCalculator.computeEffectiveParallel(node);
        Assertions.assertEquals(96, boostedParallel);
        Assertions.assertEquals(30720.0, GTPowerCalculator.computeSingleMachinePower(node), 0.001);
    }

    @Test
    @DisplayName("Test StarT Combustion Module Oxidizer Boosting")
    public void testStarTCombustionModuleBoosting() {
        RecipeNode t1Node = new RecipeNode("node-t1", "T1 Module", 20.0, -160.0, GTVoltageTier.LuV);
        t1Node.setMachineIcon(GTCombustionHelper.START_T1_COMBUSTION);
        adapter.onMachineIconChanged(t1Node, null, GTCombustionHelper.START_T1_COMBUSTION);

        Assertions.assertEquals(30720.0, GTPowerCalculator.computeSingleMachinePower(t1Node), 0.001);

        MachineAddon t1Boost = MachineAddonCatalog.getInstance().getAddon("start_core:t1_oxidizer_boost");
        Assertions.assertNotNull(t1Boost);
        adapter.onAddonInstalled(t1Node, t1Boost);

        Assertions.assertEquals(384, GTPowerCalculator.computeEffectiveParallel(t1Node));
        Assertions.assertEquals(153600.0, GTPowerCalculator.computeSingleMachinePower(t1Node), 0.001);

        RecipeNode t2Node = new RecipeNode("node-t2", "T2 Module", 20.0, -160.0, GTVoltageTier.ZPM);
        t2Node.setMachineIcon(GTCombustionHelper.START_T2_COMBUSTION);
        adapter.onMachineIconChanged(t2Node, null, GTCombustionHelper.START_T2_COMBUSTION);

        MachineAddon t2Boost = MachineAddonCatalog.getInstance().getAddon("start_core:t2_oxidizer_boost");
        Assertions.assertNotNull(t2Boost);
        adapter.onAddonInstalled(t2Node, t2Boost);

        Assertions.assertEquals(1536, GTPowerCalculator.computeEffectiveParallel(t2Node));
        Assertions.assertEquals(737280.0, GTPowerCalculator.computeSingleMachinePower(t2Node), 0.001);
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
}
