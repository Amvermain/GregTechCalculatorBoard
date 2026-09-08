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
 * Addition of one or more group frames.
 */
public class AddFramesCommand implements BoardCommand {
    private final List<CanvasGroupFrame> frames;
    private final String description;

    public AddFramesCommand(List<CanvasGroupFrame> frames, String description) {
        this.frames = new ArrayList<>(frames);
        this.description = description;
    }

    public AddFramesCommand(CanvasGroupFrame frame, String description) {
        this(List.of(frame), description);
    }

    @Override
    public void undo(FlowGraph graph) {
        for (CanvasGroupFrame frame : frames) {
            graph.removeFrame(frame);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        for (CanvasGroupFrame frame : frames) {
            if (!graph.getFrames().contains(frame)) {
                graph.addFrame(frame);
            }
        }
    }

    @Override
    public String getDescription() {
        return description != null ? description : "Add " + frames.size() + " frames";
    }
}
