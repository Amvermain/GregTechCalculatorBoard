package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class MultiblockEnergyParallelOverclockTest {

    @BeforeAll
    public static void setUp() {
        ResourceLocation destabilizer = ResourceLocation.tryParse("gtceu:molten_destabilizer");
        ResourceLocation lcr = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        MultiblockDetector.registerMultiblock(destabilizer);
        MultiblockDetector.registerParallelHatchController(destabilizer);
        MultiblockDetector.registerMultiblock(lcr);
        MultiblockDetector.registerParallelHatchController(lcr);
    }

    @Test
    public void testUV4AEnergyHatchLimits64xParallelTo8x() {
        ResourceLocation machineId = ResourceLocation.tryParse("gtceu:molten_destabilizer");
        // Recipe consuming 245,760 EU/t (0.47 A UV), 42.0s duration
        RecipeNode node = RecipeNode.create(machineId, "Molten Destabilizing", 840.0, 245760.0, GTVoltageTier.UV);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.UV);

        // Install 64x parallel hatch
        ResourceLocation parHatchId = ResourceLocation.tryParse("gtceu:luv_parallel_hatch");
        GTParallelHatchAddon parHatch = new GTParallelHatchAddon(
                parHatchId.toString(), "LuV Parallel Hatch", "64x Parallel", parHatchId, 64, false
        );
        node.getAddons().add(parHatch);

        // Install 4A UV Energy Hatch (4 * 524,288 = 2,097,152 EU/t)
        ResourceLocation energyHatchId = ResourceLocation.tryParse("gtceu:uv_energy_input_hatch_4a");
        GTEnergyHatchAddon energyHatch = new GTEnergyHatchAddon(
                energyHatchId.toString(), "4A UV Energy Hatch", "Energy Hatch", energyHatchId,
                GTVoltageTier.UV, 4, false, false, false
        );
        node.getAddons().add(energyHatch);
        node.markOverclockDirty();

        // Available capacity is 2,097,152 EU/t. Single recipe is 245,760 EU/t.
        // 2,097,152 / 245,760 = 8.53 -> max 8 parallels.
        Assertions.assertEquals(8, node.getTotalParallel(), "Parallel must be capped at 8x due to 4A UV energy hatch");
        Assertions.assertEquals(8, GTPowerCalculator.getMaxParallelCapacity(node), "Max capacity must report 8");

        // 8 parallels * 245,760 = 1,966,080 EU/t.
        // Next overclock would require 1,966,080 * 4 = 7,864,320 > 2,097,152, so 0 overclocks occur.
        Assertions.assertEquals(0, node.getOverclockResult().overclocks(), "Overclocks should be 0 because 8x batch consumes available energy");
        Assertions.assertEquals(42.0, node.getEffectiveDurationSeconds(), 1e-4, "Duration must remain 42.0s");
        Assertions.assertEquals(1966080.0, node.getSingleMachineEUt(), 1e-4, "Total consumption must be 1,966,080 EU/t");
    }

    @Test
    public void testLVRecipeWith64xParallelInEVMultiblockRunsAtLVSpeed() {
        ResourceLocation machineId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        // Recipe consuming 30 EU/t (LV), 5.0s duration (100 ticks)
        RecipeNode node = RecipeNode.create(machineId, "LV Recipe in EV Multiblock", 100.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.EV);

        // Install 64x parallel hatch
        ResourceLocation parHatchId = ResourceLocation.tryParse("gtceu:luv_parallel_hatch");
        GTParallelHatchAddon parHatch = new GTParallelHatchAddon(
                parHatchId.toString(), "LuV Parallel Hatch", "64x Parallel", parHatchId, 64, false
        );
        node.getAddons().add(parHatch);
        node.markOverclockDirty();

        // 64 parallels of 30 EU/t = 1,920 EU/t.
        // EV machine capacity = 2A EV = 3,920 EU/t (or 1,960 EU/t).
        // 1,920 <= 3,920 -> 64 parallels can run.
        Assertions.assertEquals(64, node.getTotalParallel(), "Should run 64 parallels");

        // Next overclock: 1,920 * 4 = 7,680 > 3,920 (exceeds EV capacity).
        // So 0 overclocks performed, running at LV base duration!
        Assertions.assertEquals(0, node.getOverclockResult().overclocks(), "0 overclocks should be performed");
        Assertions.assertEquals(5.0, node.getEffectiveDurationSeconds(), 1e-4, "Duration must remain LV speed (5.0s)");
        Assertions.assertEquals(1920.0, node.getSingleMachineEUt(), 1e-4, "Total consumption must be 1,920 EU/t (EV tier, not ZPM)");
    }

    @Test
    public void testLVRecipeWith4xParallelInEVMultiblockOverclocksTwice() {
        ResourceLocation machineId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        // Recipe consuming 30 EU/t (LV), 5.0s duration (100 ticks)
        RecipeNode node = RecipeNode.create(machineId, "LV Recipe with 4x Parallel", 100.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.EV);

        // Install 4x parallel hatch
        ResourceLocation parHatchId = ResourceLocation.tryParse("gtceu:ev_parallel_hatch");
        GTParallelHatchAddon parHatch = new GTParallelHatchAddon(
                parHatchId.toString(), "EV Parallel Hatch", "4x Parallel", parHatchId, 4, false
        );
        node.getAddons().add(parHatch);
        node.markOverclockDirty();

        // 4 parallels of 30 EU/t = 120 EU/t.
        // Step 1: 120 * 4 = 480 <= 3,920 (MV speed, 50 ticks)
        // Step 2: 480 * 4 = 1,920 <= 3,920 (HV speed, 25 ticks = 1.25s)
        // Step 3: 1,920 * 4 = 7,680 > 3,920 (exceeds EV capacity, stop)
        Assertions.assertEquals(4, node.getTotalParallel(), "Should run 4 parallels");
        Assertions.assertEquals(2, node.getOverclockResult().overclocks(), "Should overclock twice to HV");
        Assertions.assertEquals(1.25, node.getEffectiveDurationSeconds(), 1e-4, "Duration should be quartered (1.25s)");
        Assertions.assertEquals(1920.0, node.getSingleMachineEUt(), 1e-4, "Total consumption must be 1,920 EU/t");
    }

    @Test
    public void testLVRecipeInLVMultiblockWith2AEnergyHatchDoesNotParallel() {
        ResourceLocation machineId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        RecipeNode node = RecipeNode.create(machineId, "Ammonia Borane Dust", 80.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.LV);

        // Install 4x parallel hatch
        ResourceLocation parHatchId = ResourceLocation.tryParse("gtceu:ev_parallel_hatch");
        GTParallelHatchAddon parHatch = new GTParallelHatchAddon(
                parHatchId.toString(), "EV Parallel Hatch", "4x Parallel", parHatchId, 4, false
        );
        node.getAddons().add(parHatch);

        // Install standard 2A LV Energy Hatch (2A * 32V = 64 EU/t buffer, but 1A overclock voltage = 32V)
        ResourceLocation energyHatchId = ResourceLocation.tryParse("gtceu:lv_energy_input_hatch");
        GTEnergyHatchAddon energyHatch = new GTEnergyHatchAddon(
                energyHatchId.toString(), "LV Energy Hatch", "2A LV", energyHatchId,
                GTVoltageTier.LV, 2, false, false, false
        );
        node.getAddons().add(energyHatch);
        node.markOverclockDirty();

        // 32V / 30 EU/t = 1 parallel. Even though hatch buffer is 64 EU/t, GTCEu multiblock operates on 1A voltage (32V).
        Assertions.assertEquals(1, node.getTotalParallel(), "30 EU/t recipe must not parallel on 1A LV voltage");
        Assertions.assertEquals(1, GTPowerCalculator.getMaxParallelCapacity(node), "Max parallel capacity must be 1");
        Assertions.assertEquals(30.0, node.getSingleMachineEUt(), 1e-4, "Power consumption must remain 30.0 EU/t");
        Assertions.assertEquals(4.0, node.getEffectiveDurationSeconds(), 1e-4, "Duration must remain 4.0s");
    }

    @Test
    public void testLVRecipeInLVMultiblockWith4AEnergyHatchAllows4xParallel() {
        ResourceLocation machineId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        RecipeNode node = RecipeNode.create(machineId, "Ammonia Borane Dust", 80.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.LV);

        // Install 4x parallel hatch
        ResourceLocation parHatchId = ResourceLocation.tryParse("gtceu:ev_parallel_hatch");
        GTParallelHatchAddon parHatch = new GTParallelHatchAddon(
                parHatchId.toString(), "EV Parallel Hatch", "4x Parallel", parHatchId, 4, false
        );
        node.getAddons().add(parHatch);

        // Install 4A LV Energy Hatch (4A power-of-4 elevates overclock voltage to 1A MV = 128V)
        ResourceLocation energyHatchId = ResourceLocation.tryParse("gtceu:lv_energy_input_hatch_4a");
        GTEnergyHatchAddon energyHatch = new GTEnergyHatchAddon(
                energyHatchId.toString(), "4A LV Energy Hatch", "4A LV", energyHatchId,
                GTVoltageTier.LV, 4, false, false, false
        );
        node.getAddons().add(energyHatch);
        node.markOverclockDirty();

        // 128V / 30 EU/t = 4 parallels
        Assertions.assertEquals(4, node.getTotalParallel(), "4A hatch provides 128V effective overclock voltage for 4x parallel");
        Assertions.assertEquals(4, GTPowerCalculator.getMaxParallelCapacity(node), "Max parallel capacity must be 4");
        Assertions.assertEquals(120.0, node.getSingleMachineEUt(), 1e-4, "Total consumption must be 120.0 EU/t");
    }

    @Test
    public void testEnergyDiscountDoesNotIncreaseParallelAmount() {
        ResourceLocation machineId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        // Recipe consuming 500 EU/t (HV tier, 512V)
        RecipeNode node = RecipeNode.create(machineId, "HV Recipe with Coil Discount", 100.0, 500.0, GTVoltageTier.HV);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.HV);

        // Install 4x parallel hatch
        ResourceLocation parHatchId = ResourceLocation.tryParse("gtceu:ev_parallel_hatch");
        GTParallelHatchAddon parHatch = new GTParallelHatchAddon(
                parHatchId.toString(), "EV Parallel Hatch", "4x Parallel", parHatchId, 4, false
        );
        node.getAddons().add(parHatch);

        // Install standard 2A HV Energy Hatch (2A * 512V buffer, but 1A overclock voltage = 512V)
        ResourceLocation energyHatchId = ResourceLocation.tryParse("gtceu:hv_energy_input_hatch");
        GTEnergyHatchAddon energyHatch = new GTEnergyHatchAddon(
                energyHatchId.toString(), "HV Energy Hatch", "2A HV", energyHatchId,
                GTVoltageTier.HV, 2, false, false, false
        );
        node.getAddons().add(energyHatch);

        // Install custom addon with 50% EU/t discount (simulating coil discount)
        com.gtceu.calcboard.api.catalog.MachineAddon coilAddon = new com.gtceu.calcboard.api.catalog.MachineAddon(
                "custom:discount_coil", "Super Coil", com.gtceu.calcboard.api.catalog.MachineAddon.Category.COIL,
                "50% EU/t discount", ResourceLocation.tryParse("minecraft:iron_block")
        );
        coilAddon.setEutMultiplier(0.5);
        node.getAddons().add(coilAddon);
        node.markOverclockDirty();

        // Without discount: 512V / 500 EU/t = 1 parallel.
        // If discount were applied before parallel: 512V / 250 EU/t = 2 parallels (INCORRECT).
        // GTCEu runs PARALLEL_HATCH before discount, so parallels must remain 1.
        Assertions.assertEquals(1, node.getTotalParallel(), "Parallel must be calculated BEFORE coil discounts");
        Assertions.assertEquals(1, GTPowerCalculator.getMaxParallelCapacity(node), "Max parallel capacity must remain 1");
        Assertions.assertEquals(250.0, node.getSingleMachineEUt(), 1e-4, "Total power must reflect 50% discount on single parallel: 250 EU/t");
    }
}
