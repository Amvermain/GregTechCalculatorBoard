package com.gtceu.calcboard.server.file;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BlueprintCodec;
import com.gtceu.calcboard.api.storage.BlueprintMetadata;
import com.gtceu.calcboard.api.storage.BlueprintPackage;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for BlueprintMetadata packaging, BlueprintPackage serialization, and legacy V1 compatibility.
 */
public class BlueprintMetadataTest {

    @Test
    public void testBlueprintMetadataGenerationAndSerialization() {
        FlowGraph graph = new FlowGraph();

        RecipeNode cracker = RecipeNode.create("Steam Cracker", 20.0, 2000.0, GTVoltageTier.HV);
        cracker.setMachineCount(2.0);
        cracker.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:naphtha"), "Naphtha", 1000.0, 1.0));
        cracker.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:ethylene"), "Ethylene", 600.0, 1.0));
        graph.addNode(cracker);

        RecipeNode poly = RecipeNode.create("Polymerizer", 20.0, 4000.0, GTVoltageTier.EV);
        poly.setMachineCount(1.0);
        poly.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:ethylene"), "Ethylene", 300.0, 1.0));
        poly.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:polyethylene_plate"), "Polyethylene Plate", 100.0, 1.0));
        graph.addNode(poly);

        graph.addConnection(cracker.getId(), 0, poly.getId(), 0);

        // Export to string with metadata
        String code = BlueprintCodec.exportToString(
                graph,
                "512A EV Polyethylene Factory",
                "Requires Steam Cracker and Polymerizer",
                "EngineerSkkub",
                150.0,
                250.0,
                1.5
        );

        Assertions.assertNotNull(code);
        Assertions.assertTrue(code.startsWith("GTBOARD:512A EV Polyethylene Factory:"));

        // Import package
        BlueprintPackage pkg = BlueprintCodec.importPackageFromString(code);
        Assertions.assertNotNull(pkg);
        Assertions.assertEquals(150.0, pkg.getPanX(), 0.001);
        Assertions.assertEquals(250.0, pkg.getPanY(), 0.001);
        Assertions.assertEquals(1.5, pkg.getZoom(), 0.001);

        BlueprintMetadata meta = pkg.getMetadata();
        Assertions.assertNotNull(meta);
        Assertions.assertEquals("512A EV Polyethylene Factory", meta.getTitle());
        Assertions.assertEquals("Requires Steam Cracker and Polymerizer", meta.getDescription());
        Assertions.assertEquals("EngineerSkkub", meta.getAuthor());
        Assertions.assertEquals(2, meta.getNodeCount());
        Assertions.assertEquals(3, meta.getMachineCount());
        Assertions.assertEquals(1, meta.getConnectionCount());
        Assertions.assertEquals(8000.0, meta.getNetEuPerTick(), 0.001);

        // Check primary outputs and inputs
        Assertions.assertFalse(meta.getPrimaryOutputs().isEmpty());
        Assertions.assertFalse(meta.getPrimaryInputs().isEmpty());

        // Check graph topology roundtrip
        Assertions.assertEquals(2, pkg.getGraph().getNodes().size());
        Assertions.assertEquals(1, pkg.getGraph().getConnections().size());
    }

    @Test
    public void testBlueprintPrefixWithColonsAndFallbacks() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node = RecipeNode.create("Pyrolyse Oven", 100.0, 60.0, GTVoltageTier.MV);
        graph.addNode(node);

        String code = BlueprintCodec.exportToString(graph, "Pyrolyse: Stage 1", "Charcoal & Wood Gas", "KrakelLP", 10.0, 20.0, 1.0);
        Assertions.assertTrue(code.startsWith("GTBOARD:Pyrolyse- Stage 1:"));

        BlueprintPackage pkg = BlueprintCodec.importPackageFromString(code);
        Assertions.assertNotNull(pkg);
        Assertions.assertEquals("Pyrolyse: Stage 1", pkg.getMetadata().getTitle());
        Assertions.assertEquals("KrakelLP", pkg.getMetadata().getAuthor());
        Assertions.assertEquals(1, pkg.getGraph().getNodes().size());
    }

    @Test
    public void testLegacyV1BlueprintCompatibility() {
        // Build a raw FlowGraph NBT tag as in V1 format (without version or meta wrapper)
        FlowGraph graph = new FlowGraph();
        RecipeNode node = RecipeNode.create("Macerator", 100.0, 30.0, GTVoltageTier.LV);
        node.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:raw_iron"), "Raw Iron", 1.0));
        node.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2.0));
        graph.addNode(node);

        CompoundTag legacyTag = graph.serializeNBT(80.0, 60.0, 1.2);
        byte[] compressed = BlueprintCodec.compressTag(legacyTag);
        String legacyCode = "GTBOARD:" + java.util.Base64.getEncoder().encodeToString(compressed);

        // Import as package
        BlueprintPackage pkg = BlueprintCodec.importPackageFromString(legacyCode);
        Assertions.assertNotNull(pkg);
        Assertions.assertEquals(80.0, pkg.getPanX(), 0.001);
        Assertions.assertEquals(60.0, pkg.getPanY(), 0.001);
        Assertions.assertEquals(1.2, pkg.getZoom(), 0.001);
        Assertions.assertEquals(1, pkg.getGraph().getNodes().size());

        // Default metadata fallback
        Assertions.assertNotNull(pkg.getMetadata());
        Assertions.assertEquals("Imported Factory", pkg.getMetadata().getTitle());
        Assertions.assertEquals(1, pkg.getMetadata().getNodeCount());
        Assertions.assertEquals(1, pkg.getMetadata().getMachineCount());
    }
}
