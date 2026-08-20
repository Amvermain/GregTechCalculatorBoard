package com.gtceu.calcboard.api;

import java.util.*;

/**
 * Data transfer record representing the aggregated net balance and power statistics across multiple board pages.
 */
public record GlobalBalanceSummary(
    List<BoardPage> selectedPages,
    double totalGeneratedEUt,
    double totalConsumedEUt,
    double netEUt,
    GTVoltageTier highestVoltageTier,
    int totalMachineCount,
    Map<String, Integer> machineBreakdown,
    Map<IngredientStack, Double> totalProduction,
    Map<IngredientStack, Double> totalConsumption,
    Map<IngredientStack, Double> netOutputs,
    Map<IngredientStack, Double> rawInputs,
    Map<IngredientStack, Double> fullyBalanced,
    Map<IngredientStack, List<PageContribution>> itemContributions
) {
    /**
     * Individual page contribution breakdown for a specific ingredient.
     */
    public record PageContribution(
        String pageId,
        String pageName,
        double producedRate,
        double consumedRate
    ) {
        public double netRate() {
            return producedRate - consumedRate;
        }

        public boolean hasProduction() {
            return producedRate > 0.0001;
        }

        public boolean hasConsumption() {
            return consumedRate > 0.0001;
        }
    }

    public static GlobalBalanceSummary empty() {
        return new GlobalBalanceSummary(
            Collections.emptyList(),
            0.0,
            0.0,
            0.0,
            GTVoltageTier.ULV,
            0,
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap(),
            Collections.emptyMap()
        );
    }

    public boolean hasDeficits() {
        return !rawInputs.isEmpty();
    }

    public boolean hasOutputs() {
        return !netOutputs.isEmpty();
    }

    public boolean hasBalanced() {
        return !fullyBalanced.isEmpty();
    }

    public boolean isPowerSurplus() {
        return totalGeneratedEUt >= totalConsumedEUt;
    }

    public boolean isPowerDeficit() {
        return totalConsumedEUt > totalGeneratedEUt;
    }

    public double getNetPower() {
        return totalGeneratedEUt - totalConsumedEUt;
    }

    public List<PageContribution> getContributionsFor(IngredientStack stack) {
        if (stack == null) return Collections.emptyList();
        for (Map.Entry<IngredientStack, List<PageContribution>> entry : itemContributions.entrySet()) {
            if (entry.getKey().equals(stack) || entry.getKey().matchesOrAlternative(stack) || stack.matchesOrAlternative(entry.getKey())) {
                return entry.getValue();
            }
        }
        return Collections.emptyList();
    }
}
