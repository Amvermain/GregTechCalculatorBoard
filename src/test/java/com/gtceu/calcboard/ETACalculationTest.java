package com.gtceu.calcboard;

import com.gtceu.calcboard.api.*;
import com.gtceu.calcboard.client.gui.FormatUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Unit tests verifying RFC-005: Target Batch Production Duration (ETA) Engine,
 * formatting, total energy & raw material accumulation, and NBT serialization.
 */
public class ETACalculationTest {

    @Test
    public void testETAWithSingleProducerAndReroute() {
        FlowGraph graph = new FlowGraph();

        // Chemical Reactor: 15.00s per cycle (300 ticks), 1 output per cycle -> 1/15 = 0.066666... /s
        RecipeNode reactor = RecipeNode.create("Chemical Reactor", 300.0, 30.0, GTVoltageTier.LV);
        IngredientStack woodPlate = IngredientStack.item(new ResourceLocation("gtceu:wood_plate"), "Wood Plate", 1.0);
        IngredientStack sulfuricAcid = IngredientStack.fluid(new ResourceLocation("gtceu:sulfuric_acid"), "Sulfuric Acid", 100.0);
        reactor.getInputs().add(sulfuricAcid);
        reactor.getOutputs().add(woodPlate);
        reactor.setMachineCount(1.0);
        graph.addNode(reactor);

        // Target Reroute Node with 100x goal
        RecipeNode targetReroute = RecipeNode.createReroute(200.0, 100.0);
        targetReroute.bindRerouteIngredient(woodPlate);
        targetReroute.setTargetBatchAmount(100.0);
        graph.addNode(targetReroute);

        // Connect reactor output -> targetReroute input
        graph.addConnection(reactor.getId(), 0, targetReroute.getId(), 0);

        double netRate = ProductionETACalculator.calculateNetInflowRate(graph, targetReroute, 0);
        Assertions.assertEquals(1.0 / 15.0, netRate, 0.0001);

        double etaSeconds = ProductionETACalculator.calculateETA(targetReroute.getTargetBatchAmount(), netRate);
        Assertions.assertEquals(1500.0, etaSeconds, 0.01); // 100 / (1/15) = 1500s = 25m

        String formatted = FormatUtil.formatETA(etaSeconds);
        Assertions.assertEquals("25m", formatted);

        // If rate is ~0.067/s -> 100 / 0.067 = 1492.537s -> "24m 53s"
        double customEta = ProductionETACalculator.calculateETA(100.0, 0.067);
        Assertions.assertEquals("24m 53s", FormatUtil.formatETA(customEta));
    }

    @Test
    public void testETAFormatting() {
        Assertions.assertEquals("∞", FormatUtil.formatETA(0.0));
        Assertions.assertEquals("∞", FormatUtil.formatETA(-10.0));
        Assertions.assertEquals("∞", FormatUtil.formatETA(Double.POSITIVE_INFINITY));
        Assertions.assertEquals("∞", FormatUtil.formatETA(Double.NaN));

        Assertions.assertEquals("< 1s", FormatUtil.formatETA(0.4));
        Assertions.assertEquals("< 1s", FormatUtil.formatETA(0.99));

        Assertions.assertEquals("1.5s", FormatUtil.formatETA(1.5));
        Assertions.assertEquals("24.5s", FormatUtil.formatETA(24.5));
        Assertions.assertEquals("52s", FormatUtil.formatETA(52.0));

        Assertions.assertEquals("1m 30s", FormatUtil.formatETA(90.0));
        Assertions.assertEquals("24m 52s", FormatUtil.formatETA(1492.0));
        Assertions.assertEquals("45m", FormatUtil.formatETA(2700.0));

        Assertions.assertEquals("1h 30m", FormatUtil.formatETA(5400.0));
        Assertions.assertEquals("8h 15m", FormatUtil.formatETA(29700.0));

        Assertions.assertEquals("1d 1h", FormatUtil.formatETA(90000.0));
        Assertions.assertEquals("3d 12h", FormatUtil.formatETA(302400.0));
    }

    @Test
    public void testBatchAmountFormatting() {
        Assertions.assertEquals("0", FormatUtil.formatBatchAmount(0.0, false));
        Assertions.assertEquals("100x", FormatUtil.formatBatchAmount(100.0, false));
        Assertions.assertEquals("1,000x", FormatUtil.formatBatchAmount(1000.0, false));
        Assertions.assertEquals("64,000x", FormatUtil.formatBatchAmount(64000.0, false));
        Assertions.assertEquals("1Mx", FormatUtil.formatBatchAmount(1000000.0, false));

        Assertions.assertEquals("500 mB", FormatUtil.formatBatchAmount(500.0, true));
        Assertions.assertEquals("10 B", FormatUtil.formatBatchAmount(10000.0, true));
        Assertions.assertEquals("1M B", FormatUtil.formatBatchAmount(1000000000.0, true));
    }

    @Test
    public void testBatchAmountParsing() {
        // Fluid parsing with B and mB
        Assertions.assertEquals(100100.0, FormatUtil.parseBatchAmount("100.1B", true), 0.001);
        Assertions.assertEquals(100000.0, FormatUtil.parseBatchAmount("100B", true), 0.001);
        Assertions.assertEquals(100000.0, FormatUtil.parseBatchAmount("100 b", true), 0.001);
        Assertions.assertEquals(500.0, FormatUtil.parseBatchAmount("500mB", true), 0.001);
        Assertions.assertEquals(50.0, FormatUtil.parseBatchAmount("50mb", true), 0.001);
        Assertions.assertEquals(1000.0, FormatUtil.parseBatchAmount("1000mb", true), 0.001);
        Assertions.assertEquals(100113.0, FormatUtil.parseBatchAmount("100113", true), 0.001);
        Assertions.assertEquals(2500.0, FormatUtil.parseBatchAmount("2.5 B", true), 0.001);
        Assertions.assertEquals(500.0, FormatUtil.parseBatchAmount(".5B", true), 0.001);
        Assertions.assertEquals(1_000_000.0, FormatUtil.parseBatchAmount("1kB", true), 0.001);
        Assertions.assertEquals(1_000_000_000.0, FormatUtil.parseBatchAmount("1MB", true), 0.001);

        // Item parsing with stacks, x, and numbers
        Assertions.assertEquals(64.0, FormatUtil.parseBatchAmount("1st", false), 0.001);
        Assertions.assertEquals(128.0, FormatUtil.parseBatchAmount("2st", false), 0.001);
        Assertions.assertEquals(640.0, FormatUtil.parseBatchAmount("10stack", false), 0.001);
        Assertions.assertEquals(32.0, FormatUtil.parseBatchAmount("0.5stacks", false), 0.001);
        Assertions.assertEquals(100.0, FormatUtil.parseBatchAmount("100x", false), 0.001);
        Assertions.assertEquals(1000.0, FormatUtil.parseBatchAmount("1k", false), 0.001);
        Assertions.assertEquals(1500000.0, FormatUtil.parseBatchAmount("1.5M", false), 0.001);
        Assertions.assertEquals(100.0, FormatUtil.parseBatchAmount("100", false), 0.001);

        // Invalid or empty inputs
        Assertions.assertEquals(0.0, FormatUtil.parseBatchAmount("", false), 0.001);
        Assertions.assertEquals(0.0, FormatUtil.parseBatchAmount("abc", false), 0.001);
        Assertions.assertEquals(0.0, FormatUtil.parseBatchAmount(null, true), 0.001);
    }

    @Test
    public void testEditAmountFormatting() {
        Assertions.assertEquals("10B", FormatUtil.formatEditAmount(0.0, true));
        Assertions.assertEquals("100", FormatUtil.formatEditAmount(0.0, false));
        Assertions.assertEquals("100B", FormatUtil.formatEditAmount(100000.0, true));
        Assertions.assertEquals("100.1B", FormatUtil.formatEditAmount(100100.0, true));
        Assertions.assertEquals("100.113B", FormatUtil.formatEditAmount(100113.0, true));
        Assertions.assertEquals("500mB", FormatUtil.formatEditAmount(500.0, true));
        Assertions.assertEquals("64", FormatUtil.formatEditAmount(64.0, false));
        Assertions.assertEquals("100", FormatUtil.formatEditAmount(100.0, false));
    }

    @Test
    public void testBatchTotalEnergyAndRawMaterials() {
        FlowGraph graph = new FlowGraph();

        // 30 EU/t machine, 20 ticks (1s) cycle, produces 1 item/s, consumes 100 mB water/s
        RecipeNode machine = RecipeNode.create("Extractor", 20.0, 30.0, GTVoltageTier.LV);
        IngredientStack water = IngredientStack.fluid(new ResourceLocation("minecraft:water"), "Water", 100.0);
        IngredientStack rubber = IngredientStack.item(new ResourceLocation("gtceu:rubber_bar"), "Rubber", 1.0);
        machine.getInputs().add(water);
        machine.getOutputs().add(rubber);
        machine.setMachineCount(1.0);
        graph.addNode(machine);

        RecipeNode reroute = RecipeNode.createReroute(100.0, 50.0);
        reroute.bindRerouteIngredient(rubber);
        reroute.setTargetBatchAmount(60.0); // 60 items target
        graph.addNode(reroute);

        graph.addConnection(machine.getId(), 0, reroute.getId(), 0);

        // Net rate = 1.0 item/s -> ETA = 60s
        double etaSec = ProductionETACalculator.calculateETA(60.0, ProductionETACalculator.calculateNetInflowRate(graph, reroute, 0));
        Assertions.assertEquals(60.0, etaSec, 0.001);

        // Total EU = 30 EU/t * 20 t/s * 60s = 36,000 EU
        double totalEU = ProductionETACalculator.calculateTotalEnergyForBatch(graph, reroute, 60.0);
        Assertions.assertEquals(36000.0, totalEU, 0.001);

        // Total Raw Materials: 100 mB/s * 60s = 6,000 mB Water
        Map<IngredientStack, Double> rawMaterials = ProductionETACalculator.calculateTotalRawMaterialsForBatch(graph, reroute, 60.0);
        Assertions.assertEquals(1, rawMaterials.size());
        Map.Entry<IngredientStack, Double> entry = rawMaterials.entrySet().iterator().next();
        Assertions.assertEquals("Water", entry.getKey().getDisplayName());
        Assertions.assertEquals(6000.0, entry.getValue(), 0.001);
    }

    @Test
    public void testTargetBatchNbtSerialization() {
        RecipeNode node = RecipeNode.createReroute(150.0, 250.0);
        node.setTargetBatchAmount(500.0);
        node.setTargetBatchTimeSec(120.0);

        CompoundTag tag = RecipeNodeSerializer.serialize(node);
        RecipeNode deserialized = RecipeNodeSerializer.deserialize(tag);

        Assertions.assertNotNull(deserialized);
        Assertions.assertTrue(deserialized.isReroute());
        Assertions.assertEquals(500.0, deserialized.getTargetBatchAmount(), 0.001);
        Assertions.assertEquals(120.0, deserialized.getTargetBatchTimeSec(), 0.001);
        Assertions.assertTrue(deserialized.hasTargetBatch());
    }
}
