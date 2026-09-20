package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable domain record representing the projected outcome of a finite batch process run.
 * Contains duration, raw material requirements, output yields, voided byproducts, and total energy deltas.
 */
public record BatchRunResult(
        IngredientStack referenceIngredient,
        double referenceAmount,
        boolean isInputReference,
        double durationSeconds,
        long durationTicks,
        Map<IngredientStack, Double> requiredInputs,
        Map<IngredientStack, Double> producedOutputs,
        Map<IngredientStack, Double> voidedOutputs,
        double totalEnergyEU,
        double totalEnergyFE,
        double totalEnergySU,
        GTVoltageTier highestVoltageTier,
        int totalMachineCount
) {
    public static BatchRunResult empty() {
        return new BatchRunResult(
                null,
                0.0,
                true,
                0.0,
                0L,
                Collections.emptyMap(),
                Collections.emptyMap(),
                Collections.emptyMap(),
                0.0,
                0.0,
                0.0,
                GTVoltageTier.ULV,
                0
        );
    }

    public boolean isInfinite() {
        return Double.isInfinite(durationSeconds) || Double.isNaN(durationSeconds);
    }

    public boolean hasRequiredInputs() {
        return requiredInputs != null && !requiredInputs.isEmpty();
    }

    public boolean hasProducedOutputs() {
        return producedOutputs != null && !producedOutputs.isEmpty();
    }

    public boolean hasVoidedOutputs() {
        return voidedOutputs != null && !voidedOutputs.isEmpty();
    }
}
