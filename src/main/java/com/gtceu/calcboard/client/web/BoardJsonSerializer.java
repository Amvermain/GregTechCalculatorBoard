package com.gtceu.calcboard.client.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.CrossPageExportTarget;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.NodeRateCalculator;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.FlowBalanceMatrixSolver;
import com.gtceu.calcboard.api.solver.WorkspaceFlowCoordinator;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;

import java.util.List;

/**
 * Serializes board canvas state, machine nodes, frames, sticky notes, and port connections
 * into JSON payloads consumed by the web dashboard frontend.
 */
public final class BoardJsonSerializer {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static String serialize(com.gtceu.calcboard.api.storage.BoardPage page) {
        if (page == null) return "{}";
        return serialize(page.getGraph(), page.getId(), page.getName(), page.getPanX(), page.getPanY(), page.getZoom());
    }

    public static String serialize(FlowGraph graph, String pageId, String pageTitle, double panX, double panY, double zoom) {
        JsonObject root = serializeToJson(graph, pageId, pageTitle, panX, panY, zoom);
        return GSON.toJson(root);
    }

    public static JsonObject serializeToJson(FlowGraph graph, String pageId, String pageTitle, double panX, double panY, double zoom) {
        JsonObject root = new JsonObject();
        root.addProperty("version", 1);
        root.addProperty("pageId", pageId != null ? pageId : "default");
        root.addProperty("pageTitle", pageTitle != null ? pageTitle : "Untitled Page");
        root.addProperty("timestamp", System.currentTimeMillis());

        ClientWorkspaceState teamState = ClientWorkspaceState.getInstance();
        boolean isTeamPage = teamState.getRemotePage(pageId) != null
                || (teamState.isTeamMode() && pageId != null && pageId.equals(teamState.getActiveTeamPageId()));
        root.addProperty("workspace", isTeamPage ? "TEAM" : "LOCAL");
        root.addProperty("hasTeam", teamState.isCollaborationEnabled());
        root.addProperty("teamName", teamState.getCurrentTeamName());
        if (isTeamPage) {
            var rp = teamState.getRemotePage(pageId);
            root.addProperty("isLocked", rp != null && rp.isLocked());
            root.addProperty("lockHolder", rp != null && rp.getLockHolderName() != null ? rp.getLockHolderName() : "");
        }

        JsonObject viewport = new JsonObject();
        viewport.addProperty("panX", panX);
        viewport.addProperty("panY", panY);
        viewport.addProperty("zoom", zoom);
        root.add("viewport", viewport);

        if (graph != null) {
            for (CanvasGroupFrame frame : graph.getFrames()) {
                if (frame != null && frame.isSharedMachineFrame() && frame.getViewMode() == com.gtceu.calcboard.api.model.PoolViewMode.EMBEDDED_PANEL) {
                    frame.relayoutEmbeddedCards(graph);
                }
            }
        }

        root.add("nodes", serializeNodes(graph));
        root.add("connections", serializeConnections(graph));
        root.add("frames", serializeFrames(graph));
        root.add("stickyNotes", serializeStickyNotes(graph));

        return root;
    }

    private static JsonArray serializeNodes(FlowGraph graph) {
        JsonArray array = new JsonArray();
        if (graph == null) return array;

        for (RecipeNode node : graph.getNodes()) {
            JsonObject nodeObj = new JsonObject();
            nodeObj.addProperty("id", node.getId());
            nodeObj.addProperty("type", resolveNodeType(node));
            nodeObj.addProperty("title", node.getName());
            nodeObj.addProperty("machineId", resolveMachineId(node));
            nodeObj.addProperty("tier", node.getTargetTier() != null ? node.getTargetTier().name() : "LV");
            nodeObj.addProperty("posX", node.getPosX());
            nodeObj.addProperty("posY", node.getPosY());
            nodeObj.addProperty("width", resolveCardWidth(node, graph));
            nodeObj.addProperty("height", resolveCardHeight(node, graph));
            nodeObj.addProperty("isFlipped", node.isFlipped());

            CanvasGroupFrame emb = graph.getEmbeddedFrameForNode(node.getId());
            nodeObj.addProperty("isEmbedded", emb != null);
            if (emb != null) {
                nodeObj.addProperty("embeddedFrameId", emb.getId());
            }
            CanvasGroupFrame folded = graph.getFoldedFrameForNode(node.getId());
            nodeObj.addProperty("isFoldedInFrame", folded != null);
            if (folded != null) {
                nodeObj.addProperty("foldedFrameId", folded.getId());
            }

            nodeObj.add("metrics", serializeNodeMetrics(node));
            nodeObj.add("inputs", serializeInputPorts(node));
            nodeObj.add("outputs", serializeOutputPorts(node));

            if (node.isReroute()) {
                serializeJunctionData(node, graph, nodeObj);
            }

            array.add(nodeObj);
        }
        return array;
    }

    private static void serializeJunctionData(RecipeNode node, FlowGraph graph, JsonObject nodeObj) {
        nodeObj.addProperty("supplyMode", node.getSupplyMode() != null ? node.getSupplyMode().name() : "NONE");
        nodeObj.addProperty("allocatedInputRate", roundThreeDecimals(node.getAllocatedInputRate()));
        nodeObj.addProperty("allocatedExportRate", roundThreeDecimals(node.getAllocatedExportRate()));

        if (node.isLinkedJunction()) {
            serializeLinkedSource(node, graph, nodeObj);
        }
        if (!node.getExportTargets().isEmpty()) {
            serializeExportTargets(node, nodeObj);
        }
    }

    private static void serializeLinkedSource(RecipeNode node, FlowGraph graph, JsonObject nodeObj) {
        JsonObject linkObj = new JsonObject();
        String srcPageId = node.getLinkedSourcePageId();
        String srcNodeId = node.getLinkedSourceNodeId();
        linkObj.addProperty("pageId", srcPageId);
        linkObj.addProperty("nodeId", srcNodeId);

        BoardPage srcPage = (srcPageId != null && !srcPageId.isEmpty())
                ? ClientWorkspaceState.resolveActiveWorkspacePage(srcPageId)
                : null;
        RecipeNode srcNode = (srcPage != null && srcNodeId != null)
                ? srcPage.getGraph().findNodeById(srcNodeId)
                : null;

        linkObj.addProperty("pageName", resolvePageName(srcPage, srcPageId));
        linkObj.addProperty("nodeName", resolveNodeName(srcNode, srcNodeId));
        linkObj.addProperty("isBroken", srcPage == null || srcNode == null);

        WorkspaceFlowCoordinator.WorkspaceFlowResult flowResult = WorkspaceFlowCoordinator.getLastResult();
        linkObj.addProperty("isCircular", flowResult != null && flowResult.isCircular(node.getId()));

        double demand = graph != null
                ? FlowBalanceMatrixSolver.calculateTotalConnectedPortDemand(graph, node, 0, null)
                : 0.0;
        double alloc = node.getAllocatedInputRate();
        linkObj.addProperty("demandRate", roundThreeDecimals(demand));
        linkObj.addProperty("isStarved", demand > 0.0001 && alloc < demand - 0.0001);
        int reqPri = WorkspaceFlowCoordinator.getOutgoingMaxPriority(graph, node);
        linkObj.addProperty("priority", reqPri);

        if (srcPage != null && srcNode != null) {
            WorkspaceFlowCoordinator.SourceJunctionMetrics metrics =
                    WorkspaceFlowCoordinator.calculateSourceJunctionMetrics(srcPage, srcNode);
            JsonObject mObj = new JsonObject();
            mObj.addProperty("totalProduction", roundThreeDecimals(metrics.totalProduction()));
            mObj.addProperty("totalUsage", roundThreeDecimals(metrics.totalUsage()));
            mObj.addProperty("availableSurplus", roundThreeDecimals(metrics.availableSurplus()));
            linkObj.add("metrics", mObj);
        }

        nodeObj.add("linkedSource", linkObj);
    }

    private static String resolvePageName(BoardPage page, String fallbackId) {
        if (page != null && page.getName() != null && !page.getName().isEmpty()) {
            return page.getName();
        }
        return fallbackId != null ? fallbackId : "";
    }

    private static String resolveNodeName(RecipeNode node, String fallbackId) {
        if (node != null && node.getName() != null && !node.getName().isEmpty()) {
            return node.getName();
        }
        return fallbackId != null ? fallbackId : "";
    }

    private static void serializeExportTargets(RecipeNode node, JsonObject nodeObj) {
        JsonArray expArr = new JsonArray();
        for (CrossPageExportTarget t : node.getExportTargets()) {
            JsonObject tObj = new JsonObject();
            tObj.addProperty("targetPageId", t.targetPageId());
            BoardPage targetPage = ClientWorkspaceState.resolveActiveWorkspacePage(t.targetPageId());
            tObj.addProperty("targetPageName", resolvePageName(targetPage, t.targetPageId()));
            tObj.addProperty("priority", t.priority());
            tObj.addProperty("fixedLimit", roundThreeDecimals(t.fixedLimit()));
            expArr.add(tObj);
        }
        nodeObj.add("exportTargets", expArr);
    }

    private static double resolveCardHeight(RecipeNode node, FlowGraph graph) {
        if (node.isReroute() || node.isBoundaryPin()) return 32.0;
        if (graph != null && graph.isNodeInEmbeddedPanel(node.getId())) {
            int portRows = Math.max(node.getInputs().size(), node.getOutputs().size());
            double portRowsH = portRows > 0 ? portRows * 16.0 + 4.0 : 16.0;
            return Math.max(40.0, 16.0 + portRowsH + 4.0);
        }
        int maxRows = Math.max(node.getInputs().size(), node.getOutputs().size());
        int contentStartY = node.isModule() ? 62 : 80;
        int autoHeight = contentStartY + Math.max(1, maxRows) * 18 + 8;
        return Math.max(autoHeight, (double) node.getCardHeight());
    }

    private static double resolveCardWidth(RecipeNode node, FlowGraph graph) {
        if (node.isReroute() || node.isBoundaryPin()) return 32.0;
        if (graph != null && graph.isNodeInEmbeddedPanel(node.getId())) {
            return node.getCardWidth() > 0 ? (double) node.getCardWidth() : 245.0;
        }
        return (double) node.getCardWidth();
    }

    private static String resolveNodeType(RecipeNode node) {
        if (node.isReroute()) return "JUNCTION";
        if (node.isBoundaryPin()) return "PIN";
        if (node.isModule()) return "COMPOUND";
        if (node.getRole() != null) return node.getRole().getRoleType().name();
        return "MACHINE";
    }

    private static String resolveMachineId(RecipeNode node) {
        if (node.getMachineIcon() != null) return node.getMachineIcon().toString();
        if (node.getAvailableWorkstations() != null && !node.getAvailableWorkstations().isEmpty()) {
            return node.getAvailableWorkstations().get(0).toString();
        }
        if (node.getRecipeCategoryId() != null) return node.getRecipeCategoryId().toString();
        return "";
    }

    private static JsonObject serializeNodeMetrics(RecipeNode node) {
        JsonObject metrics = new JsonObject();
        double eut = node.isGenerator() ? node.getTotalEUt() : -node.getTotalEUt();
        metrics.addProperty("eut", roundThreeDecimals(eut));
        metrics.addProperty("durationSec", roundThreeDecimals(node.getEffectiveDurationSeconds()));
        metrics.addProperty("parallel", node.getTotalParallel());
        metrics.addProperty("efficiency", node.getEfficiency());
        metrics.addProperty("machineCount", node.getMachineCount());
        metrics.addProperty("overclockMode", node.getOverclockMode() != null ? node.getOverclockMode().name() : "STANDARD");
        metrics.addProperty("isGenerator", node.isGenerator());
        return metrics;
    }

    private static JsonArray serializeInputPorts(RecipeNode node) {
        JsonArray array = new JsonArray();
        for (int i = 0; i < node.getInputs().size(); i++) {
            IngredientStack stack = node.getInputs().get(i);
            JsonObject portObj = new JsonObject();
            portObj.addProperty("portId", "in_" + i);
            portObj.addProperty("type", stack.isFluid() ? "FLUID" : (stack.isStressUnit() ? "STRESS" : "ITEM"));
            portObj.addProperty("id", stack.getId() != null ? stack.getId().toString() : "");
            portObj.addProperty("displayName", stack.getDisplayName() != null ? stack.getDisplayName() : "");
            portObj.addProperty("amount", stack.getAmount());
            portObj.addProperty("ratePerSec", roundThreeDecimals(NodeRateCalculator.getInputSlotRate(node, i, true)));
            array.add(portObj);
        }
        return array;
    }

    private static JsonArray serializeOutputPorts(RecipeNode node) {
        JsonArray array = new JsonArray();
        for (int i = 0; i < node.getOutputs().size(); i++) {
            IngredientStack stack = node.getOutputs().get(i);
            JsonObject portObj = new JsonObject();
            portObj.addProperty("portId", "out_" + i);
            portObj.addProperty("type", stack.isFluid() ? "FLUID" : (stack.isStressUnit() ? "STRESS" : "ITEM"));
            portObj.addProperty("id", stack.getId() != null ? stack.getId().toString() : "");
            portObj.addProperty("displayName", stack.getDisplayName() != null ? stack.getDisplayName() : "");
            portObj.addProperty("amount", stack.getAmount());
            portObj.addProperty("ratePerSec", roundThreeDecimals(NodeRateCalculator.getOutputSlotRate(node, i, true)));
            array.add(portObj);
        }
        return array;
    }

    private static JsonArray serializeConnections(FlowGraph graph) {
        JsonArray array = new JsonArray();
        if (graph == null) return array;

        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            JsonObject edgeObj = new JsonObject();
            edgeObj.addProperty("fromNode", edge.fromNodeId());
            edgeObj.addProperty("fromPort", "out_" + edge.outputIndex());
            edgeObj.addProperty("toNode", edge.toNodeId());
            edgeObj.addProperty("toPort", "in_" + edge.inputIndex());

            CanvasGroupFrame fromFolded = graph.getFoldedFrameForNode(edge.fromNodeId());
            CanvasGroupFrame toFolded = graph.getFoldedFrameForNode(edge.toNodeId());
            if (fromFolded != null && toFolded != null && fromFolded.getId().equals(toFolded.getId())) {
                edgeObj.addProperty("isInternalFolded", true);
            }
            if (fromFolded != null) {
                var summary = com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.aggregateFoldedPorts(graph, fromFolded);
                int foldedOutIdx = summary.findOutputIndexForOrigin(edge.fromNodeId(), edge.outputIndex());
                edgeObj.addProperty("fromFoldedFrame", fromFolded.getId());
                edgeObj.addProperty("fromFoldedPortIndex", foldedOutIdx >= 0 ? foldedOutIdx : 0);
            }
            if (toFolded != null) {
                var summary = com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.aggregateFoldedPorts(graph, toFolded);
                int foldedInIdx = summary.findInputIndexForOrigin(edge.toNodeId(), edge.inputIndex());
                edgeObj.addProperty("toFoldedFrame", toFolded.getId());
                edgeObj.addProperty("toFoldedPortIndex", foldedInIdx >= 0 ? foldedInIdx : 0);
            }

            double flowRate = calculateEdgeFlowRate(graph, edge);
            edgeObj.addProperty("flowRate", roundThreeDecimals(flowRate));
            edgeObj.addProperty("unit", resolveEdgeUnit(graph, edge));
            edgeObj.addProperty("priority", edge.priority());
            edgeObj.addProperty("weight", edge.weight());

            array.add(edgeObj);
        }
        return array;
    }

    private static double calculateEdgeFlowRate(FlowGraph graph, FlowGraph.ConnectionEdge edge) {
        RecipeNode from = graph.findNodeById(edge.fromNodeId());
        if (from == null || edge.outputIndex() < 0 || edge.outputIndex() >= from.getOutputs().size()) {
            return 0.0;
        }

        double baseRate;
        if (from.isReroute()) {
            baseRate = resolveRerouteOutflow(graph, from);
        } else {
            baseRate = NodeRateCalculator.getOutputSlotRate(from, edge.outputIndex(), true);
        }

        List<FlowGraph.ConnectionEdge> sameOutEdges = graph.getConnections().stream()
                .filter(e -> e.fromNodeId().equals(edge.fromNodeId()) && e.outputIndex() == edge.outputIndex())
                .toList();

        if (sameOutEdges.size() <= 1) {
            return edge.fixedFlowLimit() > 0 ? Math.min(baseRate, edge.fixedFlowLimit()) : baseRate;
        }

        double totalWeight = sameOutEdges.stream().mapToDouble(FlowGraph.ConnectionEdge::weight).sum();
        double weightFrac = totalWeight > 0.0001 ? (edge.weight() / totalWeight) : (1.0 / sameOutEdges.size());
        double splitRate = baseRate * weightFrac;

        return edge.fixedFlowLimit() > 0 ? Math.min(splitRate, edge.fixedFlowLimit()) : splitRate;
    }

    private static double resolveRerouteOutflow(FlowGraph graph, RecipeNode from) {
        if (from.isInfiniteSupply()) return 999999.0;
        if (from.isExternalSupply()) return from.getExternalSupplyRate();
        boolean hasIncoming = graph.getConnections().stream().anyMatch(e -> e.toNodeId().equals(from.getId()));
        if (hasIncoming) {
            return com.gtceu.calcboard.api.solver.ProductionETACalculator.calculateNetInflowRate(graph, from, 0);
        }
        return 0.0;
    }

    private static String resolveEdgeUnit(FlowGraph graph, FlowGraph.ConnectionEdge edge) {
        RecipeNode from = graph.findNodeById(edge.fromNodeId());
        if (from != null && edge.outputIndex() >= 0 && edge.outputIndex() < from.getOutputs().size()) {
            IngredientStack out = from.getOutputs().get(edge.outputIndex());
            if (out.isFluid()) return "L/s";
            if (out.isStressUnit()) return "su";
        }
        return "items/s";
    }

    private static JsonArray serializeFrames(FlowGraph graph) {
        JsonArray array = new JsonArray();
        if (graph == null) return array;

        for (CanvasGroupFrame frame : graph.getFrames()) {
            JsonObject frameObj = new JsonObject();
            frameObj.addProperty("id", frame.getId());
            frameObj.addProperty("title", frame.getTitle());
            frameObj.addProperty("color", frame.getColor());
            frameObj.addProperty("posX", frame.getPosX());
            frameObj.addProperty("posY", frame.getPosY());
            frameObj.addProperty("width", frame.getWidth());
            frameObj.addProperty("height", frame.getHeight());
            frameObj.addProperty("isFolded", frame.isFolded());
            frameObj.addProperty("isSharedMachine", frame.isSharedMachineFrame());
            frameObj.addProperty("viewMode", frame.getViewMode().name());

            if (frame.isSharedMachineFrame()) {
                net.minecraft.resources.ResourceLocation icon = frame.getSharedMachineIcon(graph);
                frameObj.addProperty("sharedMachineId", icon != null ? icon.toString() : "");
                frameObj.addProperty("sharedMachineName", frame.getSharedMachineName(graph));
                var tier = frame.getSharedVoltageTier(graph);
                frameObj.addProperty("sharedTier", tier != null ? tier.name() : "");
                frameObj.addProperty("targetCapacity", frame.getTargetPoolCapacity());
                frameObj.addProperty("totalDuty", roundThreeDecimals(frame.computeTotalMachineDuty(graph)));
                frameObj.addProperty("requiredMachines", frame.computeRequiredMachines(graph));
                frameObj.addProperty("totalEUt", roundThreeDecimals(frame.computeSharedTotalEUt(graph)));
                frameObj.addProperty("isCompatible", frame.isMachineCompatible(graph));

                JsonArray nodeIds = new JsonArray();
                for (String nid : frame.getContainedNodeIds()) {
                    nodeIds.add(nid);
                }
                frameObj.add("containedNodeIds", nodeIds);

                if (frame.getViewMode() == com.gtceu.calcboard.api.model.PoolViewMode.FOLDED_CARD) {
                    frameObj.add("foldedPorts", serializeFoldedPorts(graph, frame));
                }
            }

            array.add(frameObj);
        }
        return array;
    }

    private static JsonObject serializeFoldedPorts(FlowGraph graph, CanvasGroupFrame frame) {
        var summary = com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.aggregateFoldedPorts(graph, frame);
        JsonObject fpObj = new JsonObject();
        JsonArray fpIn = new JsonArray();
        for (var port : summary.inputs()) {
            fpIn.add(serializeFoldedPort(port));
        }
        fpObj.add("inputs", fpIn);

        JsonArray fpOut = new JsonArray();
        for (var port : summary.outputs()) {
            fpOut.add(serializeFoldedPort(port));
        }
        fpObj.add("outputs", fpOut);
        return fpObj;
    }

    private static JsonObject serializeFoldedPort(com.gtceu.calcboard.api.solver.FlowGraphTopologyAnalyzer.AggregatedFoldedPort port) {
        JsonObject p = new JsonObject();
        IngredientStack is = port.ingredient();
        p.addProperty("type", is.isFluid() ? "FLUID" : (is.isStressUnit() ? "STRESS" : "ITEM"));
        p.addProperty("id", is.getId() != null ? is.getId().toString() : "");
        p.addProperty("displayName", is.getDisplayName() != null ? is.getDisplayName() : "");
        p.addProperty("amount", is.getAmount());
        p.addProperty("ratePerSec", roundThreeDecimals(port.actualRate()));
        return p;
    }

    private static JsonArray serializeStickyNotes(FlowGraph graph) {
        JsonArray array = new JsonArray();
        if (graph == null) return array;

        for (CanvasStickyNote note : graph.getStickyNotes()) {
            JsonObject noteObj = new JsonObject();
            noteObj.addProperty("id", note.getId());
            noteObj.addProperty("title", note.getTitle());
            noteObj.addProperty("content", note.getContent());
            noteObj.addProperty("color", note.getColor());
            noteObj.addProperty("posX", note.getPosX());
            noteObj.addProperty("posY", note.getPosY());
            noteObj.addProperty("width", note.getWidth());
            noteObj.addProperty("height", note.getHeight());
            array.add(noteObj);
        }
        return array;
    }

    private static double roundThreeDecimals(double val) {
        return Math.round(val * 1000.0) / 1000.0;
    }
}
