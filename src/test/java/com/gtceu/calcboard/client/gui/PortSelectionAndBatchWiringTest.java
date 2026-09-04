package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.model.PortRef;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PortSelectionAndBatchWiringTest {

    @Test
    public void testPortSelectionModelToggleAndRange() {
        BoardSelectionModel model = new BoardSelectionModel();
        Assertions.assertTrue(model.isEmpty());

        // 1. Single Port Selection
        model.selectPort("node_1", false, 0, false);
        Assertions.assertEquals(1, model.size());
        Assertions.assertTrue(model.isPortSelected("node_1", false, 0));
        Assertions.assertFalse(model.isPortSelected("node_1", false, 1));
        Assertions.assertFalse(model.isPortSelected("node_1", true, 0));

        // 2. Ctrl-Click Toggle (Add another port)
        model.togglePort("node_1", false, 2);
        Assertions.assertEquals(2, model.size());
        Assertions.assertTrue(model.isPortSelected("node_1", false, 0));
        Assertions.assertTrue(model.isPortSelected("node_1", false, 2));

        // 3. Ctrl-Click Toggle (Remove first port)
        model.togglePort("node_1", false, 0);
        Assertions.assertEquals(1, model.size());
        Assertions.assertFalse(model.isPortSelected("node_1", false, 0));
        Assertions.assertTrue(model.isPortSelected("node_1", false, 2));

        // 4. Shift-Click Range Selection (from port 2 to port 5)
        model.selectPortRange("node_1", false, 5);
        Assertions.assertEquals(4, model.size()); // ports 2, 3, 4, 5
        Assertions.assertTrue(model.isPortSelected("node_1", false, 2));
        Assertions.assertTrue(model.isPortSelected("node_1", false, 3));
        Assertions.assertTrue(model.isPortSelected("node_1", false, 4));
        Assertions.assertTrue(model.isPortSelected("node_1", false, 5));

        // 5. Clear Ports
        model.clearPorts();
        Assertions.assertTrue(model.isEmpty());
    }

    @Test
    public void testBundleJunctionCreationAndUndo() {
        FlowGraph graph = new FlowGraph();

        RecipeNode refinery = RecipeNode.create("Distillation Tower", 100.0, 128.0, GTVoltageTier.MV);
        refinery.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:heavy_fuel"), "Heavy Fuel", 50.0));
        refinery.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:light_fuel"), "Light Fuel", 50.0));
        refinery.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:naphtha"), "Naphtha", 50.0));
        graph.addNode(refinery);

        Set<PortRef> selectedPorts = Set.of(
                PortRef.of(refinery.getId(), false, 0),
                PortRef.of(refinery.getId(), false, 1),
                PortRef.of(refinery.getId(), false, 2)
        );

        // Simulate Bundle Junctions Creation (Plan Option 1: Vertical Stack of N Junctions)
        List<RecipeNode> createdNodes = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> createdEdges = new ArrayList<>();

        List<PortRef> sortedPorts = new ArrayList<>(selectedPorts);
        sortedPorts.sort((p1, p2) -> Integer.compare(p1.portIndex(), p2.portIndex()));

        double canvasX = 300.0;
        double canvasY = 200.0;
        double startY = canvasY - ((sortedPorts.size() - 1) * 36.0 / 2.0);

        for (int i = 0; i < sortedPorts.size(); i++) {
            PortRef pref = sortedPorts.get(i);
            RecipeNode junc = RecipeNode.createReroute(canvasX - 16, startY + (i * 36.0) - 16);
            IngredientStack stack = refinery.getOutputs().get(pref.portIndex());
            junc.bindRerouteIngredient(stack);
            graph.addNode(junc);
            graph.addConnection(refinery.getId(), pref.portIndex(), junc.getId(), 0);

            createdNodes.add(junc);
            createdEdges.add(new FlowGraph.ConnectionEdge(refinery.getId(), pref.portIndex(), junc.getId(), 0));
        }

        Assertions.assertEquals(3, createdNodes.size());
        Assertions.assertEquals(4, graph.getNodes().size()); // 1 refinery + 3 junctions
        Assertions.assertEquals(3, graph.getConnections().size());

        // Verify that junctions have proper bound resource
        Assertions.assertEquals("Heavy Fuel", createdNodes.get(0).getName());
        Assertions.assertEquals("Light Fuel", createdNodes.get(1).getName());
        Assertions.assertEquals("Naphtha", createdNodes.get(2).getName());

        // Test Undo via AddNodesCommand
        BoardCommand.AddNodesCommand addCmd = new BoardCommand.AddNodesCommand(createdNodes, createdEdges, "Create 3 Junctions");
        addCmd.undo(graph);

        Assertions.assertEquals(1, graph.getNodes().size());
        Assertions.assertTrue(graph.getConnections().isEmpty());

        // Test Redo
        addCmd.redo(graph);
        Assertions.assertEquals(4, graph.getNodes().size());
        Assertions.assertEquals(3, graph.getConnections().size());
    }

    @Test
    public void testBundleFeedIntoSharedMachinePool() {
        FlowGraph graph = new FlowGraph();

        // Producer node with 2 outputs: Steam, Oxygen
        RecipeNode producer = RecipeNode.create("Producer", 20.0, 0.0, GTVoltageTier.LV);
        producer.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 100.0));
        producer.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 50.0));
        graph.addNode(producer);

        // Consumer 1 in pool (demands Steam)
        RecipeNode consumer1 = RecipeNode.create("Steam Consumer", 20.0, 32.0, GTVoltageTier.LV);
        consumer1.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:steam"), "Steam", 100.0));
        graph.addNode(consumer1);

        // Consumer 2 in pool (demands Oxygen)
        RecipeNode consumer2 = RecipeNode.create("Oxygen Consumer", 20.0, 32.0, GTVoltageTier.LV);
        consumer2.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 50.0));
        graph.addNode(consumer2);

        CanvasGroupFrame poolFrame = CanvasGroupFrame.createFromNodes("Chemical Pool", List.of(consumer1, consumer2), CanvasGroupFrame.COLOR_CYAN);
        graph.getFrames().add(poolFrame);

        Set<PortRef> selectedPorts = Set.of(
                PortRef.of(producer.getId(), false, 0),
                PortRef.of(producer.getId(), false, 1)
        );

        // Simulate Frame Batch Connection Logic
        List<BoardCommand> subCmds = new ArrayList<>();
        List<RecipeNode> frameNodes = List.of(consumer1, consumer2);

        for (PortRef pref : selectedPorts) {
            IngredientStack srcOut = producer.getOutputs().get(pref.portIndex());
            for (RecipeNode tn : frameNodes) {
                for (int inIdx = 0; inIdx < tn.getInputs().size(); inIdx++) {
                    IngredientStack tnIn = tn.getInputs().get(inIdx);
                    if (srcOut.getId().equals(tnIn.getId())) {
                        graph.addConnection(producer.getId(), pref.portIndex(), tn.getId(), inIdx);
                        subCmds.add(new BoardCommand.ConnectWireCommand(new FlowGraph.ConnectionEdge(producer.getId(), pref.portIndex(), tn.getId(), inIdx)));
                        break;
                    }
                }
            }
        }

        Assertions.assertEquals(2, graph.getConnections().size());

        // Verify connected wire targets
        boolean hasSteamWire = graph.getConnections().stream().anyMatch(e -> e.fromNodeId().equals(producer.getId()) && e.toNodeId().equals(consumer1.getId()));
        boolean hasOxygenWire = graph.getConnections().stream().anyMatch(e -> e.fromNodeId().equals(producer.getId()) && e.toNodeId().equals(consumer2.getId()));
        Assertions.assertTrue(hasSteamWire);
        Assertions.assertTrue(hasOxygenWire);

        // Test Compound Undo
        BoardCommand.CompoundCommand compoundCmd = new BoardCommand.CompoundCommand(subCmds, "Batch Connect");
        compoundCmd.undo(graph);
        Assertions.assertTrue(graph.getConnections().isEmpty());

        // Test Redo
        compoundCmd.redo(graph);
        Assertions.assertEquals(2, graph.getConnections().size());
    }

    @Test
    public void testAutoSpawnMissingRecipeInSharedPool() {
        FlowGraph graph = new FlowGraph();

        // 1. Rock Filtrator producing 3 Geodes: Blue Topaz, Topaz, Apatite
        RecipeNode filtrator = RecipeNode.create("Rock Filtrator", 100.0, 2400.0, GTVoltageTier.HV);
        filtrator.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:blue_topaz_geode"), "Blue Topaz Geode", 10.0));
        filtrator.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:topaz_geode"), "Topaz Geode", 10.0));
        filtrator.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:apatite_geode"), "Apatite Geode", 10.0));
        graph.addNode(filtrator);

        // 2. Shared Machine Pool initially having only Cutter (Blue Topaz)
        RecipeNode cutter1 = RecipeNode.create(ResourceLocation.tryParse("gtceu:cutter"), "Cutter (Blue Topaz)", 20.0, 120.0, GTVoltageTier.HV);
        cutter1.setOverclockMode(com.gtceu.calcboard.api.type.OverclockMode.STANDARD);
        cutter1.setParallel(4);
        cutter1.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:blue_topaz_geode"), "Blue Topaz Geode", 10.0));
        cutter1.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:blue_topaz_gem"), "Blue Topaz Gem", 10.0));
        graph.addNode(cutter1);

        CanvasGroupFrame poolFrame = CanvasGroupFrame.createFromNodes("Cutter Pool", List.of(cutter1), CanvasGroupFrame.COLOR_CYAN);
        poolFrame.setSharedMachineFrame(true);
        graph.getFrames().add(poolFrame);

        // 3. User bundle drops 3 ports onto Cutter Pool
        Set<PortRef> selectedPorts = Set.of(
                PortRef.of(filtrator.getId(), false, 0),
                PortRef.of(filtrator.getId(), false, 1),
                PortRef.of(filtrator.getId(), false, 2)
        );

        // Simulate auto-spawning for Topaz & Apatite
        List<RecipeNode> createdNodes = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> createdEdges = new ArrayList<>();
        List<BoardCommand> subCmds = new ArrayList<>();

        // Match existing cutter1 for port 0
        graph.addConnection(filtrator.getId(), 0, cutter1.getId(), 0);
        FlowGraph.ConnectionEdge edge0 = new FlowGraph.ConnectionEdge(filtrator.getId(), 0, cutter1.getId(), 0);
        subCmds.add(new BoardCommand.ConnectWireCommand(edge0));

        // Auto spawn Cutter (Topaz) for port 1
        RecipeNode cutter2 = RecipeNode.create(cutter1.getMachineIcon(), "Cutter (Topaz)", 20.0, 120.0, cutter1.getTargetTier());
        cutter2.setParallel(cutter1.getParallel());
        cutter2.setOverclockMode(cutter1.getOverclockMode());
        cutter2.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:topaz_geode"), "Topaz Geode", 10.0));
        cutter2.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:topaz_gem"), "Topaz Gem", 10.0));
        graph.addNode(cutter2);
        poolFrame.addNode(cutter2.getId());
        createdNodes.add(cutter2);

        graph.addConnection(filtrator.getId(), 1, cutter2.getId(), 0);
        FlowGraph.ConnectionEdge edge1 = new FlowGraph.ConnectionEdge(filtrator.getId(), 1, cutter2.getId(), 0);
        createdEdges.add(edge1);
        subCmds.add(new BoardCommand.ConnectWireCommand(edge1));

        // Auto spawn Cutter (Apatite) for port 2
        RecipeNode cutter3 = RecipeNode.create(cutter1.getMachineIcon(), "Cutter (Apatite)", 20.0, 120.0, cutter1.getTargetTier());
        cutter3.setParallel(cutter1.getParallel());
        cutter3.setOverclockMode(cutter1.getOverclockMode());
        cutter3.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:apatite_geode"), "Apatite Geode", 10.0));
        cutter3.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:apatite_gem"), "Apatite Gem", 10.0));
        graph.addNode(cutter3);
        poolFrame.addNode(cutter3.getId());
        createdNodes.add(cutter3);

        graph.addConnection(filtrator.getId(), 2, cutter3.getId(), 0);
        FlowGraph.ConnectionEdge edge2 = new FlowGraph.ConnectionEdge(filtrator.getId(), 2, cutter3.getId(), 0);
        createdEdges.add(edge2);
        subCmds.add(new BoardCommand.ConnectWireCommand(edge2));

        subCmds.add(0, new BoardCommand.AddNodesCommand(createdNodes, createdEdges, "Spawn 2 Missing Recipes"));

        // Verify Graph State
        Assertions.assertEquals(4, graph.getNodes().size()); // filtrator + 3 cutters
        Assertions.assertEquals(3, graph.getConnections().size());
        Assertions.assertEquals(4, cutter2.getParallel()); // Hardware synced
        Assertions.assertEquals(GTVoltageTier.HV, cutter2.getTargetTier());

        // Verify fractional 1:1 Auto-Ratio on newly created node before frame resize
        double matchedConsumer = com.gtceu.calcboard.api.solver.FlowGraphSolver.calculateConsumerMatchCount(graph, filtrator, 2, cutter3, 0);
        Assertions.assertTrue(matchedConsumer < 1.0, "Auto-spawned shared machine consumer count must be fractional, got: " + matchedConsumer);

        // Test Compound Undo
        BoardCommand.CompoundCommand compoundCmd2 = new BoardCommand.CompoundCommand(subCmds, "Batch Spawn & Connect");
        compoundCmd2.undo(graph);

        Assertions.assertEquals(2, graph.getNodes().size()); // filtrator + cutter1
        Assertions.assertTrue(graph.getConnections().isEmpty());

        // Test Redo
        compoundCmd2.redo(graph);
        Assertions.assertEquals(4, graph.getNodes().size());
        Assertions.assertEquals(3, graph.getConnections().size());
    }

    @Test
    public void testFrameAutoFitAndResizeCommand() {
        FlowGraph graph = new FlowGraph();

        RecipeNode nodeA = RecipeNode.create("Node A", 20.0, 32.0, GTVoltageTier.LV);
        nodeA.setPosX(100.0);
        nodeA.setPosY(100.0);
        graph.addNode(nodeA);

        RecipeNode nodeB = RecipeNode.create("Node B", 20.0, 32.0, GTVoltageTier.LV);
        nodeB.setPosX(100.0);
        nodeB.setPosY(300.0); // Placed further down
        graph.addNode(nodeB);

        // Frame initially covers Node A and has Node B in contained list (as created by bundle drops)
        CanvasGroupFrame frame = new CanvasGroupFrame("frame_1", "Test Frame", CanvasGroupFrame.COLOR_BLUE, 80.0, 80.0, 220.0, 180.0);
        frame.addNode(nodeA.getId());
        frame.addNode(nodeB.getId());
        graph.addFrame(frame);

        double oldW = frame.getWidth();
        double oldH = frame.getHeight();

        // Node B is outside initial frame height (100 + 180 = 280 < 300 + 160)
        Assertions.assertTrue(nodeB.getPosY() + 160 > frame.getPosY() + frame.getHeight());

        // Perform autoFit
        boolean fitted = frame.autoFit(graph, CanvasGroupFrame.DEFAULT_PADDING);
        Assertions.assertTrue(fitted);
        Assertions.assertTrue(frame.getHeight() > oldH);
        Assertions.assertTrue(frame.containsNode(nodeB.getId()));
        Assertions.assertTrue(frame.getPosY() + frame.getHeight() >= nodeB.getPosY() + 160 + CanvasGroupFrame.DEFAULT_PADDING);

        // Test ResizeFrameCommand Undo / Redo
        BoardCommand.ResizeFrameCommand resizeCmd = new BoardCommand.ResizeFrameCommand(
                frame.getId(), 80.0, 80.0, oldW, oldH, frame.getPosX(), frame.getPosY(), frame.getWidth(), frame.getHeight(), "Auto-fit"
        );
        resizeCmd.undo(graph);
        Assertions.assertEquals(oldH, frame.getHeight(), 0.001);

        resizeCmd.redo(graph);
        Assertions.assertTrue(frame.getHeight() > oldH);
    }

    @Test
    public void testSecondaryInputMatchingPreference() {
        RecipeNode templateCutter = RecipeNode.create(ResourceLocation.tryParse("gtceu:cutter"), "Cutter (Blue Topaz)", 20.0, 120.0, GTVoltageTier.HV);
        templateCutter.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:blue_topaz_geode"), "Blue Topaz Geode", 10.0));
        templateCutter.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:lubricant"), "Lubricant", 10.0)); // Template uses Lubricant!

        // Candidate 1: Topaz Geode + Water
        com.gtceu.calcboard.api.model.SearchableRecipe srWater =
                new com.gtceu.calcboard.api.model.SearchableRecipe(
                        "recipe_water", "Topaz Geode (Water)", "gtceu", "gtceu:cutter", "Cutter",
                        "topaz water", "topaz_gem",
                        new ResourceLocation[]{ResourceLocation.tryParse("gtceu:topaz_geode"), ResourceLocation.tryParse("minecraft:water")},
                        new ResourceLocation[]{ResourceLocation.tryParse("gtceu:topaz_gem")},
                        new String[]{"Topaz Geode", "Water"}, new String[]{"Topaz Gem"}, true
                );

        // Candidate 2: Topaz Geode + Lubricant
        com.gtceu.calcboard.api.model.SearchableRecipe srLubricant =
                new com.gtceu.calcboard.api.model.SearchableRecipe(
                        "recipe_lubricant", "Topaz Geode (Lubricant)", "gtceu", "gtceu:cutter", "Cutter",
                        "topaz lubricant", "topaz_gem",
                        new ResourceLocation[]{ResourceLocation.tryParse("gtceu:topaz_geode"), ResourceLocation.tryParse("gtceu:lubricant")},
                        new ResourceLocation[]{ResourceLocation.tryParse("gtceu:topaz_gem")},
                        new String[]{"Topaz Geode", "Lubricant"}, new String[]{"Topaz Gem"}, true
                );

        // Setup global recipe cache with both recipes (Water first in list)
        com.gtceu.calcboard.client.gui.search.RecipeSearchCacheManager.setGlobalRecipesForTesting(java.util.List.of(srWater, srLubricant));

        // findRecipeForInput should pick Candidate 2 (Lubricant) because template uses Lubricant!
        com.gtceu.calcboard.api.model.SearchableRecipe chosen =
                com.gtceu.calcboard.client.gui.search.RecipeSearchEngine.findRecipeForInput(templateCutter, ResourceLocation.tryParse("gtceu:topaz_geode"));

        Assertions.assertNotNull(chosen);
        Assertions.assertEquals("recipe_lubricant", chosen.recipe());
    }
}
