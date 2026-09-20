package com.gtceu.calcboard.client.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.NodeRateCalculator;
import com.gtceu.calcboard.api.model.RecipeNode;

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

        JsonObject viewport = new JsonObject();
        viewport.addProperty("panX", panX);
        viewport.addProperty("panY", panY);
        viewport.addProperty("zoom", zoom);
        root.add("viewport", viewport);

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
            nodeObj.addProperty("width", resolveCardWidth(node));
            nodeObj.addProperty("height", resolveCardHeight(node));
            nodeObj.addProperty("isFlipped", node.isFlipped());

            nodeObj.add("metrics", serializeNodeMetrics(node));
            nodeObj.add("inputs", serializeInputPorts(node));
            nodeObj.add("outputs", serializeOutputPorts(node));

            array.add(nodeObj);
        }
        return array;
    }

    private static double resolveCardHeight(RecipeNode node) {
        if (node.isReroute() || node.isBoundaryPin()) return 32.0;
        int maxRows = Math.max(node.getInputs().size(), node.getOutputs().size());
        int contentStartY = node.isModule() ? 62 : 80;
        int autoHeight = contentStartY + Math.max(1, maxRows) * 18 + 8;
        return Math.max(autoHeight, (double) node.getCardHeight());
    }

    private static double resolveCardWidth(RecipeNode node) {
        if (node.isReroute() || node.isBoundaryPin()) return 32.0;
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
            array.add(frameObj);
        }
        return array;
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
