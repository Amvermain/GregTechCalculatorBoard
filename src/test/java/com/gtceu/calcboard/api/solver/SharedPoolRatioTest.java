package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MinecraftBootstrapExtension.class)
public class SharedPoolRatioTest {

    @Test
    public void testSharedPoolAutoRatioFractional() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation cutterIcon = ResourceLocation.tryParse("gtceu:lv_cutter");
        ResourceLocation itemId = ResourceLocation.tryParse("gtceu:quartz_geode");

        RecipeNode supplier = RecipeNode.create(ResourceLocation.tryParse("gtceu:miner"), "Miner", 20, 30, GTVoltageTier.LV);
        supplier.setPosX(0);
        supplier.setPosY(0);
        supplier.getOutputs().add(IngredientStack.item(itemId, "Quartz Geode", 1.0));
        supplier.setMachineCount(1.0);
        graph.addNode(supplier);

        RecipeNode node1 = RecipeNode.create(cutterIcon, "Geode A", 20.0, 30.0, GTVoltageTier.LV);
        node1.setPosX(300);
        node1.setPosY(0);
        node1.setMachineCount(0.15);
        node1.getInputs().add(IngredientStack.item(itemId, "Quartz Geode", 1.0));
        graph.addNode(node1);

        RecipeNode node2 = RecipeNode.create(cutterIcon, "Geode B", 20.0, 30.0, GTVoltageTier.LV);
        node2.setPosX(500);
        node2.setPosY(0);
        node2.setMachineCount(0.20);
        graph.addNode(node2);

        RecipeNode node3 = RecipeNode.create(cutterIcon, "Geode C", 20.0, 30.0, GTVoltageTier.LV);
        node3.setPosX(700);
        node3.setPosY(0);
        node3.setMachineCount(0.10);
        graph.addNode(node3);

        graph.addConnection(supplier.getId(), 0, node1.getId(), 0);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Cutter Pool", List.of(node1, node2, node3), CanvasGroupFrame.COLOR_EMERALD);
        frame.setSharedMachineFrame(true);
        frame.setTargetPoolCapacity(1.0);
        graph.addFrame(frame);

        double origDuty = frame.computeTotalMachineDuty(graph);
        Assertions.assertEquals(0.45, origDuty, 0.0001);

        int changed = FlowGraphSolver.autoRatioFromSharedPool(graph, frame, 1.0, AutoRatioMode.FRACTIONAL);
        Assertions.assertTrue(changed >= 3);

        double newDuty = frame.computeTotalMachineDuty(graph);
        Assertions.assertEquals(1.0, newDuty, 0.001);

        double expectedScale = 1.0 / 0.45;
        Assertions.assertEquals(0.15 * expectedScale, node1.getMachineCount(), 0.001);
        Assertions.assertEquals(0.20 * expectedScale, node2.getMachineCount(), 0.001);
        Assertions.assertEquals(0.10 * expectedScale, node3.getMachineCount(), 0.001);
        Assertions.assertEquals(1.0 * expectedScale, supplier.getMachineCount(), 0.001);
    }

    @Test
    public void testSharedPoolAutoRatioIntegerCeil() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation cutterIcon = ResourceLocation.tryParse("gtceu:lv_cutter");
        ResourceLocation itemId = ResourceLocation.tryParse("gtceu:quartz_geode");

        RecipeNode supplier = RecipeNode.create(ResourceLocation.tryParse("gtceu:miner"), "Miner", 20, 30, GTVoltageTier.LV);
        supplier.setPosX(0);
        supplier.setPosY(0);
        supplier.getOutputs().add(IngredientStack.item(itemId, "Quartz Geode", 1.0));
        supplier.setMachineCount(1.0);
        graph.addNode(supplier);

        RecipeNode node1 = RecipeNode.create(cutterIcon, "Geode A", 20.0, 30.0, GTVoltageTier.LV);
        node1.setPosX(300);
        node1.setPosY(0);
        node1.setMachineCount(0.30);
        node1.getInputs().add(IngredientStack.item(itemId, "Quartz Geode", 1.0));
        graph.addNode(node1);

        RecipeNode node2 = RecipeNode.create(cutterIcon, "Geode B", 20.0, 30.0, GTVoltageTier.LV);
        node2.setPosX(500);
        node2.setPosY(0);
        node2.setMachineCount(0.20);
        graph.addNode(node2);

        graph.addConnection(supplier.getId(), 0, node1.getId(), 0);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Cutter Pool", List.of(node1, node2), CanvasGroupFrame.COLOR_EMERALD);
        frame.setSharedMachineFrame(true);
        frame.setTargetPoolCapacity(1.0);
        graph.addFrame(frame);

        int changed = FlowGraphSolver.autoRatioFromSharedPool(graph, frame, 1.0, AutoRatioMode.INTEGER_CEIL);
        Assertions.assertEquals(3, changed);

        Assertions.assertEquals(0.60, node1.getMachineCount(), 0.001);
        Assertions.assertEquals(0.40, node2.getMachineCount(), 0.001);
        Assertions.assertEquals(1.0, frame.computeTotalMachineDuty(graph), 0.001);

        Assertions.assertEquals(2.0, supplier.getMachineCount(), 0.001);
    }

    @Test
    public void testSharedPoolAutoRatioTargetCapacityTwoMachines() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation cutterIcon = ResourceLocation.tryParse("gtceu:lv_cutter");

        RecipeNode node1 = RecipeNode.create(cutterIcon, "Geode A", 20.0, 30.0, GTVoltageTier.LV);
        node1.setPosX(300);
        node1.setPosY(0);
        node1.setMachineCount(0.30);
        graph.addNode(node1);

        RecipeNode node2 = RecipeNode.create(cutterIcon, "Geode B", 20.0, 30.0, GTVoltageTier.LV);
        node2.setPosX(500);
        node2.setPosY(0);
        node2.setMachineCount(0.20);
        graph.addNode(node2);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Cutter Pool", List.of(node1, node2), CanvasGroupFrame.COLOR_EMERALD);
        frame.setSharedMachineFrame(true);
        frame.setTargetPoolCapacity(2.0);
        graph.addFrame(frame);

        int changed = FlowGraphSolver.autoRatioFromSharedPool(graph, frame, 2.0, AutoRatioMode.FRACTIONAL);
        Assertions.assertEquals(2, changed);

        Assertions.assertEquals(1.20, node1.getMachineCount(), 0.001);
        Assertions.assertEquals(0.80, node2.getMachineCount(), 0.001);
        Assertions.assertEquals(2.0, frame.computeTotalMachineDuty(graph), 0.001);
        Assertions.assertEquals(2, frame.computeRequiredMachines(graph));
    }

    @Test
    public void testSharedPoolEmptyOrZeroDutyDefense() {
        FlowGraph graph = new FlowGraph();
        CanvasGroupFrame emptyFrame = new CanvasGroupFrame("empty", "Empty Pool", CanvasGroupFrame.COLOR_BLUE, 0, 0, 200, 200);
        emptyFrame.setSharedMachineFrame(true);
        graph.addFrame(emptyFrame);

        int resultEmpty = FlowGraphSolver.autoRatioFromSharedPool(graph, emptyFrame, 1.0, AutoRatioMode.FRACTIONAL);
        Assertions.assertEquals(0, resultEmpty);

        RecipeNode rerouteNode = RecipeNode.createReroute(50.0, 50.0);
        graph.addNode(rerouteNode);
        emptyFrame.addNode(rerouteNode.getId());

        int resultRerouteOnly = FlowGraphSolver.autoRatioFromSharedPool(graph, emptyFrame, 1.0, AutoRatioMode.FRACTIONAL);
        Assertions.assertEquals(0, resultRerouteOnly);
    }

    @Test
    public void testSharedPoolUndoRedoIntegrity() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation icon = ResourceLocation.tryParse("gtceu:lv_cutter");

        RecipeNode node1 = RecipeNode.create(icon, "Recipe A", 20.0, 30.0, GTVoltageTier.LV);
        node1.setPosX(300);
        node1.setPosY(0);
        node1.setMachineCount(0.25);
        graph.addNode(node1);

        RecipeNode node2 = RecipeNode.create(icon, "Recipe B", 20.0, 30.0, GTVoltageTier.LV);
        node2.setPosX(500);
        node2.setPosY(0);
        node2.setMachineCount(0.25);
        graph.addNode(node2);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Pool", List.of(node1, node2), CanvasGroupFrame.COLOR_BLUE);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        Map<String, Double> oldCounts = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            oldCounts.put(n.getId(), n.getMachineCount());
        }

        FlowGraphSolver.autoRatioFromSharedPool(graph, frame, 1.0, AutoRatioMode.FRACTIONAL);
        Assertions.assertEquals(0.50, node1.getMachineCount(), 0.001);
        Assertions.assertEquals(0.50, node2.getMachineCount(), 0.001);

        List<BoardCommand> subCmds = new ArrayList<>();
        for (RecipeNode n : graph.getNodes()) {
            subCmds.add(BoardCommand.ModifyPropertyCommand.machineCount(n.getId(), oldCounts.get(n.getId()), n.getMachineCount()));
        }
        BoardCommand.CompoundCommand compound = new BoardCommand.CompoundCommand(subCmds, "Pool Ratio");

        compound.undo(graph);
        Assertions.assertEquals(0.25, node1.getMachineCount(), 0.001);
        Assertions.assertEquals(0.25, node2.getMachineCount(), 0.001);

        compound.redo(graph);
        Assertions.assertEquals(0.50, node1.getMachineCount(), 0.001);
        Assertions.assertEquals(0.50, node2.getMachineCount(), 0.001);
    }

    @Test
    public void testCanvasGroupFrameTargetCapacitySerialization() {
        CanvasGroupFrame frame = new CanvasGroupFrame("test-id", "Custom Pool", CanvasGroupFrame.COLOR_PURPLE, 10, 20, 300, 200);
        frame.setSharedMachineFrame(true);
        frame.setTargetPoolCapacity(3.5);

        CompoundTag tag = frame.serializeNBT();
        CanvasGroupFrame deserialized = CanvasGroupFrame.deserializeNBT(tag);

        Assertions.assertTrue(deserialized.isSharedMachineFrame());
        Assertions.assertEquals(3.5, deserialized.getTargetPoolCapacity(), 0.0001);

        CanvasGroupFrame copied = frame.copy();
        Assertions.assertTrue(copied.isSharedMachineFrame());
        Assertions.assertEquals(3.5, copied.getTargetPoolCapacity(), 0.0001);
    }
}
