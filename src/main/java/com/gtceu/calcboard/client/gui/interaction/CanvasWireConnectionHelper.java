package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import net.minecraft.resources.ResourceLocation;

public final class CanvasWireConnectionHelper {

    private CanvasWireConnectionHelper() {}

    public static boolean matchesIngredient(IngredientStack out, IngredientStack in) {
        if (out == null || in == null) return false;
        if (out.getId().equals(in.getId())) return true;
        if (in.getAlternatives() != null && in.getAlternatives().contains(out.getId())) {
            in.selectAlternative(out.getId());
            return true;
        }
        if (out.getAlternatives() != null && out.getAlternatives().contains(in.getId())) {
            out.selectAlternative(in.getId());
            return true;
        }
        if (in.getAlternatives() != null && out.getAlternatives() != null) {
            for (ResourceLocation alt : in.getAlternatives()) {
                if (out.getAlternatives().contains(alt)) {
                    in.selectAlternative(alt);
                    out.selectAlternative(alt);
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isAlreadyConnected(FlowGraph graph, String fromNodeId, int outIdx, String toNodeId, int inIdx) {
        for (FlowGraph.ConnectionEdge e : graph.getConnections()) {
            if (e.fromNodeId().equals(fromNodeId) && e.outputIndex() == outIdx &&
                    e.toNodeId().equals(toNodeId) && e.inputIndex() == inIdx) {
                return true;
            }
        }
        return false;
    }
}
