package com.gtceu.calcboard.api.solver.linear;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.*;

/**
 * Builds linear equation systems (A * x = b) representing material balance conservation,
 * anchor constraints, and shared machine pool capacity bounds across a flow graph.
 */
public final class LinearEquationSystem {

    private final List<String> variableNodeIds = new ArrayList<>();
    private final Map<String, Integer> nodeIndexMap = new HashMap<>();
    private final List<double[]> equationsA = new ArrayList<>();
    private final List<Double> equationsB = new ArrayList<>();
    private final List<String> equationLabels = new ArrayList<>();

    public LinearEquationSystem(FlowGraph graph) {
        this(graph, null);
    }

    public LinearEquationSystem(FlowGraph graph, RecipeNode anchor) {
        if (graph == null) return;

        Set<String> orderedNodeIds = new LinkedHashSet<>();
        if (anchor != null && !anchor.isReroute()) {
            orderedNodeIds.add(anchor.getId());
            Set<String> directSuppliers = com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.getDirectSuppliers(graph, anchor.getId());
            Set<String> down = com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.findDownstreamNodes(graph, anchor.getId(), directSuppliers);
            if (down != null) orderedNodeIds.addAll(down);
            Set<String> up = com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.findUpstreamNodes(graph, anchor.getId(), down);
            if (up != null) orderedNodeIds.addAll(up);
        }

        for (RecipeNode node : graph.getNodes()) {
            if (node == null || node.isReroute()) continue;
            orderedNodeIds.add(node.getId());
        }

        for (String id : orderedNodeIds) {
            RecipeNode n = graph.findNodeById(id);
            if (n != null && !n.isReroute()) {
                nodeIndexMap.put(id, variableNodeIds.size());
                variableNodeIds.add(id);
            }
        }
    }

    public List<String> getVariableNodeIds() {
        return Collections.unmodifiableList(variableNodeIds);
    }

    public int getVariableCount() {
        return variableNodeIds.size();
    }

    public int getEquationCount() {
        return equationsA.size();
    }

    public void addConservationEquation(String resourceLabel, Map<String, Double> coefficients, double constant) {
        if (coefficients == null || coefficients.isEmpty()) return;
        double[] row = new double[variableNodeIds.size()];
        boolean hasNonZero = false;
        for (Map.Entry<String, Double> entry : coefficients.entrySet()) {
            Integer idx = nodeIndexMap.get(entry.getKey());
            if (idx == null) continue;
            row[idx] = entry.getValue();
            if (Math.abs(entry.getValue()) > 1e-9) {
                hasNonZero = true;
            }
        }
        if (hasNonZero || Math.abs(constant) > 1e-9) {
            equationsA.add(row);
            equationsB.add(constant);
            equationLabels.add(resourceLabel);
        }
    }

    public void addFixedAnchorEquation(String nodeId, double fixedCount) {
        Integer idx = nodeIndexMap.get(nodeId);
        if (idx == null) return;
        double[] row = new double[variableNodeIds.size()];
        row[idx] = 1.0;
        equationsA.add(row);
        equationsB.add(fixedCount);
        equationLabels.add("anchor:" + nodeId);
    }

    public void addSharedPoolCapacityEquation(Collection<String> poolNodeIds, double totalCapacity) {
        if (poolNodeIds == null || poolNodeIds.isEmpty()) return;
        double[] row = new double[variableNodeIds.size()];
        boolean found = false;
        for (String id : poolNodeIds) {
            Integer idx = nodeIndexMap.get(id);
            if (idx != null) {
                row[idx] = 1.0;
                found = true;
            }
        }
        if (found) {
            equationsA.add(row);
            equationsB.add(totalCapacity);
            equationLabels.add("shared_pool");
        }
    }

    public double[][] buildMatrixA() {
        return equationsA.toArray(new double[0][]);
    }

    public double[] buildVectorB() {
        double[] b = new double[equationsB.size()];
        for (int i = 0; i < equationsB.size(); i++) {
            b[i] = equationsB.get(i);
        }
        return b;
    }

    public String getEquationLabel(int index) {
        if (index >= 0 && index < equationLabels.size()) {
            return equationLabels.get(index);
        }
        return "unknown";
    }

    public Integer getNodeIndex(String nodeId) {
        return nodeIndexMap.get(nodeId);
    }

    public String getNodeId(int index) {
        if (index >= 0 && index < variableNodeIds.size()) {
            return variableNodeIds.get(index);
        }
        return null;
    }
}
