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
 * Batch command aggregating multiple individual commands (e.g. Auto Ratio, Auto Connect, Max Flow).
 */
public class CompoundCommand implements BoardCommand {
    private final List<BoardCommand> commands;
    private final String description;

    public CompoundCommand(List<BoardCommand> commands, String description) {
        this.commands = new ArrayList<>(commands);
        this.description = description;
    }

    @Override
    public void undo(FlowGraph graph) {
        for (int i = commands.size() - 1; i >= 0; i--) {
            commands.get(i).undo(graph);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        for (BoardCommand cmd : commands) {
            cmd.redo(graph);
        }
    }

    @Override
    public String getDescription() {
        return description != null ? description : "Batch operation (" + commands.size() + " actions)";
    }
}
