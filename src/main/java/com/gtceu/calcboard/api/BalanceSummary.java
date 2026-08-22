package com.gtceu.calcboard.api;

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
    Map<IngredientStack, Double> totalConsumption
) {
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
        this(totalEUt, 0.0, 0.0, highestVoltageTier, totalMachineCount, machineBreakdown, rawInputs, netOutputs, fullyBalanced, totalProduction, totalConsumption);
    }

    public boolean hasDeficits() {
        return !rawInputs.isEmpty();
    }

    public boolean hasOutputs() {
        return !netOutputs.isEmpty();
    }
}
