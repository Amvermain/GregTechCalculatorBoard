package com.gtceu.calcboard.client.gui.action;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.solver.AutoRatioResult;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BlueprintCodec;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.widget.BoardToast;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.*;

/**
 * Controller executing complex board actions triggered by the toolbar, hotkeys, or context menus.
 */
public class ToolbarActionHandler {
    private final BoardScreen screen;

    public ToolbarActionHandler(BoardScreen screen) {
        this.screen = screen;
    }

    private static boolean isShiftDownSafe() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null && Screen.hasShiftDown();
    }

    private static boolean isAltDownSafe() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.getWindow() != null && Screen.hasAltDown();
    }

    private static void playUiSound(SoundEvent sound, float pitch) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.getSoundManager() != null && sound != null) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch));
        }
    }

    private static void playUiSound(Holder<SoundEvent> sound, float pitch) {
        if (sound != null && sound.isBound()) {
            playUiSound(sound.value(), pitch);
        }
    }

    public void performAutoConnect() {
        if (!screen.ensureEditPermission()) return;
        if (isShiftDownSafe()) {
            performAutoConnectWithFilter(screen, null);
        } else {
            screen.openAutoConnectDialog();
        }
    }

    public static void performAutoConnectWithFilter(BoardScreen screen, Set<ResourceLocation> allowedItemIds) {
        if (screen == null || !screen.ensureEditPermission()) return;
        FlowGraph graph = screen.getGraph();
        List<BoardCommand> subCommands = new ArrayList<>();
        List<FlowGraph.ConnectionEdge> addedEdges = autoConnect(graph, subCommands, allowedItemIds);

        if (!addedEdges.isEmpty()) {
            subCommands.add(new BoardCommand.AddNodesCommand(
                Collections.emptyList(), addedEdges, "Auto Connect " + addedEdges.size() + " wires"
            ));
        }
        if (!subCommands.isEmpty()) {
            dispatchAutoConnectCommands(screen, subCommands, addedEdges.size());
        } else {
            BoardToast.show(Component.literal("§c✖ ").append(Component.translatable("gui.gtcalcboard.dialog.auto_connect.no_connections")));
        }
        screen.markSummaryDirty();
    }

    private static void dispatchAutoConnectCommands(BoardScreen screen, List<BoardCommand> subCommands, int edgeCount) {
        if (subCommands.size() == 1) {
            screen.recordCommand(subCommands.get(0));
        } else {
            screen.recordCommand(new BoardCommand.CompoundCommand(
                subCommands, "Auto Connect " + edgeCount + " wires"
            ));
        }
        BoardToast.show(Component.literal("§a⚡ ").append(Component.translatable("gui.gtcalcboard.toast.auto_connected", String.valueOf(edgeCount))));
        playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F);
    }

    public static List<FlowGraph.ConnectionEdge> autoConnect(FlowGraph graph, List<BoardCommand> subCommands) {
        return autoConnect(graph, subCommands, null);
    }

    public static List<FlowGraph.ConnectionEdge> autoConnect(FlowGraph graph, List<BoardCommand> subCommands, Set<ResourceLocation> allowedItemIds) {
        List<FlowGraph.ConnectionEdge> addedEdges = new ArrayList<>();
        if (graph == null) return addedEdges;

        for (RecipeNode from : graph.getNodes()) {
            for (int outIdx = 0; outIdx < from.getOutputs().size(); outIdx++) {
                connectOutputToGraph(graph, from, outIdx, allowedItemIds, addedEdges, subCommands);
            }
        }
        return addedEdges;
    }

    private static void connectOutputToGraph(FlowGraph graph, RecipeNode from, int outIdx, Set<ResourceLocation> allowedItemIds, List<FlowGraph.ConnectionEdge> addedEdges, List<BoardCommand> subCommands) {
        var out = from.getOutputs().get(outIdx);
        boolean fromFeedsReroute = !from.isReroute() && isOutputFeedingReroute(graph, from.getId(), outIdx);

        for (RecipeNode to : graph.getNodes()) {
            if (from == to) continue;
            connectOutputToNode(graph, from, outIdx, out, fromFeedsReroute, to, allowedItemIds, addedEdges, subCommands);
        }
    }

    private static void connectOutputToNode(FlowGraph graph, RecipeNode from, int outIdx, IngredientStack out, boolean fromFeedsReroute, RecipeNode to, Set<ResourceLocation> allowedItemIds, List<FlowGraph.ConnectionEdge> addedEdges, List<BoardCommand> subCommands) {
        for (int inIdx = 0; inIdx < to.getInputs().size(); inIdx++) {
            var in = to.getInputs().get(inIdx);
            tryConnectPortPair(graph, from, outIdx, out, fromFeedsReroute, to, inIdx, in, allowedItemIds, addedEdges, subCommands);
        }
    }

    private static void tryConnectPortPair(FlowGraph graph, RecipeNode from, int outIdx, IngredientStack out, boolean fromFeedsReroute, RecipeNode to, int inIdx, IngredientStack in, Set<ResourceLocation> allowedItemIds, List<FlowGraph.ConnectionEdge> addedEdges, List<BoardCommand> subCommands) {
        if (!canConnectPort(graph, from, out, to, inIdx, in)) return;

        ResourceLocation itemKey = out.getId() != null ? out.getId() : in.getId();
        if (allowedItemIds != null && (itemKey == null || !allowedItemIds.contains(itemKey))) return;
        if (isPortConnected(graph, from.getId(), outIdx, to.getId(), inIdx)) return;
        if (fromFeedsReroute && !to.isReroute()) return;

        RecipeNode targetNode = to;
        int targetInIdx = inIdx;
        if (!from.isReroute() && !to.isReroute()) {
            RecipeNode feedingReroute = findFeedingRerouteNode(graph, to.getId(), inIdx);
            if (feedingReroute != null) {
                targetNode = feedingReroute;
                targetInIdx = 0;
                if (isPortConnected(graph, from.getId(), outIdx, targetNode.getId(), targetInIdx)) {
                    return;
                }
            }
        }

        applyPortAlternativeSelection(graph, to, inIdx, out, in, subCommands);

        FlowGraph.ConnectionEdge edge = new FlowGraph.ConnectionEdge(from.getId(), outIdx, targetNode.getId(), targetInIdx);
        if (!graph.getConnections().contains(edge)) {
            graph.addConnection(from.getId(), outIdx, targetNode.getId(), targetInIdx);
            addedEdges.add(edge);
        }
    }

    private static void applyPortAlternativeSelection(FlowGraph graph, RecipeNode to, int inIdx, IngredientStack out, IngredientStack in, List<BoardCommand> subCommands) {
        boolean inputAlreadyFed = isInputPortFed(graph, to.getId(), inIdx);
        if (inputAlreadyFed || out.equals(in) || !in.hasAlternatives() || subCommands == null) return;

        ResourceLocation oldAlt = in.getId();
        in.selectAlternative(out.getId());
        ResourceLocation newAlt = in.getId();
        if (!Objects.equals(oldAlt, newAlt)) {
            subCommands.add(new BoardCommand.SelectAlternativeCommand(
                to.getId(), inIdx, true, oldAlt, newAlt
            ));
        }
    }

    private static boolean isPortConnected(FlowGraph graph, String fromNodeId, int outIdx, String toNodeId, int inIdx) {
        if (fromNodeId.equals(toNodeId)) return true;
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();

        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!edge.fromNodeId().equals(fromNodeId) || edge.outputIndex() != outIdx) continue;
            if (edge.toNodeId().equals(toNodeId) && edge.inputIndex() == inIdx) return true;
            RecipeNode target = graph.getNode(edge.toNodeId());
            if (target != null && target.isReroute() && visited.add(target.getId())) {
                queue.add(target.getId());
            }
        }

        return pollRerouteConnection(graph, queue, visited, toNodeId, inIdx);
    }

    private static boolean pollRerouteConnection(FlowGraph graph, Queue<String> queue, Set<String> visited, String toNodeId, int inIdx) {
        while (!queue.isEmpty()) {
            String currRerouteId = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (!edge.fromNodeId().equals(currRerouteId)) continue;
                if (edge.toNodeId().equals(toNodeId) && edge.inputIndex() == inIdx) return true;
                RecipeNode target = graph.getNode(edge.toNodeId());
                if (target != null && target.isReroute() && visited.add(target.getId())) {
                    queue.add(target.getId());
                }
            }
        }
        return false;
    }

    private static boolean isOutputFeedingReroute(FlowGraph graph, String fromNodeId, int outIdx) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!edge.fromNodeId().equals(fromNodeId) || edge.outputIndex() != outIdx) continue;
            RecipeNode target = graph.getNode(edge.toNodeId());
            if (target != null && target.isReroute()) return true;
        }
        return false;
    }

    private static RecipeNode findFeedingRerouteNode(FlowGraph graph, String toNodeId, int inIdx) {
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (!edge.toNodeId().equals(toNodeId) || edge.inputIndex() != inIdx) continue;
            RecipeNode src = graph.getNode(edge.fromNodeId());
            if (src != null && src.isReroute()) return src;
        }
        return null;
    }

    private static boolean isInputPortFed(FlowGraph graph, String toNodeId, int inIdx) {
        if (graph == null) return false;
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.toNodeId().equals(toNodeId) && edge.inputIndex() == inIdx) return true;
        }
        return false;
    }

    private static boolean isReachable(FlowGraph graph, String startNodeId, String targetNodeId) {
        if (startNodeId.equals(targetNodeId)) return true;
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(startNodeId);
        visited.add(startNodeId);

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
                if (!edge.fromNodeId().equals(curr)) continue;
                if (edge.toNodeId().equals(targetNodeId)) return true;
                if (visited.add(edge.toNodeId())) {
                    queue.add(edge.toNodeId());
                }
            }
        }
        return false;
    }

    private static boolean canConnectPort(FlowGraph graph, RecipeNode from, IngredientStack out, RecipeNode to, int inIdx, IngredientStack in) {
        boolean isExactMatch = out.equals(in) || Objects.equals(out.getId(), in.getId());
        if (isInputPortFed(graph, to.getId(), inIdx)) {
            return isExactMatch;
        }
        if (isExactMatch) return true;
        return in.matchesOrAlternative(out) && !isReachable(graph, to.getId(), from.getId());
    }

    public void performAutoRatio() {
        boolean isShift = isShiftDownSafe();
        boolean isAlt = isAltDownSafe();
        boolean isFractionalDefault = BoardManager.getInstance().isAutoRatioFractionalDefault();
        if (isAlt) {
            performAutoRatio(false, true);
        } else if (isShift) {
            performAutoRatio(true, false);
        } else {
            performAutoRatio(false, isFractionalDefault);
        }
    }

    public void performAutoRatio(boolean harmonized) {
        performAutoRatio(harmonized, false);
    }

    public void performAutoRatio(boolean harmonized, boolean fractional) {
        if (!screen.ensureEditPermission()) return;
        FlowGraph graph = screen.getGraph();
        RecipeNode baseNode = findAnchorNode(graph);

        Map<String, Double> oldCounts = captureMachineCounts(graph);
        AutoRatioResult result = executeAutoRatioAlgorithm(graph, baseNode, harmonized, fractional);
        recordAutoRatioHistory(graph, baseNode, oldCounts, harmonized, fractional);

        refreshWidgetsAfterAutoRatio();
        notifyAutoRatioResult(baseNode, harmonized, fractional, result);
    }

    private RecipeNode findAnchorNode(FlowGraph graph) {
        if (graph == null) return null;
        RecipeNode base = graph.findBaseNode();
        if (base == null && !graph.getNodes().isEmpty()) {
            return graph.getNodes().get(0);
        }
        return base;
    }

    private Map<String, Double> captureMachineCounts(FlowGraph graph) {
        Map<String, Double> counts = new HashMap<>();
        if (graph == null) return counts;
        for (RecipeNode n : graph.getNodes()) {
            counts.put(n.getId(), n.getMachineCount());
        }
        return counts;
    }

    private AutoRatioResult executeAutoRatioAlgorithm(FlowGraph graph, RecipeNode baseNode, boolean harmonized, boolean fractional) {
        if (graph == null || baseNode == null) return null;
        if (harmonized) {
            return graph.autoRatioHarmonized(baseNode);
        }
        if (fractional) {
            return graph.autoRatioFractional(baseNode);
        }
        return graph.autoRatioFromAnchor(baseNode);
    }

    private void recordAutoRatioHistory(FlowGraph graph, RecipeNode baseNode, Map<String, Double> oldCounts, boolean harmonized, boolean fractional) {
        if (graph == null) return;
        List<BoardCommand> subCmds = new ArrayList<>();
        for (RecipeNode n : graph.getNodes()) {
            double oldC = oldCounts.getOrDefault(n.getId(), 1.0);
            double newC = n.getMachineCount();
            if (Math.abs(oldC - newC) > 0.0001) {
                subCmds.add(BoardCommand.ModifyPropertyCommand.machineCount(n.getId(), oldC, newC));
            }
        }
        if (subCmds.isEmpty()) return;

        String baseName = baseNode != null ? baseNode.getName() : "Graph";
        String actionName = harmonized ? "Harmonized Auto Ratio (" + baseName + ")"
                : (fractional ? "Fractional Auto Ratio (" + baseName + ")" : "Auto Ratio (" + baseName + ")");
        screen.recordCommand(new BoardCommand.CompoundCommand(subCmds, actionName));
    }

    private void refreshWidgetsAfterAutoRatio() {
        for (NodeWidget w : screen.getNodeWidgets()) {
            w.updateCountBuffer();
            w.invalidateCache();
        }
        screen.markSummaryDirty();
        TutorialManager.getInstance().onAutoRatioTriggered();
    }

    private void notifyAutoRatioResult(RecipeNode baseNode, boolean harmonized, boolean fractional, AutoRatioResult result) {
        if (result != null && result.hasDivergence()) {
            BoardToast.show(Component.literal("§6⚠️ ").append(Component.translatable("message.gtcalcboard.auto_ratio_divergence_toast", result.divergentNodeIds().size())));
            playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8F);
            return;
        }
        String baseName = baseNode != null ? baseNode.getName() : "Graph";
        if (harmonized && baseNode != null) {
            BoardToast.show(Component.literal("§6✨ ").append(Component.translatable("message.gtcalcboard.auto_ratio_harmonized", baseName, (int) baseNode.getMachineCount())));
            playUiSound(SoundEvents.PLAYER_LEVELUP, 1.2F);
            return;
        }
        if (fractional && baseNode != null) {
            BoardToast.show(Component.literal("§b⚡ ").append(Component.translatable("message.gtcalcboard.auto_ratio_fractional", baseName)));
            playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F);
            return;
        }
        BoardToast.show(Component.literal("§a✔ ").append(Component.translatable("message.gtcalcboard.auto_ratio_matched", baseName)));
        playUiSound(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2F);
    }

    public void performMaxThroughputOptimization() {
        if (!screen.ensureEditPermission()) return;
        runMaxFlow();
        BoardToast.show(Component.literal("§6▲ ").append(Component.translatable("message.gtcalcboard.max_flow_optimized", "MAX")));
        playUiSound(SoundEvents.PLAYER_LEVELUP, 1.2F);
    }

    private void runMaxFlow() {
        FlowGraph graph = screen.getGraph();
        RecipeNode baseNode = graph.findBaseNode();
        if (baseNode == null && !graph.getNodes().isEmpty()) {
            baseNode = graph.getNodes().get(0);
        }

        Map<String, Object[]> oldProps = new HashMap<>();
        for (RecipeNode n : graph.getNodes()) {
            oldProps.put(n.getId(), new Object[]{n.getTargetTier(), n.getOverclockMode(), n.getParallel(), n.getMachineCount()});
        }

        for (RecipeNode n : graph.getNodes()) {
            GTVoltageTier baseTier = n.getRecipeTier();
            GTVoltageTier targetTier = GTVoltageTier.MAX;
            if (targetTier.ordinal() < baseTier.ordinal()) {
                targetTier = baseTier;
            }
            n.setTargetTier(targetTier);
        }

        if (baseNode != null) {
            graph.autoRatioFromAnchor(baseNode);
        }

        List<BoardCommand> subCmds = new ArrayList<>();
        for (RecipeNode n : graph.getNodes()) {
            Object[] oldP = oldProps.get(n.getId());
            if (oldP != null) {
                recordMaxFlowPropertyChanges(subCmds, n, oldP);
            }
        }
        if (!subCmds.isEmpty()) {
            screen.recordCommand(new BoardCommand.CompoundCommand(subCmds, "Max Flow Optimization"));
        }

        for (NodeWidget w : screen.getNodeWidgets()) {
            w.updateCountBuffer();
            w.invalidateCache();
        }
        screen.markSummaryDirty();

        BoardToast.show(Component.literal("§6▲ ").append(Component.translatable("message.gtcalcboard.max_flow_optimized", "MAX")));
        playUiSound(SoundEvents.PLAYER_LEVELUP, 1.2F);
    }

    private void recordMaxFlowPropertyChanges(List<BoardCommand> subCmds, RecipeNode n, Object[] oldP) {
        if (oldP[0] != n.getTargetTier()) {
            subCmds.add(BoardCommand.ModifyPropertyCommand.targetTier(n.getId(), (GTVoltageTier) oldP[0], n.getTargetTier()));
        }
        if (oldP[1] != n.getOverclockMode()) {
            subCmds.add(BoardCommand.ModifyPropertyCommand.overclockMode(n.getId(), (OverclockMode) oldP[1], n.getOverclockMode()));
        }
        if (!oldP[2].equals(n.getParallel())) {
            subCmds.add(BoardCommand.ModifyPropertyCommand.parallel(n.getId(), (Integer) oldP[2], n.getParallel()));
        }
        if (Math.abs(((Double) oldP[3]) - n.getMachineCount()) > 0.0001) {
            subCmds.add(BoardCommand.ModifyPropertyCommand.machineCount(n.getId(), (Double) oldP[3], n.getMachineCount()));
        }
    }

    public void performGroupIntoModule() {
        FlowGraph graph = screen.getGraph();
        var selectedIds = screen.getSelectedNodeIds();
        boolean hasSpecificSelection = selectedIds != null && selectedIds.size() >= 2;

        if (!hasSpecificSelection && graph.getNodes().size() < 2) {
            BoardToast.show(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.group_min_nodes")));
            return;
        }

        List<RecipeNode> origNodes = new ArrayList<>(graph.getNodes());
        List<FlowGraph.ConnectionEdge> origEdges = new ArrayList<>(graph.getConnections());

        String defaultModuleName = Component.translatable("gui.gtcalcboard.default_compound_name").getString();
        RecipeNode moduleNode = hasSpecificSelection 
            ? graph.groupIntoModule(selectedIds, defaultModuleName)
            : graph.groupIntoModule(defaultModuleName);

        if (moduleNode != null) {
            applyModuleGroupingResult(graph, origNodes, origEdges, moduleNode);
        }
    }

    private void applyModuleGroupingResult(FlowGraph graph, List<RecipeNode> origNodes, List<FlowGraph.ConnectionEdge> origEdges, RecipeNode moduleNode) {
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
        playUiSound(SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1.2F);
    }

    public void copyBlueprintToClipboard() {
        if (screen.getExportBlueprintDialog() != null) {
            screen.getExportBlueprintDialog().open();
        }
    }

    public void importBlueprintFromClipboard() {
        Minecraft mc = Minecraft.getInstance();
        String clip = mc.keyboardHandler.getClipboard();
        if (clip != null && !clip.trim().isEmpty()) {
            var pkg = BlueprintCodec.importPackageFromString(clip);
            if (pkg != null && screen.getImportBlueprintDialog() != null) {
                screen.getImportBlueprintDialog().open(pkg);
                return;
            }
        }

        if (screen.getDiskBlueprintsDialog() != null) {
            screen.getDiskBlueprintsDialog().open();
        } else {
            BoardToast.show(Component.literal("§c✖ ").append(Component.translatable("message.gtcalcboard.clipboard_empty")));
        }
    }
}
