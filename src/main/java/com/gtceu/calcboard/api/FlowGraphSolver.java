package com.gtceu.calcboard.api;

import java.util.*;

/**
 * Pure calculation & graph algorithm solver for Calculator Board.
 * Handles AutoRatio BFS propagation, Fixed-Point bottleneck efficiency solving,
 * port flow statistics, summary aggregation, throughput optimization, and shift-connect matching.
 */
public final class FlowGraphSolver {

    private FlowGraphSolver() {}

    /**
     * Port flow statistics record for inputs and outputs.
     */
    public record PortFlowStats(
        double requiredOrProducedRate,
        double connectedRate,
        int connectionCount,
        boolean isConnected
    ) {
        public double getRatio() {
            if (requiredOrProducedRate <= 0.0001) return 1.0;
            return connectedRate / requiredOrProducedRate;
        }

        public double getPercent() {
            return getRatio() * 100.0;
        }

        public boolean isBalanced() {
            return isConnected && Math.abs(connectedRate - requiredOrProducedRate) <= 0.001;
        }

        /**
         * For Input Port: true if incoming supply is less than required demand (shortage, WARNING).
         */
        public boolean isInputDeficit() {
            return isConnected && connectedRate < requiredOrProducedRate - 0.001;
        }

        /**
         * For Input Port: true if incoming supply is greater than required demand (overflow, SAFE).
         */
        public boolean isInputSurplus() {
            return isConnected && connectedRate > requiredOrProducedRate + 0.001;
        }

        /**
         * For Output Port: true if consumer demand is less than produced rate (production exceeds consumption -> surplus remaining, SAFE).
         */
        public boolean isOutputSurplus() {
            return isConnected && connectedRate < requiredOrProducedRate - 0.001;
        }

        /**
         * For Output Port: true if consumer demand is greater than produced rate (demand exceeds production -> shortage for downstream, WARNING).
         */
        public boolean isOutputDeficit() {
            return isConnected && connectedRate > requiredOrProducedRate + 0.001;
        }

        public boolean isDeficit() {
            return isInputDeficit();
        }

        public boolean isSurplus() {
            return isInputSurplus();
        }
    }

    /**
     * Propagates machine counts across the graph starting from the anchor node.
     * 1. Upstream pass: Scales producers to guarantee sufficient supply for the anchor and its ancestors.
     * 2. Downstream pass: Scales consumers to absorb outputs from the anchor and its ancestors.
     */
    public static void autoRatioFromAnchor(FlowGraph graph, RecipeNode anchor, boolean integerCounts) {
        if (graph == null || anchor == null || graph.getNodes().isEmpty()) return;

        double targetAnchorCount = anchor.getMachineCount();
        if (integerCounts) {
            targetAnchorCount = Math.max(1.0, Math.ceil(targetAnchorCount - 0.00001));
        }
        anchor.setMachineCount(targetAnchorCount);

        Map<String, Double> upstreamCounts = new HashMap<>();
        upstreamCounts.put(anchor.getId(), targetAnchorCount);

        solveUpstreamPass(graph, anchor, upstreamCounts, integerCounts);

        for (Map.Entry<String, Double> entry : upstreamCounts.entrySet()) {
            RecipeNode n = graph.findNodeById(entry.getKey());
            if (n != null) {
                n.setMachineCount(entry.getValue());
            }
        }

        Map<String, Double> downstreamCounts = new HashMap<>(upstreamCounts);
        solveDownstreamPass(graph, anchor, downstreamCounts, upstreamCounts.keySet(), integerCounts);

        for (Map.Entry<String, Double> entry : downstreamCounts.entrySet()) {
            RecipeNode n = graph.findNodeById(entry.getKey());
            if (n != null) {
                n.setMachineCount(entry.getValue());
            }
        }

        anchor.setMachineCount(targetAnchorCount);
        normalizeNodeCounts(graph, anchor, targetAnchorCount, integerCounts);
    }

    private static void solveUpstreamPass(FlowGraph graph, RecipeNode anchor, Map<String, Double> upstreamCounts, boolean integerCounts) {
        Queue<RecipeNode> upQueue = new ArrayDeque<>();
        upQueue.add(anchor);

        int maxUpstreamIterations = Math.max(50, graph.getNodes().size() * 5);
        int upIterations = 0;
        Map<String, Integer> upVisitCounts = new HashMap<>();

        while (!upQueue.isEmpty() && upIterations < maxUpstreamIterations) {
            upIterations++;
            RecipeNode consumer = upQueue.poll();
            if (consumer == null) continue;

            for (int inIdx = 0; inIdx < consumer.getInputs().size(); inIdx++) {
                List<FlowGraph.ConnectionEdge> inEdges = new ArrayList<>();
                for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                    if (edge.toNodeId().equals(consumer.getId()) && edge.inputIndex() == inIdx) {
                        inEdges.add(edge);
                    }
                }
                if (inEdges.isEmpty()) continue;

                for (FlowGraph.ConnectionEdge edge : inEdges) {
                    RecipeNode producer = graph.findNodeById(edge.fromNodeId());
                    if (producer == null || producer.getId().equals(anchor.getId())) continue;

                    if (edge.outputIndex() < producer.getOutputs().size()) {
                        IngredientStack outStack = producer.getOutputs().get(edge.outputIndex());
                        double singleRate = producer.calculateSingleMachineOutputRate(outStack);

                        if (singleRate > 0.0001) {
                            double totalPortDemand = calculateTotalConnectedPortDemand(graph, producer, edge.outputIndex(), upstreamCounts);
                            double neededCount = totalPortDemand / singleRate;
                            if (integerCounts) {
                                neededCount = Math.max(1.0, Math.ceil(neededCount - 0.00001));
                            } else {
                                neededCount = Math.max(0.01, Math.round(neededCount * 100.0) / 100.0);
                            }

                            double prevCount = upstreamCounts.getOrDefault(producer.getId(), 0.0);
                            int visits = upVisitCounts.getOrDefault(producer.getId(), 0);

                            if (neededCount > prevCount + 0.0001 && visits < 3) {
                                upstreamCounts.put(producer.getId(), neededCount);
                                upVisitCounts.put(producer.getId(), visits + 1);
                                upQueue.add(producer);
                            }
                        }
                    }
                }
            }
        }
    }

    private static double calculateTotalConnectedPortDemand(FlowGraph graph, RecipeNode producer, int outputIndex, Map<String, Double> countsMap) {
        return calculateTotalConnectedPortDemand(graph, producer, outputIndex, countsMap, new HashSet<>());
    }

    private static double calculateTotalConnectedPortDemand(FlowGraph graph, RecipeNode producer, int outputIndex, Map<String, Double> countsMap, Set<String> visited) {
        if (producer == null || !visited.add(producer.getId() + ":" + outputIndex)) {
            return 0.0;
        }

        double totalPortDemand = 0.0;
        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
            if (outEdge.fromNodeId().equals(producer.getId()) && outEdge.outputIndex() == outputIndex) {
                RecipeNode cNode = graph.findNodeById(outEdge.toNodeId());
                if (cNode != null) {
                    if (cNode.isReroute()) {
                        totalPortDemand += calculateTotalConnectedPortDemand(graph, cNode, 0, countsMap, visited);
                    } else if (outEdge.inputIndex() < cNode.getInputs().size()) {
                        double cCount = countsMap.getOrDefault(cNode.getId(), cNode.getMachineCount());
                        double cCps = cNode.getOverclockResult().getCyclesPerSecond() * cNode.getTotalParallel() * cCount;
                        double cReq = cNode.getInputs().get(outEdge.inputIndex()).getAmount() * cCps;

                        int inDegree = 0;
                        for (FlowGraph.ConnectionEdge iEdge : graph.getConnections()) {
                            if (iEdge.toNodeId().equals(cNode.getId()) && iEdge.inputIndex() == outEdge.inputIndex()) {
                                inDegree++;
                            }
                        }
                        totalPortDemand += cReq / Math.max(1, inDegree);
                    }
                }
            }
        }
        return totalPortDemand;
    }

    private static double calculateEffectiveIncomingSupply(FlowGraph graph, RecipeNode consumer, int inIdx, Map<String, Double> countsMap, Set<String> visited) {
        if (consumer == null || !visited.add(consumer.getId() + ":" + inIdx)) {
            return 0.0;
        }

        double totalIncomingSupply = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(consumer.getId()) && edge.inputIndex() == inIdx) {
                RecipeNode p = graph.findNodeById(edge.fromNodeId());
                if (p != null) {
                    if (p.isReroute()) {
                        double upstreamSupply = calculateEffectiveIncomingSupply(graph, p, 0, countsMap, visited);
                        int outDegree = 0;
                        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                            if (outEdge.fromNodeId().equals(p.getId())) {
                                outDegree++;
                            }
                        }
                        totalIncomingSupply += upstreamSupply / Math.max(1, outDegree);
                    } else if (edge.outputIndex() < p.getOutputs().size()) {
                        double pC = countsMap.getOrDefault(p.getId(), p.getMachineCount());
                        IngredientStack outStack = p.getOutputs().get(edge.outputIndex());
                        double pRate = p.calculateSingleMachineOutputRate(outStack) * pC;

                        int outDegree = 0;
                        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                            if (outEdge.fromNodeId().equals(p.getId()) && outEdge.outputIndex() == edge.outputIndex()) {
                                outDegree++;
                            }
                        }
                        totalIncomingSupply += pRate / Math.max(1, outDegree);
                    }
                }
            }
        }
        return totalIncomingSupply;
    }

    private static void solveDownstreamPass(FlowGraph graph, RecipeNode anchor, Map<String, Double> downstreamCounts, Set<String> upstreamNodeIds, boolean integerCounts) {
        Queue<RecipeNode> downQueue = new ArrayDeque<>();
        for (String upId : upstreamNodeIds) {
            RecipeNode n = graph.findNodeById(upId);
            if (n != null) {
                downQueue.add(n);
            }
        }

        int maxDownstreamIterations = Math.max(50, graph.getNodes().size() * 5);
        int downIterations = 0;
        Map<String, Integer> downVisitCounts = new HashMap<>();

        while (!downQueue.isEmpty() && downIterations < maxDownstreamIterations) {
            downIterations++;
            RecipeNode producer = downQueue.poll();
            if (producer == null) continue;

            Set<RecipeNode> nextConsumers = new LinkedHashSet<>();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.fromNodeId().equals(producer.getId())) {
                    RecipeNode consumer = graph.findNodeById(edge.toNodeId());
                    if (consumer != null && !consumer.getId().equals(anchor.getId()) && !upstreamNodeIds.contains(consumer.getId())) {
                        nextConsumers.add(consumer);
                    }
                }
            }

            for (RecipeNode consumer : nextConsumers) {
                if (consumer.isReroute()) {
                    downQueue.add(consumer);
                    continue;
                }

                double requiredConsumerCount = 0.0;

                for (int inIdx = 0; inIdx < consumer.getInputs().size(); inIdx++) {
                    IngredientStack inStack = consumer.getInputs().get(inIdx);
                    double singleInRate = consumer.calculateSingleMachineInputRate(inStack);
                    if (singleInRate <= 0.0001) continue;

                    double totalIncomingSupply = calculateEffectiveIncomingSupply(graph, consumer, inIdx, downstreamCounts, new HashSet<>());

                    if (totalIncomingSupply > 0.0001) {
                        double portConsumerCount = totalIncomingSupply / singleInRate;
                        requiredConsumerCount = Math.max(requiredConsumerCount, portConsumerCount);
                    }
                }

                if (requiredConsumerCount > 0.0001) {
                    double finalConsumerCount = requiredConsumerCount;
                    if (integerCounts) {
                        finalConsumerCount = Math.max(1.0, Math.floor(finalConsumerCount + 0.00001));
                    } else {
                        finalConsumerCount = Math.max(0.01, Math.round(finalConsumerCount * 100.0) / 100.0);
                    }
                    downstreamCounts.put(consumer.getId(), finalConsumerCount);

                    int v = downVisitCounts.getOrDefault(consumer.getId(), 0);
                    if (v < 3) {
                        downVisitCounts.put(consumer.getId(), v + 1);
                        downQueue.add(consumer);
                    }
                }
            }
        }
    }

    private static void normalizeNodeCounts(FlowGraph graph, RecipeNode anchor, double targetAnchorCount, boolean integerCounts) {
        for (RecipeNode n : graph.getNodes()) {
            if (n.getId().equals(anchor.getId())) {
                n.setMachineCount(targetAnchorCount);
                continue;
            }
            if (integerCounts) {
                n.setMachineCount(Math.max(1.0, Math.ceil(n.getMachineCount() - 0.00001)));
            } else {
                n.setMachineCount(Math.max(0.01, Math.round(n.getMachineCount() * 100.0) / 100.0));
            }
        }
        anchor.setMachineCount(targetAnchorCount);
    }

    /**
     * Computes the bottleneck-constrained operating efficiency for every node in the graph.
     * Efficiency = min(1.0, incoming_supply / nominal_demand) across all connected inputs.
     * Uses Fixed-Point Iteration (up to 10 rounds) to support cascading downstream flow and loops.
     */
    public static Map<String, Double> computeNodeEfficiencies(FlowGraph graph) {
        Map<String, Double> effMap = new HashMap<>();
        if (graph == null) return effMap;

        for (RecipeNode node : graph.getNodes()) {
            effMap.put(node.getId(), 1.0);
        }

        for (int iter = 0; iter < 10; iter++) {
            boolean changed = false;
            for (RecipeNode consumer : graph.getNodes()) {
                double minRatio = 1.0;
                boolean hasConnectedInput = false;

                for (int inIdx = 0; inIdx < consumer.getInputs().size(); inIdx++) {
                    List<FlowGraph.ConnectionEdge> inEdges = new ArrayList<>();
                    for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                        if (edge.toNodeId().equals(consumer.getId()) && edge.inputIndex() == inIdx) {
                            inEdges.add(edge);
                        }
                    }
                    if (inEdges.isEmpty()) {
                        // Unconnected raw input -> 100% external supply assumed
                        continue;
                    }
                    hasConnectedInput = true;

                    IngredientStack inStack = consumer.getInputs().get(inIdx);
                    double nominalInRate = inStack.getAmount() * consumer.getCyclesPerSecond();
                    if (nominalInRate <= 0.00001) continue;

                    double totalIncomingSupply = 0.0;
                    for (FlowGraph.ConnectionEdge edge : inEdges) {
                        RecipeNode producer = graph.findNodeById(edge.fromNodeId());
                        if (producer != null && edge.outputIndex() < producer.getOutputs().size()) {
                            double prodEff = effMap.getOrDefault(producer.getId(), 1.0);
                            double prodNominalRate = producer.getOutputSlotRate(edge.outputIndex(), false);
                            double prodActualRate = prodNominalRate * prodEff;

                            // Total consumer nominal demand connected to this producer's output port
                            double totalPortDemand = 0.0;
                            for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                                if (outEdge.fromNodeId().equals(producer.getId()) && outEdge.outputIndex() == edge.outputIndex()) {
                                    RecipeNode c = graph.findNodeById(outEdge.toNodeId());
                                    if (c != null && outEdge.inputIndex() < c.getInputs().size()) {
                                        totalPortDemand += c.getInputSlotRate(outEdge.inputIndex(), false);
                                    }
                                }
                            }

                            if (totalPortDemand <= prodActualRate + 0.0001) {
                                totalIncomingSupply += nominalInRate; // ample supply
                            } else if (totalPortDemand > 0.0001) {
                                totalIncomingSupply += prodActualRate * (nominalInRate / totalPortDemand);
                            }
                        }
                    }

                    double portRatio = totalIncomingSupply / nominalInRate;
                    minRatio = Math.min(minRatio, portRatio);
                }

                double calculatedEff = hasConnectedInput ? Math.max(0.0, Math.min(1.0, minRatio)) : 1.0;
                double oldEff = effMap.get(consumer.getId());
                if (Math.abs(oldEff - calculatedEff) > 0.0001) {
                    effMap.put(consumer.getId(), calculatedEff);
                    consumer.setEfficiency(calculatedEff);
                    changed = true;
                } else {
                    consumer.setEfficiency(calculatedEff);
                }
            }
            if (!changed) break;
        }

        return effMap;
    }

    public static PortFlowStats getInputPortStats(FlowGraph graph, RecipeNode node, int inputIndex) {
        if (graph == null || node == null || inputIndex < 0 || inputIndex >= node.getInputs().size()) {
            return new PortFlowStats(0, 0, 0, false);
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
        return new PortFlowStats(req, totalSupplied, count, count > 0);
    }

    public static PortFlowStats getOutputPortStats(FlowGraph graph, RecipeNode node, int outputIndex) {
        if (graph == null || node == null || outputIndex < 0 || outputIndex >= node.getOutputs().size()) {
            return new PortFlowStats(0, 0, 0, false);
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
        return new PortFlowStats(produced, totalDemanded, count, count > 0);
    }

    /**
     * Solves the overall graph and computes total EU/t, raw ingredients, net outputs, and byproducts.
     */
    public static BalanceSummary computeSummary(FlowGraph graph) {
        if (graph == null) {
            return new BalanceSummary(0, GTVoltageTier.ULV, 0, Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }

        computeNodeEfficiencies(graph);

        double totalConsumedEUt = 0.0;
        double totalGeneratedEUt = 0.0;
        double totalConsumedSU = 0.0;
        double totalGeneratedSU = 0.0;
        double totalConsumedFE = 0.0;
        double totalGeneratedFE = 0.0;
        GTVoltageTier highestTier = GTVoltageTier.ULV;

        int totalMachineCount = 0;
        Map<String, Integer> machineBreakdown = new LinkedHashMap<>();

        Map<IngredientStack, Double> totalProduction = new HashMap<>();
        Map<IngredientStack, Double> totalConsumption = new HashMap<>();

        for (RecipeNode node : graph.getNodes()) {
            if (node.isReroute()) continue;
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
                } else {
                    int subMachines = Math.max(1, node.getContainedMachineCount()) * moduleCount;
                    totalMachineCount += subMachines;
                    machineBreakdown.put(node.getName(), machineBreakdown.getOrDefault(node.getName(), 0) + subMachines);
                }
            } else {
                int nodeMachines = (int) Math.max(1, Math.ceil(node.getMachineCount() - 0.00001));
                totalMachineCount += nodeMachines;
                machineBreakdown.put(node.getName(), machineBreakdown.getOrDefault(node.getName(), 0) + nodeMachines);
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

            if (node.getTargetTier().ordinal() > highestTier.ordinal()) {
                highestTier = node.getTargetTier();
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
        return new BalanceSummary(netEUt, netSU, netFE, highestTier, totalMachineCount, machineBreakdown, rawInputs, netOutputs, balanced, totalProduction, totalConsumption);
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

    public static void optimizeMaxThroughput(FlowGraph graph, boolean preferParallels, boolean integerCounts) {
        if (graph == null) return;
        RecipeNode anchor = graph.findBaseNode();
        if (anchor == null && !graph.getNodes().isEmpty()) {
            anchor = graph.getNodes().get(0);
        }
        if (anchor == null) return;

        for (RecipeNode n : graph.getNodes()) {
            GTVoltageTier baseTier = n.getRecipeTier();
            GTVoltageTier targetTier = GTVoltageTier.MAX;
            if (targetTier.ordinal() < baseTier.ordinal()) {
                targetTier = baseTier;
            }
            n.setTargetTier(targetTier);
        }

        autoRatioFromAnchor(graph, anchor, integerCounts);

        if (preferParallels || integerCounts) {
            for (RecipeNode n : graph.getNodes()) {
                if (n == anchor && anchor.isBaseNode()) continue;

                double count = n.getMachineCount();
                if (preferParallels && count > 1.0) {
                    int[] standardParallels = {1, 2, 4, 8, 16, 64, 128, 256};
                    int bestP = 1;
                    for (int p : standardParallels) {
                        if (p <= Math.ceil(count)) {
                            bestP = p;
                        }
                    }
                    if (bestP > 1) {
                        n.setParallel(bestP);
                        count = count / bestP;
                        n.setMachineCount(Math.round(count * 100.0) / 100.0);
                    }
                }

                if (integerCounts) {
                    n.setMachineCount(Math.max(1.0, Math.ceil(n.getMachineCount())));
                }
            }
        }
    }

    /**
     * Calculates the optimal consumer machine count for Shift-Drag connection (Floor matching).
     */
    public static double calculateConsumerMatchCount(FlowGraph graph, RecipeNode producer, int outPortIdx, RecipeNode consumer, int inPortIdx) {
        if (graph == null || producer == null || consumer == null) return 1.0;
        if (outPortIdx >= producer.getOutputs().size() || inPortIdx >= consumer.getInputs().size()) return 1.0;

        IngredientStack outStack = producer.getOutputs().get(outPortIdx);
        double prodEff = producer.getEfficiency();
        double effFactor = (prodEff > 0.00001) ? prodEff : 1.0;
        double producedRate = producer.calculateSingleMachineOutputRate(outStack) * producer.getMachineCount() * effFactor;

        IngredientStack inStack = consumer.getInputs().get(inPortIdx);
        double singleInRate = consumer.calculateSingleMachineInputRate(inStack);
        if (singleInRate <= 0.0001) return 1.0;

        // Sum other existing supplies flowing into this consumer input port
        double existingSupply = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(consumer.getId()) && edge.inputIndex() == inPortIdx) {
                if (!edge.fromNodeId().equals(producer.getId())) {
                    RecipeNode otherProd = graph.findNodeById(edge.fromNodeId());
                    if (otherProd != null && edge.outputIndex() < otherProd.getOutputs().size()) {
                        IngredientStack pOut = otherProd.getOutputs().get(edge.outputIndex());
                        double oEff = (otherProd.getEfficiency() > 0.00001) ? otherProd.getEfficiency() : 1.0;
                        double pRate = otherProd.calculateSingleMachineOutputRate(pOut) * otherProd.getMachineCount() * oEff;

                        int outDegree = 0;
                        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                            if (outEdge.fromNodeId().equals(otherProd.getId()) && outEdge.outputIndex() == edge.outputIndex()) {
                                outDegree++;
                            }
                        }
                        existingSupply += pRate / Math.max(1, outDegree);
                    }
                }
            }
        }

        double totalAvailableSupply = producedRate + existingSupply;
        return Math.max(1.0, Math.floor((totalAvailableSupply / singleInRate) + 0.00001));
    }

    /**
     * Calculates the optimal producer machine count for Shift-Drag connection (Ceil matching).
     */
    public static double calculateProducerMatchCount(FlowGraph graph, RecipeNode producer, int outPortIdx, RecipeNode consumer, int inPortIdx) {
        if (graph == null || producer == null || consumer == null) return 1.0;
        if (outPortIdx >= producer.getOutputs().size() || inPortIdx >= consumer.getInputs().size()) return 1.0;

        IngredientStack inStack = consumer.getInputs().get(inPortIdx);
        double totalDemand = consumer.calculateSingleMachineInputRate(inStack) * consumer.getMachineCount();

        // Calculate existing supply already flowing into this input port from other producers
        double existingSupply = 0.0;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(consumer.getId()) && edge.inputIndex() == inPortIdx) {
                if (!edge.fromNodeId().equals(producer.getId())) {
                    RecipeNode otherProd = graph.findNodeById(edge.fromNodeId());
                    if (otherProd != null && edge.outputIndex() < otherProd.getOutputs().size()) {
                        IngredientStack pOut = otherProd.getOutputs().get(edge.outputIndex());
                        double oEff = (otherProd.getEfficiency() > 0.00001) ? otherProd.getEfficiency() : 1.0;
                        double pRate = otherProd.calculateSingleMachineOutputRate(pOut) * otherProd.getMachineCount() * oEff;

                        int outDegree = 0;
                        for (FlowGraph.ConnectionEdge outEdge : graph.getConnections()) {
                            if (outEdge.fromNodeId().equals(otherProd.getId()) && outEdge.outputIndex() == edge.outputIndex()) {
                                outDegree++;
                            }
                        }
                        existingSupply += pRate / Math.max(1, outDegree);
                    }
                }
            }
        }

        double remainingDemand = Math.max(0.0, totalDemand - existingSupply);
        IngredientStack outStack = producer.getOutputs().get(outPortIdx);
        double prodEff = (producer.getEfficiency() > 0.00001) ? producer.getEfficiency() : 1.0;
        double singleOutRate = producer.calculateSingleMachineOutputRate(outStack) * prodEff;
        if (singleOutRate <= 0.0001) return 1.0;

        return Math.max(1.0, Math.ceil((remainingDemand / singleOutRate) - 0.00001));
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
