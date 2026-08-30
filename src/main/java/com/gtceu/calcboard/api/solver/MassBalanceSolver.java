package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;

import java.util.*;

/**
 * Mass-conservative linear equation system solver (A x = b) for closed-loop / cyclic chemical networks.
 * Uses Gauss-Jordan elimination with partial pivoting.
 */
public final class MassBalanceSolver {

    private static final double EPSILON = 1e-9;

    private MassBalanceSolver() {}

    /**
     * Solves the mass balance equations for all active machines connected in the graph
     * to satisfy the anchor machine's target count and closed-loop material flows.
     *
     * @param graph The canvas flow graph.
     * @param anchor The anchor recipe node.
     * @param targetAnchorCount The target count of the anchor node.
     * @return Map of NodeId to required machine count, or null if system is inconsistent/singular.
     */
    public static Map<String, Double> solve(FlowGraph graph, RecipeNode anchor, double targetAnchorCount) {
        if (graph == null || anchor == null || graph.getNodes().isEmpty()) {
            return null;
        }

        // 1. Collect active non-reroute nodes
        List<RecipeNode> activeNodes = new ArrayList<>();
        Map<String, Integer> nodeIndices = new HashMap<>();
        for (RecipeNode node : graph.getNodes()) {
            if (!node.isReroute()) {
                nodeIndices.put(node.getId(), activeNodes.size());
                activeNodes.add(node);
            }
        }

        int n = activeNodes.size();
        if (n == 0 || !nodeIndices.containsKey(anchor.getId())) {
            return null;
        }

        int anchorIdx = nodeIndices.get(anchor.getId());

        // 2. Identify unique connected internal materials across connections
        // Material Key -> List of connected (producer/consumer) rate contributions
        Set<String> connectedMaterialKeys = new LinkedHashSet<>();
        Map<String, List<FlowGraph.ConnectionEdge>> materialEdges = new LinkedHashMap<>();

        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            RecipeNode producer = graph.findNodeById(edge.fromNodeId());
            RecipeNode consumer = graph.findNodeById(edge.toNodeId());
            if (producer == null || consumer == null) continue;

            // Resolve real producer/consumer across reroutes
            List<ResolvedEdge> resolvedList = resolveReroutes(graph, edge);
            for (ResolvedEdge re : resolvedList) {
                if (re.producer.isReroute() || re.consumer.isReroute()) continue;
                if (re.outputIndex < re.producer.getOutputs().size() && re.inputIndex < re.consumer.getInputs().size()) {
                    IngredientStack outStack = re.producer.getOutputs().get(re.outputIndex);
                    String matKey = getMaterialKey(outStack);
                    connectedMaterialKeys.add(matKey);
                    materialEdges.computeIfAbsent(matKey, k -> new ArrayList<>()).add(edge);
                }
            }
        }

        List<String> materials = new ArrayList<>(connectedMaterialKeys);
        int m = materials.size();

        // 3. Build Augmented Matrix [A | b] of size (m + 1) rows x (n + 1) cols
        // Rows 0..m-1: Mass balance for each connected material (Production - Consumption = 0)
        // Row m: Anchor constraint (x_anchor = targetAnchorCount)
        int totalRows = m + 1;
        int totalCols = n + 1;
        double[][] matrix = new double[totalRows][totalCols];

        for (int k = 0; k < m; k++) {
            String matKey = materials.get(k);

            for (int j = 0; j < n; j++) {
                RecipeNode node = activeNodes.get(j);
                // Production contribution
                double prodRate = 0.0;
                for (int oIdx = 0; oIdx < node.getOutputs().size(); oIdx++) {
                    IngredientStack out = node.getOutputs().get(oIdx);
                    if (getMaterialKey(out).equals(matKey) && isPortConnected(graph, node.getId(), oIdx, true)) {
                        prodRate += node.calculateSingleMachineOutputRate(out);
                    }
                }

                // Consumption contribution
                double consRate = 0.0;
                for (int iIdx = 0; iIdx < node.getInputs().size(); iIdx++) {
                    IngredientStack in = node.getInputs().get(iIdx);
                    if (getMaterialKey(in).equals(matKey) && isPortConnected(graph, node.getId(), iIdx, false)) {
                        consRate += node.calculateSingleMachineInputRate(in);
                    }
                }

                matrix[k][j] = prodRate - consRate;
            }
            matrix[k][n] = 0.0; // b[k] = 0 (balanced intermediate flow)
        }

        // Anchor constraint row
        matrix[m][anchorIdx] = 1.0;
        matrix[m][n] = targetAnchorCount;

        // 4. Gauss-Jordan Elimination with Partial Pivoting
        boolean solved = gaussJordan(matrix, totalRows, n);
        if (!solved) {
            return null; // Singular or inconsistent system
        }

        // 5. Extract solution and validate
        double[] x = new double[n];
        boolean[] hasRowForVar = new boolean[n];

        for (int r = 0; r < totalRows; r++) {
            int leadCol = -1;
            for (int c = 0; c < n; c++) {
                if (Math.abs(matrix[r][c] - 1.0) < 1e-5) {
                    boolean allOtherZero = true;
                    for (int c2 = 0; c2 < n; c2++) {
                        if (c2 != c && Math.abs(matrix[r][c2]) > 1e-5) {
                            allOtherZero = false;
                            break;
                        }
                    }
                    if (allOtherZero) {
                        leadCol = c;
                        break;
                    }
                }
            }

            if (leadCol >= 0) {
                x[leadCol] = matrix[r][n];
                hasRowForVar[leadCol] = true;
            } else {
                // Check if inconsistency (0 == non-zero)
                boolean allZeros = true;
                for (int c = 0; c < n; c++) {
                    if (Math.abs(matrix[r][c]) > EPSILON) {
                        allZeros = false;
                        break;
                    }
                }
                if (allZeros && Math.abs(matrix[r][n]) > 1e-4) {
                    return null; // Inconsistent system
                }
            }
        }

        // Any unconstrained free variable defaults to at least 1.0 or connected proportion
        for (int j = 0; j < n; j++) {
            if (!hasRowForVar[j] || Double.isNaN(x[j]) || Double.isInfinite(x[j]) || x[j] < 0.0) {
                if (j == anchorIdx) {
                    x[j] = targetAnchorCount;
                } else {
                    x[j] = Math.max(0.01, activeNodes.get(j).getMachineCount());
                }
            }
        }

        Map<String, Double> resultMap = new LinkedHashMap<>();
        for (int j = 0; j < n; j++) {
            resultMap.put(activeNodes.get(j).getId(), x[j]);
        }

        return resultMap;
    }

    private static boolean gaussJordan(double[][] a, int rows, int cols) {
        int r = 0;
        for (int c = 0; c < cols && r < rows; c++) {
            // Find pivot in column c from row r onwards
            int maxRow = r;
            double maxVal = Math.abs(a[r][c]);
            for (int i = r + 1; i < rows; i++) {
                double val = Math.abs(a[i][c]);
                if (val > maxVal) {
                    maxVal = val;
                    maxRow = i;
                }
            }

            if (maxVal < EPSILON) {
                continue; // Column is linearly dependent, skip
            }

            // Swap current row with pivot row
            if (maxRow != r) {
                double[] temp = a[r];
                a[r] = a[maxRow];
                a[maxRow] = temp;
            }

            // Scale pivot row so pivot element is 1.0
            double pivot = a[r][c];
            for (int j = c; j <= cols; j++) {
                a[r][j] /= pivot;
            }

            // Eliminate column c in all other rows
            for (int i = 0; i < rows; i++) {
                if (i != r) {
                    double factor = a[i][c];
                    if (Math.abs(factor) > EPSILON) {
                        for (int j = c; j <= cols; j++) {
                            a[i][j] -= factor * a[r][j];
                        }
                    }
                }
            }

            r++;
        }

        return true;
    }

    private static String getMaterialKey(IngredientStack stack) {
        if (stack == null || stack.getId() == null) return "unknown";
        return (stack.isFluid() ? "fluid:" : "item:") + stack.getId().toString();
    }

    private static boolean isPortConnected(FlowGraph graph, String nodeId, int portIndex, boolean isOutput) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (isOutput) {
                if (edge.fromNodeId().equals(nodeId) && edge.outputIndex() == portIndex) {
                    return true;
                }
            } else {
                if (edge.toNodeId().equals(nodeId) && edge.inputIndex() == portIndex) {
                    return true;
                }
            }
        }
        return false;
    }

    private record ResolvedEdge(RecipeNode producer, int outputIndex, RecipeNode consumer, int inputIndex) {}

    private static List<ResolvedEdge> resolveReroutes(FlowGraph graph, FlowGraph.ConnectionEdge edge) {
        List<ResolvedEdge> list = new ArrayList<>();
        RecipeNode from = graph.findNodeById(edge.fromNodeId());
        RecipeNode to = graph.findNodeById(edge.toNodeId());
        if (from == null || to == null) return list;

        List<RecipeNode> actualProducers = new ArrayList<>();
        List<Integer> actualOutIndices = new ArrayList<>();
        if (from.isReroute()) {
            findUpstreamProducers(graph, from.getId(), actualProducers, actualOutIndices);
        } else {
            actualProducers.add(from);
            actualOutIndices.add(edge.outputIndex());
        }

        List<RecipeNode> actualConsumers = new ArrayList<>();
        List<Integer> actualInIndices = new ArrayList<>();
        if (to.isReroute()) {
            findDownstreamConsumers(graph, to.getId(), actualConsumers, actualInIndices);
        } else {
            actualConsumers.add(to);
            actualInIndices.add(edge.inputIndex());
        }

        for (int p = 0; p < actualProducers.size(); p++) {
            for (int c = 0; c < actualConsumers.size(); c++) {
                list.add(new ResolvedEdge(actualProducers.get(p), actualOutIndices.get(p), actualConsumers.get(c), actualInIndices.get(c)));
            }
        }
        return list;
    }

    private static void findUpstreamProducers(FlowGraph graph, String startRerouteId, List<RecipeNode> producers, List<Integer> outIndices) {
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(startRerouteId);
        visited.add(startRerouteId);

        while (!queue.isEmpty()) {
            String currId = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.toNodeId().equals(currId)) {
                    RecipeNode src = graph.findNodeById(edge.fromNodeId());
                    if (src != null) {
                        if (src.isReroute()) {
                            if (visited.add(src.getId())) {
                                queue.add(src.getId());
                            }
                        } else {
                            producers.add(src);
                            outIndices.add(edge.outputIndex());
                        }
                    }
                }
            }
        }
    }

    private static void findDownstreamConsumers(FlowGraph graph, String startRerouteId, List<RecipeNode> consumers, List<Integer> inIndices) {
        Queue<String> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        queue.add(startRerouteId);
        visited.add(startRerouteId);

        while (!queue.isEmpty()) {
            String currId = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (edge.fromNodeId().equals(currId)) {
                    RecipeNode dst = graph.findNodeById(edge.toNodeId());
                    if (dst != null) {
                        if (dst.isReroute()) {
                            if (visited.add(dst.getId())) {
                                queue.add(dst.getId());
                            }
                        } else {
                            consumers.add(dst);
                            inIndices.add(edge.inputIndex());
                        }
                    }
                }
            }
        }
    }
}
