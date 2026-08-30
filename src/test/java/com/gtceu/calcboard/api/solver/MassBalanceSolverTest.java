package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.CycleDetector;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.solver.MassBalanceSolver;
import com.gtceu.calcboard.api.solver.ProductionETACalculator;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Verifies RFC-005: Closed-Loop Mass-Conservative Linear Solver, Cycle Detection, and Multi-port ETA.
 */
public class MassBalanceSolverTest {

    @Test
    @DisplayName("RFC-005: CycleDetector accurately identifies DAG vs Cyclic feedback graphs")
    void testCycleDetector() {
        FlowGraph graph = new FlowGraph();
        RecipeNode nodeA = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Reactor A", 100, 30, null);
        RecipeNode nodeB = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Reactor B", 100, 30, null);
        RecipeNode nodeC = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Reactor C", 100, 30, null);

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addNode(nodeC);

        // DAG: A -> B -> C
        graph.addConnection(new FlowGraph.ConnectionEdge(nodeA.getId(), 0, nodeB.getId(), 0));
        graph.addConnection(new FlowGraph.ConnectionEdge(nodeB.getId(), 0, nodeC.getId(), 0));

        Assertions.assertFalse(CycleDetector.hasCycle(graph), "Linear chain A->B->C should be DAG (false)");

        // Add feedback: C -> A
        FlowGraph.ConnectionEdge feedback = new FlowGraph.ConnectionEdge(nodeC.getId(), 0, nodeA.getId(), 0);
        graph.addConnection(feedback);

        Assertions.assertTrue(CycleDetector.hasCycle(graph), "Cycle A->B->C->A should be detected as Cyclic (true)");
    }

    @Test
    @DisplayName("RFC-005: Closed-loop sulfuric acid feedback cycle solves exact stoichiometric ratios")
    void testMassBalanceSolverClosedLoopSulfuricAcid() {
        FlowGraph graph = new FlowGraph();

        // Node A: 2 SO2 + 1 O2 -> 2 SO3 (Duration: 20 ticks = 1 sec, CPS = 1.0)
        RecipeNode nodeA = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Oxidizer", 20, 30, null);
        nodeA.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfur_dioxide"), "SO2", 2000.0));
        nodeA.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "O2", 1000.0));
        nodeA.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfur_trioxide"), "SO3", 2000.0));

        // Node B: 1 SO3 + 1 H2O -> 1 H2SO4 (Duration: 20 ticks = 1 sec, CPS = 1.0)
        RecipeNode nodeB = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Hydrator", 20, 30, null);
        nodeB.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfur_trioxide"), "SO3", 2000.0));
        nodeB.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));
        nodeB.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "H2SO4", 1000.0));
        nodeB.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfur_dioxide"), "SO2 Recycle", 1000.0)); // 50% SO2 feedback

        graph.addNode(nodeA);
        graph.addNode(nodeB);

        // Forward connection: Node A Output 0 (SO3) -> Node B Input 0 (SO3)
        graph.addConnection(new FlowGraph.ConnectionEdge(nodeA.getId(), 0, nodeB.getId(), 0));

        // Feedback connection: Node B Output 1 (SO2 Recycle) -> Node A Input 0 (SO2)
        graph.addConnection(new FlowGraph.ConnectionEdge(nodeB.getId(), 1, nodeA.getId(), 0));

        Assertions.assertTrue(CycleDetector.hasCycle(graph));

        // Solve with Node B as anchor = 2 machines
        nodeB.setMachineCount(2.0);
        FlowGraphSolver.autoRatioFromAnchor(graph, nodeB, false);

        // Node B (2 machines) produces 2000 SO3/s -> Node A needs to produce 4000 SO3/s
        // Node A produces 2000 SO3/s per machine -> Node A should be 2.0 machines
        Assertions.assertEquals(2.0, nodeB.getMachineCount(), 0.01);
        Assertions.assertEquals(2.0, nodeA.getMachineCount(), 0.01);
    }

    @Test
    @DisplayName("RFC-005: ProductionETACalculator supports multi-port target index")
    void testProductionETAMultiPort() {
        FlowGraph graph = new FlowGraph();

        // Producer: Output 0: Iron (10/s), Output 1: Slag (5/s)
        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:arc_furnace"), "Arc Furnace", 20, 30, null);
        producer.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron", 10.0));
        producer.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:slag"), "Slag", 5.0));
        producer.setMachineCount(2.0); // 20 Iron/s, 10 Slag/s

        // Consumer: Input 0: Slag (from Producer Output 1)
        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:centrifuge"), "Slag Centrifuge", 20, 30, null);
        consumer.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:slag"), "Slag", 10.0));
        consumer.setMachineCount(1.0);

        graph.addNode(producer);
        graph.addNode(consumer);

        graph.addConnection(new FlowGraph.ConnectionEdge(producer.getId(), 1, consumer.getId(), 0));

        // Batch of 100 Slag via Port 1
        double etaSecPort1 = ProductionETACalculator.calculateETA(100.0, 10.0);
        Assertions.assertEquals(10.0, etaSecPort1, 0.01); // 100 / 10 = 10s

        double energyPort1 = ProductionETACalculator.calculateTotalEnergyForBatch(graph, consumer, 0, 100.0);
        Assertions.assertTrue(energyPort1 > 0.0);
    }
}
