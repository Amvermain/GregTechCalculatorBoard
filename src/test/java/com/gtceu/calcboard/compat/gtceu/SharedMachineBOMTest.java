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
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class SharedMachineBOMTest {

    @BeforeAll
    public static void setupStructures() {
        ResourceLocation ebfId = ResourceLocation.tryParse("gtceu:electric_blast_furnace");
        List<MultiblockStructurePart> parts = List.of(
            new MultiblockStructurePart(ebfId, "Electric Blast Furnace", 1, PartCategory.CONTROLLER),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:heatproof_machine_casing"), "Heat Proof Casing", 10, PartCategory.CASING),
            new MultiblockStructurePart(ResourceLocation.tryParse("gtceu:cupronickel_coil_block"), "Cupronickel Coil", 16, PartCategory.COIL)
        );
        MultiblockStructureDef def = new MultiblockStructureDef(ebfId, "Electric Blast Furnace", parts, 16, 1, 1, 1, 1, 1, 1);
        MultiblockStructureCatalog.registerManualStructure(def);
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

        // 1. Without Shared Machine Frame -> 3 separate multiblocks (3 controllers, 48 coils, 30 casings)
        MultiblockBOMSummary separateSummary = MultiblockBOMCalculator.calculateBOM(graph.getNodes(), false);
        Assertions.assertEquals(3, separateSummary.totalMultiblockCount());

        // 2. With Shared Machine Frame (45% load) -> 1 multiblock (1 controller, 16 coils, 10 casings)
        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Shared EBF Pool", List.of(node1, node2, node3), CanvasGroupFrame.COLOR_EMERALD);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        MultiblockBOMSummary sharedSummary = MultiblockBOMCalculator.calculateBOM(graph, false);
        Assertions.assertEquals(1, sharedSummary.totalMultiblockCount());
        Assertions.assertEquals(1, sharedSummary.machineContributions().size());

        for (MultiblockBOMSummary.BOMItemEntry item : sharedSummary.aggregatedItems()) {
            if (item.itemId().equals(ebfId)) {
                Assertions.assertEquals(1, item.totalAmount(), "Controller count must be 1!");
            } else if (item.itemId().getPath().contains("coil")) {
                Assertions.assertEquals(16, item.totalAmount(), "Coil count must be 16!");
            } else if (item.itemId().getPath().contains("casing")) {
                Assertions.assertEquals(10, item.totalAmount(), "Casing count must be 10!");
            }
        }

        // 3. Overloaded Shared Machine Frame (145% load) -> 2 multiblocks (2 controllers, 32 coils, 20 casings)
        node1.setMachineCount(0.60);
        node2.setMachineCount(0.50);
        node3.setMachineCount(0.35);

        MultiblockBOMSummary overloadedSummary = MultiblockBOMCalculator.calculateBOM(graph, false);
        Assertions.assertEquals(2, overloadedSummary.totalMultiblockCount());

        for (MultiblockBOMSummary.BOMItemEntry item : overloadedSummary.aggregatedItems()) {
            if (item.itemId().equals(ebfId)) {
                Assertions.assertEquals(2, item.totalAmount(), "Overloaded controller count must be 2!");
            } else if (item.itemId().getPath().contains("coil")) {
                Assertions.assertEquals(32, item.totalAmount(), "Overloaded coil count must be 32!");
            } else if (item.itemId().getPath().contains("casing")) {
                Assertions.assertEquals(20, item.totalAmount(), "Overloaded casing count must be 20!");
            }
        }
    }
}
