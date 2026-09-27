package com.gtceu.calcboard.api.history.command;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.PoolViewMode;

public class SetFrameViewModeCommand implements BoardCommand {
    private final String frameId;
    private final PoolViewMode previousMode;
    private final PoolViewMode newMode;

    public SetFrameViewModeCommand(String frameId, PoolViewMode previousMode, PoolViewMode newMode) {
        this.frameId = frameId;
        this.previousMode = previousMode;
        this.newMode = newMode;
    }

    @Override
    public void undo(FlowGraph graph) {
        if (graph == null) return;
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.setViewMode(previousMode, graph);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        if (graph == null) return;
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.setViewMode(newMode, graph);
        }
    }

    @Override
    public String getDescription() {
        return "Change Pool View Mode to " + newMode;
    }
}
