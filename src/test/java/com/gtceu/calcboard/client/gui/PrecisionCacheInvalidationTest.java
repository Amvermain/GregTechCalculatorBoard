package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.GlobalBalanceAggregator;
import com.gtceu.calcboard.api.solver.GlobalBalanceSummary;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.widget.ToolbarWidget;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class PrecisionCacheInvalidationTest {

    @Test
    public void testFindFrameEnclosingNodeDirectAABB() {
        FlowGraph graph = new FlowGraph();

        CanvasGroupFrame frame = new CanvasGroupFrame("frame1", "Processing Group", 0xFF2196F3, 100.0, 100.0, 300.0, 200.0);
        graph.addFrame(frame);

        RecipeNode insideNode = new RecipeNode("node1", "Inside Node", 20.0, 32.0, GTVoltageTier.LV);
        insideNode.setPosX(150.0);
        insideNode.setPosY(150.0);
        graph.addNode(insideNode);

        RecipeNode outsideNode = new RecipeNode("node2", "Outside Node", 20.0, 32.0, GTVoltageTier.LV);
        outsideNode.setPosX(500.0);
        outsideNode.setPosY(500.0);
        graph.addNode(outsideNode);

        Assertions.assertSame(frame, graph.findFrameEnclosingNode(insideNode));
        Assertions.assertNull(graph.findFrameEnclosingNode(outsideNode));
    }

    @Test
    public void testFlowGraphSummaryCachingAndInvalidation() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node = new RecipeNode("node1", "Smelter", 20.0, 32.0, GTVoltageTier.LV);
        node.setMachineCount(1.0);
        node.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));
        graph.addNode(node);

        Assertions.assertTrue(graph.isSummaryDirty());

        BalanceSummary firstSummary = graph.computeSummary();
        Assertions.assertNotNull(firstSummary);
        Assertions.assertFalse(graph.isSummaryDirty());
        Assertions.assertSame(firstSummary, graph.getCachedSummary());

        BalanceSummary secondSummary = graph.computeSummary();
        Assertions.assertEquals(firstSummary, secondSummary);

        graph.invalidatePortStatsCache();
        Assertions.assertTrue(graph.isSummaryDirty());
        Assertions.assertNull(graph.getCachedSummary());
    }

    @Test
    public void testStickyNoteDoesNotInvalidateGraphSummary() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node = new RecipeNode("node1", "Machine", 20.0, 32.0, GTVoltageTier.LV);
        graph.addNode(node);

        BalanceSummary summary = graph.computeSummary();
        Assertions.assertNotNull(summary);
        Assertions.assertFalse(graph.isSummaryDirty());

        CanvasStickyNote note = CanvasStickyNote.create("Note", "Content", CanvasStickyNote.COLOR_AMBER, 50, 50);
        graph.addStickyNote(note);
        note.moveBy(10, 20);
        note.cycleColor();

        Assertions.assertFalse(graph.isSummaryDirty());
        Assertions.assertSame(summary, graph.getCachedSummary());
    }

    @Test
    public void testGlobalBalanceAggregatorCacheReuse() {
        BoardPage page1 = BoardPage.createDefault("Page 1");
        RecipeNode node1 = new RecipeNode("n1", "Extractor", 20.0, 16.0, GTVoltageTier.LV);
        node1.setMachineCount(2.0);
        node1.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 2.0));
        page1.getGraph().addNode(node1);

        GlobalBalanceSummary summary1 = GlobalBalanceAggregator.compute(List.of(page1));
        Assertions.assertNotNull(summary1);
        Assertions.assertFalse(page1.getGraph().isSummaryDirty());
        BalanceSummary cachedPageSummary = page1.getGraph().getCachedSummary();
        Assertions.assertNotNull(cachedPageSummary);

        GlobalBalanceSummary summary2 = GlobalBalanceAggregator.compute(List.of(page1));
        Assertions.assertNotNull(summary2);
        Assertions.assertSame(cachedPageSummary, page1.getGraph().getCachedSummary());
    }

    @Test
    public void testAutoConnectConnectsUnconnectedPorts() {
        FlowGraph graph = new FlowGraph();

        IngredientStack iron = IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0);

        RecipeNode producer = new RecipeNode("prod", "Producer", 20.0, 32.0, GTVoltageTier.LV);
        producer.getOutputs().add(iron);
        graph.addNode(producer);

        RecipeNode consumer = new RecipeNode("cons", "Consumer", 20.0, 32.0, GTVoltageTier.LV);
        consumer.getInputs().add(iron);
        graph.addNode(consumer);

        List<FlowGraph.ConnectionEdge> added = ToolbarWidget.autoConnect(graph, null);
        Assertions.assertEquals(1, added.size());
        Assertions.assertEquals(1, graph.getConnections().size());

        FlowGraph.ConnectionEdge edge = graph.getConnections().get(0);
        Assertions.assertEquals("prod", edge.fromNodeId());
        Assertions.assertEquals(0, edge.outputIndex());
        Assertions.assertEquals("cons", edge.toNodeId());
        Assertions.assertEquals(0, edge.inputIndex());

        List<FlowGraph.ConnectionEdge> secondPass = ToolbarWidget.autoConnect(graph, null);
        Assertions.assertEquals(0, secondPass.size());
        Assertions.assertEquals(1, graph.getConnections().size());
    }
}
