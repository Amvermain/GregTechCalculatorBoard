package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class GroupAutoRatioEngine {

    private GroupAutoRatioEngine() {}

    public static int executeGroupAutoRatio(FlowGraph graph, CanvasGroupFrame frame, AutoRatioMode mode) {
        if (graph == null || frame == null || graph.getNodes().isEmpty()) {
            return 0;
        }

        Set<String> enclosedIds = collectOperationalNodeIds(graph, frame);
        if (enclosedIds.isEmpty() || !hasExternalConnections(graph, enclosedIds)) {
            return 0;
        }

        FlowGraph cloned = graph.copy();
        if (cloned == null) {
            return 0;
        }

        RecipeNode virtualModule = FlowGraphModuleHandler.compressToVirtualModule(cloned, enclosedIds, frame.getTitle());
        if (virtualModule == null) {
            return 0;
        }

        virtualModule.setMachineCount(1.0);
        executeRatioAlgorithm(cloned, virtualModule, mode);

        int changedCount = applyCalculatedCounts(graph, cloned, enclosedIds);
        if (changedCount > 0) {
            FlowGraphSolver.computeSummary(graph);
        }
        return changedCount;
    }

    private static Set<String> collectOperationalNodeIds(FlowGraph graph, CanvasGroupFrame frame) {
        List<RecipeNode> enclosedNodes = frame.getEnclosedNodes(graph);
        Set<String> operationalIds = new LinkedHashSet<>();
        for (RecipeNode n : enclosedNodes) {
            if (n != null && !n.isReroute() && n.isOperational(graph)) {
                operationalIds.add(n.getId());
            }
        }
        return operationalIds;
    }

    private static boolean hasExternalConnections(FlowGraph graph, Set<String> enclosedIds) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            boolean fromIn = enclosedIds.contains(edge.fromNodeId());
            boolean toIn = enclosedIds.contains(edge.toNodeId());
            if (fromIn != toIn) {
                return true;
            }
        }
        return false;
    }

    private static void executeRatioAlgorithm(FlowGraph clonedGraph, RecipeNode virtualModule, AutoRatioMode mode) {
        AutoRatioMode effectiveMode = mode != null ? mode : AutoRatioMode.INTEGER_CEIL;
        switch (effectiveMode) {
            case FRACTIONAL -> FlowGraphSolver.autoRatioFractional(clonedGraph, virtualModule);
            case HARMONIZED -> FlowGraphSolver.autoRatioHarmonized(clonedGraph, virtualModule);
            case INTEGER_CEIL -> FlowGraphSolver.autoRatioFromAnchor(clonedGraph, virtualModule, true);
        }
    }

    private static int applyCalculatedCounts(FlowGraph graph, FlowGraph clonedGraph, Set<String> enclosedIds) {
        int changedCount = 0;
        for (RecipeNode n : graph.getNodes()) {
            if (enclosedIds.contains(n.getId()) || n.isReroute()) {
                continue;
            }
            RecipeNode clonedNode = clonedGraph.findNodeById(n.getId());
            if (clonedNode == null) {
                continue;
            }
            double oldVal = n.getMachineCount();
            double newVal = clonedNode.getMachineCount();
            if (Math.abs(oldVal - newVal) > 1e-4) {
                n.setMachineCount(newVal);
                changedCount++;
            }
        }
        return changedCount;
    }
}
