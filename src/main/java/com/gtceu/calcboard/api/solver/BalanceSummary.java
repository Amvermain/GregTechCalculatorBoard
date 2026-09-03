package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import java.util.Map;

public record BalanceSummary(
    double totalEUt,
    double totalSU,
    double totalFE,
    GTVoltageTier highestVoltageTier,
    int totalMachineCount,
    Map<String, Integer> machineBreakdown,
    Map<IngredientStack, Double> rawInputs,
    Map<IngredientStack, Double> netOutputs,
    Map<IngredientStack, Double> fullyBalanced,
    Map<IngredientStack, Double> totalProduction,
    Map<IngredientStack, Double> totalConsumption,
    Map<IngredientStack, Double> voidedOutputs,
    long totalFusionStartupEU,
    Map<Integer, Integer> fusionTierCounts,
    Map<Integer, Long> fusionTierStartupEU
) {
    public BalanceSummary(
            double totalEUt,
            double totalSU,
            double totalFE,
            GTVoltageTier highestVoltageTier,
            int totalMachineCount,
            Map<String, Integer> machineBreakdown,
            Map<IngredientStack, Double> rawInputs,
            Map<IngredientStack, Double> netOutputs,
            Map<IngredientStack, Double> fullyBalanced,
            Map<IngredientStack, Double> totalProduction,
            Map<IngredientStack, Double> totalConsumption,
            long totalFusionStartupEU,
            Map<Integer, Integer> fusionTierCounts,
            Map<Integer, Long> fusionTierStartupEU
    ) {
        this(totalEUt, totalSU, totalFE, highestVoltageTier, totalMachineCount, machineBreakdown, rawInputs, netOutputs, fullyBalanced, totalProduction, totalConsumption, Map.of(), totalFusionStartupEU, fusionTierCounts, fusionTierStartupEU);
    }

    public BalanceSummary(
            double totalEUt,
            double totalSU,
            double totalFE,
            GTVoltageTier highestVoltageTier,
            int totalMachineCount,
            Map<String, Integer> machineBreakdown,
            Map<IngredientStack, Double> rawInputs,
            Map<IngredientStack, Double> netOutputs,
            Map<IngredientStack, Double> fullyBalanced,
            Map<IngredientStack, Double> totalProduction,
            Map<IngredientStack, Double> totalConsumption
    ) {
        this(totalEUt, totalSU, totalFE, highestVoltageTier, totalMachineCount, machineBreakdown, rawInputs, netOutputs, fullyBalanced, totalProduction, totalConsumption, Map.of(), 0L, Map.of(), Map.of());
    }

    public BalanceSummary(
            double totalEUt,
            GTVoltageTier highestVoltageTier,
            int totalMachineCount,
            Map<String, Integer> machineBreakdown,
            Map<IngredientStack, Double> rawInputs,
            Map<IngredientStack, Double> netOutputs,
            Map<IngredientStack, Double> fullyBalanced,
            Map<IngredientStack, Double> totalProduction,
            Map<IngredientStack, Double> totalConsumption
    ) {
        this(totalEUt, 0.0, 0.0, highestVoltageTier, totalMachineCount, machineBreakdown, rawInputs, netOutputs, fullyBalanced, totalProduction, totalConsumption, Map.of(), 0L, Map.of(), Map.of());
    }

    public boolean hasDeficits() {
        return !rawInputs.isEmpty();
    }

    public boolean hasOutputs() {
        return !netOutputs.isEmpty();
    }

    public boolean hasVoidedOutputs() {
        return voidedOutputs != null && !voidedOutputs.isEmpty();
    }
}


