package com.gtceu.calcboard.client.history;

import com.gtceu.calcboard.client.gui.widget.ToolbarWidget;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.history.BoardCommand;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

public class AutoConnectUndoTest {

    @Test
    public void testAddNodesCommandUndoRemovesEdges() {
        FlowGraph graph = new FlowGraph();

        RecipeNode n1 = new RecipeNode("n1", "Node 1", 100, 100, GTVoltageTier.LV);
        n1.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));

        RecipeNode n2 = new RecipeNode("n2", "Node 2", 100, 100, GTVoltageTier.LV);
        n2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));

        graph.addNode(n1);
        graph.addNode(n2);

        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge("n1", 0, "n2", 0);
        graph.addConnection("n1", 0, "n2", 0);

        Assertions.assertEquals(1, graph.getConnections().size());

        BoardCommand.AddNodesCommand cmd = new BoardCommand.AddNodesCommand(Collections.emptyList(), List.of(edge), "Auto Connect 1 wires");

        // Undo
        cmd.undo(graph);
        Assertions.assertEquals(0, graph.getConnections().size(), "Undo must remove added connection edges!");

        // Redo
        cmd.redo(graph);
        Assertions.assertEquals(1, graph.getConnections().size(), "Redo must restore added connection edges!");
    }

    @Test
    public void testAutoConnectPreservesRerouteJunctionsAndPreventsBypassDuplicateWires() {
        FlowGraph graph = new FlowGraph();

        // 3 Producers producing Aluminium Dust at output 1
        RecipeNode p1 = new RecipeNode("p1", "Macerator", 100, 100, GTVoltageTier.LV);
        p1.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_ore"), "Crushed Ore", 1.0));
        p1.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:aluminium_dust"), "Aluminium Dust", 1.0));
        graph.addNode(p1);

        RecipeNode p2 = new RecipeNode("p2", "Impure Dust", 100, 100, GTVoltageTier.LV);
        p2.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:impure_dust"), "Impure Dust", 1.0));
        p2.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:aluminium_dust"), "Aluminium Dust", 1.0));
        graph.addNode(p2);

        RecipeNode p3 = new RecipeNode("p3", "Pure Dust", 100, 100, GTVoltageTier.LV);
        p3.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:pure_dust"), "Pure Dust", 1.0));
        p3.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:aluminium_dust"), "Aluminium Dust", 1.0));
        graph.addNode(p3);

        // 1 Reroute Junction for Aluminium Dust
        RecipeNode reroute = RecipeNode.createReroute(0, 0);
        reroute.setId("reroute_1");
        reroute.bindRerouteIngredient(IngredientStack.item(ResourceLocation.tryParse("gtceu:aluminium_dust"), "Aluminium Dust", 1.0));
        graph.addNode(reroute);

        // 1 Consumer (Blast Furnace) taking Aluminium Dust at input 0
        RecipeNode consumer = new RecipeNode("c1", "Aluminium Ingot", 100, 100, GTVoltageTier.MV);
        consumer.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:aluminium_dust"), "Aluminium Dust", 1.0));
        graph.addNode(consumer);

        // Initial setup: p1, p2, p3 connected to reroute, reroute connected to consumer
        graph.addConnection("p1", 1, "reroute_1", 0);
        graph.addConnection("p2", 1, "reroute_1", 0);
        graph.addConnection("p3", 1, "reroute_1", 0);
        graph.addConnection("reroute_1", 0, "c1", 0);

        Assertions.assertEquals(4, graph.getConnections().size());

        // Perform Auto Connect
        List<FlowGraph.ConnectionEdge> added = com.gtceu.calcboard.client.gui.widget.ToolbarWidget.autoConnect(graph, null);

        // Must NOT add any duplicate or bypass edges
        Assertions.assertEquals(0, added.size(), "Auto Connect must not add bypass duplicate wires when nodes are already connected through a reroute junction!");
        Assertions.assertEquals(4, graph.getConnections().size());
    }
}



