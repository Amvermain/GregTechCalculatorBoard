package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit tests verifying BatchRunSolver duration, resource scaling, energy integration,
 * and boundary edge cases for finite batch run calculations.
 */
public class BatchRunSolverTest {

    private IngredientStack methanol;
    private IngredientStack water;
    private IngredientStack oxygen;
    private IngredientStack rocketFuel;
    private IngredientStack carbonDioxide;
    private BalanceSummary sampleSummary;

    @BeforeEach
    public void setUp() {
        methanol = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:methanol"), "Methanol", 1000.0);
        water = IngredientStack.fluid(ResourceLocation.tryParse("minecraft:water"), "Water", 1000.0);
        oxygen = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:oxygen"), "Oxygen", 1000.0);
        rocketFuel = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:rocket_fuel"), "Rocket Fuel", 1000.0);
        carbonDioxide = IngredientStack.fluid(ResourceLocation.tryParse("gtceu:carbon_dioxide"), "Carbon Dioxide", 1000.0);

        Map<IngredientStack, Double> rawInputs = new LinkedHashMap<>();
        rawInputs.put(methanol, 320.0);
        rawInputs.put(water, 1000.0);
        rawInputs.put(oxygen, 500.0);

        Map<IngredientStack, Double> netOutputs = new LinkedHashMap<>();
        netOutputs.put(rocketFuel, 160.0);

        Map<IngredientStack, Double> voidedOutputs = new LinkedHashMap<>();
        voidedOutputs.put(carbonDioxide, 80.0);

        sampleSummary = new BalanceSummary(
                512.0,
                2048.0,
                100.0,
                GTVoltageTier.MV,
                4,
                Map.of("Chemical Reactor", 2, "Centrifuge", 2),
                rawInputs,
                netOutputs,
                Map.of(),
                Map.of(),
                Map.of(),
                voidedOutputs,
                0L,
                Map.of(),
                Map.of()
        );
    }

    @Test
    public void testInputDrivenModeCalculations() {
        double batchAmount = 64_000.0;
        BatchRunResult result = BatchRunSolver.calculate(sampleSummary, methanol, batchAmount, true);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(methanol, result.referenceIngredient());
        Assertions.assertEquals(64_000.0, result.referenceAmount(), 1e-6);
        Assertions.assertTrue(result.isInputReference());
        Assertions.assertFalse(result.isInfinite());

        Assertions.assertEquals(200.0, result.durationSeconds(), 1e-6);
        Assertions.assertEquals(4_000L, result.durationTicks());

        Map<IngredientStack, Double> inputs = result.requiredInputs();
        Assertions.assertEquals(3, inputs.size());
        Assertions.assertEquals(64_000.0, inputs.get(methanol), 1e-6);
        Assertions.assertEquals(200_000.0, inputs.get(water), 1e-6);
        Assertions.assertEquals(100_000.0, inputs.get(oxygen), 1e-6);

        Map<IngredientStack, Double> outputs = result.producedOutputs();
        Assertions.assertEquals(1, outputs.size());
        Assertions.assertEquals(32_000.0, outputs.get(rocketFuel), 1e-6);

        Map<IngredientStack, Double> voided = result.voidedOutputs();
        Assertions.assertEquals(1, voided.size());
        Assertions.assertEquals(16_000.0, voided.get(carbonDioxide), 1e-6);

        Assertions.assertEquals(2_048_000.0, result.totalEnergyEU(), 1e-6);
        Assertions.assertEquals(400_000.0, result.totalEnergyFE(), 1e-6);
        Assertions.assertEquals(409_600.0, result.totalEnergySU(), 1e-6);
        Assertions.assertEquals(GTVoltageTier.MV, result.highestVoltageTier());
        Assertions.assertEquals(4, result.totalMachineCount());
    }

    @Test
    public void testOutputDrivenModeCalculations() {
        double targetAmount = 10_000.0;
        BatchRunResult result = BatchRunSolver.calculate(sampleSummary, rocketFuel, targetAmount, false);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(rocketFuel, result.referenceIngredient());
        Assertions.assertEquals(10_000.0, result.referenceAmount(), 1e-6);
        Assertions.assertFalse(result.isInputReference());
        Assertions.assertFalse(result.isInfinite());

        Assertions.assertEquals(62.5, result.durationSeconds(), 1e-6);
        Assertions.assertEquals(1_250L, result.durationTicks());

        Map<IngredientStack, Double> outputs = result.producedOutputs();
        Assertions.assertEquals(10_000.0, outputs.get(rocketFuel), 1e-6);

        Map<IngredientStack, Double> inputs = result.requiredInputs();
        Assertions.assertEquals(20_000.0, inputs.get(methanol), 1e-6);
        Assertions.assertEquals(62_500.0, inputs.get(water), 1e-6);
        Assertions.assertEquals(31_250.0, inputs.get(oxygen), 1e-6);

        Map<IngredientStack, Double> voided = result.voidedOutputs();
        Assertions.assertEquals(5_000.0, voided.get(carbonDioxide), 1e-6);

        Assertions.assertEquals(640_000.0, result.totalEnergyEU(), 1e-6);
        Assertions.assertEquals(125_000.0, result.totalEnergyFE(), 1e-6);
        Assertions.assertEquals(128_000.0, result.totalEnergySU(), 1e-6);
    }

    @Test
    public void testAlternativeIngredientMatch() {
        IngredientStack aliasedMethanol = IngredientStack.fluid(
                ResourceLocation.tryParse("gtceu:synthetic_methanol"), "Synthetic Methanol", 1000.0
        );
        aliasedMethanol.addAlternative(ResourceLocation.tryParse("gtceu:methanol"));

        BatchRunResult result = BatchRunSolver.calculate(sampleSummary, aliasedMethanol, 32_000.0, true);
        Assertions.assertEquals(100.0, result.durationSeconds(), 1e-6);
        Assertions.assertEquals(2_000L, result.durationTicks());
    }

    @Test
    public void testGeneratorEnergyAccumulation() {
        BalanceSummary generatorSummary = new BalanceSummary(
                -256.0,
                0.0,
                0.0,
                GTVoltageTier.LV,
                1,
                Map.of("Diesel Generator", 1),
                Map.of(methanol, 10.0),
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );

        BatchRunResult result = BatchRunSolver.calculate(generatorSummary, methanol, 100.0, true);
        Assertions.assertEquals(10.0, result.durationSeconds(), 1e-6);
        Assertions.assertEquals(-51_200.0, result.totalEnergyEU(), 1e-6);
    }

    @Test
    public void testZeroAndNegativeAmounts() {
        BatchRunResult zeroResult = BatchRunSolver.calculate(sampleSummary, methanol, 0.0, true);
        Assertions.assertEquals(0.0, zeroResult.durationSeconds());
        Assertions.assertEquals(0L, zeroResult.durationTicks());
        Assertions.assertTrue(zeroResult.requiredInputs().isEmpty());

        BatchRunResult negativeResult = BatchRunSolver.calculate(sampleSummary, methanol, -500.0, true);
        Assertions.assertEquals(0.0, negativeResult.durationSeconds());
        Assertions.assertEquals(0L, negativeResult.durationTicks());
        Assertions.assertTrue(negativeResult.requiredInputs().isEmpty());
    }

    @Test
    public void testNullInputsDefenses() {
        BatchRunResult nullSummaryResult = BatchRunSolver.calculate(null, methanol, 100.0, true);
        Assertions.assertEquals(0.0, nullSummaryResult.durationSeconds());
        Assertions.assertEquals(0L, nullSummaryResult.durationTicks());

        BatchRunResult nullRefResult = BatchRunSolver.calculate(sampleSummary, null, 100.0, true);
        Assertions.assertEquals(0.0, nullRefResult.durationSeconds());
        Assertions.assertEquals(0L, nullRefResult.durationTicks());
    }

    @Test
    public void testIngredientNotInSummary() {
        IngredientStack unknown = IngredientStack.item(ResourceLocation.tryParse("minecraft:diamond"), "Diamond", 1.0);
        BatchRunResult result = BatchRunSolver.calculate(sampleSummary, unknown, 64.0, true);

        Assertions.assertTrue(result.isInfinite());
        Assertions.assertEquals(Long.MAX_VALUE, result.durationTicks());
        Assertions.assertTrue(result.requiredInputs().isEmpty());
        Assertions.assertTrue(result.producedOutputs().isEmpty());
        Assertions.assertEquals(0.0, result.totalEnergyEU());
    }

    @Test
    public void testNaNAndInfinityAmounts() {
        BatchRunResult nanResult = BatchRunSolver.calculate(sampleSummary, methanol, Double.NaN, true);
        Assertions.assertEquals(0.0, nanResult.durationSeconds());
        Assertions.assertEquals(0L, nanResult.durationTicks());
        Assertions.assertFalse(nanResult.isInfinite());
        Assertions.assertTrue(nanResult.requiredInputs().isEmpty());

        BatchRunResult infResult = BatchRunSolver.calculate(sampleSummary, methanol, Double.POSITIVE_INFINITY, true);
        Assertions.assertEquals(0.0, infResult.durationSeconds());
        Assertions.assertEquals(0L, infResult.durationTicks());
        Assertions.assertTrue(infResult.requiredInputs().isEmpty());

        BatchRunResult negInfResult = BatchRunSolver.calculate(sampleSummary, methanol, Double.NEGATIVE_INFINITY, true);
        Assertions.assertEquals(0.0, negInfResult.durationSeconds());
        Assertions.assertEquals(0L, negInfResult.durationTicks());
        Assertions.assertTrue(negInfResult.requiredInputs().isEmpty());
    }

    @Test
    public void testMultipleAlternativeAliasesScaling() {
        IngredientStack ironA = IngredientStack.item(ResourceLocation.tryParse("forge:ingots/iron"), "Iron Ingot A", 1.0);
        IngredientStack ironB = IngredientStack.item(ResourceLocation.tryParse("c:iron_ingots"), "Iron Ingot B", 1.0);
        ironA.addAlternative(ResourceLocation.tryParse("c:iron_ingots"));
        ironB.addAlternative(ResourceLocation.tryParse("forge:ingots/iron"));

        Map<IngredientStack, Double> rawInputs = new LinkedHashMap<>();
        rawInputs.put(ironA, 10.0);
        rawInputs.put(ironB, 5.0);

        BalanceSummary multiSummary = new BalanceSummary(
                128.0,
                0.0,
                0.0,
                GTVoltageTier.LV,
                1,
                Map.of("Compressor", 1),
                rawInputs,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );

        BatchRunResult result = BatchRunSolver.calculate(multiSummary, ironA, 100.0, true);
        Assertions.assertEquals(10.0, result.durationSeconds(), 1e-6);
        Assertions.assertEquals(100.0, result.requiredInputs().get(ironA), 1e-6);
        Assertions.assertEquals(50.0, result.requiredInputs().get(ironB), 1e-6);
    }

    @Test
    public void testZeroRateReferenceMaterial() {
        Map<IngredientStack, Double> rawInputs = new LinkedHashMap<>();
        rawInputs.put(methanol, 0.0);

        BalanceSummary zeroSummary = new BalanceSummary(
                0.0,
                0.0,
                0.0,
                GTVoltageTier.ULV,
                1,
                Map.of("Tank", 1),
                rawInputs,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of()
        );

        BatchRunResult result = BatchRunSolver.calculate(zeroSummary, methanol, 1000.0, true);
        Assertions.assertTrue(result.isInfinite());
        Assertions.assertEquals(Long.MAX_VALUE, result.durationTicks());
        Assertions.assertTrue(result.requiredInputs().isEmpty());
    }
}
