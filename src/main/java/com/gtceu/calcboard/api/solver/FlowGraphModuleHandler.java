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
                subGraph.addConnection(edge);
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

        allocateUnconnectedNetInputs(subGraph, selectedNodes, moduleNode, inPortMap);
        allocateUnconnectedNetOutputs(subGraph, selectedNodes, moduleNode, outPortMap);

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
                externalEdges.add(new FlowGraph.ConnectionEdge(edge.fromNodeId(), edge.outputIndex(), moduleNode.getId(), modulePortIdx, edge.fixedFlowLimit(), edge.priority()));
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
                externalEdges.add(new FlowGraph.ConnectionEdge(moduleNode.getId(), modulePortIdx, edge.toNodeId(), edge.inputIndex(), edge.fixedFlowLimit(), edge.priority()));
            }
        }
    }

    private static void allocateUnconnectedNetInputs(
            FlowGraph subGraph,
            List<RecipeNode> selectedNodes,
            RecipeNode moduleNode,
            Map<PortKey, Integer> inPortMap
    ) {
        Map<IngredientStack, Double> unallocatedDemandMap = new LinkedHashMap<>();
        Map<IngredientStack, List<RecipeNode.PortOrigin>> originsMap = new LinkedHashMap<>();

        for (RecipeNode sn : selectedNodes) {
            for (int pInIdx = 0; pInIdx < sn.getInputs().size(); pInIdx++) {
                PortKey key = new PortKey(sn.getId(), pInIdx);
                if (inPortMap.containsKey(key)) continue;

                IngredientStack orig = sn.getInputs().get(pInIdx);
                double req = sn.getInputSlotRate(pInIdx, true);
                var stats = FlowGraphSolver.getInputPortStats(subGraph, sn, pInIdx);
                double suppliedInternally = stats != null ? stats.connectedRate() : 0.0;
                double remainingDemand = Math.max(0.0, req - suppliedInternally);

                if (remainingDemand > 0.0001) {
                    unallocatedDemandMap.merge(orig, remainingDemand, Double::sum);
                    originsMap.computeIfAbsent(orig, k -> new ArrayList<>()).add(new RecipeNode.PortOrigin(sn.getId(), pInIdx));
                }
            }
        }

        for (Map.Entry<IngredientStack, Double> entry : unallocatedDemandMap.entrySet()) {
            IngredientStack original = entry.getKey();
            double ratePerSec = entry.getValue();
            List<RecipeNode.PortOrigin> origins = originsMap.getOrDefault(original, Collections.emptyList());

            IngredientStack netIn = original.isFluid()
                    ? IngredientStack.fluid(original.getId(), original.getDisplayName(), ratePerSec, 1.0)
                    : IngredientStack.item(original.getId(), original.getDisplayName(), ratePerSec, 1.0);
            moduleNode.addInput(netIn);
            moduleNode.getModuleInputOrigins().add(origins);
        }
    }

    private static void allocateUnconnectedNetOutputs(
            FlowGraph subGraph,
            List<RecipeNode> selectedNodes,
            RecipeNode moduleNode,
            Map<PortKey, Integer> outPortMap
    ) {
        Map<IngredientStack, Double> unallocatedSurplusMap = new LinkedHashMap<>();
        Map<IngredientStack, List<RecipeNode.PortOrigin>> originsMap = new LinkedHashMap<>();

        for (RecipeNode sn : selectedNodes) {
            for (int pOutIdx = 0; pOutIdx < sn.getOutputs().size(); pOutIdx++) {
                PortKey key = new PortKey(sn.getId(), pOutIdx);
                if (outPortMap.containsKey(key)) continue;
                if (sn.isOutputPortVoided(pOutIdx)) continue;

                IngredientStack orig = sn.getOutputs().get(pOutIdx);
                double prod = sn.getOutputSlotRate(pOutIdx, true);
                var stats = FlowGraphSolver.getOutputPortStats(subGraph, sn, pOutIdx);
                double demandedInternally = stats != null ? stats.connectedRate() : 0.0;
                double remainingSurplus = Math.max(0.0, prod - demandedInternally);

                if (remainingSurplus > 0.0001) {
                    unallocatedSurplusMap.merge(orig, remainingSurplus, Double::sum);
                    originsMap.computeIfAbsent(orig, k -> new ArrayList<>()).add(new RecipeNode.PortOrigin(sn.getId(), pOutIdx));
                }
            }
        }

        for (Map.Entry<IngredientStack, Double> entry : unallocatedSurplusMap.entrySet()) {
            IngredientStack original = entry.getKey();
            double ratePerSec = entry.getValue();
            List<RecipeNode.PortOrigin> origins = originsMap.getOrDefault(original, Collections.emptyList());

            IngredientStack netOut = original.isFluid()
                    ? IngredientStack.fluid(original.getId(), original.getDisplayName(), ratePerSec, 1.0)
                    : IngredientStack.item(original.getId(), original.getDisplayName(), ratePerSec, 1.0);
            moduleNode.addOutput(netOut);
            moduleNode.getModuleOutputOrigins().add(origins);
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
        graph.clearConnections();
        graph.addConnections(externalEdges);
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
                        rewiredEdges.add(new FlowGraph.ConnectionEdge(edge.fromNodeId(), edge.outputIndex(), orig.internalNodeId(), orig.internalPortIndex(), edge.fixedFlowLimit(), edge.priority()));
                    }
                }
            } else if (edge.fromNodeId().equals(moduleNode.getId())) {
                // Outgoing wire from module to external
                int mOutIdx = edge.outputIndex();
                if (mOutIdx < moduleNode.getModuleOutputOrigins().size()) {
                    List<RecipeNode.PortOrigin> origins = moduleNode.getModuleOutputOrigins().get(mOutIdx);
                    for (RecipeNode.PortOrigin orig : origins) {
                        rewiredEdges.add(new FlowGraph.ConnectionEdge(orig.internalNodeId(), orig.internalPortIndex(), edge.toNodeId(), edge.inputIndex(), edge.fixedFlowLimit(), edge.priority()));
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
            if (moduleScale > 0.0 && Math.abs(moduleScale - 1.0) > 1e-6) {
                n.setMachineCount(n.getMachineCount() * moduleScale);
            }
            graph.addNode(n);
        }

        // 4. Restore subGraph connections
        for (FlowGraph.ConnectionEdge edge : subGraph.getConnections()) {
            rewiredEdges.add(edge);
        }

        graph.clearConnections();
        graph.addConnections(rewiredEdges);

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


