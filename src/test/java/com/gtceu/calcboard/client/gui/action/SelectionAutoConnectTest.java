package com.gtceu.calcboard.client.gui.action;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.widget.ToolbarWidget;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class SelectionAutoConnectTest {

    @BeforeEach
    @AfterEach
    public void cleanup() {
        BoardManager.getInstance().resetToDefault();
    }

    @Test
    public void testAutoConnectOnlyConnectsNodesWithinTargetSet() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation ironId = ResourceLocation.tryParse("minecraft:iron_ingot");

        RecipeNode p1 = new RecipeNode("p1", "Iron Producer", 100, 100, GTVoltageTier.LV);
        p1.getOutputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(p1);

        RecipeNode c1 = new RecipeNode("c1", "Target Consumer", 100, 100, GTVoltageTier.LV);
        c1.getInputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(c1);

        RecipeNode c2 = new RecipeNode("c2", "External Consumer", 100, 100, GTVoltageTier.LV);
        c2.getInputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(c2);

        List<BoardCommand> subCommands = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> added = ToolbarWidget.autoConnect(graph, subCommands, null, Set.of("p1", "c1"));

        Assertions.assertEquals(1, added.size());
        Assertions.assertEquals("p1", added.get(0).fromNodeId());
        Assertions.assertEquals("c1", added.get(0).toNodeId());
        Assertions.assertEquals(1, graph.getConnections().size());
    }

    @Test
    public void testAutoConnectNullTargetConnectsAll() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation ironId = ResourceLocation.tryParse("minecraft:iron_ingot");

        RecipeNode p1 = new RecipeNode("p1", "Iron Producer", 100, 100, GTVoltageTier.LV);
        p1.getOutputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(p1);

        RecipeNode c1 = new RecipeNode("c1", "Consumer 1", 100, 100, GTVoltageTier.LV);
        c1.getInputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(c1);

        RecipeNode c2 = new RecipeNode("c2", "Consumer 2", 100, 100, GTVoltageTier.LV);
        c2.getInputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(c2);

        List<BoardCommand> subCommands = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> added = ToolbarWidget.autoConnect(graph, subCommands, null, null);

        Assertions.assertEquals(2, added.size());
        Assertions.assertEquals(2, graph.getConnections().size());
    }

    @Test
    public void testRerouteJunctionOutsideSelectionDoesNotLeak() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation copperId = ResourceLocation.tryParse("gtceu:copper_ingot");

        RecipeNode p1 = new RecipeNode("p1", "Producer 1", 100, 100, GTVoltageTier.LV);
        p1.getOutputs().add(IngredientStack.item(copperId, "Copper", 1.0));
        graph.addNode(p1);

        RecipeNode reroute = RecipeNode.createReroute(0, 0);
        reroute.setId("reroute_ext");
        reroute.bindRerouteIngredient(IngredientStack.item(copperId, "Copper", 1.0));
        graph.addNode(reroute);

        RecipeNode c1 = new RecipeNode("c1", "Consumer", 100, 100, GTVoltageTier.LV);
        c1.getInputs().add(IngredientStack.item(copperId, "Copper", 1.0));
        graph.addNode(c1);

        graph.addConnection("reroute_ext", 0, "c1", 0);

        RecipeNode p2 = new RecipeNode("p2", "Producer 2", 100, 100, GTVoltageTier.LV);
        p2.getOutputs().add(IngredientStack.item(copperId, "Copper", 1.0));
        graph.addNode(p2);

        List<BoardCommand> subCommands = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> added = ToolbarWidget.autoConnect(graph, subCommands, null, Set.of("p2", "c1"));

        Assertions.assertEquals(0, added.size());
        Assertions.assertEquals(1, graph.getConnections().size());
    }

    @Test
    public void testRerouteJunctionInsideSelectionConnectsSuccessfully() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation copperId = ResourceLocation.tryParse("gtceu:copper_ingot");

        RecipeNode p1 = new RecipeNode("p1", "Producer 1", 100, 100, GTVoltageTier.LV);
        p1.getOutputs().add(IngredientStack.item(copperId, "Copper", 1.0));
        graph.addNode(p1);

        RecipeNode reroute = RecipeNode.createReroute(0, 0);
        reroute.setId("reroute_in");
        reroute.bindRerouteIngredient(IngredientStack.item(copperId, "Copper", 1.0));
        graph.addNode(reroute);

        RecipeNode c1 = new RecipeNode("c1", "Consumer", 100, 100, GTVoltageTier.LV);
        c1.getInputs().add(IngredientStack.item(copperId, "Copper", 1.0));
        graph.addNode(c1);

        graph.addConnection("reroute_in", 0, "c1", 0);

        List<BoardCommand> subCommands = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> added = ToolbarWidget.autoConnect(graph, subCommands, null, Set.of("p1", "reroute_in"));

        Assertions.assertEquals(1, added.size());
        Assertions.assertEquals("p1", added.get(0).fromNodeId());
        Assertions.assertEquals("reroute_in", added.get(0).toNodeId());
    }

    @Test
    public void testSelectionAutoConnectUndoRollback() {
        BoardScreen screen = new BoardScreen();
        FlowGraph graph = screen.getGraph();
        ResourceLocation tinId = ResourceLocation.tryParse("gtceu:tin_ingot");

        RecipeNode p = new RecipeNode("p", "Tin Producer", 100, 100, GTVoltageTier.LV);
        p.getOutputs().add(IngredientStack.item(tinId, "Tin Ingot", 1.0));
        graph.addNode(p);

        RecipeNode c = new RecipeNode("c", "Tin Consumer", 100, 100, GTVoltageTier.LV);
        c.getInputs().add(IngredientStack.item(tinId, "Tin Ingot", 1.0));
        graph.addNode(c);

        ToolbarActionHandler.performAutoConnectForSelection(screen, Set.of("p", "c"));
        Assertions.assertEquals(1, graph.getConnections().size());

        screen.undo();
        Assertions.assertEquals(0, graph.getConnections().size());

        screen.redo();
        Assertions.assertEquals(1, graph.getConnections().size());
    }

    @Test
    public void testSelectionAutoConnectGuardsLessThanTwoNodes() {
        BoardScreen screen = new BoardScreen();
        FlowGraph graph = screen.getGraph();
        ResourceLocation goldId = ResourceLocation.tryParse("minecraft:gold_ingot");

        RecipeNode p = new RecipeNode("p", "Gold Producer", 100, 100, GTVoltageTier.LV);
        p.getOutputs().add(IngredientStack.item(goldId, "Gold Ingot", 1.0));
        graph.addNode(p);

        ToolbarActionHandler.performAutoConnectForSelection(screen, Set.of("p"));
        Assertions.assertEquals(0, graph.getConnections().size());

        ToolbarActionHandler.performAutoConnectForSelection(screen, Set.of());
        Assertions.assertEquals(0, graph.getConnections().size());
    }

    @Test
    public void testProducerFeedingExternalRerouteStillConnectsToSelectedConsumer() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation ironId = ResourceLocation.tryParse("minecraft:iron_ingot");

        RecipeNode p1 = new RecipeNode("p1", "Iron Producer", 100, 100, GTVoltageTier.LV);
        p1.getOutputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(p1);

        RecipeNode rerouteExt = RecipeNode.createReroute(0, 0);
        rerouteExt.setId("reroute_ext");
        rerouteExt.bindRerouteIngredient(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(rerouteExt);

        graph.addConnection("p1", 0, "reroute_ext", 0);

        RecipeNode c1 = new RecipeNode("c1", "Selected Consumer", 100, 100, GTVoltageTier.LV);
        c1.getInputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(c1);

        List<BoardCommand> subCommands = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> added = ToolbarWidget.autoConnect(graph, subCommands, null, Set.of("p1", "c1"));

        Assertions.assertEquals(1, added.size());
        Assertions.assertEquals("p1", added.get(0).fromNodeId());
        Assertions.assertEquals("c1", added.get(0).toNodeId());
        Assertions.assertEquals(2, graph.getConnections().size());
    }

    @Test
    public void testSelectedRerouteJunctionPreventsBypassDuplicateWire() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation ironId = ResourceLocation.tryParse("minecraft:iron_ingot");

        RecipeNode p1 = new RecipeNode("p1", "Producer", 100, 100, GTVoltageTier.LV);
        p1.getOutputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(p1);

        RecipeNode rerouteIn = RecipeNode.createReroute(0, 0);
        rerouteIn.setId("reroute_in");
        rerouteIn.bindRerouteIngredient(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(rerouteIn);

        RecipeNode c1 = new RecipeNode("c1", "Consumer", 100, 100, GTVoltageTier.LV);
        c1.getInputs().add(IngredientStack.item(ironId, "Iron Ingot", 1.0));
        graph.addNode(c1);

        List<BoardCommand> subCommands = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> added = ToolbarWidget.autoConnect(graph, subCommands, null, Set.of("p1", "reroute_in", "c1"));

        Assertions.assertEquals(2, added.size());
        boolean hasDirectBypass = added.stream().anyMatch(e -> e.fromNodeId().equals("p1") && e.toNodeId().equals("c1"));
        Assertions.assertFalse(hasDirectBypass);
        Assertions.assertTrue(added.stream().anyMatch(e -> e.fromNodeId().equals("p1") && e.toNodeId().equals("reroute_in")));
        Assertions.assertTrue(added.stream().anyMatch(e -> e.fromNodeId().equals("reroute_in") && e.toNodeId().equals("c1")));
    }

    @Test
    public void testAlternativeSelectionWithinSelectionAndUndo() {
        BoardScreen screen = new BoardScreen();
        FlowGraph graph = screen.getGraph();
        ResourceLocation primaryId = ResourceLocation.tryParse("minecraft:charcoal");
        ResourceLocation altId = ResourceLocation.tryParse("minecraft:coal");

        RecipeNode p = new RecipeNode("p", "Coal Producer", 100, 100, GTVoltageTier.LV);
        p.getOutputs().add(IngredientStack.item(altId, "Coal", 1.0));
        graph.addNode(p);

        RecipeNode c = new RecipeNode("c", "Fuel Consumer", 100, 100, GTVoltageTier.LV);
        IngredientStack inputWithAlt = IngredientStack.item(primaryId, "Charcoal", 1.0);
        inputWithAlt.setAlternatives(List.of(primaryId, altId));
        c.getInputs().add(inputWithAlt);
        graph.addNode(c);

        ToolbarActionHandler.performAutoConnectForSelection(screen, Set.of("p", "c"));
        Assertions.assertEquals(1, graph.getConnections().size());
        Assertions.assertEquals(altId, c.getInputs().get(0).getId());

        screen.undo();
        Assertions.assertEquals(0, graph.getConnections().size());
        Assertions.assertEquals(primaryId, c.getInputs().get(0).getId());

        screen.redo();
        Assertions.assertEquals(1, graph.getConnections().size());
        Assertions.assertEquals(altId, c.getInputs().get(0).getId());
    }

    @Test
    public void testSelectionAutoConnectNullGraphSafety() {
        BoardScreen nullGraphScreen = new BoardScreen() {
            @Override
            public FlowGraph getGraph() {
                return null;
            }
        };
        Assertions.assertDoesNotThrow(() -> ToolbarActionHandler.performAutoConnectForSelection(nullGraphScreen, Set.of("n1", "n2")));
    }
}
