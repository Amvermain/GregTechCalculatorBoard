package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasIdleState;
import com.gtceu.calcboard.testutil.TestFixtures;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Pseudo-random UI event stream fuzzer verifying return-to-idle, buffer cleanliness, and exception immunity.
 */
public class CanvasInteractionFuzzer {

    private static final int DEFAULT_FUZZ_STEPS = 1000;
    private static final long DEFAULT_SEED = 0xCAFE_BABE_0001L;

    public enum UIAction {
        MOUSE_DOWN,
        MOUSE_DRAG,
        MOUSE_UP,
        CLICK,
        KEY_ESC,
        KEY_DELETE,
        SCROLL_WHEEL
    }

    private BoardPage page;
    private CanvasTestHarness harness;

    @BeforeEach
    void setUp() {
        BoardManager.getInstance().resetToDefault();
        page = BoardPage.createDefault("UI Fuzzing Page");
        harness = new CanvasTestHarness(page);
    }

    @AfterEach
    void tearDown() {
        BoardManager.getInstance().resetToDefault();
    }

    @Test
    void testCanvasInteractionFuzzingStream() {
        populateFuzzingGraph();
        runFuzzingSession(DEFAULT_SEED, DEFAULT_FUZZ_STEPS);
    }

    public void runFuzzingSession(long seed, int steps) {
        Random random = new Random(seed);
        List<String> actionHistory = new ArrayList<>(steps);

        try {
            for (int i = 0; i < steps; i++) {
                UIAction action = UIAction.values()[random.nextInt(UIAction.values().length)];
                executeFuzzAction(action, random, actionHistory);
                verifyStepInvariants();
            }

            finalizeFuzzingSession();
        } catch (Throwable t) {
            String historyDump = String.join("\n", actionHistory.subList(Math.max(0, actionHistory.size() - 20), actionHistory.size()));
            throw new AssertionError("CanvasInteractionFuzzer failed with seed " + seed + " at step " + actionHistory.size() + "\nRecent Actions:\n" + historyDump, t);
        }
    }

    private void populateFuzzingGraph() {
        RecipeNode node1 = createNode("node-1", 100, 100);
        RecipeNode node2 = createNode("node-2", 350, 100);
        RecipeNode node3 = createNode("node-3", 200, 300);
        page.getGraph().addNode(node1);
        page.getGraph().addNode(node2);
        page.getGraph().addNode(node3);
        harness.getContext().rebuildWidgets();
    }

    private RecipeNode createNode(String id, double x, double y) {
        RecipeNode node = RecipeNode.create("Fuzz Node " + id, 100.0, 30.0, GTVoltageTier.LV);
        node.setId(id);
        node.setPosX(x);
        node.setPosY(y);
        node.getInputs().add(TestFixtures.item("minecraft:iron_ingot", "Iron Ingot", 1.0));
        node.getOutputs().add(TestFixtures.item("minecraft:iron_nugget", "Iron Nugget", 9.0));
        return node;
    }

    private void executeFuzzAction(UIAction action, Random random, List<String> history) {
        double[] coords = generateRandomCoordinates(random);
        int button = random.nextInt(10) < 7 ? 0 : (random.nextInt(10) < 5 ? 1 : 2);

        switch (action) {
            case MOUSE_DOWN -> {
                harness.mouseDown(coords[0], coords[1], button);
                history.add("MOUSE_DOWN(" + coords[0] + ", " + coords[1] + ", " + button + ")");
            }
            case MOUSE_DRAG -> {
                double dx = (random.nextDouble() - 0.5) * 40.0;
                double dy = (random.nextDouble() - 0.5) * 40.0;
                harness.mouseDrag(coords[0], coords[1], button, dx, dy);
                history.add("MOUSE_DRAG(" + coords[0] + ", " + coords[1] + ", " + button + ")");
            }
            case MOUSE_UP -> {
                harness.mouseUp(coords[0], coords[1], button);
                history.add("MOUSE_UP(" + coords[0] + ", " + coords[1] + ", " + button + ")");
            }
            case CLICK -> {
                harness.click(coords[0], coords[1], button);
                history.add("CLICK(" + coords[0] + ", " + coords[1] + ", " + button + ")");
            }
            case KEY_ESC -> {
                harness.pressKey(GLFW.GLFW_KEY_ESCAPE, 0);
                history.add("KEY_ESC");
            }
            case KEY_DELETE -> {
                harness.pressKey(GLFW.GLFW_KEY_DELETE, 0);
                history.add("KEY_DELETE");
            }
            case SCROLL_WHEEL -> {
                double delta = (random.nextDouble() - 0.5) * 2.0;
                harness.getContext().getWireHandler().handleWireScroll(coords[0], coords[1], delta, harness.getScreen());
                history.add("SCROLL_WHEEL(" + delta + ")");
            }
        }
    }

    private double[] generateRandomCoordinates(Random random) {
        int mode = random.nextInt(10);
        if (mode < 7) {
            return new double[]{(random.nextDouble() - 0.2) * 800.0, (random.nextDouble() - 0.2) * 600.0};
        } else if (mode < 9) {
            List<RecipeNode> nodes = page.getGraph().getNodes();
            if (!nodes.isEmpty()) {
                RecipeNode target = nodes.get(random.nextInt(nodes.size()));
                return new double[]{target.getPosX() + random.nextDouble() * 100.0, target.getPosY() + random.nextDouble() * 50.0};
            }
        }
        return new double[]{(random.nextDouble() - 0.5) * 2_000_000.0, (random.nextDouble() - 0.5) * 2_000_000.0};
    }

    private void verifyStepInvariants() {
        if (harness.getStateMachine().isInState(CanvasIdleState.class)) {
            Assertions.assertTrue(harness.getContext().getDragStartPositions().isEmpty());
            Assertions.assertNull(harness.getContext().getDraggingNode());
            Assertions.assertNull(harness.getContext().getResizingNode());
        }

        for (RecipeNode node : page.getGraph().getNodes()) {
            Assertions.assertFalse(Double.isNaN(node.getPosX()));
            Assertions.assertFalse(Double.isNaN(node.getPosY()));
            Assertions.assertFalse(Double.isInfinite(node.getPosX()));
            Assertions.assertFalse(Double.isInfinite(node.getPosY()));
        }
    }

    private void finalizeFuzzingSession() {
        harness.mouseUp(0, 0, 0);
        harness.mouseUp(0, 0, 1);
        harness.pressKey(GLFW.GLFW_KEY_ESCAPE, 0);

        harness.assertIdle();
        Assertions.assertTrue(harness.getContext().getDragStartPositions().isEmpty());
        Assertions.assertNull(harness.getContext().getDraggingNode());
        Assertions.assertNull(harness.getContext().getResizingNode());
    }
}
