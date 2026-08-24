package com.gtceu.calcboard.api;

import java.util.*;

/**
 * Pure calculation engine for aggregating multi-page process balances, cross-page flows,
 * total power production/consumption, and per-item source/sink contributions.
 */
public final class GlobalBalanceAggregator {

    private GlobalBalanceAggregator() {}

    /**
     * Aggregates process summaries across the specified collection of BoardPage instances.
     */
    public static GlobalBalanceSummary compute(Collection<BoardPage> pages) {
        if (pages == null || pages.isEmpty()) {
            return GlobalBalanceSummary.empty();
        }

        List<BoardPage> selectedPagesList = new ArrayList<>(pages);
        double totalGeneratedEUt = 0.0;
        double totalConsumedEUt = 0.0;
        GTVoltageTier highestTier = GTVoltageTier.ULV;
        int totalMachineCount = 0;
        Map<String, Integer> machineBreakdown = new LinkedHashMap<>();

        Map<IngredientStack, Double> totalProduction = new LinkedHashMap<>();
        Map<IngredientStack, Double> totalConsumption = new LinkedHashMap<>();

        // Tracks per-item page contribution records (normalized by matching IngredientStack key)
        Map<IngredientStack, List<GlobalBalanceSummary.PageContribution>> itemContributions = new LinkedHashMap<>();

        for (BoardPage page : selectedPagesList) {
            if (page == null || page.getGraph() == null) continue;

            FlowGraph graph = page.getGraph();
            BalanceSummary pageSummary = FlowGraphSolver.computeSummary(graph);

            // 1. Machine count and breakdown aggregation
            totalMachineCount += pageSummary.totalMachineCount();
            for (Map.Entry<String, Integer> entry : pageSummary.machineBreakdown().entrySet()) {
                machineBreakdown.put(entry.getKey(), machineBreakdown.getOrDefault(entry.getKey(), 0) + entry.getValue());
            }

            // 2. Voltage Tier
            if (pageSummary.highestVoltageTier() != null && pageSummary.highestVoltageTier().ordinal() > highestTier.ordinal()) {
                highestTier = pageSummary.highestVoltageTier();
            }

            // 3. Power Generation vs Consumption calculation per page
            for (RecipeNode node : graph.getNodes()) {
                double nodeEffectiveEUt = node.getEffectiveTotalEUt();
                if (node.getEnergyType() == EnergyType.ELECTRIC_FE) {
                    nodeEffectiveEUt = nodeEffectiveEUt / 4.0; // 4 RF = 1 EU
                } else if (node.getEnergyType() != EnergyType.ELECTRIC_EU) {
                    nodeEffectiveEUt = 0.0;
                }

                if (node.isGenerator()) {
                    totalGeneratedEUt += nodeEffectiveEUt;
                } else {
                    totalConsumedEUt += nodeEffectiveEUt;
                }
                if (node.getEnergyType() == EnergyType.ELECTRIC_EU && node.getTargetTier() != null && node.getTargetTier().ordinal() > highestTier.ordinal()) {
                    highestTier = node.getTargetTier();
                }
            }

            // 4. Per-page Production and Consumption aggregation
            Map<IngredientStack, Double> pageProd = pageSummary.totalProduction();
            Map<IngredientStack, Double> pageCons = pageSummary.totalConsumption();

            // Collect all unique stacks present in this page
            List<IngredientStack> pageStacks = new ArrayList<>();
            for (IngredientStack s : pageProd.keySet()) {
                if (!containsMatching(pageStacks, s)) pageStacks.add(s);
            }
            for (IngredientStack s : pageCons.keySet()) {
                if (!containsMatching(pageStacks, s)) pageStacks.add(s);
            }

            for (IngredientStack stack : pageStacks) {
                double prodRate = findRate(pageProd, stack);
                double consRate = findRate(pageCons, stack);

                if (prodRate > 0.0001) {
                    mergeRate(totalProduction, stack, prodRate);
                }
                if (consRate > 0.0001) {
                    mergeRate(totalConsumption, stack, consRate);
                }

                if (prodRate > 0.0001 || consRate > 0.0001) {
                    IngredientStack canonicalKey = findOrCreateCanonicalKey(itemContributions.keySet(), stack);
                    List<GlobalBalanceSummary.PageContribution> contribList = itemContributions.computeIfAbsent(canonicalKey, k -> new ArrayList<>());
                    contribList.add(new GlobalBalanceSummary.PageContribution(page.getId(), page.getName(), prodRate, consRate));
                }
            }
        }

        // 5. Compute global net balances (Produced - Consumed)
        Map<IngredientStack, Double> rawInputs = new LinkedHashMap<>();
        Map<IngredientStack, Double> netOutputs = new LinkedHashMap<>();
        Map<IngredientStack, Double> fullyBalanced = new LinkedHashMap<>();

        List<IngredientStack> allUniqueStacks = new ArrayList<>();
        for (IngredientStack s : totalProduction.keySet()) {
            if (!containsMatching(allUniqueStacks, s)) allUniqueStacks.add(s);
        }
        for (IngredientStack s : totalConsumption.keySet()) {
            if (!containsMatching(allUniqueStacks, s)) allUniqueStacks.add(s);
        }

        for (IngredientStack stack : allUniqueStacks) {
            double produced = findRate(totalProduction, stack);
            double consumed = findRate(totalConsumption, stack);
            double delta = produced - consumed;

            if (Math.abs(delta) < 0.0001) {
                fullyBalanced.put(stack, produced);
            } else if (delta > 0) {
                netOutputs.put(stack, delta);
            } else {
                rawInputs.put(stack, -delta);
            }
        }

        double netEUt = totalConsumedEUt - totalGeneratedEUt;
        return new GlobalBalanceSummary(
            selectedPagesList,
            totalGeneratedEUt,
            totalConsumedEUt,
            netEUt,
            highestTier,
            totalMachineCount,
            machineBreakdown,
            totalProduction,
            totalConsumption,
            netOutputs,
            rawInputs,
            fullyBalanced,
            itemContributions
        );
    }

    private static boolean containsMatching(List<IngredientStack> list, IngredientStack target) {
        if (target == null) return false;
        for (IngredientStack s : list) {
            if (s.equals(target) || s.matchesOrAlternative(target) || target.matchesOrAlternative(s)) {
                return true;
            }
        }
        return false;
    }

    private static IngredientStack findOrCreateCanonicalKey(Set<IngredientStack> set, IngredientStack target) {
        if (target == null) return null;
        for (IngredientStack s : set) {
            if (s.equals(target) || s.matchesOrAlternative(target) || target.matchesOrAlternative(s)) {
                return s;
            }
        }
        return target;
    }

    private static void mergeRate(Map<IngredientStack, Double> map, IngredientStack stack, double rate) {
        if (stack == null) return;
        for (Map.Entry<IngredientStack, Double> entry : map.entrySet()) {
            if (entry.getKey().equals(stack) || entry.getKey().matchesOrAlternative(stack) || stack.matchesOrAlternative(entry.getKey())) {
                entry.setValue(entry.getValue() + rate);
                return;
            }
        }
        map.put(stack, rate);
    }

    private static double findRate(Map<IngredientStack, Double> map, IngredientStack stack) {
        if (stack == null) return 0.0;
        for (Map.Entry<IngredientStack, Double> entry : map.entrySet()) {
            if (entry.getKey().equals(stack) || entry.getKey().matchesOrAlternative(stack) || stack.matchesOrAlternative(entry.getKey())) {
                return entry.getValue();
            }
        }
        return 0.0;
    }
}
