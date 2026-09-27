package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.NodeHardwareReconciler;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.testutil.TestFixtures;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HardwareReversibilityTest {

    private BoardPage page;
    private CanvasTestHarness harness;

    @BeforeEach
    void setUp() {
        com.gtceu.calcboard.testutil.SimulatedGTEnvironment.setupFullEnvironment();
        BoardManager.getInstance().resetToDefault();
        page = BoardPage.createDefault("Reversibility Page");
        harness = new CanvasTestHarness(page);
    }

    @AfterEach
    void tearDown() {
        com.gtceu.calcboard.testutil.SimulatedGTEnvironment.tearDownEnvironment();
        BoardManager.getInstance().resetToDefault();
    }

    @Test
    void testRoundTripMachineChangeAndAddonBoosting() {
        ResourceLocation standardEbf = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation megaEbf = ResourceLocation.tryParse("gtceu:mega_electric_blast_furnace");

        RecipeNode node = RecipeNode.create(standardEbf, "Electric Blast Furnace", 200.0, 120.0, GTVoltageTier.MV);
        node.setId("ebf-1");
        node.setTargetTier(GTVoltageTier.MV);
        NodeHardwareReconciler.reconcileForMachine(node, standardEbf);
        node.getInputs().add(TestFixtures.item("minecraft:iron_ingot", "Iron Ingot", 2.0));
        node.getOutputs().add(TestFixtures.item("gtceu:iron_plate", "Iron Plate", 1.0));
        page.getGraph().addNode(node);
        harness.getContext().rebuildWidgets();

        NodeHardwareSnapshot baseline = harness.captureHardwareSnapshot("ebf-1");

        harness.changeMachine("ebf-1", megaEbf);
        MachineAddon booster = new MachineAddon("gtceu:throughput_boosting", "Throughput Boosting", AddonCategory.MULTIBLOCK_TRAIT, "2x Speed", null);
        harness.attachAddon("ebf-1", booster);
        harness.setVoltageTier("ebf-1", GTVoltageTier.HV);
        harness.setParallel("ebf-1", 8);

        Assertions.assertNotEquals(baseline.machineIcon(), node.getMachineIcon());
        Assertions.assertNotEquals(baseline.targetTier(), node.getTargetTier());
        Assertions.assertNotEquals(baseline.parallel(), node.getParallel());
        Assertions.assertFalse(node.getAddons().isEmpty());

        harness.detachAddon("ebf-1", AddonCategory.MULTIBLOCK_TRAIT);
        harness.changeMachine("ebf-1", standardEbf);
        harness.setVoltageTier("ebf-1", baseline.targetTier());
        harness.setParallel("ebf-1", baseline.parallel());

        harness.assertReversible("ebf-1", baseline);
        harness.assertSpecUnpolluted("ebf-1", baseline.recipeSpec());
    }

    @Test
    void testVoltageTierRoundTrip() {
        RecipeNode node = RecipeNode.create("Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        node.setId("chem-1");
        node.setTargetTier(GTVoltageTier.LV);
        page.getGraph().addNode(node);
        harness.getContext().rebuildWidgets();

        NodeHardwareSnapshot baseline = harness.captureHardwareSnapshot("chem-1");

        harness.setVoltageTier("chem-1", GTVoltageTier.UV);
        Assertions.assertEquals(GTVoltageTier.UV, node.getTargetTier());

        harness.setVoltageTier("chem-1", GTVoltageTier.LV);
        harness.assertReversible("chem-1", baseline);
    }

    @Test
    void testParallelHatchAddonRoundTrip() {
        RecipeNode node = RecipeNode.create("Large Chemical Reactor", 100.0, 30.0, GTVoltageTier.HV);
        node.setId("lcr-1");
        node.setMultiblock(true);
        node.setTargetTier(GTVoltageTier.HV);
        node.setParallel(1);
        NodeHardwareReconciler.reconcileForMachine(node, node.getMachineIcon());
        page.getGraph().addNode(node);
        harness.getContext().rebuildWidgets();

        NodeHardwareSnapshot baseline = harness.captureHardwareSnapshot("lcr-1");

        MachineAddon parallelAddon = TestFixtures.createParallelHatch("gtceu:parallel_4", "Parallel Hatch 4x", 4, false);
        harness.attachAddon("lcr-1", parallelAddon);
        Assertions.assertEquals(1, node.getAddons().size());

        harness.detachAddon("lcr-1", AddonCategory.PARALLEL);
        harness.setParallel("lcr-1", baseline.parallel());
        harness.assertReversible("lcr-1", baseline);
    }

    @Test
    void testSnapshotDirectRestore() {
        ResourceLocation lvAssembler = ResourceLocation.tryParse("gtceu:lv_assembler");
        RecipeNode node = RecipeNode.create(lvAssembler, "Assembler", 80.0, 16.0, GTVoltageTier.LV);
        node.setId("assembler-1");
        node.setTargetTier(GTVoltageTier.LV);
        node.getInputs().add(TestFixtures.item("minecraft:copper_ingot", "Copper Ingot", 4.0));
        node.getOutputs().add(TestFixtures.item("gtceu:copper_wire", "Copper Wire", 8.0));
        page.getGraph().addNode(node);
        harness.getContext().rebuildWidgets();

        NodeHardwareSnapshot baseline = harness.captureHardwareSnapshot("assembler-1");

        harness.changeMachine("assembler-1", ResourceLocation.tryParse("gtceu:large_assembler"));
        harness.setVoltageTier("assembler-1", GTVoltageTier.EV);
        harness.setParallel("assembler-1", 16);

        baseline.restoreTo(node);
        harness.assertReversible("assembler-1", baseline);
        harness.assertSpecUnpolluted("assembler-1", baseline.recipeSpec());
    }

    @Test
    void testRotorAddonRoundTrip() {
        RecipeNode node = RecipeNode.create("Large Gas Turbine", 20.0, 32.0, GTVoltageTier.EV);
        node.setId("turbine-1");
        NodeHardwareReconciler.reconcileForMachine(node, node.getMachineIcon());
        node.setRotorEfficiency(100);
        node.setRotorPower(100);
        node.setRotorName("Standard (100%)");
        page.getGraph().addNode(node);
        harness.getContext().rebuildWidgets();

        NodeHardwareSnapshot baseline = harness.captureHardwareSnapshot("turbine-1");

        node.setRotorEfficiency(145);
        node.setRotorPower(120);
        node.setRotorName("High-Efficiency Rotor");
        Assertions.assertEquals(145, node.getRotorEfficiency());

        baseline.restoreTo(node);
        harness.assertReversible("turbine-1", baseline);
        Assertions.assertEquals(100, node.getRotorEfficiency());
        Assertions.assertEquals(100, node.getRotorPower());
        Assertions.assertEquals("Standard (100%)", node.getRotorName());
    }

    @Test
    void testOverclockModeRoundTrip() {
        RecipeNode node = RecipeNode.create("Centrifuge", 100.0, 30.0, GTVoltageTier.MV);
        node.setId("cent-1");
        NodeHardwareReconciler.reconcileForMachine(node, node.getMachineIcon());
        node.setOverclockMode(com.gtceu.calcboard.api.type.OverclockMode.STANDARD);
        page.getGraph().addNode(node);
        harness.getContext().rebuildWidgets();

        NodeHardwareSnapshot baseline = harness.captureHardwareSnapshot("cent-1");

        node.setOverclockMode(com.gtceu.calcboard.api.type.OverclockMode.PERFECT);
        node.markOverclockDirty();
        Assertions.assertEquals(com.gtceu.calcboard.api.type.OverclockMode.PERFECT, node.getOverclockMode());

        baseline.restoreTo(node);
        harness.assertReversible("cent-1", baseline);
        Assertions.assertEquals(com.gtceu.calcboard.api.type.OverclockMode.STANDARD, node.getOverclockMode());
    }
}
