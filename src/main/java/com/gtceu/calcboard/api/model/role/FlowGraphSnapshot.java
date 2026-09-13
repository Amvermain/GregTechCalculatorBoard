package com.gtceu.calcboard.api.model.role;

import java.util.Map;

/**
 * Immutable snapshot capturing node calculation states across an entire {@link com.gtceu.calcboard.api.model.FlowGraph}.
 */
public record FlowGraphSnapshot(
    Map<String, NodeCalculationSnapshot> nodeSnapshots,
    long timestamp
) {
    public static final FlowGraphSnapshot EMPTY = new FlowGraphSnapshot(Map.of(), 0L);

    public FlowGraphSnapshot {
        nodeSnapshots = nodeSnapshots != null ? Map.copyOf(nodeSnapshots) : Map.of();
    }

    public NodeCalculationSnapshot getNodeSnapshot(String nodeId) {
        if (nodeId == null) return NodeCalculationSnapshot.EMPTY;
        return nodeSnapshots.getOrDefault(nodeId, NodeCalculationSnapshot.EMPTY);
    }
}
