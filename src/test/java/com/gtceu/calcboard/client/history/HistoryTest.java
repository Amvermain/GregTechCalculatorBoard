package com.gtceu.calcboard.client.history;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.history.HistoryManager;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
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
        node.setOverclockMode(com.gtceu.calcboard.api.type.OverclockMode.PERFECT);
        historyManager.record(BoardCommand.ModifyPropertyCommand.overclockMode(node.getId(), com.gtceu.calcboard.api.type.OverclockMode.STANDARD, com.gtceu.calcboard.api.type.OverclockMode.PERFECT));

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
        assertEquals(com.gtceu.calcboard.api.type.OverclockMode.PERFECT, node.getOverclockMode());
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
        assertEquals(com.gtceu.calcboard.api.type.OverclockMode.STANDARD, node.getOverclockMode());

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
        assertEquals(com.gtceu.calcboard.api.type.OverclockMode.PERFECT, node.getOverclockMode());

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

    @Test
    public void testSetMachineIconCommandUndoRedo() {
        ResourceLocation singleWs = ResourceLocation.tryParse("gtceu:lv_chemical_reactor");
        ResourceLocation multiWs = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        RecipeNode node = RecipeNode.create(singleWs, "Chemical Reactor", 100.0, 30.0, GTVoltageTier.LV);
        node.getAvailableWorkstations().add(multiWs);
        node.setMultiblock(false);
        node.setParallel(1);
        graph.addNode(node);

        // Record switching from singleblock to multiblock controller
        ResourceLocation oldIcon = node.getMachineIcon();
        boolean oldMb = node.isMultiblock();
        int oldPar = node.getParallel();
        var oldSteam = node.getSteamMode();
        var oldTier = node.getTargetTier();

        node.setMachineIcon(multiWs);
        node.setMultiblock(true);
        node.setParallel(8);

        historyManager.record(new BoardCommand.SetMachineIconCommand(node, oldIcon, multiWs, oldMb, oldPar, oldSteam, oldTier));

        assertEquals(multiWs, node.getMachineIcon());
        assertTrue(node.isMultiblock());
        assertEquals(8, node.getParallel());

        // Undo
        historyManager.undo(graph);
        assertEquals(singleWs, node.getMachineIcon());
        assertFalse(node.isMultiblock());
        assertEquals(1, node.getParallel());

        // Redo
        historyManager.redo(graph);
        assertEquals(multiWs, node.getMachineIcon());
        assertTrue(node.isMultiblock());
        assertEquals(8, node.getParallel());
    }

    @Test
    public void testDisconnectWireUndoRedo() {
        RecipeNode node1 = RecipeNode.create("Node 1", 100.0, 30.0, GTVoltageTier.LV);
        RecipeNode node2 = RecipeNode.create("Node 2", 200.0, 30.0, GTVoltageTier.LV);
        node1.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0, 1.0));
        node2.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 100.0, 1.0));

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addConnection(node1.getId(), 0, node2.getId(), 0);
        assertEquals(1, graph.getConnections().size());

        FlowGraph.ConnectionEdge edge = graph.getConnections().get(0);
        graph.getConnections().remove(edge);
        historyManager.record(new BoardCommand.DisconnectWireCommand(edge));
        assertEquals(0, graph.getConnections().size());

        historyManager.undo(graph);
        assertEquals(1, graph.getConnections().size());
        assertEquals(edge, graph.getConnections().get(0));

        historyManager.redo(graph);
        assertEquals(0, graph.getConnections().size());
    }

    @Test
    public void testGroupFrameCreationUndoRedo() {
        CanvasGroupFrame frame = new CanvasGroupFrame("frame1", "Group A", CanvasGroupFrame.COLOR_BLUE, 100, 100, 300, 200);
        graph.addFrame(frame);
        historyManager.record(new BoardCommand.AddFramesCommand(frame, "Create Group Frame"));
        assertEquals(1, graph.getFrames().size());

        historyManager.undo(graph);
        assertEquals(0, graph.getFrames().size());

        historyManager.redo(graph);
        assertEquals(1, graph.getFrames().size());
        assertEquals("frame1", graph.getFrames().get(0).getId());
    }

    @Test
    public void testStickyNoteCreationUndoRedo() {
        CanvasStickyNote note = CanvasStickyNote.create("Note 1", "Content", CanvasStickyNote.COLOR_AMBER, 50, 50);
        graph.addStickyNote(note);
        historyManager.record(new BoardCommand.AddStickyNotesCommand(note, "Create Sticky Note"));
        assertEquals(1, graph.getStickyNotes().size());

        historyManager.undo(graph);
        assertEquals(0, graph.getStickyNotes().size());

        historyManager.redo(graph);
        assertEquals(1, graph.getStickyNotes().size());
        assertEquals(note.getId(), graph.getStickyNotes().get(0).getId());
    }

    @Test
    public void testModifyFramePropertiesUndoRedo() {
        CanvasGroupFrame frame = new CanvasGroupFrame("f1", "Initial Title", CanvasGroupFrame.COLOR_BLUE, 0, 0, 200, 150);
        frame.setSharedMachineFrame(false);
        graph.addFrame(frame);

        frame.setTitle("Updated Title");
        frame.setColor(CanvasGroupFrame.COLOR_EMERALD);
        frame.setSharedMachineFrame(true);

        historyManager.record(new BoardCommand.ModifyFramePropertiesCommand(
                "f1",
                "Initial Title", "Updated Title",
                CanvasGroupFrame.COLOR_BLUE, CanvasGroupFrame.COLOR_EMERALD,
                false, true
        ));

        assertEquals("Updated Title", frame.getTitle());
        assertEquals(CanvasGroupFrame.COLOR_EMERALD, frame.getColor());
        assertTrue(frame.isSharedMachineFrame());

        historyManager.undo(graph);
        assertEquals("Initial Title", frame.getTitle());
        assertEquals(CanvasGroupFrame.COLOR_BLUE, frame.getColor());
        assertFalse(frame.isSharedMachineFrame());

        historyManager.redo(graph);
        assertEquals("Updated Title", frame.getTitle());
        assertEquals(CanvasGroupFrame.COLOR_EMERALD, frame.getColor());
        assertTrue(frame.isSharedMachineFrame());
    }

    @Test
    public void testModifyNotePropertiesUndoRedo() {
        CanvasStickyNote note = CanvasStickyNote.create("Old Title", "Old Text", CanvasStickyNote.COLOR_AMBER, 10, 10);
        graph.addStickyNote(note);

        note.setTitle("New Title");
        note.setContent("New Text");
        note.setColor(CanvasStickyNote.COLOR_ROSE);

        historyManager.record(new BoardCommand.ModifyNotePropertiesCommand(
                note.getId(),
                "Old Title", "New Title",
                "Old Text", "New Text",
                CanvasStickyNote.COLOR_AMBER, CanvasStickyNote.COLOR_ROSE
        ));

        assertEquals("New Title", note.getTitle());
        assertEquals("New Text", note.getContent());
        assertEquals(CanvasStickyNote.COLOR_ROSE, note.getColor());

        historyManager.undo(graph);
        assertEquals("Old Title", note.getTitle());
        assertEquals("Old Text", note.getContent());
        assertEquals(CanvasStickyNote.COLOR_AMBER, note.getColor());

        historyManager.redo(graph);
        assertEquals("New Title", note.getTitle());
        assertEquals("New Text", note.getContent());
        assertEquals(CanvasStickyNote.COLOR_ROSE, note.getColor());
    }

    @Test
    public void testGroupModulePreservesEnclosedFramesAndNotesOnUndoRedo() {
        RecipeNode node1 = RecipeNode.create("Machine 1", 20.0, 30.0, GTVoltageTier.LV);
        node1.setPos(100, 100);
        node1.setCardWidth(180);
        node1.setCardHeight(100);
        RecipeNode node2 = RecipeNode.create("Machine 2", 20.0, 30.0, GTVoltageTier.LV);
        node2.setPos(300, 100);
        node2.setCardWidth(180);
        node2.setCardHeight(100);

        graph.addNode(node1);
        graph.addNode(node2);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Group Frame", List.of(node1, node2), CanvasGroupFrame.COLOR_BLUE);
        graph.addFrame(frame);

        CanvasStickyNote note = CanvasStickyNote.create("Note", "Info", CanvasStickyNote.COLOR_CYAN, 120, 120);
        graph.addStickyNote(note);

        List<RecipeNode> origNodes = new ArrayList<>(graph.getNodes());
        List<FlowGraph.ConnectionEdge> origEdges = new ArrayList<>(graph.getConnections());

        RecipeNode moduleNode = graph.groupIntoModule(Set.of(node1.getId(), node2.getId()), "Test Module", frame);
        assertNotNull(moduleNode);
        assertTrue(moduleNode.isModule());

        List<RecipeNode> groupedNodes = new ArrayList<>();
        for (RecipeNode n : origNodes) {
            if (!graph.getNodes().contains(n)) groupedNodes.add(n);
        }
        List<FlowGraph.ConnectionEdge> rewires = new ArrayList<>(graph.getConnections());

        historyManager.record(new BoardCommand.GroupModuleCommand(groupedNodes, moduleNode, origEdges, rewires));

        assertEquals(0, graph.getFrames().size());
        assertEquals(0, graph.getStickyNotes().size());
        assertEquals(1, graph.getNodes().size());
        assertEquals(moduleNode.getId(), graph.getNodes().get(0).getId());

        historyManager.undo(graph);

        assertEquals(1, graph.getFrames().size());
        assertEquals(frame.getId(), graph.getFrames().get(0).getId());
        assertEquals(1, graph.getStickyNotes().size());
        assertEquals(note.getId(), graph.getStickyNotes().get(0).getId());
        assertEquals(2, graph.getNodes().size());
        assertFalse(graph.getNodes().contains(moduleNode));

        historyManager.redo(graph);

        assertEquals(0, graph.getFrames().size());
        assertEquals(0, graph.getStickyNotes().size());
        assertEquals(1, graph.getNodes().size());
        assertEquals(moduleNode.getId(), graph.getNodes().get(0).getId());
    }

    @Test
    public void testExpandModulePreservesEnclosedFramesAndNotesOnUndoRedo() {
        RecipeNode node1 = RecipeNode.create("Machine 1", 20.0, 30.0, GTVoltageTier.LV);
        node1.setPos(100, 100);
        node1.setCardWidth(180);
        node1.setCardHeight(100);
        RecipeNode node2 = RecipeNode.create("Machine 2", 20.0, 30.0, GTVoltageTier.LV);
        node2.setPos(300, 100);
        node2.setCardWidth(180);
        node2.setCardHeight(100);

        graph.addNode(node1);
        graph.addNode(node2);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Group Frame", List.of(node1, node2), CanvasGroupFrame.COLOR_BLUE);
        graph.addFrame(frame);

        CanvasStickyNote note = CanvasStickyNote.create("Note", "Info", CanvasStickyNote.COLOR_CYAN, 120, 120);
        graph.addStickyNote(note);

        RecipeNode moduleNode = graph.groupIntoModule(Set.of(node1.getId(), node2.getId()), "Test Module", frame);
        assertNotNull(moduleNode);

        List<FlowGraph.ConnectionEdge> moduleEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge e : graph.getConnections()) {
            if (e.fromNodeId().equals(moduleNode.getId()) || e.toNodeId().equals(moduleNode.getId())) {
                moduleEdges.add(e);
            }
        }
        List<RecipeNode> subNodes = new ArrayList<>(moduleNode.getSubGraph().getNodes());
        List<FlowGraph.ConnectionEdge> subEdges = new ArrayList<>(moduleNode.getSubGraph().getConnections());
        List<CanvasGroupFrame> subFrames = new ArrayList<>(moduleNode.getSubGraph().getFrames());
        List<CanvasStickyNote> subNotes = new ArrayList<>(moduleNode.getSubGraph().getStickyNotes());

        boolean expanded = graph.expandModule(moduleNode);
        assertTrue(expanded);

        historyManager.record(new BoardCommand.ExpandModuleCommand(moduleNode, subNodes, subEdges, moduleEdges, subFrames, subNotes));

        assertEquals(1, graph.getFrames().size());
        assertEquals(1, graph.getStickyNotes().size());
        assertEquals(2, graph.getNodes().size());
        assertFalse(graph.getNodes().contains(moduleNode));

        historyManager.undo(graph);

        assertEquals(0, graph.getFrames().size());
        assertEquals(0, graph.getStickyNotes().size());
        assertEquals(1, graph.getNodes().size());
        assertEquals(moduleNode.getId(), graph.getNodes().get(0).getId());

        historyManager.redo(graph);

        assertEquals(1, graph.getFrames().size());
        assertEquals(1, graph.getStickyNotes().size());
        assertEquals(2, graph.getNodes().size());
        assertFalse(graph.getNodes().contains(moduleNode));
    }
}



