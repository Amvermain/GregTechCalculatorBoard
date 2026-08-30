package com.gtceu.calcboard.server.file;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BlueprintFileManager;
import com.gtceu.calcboard.api.storage.BlueprintMetadata;
import com.gtceu.calcboard.api.storage.BlueprintPackage;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

/**
 * Unit tests for BlueprintFileManager saving, loading, listing, and filename sanitization.
 */
public class BlueprintFileTest {

    @Test
    public void testSanitizeFilename() {
        Assertions.assertEquals("blueprint", BlueprintFileManager.sanitizeFilename(null));
        Assertions.assertEquals("blueprint", BlueprintFileManager.sanitizeFilename("   "));
        Assertions.assertEquals("Pyrolyse_ Stage_1", BlueprintFileManager.sanitizeFilename("Pyrolyse: Stage/1"));
        Assertions.assertEquals("Ethylene Plant", BlueprintFileManager.sanitizeFilename("Ethylene Plant *?<>|"));
        Assertions.assertEquals("blueprint", BlueprintFileManager.sanitizeFilename(":::///???"));
    }

    @Test
    public void testBlueprintFileSaveAndLoadRoundtrip(@TempDir Path tempDir) {
        File file = tempDir.resolve("test_plant.gtcb").toFile();

        FlowGraph graph = new FlowGraph();
        RecipeNode node = RecipeNode.create("Pyrolyse Oven", 100.0, 60.0, GTVoltageTier.MV);
        node.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 16.0));
        node.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:charcoal"), "Charcoal", 20.0));
        node.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_gas"), "Wood Gas", 4000.0, 1.0));
        graph.addNode(node);

        BlueprintMetadata meta = BlueprintMetadata.fromGraph(graph, "Pyrolyse Plant", "High efficiency charcoal production", "KrakelLP");
        BlueprintPackage pkg = new BlueprintPackage(meta, graph, 120.0, 180.0, 1.25);

        boolean saved = BlueprintFileManager.saveBlueprint(file, pkg);
        Assertions.assertTrue(saved);
        Assertions.assertTrue(file.exists());
        Assertions.assertTrue(file.length() > 0);

        BlueprintPackage loaded = BlueprintFileManager.loadBlueprint(file);
        Assertions.assertNotNull(loaded);
        Assertions.assertEquals("Pyrolyse Plant", loaded.getMetadata().getTitle());
        Assertions.assertEquals("High efficiency charcoal production", loaded.getMetadata().getDescription());
        Assertions.assertEquals("KrakelLP", loaded.getMetadata().getAuthor());
        Assertions.assertEquals(120.0, loaded.getPanX(), 0.001);
        Assertions.assertEquals(180.0, loaded.getPanY(), 0.001);
        Assertions.assertEquals(1.25, loaded.getZoom(), 0.001);
        Assertions.assertEquals(1, loaded.getGraph().getNodes().size());

        boolean deleted = BlueprintFileManager.deleteBlueprint(file);
        Assertions.assertTrue(deleted);
        Assertions.assertFalse(file.exists());
    }
}
