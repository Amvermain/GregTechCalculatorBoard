package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.NodeHardwareReconciler;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.compat.gtceu.GTCEuAddonCrawler;
import com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper;
import com.gtceu.calcboard.compat.start.StarTAddonCrawler;
import com.gtceu.calcboard.testutil.TestFixtures;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Exhaustive regression test verifying that multiblock traits, throughput boostings,
 * and hardware addons never irreversibly mutate recipe specs, input/output ports, or power metrics.
 * <p>
 * Tests two distinct detachment lifecycles:
 * 1. Direct detachment (detachAddon / onAddonRemoved)
 * 2. Purge detachment during machine change (reconcileForMachine -> purgeIncompatibleAddons)
 */
class ExhaustiveTraitReversibilityTest {

    private BoardPage page;
    private CanvasTestHarness harness;

    @BeforeEach
    void setUp() {
        com.gtceu.calcboard.testutil.SimulatedGTEnvironment.setupFullEnvironment();
        BoardManager.getInstance().resetToDefault();
        page = BoardPage.createDefault("Exhaustive Trait Test Page");
        harness = new CanvasTestHarness(page);
    }

    @AfterEach
    void tearDown() {
        com.gtceu.calcboard.testutil.SimulatedGTEnvironment.tearDownEnvironment();
        BoardManager.getInstance().resetToDefault();
    }

    static List<TraitTestCase> getAllMultiblockTraits() {
        List<TraitTestCase> cases = new ArrayList<>();

        List<MachineAddon> builtinTraits = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        GTCEuAddonCrawler.addBuiltinTraits(builtinTraits, seen);
        StarTAddonCrawler.addBuiltinStarTTraits(builtinTraits, seen);

        ResourceLocation ebf = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation lce = GTCombustionHelper.LARGE_COMBUSTION_ENGINE;
        ResourceLocation ece = GTCombustionHelper.EXTREME_COMBUSTION_ENGINE;
        ResourceLocation mcf = GTCombustionHelper.START_MCF;
        ResourceLocation startT1 = GTCombustionHelper.START_T1_COMBUSTION;
        ResourceLocation startT2 = GTCombustionHelper.START_T2_COMBUSTION;
        ResourceLocation startT3 = GTCombustionHelper.START_T3_ROCKET;
        ResourceLocation startT4 = GTCombustionHelper.START_T4_ROCKET;
        ResourceLocation pyrolyse = ResourceLocation.tryParse("gtceu:pyrolyse_oven");
        ResourceLocation lcr = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        ResourceLocation ultimateAbs = ResourceLocation.tryParse("gtceu:ultimate_abs");
        ResourceLocation largeAutoclave = ResourceLocation.tryParse("gtceu:large_autoclave");

        for (MachineAddon addon : builtinTraits) {
            ResourceLocation targetMachine = ebf;
            if ("gtceu:throughput_boosting".equals(addon.getId())) {
                targetMachine = pyrolyse;
            } else if ("gtceu:batch_mode".equals(addon.getId()) || "gtceu:batch_processing".equals(addon.getId())) {
                targetMachine = lcr;
            } else if ("gtceu:bulk_processing".equals(addon.getId())) {
                targetMachine = ultimateAbs;
            } else if ("gtceu:overpressure_autoclave".equals(addon.getId())) {
                targetMachine = largeAutoclave;
            } else if ("gtceu:oxygen_boost".equals(addon.getId())) {
                targetMachine = lce;
            } else if ("gtceu:liquid_oxygen_boost".equals(addon.getId())) {
                targetMachine = ece;
            } else if (addon.getId().contains("coolant")) {
                targetMachine = mcf;
            } else if ("start_core:t1_oxidizer_boost".equals(addon.getId())) {
                targetMachine = startT1;
            } else if ("start_core:t2_oxidizer_boost".equals(addon.getId())) {
                targetMachine = startT2;
            } else if ("start_core:t3_oxidizer_boost".equals(addon.getId())) {
                targetMachine = startT3;
            } else if ("start_core:t4_oxidizer_boost".equals(addon.getId())) {
                targetMachine = startT4;
            }
            cases.add(new TraitTestCase(addon.getId(), addon, targetMachine));
        }

        cases.add(new TraitTestCase("gtceu:parallel_hatch_4x", TestFixtures.createParallelHatch("gtceu:parallel_4", "Parallel 4x", 4, false), ebf));
        cases.add(new TraitTestCase("gtceu:parallel_hatch_16x", TestFixtures.createParallelHatch("gtceu:parallel_16", "Parallel 16x", 16, false), ebf));
        cases.add(new TraitTestCase("custom:speed_boost", MachineAddon.custom("Custom 2x Booster", 0.5, 2.0, 2), ebf));
        cases.add(new TraitTestCase("custom:power_saving", MachineAddon.custom("Custom 0.5x Power", 1.0, 0.5, 1), ebf));

        return cases;
    }

    record TraitTestCase(String id, MachineAddon addon, ResourceLocation compatibleMachine) {
        @Override
        public String toString() {
            return id + " on " + compatibleMachine.getPath();
        }
    }

    @Test
    @DisplayName("Verify direct detachment reversibility for ALL individual traits")
    void testDirectDetachReversibilityForAllTraits() {
        List<TraitTestCase> allCases = getAllMultiblockTraits();
        Assertions.assertFalse(allCases.isEmpty(), "Trait catalog must not be empty");

        for (TraitTestCase testCase : allCases) {
            System.out.println(">>> [Direct Detach Test] Testing trait: " + testCase.id());
            RecipeNode node = createStandardNode("node-direct-" + testCase.id().replace(':', '_'), testCase.compatibleMachine());
            page.getGraph().addNode(node);
            harness.getContext().rebuildWidgets();

            NodeHardwareSnapshot baseline = harness.captureHardwareSnapshot(node.getId());
            RecipeSpec baselineSpec = node.getBaseSpec();
            int baselineInputCount = node.getInputs().size();
            int baselineOutputCount = node.getOutputs().size();

            harness.attachAddon(node.getId(), testCase.addon());
            Assertions.assertTrue(node.getAddons().contains(testCase.addon()), "Addon should be installed: " + testCase.id());

            harness.detachAddon(node.getId(), testCase.addon().getCategory());

            Assertions.assertFalse(node.getAddons().contains(testCase.addon()), "Addon should be removed: " + testCase.id());
            harness.assertReversible(node.getId(), baseline);
            harness.assertSpecUnpolluted(node.getId(), baselineSpec);
            Assertions.assertEquals(baselineInputCount, node.getInputs().size(), "Input port count must not leak on direct detach: " + testCase.id());
            Assertions.assertEquals(baselineOutputCount, node.getOutputs().size(), "Output port count must not leak on direct detach: " + testCase.id());

            page.getGraph().removeNode(node);
        }
    }

    @Test
    @DisplayName("Verify purge-on-machine-change reversibility for ALL individual traits")
    void testPurgeOnMachineChangeReversibilityForAllTraits() {
        List<TraitTestCase> allCases = getAllMultiblockTraits();
        ResourceLocation singleblockCentrifuge = ResourceLocation.tryParse("gtceu:centrifuge");

        for (TraitTestCase testCase : allCases) {
            if (testCase.addon().getCategory() == AddonCategory.CUSTOM) {
                continue;
            }
            System.out.println(">>> [Purge On Machine Change Test] Testing trait: " + testCase.id());
            RecipeNode node = createStandardNode("node-purge-" + testCase.id().replace(':', '_'), testCase.compatibleMachine());
            page.getGraph().addNode(node);
            harness.getContext().rebuildWidgets();

            RecipeSpec originalSpec = node.getBaseSpec();
            double originalDuration = node.getBaseDurationTicks();
            double originalEUt = node.getBaseEUt();
            int baselineInputCount = node.getInputs().size();
            int baselineOutputCount = node.getOutputs().size();

            harness.attachAddon(node.getId(), testCase.addon());

            // 1. Transition to incompatible singleblock machine
            harness.changeMachine(node.getId(), singleblockCentrifuge);

            // Verify addon is cleanly purged
            boolean addonStillPresent = node.getAddons().stream().anyMatch(a -> a.getId().equals(testCase.addon().getId()));
            Assertions.assertFalse(addonStillPresent, "Addon must be purged when switching to incompatible singleblock machine: " + testCase.id());

            // Verify recipe spec & ports remain unpolluted in singleblock state
            harness.assertSpecUnpolluted(node.getId(), originalSpec);
            Assertions.assertEquals(originalSpec.baseInputs().size(), node.getInputs().size(), "Input port count must not leak during transition: " + testCase.id());
            Assertions.assertEquals(originalSpec.baseOutputs().size(), node.getOutputs().size(), "Output port count must not leak during transition: " + testCase.id());
            Assertions.assertEquals(originalDuration, node.getBaseDurationTicks(), 0.0001, "Base duration ticks must not pollute: " + testCase.id());
            Assertions.assertEquals(originalEUt, node.getBaseEUt(), 0.0001, "Base EUt must not pollute: " + testCase.id());

            // 2. Transition back to compatible multiblock machine
            harness.changeMachine(node.getId(), testCase.compatibleMachine());
            if (GTCombustionHelper.isModularCombustionFrame(testCase.compatibleMachine())) {
                com.gtceu.calcboard.compat.gtceu.model.mcf.MCFSlotConfiguration cfg = new com.gtceu.calcboard.compat.gtceu.model.mcf.MCFSlotConfiguration();
                cfg.getSlot(0).setEnabled(true);
                GTCombustionHelper.setMCFConfiguration(node, cfg);
                node.syncProjectedPorts();
            }

            // Verify recipe spec & ports remain 100% unpolluted after round-trip
            harness.assertSpecUnpolluted(node.getId(), originalSpec);
            Assertions.assertEquals(baselineInputCount, node.getInputs().size(), "Input port count must cleanly restore after machine round-trip: " + testCase.id());
            Assertions.assertEquals(baselineOutputCount, node.getOutputs().size(), "Output port count must cleanly restore after machine round-trip: " + testCase.id());
            Assertions.assertEquals(originalDuration, node.getBaseDurationTicks(), 0.0001, "Base duration ticks must cleanly restore after round-trip: " + testCase.id());
            Assertions.assertEquals(originalEUt, node.getBaseEUt(), 0.0001, "Base EUt must cleanly restore after round-trip: " + testCase.id());

            page.getGraph().removeNode(node);
        }
    }

    @Test
    @DisplayName("Verify user scenario: switch to Throughput Boosting machine -> detach boosting -> switch to other machine -> ZERO recipe pollution")
    void testThroughputBoostingManualDetachThenMachineSwitchDoesNotPolluteRecipe() {
        ResourceLocation ebf = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation pyrolyse = ResourceLocation.tryParse("gtceu:pyrolyse_oven");
        ResourceLocation centrifuge = ResourceLocation.tryParse("gtceu:centrifuge");

        RecipeNode node = createStandardNode("tpb-scenario-node", ebf);
        page.getGraph().addNode(node);
        harness.getContext().rebuildWidgets();

        RecipeSpec originalSpec = node.getBaseSpec();
        double originalDuration = node.getBaseDurationTicks();
        double originalEUt = node.getBaseEUt();
        int originalInputs = node.getInputs().size();
        int originalOutputs = node.getOutputs().size();

        // Step 1: Switch to Throughput Boosting machine (Pyrolyse Oven)
        harness.changeMachine(node.getId(), pyrolyse);
        boolean hasTpb = node.getAddons().stream().anyMatch(a -> "gtceu:throughput_boosting".equals(a.getId()));
        Assertions.assertTrue(hasTpb, "Pyrolyse Oven must have Throughput Boosting auto-equipped");

        // Step 2: Manually detach Throughput Boosting
        harness.detachAddon(node.getId(), AddonCategory.MULTIBLOCK_TRAIT);
        boolean tpbRemoved = node.getAddons().stream().noneMatch(a -> "gtceu:throughput_boosting".equals(a.getId()));
        Assertions.assertTrue(tpbRemoved, "Throughput Boosting must be detached");
        harness.assertSpecUnpolluted(node.getId(), originalSpec);

        // Step 3: Switch to an entirely different machine (Singleblock Centrifuge)
        harness.changeMachine(node.getId(), centrifuge);
        harness.assertSpecUnpolluted(node.getId(), originalSpec);
        Assertions.assertEquals(originalDuration, node.getBaseDurationTicks(), 0.0001, "Base duration must not be permanently altered by throughput boosting");
        Assertions.assertEquals(originalEUt, node.getBaseEUt(), 0.0001, "Base EUt must not be permanently altered by throughput boosting");
        Assertions.assertEquals(originalInputs, node.getInputs().size(), "Inputs count must remain unchanged");
        Assertions.assertEquals(originalOutputs, node.getOutputs().size(), "Outputs count must remain unchanged");

        // Step 4: Switch back to EBF
        harness.changeMachine(node.getId(), ebf);
        harness.assertSpecUnpolluted(node.getId(), originalSpec);
        Assertions.assertEquals(originalDuration, node.getBaseDurationTicks(), 0.0001, "Base duration must cleanly restore");
        Assertions.assertEquals(originalEUt, node.getBaseEUt(), 0.0001, "Base EUt must cleanly restore");
        Assertions.assertEquals(originalInputs, node.getInputs().size(), "Inputs count must cleanly restore");
        Assertions.assertEquals(originalOutputs, node.getOutputs().size(), "Outputs count must cleanly restore");
    }

    @Test
    @DisplayName("Verify user scenario: switch to Combustion Engine -> detach Oxygen Boost -> switch to Centrifuge -> ZERO recipe pollution")
    void testCombustionEngineOxygenBoostDetachThenMachineSwitchDoesNotPolluteRecipe() {
        ResourceLocation ebf = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation lce = GTCombustionHelper.LARGE_COMBUSTION_ENGINE;
        ResourceLocation centrifuge = ResourceLocation.tryParse("gtceu:centrifuge");

        RecipeNode node = createStandardNode("lce-scenario-node", ebf);
        page.getGraph().addNode(node);
        harness.getContext().rebuildWidgets();

        RecipeSpec originalSpec = node.getBaseSpec();
        int originalInputs = node.getInputs().size();
        int originalOutputs = node.getOutputs().size();

        // Step 1: Switch to LCE and attach Oxygen Boost
        harness.changeMachine(node.getId(), lce);
        MachineAddon oxygenBoost = new MachineAddon("gtceu:oxygen_boost", "Oxygen Boost", AddonCategory.MULTIBLOCK_TRAIT, "", null);
        harness.attachAddon(node.getId(), oxygenBoost);
        Assertions.assertTrue(node.getAddons().contains(oxygenBoost), "Oxygen boost should be installed");

        // Step 2: Detach Oxygen Boost
        harness.detachAddon(node.getId(), AddonCategory.MULTIBLOCK_TRAIT);
        Assertions.assertFalse(node.getAddons().contains(oxygenBoost), "Oxygen boost should be removed");
        harness.assertSpecUnpolluted(node.getId(), originalSpec);

        // Step 3: Switch to Centrifuge
        harness.changeMachine(node.getId(), centrifuge);
        harness.assertSpecUnpolluted(node.getId(), originalSpec);
        Assertions.assertFalse(node.isMultiblock(), "Centrifuge must be singleblock");
        Assertions.assertFalse(node.isGenerator(), "Centrifuge must not be generator");
        Assertions.assertEquals(originalInputs, node.getInputs().size(), "Inputs count must not leak auxiliary oxygen port");
        Assertions.assertEquals(originalOutputs, node.getOutputs().size(), "Outputs count must remain unchanged");

        // Step 4: Switch back to EBF
        harness.changeMachine(node.getId(), ebf);
        harness.assertSpecUnpolluted(node.getId(), originalSpec);
        Assertions.assertTrue(node.isMultiblock(), "EBF must be multiblock");
        Assertions.assertEquals(originalInputs, node.getInputs().size(), "Inputs count must cleanly restore");
        Assertions.assertEquals(originalOutputs, node.getOutputs().size(), "Outputs count must cleanly restore");
    }

    @Test
    @DisplayName("Verify sequential installation and removal of ALL traits on a single node does not degrade recipe")
    void testSequentialAllTraitsCycle() {
        ResourceLocation ebf = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        RecipeNode node = createStandardNode("cycle-node", ebf);
        page.getGraph().addNode(node);
        harness.getContext().rebuildWidgets();

        NodeHardwareSnapshot baseline = harness.captureHardwareSnapshot("cycle-node");
        RecipeSpec baselineSpec = node.getBaseSpec();

        List<TraitTestCase> allCases = getAllMultiblockTraits();

        for (TraitTestCase tc : allCases) {
            harness.changeMachine("cycle-node", tc.compatibleMachine());
            harness.attachAddon("cycle-node", tc.addon());

            harness.detachAddon("cycle-node", tc.addon().getCategory());
            harness.changeMachine("cycle-node", ebf);
            harness.setVoltageTier("cycle-node", baseline.targetTier());
            harness.setParallel("cycle-node", baseline.parallel());

            harness.assertReversible("cycle-node", baseline);
            harness.assertSpecUnpolluted("cycle-node", baselineSpec);
        }

        Assertions.assertTrue(node.getAddons().isEmpty(), "All addons must be empty after full cycle");
        harness.assertReversible("cycle-node", baseline);
    }

    private RecipeNode createStandardNode(String id, ResourceLocation machineIcon) {
        RecipeNode node = RecipeNode.create(machineIcon, "Test Multiblock Machine", 200.0, 120.0, GTVoltageTier.EV);
        node.setId(id);
        node.setTargetTier(GTVoltageTier.EV);
        node.addInput(TestFixtures.item("minecraft:iron_ingot", "Iron Ingot", 2.0));
        node.addOutput(TestFixtures.item("gtceu:iron_plate", "Iron Plate", 1.0));
        NodeHardwareReconciler.reconcileForMachine(node, machineIcon);

        if (GTCombustionHelper.isModularCombustionFrame(machineIcon)) {
            com.gtceu.calcboard.compat.gtceu.model.mcf.MCFSlotConfiguration cfg = new com.gtceu.calcboard.compat.gtceu.model.mcf.MCFSlotConfiguration();
            cfg.getSlot(0).setEnabled(true);
            GTCombustionHelper.setMCFConfiguration(node, cfg);
            node.syncProjectedPorts();
        }
        return node;
    }
}
