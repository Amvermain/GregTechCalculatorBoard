package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.OverclockMode;
import com.gtceu.calcboard.api.RecipeNode;
import com.gtceu.calcboard.api.SteamMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.*;

/**
 * Interactive card widget representing a single GregTech recipe machine node on the canvas.
 */
public class NodeWidget {
    public static final int WIDTH = 245;
    public static final int DEFAULT_WIDTH = 245;
    public static final int HEADER_HEIGHT = 20;

    private final RecipeNode node;
    private final BoardScreen parent;
    private final NodeCountEditor countEditor;
    private final NodeParallelEditor parallelEditor;
    private final NodeNameEditor nameEditor;
    private long lastHeaderClickTime = 0;

    // Cached rates for 144+ FPS performance
    private Map<IngredientStack, Double> cachedInputRates = null;
    private Map<IngredientStack, Double> cachedOutputRates = null;

    public NodeWidget(RecipeNode node) {
        this(node, null);
    }

    public NodeWidget(RecipeNode node, BoardScreen parent) {
        this.node = node;
        this.parent = parent;
        this.countEditor = new NodeCountEditor(this);
        this.parallelEditor = new NodeParallelEditor(this);
        this.nameEditor = new NodeNameEditor(this, node);
        invalidateCache();
    }

    public void invalidateCache() {
        this.cachedInputRates = null;
        this.cachedOutputRates = null;
        if (parent != null) {
            parent.markSummaryDirty();
        }
    }

    public void updateCountBuffer() {
        countEditor.updateBuffer();
        parallelEditor.updateBuffer();
    }

    public void commitCountEdit() {
        countEditor.commit();
        parallelEditor.commit();
        nameEditor.commitEdit();
    }

    public RecipeNode getNode() {
        return node;
    }

    public NodeCountEditor getCountEditor() {
        return countEditor;
    }

    public NodeParallelEditor getParallelEditor() {
        return parallelEditor;
    }

    public NodeNameEditor getNameEditor() {
        return nameEditor;
    }

    public BoardScreen getParent() {
        return parent;
    }

    public int getWidth() {
        return node.getCardWidth();
    }

    public int getContentStartY() {
        int y = (int) node.getPosY();
        int ctrlY = y + HEADER_HEIGHT + 6;
        int row2H = node.isModule() ? 0 : 18;
        int infoY = ctrlY + row2H + 18;
        int sepY = infoY + 14;
        return sepY + 4;
    }

    public int calculateAutoHeight() {
        if (node.isReroute()) return 32;
        int maxRows = Math.max(node.getInputs().size(), node.getOutputs().size());
        int contentStartY = getContentStartY();
        int contentEndY = contentStartY + Math.max(1, maxRows) * 18 + 8;
        return (int) (contentEndY - node.getPosY());
    }

    public int getHeight() {
        if (node.isReroute()) return 32;
        return Math.max(calculateAutoHeight(), node.getCardHeight());
    }

    public float getOutputPortX(int index) {
        if (node.isReroute()) {
            return (float) (node.getPosX() + (node.isFlipped() ? 0 : 32));
        }
        return (float) (node.getPosX() + (node.isFlipped() ? 6 : getWidth() - 6));
    }

    public float getOutputPortY(int index) {
        if (node.isReroute()) {
            return (float) (node.getPosY() + 16);
        }
        int contentY = getContentStartY();
        return contentY + index * 18 + 8;
    }

    public float getInputPortX(int index) {
        if (node.isReroute()) {
            return (float) (node.getPosX() + (node.isFlipped() ? 32 : 0));
        }
        return (float) (node.getPosX() + (node.isFlipped() ? getWidth() - 6 : 6));
    }

    public float getInputPortY(int index) {
        if (node.isReroute()) {
            return (float) (node.getPosY() + 16);
        }
        int contentY = getContentStartY();
        return contentY + index * 18 + 8;
    }

    public boolean isHeaderHovered(double canvasMouseX, double canvasMouseY) {
        if (node.isReroute()) {
            return isPointInside(canvasMouseX, canvasMouseY)
                    && getHoveredInputPortIndex(canvasMouseX, canvasMouseY) < 0
                    && getHoveredOutputPortIndex(canvasMouseX, canvasMouseY) < 0;
        }
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int headerBtnWidth = node.isModule() ? 74 : 56;
        return canvasMouseX >= x && canvasMouseX <= x + getWidth() - headerBtnWidth && canvasMouseY >= y && canvasMouseY <= y + HEADER_HEIGHT;
    }

    public boolean isExpandButtonHovered(double canvasMouseX, double canvasMouseY) {
        if (!node.isModule() || node.isReroute()) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int expandX = x + getWidth() - 72;
        return canvasMouseX >= expandX && canvasMouseX <= expandX + 16 && canvasMouseY >= y + 2 && canvasMouseY <= y + 18;
    }

    public boolean isFlipButtonHovered(double canvasMouseX, double canvasMouseY) {
        if (node.isReroute()) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int flipX = x + getWidth() - 54;
        return canvasMouseX >= flipX && canvasMouseX <= flipX + 16 && canvasMouseY >= y + 2 && canvasMouseY <= y + 18;
    }

    public boolean isTargetButtonHovered(double canvasMouseX, double canvasMouseY) {
        if (node.isReroute()) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int targetX = x + getWidth() - 36;
        return canvasMouseX >= targetX && canvasMouseX <= targetX + 18 && canvasMouseY >= y + 2 && canvasMouseY <= y + 18;
    }

    public boolean isCloseButtonHovered(double canvasMouseX, double canvasMouseY) {
        if (node.isReroute()) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int closeX = x + getWidth() - 18;
        return canvasMouseX >= closeX && canvasMouseX <= closeX + 16 && canvasMouseY >= y + 2 && canvasMouseY <= y + 18;
    }

    public boolean isResizeHandleHovered(double canvasMouseX, double canvasMouseY) {
        if (node.isReroute()) return false;
        int right = (int) (node.getPosX() + getWidth());
        int bottom = (int) (node.getPosY() + getHeight());
        return canvasMouseX >= right - 12 && canvasMouseX <= right && canvasMouseY >= bottom - 12 && canvasMouseY <= bottom;
    }

    public boolean isPointInside(double canvasMouseX, double canvasMouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        return canvasMouseX >= x && canvasMouseX <= x + getWidth() && canvasMouseY >= y && canvasMouseY <= y + getHeight();
    }

    public int getHoveredInputPortIndex(double canvasMouseX, double canvasMouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        if (node.isReroute()) {
            boolean isFlipped = node.isFlipped();
            int minX = isFlipped ? (x + 22) : (x - 4);
            int maxX = isFlipped ? (x + 36) : (x + 10);
            if (canvasMouseX >= minX && canvasMouseX <= maxX && canvasMouseY >= y + 6 && canvasMouseY <= y + 26) {
                return 0;
            }
            return -1;
        }
        int contentY = getContentStartY();
        boolean isFlipped = node.isFlipped();

        for (int i = 0; i < node.getInputs().size(); i++) {
            int rowY = contentY + i * 18;
            int minX = isFlipped ? (x + getWidth() - 40) : x;
            int maxX = isFlipped ? (x + getWidth() + 4) : (x + 40);
            if (canvasMouseX >= minX && canvasMouseX <= maxX && canvasMouseY >= rowY - 3 && canvasMouseY <= rowY + 19) {
                return i;
            }
        }
        return -1;
    }

    public int getHoveredOutputPortIndex(double canvasMouseX, double canvasMouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        if (node.isReroute()) {
            boolean isFlipped = node.isFlipped();
            int minX = isFlipped ? (x - 4) : (x + 22);
            int maxX = isFlipped ? (x + 10) : (x + 36);
            if (canvasMouseX >= minX && canvasMouseX <= maxX && canvasMouseY >= y + 6 && canvasMouseY <= y + 26) {
                return 0;
            }
            return -1;
        }
        int contentY = getContentStartY();
        boolean isFlipped = node.isFlipped();

        for (int i = 0; i < node.getOutputs().size(); i++) {
            int rowY = contentY + i * 18;
            int minX = isFlipped ? x : (x + getWidth() - 40);
            int maxX = isFlipped ? (x + 40) : (x + getWidth() + 4);
            if (canvasMouseX >= minX && canvasMouseX <= maxX && canvasMouseY >= rowY - 3 && canvasMouseY <= rowY + 19) {
                return i;
            }
        }
        return -1;
    }

    public IngredientStack getHoveredIngredient(double canvasMouseX, double canvasMouseY) {
        int inIdx = getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
        if (inIdx >= 0 && inIdx < node.getInputs().size()) {
            return node.getInputs().get(inIdx);
        }
        int outIdx = getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
        if (outIdx >= 0 && outIdx < node.getOutputs().size()) {
            return node.getOutputs().get(outIdx);
        }
        return null;
    }

    public double getInputRate(int index) {
        return node.getInputSlotRate(index, true);
    }

    public double getOutputRate(int index) {
        return node.getOutputSlotRate(index, true);
    }

    public double getNominalInputRate(int index) {
        return node.getInputSlotRate(index, false);
    }

    public double getNominalOutputRate(int index) {
        return node.getOutputSlotRate(index, false);
    }

    public Map<IngredientStack, Double> getCachedInputRates() {
        if (cachedInputRates == null) {
            cachedInputRates = node.calculateEffectiveInputRates();
        }
        return cachedInputRates;
    }

    public Map<IngredientStack, Double> getCachedOutputRates() {
        if (cachedOutputRates == null) {
            cachedOutputRates = node.calculateEffectiveOutputRates();
        }
        return cachedOutputRates;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NodeCardRenderer.render(this, graphics, mouseX, mouseY, partialTicks);
    }

    public boolean isModuleBadgeHovered(double mouseX, double mouseY) {
        if (!node.isModule()) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + HEADER_HEIGHT + 6;
        int cardW = getWidth();
        String machinesBadge = String.format("§d📦 %d%s", node.getContainedMachineCount(), Component.translatable("gui.gtcalcboard.machine_unit").getString());
        int badgeW = Minecraft.getInstance().font.width(machinesBadge);
        int badgeX = x + cardW - 6 - badgeW;
        return mouseX >= badgeX - 4 && mouseX <= x + cardW && mouseY >= ctrlY && mouseY <= ctrlY + 16;
    }

    public boolean isTierButtonHovered(double mouseX, double mouseY) {
        if (node.isModule()) return false;
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        return adapter.isTierOrSpeedControlHovered(node, mouseX, mouseY);
    }

    public boolean isOcButtonHovered(double mouseX, double mouseY) {
        if (node.isModule()) return false;
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        return adapter.isSecondaryControlHovered(node, mouseX, mouseY);
    }

    public boolean isAddonTrayHovered(double mouseX, double mouseY) {
        if (node.isModule() || node.getAddons().isEmpty()) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + HEADER_HEIGHT + 6;
        int cardW = getWidth();
        int trayWidth = Math.min(node.getAddons().size(), 3) * 16 + (node.getAddons().size() > 3 ? 16 : 0);
        return mouseX >= x + cardW - 6 - trayWidth && mouseX <= x + cardW - 6 && mouseY >= ctrlY - 2 && mouseY <= ctrlY + 16;
    }

    public boolean isMachineConfigButtonHovered(double mouseX, double mouseY) {
        if (node.isModule()) return false;
        if (isAddonTrayHovered(mouseX, mouseY)) return true;
        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        return adapter.isMachineConfigHovered(node, mouseX, mouseY);
    }

    public boolean isRotorButtonHovered(double mouseX, double mouseY) {
        return isMachineConfigButtonHovered(mouseX, mouseY);
    }

    public boolean isParallelButtonHovered(double mouseX, double mouseY) {
        return isMachineConfigButtonHovered(mouseX, mouseY);
    }

    public boolean changeTier(int direction) {
        if (parent != null && !parent.ensureEditPermission()) return false;

        if (node.getEnergyType() == com.gtceu.calcboard.api.EnergyType.HEAT_OR_SELF) {
            com.gtceu.calcboard.api.GTBoilerTier curTier = com.gtceu.calcboard.api.GTBoilerTier.getBoilerTier(node);
            com.gtceu.calcboard.api.GTBoilerTier[] vals = com.gtceu.calcboard.api.GTBoilerTier.values();
            int newIdx = (curTier.ordinal() + direction + vals.length) % vals.length;
            com.gtceu.calcboard.api.GTBoilerTier nextTier = vals[newIdx];
            node.setMachineIcon(nextTier.getDefaultIcon());
            node.setMultiblock(nextTier.isMultiblock());
            if (parent != null) parent.markSummaryDirty();
            invalidateCache();
            return true;
        }

        if (node.supportsSteamMode()) {
            SteamMode curSteam = node.getSteamMode();
            if (curSteam == SteamMode.LOW_PRESSURE) {
                if (direction > 0) {
                    node.setSteamMode(SteamMode.HIGH_PRESSURE);
                    if (parent != null) parent.markSummaryDirty();
                    invalidateCache();
                    return true;
                }
                return false;
            } else if (curSteam == SteamMode.HIGH_PRESSURE) {
                if (direction > 0) {
                    node.setSteamMode(SteamMode.NONE);
                    node.setTargetTier(GTVoltageTier.LV);
                    if (parent != null) parent.markSummaryDirty();
                    invalidateCache();
                    return true;
                } else if (direction < 0) {
                    node.setSteamMode(SteamMode.LOW_PRESSURE);
                    if (parent != null) parent.markSummaryDirty();
                    invalidateCache();
                    return true;
                }
            } else {
                if (direction < 0 && node.getTargetTier() == GTVoltageTier.LV) {
                    node.setSteamMode(SteamMode.HIGH_PRESSURE);
                    if (parent != null) parent.markSummaryDirty();
                    invalidateCache();
                    return true;
                }
            }
        }

        int curIdx = node.getTargetTier().ordinal();
        int minIdx = node.getRecipeTier().ordinal();
        int maxIdx = GTVoltageTier.values().length - 1;

        if (node.isTurbine() && !node.isMultiblock()) {
            maxIdx = GTVoltageTier.HV.ordinal();
        }

        int newIdx = curIdx + direction;
        if (node.isTurbine() && !node.isMultiblock() && newIdx > GTVoltageTier.HV.ordinal()) {
            if (node.hasMultiblockOption()) {
                node.setMultiblock(true);
            } else {
                return false;
            }
        } else if (newIdx < minIdx || newIdx > maxIdx || newIdx == curIdx) {
            return false;
        }

        GTVoltageTier oldTier = node.getTargetTier();
        GTVoltageTier newTier = GTVoltageTier.getByIndex(newIdx);

        node.setTargetTier(newTier);
        com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.syncTurbineMachineIcon(node);
        if (parent != null) {
            parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, newTier));
            parent.markSummaryDirty();
        }
        invalidateCache();
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!node.isModule()) {
            com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
            if (adapter.handleControlScroll(this, node, mouseX, mouseY, delta)) {
                return true;
            }
        }

        int inIdx = getHoveredInputPortIndex(mouseX, mouseY);
        if (inIdx >= 0 && inIdx < node.getInputs().size()) {
            if (parent != null && !parent.ensureEditPermission()) return true;
            IngredientStack in = node.getInputs().get(inIdx);
            if (in.hasAlternatives()) {
                in.cycleAlternative(delta > 0 ? -1 : 1);
                invalidateCache();
                if (parent != null) {
                    parent.markSummaryDirty();
                }
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                        net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.4F
                    )
                );
                return true;
            }
        }
        return false;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();

        if (!isPointInside(mouseX, mouseY)) {
            commitCountEdit();
            return false;
        }

        if (parent != null && !parent.ensureEditPermission()) {
            return true;
        }

        // Module Expand Button [⤢]
        if (isExpandButtonHovered(mouseX, mouseY)) {
            List<FlowGraph.ConnectionEdge> moduleEdges = new ArrayList<>();
            for (FlowGraph.ConnectionEdge e : parent.getGraph().getConnections()) {
                if (e.fromNodeId().equals(node.getId()) || e.toNodeId().equals(node.getId())) {
                    moduleEdges.add(e);
                }
            }
            List<RecipeNode> subNodes = node.getSubGraph() != null ? new ArrayList<>(node.getSubGraph().getNodes()) : Collections.emptyList();
            List<FlowGraph.ConnectionEdge> subEdges = node.getSubGraph() != null ? new ArrayList<>(node.getSubGraph().getConnections()) : Collections.emptyList();

            boolean expanded = parent.getGraph().expandModule(node);
            if (expanded) {
                parent.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.ExpandModuleCommand(node, subNodes, subEdges, moduleEdges));
                parent.rebuildWidgets();
                parent.markSummaryDirty();
                com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onModuleExpanded();
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_STONECUTTER_TAKE_RESULT, 1.2F)
                );
            }
            return true;
        }

        // Close Button [X]
        if (isCloseButtonHovered(mouseX, mouseY)) {
            parent.removeNode(this);
            return true;
        }

        // Direction / Flip Button [➔] or [⬅]
        if (isFlipButtonHovered(mouseX, mouseY)) {
            boolean oldFlipped = node.isFlipped();
            boolean newFlipped = !oldFlipped;
            node.setFlipped(newFlipped);
            if (parent != null) {
                parent.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.FlipNodesCommand(node, oldFlipped, newFlipped));
                parent.markSummaryDirty();
            }
            invalidateCache();
            Minecraft mc = Minecraft.getInstance();
            mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.1F));
            return true;
        }

        // Target Base Node Toggle Button [🎯]
        if (isTargetButtonHovered(mouseX, mouseY)) {
            boolean nowBase = !node.isBaseNode();
            parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.baseAnchor(node.getId(), !nowBase, nowBase));
            parent.getGraph().setBaseNode(nowBase ? node : null);
            parent.rebuildWidgets();
            parent.markSummaryDirty();

            Minecraft mc = Minecraft.getInstance();
            if (nowBase) {
                BoardToast.show(Component.literal("§6🎯 ").append(Component.translatable("message.gtcalcboard.base_set", node.getName())));
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.2F));
            } else {
                BoardToast.show(Component.literal("§7").append(Component.translatable("message.gtcalcboard.base_cleared")));
            }
            return true;
        }

        int ctrlY = y + HEADER_HEIGHT + 6;
        int countMinusX = x + 36;

        // Machine Count Decrement [-]
        if (mouseX >= countMinusX && mouseX <= countMinusX + 14 && mouseY >= ctrlY && mouseY <= ctrlY + 14) {
            commitCountEdit();
            double oldVal = node.getMachineCount();
            double newVal = Math.max(1.0, oldVal - 1);
            if (oldVal != newVal) {
                node.setMachineCount(newVal);
                parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.machineCount(node.getId(), oldVal, newVal));
            }
            updateCountBuffer();
            invalidateCache();
            return true;
        }

        // Count Input Box Click
        int countBoxX = countMinusX + 16;
        int countBoxW = Math.max(28, Minecraft.getInstance().font.width(countEditor.getDisplayText()) + 6);
        if (mouseX >= countBoxX && mouseX <= countBoxX + countBoxW && mouseY >= ctrlY && mouseY <= ctrlY + 14) {
            countEditor.startEditing();
            return true;
        }

        // Machine Count Increment [+]
        int afterCountX = countBoxX + countBoxW + 2;
        if (mouseX >= afterCountX && mouseX <= afterCountX + 14 && mouseY >= ctrlY && mouseY <= ctrlY + 14) {
            commitCountEdit();
            double oldVal = node.getMachineCount();
            double newVal = oldVal + 1;
            node.setMachineCount(newVal);
            parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.machineCount(node.getId(), oldVal, newVal));
            updateCountBuffer();
            invalidateCache();
            return true;
        }

        // Machine Count Half [/2]
        if (mouseX >= afterCountX + 16 && mouseX <= afterCountX + 32 && mouseY >= ctrlY && mouseY <= ctrlY + 14) {
            commitCountEdit();
            double oldVal = node.getMachineCount();
            double newVal = Math.max(1.0, Math.floor(oldVal / 2.0));
            if (oldVal != newVal) {
                node.setMachineCount(newVal);
                parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.machineCount(node.getId(), oldVal, newVal));
            }
            updateCountBuffer();
            invalidateCache();
            return true;
        }

        // Machine Count Double [x2]
        if (mouseX >= afterCountX + 34 && mouseX <= afterCountX + 50 && mouseY >= ctrlY && mouseY <= ctrlY + 14) {
            commitCountEdit();
            double oldVal = node.getMachineCount();
            double newVal = oldVal * 2.0;
            node.setMachineCount(newVal);
            parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.machineCount(node.getId(), oldVal, newVal));
            updateCountBuffer();
            invalidateCache();
            return true;
        }

        // Delegate row 2 control clicks (Tier/Speed, OC Mode, Machine Config) to IModAdapter
        if (!node.isModule()) {
            com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
            if (adapter.handleControlClick(this, node, mouseX, mouseY, button)) {
                return true;
            }
        }

        return false;
    }

    public boolean checkHeaderDoubleClick(double canvasMouseX, double canvasMouseY) {
        if (isHeaderHovered(canvasMouseX, canvasMouseY)) {
            long now = System.currentTimeMillis();
            if (now - lastHeaderClickTime < 350) {
                commitCountEdit();
                nameEditor.startEditing();
                return true;
            }
            lastHeaderClickTime = now;
        }
        return false;
    }


    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameEditor.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (countEditor.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (parallelEditor.keyPressed(keyCode, scanCode, modifiers)) return true;
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (nameEditor.charTyped(codePoint, modifiers)) return true;
        if (countEditor.charTyped(codePoint, modifiers)) return true;
        if (parallelEditor.charTyped(codePoint, modifiers)) return true;
        return false;
    }
}
