package com.gtceu.calcboard.compat.gtceu;

import com.gtceu.calcboard.api.bom.MultiblockBOMCalculator;
import com.gtceu.calcboard.api.bom.MultiblockBOMSummary;
import com.gtceu.calcboard.api.bom.MultiblockStructureCatalog;
import com.gtceu.calcboard.api.bom.MultiblockStructureDef;
import com.gtceu.calcboard.api.bom.MultiblockStructurePart;
import com.gtceu.calcboard.api.bom.PartCategory;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class SharedMachineBOMTest {

    @BeforeEach
    public void setupStructures() {
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        MultiblockStructureCatalog.clearStructure(ebfId);
        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(ebfId, "Electric Blast Furnace", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:heatproof_machine_casing"), "Heat Proof Casing", 10, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:cupronickel_coil_block"), "Cupronickel Coil", 16, PartCategory.COIL)
        );
        MultiblockStructureDef def = new MultiblockStructureDef(ebfId, "Electric Blast Furnace", parts, 16, 1, 1, 1, 1, 1, 1);
        MultiblockStructureCatalog.registerManualStructure(def);
        ResourceLocation lcrId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        MultiblockStructureCatalog.clearStructure(lcrId);
        List<MultiblockStructurePart> lcrParts = List.of(
            new MultiblockStructurePart(lcrId, "Large Chemical Reactor", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:ptfe_pipe_casing"), "block.gtceu.ptfe_pipe_casing", 20, PartCategory.CASING)
        );
        MultiblockStructureDef lcrDef = new MultiblockStructureDef(lcrId, "Large Chemical Reactor", lcrParts, 16, 1, 1, 1, 1, 1, 1);
        MultiblockStructureCatalog.registerManualStructure(lcrDef);
    }

    @Test
    public void testSharedMachineBOMDeduplication() {
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");

        RecipeNode node1 = RecipeNode.create(ebfId, "EBF Steel Recipe", 100.0, 120.0, GTVoltageTier.MV);
        node1.setMachineCount(0.15);
        node1.setMultiblock(true);

        RecipeNode node2 = RecipeNode.create(ebfId, "EBF Aluminium Recipe", 100.0, 120.0, GTVoltageTier.MV);
        node2.setMachineCount(0.20);
        node2.setMultiblock(true);

        RecipeNode node3 = RecipeNode.create(ebfId, "EBF Titanium Recipe", 100.0, 120.0, GTVoltageTier.MV);
        node3.setMachineCount(0.10);
        node3.setMultiblock(true);

        FlowGraph graph = new FlowGraph();
        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        MultiblockBOMSummary separateSummary = MultiblockBOMCalculator.calculateBOM(graph.getNodes(), false);
        Assertions.assertEquals(3, separateSummary.totalMultiblockCount());

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Shared EBF Pool", List.of(node1, node2, node3), CanvasGroupFrame.COLOR_EMERALD);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        MultiblockBOMSummary sharedSummary = MultiblockBOMCalculator.calculateBOM(graph, false);
        Assertions.assertEquals(1, sharedSummary.totalMultiblockCount());
        Assertions.assertEquals(1, sharedSummary.machineContributions().size());

        for (MultiblockBOMSummary.BOMItemEntry item : sharedSummary.aggregatedItems()) {
            if (item.itemId().equals(ebfId)) {
                Assertions.assertEquals(1, item.totalAmount());
            } else if (item.itemId().getPath().contains("coil")) {
                Assertions.assertEquals(16, item.totalAmount());
            } else if (item.itemId().getPath().contains("casing")) {
                Assertions.assertEquals(8, item.totalAmount());
            }
        }

        node1.setMachineCount(0.60);
        node2.setMachineCount(0.50);
        node3.setMachineCount(0.35);

        MultiblockBOMSummary overloadedSummary = MultiblockBOMCalculator.calculateBOM(graph, false);
        Assertions.assertEquals(2, overloadedSummary.totalMultiblockCount());

        for (MultiblockBOMSummary.BOMItemEntry item : overloadedSummary.aggregatedItems()) {
            if (item.itemId().equals(ebfId)) {
                Assertions.assertEquals(2, item.totalAmount());
            } else if (item.itemId().getPath().contains("coil")) {
                Assertions.assertEquals(32, item.totalAmount());
            } else if (item.itemId().getPath().contains("casing")) {
                Assertions.assertEquals(16, item.totalAmount());
            }
        }
    }

    @Test
    public void testSharedMachineSpatialContainment() {
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        RecipeNode node1 = RecipeNode.create(ebfId, "EBF Spatial 1", 100.0, 120.0, GTVoltageTier.MV);
        node1.setPosX(100);
        node1.setPosY(100);
        node1.setMachineCount(0.3);
        node1.setMultiblock(true);

        RecipeNode node2 = RecipeNode.create(ebfId, "EBF Spatial 2", 100.0, 120.0, GTVoltageTier.MV);
        node2.setPosX(200);
        node2.setPosY(150);
        node2.setMachineCount(0.4);
        node2.setMultiblock(true);

        FlowGraph graph = new FlowGraph();
        graph.addNode(node1);
        graph.addNode(node2);

        CanvasGroupFrame frame = new CanvasGroupFrame(java.util.UUID.randomUUID().toString(), "Spatial Frame", CanvasGroupFrame.COLOR_EMERALD, 50, 50, 300, 200);
        frame.setSharedMachineFrame(true);
        Assertions.assertTrue(frame.getContainedNodeIds().isEmpty());
        graph.addFrame(frame);

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(graph, false);
        Assertions.assertEquals(1, summary.totalMultiblockCount());
    }

    @Test
    public void testHeterogeneousMachinesInSharedFrame() {
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation lcrId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");

        RecipeNode ebf1 = RecipeNode.create(ebfId, "EBF 1", 100.0, 120.0, GTVoltageTier.MV);
        ebf1.setMachineCount(0.3);
        ebf1.setMultiblock(true);
        RecipeNode ebf2 = RecipeNode.create(ebfId, "EBF 2", 100.0, 120.0, GTVoltageTier.MV);
        ebf2.setMachineCount(0.3);
        ebf2.setMultiblock(true);

        RecipeNode lcr1 = RecipeNode.create(lcrId, "LCR 1", 100.0, 120.0, GTVoltageTier.MV);
        lcr1.setMachineCount(0.4);
        lcr1.setMultiblock(true);
        RecipeNode lcr2 = RecipeNode.create(lcrId, "LCR 2", 100.0, 120.0, GTVoltageTier.MV);
        lcr2.setMachineCount(0.4);
        lcr2.setMultiblock(true);

        FlowGraph graph = new FlowGraph();
        graph.addNode(ebf1);
        graph.addNode(ebf2);
        graph.addNode(lcr1);
        graph.addNode(lcr2);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Mixed Shared Pool", List.of(ebf1, ebf2, lcr1, lcr2), CanvasGroupFrame.COLOR_EMERALD);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        MultiblockBOMSummary summary = MultiblockBOMCalculator.calculateBOM(graph, false);
        Assertions.assertEquals(2, summary.totalMultiblockCount());
        Assertions.assertEquals(2, summary.machineContributions().size());
    }

    @Test
    public void testBOMItemDisplayNameResolution() {
        MultiblockBOMSummary.BOMItemEntry rawEntry = new MultiblockBOMSummary.BOMItemEntry(
            ResourceLocation.tryParse("gtceu:ptfe_pipe_casing"),
            "block.gtceu.ptfe_pipe_casing",
            10,
            0,
            10,
            PartCategory.CASING,
            List.of()
        );
        String resolvedName = rawEntry.displayName();
        Assertions.assertFalse(resolvedName.startsWith("block."));
        Assertions.assertFalse(resolvedName.startsWith("item."));
        Assertions.assertEquals("Ptfe Pipe Casing", resolvedName);
    }

    @Test
    public void testMultiblockBOMSummaryMerge() {
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        RecipeNode node1 = RecipeNode.create(ebfId, "EBF 1", 100.0, 120.0, GTVoltageTier.MV);
        node1.setMachineCount(1.0);
        node1.setMultiblock(true);

        RecipeNode node2 = RecipeNode.create(ebfId, "EBF 2", 100.0, 120.0, GTVoltageTier.MV);
        node2.setMachineCount(1.0);
        node2.setMultiblock(true);

        MultiblockBOMSummary page1Summary = MultiblockBOMCalculator.calculateBOM(List.of(node1), false);
        MultiblockBOMSummary page2Summary = MultiblockBOMCalculator.calculateBOM(List.of(node2), false);

        MultiblockBOMSummary merged = MultiblockBOMSummary.merge(List.of(page1Summary, page2Summary));
        Assertions.assertEquals(2, merged.totalMultiblockCount());
        Assertions.assertEquals(2, merged.machineContributions().size());
    }

    @AfterAll
    public static void cleanupStructures() {
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        ResourceLocation lcrId = ResourceLocation.tryParse("gtceu:large_chemical_reactor");
        MultiblockStructureCatalog.clearStructure(ebfId);
        MultiblockStructureCatalog.clearStructure(lcrId);
    }
}
