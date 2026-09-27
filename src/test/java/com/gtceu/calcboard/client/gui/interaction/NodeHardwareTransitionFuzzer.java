package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.NodeHardwareReconciler;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.testutil.TestFixtures;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pseudo-random hardware transition fuzzer verifying recipe spec immutability and round-trip reversibility.
 */
public class NodeHardwareTransitionFuzzer {

    private static final int DEFAULT_MUTATION_STEPS = 500;
    private static final long DEFAULT_SEED = 0xDEAD_BEEF_0042L;

    public enum MutationAction {
        CHANGE_MACHINE,
        SET_TIER,
        ATTACH_ADDON,
        DETACH_ADDON,
        SET_PARALLEL,
        TOGGLE_OVERCLOCK
    }

    private static final List<ResourceLocation> SAMPLE_WORKSTATIONS = List.of(
            ResourceLocation.tryParse("gtceu:electric_blast_furnace"),
            ResourceLocation.tryParse("gtceu:mega_electric_blast_furnace"),
            ResourceLocation.tryParse("gtceu:chemical_reactor"),
            ResourceLocation.tryParse("gtceu:large_chemical_reactor"),
            ResourceLocation.tryParse("gtceu:assembler"),
            ResourceLocation.tryParse("gtceu:centrifuge")
    );

    private static final List<GTVoltageTier> SAMPLE_TIERS = List.of(
            GTVoltageTier.LV,
            GTVoltageTier.MV,
            GTVoltageTier.HV,
            GTVoltageTier.EV,
            GTVoltageTier.IV,
            GTVoltageTier.LuV,
            GTVoltageTier.ZPM,
            GTVoltageTier.UV
    );

    private BoardPage page;
    private CanvasTestHarness harness;

    @BeforeEach
    void setUp() {
        BoardManager.getInstance().resetToDefault();
        page = BoardPage.createDefault("HW Fuzzing Page");
        harness = new CanvasTestHarness(page);
    }

    @AfterEach
    void tearDown() {
        BoardManager.getInstance().resetToDefault();
    }

    @Test
    void testNodeHardwareTransitionFuzzing() {
        RecipeNode node = createBaselineNode("target-node");
        page.getGraph().addNode(node);
        harness.getContext().rebuildWidgets();

        runHardwareFuzzingSession("target-node", DEFAULT_SEED, DEFAULT_MUTATION_STEPS);
    }

    public void runHardwareFuzzingSession(String nodeId, long seed, int steps) {
        Random random = new Random(seed);
        List<String> mutationHistory = new ArrayList<>(steps);
        NodeHardwareSnapshot baseline = harness.captureHardwareSnapshot(nodeId);
        RecipeSpec originalSpec = baseline.recipeSpec();

        try {
            for (int i = 0; i < steps; i++) {
                MutationAction action = MutationAction.values()[random.nextInt(MutationAction.values().length)];
                executeMutationAction(nodeId, action, random, mutationHistory);
                verifySixInvariants(nodeId, originalSpec);
            }

            revertToBaseline(nodeId, baseline);
            harness.assertReversible(nodeId, baseline);
            verifySixInvariants(nodeId, originalSpec);
        } catch (Throwable t) {
            String historyDump = String.join("\n", mutationHistory.subList(Math.max(0, mutationHistory.size() - 20), mutationHistory.size()));
            throw new AssertionError("NodeHardwareTransitionFuzzer failed with seed " + seed + " at step " + mutationHistory.size() + "\nRecent Mutations:\n" + historyDump, t);
        }
    }

    private void verifySixInvariants(String nodeId, RecipeSpec originalSpec) {
        RecipeNode node = harness.getNode(nodeId);
        Assertions.assertNotNull(node, "Node must exist");

        Assertions.assertTrue(node.getBaseEUt() > 0, "Base EU/t must be positive");
        Assertions.assertTrue(node.getOverclockResult().eut() > 0, "Overclock EU/t must be positive");
        if (node.isOperational()) {
            Assertions.assertTrue(node.getSingleMachineEUt() > 0, "Operational single machine EU/t must be positive");
            Assertions.assertTrue(node.getTotalEUt() > 0, "Operational total EU/t must be positive");
        } else {
            Assertions.assertEquals(0.0, node.getSingleMachineEUt(), 0.0001, "Non-operational single machine EU/t must be 0");
            Assertions.assertEquals(0.0, node.getTotalEUt(), 0.0001, "Non-operational total EU/t must be 0");
        }

        Assertions.assertTrue(node.getBaseDurationTicks() > 0, "Base duration ticks must be positive");
        Assertions.assertTrue(node.getEffectiveDurationSeconds() > 0, "Effective duration must be positive");

        Assertions.assertTrue(node.getParallel() >= 1, "Parallel must be at least 1");
        if (!node.isMultiblock()) {
            Assertions.assertEquals(1, node.getParallel(), "Singleblock parallel must be clamped to 1");
            Assertions.assertEquals(0, node.getCustomParallel(), "Singleblock custom parallel must be 0");
        }

        harness.assertSpecUnpolluted(nodeId, originalSpec);

        if (!node.isMultiblock()) {
            for (MachineAddon addon : node.getAddons()) {
                Assertions.assertNotEquals(AddonCategory.COIL, addon.getCategory(), "Singleblock cannot have coil addon");
                Assertions.assertNotEquals(AddonCategory.PARALLEL, addon.getCategory(), "Singleblock cannot have parallel addon");
            }
        }

        Assertions.assertEquals(originalSpec.baseInputs().size(), node.getInputs().size(), "Input port count mismatch");
        Assertions.assertEquals(originalSpec.baseOutputs().size(), node.getOutputs().size(), "Output port count mismatch");
    }

    private RecipeNode createBaselineNode(String id) {
        ResourceLocation icon = SAMPLE_WORKSTATIONS.get(0);
        RecipeNode node = RecipeNode.create(icon, "Blast Furnace", 240.0, 120.0, GTVoltageTier.MV);
        node.setId(id);
        NodeHardwareReconciler.reconcileForMachine(node, icon);
        node.setTargetTier(GTVoltageTier.MV);
        node.getInputs().add(TestFixtures.item("minecraft:iron_ingot", "Iron Ingot", 2.0));
        node.getInputs().add(TestFixtures.item("minecraft:coal", "Coal", 1.0));
        node.getOutputs().add(TestFixtures.item("gtceu:steel_ingot", "Steel Ingot", 2.0));
        node.getOutputs().add(TestFixtures.item("gtceu:dark_ash_dust", "Dark Ash Dust", 1.0));
        return node;
    }

    private void executeMutationAction(String nodeId, MutationAction action, Random random, List<String> history) {
        RecipeNode node = harness.getNode(nodeId);

        switch (action) {
            case CHANGE_MACHINE -> {
                ResourceLocation ws = SAMPLE_WORKSTATIONS.get(random.nextInt(SAMPLE_WORKSTATIONS.size()));
                harness.changeMachine(nodeId, ws);
                history.add("CHANGE_MACHINE(" + ws + ")");
            }
            case SET_TIER -> {
                GTVoltageTier tier = SAMPLE_TIERS.get(random.nextInt(SAMPLE_TIERS.size()));
                harness.setVoltageTier(nodeId, tier);
                history.add("SET_TIER(" + tier + ")");
            }
            case ATTACH_ADDON -> {
                MachineAddon addon = createRandomAddon(random);
                harness.attachAddon(nodeId, addon);
                history.add("ATTACH_ADDON(" + addon.getId() + ")");
            }
            case DETACH_ADDON -> {
                if (!node.getAddons().isEmpty()) {
                    AddonCategory cat = node.getAddons().get(random.nextInt(node.getAddons().size())).getCategory();
                    harness.detachAddon(nodeId, cat);
                    history.add("DETACH_ADDON(" + cat + ")");
                } else {
                    history.add("DETACH_ADDON_SKIPPED");
                }
            }
            case SET_PARALLEL -> {
                int par = 1 + random.nextInt(32);
                harness.setParallel(nodeId, par);
                history.add("SET_PARALLEL(" + par + ")");
            }
            case TOGGLE_OVERCLOCK -> {
                com.gtceu.calcboard.api.type.OverclockMode mode = com.gtceu.calcboard.api.type.OverclockMode.values()[random.nextInt(com.gtceu.calcboard.api.type.OverclockMode.values().length)];
                node.setOverclockMode(mode);
                node.markOverclockDirty();
                history.add("SET_OVERCLOCK_MODE(" + mode + ")");
            }
        }
    }

    private MachineAddon createRandomAddon(Random random) {
        int type = random.nextInt(5);
        return switch (type) {
            case 0 -> TestFixtures.createParallelHatch("gtceu:par_" + random.nextInt(64), "Parallel Hatch", 2 + random.nextInt(8), random.nextBoolean());
            case 1 -> new MachineAddon("gtceu:booster_" + random.nextInt(10), "Throughput Booster", AddonCategory.MULTIBLOCK_TRAIT, "2x", null);
            case 2 -> new MachineAddon("gtceu:coil_" + random.nextInt(5), "Heating Coil", AddonCategory.COIL, "1800K", null);
            case 3 -> new MachineAddon("gtceu:maint_" + random.nextInt(5), "Auto Maintenance", AddonCategory.MAINTENANCE, "", null);
            default -> new MachineAddon("gtceu:custom_" + random.nextInt(5), "Custom Module", AddonCategory.CUSTOM, "", null);
        };
    }

    private void revertToBaseline(String nodeId, NodeHardwareSnapshot baseline) {
        RecipeNode node = harness.getNode(nodeId);
        baseline.restoreTo(node);
        NodeHardwareReconciler.clampParallel(node);
        harness.getContext().rebuildWidgets();
    }
}
