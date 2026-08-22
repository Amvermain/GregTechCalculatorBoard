package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class CanvasGroupFrameTest {

    @Test
    public void testFrameCreationAndPalette() {
        CanvasGroupFrame frame = new CanvasGroupFrame("f1", "Smelting Area", CanvasGroupFrame.COLOR_BLUE, 50, 60, 300, 200);
        Assertions.assertEquals("f1", frame.getId());
        Assertions.assertEquals("Smelting Area", frame.getTitle());
        Assertions.assertEquals(CanvasGroupFrame.COLOR_BLUE, frame.getColor());
        Assertions.assertEquals(50, frame.getPosX(), 0.001);
        Assertions.assertEquals(60, frame.getPosY(), 0.001);
        Assertions.assertEquals(300, frame.getWidth(), 0.001);
        Assertions.assertEquals(200, frame.getHeight(), 0.001);

        frame.cycleColor();
        Assertions.assertEquals(CanvasGroupFrame.COLOR_EMERALD, frame.getColor());

        frame.setNote("Keep oxygen supply above 1000L");
        Assertions.assertEquals("Keep oxygen supply above 1000L", frame.getNote());
    }

    @Test
    public void testFrameCreationFromNodes() {
        RecipeNode n1 = RecipeNode.create("Arc Furnace", 100, 30, GTVoltageTier.LV);
        n1.setPos(100, 100);
        n1.setCardWidth(200);
        n1.setCardHeight(150);

        RecipeNode n2 = RecipeNode.create("Macerator", 100, 30, GTVoltageTier.LV);
        n2.setPos(400, 200);
        n2.setCardWidth(200);
        n2.setCardHeight(150);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Ore Processing", List.of(n1, n2), CanvasGroupFrame.COLOR_AMBER);
        Assertions.assertEquals("Ore Processing", frame.getTitle());
        Assertions.assertEquals(2, frame.getContainedNodeIds().size());
        Assertions.assertTrue(frame.getContainedNodeIds().contains(n1.getId()));
        Assertions.assertTrue(frame.getContainedNodeIds().contains(n2.getId()));

        // minX = 100 - 24 = 76, minY = 100 - 24 - 24(HEADER_HEIGHT) = 52
        Assertions.assertEquals(76, frame.getPosX(), 0.001);
        Assertions.assertEquals(52, frame.getPosY(), 0.001);
    }

    @Test
    public void testFlowGraphFrameNbtSerialization() {
        FlowGraph graph = new FlowGraph();
        RecipeNode n1 = RecipeNode.create("Chemical Reactor", 100, 120, GTVoltageTier.MV);
        n1.setPos(50, 50);
        graph.addNode(n1);

        CanvasGroupFrame frame = new CanvasGroupFrame("f1", "Petrochem Line", CanvasGroupFrame.COLOR_EMERALD, 40, 40, 350, 250);
        frame.setNote("Warning: high ethylene throughput");
        frame.getContainedNodeIds().add(n1.getId());
        graph.addFrame(frame);

        CompoundTag tag = graph.serializeNBT();
        FlowGraph restored = FlowGraph.deserializeNBT(tag);

        Assertions.assertEquals(1, restored.getFrames().size());
        CanvasGroupFrame restoredFrame = restored.getFrames().get(0);
        Assertions.assertEquals("f1", restoredFrame.getId());
        Assertions.assertEquals("Petrochem Line", restoredFrame.getTitle());
        Assertions.assertEquals(CanvasGroupFrame.COLOR_EMERALD, restoredFrame.getColor());
        Assertions.assertEquals("Warning: high ethylene throughput", restoredFrame.getNote());
        Assertions.assertTrue(restoredFrame.getContainedNodeIds().contains(n1.getId()));
    }

    @Test
    public void testModuleGroupingPreservesFramesAndNNWiring() {
        FlowGraph graph = new FlowGraph();

        // 1. External Source: Produces Iron
        RecipeNode extSource = RecipeNode.create("Miner", 100, 30, GTVoltageTier.LV);
        extSource.getOutputs().add(IngredientStack.item(new ResourceLocation("minecraft:iron_ore"), "Iron Ore", 1.0));
        graph.addNode(extSource);

        // 2. Internal Node 1 (Macerator)
        RecipeNode internal1 = RecipeNode.create("Macerator", 100, 30, GTVoltageTier.LV);
        internal1.setPos(200, 100);
        internal1.getInputs().add(IngredientStack.item(new ResourceLocation("minecraft:iron_ore"), "Iron Ore", 1.0));
        internal1.getOutputs().add(IngredientStack.item(new ResourceLocation("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2.0));
        graph.addNode(internal1);

        // 3. Internal Node 2 (Furnace)
        RecipeNode internal2 = RecipeNode.create("Furnace", 100, 30, GTVoltageTier.LV);
        internal2.setPos(500, 100);
        internal2.getInputs().add(IngredientStack.item(new ResourceLocation("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2.0));
        internal2.getOutputs().add(IngredientStack.item(new ResourceLocation("minecraft:iron_ingot"), "Iron Ingot", 2.0));
        graph.addNode(internal2);

        // 4. External Sink
        RecipeNode extSink = RecipeNode.create("Assembler", 100, 30, GTVoltageTier.LV);
        extSink.getInputs().add(IngredientStack.item(new ResourceLocation("minecraft:iron_ingot"), "Iron Ingot", 2.0));
        graph.addNode(extSink);

        // Connections: extSource -> internal1 -> internal2 -> extSink
        graph.addConnection(extSource.getId(), 0, internal1.getId(), 0);
        graph.addConnection(internal1.getId(), 0, internal2.getId(), 0);
        graph.addConnection(internal2.getId(), 0, extSink.getId(), 0);

        // Add a Frame around internal1 and internal2
        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Inner Refinement", List.of(internal1, internal2), CanvasGroupFrame.COLOR_CYAN);
        graph.addFrame(frame);

        // Group internal1 and internal2 into Module
        RecipeNode module = graph.groupIntoModule(Set.of(internal1.getId(), internal2.getId()), "Iron Processing Unit");
        Assertions.assertNotNull(module);
        Assertions.assertTrue(module.isModule());

        // Verify that the frame was preserved in module's subGraph
        Assertions.assertEquals(0, graph.getFrames().size());
        Assertions.assertNotNull(module.getSubGraph());
        Assertions.assertEquals(1, module.getSubGraph().getFrames().size());
        Assertions.assertEquals("Inner Refinement", module.getSubGraph().getFrames().get(0).getTitle());

        // Verify module external connections: extSource -> module -> extSink
        Assertions.assertEquals(2, graph.getConnections().size());

        // Now Expand the Module
        boolean expanded = graph.expandModule(module);
        Assertions.assertTrue(expanded);

        // Verify that the frame is restored to the outer graph
        Assertions.assertEquals(1, graph.getFrames().size());
        Assertions.assertEquals("Inner Refinement", graph.getFrames().get(0).getTitle());

        // Verify that the original N:N connections are fully restored!
        Assertions.assertEquals(3, graph.getConnections().size());

        FlowGraph.ConnectionEdge wire1 = graph.getConnections().stream()
            .filter(c -> c.fromNodeId().equals(extSource.getId()))
            .findFirst().orElse(null);
        Assertions.assertNotNull(wire1);
        Assertions.assertEquals(internal1.getId(), wire1.toNodeId());

        FlowGraph.ConnectionEdge wire2 = graph.getConnections().stream()
            .filter(c -> c.fromNodeId().equals(internal1.getId()))
            .findFirst().orElse(null);
        Assertions.assertNotNull(wire2);
        Assertions.assertEquals(internal2.getId(), wire2.toNodeId());

        FlowGraph.ConnectionEdge wire3 = graph.getConnections().stream()
            .filter(c -> c.fromNodeId().equals(internal2.getId()))
            .findFirst().orElse(null);
        Assertions.assertNotNull(wire3);
        Assertions.assertEquals(extSink.getId(), wire3.toNodeId());
    }

    @Test
    public void testNestedFrameCollapsePreservesOuterFrame() {
        FlowGraph graph = new FlowGraph();

        RecipeNode n1 = RecipeNode.create("Boiler", 20, 0, GTVoltageTier.LV);
        n1.setPos(100, 100);
        graph.addNode(n1);

        RecipeNode n2 = RecipeNode.create("Turbine", 20, 32, GTVoltageTier.LV);
        n2.setPos(300, 100);
        graph.addNode(n2);

        // Inner Frame around n1 and n2
        CanvasGroupFrame innerFrame = new CanvasGroupFrame("inner", "Inner Frame", CanvasGroupFrame.COLOR_BLUE, 80, 80, 450, 200);
        innerFrame.getContainedNodeIds().addAll(List.of(n1.getId(), n2.getId()));
        graph.addFrame(innerFrame);

        // Outer Frame enclosing innerFrame and larger space
        CanvasGroupFrame outerFrame = new CanvasGroupFrame("outer", "Outer Frame", CanvasGroupFrame.COLOR_PURPLE, 20, 20, 700, 400);
        outerFrame.getContainedNodeIds().addAll(List.of(n1.getId(), n2.getId()));
        graph.addFrame(outerFrame);

        Assertions.assertEquals(2, graph.getFrames().size());

        // Collapse specifically the inner frame
        RecipeNode module = FlowGraphModuleHandler.groupIntoModule(graph, Set.of(n1.getId(), n2.getId()), "Inner Frame", innerFrame);
        Assertions.assertNotNull(module);
        Assertions.assertTrue(module.isModule());

        // Outer frame MUST remain on the main graph
        Assertions.assertEquals(1, graph.getFrames().size());
        Assertions.assertEquals("outer", graph.getFrames().get(0).getId());
        Assertions.assertEquals("Outer Frame", graph.getFrames().get(0).getTitle());

        // Inner frame MUST be captured into subGraph
        Assertions.assertNotNull(module.getSubGraph());
        Assertions.assertEquals(1, module.getSubGraph().getFrames().size());
        Assertions.assertEquals("inner", module.getSubGraph().getFrames().get(0).getId());
        Assertions.assertEquals("Inner Frame", module.getSubGraph().getFrames().get(0).getTitle());
    }
}
