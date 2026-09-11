package com.gtceu.calcboard.api.event;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import net.minecraftforge.eventbus.ListenerList;
import net.minecraftforge.eventbus.api.Event;

/**
 * Base Forge event for flow graph solver lifecycle hooks.
 */
public abstract class FlowGraphEvent extends Event {

    private static ListenerList LISTENER_LIST = new ListenerList();
    private final FlowGraph graph;

    public FlowGraphEvent() {
        this(null);
    }

    public FlowGraphEvent(FlowGraph graph) {
        this.graph = graph;
    }

    public FlowGraph getGraph() {
        return graph;
    }

    @Override
    public ListenerList getListenerList() {
        return LISTENER_LIST;
    }

    public static void clearListeners() {
        try {
            java.lang.reflect.Field field = net.minecraftforge.eventbus.EventBus.class.getDeclaredField("busID");
            field.setAccessible(true);
            int busId = field.getInt(net.minecraftforge.common.MinecraftForge.EVENT_BUS);
            ListenerList.clearBusID(busId);
        } catch (Throwable ignored) {
            LISTENER_LIST = new ListenerList();
            PreSolve.LISTENER_LIST = new ListenerList(LISTENER_LIST);
            PostSolve.LISTENER_LIST = new ListenerList(LISTENER_LIST);
            WireConnected.LISTENER_LIST = new ListenerList(LISTENER_LIST);
            WireDisconnected.LISTENER_LIST = new ListenerList(LISTENER_LIST);
            JunctionInserted.LISTENER_LIST = new ListenerList(LISTENER_LIST);
            JunctionConfigured.LISTENER_LIST = new ListenerList(LISTENER_LIST);
        }
    }

    /**
     * Fired before flow graph solving starts (AutoRatio BFS, bottleneck solving).
     */
    public static class PreSolve extends FlowGraphEvent {
        private static ListenerList LISTENER_LIST = new ListenerList(FlowGraphEvent.LISTENER_LIST);

        public PreSolve() {
            super(null);
        }

        public PreSolve(FlowGraph graph) {
            super(graph);
        }

        @Override
        public ListenerList getListenerList() {
            return LISTENER_LIST;
        }
    }

    /**
     * Fired after flow graph solving completes.
     */
    public static class PostSolve extends FlowGraphEvent {
        private static ListenerList LISTENER_LIST = new ListenerList(FlowGraphEvent.LISTENER_LIST);

        public PostSolve() {
            super(null);
        }

        public PostSolve(FlowGraph graph) {
            super(graph);
        }

        @Override
        public ListenerList getListenerList() {
            return LISTENER_LIST;
        }
    }

    /**
     * Fired when a connection wire is created between two nodes.
     */
    public static class WireConnected extends FlowGraphEvent {
        private static ListenerList LISTENER_LIST = new ListenerList(FlowGraphEvent.LISTENER_LIST);
        private final FlowGraph.ConnectionEdge edge;
        private final boolean shiftDown;

        public WireConnected() {
            this(null, null, false);
        }

        public WireConnected(FlowGraph graph, FlowGraph.ConnectionEdge edge, boolean shiftDown) {
            super(graph);
            this.edge = edge;
            this.shiftDown = shiftDown;
        }

        public FlowGraph.ConnectionEdge getEdge() {
            return edge;
        }

        public boolean isShiftDown() {
            return shiftDown;
        }

        @Override
        public ListenerList getListenerList() {
            return LISTENER_LIST;
        }
    }

    /**
     * Fired when a connection wire is cut or removed.
     */
    public static class WireDisconnected extends FlowGraphEvent {
        private static ListenerList LISTENER_LIST = new ListenerList(FlowGraphEvent.LISTENER_LIST);
        private final FlowGraph.ConnectionEdge edge;

        public WireDisconnected() {
            this(null, null);
        }

        public WireDisconnected(FlowGraph graph, FlowGraph.ConnectionEdge edge) {
            super(graph);
            this.edge = edge;
        }

        public FlowGraph.ConnectionEdge getEdge() {
            return edge;
        }

        @Override
        public ListenerList getListenerList() {
            return LISTENER_LIST;
        }
    }

    /**
     * Fired when a wire is split by inserting a reroute junction node.
     */
    public static class JunctionInserted extends FlowGraphEvent {
        private static ListenerList LISTENER_LIST = new ListenerList(FlowGraphEvent.LISTENER_LIST);
        private final FlowGraph.ConnectionEdge originalEdge;
        private final RecipeNode junction;

        public JunctionInserted() {
            this(null, null, null);
        }

        public JunctionInserted(FlowGraph graph, FlowGraph.ConnectionEdge originalEdge, RecipeNode junction) {
            super(graph);
            this.originalEdge = originalEdge;
            this.junction = junction;
        }

        public FlowGraph.ConnectionEdge getOriginalEdge() {
            return originalEdge;
        }

        public RecipeNode getJunction() {
            return junction;
        }

        @Override
        public ListenerList getListenerList() {
            return LISTENER_LIST;
        }
    }

    /**
     * Fired when a junction node's external supply mode, drain, or buffer configuration is modified.
     */
    public static class JunctionConfigured extends FlowGraphEvent {
        private static ListenerList LISTENER_LIST = new ListenerList(FlowGraphEvent.LISTENER_LIST);
        private final RecipeNode junction;
        private final com.gtceu.calcboard.api.type.SupplyMode mode;

        public JunctionConfigured() {
            this(null, null, null);
        }

        public JunctionConfigured(FlowGraph graph, RecipeNode junction, com.gtceu.calcboard.api.type.SupplyMode mode) {
            super(graph);
            this.junction = junction;
            this.mode = mode;
        }

        public RecipeNode getJunction() {
            return junction;
        }

        public com.gtceu.calcboard.api.type.SupplyMode getMode() {
            return mode;
        }

        @Override
        public ListenerList getListenerList() {
            return LISTENER_LIST;
        }
    }
}

