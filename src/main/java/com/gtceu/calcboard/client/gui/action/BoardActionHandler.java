package com.gtceu.calcboard.client.gui.action;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.CanvasStickyNote;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.SteamMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

/**
 * Handles board graph mutations, user editing commands, and element creation workflows.
 */
public class BoardActionHandler {
    private final BoardScreen screen;

    public BoardActionHandler(BoardScreen screen) {
        this.screen = screen;
    }

    public void addNode(RecipeNode node) {
        if (!screen.ensureEditPermission()) return;
        screen.getGraph().addNode(node);
        screen.rebuildWidgets();
        TutorialManager.getInstance().onNodeAdded(node);
    }

    public void removeNode(NodeWidget widget) {
        if (widget == null || widget.getNode() == null) return;
        RecipeNode targetNode = widget.getNode();
        List<RecipeNode> removedNodes = new ArrayList<>();
        List<CanvasGroupFrame> removedFrames = new ArrayList<>();
        Set<String> targetNodeIds = new HashSet<>();

        collectRemovalTargets(targetNode, removedNodes, removedFrames, targetNodeIds);
        List<FlowGraph.ConnectionEdge> removedEdges = collectRemovedEdges(targetNodeIds);

        screen.getGraph().removeNode(targetNode);
        recordRemovalCommands(targetNode, removedNodes, removedFrames, removedEdges);

        screen.getSelectedNodeIds().removeAll(targetNodeIds);
        screen.rebuildWidgets();
        screen.markSummaryDirty();

        for (RecipeNode removed : removedNodes) {
            TutorialManager.getInstance().onNodeRemoved(removed);
        }
    }

    private void collectRemovalTargets(
            RecipeNode target,
            List<RecipeNode> removedNodes,
            List<CanvasGroupFrame> removedFrames,
            Set<String> targetNodeIds
    ) {
        if (target.isCompoundNode()) {
            List<RecipeNode> siblings = screen.getGraph().findCompoundSiblingNodes(target.getCompoundGroupId());
            removedNodes.addAll(siblings);
            for (RecipeNode sibling : siblings) {
                targetNodeIds.add(sibling.getId());
            }
            CanvasGroupFrame cFrame = screen.getGraph().findCompoundFrame(target.getCompoundGroupId());
            if (cFrame != null) {
                removedFrames.add(cFrame);
            }
            return;
        }

        removedNodes.add(target);
        targetNodeIds.add(target.getId());
    }

    private List<FlowGraph.ConnectionEdge> collectRemovedEdges(Set<String> targetNodeIds) {
        List<FlowGraph.ConnectionEdge> removedEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : screen.getGraph().getConnections()) {
            if (targetNodeIds.contains(edge.fromNodeId()) || targetNodeIds.contains(edge.toNodeId())) {
                removedEdges.add(edge);
            }
        }
        return removedEdges;
    }

    private void recordRemovalCommands(
            RecipeNode target,
            List<RecipeNode> removedNodes,
            List<CanvasGroupFrame> removedFrames,
            List<FlowGraph.ConnectionEdge> removedEdges
    ) {
        List<BoardCommand> commands = new ArrayList<>();
        commands.add(new BoardCommand.RemoveNodesCommand(removedNodes, removedEdges, "Delete " + target.getName()));
        if (!removedFrames.isEmpty()) {
            commands.add(new BoardCommand.RemoveFramesCommand(removedFrames, "Delete compound frame"));
        }

        if (commands.size() == 1) {
            screen.recordCommand(commands.get(0));
        } else {
            screen.recordCommand(new BoardCommand.CompoundCommand(commands, "Delete compound " + target.getName()));
        }
    }

    public void flipSelectedNodes(double lastMouseX, double lastMouseY) {
        if (!screen.ensureEditPermission()) return;
        List<String> nodeIds = new ArrayList<>(screen.getSelectedNodeIds());
        if (nodeIds.isEmpty()) {
            nodeIds = findHoveredNodeIdList(lastMouseX, lastMouseY);
        }
        if (nodeIds.isEmpty()) return;

        Map<String, Boolean> prevStates = new HashMap<>();
        Map<String, Boolean> newStates = new HashMap<>();
        for (String id : nodeIds) {
            RecipeNode node = screen.getGraph().findNodeById(id);
            if (node != null) {
                prevStates.put(id, node.isFlipped());
                newStates.put(id, !node.isFlipped());
                node.setFlipped(!node.isFlipped());
            }
        }

        if (newStates.isEmpty()) return;

        screen.recordCommand(new BoardCommand.FlipNodesCommand(prevStates, newStates));
        screen.markSummaryDirty();
        invalidateFlippedWidgets(newStates.keySet());

        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.1F));
        BoardToast.show(Component.literal("§b⇄ ").append(Component.translatable("message.gtcalcboard.flipped_nodes", newStates.size())));
    }

    private List<String> findHoveredNodeIdList(double mouseX, double mouseY) {
        double canvasX = screen.toCanvasX(mouseX);
        double canvasY = screen.toCanvasY(mouseY);
        List<NodeWidget> widgets = screen.getNodeWidgets();
        for (int i = widgets.size() - 1; i >= 0; i--) {
            NodeWidget widget = widgets.get(i);
            if (widget.isPointInside(canvasX, canvasY)) {
                return List.of(widget.getNode().getId());
            }
        }
        return Collections.emptyList();
    }

    private void invalidateFlippedWidgets(Set<String> affectedIds) {
        for (NodeWidget widget : screen.getNodeWidgets()) {
            if (affectedIds.contains(widget.getNode().getId())) {
                widget.invalidateCache();
            }
        }
    }

    public void switchMachineWorkstation(RecipeNode node, ResourceLocation newWs) {
        if (!screen.ensureEditPermission() || node == null || newWs == null) return;
        if (Objects.equals(node.getMachineIcon(), newWs)) return;

        ResourceLocation oldIcon = node.getMachineIcon();
        boolean oldMb = node.isMultiblock();
        int oldPar = node.getParallel();
        SteamMode oldSteam = node.getSteamMode();
        GTVoltageTier oldTier = node.getTargetTier();

        node.setMachineIcon(newWs);
        TutorialManager.getInstance().onMachineSwitched(node, newWs);

        screen.recordCommand(new BoardCommand.SetMachineIconCommand(node, oldIcon, newWs, oldMb, oldPar, oldSteam, oldTier));
        screen.markSummaryDirty();
        screen.rebuildWidgets();

        String machineName = node.getMachineDisplayName();
        BoardToast.show(Component.literal("§b▦ ").append(Component.translatable("message.gtcalcboard.machine_switched", machineName)));
    }

    public void switchNodeRecipe(RecipeNode targetNode, RecipeNode newRecipeTemplate) {
        if (!screen.ensureEditPermission() || targetNode == null || newRecipeTemplate == null) return;

        BoardCommand cmd = screen.getGraph().switchNodeRecipe(targetNode, newRecipeTemplate);
        if (cmd == null) return;

        screen.recordCommand(cmd);
        screen.markSummaryDirty();
        for (NodeWidget widget : screen.getNodeWidgets()) {
            if (widget.getNode().getId().equals(targetNode.getId())) {
                widget.invalidateCache();
            }
        }
        BoardToast.show(Component.literal("§e⟲ ").append(Component.translatable("message.gtcalcboard.recipe_switched", targetNode.getName())));
    }

    public void createFrameFromSelection() {
        if (!screen.ensureEditPermission()) return;
        FlowGraph graph = screen.getGraph();
        List<RecipeNode> selectedNodes = getSelectedNodesList();
        List<CanvasStickyNote> selectedNotes = getSelectedNotesList();

        String defaultTitle = Component.translatable("gui.gtcalcboard.default_frame_name").getString();
        CanvasGroupFrame frame;
        if (!selectedNodes.isEmpty() || !selectedNotes.isEmpty()) {
            frame = CanvasGroupFrame.createFromElements(defaultTitle, selectedNodes, selectedNotes, CanvasGroupFrame.COLOR_BLUE);
        } else {
            double cx = screen.toCanvasX(screen.width / 2.0) - 100;
            double cy = screen.toCanvasY(screen.height / 2.0) - 60;
            frame = new CanvasGroupFrame(UUID.randomUUID().toString(), defaultTitle, CanvasGroupFrame.COLOR_BLUE, cx, cy, 200, 120);
        }

        graph.addFrame(frame);
        screen.recordCommand(new BoardCommand.AddFramesCommand(frame, "Create Group Frame"));
        screen.clearSelection();
        screen.rebuildWidgets();
        screen.markSummaryDirty();
        screen.openFrameEditDialog(frame);
        TutorialManager.getInstance().onGroupFramed();
        BoardToast.show(Component.literal("§b").append(Component.translatable("message.gtcalcboard.frame_created")));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.1F));
    }

    public void createSharedMachineFrameFromSelection() {
        if (!screen.ensureEditPermission()) return;
        FlowGraph graph = screen.getGraph();
        List<RecipeNode> selectedNodes = getSelectedNodesList();

        String defaultTitle = Component.translatable("gui.gtcalcboard.default_shared_frame_name").getString();
        CanvasGroupFrame frame;
        if (!selectedNodes.isEmpty()) {
            frame = CanvasGroupFrame.createFromNodes(defaultTitle, selectedNodes, CanvasGroupFrame.COLOR_EMERALD);
        } else {
            double cx = screen.toCanvasX(screen.width / 2.0) - 100;
            double cy = screen.toCanvasY(screen.height / 2.0) - 60;
            frame = new CanvasGroupFrame(UUID.randomUUID().toString(), defaultTitle, CanvasGroupFrame.COLOR_EMERALD, cx, cy, 200, 120);
        }
        frame.setSharedMachineFrame(true);
        graph.addFrame(frame);
        screen.recordCommand(new BoardCommand.AddFramesCommand(frame, "Create Shared Machine Frame"));
        screen.clearSelection();
        screen.rebuildWidgets();
        screen.markSummaryDirty();
        TutorialManager.getInstance().onSharedMachineFramed();
        BoardToast.show(Component.literal("§a↔ ").append(Component.translatable("message.gtcalcboard.shared_frame_created")));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F));
    }

    public void createFrameAt(double canvasX, double canvasY) {
        if (!screen.ensureEditPermission()) return;
        if (!screen.getSelectedNodeIds().isEmpty() || !screen.getSelectedNoteIds().isEmpty()) {
            createFrameFromSelection();
            return;
        }

        String defaultTitle = Component.translatable("gui.gtcalcboard.default_frame_name").getString();
        CanvasGroupFrame frame = new CanvasGroupFrame(UUID.randomUUID().toString(), defaultTitle, CanvasGroupFrame.COLOR_BLUE, canvasX - 100, canvasY - 60, 200, 120);
        screen.getGraph().addFrame(frame);
        screen.recordCommand(new BoardCommand.AddFramesCommand(frame, "Create Group Frame"));
        screen.clearSelection();
        screen.rebuildWidgets();
        screen.markSummaryDirty();
        screen.openFrameEditDialog(frame);
        TutorialManager.getInstance().onGroupFramed();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.1F));
    }

    public void createNoteAt(double canvasX, double canvasY) {
        if (!screen.ensureEditPermission()) return;
        String defaultTitle = Component.translatable("gui.gtcalcboard.default_note_name").getString();
        CanvasStickyNote note = CanvasStickyNote.create(
                defaultTitle,
                "",
                CanvasStickyNote.COLOR_AMBER,
                canvasX - 80, canvasY - 50
        );
        screen.getGraph().addStickyNote(note);
        screen.recordCommand(new BoardCommand.AddStickyNotesCommand(note, "Create Sticky Note"));
        screen.clearSelection();
        screen.rebuildWidgets();
        screen.markSummaryDirty();
        screen.openNoteEditDialog(note);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.1F));
    }

    public void addRerouteNodeAt(double canvasX, double canvasY) {
        if (!screen.ensureEditPermission()) return;
        RecipeNode reroute = RecipeNode.createReroute(canvasX - 16, canvasY - 16);
        screen.getGraph().addNode(reroute);
        screen.recordCommand(new BoardCommand.AddNodesCommand(List.of(reroute), Collections.emptyList(), "Add Junction Node"));
        screen.rebuildWidgets();
        screen.markSummaryDirty();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F));
    }

    public void groupNodesIntoModule(Set<String> targetNodeIds, String moduleName, CanvasGroupFrame primaryFrame) {
        if (!screen.ensureEditPermission() || targetNodeIds == null || targetNodeIds.isEmpty()) return;
        FlowGraph graph = screen.getGraph();

        List<RecipeNode> origNodes = new ArrayList<>(graph.getNodes());
        List<FlowGraph.ConnectionEdge> origEdges = new ArrayList<>(graph.getConnections());

        String name = (moduleName != null && !moduleName.trim().isEmpty()) ? moduleName.trim() : Component.translatable("gui.gtcalcboard.default_compound_name").getString();
        RecipeNode moduleNode = graph.groupIntoModule(targetNodeIds, name, primaryFrame);

        if (moduleNode != null) {
            List<RecipeNode> groupedNodes = new ArrayList<>();
            for (RecipeNode n : origNodes) {
                if (!graph.getNodes().contains(n)) {
                    groupedNodes.add(n);
                }
            }
            List<FlowGraph.ConnectionEdge> rewires = new ArrayList<>(graph.getConnections());
            screen.recordCommand(new BoardCommand.GroupModuleCommand(groupedNodes, moduleNode, origEdges, rewires));

            screen.clearSelection();
            screen.rebuildWidgets();
            screen.markSummaryDirty();
            TutorialManager.getInstance().onModuleGrouped();

            BoardToast.show(Component.literal("§d▦ ").append(Component.translatable("message.gtcalcboard.group_success", String.valueOf(moduleNode.getContainedMachineCount()))));
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1.2F));
        }
    }

    public void collapseFrameIntoModule(CanvasGroupFrame frame) {
        if (!screen.ensureEditPermission() || frame == null) return;
        FlowGraph graph = screen.getGraph();

        Set<String> nodeIds = new HashSet<>();
        for (RecipeNode n : frame.getEnclosedNodes(graph)) {
            nodeIds.add(n.getId());
        }
        if (nodeIds.isEmpty()) {
            nodeIds.addAll(frame.getContainedNodeIds());
        }
        if (nodeIds.isEmpty()) return;

        groupNodesIntoModule(nodeIds, frame.getTitle(), frame);
    }

    public void bringNodeToFront(RecipeNode node) {
        if (node == null) return;
        screen.getGraph().bringNodeToFront(node);
        List<NodeWidget> widgets = screen.getNodeWidgets();
        for (int i = 0; i < widgets.size(); i++) {
            if (widgets.get(i).getNode() == node) {
                NodeWidget widget = widgets.remove(i);
                widgets.add(widget);
                break;
            }
        }
    }

    public void undo() {
        BoardPage page = BoardManager.getInstance().getActivePage();
        if (page == null) return;
        BoardCommand cmd = page.getHistoryManager().undo(screen.getGraph());
        if (cmd != null) {
            screen.rebuildWidgets();
            screen.markSummaryDirty();
            TutorialManager.getInstance().onUndo();
            BoardToast.show(Component.literal("§e↶ ").append(Component.translatable("message.gtcalcboard.undo", cmd.getDescription())));
        }
    }

    public void redo() {
        BoardPage page = BoardManager.getInstance().getActivePage();
        if (page == null) return;
        BoardCommand cmd = page.getHistoryManager().redo(screen.getGraph());
        if (cmd != null) {
            screen.rebuildWidgets();
            screen.markSummaryDirty();
            TutorialManager.getInstance().onRedo();
            BoardToast.show(Component.literal("§a↷ ").append(Component.translatable("message.gtcalcboard.redo", cmd.getDescription())));
        }
    }

    public void fitToView() {
        FlowGraph graph = screen.getGraph();
        if (graph == null) return;

        List<RecipeNode> nodes = graph.getNodes();
        List<CanvasGroupFrame> frames = graph.getFrames();
        List<CanvasStickyNote> notes = graph.getStickyNotes();

        if (nodes.isEmpty() && frames.isEmpty() && notes.isEmpty()) {
            resetViewToDefault();
            return;
        }

        double[] bounds = calculateElementsBounds(nodes, frames, notes);
        if (bounds == null) return;

        applyViewBounds(bounds[0], bounds[1], bounds[2], bounds[3]);
    }

    private void resetViewToDefault() {
        screen.setPanX(40.0);
        screen.setPanY(40.0);
        screen.setZoom(1.0);
        BoardScreen.lastPanX = 40.0;
        BoardScreen.lastPanY = 40.0;
        BoardScreen.lastZoom = 1.0;
        BoardPage active = BoardManager.getInstance().getActivePage();
        if (active != null) {
            active.setPanX(40.0);
            active.setPanY(40.0);
            active.setZoom(1.0);
        }
        BoardToast.show(Component.literal("§e⌖ ").append(Component.translatable("gui.gtcalcboard.toast.fit_view_empty")));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
    }

    private double[] calculateElementsBounds(List<RecipeNode> nodes, List<CanvasGroupFrame> frames, List<CanvasStickyNote> notes) {
        Set<String> selNodes = screen.getSelectedNodeIds();
        Set<String> selFrames = screen.getSelectedFrameIds();
        Set<String> selNotes = screen.getSelectedNoteIds();
        boolean hasSelection = !selNodes.isEmpty() || !selFrames.isEmpty() || !selNotes.isEmpty();

        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE, maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;

        for (RecipeNode n : nodes) {
            if (hasSelection && !selNodes.contains(n.getId())) continue;
            minX = Math.min(minX, n.getPosX());
            minY = Math.min(minY, n.getPosY());
            maxX = Math.max(maxX, n.getPosX() + n.getCardWidth());
            maxY = Math.max(maxY, n.getPosY() + n.getCardHeight());
        }
        for (CanvasGroupFrame f : frames) {
            if (hasSelection && !selFrames.contains(f.getId())) continue;
            minX = Math.min(minX, f.getPosX());
            minY = Math.min(minY, f.getPosY());
            maxX = Math.max(maxX, f.getPosX() + f.getWidth());
            maxY = Math.max(maxY, f.getPosY() + f.getHeight());
        }
        for (CanvasStickyNote note : notes) {
            if (hasSelection && !selNotes.contains(note.getId())) continue;
            minX = Math.min(minX, note.getPosX());
            minY = Math.min(minY, note.getPosY());
            maxX = Math.max(maxX, note.getPosX() + note.getWidth());
            maxY = Math.max(maxY, note.getPosY() + note.getHeight());
        }

        if (minX == Double.MAX_VALUE) {
            for (RecipeNode n : nodes) {
                minX = Math.min(minX, n.getPosX());
                minY = Math.min(minY, n.getPosY());
                maxX = Math.max(maxX, n.getPosX() + n.getCardWidth());
                maxY = Math.max(maxY, n.getPosY() + n.getCardHeight());
            }
        }

        if (minX == Double.MAX_VALUE) return null;
        return new double[]{minX, minY, maxX, maxY};
    }

    private void applyViewBounds(double minX, double minY, double maxX, double maxY) {
        double contentW = Math.max(60.0, maxX - minX);
        double contentH = Math.max(60.0, maxY - minY);
        double centerCanvasX = minX + contentW / 2.0;
        double centerCanvasY = minY + contentH / 2.0;

        double padding = 80.0;
        double availableW = Math.max(150.0, screen.width - padding * 2);
        double availableH = Math.max(150.0, screen.height - padding * 2);

        double targetZoom = Math.min(1.0, Math.min(availableW / contentW, availableH / contentH));
        targetZoom = Math.max(0.25, Math.min(1.5, targetZoom));

        double targetPanX = (screen.width / 2.0) - (centerCanvasX * targetZoom);
        double targetPanY = (screen.height / 2.0) - (centerCanvasY * targetZoom);

        screen.setPanX(targetPanX);
        screen.setPanY(targetPanY);
        screen.setZoom(targetZoom);
        BoardScreen.lastPanX = targetPanX;
        BoardScreen.lastPanY = targetPanY;
        BoardScreen.lastZoom = targetZoom;

        BoardPage active = BoardManager.getInstance().getActivePage();
        if (active != null) {
            active.setPanX(targetPanX);
            active.setPanY(targetPanY);
            active.setZoom(targetZoom);
        }

        BoardToast.show(Component.literal("§b⌖ ").append(Component.translatable("gui.gtcalcboard.toast.fit_view")));
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.2F));
    }

    private List<RecipeNode> getSelectedNodesList() {
        Set<String> ids = screen.getSelectedNodeIds();
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<RecipeNode> result = new ArrayList<>();
        for (RecipeNode n : screen.getGraph().getNodes()) {
            if (ids.contains(n.getId())) result.add(n);
        }
        return result;
    }

    private List<CanvasStickyNote> getSelectedNotesList() {
        Set<String> ids = screen.getSelectedNoteIds();
        if (ids == null || ids.isEmpty()) return Collections.emptyList();
        List<CanvasStickyNote> result = new ArrayList<>();
        for (CanvasStickyNote note : screen.getGraph().getStickyNotes()) {
            if (ids.contains(note.getId())) result.add(note);
        }
        return result;
    }
}
