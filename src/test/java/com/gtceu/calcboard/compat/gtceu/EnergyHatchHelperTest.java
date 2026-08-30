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
}

