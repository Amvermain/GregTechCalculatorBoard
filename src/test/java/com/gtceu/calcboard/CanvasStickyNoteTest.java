package com.gtceu.calcboard;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.RecipeNode;

import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class CanvasStickyNoteTest {

    @Test
    public void testStickyNoteCreationAndMovement() {
        CanvasStickyNote note = CanvasStickyNote.create("Oil Processing Notes", "Check naphtha cracked ratio.", CanvasStickyNote.COLOR_AMBER, 100, 200);

        Assertions.assertEquals("Oil Processing Notes", note.getTitle());
        Assertions.assertEquals("Check naphtha cracked ratio.", note.getContent());
        Assertions.assertEquals(CanvasStickyNote.COLOR_AMBER, note.getColor());
        Assertions.assertEquals(100.0, note.getPosX());
        Assertions.assertEquals(200.0, note.getPosY());

        note.moveBy(50, -30);
        Assertions.assertEquals(150.0, note.getPosX());
        Assertions.assertEquals(170.0, note.getPosY());

        Assertions.assertTrue(note.isPointInside(160, 180));
        Assertions.assertFalse(note.isPointInside(10, 10));

        Assertions.assertTrue(note.isPointInHeader(160, 175));
        Assertions.assertFalse(note.isPointInHeader(160, 210));
    }

    @Test
    public void testStickyNoteColorCycling() {
        CanvasStickyNote note = CanvasStickyNote.create("Note", "", CanvasStickyNote.COLOR_AMBER, 0, 0);
        note.cycleColor();
        Assertions.assertEquals(CanvasStickyNote.COLOR_EMERALD, note.getColor());
        note.cycleColor();
        Assertions.assertEquals(CanvasStickyNote.COLOR_CYAN, note.getColor());
    }

    @Test
    public void testStickyNoteNBTSerialization() {
        CanvasStickyNote note = new CanvasStickyNote("note-123", "Important Task", "Step 1: Build EBF\nStep 2: Connect Power", CanvasStickyNote.COLOR_PURPLE, 50, 60, 200, 150);

        CompoundTag tag = note.serializeNBT();
        CanvasStickyNote deserialized = CanvasStickyNote.deserializeNBT(tag);

        Assertions.assertNotNull(deserialized);
        Assertions.assertEquals("note-123", deserialized.getId());
        Assertions.assertEquals("Important Task", deserialized.getTitle());
        Assertions.assertEquals("Step 1: Build EBF\nStep 2: Connect Power", deserialized.getContent());
        Assertions.assertEquals(CanvasStickyNote.COLOR_PURPLE, deserialized.getColor());
        Assertions.assertEquals(50.0, deserialized.getPosX());
        Assertions.assertEquals(60.0, deserialized.getPosY());
        Assertions.assertEquals(200.0, deserialized.getWidth());
        Assertions.assertEquals(150.0, deserialized.getHeight());
    }

    @Test
    public void testFlowGraphStickyNotesPersistence() {
        FlowGraph graph = new FlowGraph();
        CanvasStickyNote note1 = CanvasStickyNote.create("Note 1", "Content 1", CanvasStickyNote.COLOR_AMBER, 0, 0);
        CanvasStickyNote note2 = CanvasStickyNote.create("Note 2", "Content 2", CanvasStickyNote.COLOR_EMERALD, 100, 100);

        graph.addStickyNote(note1);
        graph.addStickyNote(note2);
        Assertions.assertEquals(2, graph.getStickyNotes().size());

        FlowGraph copy = graph.copy();
        Assertions.assertEquals(2, copy.getStickyNotes().size());
        Assertions.assertEquals("Note 1", copy.getStickyNotes().get(0).getTitle());
        Assertions.assertEquals("Note 2", copy.getStickyNotes().get(1).getTitle());

        graph.removeStickyNote(note1);
        Assertions.assertEquals(1, graph.getStickyNotes().size());
    }

    @Test
    public void testFrameDynamicContainmentWithNotesAndNodes() {
        FlowGraph graph = new FlowGraph();
        com.gtceu.calcboard.api.model.RecipeNode node = com.gtceu.calcboard.api.model.RecipeNode.createReroute(100, 100);
        CanvasStickyNote note = CanvasStickyNote.create("Check Ratio", "Line 1\nLine 2\nLine 3", CanvasStickyNote.COLOR_AMBER, 120, 120);
        com.gtceu.calcboard.api.model.RecipeNode outsideNode = com.gtceu.calcboard.api.model.RecipeNode.createReroute(500, 500);

        graph.addNode(node);
        graph.addNode(outsideNode);
        graph.addStickyNote(note);

        com.gtceu.calcboard.api.model.CanvasGroupFrame frame = com.gtceu.calcboard.api.model.CanvasGroupFrame.createFromElements(
                "My Sub-Process",
                java.util.List.of(node),
                java.util.List.of(note),
                com.gtceu.calcboard.api.model.CanvasGroupFrame.COLOR_BLUE
        );
        graph.addFrame(frame);

        // Verify dynamic containment
        java.util.List<com.gtceu.calcboard.api.model.RecipeNode> enclosedNodes = frame.getEnclosedNodes(graph);
        java.util.List<CanvasStickyNote> enclosedNotes = frame.getEnclosedNotes(graph);

        Assertions.assertEquals(1, enclosedNodes.size());
        Assertions.assertEquals(node.getId(), enclosedNodes.get(0).getId());
        Assertions.assertEquals(1, enclosedNotes.size());
        Assertions.assertEquals(note.getId(), enclosedNotes.get(0).getId());

        // Moving outsideNode into frame
        outsideNode.setPos(130, 130);
        Assertions.assertEquals(2, frame.getEnclosedNodes(graph).size());

        // Moving node out of frame
        node.setPos(999, 999);
        Assertions.assertEquals(1, frame.getEnclosedNodes(graph).size());
    }

    @Test
    public void testModuleCollapseAndExpandWithStickyNotes() {
        FlowGraph graph = new FlowGraph();
        com.gtceu.calcboard.api.model.RecipeNode node1 = com.gtceu.calcboard.api.model.RecipeNode.createReroute(100, 100);
        com.gtceu.calcboard.api.model.RecipeNode node2 = com.gtceu.calcboard.api.model.RecipeNode.createReroute(100, 200);
        CanvasStickyNote note = CanvasStickyNote.create("Process Note", "test note content", CanvasStickyNote.COLOR_AMBER, 150, 150);

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addStickyNote(note);

        com.gtceu.calcboard.api.model.CanvasGroupFrame frame = com.gtceu.calcboard.api.model.CanvasGroupFrame.createFromElements(
                "Group Frame",
                java.util.List.of(node1, node2),
                java.util.List.of(note),
                com.gtceu.calcboard.api.model.CanvasGroupFrame.COLOR_BLUE
        );
        graph.addFrame(frame);

        // Collapse into module
        java.util.Set<String> nodeIds = new java.util.HashSet<>();
        for (com.gtceu.calcboard.api.model.RecipeNode n : frame.getEnclosedNodes(graph)) {
            nodeIds.add(n.getId());
        }
        com.gtceu.calcboard.api.model.RecipeNode module = graph.groupIntoModule(nodeIds, frame.getTitle());
        Assertions.assertNotNull(module);

        // Main graph should not contain the note or frame
        Assertions.assertEquals(0, graph.getStickyNotes().size());
        Assertions.assertEquals(0, graph.getFrames().size());
        Assertions.assertEquals(1, graph.getNodes().size()); // only the module

        // SubGraph should contain the note and frame
        Assertions.assertNotNull(module.getSubGraph());
        Assertions.assertEquals(1, module.getSubGraph().getStickyNotes().size());
        Assertions.assertEquals("Process Note", module.getSubGraph().getStickyNotes().get(0).getTitle());
        Assertions.assertEquals(1, module.getSubGraph().getFrames().size());

        // Expand module
        boolean expanded = graph.expandModule(module);
        Assertions.assertTrue(expanded);

        // Main graph should have the sticky note and frame restored
        Assertions.assertEquals(1, graph.getStickyNotes().size());
        Assertions.assertEquals("Process Note", graph.getStickyNotes().get(0).getTitle());
        Assertions.assertEquals(1, graph.getFrames().size());
        Assertions.assertEquals(2, graph.getNodes().size());
    }
}



