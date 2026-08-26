package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.CanvasGroupFrame;
import com.gtceu.calcboard.api.CanvasStickyNote;
import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.NodeClipboard;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Manages component selection state, bounding box marquee selection,
 * and bulk operations (delete, copy, cut, paste, duplicate) for BoardScreen.
 */
public class BoardSelectionModel {
    private final Set<String> selectedNodeIds = new HashSet<>();
    private final Set<String> selectedNoteIds = new HashSet<>();
    private final Set<String> selectedFrameIds = new HashSet<>();

    public Set<String> getSelectedNodeIds() {
        return selectedNodeIds;
    }

    public Set<String> getSelectedNoteIds() {
        return selectedNoteIds;
    }

    public Set<String> getSelectedFrameIds() {
        return selectedFrameIds;
    }

    public boolean isSelected(String id) {
        return id != null && (selectedNodeIds.contains(id) || selectedNoteIds.contains(id) || selectedFrameIds.contains(id));
    }

    public boolean isNodeSelected(String id) {
        return id != null && selectedNodeIds.contains(id);
    }

    public boolean isNoteSelected(String id) {
        return id != null && selectedNoteIds.contains(id);
    }

    public boolean isFrameSelected(String id) {
        return id != null && selectedFrameIds.contains(id);
    }

    public boolean isEmpty() {
        return selectedNodeIds.isEmpty() && selectedNoteIds.isEmpty() && selectedFrameIds.isEmpty();
    }

    public int size() {
        return selectedNodeIds.size() + selectedNoteIds.size() + selectedFrameIds.size();
    }

    public void select(String id, boolean multi) {
        if (!multi) {
            clear();
        }
        if (id != null) {
            selectedNodeIds.add(id);
        }
    }

    public void selectNote(String id, boolean multi) {
        if (!multi) {
            clear();
        }
        if (id != null) {
            selectedNoteIds.add(id);
        }
    }

    public void selectFrame(String id, boolean multi) {
        if (!multi) {
            clear();
        }
        if (id != null) {
            selectedFrameIds.add(id);
        }
    }

    public void toggle(String id) {
        if (id == null) return;
        if (selectedNodeIds.contains(id)) {
            selectedNodeIds.remove(id);
        } else {
            selectedNodeIds.add(id);
        }
    }

    public void toggleNote(String id) {
        if (id == null) return;
        if (selectedNoteIds.contains(id)) {
            selectedNoteIds.remove(id);
        } else {
            selectedNoteIds.add(id);
        }
    }

    public void toggleFrame(String id) {
        if (id == null) return;
        if (selectedFrameIds.contains(id)) {
            selectedFrameIds.remove(id);
        } else {
            selectedFrameIds.add(id);
        }
    }

    public void clear() {
        selectedNodeIds.clear();
        selectedNoteIds.clear();
        selectedFrameIds.clear();
    }

    public void selectAll(BoardScreen screen) {
        clear();
        FlowGraph graph = screen.getGraph();
        if (graph != null) {
            for (RecipeNode n : graph.getNodes()) {
                selectedNodeIds.add(n.getId());
            }
            for (CanvasStickyNote note : graph.getStickyNotes()) {
                selectedNoteIds.add(note.getId());
            }
            for (CanvasGroupFrame frame : graph.getFrames()) {
                selectedFrameIds.add(frame.getId());
            }
        }
        TutorialManager.getInstance().onSelectAll();
    }

    public void deleteSelection(BoardScreen screen) {
        if (isEmpty()) return;
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        int count = size();
        List<RecipeNode> removedNodes = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> removedEdges = new ArrayList<>();
        List<CanvasStickyNote> removedNotes = new ArrayList<>();
        List<CanvasGroupFrame> removedFrames = new ArrayList<>();

        for (RecipeNode n : graph.getNodes()) {
            if (selectedNodeIds.contains(n.getId())) {
                removedNodes.add(n);
            }
        }
        for (FlowGraph.ConnectionEdge e : graph.getConnections()) {
            if (selectedNodeIds.contains(e.fromNodeId()) || selectedNodeIds.contains(e.toNodeId())) {
                removedEdges.add(e);
            }
        }
        for (CanvasStickyNote note : graph.getStickyNotes()) {
            if (selectedNoteIds.contains(note.getId())) {
                removedNotes.add(note);
            }
        }
        for (CanvasGroupFrame frame : graph.getFrames()) {
            if (selectedFrameIds.contains(frame.getId())) {
                removedFrames.add(frame);
            }
        }

        graph.getNodes().removeAll(removedNodes);
        graph.getConnections().removeAll(removedEdges);
        graph.getStickyNotes().removeAll(removedNotes);
        graph.getFrames().removeAll(removedFrames);

        List<BoardCommand> commands = new ArrayList<>();
        if (!removedNodes.isEmpty() || !removedEdges.isEmpty()) {
            commands.add(new BoardCommand.RemoveNodesCommand(removedNodes, removedEdges, "Delete nodes"));
        }
        if (!removedNotes.isEmpty()) {
            commands.add(new BoardCommand.RemoveStickyNotesCommand(removedNotes, "Delete notes"));
        }
        if (!removedFrames.isEmpty()) {
            commands.add(new BoardCommand.RemoveFramesCommand(removedFrames, "Delete frames"));
        }

        if (!commands.isEmpty()) {
            if (commands.size() == 1) {
                screen.recordCommand(commands.get(0));
            } else {
                screen.recordCommand(new BoardCommand.CompoundCommand(commands, "Delete " + count + " components"));
            }
        }

        clear();
        screen.rebuildWidgets();
        screen.markSummaryDirty();

        for (RecipeNode n : removedNodes) {
            TutorialManager.getInstance().onNodeRemoved(n);
        }

        BoardToast.show(Component.literal("§c🗑 ").append(Component.translatable("message.gtcalcboard.deleted_components", String.valueOf(count))));
    }

    public void copySelection(BoardScreen screen) {
        if (isEmpty()) {
            screen.getToolbarWidget().copyBlueprintToClipboard();
            return;
        }
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        int count = size();
        NodeClipboard.getInstance().copy(graph, selectedNodeIds, selectedNoteIds, selectedFrameIds);
        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.copied_components", String.valueOf(count))));
    }

    public void pasteSelection(BoardScreen screen, double canvasX, double canvasY) {
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        if (!NodeClipboard.getInstance().hasContent()) {
            screen.getToolbarWidget().importBlueprintFromClipboard();
            return;
        }

        NodeClipboard.PasteResult res = NodeClipboard.getInstance().paste(graph, canvasX, canvasY);
        if (res.isEmpty()) return;

        List<BoardCommand> commands = new ArrayList<>();
        if (!res.nodes().isEmpty() || !res.edges().isEmpty()) {
            commands.add(new BoardCommand.AddNodesCommand(res.nodes(), res.edges(), "Paste nodes"));
        }
        if (!res.notes().isEmpty()) {
            commands.add(new BoardCommand.AddStickyNotesCommand(res.notes(), "Paste sticky notes"));
        }
        if (!res.frames().isEmpty()) {
            commands.add(new BoardCommand.AddFramesCommand(res.frames(), "Paste frames"));
        }

        if (!commands.isEmpty()) {
            if (commands.size() == 1) {
                screen.recordCommand(commands.get(0));
            } else {
                screen.recordCommand(new BoardCommand.CompoundCommand(commands, "Paste " + res.size() + " components"));
            }
        }

        clear();
        for (RecipeNode n : res.nodes()) {
            selectedNodeIds.add(n.getId());
        }
        for (CanvasStickyNote note : res.notes()) {
            selectedNoteIds.add(note.getId());
        }
        for (CanvasGroupFrame frame : res.frames()) {
            selectedFrameIds.add(frame.getId());
        }

        screen.rebuildWidgets();
        screen.markSummaryDirty();
        TutorialManager.getInstance().onPasted();

        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.pasted_components", String.valueOf(res.size()))));
    }

    public void cutSelection(BoardScreen screen) {
        if (isEmpty()) return;
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        int count = size();
        NodeClipboard.getInstance().copy(graph, selectedNodeIds, selectedNoteIds, selectedFrameIds);

        List<RecipeNode> removedNodes = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> removedEdges = new ArrayList<>();
        List<CanvasStickyNote> removedNotes = new ArrayList<>();
        List<CanvasGroupFrame> removedFrames = new ArrayList<>();

        for (RecipeNode n : graph.getNodes()) {
            if (selectedNodeIds.contains(n.getId())) {
                removedNodes.add(n);
            }
        }
        for (FlowGraph.ConnectionEdge e : graph.getConnections()) {
            if (selectedNodeIds.contains(e.fromNodeId()) || selectedNodeIds.contains(e.toNodeId())) {
                removedEdges.add(e);
            }
        }
        for (CanvasStickyNote note : graph.getStickyNotes()) {
            if (selectedNoteIds.contains(note.getId())) {
                removedNotes.add(note);
            }
        }
        for (CanvasGroupFrame frame : graph.getFrames()) {
            if (selectedFrameIds.contains(frame.getId())) {
                removedFrames.add(frame);
            }
        }

        graph.getNodes().removeAll(removedNodes);
        graph.getConnections().removeAll(removedEdges);
        graph.getStickyNotes().removeAll(removedNotes);
        graph.getFrames().removeAll(removedFrames);

        List<BoardCommand> commands = new ArrayList<>();
        if (!removedNodes.isEmpty() || !removedEdges.isEmpty()) {
            commands.add(new BoardCommand.RemoveNodesCommand(removedNodes, removedEdges, "Cut nodes"));
        }
        if (!removedNotes.isEmpty()) {
            commands.add(new BoardCommand.RemoveStickyNotesCommand(removedNotes, "Cut sticky notes"));
        }
        if (!removedFrames.isEmpty()) {
            commands.add(new BoardCommand.RemoveFramesCommand(removedFrames, "Cut frames"));
        }

        if (!commands.isEmpty()) {
            if (commands.size() == 1) {
                screen.recordCommand(commands.get(0));
            } else {
                screen.recordCommand(new BoardCommand.CompoundCommand(commands, "Cut " + count + " components"));
            }
        }

        clear();
        screen.rebuildWidgets();
        screen.markSummaryDirty();
        TutorialManager.getInstance().onCut();

        BoardToast.show(Component.literal("§6✂ ").append(Component.translatable("message.gtcalcboard.cut_components", String.valueOf(count))));
    }

    public void duplicateSelection(BoardScreen screen, double mouseX, double mouseY) {
        if (isEmpty()) return;
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        NodeClipboard.getInstance().copy(graph, selectedNodeIds, selectedNoteIds, selectedFrameIds);
        double canvasX = screen.toCanvasX(mouseX) + 30.0;
        double canvasY = screen.toCanvasY(mouseY) + 30.0;
        pasteSelection(screen, canvasX, canvasY);
    }
}

