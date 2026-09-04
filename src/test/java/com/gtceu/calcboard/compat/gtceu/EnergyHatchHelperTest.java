package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.helper.EnergyHatchHelper;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class EnergyHatchHelperTest {

    @Test
    void testStandardEnergyHatches() {
        ResourceLocation id = ResourceLocation.tryParse("gtceu:hv_energy_input_hatch");
        EnergyHatchHelper.EnergyHatchStats stats = EnergyHatchHelper.getEnergyHatchStats(id);

        Assertions.assertNotNull(stats);
        Assertions.assertEquals(GTVoltageTier.HV, stats.tier());
        Assertions.assertEquals(2, stats.amperage());
        Assertions.assertFalse(stats.isLaser());
        Assertions.assertFalse(stats.isSubstation());
    }

    @Test
    void testHighAmpEnergyHatches() {
        ResourceLocation id16a = ResourceLocation.tryParse("gtceu:ev_energy_input_hatch_16a");
        EnergyHatchHelper.EnergyHatchStats stats16a = EnergyHatchHelper.getEnergyHatchStats(id16a);

        Assertions.assertNotNull(stats16a);
        Assertions.assertEquals(GTVoltageTier.EV, stats16a.tier());
        Assertions.assertEquals(16, stats16a.amperage());
        Assertions.assertFalse(stats16a.isLaser());

        ResourceLocation id4a = ResourceLocation.tryParse("gtceu:iv_energy_input_hatch_4a");
        EnergyHatchHelper.EnergyHatchStats stats4a = EnergyHatchHelper.getEnergyHatchStats(id4a);

        Assertions.assertNotNull(stats4a);
        Assertions.assertEquals(GTVoltageTier.IV, stats4a.tier());
        Assertions.assertEquals(4, stats4a.amperage());
    }

    @Test
    void testSubstationHatches() {
        ResourceLocation id = ResourceLocation.tryParse("gtceu:iv_substation_input_hatch_64a");
        EnergyHatchHelper.EnergyHatchStats stats = EnergyHatchHelper.getEnergyHatchStats(id);

        Assertions.assertNotNull(stats);
        Assertions.assertEquals(GTVoltageTier.IV, stats.tier());
        Assertions.assertEquals(64, stats.amperage());
        Assertions.assertTrue(stats.isSubstation());
    }

    @Test
    void testLaserTargetHatches() {
        ResourceLocation id = ResourceLocation.tryParse("gtceu:zpm_laser_target_hatch_256a");
        EnergyHatchHelper.EnergyHatchStats stats = EnergyHatchHelper.getEnergyHatchStats(id);

        Assertions.assertNotNull(stats);
        Assertions.assertEquals(GTVoltageTier.ZPM, stats.tier());
        Assertions.assertEquals(256, stats.amperage());
        Assertions.assertTrue(stats.isLaser());
        Assertions.assertFalse(stats.isSubstation());
    }

    @Test
    void testStarTechnologyCustomHatches() {
        ResourceLocation id = ResourceLocation.tryParse("start_core:uiv_256a_dream_link_energy_hatch");
        EnergyHatchHelper.EnergyHatchStats stats = EnergyHatchHelper.getEnergyHatchStats(id);

        Assertions.assertNotNull(stats);
        Assertions.assertEquals(GTVoltageTier.UIV, stats.tier());
        Assertions.assertEquals(256, stats.amperage());
        Assertions.assertFalse(stats.isLaser());
    }

    @Test
    void testNonHatchFiltering() {
        ResourceLocation outputHatch = ResourceLocation.tryParse("gtceu:hv_energy_output_hatch");
        Assertions.assertNull(EnergyHatchHelper.getEnergyHatchStats(outputHatch));

        ResourceLocation dynamo = ResourceLocation.tryParse("gtceu:ev_dynamo_hatch");
        Assertions.assertNull(EnergyHatchHelper.getEnergyHatchStats(dynamo));

        ResourceLocation cable = ResourceLocation.tryParse("gtceu:tin_single_cable");
        Assertions.assertNull(EnergyHatchHelper.getEnergyHatchStats(cable));
    }

    @Test
    void testDualEnergyHatchTierOverclockAndPowerCapacity() {
        com.gtceu.calcboard.api.model.RecipeNode node = com.gtceu.calcboard.api.model.RecipeNode.create(
                ResourceLocation.tryParse("gtceu:electric_blast_furnace"),
                "Electric Blast Furnace",
                120.0,
                1920.0,
                GTVoltageTier.EV
        );
        node.setMultiblock(true);

        // 1. Install EV 2A hatch
        com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon h2a = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(
                "gtceu:ev_energy_input_hatch", "EV 2A Hatch", "2A EV",
                ResourceLocation.tryParse("gtceu:ev_energy_input_hatch"),
                GTVoltageTier.EV, 2, false, false, false
        );
        com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler.onAddonInstalled(node, h2a);

        // With single EV 2A hatch -> Target tier is EV, Capacity = 2 * 2048 = 4096 EU/t
        Assertions.assertEquals(GTVoltageTier.EV, node.getTargetTier());
        Assertions.assertEquals(4096L, com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler.getMaxEUtCapacity(node));

        // 2. Install EV 16A hatch as second hatch (Dual Hatch: 2A + 16A)
        com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon h16a = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(
                "gtceu:ev_energy_input_hatch_16a", "EV 16A Hatch", "16A EV",
                ResourceLocation.tryParse("gtceu:ev_energy_input_hatch_16a"),
                GTVoltageTier.EV, 16, false, false, false
        );
        com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler.onAddonInstalled(node, h16a);

        // With Dual EV Hatches (2A + 16A):
        // Physical Voltage Tier is strictly IV (EV + EV -> IV tier)
        Assertions.assertEquals(GTVoltageTier.IV, node.getTargetTier(), "Dual EV hatches must strictly have physical voltage tier IV");
        // Total capacity is (2 + 16) * 2048 = 18 * 2048 = 36,864 EU/t (LuV-level speed power budget)
        long expectedCapacity = (2L + 16L) * GTVoltageTier.EV.getVoltage();
        Assertions.assertEquals(expectedCapacity, com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler.getMaxEUtCapacity(node));
        Assertions.assertEquals(36864L, com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler.getMaxEUtCapacity(node));

        // 3. Verify that the node can perform overclocking beyond IV using the 36,864 EU/t LuV speed budget
        com.gtceu.calcboard.api.type.OverclockMode.OverclockResult oc = com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator.computeOverclock(node, node.getTargetTier(), false);
        Assertions.assertTrue(oc.eut() <= expectedCapacity, "Overclocked EU/t must not exceed total dual hatch power capacity");
        Assertions.assertTrue(oc.overclocks() >= 1, "Must perform speed overclocking using the 36,864 EU/t budget");
    }
}

