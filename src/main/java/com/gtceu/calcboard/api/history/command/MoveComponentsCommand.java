package com.gtceu.calcboard.api.history.command;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.type.SteamMode;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

/**
 * Vector translation command for moving nodes, sticky notes, and frames by (dx, dy).
 */
public class MoveComponentsCommand implements BoardCommand {
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
