package com.gtceu.calcboard;

import com.gtceu.calcboard.api.bom.MultiblockBOMCalculator;
import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.CategoryCapability;
import com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.catalog.MultiblockDetector;
import com.gtceu.calcboard.compat.IModAdapter;

import com.gtceu.calcboard.api.type.GTThreadingHelix;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.NodeThreadingConfig;
import com.gtceu.calcboard.api.model.RecipeNode;
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
        com.gtceu.calcboard.api.catalog.MachineAddon maxHelixAddon = node.getAddons().stream()
                .filter(a -> a.getName().contains("MAX Supreme"))
                .findFirst().orElseThrow();
        adapter.handleUninstallAddon(node, maxHelixAddon);

        // MAX Supreme count should now be 0
        Assertions.assertEquals(0, cfg.getHelixCount(GTThreadingHelix.MAX_SUPREME));
        Assertions.assertEquals(8, cfg.getHelixCount(GTThreadingHelix.UHV_OVERDRIVE));
    }

    @Test
    public void testStandardMultiblockThreadingModeToggle() {
        RecipeNode dtNode = RecipeNode.create("Distillation Tower", 100.0, 120.0, GTVoltageTier.MV);
        dtNode.setMultiblock(true);
        dtNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:distillation_tower"));

        // Standard Distillation Tower is NOT an explicit threading machine
        Assertions.assertFalse(dtNode.isExplicitThreadingMachine());
        // Initially, hasThreading must be false
        Assertions.assertFalse(dtNode.hasThreading());

        var cats = com.gtceu.calcboard.api.catalog.MachineAddon.getRelevantCategories(dtNode);
        Assertions.assertFalse(cats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.THREADING));

        // Switch to Threading Mode
        dtNode.setThreadingActive(true);
        Assertions.assertTrue(dtNode.hasThreading());
        var threadingCats = com.gtceu.calcboard.api.catalog.MachineAddon.getRelevantCategories(dtNode);
        Assertions.assertTrue(threadingCats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.THREADING));

        // Switch back to Standard Mode
        dtNode.setThreadingActive(false);
        Assertions.assertFalse(dtNode.hasThreading());
        var revertedCats = com.gtceu.calcboard.api.catalog.MachineAddon.getRelevantCategories(dtNode);
        Assertions.assertFalse(revertedCats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.THREADING));
    }

    @Test
    public void testPlasmaTurbineRecognition() {
        RecipeNode argonNode = RecipeNode.create("Plasma Generator (Argon Plasma)", 20.0, 16384.0, GTVoltageTier.IV);
        argonNode.setMultiblock(true);
        argonNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:plasma_generator"));

        RecipeNode sptNode = RecipeNode.create("Supreme Plasma Turbine (Argon Plasma)", 20.0, 98304.0, GTVoltageTier.IV);
        sptNode.setMultiblock(true);
        sptNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:plasma_generator"));
        sptNode.setMachineIcon(ResourceLocation.tryParse("gtceu:supreme_plasma_turbine"));

        Assertions.assertTrue(com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.isPlasmaTurbine(argonNode));
        Assertions.assertTrue(com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.isPlasmaTurbine(sptNode));

        Assertions.assertEquals(com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.LPT, com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.getModel(argonNode));
        Assertions.assertEquals(com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.SPT, com.gtceu.calcboard.compat.gtceu.model.GTPlasmaTurbineModel.getModel(sptNode));
    }

    @Test
    public void testMultiblockControllerVariantAndParallelHatchAddonIntegration() {
        RecipeNode dtNode = RecipeNode.create("Distillation Tower", 100.0, 120.0, GTVoltageTier.MV);
        dtNode.setMultiblock(true);
        dtNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:distillation_tower"));

        dtNode.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:distillation_tower"));
        dtNode.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:large_distillery"));
        dtNode.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:yielding_excession_advanced_seperation_transformator"));
        dtNode.getAvailableWorkstations().add(ResourceLocation.tryParse("start:threading_processing_plant"));

        // 1. Initial State: Default 1x parallel
        Assertions.assertEquals(1, dtNode.getTotalParallel());

        // 2. Equip Elite Parallel Hatch (64x)
        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(dtNode);
        com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon elitePar = new com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon(
                "gtceu:elite_parallel_hatch", "Elite Parallel Control Hatch", "", ResourceLocation.tryParse("gtceu:elite_parallel_control_hatch"), 64, false
        );
        adapter.handleInstallAddon(dtNode, elitePar, false);

        Assertions.assertEquals(64, dtNode.getTotalParallel());

        // Verify BOM reflects the 64x parallel hatch
        var bomSummary = com.gtceu.calcboard.api.bom.MultiblockBOMCalculator.calculateBOM(java.util.List.of(dtNode), false);
        boolean hasEliteHatchInBOM = bomSummary.aggregatedItems().stream().anyMatch(item -> item.itemId().equals(ResourceLocation.tryParse("gtceu:elite_parallel_control_hatch")));
        Assertions.assertTrue(hasEliteHatchInBOM);

        // 3. Equip Mega Absolute Parallel Hatch (1024x) -> Should cleanly replace Elite
        com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon megaPar = new com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon(
                "start:mega_absolute_parallel_hatch", "Mega Absolute Parallel Hatch", "", ResourceLocation.tryParse("start:mega_absolute_parallel_hatch"), 1024, true
        );
        adapter.handleInstallAddon(dtNode, megaPar, false);

        Assertions.assertEquals(1024, dtNode.getTotalParallel());
        Assertions.assertTrue(dtNode.hasPowerConstantAddon());
        Assertions.assertEquals(1, dtNode.getAddons().stream().filter(a -> a.getCategory() == com.gtceu.calcboard.api.catalog.MachineAddon.Category.PARALLEL).count());

        // Power remains 1x recipe power (not 1024x)!
        double singleRecipePower = adapter.computeOverclock(dtNode, dtNode.getTargetTier(), false).eut();
        Assertions.assertEquals(singleRecipePower, adapter.computeSingleMachinePower(dtNode), 0.001);
        Assertions.assertEquals(singleRecipePower, dtNode.getTotalEUt(), 0.001);

        // 4. Uninstall Parallel Hatch -> Returns to 1x
        adapter.handleUninstallAddon(dtNode, megaPar);
        Assertions.assertEquals(1, dtNode.getTotalParallel());
        Assertions.assertEquals(0, dtNode.getAddons().stream().filter(a -> a.getCategory() == com.gtceu.calcboard.api.catalog.MachineAddon.Category.PARALLEL).count());

        // 5. Equip Energy Hatch Addon (UV Energy Input Hatch)
        com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon uvEnergyHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(
                "gtceu:uv_energy_input_hatch", "UV Energy Input Hatch", "", ResourceLocation.tryParse("gtceu:uv_energy_input_hatch"), GTVoltageTier.UV, 1, false, false, false
        );
        adapter.handleInstallAddon(dtNode, uvEnergyHatch, false);

        Assertions.assertEquals(GTVoltageTier.UV, dtNode.getTargetTier());
        var bomWithEnergy = com.gtceu.calcboard.api.bom.MultiblockBOMCalculator.calculateBOM(java.util.List.of(dtNode), false);
        boolean hasUVEnergyHatch = bomWithEnergy.aggregatedItems().stream().anyMatch(item -> item.itemId().equals(ResourceLocation.tryParse("gtceu:uv_energy_input_hatch")));
        Assertions.assertTrue(hasUVEnergyHatch);

        // 6. Equip 2nd UV Energy Hatch -> Dual Hatch Overclock (Tier becomes UHV!)
        adapter.handleInstallAddon(dtNode, uvEnergyHatch, false);
        Assertions.assertEquals(2, dtNode.getAddons().stream().filter(a -> a.getCategory() == com.gtceu.calcboard.api.catalog.MachineAddon.Category.ENERGY_HATCH).count());
        Assertions.assertEquals(GTVoltageTier.UHV, dtNode.getTargetTier());

        var bomWithDualEnergy = com.gtceu.calcboard.api.bom.MultiblockBOMCalculator.calculateBOM(java.util.List.of(dtNode), false);
        var uvEntry = bomWithDualEnergy.aggregatedItems().stream().filter(item -> item.itemId().equals(ResourceLocation.tryParse("gtceu:uv_energy_input_hatch"))).findFirst();
        Assertions.assertTrue(uvEntry.isPresent());
        Assertions.assertEquals(2, uvEntry.get().totalAmount());

        // 7. Uninstall 1 UV Hatch -> Returns to 1x UV
        adapter.handleUninstallAddon(dtNode, uvEnergyHatch);
        Assertions.assertEquals(1, dtNode.getAddons().stream().filter(a -> a.getCategory() == com.gtceu.calcboard.api.catalog.MachineAddon.Category.ENERGY_HATCH).count());
        Assertions.assertEquals(GTVoltageTier.UV, dtNode.getTargetTier());

        // 8. Test Asymmetric Hatches: 16A EV + 1A IV -> Total EU/t capacity = 32,768 (EV 16A) + 8,192 (IV 1A) = 40,960 EU/t (LuV power)
        adapter.handleUninstallAddon(dtNode, uvEnergyHatch);
        com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon ev16a = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(
                "gtceu:ev_energy_input_hatch_16a", "EV 16A Energy Input Hatch", "", ResourceLocation.tryParse("gtceu:ev_energy_input_hatch_16a"), GTVoltageTier.EV, 16, false, false, false
        );
        com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon iv1a = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(
                "gtceu:iv_energy_input_hatch", "IV Energy Input Hatch", "", ResourceLocation.tryParse("gtceu:iv_energy_input_hatch"), GTVoltageTier.IV, 1, false, false, false
        );
        adapter.handleInstallAddon(dtNode, ev16a, false);
        adapter.handleInstallAddon(dtNode, iv1a, false);

        // Effective tier for speed overclocking becomes LuV!
        Assertions.assertEquals(GTVoltageTier.LuV, dtNode.getTargetTier());

        var bomAsym = com.gtceu.calcboard.api.bom.MultiblockBOMCalculator.calculateBOM(java.util.List.of(dtNode), false);
        boolean hasEV16A = bomAsym.aggregatedItems().stream().anyMatch(item -> item.itemId().equals(ResourceLocation.tryParse("gtceu:ev_energy_input_hatch_16a")));
        boolean hasIV1A = bomAsym.aggregatedItems().stream().anyMatch(item -> item.itemId().equals(ResourceLocation.tryParse("gtceu:iv_energy_input_hatch")));
        Assertions.assertTrue(hasEV16A);
        Assertions.assertTrue(hasIV1A);
    }

    @Test
    public void testFermentingArborealRejuvenationMonstrosityThreadingRecognition() {
        ResourceLocation farmId = ResourceLocation.tryParse("gtceu:fermenting_arboreal_rejuvenation_monstrosity");
        Assertions.assertTrue(com.gtceu.calcboard.api.catalog.MultiblockDetector.isThreadingMultiblock(farmId));
        Assertions.assertEquals(8, com.gtceu.calcboard.api.catalog.MultiblockDetector.getMaxHelixCount(farmId));
        Assertions.assertFalse(com.gtceu.calcboard.api.catalog.MultiblockDetector.isCoilMultiblock(farmId));

        // Verify fusion coil is classified as CASING, not heating COIL
        ResourceLocation fusionCoilId = ResourceLocation.tryParse("start_core:advanced_fusion_coil");
        Assertions.assertEquals(com.gtceu.calcboard.api.bom.PartCategory.CASING,
                com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.classifyPart(fusionCoilId));
        ResourceLocation cupronickelId = ResourceLocation.tryParse("gtceu:cupronickel_coil_block");
        Assertions.assertEquals(com.gtceu.calcboard.api.bom.PartCategory.COIL,
                com.gtceu.calcboard.api.bom.MultiblockStructureCatalog.classifyPart(cupronickelId));

        RecipeNode farmNode = RecipeNode.create("Fermenting Arboreal Rejuvenation Monstrosity", 200.0, 500000.0, GTVoltageTier.UXV);
        farmNode.setMultiblock(true);
        farmNode.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:tree_greenhouse"));
        farmNode.setMachineIcon(farmId);
        farmNode.getAvailableWorkstations().add(farmId);

        Assertions.assertTrue(farmNode.isExplicitThreadingMachine());
        Assertions.assertTrue(farmNode.hasThreading());
        Assertions.assertFalse(farmNode.canUseCoils());
        Assertions.assertEquals(8, farmNode.getThreadingConfig().getMaxHelixCapacity());

        var adapter = ModAdapterRegistry.getAdapterForNode(farmNode);
        var applicableCats = adapter.getApplicableAddonCategories(farmNode);
        Assertions.assertTrue(applicableCats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.THREADING));
        Assertions.assertFalse(applicableCats.contains(com.gtceu.calcboard.api.catalog.MachineAddon.Category.COIL));

        // Test CategoryCapability active categories
        var catCap = com.gtceu.calcboard.api.catalog.CategoryCapabilityMatrix.getInstance().getCapability(farmNode.getRecipeCategoryId());
        var activeCats = catCap.getActiveCategoriesForNode(farmNode);
        Assertions.assertTrue(activeCats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.THREADING));
        Assertions.assertFalse(activeCats.contains(com.gtceu.calcboard.api.catalog.MachineAddon.Category.COIL));

        // Switch to Greenhouse (non-threading multiblock)
        ResourceLocation greenhouseId = ResourceLocation.tryParse("gtceu:greenhouse");
        farmNode.setMachineIcon(greenhouseId);
        farmNode.setThreadingActive(false);

        Assertions.assertFalse(farmNode.isExplicitThreadingMachine());
        Assertions.assertFalse(farmNode.hasThreading());
        var ghCats = adapter.getApplicableAddonCategories(farmNode);
        Assertions.assertFalse(ghCats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.THREADING));
        var ghActiveCats = catCap.getActiveCategoriesForNode(farmNode);
        Assertions.assertFalse(ghActiveCats.contains(com.gtceu.calcboard.api.catalog.AddonCategory.THREADING));

        // Switch back to FARM
        farmNode.setMachineIcon(farmId);
        farmNode.setThreadingActive(true);
        Assertions.assertTrue(farmNode.hasThreading());
        Assertions.assertTrue(adapter.getApplicableAddonCategories(farmNode).contains(com.gtceu.calcboard.api.catalog.AddonCategory.THREADING));
    }

    @Test
    public void testAllStarTThreadedMultiblocksRecognition() {
        String[] starTMultis = new String[]{
                "gtceu:fermenting_arboreal_rejuvenation_monstrosity",
                "gtceu:multithreaded_component_synthesis_forge",
                "gtceu:aqueous_transformation_processing_center",
                "gtceu:ascendant_engraving_matrix",
                "gtceu:byteforce_unified_incomparable_logistics_depot",
                "gtceu:electro_magnetic_material_ripper",
                "gtceu:gravitational_compression_chamber",
                "gtceu:material_annihilation_array",
                "gtceu:molecular_inducing_xanadu",
                "gtceu:subatomic_particle_lattice_isolation_terminal",
                "gtceu:superior_particulate_isolation_nexus",
                "gtceu:yielding_excression_advanced_seperation_transformator"
        };

        for (String idStr : starTMultis) {
            ResourceLocation id = ResourceLocation.tryParse(idStr);
            Assertions.assertTrue(com.gtceu.calcboard.api.catalog.MultiblockDetector.isMultiblock(id), idStr + " should be registered as multiblock");
            Assertions.assertTrue(com.gtceu.calcboard.api.catalog.MultiblockDetector.isThreadingMultiblock(id), idStr + " should be recognized as threading multiblock");
            Assertions.assertTrue(com.gtceu.calcboard.api.catalog.MultiblockDetector.getMaxHelixCount(id) > 0, idStr + " should have max helix capacity > 0");

            RecipeNode node = RecipeNode.create(id.getPath(), 100.0, 10000.0, GTVoltageTier.UIV);
            node.setMultiblock(true);
            node.setMachineIcon(id);
            node.getAvailableWorkstations().add(id);

            Assertions.assertTrue(node.hasThreading(), idStr + " node should have threading");
            var adapter = ModAdapterRegistry.getAdapterForNode(node);
            Assertions.assertTrue(adapter.getApplicableAddonCategories(node).contains(com.gtceu.calcboard.api.catalog.AddonCategory.THREADING),
                    idStr + " applicable categories should contain THREADING");
        }
    }

    @Test
    public void testEnergyHatchCapacityAndParallelOverclockSimulation() {
        // 1. Create Chemical Reactor (Nitrobenzene) node: MV 90 EU/t, 160 ticks (8.0s)
        RecipeNode lcrNode = RecipeNode.create("Large Chemical Reactor (Nitrobenzene)", 160.0, 90.0, GTVoltageTier.MV);
        lcrNode.setMultiblock(true);
        lcrNode.setMachineIcon(ResourceLocation.tryParse("gtceu:large_chemical_reactor"));
        lcrNode.addOutput(com.gtceu.calcboard.api.model.IngredientStack.fluid(ResourceLocation.tryParse("gtceu:nitrobenzene"), "Nitrobenzene", 8000.0, 1.0));
        lcrNode.addOutput(com.gtceu.calcboard.api.model.IngredientStack.fluid(ResourceLocation.tryParse("gtceu:diluted_sulfuric_acid"), "Diluted Sulfuric Acid", 1000.0, 1.0));

        var adapter = ModAdapterRegistry.getAdapterForNode(lcrNode);

        // 2. Install IV 1A Energy Input Hatch (8,192 EU/t) and 16x Parallel Control Hatch
        com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon ivEnergyHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(
                "gtceu:iv_energy_input_hatch", "IV Energy Input Hatch", "", ResourceLocation.tryParse("gtceu:iv_energy_input_hatch"), GTVoltageTier.IV, 1, false, false, false
        );
        com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon par16x = new com.gtceu.calcboard.compat.gtceu.addon.GTParallelHatchAddon(
                "gtceu:luv_parallel_hatch", "Master Parallel Control Hatch", "", ResourceLocation.tryParse("gtceu:luv_parallel_hatch"), 16, false
        );

        adapter.handleInstallAddon(lcrNode, ivEnergyHatch, false);
        adapter.handleInstallAddon(lcrNode, par16x, false);

        // Node target tier is IV, but power capacity limit (8,192 EU/t) constrains overclocks to 1 OC (HV)
        Assertions.assertEquals(GTVoltageTier.IV, lcrNode.getTargetTier());
        Assertions.assertEquals(16, lcrNode.getTotalParallel());

        var oc = adapter.computeOverclock(lcrNode, lcrNode.getTargetTier(), false);
        // Base 160 ticks -> 1 OC (80 ticks = 4.00s)
        Assertions.assertEquals(80.0, oc.durationTicks(), 0.001);
        Assertions.assertEquals(4.00, lcrNode.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(1, oc.overclocks());

        // Single recipe EUt is 360 EU/t, 16 parallel total power is 5,760 EU/t (0.7 A @ IV)
        double totalPower = adapter.computeSingleMachinePower(lcrNode);
        Assertions.assertEquals(5760.0, totalPower, 0.001);
        Assertions.assertEquals(5760.0, lcrNode.getTotalEUt(), 0.001);

        // CPS is (20/80) * 16 = 4.0 cps
        Assertions.assertEquals(4.0, lcrNode.getCyclesPerSecond(), 0.001);

        // Outputs: 8,000 mB * 4.0 = 32,000 mB/s (32 B/s), 1,000 mB * 4.0 = 4,000 mB/s (4 B/s)
        var outRates = lcrNode.calculateOutputRates();
        var nitroEntry = outRates.entrySet().stream().filter(e -> e.getKey().getDisplayName().equals("Nitrobenzene")).findFirst();
        var acidEntry = outRates.entrySet().stream().filter(e -> e.getKey().getDisplayName().equals("Diluted Sulfuric Acid")).findFirst();
        Assertions.assertTrue(nitroEntry.isPresent());
        Assertions.assertTrue(acidEntry.isPresent());
        Assertions.assertEquals(32000.0, nitroEntry.get().getValue(), 0.001);
        Assertions.assertEquals(4000.0, acidEntry.get().getValue(), 0.001);

        // 3. Upgrade to LuV 1A Energy Hatch (32,768 EU/t) -> Unlocks 2nd OC (EV level, 2.00s, 23,040 EU/t)
        com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon luvEnergyHatch = new com.gtceu.calcboard.compat.gtceu.addon.GTEnergyHatchAddon(
                "gtceu:luv_energy_input_hatch", "LuV Energy Input Hatch", "", ResourceLocation.tryParse("gtceu:luv_energy_input_hatch"), GTVoltageTier.LuV, 1, false, false, false
        );
        adapter.handleUninstallAddon(lcrNode, ivEnergyHatch);
        adapter.handleInstallAddon(lcrNode, luvEnergyHatch, false);

        Assertions.assertEquals(GTVoltageTier.LuV, lcrNode.getTargetTier());
        var ocLuV = adapter.computeOverclock(lcrNode, lcrNode.getTargetTier(), false);
        Assertions.assertEquals(40.0, ocLuV.durationTicks(), 0.001); // 2.00s
        Assertions.assertEquals(2.00, lcrNode.getEffectiveDurationSeconds(), 0.001);
        Assertions.assertEquals(2, ocLuV.overclocks());
        Assertions.assertEquals(23040.0, lcrNode.getTotalEUt(), 0.001); // 1,440 * 16 = 23,040 EU/t
        Assertions.assertEquals(8.0, lcrNode.getCyclesPerSecond(), 0.001);

        // 4. Uninstall energy hatches -> Resets target tier to recipe tier (MV)
        adapter.handleUninstallAddon(lcrNode, luvEnergyHatch);
        Assertions.assertEquals(GTVoltageTier.MV, lcrNode.getTargetTier());
    }
}




