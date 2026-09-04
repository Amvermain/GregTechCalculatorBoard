package com.gtceu.calcboard.integration.ae2;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.integration.ae2.generator.Ae2PatternPageGenerator;
import com.gtceu.calcboard.integration.ae2.registry.PatternGraphRegistry;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Ae2PatternPageGeneratorTest {

    @BeforeEach
    public void setup() {
        BoardManager.getInstance().resetToDefault();
        PatternGraphRegistry.getInstance().clear();
    }

    @Test
    public void testPureJunctionNodeGeneration() {
        ItemStack patternStack = new ItemStack(Items.IRON_INGOT);

        BoardPage page = Ae2PatternPageGenerator.createPageFromPatternStack(patternStack, null);

        Assertions.assertNotNull(page);
        Assertions.assertEquals("ae2", page.getFolderPath());

        FlowGraph graph = page.getGraph();
        Assertions.assertFalse(graph.getNodes().isEmpty());

        // Verify all generated nodes are pure Junction (reroute) nodes with target batch amount set
        for (RecipeNode node : graph.getNodes()) {
            Assertions.assertTrue(node.isReroute(), "All auto-generated nodes should be Junction (Reroute) nodes");
            Assertions.assertTrue(node.hasTargetBatch(), "Junction node must have target batch amount configured for Estimate Time");
            Assertions.assertTrue(node.getTargetBatchAmount() >= 1.0);
        }

        Assertions.assertTrue(PatternGraphRegistry.getInstance().isPageBound(page.getId()));
    }
}
