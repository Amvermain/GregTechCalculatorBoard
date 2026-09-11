package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class StoichiometricBatchViewTest {

    @Test
    public void testElectrolyzerToChemicalReactorStoichiometric1to1() {
        FlowGraph graph = new FlowGraph();

        RecipeNode electrolyzer = RecipeNode.create("Electrolyzer", 100.0, 30.0, GTVoltageTier.LV);
        electrolyzer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0, 1.0));
        electrolyzer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrogen"), "Hydrogen", 2000.0, 1.0));
        electrolyzer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 1000.0, 1.0));
        graph.addNode(electrolyzer);

        RecipeNode reactor = RecipeNode.create("Chemical Reactor", 30.0, 30.0, GTVoltageTier.LV);
        reactor.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrogen"), "Hydrogen", 2000.0, 1.0));
        reactor.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:chlorine"), "Chlorine", 1000.0, 1.0));
        reactor.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrochloric_acid"), "Hydrochloric Acid", 2000.0, 1.0));
        graph.addNode(reactor);

        graph.addConnection(electrolyzer.getId(), 0, reactor.getId(), 0);

        FlowGraphSolver.PortFlowStats inStats = graph.getBatchInputPortStats(reactor, 0);
        Assertions.assertTrue(inStats.isConnected());
        Assertions.assertEquals(2000.0, inStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(2000.0, inStats.connectedRate(), 0.001);
        Assertions.assertTrue(inStats.isBalanced());
        Assertions.assertFalse(inStats.isInputDeficit());

        FlowGraphSolver.PortFlowStats outStats = graph.getBatchOutputPortStats(electrolyzer, 0);
        Assertions.assertTrue(outStats.isConnected());
        Assertions.assertEquals(2000.0, outStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(2000.0, outStats.connectedRate(), 0.001);
        Assertions.assertTrue(outStats.isBalanced());
        Assertions.assertFalse(outStats.isOutputDeficit());
    }

    @Test
    public void testBatchPortSplitFanOut() {
        FlowGraph graph = new FlowGraph();

        RecipeNode electrolyzer = RecipeNode.create("Electrolyzer", 100.0, 30.0, GTVoltageTier.LV);
        electrolyzer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0, 1.0));
        electrolyzer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrogen"), "Hydrogen", 2000.0, 1.0));
        electrolyzer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 1000.0, 1.0));
        graph.addNode(electrolyzer);

        RecipeNode reactorA = RecipeNode.create("Reactor A", 30.0, 30.0, GTVoltageTier.LV);
        reactorA.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrogen"), "Hydrogen", 2000.0, 1.0));
        reactorA.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrochloric_acid"), "Hydrochloric Acid", 2000.0, 1.0));
        graph.addNode(reactorA);

        RecipeNode reactorB = RecipeNode.create("Reactor B", 30.0, 30.0, GTVoltageTier.LV);
        reactorB.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrogen"), "Hydrogen", 2000.0, 1.0));
        reactorB.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrochloric_acid"), "Hydrochloric Acid", 2000.0, 1.0));
        graph.addNode(reactorB);

        graph.addConnection(electrolyzer.getId(), 0, reactorA.getId(), 0);
        graph.addConnection(electrolyzer.getId(), 0, reactorB.getId(), 0);

        FlowGraphSolver.PortFlowStats statsA = graph.getBatchInputPortStats(reactorA, 0);
        Assertions.assertTrue(statsA.isConnected());
        Assertions.assertEquals(2000.0, statsA.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(1000.0, statsA.connectedRate(), 0.001);
        Assertions.assertTrue(statsA.isInputDeficit());
        Assertions.assertFalse(statsA.isBalanced());

        FlowGraphSolver.PortFlowStats statsB = graph.getBatchInputPortStats(reactorB, 0);
        Assertions.assertTrue(statsB.isConnected());
        Assertions.assertEquals(1000.0, statsB.connectedRate(), 0.001);
        Assertions.assertTrue(statsB.isInputDeficit());
    }

    @Test
    public void testBatchPortThroughReroute() {
        FlowGraph graph = new FlowGraph();

        RecipeNode electrolyzer = RecipeNode.create("Electrolyzer", 100.0, 30.0, GTVoltageTier.LV);
        electrolyzer.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0, 1.0));
        electrolyzer.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrogen"), "Hydrogen", 2000.0, 1.0));
        graph.addNode(electrolyzer);

        RecipeNode reroute = RecipeNode.createReroute(100.0, 100.0);
        graph.addNode(reroute);

        RecipeNode reactor = RecipeNode.create("Reactor", 30.0, 30.0, GTVoltageTier.LV);
        reactor.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrogen"), "Hydrogen", 2000.0, 1.0));
        reactor.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:hydrochloric_acid"), "Hydrochloric Acid", 2000.0, 1.0));
        graph.addNode(reactor);

        graph.addConnection(electrolyzer.getId(), 0, reroute.getId(), 0);
        graph.addConnection(reroute.getId(), 0, reactor.getId(), 0);

        FlowGraphSolver.PortFlowStats reactorStats = graph.getBatchInputPortStats(reactor, 0);
        Assertions.assertTrue(reactorStats.isConnected());
        Assertions.assertEquals(2000.0, reactorStats.requiredOrProducedRate(), 0.001);
        Assertions.assertEquals(2000.0, reactorStats.connectedRate(), 0.001);
        Assertions.assertTrue(reactorStats.isBalanced());
    }
}
