package com.gtceu.calcboard.api.history;

import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;

import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Represents a lightweight, vector/delta-based reversible action on a Calculator Board FlowGraph.
 */
public interface BoardCommand {

    void undo(FlowGraph graph);

    void redo(FlowGraph graph);

    String getDescription();

    /**
     * Vector translation command for moving one or more nodes by (dx, dy).
     */
    class MoveNodesCommand implements BoardCommand {
        private final Map<String, double[]> deltas; // nodeId -> [dx, dy]

        public MoveNodesCommand(Map<String, double[]> deltas) {
            this.deltas = new HashMap<>(deltas);
        }

        @Override
        public void undo(FlowGraph graph) {
            for (Map.Entry<String, double[]> entry : deltas.entrySet()) {
                RecipeNode node = graph.findNodeById(entry.getKey());
                if (node != null) {
                    node.setPosX(node.getPosX() - entry.getValue()[0]);
                    node.setPosY(node.getPosY() - entry.getValue()[1]);
                }
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            for (Map.Entry<String, double[]> entry : deltas.entrySet()) {
                RecipeNode node = graph.findNodeById(entry.getKey());
                if (node != null) {
                    node.setPosX(node.getPosX() + entry.getValue()[0]);
                    node.setPosY(node.getPosY() + entry.getValue()[1]);
                }
            }
        }

        @Override
        public String getDescription() {
            return "Move " + deltas.size() + " components";
        }
    }

    /**
     * Vector translation command for moving nodes, sticky notes, and frames by (dx, dy).
     */
    class MoveComponentsCommand implements BoardCommand {
        private final Map<String, double[]> nodeDeltas;
        private final Map<String, double[]> noteDeltas;
        private final Map<String, double[]> frameDeltas;

        public MoveComponentsCommand(Map<String, double[]> nodeDeltas, Map<String, double[]> noteDeltas, Map<String, double[]> frameDeltas) {
            this.nodeDeltas = nodeDeltas != null ? new HashMap<>(nodeDeltas) : Collections.emptyMap();
            this.noteDeltas = noteDeltas != null ? new HashMap<>(noteDeltas) : Collections.emptyMap();
            this.frameDeltas = frameDeltas != null ? new HashMap<>(frameDeltas) : Collections.emptyMap();
        }

        public MoveComponentsCommand(Map<String, double[]> nodeDeltas) {
            this(nodeDeltas, Collections.emptyMap(), Collections.emptyMap());
        }

        @Override
        public void undo(FlowGraph graph) {
            for (Map.Entry<String, double[]> entry : nodeDeltas.entrySet()) {
                RecipeNode node = graph.findNodeById(entry.getKey());
                if (node != null) {
                    node.setPosX(node.getPosX() - entry.getValue()[0]);
                    node.setPosY(node.getPosY() - entry.getValue()[1]);
                }
            }
            for (Map.Entry<String, double[]> entry : noteDeltas.entrySet()) {
                CanvasStickyNote note = graph.findStickyNoteById(entry.getKey());
                if (note != null) {
                    note.moveBy(-entry.getValue()[0], -entry.getValue()[1]);
                }
            }
            for (Map.Entry<String, double[]> entry : frameDeltas.entrySet()) {
                CanvasGroupFrame frame = graph.findFrameById(entry.getKey());
                if (frame != null) {
                    frame.moveBy(-entry.getValue()[0], -entry.getValue()[1]);
                }
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            for (Map.Entry<String, double[]> entry : nodeDeltas.entrySet()) {
                RecipeNode node = graph.findNodeById(entry.getKey());
                if (node != null) {
                    node.setPosX(node.getPosX() + entry.getValue()[0]);
                    node.setPosY(node.getPosY() + entry.getValue()[1]);
                }
            }
            for (Map.Entry<String, double[]> entry : noteDeltas.entrySet()) {
                CanvasStickyNote note = graph.findStickyNoteById(entry.getKey());
                if (note != null) {
                    note.moveBy(entry.getValue()[0], entry.getValue()[1]);
                }
            }
            for (Map.Entry<String, double[]> entry : frameDeltas.entrySet()) {
                CanvasGroupFrame frame = graph.findFrameById(entry.getKey());
                if (frame != null) {
                    frame.moveBy(entry.getValue()[0], entry.getValue()[1]);
                }
            }
        }

        @Override
        public String getDescription() {
            int total = nodeDeltas.size() + noteDeltas.size() + frameDeltas.size();
            return "Move " + total + " components";
        }
    }

    /**
     * Addition of one or more recipe nodes and their internal connections.
     */
    class AddNodesCommand implements BoardCommand {
        private final List<RecipeNode> nodes;
        private final List<FlowGraph.ConnectionEdge> edges;
        private final String description;

        public AddNodesCommand(List<RecipeNode> nodes, List<FlowGraph.ConnectionEdge> edges, String description) {
            this.nodes = new ArrayList<>(nodes);
            this.edges = edges != null ? new ArrayList<>(edges) : Collections.emptyList();
            this.description = description;
        }

        public AddNodesCommand(RecipeNode node, String description) {
            this(List.of(node), Collections.emptyList(), description);
        }

        @Override
        public void undo(FlowGraph graph) {
            for (FlowGraph.ConnectionEdge e : edges) {
                graph.getConnections().remove(e);
            }
            for (RecipeNode n : nodes) {
                graph.removeNode(n);
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            for (RecipeNode n : nodes) {
                if (!graph.getNodes().contains(n)) {
                    graph.addNode(n);
                }
            }
            for (FlowGraph.ConnectionEdge e : edges) {
                if (!graph.getConnections().contains(e)) {
                    graph.getConnections().add(e);
                }
            }
        }

        @Override
        public String getDescription() {
            return description != null ? description : "Add " + nodes.size() + " components";
        }
    }

    /**
     * Removal (Delete / Cut) of one or more recipe nodes and their associated wires.
     */
    class RemoveNodesCommand implements BoardCommand {
        private final List<RecipeNode> nodes;
        private final List<FlowGraph.ConnectionEdge> edges;
        private final String description;

        public RemoveNodesCommand(List<RecipeNode> nodes, List<FlowGraph.ConnectionEdge> edges, String description) {
            this.nodes = new ArrayList<>(nodes);
            this.edges = new ArrayList<>(edges);
            this.description = description;
        }

        @Override
        public void undo(FlowGraph graph) {
            for (RecipeNode n : nodes) {
                if (!graph.getNodes().contains(n)) {
                    graph.addNode(n);
                }
            }
            for (FlowGraph.ConnectionEdge e : edges) {
                if (!graph.getConnections().contains(e)) {
                    graph.getConnections().add(e);
                }
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            for (FlowGraph.ConnectionEdge e : edges) {
                graph.getConnections().remove(e);
            }
            for (RecipeNode n : nodes) {
                graph.removeNode(n);
            }
        }

        @Override
        public String getDescription() {
            return description != null ? description : "Remove " + nodes.size() + " components";
        }
    }

    /**
     * Wire connection creation (with optional machine count delta for Shift-drag balance).
     */
    class ConnectWireCommand implements BoardCommand {
        private final FlowGraph.ConnectionEdge edge;
        private final String scaledNodeId;
        private final Double oldMachineCount;
        private final Double newMachineCount;

        public ConnectWireCommand(FlowGraph.ConnectionEdge edge, String scaledNodeId, Double oldMachineCount, Double newMachineCount) {
            this.edge = edge;
            this.scaledNodeId = scaledNodeId;
            this.oldMachineCount = oldMachineCount;
            this.newMachineCount = newMachineCount;
        }

        public ConnectWireCommand(FlowGraph.ConnectionEdge edge) {
            this(edge, null, null, null);
        }

        @Override
        public void undo(FlowGraph graph) {
            graph.getConnections().remove(edge);
            if (scaledNodeId != null && oldMachineCount != null) {
                RecipeNode node = graph.findNodeById(scaledNodeId);
                if (node != null) {
                    node.setMachineCount(oldMachineCount);
                }
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            if (!graph.getConnections().contains(edge)) {
                graph.getConnections().add(edge);
            }
            if (scaledNodeId != null && newMachineCount != null) {
                RecipeNode node = graph.findNodeById(scaledNodeId);
                if (node != null) {
                    node.setMachineCount(newMachineCount);
                }
            }
        }

        @Override
        public String getDescription() {
            return "Connect wire";
        }
    }

    /**
     * Wire severance / disconnection.
     */
    class DisconnectWireCommand implements BoardCommand {
        private final FlowGraph.ConnectionEdge edge;

        public DisconnectWireCommand(FlowGraph.ConnectionEdge edge) {
            this.edge = edge;
        }

        @Override
        public void undo(FlowGraph graph) {
            if (!graph.getConnections().contains(edge)) {
                graph.getConnections().add(edge);
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            graph.getConnections().remove(edge);
        }

        @Override
        public String getDescription() {
            return "Disconnect wire";
        }
    }

    /**
     * Node property modification (Count, Tier, OC, Parallel, Rotor, Title, Anchor).
     */
    class ModifyPropertyCommand<T> implements BoardCommand {
        public enum Property {
            MACHINE_COUNT,
            TARGET_TIER,
            OVERCLOCK_MODE,
            PARALLEL,
            CUSTOM_NAME,
            BASE_ANCHOR,
            ROTOR_EFFICIENCY,
            ROTOR_POWER,
            ROTOR_NAME
        }

        private final String nodeId;
        private final Property property;
        private final T oldValue;
        private final T newValue;

        public ModifyPropertyCommand(String nodeId, Property property, T oldValue, T newValue) {
            this.nodeId = nodeId;
            this.property = property;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        public static ModifyPropertyCommand<Double> machineCount(String nodeId, double oldVal, double newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.MACHINE_COUNT, oldVal, newVal);
        }

        public static ModifyPropertyCommand<com.gtceu.calcboard.api.type.GTVoltageTier> targetTier(String nodeId, com.gtceu.calcboard.api.type.GTVoltageTier oldVal, com.gtceu.calcboard.api.type.GTVoltageTier newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.TARGET_TIER, oldVal, newVal);
        }

        public static ModifyPropertyCommand<com.gtceu.calcboard.api.type.OverclockMode> overclockMode(String nodeId, com.gtceu.calcboard.api.type.OverclockMode oldVal, com.gtceu.calcboard.api.type.OverclockMode newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.OVERCLOCK_MODE, oldVal, newVal);
        }

        public static ModifyPropertyCommand<Integer> parallel(String nodeId, int oldVal, int newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.PARALLEL, oldVal, newVal);
        }

        public static ModifyPropertyCommand<String> customName(String nodeId, String oldVal, String newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.CUSTOM_NAME, oldVal, newVal);
        }

        public static ModifyPropertyCommand<Boolean> baseAnchor(String nodeId, boolean oldVal, boolean newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.BASE_ANCHOR, oldVal, newVal);
        }

        public static ModifyPropertyCommand<Integer> rotorEfficiency(String nodeId, int oldVal, int newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.ROTOR_EFFICIENCY, oldVal, newVal);
        }

        public static ModifyPropertyCommand<Integer> rotorPower(String nodeId, int oldVal, int newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.ROTOR_POWER, oldVal, newVal);
        }

        public static ModifyPropertyCommand<String> rotorName(String nodeId, String oldVal, String newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.ROTOR_NAME, oldVal, newVal);
        }

        @Override
        public void undo(FlowGraph graph) {
            applyValue(graph, oldValue);
        }

        @Override
        public void redo(FlowGraph graph) {
            applyValue(graph, newValue);
        }

        private void applyValue(FlowGraph graph, T val) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node == null || val == null) return;

            switch (property) {
                case MACHINE_COUNT -> {
                    if (val instanceof Number n) node.setMachineCount(n.doubleValue());
                }
                case TARGET_TIER -> {
                    if (val instanceof com.gtceu.calcboard.api.type.GTVoltageTier t) node.setTargetTier(t);
                }
                case OVERCLOCK_MODE -> {
                    if (val instanceof com.gtceu.calcboard.api.type.OverclockMode m) node.setOverclockMode(m);
                }
                case PARALLEL -> {
                    if (val instanceof Number n) node.setParallel(n.intValue());
                }
                case CUSTOM_NAME -> {
                    if (val instanceof String s) node.setName(s);
                }
                case BASE_ANCHOR -> {
                    if (val instanceof Boolean b) {
                        if (b) {
                            for (RecipeNode n : graph.getNodes()) {
                                n.setBaseNode(n.getId().equals(nodeId));
                            }
                        } else {
                            node.setBaseNode(false);
                        }
                    }
                }
                case ROTOR_EFFICIENCY -> {
                    if (val instanceof Number n) node.setRotorEfficiency(n.intValue());
                }
                case ROTOR_POWER -> {
                    if (val instanceof Number n) node.setRotorPower(n.intValue());
                }
                case ROTOR_NAME -> {
                    if (val instanceof String s) node.setRotorName(s);
                }
            }
        }

        @Override
        public String getDescription() {
            return "Modify " + property.name().toLowerCase().replace('_', ' ');
        }
    }

    /**
     * Compound grouping of multiple nodes into a single compound module.
     */
    class GroupModuleCommand implements BoardCommand {
        private final List<RecipeNode> groupedNodes;
        private final RecipeNode moduleNode;
        private final List<FlowGraph.ConnectionEdge> originalEdges;
        private final List<FlowGraph.ConnectionEdge> rewires;
        private final List<CanvasGroupFrame> capturedFrames;
        private final List<CanvasStickyNote> capturedNotes;

        public GroupModuleCommand(
                List<RecipeNode> groupedNodes,
                RecipeNode moduleNode,
                List<FlowGraph.ConnectionEdge> originalEdges,
                List<FlowGraph.ConnectionEdge> rewires,
                List<CanvasGroupFrame> capturedFrames,
                List<CanvasStickyNote> capturedNotes
        ) {
            this.groupedNodes = new ArrayList<>(groupedNodes);
            this.moduleNode = moduleNode;
            this.originalEdges = new ArrayList<>(originalEdges);
            this.rewires = new ArrayList<>(rewires);
            this.capturedFrames = capturedFrames != null ? new ArrayList<>(capturedFrames) : Collections.emptyList();
            this.capturedNotes = capturedNotes != null ? new ArrayList<>(capturedNotes) : Collections.emptyList();
        }

        public GroupModuleCommand(List<RecipeNode> groupedNodes, RecipeNode moduleNode, List<FlowGraph.ConnectionEdge> originalEdges, List<FlowGraph.ConnectionEdge> rewires) {
            this(groupedNodes, moduleNode, originalEdges, rewires,
                    moduleNode != null && moduleNode.getSubGraph() != null ? moduleNode.getSubGraph().getFrames() : Collections.emptyList(),
                    moduleNode != null && moduleNode.getSubGraph() != null ? moduleNode.getSubGraph().getStickyNotes() : Collections.emptyList());
        }

        @Override
        public void undo(FlowGraph graph) {
            graph.removeNode(moduleNode);
            for (FlowGraph.ConnectionEdge r : rewires) {
                graph.getConnections().remove(r);
            }
            for (RecipeNode n : groupedNodes) {
                if (!graph.getNodes().contains(n)) {
                    graph.addNode(n);
                }
            }
            for (FlowGraph.ConnectionEdge e : originalEdges) {
                if (!graph.getConnections().contains(e)) {
                    graph.getConnections().add(e);
                }
            }
            for (CanvasGroupFrame f : capturedFrames) {
                if (!graph.getFrames().contains(f)) {
                    graph.addFrame(f);
                }
            }
            for (CanvasStickyNote note : capturedNotes) {
                if (!graph.getStickyNotes().contains(note)) {
                    graph.addStickyNote(note);
                }
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            for (RecipeNode n : groupedNodes) {
                graph.removeNode(n);
            }
            if (!graph.getNodes().contains(moduleNode)) {
                graph.addNode(moduleNode);
            }
            for (FlowGraph.ConnectionEdge r : rewires) {
                if (!graph.getConnections().contains(r)) {
                    graph.getConnections().add(r);
                }
            }
            for (CanvasGroupFrame f : capturedFrames) {
                graph.removeFrame(f);
            }
            for (CanvasStickyNote note : capturedNotes) {
                graph.removeStickyNote(note);
            }
        }

        @Override
        public String getDescription() {
            return "Group " + groupedNodes.size() + " components into Module";
        }
    }

    /**
     * Expansion of a compound module back into its constituent machines.
     */
    class ExpandModuleCommand implements BoardCommand {
        private final RecipeNode moduleNode;
        private final List<RecipeNode> expandedNodes;
        private final List<FlowGraph.ConnectionEdge> restoredEdges;
        private final List<FlowGraph.ConnectionEdge> moduleEdges;
        private final List<CanvasGroupFrame> expandedFrames;
        private final List<CanvasStickyNote> expandedNotes;

        public ExpandModuleCommand(
                RecipeNode moduleNode,
                List<RecipeNode> expandedNodes,
                List<FlowGraph.ConnectionEdge> restoredEdges,
                List<FlowGraph.ConnectionEdge> moduleEdges,
                List<CanvasGroupFrame> expandedFrames,
                List<CanvasStickyNote> expandedNotes
        ) {
            this.moduleNode = moduleNode;
            this.expandedNodes = new ArrayList<>(expandedNodes);
            this.restoredEdges = new ArrayList<>(restoredEdges);
            this.moduleEdges = new ArrayList<>(moduleEdges);
            this.expandedFrames = expandedFrames != null ? new ArrayList<>(expandedFrames) : Collections.emptyList();
            this.expandedNotes = expandedNotes != null ? new ArrayList<>(expandedNotes) : Collections.emptyList();
        }

        public ExpandModuleCommand(RecipeNode moduleNode, List<RecipeNode> expandedNodes, List<FlowGraph.ConnectionEdge> restoredEdges, List<FlowGraph.ConnectionEdge> moduleEdges) {
            this(moduleNode, expandedNodes, restoredEdges, moduleEdges,
                    moduleNode != null && moduleNode.getSubGraph() != null ? moduleNode.getSubGraph().getFrames() : Collections.emptyList(),
                    moduleNode != null && moduleNode.getSubGraph() != null ? moduleNode.getSubGraph().getStickyNotes() : Collections.emptyList());
        }

        @Override
        public void undo(FlowGraph graph) {
            for (RecipeNode n : expandedNodes) {
                graph.removeNode(n);
            }
            for (FlowGraph.ConnectionEdge e : restoredEdges) {
                graph.getConnections().remove(e);
            }
            for (CanvasGroupFrame f : expandedFrames) {
                graph.removeFrame(f);
            }
            for (CanvasStickyNote note : expandedNotes) {
                graph.removeStickyNote(note);
            }
            if (!graph.getNodes().contains(moduleNode)) {
                graph.addNode(moduleNode);
            }
            for (FlowGraph.ConnectionEdge e : moduleEdges) {
                if (!graph.getConnections().contains(e)) {
                    graph.getConnections().add(e);
                }
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            graph.removeNode(moduleNode);
            for (FlowGraph.ConnectionEdge e : moduleEdges) {
                graph.getConnections().remove(e);
            }
            for (RecipeNode n : expandedNodes) {
                if (!graph.getNodes().contains(n)) {
                    graph.addNode(n);
                }
            }
            for (FlowGraph.ConnectionEdge e : restoredEdges) {
                if (!graph.getConnections().contains(e)) {
                    graph.getConnections().add(e);
                }
            }
            for (CanvasGroupFrame f : expandedFrames) {
                if (!graph.getFrames().contains(f)) {
                    graph.addFrame(f);
                }
            }
            for (CanvasStickyNote note : expandedNotes) {
                if (!graph.getStickyNotes().contains(note)) {
                    graph.addStickyNote(note);
                }
            }
        }

        @Override
        public String getDescription() {
            return "Expand Module into " + expandedNodes.size() + " components";
        }
    }

    /**
     * Batch command aggregating multiple individual commands (e.g. Auto Ratio, Auto Connect, Max Flow).
     */
    class CompoundCommand implements BoardCommand {
        private final List<BoardCommand> commands;
        private final String description;

        public CompoundCommand(List<BoardCommand> commands, String description) {
            this.commands = new ArrayList<>(commands);
            this.description = description;
        }

        @Override
        public void undo(FlowGraph graph) {
            for (int i = commands.size() - 1; i >= 0; i--) {
                commands.get(i).undo(graph);
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            for (BoardCommand cmd : commands) {
                cmd.redo(graph);
            }
        }

        @Override
        public String getDescription() {
            return description != null ? description : "Batch operation (" + commands.size() + " actions)";
        }
    }

    /**
     * Reversible command for horizontally flipping one or more nodes.
     */
    class FlipNodesCommand implements BoardCommand {
        private final Map<String, Boolean> previousStates;
        private final Map<String, Boolean> newStates;

        public FlipNodesCommand(Map<String, Boolean> previousStates, Map<String, Boolean> newStates) {
            this.previousStates = new HashMap<>(previousStates);
            this.newStates = new HashMap<>(newStates);
        }

        public FlipNodesCommand(RecipeNode node, boolean previousState, boolean newState) {
            this(Map.of(node.getId(), previousState), Map.of(node.getId(), newState));
        }

        @Override
        public void undo(FlowGraph graph) {
            for (Map.Entry<String, Boolean> entry : previousStates.entrySet()) {
                RecipeNode node = graph.findNodeById(entry.getKey());
                if (node != null) {
                    node.setFlipped(entry.getValue());
                }
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            for (Map.Entry<String, Boolean> entry : newStates.entrySet()) {
                RecipeNode node = graph.findNodeById(entry.getKey());
                if (node != null) {
                    node.setFlipped(entry.getValue());
                }
            }
        }

        @Override
        public String getDescription() {
            return "Flip " + newStates.size() + " nodes horizontally";
        }
    }

    /**
     * Toggling or selecting an alternative ingredient on an input/output slot.
     */
    class SelectAlternativeCommand implements BoardCommand {
        private final String nodeId;
        private final int slotIndex;
        private final boolean isInput;
        private final ResourceLocation oldAlternativeId;
        private final ResourceLocation newAlternativeId;

        public SelectAlternativeCommand(String nodeId, int slotIndex, boolean isInput, ResourceLocation oldAlternativeId, ResourceLocation newAlternativeId) {
            this.nodeId = nodeId;
            this.slotIndex = slotIndex;
            this.isInput = isInput;
            this.oldAlternativeId = oldAlternativeId;
            this.newAlternativeId = newAlternativeId;
        }

        @Override
        public void undo(FlowGraph graph) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node != null) {
                List<IngredientStack> list = isInput ? node.getInputs() : node.getOutputs();
                if (slotIndex >= 0 && slotIndex < list.size()) {
                    list.get(slotIndex).selectAlternative(oldAlternativeId);
                }
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node != null) {
                List<IngredientStack> list = isInput ? node.getInputs() : node.getOutputs();
                if (slotIndex >= 0 && slotIndex < list.size()) {
                    list.get(slotIndex).selectAlternative(newAlternativeId);
                }
            }
        }

        @Override
        public String getDescription() {
            return "Select alternative ingredient";
        }
    }

    /**
     * Addition of one or more sticky notes.
     */
    class AddStickyNotesCommand implements BoardCommand {
        private final List<CanvasStickyNote> notes;
        private final String description;

        public AddStickyNotesCommand(List<CanvasStickyNote> notes, String description) {
            this.notes = new ArrayList<>(notes);
            this.description = description;
        }

        public AddStickyNotesCommand(CanvasStickyNote note, String description) {
            this(List.of(note), description);
        }

        @Override
        public void undo(FlowGraph graph) {
            for (CanvasStickyNote note : notes) {
                graph.removeStickyNote(note);
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            for (CanvasStickyNote note : notes) {
                if (!graph.getStickyNotes().contains(note)) {
                    graph.addStickyNote(note);
                }
            }
        }

        @Override
        public String getDescription() {
            return description != null ? description : "Add " + notes.size() + " sticky notes";
        }
    }

    /**
     * Removal of one or more sticky notes.
     */
    class RemoveStickyNotesCommand implements BoardCommand {
        private final List<CanvasStickyNote> notes;
        private final String description;

        public RemoveStickyNotesCommand(List<CanvasStickyNote> notes, String description) {
            this.notes = new ArrayList<>(notes);
            this.description = description;
        }

        public RemoveStickyNotesCommand(CanvasStickyNote note, String description) {
            this(List.of(note), description);
        }

        @Override
        public void undo(FlowGraph graph) {
            for (CanvasStickyNote note : notes) {
                if (!graph.getStickyNotes().contains(note)) {
                    graph.addStickyNote(note);
                }
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            for (CanvasStickyNote note : notes) {
                graph.removeStickyNote(note);
            }
        }

        @Override
        public String getDescription() {
            return description != null ? description : "Remove " + notes.size() + " sticky notes";
        }
    }

    /**
     * Addition of one or more group frames.
     */
    class AddFramesCommand implements BoardCommand {
        private final List<CanvasGroupFrame> frames;
        private final String description;

        public AddFramesCommand(List<CanvasGroupFrame> frames, String description) {
            this.frames = new ArrayList<>(frames);
            this.description = description;
        }

        public AddFramesCommand(CanvasGroupFrame frame, String description) {
            this(List.of(frame), description);
        }

        @Override
        public void undo(FlowGraph graph) {
            for (CanvasGroupFrame frame : frames) {
                graph.removeFrame(frame);
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            for (CanvasGroupFrame frame : frames) {
                if (!graph.getFrames().contains(frame)) {
                    graph.addFrame(frame);
                }
            }
        }

        @Override
        public String getDescription() {
            return description != null ? description : "Add " + frames.size() + " frames";
        }
    }

    /**
     * Removal of one or more group frames.
     */
    class RemoveFramesCommand implements BoardCommand {
        private final List<CanvasGroupFrame> frames;
        private final String description;

        public RemoveFramesCommand(List<CanvasGroupFrame> frames, String description) {
            this.frames = new ArrayList<>(frames);
            this.description = description;
        }

        public RemoveFramesCommand(CanvasGroupFrame frame, String description) {
            this(List.of(frame), description);
        }

        @Override
        public void undo(FlowGraph graph) {
            for (CanvasGroupFrame frame : frames) {
                if (!graph.getFrames().contains(frame)) {
                    graph.addFrame(frame);
                }
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            for (CanvasGroupFrame frame : frames) {
                graph.removeFrame(frame);
            }
        }

        @Override
        public String getDescription() {
            return description != null ? description : "Remove " + frames.size() + " frames";
        }
    }

    /**
     * Resizing and repositioning of a group frame.
     */
    class ResizeFrameCommand implements BoardCommand {
        private final String frameId;
        private final double oldX, oldY, oldW, oldH;
        private final double newX, newY, newW, newH;
        private final String description;

        public ResizeFrameCommand(String frameId, double oldX, double oldY, double oldW, double oldH, double newX, double newY, double newW, double newH, String description) {
            this.frameId = frameId;
            this.oldX = oldX;
            this.oldY = oldY;
            this.oldW = oldW;
            this.oldH = oldH;
            this.newX = newX;
            this.newY = newY;
            this.newW = newW;
            this.newH = newH;
            this.description = description;
        }

        @Override
        public void undo(FlowGraph graph) {
            CanvasGroupFrame frame = graph.findFrameById(frameId);
            if (frame != null) {
                frame.setPosX(oldX);
                frame.setPosY(oldY);
                frame.setWidth(oldW);
                frame.setHeight(oldH);
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            CanvasGroupFrame frame = graph.findFrameById(frameId);
            if (frame != null) {
                frame.setPosX(newX);
                frame.setPosY(newY);
                frame.setWidth(newW);
                frame.setHeight(newH);
            }
        }

        @Override
        public String getDescription() {
            return description != null ? description : "Resize Frame";
        }
    }

    /**
     * In-place recipe switching for an existing node with smart wire preservation.
     */
    class SwitchRecipeCommand implements BoardCommand {
        public record RecipeSnapshot(
                String name,
                double baseDurationTicks,
                double baseEUt,
                com.gtceu.calcboard.api.type.GTVoltageTier recipeTier,
                ResourceLocation recipeCategoryId,
                List<IngredientStack> inputs,
                List<IngredientStack> outputs
        ) {
            public static RecipeSnapshot of(RecipeNode node) {
                List<IngredientStack> inList = new ArrayList<>();
                for (IngredientStack in : node.getInputs()) {
                    inList.add(in.copy());
                }
                List<IngredientStack> outList = new ArrayList<>();
                for (IngredientStack out : node.getOutputs()) {
                    outList.add(out.copy());
                }
                return new RecipeSnapshot(
                        node.getRawName(),
                        node.getBaseDurationTicks(),
                        node.getBaseEUt(),
                        node.getRecipeTier(),
                        node.getRecipeCategoryId(),
                        inList,
                        outList
                );
            }

            public void applyTo(RecipeNode node) {
                node.setName(name);
                node.setBaseDurationTicks(baseDurationTicks);
                node.setBaseEUt(baseEUt);
                node.setRecipeTier(recipeTier);
                node.setRecipeCategoryId(recipeCategoryId);
                node.getInputs().clear();
                for (IngredientStack in : inputs) {
                    node.getInputs().add(in.copy());
                }
                node.getOutputs().clear();
                for (IngredientStack out : outputs) {
                    node.getOutputs().add(out.copy());
                }
            }
        }

        private final String nodeId;
        private final RecipeSnapshot oldRecipe;
        private final RecipeSnapshot newRecipe;
        private final List<FlowGraph.ConnectionEdge> oldEdges;
        private final List<FlowGraph.ConnectionEdge> newEdges;

        public SwitchRecipeCommand(
                String nodeId,
                RecipeSnapshot oldRecipe,
                RecipeSnapshot newRecipe,
                List<FlowGraph.ConnectionEdge> oldEdges,
                List<FlowGraph.ConnectionEdge> newEdges
        ) {
            this.nodeId = nodeId;
            this.oldRecipe = oldRecipe;
            this.newRecipe = newRecipe;
            this.oldEdges = new ArrayList<>(oldEdges);
            this.newEdges = new ArrayList<>(newEdges);
        }

        @Override
        public void undo(FlowGraph graph) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node != null) {
                oldRecipe.applyTo(node);
            }
            graph.getConnections().removeIf(e -> e.fromNodeId().equals(nodeId) || e.toNodeId().equals(nodeId));
            for (FlowGraph.ConnectionEdge e : oldEdges) {
                if (!graph.getConnections().contains(e)) {
                    graph.getConnections().add(e);
                }
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node != null) {
                newRecipe.applyTo(node);
            }
            graph.getConnections().removeIf(e -> e.fromNodeId().equals(nodeId) || e.toNodeId().equals(nodeId));
            for (FlowGraph.ConnectionEdge e : newEdges) {
                if (!graph.getConnections().contains(e)) {
                    graph.getConnections().add(e);
                }
            }
        }

        @Override
        public String getDescription() {
            return "Switch recipe for " + (newRecipe != null ? newRecipe.name() : "node");
        }
    }

    /**
     * Reversible command for switching a RecipeNode's machine workstation/controller icon and its associated traits.
     */
    class SetMachineIconCommand implements BoardCommand {
        private final String nodeId;
        private final ResourceLocation oldIcon;
        private final ResourceLocation newIcon;
        private final boolean oldMultiblock;
        private final boolean newMultiblock;
        private final int oldParallel;
        private final int newParallel;
        private final com.gtceu.calcboard.api.type.SteamMode oldSteamMode;
        private final com.gtceu.calcboard.api.type.SteamMode newSteamMode;
        private final com.gtceu.calcboard.api.type.GTVoltageTier oldTier;
        private final com.gtceu.calcboard.api.type.GTVoltageTier newTier;

        public SetMachineIconCommand(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon,
                                     boolean oldMultiblock, int oldParallel, com.gtceu.calcboard.api.type.SteamMode oldSteamMode, com.gtceu.calcboard.api.type.GTVoltageTier oldTier) {
            this.nodeId = node.getId();
            this.oldIcon = oldIcon;
            this.newIcon = newIcon;
            this.oldMultiblock = oldMultiblock;
            this.newMultiblock = node.isMultiblock();
            this.oldParallel = oldParallel;
            this.newParallel = node.getParallel();
            this.oldSteamMode = oldSteamMode;
            this.newSteamMode = node.getSteamMode();
            this.oldTier = oldTier;
            this.newTier = node.getTargetTier();
        }

        @Override
        public void undo(FlowGraph graph) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node != null) {
                node.setMachineIcon(oldIcon);
                node.setMultiblock(oldMultiblock);
                node.setParallel(oldParallel);
                node.setSteamMode(oldSteamMode);
                node.setTargetTier(oldTier);
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node != null) {
                node.setMachineIcon(newIcon);
                node.setMultiblock(newMultiblock);
                node.setParallel(newParallel);
                node.setSteamMode(newSteamMode);
                node.setTargetTier(newTier);
            }
        }

        @Override
        public String getDescription() {
            return "Switch machine icon to " + (newIcon != null ? newIcon.getPath() : "none");
        }
    }

    /**
     * Modification of frame properties (title, color, shared machine mode).
     */
    class ModifyFramePropertiesCommand implements BoardCommand {
        private final String frameId;
        private final String oldTitle;
        private final String newTitle;
        private final int oldColor;
        private final int newColor;
        private final boolean oldShared;
        private final boolean newShared;

        public ModifyFramePropertiesCommand(
                String frameId,
                String oldTitle, String newTitle,
                int oldColor, int newColor,
                boolean oldShared, boolean newShared
        ) {
            this.frameId = frameId;
            this.oldTitle = oldTitle;
            this.newTitle = newTitle;
            this.oldColor = oldColor;
            this.newColor = newColor;
            this.oldShared = oldShared;
            this.newShared = newShared;
        }

        @Override
        public void undo(FlowGraph graph) {
            CanvasGroupFrame frame = graph.findFrameById(frameId);
            if (frame != null) {
                frame.setTitle(oldTitle);
                frame.setColor(oldColor);
                frame.setSharedMachineFrame(oldShared);
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            CanvasGroupFrame frame = graph.findFrameById(frameId);
            if (frame != null) {
                frame.setTitle(newTitle);
                frame.setColor(newColor);
                frame.setSharedMachineFrame(newShared);
            }
        }

        @Override
        public String getDescription() {
            return "Modify Frame Properties";
        }
    }

    /**
     * Modification of sticky note properties (title, content, color).
     */
    class ModifyNotePropertiesCommand implements BoardCommand {
        private final String noteId;
        private final String oldTitle;
        private final String newTitle;
        private final String oldContent;
        private final String newContent;
        private final int oldColor;
        private final int newColor;

        public ModifyNotePropertiesCommand(
                String noteId,
                String oldTitle, String newTitle,
                String oldContent, String newContent,
                int oldColor, int newColor
        ) {
            this.noteId = noteId;
            this.oldTitle = oldTitle;
            this.newTitle = newTitle;
            this.oldContent = oldContent;
            this.newContent = newContent;
            this.oldColor = oldColor;
            this.newColor = newColor;
        }

        @Override
        public void undo(FlowGraph graph) {
            CanvasStickyNote note = graph.findStickyNoteById(noteId);
            if (note != null) {
                note.setTitle(oldTitle);
                note.setContent(oldContent);
                note.setColor(oldColor);
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            CanvasStickyNote note = graph.findStickyNoteById(noteId);
            if (note != null) {
                note.setTitle(newTitle);
                note.setContent(newContent);
                note.setColor(newColor);
            }
        }

        @Override
        public String getDescription() {
            return "Modify Note Properties";
        }
    }

    /**
     * Resizing of a sticky note.
     */
    class ResizeStickyNoteCommand implements BoardCommand {
        private final String noteId;
        private final double oldW;
        private final double oldH;
        private final double newW;
        private final double newH;
        private final String description;

        public ResizeStickyNoteCommand(String noteId, double oldW, double oldH, double newW, double newH, String description) {
            this.noteId = noteId;
            this.oldW = oldW;
            this.oldH = oldH;
            this.newW = newW;
            this.newH = newH;
            this.description = description;
        }

        @Override
        public void undo(FlowGraph graph) {
            CanvasStickyNote note = graph.findStickyNoteById(noteId);
            if (note != null) {
                note.setWidth(oldW);
                note.setHeight(oldH);
            }
        }

        @Override
        public void redo(FlowGraph graph) {
            CanvasStickyNote note = graph.findStickyNoteById(noteId);
            if (note != null) {
                note.setWidth(newW);
                note.setHeight(newH);
            }
        }

        @Override
        public String getDescription() {
            return description != null ? description : "Resize Sticky Note";
        }
    }
}



