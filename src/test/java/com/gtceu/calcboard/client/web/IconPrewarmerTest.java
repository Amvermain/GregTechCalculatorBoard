package com.gtceu.calcboard.client.web;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class IconPrewarmerTest {

    private IconPrewarmer prewarmer;
    private IconDiskCache cache;
    private Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        prewarmer = IconPrewarmer.getInstance();
        prewarmer.clear();
        cache = IconDiskCache.getInstance();
        tempDir = Files.createTempDirectory("calcboard_test_prewarmer");
        cache.setCustomCacheDir(tempDir);
        cache.clear();
    }

    @AfterEach
    void tearDown() throws IOException {
        prewarmer.clear();
        cache.clear();
        if (tempDir != null && Files.exists(tempDir)) {
            try (var stream = Files.list(tempDir)) {
                for (Path p : stream.toList()) {
                    Files.deleteIfExists(p);
                }
            }
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    void testEnqueueNodeAndDeduplication() {
        RecipeNode node = new RecipeNode("node_1", "Test Machine", 20.0, 30.0, GTVoltageTier.LV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1.0));
        node.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));

        prewarmer.enqueue(node);
        assertEquals(3, prewarmer.getQueueSize(), "Machine icon, item input, and fluid output should be enqueued");

        // Enqueue again -> duplicates should be ignored
        prewarmer.enqueue(node);
        assertEquals(3, prewarmer.getQueueSize(), "Duplicate enqueue should be ignored");
    }

    @Test
    void testEnqueueSkipsAlreadyCachedIcons() {
        byte[] fakeData = new byte[]{(byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G', 0, 0, 0, 0};
        cache.saveRenderedItemIcon("gtceu:electric_blast_furnace", null, fakeData);
        assertTrue(cache.isItemCached("gtceu:electric_blast_furnace", null));

        RecipeNode node = new RecipeNode("node_1", "Test Machine", 20.0, 30.0, GTVoltageTier.LV);
        node.setMachineIcon(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
        node.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:copper_ingot"), "Copper Ingot", 1.0));

        prewarmer.enqueue(node);
        assertEquals(1, prewarmer.getQueueSize(), "Cached machine icon should be skipped, only copper ingot queued");
    }

    @Test
    void testEnqueueGraph() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node1 = new RecipeNode("node_1", "Macerator", 20.0, 30.0, GTVoltageTier.LV);
        node1.setMachineIcon(ResourceLocation.tryParse("gtceu:macerator"));
        RecipeNode node2 = new RecipeNode("node_2", "Wiremill", 20.0, 30.0, GTVoltageTier.LV);
        node2.setMachineIcon(ResourceLocation.tryParse("gtceu:wiremill"));
        graph.addNode(node1);
        graph.addNode(node2);

        prewarmer.enqueue(graph);
        assertEquals(2, prewarmer.getQueueSize());
    }

    @Test
    void testNullAndEmptySafety() {
        assertDoesNotThrow(() -> prewarmer.enqueue((FlowGraph) null));
        assertDoesNotThrow(() -> prewarmer.enqueue((RecipeNode) null));
        assertDoesNotThrow(() -> prewarmer.enqueueIngredient(null));
        assertDoesNotThrow(() -> prewarmer.enqueueItem(null, null));
        assertDoesNotThrow(() -> prewarmer.enqueueItem("", ""));
        assertDoesNotThrow(() -> prewarmer.enqueueFluid(null, null));
        assertDoesNotThrow(() -> prewarmer.enqueueFluid("  ", 0));
        assertEquals(0, prewarmer.getQueueSize());
    }

    @Test
    void testEnqueueWorkstationFallbackWhenMachineIconNull() {
        RecipeNode node = new RecipeNode("node_workstation", "Workstation Node", 20.0, 30.0, GTVoltageTier.LV);
        node.setMachineIcon(null);
        node.getAvailableWorkstations().add(ResourceLocation.tryParse("gtceu:chemical_bath"));

        prewarmer.enqueue(node);
        assertEquals(1, prewarmer.getQueueSize(), "Workstation should be enqueued when machineIcon is null");
    }
}
