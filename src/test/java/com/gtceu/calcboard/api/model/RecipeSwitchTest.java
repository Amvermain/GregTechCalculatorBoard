package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.SearchableRecipe;
import com.gtceu.calcboard.client.gui.search.RecipeSearchEngine;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class RecipeSwitchTest {

    private SearchableRecipe createMockRecipe(String displayName, String modId, String categoryId, String categoryName,
                                              List<String> outputNames, List<String> inputNames) {
        StringBuilder outSb = new StringBuilder();
        for (String out : outputNames) {
            outSb.append(out.toLowerCase()).append(" ");
        }
        StringBuilder inSb = new StringBuilder();
        for (String in : inputNames) {
            inSb.append(in.toLowerCase()).append(" ");
        }

        ResourceLocation[] outIds = outputNames.stream()
                .map(n -> ResourceLocation.tryParse(modId + ":" + n.toLowerCase().replace(' ', '_')))
                .toArray(ResourceLocation[]::new);
        ResourceLocation[] inIds = inputNames.stream()
                .map(n -> ResourceLocation.tryParse(modId + ":" + n.toLowerCase().replace(' ', '_')))
                .toArray(ResourceLocation[]::new);
        String[] outNamesArr = outputNames.toArray(new String[0]);
        String[] inNamesArr = inputNames.toArray(new String[0]);

        return new SearchableRecipe(
                null,
                displayName,
                modId.toLowerCase(),
                categoryId.toLowerCase(),
                categoryName.toLowerCase(),
                inSb.toString().trim(),
                outSb.toString().trim(),
                inIds,
                outIds,
                inNamesArr,
                outNamesArr
        );
    }

    @Test
    public void testSwitchNodeRecipePreservesMatchingWires() {
        FlowGraph graph = new FlowGraph();

        // 1. Setup Source & Consumer nodes
        RecipeNode waterSource = RecipeNode.create(ResourceLocation.tryParse("gtceu:pump"), "Water Pump", 20, 32, GTVoltageTier.LV);
        waterSource.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));
        graph.addNode(waterSource);

        RecipeNode nopSupplier = RecipeNode.create(ResourceLocation.tryParse("gtceu:chemical_reactor"), "NOP Maker", 20, 32, GTVoltageTier.LV);
        nopSupplier.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:nop_fertilizer"), "NOP Fertilizer", 1.0));
        graph.addNode(nopSupplier);

        RecipeNode woodConsumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:wood_cutter"), "Wood Cutter", 20, 32, GTVoltageTier.LV);
        woodConsumer.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 10.0));
        graph.addNode(woodConsumer);

        RecipeNode tarConsumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:distillery"), "Tar Distiller", 20, 32, GTVoltageTier.LV);
        tarConsumer.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 500.0));
        graph.addNode(tarConsumer);

        // 2. Setup Greenhouse Node (NOP Recipe)
        // Inputs: [0: Water, 1: NOP Fertilizer]
        // Outputs: [0: Oak Log, 1: Wood Tar]
        RecipeNode greenhouse = RecipeNode.create(ResourceLocation.tryParse("gtceu:greenhouse"), "Greenhouse (NOP)", 400, 128, GTVoltageTier.MV);
        greenhouse.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0));
        greenhouse.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:nop_fertilizer"), "NOP Fertilizer", 1.0));
        greenhouse.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 16.0));
        greenhouse.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 200.0));
        graph.addNode(greenhouse);

        // Connect wires:
        // Water Pump -> Greenhouse Input 0 (Water)
        // NOP Maker -> Greenhouse Input 1 (NOP Fertilizer)
        // Greenhouse Output 0 (Oak Log) -> Wood Cutter Input 0
        // Greenhouse Output 1 (Wood Tar) -> Tar Distiller Input 0
        graph.getConnections().add(new FlowGraph.ConnectionEdge(waterSource.getId(), 0, greenhouse.getId(), 0));
        graph.getConnections().add(new FlowGraph.ConnectionEdge(nopSupplier.getId(), 0, greenhouse.getId(), 1));
        graph.getConnections().add(new FlowGraph.ConnectionEdge(greenhouse.getId(), 0, woodConsumer.getId(), 0));
        graph.getConnections().add(new FlowGraph.ConnectionEdge(greenhouse.getId(), 1, tarConsumer.getId(), 0));

        Assertions.assertEquals(4, graph.getConnections().size());

        // 3. Define New Alternative Recipe Template (Carbon Recipe)
        // Inputs: [0: Carbon Fertilizer, 1: Water] (Note: Water is now index 1!)
        // Outputs: [0: Oak Log, 1: Wood Tar]
        RecipeNode carbonTemplate = RecipeNode.create(ResourceLocation.tryParse("gtceu:greenhouse"), "Greenhouse (Carbon)", 300, 256, GTVoltageTier.HV);
        carbonTemplate.getInputs().add(IngredientStack.item(ResourceLocation.tryParse("gtceu:carbon_fertilizer"), "Carbon Fertilizer", 2.0));
        carbonTemplate.getInputs().add(IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1200.0));
        carbonTemplate.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 24.0));
        carbonTemplate.getOutputs().add(IngredientStack.fluid(ResourceLocation.tryParse("gtceu:wood_tar"), "Wood Tar", 300.0));

        // 4. Perform Switch Recipe
        var cmd = graph.switchNodeRecipe(greenhouse, carbonTemplate);
        Assertions.assertNotNull(cmd);

        // 5. Verify Node Properties Updated
        Assertions.assertEquals("Greenhouse (Carbon)", greenhouse.getRawName());
        Assertions.assertEquals(300, greenhouse.getBaseDurationTicks());
        Assertions.assertEquals(256, greenhouse.getBaseEUt());
        Assertions.assertEquals(2, greenhouse.getInputs().size());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:carbon_fertilizer"), greenhouse.getInputs().get(0).getId());
        Assertions.assertEquals(ResourceLocation.tryParse("minecraft:water"), greenhouse.getInputs().get(1).getId());

        // 6. Verify Smart Wire Preservation
        // - NOP wire should be severed (3 wires remaining: Water, Oak Log, Wood Tar)
        // - Water wire should now connect to Greenhouse input index 1
        Assertions.assertEquals(3, graph.getConnections().size());

        boolean waterWireFound = false;
        boolean oakWireFound = false;
        boolean tarWireFound = false;

        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(waterSource.getId()) && edge.toNodeId().equals(greenhouse.getId())) {
                Assertions.assertEquals(1, edge.inputIndex(), "Water wire should re-bind to new input port index 1");
                waterWireFound = true;
            }
            if (edge.fromNodeId().equals(greenhouse.getId()) && edge.toNodeId().equals(woodConsumer.getId())) {
                Assertions.assertEquals(0, edge.outputIndex());
                oakWireFound = true;
            }
            if (edge.fromNodeId().equals(greenhouse.getId()) && edge.toNodeId().equals(tarConsumer.getId())) {
                Assertions.assertEquals(1, edge.outputIndex());
                tarWireFound = true;
            }
        }

        Assertions.assertTrue(waterWireFound);
        Assertions.assertTrue(oakWireFound);
        Assertions.assertTrue(tarWireFound);

        // 7. Verify Undo / Redo
        cmd.undo(graph);
        Assertions.assertEquals("Greenhouse (NOP)", greenhouse.getRawName());
        Assertions.assertEquals(400, greenhouse.getBaseDurationTicks());
        Assertions.assertEquals(4, graph.getConnections().size());

        cmd.redo(graph);
        Assertions.assertEquals("Greenhouse (Carbon)", greenhouse.getRawName());
        Assertions.assertEquals(300, greenhouse.getBaseDurationTicks());
        Assertions.assertEquals(3, graph.getConnections().size());
    }

    @Test
    public void testFindAlternativeRecipesRanking() {
        RecipeNode greenhouse = RecipeNode.create(ResourceLocation.tryParse("gtceu:greenhouse"), "Greenhouse", 400, 128, GTVoltageTier.MV);
        greenhouse.getOutputs().add(IngredientStack.item(ResourceLocation.tryParse("minecraft:oak_log"), "Oak Log", 16.0));

        List<SearchableRecipe> pool = new ArrayList<>();

        // Recipe 1: Unrelated recipe (Macerator -> Iron Dust)
        pool.add(createMockRecipe("Iron Macerating", "gtceu", "macerator", "Macerator",
                List.of("Iron Dust"), List.of("Iron Ore")));

        // Recipe 2: Same output, different machine (Large Tree Farm -> Oak Log)
        pool.add(createMockRecipe("Large Tree Farm", "gtceu", "tree_farm", "Tree Farm",
                List.of("Oak Log"), List.of("Water", "Sapling")));

        // Recipe 3: Same machine (Greenhouse -> Birch Log)
        pool.add(createMockRecipe("Birch Growing", "gtceu", "greenhouse", "Greenhouse",
                List.of("Birch Log"), List.of("Water", "Birch Sapling")));

        // Recipe 4: Same machine AND same output (Greenhouse -> Oak Log + Wood Tar)
        pool.add(createMockRecipe("Oak Growing (Carbon)", "gtceu", "greenhouse", "Greenhouse",
                List.of("Oak Log", "Wood Tar"), List.of("Water", "Carbon Fertilizer")));

        List<SearchableRecipe> results = RecipeSearchEngine.findAlternativeRecipes(greenhouse, pool);

        Assertions.assertFalse(results.isEmpty());
        // Recipe 4 (Same machine + Same output) must be ranked #1
        Assertions.assertEquals("Oak Growing (Carbon)", results.get(0).displayName());
        // Recipe 3 (Same machine) should be before unrelated
        Assertions.assertEquals("Birch Growing", results.get(1).displayName());
        // Recipe 2 (Same output) should be included
        Assertions.assertEquals("Large Tree Farm", results.get(2).displayName());
        // Recipe 1 (Unrelated) should be excluded
        Assertions.assertEquals(3, results.size());
    }
}

