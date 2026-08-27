package com.gtceu.calcboard;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.GlobalBalanceAggregator;
import com.gtceu.calcboard.api.solver.GlobalBalanceSummary;
import com.gtceu.calcboard.api.storage.BlueprintCodec;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.NodeClipboard;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.PowerDisplayMode;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

/**
 * Unit tests for Graph NBT Serialization, Blueprints, Modules, Multi-Page Presets, and Global Balance Aggregation.
 */
public class GraphSerializationTest {

    @Test
    public void testSaveAndLoadGraphNBT() {
        FlowGraph graph = new FlowGraph();
        RecipeNode node1 = RecipeNode.create("Machine A", 100.0, 32.0, GTVoltageTier.LV);
        node1.setMachineCount(2.5);
        node1.setTargetTier(GTVoltageTier.HV);
        node1.setOverclockMode(OverclockMode.PERFECT);
        node1.setParallel(4);
        node1.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 2, 1.0));
        node1.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:molten_iron"), "Molten Iron", 288, 1.0));
        graph.addNode(node1);

        RecipeNode node2 = RecipeNode.create("Machine B", 80.0, 128.0, GTVoltageTier.MV);
        node2.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:molten_iron"), "Molten Iron", 144, 1.0));
        graph.addNode(node2);

        graph.addConnection(node1.getId(), 0, node2.getId(), 0);

        // Serialize
        CompoundTag tag = graph.serializeNBT(120.0, 80.0, 1.5);

        // Deserialize
        FlowGraph loaded = FlowGraph.deserializeNBT(tag);

        Assertions.assertEquals(2, loaded.getNodes().size());
        Assertions.assertEquals(1, loaded.getConnections().size());

        RecipeNode loadedNode1 = loaded.findNodeById(node1.getId());
        Assertions.assertNotNull(loadedNode1);
        Assertions.assertEquals(2.5, loadedNode1.getMachineCount(), 0.001);
        Assertions.assertEquals(GTVoltageTier.HV, loadedNode1.getTargetTier());
        Assertions.assertEquals(OverclockMode.PERFECT, loadedNode1.getOverclockMode());
        Assertions.assertEquals(4, loadedNode1.getParallel());
        Assertions.assertEquals(1, loadedNode1.getInputs().size());
        Assertions.assertEquals(1, loadedNode1.getOutputs().size());

        Assertions.assertEquals(120.0, tag.getDouble("panX"), 0.001);
        Assertions.assertEquals(80.0, tag.getDouble("panY"), 0.001);
        Assertions.assertEquals(1.5, tag.getDouble("zoom"), 0.001);
    }

    @Test
    public void testBlueprintExportAndImportRoundTrip() {
        FlowGraph graph = new FlowGraph();

        RecipeNode macerator = RecipeNode.create("Macerator", 100.0, 32.0, GTVoltageTier.LV);
        macerator.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1, 1.0));
        macerator.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2, 1.0));
        macerator.setMachineCount(3.5);
        macerator.setBaseNode(true);
        graph.addNode(macerator);

        RecipeNode furnace = RecipeNode.create("Electric Furnace", 60.0, 32.0, GTVoltageTier.LV);
        furnace.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 1, 1.0));
        furnace.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 1, 1.0));
        graph.addNode(furnace);

        graph.addConnection(macerator.getId(), 0, furnace.getId(), 0);

        String code = BlueprintCodec.exportToString(graph, 120.0, 80.0, 1.25);
        Assertions.assertNotNull(code);
        Assertions.assertTrue(code.startsWith("GTBOARD:"));
        Assertions.assertTrue(code.length() > 20);

        double[] viewport = new double[3];
        FlowGraph imported = BlueprintCodec.importFromString(code, viewport);

        Assertions.assertNotNull(imported);
        Assertions.assertEquals(2, imported.getNodes().size());
        Assertions.assertEquals(1, imported.getConnections().size());

        Assertions.assertEquals(120.0, viewport[0], 0.001);
        Assertions.assertEquals(80.0, viewport[1], 0.001);
        Assertions.assertEquals(1.25, viewport[2], 0.001);

        RecipeNode loadedMacerator = imported.getNodes().get(0);
        Assertions.assertEquals("Macerator", loadedMacerator.getName());
        Assertions.assertEquals(3.5, loadedMacerator.getMachineCount(), 0.001);
        Assertions.assertTrue(loadedMacerator.isBaseNode());
    }

    @Test
    public void testPetrochemNitrobenzeneBlueprint() {
        String blueprint = "GTBOARD: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";

        double[] viewport = new double[3];
        FlowGraph graph = BlueprintCodec.importFromString(blueprint, viewport);
        Assertions.assertNotNull(graph);

        RecipeNode nitrobenzene = null;
        for (RecipeNode n : graph.getNodes()) {
            if (n.getName().toLowerCase().contains("nitrobenzene")) {
                nitrobenzene = n;
                break;
            }
        }
        Assertions.assertNotNull(nitrobenzene);
        nitrobenzene.setMachineCount(1.0);
        nitrobenzene.setBaseNode(true);

        graph.autoRatioFromAnchor(nitrobenzene);

        for (RecipeNode n : graph.getNodes()) {
            Assertions.assertTrue(n.getMachineCount() < 50.0, "Node " + n.getName() + " exploded to " + n.getMachineCount());
            Assertions.assertEquals(Math.floor(n.getMachineCount()), n.getMachineCount(), 0.001, "Node " + n.getName() + " is not integer: " + n.getMachineCount());
        }
    }

    @Test
    public void testGroupIntoModuleAndExpand() {
        FlowGraph graph = new FlowGraph();

        RecipeNode plantA = RecipeNode.create("Plant A", 100.0, 30.0, GTVoltageTier.LV);
        plantA.setMachineCount(2.0);
        plantA.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfur_trioxide"), "Sulfur Trioxide", 1000, 1.0));
        plantA.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:diluted_sulfuric_acid"), "Diluted Sulfuric Acid", 1000, 1.0));
        plantA.setPos(100, 100);
        graph.addNode(plantA);

        RecipeNode plantB = RecipeNode.create("Plant B", 100.0, 30.0, GTVoltageTier.LV);
        plantB.setMachineCount(2.0);
        plantB.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:diluted_sulfuric_acid"), "Diluted Sulfuric Acid", 1000, 1.0));
        plantB.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:sulfuric_acid"), "Sulfuric Acid", 1000, 1.0));
        plantB.setPos(300, 100);
        graph.addNode(plantB);

        graph.addConnection(plantA.getId(), 0, plantB.getId(), 0);

        RecipeNode moduleNode = graph.groupIntoModule("Sulfuric Acid Line");
        Assertions.assertNotNull(moduleNode);
        Assertions.assertTrue(moduleNode.isModule());
        Assertions.assertEquals(1, graph.getNodes().size());
        Assertions.assertEquals(4, moduleNode.getContainedMachineCount());

        Assertions.assertEquals(1, moduleNode.getInputs().size());
        Assertions.assertEquals("Sulfur Trioxide", moduleNode.getInputs().get(0).getDisplayName());

        Assertions.assertEquals(1, moduleNode.getOutputs().size());
        Assertions.assertEquals("Sulfuric Acid", moduleNode.getOutputs().get(0).getDisplayName());

        boolean expanded = graph.expandModule(moduleNode);
        Assertions.assertTrue(expanded);
        Assertions.assertEquals(2, graph.getNodes().size());
        Assertions.assertEquals(1, graph.getConnections().size());
    }

    @Test
    public void testNodeClipboardCopyAndPaste() {
        FlowGraph graph = new FlowGraph();
        RecipeNode nodeA = RecipeNode.create("Node A", 100.0, 30.0, GTVoltageTier.LV);
        nodeA.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000, 1.0));
        graph.addNode(nodeA);

        RecipeNode nodeB = RecipeNode.create("Node B", 100.0, 30.0, GTVoltageTier.LV);
        nodeB.addInput(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000, 1.0));
        graph.addNode(nodeB);

        graph.addConnection(nodeA.getId(), 0, nodeB.getId(), 0);

        Set<String> sel = Set.of(nodeA.getId(), nodeB.getId());
        NodeClipboard.getInstance().copy(graph, sel);
        Assertions.assertTrue(NodeClipboard.getInstance().hasContent());

        List<RecipeNode> pasted = NodeClipboard.getInstance().paste(graph, 200.0, 200.0).nodes();
        Assertions.assertEquals(2, pasted.size());
        Assertions.assertEquals(4, graph.getNodes().size());
        Assertions.assertEquals(2, graph.getConnections().size());

        RecipeNode pastedA = pasted.get(0);
        RecipeNode pastedB = pasted.get(1);
        Assertions.assertNotEquals(nodeA.getId(), pastedA.getId());
        Assertions.assertNotEquals(nodeB.getId(), pastedB.getId());

        boolean hasPastedEdge = false;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(pastedA.getId()) && edge.toNodeId().equals(pastedB.getId())) {
                hasPastedEdge = true;
                break;
            }
        }
        Assertions.assertTrue(hasPastedEdge);
    }

    @Test
    public void testSelectiveGroupIntoModuleAndStats() {
        FlowGraph graph = new FlowGraph();

        RecipeNode n1 = RecipeNode.create("Machine 1", 100.0, 30.0, GTVoltageTier.LV);
        n1.setMachineCount(2.0);
        graph.addNode(n1);

        RecipeNode n2 = RecipeNode.create("Machine 2", 100.0, 30.0, GTVoltageTier.LV);
        n2.setMachineCount(3.0);
        graph.addNode(n2);

        RecipeNode n3 = RecipeNode.create("Machine 3", 100.0, 30.0, GTVoltageTier.LV);
        n3.setMachineCount(5.0);
        graph.addNode(n3);

        Set<String> sel = Set.of(n1.getId(), n2.getId());
        RecipeNode module = graph.groupIntoModule(sel, "Sub Module");
        Assertions.assertNotNull(module);
        Assertions.assertEquals(2, graph.getNodes().size());
        Assertions.assertEquals(5, module.getContainedMachineCount());

        BalanceSummary summary = graph.computeSummary();
        Assertions.assertEquals(10, summary.totalMachineCount());
        Assertions.assertEquals(2, summary.machineBreakdown().get("Machine 1"));
        Assertions.assertEquals(3, summary.machineBreakdown().get("Machine 2"));
        Assertions.assertEquals(5, summary.machineBreakdown().get("Machine 3"));
    }

    @Test
    public void testMultiPagePresetSerialization() {
        BoardPage page1 = BoardPage.createDefault("Sulfuric Acid Line");
        RecipeNode node1 = RecipeNode.create("Distillation", 100.0, 120.0, GTVoltageTier.MV);
        page1.getGraph().addNode(node1);

        BoardPage page2 = BoardPage.createDefault("Polyethylene Line");
        RecipeNode node2 = RecipeNode.create("Polymerization", 200.0, 480.0, GTVoltageTier.HV);
        page2.getGraph().addNode(node2);

        CompoundTag tag1 = page1.serializeNBT();
        BoardPage loaded1 = BoardPage.deserializeNBT(tag1);
        Assertions.assertEquals("Sulfuric Acid Line", loaded1.getName());
        Assertions.assertEquals(1, loaded1.getGraph().getNodes().size());
        Assertions.assertEquals("Distillation", loaded1.getGraph().getNodes().get(0).getName());

        CompoundTag tag2 = page2.serializeNBT();
        BoardPage loaded2 = BoardPage.deserializeNBT(tag2);
        Assertions.assertEquals("Polyethylene Line", loaded2.getName());
        Assertions.assertEquals(1, loaded2.getGraph().getNodes().size());
        Assertions.assertEquals("Polymerization", loaded2.getGraph().getNodes().get(0).getName());
    }

    @Test
    public void testGlobalBalanceMultiPageMaterialAndPowerAggregation() {
        BoardPage page1 = BoardPage.createDefault("Oil Refinery");
        RecipeNode cracker = RecipeNode.create("Steam Cracker", 20.0, 2000.0, GTVoltageTier.HV);
        cracker.setMachineCount(1.0);
        IngredientStack ethylene = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:ethylene"), "Ethylene", 500.0, 1.0);
        cracker.addOutput(ethylene);
        page1.getGraph().addNode(cracker);

        BoardPage page2 = BoardPage.createDefault("Plastics Plant");
        RecipeNode poly = RecipeNode.create("Polymerizer", 20.0, 5000.0, GTVoltageTier.EV);
        poly.setMachineCount(1.0);
        poly.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:ethylene"), "Ethylene", 300.0, 1.0));
        IngredientStack polyethylene = IngredientStack.item(ResourceLocation.tryParse("gtceu:polyethylene_plate"), "Polyethylene Plate", 100.0, 1.0);
        poly.addOutput(polyethylene);
        page2.getGraph().addNode(poly);

        BoardPage page3 = BoardPage.createDefault("Turbine Power Station");
        RecipeNode turbine = RecipeNode.create("Gas Turbine", 20.0, 30000.0, GTVoltageTier.EV);
        turbine.setGenerator(true);
        turbine.setMachineCount(1.0);
        page3.getGraph().addNode(turbine);

        List<BoardPage> selectedPages = List.of(page1, page2, page3);
        GlobalBalanceSummary summary = GlobalBalanceAggregator.compute(selectedPages);

        Assertions.assertEquals(3, summary.totalMachineCount());
        Assertions.assertEquals(GTVoltageTier.EV, summary.highestVoltageTier());

        Assertions.assertEquals(30000.0, summary.totalGeneratedEUt(), 0.001);
        Assertions.assertEquals(7000.0, summary.totalConsumedEUt(), 0.001);
        Assertions.assertEquals(-23000.0, summary.netEUt(), 0.001);
        Assertions.assertEquals(23000.0, summary.getNetPower(), 0.001);
        Assertions.assertTrue(summary.isPowerSurplus());
        Assertions.assertFalse(summary.isPowerDeficit());

        Double netEthylene = summary.netOutputs().get(ethylene);
        Assertions.assertNotNull(netEthylene);
        Assertions.assertEquals(200.0, netEthylene, 0.001);
        Assertions.assertFalse(summary.rawInputs().containsKey(ethylene));

        Double netPoly = summary.netOutputs().get(polyethylene);
        Assertions.assertNotNull(netPoly);
        Assertions.assertEquals(100.0, netPoly, 0.001);

        List<GlobalBalanceSummary.PageContribution> ethyleneContribs = summary.getContributionsFor(ethylene);
        Assertions.assertEquals(2, ethyleneContribs.size());
    }

    @Test
    public void testGlobalBalanceSelectiveExclusion() {
        BoardPage page1 = BoardPage.createDefault("Page 1");
        RecipeNode n1 = RecipeNode.create("Producer", 20.0, 1000.0, GTVoltageTier.LV);
        n1.addOutput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 100.0, 1.0));
        page1.getGraph().addNode(n1);

        BoardPage page2 = BoardPage.createDefault("Page 2");
        RecipeNode n2 = RecipeNode.create("Consumer", 20.0, 2000.0, GTVoltageTier.LV);
        n2.addInput(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 250.0, 1.0));
        page2.getGraph().addNode(n2);

        IngredientStack oxygen = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 100.0, 1.0);

        GlobalBalanceSummary fullSummary = GlobalBalanceAggregator.compute(List.of(page1, page2));
        Assertions.assertTrue(fullSummary.rawInputs().containsKey(oxygen));
        Assertions.assertEquals(150.0, fullSummary.rawInputs().get(oxygen), 0.001);
        Assertions.assertEquals(3000.0, fullSummary.totalConsumedEUt(), 0.001);

        GlobalBalanceSummary p1Summary = GlobalBalanceAggregator.compute(List.of(page1));
        Assertions.assertTrue(p1Summary.netOutputs().containsKey(oxygen));
        Assertions.assertEquals(100.0, p1Summary.netOutputs().get(oxygen), 0.001);
        Assertions.assertEquals(1000.0, p1Summary.totalConsumedEUt(), 0.001);

        GlobalBalanceSummary p2Summary = GlobalBalanceAggregator.compute(List.of(page2));
        Assertions.assertTrue(p2Summary.rawInputs().containsKey(oxygen));
        Assertions.assertEquals(250.0, p2Summary.rawInputs().get(oxygen), 0.001);
        Assertions.assertEquals(2000.0, p2Summary.totalConsumedEUt(), 0.001);
    }

    @Test
    public void testBoardManagerUiStatePersistence() throws Exception {
        java.io.File tempFile = java.io.File.createTempFile("gtcalcboard_test_uistate", ".dat");
        tempFile.deleteOnExit();

        BoardManager manager = BoardManager.getInstance();
        manager.resetToDefault();

        // Default state
        Assertions.assertFalse(manager.isSummaryOverlayCollapsed());
        Assertions.assertTrue(manager.isHotkeyHudExpanded());
        Assertions.assertTrue(manager.isFavoritesDockExpanded());

        // Modify state
        manager.setSummaryOverlayCollapsed(true);
        manager.setHotkeyHudExpanded(false);
        manager.setFavoritesDockExpanded(false);
        manager.setPowerDisplayMode(PowerDisplayMode.AMPS);

        boolean saved = manager.saveToFile(tempFile);
        Assertions.assertTrue(saved);

        // Reset and reload
        manager.resetToDefault();
        Assertions.assertFalse(manager.isSummaryOverlayCollapsed());
        Assertions.assertTrue(manager.isHotkeyHudExpanded());
        Assertions.assertTrue(manager.isFavoritesDockExpanded());

        boolean loaded = manager.loadFromFile(tempFile);
        Assertions.assertTrue(loaded);
        Assertions.assertTrue(manager.isSummaryOverlayCollapsed());
        Assertions.assertFalse(manager.isHotkeyHudExpanded());
        Assertions.assertFalse(manager.isFavoritesDockExpanded());
        Assertions.assertEquals(PowerDisplayMode.AMPS, manager.getPowerDisplayMode());
    }

    @Test
    public void testSerializationCompactPayloadAndFidelity() {
        FlowGraph graph = new FlowGraph();

        RecipeNode macerator = RecipeNode.create("Macerator", 100.0, 30.0, GTVoltageTier.LV);
        macerator.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:macerator"));
        macerator.addInput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ore"), "Iron Ore", 1.0));
        macerator.addOutput(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2.0));
        graph.addNode(macerator);

        RecipeNode furnace = RecipeNode.create("Furnace", 200.0, 120.0, GTVoltageTier.MV);
        furnace.setRecipeCategoryId(ResourceLocation.tryParse("gtceu:electric_blast_furnace"));
        furnace.setMultiblock(true);
        furnace.setParallel(8);
        furnace.addInput(IngredientStack.item(ResourceLocation.tryParse("gtceu:crushed_iron_ore"), "Crushed Iron Ore", 2.0));
        furnace.addOutput(IngredientStack.item(ResourceLocation.tryParse("minecraft:iron_ingot"), "Iron Ingot", 2.0));
        graph.addNode(furnace);

        graph.addConnection(macerator.getId(), 0, furnace.getId(), 0);

        // Serialize to NBT and export to Blueprint string
        CompoundTag tag = graph.serializeNBT(100.0, 200.0, 1.25);
        String blueprint = BlueprintCodec.exportToString(graph, 100.0, 200.0, 1.25);

        // Ensure compact size
        Assertions.assertNotNull(blueprint);
        Assertions.assertTrue(blueprint.startsWith("GTBOARD:"));
        Assertions.assertTrue(blueprint.length() < 1200, "Blueprint string should be ultra-compact (< 1200 chars), was: " + blueprint.length());

        // Deserialize and check 100% roundtrip fidelity
        double[] viewport = new double[3];
        FlowGraph imported = BlueprintCodec.importFromString(blueprint, viewport);
        Assertions.assertNotNull(imported);
        Assertions.assertEquals(100.0, viewport[0], 0.001);
        Assertions.assertEquals(200.0, viewport[1], 0.001);
        Assertions.assertEquals(1.25, viewport[2], 0.001);

        Assertions.assertEquals(2, imported.getNodes().size());
        Assertions.assertEquals(1, imported.getConnections().size());

        RecipeNode importedMacerator = imported.getNodes().get(0);
        Assertions.assertEquals("Macerator", importedMacerator.getName());
        Assertions.assertEquals(GTVoltageTier.LV, importedMacerator.getRecipeTier());
        Assertions.assertEquals(GTVoltageTier.LV, importedMacerator.getTargetTier());
        Assertions.assertEquals(1.0, importedMacerator.getMachineCount(), 0.001);
        Assertions.assertEquals(1, importedMacerator.getParallel());
        Assertions.assertFalse(importedMacerator.isMultiblock());

        RecipeNode importedFurnace = imported.getNodes().get(1);
        Assertions.assertEquals("Furnace", importedFurnace.getName());
        Assertions.assertEquals(GTVoltageTier.MV, importedFurnace.getRecipeTier());
        Assertions.assertEquals(8, importedFurnace.getParallel());
        Assertions.assertTrue(importedFurnace.isMultiblock());
    }
}


