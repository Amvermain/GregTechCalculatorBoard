package com.gtceu.calcboard.api;

import java.util.*;

/**
 * Handles Compound Module packing, external wire rewiring, and sub-graph expansion for FlowGraph.
 */
public final class FlowGraphModuleHandler {

    private FlowGraphModuleHandler() {}

    /**
     * Groups selected nodes (or all nodes if targetNodeIds is null/empty) into a single Compound Module node.
     */
    public static RecipeNode groupIntoModule(FlowGraph graph, Set<String> targetNodeIds, String moduleName) {
        if (graph == null) return null;

        List<RecipeNode> selectedNodes = new ArrayList<>();
        if (targetNodeIds != null && !targetNodeIds.isEmpty()) {
            for (RecipeNode n : graph.getNodes()) {
                if (targetNodeIds.contains(n.getId())) {
                    selectedNodes.add(n);
                }
            }
        } else {
            selectedNodes.addAll(graph.getNodes());
        }

        if (selectedNodes.isEmpty()) return null;

        Set<String> selectedIdSet = new HashSet<>();
        for (RecipeNode n : selectedNodes) selectedIdSet.add(n.getId());

        // 1. Create subGraph with selected nodes and their internal connections
        FlowGraph subGraph = new FlowGraph();
        for (RecipeNode n : selectedNodes) {
            subGraph.addNode(n);
        }
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (selectedIdSet.contains(edge.fromNodeId()) && selectedIdSet.contains(edge.toNodeId())) {
                subGraph.addConnection(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex());
            }
        }

        // 2. Calculate balance summary for subGraph
        BalanceSummary summary = FlowGraphSolver.computeSummary(subGraph);

        // 3. Compute centroid position
        double sumX = 0, sumY = 0;
        for (RecipeNode n : selectedNodes) {
            sumX += n.getPosX();
            sumY += n.getPosY();
        }
        double centerX = sumX / selectedNodes.size();
        double centerY = sumY / selectedNodes.size();

        // 4. Create Module RecipeNode
        String name = (moduleName != null && !moduleName.trim().isEmpty()) ? moduleName.trim() : "Compound Module";
        double baseEUt = Math.max(1.0, Math.abs(summary.totalEUt()));
        boolean isGen = summary.totalEUt() < -0.001;
        GTVoltageTier tier = summary.highestVoltageTier();

        // Standard 1.0 second duration (20 ticks) base for modules
        RecipeNode moduleNode = RecipeNode.create(name, 20.0, baseEUt, tier);
        moduleNode.setModule(true);
        moduleNode.setSubGraph(subGraph);
        moduleNode.setContainedMachineCount(summary.totalMachineCount());
        moduleNode.setGenerator(isGen);
        moduleNode.setPos(centerX, centerY);
        moduleNode.setCardWidth(230); // Default wider card for compound modules

        // Add net external inputs
        for (Map.Entry<IngredientStack, Double> entry : summary.rawInputs().entrySet()) {
            IngredientStack original = entry.getKey();
            double ratePerSec = entry.getValue();
            IngredientStack netIn = original.isFluid()
                ? IngredientStack.fluid(original.getId(), original.getDisplayName(), ratePerSec, 1.0)
                : IngredientStack.item(original.getId(), original.getDisplayName(), ratePerSec, 1.0);
            moduleNode.addInput(netIn);
        }

        // Add net external outputs
        for (Map.Entry<IngredientStack, Double> entry : summary.netOutputs().entrySet()) {
            IngredientStack original = entry.getKey();
            double ratePerSec = entry.getValue();
            IngredientStack netOut = original.isFluid()
                ? IngredientStack.fluid(original.getId(), original.getDisplayName(), ratePerSec, 1.0)
                : IngredientStack.item(original.getId(), original.getDisplayName(), ratePerSec, 1.0);
            moduleNode.addOutput(netOut);
        }

        // 5. External connections rewiring
        List<FlowGraph.ConnectionEdge> externalEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            boolean fromSelected = selectedIdSet.contains(edge.fromNodeId());
            boolean toSelected = selectedIdSet.contains(edge.toNodeId());

            if (fromSelected && !toSelected) {
                // Outgoing wire from module to outside
                RecipeNode origFrom = graph.findNodeById(edge.fromNodeId());
                if (origFrom != null && edge.outputIndex() < origFrom.getOutputs().size()) {
                    IngredientStack outStack = origFrom.getOutputs().get(edge.outputIndex());
                    // Find matching output port on moduleNode
                    for (int mOutIdx = 0; mOutIdx < moduleNode.getOutputs().size(); mOutIdx++) {
                        if (moduleNode.getOutputs().get(mOutIdx).equals(outStack)) {
                            externalEdges.add(new FlowGraph.ConnectionEdge(moduleNode.getId(), mOutIdx, edge.toNodeId(), edge.inputIndex()));
                            break;
                        }
                    }
                }
            } else if (!fromSelected && toSelected) {
                // Incoming wire from outside into module
                RecipeNode origTo = graph.findNodeById(edge.toNodeId());
                if (origTo != null && edge.inputIndex() < origTo.getInputs().size()) {
                    IngredientStack inStack = origTo.getInputs().get(edge.inputIndex());
                    // Find matching input port on moduleNode
                    for (int mInIdx = 0; mInIdx < moduleNode.getInputs().size(); mInIdx++) {
                        if (moduleNode.getInputs().get(mInIdx).equals(inStack)) {
                            externalEdges.add(new FlowGraph.ConnectionEdge(edge.fromNodeId(), edge.outputIndex(), moduleNode.getId(), mInIdx));
                            break;
                        }
                    }
                }
            } else if (!fromSelected && !toSelected) {
                // Unrelated wire outside
                externalEdges.add(edge);
            }
        }

        // 6. Update this graph
        for (RecipeNode n : selectedNodes) {
            graph.removeNode(n);
        }
        graph.addNode(moduleNode);
        graph.getConnections().clear();
        graph.getConnections().addAll(externalEdges);

        return moduleNode;
    }

    /**
     * Expands a Compound Module back into its constituent sub-graph nodes and connections.
     */
    public static boolean expandModule(FlowGraph graph, RecipeNode moduleNode) {
        if (graph == null || moduleNode == null || !moduleNode.isModule() || moduleNode.getSubGraph() == null) {
            return false;
        }

        FlowGraph subGraph = moduleNode.getSubGraph();
        if (subGraph.getNodes().isEmpty()) return false;

        // Calculate centroid of subGraph to apply relative positioning offset
        double sumX = 0, sumY = 0;
        for (RecipeNode n : subGraph.getNodes()) {
            sumX += n.getPosX();
            sumY += n.getPosY();
        }
        double origCenterX = sumX / subGraph.getNodes().size();
        double origCenterY = sumY / subGraph.getNodes().size();

        double offsetX = moduleNode.getPosX() - origCenterX;
        double offsetY = moduleNode.getPosY() - origCenterY;
        double moduleScale = moduleNode.getMachineCount();

        // Remove moduleNode
        graph.removeNode(moduleNode);

        // Restore subGraph nodes
        for (RecipeNode n : subGraph.getNodes()) {
            n.setPos(n.getPosX() + offsetX, n.getPosY() + offsetY);
            if (moduleScale > 1.0) {
                n.setMachineCount(n.getMachineCount() * moduleScale);
            }
            graph.addNode(n);
        }

        // Restore subGraph connections
        for (FlowGraph.ConnectionEdge edge : subGraph.getConnections()) {
            graph.addConnection(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex());
        }
        return true;
    }
}
