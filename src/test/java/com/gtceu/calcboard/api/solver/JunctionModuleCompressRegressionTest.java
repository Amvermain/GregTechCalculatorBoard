package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

public class JunctionModuleCompressRegressionTest {

    @Test
    public void testCompressFrameWithJunctionPreservesNetWorth() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation hotBrineId = ResourceLocation.tryParse("gtceu:hot_brine");
        ResourceLocation hclId = ResourceLocation.tryParse("gtceu:hydrochloric_acid");
        ResourceLocation debrominatedBrineId = ResourceLocation.tryParse("gtceu:debrominated_brine");
        ResourceLocation hiId = ResourceLocation.tryParse("gtceu:hydrogen_iodide");
        ResourceLocation potassiumDustId = ResourceLocation.tryParse("gtceu:potassium_dust");
        ResourceLocation rockSaltId = ResourceLocation.tryParse("gtceu:rock_salt");
        ResourceLocation rawBrineId = ResourceLocation.tryParse("gtceu:raw_brine");

        RecipeNode m1 = RecipeNode.create("Chemical Reactor 1", 20.0, 30.0, GTVoltageTier.MV);
        m1.addInput(IngredientStack.fluid(hotBrineId, "Hot Brine", 30.0));
        m1.addInput(IngredientStack.fluid(hclId, "Hydrochloric Acid", 15.0));
        m1.addOutput(IngredientStack.fluid(debrominatedBrineId, "Debrominated Brine", 30.0));
        m1.addOutput(IngredientStack.fluid(hiId, "Hydrogen Iodide", 15.0));

        RecipeNode m2 = RecipeNode.create("Chemical Reactor 2", 20.0, 30.0, GTVoltageTier.MV);
        m2.addInput(IngredientStack.item(potassiumDustId, "Potassium Dust", 0.01));
        m2.addInput(IngredientStack.fluid(debrominatedBrineId, "Debrominated Brine", 30.0));
        m2.addOutput(IngredientStack.item(rockSaltId, "Rock Salt", 0.02));
        m2.addOutput(IngredientStack.fluid(hotBrineId, "Hot Brine", 20.0));

        RecipeNode m3 = RecipeNode.create("Chemical Reactor 3", 20.0, 30.0, GTVoltageTier.MV);
        m3.addInput(IngredientStack.fluid(hotBrineId, "Hot Brine", 20.0));
        m3.addInput(IngredientStack.fluid(rawBrineId, "Raw Brine", 20.0));
        m3.addOutput(IngredientStack.fluid(hotBrineId, "Hot Brine", 30.0));
        m3.addOutput(IngredientStack.fluid(debrominatedBrineId, "Debrominated Brine", 20.0));

        RecipeNode junction = RecipeNode.createReroute(100.0, 100.0);
        junction.bindRerouteIngredient(IngredientStack.fluid(hotBrineId, "Hot Brine", 2000.0));

        graph.addNode(m1);
        graph.addNode(m2);
        graph.addNode(m3);
        graph.addNode(junction);

        // Internal wiring
        // m3 output 0 (Hot Brine 30) -> junction
        graph.addConnection(m3.getId(), 0, junction.getId(), 0);
        // junction -> m1 input 0 (Hot Brine 30)
        graph.addConnection(junction.getId(), 0, m1.getId(), 0);
        // m1 output 0 (Debrominated Brine 30) -> m2 input 1 (Debrominated Brine 30)
        graph.addConnection(m1.getId(), 0, m2.getId(), 1);
        // m2 output 1 (Hot Brine 20) -> m3 input 0 (Hot Brine 20)
        graph.addConnection(m2.getId(), 1, m3.getId(), 0);

        BalanceSummary summaryBefore = FlowGraphSolver.computeSummary(graph);

        // Hot Brine is recirculated internally, so it should not appear in raw inputs or net outputs
        Assertions.assertFalse(summaryBefore.rawInputs().keySet().stream()
                .anyMatch(s -> s.getId().equals(hotBrineId)), "Hot Brine must NOT be in raw inputs before compression");
        Assertions.assertFalse(summaryBefore.netOutputs().keySet().stream()
                .anyMatch(s -> s.getId().equals(hotBrineId)), "Hot Brine must NOT be in net outputs before compression");

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Group Frame", List.of(junction, m1, m2, m3), CanvasGroupFrame.COLOR_PURPLE);
        graph.addFrame(frame);

        RecipeNode module = graph.groupIntoModule(Set.of(junction.getId(), m1.getId(), m2.getId(), m3.getId()), "Group Frame", frame);
        Assertions.assertNotNull(module, "Module must be created");

        BalanceSummary summaryAfter = FlowGraphSolver.computeSummary(graph);

        // Hot Brine must NOT be present as an external module input or output
        boolean moduleHasHotBrineInput = module.getInputs().stream().anyMatch(s -> s.getId().equals(hotBrineId));
        boolean moduleHasHotBrineOutput = module.getOutputs().stream().anyMatch(s -> s.getId().equals(hotBrineId));
        Assertions.assertFalse(moduleHasHotBrineInput, "Module node must NOT have Hot Brine as an input port");
        Assertions.assertFalse(moduleHasHotBrineOutput, "Module node must NOT have Hot Brine as an output port");

        // Process summary must remain identical to before compression
        Assertions.assertFalse(summaryAfter.rawInputs().keySet().stream()
                .anyMatch(s -> s.getId().equals(hotBrineId)), "Hot Brine must NOT be in raw inputs after compression");
        Assertions.assertFalse(summaryAfter.netOutputs().keySet().stream()
                .anyMatch(s -> s.getId().equals(hotBrineId)), "Hot Brine must NOT be in net outputs after compression");

        // Compare raw inputs counts and net outputs counts
        Assertions.assertEquals(summaryBefore.rawInputs().size(), summaryAfter.rawInputs().size(), "Raw input count mismatch");
        Assertions.assertEquals(summaryBefore.netOutputs().size(), summaryAfter.netOutputs().size(), "Net output count mismatch");
    }

    @Test
    public void testCompressFrameWithExternalJunctionSupplyPreservesRatesAndEfficiency() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation hotBrineId = ResourceLocation.tryParse("gtceu:hot_brine");
        ResourceLocation hclId = ResourceLocation.tryParse("gtceu:hydrochloric_acid");
        ResourceLocation debrominatedBrineId = ResourceLocation.tryParse("gtceu:debrominated_brine");
        ResourceLocation hiId = ResourceLocation.tryParse("gtceu:hydrogen_iodide");
        ResourceLocation potassiumDustId = ResourceLocation.tryParse("gtceu:potassium_dust");
        ResourceLocation rockSaltId = ResourceLocation.tryParse("gtceu:rock_salt");
        ResourceLocation rawBrineId = ResourceLocation.tryParse("gtceu:raw_brine");

        RecipeNode m1 = RecipeNode.create("Chemical Reactor 1", 20.0, 30.0, GTVoltageTier.MV);
        m1.addInput(IngredientStack.fluid(hotBrineId, "Hot Brine", 30.0));
        m1.addInput(IngredientStack.fluid(hclId, "Hydrochloric Acid", 15.0));
        m1.addOutput(IngredientStack.fluid(debrominatedBrineId, "Debrominated Brine", 30.0));
        m1.addOutput(IngredientStack.fluid(hiId, "Hydrogen Iodide", 15.0));

        RecipeNode m2 = RecipeNode.create("Chemical Reactor 2", 20.0, 30.0, GTVoltageTier.MV);
        m2.addInput(IngredientStack.item(potassiumDustId, "Potassium Dust", 0.01));
        m2.addInput(IngredientStack.fluid(debrominatedBrineId, "Debrominated Brine", 30.0));
        m2.addOutput(IngredientStack.item(rockSaltId, "Rock Salt", 0.02));
        m2.addOutput(IngredientStack.fluid(hotBrineId, "Hot Brine", 20.0));

        RecipeNode m3 = RecipeNode.create("Chemical Reactor 3", 20.0, 30.0, GTVoltageTier.MV);
        m3.addInput(IngredientStack.fluid(hotBrineId, "Hot Brine", 20.0));
        m3.addInput(IngredientStack.fluid(rawBrineId, "Raw Brine", 20.0));
        m3.addOutput(IngredientStack.fluid(debrominatedBrineId, "Debrominated Brine", 20.0));
        m3.addOutput(IngredientStack.fluid(hotBrineId, "Hot Brine", 20.0));

        // External junction node outside group frame providing 10.0 mB/t Hot Brine
        RecipeNode externalJunction = RecipeNode.createReroute(20.0, 100.0);
        externalJunction.bindRerouteIngredient(IngredientStack.fluid(hotBrineId, "Hot Brine", 1000.0));
        externalJunction.setSupplyMode(com.gtceu.calcboard.api.type.SupplyMode.FIXED_RATE);
        externalJunction.setExternalSupplyRate(10.0);

        graph.addNode(m1);
        graph.addNode(m2);
        graph.addNode(m3);
        graph.addNode(externalJunction);

        // Wiring:
        // External Junction -> m1 input 0 (Hot Brine 10 mB/t)
        graph.addConnection(externalJunction.getId(), 0, m1.getId(), 0);
        // m3 output 1 (Hot Brine 20 mB/t) -> m1 input 0
        graph.addConnection(m3.getId(), 1, m1.getId(), 0);
        // m1 output 0 (Debrominated Brine 30 mB/t) -> m2 input 1
        graph.addConnection(m1.getId(), 0, m2.getId(), 1);
        // m2 output 1 (Hot Brine 20 mB/t) -> m3 input 0
        graph.addConnection(m2.getId(), 1, m3.getId(), 0);

        BalanceSummary summaryBefore = FlowGraphSolver.computeSummary(graph);
        Assertions.assertEquals(1.0, m1.getEfficiency(), 0.001, "m1 must be 100% efficient before compression");
        Assertions.assertEquals(1.0, m2.getEfficiency(), 0.001, "m2 must be 100% efficient before compression");
        Assertions.assertEquals(1.0, m3.getEfficiency(), 0.001, "m3 must be 100% efficient before compression");
        Assertions.assertEquals(90.0, summaryBefore.totalEUt(), 0.001, "Total EU/t before compression");

        // Group frame contains ONLY m1, m2, m3 (externalJunction is left outside)
        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Group Frame", List.of(m1, m2, m3), CanvasGroupFrame.COLOR_PURPLE);
        graph.addFrame(frame);

        RecipeNode module = graph.groupIntoModule(Set.of(m1.getId(), m2.getId(), m3.getId()), "Group Frame", frame);
        Assertions.assertNotNull(module, "Module must be created");

        BalanceSummary summaryAfter = FlowGraphSolver.computeSummary(graph);

        // Module input rate for Hot Brine must be 10.0 mB/t (30 - 20 internal supply), NOT 30.0 mB/t
        IngredientStack hotBrineInput = module.getInputs().stream()
                .filter(s -> s.getId().equals(hotBrineId))
                .findFirst()
                .orElse(null);
        Assertions.assertNotNull(hotBrineInput, "Module node must have Hot Brine input port");
        Assertions.assertEquals(10.0, hotBrineInput.getAmount(), 0.001, "Module Hot Brine input rate must be 10 mB/t");

        // Module node must NOT produce Hot Brine as an output (surplus should be 0)
        boolean moduleHasHotBrineOutput = module.getOutputs().stream().anyMatch(s -> s.getId().equals(hotBrineId));
        Assertions.assertFalse(moduleHasHotBrineOutput, "Module node must NOT have Hot Brine as an output port");

        // Module efficiency must remain 1.0 (not drop to 0.3333)
        Assertions.assertEquals(1.0, module.getEfficiency(), 0.001, "Module node must remain 100% efficient");
        Assertions.assertEquals(90.0, summaryAfter.totalEUt(), 0.001, "Total EU/t after compression must match");
    }
}
