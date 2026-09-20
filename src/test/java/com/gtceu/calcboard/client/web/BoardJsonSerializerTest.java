package com.gtceu.calcboard.client.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoardJsonSerializerTest {

    @Test
    void testEmptyGraphSerialization() {
        FlowGraph graph = new FlowGraph();
        String json = BoardJsonSerializer.serialize(graph, "test_page_1", "Test Page", 100.0, 50.0, 1.5);
        assertNotNull(json);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertEquals(1, root.get("version").getAsInt());
        assertEquals("test_page_1", root.get("pageId").getAsString());
        assertEquals("Test Page", root.get("pageTitle").getAsString());

        JsonObject vp = root.getAsJsonObject("viewport");
        assertEquals(100.0, vp.get("panX").getAsDouble(), 0.001);
        assertEquals(50.0, vp.get("panY").getAsDouble(), 0.001);
        assertEquals(1.5, vp.get("zoom").getAsDouble(), 0.001);

        assertEquals(0, root.getAsJsonArray("nodes").size());
        assertEquals(0, root.getAsJsonArray("connections").size());
        assertEquals(0, root.getAsJsonArray("frames").size());
        assertEquals(0, root.getAsJsonArray("stickyNotes").size());
    }

    @Test
    void testComplexGraphSerialization() {
        FlowGraph graph = new FlowGraph();

        RecipeNode machine = RecipeNode.create(
                ResourceLocation.tryParse("gtceu:electric_blast_furnace"),
                "Electric Blast Furnace",
                300.0,
                1920.0,
                GTVoltageTier.EV
        );
        machine.setPos(120.0, 80.0);
        machine.setParallel(8);
        machine.setMachineCount(2.0);

        IngredientStack in1 = IngredientStack.item(ResourceLocation.tryParse("minecraft:raw_iron"), "Raw Iron", 8.0);
        IngredientStack in2 = IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0);
        machine.getInputs().add(in1);
        machine.getInputs().add(in2);

        IngredientStack out1 = IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 8.0);
        machine.getOutputs().add(out1);

        RecipeNode reroute = RecipeNode.createReroute(400.0, 100.0);
        reroute.getInputs().add(in1.copy());
        reroute.getOutputs().add(in1.copy());

        graph.addNode(machine);
        graph.addNode(reroute);

        graph.addConnection(machine.getId(), 0, reroute.getId(), 0);

        CanvasGroupFrame frame = new CanvasGroupFrame("frame_1", "Processing Frame", 0xFF3B82F6, 100.0, 60.0, 400.0, 300.0);
        graph.addFrame(frame);

        CanvasStickyNote note = CanvasStickyNote.create("Note 1", "Verify overclock parameters", 0xFFF59E0B, 50.0, 50.0);
        graph.addStickyNote(note);

        String json = BoardJsonSerializer.serialize(graph, "main_line", "Main Refining Line", 0.0, 0.0, 1.0);
        assertNotNull(json);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("main_line", root.get("pageId").getAsString());
        assertEquals("Main Refining Line", root.get("pageTitle").getAsString());

        var nodes = root.getAsJsonArray("nodes");
        assertEquals(2, nodes.size());

        JsonObject mNode = nodes.get(0).getAsJsonObject();
        assertEquals(machine.getId(), mNode.get("id").getAsString());
        assertEquals("Electric Blast Furnace", mNode.get("title").getAsString());
        assertEquals("EV", mNode.get("tier").getAsString());
        assertEquals("MACHINE", mNode.get("type").getAsString());

        JsonObject metrics = mNode.getAsJsonObject("metrics");
        assertNotNull(metrics);
        assertEquals(8, metrics.get("parallel").getAsInt());
        assertEquals(2.0, metrics.get("machineCount").getAsDouble(), 0.001);

        var inputs = mNode.getAsJsonArray("inputs");
        assertEquals(2, inputs.size());
        assertEquals("in_0", inputs.get(0).getAsJsonObject().get("portId").getAsString());
        assertEquals("ITEM", inputs.get(0).getAsJsonObject().get("type").getAsString());
        assertEquals("in_1", inputs.get(1).getAsJsonObject().get("portId").getAsString());
        assertEquals("FLUID", inputs.get(1).getAsJsonObject().get("type").getAsString());

        var conns = root.getAsJsonArray("connections");
        assertEquals(1, conns.size());
        JsonObject edge = conns.get(0).getAsJsonObject();
        assertEquals(machine.getId(), edge.get("fromNode").getAsString());
        assertEquals("out_0", edge.get("fromPort").getAsString());
        assertEquals(reroute.getId(), edge.get("toNode").getAsString());
        assertEquals("in_0", edge.get("toPort").getAsString());

        var frames = root.getAsJsonArray("frames");
        assertEquals(1, frames.size());
        assertEquals("Processing Frame", frames.get(0).getAsJsonObject().get("title").getAsString());

        var notes = root.getAsJsonArray("stickyNotes");
        assertEquals(1, notes.size());
        assertEquals("Note 1", notes.get(0).getAsJsonObject().get("title").getAsString());
        assertEquals("Verify overclock parameters", notes.get(0).getAsJsonObject().get("content").getAsString());
    }

    @Test
    void testFlippedNodeAndRerouteOutflow() {
        FlowGraph graph = new FlowGraph();

        RecipeNode producer = RecipeNode.create(
                ResourceLocation.tryParse("gtceu:macerator"),
                "Macerator",
                20.0,
                32.0,
                GTVoltageTier.LV
        );
        producer.setFlipped(true);
        IngredientStack out = IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_dust"), "Iron Dust", 1.0);
        producer.getOutputs().add(out);

        RecipeNode reroute = RecipeNode.createReroute(200.0, 100.0);
        reroute.getInputs().add(out.copy());
        reroute.getOutputs().add(out.copy());

        RecipeNode consumer = RecipeNode.create(
                ResourceLocation.tryParse("gtceu:electric_furnace"),
                "Furnace",
                20.0,
                32.0,
                GTVoltageTier.LV
        );
        consumer.getInputs().add(out.copy());

        graph.addNode(producer);
        graph.addNode(reroute);
        graph.addNode(consumer);

        graph.addConnection(producer.getId(), 0, reroute.getId(), 0);
        graph.addConnection(reroute.getId(), 0, consumer.getId(), 0);

        String json = BoardJsonSerializer.serialize(graph, "test", "Reroute Flow Test", 0, 0, 1);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        var nodes = root.getAsJsonArray("nodes");
        JsonObject pNode = nodes.get(0).getAsJsonObject();
        assertTrue(pNode.get("isFlipped").getAsBoolean());

        JsonObject rNode = nodes.get(1).getAsJsonObject();
        assertEquals("JUNCTION", rNode.get("type").getAsString());

        var conns = root.getAsJsonArray("connections");
        assertEquals(2, conns.size());

        JsonObject rerouteOutEdge = conns.get(1).getAsJsonObject();
        assertEquals(reroute.getId(), rerouteOutEdge.get("fromNode").getAsString());
        assertTrue(rerouteOutEdge.get("flowRate").getAsDouble() > 0.0, "Flow rate leaving reroute must not be zero");
    }

    @Test
    void testSerializeBoardPageAndMetricsRounding() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node = RecipeNode.create(
                ResourceLocation.tryParse("gtceu:chemical_reactor"),
                "Chemical Reactor",
                16.0,
                60.0,
                GTVoltageTier.MV
        );
        graph.addNode(node);

        com.gtceu.calcboard.api.storage.BoardPage page = new com.gtceu.calcboard.api.storage.BoardPage("page_round_test", "Page Round Test", graph);
        page.setPanX(15.5);
        page.setPanY(25.5);
        page.setZoom(1.2);

        String json = BoardJsonSerializer.serialize(page);
        assertNotNull(json);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        assertEquals("page_round_test", root.get("pageId").getAsString());
        assertEquals("Page Round Test", root.get("pageTitle").getAsString());

        JsonObject metrics = root.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("metrics");
        assertEquals(0.8, metrics.get("durationSec").getAsDouble(), 0.0001);
        assertEquals(-60.0, metrics.get("eut").getAsDouble(), 0.0001);
    }

    @Test
    void testCardDimensionsConsistencyWithInGame() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node = RecipeNode.create(
                ResourceLocation.tryParse("gtceu:macerator"),
                "Macerator",
                20.0,
                32.0,
                GTVoltageTier.LV
        );
        IngredientStack in = IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1.0);
        IngredientStack out = IngredientStack.item(ResourceLocation.tryParse("minecraft:raw_iron"), "Raw Iron", 1.0);
        node.getInputs().add(in);
        node.getOutputs().add(out);
        graph.addNode(node);

        RecipeNode pin = RecipeNode.createReroute(100.0, 100.0);
        graph.addNode(pin);

        String json = BoardJsonSerializer.serialize(graph, "dims_test", "Dims Test", 0, 0, 1);
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        var nodes = root.getAsJsonArray("nodes");

        JsonObject machineObj = nodes.get(0).getAsJsonObject();
        assertEquals(245.0, machineObj.get("width").getAsDouble(), 0.001);
        // contentStartY(80) + 1 * 18 + 8 = 106.0
        assertEquals(106.0, machineObj.get("height").getAsDouble(), 0.001);

        JsonObject pinObj = nodes.get(1).getAsJsonObject();
        assertEquals(32.0, pinObj.get("width").getAsDouble(), 0.001);
        assertEquals(32.0, pinObj.get("height").getAsDouble(), 0.001);
    }
}
