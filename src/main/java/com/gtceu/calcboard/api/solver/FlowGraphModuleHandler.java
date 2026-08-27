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

        // 5. Determine frames to capture into subGraph (preserving outer enclosing frames)
        List<CanvasGroupFrame> capturedFrames = new ArrayList<>();
        if (primaryFrame != null) {
            capturedFrames.add(primaryFrame);
            for (CanvasGroupFrame f : graph.getFrames()) {
                if (!f.equals(primaryFrame)) {
                    // Check if f is strictly inside primaryFrame
                    if (f.getPosX() >= primaryFrame.getPosX() - 5
                            && f.getPosY() >= primaryFrame.getPosY() - 5
                            && f.getPosX() + f.getWidth() <= primaryFrame.getPosX() + primaryFrame.getWidth() + 5
                            && f.getPosY() + f.getHeight() <= primaryFrame.getPosY() + primaryFrame.getHeight() + 5) {
                        capturedFrames.add(f);
                    }
                }
            }
        } else {
            // Find candidate frames whose enclosed nodes are completely contained in selectedIdSet
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

            if (!candidateFrames.isEmpty()) {
                // Find candidate with smallest area (innermost) that tightly wraps the selection
                CanvasGroupFrame tightestFrame = null;
                double minArea = Double.MAX_VALUE;
                for (CanvasGroupFrame cf : candidateFrames) {
                    double area = cf.getWidth() * cf.getHeight();
                    if (area < minArea) {
                        minArea = area;
                        tightestFrame = cf;
                    }
                }
                if (tightestFrame != null) {
                    capturedFrames.add(tightestFrame);
                    for (CanvasGroupFrame cf : candidateFrames) {
                        if (!cf.equals(tightestFrame)) {
                            // If cf is inside tightestFrame
                            if (cf.getPosX() >= tightestFrame.getPosX() - 5
                                    && cf.getPosY() >= tightestFrame.getPosY() - 5
                                    && cf.getPosX() + cf.getWidth() <= tightestFrame.getPosX() + tightestFrame.getWidth() + 5
                                    && cf.getPosY() + cf.getHeight() <= tightestFrame.getPosY() + tightestFrame.getHeight() + 5) {
                                capturedFrames.add(cf);
                            }
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
                    capturedNotes.add(note);
                }
            }
        }

        for (CanvasGroupFrame f : capturedFrames) {
            graph.removeFrame(f);
            subGraph.addFrame(f);
        }
        for (CanvasStickyNote note : capturedNotes) {
            graph.removeStickyNote(note);
            subGraph.addStickyNote(note);
        }

        // Add net external inputs and record port origins
        for (Map.Entry<IngredientStack, Double> entry : summary.rawInputs().entrySet()) {
            IngredientStack original = entry.getKey();
            double ratePerSec = entry.getValue();
            IngredientStack netIn = original.isFluid()
                ? IngredientStack.fluid(original.getId(), original.getDisplayName(), ratePerSec, 1.0)
                : IngredientStack.item(original.getId(), original.getDisplayName(), ratePerSec, 1.0);
            moduleNode.addInput(netIn);

            List<RecipeNode.PortOrigin> origins = new ArrayList<>();
            for (RecipeNode sn : selectedNodes) {
                for (int pInIdx = 0; pInIdx < sn.getInputs().size(); pInIdx++) {
                    if (sn.getInputs().get(pInIdx).equals(original)) {
                        origins.add(new RecipeNode.PortOrigin(sn.getId(), pInIdx));
                    }
                }
            }
            moduleNode.getModuleInputOrigins().add(origins);
        }

        // Add net external outputs and record port origins
        for (Map.Entry<IngredientStack, Double> entry : summary.netOutputs().entrySet()) {
            IngredientStack original = entry.getKey();
            double ratePerSec = entry.getValue();
            IngredientStack netOut = original.isFluid()
                ? IngredientStack.fluid(original.getId(), original.getDisplayName(), ratePerSec, 1.0)
                : IngredientStack.item(original.getId(), original.getDisplayName(), ratePerSec, 1.0);
            moduleNode.addOutput(netOut);

            List<RecipeNode.PortOrigin> origins = new ArrayList<>();
            for (RecipeNode sn : selectedNodes) {
                for (int pOutIdx = 0; pOutIdx < sn.getOutputs().size(); pOutIdx++) {
                    if (sn.getOutputs().get(pOutIdx).equals(original)) {
                        origins.add(new RecipeNode.PortOrigin(sn.getId(), pOutIdx));
                    }
                }
            }
            moduleNode.getModuleOutputOrigins().add(origins);
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


