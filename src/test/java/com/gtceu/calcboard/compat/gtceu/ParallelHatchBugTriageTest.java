package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon;
import com.gtceu.calcboard.compat.gtceu.physics.GTPowerCalculator;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class ParallelHatchBugTriageTest {

    @Test
    public void testParallelHatchWithIV16AEnergyHatch() {
        ResourceLocation machineId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        MultiblockDetector.registerMultiblock(machineId);
        MultiblockDetector.registerParallelHatchController(machineId);

        RecipeNode node = RecipeNode.create(machineId, "Netherrack Dust", 100.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.IV);

        ResourceLocation parHatchId = ResourceLocation.tryParse("gtceu:ultimate_parallel_hatch");
        GTParallelHatchAddon parHatch = new GTParallelHatchAddon(
                parHatchId.toString(), "Ultimate Parallel Control Hatch", "64x Parallel", parHatchId, 64, false
        );
        node.getAddons().add(parHatch);

        ResourceLocation energyHatchId = ResourceLocation.tryParse("gtceu:iv_energy_input_hatch_16a");
        GTEnergyHatchAddon energyHatch = new GTEnergyHatchAddon(
                energyHatchId.toString(), "16A IV Energy Hatch", "Energy Hatch", energyHatchId,
                GTVoltageTier.IV, 16, false, false, false
        );
        node.getAddons().add(energyHatch);
        node.markOverclockDirty();

        System.out.println("DEBUG: node.getTotalParallel() = " + node.getTotalParallel());
        System.out.println("DEBUG: GTPowerCalculator.computeEffectiveParallel(node) = " + GTPowerCalculator.computeEffectiveParallel(node));
        System.out.println("DEBUG: node.getOverclockResult() = " + node.getOverclockResult());
        System.out.println("DEBUG: node.getSingleMachineEUt() = " + node.getSingleMachineEUt());

        Assertions.assertTrue(node.getTotalParallel() > 1, "Total parallel should be > 1");
        Assertions.assertEquals(64, node.getTotalParallel());
    }

    @Test
    public void testParallelHatchSelfHealingFromCustomParallelOne() {
        ResourceLocation machineId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        MultiblockDetector.registerMultiblock(machineId);
        MultiblockDetector.registerParallelHatchController(machineId);

        RecipeNode node = RecipeNode.create(machineId, "Netherrack Dust", 100.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.IV);

        ResourceLocation parHatchId = ResourceLocation.tryParse("gtceu:ultimate_parallel_hatch");
        GTParallelHatchAddon parHatch = new GTParallelHatchAddon(
                parHatchId.toString(), "Ultimate Parallel Control Hatch", "64x Parallel", parHatchId, 64, false
        );
        node.getAddons().add(parHatch);

        ResourceLocation energyHatchId = ResourceLocation.tryParse("gtceu:iv_energy_input_hatch_16a");
        GTEnergyHatchAddon energyHatch = new GTEnergyHatchAddon(
                energyHatchId.toString(), "16A IV Energy Hatch", "Energy Hatch", energyHatchId,
                GTVoltageTier.IV, 16, false, false, false
        );
        node.getAddons().add(energyHatch);

        node.setCustomParallel(1);
        Assertions.assertEquals(1, node.getCustomParallel());

        int totalPar = node.getTotalParallel();
        Assertions.assertEquals(64, totalPar);
        Assertions.assertEquals(0, node.getCustomParallel());
    }

    @Test
    public void testAddonLifecycleHandlerResetsCustomParallel() {
        ResourceLocation machineId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        RecipeNode node = RecipeNode.create(machineId, "Test Recipe", 100.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setCustomParallel(1);

        ResourceLocation parHatchId = ResourceLocation.tryParse("gtceu:advanced_parallel_hatch");
        GTParallelHatchAddon parHatch = new GTParallelHatchAddon(
                parHatchId.toString(), "Advanced Parallel Hatch", "16x Parallel", parHatchId, 16, false
        );

        com.gtceu.calcboard.compat.gtceu.handler.GTAddonLifecycleHandler.onAddonInstalled(node, parHatch);
        Assertions.assertEquals(0, node.getCustomParallel());
        Assertions.assertTrue(node.getAddons().contains(parHatch));
    }

    @Test
    public void testNodeMultiblockHelperResetsCustomParallel() {
        ResourceLocation machineId = ResourceLocation.tryParse("gtceu:chemical_reactor");
        RecipeNode node = RecipeNode.create(machineId, "Test Recipe", 100.0, 30.0, GTVoltageTier.LV);
        node.setCustomParallel(1);

        com.gtceu.calcboard.api.model.NodeMultiblockHelper.configureMultiblock(node, true);
        Assertions.assertEquals(0, node.getCustomParallel());

        node.setCustomParallel(1);
        com.gtceu.calcboard.api.model.NodeMultiblockHelper.configureMultiblock(node, false);
        Assertions.assertEquals(0, node.getCustomParallel());
    }

    @Test
    public void testNodeHardwareReconcilerClampParallelSelfHealing() {
        ResourceLocation machineId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        RecipeNode node = RecipeNode.create(machineId, "Test Recipe", 100.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);

        ResourceLocation parHatchId = ResourceLocation.tryParse("gtceu:ultimate_parallel_hatch");
        GTParallelHatchAddon parHatch = new GTParallelHatchAddon(
                parHatchId.toString(), "Ultimate Parallel Control Hatch", "64x Parallel", parHatchId, 64, false
        );
        node.getAddons().add(parHatch);
        node.setCustomParallel(1);

        com.gtceu.calcboard.api.model.NodeHardwareReconciler.clampParallel(node);
        Assertions.assertEquals(0, node.getCustomParallel());
        Assertions.assertEquals(64, node.getParallel());
    }
}
