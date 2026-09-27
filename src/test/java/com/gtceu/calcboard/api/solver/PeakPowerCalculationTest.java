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

@ExtendWith(MinecraftBootstrapExtension.class)
public class PeakPowerCalculationTest {

    @Test
    public void testBottleneckedAverageVsPeakPower() {
        FlowGraph graph = new FlowGraph();

        IngredientStack item = IngredientStack.item(ResourceLocation.tryParse("gtceu:rubber_drop"), "Rubber Drop", 1, 1.0);

        RecipeNode producer = RecipeNode.create("Extractor", 20.0, 30.0, GTVoltageTier.LV);
        producer.addOutput(item);
        producer.setMachineCount(1.0);
        graph.addNode(producer);

        RecipeNode consumer = RecipeNode.create("Chemical Reactor", 20.0, 60.0, GTVoltageTier.LV);
        consumer.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:rubber_drop"), "Rubber Drop", 2, 1.0));
        consumer.setMachineCount(1.0);
        graph.addNode(consumer);

        graph.addConnection(new FlowGraph.ConnectionEdge(producer.getId(), 0, consumer.getId(), 0));

        BalanceSummary summary = graph.computeSummary();

        Assertions.assertEquals(0.5, consumer.getEfficiency(), 0.001);
        Assertions.assertEquals(60.0, summary.totalEUt(), 0.001);
        Assertions.assertEquals(90.0, summary.peakEUt(), 0.001);
    }

    @Test
    public void testUnlinkedFullEfficiencyEqualPower() {
        FlowGraph graph = new FlowGraph();

        RecipeNode machine1 = RecipeNode.create("Macerator", 20.0, 32.0, GTVoltageTier.LV);
        machine1.setMachineCount(2.0);
        graph.addNode(machine1);

        RecipeNode machine2 = RecipeNode.create("Furnace", 20.0, 16.0, GTVoltageTier.LV);
        machine2.setMachineCount(1.0);
        graph.addNode(machine2);

        BalanceSummary summary = graph.computeSummary();

        double expectedPower = 32.0 * 2.0 + 16.0;
        Assertions.assertEquals(expectedPower, summary.totalEUt(), 0.001);
        Assertions.assertEquals(expectedPower, summary.peakEUt(), 0.001);
    }

    @Test
    public void testGeneratorPeakPowerWithBottleneck() {
        FlowGraph graph = new FlowGraph();

        IngredientStack fuel = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fuel"), "Fuel", 50, 1.0);

        RecipeNode supplier = RecipeNode.create("Fuel Supplier", 20.0, 0.0, GTVoltageTier.LV);
        supplier.addOutput(fuel);
        supplier.setMachineCount(1.0);
        graph.addNode(supplier);

        RecipeNode generator = RecipeNode.create("Basic Generator", 20.0, 32.0, GTVoltageTier.LV);
        generator.setGenerator(true);
        generator.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:fuel"), "Fuel", 100, 1.0));
        generator.setMachineCount(1.0);
        graph.addNode(generator);

        RecipeNode consumer = RecipeNode.create("Centrifuge", 20.0, 10.0, GTVoltageTier.LV);
        consumer.setMachineCount(1.0);
        graph.addNode(consumer);

        graph.addConnection(new FlowGraph.ConnectionEdge(supplier.getId(), 0, generator.getId(), 0));

        BalanceSummary summary = graph.computeSummary();

        Assertions.assertEquals(0.5, generator.getEfficiency(), 0.001);
        Assertions.assertEquals(-6.0, summary.totalEUt(), 0.001);
        Assertions.assertEquals(-22.0, summary.peakEUt(), 0.001);
    }
}
