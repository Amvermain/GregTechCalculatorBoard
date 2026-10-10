package com.gtceu.calcboard.api.history.command;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;

/**
 * Reversible command for flipping the horizontal orientation of a group frame
 * and all of its enclosed member nodes.
 */
public class FlipFrameCommand implements BoardCommand {

    private final String frameId;
    private final boolean previousFlipped;
    private final boolean newFlipped;

    /**
     * Constructs a new frame flipping command.
     *
     * @param frameId         target frame unique identifier
     * @param previousFlipped horizontal flip state before execution
     * @param newFlipped      horizontal flip state after execution
     */
    public FlipFrameCommand(String frameId, boolean previousFlipped, boolean newFlipped) {
        this.frameId = frameId;
        this.previousFlipped = previousFlipped;
        this.newFlipped = newFlipped;
    }

    @Override
    public void undo(FlowGraph graph) {
        if (graph == null) return;
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.setFlipped(previousFlipped);
            for (RecipeNode sub : frame.getSubNodes(graph)) {
                if (sub != null) {
                    sub.setFlipped(previousFlipped);
                }
            }
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        if (graph == null) return;
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.setFlipped(newFlipped);
            for (RecipeNode sub : frame.getSubNodes(graph)) {
                if (sub != null) {
                    sub.setFlipped(newFlipped);
                }
            }
        }
    }

    @Override
    public String getDescription() {
        return "Flip Frame " + (newFlipped ? "RTL" : "LTR");
    }
}
