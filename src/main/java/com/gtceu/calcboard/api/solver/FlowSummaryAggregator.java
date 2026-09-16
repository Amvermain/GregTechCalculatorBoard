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

        FixedPointEfficiencySolver.PrecomputedDampedLoopMeta dampedMeta = (!node.isReroute())
                ? FixedPointEfficiencySolver.findDampedLoopMeta(graph, node, inputIndex)
                : null;
        boolean isSteadyStateRecirculating = false;
        boolean isUnfedDampedLoop = false;
        double externalSupplyRate = 0.0;
        double loopSupplyRate = 0.0;
        double recirculationRatio = 0.0;

        if (dampedMeta != null && isConnected) {
            double sExt = dampedMeta.computeExternalSupply(graph, null, null);
            if (sExt <= 0.0001) {
                isUnfedDampedLoop = true;
                recirculationRatio = dampedMeta.recirculationRatio();
            } else {
                double sSteady = dampedMeta.recirculationRatio() < 1.0 - 1e-4
                        ? sExt / (1.0 - dampedMeta.recirculationRatio())
                        : sExt;
                double steadyReq = dampedMeta.nominalDemand() > 0 ? sSteady * (nominalReq / dampedMeta.nominalDemand()) : sSteady;
                boolean fulfillsSteady = totalSupplied >= steadyReq * 0.999 - 1e-4;
                boolean belowNominal = totalSupplied < nominalReq - 0.001;

                double extSupply = calculateExternalSupplyToPort(graph, node.getId(), inputIndex, dampedMeta.scc());
                double loopSupply = Math.max(0.0, totalSupplied - extSupply);

                if (fulfillsSteady && belowNominal && loopSupply > 0.0001) {
                    isSteadyStateRecirculating = true;
                    recirculationRatio = dampedMeta.recirculationRatio();
                    externalSupplyRate = extSupply;
                    loopSupplyRate = loopSupply;
                }
            }
        }

        return new FlowGraphSolver.PortFlowStats(
                nominalReq,
                totalSupplied,
                count,
                isConnected,
                effectiveRate,
                isUpstreamThrottled,
                isSteadyStateRecirculating,
                externalSupplyRate,
                loopSupplyRate,
                recirculationRatio,
                isUnfedDampedLoop
        );
    }

    private static double calculateExternalSupplyToPort(FlowGraph graph, String nodeId, int inputIndex, Set<String> scc) {
        double extSupply = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!edge.toNodeId().equals(nodeId) || edge.inputIndex() != inputIndex) continue;
            RecipeNode producer = graph.findNodeById(edge.fromNodeId());
            if (producer == null) continue;

            if (!scc.contains(producer.getId())) {
                extSupply += FlowBalanceMatrixSolver.getEdgeAllocatedFlow(graph, edge, null);
            } else if (producer.isReroute()) {
                double edgeFlow = FlowBalanceMatrixSolver.getEdgeAllocatedFlow(graph, edge, null);
                double extFraction = computeJunctionExternalFraction(graph, producer, scc);
                extSupply += edgeFlow * extFraction;
            }
        }
        return extSupply;
    }

    private static double computeJunctionExternalFraction(FlowGraph graph, RecipeNode junction, Set<String> scc) {
        double totalIn = 0.0;
        double extIn = 0.0;
        for (FlowGraph.ConnectionEdge inEdge : graph.getConnections()) {
            if (!inEdge.toNodeId().equals(junction.getId()) || inEdge.inputIndex() != 0) continue;
            double flow = FlowBalanceMatrixSolver.getEdgeAllocatedFlow(graph, inEdge, null);
            totalIn += flow;
            if (!scc.contains(inEdge.fromNodeId())) {
                extIn += flow;
            }
        }
        return totalIn > 1e-5 ? Math.min(1.0, extIn / totalIn) : 0.0;
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
    private static final int MAX_MODULE_DEPTH = 16;

    public static BalanceSummary computeSummaryPreservingEfficiencies(FlowGraph graph) {
        return computeSummary(graph, false);
    }

    public static BalanceSummary computeSummary(FlowGraph graph, boolean recomputeEfficiencies) {
        BalanceSummary summary = computeSummaryInternal(graph, recomputeEfficiencies, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
        if (graph != null) {
            if (recomputeEfficiencies) {
                graph.setCachedSummary(summary);
            }
            graph.captureSnapshot();
        }
        return summary;
    }

    private static BalanceSummary computeSummaryInternal(
            FlowGraph graph,
            boolean recomputeEfficiencies,
            int depth,
            Set<FlowGraph> visitedGraphs
    ) {
        if (graph == null || depth > MAX_MODULE_DEPTH || !visitedGraphs.add(graph)) {
            return new BalanceSummary(0, GTVoltageTier.ULV, 0, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        if (recomputeEfficiencies) {
            FlowBalanceMatrixSolver.computeNodeEfficiencies(graph);
            graph.invalidatePortStatsCache();
        }

        PowerAccumulator power = new PowerAccumulator();
        FusionAccumulator fusion = new FusionAccumulator();
        int[] totalMachineCountHolder = new int[1];
        Map<String, Integer> machineBreakdown = new LinkedHashMap<>();

        Set<String> sharedMachineNodeIds = new HashSet<>();
        aggregateSharedMachineFrames(graph, sharedMachineNodeIds, machineBreakdown, totalMachineCountHolder);

        Map<IngredientStack, Double> totalProduction = new HashMap<>();
        Map<IngredientStack, Double> totalConsumption = new HashMap<>();
        Map<IngredientStack, Double> totalVoided = new HashMap<>();

        for (RecipeNode node : graph.getNodes()) {
            if (node.isReroute()) {
                aggregateRerouteNode(graph, node, totalProduction, totalConsumption, totalVoided);
                continue;
            }
            if (node.isBoundaryPin()) {
                continue;
            }
            boolean isCompoundSlave = node.isCompoundNode() && !node.isCompoundMaster();
            boolean isSharedMachine = sharedMachineNodeIds.contains(node.getId());

            if (!isCompoundSlave) {
                aggregateMachineBreakdown(node, isSharedMachine, depth, visitedGraphs, machineBreakdown, totalMachineCountHolder, power, fusion);
                fusion.addNodeFusion(node);
                power.addNodePower(node);
            }

            Map<IngredientStack, Double> outRates = node.calculateEffectiveOutputRates(false);
            for (Map.Entry<IngredientStack, Double> entry : outRates.entrySet()) {
                mergeRate(totalProduction, entry.getKey(), entry.getValue());
            }

            aggregateVoidOutputs(graph, node, totalVoided);

            Map<IngredientStack, Double> inRates = node.calculateEffectiveInputRates(false);
            for (Map.Entry<IngredientStack, Double> entry : inRates.entrySet()) {
                mergeRate(totalConsumption, entry.getKey(), entry.getValue());
            }
        }

        int totalMachineCount = totalMachineCountHolder[0];
        double totalConsumedEUt = power.consumedEUt;
        double totalGeneratedEUt = power.generatedEUt;
        double totalConsumedSU = power.consumedSU;
        double totalGeneratedSU = power.generatedSU;
        double totalConsumedFE = power.consumedFE;
        double totalGeneratedFE = power.generatedFE;
        GTVoltageTier highestTier = power.highestTier;
        long totalFusionStartupEU = fusion.totalFusionStartupEU;
        Map<Integer, Integer> fusionTierCounts = fusion.fusionTierCounts;
        Map<Integer, Long> fusionTierStartupEU = fusion.fusionTierStartupEU;

        Map<IngredientStack, Double> rawInputs = new LinkedHashMap<>();
        Map<IngredientStack, Double> netOutputs = new LinkedHashMap<>();
        Map<IngredientStack, Double> balanced = new LinkedHashMap<>();
        Map<IngredientStack, Double> voidedOutputs = new LinkedHashMap<>();

        Set<IngredientStack> uniqueStacks = new LinkedHashSet<>();
        uniqueStacks.addAll(totalProduction.keySet());
        uniqueStacks.addAll(totalConsumption.keySet());
        uniqueStacks.addAll(totalVoided.keySet());

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

        graph.captureSnapshot();
        return new BalanceSummary(netEUt, netSU, netFE, highestTier, totalMachineCount, machineBreakdown, rawInputs, netOutputs, balanced, totalProduction, totalConsumption, voidedOutputs, totalFusionStartupEU, fusionTierCounts, fusionTierStartupEU);
    }

    private static void mergeRate(Map<IngredientStack, Double> map, IngredientStack stack, double rate) {
        if (stack == null) return;
        map.merge(stack, rate, Double::sum);
    }

    private static double findRate(Map<IngredientStack, Double> map, IngredientStack stack) {
        return stack != null ? map.getOrDefault(stack, 0.0) : 0.0;
    }

    private static void aggregateSharedMachineFrames(FlowGraph graph, Set<String> sharedMachineNodeIds, Map<String, Integer> machineBreakdown, int[] totalMachineCountHolder) {
        for (CanvasGroupFrame frame : graph.getFrames()) {
            aggregateSingleSharedMachineFrame(frame, graph, sharedMachineNodeIds, machineBreakdown, totalMachineCountHolder);
        }
    }

    private static void aggregateSingleSharedMachineFrame(CanvasGroupFrame frame, FlowGraph graph, Set<String> sharedMachineNodeIds, Map<String, Integer> machineBreakdown, int[] totalMachineCountHolder) {
        if (frame == null || !frame.isSharedMachineFrame()) return;
        List<RecipeNode> enclosed = frame.getEnclosedNodes(graph);
        if (enclosed.isEmpty()) return;

        for (RecipeNode n : enclosed) {
            if (n != null && !n.isReroute()) {
                sharedMachineNodeIds.add(n.getId());
            }
        }
        int sharedCount = frame.computeRequiredMachines(graph);
        totalMachineCountHolder[0] += sharedCount;
        String machineKey = frame.getSharedMachineName(graph);
        if (machineKey == null || machineKey.isBlank()) {
            machineKey = frame.getTitle();
        }
        machineBreakdown.put(machineKey, machineBreakdown.getOrDefault(machineKey, 0) + sharedCount);
    }

    private static void aggregateMachineBreakdown(
            RecipeNode node,
            boolean isSharedMachine,
            int depth,
            Set<FlowGraph> visitedGraphs,
            Map<String, Integer> machineBreakdown,
            int[] totalMachineCountHolder,
            PowerAccumulator power,
            FusionAccumulator fusion
    ) {
        if (isSharedMachine) return;
        if (node.isModule()) {
            aggregateModuleNode(node, depth, visitedGraphs, machineBreakdown, totalMachineCountHolder, power, fusion);
            return;
        }
        int nodeMachines = (int) Math.max(1, Math.ceil(node.getMachineCount() - 0.00001));
        totalMachineCountHolder[0] += nodeMachines;
        String machineKey = node.getMachineDisplayName();
        machineBreakdown.put(machineKey, machineBreakdown.getOrDefault(machineKey, 0) + nodeMachines);
    }

    private static void aggregateModuleNode(
            RecipeNode node,
            int depth,
            Set<FlowGraph> visitedGraphs,
            Map<String, Integer> machineBreakdown,
            int[] totalMachineCountHolder,
            PowerAccumulator power,
            FusionAccumulator fusion
    ) {
        int moduleCount = (int) Math.max(1, Math.ceil(node.getMachineCount() - 0.00001));
        if (node.getSubGraph() != null) {
            aggregateSubGraphModule(node.getSubGraph(), moduleCount, depth, visitedGraphs, machineBreakdown, totalMachineCountHolder, power, fusion);
            return;
        }
        int subMachines = Math.max(1, node.getContainedMachineCount()) * moduleCount;
        totalMachineCountHolder[0] += subMachines;
        String machineKey = node.getMachineDisplayName();
        machineBreakdown.put(machineKey, machineBreakdown.getOrDefault(machineKey, 0) + subMachines);
    }

    private static void aggregateSubGraphModule(
            FlowGraph subGraph,
            int moduleCount,
            int depth,
            Set<FlowGraph> visitedGraphs,
            Map<String, Integer> machineBreakdown,
            int[] totalMachineCountHolder,
            PowerAccumulator power,
            FusionAccumulator fusion
    ) {
        BalanceSummary subSummary = computeSummaryInternal(subGraph, false, depth + 1, visitedGraphs);
        int subMachines = subSummary.totalMachineCount() * moduleCount;
        totalMachineCountHolder[0] += subMachines;
        for (Map.Entry<String, Integer> entry : subSummary.machineBreakdown().entrySet()) {
            machineBreakdown.put(entry.getKey(), machineBreakdown.getOrDefault(entry.getKey(), 0) + entry.getValue() * moduleCount);
        }
        power.addSubSummaryPower(subSummary, moduleCount);
        fusion.mergeSubSummaryFusion(subSummary, moduleCount);
    }

    private static void aggregateVoidOutputs(FlowGraph graph, RecipeNode node, Map<IngredientStack, Double> totalVoided) {
        for (int i = 0; i < node.getOutputs().size(); i++) {
            if (!node.isOutputPortVoided(i)) continue;
            double portVoidRate = calculatePortVoidRate(graph, node, i);
            if (portVoidRate > 0.0001) {
                mergeRate(totalVoided, node.getOutputs().get(i), portVoidRate);
            }
        }
    }

    private static double calculatePortVoidRate(FlowGraph graph, RecipeNode node, int portIndex) {
        IngredientStack out = node.getOutputs().get(portIndex);
        double singleRate = node.calculateSingleMachineOutputRate(out);
        double totalPortOut = singleRate * node.getMachineCount() * node.getEfficiency();
        double connectedDemand = 0.0;
        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
            if (!outEdge.fromNodeId().equals(node.getId()) || outEdge.outputIndex() != portIndex) {
                continue;
            }
            RecipeNode c = graph.findNodeById(outEdge.toNodeId());
            if (c != null && !c.isVoidSink()) {
                connectedDemand += FlowBalanceMatrixSolver.getConnectedConsumerDemand(graph, c, outEdge.inputIndex());
            }
        }
        return Math.max(0.0, totalPortOut - connectedDemand);
    }

    private static final class PowerAccumulator {
        double consumedEUt;
        double generatedEUt;
        double consumedSU;
        double generatedSU;
        double consumedFE;
        double generatedFE;
        GTVoltageTier highestTier = GTVoltageTier.ULV;

        void addSubSummaryPower(BalanceSummary subSummary, int moduleCount) {
            consumedSU += subSummary.totalSU() < 0 ? -subSummary.totalSU() * moduleCount : 0;
            generatedSU += subSummary.totalSU() > 0 ? subSummary.totalSU() * moduleCount : 0;
            consumedFE += subSummary.totalFE() < 0 ? -subSummary.totalFE() * moduleCount : 0;
            generatedFE += subSummary.totalFE() > 0 ? subSummary.totalFE() * moduleCount : 0;
        }

        void addNodePower(RecipeNode node) {
            double rawPower = node.getEffectiveTotalEUt();
            EnergyType eType = node.getEnergyType();
            if (eType == EnergyType.KINETIC_SU) {
                accumulateKineticPower(node, rawPower);
            } else if (eType == EnergyType.ELECTRIC_FE) {
                accumulateFePower(node, rawPower);
            } else if (eType == EnergyType.ELECTRIC_EU) {
                accumulateEuPower(node, rawPower);
            }
        }

        private void accumulateKineticPower(RecipeNode node, double rawPower) {
            if (node.isGenerator()) {
                generatedSU += rawPower;
            } else {
                consumedSU += rawPower;
            }
        }

        private void accumulateFePower(RecipeNode node, double rawPower) {
            if (node.isGenerator()) {
                generatedFE += rawPower;
                generatedEUt += rawPower / 4.0;
            } else {
                consumedFE += rawPower;
                consumedEUt += rawPower / 4.0;
            }
        }

        private void accumulateEuPower(RecipeNode node, double rawPower) {
            if (node.isGenerator()) {
                generatedEUt += rawPower;
            } else {
                consumedEUt += rawPower;
            }
            if (node.getTargetTier().ordinal() > highestTier.ordinal()) {
                highestTier = node.getTargetTier();
            }
        }
    }

    private static final class FusionAccumulator {
        long totalFusionStartupEU = 0L;
        final Map<Integer, Integer> fusionTierCounts = new LinkedHashMap<>();
        final Map<Integer, Long> fusionTierStartupEU = new LinkedHashMap<>();

        void mergeSubSummaryFusion(BalanceSummary subSummary, int moduleCount) {
            if (subSummary.totalFusionStartupEU() <= 0) return;
            totalFusionStartupEU += subSummary.totalFusionStartupEU() * moduleCount;
            for (Map.Entry<Integer, Integer> entry : subSummary.fusionTierCounts().entrySet()) {
                fusionTierCounts.put(entry.getKey(), fusionTierCounts.getOrDefault(entry.getKey(), 0) + entry.getValue() * moduleCount);
            }
            for (Map.Entry<Integer, Long> entry : subSummary.fusionTierStartupEU().entrySet()) {
                fusionTierStartupEU.put(entry.getKey(), fusionTierStartupEU.getOrDefault(entry.getKey(), 0L) + entry.getValue() * moduleCount);
            }
        }

        void addNodeFusion(RecipeNode node) {
            if (node.getEuToStart() <= 0 || !node.isFusion()) return;
            int fTier = node.getFusionTier();
            long startEU = node.getEuToStart();
            int nodeMachines = (int) Math.max(1, Math.ceil(node.getMachineCount() - 0.00001));
            long totalNodeStartEU = startEU * nodeMachines;
            totalFusionStartupEU += totalNodeStartEU;
            fusionTierCounts.put(fTier, fusionTierCounts.getOrDefault(fTier, 0) + nodeMachines);
            fusionTierStartupEU.put(fTier, fusionTierStartupEU.getOrDefault(fTier, 0L) + totalNodeStartEU);
        }
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
        try {
            if (!producer.isReroute()) {
                if (outputIndex >= producer.getOutputs().size()) return 0.0;
                IngredientStack s = producer.getOutputs().get(outputIndex);
                return s.getAmount() * s.getChance();
            }

            boolean hasIncoming = false;
            double totalIncoming = 0.0;
            for (FlowGraph.ConnectionEdge inEdge : graph.getConnections()) {
                if (inEdge.toNodeId().equals(producer.getId()) && inEdge.inputIndex() == 0) {
                    hasIncoming = true;
                    RecipeNode p = graph.findNodeById(inEdge.fromNodeId());
                    totalIncoming += getEffectiveProducerBatchAmount(graph, p, inEdge.outputIndex(), visited);
                }
            }

            if (producer.isInfiniteSupply()) {
                return calculateTotalBatchPortDemand(graph, producer, outputIndex);
            }

            if (producer.isExternalSupply() && producer.getExternalSupplyRate() > 0.0) {
                return totalIncoming + producer.getExternalSupplyRate();
            }

            if (producer.isFixedDrain() && producer.getExternalDrainRate() > 0.0) {
                return Math.max(0.0, totalIncoming - producer.getExternalDrainRate());
            }

            if (!hasIncoming) {
                return calculateTotalBatchPortDemand(graph, producer, outputIndex);
            }

            return totalIncoming;
        } finally {
            visited.remove(producer.getId());
        }
    }

    private static double getEffectiveConsumerBatchAmount(FlowGraph graph, RecipeNode consumer, int inputIndex, Set<String> visited) {
        if (consumer == null || inputIndex < 0 || !visited.add(consumer.getId())) {
            return 0.0;
        }
        try {
            if (!consumer.isReroute()) {
                if (inputIndex >= consumer.getInputs().size()) return 0.0;
                return consumer.getInputs().get(inputIndex).getAmount();
            }
            if (consumer.isVoidSink()) {
                return calculateTotalBatchPortSupply(graph, consumer, inputIndex);
            }
            double totalOutgoing = 0.0;
            for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                if (outEdge.fromNodeId().equals(consumer.getId()) && outEdge.outputIndex() == 0) {
                    RecipeNode c = graph.findNodeById(outEdge.toNodeId());
                    totalOutgoing += getEffectiveConsumerBatchAmount(graph, c, outEdge.inputIndex(), visited);
                }
            }
            return totalOutgoing;
        } finally {
            visited.remove(consumer.getId());
        }
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
