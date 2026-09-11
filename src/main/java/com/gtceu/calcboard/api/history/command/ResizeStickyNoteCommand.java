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
 * Resizing of a sticky note.
 */
public class ResizeStickyNoteCommand implements BoardCommand {
    private final String noteId;
    private final double oldW;
    private final double oldH;
    private final double newW;
    private final double newH;
    private final String description;

    public ResizeStickyNoteCommand(String noteId, double oldW, double oldH, double newW, double newH, String description) {
        this.noteId = noteId;
        this.oldW = oldW;
        this.oldH = oldH;
        this.newW = newW;
        this.newH = newH;
        this.description = description;
    }

    @Override
    public void undo(FlowGraph graph) {
        CanvasStickyNote note = graph.findStickyNoteById(noteId);
        if (note != null) {
            note.setWidth(oldW);
            note.setHeight(oldH);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        CanvasStickyNote note = graph.findStickyNoteById(noteId);
        if (note != null) {
            note.setWidth(newW);
            note.setHeight(newH);
        }
    }

    @Override
    public String getDescription() {
        return description != null ? description : "Resize Sticky Note";
    }
}
