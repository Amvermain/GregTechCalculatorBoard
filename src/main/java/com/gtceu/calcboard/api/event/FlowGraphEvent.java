package com.gtceu.calcboard.api.event;

import com.gtceu.calcboard.api.model.FlowGraph;
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
        LISTENER_LIST = new ListenerList();
        PreSolve.LISTENER_LIST = new ListenerList(LISTENER_LIST);
        PostSolve.LISTENER_LIST = new ListenerList(LISTENER_LIST);
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
}

