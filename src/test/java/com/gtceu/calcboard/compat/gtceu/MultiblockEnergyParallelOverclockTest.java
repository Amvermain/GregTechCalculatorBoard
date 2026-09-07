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
}
