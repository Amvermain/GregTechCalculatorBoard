package com.gtceu.calcboard.api.solver.linear;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.AutoRatioEngine;
import com.gtceu.calcboard.api.solver.AutoRatioResult;
import com.gtceu.calcboard.api.solver.FlowBalanceMatrixSolver;
import com.gtceu.calcboard.api.solver.FlowEdgeAllocator;

import java.util.*;

/**
 * Two-stage linear flow balance solver.
 * Stage 1: Formulates graph material balances as a linear system (A * x = b) and solves continuous machine rates.
 * Stage 2: Quantizes continuous rates into integer machine counts and balances buffer surpluses.
 */
public final class TwoStageLinearFlowSolver {

    public record SolveResult(
            boolean successful,
            Map<String, Double> machineCounts,
            String infeasibleResourceLabel,
            boolean underDetermined
    ) {
        public static SolveResult solved(Map<String, Double> counts) {
            return new SolveResult(true, counts, null, false);
        }

        public static SolveResult underDetermined(Map<String, Double> counts) {
            return new SolveResult(true, counts, null, true);
        }

        public static SolveResult infeasible(String resourceLabel) {
            return new SolveResult(false, Collections.emptyMap(), resourceLabel, false);
        }
    }

    private TwoStageLinearFlowSolver() {}

    public static SolveResult solve(FlowGraph graph, RecipeNode anchor, boolean integerCounts) {
        if (graph == null || anchor == null || graph.getNodes().isEmpty()) {
            return SolveResult.infeasible("empty_graph");
        }

        LinearEquationSystem system = new LinearEquationSystem(graph, anchor);
        if (system.getVariableCount() == 0) {
            return SolveResult.infeasible("no_machine_nodes");
        }

        buildConservationEquations(graph, system);
        buildAnchorEquation(graph, anchor, system);
        buildSharedPoolEquations(graph, system);

        GaussJordanEliminator.Solution solution = GaussJordanEliminator.solve(
                system.buildMatrixA(),
                system.buildVectorB(),
                true
        );

        if (solution.status() == GaussJordanEliminator.ResultStatus.INFEASIBLE) {
            String label = system.getEquationLabel(solution.infeasibleRowIndex());
            return SolveResult.infeasible(label);
        }

        Map<String, Double> continuousRates = extractContinuousRates(system, solution.values());
        Map<String, Double> finalCounts = quantizeRates(graph, anchor, continuousRates, integerCounts);

        if (solution.status() == GaussJordanEliminator.ResultStatus.UNDER_DETERMINED) {
            return SolveResult.underDetermined(finalCounts);
        }

        return SolveResult.solved(finalCounts);
    }

    private static void buildConservationEquations(FlowGraph graph, LinearEquationSystem system) {
        List<ResourceNet> nets = groupConnectedResourceNets(graph);
        for (ResourceNet net : nets) {
            Map<String, Double> coeffs = new HashMap<>();
            double constant = 0.0;

            for (ProducerPort pp : net.producers) {
                RecipeNode p = graph.findNodeById(pp.nodeId);
                if (p == null || pp.outputIndex >= p.getOutputs().size()) continue;
                IngredientStack outStack = p.getOutputs().get(pp.outputIndex);
                double singleRate = p.calculateSingleMachineOutputRate(outStack);
                coeffs.merge(p.getId(), singleRate, Double::sum);
            }

            for (ConsumerPort cp : net.consumers) {
                RecipeNode c = graph.findNodeById(cp.nodeId);
                if (c == null || cp.inputIndex >= c.getInputs().size()) continue;
                IngredientStack inStack = c.getInputs().get(cp.inputIndex);
                double singleRate = c.calculateSingleMachineInputRate(inStack);
                coeffs.merge(c.getId(), -singleRate, Double::sum);
            }

            for (RecipeNode reroute : net.reroutes) {
                if (reroute.isFixedDrain() && reroute.getExternalDrainRate() > 0.0) {
                    constant += reroute.getExternalDrainRate();
                } else if (reroute.isExternalSupply() && reroute.getExternalSupplyRate() > 0.0) {
                    constant -= reroute.getExternalSupplyRate();
                }
            }

            if (!coeffs.isEmpty()) {
                system.addConservationEquation(net.resourceKey, coeffs, constant);
            }
        }
    }

    private static void buildAnchorEquation(FlowGraph graph, RecipeNode anchor, LinearEquationSystem system) {
        if (!anchor.isReroute()) {
            double target = anchor.getMachineCount() > 0.0001 ? anchor.getMachineCount() : 1.0;
            system.addFixedAnchorEquation(anchor.getId(), target);
        }
    }

    private static void buildSharedPoolEquations(FlowGraph graph, LinearEquationSystem system) {
        if (graph.getFrames() == null) return;
        for (CanvasGroupFrame frame : graph.getFrames()) {
            if (!frame.isSharedMachineFrame()) continue;
            List<String> poolNodeIds = new ArrayList<>();
            for (RecipeNode n : graph.getNodes()) {
                if (frame.containsNode(n.getId()) && !n.isReroute()) {
                    poolNodeIds.add(n.getId());
                }
            }
            if (!poolNodeIds.isEmpty()) {
                system.addSharedPoolCapacityEquation(poolNodeIds, frame.getTargetPoolCapacity());
            }
        }
    }

    private static Map<String, Double> extractContinuousRates(LinearEquationSystem system, double[] values) {
        Map<String, Double> rates = new LinkedHashMap<>();
        if (values == null) return rates;
        for (int i = 0; i < system.getVariableCount(); i++) {
            String nodeId = system.getNodeId(i);
            if (nodeId != null && i < values.length) {
                rates.put(nodeId, values[i]);
            }
        }
        return rates;
    }

    private static Map<String, Double> quantizeRates(FlowGraph graph, RecipeNode anchor, Map<String, Double> continuousRates, boolean integerCounts) {
        Map<String, Double> quantized = new LinkedHashMap<>();
        Set<String> directAnchorSuppliers = com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.getDirectSuppliers(graph, anchor.getId());
        Set<String> downstreamNodes = com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.findDownstreamNodes(graph, anchor.getId(), directAnchorSuppliers);
        Set<String> cyclicNodes = AutoRatioEngine.findCyclicNodeIds(graph, FlowEdgeAllocator.buildEdgeIndex(graph));

        for (Map.Entry<String, Double> entry : continuousRates.entrySet()) {
            RecipeNode node = graph.findNodeById(entry.getKey());
            if (node == null) continue;
            double count = entry.getValue();

            boolean isPureDownstream = downstreamNodes != null
                    && downstreamNodes.contains(node.getId())
                    && (cyclicNodes == null || !cyclicNodes.contains(node.getId()));

            FlowBalanceMatrixSolver.CountRoundingMode mode = isPureDownstream
                    ? FlowBalanceMatrixSolver.CountRoundingMode.FLOOR
                    : FlowBalanceMatrixSolver.CountRoundingMode.CEIL;

            double finalCount = FlowBalanceMatrixSolver.quantizeMachineCount(
                    graph,
                    node,
                    count,
                    mode,
                    integerCounts
            );
            quantized.put(entry.getKey(), finalCount);
        }
        return quantized;
    }

    private record PortRef(String nodeId, boolean isOutput, int portIndex) {}
    private record ProducerPort(String nodeId, int outputIndex) {}
    private record ConsumerPort(String nodeId, int inputIndex) {}

    private static class ResourceNet {
        final String resourceKey;
        final Set<ProducerPort> producers = new LinkedHashSet<>();
        final Set<ConsumerPort> consumers = new LinkedHashSet<>();
        final Set<RecipeNode> reroutes = new LinkedHashSet<>();

        ResourceNet(String resourceKey) {
            this.resourceKey = resourceKey;
        }
    }

    private static class PortDisjointSet {
        private final Map<PortRef, PortRef> parent = new HashMap<>();

        PortRef find(PortRef p) {
            PortRef par = parent.computeIfAbsent(p, k -> k);
            if (par.equals(p)) return p;
            PortRef root = find(par);
            parent.put(p, root);
            return root;
        }

        void union(PortRef p1, PortRef p2) {
            PortRef r1 = find(p1);
            PortRef r2 = find(p2);
            if (!r1.equals(r2)) {
                parent.put(r1, r2);
            }
        }
    }

    private static List<ResourceNet> groupConnectedResourceNets(FlowGraph graph) {
        PortDisjointSet dsu = new PortDisjointSet();

        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            PortRef srcPort = new PortRef(edge.fromNodeId(), true, edge.outputIndex());
            PortRef dstPort = new PortRef(edge.toNodeId(), false, edge.inputIndex());
            dsu.union(srcPort, dstPort);
        }

        for (RecipeNode node : graph.getNodes()) {
            if (node.isReroute()) {
                PortRef inPort = new PortRef(node.getId(), false, 0);
                PortRef outPort = new PortRef(node.getId(), true, 0);
                dsu.union(inPort, outPort);
            }
        }

        Map<PortRef, ResourceNet> netMap = new LinkedHashMap<>();

        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            RecipeNode src = graph.findNodeById(edge.fromNodeId());
            RecipeNode dst = graph.findNodeById(edge.toNodeId());
            if (src == null || dst == null) continue;

            PortRef srcPort = new PortRef(edge.fromNodeId(), true, edge.outputIndex());
            PortRef root = dsu.find(srcPort);

            String resourceKey = resolveEdgeResourceKey(src, edge.outputIndex(), dst, edge.inputIndex());
            if (resourceKey == null) continue;

            ResourceNet net = netMap.computeIfAbsent(root, r -> new ResourceNet(resourceKey));
            registerPortInNet(src, true, edge.outputIndex(), net);
            registerPortInNet(dst, false, edge.inputIndex(), net);
        }

        return new ArrayList<>(netMap.values());
    }

    private static void registerPortInNet(RecipeNode node, boolean isOutput, int portIndex, ResourceNet net) {
        if (node.isReroute()) {
            net.reroutes.add(node);
        } else if (isOutput) {
            net.producers.add(new ProducerPort(node.getId(), portIndex));
        } else {
            net.consumers.add(new ConsumerPort(node.getId(), portIndex));
        }
    }

    private static String resolveEdgeResourceKey(RecipeNode src, int outIdx, RecipeNode dst, int inIdx) {
        if (!src.isReroute() && outIdx < src.getOutputs().size()) {
            IngredientStack stack = src.getOutputs().get(outIdx);
            return stack.getType() + ":" + stack.getId();
        }
        if (!dst.isReroute() && inIdx < dst.getInputs().size()) {
            IngredientStack stack = dst.getInputs().get(inIdx);
            return stack.getType() + ":" + stack.getId();
        }
        return null;
    }
}
