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
 * Modification of sticky note properties (title, content, color).
 */
public class ModifyNotePropertiesCommand implements BoardCommand {
    private final String noteId;
    private final String oldTitle;
    private final String newTitle;
    private final String oldContent;
    private final String newContent;
    private final int oldColor;
    private final int newColor;

    public ModifyNotePropertiesCommand(
            String noteId,
            String oldTitle, String newTitle,
            String oldContent, String newContent,
            int oldColor, int newColor
    ) {
        this.noteId = noteId;
        this.oldTitle = oldTitle;
        this.newTitle = newTitle;
        this.oldContent = oldContent;
        this.newContent = newContent;
        this.oldColor = oldColor;
        this.newColor = newColor;
    }

    @Override
    public void undo(FlowGraph graph) {
        CanvasStickyNote note = graph.findStickyNoteById(noteId);
        if (note != null) {
            note.setTitle(oldTitle);
            note.setContent(oldContent);
            note.setColor(oldColor);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        CanvasStickyNote note = graph.findStickyNoteById(noteId);
        if (note != null) {
            note.setTitle(newTitle);
            note.setContent(newContent);
            note.setColor(newColor);
        }
    }

    @Override
    public String getDescription() {
        return "Modify Note Properties";
    }
}
