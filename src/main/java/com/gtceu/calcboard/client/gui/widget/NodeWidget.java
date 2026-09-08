package com.gtceu.calcboard.client.gui.widget;

import com.gtceu.calcboard.api.type.GTBoilerTier;
import com.gtceu.calcboard.client.gui.BoardScreen;
import com.gtceu.calcboard.client.gui.editor.NodeCountEditor;
import com.gtceu.calcboard.client.gui.editor.NodeNameEditor;
import com.gtceu.calcboard.client.gui.editor.NodeParallelEditor;
import com.gtceu.calcboard.client.gui.editor.NodeTargetBatchEditor;
import com.gtceu.calcboard.client.gui.render.NodeCardRenderer;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.compat.IModAdapter;
import com.gtceu.calcboard.compat.ModAdapterRegistry;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.api.model.IngredientStack;
import com.gtceu.calcboard.api.type.OverclockMode;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.type.SteamMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
    private final NodeTargetBatchEditor targetBatchEditor;
    private final HiddenPortsPopup hiddenPortsPopup;
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
        this.targetBatchEditor = new NodeTargetBatchEditor(this);
        this.hiddenPortsPopup = new HiddenPortsPopup(this);
        invalidateCache();
    }

    private final com.gtceu.calcboard.client.gui.render.NodeCardTextCache textCache = new com.gtceu.calcboard.client.gui.render.NodeCardTextCache();
    private com.gtceu.calcboard.client.gui.layout.NodeLayoutBounds layoutBounds = null;
    private double lastPosX = Double.NaN;
    private double lastPosY = Double.NaN;
    private int lastCardWidth = -1;
    private int lastCardHeight = -1;
    private boolean lastFlipped = false;
    private int lastInCount = -1;
    private int lastOutCount = -1;
    private int lastVisInCount = -1;
    private int lastVisOutCount = -1;
    private int lastHiddenCount = -1;
    private int lastAddonCount = -1;
    private com.gtceu.calcboard.api.type.EnergyType lastEnergyType = null;
    private boolean lastHasTargetBatch = false;
    private boolean lastIsModule = false;
    private boolean lastSlimMode = false;
    private boolean lastTargetBatchEditing = false;
    private String lastCountText = null;

    public com.gtceu.calcboard.client.gui.render.NodeCardTextCache getTextCache() {
        return textCache;
    }

    private boolean isLayoutDirty(boolean slim, boolean targetBatchEditing, String countText) {
        if (layoutBounds == null) return true;
        return node.getPosX() != lastPosX
                || node.getPosY() != lastPosY
                || node.getCardWidth() != lastCardWidth
                || node.getCardHeight() != lastCardHeight
                || node.isFlipped() != lastFlipped
                || node.getInputs().size() != lastInCount
                || node.getOutputs().size() != lastOutCount
                || node.getVisibleInputIndices().size() != lastVisInCount
                || node.getVisibleOutputIndices().size() != lastVisOutCount
                || node.getTotalHiddenCount() != lastHiddenCount
                || node.getAddons().size() != lastAddonCount
                || node.getEnergyType() != lastEnergyType
                || node.hasTargetBatch() != lastHasTargetBatch
                || node.isModule() != lastIsModule
                || slim != lastSlimMode
                || targetBatchEditing != lastTargetBatchEditing
                || !Objects.equals(countText, lastCountText);
    }

    public com.gtceu.calcboard.client.gui.layout.NodeLayoutBounds getLayoutBounds() {
        boolean slim = com.gtceu.calcboard.api.storage.BoardManager.getInstance().isSlimCardMode();
        boolean targetBatchEditing = targetBatchEditor != null && targetBatchEditor.isEditing();
        String countText = countEditor != null ? countEditor.getDisplayText() : "";

        if (isLayoutDirty(slim, targetBatchEditing, countText)) {
            int fontW = 20;
            try {
                var mc = Minecraft.getInstance();
                if (mc != null && mc.font != null) {
                    fontW = mc.font.width(countText);
                }
            } catch (Throwable ignored) {}
            this.layoutBounds = com.gtceu.calcboard.client.gui.layout.NodeLayoutCalculator.compute(node, slim, fontW, targetBatchEditing);
            this.lastPosX = node.getPosX();
            this.lastPosY = node.getPosY();
            this.lastCardWidth = node.getCardWidth();
            this.lastCardHeight = node.getCardHeight();
            this.lastFlipped = node.isFlipped();
            this.lastInCount = node.getInputs().size();
            this.lastOutCount = node.getOutputs().size();
            this.lastVisInCount = node.getVisibleInputIndices().size();
            this.lastVisOutCount = node.getVisibleOutputIndices().size();
            this.lastHiddenCount = node.getTotalHiddenCount();
            this.lastAddonCount = node.getAddons().size();
            this.lastEnergyType = node.getEnergyType();
            this.lastHasTargetBatch = node.hasTargetBatch();
            this.lastIsModule = node.isModule();
            this.lastSlimMode = slim;
            this.lastTargetBatchEditing = targetBatchEditing;
            this.lastCountText = countText;
        }
        return layoutBounds;
    }

    public void invalidateCache() {
        this.cachedInputRates = null;
        this.cachedOutputRates = null;
        this.layoutBounds = null;
        this.lastPosX = Double.NaN;
        this.textCache.markDirty();
        if (parent != null) {
            parent.markSummaryDirty();
        }
    }

    public void updateCountBuffer() {
        countEditor.updateBuffer();
        parallelEditor.updateBuffer();
        targetBatchEditor.updateBuffer();
    }

    public void commitCountEdit() {
        countEditor.commit();
        parallelEditor.commit();
        nameEditor.commitEdit();
        targetBatchEditor.commit();
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

    public NodeTargetBatchEditor getTargetBatchEditor() {
        return targetBatchEditor;
    }

    public HiddenPortsPopup getHiddenPortsPopup() {
        return hiddenPortsPopup;
    }

    public BoardScreen getParent() {
        return parent;
    }

    public int getWidth() {
        return node.getCardWidth();
    }

    public int getContentStartY() {
        return getLayoutBounds().getContentStartY();
    }

    public int calculateAutoHeight() {
        return getLayoutBounds().getAutoHeight();
    }

    public int getHeight() {
        return getLayoutBounds().getCardHeight();
    }

    public float getOutputPortX(int index) {
        var port = getLayoutBounds().findPort(false, index);
        if (port != null) return port.anchorX();
        if (node.isReroute()) return (float) (node.getPosX() + (node.isFlipped() ? 0 : 32));
        return (float) (node.getPosX() + (node.isFlipped() ? 6 : getWidth() - 6));
    }

    public float getOutputPortY(int index) {
        var port = getLayoutBounds().findPort(false, index);
        if (port != null) return port.anchorY();
        if (node.isReroute()) return (float) (node.getPosY() + 16);
        return (float) (getContentStartY() + index * 18 + 8);
    }

    public float getInputPortX(int index) {
        var port = getLayoutBounds().findPort(true, index);
        if (port != null) return port.anchorX();
        if (node.isReroute()) return (float) (node.getPosX() + (node.isFlipped() ? 32 : 0));
        return (float) (node.getPosX() + (node.isFlipped() ? getWidth() - 6 : 6));
    }

    public float getInputPortY(int index) {
        var port = getLayoutBounds().findPort(true, index);
        if (port != null) return port.anchorY();
        if (node.isReroute()) return (float) (node.getPosY() + 16);
        return (float) (getContentStartY() + index * 18 + 8);
    }

    public boolean isMachineIconHovered(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().isMachineIconHovered(canvasMouseX, canvasMouseY);
    }

    public boolean isTargetBatchBadgeHovered(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().isTargetBatchBadgeHovered(canvasMouseX, canvasMouseY);
    }

    public boolean isHeaderHovered(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().isHeaderHovered(canvasMouseX, canvasMouseY);
    }

    public boolean isSwitchButtonHovered(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().isSwitchButtonHovered(canvasMouseX, canvasMouseY);
    }

    public boolean isExpandButtonHovered(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().isExpandButtonHovered(canvasMouseX, canvasMouseY);
    }

    public boolean isFlipButtonHovered(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().isFlipButtonHovered(canvasMouseX, canvasMouseY);
    }

    public boolean isTargetButtonHovered(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().isTargetButtonHovered(canvasMouseX, canvasMouseY);
    }

    public boolean isCloseButtonHovered(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().isCloseButtonHovered(canvasMouseX, canvasMouseY);
    }

    public boolean isResizeHandleHovered(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().isResizeHandleHovered(canvasMouseX, canvasMouseY);
    }

    public boolean isPointInside(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().isPointInside(canvasMouseX, canvasMouseY);
    }

    public int getHoveredInputPortIndex(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().getHoveredInputPortIndex(canvasMouseX, canvasMouseY);
    }

    public int getHoveredOutputPortIndex(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().getHoveredOutputPortIndex(canvasMouseX, canvasMouseY);
    }

    public double[] getPortBounds(boolean isInput, int index) {
        return getLayoutBounds().getPortBounds(isInput, index);
    }

    public boolean isPortOverlapping(boolean isInput, int index, double minX, double minY, double maxX, double maxY) {
        double[] b = getPortBounds(isInput, index);
        if (b == null) return false;
        return (b[0] < maxX && b[2] > minX && b[1] < maxY && b[3] > minY);
    }

    public boolean isHiddenPortsBadgeHovered(double canvasMouseX, double canvasMouseY) {
        return getLayoutBounds().isHiddenPortsBadgeHovered(canvasMouseX, canvasMouseY);
    }

    public void hidePortAndDisconnectWires(boolean isInput, int portIndex) {
        if (parent != null && !parent.ensureEditPermission()) return;
        if (parent != null && parent.getGraph() != null) {
            com.gtceu.calcboard.api.model.FlowGraph graph = parent.getGraph();
            List<com.gtceu.calcboard.api.model.FlowGraph.ConnectionEdge> toRemove = new ArrayList<>();
            for (com.gtceu.calcboard.api.model.FlowGraph.ConnectionEdge e : graph.getConnections()) {
                if (isInput) {
                    if (e.toNodeId().equals(node.getId()) && e.inputIndex() == portIndex) {
                        toRemove.add(e);
                    }
                } else {
                    if (e.fromNodeId().equals(node.getId()) && e.outputIndex() == portIndex) {
                        toRemove.add(e);
                    }
                }
            }
            for (com.gtceu.calcboard.api.model.FlowGraph.ConnectionEdge edge : toRemove) {
                graph.removeConnection(edge);
            }
        }

        if (isInput) {
            node.hideInputPort(portIndex);
        } else {
            node.hideOutputPort(portIndex);
        }
        invalidateCache();
        if (parent != null) parent.markSummaryDirty();
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 0.9F)
        );
    }

    public void toggleOutputPortVoid(int portIndex) {
        if (parent != null && !parent.ensureEditPermission()) return;
        if (portIndex < 0 || portIndex >= node.getOutputs().size()) return;
        boolean currentlyVoided = node.isOutputPortVoided(portIndex);
        node.setOutputPortVoided(portIndex, !currentlyVoided);
        invalidateCache();
        if (parent != null) {
            parent.markSummaryDirty();
        }
        Minecraft.getInstance().getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, currentlyVoided ? 0.8F : 1.3F
            )
        );
    }

    public static boolean isVoidToggleModifier() {
        return Screen.hasControlDown() || Screen.hasAltDown() || Screen.hasShiftDown();
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
        return getLayoutBounds().isModuleBadgeHovered(mouseX, mouseY);
    }

    public boolean isTierButtonHovered(double mouseX, double mouseY) {
        if (!getLayoutBounds().hasRow2Controls()) return false;
        var handler = com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry.getHandlerForNode(node);
        return handler.isTierOrSpeedControlHovered(this, node, mouseX, mouseY);
    }

    public boolean isOcButtonHovered(double mouseX, double mouseY) {
        if (!getLayoutBounds().hasRow2Controls()) return false;
        var handler = com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry.getHandlerForNode(node);
        return handler.isSecondaryControlHovered(this, node, mouseX, mouseY);
    }

    public boolean isAddonTrayHovered(double mouseX, double mouseY) {
        return getLayoutBounds().isAddonTrayHovered(mouseX, mouseY);
    }

    public boolean isMachineConfigButtonHovered(double mouseX, double mouseY) {
        if (!getLayoutBounds().hasRow2Controls()) return false;
        if (isAddonTrayHovered(mouseX, mouseY)) return true;
        var handler = com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry.getHandlerForNode(node);
        return handler.isMachineConfigHovered(this, node, mouseX, mouseY);
    }

    public boolean isRotorButtonHovered(double mouseX, double mouseY) {
        return isMachineConfigButtonHovered(mouseX, mouseY);
    }

    public boolean isParallelButtonHovered(double mouseX, double mouseY) {
        return isMachineConfigButtonHovered(mouseX, mouseY);
    }

    public boolean changeTier(int direction) {
        return NodeTierChangeHandler.changeTier(this, node, parent, direction);
    }

    public void syncSharedFrameHardware(RecipeNode node) {
        NodeTierChangeHandler.syncSharedFrameHardware(parent, node);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return NodeWidgetInteractionHandler.mouseScrolled(this, mouseX, mouseY, delta);
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return NodeWidgetInteractionHandler.mouseClicked(this, mouseX, mouseY, button);
    }

    public boolean checkHeaderDoubleClick(double canvasMouseX, double canvasMouseY) {
        if (isHeaderHovered(canvasMouseX, canvasMouseY)) {
            long now = System.currentTimeMillis();
            if (now - lastHeaderClickTime < 350) {
                commitCountEdit();
                if (node.isReroute()) {
                    targetBatchEditor.startEditing();
                    Minecraft.getInstance().getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.1F)
                    );
                } else {
                    nameEditor.startEditing();
                }
                return true;
            }
            lastHeaderClickTime = now;
        }
        return false;
    }

    public boolean mouseDragged(double canvasMouseX, double canvasMouseY, int button, double dragX, double dragY) {
        if (button != 0) return false;
        var mc = Minecraft.getInstance();
        var font = mc != null ? mc.font : null;
        if (font == null) return false;

        int x = (int) node.getPosX();
        int y = (int) node.getPosY();

        if (nameEditor.isEditing()) {
            int titleX = x + (node.getMachineIcon() != null ? 22 : 6);
            nameEditor.onDrag(font, canvasMouseX, titleX + 2);
            return true;
        }

        if (countEditor.isEditing()) {
            int countMinusX = x + 36;
            int countBoxX = countMinusX + 16;
            countEditor.onDrag(font, canvasMouseX, countBoxX + 2);
            return true;
        }

        if (parallelEditor.isEditing()) {
            parallelEditor.onDrag(font, canvasMouseX, x + 6);
            return true;
        }

        if (targetBatchEditor.isEditing()) {
            targetBatchEditor.onDrag(font, canvasMouseX, x - 10);
            return true;
        }

        return false;
    }

    public boolean isAnyEditorActive() {
        return nameEditor.isEditing() || countEditor.isEditing() || parallelEditor.isEditing() || targetBatchEditor.isEditing();
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (nameEditor.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (countEditor.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (parallelEditor.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (targetBatchEditor.keyPressed(keyCode, scanCode, modifiers)) return true;
        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (nameEditor.charTyped(codePoint, modifiers)) return true;
        if (countEditor.charTyped(codePoint, modifiers)) return true;
        if (parallelEditor.charTyped(codePoint, modifiers)) return true;
        if (targetBatchEditor.charTyped(codePoint, modifiers)) return true;
        return false;
    }
}
