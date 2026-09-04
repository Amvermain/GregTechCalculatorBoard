package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;

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
        return groupIntoModule(graph, targetNodeIds, moduleName, null);
    }

    public static RecipeNode groupIntoModule(FlowGraph graph, Set<String> targetNodeIds, String moduleName, CanvasGroupFrame primaryFrame) {
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

        // Ensure full graph is evaluated so nodes hold valid, active efficiencies and flows
        FlowGraphSolver.computeSummary(graph);

        Set<String> selectedIdSet = new HashSet<>();
        for (RecipeNode n : selectedNodes) selectedIdSet.add(n.getId());

        FlowGraph subGraph = buildSubGraph(selectedNodes, graph.getConnections(), selectedIdSet);
        BalanceSummary summary = FlowGraphSolver.computeSummaryPreservingEfficiencies(subGraph);

        RecipeNode moduleNode = createModuleNode(selectedNodes, summary, moduleName, subGraph);
        transferFramesAndNotes(graph, subGraph, primaryFrame, selectedNodes, selectedIdSet);

        List<FlowGraph.ConnectionEdge> externalEdges = new ArrayList<>();
        allocateModulePortsAndRewireEdges(graph, subGraph, selectedNodes, selectedIdSet, summary, moduleNode, externalEdges);

        updateGraphWithModule(graph, selectedNodes, moduleNode, externalEdges);
        return moduleNode;
    }

    private record PortKey(String nodeId, int portIndex) {}

    private static FlowGraph buildSubGraph(List<RecipeNode> selectedNodes, List<FlowGraph.ConnectionEdge> edges, Set<String> selectedIdSet) {
        FlowGraph subGraph = new FlowGraph();
        for (RecipeNode n : selectedNodes) {
            subGraph.addNode(n);
        }
        for (FlowGraph.ConnectionEdge edge : edges) {
            if (selectedIdSet.contains(edge.fromNodeId()) && selectedIdSet.contains(edge.toNodeId())) {
                subGraph.addConnection(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex());
            }
        }
        return subGraph;
    }

    private static RecipeNode createModuleNode(List<RecipeNode> selectedNodes, BalanceSummary summary, String moduleName, FlowGraph subGraph) {
        double sumX = 0, sumY = 0;
        for (RecipeNode n : selectedNodes) {
            sumX += n.getPosX();
            sumY += n.getPosY();
        }
        double centerX = sumX / selectedNodes.size();
        double centerY = sumY / selectedNodes.size();

        String name = (moduleName != null && !moduleName.trim().isEmpty()) ? moduleName.trim() : "Compound Module";
        double baseEUt = Math.max(1.0, Math.abs(summary.totalEUt()));
        boolean isGen = summary.totalEUt() < -0.001;
        GTVoltageTier tier = summary.highestVoltageTier();

        RecipeNode moduleNode = RecipeNode.create(name, 20.0, baseEUt, tier);
        moduleNode.setModule(true);
        moduleNode.setSubGraph(subGraph);
        moduleNode.setContainedMachineCount(summary.totalMachineCount());
        moduleNode.setGenerator(isGen);
        moduleNode.setPos(centerX, centerY);
        moduleNode.setCardWidth(230);
        return moduleNode;
    }

    private static void transferFramesAndNotes(
            FlowGraph graph,
            FlowGraph subGraph,
            CanvasGroupFrame primaryFrame,
            List<RecipeNode> selectedNodes,
            Set<String> selectedIdSet
    ) {
        List<CanvasGroupFrame> capturedFrames = new ArrayList<>();
        if (primaryFrame != null) {
            capturedFrames.add(primaryFrame);
            for (CanvasGroupFrame f : graph.getFrames()) {
                if (!f.equals(primaryFrame) && isFrameStrictlyInside(f, primaryFrame)) {
                    capturedFrames.add(f);
                }
            }
        } else {
            List<CanvasGroupFrame> candidateFrames = findCandidateFrames(graph, selectedIdSet);
            if (!candidateFrames.isEmpty()) {
                CanvasGroupFrame tightestFrame = findTightestFrame(candidateFrames);
                if (tightestFrame != null) {
                    capturedFrames.add(tightestFrame);
                    for (CanvasGroupFrame cf : candidateFrames) {
                        if (!cf.equals(tightestFrame) && isFrameStrictlyInside(cf, tightestFrame)) {
                            capturedFrames.add(cf);
                        }
                    }
                }
            }
        }

        Set<CanvasStickyNote> capturedNotes = new HashSet<>();
        for (CanvasGroupFrame f : capturedFrames) {
            capturedNotes.addAll(f.getEnclosedNotes(graph));
        }

        if (!selectedNodes.isEmpty()) {
            capturedNotes.addAll(findSpatiallyEnclosedNotes(graph, selectedNodes));
        }

        for (CanvasGroupFrame f : capturedFrames) {
            graph.removeFrame(f);
            subGraph.addFrame(f);
        }
        for (CanvasStickyNote note : capturedNotes) {
            graph.removeStickyNote(note);
            subGraph.addStickyNote(note);
        }
    }

    private static boolean isFrameStrictlyInside(CanvasGroupFrame inner, CanvasGroupFrame outer) {
        return inner.getPosX() >= outer.getPosX() - 5
                && inner.getPosY() >= outer.getPosY() - 5
                && inner.getPosX() + inner.getWidth() <= outer.getPosX() + outer.getWidth() + 5
                && inner.getPosY() + inner.getHeight() <= outer.getPosY() + outer.getHeight() + 5;
    }

    private static List<CanvasGroupFrame> findCandidateFrames(FlowGraph graph, Set<String> selectedIdSet) {
        List<CanvasGroupFrame> candidateFrames = new ArrayList<>();
        for (CanvasGroupFrame f : graph.getFrames()) {
            List<RecipeNode> enclosed = f.getEnclosedNodes(graph);
            if (!enclosed.isEmpty()) {
                boolean allSelected = true;
                for (RecipeNode n : enclosed) {
                    if (!selectedIdSet.contains(n.getId())) {
                        allSelected = false;
                        break;
                    }
                }
                if (allSelected) {
                    candidateFrames.add(f);
                }
            }
        }
        return candidateFrames;
    }

    private static CanvasGroupFrame findTightestFrame(List<CanvasGroupFrame> frames) {
        CanvasGroupFrame tightest = null;
        double minArea = Double.MAX_VALUE;
        for (CanvasGroupFrame f : frames) {
            double area = f.getWidth() * f.getHeight();
            if (area < minArea) {
                minArea = area;
                tightest = f;
            }
        }
        return tightest;
    }

    private static Set<CanvasStickyNote> findSpatiallyEnclosedNotes(FlowGraph graph, List<RecipeNode> selectedNodes) {
        Set<CanvasStickyNote> notes = new HashSet<>();
        double selMinX = Double.MAX_VALUE, selMinY = Double.MAX_VALUE;
        double selMaxX = -Double.MAX_VALUE, selMaxY = -Double.MAX_VALUE;
        for (RecipeNode n : selectedNodes) {
            selMinX = Math.min(selMinX, n.getPosX());
            selMinY = Math.min(selMinY, n.getPosY());
            selMaxX = Math.max(selMaxX, n.getPosX() + n.getCardWidth());
            selMaxY = Math.max(selMaxY, n.getPosY() + (n.getCardHeight() > 0 ? n.getCardHeight() : 160));
        }
        for (CanvasStickyNote note : graph.getStickyNotes()) {
            if (note.getPosX() >= selMinX - 10 && note.getPosY() >= selMinY - 10
                    && note.getPosX() + note.getWidth() <= selMaxX + 10
                    && note.getPosY() + note.getHeight() <= selMaxY + 10) {
                notes.add(note);
            }
        }
        return notes;
    }

    private static void allocateModulePortsAndRewireEdges(
            FlowGraph graph,
            FlowGraph subGraph,
            List<RecipeNode> selectedNodes,
            Set<String> selectedIdSet,
            BalanceSummary summary,
            RecipeNode moduleNode,
            List<FlowGraph.ConnectionEdge> externalEdges
    ) {
        Map<PortKey, Integer> inPortMap = new LinkedHashMap<>();
        Map<PortKey, Integer> outPortMap = new LinkedHashMap<>();

        allocateIncomingBoundaryPorts(graph, moduleNode, selectedIdSet, inPortMap, externalEdges);
        allocateOutgoingBoundaryPorts(graph, moduleNode, selectedIdSet, outPortMap, externalEdges);

        allocateUnconnectedNetInputs(summary, selectedNodes, moduleNode, inPortMap);
        allocateUnconnectedNetOutputs(summary, selectedNodes, moduleNode, outPortMap);

        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            boolean fromSelected = selectedIdSet.contains(edge.fromNodeId());
            boolean toSelected = selectedIdSet.contains(edge.toNodeId());
            if (!fromSelected && !toSelected) {
                externalEdges.add(edge);
            }
        }
    }

    private static void allocateIncomingBoundaryPorts(
            FlowGraph graph,
            RecipeNode moduleNode,
            Set<String> selectedIdSet,
            Map<PortKey, Integer> inPortMap,
            List<FlowGraph.ConnectionEdge> externalEdges
    ) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            boolean fromSelected = selectedIdSet.contains(edge.fromNodeId());
            boolean toSelected = selectedIdSet.contains(edge.toNodeId());
            if (!fromSelected && toSelected) {
                PortKey key = new PortKey(edge.toNodeId(), edge.inputIndex());
                int modulePortIdx;
                if (!inPortMap.containsKey(key)) {
                    RecipeNode targetNode = graph.findNodeById(edge.toNodeId());
                    if (targetNode != null && edge.inputIndex() < targetNode.getInputs().size()) {
                        IngredientStack orig = targetNode.getInputs().get(edge.inputIndex());
                        double reqRate = targetNode.getInputSlotRate(edge.inputIndex(), true);
                        IngredientStack portStack = orig.isFluid()
                                ? IngredientStack.fluid(orig.getId(), orig.getDisplayName(), reqRate, 1.0)
                                : IngredientStack.item(orig.getId(), orig.getDisplayName(), reqRate, 1.0);
                        modulePortIdx = moduleNode.getInputs().size();
                        moduleNode.addInput(portStack);
                        moduleNode.getModuleInputOrigins().add(new ArrayList<>(List.of(
                                new RecipeNode.PortOrigin(edge.toNodeId(), edge.inputIndex())
                        )));
                        inPortMap.put(key, modulePortIdx);
                    } else {
                        continue;
                    }
                } else {
                    modulePortIdx = inPortMap.get(key);
                }
                externalEdges.add(new FlowGraph.ConnectionEdge(edge.fromNodeId(), edge.outputIndex(), moduleNode.getId(), modulePortIdx));
            }
        }
    }

    private static void allocateOutgoingBoundaryPorts(
            FlowGraph graph,
            RecipeNode moduleNode,
            Set<String> selectedIdSet,
            Map<PortKey, Integer> outPortMap,
            List<FlowGraph.ConnectionEdge> externalEdges
    ) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            boolean fromSelected = selectedIdSet.contains(edge.fromNodeId());
            boolean toSelected = selectedIdSet.contains(edge.toNodeId());
            if (fromSelected && !toSelected) {
                PortKey key = new PortKey(edge.fromNodeId(), edge.outputIndex());
                int modulePortIdx;
                if (!outPortMap.containsKey(key)) {
                    RecipeNode sourceNode = graph.findNodeById(edge.fromNodeId());
                    if (sourceNode != null && edge.outputIndex() < sourceNode.getOutputs().size()) {
                        IngredientStack orig = sourceNode.getOutputs().get(edge.outputIndex());
                        double prodRate = sourceNode.getOutputSlotRate(edge.outputIndex(), true);
                        IngredientStack portStack = orig.isFluid()
                                ? IngredientStack.fluid(orig.getId(), orig.getDisplayName(), prodRate, 1.0)
                                : IngredientStack.item(orig.getId(), orig.getDisplayName(), prodRate, 1.0);
                        modulePortIdx = moduleNode.getOutputs().size();
                        moduleNode.addOutput(portStack);
                        moduleNode.getModuleOutputOrigins().add(new ArrayList<>(List.of(
                                new RecipeNode.PortOrigin(edge.fromNodeId(), edge.outputIndex())
                        )));
                        outPortMap.put(key, modulePortIdx);
                    } else {
                        continue;
                    }
                } else {
                    modulePortIdx = outPortMap.get(key);
                }
                externalEdges.add(new FlowGraph.ConnectionEdge(moduleNode.getId(), modulePortIdx, edge.toNodeId(), edge.inputIndex()));
            }
        }
    }

    private static void allocateUnconnectedNetInputs(
            BalanceSummary summary,
            List<RecipeNode> selectedNodes,
            RecipeNode moduleNode,
            Map<PortKey, Integer> inPortMap
    ) {
        for (Map.Entry<IngredientStack, Double> entry : summary.rawInputs().entrySet()) {
            IngredientStack original = entry.getKey();
            List<RecipeNode.PortOrigin> unassignedOrigins = new ArrayList<>();
            for (RecipeNode sn : selectedNodes) {
                for (int pInIdx = 0; pInIdx < sn.getInputs().size(); pInIdx++) {
                    if (sn.getInputs().get(pInIdx).equals(original) && !inPortMap.containsKey(new PortKey(sn.getId(), pInIdx))) {
                        unassignedOrigins.add(new RecipeNode.PortOrigin(sn.getId(), pInIdx));
                    }
                }
            }

            if (!unassignedOrigins.isEmpty()) {
                double ratePerSec = entry.getValue();
                IngredientStack netIn = original.isFluid()
                        ? IngredientStack.fluid(original.getId(), original.getDisplayName(), ratePerSec, 1.0)
                        : IngredientStack.item(original.getId(), original.getDisplayName(), ratePerSec, 1.0);
                moduleNode.addInput(netIn);
                moduleNode.getModuleInputOrigins().add(unassignedOrigins);
            }
        }
    }

    private static void allocateUnconnectedNetOutputs(
            BalanceSummary summary,
            List<RecipeNode> selectedNodes,
            RecipeNode moduleNode,
            Map<PortKey, Integer> outPortMap
    ) {
        for (Map.Entry<IngredientStack, Double> entry : summary.netOutputs().entrySet()) {
            IngredientStack original = entry.getKey();
            List<RecipeNode.PortOrigin> unassignedOrigins = new ArrayList<>();
            for (RecipeNode sn : selectedNodes) {
                for (int pOutIdx = 0; pOutIdx < sn.getOutputs().size(); pOutIdx++) {
                    if (sn.getOutputs().get(pOutIdx).equals(original) && !outPortMap.containsKey(new PortKey(sn.getId(), pOutIdx))) {
                        unassignedOrigins.add(new RecipeNode.PortOrigin(sn.getId(), pOutIdx));
                    }
                }
            }

            if (!unassignedOrigins.isEmpty()) {
                double ratePerSec = entry.getValue();
                IngredientStack netOut = original.isFluid()
                        ? IngredientStack.fluid(original.getId(), original.getDisplayName(), ratePerSec, 1.0)
                        : IngredientStack.item(original.getId(), original.getDisplayName(), ratePerSec, 1.0);
                moduleNode.addOutput(netOut);
                moduleNode.getModuleOutputOrigins().add(unassignedOrigins);
            }
        }
    }

    private static void updateGraphWithModule(
            FlowGraph graph,
            List<RecipeNode> selectedNodes,
            RecipeNode moduleNode,
            List<FlowGraph.ConnectionEdge> externalEdges
    ) {
        for (RecipeNode n : selectedNodes) {
            graph.removeNode(n);
        }
        graph.addNode(moduleNode);
        graph.getConnections().clear();
        graph.getConnections().addAll(externalEdges);
    }

    /**
     * Expands a Compound Module back into its constituent sub-graph nodes, frames, and N:N connections.
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

        // 1. Rewire existing external connections using PortOrigins
        List<FlowGraph.ConnectionEdge> currentEdges = new ArrayList<>(graph.getConnections());
        List<FlowGraph.ConnectionEdge> rewiredEdges = new ArrayList<>();

        for (FlowGraph.ConnectionEdge edge : currentEdges) {
            if (edge.toNodeId().equals(moduleNode.getId())) {
                // Incoming wire from external to module
                int mInIdx = edge.inputIndex();
                if (mInIdx < moduleNode.getModuleInputOrigins().size()) {
                    List<RecipeNode.PortOrigin> origins = moduleNode.getModuleInputOrigins().get(mInIdx);
                    for (RecipeNode.PortOrigin orig : origins) {
                        rewiredEdges.add(new FlowGraph.ConnectionEdge(edge.fromNodeId(), edge.outputIndex(), orig.internalNodeId(), orig.internalPortIndex()));
                    }
                }
            } else if (edge.fromNodeId().equals(moduleNode.getId())) {
                // Outgoing wire from module to external
                int mOutIdx = edge.outputIndex();
                if (mOutIdx < moduleNode.getModuleOutputOrigins().size()) {
                    List<RecipeNode.PortOrigin> origins = moduleNode.getModuleOutputOrigins().get(mOutIdx);
                    for (RecipeNode.PortOrigin orig : origins) {
                        rewiredEdges.add(new FlowGraph.ConnectionEdge(orig.internalNodeId(), orig.internalPortIndex(), edge.toNodeId(), edge.inputIndex()));
                    }
                }
            } else {
                rewiredEdges.add(edge);
            }
        }

        // 2. Remove moduleNode
        graph.removeNode(moduleNode);

        // 3. Restore subGraph nodes
        for (RecipeNode n : subGraph.getNodes()) {
            n.setPos(n.getPosX() + offsetX, n.getPosY() + offsetY);
            if (moduleScale > 1.0) {
                n.setMachineCount(n.getMachineCount() * moduleScale);
            }
            graph.addNode(n);
        }

        // 4. Restore subGraph connections
        for (FlowGraph.ConnectionEdge edge : subGraph.getConnections()) {
            rewiredEdges.add(new FlowGraph.ConnectionEdge(edge.fromNodeId(), edge.outputIndex(), edge.toNodeId(), edge.inputIndex()));
        }

        graph.getConnections().clear();
        graph.getConnections().addAll(rewiredEdges);

        // 5. Restore subGraph frames
        for (CanvasGroupFrame f : subGraph.getFrames()) {
            f.moveBy(offsetX, offsetY);
            graph.addFrame(f);
        }

        // 6. Restore subGraph sticky notes
        for (CanvasStickyNote note : subGraph.getStickyNotes()) {
            note.moveBy(offsetX, offsetY);
            graph.addStickyNote(note);
        }

        return true;
    }
}


