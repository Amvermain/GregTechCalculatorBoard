package com.gtceu.calcboard.api.history;

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
 * Represents a lightweight, vector/delta-based reversible action on a Calculator Board FlowGraph.
 * Concrete command implementations are located in {@link com.gtceu.calcboard.api.history.command}.
 * The nested subclasses below are preserved for 100% backward compatibility with existing callers.
 */
public interface BoardCommand {

    void undo(FlowGraph graph);

    void redo(FlowGraph graph);

    String getDescription();

    class AddFramesCommand extends com.gtceu.calcboard.api.history.command.AddFramesCommand {
        public AddFramesCommand(List<CanvasGroupFrame> frames, String description) {
            super(frames, description);
        }
        public AddFramesCommand(CanvasGroupFrame frame, String description) {
            super(frame, description);
        }
    }

    class AddNodesCommand extends com.gtceu.calcboard.api.history.command.AddNodesCommand {
        public AddNodesCommand(List<RecipeNode> nodes, List<FlowGraph.ConnectionEdge> edges, String description) {
            super(nodes, edges, description);
        }
        public AddNodesCommand(RecipeNode node, String description) {
            super(node, description);
        }
    }

    class AddStickyNotesCommand extends com.gtceu.calcboard.api.history.command.AddStickyNotesCommand {
        public AddStickyNotesCommand(List<CanvasStickyNote> notes, String description) {
            super(notes, description);
        }
        public AddStickyNotesCommand(CanvasStickyNote note, String description) {
            super(note, description);
        }
    }

    class CompoundCommand extends com.gtceu.calcboard.api.history.command.CompoundCommand {
        public CompoundCommand(List<BoardCommand> commands, String description) {
            super(commands, description);
        }
    }

    class ConnectWireCommand extends com.gtceu.calcboard.api.history.command.ConnectWireCommand {
        public ConnectWireCommand(FlowGraph.ConnectionEdge edge, String scaledNodeId, Double oldMachineCount, Double newMachineCount) {
            super(edge, scaledNodeId, oldMachineCount, newMachineCount);
        }
        public ConnectWireCommand(FlowGraph.ConnectionEdge edge) {
            super(edge);
        }
    }

    class DisconnectWireCommand extends com.gtceu.calcboard.api.history.command.DisconnectWireCommand {
        public DisconnectWireCommand(FlowGraph.ConnectionEdge edge) {
            super(edge);
        }
    }

    class ExpandModuleCommand extends com.gtceu.calcboard.api.history.command.ExpandModuleCommand {
        public ExpandModuleCommand(RecipeNode moduleNode, List<RecipeNode> expandedNodes, List<FlowGraph.ConnectionEdge> restoredEdges, List<FlowGraph.ConnectionEdge> moduleEdges, List<CanvasGroupFrame> expandedFrames, List<CanvasStickyNote> expandedNotes, com.gtceu.calcboard.api.storage.BoardPage capturedSubPage) {
            super(moduleNode, expandedNodes, restoredEdges, moduleEdges, expandedFrames, expandedNotes, capturedSubPage);
        }
        public ExpandModuleCommand(RecipeNode moduleNode, List<RecipeNode> expandedNodes, List<FlowGraph.ConnectionEdge> restoredEdges, List<FlowGraph.ConnectionEdge> moduleEdges, List<CanvasGroupFrame> expandedFrames, List<CanvasStickyNote> expandedNotes) {
            super(moduleNode, expandedNodes, restoredEdges, moduleEdges, expandedFrames, expandedNotes);
        }
        public ExpandModuleCommand(RecipeNode moduleNode, List<RecipeNode> expandedNodes, List<FlowGraph.ConnectionEdge> restoredEdges, List<FlowGraph.ConnectionEdge> moduleEdges) {
            super(moduleNode, expandedNodes, restoredEdges, moduleEdges);
        }
    }

    class FlipNodesCommand extends com.gtceu.calcboard.api.history.command.FlipNodesCommand {
        public FlipNodesCommand(Map<String, Boolean> previousStates, Map<String, Boolean> newStates) {
            super(previousStates, newStates);
        }
        public FlipNodesCommand(RecipeNode node, boolean previousState, boolean newState) {
            super(node, previousState, newState);
        }
    }

    class GroupModuleCommand extends com.gtceu.calcboard.api.history.command.GroupModuleCommand {
        public GroupModuleCommand(List<RecipeNode> groupedNodes, RecipeNode moduleNode, List<FlowGraph.ConnectionEdge> originalEdges, List<FlowGraph.ConnectionEdge> rewires, List<CanvasGroupFrame> capturedFrames, List<CanvasStickyNote> capturedNotes, com.gtceu.calcboard.api.storage.BoardPage capturedSubPage) {
            super(groupedNodes, moduleNode, originalEdges, rewires, capturedFrames, capturedNotes, capturedSubPage);
        }
        public GroupModuleCommand(List<RecipeNode> groupedNodes, RecipeNode moduleNode, List<FlowGraph.ConnectionEdge> originalEdges, List<FlowGraph.ConnectionEdge> rewires, List<CanvasGroupFrame> capturedFrames, List<CanvasStickyNote> capturedNotes) {
            super(groupedNodes, moduleNode, originalEdges, rewires, capturedFrames, capturedNotes);
        }
        public GroupModuleCommand(List<RecipeNode> groupedNodes, RecipeNode moduleNode, List<FlowGraph.ConnectionEdge> originalEdges, List<FlowGraph.ConnectionEdge> rewires) {
            super(groupedNodes, moduleNode, originalEdges, rewires);
        }
    }

    class ModifyFramePropertiesCommand extends com.gtceu.calcboard.api.history.command.ModifyFramePropertiesCommand {
        public ModifyFramePropertiesCommand(String frameId, String oldTitle, String newTitle, int oldColor, int newColor, boolean oldShared, boolean newShared) {
            super(frameId, oldTitle, newTitle, oldColor, newColor, oldShared, newShared);
        }
        public ModifyFramePropertiesCommand(String frameId, String oldTitle, String newTitle, int oldColor, int newColor, boolean oldShared, boolean newShared, double oldTargetCapacity, double newTargetCapacity) {
            super(frameId, oldTitle, newTitle, oldColor, newColor, oldShared, newShared, oldTargetCapacity, newTargetCapacity);
        }
    }

    class ModifyNotePropertiesCommand extends com.gtceu.calcboard.api.history.command.ModifyNotePropertiesCommand {
        public ModifyNotePropertiesCommand(String noteId, String oldTitle, String newTitle, String oldContent, String newContent, int oldColor, int newColor) {
            super(noteId, oldTitle, newTitle, oldContent, newContent, oldColor, newColor);
        }
    }

    class ModifyPropertyCommand<T> extends com.gtceu.calcboard.api.history.command.ModifyPropertyCommand<T> {
        public ModifyPropertyCommand(String nodeId, Property property, T oldValue, T newValue) {
            super(nodeId, property, oldValue, newValue);
        }

        public static ModifyPropertyCommand<Double> machineCount(String nodeId, double oldVal, double newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.MACHINE_COUNT, oldVal, newVal);
        }

        public static ModifyPropertyCommand<GTVoltageTier> targetTier(String nodeId, GTVoltageTier oldVal, GTVoltageTier newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.TARGET_TIER, oldVal, newVal);
        }

        public static ModifyPropertyCommand<OverclockMode> overclockMode(String nodeId, OverclockMode oldVal, OverclockMode newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.OVERCLOCK_MODE, oldVal, newVal);
        }

        public static ModifyPropertyCommand<Integer> parallel(String nodeId, int oldVal, int newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.PARALLEL, oldVal, newVal);
        }

        public static ModifyPropertyCommand<String> customName(String nodeId, String oldVal, String newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.CUSTOM_NAME, oldVal, newVal);
        }

        public static ModifyPropertyCommand<Boolean> baseAnchor(String nodeId, boolean oldVal, boolean newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.BASE_ANCHOR, oldVal, newVal);
        }

        public static ModifyPropertyCommand<Integer> rotorEfficiency(String nodeId, int oldVal, int newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.ROTOR_EFFICIENCY, oldVal, newVal);
        }

        public static ModifyPropertyCommand<Integer> rotorPower(String nodeId, int oldVal, int newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.ROTOR_POWER, oldVal, newVal);
        }

        public static ModifyPropertyCommand<String> rotorName(String nodeId, String oldVal, String newVal) {
            return new ModifyPropertyCommand<>(nodeId, Property.ROTOR_NAME, oldVal, newVal);
        }
    }

    class MoveComponentsCommand extends com.gtceu.calcboard.api.history.command.MoveComponentsCommand {
        public MoveComponentsCommand(Map<String, double[]> nodeDeltas, Map<String, double[]> noteDeltas, Map<String, double[]> frameDeltas) {
            super(nodeDeltas, noteDeltas, frameDeltas);
        }
        public MoveComponentsCommand(Map<String, double[]> nodeDeltas) {
            super(nodeDeltas);
        }
    }

    class MoveNodesCommand extends com.gtceu.calcboard.api.history.command.MoveNodesCommand {
        public MoveNodesCommand(Map<String, double[]> deltas) {
            super(deltas);
        }
    }

    class RemoveFramesCommand extends com.gtceu.calcboard.api.history.command.RemoveFramesCommand {
        public RemoveFramesCommand(List<CanvasGroupFrame> frames, String description) {
            super(frames, description);
        }
        public RemoveFramesCommand(CanvasGroupFrame frame, String description) {
            super(frame, description);
        }
    }

    class RemoveNodesCommand extends com.gtceu.calcboard.api.history.command.RemoveNodesCommand {
        public RemoveNodesCommand(List<RecipeNode> nodes, List<FlowGraph.ConnectionEdge> edges, String description, List<com.gtceu.calcboard.api.storage.BoardPage> capturedSubPages) {
            super(nodes, edges, description, capturedSubPages);
        }
        public RemoveNodesCommand(List<RecipeNode> nodes, List<FlowGraph.ConnectionEdge> edges, String description) {
            super(nodes, edges, description);
        }
    }

    class RemoveStickyNotesCommand extends com.gtceu.calcboard.api.history.command.RemoveStickyNotesCommand {
        public RemoveStickyNotesCommand(List<CanvasStickyNote> notes, String description) {
            super(notes, description);
        }
        public RemoveStickyNotesCommand(CanvasStickyNote note, String description) {
            super(note, description);
        }
    }

    class ResizeFrameCommand extends com.gtceu.calcboard.api.history.command.ResizeFrameCommand {
        public ResizeFrameCommand(String frameId, double oldX, double oldY, double oldW, double oldH, double newX, double newY, double newW, double newH, String description) {
            super(frameId, oldX, oldY, oldW, oldH, newX, newY, newW, newH, description);
        }
    }

    class ResizeStickyNoteCommand extends com.gtceu.calcboard.api.history.command.ResizeStickyNoteCommand {
        public ResizeStickyNoteCommand(String noteId, double oldW, double oldH, double newW, double newH, String description) {
            super(noteId, oldW, oldH, newW, newH, description);
        }
    }

    class SelectAlternativeCommand extends com.gtceu.calcboard.api.history.command.SelectAlternativeCommand {
        public SelectAlternativeCommand(String nodeId, int slotIndex, boolean isInput, ResourceLocation oldAlternativeId, ResourceLocation newAlternativeId) {
            super(nodeId, slotIndex, isInput, oldAlternativeId, newAlternativeId);
        }
    }

    class SetMachineIconCommand extends com.gtceu.calcboard.api.history.command.SetMachineIconCommand {
        public SetMachineIconCommand(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon, boolean oldMultiblock, int oldParallel, com.gtceu.calcboard.api.type.SteamMode oldSteamMode, com.gtceu.calcboard.api.type.GTVoltageTier oldTier) {
            super(node, oldIcon, newIcon, oldMultiblock, oldParallel, oldSteamMode, oldTier);
        }
        public SetMachineIconCommand(RecipeNode node, ResourceLocation oldIcon, ResourceLocation newIcon, boolean oldMultiblock, int oldParallel, com.gtceu.calcboard.api.type.SteamMode oldSteamMode, com.gtceu.calcboard.api.type.GTVoltageTier oldTier, String oldName, String newName) {
            super(node, oldIcon, newIcon, oldMultiblock, oldParallel, oldSteamMode, oldTier, oldName, newName);
        }
    }

    class SwitchRecipeCommand extends com.gtceu.calcboard.api.history.command.SwitchRecipeCommand {
        public SwitchRecipeCommand(String nodeId, RecipeSnapshot oldRecipe, RecipeSnapshot newRecipe, List<FlowGraph.ConnectionEdge> oldEdges, List<FlowGraph.ConnectionEdge> newEdges) {
            super(nodeId, oldRecipe, newRecipe, oldEdges, newEdges);
        }
    }

    class ToggleFrameFoldCommand extends com.gtceu.calcboard.api.history.command.ToggleFrameFoldCommand {
        public ToggleFrameFoldCommand(String frameId, boolean previousFolded, boolean newFolded) {
            super(frameId, previousFolded, newFolded);
        }
    }
}
