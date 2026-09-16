package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.linear.TwoStageLinearFlowSolver;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class SolverNegativeIndexTest {

    private RecipeNode createProducer() {
        RecipeNode producer = RecipeNode.create("Test Producer", 20.0, 30.0, GTVoltageTier.LV);
        producer.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 4.0, 1.0));
        producer.setMachineCount(1.0);
        return producer;
    }

    private RecipeNode createConsumer() {
        RecipeNode consumer = RecipeNode.create("Test Consumer", 20.0, 30.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 2.0, 1.0));
        consumer.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:iron_plate"), "Iron Plate", 1.0, 1.0));
        consumer.setMachineCount(1.0);
        return consumer;
    }

    @Test
    @DisplayName("RFC-058: TwoStageLinearFlowSolver safely skips edges with negative port indices without IOOBE")
    public void testTwoStageLinearFlowSolverNegativeIndex() {
        FlowGraph graph = new FlowGraph();
        RecipeNode p = createProducer();
        RecipeNode c = createConsumer();
        graph.addNode(p);
        graph.addNode(c);

        graph.addConnection(p.getId(), -1, c.getId(), 0);
        graph.addConnection(p.getId(), 0, c.getId(), -1);

        Assertions.assertDoesNotThrow(() -> {
            TwoStageLinearFlowSolver.SolveResult res = TwoStageLinearFlowSolver.solve(graph, c, true);
            Assertions.assertNotNull(res);
        });
    }

    @Test
    @DisplayName("RFC-058: HarmonizedRatioOptimizer returns default ratio on negative port indices without IOOBE")
    public void testHarmonizedRatioOptimizerNegativeIndex() {
        FlowGraph graph = new FlowGraph();
        RecipeNode p = createProducer();
        RecipeNode p2 = createProducer();
        RecipeNode c = createConsumer();
        graph.addNode(p);
        graph.addNode(p2);
        graph.addNode(c);

        // Edge with negative port index from alternate producer p2
        graph.addConnection(p2.getId(), -1, c.getId(), 0);

        Assertions.assertDoesNotThrow(() -> {
            double r1 = HarmonizedRatioOptimizer.calculateConsumerMatchCount(graph, p, -1, c, 0);
            Assertions.assertEquals(1.0, r1);

            double r2 = HarmonizedRatioOptimizer.calculateConsumerMatchCount(graph, p, 0, c, -1);
            Assertions.assertEquals(1.0, r2);

            // Valid indices but graph contains corrupted edge on p2 -> tests calculateAlternateIncomingSupply guard
            double rValid = HarmonizedRatioOptimizer.calculateConsumerMatchCount(graph, p, 0, c, 0);
            Assertions.assertTrue(rValid > 0.0);

            double r3 = HarmonizedRatioOptimizer.calculateProducerMatchCount(graph, p, -1, c, 0);
            Assertions.assertEquals(1.0, r3);

            double r4 = HarmonizedRatioOptimizer.calculateProducerMatchCount(graph, p, 0, c, -1);
            Assertions.assertEquals(1.0, r4);
        });
    }

    @Test
    @DisplayName("RFC-058: AutoRatioEngine and AutoRatioFlowTraverser handle negative indices without IOOBE")
    public void testAutoRatioFlowTraverserNegativeIndex() {
        FlowGraph graph = new FlowGraph();
        RecipeNode p = createProducer();
        RecipeNode c = createConsumer();
        graph.addNode(p);
        graph.addNode(c);

        graph.addConnection(p.getId(), -1, c.getId(), 0);
        graph.addConnection(p.getId(), 0, c.getId(), -1);

        Assertions.assertDoesNotThrow(() -> {
            double demand = AutoRatioFlowTraverser.calculateTotalConnectedPortDemand(graph, p, 0);
            Assertions.assertTrue(demand >= 0.0);

            // Test direct negative port query
            double negativeDemand = AutoRatioFlowTraverser.calculateTotalConnectedPortDemand(graph, p, -1);
            Assertions.assertEquals(0.0, negativeDemand);

            double supply = AutoRatioFlowTraverser.calculateEffectiveIncomingSupply(graph, c, 0, null);
            Assertions.assertTrue(supply >= 0.0);

            // Test direct negative port query
            double negativeSupply = AutoRatioFlowTraverser.calculateEffectiveIncomingSupply(graph, c, -1, null);
            Assertions.assertEquals(0.0, negativeSupply);
        });
    }

    @Test
    @DisplayName("RFC-058: MassBalanceSolver handles negative indices without IOOBE")
    public void testMassBalanceSolverNegativeIndex() {
        FlowGraph graph = new FlowGraph();
        RecipeNode p = createProducer();
        RecipeNode c = createConsumer();
        graph.addNode(p);
        graph.addNode(c);

        graph.addConnection(p.getId(), -1, c.getId(), 0);
        graph.addConnection(p.getId(), 0, c.getId(), -1);

        Assertions.assertDoesNotThrow(() -> {
            Map<String, Double> res = MassBalanceSolver.solve(graph, c, 1.0);
        });
    }
}
