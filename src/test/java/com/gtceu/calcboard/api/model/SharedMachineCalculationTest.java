package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.solver.BalanceSummary;
import com.gtceu.calcboard.api.solver.FlowSummaryAggregator;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;

@ExtendWith(MinecraftBootstrapExtension.class)
public class SharedMachineCalculationTest {

    @Test
    public void testSharedMachineDutyAndRequiredCount() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation cutterIcon = ResourceLocation.tryParse("gtceu:lv_cutter");
        RecipeNode node1 = RecipeNode.create(cutterIcon, "Quartz Geode", 20.0, 30.0, GTVoltageTier.LV);
        node1.setMachineCount(0.15);

        RecipeNode node2 = RecipeNode.create(cutterIcon, "Amethyst Geode", 20.0, 30.0, GTVoltageTier.LV);
        node2.setMachineCount(0.20);

        RecipeNode node3 = RecipeNode.create(cutterIcon, "Echo Geode", 20.0, 30.0, GTVoltageTier.LV);
        node3.setMachineCount(0.10);

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Geode Slicing Cutter Pool", List.of(node1, node2, node3), CanvasGroupFrame.COLOR_EMERALD);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        // 1. Total duty check: 0.15 + 0.20 + 0.10 = 0.45 (45.0%)
        double totalDuty = frame.computeTotalMachineDuty(graph);
        Assertions.assertEquals(0.45, totalDuty, 0.0001);

        // 2. Required machines check: ceil(0.45) = 1 machine
        int reqMachines = frame.computeRequiredMachines(graph);
        Assertions.assertEquals(1, reqMachines);

        // 3. Machine compatibility check: all use lv_cutter -> true
        Assertions.assertTrue(frame.isMachineCompatible(graph));

        // 4. Overload test
        node1.setMachineCount(0.60);
        node2.setMachineCount(0.50);
        node3.setMachineCount(0.35);
        double overloadedDuty = frame.computeTotalMachineDuty(graph);
        Assertions.assertEquals(1.45, overloadedDuty, 0.0001);
        Assertions.assertEquals(2, frame.computeRequiredMachines(graph));
    }

    @Test
    public void testFlowSummarySharedMachineAggregation() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation cutterIcon = ResourceLocation.tryParse("gtceu:lv_cutter");
        // Single machine power: 30 EU/t
        RecipeNode node1 = RecipeNode.create(cutterIcon, "Quartz Geode", 20.0, 30.0, GTVoltageTier.LV);
        node1.setMachineCount(0.15); // Power: 30 * 0.15 = 4.5 EU/t

        RecipeNode node2 = RecipeNode.create(cutterIcon, "Amethyst Geode", 20.0, 30.0, GTVoltageTier.LV);
        node2.setMachineCount(0.20); // Power: 30 * 0.20 = 6.0 EU/t

        RecipeNode node3 = RecipeNode.create(cutterIcon, "Echo Geode", 20.0, 30.0, GTVoltageTier.LV);
        node3.setMachineCount(0.10); // Power: 30 * 0.10 = 3.0 EU/t

        graph.addNode(node1);
        graph.addNode(node2);
        graph.addNode(node3);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Shared Cutter", List.of(node1, node2, node3), CanvasGroupFrame.COLOR_EMERALD);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        BalanceSummary summary = FlowSummaryAggregator.computeSummary(graph);

        // Total machine count should be 1, NOT 3!
        Assertions.assertEquals(1, summary.totalMachineCount());

        // Machine breakdown should have only 1 cutter entry with count 1
        Assertions.assertEquals(1, summary.machineBreakdown().size());
        Assertions.assertEquals(1, summary.machineBreakdown().values().iterator().next());

        // Total power: 4.5 + 6.0 + 3.0 = 13.5 EU/t
        Assertions.assertEquals(13.5, summary.totalEUt(), 0.001);
    }

    @Test
    public void testIncompatibleMachineDetection() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation cutterIcon = ResourceLocation.tryParse("gtceu:lv_cutter");
        ResourceLocation latheIcon = ResourceLocation.tryParse("gtceu:lv_lathe");

        RecipeNode node1 = RecipeNode.create(cutterIcon, "Cutter Node", 20.0, 30.0, GTVoltageTier.LV);
        RecipeNode node2 = RecipeNode.create(latheIcon, "Lathe Node", 20.0, 30.0, GTVoltageTier.LV);

        graph.addNode(node1);
        graph.addNode(node2);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Mixed Pool", List.of(node1, node2), CanvasGroupFrame.COLOR_ROSE);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        Assertions.assertFalse(frame.isMachineCompatible(graph), "Different machine types in shared frame must return false!");
    }

    @Test
    public void testVoltageTierIncompatibilityAndHardwareBatchSync() {
        FlowGraph graph = new FlowGraph();

        ResourceLocation lvCutterIcon = ResourceLocation.tryParse("gtceu:lv_cutter");
        ResourceLocation mvCutterIcon = ResourceLocation.tryParse("gtceu:mv_cutter");

        RecipeNode node1 = RecipeNode.create(lvCutterIcon, "Quartz Geode", 20.0, 30.0, GTVoltageTier.LV);
        node1.setMachineCount(0.15);
        node1.setPos(0, 0);

        RecipeNode node2 = RecipeNode.create(mvCutterIcon, "Amethyst Geode", 20.0, 30.0, GTVoltageTier.MV);
        node2.setMachineCount(0.20);
        node2.setPos(150, 0);

        graph.addNode(node1);
        graph.addNode(node2);

        CanvasGroupFrame frame = CanvasGroupFrame.createFromNodes("Mixed Voltage Cutter Pool", List.of(node1, node2), CanvasGroupFrame.COLOR_ROSE);
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);

        // 1. Different voltage tiers (LV vs MV) must be detected as incompatible!
        Assertions.assertFalse(frame.isMachineCompatible(graph), "Different voltage tiers in shared pool must return false!");

        // 2. Batch Sync hardware config from master (node1) to all enclosed nodes
        node1.setTargetTier(GTVoltageTier.HV);
        node1.setMachineIcon(ResourceLocation.tryParse("gtceu:hv_cutter"));
        node1.setParallel(4);
        node1.setMultiblock(true);
        node1.getAddons().add(new com.gtceu.calcboard.api.catalog.MachineAddon("gtceu:cupronickel_coil", "Cupronickel Coil", com.gtceu.calcboard.api.catalog.MachineAddon.Category.COIL, "Coil", ResourceLocation.tryParse("gtceu:cupronickel_coil")));
        frame.syncHardwareConfig(node1, graph);

        // node2 should now be synchronized to HV, parallel 4, multiblock true, and have the coil addon!
        Assertions.assertEquals(GTVoltageTier.HV, node2.getTargetTier());
        Assertions.assertEquals(ResourceLocation.tryParse("gtceu:hv_cutter"), node2.getMachineIcon());
        Assertions.assertEquals(4, node2.getParallel());
        Assertions.assertTrue(node2.isMultiblock());
        Assertions.assertEquals(1, node2.getAddons().size());
        Assertions.assertEquals("Cupronickel Coil", node2.getAddons().get(0).getName());
        Assertions.assertTrue(frame.isMachineCompatible(graph), "After batch sync, frame must be fully compatible!");
    }

    @Test
    public void testSharedMachineAutoRatioDecimalPreservation() {
        FlowGraph graph = new FlowGraph();

        IngredientStack geode = IngredientStack.item(ResourceLocation.tryParse("gtceu:blue_topaz_geode"), "Blue Topaz Geode", 1.0, 1.0);
        IngredientStack gem = IngredientStack.item(ResourceLocation.tryParse("gtceu:blue_topaz_gem"), "Blue Topaz Gem", 1.0, 1.0);

        RecipeNode producer = RecipeNode.create(ResourceLocation.tryParse("gtceu:mv_rock_filtrator"), "Rock Filtrator", 48.0, 60.0, GTVoltageTier.MV);
        producer.getOutputs().add(geode);
        producer.setMachineCount(1.0);
        producer.setPos(0, 0);

        RecipeNode consumer = RecipeNode.create(ResourceLocation.tryParse("gtceu:lv_cutter"), "Cutter", 10.0, 30.0, GTVoltageTier.LV);
        consumer.getInputs().add(geode);
        consumer.getOutputs().add(gem);
        consumer.setMachineCount(1.0);
        consumer.setPos(200, 0);

        graph.addNode(producer);
        graph.addNode(consumer);

        CanvasGroupFrame sharedFrame = CanvasGroupFrame.createFromNodes("Shared Machine Pool", List.of(consumer), CanvasGroupFrame.COLOR_CYAN);
        sharedFrame.setSharedMachineFrame(true);
        graph.addFrame(sharedFrame);

        // 1. Calculate Shift-drag match count: supply = 1/48/20 = 1/960 = 0.0010416/s. consumer in = 1/10/20 = 1/200 = 0.005/s.
        // matched count = 200 / 960 = 0.2083
        double matchedConsumer = com.gtceu.calcboard.api.solver.FlowGraphSolver.calculateConsumerMatchCount(graph, producer, 0, consumer, 0);
        Assertions.assertTrue(matchedConsumer < 1.0, "Shared machine consumer match count must be decimal (less than 1.0), but got: " + matchedConsumer);
        Assertions.assertEquals(10.0 / 48.0, matchedConsumer, 0.0001);

        // 2. Full Auto-Ratio with integerCounts = true must preserve consumer's decimal count because it is in a shared frame
        graph.addConnection(producer.getId(), 0, consumer.getId(), 0);
        com.gtceu.calcboard.api.solver.FlowGraphSolver.autoRatioFromAnchor(graph, producer, true);

        Assertions.assertEquals(10.0 / 48.0, consumer.getMachineCount(), 0.0001, "Shared machine frame node count must stay decimal!");
    }

    @Test
    public void testNBTSerializationRoundtrip() {
        CanvasGroupFrame frame = new CanvasGroupFrame("shared-123", "My Shared Cutter", CanvasGroupFrame.COLOR_EMERALD, 10, 20, 200, 150);
        frame.setSharedMachineFrame(true);
        frame.addNode("node-a");
        frame.addNode("node-b");

        CompoundTag tag = frame.serializeNBT();
        Assertions.assertTrue(tag.getBoolean("isSharedMachineFrame"));

        CanvasGroupFrame deserialized = CanvasGroupFrame.deserializeNBT(tag);
        Assertions.assertTrue(deserialized.isSharedMachineFrame());
        Assertions.assertEquals("shared-123", deserialized.getId());
        Assertions.assertEquals("My Shared Cutter", deserialized.getTitle());
        Assertions.assertEquals(2, deserialized.getContainedNodeIds().size());
    }
}
