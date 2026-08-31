package com.gtceu.calcboard.client.gui.model;

import java.util.Objects;

/**
 * Immutable reference identifying a specific port on a node.
 *
 * @param nodeId     The ID of the owner node.
 * @param isInput    True for input port, false for output port.
 * @param portIndex  The zero-based index of the port.
 */
public record PortRef(String nodeId, boolean isInput, int portIndex) {

    public PortRef {
        Objects.requireNonNull(nodeId, "nodeId cannot be null");
    }

    public static PortRef of(String nodeId, boolean isInput, int portIndex) {
        return new PortRef(nodeId, isInput, portIndex);
    }
}
