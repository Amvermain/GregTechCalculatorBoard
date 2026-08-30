package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.EnergyType;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import java.util.*;

/**
 * Aggregates flow balance summaries, total energy/power deltas,
 * byproduct separations, and calculates individual port flow statistics.
 */
public final class FlowSummaryAggregator {

    private FlowSummaryAggregator() {}

    public static FlowGraphSolver.PortFlowStats getInputPortStats(FlowGraph graph, RecipeNode node, int inputIndex) {
        if (graph == null || node == null || inputIndex < 0 || inputIndex >= node.getInputs().size()) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        double req = node.getInputSlotRate(inputIndex, false);

        double totalSupplied = 0.0;
        int count = 0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(node.getId()) && edge.inputIndex() == inputIndex) {
                RecipeNode p = graph.findNodeById(edge.fromNodeId());
                if (p != null && edge.outputIndex() < p.getOutputs().size()) {
                    double pRate = p.getOutputSlotRate(edge.outputIndex(), true);

                    // Total nominal demand from all consumers sharing this producer's output port
                    double totalPortDemand = 0.0;
                    for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                        if (outEdge.fromNodeId().equals(p.getId()) && outEdge.outputIndex() == edge.outputIndex()) {
                            RecipeNode c = graph.findNodeById(outEdge.toNodeId());
                            if (c != null && outEdge.inputIndex() < c.getInputs().size()) {
                                totalPortDemand += c.getInputSlotRate(outEdge.inputIndex(), false);
                            }
                        }
                    }

                    if (totalPortDemand > 0.0001) {
                        double share = req / totalPortDemand;
                        totalSupplied += pRate * share;
                    }
                    count++;
                }
            }
        }
        return new FlowGraphSolver.PortFlowStats(req, totalSupplied, count, count > 0);
    }

    public static FlowGraphSolver.PortFlowStats getOutputPortStats(FlowGraph graph, RecipeNode node, int outputIndex) {
        if (graph == null || node == null || outputIndex < 0 || outputIndex >= node.getOutputs().size()) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        double produced = node.getOutputSlotRate(outputIndex, true);

        double totalDemanded = 0.0;
        int count = 0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(node.getId()) && edge.outputIndex() == outputIndex) {
                RecipeNode c = graph.findNodeById(edge.toNodeId());
                if (c != null && edge.inputIndex() < c.getInputs().size()) {
                    double cReq = c.getInputSlotRate(edge.inputIndex(), true);

                    // Calculate total supply from all producers connected to this consumer's input port
                    double totalProducerSupply = 0.0;
                    for (FlowGraph.ConnectionEdge inEdge : graph.getConnections()) {
                        if (inEdge.toNodeId().equals(c.getId()) && inEdge.inputIndex() == edge.inputIndex()) {
                            RecipeNode p = graph.findNodeById(inEdge.fromNodeId());
                            if (p != null && inEdge.outputIndex() < p.getOutputs().size()) {
                                totalProducerSupply += p.getOutputSlotRate(inEdge.outputIndex(), true);
                            }
                        }
                    }

                    if (totalProducerSupply > 0.0001) {
                        double share = produced / totalProducerSupply;
                        totalDemanded += cReq * share;
                    } else {
                        totalDemanded += cReq;
                    }
                    count++;
                }
            }
        }
        return new FlowGraphSolver.PortFlowStats(produced, totalDemanded, count, count > 0);
    }

    /**
     * Solves the overall graph and computes total EU/t, raw ingredients, net outputs, and byproducts.
     */
    public static BalanceSummary computeSummary(FlowGraph graph) {
        if (graph == null) {
            return new BalanceSummary(0, GTVoltageTier.ULV, 0, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        FlowBalanceMatrixSolver.computeNodeEfficiencies(graph);

        double totalConsumedEUt = 0.0;
        double totalGeneratedEUt = 0.0;
        double totalConsumedSU = 0.0;
        double totalGeneratedSU = 0.0;
        double totalConsumedFE = 0.0;
        double totalGeneratedFE = 0.0;
        GTVoltageTier highestTier = GTVoltageTier.ULV;

        int totalMachineCount = 0;
        Map<String, Integer> machineBreakdown = new LinkedHashMap<>();

        long totalFusionStartupEU = 0L;
        Map<Integer, Integer> fusionTierCounts = new LinkedHashMap<>();
        Map<Integer, Long> fusionTierStartupEU = new LinkedHashMap<>();

        Map<IngredientStack, Double> totalProduction = new HashMap<>();
        Map<IngredientStack, Double> totalConsumption = new HashMap<>();

        for (RecipeNode node : graph.getNodes()) {
            if (node.isReroute()) continue;
            boolean isCompoundSlave = node.isCompoundNode() && !node.isCompoundMaster();

            if (!isCompoundSlave) {
                if (node.isModule()) {
                    int moduleCount = (int) Math.max(1, Math.ceil(node.getMachineCount() - 0.00001));
                    if (node.getSubGraph() != null) {
                        BalanceSummary subSummary = computeSummary(node.getSubGraph());
                        int subMachines = subSummary.totalMachineCount() * moduleCount;
                        totalMachineCount += subMachines;
                        for (Map.Entry<String, Integer> entry : subSummary.machineBreakdown().entrySet()) {
                            machineBreakdown.put(entry.getKey(), machineBreakdown.getOrDefault(entry.getKey(), 0) + entry.getValue() * moduleCount);
                        }
                        totalConsumedSU += subSummary.totalSU() < 0 ? -subSummary.totalSU() * moduleCount : 0;
                        totalGeneratedSU += subSummary.totalSU() > 0 ? subSummary.totalSU() * moduleCount : 0;
                        totalConsumedFE += subSummary.totalFE() < 0 ? -subSummary.totalFE() * moduleCount : 0;
                        totalGeneratedFE += subSummary.totalFE() > 0 ? subSummary.totalFE() * moduleCount : 0;

                        if (subSummary.totalFusionStartupEU() > 0) {
                            long subFusionEU = subSummary.totalFusionStartupEU() * moduleCount;
                            totalFusionStartupEU += subFusionEU;
                            for (Map.Entry<Integer, Integer> entry : subSummary.fusionTierCounts().entrySet()) {
                                fusionTierCounts.put(entry.getKey(), fusionTierCounts.getOrDefault(entry.getKey(), 0) + entry.getValue() * moduleCount);
                            }
                            for (Map.Entry<Integer, Long> entry : subSummary.fusionTierStartupEU().entrySet()) {
                                fusionTierStartupEU.put(entry.getKey(), fusionTierStartupEU.getOrDefault(entry.getKey(), 0L) + entry.getValue() * moduleCount);
                            }
                        }
                    } else {
                        int subMachines = Math.max(1, node.getContainedMachineCount()) * moduleCount;
                        totalMachineCount += subMachines;
                        String machineKey = node.getMachineDisplayName();
                        machineBreakdown.put(machineKey, machineBreakdown.getOrDefault(machineKey, 0) + subMachines);
                    }
                } else {
                    int nodeMachines = (int) Math.max(1, Math.ceil(node.getMachineCount() - 0.00001));
                    totalMachineCount += nodeMachines;
                    String machineKey = node.getMachineDisplayName();
                    machineBreakdown.put(machineKey, machineBreakdown.getOrDefault(machineKey, 0) + nodeMachines);

                    if (node.isFusion() && node.getEuToStart() > 0) {
                        int fTier = node.getFusionTier();
                        long startEU = node.getEuToStart();
                        long totalNodeStartEU = startEU * nodeMachines;
                        totalFusionStartupEU += totalNodeStartEU;
                        fusionTierCounts.put(fTier, fusionTierCounts.getOrDefault(fTier, 0) + nodeMachines);
                        fusionTierStartupEU.put(fTier, fusionTierStartupEU.getOrDefault(fTier, 0L) + totalNodeStartEU);
                    }
                }

                double rawPower = node.getEffectiveTotalEUt();
                if (node.getEnergyType() == EnergyType.KINETIC_SU) {
                    if (node.isGenerator()) {
                        totalGeneratedSU += rawPower;
                    } else {
                        totalConsumedSU += rawPower;
                    }
                } else if (node.getEnergyType() == EnergyType.ELECTRIC_FE) {
                    if (node.isGenerator()) {
                        totalGeneratedFE += rawPower;
                        totalGeneratedEUt += rawPower / 4.0;
                    } else {
                        totalConsumedFE += rawPower;
                        totalConsumedEUt += rawPower / 4.0;
                    }
                } else if (node.getEnergyType() == EnergyType.ELECTRIC_EU) {
                    if (node.isGenerator()) {
                        totalGeneratedEUt += rawPower;
                    } else {
                        totalConsumedEUt += rawPower;
                    }
                }

                if (node.getEnergyType() == EnergyType.ELECTRIC_EU && node.getTargetTier().ordinal() > highestTier.ordinal()) {
                    highestTier = node.getTargetTier();
                }
            }

            Map<IngredientStack, Double> outRates = node.calculateEffectiveOutputRates();
            for (Map.Entry<IngredientStack, Double> entry : outRates.entrySet()) {
                mergeRate(totalProduction, entry.getKey(), entry.getValue());
            }

            Map<IngredientStack, Double> inRates = node.calculateEffectiveInputRates();
            for (Map.Entry<IngredientStack, Double> entry : inRates.entrySet()) {
                mergeRate(totalConsumption, entry.getKey(), entry.getValue());
            }
        }

        Map<IngredientStack, Double> rawInputs = new LinkedHashMap<>();
        Map<IngredientStack, Double> netOutputs = new LinkedHashMap<>();
        Map<IngredientStack, Double> balanced = new LinkedHashMap<>();

        List<IngredientStack> uniqueStacks = new ArrayList<>();
        collectUniqueStacks(totalProduction.keySet(), uniqueStacks);
        collectUniqueStacks(totalConsumption.keySet(), uniqueStacks);

        for (IngredientStack stack : uniqueStacks) {
            double produced = findRate(totalProduction, stack);
            double consumed = findRate(totalConsumption, stack);
            double delta = produced - consumed;

            if (Math.abs(delta) < 0.0001) {
                balanced.put(stack, produced);
            } else if (delta > 0) {
                netOutputs.put(stack, delta);
            } else {
                rawInputs.put(stack, -delta);
            }
        }

        double netEUt = totalConsumedEUt - totalGeneratedEUt;
        double netSU = totalGeneratedSU - totalConsumedSU;
        double netFE = totalGeneratedFE - totalConsumedFE;
        return new BalanceSummary(netEUt, netSU, netFE, highestTier, totalMachineCount, machineBreakdown, rawInputs, netOutputs, balanced, totalProduction, totalConsumption, totalFusionStartupEU, fusionTierCounts, fusionTierStartupEU);
    }

    private static void collectUniqueStacks(Set<IngredientStack> source, List<IngredientStack> destination) {
        for (IngredientStack s : source) {
            boolean exists = false;
            for (IngredientStack u : destination) {
                if (u.equals(s) || u.matchesOrAlternative(s) || s.matchesOrAlternative(u)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) destination.add(s);
        }
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
