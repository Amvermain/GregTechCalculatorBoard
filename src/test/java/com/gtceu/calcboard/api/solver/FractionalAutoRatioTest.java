package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FractionalAutoRatioTest {

    @Test
    public void testFractionalAutoRatioExactDecimals() {
        FlowGraph graph = new FlowGraph();
        ResourceLocation plateId = ResourceLocation.tryParse("gtceu:aerogel_sheet");

        // Fast supplier (1 plate per second: 20 ticks duration, 1.0 amount)
        RecipeNode extractor = RecipeNode.create(ResourceLocation.tryParse("gtceu:extractor"), "Extractor", 20, 30, GTVoltageTier.LV);
        extractor.getOutputs().add(IngredientStack.item(plateId, "Aerogel Sheet", 1.0));
        graph.addNode(extractor);

        // Slow consumer (1 plate per 12 seconds: 240 ticks duration, 1.0 amount) -> ~0.0833 demand/s
        RecipeNode reactor = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "Reactor", 240, 30, GTVoltageTier.LV);
        reactor.setBaseNode(true);
        reactor.setMachineCount(1.0);
        reactor.getInputs().add(IngredientStack.item(plateId, "Aerogel Sheet", 1.0));
        graph.addNode(reactor);

        graph.addConnection(extractor.getId(), 0, reactor.getId(), 0);

        // 1. Fractional Auto-Ratio
        graph.autoRatioFractional(reactor);

        // Extractor should be ~0.08333 machines, not rounded up to 1.0!
        double expectedCount = 1.0 / 12.0;
        Assertions.assertEquals(expectedCount, extractor.getMachineCount(), 0.005,
                "Extractor machine count must be ~0.0833 without integer ceil! Actual: " + extractor.getMachineCount());

        // 2. Compare with regular integer Auto-Ratio
        graph.autoRatioFromAnchor(reactor);
        Assertions.assertEquals(1.0, extractor.getMachineCount(), 0.001,
                "Standard autoRatioFromAnchor must round up to integer 1.0! Actual: " + extractor.getMachineCount());
    }

    @Test
    public void testPreserveFractionalAnchorWhenEnabled() {
        BoardManager bm = BoardManager.getInstance();
        boolean origPreserve = bm.isPreserveFractionalAnchor();
        try {
            bm.setPreserveFractionalAnchor(true);

            FlowGraph graph = new FlowGraph();
            ResourceLocation itemId = ResourceLocation.tryParse("gtceu:silicon_ingot");

            RecipeNode supplier = RecipeNode.create(ResourceLocation.tryParse("gtceu:ebf"), "EBF", 20, 120, GTVoltageTier.MV);
            supplier.getOutputs().add(IngredientStack.item(itemId, "Silicon Ingot", 1.0));
            graph.addNode(supplier);

            RecipeNode anchor = RecipeNode.create(ResourceLocation.tryParse("gtceu:cutter"), "Cutter", 20, 30, GTVoltageTier.LV);
            anchor.setBaseNode(true);
            anchor.setMachineCount(0.125);
            anchor.getInputs().add(IngredientStack.item(itemId, "Silicon Ingot", 1.0));
            graph.addNode(anchor);

            graph.addConnection(supplier.getId(), 0, anchor.getId(), 0);

            // Execute standard autoRatioFromAnchor
            graph.autoRatioFromAnchor(anchor);

            // Anchor's fractional count (0.125) must be preserved!
            Assertions.assertEquals(0.125, anchor.getMachineCount(), 0.0001,
                    "Anchor fractional count must be preserved when preserveFractionalAnchor is true!");

            // Supplier is ceil(0.125) = 1.0 under standard integer quantization
            Assertions.assertEquals(1.0, supplier.getMachineCount(), 0.0001);
        } finally {
            bm.setPreserveFractionalAnchor(origPreserve);
        }
    }

    @Test
    public void testPreserveFractionalAnchorWhenDisabled() {
        BoardManager bm = BoardManager.getInstance();
        boolean origPreserve = bm.isPreserveFractionalAnchor();
        try {
            bm.setPreserveFractionalAnchor(false);

            FlowGraph graph = new FlowGraph();
            ResourceLocation itemId = ResourceLocation.tryParse("gtceu:silicon_ingot");

            RecipeNode supplier = RecipeNode.create(ResourceLocation.tryParse("gtceu:ebf"), "EBF", 20, 120, GTVoltageTier.MV);
            supplier.getOutputs().add(IngredientStack.item(itemId, "Silicon Ingot", 1.0));
            graph.addNode(supplier);

            RecipeNode anchor = RecipeNode.create(ResourceLocation.tryParse("gtceu:cutter"), "Cutter", 20, 30, GTVoltageTier.LV);
            anchor.setBaseNode(true);
            anchor.setMachineCount(0.125);
            anchor.getInputs().add(IngredientStack.item(itemId, "Silicon Ingot", 1.0));
            graph.addNode(anchor);

            graph.addConnection(supplier.getId(), 0, anchor.getId(), 0);

            // Execute standard autoRatioFromAnchor
            graph.autoRatioFromAnchor(anchor);

            // When disabled, anchor count must be rounded up to ceil(0.125) = 1.0!
            Assertions.assertEquals(1.0, anchor.getMachineCount(), 0.0001,
                    "Anchor fractional count should be rounded up to 1.0 when preserveFractionalAnchor is false!");
        } finally {
            bm.setPreserveFractionalAnchor(origPreserve);
        }
    }
}
