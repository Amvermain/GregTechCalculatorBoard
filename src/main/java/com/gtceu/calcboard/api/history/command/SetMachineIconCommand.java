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
 * Reversible command for switching a RecipeNode's machine workstation/controller icon and its associated traits.
 */
public class SetMachineIconCommand implements BoardCommand {
    private final String nodeId;
    private final ResourceLocation oldIcon;
    private final ResourceLocation newIcon;
    private final boolean oldMultiblock;
    private final boolean newMultiblock;
    private final int oldParallel;
    private final int newParallel;
    private final com.gtceu.calcboard.api.type.SteamMode oldSteamMode;
    private final com.gtceu.calcboard.api.type.SteamMode newSteamMode;
    private final com.gtceu.calcboard.api.type.GTVoltageTier oldTier;
    private final com.gtceu.calcboard.api.type.GTVoltageTier newTier;
    private final String oldName;
    private final String newName;

    public SetMachineIconCommand(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon,
                                 boolean oldMultiblock, int oldParallel, com.gtceu.calcboard.api.type.SteamMode oldSteamMode, com.gtceu.calcboard.api.type.GTVoltageTier oldTier) {
        this(node, oldIcon, newIcon, oldMultiblock, oldParallel, oldSteamMode, oldTier, null, null);
    }

    public SetMachineIconCommand(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon,
                                 boolean oldMultiblock, int oldParallel, com.gtceu.calcboard.api.type.SteamMode oldSteamMode, com.gtceu.calcboard.api.type.GTVoltageTier oldTier,
                                 String oldName, String newName) {
        this.nodeId = node.getId();
        this.oldIcon = oldIcon;
        this.newIcon = newIcon;
        this.oldMultiblock = oldMultiblock;
        this.newMultiblock = node.isMultiblock();
        this.oldParallel = oldParallel;
        this.newParallel = node.getParallel();
        this.oldSteamMode = oldSteamMode;
        this.newSteamMode = node.getSteamMode();
        this.oldTier = oldTier;
        this.newTier = node.getTargetTier();
        this.oldName = oldName;
        this.newName = newName;
    }

    @Override
    public void undo(FlowGraph graph) {
        RecipeNode node = graph.findNodeById(nodeId);
        if (node != null) {
            node.setMachineIcon(oldIcon);
            node.setMultiblock(oldMultiblock);
            node.setParallel(oldParallel);
            node.setSteamMode(oldSteamMode);
            node.setTargetTier(oldTier);
            if (oldName != null) {
                node.setName(oldName);
            }
        }
    }

    @Override
    public void redo(FlowGraph graph) {
        RecipeNode node = graph.findNodeById(nodeId);
        if (node != null) {
            node.setMachineIcon(newIcon);
            node.setMultiblock(newMultiblock);
            node.setParallel(newParallel);
            node.setSteamMode(newSteamMode);
            node.setTargetTier(newTier);
            if (newName != null) {
                node.setName(newName);
            }
        }
    }

    @Override
    public String getDescription() {
        return "Switch machine icon to " + (newIcon != null ? newIcon.getPath() : "none");
    }
}
