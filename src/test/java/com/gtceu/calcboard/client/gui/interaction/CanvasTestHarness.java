package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.catalog.AddonCategory;
import com.gtceu.calcboard.api.catalog.MachineAddon;
import com.gtceu.calcboard.api.model.NodeHardwareReconciler;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.model.RecipeSpec;
import com.gtceu.calcboard.api.spi.IModAdapter;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasIdleState;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasInteractionState;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasStateMachine;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Headless test harness providing fluent APIs for simulated canvas gestures and hardware transition checks.
 */
public final class CanvasTestHarness {

    private final CanvasStateMachine stateMachine;
    private final TestInteractionContext context;
    private final BoardPage activePage;

    private double lastMouseX = 0;
    private double lastMouseY = 0;

    public CanvasTestHarness() {
        this(BoardPage.createDefault("Harness Page"));
    }

    public CanvasTestHarness(BoardPage page) {
        this.activePage = page != null ? page : BoardManager.getInstance().getActivePage();
        this.context = new TestInteractionContext(this.activePage);
        this.stateMachine = new CanvasStateMachine(this.context);
        this.context.rebuildWidgets();
    }

    public CanvasTestHarness mouseDown(double canvasX, double canvasY, int button) {
        this.lastMouseX = canvasX;
        this.lastMouseY = canvasY;
        stateMachine.dispatchMouseDown(canvasX, canvasY, button);
        return this;
    }

    public CanvasTestHarness mouseDrag(double canvasX, double canvasY, int button, double dx, double dy) {
        this.lastMouseX = canvasX;
        this.lastMouseY = canvasY;
        stateMachine.dispatchMouseDrag(canvasX, canvasY, button, dx, dy);
        return this;
    }

    public CanvasTestHarness mouseDrag(double canvasX, double canvasY) {
        return mouseDrag(canvasX, canvasY, 0, canvasX - lastMouseX, canvasY - lastMouseY);
    }

    public CanvasTestHarness mouseUp(double canvasX, double canvasY, int button) {
        this.lastMouseX = canvasX;
        this.lastMouseY = canvasY;
        stateMachine.dispatchMouseUp(canvasX, canvasY, button);
        return this;
    }

    public CanvasTestHarness mouseUp() {
        return mouseUp(lastMouseX, lastMouseY, 0);
    }

    public CanvasTestHarness click(double canvasX, double canvasY, int button) {
        mouseDown(canvasX, canvasY, button);
        mouseUp(canvasX, canvasY, button);
        return this;
    }

    public CanvasTestHarness pressKey(int keyCode, int modifiers) {
        stateMachine.dispatchKeyPressed(keyCode, 0, modifiers);
        return this;
    }

    public CanvasTestHarness pressKey(int keyCode) {
        return pressKey(keyCode, 0);
    }

    public CanvasTestHarness boxSelect(double startX, double startY, double endX, double endY) {
        mouseDown(startX, startY, 0);
        mouseDrag(endX, endY, 0, endX - startX, endY - startY);
        mouseUp(endX, endY, 0);
        return this;
    }

    public CanvasTestHarness startDragNode(String nodeId) {
        NodeWidget widget = getWidget(nodeId);
        if (widget != null) {
            double startX = widget.getNode().getPosX() + 30;
            double startY = widget.getNode().getPosY() + 10;
            mouseDown(startX, startY, 0);
        }
        return this;
    }

    public CanvasTestHarness dragNode(String nodeId, double deltaX, double deltaY) {
        NodeWidget widget = getWidget(nodeId);
        if (widget != null) {
            double startX = widget.getNode().getPosX() + 30;
            double startY = widget.getNode().getPosY() + 10;
            mouseDown(startX, startY, 0);
            mouseDrag(startX + deltaX, startY + deltaY, 0, deltaX, deltaY);
            mouseUp(startX + deltaX, startY + deltaY, 0);
        }
        return this;
    }

    public CanvasTestHarness startWire(String nodeId, int portIndex) {
        return startWire(nodeId, portIndex, false);
    }

    public CanvasTestHarness startWire(String nodeId, int portIndex, boolean isInput) {
        double[] center = getPortCenter(nodeId, isInput, portIndex);
        mouseDown(center[0], center[1], 0);
        return this;
    }

    public CanvasTestHarness connectWire(String sourceNodeId, int sourcePort, String targetNodeId, int targetPort) {
        double[] src = getPortCenter(sourceNodeId, false, sourcePort);
        double[] tgt = getPortCenter(targetNodeId, true, targetPort);
        mouseDown(src[0], src[1], 0);
        mouseDrag(tgt[0], tgt[1], 0, tgt[0] - src[0], tgt[1] - src[1]);
        mouseUp(tgt[0], tgt[1], 0);
        return this;
    }

    public CanvasTestHarness changeMachine(String nodeId, ResourceLocation newWorkstation) {
        RecipeNode node = getNode(nodeId);
        if (node != null && newWorkstation != null) {
            NodeHardwareReconciler.reconcileForMachine(node, newWorkstation);
            context.rebuildWidgets();
        }
        return this;
    }

    public CanvasTestHarness setVoltageTier(String nodeId, GTVoltageTier tier) {
        RecipeNode node = getNode(nodeId);
        if (node != null && tier != null) {
            node.setTargetTier(tier);
            NodeHardwareReconciler.clampParallel(node);
            node.markOverclockDirty();
            context.rebuildWidgets();
        }
        return this;
    }

    public CanvasTestHarness attachAddon(String nodeId, MachineAddon addon) {
        RecipeNode node = getNode(nodeId);
        if (node != null && addon != null) {
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            if (adapter != null) {
                if (adapter.canInstallAddon(node, addon)) {
                    adapter.handleInstallAddon(node, addon, false);
                }
            } else {
                node.getAddons().add(addon.copy());
                node.markOverclockDirty();
            }
            NodeHardwareReconciler.purgeIncompatibleAddons(node);
            NodeHardwareReconciler.clampParallel(node);
            context.rebuildWidgets();
        }
        return this;
    }

    public CanvasTestHarness detachAddon(String nodeId, AddonCategory category) {
        RecipeNode node = getNode(nodeId);
        if (node != null && category != null) {
            IModAdapter adapter = ModAdapterRegistry.getAdapterForNode(node);
            List<MachineAddon> toRemove = new ArrayList<>();
            for (MachineAddon addon : node.getAddons()) {
                if (addon.getCategory() == category) {
                    toRemove.add(addon);
                }
            }
            for (MachineAddon addon : toRemove) {
                if (adapter != null) {
                    adapter.handleUninstallAddon(node, addon);
                } else {
                    node.getAddons().remove(addon);
                    node.markOverclockDirty();
                }
            }
            NodeHardwareReconciler.clampParallel(node);
            context.rebuildWidgets();
        }
        return this;
    }

    public CanvasTestHarness setParallel(String nodeId, int parallel) {
        RecipeNode node = getNode(nodeId);
        if (node != null) {
            node.setParallel(parallel);
            node.setCustomParallel(parallel > 1 ? parallel : 0);
            NodeHardwareReconciler.clampParallel(node);
            node.markOperationalDirty();
            node.markOverclockDirty();
            context.rebuildWidgets();
        }
        return this;
    }

    public NodeHardwareSnapshot captureHardwareSnapshot(String nodeId) {
        RecipeNode node = getNode(nodeId);
        Assertions.assertNotNull(node, "Node not found for snapshot: " + nodeId);
        return NodeHardwareSnapshot.capture(node);
    }

    public CanvasTestHarness assertReversible(String nodeId, NodeHardwareSnapshot baseline) {
        RecipeNode node = getNode(nodeId);
        Assertions.assertNotNull(node, "Node not found: " + nodeId);
        Assertions.assertEquals(baseline.machineIcon(), node.getMachineIcon(), "Machine icon mismatch");
        Assertions.assertEquals(baseline.targetTier(), node.getTargetTier(), "Voltage tier mismatch");
        Assertions.assertEquals(baseline.recipeTier(), node.getRecipeTier(), "Recipe tier mismatch");
        Assertions.assertEquals(baseline.parallel(), node.getParallel(), "Parallel mismatch");
        Assertions.assertEquals(baseline.customParallel(), node.getCustomParallel(), "Custom parallel mismatch");
        Assertions.assertEquals(baseline.isMultiblock(), node.isMultiblock(), "Multiblock mismatch");
        Assertions.assertEquals(baseline.overclockMode(), node.getOverclockMode(), "Overclock mode mismatch");
        Assertions.assertEquals(baseline.addons().size(), node.getAddons().size(), "Addons count mismatch");
        Assertions.assertEquals(baseline.baseDurationTicks(), node.getBaseDurationTicks(), 0.0001, "Base duration ticks mismatch");
        Assertions.assertEquals(baseline.baseEUt(), node.getBaseEUt(), 0.0001, "Base EUt mismatch");
        Assertions.assertEquals(baseline.singleMachinePower(), node.getSingleMachineEUt(), 0.0001, "Single machine power mismatch");
        Assertions.assertEquals(baseline.totalPower(), node.getTotalEUt(), 0.0001, "Total power mismatch");
        Assertions.assertEquals(baseline.effectiveDurationSeconds(), node.getEffectiveDurationSeconds(), 0.0001, "Effective duration mismatch");
        Assertions.assertEquals(baseline.inputAmounts().size(), node.getInputs().size(), "Input counts mismatch");
        for (int i = 0; i < baseline.inputAmounts().size(); i++) {
            Assertions.assertEquals(baseline.inputAmounts().get(i), node.getInputs().get(i).getAmount(), 0.0001, "Input amount mismatch at " + i);
        }
        Assertions.assertEquals(baseline.outputAmounts().size(), node.getOutputs().size(), "Output counts mismatch");
        for (int i = 0; i < baseline.outputAmounts().size(); i++) {
            Assertions.assertEquals(baseline.outputAmounts().get(i), node.getOutputs().get(i).getAmount(), 0.0001, "Output amount mismatch at " + i);
        }
        assertSpecUnpolluted(nodeId, baseline.recipeSpec());
        return this;
    }

    public CanvasTestHarness assertSpecUnpolluted(String nodeId, RecipeSpec expectedOriginalSpec) {
        RecipeNode node = getNode(nodeId);
        Assertions.assertNotNull(node, "Node not found: " + nodeId);
        RecipeSpec currentSpec = node.getBaseSpec();
        Assertions.assertNotNull(currentSpec, "Current spec is null for node: " + nodeId);
        if (expectedOriginalSpec != null) {
            Assertions.assertEquals(expectedOriginalSpec.baseDurationTicks(), currentSpec.baseDurationTicks(), 0.0001, "Spec duration mismatch");
            Assertions.assertEquals(expectedOriginalSpec.baseEUt(), currentSpec.baseEUt(), 0.0001, "Spec EUt mismatch");
            Assertions.assertEquals(expectedOriginalSpec.recipeId(), currentSpec.recipeId(), "Spec recipeId mismatch");
            Assertions.assertEquals(expectedOriginalSpec.categoryId(), currentSpec.categoryId(), "Spec categoryId mismatch");
            Assertions.assertEquals(expectedOriginalSpec.baseInputs().size(), currentSpec.baseInputs().size(), "Spec baseInputs count mismatch");
            for (int i = 0; i < expectedOriginalSpec.baseInputs().size(); i++) {
                Assertions.assertEquals(
                        expectedOriginalSpec.baseInputs().get(i).getAmount(),
                        currentSpec.baseInputs().get(i).getAmount(),
                        0.0001,
                        "Input amount mismatch at index " + i
                );
            }
            Assertions.assertEquals(expectedOriginalSpec.baseOutputs().size(), currentSpec.baseOutputs().size(), "Spec baseOutputs count mismatch");
            for (int i = 0; i < expectedOriginalSpec.baseOutputs().size(); i++) {
                Assertions.assertEquals(
                        expectedOriginalSpec.baseOutputs().get(i).getAmount(),
                        currentSpec.baseOutputs().get(i).getAmount(),
                        0.0001,
                        "Output amount mismatch at index " + i
                );
            }
        }
        return this;
    }

    public CanvasTestHarness assertState(Class<? extends CanvasInteractionState> expectedStateClass) {
        Assertions.assertTrue(
                stateMachine.isInState(expectedStateClass),
                "Expected state: " + expectedStateClass.getSimpleName() + ", but was: " + stateMachine.getCurrentState().getClass().getSimpleName()
        );
        return this;
    }

    public CanvasTestHarness assertIdle() {
        return assertState(CanvasIdleState.class);
    }

    public double[] getPortCenter(String nodeId, boolean isInput, int portIndex) {
        NodeWidget widget = getWidget(nodeId);
        if (widget == null) return new double[]{0, 0};
        var port = widget.getLayoutBounds().findPort(isInput, portIndex);
        if (port != null) {
            return new double[]{
                    port.hitBox().x() + port.hitBox().width() / 2.0,
                    port.hitBox().y() + port.hitBox().height() / 2.0
            };
        }
        return new double[]{
                isInput ? widget.getInputPortX(portIndex) : widget.getOutputPortX(portIndex),
                isInput ? widget.getInputPortY(portIndex) : widget.getOutputPortY(portIndex)
        };
    }

    public RecipeNode getNode(String nodeId) {
        return activePage.getGraph().findNodeById(nodeId);
    }

    public NodeWidget getWidget(String nodeId) {
        if (context.getScreen() == null) return null;
        return context.getScreen().findWidgetByNodeId(nodeId);
    }

    public CanvasStateMachine getStateMachine() {
        return stateMachine;
    }

    public TestInteractionContext getContext() {
        return context;
    }

    public BoardPage getActivePage() {
        return activePage;
    }

    public BoardScreen getScreen() {
        return context.getScreen();
    }
}
