package com.gtceu.calcboard.api.solver;

import com.gtceu.calcboard.api.model.BoundaryPinNode;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.ModuleInputPin;
import com.gtceu.calcboard.api.model.ModuleOutputPin;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.storage.PageType;
import com.gtceu.calcboard.api.type.GTVoltageTier;

import java.util.*;

/**
 * Handles Compound Module packing, external wire rewiring, and sub-graph expansion for FlowGraph.
 */
public final class FlowGraphModuleHandler {

    private FlowGraphModuleHandler() {}

    public static int calculateNestingDepth(BoardPage page) {
        if (page == null) return 0;
        int depth = 0;
        Set<String> visited = new HashSet<>();
        visited.add(page.getId());
        String curParentId = page.getParentPageId();
        while (curParentId != null && !curParentId.isEmpty() && !visited.contains(curParentId)) {
            visited.add(curParentId);
            depth++;
            Optional<BoardPage> parent = BoardManager.getInstance().getPage(curParentId);
            if (parent.isPresent()) {
                curParentId = parent.get().getParentPageId();
            } else {
                break;
            }
        }
        return depth;
    }

    public static BoardPage findPageForGraph(FlowGraph graph) {
        if (graph == null) return null;
        BoardManager bm = BoardManager.getInstance();
        for (BoardPage page : bm.getPages()) {
            if (page.getGraph() == graph) {
                return page;
            }
        }
        return bm.getActivePage();
    }

    /**
     * Groups selected nodes (or all nodes if targetNodeIds is null/empty) into a single Compound Module node.
     */
    public static RecipeNode groupIntoModule(FlowGraph graph, Set<String> targetNodeIds, String moduleName) {
        return groupIntoModule(graph, targetNodeIds, moduleName, null);
    }

    public static RecipeNode groupIntoModule(FlowGraph graph, Set<String> targetNodeIds, String moduleName, CanvasGroupFrame primaryFrame) {
        if (graph == null) return null;

        BoardPage parentPage = findPageForGraph(graph);
        if (parentPage != null && calculateNestingDepth(parentPage) >= 3) {
            return null;
        }

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

        FlowGraphSolver.computeSummary(graph);

        Set<String> selectedIdSet = new HashSet<>();
        for (RecipeNode n : selectedNodes) selectedIdSet.add(n.getId());

        FlowGraph subGraph = buildSubGraph(selectedNodes, graph.getConnections(), selectedIdSet);
        BalanceSummary summary = FlowGraphSolver.computeSummaryPreservingEfficiencies(subGraph);

        RecipeNode moduleNode = createModuleNode(selectedNodes, summary, moduleName, subGraph);
        transferFramesAndNotes(graph, subGraph, primaryFrame, selectedNodes, selectedIdSet);

        List<FlowGraph.ConnectionEdge> externalEdges = new ArrayList<>();
        allocateModulePortsAndRewireEdges(graph, subGraph, selectedNodes, selectedIdSet, summary, moduleNode, externalEdges);

        BoardPage subPage = new BoardPage(UUID.randomUUID().toString(), moduleName != null && !moduleName.trim().isEmpty() ? moduleName.trim() : "Compound Module", subGraph);
        subPage.setPageType(PageType.MODULE);
        if (parentPage != null) {
            subPage.setParentPageId(parentPage.getId());
        }
        subPage.setParentModuleNodeId(moduleNode.getId());
        moduleNode.setSubPageId(subPage.getId());
        BoardManager.getInstance().getPageManager().addPage(subPage);

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
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        for (RecipeNode n : selectedNodes) {
            minX = Math.min(minX, n.getPosX());
            minY = Math.min(minY, n.getPosY());
            maxX = Math.max(maxX, n.getPosX() + (n.getCardWidth() > 0 ? n.getCardWidth() : 180));
            maxY = Math.max(maxY, n.getPosY() + (n.getCardHeight() > 0 ? n.getCardHeight() : 160));
        }
        if (minX == Double.MAX_VALUE) {
            minX = 0; minY = 0; maxX = 200; maxY = 200;
        }

        Map<PortKey, Integer> inPortMap = new LinkedHashMap<>();
        Map<PortKey, Integer> outPortMap = new LinkedHashMap<>();

        allocateIncomingBoundaryPorts(graph, subGraph, moduleNode, selectedIdSet, inPortMap, externalEdges, minX, minY);
        allocateOutgoingBoundaryPorts(graph, subGraph, moduleNode, selectedIdSet, outPortMap, externalEdges, maxX, minY);

        allocateUnconnectedNetInputs(subGraph, selectedNodes, moduleNode, inPortMap, minX, minY);
        allocateUnconnectedNetOutputs(subGraph, selectedNodes, moduleNode, outPortMap, maxX, minY);

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
            FlowGraph subGraph,
            RecipeNode moduleNode,
            Set<String> selectedIdSet,
            Map<PortKey, Integer> inPortMap,
            List<FlowGraph.ConnectionEdge> externalEdges,
            double minX, double minY
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
                        double reqRate = determineIncomingBoundaryRate(graph, subGraph, targetNode, edge);
                        IngredientStack portStack = orig.isFluid()
                                ? IngredientStack.fluid(orig.getId(), orig.getDisplayName(), reqRate, 1.0)
                                : IngredientStack.item(orig.getId(), orig.getDisplayName(), reqRate, 1.0);
                        modulePortIdx = moduleNode.getInputs().size();
                        moduleNode.addInput(portStack);
                        moduleNode.getModuleInputOrigins().add(new ArrayList<>(List.of(
                                new RecipeNode.PortOrigin(edge.toNodeId(), edge.inputIndex())
                        )));
                        inPortMap.put(key, modulePortIdx);

                        ModuleInputPin inPin = new ModuleInputPin(UUID.randomUUID().toString(), orig.getDisplayName(), portStack.copy());
                        inPin.setPos(minX - 80, minY + modulePortIdx * 48);
                        subGraph.addNode(inPin);
                        subGraph.addConnection(new FlowGraph.ConnectionEdge(inPin.getId(), 0, edge.toNodeId(), edge.inputIndex(), edge.fixedFlowLimit(), edge.priority()));
                        moduleNode.getInputPinNodeIds().add(inPin.getId());
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

    private static double determineIncomingBoundaryRate(FlowGraph graph, FlowGraph subGraph, RecipeNode targetNode, FlowGraph.ConnectionEdge edge) {
        if (edge.fixedFlowLimit() > 0.0) {
            return edge.fixedFlowLimit();
        }
        if (targetNode.isReroute()) {
            return FlowBalanceMatrixSolver.getEdgeAllocatedFlow(graph, edge, null);
        }
        var stats = FlowGraphSolver.getInputPortStats(subGraph, targetNode, edge.inputIndex());
        double suppliedInternally = stats != null ? stats.connectedRate() : 0.0;
        return Math.max(0.0, targetNode.getInputSlotRate(edge.inputIndex(), true) - suppliedInternally);
    }

    private static void allocateOutgoingBoundaryPorts(
            FlowGraph graph,
            FlowGraph subGraph,
            RecipeNode moduleNode,
            Set<String> selectedIdSet,
            Map<PortKey, Integer> outPortMap,
            List<FlowGraph.ConnectionEdge> externalEdges,
            double maxX, double minY
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
                        double prodRate = determineOutgoingBoundaryRate(graph, subGraph, sourceNode, edge);
                        IngredientStack portStack = orig.isFluid()
                                ? IngredientStack.fluid(orig.getId(), orig.getDisplayName(), prodRate, 1.0)
                                : IngredientStack.item(orig.getId(), orig.getDisplayName(), prodRate, 1.0);
                        modulePortIdx = moduleNode.getOutputs().size();
                        moduleNode.addOutput(portStack);
                        moduleNode.getModuleOutputOrigins().add(new ArrayList<>(List.of(
                                new RecipeNode.PortOrigin(edge.fromNodeId(), edge.outputIndex())
                        )));
                        outPortMap.put(key, modulePortIdx);

                        ModuleOutputPin outPin = new ModuleOutputPin(UUID.randomUUID().toString(), orig.getDisplayName(), portStack.copy());
                        outPin.setPos(maxX + 48, minY + modulePortIdx * 48);
                        subGraph.addNode(outPin);
                        subGraph.addConnection(new FlowGraph.ConnectionEdge(edge.fromNodeId(), edge.outputIndex(), outPin.getId(), 0, edge.fixedFlowLimit(), edge.priority()));
                        moduleNode.getOutputPinNodeIds().add(outPin.getId());
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

    private static double determineOutgoingBoundaryRate(FlowGraph graph, FlowGraph subGraph, RecipeNode sourceNode, FlowGraph.ConnectionEdge edge) {
        if (edge.fixedFlowLimit() > 0.0) {
            return edge.fixedFlowLimit();
        }
        if (sourceNode.isReroute()) {
            return FlowBalanceMatrixSolver.getEdgeAllocatedFlow(graph, edge, null);
        }
        var stats = FlowGraphSolver.getOutputPortStats(subGraph, sourceNode, edge.outputIndex());
        double demandedInternally = stats != null ? stats.connectedRate() : 0.0;
        return Math.max(0.0, sourceNode.getOutputSlotRate(edge.outputIndex(), true) - demandedInternally);
    }

    private static void allocateUnconnectedNetInputs(
            FlowGraph subGraph,
            List<RecipeNode> selectedNodes,
            RecipeNode moduleNode,
            Map<PortKey, Integer> inPortMap,
            double minX, double minY
    ) {
        Map<IngredientStack, Double> unallocatedDemandMap = new LinkedHashMap<>();
        Map<IngredientStack, List<RecipeNode.PortOrigin>> originsMap = new LinkedHashMap<>();

        for (RecipeNode sn : selectedNodes) {
            if (sn.isReroute()) continue;
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
            int modulePortIdx = moduleNode.getInputs().size();
            moduleNode.addInput(netIn);
            moduleNode.getModuleInputOrigins().add(origins);

            ModuleInputPin inPin = new ModuleInputPin(UUID.randomUUID().toString(), original.getDisplayName(), netIn.copy());
            inPin.setPos(minX - 80, minY + modulePortIdx * 48);
            subGraph.addNode(inPin);
            for (RecipeNode.PortOrigin orig : origins) {
                subGraph.addConnection(new FlowGraph.ConnectionEdge(inPin.getId(), 0, orig.internalNodeId(), orig.internalPortIndex(), 0.0, 0));
            }
            moduleNode.getInputPinNodeIds().add(inPin.getId());
        }
    }

    private static void allocateUnconnectedNetOutputs(
            FlowGraph subGraph,
            List<RecipeNode> selectedNodes,
            RecipeNode moduleNode,
            Map<PortKey, Integer> outPortMap,
            double maxX, double minY
    ) {
        Map<IngredientStack, Double> unallocatedSurplusMap = new LinkedHashMap<>();
        Map<IngredientStack, List<RecipeNode.PortOrigin>> originsMap = new LinkedHashMap<>();

        for (RecipeNode sn : selectedNodes) {
            if (sn.isReroute()) continue;
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
            int modulePortIdx = moduleNode.getOutputs().size();
            moduleNode.addOutput(netOut);
            moduleNode.getModuleOutputOrigins().add(origins);

            ModuleOutputPin outPin = new ModuleOutputPin(UUID.randomUUID().toString(), original.getDisplayName(), netOut.copy());
            outPin.setPos(maxX + 48, minY + modulePortIdx * 48);
            subGraph.addNode(outPin);
            for (RecipeNode.PortOrigin orig : origins) {
                subGraph.addConnection(new FlowGraph.ConnectionEdge(orig.internalNodeId(), orig.internalPortIndex(), outPin.getId(), 0, 0.0, 0));
            }
            moduleNode.getOutputPinNodeIds().add(outPin.getId());
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

    public static void syncModulePortsFromSubPage(RecipeNode moduleNode, BoardPage subPage) {
        if (moduleNode == null || subPage == null) return;
        syncModulePortsFromSubGraph(moduleNode, subPage.getGraph());
    }

    public static void syncModulePortsFromSubGraph(RecipeNode moduleNode, FlowGraph subGraph) {
        if (moduleNode == null || subGraph == null) return;

        List<ModuleInputPin> inputPins = new ArrayList<>();
        List<ModuleOutputPin> outputPins = new ArrayList<>();
        for (RecipeNode n : subGraph.getNodes()) {
            if (n instanceof ModuleInputPin inPin) {
                inputPins.add(inPin);
            } else if (n instanceof ModuleOutputPin outPin) {
                outputPins.add(outPin);
            }
        }

        Comparator<RecipeNode> pinSorter = Comparator
                .comparingDouble(RecipeNode::getPosY)
                .thenComparingDouble(RecipeNode::getPosX);
        inputPins.sort(pinSorter);
        outputPins.sort(pinSorter);

        moduleNode.getInputPinNodeIds().clear();
        for (ModuleInputPin pin : inputPins) {
            moduleNode.getInputPinNodeIds().add(pin.getId());
        }

        moduleNode.getOutputPinNodeIds().clear();
        for (ModuleOutputPin pin : outputPins) {
            moduleNode.getOutputPinNodeIds().add(pin.getId());
        }

        moduleNode.getInputs().clear();
        moduleNode.getModuleInputOrigins().clear();
        for (ModuleInputPin pin : inputPins) {
            IngredientStack bound = pin.getBoundIngredient();
            double demand = calculatePinInternalFlow(subGraph, pin, true);
            if (demand <= 0.0001 && bound != null) {
                demand = bound.getAmount();
            }
            IngredientStack portStack = bound != null ? bound.withAmount(demand) : IngredientStack.item(null, pin.getPinLabel(), demand, 1.0);
            moduleNode.addInput(portStack);
            moduleNode.getModuleInputOrigins().add(new ArrayList<>(List.of(new RecipeNode.PortOrigin(pin.getId(), 0))));
        }

        moduleNode.getOutputs().clear();
        moduleNode.getModuleOutputOrigins().clear();
        for (ModuleOutputPin pin : outputPins) {
            IngredientStack bound = pin.getBoundIngredient();
            double supply = calculatePinInternalFlow(subGraph, pin, false);
            if (supply <= 0.0001 && bound != null) {
                supply = bound.getAmount();
            }
            IngredientStack portStack = bound != null ? bound.withAmount(supply) : IngredientStack.item(null, pin.getPinLabel(), supply, 1.0);
            moduleNode.addOutput(portStack);
            moduleNode.getModuleOutputOrigins().add(new ArrayList<>(List.of(new RecipeNode.PortOrigin(pin.getId(), 0))));
        }

        BalanceSummary summary = FlowGraphSolver.computeSummaryPreservingEfficiencies(subGraph);
        moduleNode.setContainedMachineCount(summary.totalMachineCount());
        double baseEUt = Math.max(1.0, Math.abs(summary.totalEUt()));
        moduleNode.setBaseEUt(baseEUt);
        moduleNode.setGenerator(summary.totalEUt() < -0.001);
        moduleNode.setTargetTier(summary.highestVoltageTier());
    }

    private static double calculatePinInternalFlow(FlowGraph subGraph, BoundaryPinNode pin, boolean isInput) {
        if (subGraph == null || pin == null) return 0.0;
        double total = 0.0;
        for (FlowGraph.ConnectionEdge edge : subGraph.getConnections()) {
            if (isInput) {
                total += calculateInputPinEdgeFlow(subGraph, pin, edge);
            } else {
                total += calculateOutputPinEdgeFlow(subGraph, pin, edge);
            }
        }
        return total;
    }

    private static double calculateInputPinEdgeFlow(FlowGraph subGraph, BoundaryPinNode pin, FlowGraph.ConnectionEdge edge) {
        if (!edge.fromNodeId().equals(pin.getId())) return 0.0;
        RecipeNode target = subGraph.findNodeById(edge.toNodeId());
        if (target == null || edge.inputIndex() >= target.getInputs().size()) return 0.0;
        if (edge.fixedFlowLimit() > 0.0) return edge.fixedFlowLimit();
        if (target.isReroute()) return FlowBalanceMatrixSolver.getEdgeAllocatedFlow(subGraph, edge, null);

        double otherSupplied = calculateOtherSuppliedToInput(subGraph, pin.getId(), target.getId(), edge.inputIndex());
        return Math.max(0.0, target.getInputSlotRate(edge.inputIndex(), true) - otherSupplied);
    }

    private static double calculateOtherSuppliedToInput(FlowGraph subGraph, String pinId, String targetId, int inputIndex) {
        double otherSupplied = 0.0;
        for (FlowGraph.ConnectionEdge otherEdge : subGraph.getConnections()) {
            if (otherEdge.toNodeId().equals(targetId) && otherEdge.inputIndex() == inputIndex && !otherEdge.fromNodeId().equals(pinId)) {
                otherSupplied += FlowBalanceMatrixSolver.getEdgeAllocatedFlow(subGraph, otherEdge, null);
            }
        }
        return otherSupplied;
    }

    private static double calculateOutputPinEdgeFlow(FlowGraph subGraph, BoundaryPinNode pin, FlowGraph.ConnectionEdge edge) {
        if (!edge.toNodeId().equals(pin.getId())) return 0.0;
        RecipeNode source = subGraph.findNodeById(edge.fromNodeId());
        if (source == null || edge.outputIndex() >= source.getOutputs().size()) return 0.0;
        if (edge.fixedFlowLimit() > 0.0) return edge.fixedFlowLimit();
        if (source.isReroute()) return FlowBalanceMatrixSolver.getEdgeAllocatedFlow(subGraph, edge, null);

        double otherDemanded = calculateOtherDemandedFromOutput(subGraph, pin.getId(), source.getId(), edge.outputIndex());
        return Math.max(0.0, source.getOutputSlotRate(edge.outputIndex(), true) - otherDemanded);
    }

    private static double calculateOtherDemandedFromOutput(FlowGraph subGraph, String pinId, String sourceId, int outputIndex) {
        double otherDemanded = 0.0;
        for (FlowGraph.ConnectionEdge otherEdge : subGraph.getConnections()) {
            if (otherEdge.fromNodeId().equals(sourceId) && otherEdge.outputIndex() == outputIndex && !otherEdge.toNodeId().equals(pinId)) {
                otherDemanded += FlowBalanceMatrixSolver.getEdgeAllocatedFlow(subGraph, otherEdge, null);
            }
        }
        return otherDemanded;
    }

    public static void scaleModuleSubPage(RecipeNode moduleNode, double newCount) {
        if (moduleNode == null || !moduleNode.isModule() || newCount <= 0.0) return;
        double oldCount = moduleNode.getMachineCount();
        if (Math.abs(oldCount - newCount) < 1e-6) return;
        double factor = newCount / Math.max(0.01, oldCount);
        moduleNode.setMachineCount(newCount);

        FlowGraph subGraph = moduleNode.getSubGraph();
        if (subGraph == null && !moduleNode.getSubPageId().isEmpty()) {
            BoardManager.getInstance().getPage(moduleNode.getSubPageId()).ifPresent(p -> {
                scaleGraphInternalNodes(p.getGraph(), factor);
                FlowGraphSolver.computeSummary(p.getGraph());
                syncModulePortsFromSubPage(moduleNode, p);
            });
            return;
        }
        if (subGraph != null) {
            scaleGraphInternalNodes(subGraph, factor);
            FlowGraphSolver.computeSummary(subGraph);
            syncModulePortsFromSubGraph(moduleNode, subGraph);
        }
    }

    private static void scaleGraphInternalNodes(FlowGraph graph, double factor) {
        if (graph == null || factor <= 0.0) return;
        for (RecipeNode node : graph.getNodes()) {
            if (node == null || node instanceof BoundaryPinNode || node.isReroute()) continue;
            double updated = Math.round(node.getMachineCount() * factor * 10000.0) / 10000.0;
            node.setMachineCount(Math.max(0.0001, updated));
        }
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

        String subPageId = moduleNode.getSubPageId();
        if (subPageId != null && !subPageId.isEmpty()) {
            BoardManager.getInstance().getPage(subPageId).ifPresent(subPage -> {
                int idx = BoardManager.getInstance().getPages().indexOf(subPage);
                if (idx >= 0) {
                    BoardManager.getInstance().removePage(idx);
                }
            });
        }

        double sumX = 0, sumY = 0;
        int nonPinCount = 0;
        for (RecipeNode n : subGraph.getNodes()) {
            if (n instanceof BoundaryPinNode) continue;
            sumX += n.getPosX();
            sumY += n.getPosY();
            nonPinCount++;
        }
        double origCenterX = nonPinCount > 0 ? sumX / nonPinCount : 0;
        double origCenterY = nonPinCount > 0 ? sumY / nonPinCount : 0;

        double offsetX = moduleNode.getPosX() - origCenterX;
        double offsetY = moduleNode.getPosY() - origCenterY;
        double moduleScale = moduleNode.getMachineCount();

        List<FlowGraph.ConnectionEdge> currentEdges = new ArrayList<>(graph.getConnections());
        List<FlowGraph.ConnectionEdge> rewiredEdges = new ArrayList<>();

        for (FlowGraph.ConnectionEdge edge : currentEdges) {
            if (edge.toNodeId().equals(moduleNode.getId())) {
                int mInIdx = edge.inputIndex();
                if (mInIdx < moduleNode.getModuleInputOrigins().size()) {
                    List<RecipeNode.PortOrigin> origins = moduleNode.getModuleInputOrigins().get(mInIdx);
                    for (RecipeNode.PortOrigin orig : origins) {
                        rewiredEdges.add(new FlowGraph.ConnectionEdge(edge.fromNodeId(), edge.outputIndex(), orig.internalNodeId(), orig.internalPortIndex(), edge.fixedFlowLimit(), edge.priority()));
                    }
                }
            } else if (edge.fromNodeId().equals(moduleNode.getId())) {
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

        graph.removeNode(moduleNode);

        for (RecipeNode n : subGraph.getNodes()) {
            if (n instanceof BoundaryPinNode) continue;
            n.setPos(n.getPosX() + offsetX, n.getPosY() + offsetY);
            if (moduleScale > 0.0 && Math.abs(moduleScale - 1.0) > 1e-6) {
                n.setMachineCount(n.getMachineCount() * moduleScale);
            }
            graph.addNode(n);
        }

        for (FlowGraph.ConnectionEdge edge : subGraph.getConnections()) {
            RecipeNode from = subGraph.findNodeById(edge.fromNodeId());
            RecipeNode to = subGraph.findNodeById(edge.toNodeId());
            if (from instanceof BoundaryPinNode || to instanceof BoundaryPinNode) continue;
            rewiredEdges.add(edge);
        }

        graph.clearConnections();
        graph.addConnections(rewiredEdges);

        for (CanvasGroupFrame f : subGraph.getFrames()) {
            f.moveBy(offsetX, offsetY);
            graph.addFrame(f);
        }

        for (CanvasStickyNote note : subGraph.getStickyNotes()) {
            note.moveBy(offsetX, offsetY);
            graph.addStickyNote(note);
        }

        return true;
    }
}


