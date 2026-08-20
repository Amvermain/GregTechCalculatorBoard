package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.FlowGraph;
import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.OverclockMode;
import com.gtceu.calcboard.api.RecipeNode;
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
        int maxRows = Math.max(node.getInputs().size(), node.getOutputs().size());
        int contentStartY = getContentStartY();
        int contentEndY = contentStartY + Math.max(1, maxRows) * 18 + 8;
        return (int) (contentEndY - node.getPosY());
    }

    public int getHeight() {
        return Math.max(calculateAutoHeight(), node.getCardHeight());
    }

    public float getOutputPortX(int index) {
        return (float) (node.getPosX() + getWidth() - 6);
    }

    public float getOutputPortY(int index) {
        int contentY = getContentStartY();
        return contentY + index * 18 + 8;
    }

    public float getInputPortX(int index) {
        return (float) (node.getPosX() + 6);
    }

    public float getInputPortY(int index) {
        int contentY = getContentStartY();
        return contentY + index * 18 + 8;
    }

    public boolean isHeaderHovered(double canvasMouseX, double canvasMouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        return canvasMouseX >= x && canvasMouseX <= x + getWidth() - 40 && canvasMouseY >= y && canvasMouseY <= y + HEADER_HEIGHT;
    }

    public boolean isExpandButtonHovered(double canvasMouseX, double canvasMouseY) {
        if (!node.isModule()) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int expandX = x + getWidth() - 54;
        return canvasMouseX >= expandX && canvasMouseX <= expandX + 16 && canvasMouseY >= y + 2 && canvasMouseY <= y + 18;
    }

    public boolean isTargetButtonHovered(double canvasMouseX, double canvasMouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int targetX = x + getWidth() - 36;
        return canvasMouseX >= targetX && canvasMouseX <= targetX + 18 && canvasMouseY >= y + 2 && canvasMouseY <= y + 18;
    }

    public boolean isCloseButtonHovered(double canvasMouseX, double canvasMouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int closeX = x + getWidth() - 18;
        return canvasMouseX >= closeX && canvasMouseX <= closeX + 16 && canvasMouseY >= y + 2 && canvasMouseY <= y + 18;
    }

    public boolean isResizeHandleHovered(double canvasMouseX, double canvasMouseY) {
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
        int contentY = getContentStartY();

        for (int i = 0; i < node.getInputs().size(); i++) {
            int rowY = contentY + i * 18;
            if (canvasMouseX >= x && canvasMouseX <= x + 40 && canvasMouseY >= rowY - 3 && canvasMouseY <= rowY + 19) {
                return i;
            }
        }
        return -1;
    }

    public int getHoveredOutputPortIndex(double canvasMouseX, double canvasMouseY) {
        int x = (int) node.getPosX();
        int contentY = getContentStartY();

        for (int i = 0; i < node.getOutputs().size(); i++) {
            int rowY = contentY + i * 18;
            int outPortX = x + getWidth() - 36;
            if (canvasMouseX >= outPortX && canvasMouseX <= x + getWidth() + 4 && canvasMouseY >= rowY - 3 && canvasMouseY <= rowY + 19) {
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
        if (index >= 0 && index < node.getInputs().size()) {
            return node.getInputs().get(index).getAmount() * node.getEffectiveCyclesPerSecond();
        }
        return 0.0;
    }

    public double getOutputRate(int index) {
        if (index >= 0 && index < node.getOutputs().size()) {
            return node.getOutputs().get(index).getExpectedAmount(node.getTierDelta()) * node.getEffectiveCyclesPerSecond();
        }
        return 0.0;
    }

    public double getNominalInputRate(int index) {
        if (index >= 0 && index < node.getInputs().size()) {
            return node.getInputs().get(index).getAmount() * node.getCyclesPerSecond();
        }
        return 0.0;
    }

    public double getNominalOutputRate(int index) {
        if (index >= 0 && index < node.getOutputs().size()) {
            return node.getOutputs().get(index).getExpectedAmount(node.getTierDelta()) * node.getCyclesPerSecond();
        }
        return 0.0;
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
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + HEADER_HEIGHT + 6;
        int row2Y = ctrlY + 18;
        return mouseX >= x + 6 && mouseX <= x + 38 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    public boolean isOcButtonHovered(double mouseX, double mouseY) {
        if (node.isModule() || node.isGenerator()) return false;
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + HEADER_HEIGHT + 6;
        int row2Y = ctrlY + 18;
        int ocX = x + 42;
        String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
        String ocText = Component.translatable(ocKey).getString();
        int ocW = Math.max(50, Minecraft.getInstance().font.width(ocText) + 6);
        return mouseX >= ocX && mouseX <= ocX + ocW && mouseY >= row2Y && mouseY <= row2Y + 14;
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
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + HEADER_HEIGHT + 6;
        int row2Y = ctrlY + 18;
        int configStartX;
        if (node.isGenerator()) {
            String genBadge = Component.translatable("gui.gtcalcboard.gen_badge").getString();
            int genW = Math.max(28, Minecraft.getInstance().font.width(genBadge) + 4);
            configStartX = x + 42 + genW + 3;
        } else {
            String ocKey = node.getOverclockMode() == OverclockMode.PERFECT ? "gui.gtcalcboard.oc_perf" : "gui.gtcalcboard.oc_std";
            String ocText = Component.translatable(ocKey).getString();
            int ocW = Math.max(50, Minecraft.getInstance().font.width(ocText) + 6);
            configStartX = x + 42 + ocW + 3;
        }
        return mouseX >= configStartX && mouseX <= x + getWidth() - 6 && mouseY >= row2Y && mouseY <= row2Y + 14;
    }

    public boolean isRotorButtonHovered(double mouseX, double mouseY) {
        return isMachineConfigButtonHovered(mouseX, mouseY);
    }

    public boolean isParallelButtonHovered(double mouseX, double mouseY) {
        return isMachineConfigButtonHovered(mouseX, mouseY);
    }

    public boolean changeTier(int direction) {
        int curIdx = node.getTargetTier().ordinal();
        int minIdx = node.getRecipeTier().ordinal();
        int maxIdx = GTVoltageTier.values().length - 1;

        int newIdx = curIdx + direction;
        if (newIdx < minIdx || newIdx > maxIdx || newIdx == curIdx) {
            return false;
        }

        // Only scale down machine count for normal consumer machines. Generators scale up parallels, not reduce count!
        if (!node.isGenerator()) {
            double speedRatio = node.getOverclockMode().getSpeedFactor();
            if (direction > 0) {
                node.setMachineCount(Math.max(0.01, node.getMachineCount() / speedRatio));
            } else {
                node.setMachineCount(node.getMachineCount() * speedRatio);
            }
        }

        node.setTargetTier(GTVoltageTier.getByIndex(newIdx));
        updateCountBuffer();
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isTierButtonHovered(mouseX, mouseY)) {
            return changeTier(delta > 0 ? +1 : -1);
        }

        int inIdx = getHoveredInputPortIndex(mouseX, mouseY);
        if (inIdx >= 0 && inIdx < node.getInputs().size()) {
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

        // Tier Selector Button Click (Row 2, Left: [LV])
        if (isTierButtonHovered(mouseX, mouseY)) {
            commitCountEdit();
            GTVoltageTier oldTier = node.getTargetTier();
            int direction = (button == 1) ? -1 : 1;
            changeTier(direction);
            GTVoltageTier newTier = node.getTargetTier();
            if (oldTier != newTier) {
                parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, newTier));
            }
            invalidateCache();
            return true;
        }

        // Overclock Mode Button Click (Row 2: [STD OC] / [PERF OC])
        if (isOcButtonHovered(mouseX, mouseY)) {
            commitCountEdit();
            OverclockMode oldOc = node.getOverclockMode();
            OverclockMode newOc = (oldOc == OverclockMode.STANDARD) ? OverclockMode.PERFECT : OverclockMode.STANDARD;
            node.setOverclockMode(newOc);
            parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.overclockMode(node.getId(), oldOc, newOc));
            invalidateCache();
            return true;
        }

        // Machine Configuration & Addon Dialog Button Click (Row 2: [⚙ 4x] / [⚙ 220%])
        if (isMachineConfigButtonHovered(mouseX, mouseY)) {
            commitCountEdit();
            parent.openMachineConfigDialog(node);
            return true;
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
