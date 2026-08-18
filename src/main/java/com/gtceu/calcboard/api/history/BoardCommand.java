package com.gtceu.calcboard.api.history;

import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.RecipeNode;

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
    class ModifyPropertyCommand implements BoardCommand {
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
        private final Object oldValue;
        private final Object newValue;

        public ModifyPropertyCommand(String nodeId, Property property, Object oldValue, Object newValue) {
            this.nodeId = nodeId;
            this.property = property;
            this.oldValue = oldValue;
            this.newValue = newValue;
        }

        @Override
        public void undo(FlowGraph graph) {
            applyValue(graph, oldValue);
        }

        @Override
        public void redo(FlowGraph graph) {
            applyValue(graph, newValue);
        }

        private void applyValue(FlowGraph graph, Object val) {
            RecipeNode node = graph.findNodeById(nodeId);
            if (node == null || val == null) return;

            switch (property) {
                case MACHINE_COUNT -> node.setMachineCount(((Number) val).doubleValue());
                case TARGET_TIER -> node.setTargetTier((com.gtceu.calcboard.api.GTVoltageTier) val);
                case OVERCLOCK_MODE -> node.setOverclockMode((com.gtceu.calcboard.api.OverclockMode) val);
                case PARALLEL -> node.setParallel(((Number) val).intValue());
                case CUSTOM_NAME -> node.setName((String) val);
                case BASE_ANCHOR -> {
                    boolean isBase = (Boolean) val;
                    if (isBase) {
                        for (RecipeNode n : graph.getNodes()) {
                            n.setBaseNode(n.getId().equals(nodeId));
                        }
                    } else {
                        node.setBaseNode(false);
                    }
                }
                case ROTOR_EFFICIENCY -> node.setRotorEfficiency(((Number) val).intValue());
                case ROTOR_POWER -> node.setRotorPower(((Number) val).intValue());
                case ROTOR_NAME -> node.setRotorName((String) val);
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

        public GroupModuleCommand(List<RecipeNode> groupedNodes, RecipeNode moduleNode, List<FlowGraph.ConnectionEdge> originalEdges, List<FlowGraph.ConnectionEdge> rewires) {
            this.groupedNodes = new ArrayList<>(groupedNodes);
            this.moduleNode = moduleNode;
            this.originalEdges = new ArrayList<>(originalEdges);
            this.rewires = new ArrayList<>(rewires);
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

        public ExpandModuleCommand(RecipeNode moduleNode, List<RecipeNode> expandedNodes, List<FlowGraph.ConnectionEdge> restoredEdges, List<FlowGraph.ConnectionEdge> moduleEdges) {
            this.moduleNode = moduleNode;
            this.expandedNodes = new ArrayList<>(expandedNodes);
            this.restoredEdges = new ArrayList<>(restoredEdges);
            this.moduleEdges = new ArrayList<>(moduleEdges);
        }

        @Override
        public void undo(FlowGraph graph) {
            for (RecipeNode n : expandedNodes) {
                graph.removeNode(n);
            }
            for (FlowGraph.ConnectionEdge e : restoredEdges) {
                graph.getConnections().remove(e);
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
}
