package com.gtceu.calcboard;

import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.property.NodeProperties;
import com.gtceu.calcboard.compat.gtceu.physics.GTBoilerPhysics;
import com.gtceu.calcboard.compat.gtceu.physics.GTMultiblockBOMResolver;
import com.gtceu.calcboard.compat.gtceu.physics.GTTurbinePhysics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies RFC-006: Domain Model Purification, FlowGraph Encapsulation, and SPI Physics Submodules.
 */
public class DomainModelEncapsulationTest {

    @Test
    @DisplayName("RFC-006: FlowGraph collection getters return unmodifiable views")
    void testFlowGraphUnmodifiableCollections() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node = RecipeNode.create(ResourceLocation.tryParse("gtceu:macerator"), "Macerator", 100, 30, null);

        graph.addNode(node);

        // Attempting to modify getNodes() directly should throw UnsupportedOperationException
        Assertions.assertThrows(UnsupportedOperationException.class, () -> {
            graph.getNodes().add(RecipeNode.create(ResourceLocation.tryParse("gtceu:furnace"), "Furnace", 100, 30, null));
        }, "graph.getNodes() must be unmodifiable to protect nodeMap index integrity");
    }

    @Test
    @DisplayName("RFC-006: FlowGraph addNode and removeNode keep O(1) nodeMap in sync")
    void testFlowGraphIndexSynchronization() {
        FlowGraph graph = new FlowGraph();
        RecipeNode nodeA = RecipeNode.create(ResourceLocation.tryParse("gtceu:compressor"), "Compressor", 100, 30, null);
        RecipeNode nodeB = RecipeNode.create(ResourceLocation.tryParse("gtceu:centrifuge"), "Centrifuge", 100, 30, null);

        graph.addNode(nodeA);
        graph.addNode(nodeB);
        graph.addConnection(nodeA.getId(), 0, nodeB.getId(), 0);

        Assertions.assertEquals(nodeA, graph.findNodeById(nodeA.getId()));
        Assertions.assertEquals(nodeB, graph.findNodeById(nodeB.getId()));
        Assertions.assertEquals(1, graph.getConnections().size());

        // Remove nodeA
        graph.removeNode(nodeA.getId());

        Assertions.assertNull(graph.findNodeById(nodeA.getId()), "Removed node should not be in nodeMap");
        Assertions.assertEquals(1, graph.getNodes().size());
        Assertions.assertEquals(0, graph.getConnections().size(), "Connections tied to deleted node must be purged");
    }

    @Test
    @DisplayName("RFC-006: Legacy NBT properties migrate smoothly into NodePropertyStore")
    void testLegacyNBTMigration() {
        CompoundTag legacyTag = new CompoundTag();
        legacyTag.putString("id", "legacy_node_1");
        legacyTag.putString("name", "Legacy Turbine");
        legacyTag.putInt("rpm", 128);
        legacyTag.putInt("rotorEfficiency", 120);
        legacyTag.putInt("rotorPower", 150);

        RecipeNode node = RecipeNode.deserializeNBT(legacyTag);

        Assertions.assertEquals(128, node.getRpm());
        Assertions.assertEquals(128, node.getProperties().get(NodeProperties.KINETIC_RPM));
        Assertions.assertEquals(120, node.getProperties().get(NodeProperties.TURBINE_ROTOR_EFFICIENCY));
        Assertions.assertEquals(150, node.getProperties().get(NodeProperties.TURBINE_ROTOR_POWER));
    }

    @Test
    @DisplayName("RFC-006: GTPhysics submodules execute calculations accurately")
    void testPhysicsSubmodules() {
        // Boiler Physics
        RecipeNode boiler = RecipeNode.create(ResourceLocation.tryParse("gtceu:bronze_boiler"), "Bronze Boiler", 20, 30, null);
        boiler.getProperties().set(NodeProperties.BOILER_THROTTLE, 80);

        Assertions.assertTrue(GTBoilerPhysics.isBoilerRecipe(boiler));
        double speed = GTBoilerPhysics.getBoilerSpeedMultiplier(boiler);
        Assertions.assertTrue(speed > 0.0);

        // Multiblock BOM Classifier
        PartCategory coilCategory = GTMultiblockBOMResolver.classifyBOMPart(ResourceLocation.tryParse("gtceu:cupronickel_coil_block"));
        Assertions.assertEquals(PartCategory.COIL, coilCategory);

        PartCategory casingCategory = GTMultiblockBOMResolver.classifyBOMPart(ResourceLocation.tryParse("gtceu:solid_machine_casing"));
        Assertions.assertEquals(PartCategory.CASING, casingCategory);
    }

    @Test
    @DisplayName("RFC-006: FlowGraph bringNodeToFront reorders node to the end safely")
    void testFlowGraphBringNodeToFront() {
        FlowGraph graph = new FlowGraph();
        RecipeNode nodeA = RecipeNode.create(ResourceLocation.tryParse("gtceu:compressor"), "Compressor", 100, 30, null);
        RecipeNode nodeB = RecipeNode.create(ResourceLocation.tryParse("gtceu:centrifuge"), "Centrifuge", 100, 30, null);

        graph.addNode(nodeA);
        graph.addNode(nodeB);

        Assertions.assertEquals(nodeA, graph.getNodes().get(0));
        Assertions.assertEquals(nodeB, graph.getNodes().get(1));

        // Bring nodeA to front
        graph.bringNodeToFront(nodeA);

        Assertions.assertEquals(nodeB, graph.getNodes().get(0));
        Assertions.assertEquals(nodeA, graph.getNodes().get(1));
    }
}
