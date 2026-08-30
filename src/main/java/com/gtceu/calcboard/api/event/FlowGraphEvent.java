package com.gtceu.calcboard.api.event;

import com.gtceu.calcboard.api.model.FlowGraph;
import net.neoforged.bus.api.Event;

/**
 * Base NeoForge event for flow graph solver lifecycle hooks.
 */
public abstract class FlowGraphEvent extends Event {

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

    public static void clearListeners() {
        // No-op in NeoForge event system
    }

    /**
     * Fired before flow graph solving starts (AutoRatio BFS, bottleneck solving).
     */
    public static class PreSolve extends FlowGraphEvent {
        public PreSolve() {
            super(null);
        }

        public PreSolve(FlowGraph graph) {
            super(graph);
        }
    }

    /**
     * Fired after flow graph solving completes.
     */
    public static class PostSolve extends FlowGraphEvent {
        public PostSolve() {
            super(null);
        }

        public PostSolve(FlowGraph graph) {
            super(graph);
        }
    }
}

