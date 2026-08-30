package com.gtceu.calcboard;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowGraphSolver;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

public class AutoRatioBottleneckTest {

    @Test
    public void testAutoRatioResolvesChanceAndUpstreamBottlenecks() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation shardId = ResourceLocation.tryParse("gtceu:lightning_shard");
        ResourceLocation sheetId = ResourceLocation.tryParse("gtceu:nether_star_plate");

        // Extractor: produces Nether Star Plate (1.0/s)
        RecipeNode extractor = RecipeNode.create(ResourceLocation.tryParse("gtceu:extractor"), "Extractor", 20, 30, GTVoltageTier.LV);
        extractor.getOutputs().add(IngredientStack.item(sheetId, "Nether Star Plate", 1.0));
        graph.addNode(extractor);

        // Autoclave: consumes Nether Star Plate (1.0/s) and produces Shard with 50% chance (0.5 shard/s)
        RecipeNode autoclave = RecipeNode.create(ResourceLocation.tryParse("gtceu:autoclave"), "Autoclave", 20, 30, GTVoltageTier.LV);
        IngredientStack outShard = IngredientStack.item(shardId, "Lightning Shard", 1.0, 0.5f);
        autoclave.getOutputs().add(outShard);
        IngredientStack inSheet = IngredientStack.item(sheetId, "Nether Star Plate", 1.0);
        autoclave.getInputs().add(inSheet);
        graph.addNode(autoclave);

        // Forming Press (Anchor, Count 1): requires 1.0 Shard per cycle (1 cycle/s = 1.0 shard/s demand)
        RecipeNode formingPress = RecipeNode.create(ResourceLocation.tryParse("gtceu:forming_press"), "Forming Press", 20, 30, GTVoltageTier.LV);
        formingPress.setBaseNode(true);
        formingPress.setMachineCount(1.0);
        IngredientStack formingIn = IngredientStack.item(shardId, "Lightning Shard", 1.0);
        formingPress.getInputs().add(formingIn);
        graph.addNode(formingPress);

        // Connect: Extractor -> Autoclave -> Forming Press
        graph.addConnection(extractor.getId(), 0, autoclave.getId(), 0);
        graph.addConnection(autoclave.getId(), 0, formingPress.getId(), 0);

        // Run autoRatioFromAnchor starting from Forming Press (Anchor Count = 1.0)
        FlowGraphSolver.autoRatioFromAnchor(graph, formingPress, true);

        // Forming Press requires 1.0 shard/s. Autoclave outputs 0.5 shard/s per machine.
        // Therefore, Autoclave must be scaled to AT LEAST 2 machines (1.0 / 0.5 = 2.0).
        Assertions.assertTrue(autoclave.getMachineCount() >= 2.0,
                "Autoclave must be at least 2 machines to fulfill 50% chance shard demand without bottleneck! Actual: " + autoclave.getMachineCount());

        // Extractor must supply 2 plates/s to feed 2 Autoclaves -> Extractor must be at least 2 machines
        Assertions.assertTrue(extractor.getMachineCount() >= 2.0,
                "Extractor must be at least 2 machines to supply 2 Autoclaves! Actual: " + extractor.getMachineCount());

        // Check node efficiencies: all nodes should be 1.0 (100% operational without input deficiency)
        Map<String, Double> efficiencies = FlowGraphSolver.computeNodeEfficiencies(graph);
        Assertions.assertEquals(1.0, efficiencies.getOrDefault(formingPress.getId(), 0.0), 1e-3, "Forming Press must operate at 100% efficiency without bottleneck");
        Assertions.assertEquals(1.0, efficiencies.getOrDefault(autoclave.getId(), 0.0), 1e-3, "Autoclave must operate at 100% efficiency without bottleneck");
        Assertions.assertEquals(1.0, efficiencies.getOrDefault(extractor.getId(), 0.0), 1e-3, "Extractor must operate at 100% efficiency without bottleneck");
    }

    @Test
    public void testMultiSlotLimitingReagentBottleneckResolution() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation itemA = ResourceLocation.tryParse("gtceu:item_a");
        ResourceLocation itemB = ResourceLocation.tryParse("gtceu:item_b");

        // Producer: outputs Item A (50% chance = 0.5/s) and Item B (45% chance = 0.45/s)
        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:autoclave"), "Autoclave", 20, 30, GTVoltageTier.LV);
        producer.getOutputs().add(IngredientStack.item(itemA, "Item A", 1.0, 0.50f));
        producer.getOutputs().add(IngredientStack.item(itemB, "Item B", 1.0, 0.45f));
        graph.addNode(producer);

        // Consumer (Anchor, Count 1): requires 1.0 Item A and 1.0 Item B per second
        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:forming_press"), "Forming Press", 20, 30, GTVoltageTier.LV);
        consumer.setBaseNode(true);
        consumer.setMachineCount(1.0);
        consumer.getInputs().add(IngredientStack.item(itemA, "Item A", 1.0));
        consumer.getInputs().add(IngredientStack.item(itemB, "Item B", 1.0));
        graph.addNode(consumer);

        // Connect both outputs to consumer's respective inputs
        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);
        graph.addConnection(producer.getId(), 1, consumer.getId(), 1);

        FlowGraphSolver.autoRatioFromAnchor(graph, consumer, true);

        // Item A needs 1.0 / 0.50 = 2.0 machines.
        // Item B (the bottleneck) needs 1.0 / 0.45 = 2.222 machines -> Integer count = 3 machines!
        Assertions.assertTrue(producer.getMachineCount() >= 3.0,
                "Producer must scale up to bottleneck (45% slot) needing 3 machines! Actual: " + producer.getMachineCount());

        Map<String, Double> efficiencies = FlowGraphSolver.computeNodeEfficiencies(graph);
        Assertions.assertEquals(1.0, efficiencies.getOrDefault(consumer.getId(), 0.0), 1e-3, "Consumer must operate at 100% efficiency without bottleneck");
    }

    @Test
    public void testClosedLoopDownstreamChainDoesNotOverScale() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation shardId = ResourceLocation.tryParse("gtceu:lightning_shard");
        ResourceLocation netherStarId = ResourceLocation.tryParse("minecraft:nether_star");
        ResourceLocation sheetId = ResourceLocation.tryParse("gtceu:nether_star_plate");

        // Autoclave: produces Shard with 50% chance (0.5/s)
        RecipeNode autoclave = RecipeNode.create(ResourceLocation.tryParse("gtceu:autoclave"), "Autoclave", 20, 30, GTVoltageTier.LV);
        autoclave.getOutputs().add(IngredientStack.item(shardId, "Lightning Shard", 1.0, 0.50f));
        autoclave.getInputs().add(IngredientStack.item(sheetId, "Nether Star Plate", 1.0));
        graph.addNode(autoclave);

        // Forming Press (Anchor, Count 1): requires 1.0 Shard/s, outputs 1.0 Nether Star/s
        RecipeNode formingPress = RecipeNode.create(ResourceLocation.tryParse("gtceu:forming_press"), "Forming Press", 20, 30, GTVoltageTier.LV);
        formingPress.setBaseNode(true);
        formingPress.setMachineCount(1.0);
        formingPress.getInputs().add(IngredientStack.item(shardId, "Lightning Shard", 1.0));
        formingPress.getOutputs().add(IngredientStack.item(netherStarId, "Nether Star", 1.0));
        graph.addNode(formingPress);

        // Implosion Compressor: consumes 1.0 Nether Star/s, outputs 1.0 Nether Star/s
        RecipeNode implosion = RecipeNode.create(ResourceLocation.tryParse("gtceu:implosion_compressor"), "Implosion Compressor", 20, 30, GTVoltageTier.LV);
        implosion.getInputs().add(IngredientStack.item(netherStarId, "Nether Star", 1.0));
        implosion.getOutputs().add(IngredientStack.item(netherStarId, "Nether Star", 1.0));
        graph.addNode(implosion);

        // Forge Hammer: consumes 1.0 Nether Star/s, outputs 1.0 Nether Star/s
        RecipeNode hammer = RecipeNode.create(ResourceLocation.tryParse("gtceu:forge_hammer"), "Forge Hammer", 20, 30, GTVoltageTier.LV);
        hammer.getInputs().add(IngredientStack.item(netherStarId, "Nether Star", 1.0));
        hammer.getOutputs().add(IngredientStack.item(netherStarId, "Nether Star", 1.0));
        graph.addNode(hammer);

        // Polarizer: consumes 1.0 Nether Star/s (takes 2s = 0.5/s rate), outputs 1.0 Plate
        RecipeNode polarizer = RecipeNode.create(ResourceLocation.tryParse("gtceu:polarizer"), "Polarizer", 40, 30, GTVoltageTier.LV);
        polarizer.getInputs().add(IngredientStack.item(netherStarId, "Nether Star", 1.0));
        polarizer.getOutputs().add(IngredientStack.item(sheetId, "Nether Star Plate", 1.0));
        graph.addNode(polarizer);

        // Connect loop: Autoclave -> Forming Press -> Implosion -> Hammer -> Polarizer -> Autoclave
        graph.addConnection(autoclave.getId(), 0, formingPress.getId(), 0);
        graph.addConnection(formingPress.getId(), 0, implosion.getId(), 0);
        graph.addConnection(implosion.getId(), 0, hammer.getId(), 0);
        graph.addConnection(hammer.getId(), 0, polarizer.getId(), 0);
        graph.addConnection(polarizer.getId(), 0, autoclave.getId(), 0);

        FlowGraphSolver.autoRatioFromAnchor(graph, formingPress, true);

        // 1. Forming Press (Anchor) = 1.0 machine
        Assertions.assertEquals(1.0, formingPress.getMachineCount(), 1e-4);

        // 2. Autoclave (Upstream of Anchor) must scale to 2.0 machines to satisfy 50% chance demand
        Assertions.assertEquals(2.0, autoclave.getMachineCount(), 1e-4);

        // 3. Implosion Compressor (Downstream of Anchor) processes 1.0 Nether Star/s from Anchor -> MUST BE 1.0 machine!
        Assertions.assertEquals(1.0, implosion.getMachineCount(), 1e-4, "Implosion Compressor must not overscale beyond Anchor output!");

        // 4. Forge Hammer (Downstream of Anchor) processes 1.0 Nether Star/s -> MUST BE 1.0 machine!
        Assertions.assertEquals(1.0, hammer.getMachineCount(), 1e-4, "Forge Hammer must not overscale beyond Anchor output!");

        // 5. Polarizer (Downstream of Anchor, rate 0.5/s) processes 1.0 Nether Star/s -> MUST BE 2.0 machines!
        Assertions.assertEquals(2.0, polarizer.getMachineCount(), 1e-4, "Polarizer must scale to exactly 2.0 machines for 1.0/s incoming supply!");
    }

    @Test
    public void testHarmonizedAutoRatioPerfectCleanScale() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation itemA = ResourceLocation.tryParse("gtceu:item_a");

        // Producer: 1.0 item / 3s = 0.33333/s per machine
        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:macerator"), "Macerator", 60, 30, GTVoltageTier.LV);
        producer.getOutputs().add(IngredientStack.item(itemA, "Item A", 1.0));
        graph.addNode(producer);

        // Consumer (Anchor): 1.0 item / 2s = 0.50/s per machine (Anchor count = 1.0)
        // Baseline Ratio: 1.0 Consumer (0.5/s) requires 0.5 / (1/3) = 1.5 Macerators (Non-integer fraction 3/2!)
        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:furnace"), "Electric Furnace", 40, 30, GTVoltageTier.LV);
        consumer.setBaseNode(true);
        consumer.setMachineCount(1.0);
        consumer.getInputs().add(IngredientStack.item(itemA, "Item A", 1.0));
        graph.addNode(consumer);

        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);

        // Standard Auto-Ratio with anchor = 1.0 would ceil Macerator from 1.5 to 2.0 (yielding 0.5 surplus)
        // Harmonized Auto-Ratio should scale Anchor to LCM = 2.0, resulting in exactly 3.0 Macerators!
        FlowGraphSolver.autoRatioHarmonized(graph, consumer);

        // Consumer (Anchor) scaled to 2.0 machines (1.0 item/s total demand)
        Assertions.assertEquals(2.0, consumer.getMachineCount(), 1e-4, "Consumer anchor must be harmonized to 2.0 machines!");

        // Producer scaled to 3.0 machines (1.0 item/s total supply, exactly 100% 0 waste!)
        Assertions.assertEquals(3.0, producer.getMachineCount(), 1e-4, "Producer must be harmonized to exactly 3.0 machines!");

        // Check efficiency: exactly 100% without surplus
        Map<String, Double> eff = FlowGraphSolver.computeNodeEfficiencies(graph);
        Assertions.assertEquals(1.0, eff.get(consumer.getId()), 1e-4);
        Assertions.assertEquals(1.0, eff.get(producer.getId()), 1e-4);
    }

    @Test
    public void testHarmonizedAutoRatioWithDifferentTolerances() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation dustA = ResourceLocation.tryParse("gtceu:dust_a");
        ResourceLocation starId = ResourceLocation.tryParse("gtceu:star");

        // Autoclave: produces dustA at 0.28/s per machine (1.0 / 0.28 = 25/7 = 3.5714 autoclaves per press)
        RecipeNode autoclave = RecipeNode.create(ResourceLocation.tryParse("gtceu:autoclave"), "Autoclave", 20, 30, GTVoltageTier.LV);
        autoclave.getOutputs().add(IngredientStack.item(dustA, "Dust A", 0.28));
        graph.addNode(autoclave);

        // Forming Press (Anchor): consumes dustA at 1.0/s per machine
        RecipeNode formingPress = RecipeNode.create(ResourceLocation.tryParse("gtceu:forming_press"), "Forming Press", 20, 30, GTVoltageTier.LV);
        formingPress.setBaseNode(true);
        formingPress.setMachineCount(1.0);
        formingPress.getInputs().add(IngredientStack.item(dustA, "Dust A", 1.0));
        formingPress.getOutputs().add(IngredientStack.item(starId, "Star", 1.0));
        graph.addNode(formingPress);

        // Polarizer: consumes star at 0.175/s per machine (1.0 / 0.175 = 40/7 = 5.7142857 polarizers per press)
        RecipeNode polarizer = RecipeNode.create(ResourceLocation.tryParse("gtceu:polarizer"), "Polarizer", 20, 30, GTVoltageTier.LV);
        polarizer.getInputs().add(IngredientStack.item(starId, "Star", 0.175));
        graph.addNode(polarizer);

        graph.addConnection(autoclave.getId(), 0, formingPress.getId(), 0);
        graph.addConnection(formingPress.getId(), 0, polarizer.getId(), 0);

        // Case 1: Tolerance = 0.20 (20%) -> 1 Press (3.57 -> 4 Autoclaves [12% <= 20%], 5.71 -> 6 Polarizers [5.0% <= 20%])
        com.gtceu.calcboard.api.storage.BoardManager.getInstance().setMaxHarmonizeScale(16);
        com.gtceu.calcboard.api.storage.BoardManager.getInstance().setHarmonizeSurplusTolerance(0.20);
        double scale20 = FlowGraphSolver.findPerfectHarmonizedAnchorCount(graph, formingPress);
        Assertions.assertEquals(1.0, scale20, 1e-4, "Under 20% tolerance, anchor should be compact 1 machine!");

        // Case 2: Tolerance = 0.05 (5%) -> 2 Presses or 7 Presses based on tolerance
        com.gtceu.calcboard.api.storage.BoardManager.getInstance().setHarmonizeSurplusTolerance(0.05);
        double scale5 = FlowGraphSolver.findPerfectHarmonizedAnchorCount(graph, formingPress);
        Assertions.assertTrue(scale5 >= 1.0 && scale5 <= 7.0, "Scale at 5% tolerance should be within reasonable bounds: " + scale5);

        // Case 3: Tolerance = 0.00 (0% Strict) -> Must scale up to exact integer multiples (scale == 7.0 for 25 Autoclaves & 40 Polarizers!)
        com.gtceu.calcboard.api.storage.BoardManager.getInstance().setHarmonizeSurplusTolerance(0.0);
        double scale0 = FlowGraphSolver.findPerfectHarmonizedAnchorCount(graph, formingPress);
        Assertions.assertEquals(7.0, scale0, 1e-4, "Under 0% strict tolerance, anchor must scale up to exactly 7.0 machines!");
    }
}
