package com.gtceu.calcboard.api.model;

import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.spi.ModAdapterRegistry;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Dedicated calculator for recipe node ingredient flow rates, single machine yields,
 * and effective input/output integration over overclocked cycles.
 */
public final class NodeRateCalculator {

    private NodeRateCalculator() {}

    public static Map<IngredientStack, Double> calculateInputRates(RecipeNode node) {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        if (node == null) return rates;

        double cps = node.getCyclesPerSecond();
        for (int i = 0; i < node.getInputs().size(); i++) {
            IngredientStack in = node.getInputs().get(i);
            double r;
            if (in.isStressUnit()) {
                r = computeStressRate(node, in, false, false);
            } else {
                double amount = in.getAmount() * getEffectiveInputChance(node, i);
                r = amount * cps;
            }
            r = ModAdapterRegistry.getAdapterForNode(node).computeEffectiveIngredientRate(node, in, true, r);
            boolean merged = false;
            for (Map.Entry<IngredientStack, Double> entry : rates.entrySet()) {
                if (entry.getKey().equals(in)) {
                    entry.setValue(entry.getValue() + r);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                rates.put(in, r);
            }
        }
        try {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.gtceu.calcboard.api.event.RecipeNodeEvent.PostCalculation(node, rates, Collections.emptyMap()));
        } catch (Throwable ignored) {}
        return rates;
    }

    public static double getInputSlotRate(RecipeNode node, int index, boolean effective) {
        if (node == null || index < 0 || index >= node.getInputs().size()) return 0.0;
        if (node.isReroute()) {
            return node.isFixedDrain() ? node.getExternalDrainRate() : 0.0;
        }
        IngredientStack in = node.getInputs().get(index);
        double r;
        if (in.isStressUnit()) {
            r = computeStressRate(node, in, effective, !effective);
        } else {
            double amount = in.getAmount() * getEffectiveInputChance(node, index);
            double cps = effective ? node.getEffectiveCyclesPerSecond() : node.getNominalCyclesPerSecond();
            r = amount * cps;
        }
        return ModAdapterRegistry.getAdapterForNode(node).computeEffectiveIngredientRate(node, in, true, r);
    }

    public static double getEffectiveInputChance(RecipeNode node, int inputIndex) {
        if (node == null || inputIndex < 0 || inputIndex >= node.getInputs().size()) return 0.0;
        IngredientStack in = node.getInputs().get(inputIndex);
        if (in.getChance() >= 1.0 && in.getTierChanceBoost() >= 0.0) return 1.0;

        double baseChance = in.getEffectiveChance(node.getTierDelta());
        return ModAdapterRegistry.getAdapterForNode(node).computeEffectiveInputChance(node, inputIndex, baseChance);
    }

    public static double getEffectiveOutputChance(RecipeNode node, int outputIndex) {
        if (node == null || outputIndex < 0 || outputIndex >= node.getOutputs().size()) return 0.0;
        IngredientStack out = node.getOutputs().get(outputIndex);
        if (outputIndex == 0 && out.getChance() >= 1.0) return 1.0;

        double baseChance = out.getEffectiveChance(node.getTierDelta());
        return ModAdapterRegistry.getAdapterForNode(node).computeEffectiveOutputChance(node, outputIndex, baseChance);
    }

    public static double getOutputSlotRate(RecipeNode node, int index, boolean effective) {
        if (node == null || index < 0 || index >= node.getOutputs().size()) return 0.0;
        if (node.isReroute()) {
            return node.isExternalSupply() ? node.getExternalSupplyRate() : 0.0;
        }
        IngredientStack out = node.getOutputs().get(index);
        double r;
        if (out.isStressUnit()) {
            r = computeStressRate(node, out, effective, false);
        } else {
            double amount = out.getAmount() * getEffectiveOutputChance(node, index);
            double cps = effective ? node.getEffectiveCyclesPerSecond() : node.getCyclesPerSecond();
            r = amount * cps;
        }
        return ModAdapterRegistry.getAdapterForNode(node).computeEffectiveIngredientRate(node, out, false, r);
    }

    public static double getSingleOutputExpectedAmount(RecipeNode node, int index) {
        if (node == null || index < 0 || index >= node.getOutputs().size()) return 0.0;
        return node.getOutputs().get(index).getAmount() * getEffectiveOutputChance(node, index);
    }

    public static Map<IngredientStack, Double> calculateOutputRates(RecipeNode node) {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        if (node == null) return rates;
        if (node.isReroute()) {
            if (node.isExternalSupply() && node.getExternalSupplyRate() > 0.0 && node.getRerouteIngredient() != null) {
                rates.put(node.getRerouteIngredient(), node.getExternalSupplyRate());
            }
            return rates;
        }

        double cps = node.getCyclesPerSecond();
        for (int i = 0; i < node.getOutputs().size(); i++) {
            IngredientStack out = node.getOutputs().get(i);
            double r;
            if (out.isStressUnit()) {
                r = computeStressRate(node, out, false, false);
            } else {
                double amount = out.getAmount() * getEffectiveOutputChance(node, i);
                r = amount * cps;
            }
            r = ModAdapterRegistry.getAdapterForNode(node).computeEffectiveIngredientRate(node, out, false, r);
            boolean merged = false;
            for (Map.Entry<IngredientStack, Double> entry : rates.entrySet()) {
                if (entry.getKey().equals(out)) {
                    entry.setValue(entry.getValue() + r);
                    merged = true;
                    break;
                }
            }
            if (!merged) {
                rates.put(out, r);
            }
        }
        try {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.gtceu.calcboard.api.event.RecipeNodeEvent.PostCalculation(node, Collections.emptyMap(), rates));
        } catch (Throwable ignored) {}
        return rates;
    }

    public static Map<IngredientStack, Double> calculateEffectiveInputRates(RecipeNode node) {
        return calculateEffectiveInputRates(node, true);
    }

    public static Map<IngredientStack, Double> calculateEffectiveInputRates(RecipeNode node, boolean postEvent) {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        if (node == null) return rates;
        if (node.isReroute()) {
            if (node.isFixedDrain() && node.getExternalDrainRate() > 0.0 && node.getRerouteIngredient() != null) {
                rates.put(node.getRerouteIngredient(), node.getExternalDrainRate());
            }
            return rates;
        }

        double cps = node.getEffectiveCyclesPerSecond();
        for (int i = 0; i < node.getInputs().size(); i++) {
            IngredientStack in = node.getInputs().get(i);
            double r;
            if (in.isStressUnit()) {
                r = computeStressRate(node, in, true, false);
            } else {
                double amount = in.getAmount() * getEffectiveInputChance(node, i);
                r = amount * cps;
            }
            r = ModAdapterRegistry.getAdapterForNode(node).computeEffectiveIngredientRate(node, in, true, r);
            rates.merge(in, r, Double::sum);
        }
        if (postEvent) {
            try {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.gtceu.calcboard.api.event.RecipeNodeEvent.PostCalculation(node, rates, Collections.emptyMap()));
            } catch (Throwable ignored) {}
        }
        return rates;
    }

    public static Map<IngredientStack, Double> calculateEffectiveOutputRates(RecipeNode node) {
        return calculateEffectiveOutputRates(node, true);
    }

    public static Map<IngredientStack, Double> calculateEffectiveOutputRates(RecipeNode node, boolean postEvent) {
        Map<IngredientStack, Double> rates = new LinkedHashMap<>();
        if (node == null) return rates;
        if (node.isReroute()) {
            if (node.isExternalSupply() && node.getExternalSupplyRate() > 0.0 && node.getRerouteIngredient() != null) {
                rates.put(node.getRerouteIngredient(), node.getExternalSupplyRate());
            }
            return rates;
        }

        double cps = node.getEffectiveCyclesPerSecond();
        for (int i = 0; i < node.getOutputs().size(); i++) {
            IngredientStack out = node.getOutputs().get(i);
            double r;
            if (out.isStressUnit()) {
                r = computeStressRate(node, out, true, false);
            } else {
                double amount = out.getAmount() * getEffectiveOutputChance(node, i);
                r = amount * cps;
            }
            r = ModAdapterRegistry.getAdapterForNode(node).computeEffectiveIngredientRate(node, out, false, r);
            rates.merge(out, r, Double::sum);
        }
        if (postEvent) {
            try {
                net.minecraftforge.common.MinecraftForge.EVENT_BUS.post(new com.gtceu.calcboard.api.event.RecipeNodeEvent.PostCalculation(node, Collections.emptyMap(), rates));
            } catch (Throwable ignored) {}
        }
        return rates;
    }

    public static double calculateSingleMachineOutputRate(RecipeNode node, IngredientStack out) {
        if (node == null || out == null || !node.isOperational()) return 0.0;
        double baseRate;
        if (out.isStressUnit()) {
            baseRate = (node.getEnergyType() == EnergyType.KINETIC_SU)
                    ? node.getSingleMachineEUt()
                    : (out.getAmount() * node.getTotalParallel());
        } else {
            double singleCps = node.getOverclockResult().getCyclesPerSecond() * node.getTotalParallel();
            int idx = node.getOutputs().indexOf(out);
            double effChance = idx >= 0 ? getEffectiveOutputChance(node, idx) : out.getEffectiveChance(node.getTierDelta());
            baseRate = out.getAmount() * effChance * singleCps;
        }
        return ModAdapterRegistry.getAdapterForNode(node).computeSingleMachineIngredientRate(node, out, false, baseRate);
    }

    public static double calculateSingleMachineInputRate(RecipeNode node, IngredientStack in) {
        if (node == null || in == null) return 0.0;
        double baseRate;
        if (in.isStressUnit()) {
            baseRate = (node.getEnergyType() == EnergyType.KINETIC_SU)
                    ? node.getSingleMachineEUt()
                    : (in.getAmount() * node.getTotalParallel());
        } else {
            int idx = node.getInputs().indexOf(in);
            double effChance = idx >= 0 ? getEffectiveInputChance(node, idx) : in.getEffectiveChance(node.getTierDelta());
            double singleCps = node.getOverclockResult().getCyclesPerSecond() * node.getTotalParallel();
            baseRate = in.getAmount() * effChance * singleCps;
        }
        return ModAdapterRegistry.getAdapterForNode(node).computeSingleMachineIngredientRate(node, in, true, baseRate);
    }

    private static double computeStressRate(RecipeNode node, IngredientStack stack, boolean effective, boolean nominal) {
        if (node == null || stack == null) return 0.0;
        if (!nominal && !node.isOperational()) return 0.0;

        double eff = effective ? node.getEfficiency() : 1.0;
        if (node.getEnergyType() == EnergyType.KINETIC_SU) {
            double total = nominal ? (node.getSingleMachineEUt() * node.getMachineCount()) : node.getTotalEUt();
            return total * eff;
        }
        return stack.getAmount() * node.getMachineCount() * node.getTotalParallel() * eff;
    }
}
