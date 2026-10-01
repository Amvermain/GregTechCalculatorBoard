package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SupplyMode;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

public class CrossPageJunctionCombineBugTest {

    private RecipeNode createJunction(String id, String name) {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:junction"), name, 20, 0, GTVoltageTier.LV);
        node.setId(id);
        node.setReroute(true);
        node.bindRerouteIngredient(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen Gas", 1));
        return node;
    }

    private RecipeNode createProducer(String id, String name, double rate) {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:producer"), name, 20, 30, GTVoltageTier.LV);
        node.setId(id);
        node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen Gas", rate));
        return node;
    }

    private RecipeNode createConsumer(String id, String name, double requiredRate) {
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:consumer"), name, 20, 30, GTVoltageTier.LV);
        node.setId(id);
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:other"), "Other Fluid", 100.0));
        node.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen Gas", requiredRate));
        return node;
    }

    @Test
    @DisplayName("Cross-page junction combined with local producer into intermediate relay and downstream machine")
    void testCrossPageJunctionCombinedWithLocalProducerIntoDownstreamMachine() {
        BoardPage pageA = new BoardPage("page_a", "Biotite Electrolyzer", new FlowGraph());
        RecipeNode electrolyzer = createProducer("electrolyzer", "Biotite Electrolyzer", 34.0);
        RecipeNode sourceJunc = createJunction("src_oxygen", "Oxygen Gas");
        sourceJunc.setSupplyMode(SupplyMode.NONE);
        pageA.getGraph().addNode(electrolyzer);
        pageA.getGraph().addNode(sourceJunc);
        pageA.getGraph().addConnection(new FlowGraph.ConnectionEdge(electrolyzer.getId(), 0, sourceJunc.getId(), 0));

        BoardPage pageB = new BoardPage("page_b", "Main Page", new FlowGraph());
        RecipeNode centrifuge = createProducer("centrifuge", "Centrifuge (Air)", 700.0);
        RecipeNode crossPageJunc = createJunction("cross_junc", "Oxygen Cross-Page In");
        crossPageJunc.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        crossPageJunc.setLinkedSource("page_a", "src_oxygen");

        RecipeNode rightmostJunc = createJunction("rightmost_junc", "Oxygen Combined Relay");
        rightmostJunc.setSupplyMode(SupplyMode.NONE);

        RecipeNode machine = createConsumer("reactor", "Large Chemical Reactor", 80000.0);

        pageB.getGraph().addNode(centrifuge);
        pageB.getGraph().addNode(crossPageJunc);
        pageB.getGraph().addNode(rightmostJunc);
        pageB.getGraph().addNode(machine);

        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(centrifuge.getId(), 0, rightmostJunc.getId(), 0));
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(crossPageJunc.getId(), 0, rightmostJunc.getId(), 0));
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(rightmostJunc.getId(), 0, machine.getId(), 1));

        WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB));

        Assertions.assertEquals(34.0, crossPageJunc.getAllocatedInputRate(), 0.001);
        double inflow = ProductionETACalculator.calculateNetInflowRate(pageB.getGraph(), rightmostJunc, 0);
        Assertions.assertEquals(734.0, inflow, 0.001);
    }

    @Test
    @DisplayName("Cross-page junction combined with local producer into terminal batch junction without downstream machine")
    void testCrossPageJunctionCombinedIntoTerminalJunction() {
        BoardPage pageA = new BoardPage("page_a", "Biotite Electrolyzer", new FlowGraph());
        RecipeNode electrolyzer = createProducer("electrolyzer", "Biotite Electrolyzer", 34.0);
        RecipeNode sourceJunc = createJunction("src_oxygen", "Oxygen Gas");
        sourceJunc.setSupplyMode(SupplyMode.NONE);
        pageA.getGraph().addNode(electrolyzer);
        pageA.getGraph().addNode(sourceJunc);
        pageA.getGraph().addConnection(new FlowGraph.ConnectionEdge(electrolyzer.getId(), 0, sourceJunc.getId(), 0));

        BoardPage pageB = new BoardPage("page_b", "Main Page", new FlowGraph());
        RecipeNode centrifuge = createProducer("centrifuge", "Centrifuge (Air)", 700.0);
        RecipeNode crossPageJunc = createJunction("cross_junc", "Oxygen Cross-Page In");
        crossPageJunc.setSupplyMode(SupplyMode.LINKED_JUNCTION);
        crossPageJunc.setLinkedSource("page_a", "src_oxygen");

        RecipeNode terminalJunc = createJunction("terminal_junc", "Oxygen Batch Buffer");
        terminalJunc.setSupplyMode(SupplyMode.NONE);
        terminalJunc.setTargetBatchAmount(10.0);

        pageB.getGraph().addNode(centrifuge);
        pageB.getGraph().addNode(crossPageJunc);
        pageB.getGraph().addNode(terminalJunc);

        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(centrifuge.getId(), 0, terminalJunc.getId(), 0));
        pageB.getGraph().addConnection(new FlowGraph.ConnectionEdge(crossPageJunc.getId(), 0, terminalJunc.getId(), 0));

        WorkspaceFlowCoordinator.coordinate(List.of(pageA, pageB));

        Assertions.assertEquals(34.0, crossPageJunc.getAllocatedInputRate(), 0.001);
        double inflow = ProductionETACalculator.calculateNetInflowRate(pageB.getGraph(), terminalJunc, 0);
        Assertions.assertEquals(734.0, inflow, 0.001);
    }
}
