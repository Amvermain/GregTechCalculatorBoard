package com.gtceu.calcboard;

import com.gtceu.calcboard.api.GTThreadingHelix;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.NodeThreadingConfig;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.compat.ModAdapterRegistry;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class GTCEuThreadingTest {

    @Test
    public void testHelixStatAccumulation() {
        NodeThreadingConfig cfg = new NodeThreadingConfig();

        // 2x UEV Supreme: 2 * (+20 Gen) = +40 Gen
        cfg.addHelixCount(GTThreadingHelix.UEV_SUPREME, 2);

        // 1x UHV Overdrive: +4 Gen, +12 Spd, +4 Eff
        cfg.addHelixCount(GTThreadingHelix.UHV_OVERDRIVE, 1);

        // 1x UHV Co-Processor: +3 Gen, +5 Spd, +2 Eff, +10 Par
        cfg.addHelixCount(GTThreadingHelix.UHV_COPROCESSOR, 1);

        // 1x UHV Weaving: +3 Gen, +2 Spd, +5 Eff, +10 Thrd
        cfg.addHelixCount(GTThreadingHelix.UHV_WEAVING, 1);

        Assertions.assertEquals(50, cfg.getBaseGeneral()); // 40 + 4 + 3 + 3 = 50
        Assertions.assertEquals(19, cfg.getBaseSpeed());   // 12 + 5 + 2 = 19
        Assertions.assertEquals(11, cfg.getBaseEfficiency()); // 4 + 2 + 5 = 11
        Assertions.assertEquals(10, cfg.getBaseParallels());  // 10
        Assertions.assertEquals(10, cfg.getBaseThreading());  // 10
        Assertions.assertEquals(50, cfg.getRemainingGeneral());
    }

    @Test
    public void testGeneralisPointAllocationAndClamping() {
        NodeThreadingConfig cfg = new NodeThreadingConfig();
        cfg.setHelixCount(GTThreadingHelix.MAX_SUPREME, 1); // +60 Gen

        Assertions.assertEquals(60, cfg.getBaseGeneral());
        Assertions.assertEquals(60, cfg.getRemainingGeneral());

        // Allocate 30 to Eff, 20 to Par, 10 to Spd (Total 60)
        cfg.setAssignedEfficiency(30);
        cfg.setAssignedParallels(20);
        cfg.setAssignedSpeed(10);

        Assertions.assertEquals(0, cfg.getRemainingGeneral());
        Assertions.assertEquals(30, cfg.getTotalEfficiency());
        Assertions.assertEquals(20, cfg.getTotalParallels());
        Assertions.assertEquals(10, cfg.getTotalSpeed());

        // Remove MAX Supreme -> Gen becomes 0, assignments clamped to 0
        cfg.setHelixCount(GTThreadingHelix.MAX_SUPREME, 0);
        Assertions.assertEquals(0, cfg.getBaseGeneral());
        Assertions.assertEquals(0, cfg.getTotalAssignedGeneral());
        Assertions.assertEquals(0, cfg.getAssignedEfficiency());
        Assertions.assertEquals(0, cfg.getAssignedParallels());
        Assertions.assertEquals(0, cfg.getAssignedSpeed());
    }

    @Test
    public void testThreadingOverclockAndPowerScaling() {
        RecipeNode node = RecipeNode.create("Multithreaded Component Synthesis Forge", 100.0, 1000.0, GTVoltageTier.UIV);
        node.setMultiblock(true);
        node.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:multithreaded_component_synthesis_forge"));

        NodeThreadingConfig cfg = node.getThreadingConfig();
        // Add 3x MAX Supreme (+180 Generalis)
        cfg.setHelixCount(GTThreadingHelix.MAX_SUPREME, 3);

        // 1. 100 Velocitas Points: duration * 0.5 (50% reduction)
        cfg.setAssignedSpeed(100);
        Assertions.assertEquals(0.5, cfg.calculateDurationMultiplier(), 0.001);

        // 2. 30 Efficienta Points: energy * 0.5 (50% reduction)
        cfg.setAssignedEfficiency(30);
        Assertions.assertEquals(0.5, cfg.calculateEnergyMultiplier(), 0.001);

        // 3. 20 Parallelismus Points: 2 Parallels, duration * sqrt(2)
        cfg.setAssignedParallels(20);
        Assertions.assertEquals(2, cfg.getEffectiveParallels());
        Assertions.assertEquals(0.5 * Math.sqrt(2), cfg.getFinalDurationMultiplier(), 0.001);

        // Verify with GTCEuModAdapter
        var adapter = ModAdapterRegistry.getAdapterForNode(node);
        int effectivePar = adapter.computeEffectiveParallel(node);
        Assertions.assertEquals(2, effectivePar);

        var oc = adapter.computeOverclock(node, node.getTargetTier(), false);
        // Base duration 100 ticks -> 100 * 0.5 * sqrt(2) ~ 70.71 ticks
        Assertions.assertEquals(100.0 * 0.5 * Math.sqrt(2), oc.durationTicks(), 0.01);
        // Base eut 1000 -> 1000 * 0.5 = 500 EU/t
        Assertions.assertEquals(500.0, oc.eut(), 0.01);

        // Total power for single machine: 500 EU/t * 2 Par = 1000 EU/t
        double power = adapter.computeSingleMachinePower(node);
        Assertions.assertEquals(1000.0, power, 0.01);
    }

    @Test
    public void testThreadingSerialization() {
        RecipeNode original = RecipeNode.create("Multithreaded Component Synthesis Forge", 200.0, 5000.0, GTVoltageTier.UXV);
        original.setMultiblock(true);

        NodeThreadingConfig cfg = original.getThreadingConfig();
        cfg.setHelixCount(GTThreadingHelix.UXV_SUPREME, 2); // +80 Gen
        cfg.setHelixCount(GTThreadingHelix.UIV_OVERDRIVE, 1); // +6 Gen, +18 Spd, +6 Eff
        cfg.setAssignedSpeed(30);
        cfg.setAssignedEfficiency(30);
        cfg.setAssignedParallels(20);

        CompoundTag tag = original.serializeNBT();
        RecipeNode deserialized = RecipeNode.deserializeNBT(tag);

        Assertions.assertTrue(deserialized.hasThreading());
        NodeThreadingConfig dCfg = deserialized.getThreadingConfig();

        Assertions.assertEquals(2, dCfg.getHelixCount(GTThreadingHelix.UXV_SUPREME));
        Assertions.assertEquals(1, dCfg.getHelixCount(GTThreadingHelix.UIV_OVERDRIVE));
        Assertions.assertEquals(30, dCfg.getAssignedSpeed());
        Assertions.assertEquals(30, dCfg.getAssignedEfficiency());
        Assertions.assertEquals(20, dCfg.getAssignedParallels());
        Assertions.assertEquals(cfg.calculateDurationMultiplier(), dCfg.calculateDurationMultiplier(), 0.001);
        Assertions.assertEquals(cfg.calculateEnergyMultiplier(), dCfg.calculateEnergyMultiplier(), 0.001);
        Assertions.assertEquals(cfg.getEffectiveParallels(), dCfg.getEffectiveParallels());
    }

    @Test
    public void testMaxHelixCapacityClamping() {
        NodeThreadingConfig cfg = new NodeThreadingConfig();
        cfg.setMaxHelixCapacity(12); // Capacity deduced from multiblock_info (e.g. 12 helix blocks)

        // Add 10 UEV Supreme
        cfg.addHelixCount(GTThreadingHelix.UEV_SUPREME, 10);
        Assertions.assertEquals(10, cfg.getTotalHelixCount());

        // Try adding 10 more (should clamp to remaining 2 -> total 12)
        cfg.addHelixCount(GTThreadingHelix.UHV_OVERDRIVE, 10);
        Assertions.assertEquals(12, cfg.getTotalHelixCount());
        Assertions.assertEquals(2, cfg.getHelixCount(GTThreadingHelix.UHV_OVERDRIVE));

        // Further additions are blocked when at max capacity
        cfg.addHelixCount(GTThreadingHelix.MAX_SUPREME, 1);
        Assertions.assertEquals(12, cfg.getTotalHelixCount());
        Assertions.assertEquals(0, cfg.getHelixCount(GTThreadingHelix.MAX_SUPREME));
    }

    @Test
    public void testSyncThreadingAddonsToActiveAddons() {
        RecipeNode node = RecipeNode.create("Quantum Force Transformer", 100.0, 1000000.0, GTVoltageTier.UEV);
        node.setMultiblock(true);
        NodeThreadingConfig cfg = node.getThreadingConfig();
        cfg.addHelixCount(GTThreadingHelix.MAX_SUPREME, 4);
        cfg.addHelixCount(GTThreadingHelix.UHV_OVERDRIVE, 8);

        // Sync to active addons
        com.gtceu.calcboard.compat.start.StarTModAdapter.syncThreadingAddons(node);

        // Active addons must now contain the 2 helix entries
        Assertions.assertEquals(2, node.getAddons().size());
        Assertions.assertTrue(node.getAddons().stream().anyMatch(a -> a.getName().contains("4x MAX Supreme")));
        Assertions.assertTrue(node.getAddons().stream().anyMatch(a -> a.getName().contains("8x UHV Overdrive")));

        // Test uninstall via adapter
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        com.gtceu.calcboard.api.MachineAddon maxHelixAddon = node.getAddons().stream()
                .filter(a -> a.getName().contains("MAX Supreme"))
                .findFirst().orElseThrow();
        adapter.handleUninstallAddon(node, maxHelixAddon);

        // MAX Supreme count should now be 0
        Assertions.assertEquals(0, cfg.getHelixCount(GTThreadingHelix.MAX_SUPREME));
        Assertions.assertEquals(8, cfg.getHelixCount(GTThreadingHelix.UHV_OVERDRIVE));
    }
}
