package com.gtceu.calcboard.api.model.role;

import com.gtceu.calcboard.api.type.EnergyType;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot capturing calculated throughput, power, duration, and port rates for lock-free rendering.
 */
public record NodeCalculationSnapshot(
    String nodeId,
    NodeRoleType roleType,
    double nominalCyclesPerSecond,
    double effectiveCyclesPerSecond,
    double singleMachinePower,
    double totalPower,
    double effectiveTotalPower,
    double durationSeconds,
    int totalParallel,
    EnergyType energyType,
    boolean isOperational,
    boolean isStarved,
    List<Component> operationalWarnings,
    Map<Integer, Double> inputPortRates,
    Map<Integer, Double> outputPortRates,
    Map<Integer, Double> effectiveInputChances,
    Map<Integer, Double> effectiveOutputChances
) {
    public static final NodeCalculationSnapshot EMPTY = new NodeCalculationSnapshot(
        "", NodeRoleType.MACHINE, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 1,
        EnergyType.ELECTRIC_EU, true, false, List.of(),
        Map.of(), Map.of(), Map.of(), Map.of()
    );

    public NodeCalculationSnapshot {
        operationalWarnings = operationalWarnings != null ? List.copyOf(operationalWarnings) : List.of();
        inputPortRates = inputPortRates != null ? Map.copyOf(inputPortRates) : Map.of();
        outputPortRates = outputPortRates != null ? Map.copyOf(outputPortRates) : Map.of();
        effectiveInputChances = effectiveInputChances != null ? Map.copyOf(effectiveInputChances) : Map.of();
        effectiveOutputChances = effectiveOutputChances != null ? Map.copyOf(effectiveOutputChances) : Map.of();
    }
}
