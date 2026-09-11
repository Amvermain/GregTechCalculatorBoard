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
 * Addition of one or more sticky notes.
 */
public class AddStickyNotesCommand implements BoardCommand {
    private final List<CanvasStickyNote> notes;
    private final String description;

    public AddStickyNotesCommand(List<CanvasStickyNote> notes, String description) {
        this.notes = new ArrayList<>(notes);
        this.description = description;
    }

    public AddStickyNotesCommand(CanvasStickyNote note, String description) {
        this(List.of(note), description);
    }

    @Override
    public void undo(FlowGraph graph) {
        for (CanvasStickyNote note : notes) {
            graph.removeStickyNote(note);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        for (CanvasStickyNote note : notes) {
            if (!graph.getStickyNotes().contains(note)) {
                graph.addStickyNote(note);
            }
        }
    }

    @Override
    public String getDescription() {
        return description != null ? description : "Add " + notes.size() + " sticky notes";
    }
}
