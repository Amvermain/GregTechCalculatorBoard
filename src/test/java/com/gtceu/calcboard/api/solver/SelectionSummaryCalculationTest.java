package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Set;

@ExtendWith(MinecraftBootstrapExtension.class)
public class SelectionSummaryCalculationTest {

    @Test
    public void testSubsetPowerAndMachineCount() {
        FlowGraph graph = new FlowGraph();

        RecipeNode m1 = RecipeNode.create("Macerator 1", 20.0, 30.0, GTVoltageTier.LV);
        m1.setMachineCount(1.0);
        graph.addNode(m1);

        RecipeNode m2 = RecipeNode.create("Macerator 2", 20.0, 60.0, GTVoltageTier.LV);
        m2.setMachineCount(1.0);
        graph.addNode(m2);

        RecipeNode m3 = RecipeNode.create("Furnace 1", 20.0, 120.0, GTVoltageTier.MV);
        m3.setMachineCount(1.0);
        graph.addNode(m3);

        RecipeNode m4 = RecipeNode.create("Furnace 2", 20.0, 240.0, GTVoltageTier.MV);
        m4.setMachineCount(1.0);
        graph.addNode(m4);

        BalanceSummary fullSummary = graph.computeSummary();
        Assertions.assertEquals(450.0, fullSummary.totalEUt(), 0.001);
        Assertions.assertEquals(4, fullSummary.totalMachineCount());
        Assertions.assertEquals(GTVoltageTier.MV, fullSummary.highestVoltageTier());

        BalanceSummary subsetSummary = FlowGraphSolver.computeSubsetSummary(graph, Set.of(m1.getId(), m2.getId()));
        Assertions.assertEquals(90.0, subsetSummary.totalEUt(), 0.001);
        Assertions.assertEquals(90.0, subsetSummary.peakEUt(), 0.001);
        Assertions.assertEquals(2, subsetSummary.totalMachineCount());
        Assertions.assertEquals(GTVoltageTier.LV, subsetSummary.highestVoltageTier());
        Assertions.assertEquals(2, subsetSummary.machineBreakdown().size());
    }

    @Test
    public void testSubsetMaterialInternalBalance() {
        FlowGraph graph = new FlowGraph();

        IngredientStack itemX = IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_ore"), "Crushed Ore", 1, 1.0);
        IngredientStack itemY = IngredientStack.item(ResourceLocation.tryParse("gtceu:purified_ore"), "Purified Ore", 1, 1.0);

        RecipeNode nodeA = RecipeNode.create("Node A", 20.0, 30.0, GTVoltageTier.LV);
        nodeA.addOutput(itemX);
        nodeA.setMachineCount(1.0);
        graph.addNode(nodeA);

        RecipeNode nodeB = RecipeNode.create("Node B", 20.0, 30.0, GTVoltageTier.LV);
        nodeB.addInput(itemX);
        nodeB.addOutput(itemY);
        nodeB.setMachineCount(1.0);
        graph.addNode(nodeB);

        RecipeNode nodeC = RecipeNode.create("Node C", 20.0, 30.0, GTVoltageTier.LV);
        nodeC.addInput(itemY);
        nodeC.setMachineCount(1.0);
        graph.addNode(nodeC);

        graph.addConnection(new FlowGraph.ConnectionEdge(nodeA.getId(), 0, nodeB.getId(), 0));
        graph.addConnection(new FlowGraph.ConnectionEdge(nodeB.getId(), 0, nodeC.getId(), 0));

        graph.computeSummary();

        BalanceSummary abSubset = FlowGraphSolver.computeSubsetSummary(graph, Set.of(nodeA.getId(), nodeB.getId()));
        Assertions.assertEquals(60.0, abSubset.totalEUt(), 0.001);
        Assertions.assertTrue(abSubset.rawInputs().isEmpty());
        Assertions.assertEquals(1, abSubset.netOutputs().size());
        Assertions.assertTrue(abSubset.netOutputs().containsKey(itemY));

        BalanceSummary bcSubset = FlowGraphSolver.computeSubsetSummary(graph, Set.of(nodeB.getId(), nodeC.getId()));
        Assertions.assertEquals(60.0, bcSubset.totalEUt(), 0.001);
        Assertions.assertEquals(1, bcSubset.rawInputs().size());
        Assertions.assertTrue(bcSubset.rawInputs().containsKey(itemX));
        Assertions.assertTrue(bcSubset.netOutputs().isEmpty());
    }

    @Test
    public void testEmptyOrFullSelectionFallback() {
        FlowGraph graph = new FlowGraph();

        RecipeNode n1 = RecipeNode.create("Node 1", 20.0, 32.0, GTVoltageTier.LV);
        n1.setMachineCount(1.0);
        graph.addNode(n1);

        RecipeNode n2 = RecipeNode.create("Node 2", 20.0, 64.0, GTVoltageTier.LV);
        n2.setMachineCount(1.0);
        graph.addNode(n2);

        BalanceSummary fullSummary = graph.computeSummary();

        BalanceSummary emptySubset = FlowGraphSolver.computeSubsetSummary(graph, Set.of());
        Assertions.assertEquals(fullSummary.totalEUt(), emptySubset.totalEUt(), 0.001);
        Assertions.assertEquals(fullSummary.totalMachineCount(), emptySubset.totalMachineCount());

        BalanceSummary allSubset = FlowGraphSolver.computeSubsetSummary(graph, Set.of(n1.getId(), n2.getId()));
        Assertions.assertEquals(fullSummary.totalEUt(), allSubset.totalEUt(), 0.001);
        Assertions.assertEquals(fullSummary.totalMachineCount(), allSubset.totalMachineCount());
    }
}
