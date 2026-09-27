package com.gtceu.calcboard.client.gui.interaction;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.CanvasGroupFrame;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.dialog.RecipeSearchDialog;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles user interactions with an embedded shared machine panel,
 * including sub-card duty adjustments, inline recipe additions, and sub-card deletions.
 */
public final class EmbeddedPanelInteractionHandler {

    private EmbeddedPanelInteractionHandler() {}

    public static boolean handleMouseClick(
            BoardScreen screen,
            FlowGraph graph,
            CanvasGroupFrame frame,
            double canvasMouseX,
            double canvasMouseY,
            int button
    ) {
        if (screen == null || graph == null || frame == null || button != 0) return false;
        if (!screen.ensureEditPermission()) return true;

        int fx = (int) frame.getPosX();
        int fy = (int) frame.getPosY();
        int fw = (int) frame.getWidth();
        int fh = (int) frame.getHeight();

        int sumY = fy + (int) CanvasGroupFrame.HEADER_HEIGHT;
        if (canvasMouseY >= sumY && canvasMouseY <= sumY + 20) {
            if (handleSummaryBarClick(screen, graph, frame, canvasMouseX, sumY, fx)) {
                return true;
            }
        }

        if (handleSubCardsClick(screen, graph, frame, canvasMouseX, canvasMouseY)) {
            return true;
        }

        int addBtnY = fy + fh - 28;
        if (canvasMouseY >= addBtnY && canvasMouseY <= addBtnY + 22 && canvasMouseX >= fx + 6 && canvasMouseX <= fx + fw - 6) {
            openInlineRecipeSearch(screen, frame);
            return true;
        }

        return false;
    }

    private static boolean handleSubCardsClick(BoardScreen screen, FlowGraph graph, CanvasGroupFrame frame, double mouseX, double mouseY) {
        for (RecipeNode subNode : frame.getSubNodes(graph)) {
            if (subNode == null || subNode.isReroute()) continue;
            if (handleSingleSubCardClick(screen, graph, frame, subNode, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handleSingleSubCardClick(BoardScreen screen, FlowGraph graph, CanvasGroupFrame frame, RecipeNode subNode, double mouseX, double mouseY) {
        int sx = (int) subNode.getPosX();
        int sy = (int) subNode.getPosY();
        int sw = subNode.getCardWidth();

        if (mouseY < sy || mouseY > sy + 18) return false;

        int delX = sx + sw - 16;
        if (mouseX >= delX && mouseX <= delX + 12) {
            deleteSubCard(screen, graph, frame, subNode);
            return true;
        }

        int plusX = delX - 14;
        if (mouseX >= plusX && mouseX <= plusX + 12) {
            adjustSubNodeCount(screen, subNode, 0.1);
            return true;
        }

        int dutyW = Minecraft.getInstance().font.width(String.format(java.util.Locale.ROOT, "%.2fx", subNode.getMachineCount()));
        int minusX = plusX - dutyW - 18;
        if (mouseX >= minusX && mouseX <= minusX + 12) {
            adjustSubNodeCount(screen, subNode, -0.1);
            return true;
        }

        return false;
    }

    private static boolean handleSummaryBarClick(
            BoardScreen screen,
            FlowGraph graph,
            CanvasGroupFrame frame,
            double canvasMouseX,
            int sumY,
            int fx
    ) {
        int countMinusX = fx + 40;
        int countPlusX = countMinusX + 16 + Math.max(30, Minecraft.getInstance().font.width(String.format(java.util.Locale.ROOT, "%.2f", frame.getTargetPoolCapacity())) + 6) + 2;
        int div2X = countPlusX + 16;
        int mul2X = countPlusX + 34;

        double oldCap = frame.getTargetPoolCapacity();

        if (canvasMouseX >= countMinusX && canvasMouseX <= countMinusX + 14) {
            double newCap = Math.max(0.1, Math.round((oldCap - 1.0) * 100.0) / 100.0);
            scaleEmbeddedFrame(screen, graph, frame, oldCap, newCap);
            return true;
        }

        if (canvasMouseX >= countPlusX && canvasMouseX <= countPlusX + 14) {
            double newCap = Math.round((oldCap + 1.0) * 100.0) / 100.0;
            scaleEmbeddedFrame(screen, graph, frame, oldCap, newCap);
            return true;
        }

        if (canvasMouseX >= div2X && canvasMouseX <= div2X + 16) {
            double newCap = Math.max(0.1, Math.round((oldCap * 0.5) * 100.0) / 100.0);
            scaleEmbeddedFrame(screen, graph, frame, oldCap, newCap);
            return true;
        }

        if (canvasMouseX >= mul2X && canvasMouseX <= mul2X + 16) {
            double newCap = Math.round((oldCap * 2.0) * 100.0) / 100.0;
            scaleEmbeddedFrame(screen, graph, frame, oldCap, newCap);
            return true;
        }

        return false;
    }

    private static void adjustSubNodeCount(BoardScreen screen, RecipeNode node, double delta) {
        double oldCount = node.getMachineCount();
        double newCount = Math.max(0.01, Math.round((oldCount + delta) * 100.0) / 100.0);
        node.setMachineCount(newCount);
        screen.recordCommand(BoardCommand.ModifyPropertyCommand.machineCount(node.getId(), oldCount, newCount));
        screen.markSummaryDirty();
        screen.rebuildWidgets();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
    }

    private static void deleteSubCard(BoardScreen screen, FlowGraph graph, CanvasGroupFrame frame, RecipeNode subNode) {
        List<FlowGraph.ConnectionEdge> connectedEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge edge : graph.getConnections()) {
            if (edge.fromNodeId().equals(subNode.getId()) || edge.toNodeId().equals(subNode.getId())) {
                connectedEdges.add(edge);
            }
        }
        frame.removeRecipe(subNode.getId(), graph);
        screen.recordCommand(new BoardCommand.RemoveRecipeFromSharedFrameCommand(
                frame.getId(), subNode, connectedEdges, "Remove sub-recipe " + subNode.getName()
        ));
        screen.markSummaryDirty();
        screen.rebuildWidgets();
        if (screen.getWireRenderer() != null) {
            screen.getWireRenderer().markDirty();
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 0.9F));
    }

    public static void openInlineRecipeSearch(BoardScreen screen, CanvasGroupFrame frame) {
        if (screen == null || frame == null) return;
        screen.openRecipeSearchForSharedFrame(frame);
    }

    private static void scaleEmbeddedFrame(BoardScreen screen, FlowGraph graph, CanvasGroupFrame frame, double oldCap, double newCap) {
        frame.setTargetPoolCapacity(newCap);
        double factor = oldCap > 0.0001 ? (newCap / oldCap) : 1.0;

        List<RecipeNode> enclosed = frame.getEnclosedNodes(graph);
        Map<String, Double> oldCounts = new HashMap<>();
        for (RecipeNode n : enclosed) {
            oldCounts.put(n.getId(), n.getMachineCount());
        }
        if (!enclosed.isEmpty() && factor > 0.0) {
            frame.scaleEnclosedNodes(graph, factor);
        }
        List<BoardCommand> subCmds = new ArrayList<>();
        subCmds.add(new BoardCommand.ModifyFramePropertiesCommand(
                frame.getId(),
                frame.getTitle(), frame.getTitle(),
                frame.getColor(), frame.getColor(),
                frame.isSharedMachineFrame(), frame.isSharedMachineFrame(),
                oldCap, newCap
        ));
        for (RecipeNode n : enclosed) {
            double oldC = oldCounts.getOrDefault(n.getId(), 1.0);
            double newC = n.getMachineCount();
            if (Math.abs(oldC - newC) > 0.0001) {
                subCmds.add(BoardCommand.ModifyPropertyCommand.machineCount(n.getId(), oldC, newC));
            }
        }
        screen.recordCommand(new BoardCommand.CompoundCommand(subCmds, "Scale Embedded Pool: " + frame.getTitle()));
        screen.markSummaryDirty();
        screen.rebuildWidgets();
        if (screen.getWireRenderer() != null) {
            screen.getWireRenderer().markDirty();
        }
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.get(), 1.0F));
    }
}
