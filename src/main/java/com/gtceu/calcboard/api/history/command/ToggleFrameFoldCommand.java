package com.gtceu.calcboard.api.history.command;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;

public class ToggleFrameFoldCommand implements BoardCommand {
    private final String frameId;
    private final boolean previousFolded;
    private final boolean newFolded;

    public ToggleFrameFoldCommand(String frameId, boolean previousFolded, boolean newFolded) {
        this.frameId = frameId;
        this.previousFolded = previousFolded;
        this.newFolded = newFolded;
    }

    @Override
    public void undo(FlowGraph graph) {
        if (graph == null) return;
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.setFolded(previousFolded, graph);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        if (graph == null) return;
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.setFolded(newFolded, graph);
        }
    }

    @Override
    public String getDescription() {
        return newFolded ? "Fold Frame" : "Unfold Frame";
    }
}
