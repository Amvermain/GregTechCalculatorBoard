package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import com.gtceu.calcboard.compat.gtceu.handler.GTAddonCompatibilityHandler;
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

        ResourceLocation energyHatchId = ResourceLocation.tryParse("gtceu:ev_energy_input_hatch_2a");
        GTEnergyHatchAddon energyHatch = new GTEnergyHatchAddon(
                energyHatchId.toString(), "2A EV Energy Hatch", "Energy Hatch", energyHatchId,
                GTVoltageTier.EV, 2, false, false, false
        );
        node.getAddons().add(energyHatch);
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

        ResourceLocation energyHatchId = ResourceLocation.tryParse("gtceu:ev_energy_input_hatch_2a");
        GTEnergyHatchAddon energyHatch = new GTEnergyHatchAddon(
                energyHatchId.toString(), "2A EV Energy Hatch", "Energy Hatch", energyHatchId,
                GTVoltageTier.EV, 2, false, false, false
        );
        node.getAddons().add(energyHatch);
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

    @Test
    public void testUltraBarrelDurationAndBatching() {
        ResourceLocation ultraBarrelId = ResourceLocation.tryParse("gtceu:ultra_barrel");
        MultiblockDetector.registerMultiblock(ultraBarrelId);
        MultiblockDetector.registerParallelHatchController(ultraBarrelId);
        MultiblockDetector.registerThroughputBoostingMultiblock(ultraBarrelId);
        MultiblockDetector.registerBulkProcessingMultiblock(ultraBarrelId);
        MultiblockDetector.registerBatchModeMultiblock(ultraBarrelId);

        RecipeNode node = RecipeNode.create(ultraBarrelId, "Magmatic Industrial Barrel", 64.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.UEV);

        // 1024x UEV parallel hatch
        ResourceLocation phId = ResourceLocation.tryParse("gtceu:uev_parallel_hatch");
        GTParallelHatchAddon ph = new GTParallelHatchAddon(phId.toString(), "UEV Parallel Hatch", "1024x", phId, 1024, false);
        node.getAddons().add(ph);

        // Throughput boosting
        MachineAddon tpb = new MachineAddon("gtceu:throughput_boosting", "Throughput Boosting", MachineAddon.Category.MULTIBLOCK_TRAIT, "", null);
        tpb.setParallelMultiplier(4);
        tpb.setDurationMultiplier(1.6);
        tpb.setEutMultiplier(0.95);
        tpb.setPowerConstant(true);
        node.getAddons().add(tpb);

        // Bulk processing 4:3.25
        MachineAddon bulk4 = new MachineAddon("gtceu:bulk_processing_4_3", "Bulk Processing 4:3.25", MachineAddon.Category.MULTIBLOCK_TRAIT, "", null);
        bulk4.setParallelMultiplier(4);
        bulk4.setDurationMultiplier(3.25);
        bulk4.setEutMultiplier(1.0);
        bulk4.setPowerConstant(true);
        node.getAddons().add(bulk4);

        // UEV 16A Energy Hatch (134,217,728 EU/t capacity to power 1024 parallels at 119,537,664 EU/t)
        ResourceLocation ehId = ResourceLocation.tryParse("gtceu:uev_energy_input_hatch_16a");
        GTEnergyHatchAddon eh = new GTEnergyHatchAddon(ehId.toString(), "UEV 16A Energy Hatch", "16A", ehId, GTVoltageTier.UEV, 16);
        node.getAddons().add(eh);

        node.getOutputs().add(com.gtceu.calcboard.api.model.IngredientStack.item(
                ResourceLocation.tryParse("exnihilosequentia:andesite_pebble"), "Andesite Pebble", 64.0, 1.0));

        node.markOverclockDirty();
        var ocNoBatch = GTPowerCalculator.computeOverclock(node, GTVoltageTier.UEV, false);
        Assertions.assertEquals(3.0, ocNoBatch.durationTicks(), 1e-4, "Base 1 tick + 1.6x Throughput (1 tick) + 3.25x Bulking (3 ticks) must equal 3.0 ticks");
        Assertions.assertEquals(16384, GTPowerCalculator.computeEffectiveParallel(node), "1024 parallel hatch * 4x throughput * 4x bulking must equal 16,384");
        Assertions.assertEquals(1024, GTPowerCalculator.computePowerEffectiveParallel(node), "Power parallel must only count power-consuming parallel: 1024");
        Assertions.assertEquals(0.15, node.getEffectiveDurationSeconds(), 1e-4, "3 ticks is 0.15 seconds");
        double rateNoBatch = node.calculateSingleMachineOutputRate(node.getOutputs().get(0));
        Assertions.assertEquals(6990506.67, rateNoBatch, 1.0, "Production rate must equal 6,990,506.67 items/s");

        MachineAddon batch = new MachineAddon("gtceu:batch_processing", "Batch Mode", MachineAddon.Category.MULTIBLOCK_TRAIT, "", null);
        node.getAddons().add(batch);
        node.markOverclockDirty();

        var ocWithBatch = GTPowerCalculator.computeOverclock(node, GTVoltageTier.UEV, false);
        Assertions.assertEquals(99.0, ocWithBatch.durationTicks(), 1e-4, "3.0 ticks * 33 batch multiplier must equal 99.0 ticks");
        Assertions.assertEquals(4.95, node.getEffectiveDurationSeconds(), 1e-4, "99 ticks is 4.95 seconds");
        Assertions.assertEquals(540672, GTPowerCalculator.computeEffectiveParallel(node), "16,384 * 33 batching must equal 540,672 parallels");
        Assertions.assertEquals(1024, GTPowerCalculator.computePowerEffectiveParallel(node), "Power parallel must remain 1024 without EU cost");
        double rateWithBatch = node.calculateSingleMachineOutputRate(node.getOutputs().get(0));
        Assertions.assertEquals(6990506.67, rateWithBatch, 1.0, "Rate with batching must stay identical: 6,990,506.67 items/s");
    }

    @Test
    public void testLargeCentrifuge16xParallelWith2AHVEnergyHatchDoesNotOverclockIntoSecondAmp() {
        ResourceLocation centrifugeId = ResourceLocation.tryParse("gtceu:large_centrifuge");
        MultiblockDetector.registerMultiblock(centrifugeId);
        MultiblockDetector.registerParallelHatchController(centrifugeId);

        RecipeNode node = RecipeNode.create(centrifugeId, "Sea Water Centrifuging", 50.0, 15.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.HV);

        ResourceLocation parHatchId = ResourceLocation.tryParse("gtceu:advanced_parallel_hatch");
        GTParallelHatchAddon parHatch = new GTParallelHatchAddon(
                parHatchId.toString(), "Advanced Parallel Hatch", "16x Parallel", parHatchId, 16, false
        );
        node.getAddons().add(parHatch);

        ResourceLocation energyHatchId = ResourceLocation.tryParse("gtceu:hv_energy_input_hatch_2a");
        GTEnergyHatchAddon energyHatch = new GTEnergyHatchAddon(
                energyHatchId.toString(), "2A HV Energy Hatch", "2A HV", energyHatchId,
                GTVoltageTier.HV, 2, false, false, false
        );
        node.getAddons().add(energyHatch);

        node.getOutputs().add(com.gtceu.calcboard.api.model.IngredientStack.item(
                ResourceLocation.tryParse("minecraft:sand"), "Mineral Sand", 1.0, 1.0));

        node.markOverclockDirty();

        Assertions.assertEquals(16, node.getTotalParallel(), "Should run 16 parallels");
        Assertions.assertEquals(0, node.getOverclockResult().overclocks(), "Overclocks must be 0 because 960 EU/t exceeds 1A HV limit (512 EU/t)");
        Assertions.assertEquals(2.50, node.getEffectiveDurationSeconds(), 1e-4, "Duration must remain 2.50s");
        Assertions.assertEquals(240.0, node.getSingleMachineEUt(), 1e-4, "Total consumption must be 240.0 EU/t (0.47A HV), not 960.0 EU/t (1.9A HV)");

        double rate = node.calculateSingleMachineOutputRate(node.getOutputs().get(0));
        Assertions.assertEquals(6.4, rate, 1e-4, "Output rate must be 6.4/s (matching 16x 1A LV centrifuges), not 12.8/s");
    }
}

