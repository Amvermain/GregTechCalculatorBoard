package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.history.HistoryManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class HistoryTest {

    private FlowGraph graph;
    private HistoryManager historyManager;

    @BeforeEach
    public void setup() {
        graph = new FlowGraph();
        historyManager = new HistoryManager();
    }

    @Test
    public void testMoveNodesUndoRedo() {
        RecipeNode node = RecipeNode.create("Test Node", 100.0, 30.0, GTVoltageTier.LV);
        node.setPos(50.0, 50.0);
        graph.addNode(node);

        // Record a move delta of (+20, +30)
        Map<String, double[]> deltas = Map.of(node.getId(), new double[]{20.0, 30.0});
        node.setPos(70.0, 80.0);
        historyManager.record(new BoardCommand.MoveNodesCommand(deltas));

        assertEquals(70.0, node.getPosX(), 0.001);
        assertEquals(80.0, node.getPosY(), 0.001);

        // Undo
        historyManager.undo(graph);
        assertEquals(50.0, node.getPosX(), 0.001);
        assertEquals(50.0, node.getPosY(), 0.001);

        // Redo
        historyManager.redo(graph);
        assertEquals(70.0, node.getPosX(), 0.001);
        assertEquals(80.0, node.getPosY(), 0.001);
    }

    @Test
    public void testAddRemoveNodesUndoRedo() {
        RecipeNode node1 = RecipeNode.create("Node 1", 100.0, 30.0, GTVoltageTier.LV);
        RecipeNode node2 = RecipeNode.create("Node 2", 100.0, 30.0, GTVoltageTier.LV);
        node1.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0, 1.0));
        node2.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0, 1.0));

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addConnection(node1.getId(), 0, node2.getId(), 0);

        // Remove node2 with its wire
        FlowGraph.ConnectionEdge edge = graph.getConnections().get(0);
        graph.removeNode(node2);
        historyManager.record(new BoardCommand.RemoveNodesCommand(List.of(node2), List.of(edge), "Remove Node 2"));

        assertEquals(1, graph.getNodes().size());
        assertEquals(0, graph.getConnections().size());

        // Undo -> node2 and edge restored
        historyManager.undo(graph);
        assertEquals(2, graph.getNodes().size());
        assertEquals(1, graph.getConnections().size());
        assertEquals(node1.getId(), graph.getConnections().get(0).fromNodeId());

        // Redo -> removed again
        historyManager.redo(graph);
        assertEquals(1, graph.getNodes().size());
        assertEquals(0, graph.getConnections().size());
    }

    @Test
    public void testWireConnectWithShiftScalingUndoRedo() {
        RecipeNode producer = RecipeNode.create("Producer", 100.0, 20.0, GTVoltageTier.LV);
        RecipeNode consumer = RecipeNode.create("Consumer", 100.0, 20.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 500.0, 1.0));
        consumer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 100.0, 1.0));
        consumer.setMachineCount(1.0);

        graph.addNode(producer);
        graph.addNode(consumer);

        // Shift Connect: scale consumer to 5.0
        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(producer.getId(), 0, consumer.getId(), 0);
        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);
        consumer.setMachineCount(5.0);
        historyManager.record(new BoardCommand.ConnectWireCommand(edge, consumer.getId(), 1.0, 5.0));

        assertEquals(1, graph.getConnections().size());
        assertEquals(5.0, consumer.getMachineCount(), 0.001);

        // Undo -> wire removed and count restored to 1.0
        historyManager.undo(graph);
        assertEquals(0, graph.getConnections().size());
        assertEquals(1.0, consumer.getMachineCount(), 0.001);

        // Redo -> wire reconnected and count restored to 5.0
        historyManager.redo(graph);
        assertEquals(1, graph.getConnections().size());
        assertEquals(5.0, consumer.getMachineCount(), 0.001);
    }

    @Test
    public void testPropertyModificationUndoRedo() {
        RecipeNode node = RecipeNode.create("Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        graph.addNode(node);

        // Modify Machine Count (1.0 -> 8.0)
        node.setMachineCount(8.0);
        historyManager.record(BoardCommand.ModifyPropertyCommand.machineCount(node.getId(), 1.0, 8.0));

        // Modify Custom Name
        node.setName("Sulfuric Acid Line 1");
        historyManager.record(BoardCommand.ModifyPropertyCommand.customName(node.getId(), "Chemical Reactor", "Sulfuric Acid Line 1"));

        // Modify Tier (LV -> HV)
        node.setTargetTier(GTVoltageTier.HV);
        historyManager.record(BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), GTVoltageTier.LV, GTVoltageTier.HV));

        // Modify OverclockMode (STANDARD -> PERFECT)
        node.setOverclockMode(com.gtceu.calcboard.api.OverclockMode.PERFECT);
        historyManager.record(BoardCommand.ModifyPropertyCommand.overclockMode(node.getId(), com.gtceu.calcboard.api.OverclockMode.STANDARD, com.gtceu.calcboard.api.OverclockMode.PERFECT));

        // Modify Parallel (1 -> 16)
        node.setParallel(16);
        historyManager.record(BoardCommand.ModifyPropertyCommand.parallel(node.getId(), 1, 16));

        // Modify Base Anchor (false -> true)
        node.setBaseNode(true);
        historyManager.record(BoardCommand.ModifyPropertyCommand.baseAnchor(node.getId(), false, true));

        // Modify Rotor Efficiency (100 -> 220)
        node.setRotorEfficiency(220);
        historyManager.record(BoardCommand.ModifyPropertyCommand.rotorEfficiency(node.getId(), 100, 220));

        assertEquals(220, node.getRotorEfficiency());
        assertTrue(node.isBaseNode());
        assertEquals(16, node.getParallel());
        assertEquals(com.gtceu.calcboard.api.OverclockMode.PERFECT, node.getOverclockMode());
        assertEquals(GTVoltageTier.HV, node.getTargetTier());
        assertEquals("Sulfuric Acid Line 1", node.getName());
        assertEquals(8.0, node.getMachineCount(), 0.001);

        // Undo Rotor
        historyManager.undo(graph);
        assertEquals(100, node.getRotorEfficiency());

        // Undo Base Anchor
        historyManager.undo(graph);
        assertFalse(node.isBaseNode());

        // Undo Parallel
        historyManager.undo(graph);
        assertEquals(1, node.getParallel());

        // Undo OC
        historyManager.undo(graph);
        assertEquals(com.gtceu.calcboard.api.OverclockMode.STANDARD, node.getOverclockMode());

        // Undo Tier
        historyManager.undo(graph);
        assertEquals(GTVoltageTier.LV, node.getTargetTier());

        // Undo Name
        historyManager.undo(graph);
        assertEquals("Chemical Reactor", node.getName());
        assertEquals(8.0, node.getMachineCount(), 0.001);

        // Undo Count
        historyManager.undo(graph);
        assertEquals("Chemical Reactor", node.getName());
        assertEquals(1.0, node.getMachineCount(), 0.001);

        // Redo all
        historyManager.redo(graph); // Count
        assertEquals(8.0, node.getMachineCount(), 0.001);

        historyManager.redo(graph); // Name
        assertEquals("Sulfuric Acid Line 1", node.getName());

        historyManager.redo(graph); // Tier
        assertEquals(GTVoltageTier.HV, node.getTargetTier());

        historyManager.redo(graph); // OC
        assertEquals(com.gtceu.calcboard.api.OverclockMode.PERFECT, node.getOverclockMode());

        historyManager.redo(graph); // Parallel
        assertEquals(16, node.getParallel());

        historyManager.redo(graph); // Base Anchor
        assertTrue(node.isBaseNode());

        historyManager.redo(graph); // Rotor
        assertEquals(220, node.getRotorEfficiency());
    }

    @Test
    public void testGroupAndExpandModuleUndoRedo() {
        RecipeNode nodeA = RecipeNode.create("Node A", 100.0, 20.0, GTVoltageTier.LV);
        RecipeNode nodeB = RecipeNode.create("Node B", 100.0, 20.0, GTVoltageTier.LV);
        nodeA.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:raw_a"), "Raw A", 10.0, 1.0));
        nodeB.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:raw_a"), "Raw A", 10.0, 1.0));
        nodeB.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:final_b"), "Final B", 5.0, 1.0));

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addConnection(nodeA.getId(), 0, nodeB.getId(), 0);

        List<RecipeNode> origNodes = new ArrayList<>(graph.getNodes());
        List<FlowGraph.ConnectionEdge> origEdges = new ArrayList<>(graph.getConnections());

        RecipeNode module = graph.groupIntoModule("Compound Process");
        assertNotNull(module);
        assertEquals(1, graph.getNodes().size());

        historyManager.record(new BoardCommand.GroupModuleCommand(origNodes, module, origEdges, List.of()));

        // Undo Module grouping -> restores Node A and Node B
        historyManager.undo(graph);
        assertEquals(2, graph.getNodes().size());
        assertEquals(1, graph.getConnections().size());
        assertFalse(graph.getNodes().get(0).isModule());

        // Redo Module grouping -> back to single module
        historyManager.redo(graph);
        assertEquals(1, graph.getNodes().size());
        assertTrue(graph.getNodes().get(0).isModule());
    }

    @Test
    public void testPerTabHistoryIndependence() {
        BoardPage page1 = BoardPage.createDefault("Page 1");
        BoardPage page2 = BoardPage.createDefault("Page 2");

        RecipeNode node1 = RecipeNode.create("Node in P1", 100.0, 30.0, GTVoltageTier.LV);
        page1.getGraph().addNode(node1);
        page1.getHistoryManager().record(new BoardCommand.AddNodesCommand(node1, "Add to P1"));

        RecipeNode node2 = RecipeNode.create("Node in P2", 100.0, 30.0, GTVoltageTier.LV);
        page2.getGraph().addNode(node2);
        page2.getHistoryManager().record(new BoardCommand.AddNodesCommand(node2, "Add to P2"));

        // Undo in Page 1 -> only affects Page 1
        page1.getHistoryManager().undo(page1.getGraph());
        assertEquals(0, page1.getGraph().getNodes().size());
        assertEquals(1, page2.getGraph().getNodes().size());

        // Page 2 undo -> only affects Page 2
        page2.getHistoryManager().undo(page2.getGraph());
        assertEquals(0, page2.getGraph().getNodes().size());
    }

    @Test
    public void testTierChangePreservesMachineCountAndSupportsUndo() {
        RecipeNode node = RecipeNode.create("Large Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        node.setMachineCount(1.0);
        graph.addNode(node);

        // Change Tier from LV to LuV (should NOT modify machine count)
        GTVoltageTier oldTier = node.getTargetTier();
        GTVoltageTier newTier = GTVoltageTier.LuV;
        node.setTargetTier(newTier);
        historyManager.record(BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, newTier));

        assertEquals(1.0, node.getMachineCount(), 0.0001, "Machine count must not be halved or altered when changing voltage tier");
        assertEquals(GTVoltageTier.LuV, node.getTargetTier());

        // Undo
        historyManager.undo(graph);
        assertEquals(GTVoltageTier.LV, node.getTargetTier());
        assertEquals(1.0, node.getMachineCount(), 0.0001);

        // Redo
        historyManager.redo(graph);
        assertEquals(GTVoltageTier.LuV, node.getTargetTier());
        assertEquals(1.0, node.getMachineCount(), 0.0001);
    }
}
