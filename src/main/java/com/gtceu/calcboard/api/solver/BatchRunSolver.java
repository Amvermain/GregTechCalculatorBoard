package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Pure headless calculation engine for computing finite batch run projections.
 * Given a steady-state balance summary and a reference ingredient target amount,
 * projects the total time required, raw material requirements, output yields, and energy deltas.
 */
public final class BatchRunSolver {

    private static final double EPSILON = 1e-9;
    private static final double TICKS_PER_SECOND = 20.0;

    private BatchRunSolver() {}

    public static BatchRunResult calculate(
            BalanceSummary summary,
            IngredientStack refStack,
            double amount,
            boolean isInputRef
    ) {
        if (summary == null || refStack == null || amount <= 0.0 || Double.isNaN(amount) || Double.isInfinite(amount)) {
            return createZeroResult(summary, refStack, amount, isInputRef);
        }

        double refRate = findReferenceRate(summary, refStack, isInputRef);
        if (refRate <= EPSILON || Double.isNaN(refRate) || Double.isInfinite(refRate)) {
            return createInfiniteResult(summary, refStack, amount, isInputRef);
        }

        double durationSeconds = amount / refRate;
        long durationTicks = Math.round(durationSeconds * TICKS_PER_SECOND);

        Map<IngredientStack, Double> requiredInputs = scaleRates(
                summary.rawInputs(), durationSeconds, isInputRef ? refStack : null, amount
        );
        Map<IngredientStack, Double> producedOutputs = scaleRates(
                summary.netOutputs(), durationSeconds, !isInputRef ? refStack : null, amount
        );
        Map<IngredientStack, Double> voidedOutputs = scaleRates(
                summary.voidedOutputs(), durationSeconds, null, 0.0
        );

        double totalEnergyEU = durationSeconds * TICKS_PER_SECOND * summary.totalEUt();
        double totalEnergyFE = durationSeconds * TICKS_PER_SECOND * summary.totalFE();
        double totalEnergySU = durationSeconds * summary.totalSU();

        GTVoltageTier highestTier = summary.highestVoltageTier() != null
                ? summary.highestVoltageTier()
                : GTVoltageTier.ULV;

        return new BatchRunResult(
                refStack,
                amount,
                isInputRef,
                durationSeconds,
                durationTicks,
                requiredInputs,
                producedOutputs,
                voidedOutputs,
                totalEnergyEU,
                totalEnergyFE,
                totalEnergySU,
                highestTier,
                summary.totalMachineCount()
        );
    }

    public static double findReferenceRate(BalanceSummary summary, IngredientStack refStack, boolean isInputRef) {
        if (summary == null || refStack == null) {
            return 0.0;
        }
        Map<IngredientStack, Double> pool = isInputRef ? summary.rawInputs() : summary.netOutputs();
        if (pool == null || pool.isEmpty()) {
            return 0.0;
        }

        Double exact = pool.get(refStack);
        if (exact != null) {
            return exact;
        }

        for (Map.Entry<IngredientStack, Double> entry : pool.entrySet()) {
            IngredientStack candidate = entry.getKey();
            if (candidate != null && matches(candidate, refStack)) {
                return entry.getValue();
            }
        }
        return 0.0;
    }

    private static boolean matches(IngredientStack a, IngredientStack b) {
        if (a == null || b == null) {
            return false;
        }
        return a.equals(b) || a.matchesOrAlternative(b) || b.matchesOrAlternative(a);
    }

    private static Map<IngredientStack, Double> scaleRates(
            Map<IngredientStack, Double> source,
            double durationSeconds,
            IngredientStack exactRefStack,
            double exactAmount
    ) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<IngredientStack, Double> result = new LinkedHashMap<>(source.size());
        boolean replacedExact = false;
        for (Map.Entry<IngredientStack, Double> entry : source.entrySet()) {
            IngredientStack stack = entry.getKey();
            if (stack == null) {
                continue;
            }
            if (!replacedExact && exactRefStack != null && matches(stack, exactRefStack)) {
                result.put(stack, exactAmount);
                replacedExact = true;
            } else {
                result.put(stack, entry.getValue() * durationSeconds);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static BatchRunResult createZeroResult(
            BalanceSummary summary,
            IngredientStack refStack,
            double amount,
            boolean isInputRef
    ) {
        GTVoltageTier tier = (summary != null && summary.highestVoltageTier() != null)
                ? summary.highestVoltageTier()
                : GTVoltageTier.ULV;
        int machineCount = summary != null ? summary.totalMachineCount() : 0;

        return new BatchRunResult(
                refStack,
                amount,
                isInputRef,
                0.0,
                0L,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                0.0,
                0.0,
                0.0,
                tier,
                machineCount
        );
    }

    private static BatchRunResult createInfiniteResult(
            BalanceSummary summary,
            IngredientStack refStack,
            double amount,
            boolean isInputRef
    ) {
        GTVoltageTier tier = (summary != null && summary.highestVoltageTier() != null)
                ? summary.highestVoltageTier()
                : GTVoltageTier.ULV;
        int machineCount = summary != null ? summary.totalMachineCount() : 0;

        return new BatchRunResult(
                refStack,
                amount,
                isInputRef,
                Double.POSITIVE_INFINITY,
                Long.MAX_VALUE,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                0.0,
                0.0,
                0.0,
                tier,
                machineCount
        );
    }
}
