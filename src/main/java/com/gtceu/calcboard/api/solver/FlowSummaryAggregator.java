package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
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
        if (graph == null || node == null || inputIndex < 0) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        if (!node.isReroute() && inputIndex >= node.getInputs().size()) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        if (node.isReroute() && inputIndex != 0) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        double req = node.isReroute()
                ? FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, node, 0, null)
                : node.getInputSlotRate(inputIndex, false);

        double totalSupplied = 0.0;
        int count = 0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(node.getId()) && edge.inputIndex() == inputIndex) {
                RecipeNode p = graph.findNodeById(edge.fromNodeId());
                if (p != null && (p.isReroute() || edge.outputIndex() < p.getOutputs().size())) {
                    totalSupplied += FlowBalanceMatrixSolver.getEdgeAllocatedFlow(graph, edge, null);
                    count++;
                }
            }
        }
        return new FlowGraphSolver.PortFlowStats(req, totalSupplied, count, count > 0);
    }

    public static FlowGraphSolver.PortFlowStats getOutputPortStats(FlowGraph graph, RecipeNode node, int outputIndex) {
        if (graph == null || node == null || outputIndex < 0) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        if (!node.isReroute() && outputIndex >= node.getOutputs().size()) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        if (node.isReroute() && outputIndex != 0) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        double produced = FlowBalanceMatrixSolver.getEffectiveProducerOutputRate(graph, node, outputIndex, null);

        double totalDemanded = 0.0;
        int count = 0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!edge.fromNodeId().equals(node.getId()) || edge.outputIndex() != outputIndex) {
                continue;
            }
            RecipeNode c = graph.findNodeById(edge.toNodeId());
            if (c == null || c.isVoidSink()) {
                continue;
            }
            totalDemanded += resolveConnectedDemand(graph, edge, c, produced);
            count++;
        }
        return new FlowGraphSolver.PortFlowStats(produced, totalDemanded, count, count > 0);
    }

    private static double resolveConnectedDemand(FlowGraph graph, FlowGraph.ConnectionEdge edge, RecipeNode consumer, double producedRate) {
        if (edge.hasFixedLimit()) {
            return edge.fixedFlowLimit();
        }
        if (consumer.isReroute()) {
            return FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, consumer, 0, null);
        }
        if (edge.inputIndex() >= consumer.getInputs().size()) {
            return 0.0;
        }
        double cReq = consumer.getInputSlotRate(edge.inputIndex(), true);
        double totalProducerSupply = calculateTotalSupplyToInputSlot(graph, consumer.getId(), edge.inputIndex());
        if (totalProducerSupply > 0.0001) {
            return cReq * (producedRate / totalProducerSupply);
        }
        return cReq;
    }

    private static double calculateTotalSupplyToInputSlot(FlowGraph graph, String consumerId, int inputIndex) {
        double supply = 0.0;
        for (FlowGraph.ConnectionEdge inEdge : graph.getConnections()) {
            if (inEdge.toNodeId().equals(consumerId) && inEdge.inputIndex() == inputIndex) {
                RecipeNode p = graph.findNodeById(inEdge.fromNodeId());
                if (p != null && (p.isReroute() || inEdge.outputIndex() < p.getOutputs().size())) {
                    supply += FlowBalanceMatrixSolver.getEffectiveProducerOutputRate(graph, p, inEdge.outputIndex(), null);
                }
            }
        }
        return supply;
    }

    /**
     * Solves the overall graph and computes total EU/t, raw ingredients, net outputs, and byproducts.
     */
    public static BalanceSummary computeSummary(FlowGraph graph) {
        return computeSummary(graph, true);
    }

    /**
     * Computes the balance summary using existing node efficiencies and port states without re-evaluating efficiencies.
     * Prevents bottleneck collapse for isolated subgraphs.
     */
    public static BalanceSummary computeSummaryPreservingEfficiencies(FlowGraph graph) {
        return computeSummary(graph, false);
    }

    public static BalanceSummary computeSummary(FlowGraph graph, boolean recomputeEfficiencies) {
        if (graph == null) {
            return new BalanceSummary(0, GTVoltageTier.ULV, 0, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        if (recomputeEfficiencies) {
            FlowBalanceMatrixSolver.computeNodeEfficiencies(graph);
            graph.invalidatePortStatsCache();
        }

        double totalConsumedEUt = 0.0;
        double totalGeneratedEUt = 0.0;
        double totalConsumedSU = 0.0;
        double totalGeneratedSU = 0.0;
        double totalConsumedFE = 0.0;
        double totalGeneratedFE = 0.0;
        GTVoltageTier highestTier = GTVoltageTier.ULV;

        int totalMachineCount = 0;
        Map<String, Integer> machineBreakdown = new LinkedHashMap<>();

        // Pre-aggregate shared machine frames
        Set<String> sharedMachineNodeIds = new HashSet<>();
        for (CanvasGroupFrame frame : graph.getFrames()) {
            if (frame != null && frame.isSharedMachineFrame()) {
                List<RecipeNode> enclosed = frame.getEnclosedNodes(graph);
                if (!enclosed.isEmpty()) {
                    for (RecipeNode n : enclosed) {
                        if (n != null && !n.isReroute()) {
                            sharedMachineNodeIds.add(n.getId());
                        }
                    }
                    int sharedCount = frame.computeRequiredMachines(graph);
                    totalMachineCount += sharedCount;
                    String machineKey = frame.getSharedMachineName(graph);
                    if (machineKey == null || machineKey.isBlank()) {
                        machineKey = frame.getTitle();
                    }
                    machineBreakdown.put(machineKey, machineBreakdown.getOrDefault(machineKey, 0) + sharedCount);
                }
            }
        }

        long totalFusionStartupEU = 0L;
        Map<Integer, Integer> fusionTierCounts = new LinkedHashMap<>();
        Map<Integer, Long> fusionTierStartupEU = new LinkedHashMap<>();

        Map<IngredientStack, Double> totalProduction = new HashMap<>();
        Map<IngredientStack, Double> totalConsumption = new HashMap<>();
        Map<IngredientStack, Double> totalVoided = new HashMap<>();

        for (RecipeNode node : graph.getNodes()) {
            if (node.isReroute()) {
                if (node.isVoidSink()) {
                    for (FlowGraph.ConnectionEdge inEdge : graph.getConnections()) {
                        if (inEdge.toNodeId().equals(node.getId())) {
                            RecipeNode producer = graph.findNodeById(inEdge.fromNodeId());
                            if (producer != null && inEdge.outputIndex() < producer.getOutputs().size()) {
                                IngredientStack outStack = producer.getOutputs().get(inEdge.outputIndex());
                                double pRate = FlowBalanceMatrixSolver.getEffectiveProducerOutputRate(graph, producer, inEdge.outputIndex(), null);
                                double totalPortDemand = 0.0;
                                for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                                    if (outEdge.fromNodeId().equals(producer.getId()) && outEdge.outputIndex() == inEdge.outputIndex()) {
                                        RecipeNode c = graph.findNodeById(outEdge.toNodeId());
                                        if (c != null && !c.isVoidSink()) {
                                            if (c.isReroute()) {
                                                totalPortDemand += FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, c, 0, null);
                                            } else if (outEdge.inputIndex() < c.getInputs().size()) {
                                                totalPortDemand += c.getInputSlotRate(outEdge.inputIndex(), false);
                                            }
                                        }
                                    }
                                }
                                double voidRate = Math.max(0.0, pRate - totalPortDemand);
                                if (voidRate > 0.0001) {
                                    mergeRate(totalVoided, outStack, voidRate);
                                }
                            }
                        }
                    }
                } else if (node.isExternalSupply()) {
                    IngredientStack rStack = node.getRerouteIngredient();
                    if (rStack != null) {
                        if (node.isInfiniteSupply()) {
                            double downstreamDemand = FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, node, 0, null);
                            if (downstreamDemand > 0.0) {
                                mergeRate(totalProduction, rStack, downstreamDemand);
                            }
                        } else if (node.getExternalSupplyRate() > 0.0) {
                            mergeRate(totalProduction, rStack, node.getExternalSupplyRate());
                        }
                    }
                }
                continue;
            }
            boolean isCompoundSlave = node.isCompoundNode() && !node.isCompoundMaster();
            boolean isSharedMachine = sharedMachineNodeIds.contains(node.getId());

            if (!isCompoundSlave) {
                if (!isSharedMachine) {
                    if (node.isModule()) {
                        int moduleCount = (int) Math.max(1, Math.ceil(node.getMachineCount() - 0.00001));
                        if (node.getSubGraph() != null) {
                            BalanceSummary subSummary = computeSummaryPreservingEfficiencies(node.getSubGraph());
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
                    }
                }

                if (node.isFusion() && node.getEuToStart() > 0) {
                    int fTier = node.getFusionTier();
                    long startEU = node.getEuToStart();
                    int nodeMachines = (int) Math.max(1, Math.ceil(node.getMachineCount() - 0.00001));
                    long totalNodeStartEU = startEU * nodeMachines;
                    totalFusionStartupEU += totalNodeStartEU;
                    fusionTierCounts.put(fTier, fusionTierCounts.getOrDefault(fTier, 0) + nodeMachines);
                    fusionTierStartupEU.put(fTier, fusionTierStartupEU.getOrDefault(fTier, 0L) + totalNodeStartEU);
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

            for (int i = 0; i < node.getOutputs().size(); i++) {
                if (node.isOutputPortVoided(i)) {
                    IngredientStack out = node.getOutputs().get(i);
                    double singleRate = node.calculateSingleMachineOutputRate(out);
                    double totalPortOut = singleRate * node.getMachineCount() * node.getEfficiency();
                    double connectedDemand = 0.0;
                    for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                        if (outEdge.fromNodeId().equals(node.getId()) && outEdge.outputIndex() == i) {
                            RecipeNode c = graph.findNodeById(outEdge.toNodeId());
                            if (c != null && !c.isVoidSink()) {
                                if (c.isReroute()) {
                                    totalPortDemandCheck:
                                    connectedDemand += FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, c, 0, null);
                                } else if (outEdge.inputIndex() < c.getInputs().size()) {
                                    connectedDemand += c.getInputSlotRate(outEdge.inputIndex(), false);
                                }
                            }
                        }
                    }
                    double portVoidRate = Math.max(0.0, totalPortOut - connectedDemand);
                    if (portVoidRate > 0.0001) {
                        mergeRate(totalVoided, out, portVoidRate);
                    }
                }
            }

            Map<IngredientStack, Double> inRates = node.calculateEffectiveInputRates();
            for (Map.Entry<IngredientStack, Double> entry : inRates.entrySet()) {
                mergeRate(totalConsumption, entry.getKey(), entry.getValue());
            }
        }

        Map<IngredientStack, Double> rawInputs = new LinkedHashMap<>();
        Map<IngredientStack, Double> netOutputs = new LinkedHashMap<>();
        Map<IngredientStack, Double> balanced = new LinkedHashMap<>();
        Map<IngredientStack, Double> voidedOutputs = new LinkedHashMap<>();

        List<IngredientStack> uniqueStacks = new ArrayList<>();
        collectUniqueStacks(totalProduction.keySet(), uniqueStacks);
        collectUniqueStacks(totalConsumption.keySet(), uniqueStacks);
        collectUniqueStacks(totalVoided.keySet(), uniqueStacks);

        for (IngredientStack stack : uniqueStacks) {
            double produced = findRate(totalProduction, stack);
            double consumed = findRate(totalConsumption, stack);
            double voided = findRate(totalVoided, stack);
            double netSurplus = produced - consumed;
            double effectiveVoided = Math.min(Math.max(0.0, netSurplus), voided);

            if (effectiveVoided > 0.0001) {
                voidedOutputs.put(stack, effectiveVoided);
            }

            double delta = netSurplus - effectiveVoided;

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
        return new BalanceSummary(netEUt, netSU, netFE, highestTier, totalMachineCount, machineBreakdown, rawInputs, netOutputs, balanced, totalProduction, totalConsumption, voidedOutputs, totalFusionStartupEU, fusionTierCounts, fusionTierStartupEU);
    }

    private static void collectUniqueStacks(Set<IngredientStack> source, List<IngredientStack> destination) {
        for (IngredientStack s : source) {
            boolean exists = false;
            for (IngredientStack u : destination) {
                if (u.equals(s)) {
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
            if (entry.getKey().equals(stack)) {
                entry.setValue(entry.getValue() + rate);
                return;
            }
        }
        map.put(stack, rate);
    }

    private static double findRate(Map<IngredientStack, Double> map, IngredientStack stack) {
        if (stack == null) return 0.0;
        for (Map.Entry<IngredientStack, Double> entry : map.entrySet()) {
            if (entry.getKey().equals(stack)) {
                return entry.getValue();
            }
        }
        return 0.0;
    }
}
