package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTThreadingHelix;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.NodeThreadingConfig;
import com.gtceu.calcboard.compat.gtceu.addon.GTCoilAddon;
import com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon;
import com.gtceu.calcboard.compat.gtceu.helper.CoilHelper;
import com.gtceu.calcboard.testutil.TestFixtures;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MachineSwitchLifecycleTest {

    private GTCEuModAdapter adapter;

    @BeforeEach
    public void setup() {
        MultiblockDetector.reinitialize();
        com.gtceu.calcboard.testutil.TestMultiblockFixtures.initTestEnvironmentDefaults();
        adapter = new GTCEuModAdapter();
    }

    @Test
    public void testSwitchFromEbfToSingleblockPurgesCoilAndParallel() {
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation singleId = ResourceLocation.tryParse("gtceu:lv_chemical_reactor");

        RecipeNode node = RecipeNode.create(ebfId, "EBF Smelting", 100.0, 30.0, GTVoltageTier.LV);
        node.setMultiblock(true);
        node.setParallel(8);

        // Equip Coil and Parallel Addons
        GTCoilAddon coilAddon = new GTCoilAddon("gtceu:cupronickel_coil", "Cupronickel Coil", "Coil",
                ResourceLocation.tryParse("gtceu:cupronickel_coil_block"), CoilHelper.CoilStats.DEFAULT);
        MachineAddon parAddon = TestFixtures.createParallelHatch("gtceu:ev_parallel_hatch", "EV Parallel Hatch", 8, false);
        node.getAddons().add(coilAddon);
        node.getAddons().add(parAddon);

        Assertions.assertEquals(2, node.getAddons().size());
        Assertions.assertEquals(8, node.getParallel());
        Assertions.assertEquals(1800, CoilHelper.getInstalledCoilTemperature(node));

        // Switch Machine from EBF -> Singleblock Machine
        node.setMachineIcon(singleId);

        // Verify Incompatible Addons & Stats are Automatically Purged
        Assertions.assertFalse(node.isMultiblock());
        Assertions.assertEquals(1, node.getParallel(), "Parallel must reset to 1 on singleblock machine");
        Assertions.assertEquals(0, CoilHelper.getInstalledCoilTemperature(node), "Coil temp must be 0 on non-coil machine");
        Assertions.assertTrue(node.getAddons().stream().noneMatch(a -> a.getCategory() == MachineAddon.Category.COIL), "Coil addon must be purged");
        Assertions.assertTrue(node.getAddons().stream().noneMatch(a -> a.getCategory() == MachineAddon.Category.PARALLEL), "Parallel addon must be purged");
    }

    @Test
    public void testSwitchToNptApplies12xParallelAndGeneratorPreset() {
        ResourceLocation singleId = ResourceLocation.tryParse("gtceu:lv_chemical_reactor");
        ResourceLocation nptId = ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine");

        RecipeNode node = RecipeNode.create(singleId, "Chemical Process", 100.0, 30.0, GTVoltageTier.LV);
        Assertions.assertFalse(node.isGenerator());
        Assertions.assertEquals(1, node.getParallel());

        // Switch to NPT
        node.setMachineIcon(nptId);

        // Verify NPT Presets
        Assertions.assertTrue(node.isGenerator(), "NPT must enable generator mode automatically");
        Assertions.assertEquals(12, node.getParallel(), "NPT must preset to 12x turbine parallel automatically");
        Assertions.assertEquals(GTVoltageTier.IV, node.getTargetTier(), "NPT must preset tier to at least IV");
    }

    @Test
    public void testSwitchFromNptToSingleblockPurgesRotorAndLaser() {
        ResourceLocation nptId = ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine");
        ResourceLocation singleId = ResourceLocation.tryParse("gtceu:lv_chemical_reactor");

        RecipeNode node = RecipeNode.create(nptId, "Plasma Generation", 20.0, 16384.0, GTVoltageTier.IV);

        // Equip Rotor Addon and Laser Target Hatch
        MachineAddon rotorAddon = new MachineAddon("gtceu:tungstensteel_rotor", "Tungstensteel Rotor", MachineAddon.Category.ROTOR, "Rotor", null);
        GTEnergyHatchAddon laserHatch = new GTEnergyHatchAddon("gtceu:iv_laser_target_hatch", "IV Laser Target Hatch", "Laser Target",
                ResourceLocation.tryParse("gtceu:iv_laser_target_hatch_256a"), GTVoltageTier.IV, 256, true, false, false);
        node.getAddons().add(rotorAddon);
        node.getAddons().add(laserHatch);

        Assertions.assertEquals(2, node.getAddons().size());

        // Switch from NPT -> Singleblock
        node.setMachineIcon(singleId);

        // Verify Rotor and Laser Hatch are Purged
        Assertions.assertTrue(node.getAddons().stream().noneMatch(a -> a.getCategory() == MachineAddon.Category.ROTOR), "Rotor addon must be purged");
        Assertions.assertTrue(node.getAddons().stream().noneMatch(a -> a instanceof GTEnergyHatchAddon eh && eh.isLaser()), "Laser hatch must be purged");
        Assertions.assertEquals(1, node.getParallel());
    }

    @Test
    public void testThreadingConfigPurgedWhenSwitchingToNonThreadingMachine() {
        ResourceLocation threadId = ResourceLocation.tryParse("gtceu:multithreaded_component_synthesis_forge");
        ResourceLocation singleId = ResourceLocation.tryParse("gtceu:lv_chemical_reactor");

        RecipeNode node = RecipeNode.create(threadId, "Threading Synthesis", 100.0, 30.0, GTVoltageTier.HV);
        NodeThreadingConfig cfg = new NodeThreadingConfig();
        cfg.setHelixCount(GTThreadingHelix.UEV_SUPREME, 10);
        node.setThreadingConfig(cfg);

        Assertions.assertNotNull(node.getThreadingConfig());

        // Switch to Non-Threading Singleblock
        node.setMachineIcon(singleId);

        // Verify ThreadingConfig is Purged
        Assertions.assertEquals(0, node.getThreadingConfig().getTotalHelixCount(), "Threading helices must be cleared");
        Assertions.assertEquals(0, node.getThreadingConfig().getMaxHelixCapacity(), "Threading capacity must be 0");
    }

    @Test
    public void testCapabilitiesDetection() {
        ResourceLocation nptId = ResourceLocation.tryParse("gtceu:nyinsane_plasma_turbine");
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation rhfId = ResourceLocation.tryParse("gtceu:mega_blast_furnace");
        ResourceLocation threadId = ResourceLocation.tryParse("gtceu:multithreaded_component_synthesis_forge");

        // LPT Capabilities (No Laser, No Par Hatch, 1x)
        ResourceLocation lptId = ResourceLocation.tryParse("gtceu:large_plasma_turbine");
        ResourceLocation sptId = ResourceLocation.tryParse("gtceu:supreme_plasma_turbine");
        Assertions.assertFalse(MultiblockDetector.supportsLaserHatch(lptId, null));
        Assertions.assertFalse(MultiblockDetector.supportsParallelHatch(lptId, null));
        Assertions.assertTrue(MultiblockDetector.supportsTurbineRotor(lptId, null));
        Assertions.assertEquals(1, MultiblockDetector.getDefaultParallel(lptId));

        // SPT Capabilities (Laser, No Par Hatch, 6x)
        Assertions.assertTrue(MultiblockDetector.supportsLaserHatch(sptId, null));
        Assertions.assertFalse(MultiblockDetector.supportsParallelHatch(sptId, null));
        Assertions.assertTrue(MultiblockDetector.supportsTurbineRotor(sptId, null));
        Assertions.assertEquals(6, MultiblockDetector.getDefaultParallel(sptId));

        // NPT Capabilities (Laser, No Par Hatch, 12x)
        Assertions.assertTrue(MultiblockDetector.supportsLaserHatch(nptId, null));
        Assertions.assertFalse(MultiblockDetector.supportsParallelHatch(nptId, null));
        Assertions.assertTrue(MultiblockDetector.supportsTurbineRotor(nptId, null));
        Assertions.assertEquals(12, MultiblockDetector.getDefaultParallel(nptId));

        // EBF & RHF Capabilities
        Assertions.assertFalse(MultiblockDetector.supportsParallelHatch(ebfId, null));
        Assertions.assertTrue(MultiblockDetector.supportsParallelHatch(rhfId, null));
        Assertions.assertTrue(MultiblockDetector.isCoilMultiblock(ebfId));
        Assertions.assertTrue(MultiblockDetector.isCoilMultiblock(rhfId));

        // Threading Capabilities
        Assertions.assertEquals(24, MultiblockDetector.getMaxHelixCount(threadId));
    }
}
