package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class NodeWidgetInteractionHandler {

    private NodeWidgetInteractionHandler() {}

    public static boolean mouseClicked(NodeWidget widget, double mouseX, double mouseY, int button) {
        RecipeNode node = widget.getNode();
        BoardScreen parent = widget.getParent();
        int x = (int) node.getPosX();

        if (widget.getHiddenPortsPopup().mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (!widget.isPointInside(mouseX, mouseY)) {
            widget.commitCountEdit();
            return false;
        }

        if (button == 1 && handlePortRightClick(widget, mouseX, mouseY)) {
            return true;
        }

        if (button == 0 && widget.isHiddenPortsBadgeHovered(mouseX, mouseY)) {
            widget.getHiddenPortsPopup().toggle();
            playClickSound(1.1F);
            return true;
        }

        if (node.isReroute()) {
            return handleRerouteClick(widget, mouseX, mouseY, button);
        }

        if (button == 0 && widget.isMachineIconHovered(mouseX, mouseY)) {
            if (parent != null) {
                parent.openMachineSelectorDialog(node);
                playClickSound(1.1F);
                return true;
            }
        }

        if (widget.isSwitchButtonHovered(mouseX, mouseY)) {
            if (parent != null) {
                parent.openRecipeSwitchDialog(node);
                playClickSound(1.1F);
            }
            return true;
        }

        if (widget.isExpandButtonHovered(mouseX, mouseY)) {
            return handleExpandModule(parent, node);
        }

        if (widget.isCloseButtonHovered(mouseX, mouseY)) {
            if (parent != null) parent.removeNode(widget);
            return true;
        }

        if (widget.isFlipButtonHovered(mouseX, mouseY)) {
            return handleFlipNode(widget, parent, node);
        }

        if (widget.isTargetButtonHovered(mouseX, mouseY)) {
            return handleTargetBaseToggle(parent, node);
        }

        if (handleCountButtonsAndEditors(widget, parent, node, mouseX, mouseY, button, x)) {
            return true;
        }

        return handleRow2ControlClick(widget, parent, node, mouseX, mouseY, button);
    }

    private static boolean handlePortRightClick(NodeWidget widget, double mouseX, double mouseY) {
        int inPort = widget.getHoveredInputPortIndex(mouseX, mouseY);
        if (inPort >= 0) {
            widget.hidePortAndDisconnectWires(true, inPort);
            return true;
        }
        int outPort = widget.getHoveredOutputPortIndex(mouseX, mouseY);
        if (outPort >= 0) {
            if (NodeWidget.isVoidToggleModifier()) {
                widget.toggleOutputPortVoid(outPort);
                return true;
            }
            widget.hidePortAndDisconnectWires(false, outPort);
            return true;
        }
        return false;
    }

    private static boolean handleExpandModule(BoardScreen parent, RecipeNode node) {
        if (parent == null || parent.getGraph() == null) return true;
        List<FlowGraph.ConnectionEdge> moduleEdges = new ArrayList<>();
        for (FlowGraph.ConnectionEdge e : parent.getGraph().getConnections()) {
            if (e.fromNodeId().equals(node.getId()) || e.toNodeId().equals(node.getId())) {
                moduleEdges.add(e);
            }
        }
        List<RecipeNode> subNodes = node.getSubGraph() != null ? new ArrayList<>(node.getSubGraph().getNodes()) : Collections.emptyList();
        List<FlowGraph.ConnectionEdge> subEdges = node.getSubGraph() != null ? new ArrayList<>(node.getSubGraph().getConnections()) : Collections.emptyList();
        List<com.gtceu.calcboard.api.model.CanvasGroupFrame> subFrames = node.getSubGraph() != null ? new ArrayList<>(node.getSubGraph().getFrames()) : Collections.emptyList();
        List<com.gtceu.calcboard.api.model.CanvasStickyNote> subNotes = node.getSubGraph() != null ? new ArrayList<>(node.getSubGraph().getStickyNotes()) : Collections.emptyList();

        boolean expanded = parent.getGraph().expandModule(node);
        if (expanded) {
            Set<String> subNodeIds = new HashSet<>();
            for (RecipeNode sn : subNodes) subNodeIds.add(sn.getId());
            List<FlowGraph.ConnectionEdge> restoredEdges = new ArrayList<>();
            for (FlowGraph.ConnectionEdge e : parent.getGraph().getConnections()) {
                if (subNodeIds.contains(e.fromNodeId()) || subNodeIds.contains(e.toNodeId())) {
                    restoredEdges.add(e);
                }
            }
            parent.recordCommand(new BoardCommand.ExpandModuleCommand(node, subNodes, restoredEdges, moduleEdges, subFrames, subNotes));
            parent.rebuildWidgets();
            parent.markSummaryDirty();
            TutorialManager.getInstance().onModuleExpanded();
            Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1.2F)
            );
        }
        return true;
    }

    private static boolean handleFlipNode(NodeWidget widget, BoardScreen parent, RecipeNode node) {
        boolean oldFlipped = node.isFlipped();
        boolean newFlipped = !oldFlipped;
        node.setFlipped(newFlipped);
        if (parent != null) {
            parent.recordCommand(new BoardCommand.FlipNodesCommand(node, oldFlipped, newFlipped));
            parent.markSummaryDirty();
        }
        widget.invalidateCache();
        playClickSound(1.1F);
        return true;
    }

    private static boolean handleTargetBaseToggle(BoardScreen parent, RecipeNode node) {
        if (parent == null || parent.getGraph() == null) return true;
        boolean nowBase = !node.isBaseNode();
        parent.recordCommand(BoardCommand.ModifyPropertyCommand.baseAnchor(node.getId(), !nowBase, nowBase));
        parent.getGraph().setBaseNode(nowBase ? node : null);
        parent.rebuildWidgets();
        parent.markSummaryDirty();

        Minecraft mc = Minecraft.getInstance();
        if (nowBase) {
            BoardToast.show(Component.literal("§6⌖ ").append(Component.translatable("message.gtcalcboard.base_set", node.getName())));
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP, 1.2F));
        } else {
            BoardToast.show(Component.literal("§7").append(Component.translatable("message.gtcalcboard.base_cleared")));
        }
        return true;
    }

    private static boolean handleCountButtonsAndEditors(NodeWidget widget, BoardScreen parent, RecipeNode node, double mouseX, double mouseY, int button, int x) {
        var bounds = widget.getLayoutBounds();

        if (bounds.getCountMinusBtnBounds().contains(mouseX, mouseY)) {
            return adjustMachineCount(widget, parent, node, -1, net.minecraft.client.gui.screens.Screen.hasShiftDown());
        }

        if (button == 0 && widget.getNameEditor().isEditing() && widget.isHeaderHovered(mouseX, mouseY)) {
            var mc = Minecraft.getInstance();
            if (mc != null && mc.font != null) {
                int titleX = x + (node.getMachineIcon() != null ? 22 : 6);
                widget.getNameEditor().onClick(mc.font, mouseX, titleX + 2, net.minecraft.client.gui.screens.Screen.hasShiftDown());
                return true;
            }
        }

        if (bounds.getCountBoxBounds().contains(mouseX, mouseY)) {
            if (!widget.getCountEditor().isEditing()) {
                widget.getCountEditor().startEditing();
            } else {
                var mc = Minecraft.getInstance();
                if (mc != null && mc.font != null) {
                    widget.getCountEditor().onClick(mc.font, mouseX, bounds.getCountBoxBounds().x() + 2, net.minecraft.client.gui.screens.Screen.hasShiftDown());
                }
            }
            return true;
        }

        if (bounds.getCountPlusBtnBounds().contains(mouseX, mouseY)) {
            return adjustMachineCount(widget, parent, node, 1, net.minecraft.client.gui.screens.Screen.hasShiftDown());
        }

        if (bounds.getCountHalfBtnBounds().contains(mouseX, mouseY)) {
            return scaleMachineCount(widget, parent, node, 0.5);
        }

        if (bounds.getCountDoubleBtnBounds().contains(mouseX, mouseY)) {
            return scaleMachineCount(widget, parent, node, 2.0);
        }

        return false;
    }

    private static boolean adjustMachineCount(NodeWidget widget, BoardScreen parent, RecipeNode node, int sign, boolean shift) {
        widget.commitCountEdit();
        double oldVal = node.getMachineCount();
        double step = shift ? 0.1 : (sign > 0 ? (oldVal < 1.0 ? 0.05 : 1.0) : (oldVal <= 1.0 ? 0.05 : 1.0));
        double newVal = sign > 0 ? Math.round((oldVal + step) * 1000.0) / 1000.0 : Math.max(0.01, Math.round((oldVal - step) * 1000.0) / 1000.0);
        applyNewCount(widget, parent, node, oldVal, newVal);
        return true;
    }

    private static boolean scaleMachineCount(NodeWidget widget, BoardScreen parent, RecipeNode node, double factor) {
        widget.commitCountEdit();
        double oldVal = node.getMachineCount();
        double newVal = factor < 1.0 ? Math.max(0.01, Math.round((oldVal * factor) * 1000.0) / 1000.0) : Math.round((oldVal * factor) * 1000.0) / 1000.0;
        applyNewCount(widget, parent, node, oldVal, newVal);
        return true;
    }

    private static void applyNewCount(NodeWidget widget, BoardScreen parent, RecipeNode node, double oldVal, double newVal) {
        if (oldVal != newVal) {
            node.setMachineCount(newVal);
            if (parent != null) {
                parent.recordCommand(BoardCommand.ModifyPropertyCommand.machineCount(node.getId(), oldVal, newVal));
                if (node.isCompoundNode()) {
                    parent.getGraph().syncCompoundParameters(node);
                    parent.rebuildWidgets();
                    parent.markSummaryDirty();
                }
            }
        }
        widget.updateCountBuffer();
        widget.invalidateCache();
    }

    private static boolean handleRow2ControlClick(NodeWidget widget, BoardScreen parent, RecipeNode node, double mouseX, double mouseY, int button) {
        var bounds = widget.getLayoutBounds();
        if (bounds.hasRow2Controls()) {
            var handler = ModGuiHandlerRegistry.getHandlerForNode(node);
            if (handler.handleControlClick(widget, node, mouseX, mouseY, button)) {
                if (node.isCompoundNode() && parent != null) {
                    parent.getGraph().syncCompoundParameters(node);
                    parent.rebuildWidgets();
                    parent.markSummaryDirty();
                }
                return true;
            }
        }
        return false;
    }

    public static boolean handleRerouteClick(NodeWidget widget, double mouseX, double mouseY, int button) {
        RecipeNode node = widget.getNode();
        BoardScreen parent = widget.getParent();
        if (widget.getHoveredInputPortIndex(mouseX, mouseY) >= 0 || widget.getHoveredOutputPortIndex(mouseX, mouseY) >= 0) {
            return false;
        }
        if (button == 0 && widget.isTargetBatchBadgeHovered(mouseX, mouseY)) {
            widget.commitCountEdit();
            widget.getTargetBatchEditor().startEditing();
            playClickSound(1.1F);
            return true;
        }
        if (button == 1 && net.minecraft.client.gui.screens.Screen.hasShiftDown() && parent != null) {
            parent.openJunctionSupplyDialog(node);
            playClickSound(1.1F);
            return true;
        }
        return false;
    }

    public static boolean mouseScrolled(NodeWidget widget, double mouseX, double mouseY, double delta) {
        var bounds = widget.getLayoutBounds();
        RecipeNode node = widget.getNode();
        int inIdx = bounds.getHoveredInputPortIndex(mouseX, mouseY);
        if (inIdx >= 0 && inIdx < node.getInputs().size()) {
            return handleInputPortScroll(widget, inIdx, delta);
        }

        if (bounds.hasRow2Controls()) {
            var handler = ModGuiHandlerRegistry.getHandlerForNode(node);
            if (handler.handleControlScroll(widget, node, mouseX, mouseY, delta)) {
                return true;
            }
        }
        return false;
    }

    public static boolean handleInputPortScroll(NodeWidget widget, int inIdx, double delta) {
        RecipeNode node = widget.getNode();
        BoardScreen parent = widget.getParent();
        if (parent != null && !parent.ensureEditPermission()) return true;

        IngredientStack in = node.getInputs().get(inIdx);
        if (in.isFluid() && SysteamsRecipeHandler.isDynamoToBoilerConvertible(node)) {
            var allFluids = SysteamsRecipeHandler.getAllBoilingFluidInputs();
            if (in.getAlternatives().size() != allFluids.size() || in.getAlternatives().stream().anyMatch(id -> !allFluids.contains(id))) {
                in.setAlternatives(allFluids);
            }
        }
        if (!in.hasAlternatives()) {
            return false;
        }

        ResourceLocation oldAlt = in.getId();
        in.cycleAlternative(delta > 0 ? -1 : 1);
        if (in.isFluid() && SysteamsRecipeHandler.isDynamoToBoilerConvertible(node)) {
            SysteamsRecipeHandler.updateBoilerFluidRecipe(node, in.getId());
        }
        widget.invalidateCache();
        if (parent != null) {
            ResourceLocation newAlt = in.getId();
            if (oldAlt != null && !oldAlt.equals(newAlt)) {
                parent.recordCommand(new BoardCommand.SelectAlternativeCommand(node.getId(), inIdx, true, oldAlt, newAlt));
            }
            parent.getGraph().cleanupInvalidConnections();
            parent.markSummaryDirty();
        }
        playClickSound(1.4F);
        return true;
    }

    private static void playClickSound(float pitch) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null) {
                mc.getSoundManager().play(
                    SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, pitch)
                );
            }
        } catch (Throwable ignored) {}
    }
}
