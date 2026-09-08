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
 * Modification of frame properties (title, color, shared machine mode).
 */
public class ModifyFramePropertiesCommand implements BoardCommand {
    private final String frameId;
    private final String oldTitle;
    private final String newTitle;
    private final int oldColor;
    private final int newColor;
    private final boolean oldShared;
    private final boolean newShared;
    private final double oldTargetCapacity;
    private final double newTargetCapacity;

    public ModifyFramePropertiesCommand(
            String frameId,
            String oldTitle, String newTitle,
            int oldColor, int newColor,
            boolean oldShared, boolean newShared
    ) {
        this(frameId, oldTitle, newTitle, oldColor, newColor, oldShared, newShared, 1.0, 1.0);
    }

    public ModifyFramePropertiesCommand(
            String frameId,
            String oldTitle, String newTitle,
            int oldColor, int newColor,
            boolean oldShared, boolean newShared,
            double oldTargetCapacity, double newTargetCapacity
    ) {
        this.frameId = frameId;
        this.oldTitle = oldTitle;
        this.newTitle = newTitle;
        this.oldColor = oldColor;
        this.newColor = newColor;
        this.oldShared = oldShared;
        this.newShared = newShared;
        this.oldTargetCapacity = oldTargetCapacity;
        this.newTargetCapacity = newTargetCapacity;
    }

    @Override
    public void undo(FlowGraph graph) {
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.setTitle(oldTitle);
            frame.setColor(oldColor);
            frame.setSharedMachineFrame(oldShared);
            frame.setTargetPoolCapacity(oldTargetCapacity);
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        CanvasGroupFrame frame = graph.findFrameById(frameId);
        if (frame != null) {
            frame.setTitle(newTitle);
            frame.setColor(newColor);
            frame.setSharedMachineFrame(newShared);
            frame.setTargetPoolCapacity(newTargetCapacity);
        }
    }

    @Override
    public String getDescription() {
        return "Modify Frame Properties";
    }
}
