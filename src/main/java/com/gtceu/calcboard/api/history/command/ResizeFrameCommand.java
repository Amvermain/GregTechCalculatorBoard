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
 * Resizing and repositioning of a group frame.
 */
public class ResizeFrameCommand implements BoardCommand {
    private final String frameId;
    private final double oldX, oldY, oldW, oldH;
    private final double newX, newY, newW, newH;
    private final String description;

    public ResizeFrameCommand(String frameId, double oldX, double oldY, double oldW, double oldH, double newX, double newY, double newW, double newH, String description) {
        this.frameId = frameId;
        this.oldX = oldX;
        this.oldY = oldY;
        this.oldW = oldW;
        this.oldH = oldH;
        this.newX = newX;
        this.newY = newY;
        this.newW = newW;
        this.newH = newH;
        this.description = description;
    }

    @Override
    public void undo(FlowGraph graph) {
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.setPosX(oldX);
            frame.setPosY(oldY);
            frame.setWidth(oldW);
            frame.setHeight(oldH);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.setPosX(newX);
            frame.setPosY(newY);
            frame.setWidth(newW);
            frame.setHeight(newH);
        }
    }

    @Override
    public String getDescription() {
        return description != null ? description : "Resize Frame";
    }
}
