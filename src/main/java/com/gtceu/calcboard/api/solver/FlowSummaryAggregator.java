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
        double nominalReq = node.isReroute()
                ? (FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, node, 0, null) + (node.isFixedDrain() ? node.getExternalDrainRate() : 0.0))
                : node.getInputSlotRate(inputIndex, false);
        double effectiveReq = node.isReroute()
                ? nominalReq
                : node.getInputSlotRate(inputIndex, true);

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

        boolean isConnected = count > 0;
        double effectiveRate = Math.min(effectiveReq, totalSupplied);
        boolean isUpstreamThrottled = isConnected && (effectiveReq < nominalReq - 0.001) && (totalSupplied > effectiveReq + 0.001);
        return new FlowGraphSolver.PortFlowStats(nominalReq, totalSupplied, count, isConnected, effectiveRate, isUpstreamThrottled);
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
            double drain = consumer.isFixedDrain() ? consumer.getExternalDrainRate() : 0.0;
            double totalDemand = drain + FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, consumer, 0, null);
            double totalProducerSupply = calculateTotalSupplyToInputSlot(graph, consumer.getId(), edge.inputIndex());
            if (totalProducerSupply > 0.0001) {
                return totalDemand * (producedRate / totalProducerSupply);
            }
            return totalDemand;
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
                aggregateRerouteNode(graph, node, totalProduction, totalConsumption, totalVoided);
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
                                connectedDemand += FlowBalanceMatrixSolver.getConnectedConsumerDemand(graph, c, outEdge.inputIndex());
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

    public static FlowGraphSolver.PortFlowStats getBatchInputPortStats(FlowGraph graph, RecipeNode node, int inputIndex) {
        if (graph == null || node == null || inputIndex < 0) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        if (!node.isReroute() && inputIndex >= node.getInputs().size()) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        if (node.isReroute() && inputIndex != 0) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        double reqBatch = node.isOperational() ? getEffectiveConsumerBatchAmount(graph, node, inputIndex, new HashSet<>()) : 0.0;
        double totalSupplied = 0.0;
        int count = 0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(node.getId()) && edge.inputIndex() == inputIndex) {
                RecipeNode p = graph.findNodeById(edge.fromNodeId());
                if (p != null) {
                    totalSupplied += resolveAllocatedBatchSupply(graph, edge, p, reqBatch);
                    count++;
                }
            }
        }
        return new FlowGraphSolver.PortFlowStats(reqBatch, totalSupplied, count, count > 0, reqBatch, false);
    }

    public static FlowGraphSolver.PortFlowStats getBatchOutputPortStats(FlowGraph graph, RecipeNode node, int outputIndex) {
        if (graph == null || node == null || outputIndex < 0) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        if (!node.isReroute() && outputIndex >= node.getOutputs().size()) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        if (node.isReroute() && outputIndex != 0) {
            return new FlowGraphSolver.PortFlowStats(0, 0, 0, false);
        }
        double prodBatch = node.isOperational() ? getEffectiveProducerBatchAmount(graph, node, outputIndex, new HashSet<>()) : 0.0;
        double totalDemanded = 0.0;
        int count = 0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(node.getId()) && edge.outputIndex() == outputIndex) {
                RecipeNode c = graph.findNodeById(edge.toNodeId());
                if (c != null && !c.isVoidSink()) {
                    totalDemanded += resolveAllocatedBatchDemand(graph, edge, c, prodBatch);
                    count++;
                }
            }
        }
        return new FlowGraphSolver.PortFlowStats(prodBatch, totalDemanded, count, count > 0, prodBatch, false);
    }

    private static double resolveAllocatedBatchSupply(FlowGraph graph, FlowGraph.ConnectionEdge edge, RecipeNode producer, double consumerReqBatch) {
        if (producer == null || !producer.isOperational()) return 0.0;
        double prodBatch = getEffectiveProducerBatchAmount(graph, producer, edge.outputIndex(), new HashSet<>());
        if (prodBatch <= 0.00001) return 0.0;

        double totalPortDemanded = calculateTotalBatchPortDemand(graph, producer, edge.outputIndex());
        if (totalPortDemanded > 0.0001) {
            return prodBatch * (consumerReqBatch / totalPortDemanded);
        }
        int edgeCount = countOutgoingEdges(graph, producer.getId(), edge.outputIndex());
        return edgeCount > 0 ? prodBatch / edgeCount : prodBatch;
    }

    private static double resolveAllocatedBatchDemand(FlowGraph graph, FlowGraph.ConnectionEdge edge, RecipeNode consumer, double producerProdBatch) {
        if (consumer == null || !consumer.isOperational()) return 0.0;
        double reqBatch = getEffectiveConsumerBatchAmount(graph, consumer, edge.inputIndex(), new HashSet<>());
        if (reqBatch <= 0.00001) return 0.0;

        double totalPortSupplied = calculateTotalBatchPortSupply(graph, consumer, edge.inputIndex());
        if (totalPortSupplied > 0.0001) {
            return reqBatch * (producerProdBatch / totalPortSupplied);
        }
        int edgeCount = countIncomingEdges(graph, consumer.getId(), edge.inputIndex());
        return edgeCount > 0 ? reqBatch / edgeCount : reqBatch;
    }

    private static double getEffectiveProducerBatchAmount(FlowGraph graph, RecipeNode producer, int outputIndex, Set<String> visited) {
        if (producer == null || outputIndex < 0 || !visited.add(producer.getId())) {
            return 0.0;
        }
        if (!producer.isReroute()) {
            if (outputIndex >= producer.getOutputs().size()) return 0.0;
            IngredientStack s = producer.getOutputs().get(outputIndex);
            return s.getAmount() * s.getChance();
        }
        double totalIncoming = 0.0;
        for (FlowGraph.ConnectionEdge inEdge : graph.getConnections()) {
            if (inEdge.toNodeId().equals(producer.getId()) && inEdge.inputIndex() == 0) {
                RecipeNode p = graph.findNodeById(inEdge.fromNodeId());
                totalIncoming += getEffectiveProducerBatchAmount(graph, p, inEdge.outputIndex(), visited);
            }
        }
        return totalIncoming;
    }

    private static double getEffectiveConsumerBatchAmount(FlowGraph graph, RecipeNode consumer, int inputIndex, Set<String> visited) {
        if (consumer == null || inputIndex < 0 || !visited.add(consumer.getId())) {
            return 0.0;
        }
        if (!consumer.isReroute()) {
            if (inputIndex >= consumer.getInputs().size()) return 0.0;
            return consumer.getInputs().get(inputIndex).getAmount();
        }
        double totalOutgoing = 0.0;
        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
            if (outEdge.fromNodeId().equals(consumer.getId()) && outEdge.outputIndex() == 0) {
                RecipeNode c = graph.findNodeById(outEdge.toNodeId());
                totalOutgoing += getEffectiveConsumerBatchAmount(graph, c, outEdge.inputIndex(), visited);
            }
        }
        return totalOutgoing;
    }

    private static double calculateTotalBatchPortDemand(FlowGraph graph, RecipeNode producer, int outputIndex) {
        double demand = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(producer.getId()) && edge.outputIndex() == outputIndex) {
                RecipeNode c = graph.findNodeById(edge.toNodeId());
                if (c != null && !c.isVoidSink()) {
                    demand += getEffectiveConsumerBatchAmount(graph, c, edge.inputIndex(), new HashSet<>());
                }
            }
        }
        return demand;
    }

    private static double calculateTotalBatchPortSupply(FlowGraph graph, RecipeNode consumer, int inputIndex) {
        double supply = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(consumer.getId()) && edge.inputIndex() == inputIndex) {
                RecipeNode p = graph.findNodeById(edge.fromNodeId());
                if (p != null) {
                    supply += getEffectiveProducerBatchAmount(graph, p, edge.outputIndex(), new HashSet<>());
                }
            }
        }
        return supply;
    }

    private static int countOutgoingEdges(FlowGraph graph, String producerId, int outputIndex) {
        int count = 0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(producerId) && edge.outputIndex() == outputIndex) {
                count++;
            }
        }
        return count;
    }

    private static int countIncomingEdges(FlowGraph graph, String consumerId, int inputIndex) {
        int count = 0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(consumerId) && edge.inputIndex() == inputIndex) {
                count++;
            }
        }
        return count;
    }

    private static void aggregateRerouteNode(
            FlowGraph graph,
            RecipeNode node,
            Map<IngredientStack, Double> totalProduction,
            Map<IngredientStack, Double> totalConsumption,
            Map<IngredientStack, Double> totalVoided
    ) {
        if (node.isVoidSink()) {
            aggregateVoidSinkReroute(graph, node, totalVoided);
            return;
        }
        if (node.isExternalSupply()) {
            aggregateExternalSupplyReroute(graph, node, totalProduction);
            return;
        }
        if (node.isFixedDrain()) {
            aggregateFixedDrainReroute(node, totalConsumption);
        }
    }

    private static void aggregateFixedDrainReroute(RecipeNode node, Map<IngredientStack, Double> totalConsumption) {
        IngredientStack rStack = node.getRerouteIngredient();
        if (rStack == null || node.getExternalDrainRate() <= 0.0) return;
        mergeRate(totalConsumption, rStack, node.getExternalDrainRate());
    }

    private static void aggregateExternalSupplyReroute(FlowGraph graph, RecipeNode node, Map<IngredientStack, Double> totalProduction) {
        IngredientStack rStack = node.getRerouteIngredient();
        if (rStack == null) return;
        if (node.isInfiniteSupply()) {
            double downstreamDemand = FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, node, 0, null);
            if (downstreamDemand > 0.0) {
                mergeRate(totalProduction, rStack, downstreamDemand);
            }
        } else if (node.getExternalSupplyRate() > 0.0) {
            mergeRate(totalProduction, rStack, node.getExternalSupplyRate());
        }
    }

    private static void aggregateVoidSinkReroute(FlowGraph graph, RecipeNode node, Map<IngredientStack, Double> totalVoided) {
        for (FlowGraph.ConnectionEdge inEdge : graph.getConnections()) {
            if (!inEdge.toNodeId().equals(node.getId())) continue;
            RecipeNode producer = graph.findNodeById(inEdge.fromNodeId());
            if (producer == null || inEdge.outputIndex() >= producer.getOutputs().size()) continue;

            IngredientStack outStack = producer.getOutputs().get(inEdge.outputIndex());
            double pRate = FlowBalanceMatrixSolver.getEffectiveProducerOutputRate(graph, producer, inEdge.outputIndex(), null);
            double totalPortDemand = computeConnectedNonVoidPortDemand(graph, producer.getId(), inEdge.outputIndex());
            double voidRate = Math.max(0.0, pRate - totalPortDemand);
            if (voidRate > 0.0001) {
                mergeRate(totalVoided, outStack, voidRate);
            }
        }
    }

    private static double computeConnectedNonVoidPortDemand(FlowGraph graph, String producerId, int outputIndex) {
        double totalPortDemand = 0.0;
        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
            if (!outEdge.fromNodeId().equals(producerId) || outEdge.outputIndex() != outputIndex) continue;
            RecipeNode c = graph.findNodeById(outEdge.toNodeId());
            if (c == null || c.isVoidSink()) continue;
            totalPortDemand += FlowBalanceMatrixSolver.getConnectedConsumerDemand(graph, c, outEdge.inputIndex());
        }
        return totalPortDemand;
    }
}
