package com.gtceu.calcboard.integration.ae2;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.integration.ae2.evaluator.PageDurationEvaluator;
import com.gtceu.calcboard.integration.ae2.model.PageProcessingSpec;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PageDurationEvaluatorTest {

    @Test
    public void testEmptyPageFallback() {
        BoardPage emptyPage = BoardPage.createDefault("Empty Page");
        PageProcessingSpec spec = PageDurationEvaluator.evaluate(emptyPage);

        Assertions.assertNotNull(spec);
        Assertions.assertEquals(20.0, spec.unitDurationTicks());
        Assertions.assertEquals(1, spec.effectiveParallel());
    }

    @Test
    public void testSingleMachineEvaluation() {
        BoardPage page = BoardPage.createDefault("Assembler Page");
        RecipeNode node = RecipeNode.create("Assembler", 100.0, 32.0, GTVoltageTier.LV);
        node.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:motor"), "Electric Motor", 1.0, 1.0));
        page.getGraph().addNode(node);

        PageProcessingSpec spec = PageDurationEvaluator.evaluate(page);
        Assertions.assertEquals(100.0, spec.unitDurationTicks(), 0.01);
        Assertions.assertEquals(1, spec.effectiveParallel());
        Assertions.assertEquals("Electric Motor", spec.primaryOutputName());
    }

    @Test
    public void testOverclockedAndParallelMachineEvaluation() {
        BoardPage page = BoardPage.createDefault("EBF Titanium");
        RecipeNode ebf = RecipeNode.create("Electric Blast Furnace", 800.0, 120.0, GTVoltageTier.MV);
        ebf.setTargetTier(GTVoltageTier.HV);
        ebf.setParallel(4);
        ebf.setMachineCount(2.0);
        ebf.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:titanium_ingot"), "Titanium Ingot", 1.0, 1.0));
        page.getGraph().addNode(ebf);

        PageProcessingSpec spec = PageDurationEvaluator.evaluate(page);

        // Target Tier HV is 1 tier above MV, standard OC cuts duration in half: 800 / 2 = 400 ticks (20s)
        Assertions.assertEquals(400.0, spec.unitDurationTicks(), 0.01);
        // Effective parallel = 4 * 2 = 8
        Assertions.assertEquals(8, spec.effectiveParallel());
        Assertions.assertEquals("Titanium Ingot", spec.primaryOutputName());
    }
}
