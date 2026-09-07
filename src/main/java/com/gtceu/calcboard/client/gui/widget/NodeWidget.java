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
    private LayoutCacheKey layoutCacheKey = null;
    private com.gtceu.calcboard.client.gui.layout.NodeLayoutBounds layoutBounds = null;

    private record LayoutCacheKey(
            double posX,
            double posY,
            int cardWidth,
            int cardHeight,
            boolean isFlipped,
            int inCount,
            int outCount,
            int visInCount,
            int visOutCount,
            int hiddenCount,
            int addonCount,
            com.gtceu.calcboard.api.type.EnergyType energyType,
            boolean hasTargetBatch,
            boolean isModule,
            boolean slimMode,
            boolean targetBatchEditing,
            String countText
    ) {}

    public com.gtceu.calcboard.client.gui.render.NodeCardTextCache getTextCache() {
        return textCache;
    }

    public com.gtceu.calcboard.client.gui.layout.NodeLayoutBounds getLayoutBounds() {
        boolean slim = com.gtceu.calcboard.api.storage.BoardManager.getInstance().isSlimCardMode();
        boolean targetBatchEditing = targetBatchEditor != null && targetBatchEditor.isEditing();
        String countText = countEditor != null ? countEditor.getDisplayText() : "";

        LayoutCacheKey currentKey = new LayoutCacheKey(
                node.getPosX(),
                node.getPosY(),
                node.getCardWidth(),
                node.getCardHeight(),
                node.isFlipped(),
                node.getInputs().size(),
                node.getOutputs().size(),
                node.getVisibleInputIndices().size(),
                node.getVisibleOutputIndices().size(),
                node.getTotalHiddenCount(),
                node.getAddons().size(),
                node.getEnergyType(),
                node.hasTargetBatch(),
                node.isModule(),
                slim,
                targetBatchEditing,
                countText
        );

        if (layoutBounds == null || !currentKey.equals(layoutCacheKey)) {
            int fontW = 20;
            try {
                var mc = Minecraft.getInstance();
                if (mc != null && mc.font != null) {
                    fontW = mc.font.width(countText);
                }
            } catch (Throwable ignored) {}
            this.layoutBounds = com.gtceu.calcboard.client.gui.layout.NodeLayoutCalculator.compute(node, slim, fontW, targetBatchEditing);
            this.layoutCacheKey = currentKey;
        }
        return layoutBounds;
    }

    public void invalidateCache() {
        this.cachedInputRates = null;
        this.cachedOutputRates = null;
        this.layoutBounds = null;
        this.layoutCacheKey = null;
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
        return handler.isTierOrSpeedControlHovered(node, mouseX, mouseY);
    }

    public boolean isOcButtonHovered(double mouseX, double mouseY) {
        if (!getLayoutBounds().hasRow2Controls()) return false;
        var handler = com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry.getHandlerForNode(node);
        return handler.isSecondaryControlHovered(node, mouseX, mouseY);
    }

    public boolean isAddonTrayHovered(double mouseX, double mouseY) {
        return getLayoutBounds().isAddonTrayHovered(mouseX, mouseY);
    }

    public boolean isMachineConfigButtonHovered(double mouseX, double mouseY) {
        if (!getLayoutBounds().hasRow2Controls()) return false;
        if (isAddonTrayHovered(mouseX, mouseY)) return true;
        var handler = com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry.getHandlerForNode(node);
        return handler.isMachineConfigHovered(node, mouseX, mouseY);
    }

    public boolean isRotorButtonHovered(double mouseX, double mouseY) {
        return isMachineConfigButtonHovered(mouseX, mouseY);
    }

    public boolean isParallelButtonHovered(double mouseX, double mouseY) {
        return isMachineConfigButtonHovered(mouseX, mouseY);
    }

    public boolean changeTier(int direction) {
        if (parent != null && !parent.ensureEditPermission()) return false;

        com.gtceu.calcboard.compat.IModAdapter adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        if (adapter != null && adapter.isBoilerRecipe(node)) {
            com.gtceu.calcboard.api.type.GTBoilerTier curTier = com.gtceu.calcboard.api.type.GTBoilerTier.getBoilerTier(node);
            com.gtceu.calcboard.api.type.GTBoilerTier[] vals = com.gtceu.calcboard.api.type.GTBoilerTier.values();
            int newIdx = (curTier.ordinal() + direction + vals.length) % vals.length;
            com.gtceu.calcboard.api.type.GTBoilerTier nextTier = vals[newIdx];
            node.setMachineIcon(nextTier.getDefaultIcon());
            node.setMultiblock(nextTier.isMultiblock());
            if (parent != null) parent.markSummaryDirty();
            invalidateCache();
            return true;
        }

        if (com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.isCombustionFamily(node)) {
            GTVoltageTier curTier = node.getTargetTier();
            if (curTier == null) {
                curTier = com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.getCombustionTierForMachine(node.getMachineIcon());
                if (curTier == null) curTier = GTVoltageTier.LV;
            }
            int curOrdinal = curTier.ordinal();
            int minOrdinal = com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.getMinCombustionTier().ordinal();
            int maxOrdinal = com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.getMaxCombustionTier().ordinal();
            int nextOrdinal = curOrdinal + direction;
            if (nextOrdinal < minOrdinal || nextOrdinal > maxOrdinal || nextOrdinal == curOrdinal) {
                return false;
            }
            GTVoltageTier nextTier = GTVoltageTier.getByIndex(nextOrdinal);
            GTVoltageTier oldTier = node.getTargetTier();
            boolean ok = com.gtceu.calcboard.compat.gtceu.helper.GTCombustionHelper.syncCombustionMachine(node, nextTier);
            if (ok) {
                syncSharedFrameHardware(node);
                if (parent != null) {
                    parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, nextTier));
                    parent.markSummaryDirty();
                }
                invalidateCache();
                return true;
            }
            return false;
        }

        boolean isVanillaCooking = node.getRecipeCategoryId() != null && com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.VANILLA_COOKING_RECIPE_TYPES.contains(node.getRecipeCategoryId());
        boolean isPassiveOrSteam = (node.getSteamMode() != null && node.getSteamMode().isSteam()) || node.getEnergyType() == com.gtceu.calcboard.api.type.EnergyType.NONE;

        int minIdx = node.getRecipeTier() != null ? node.getRecipeTier().ordinal() : GTVoltageTier.ULV.ordinal();
        if (adapter != null && !isVanillaCooking && !isPassiveOrSteam) {
            GTVoltageTier minWsTier = adapter.getMinimumWorkstationTier(node);
            if (minWsTier != null) {
                minIdx = Math.max(minIdx, minWsTier.ordinal());
            }
        }
        int maxIdx = GTVoltageTier.values().length - 1;
        if (node.isTurbine()) {
            if (node.isMultiblock()) {
                GTVoltageTier baseTier = com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getTurbineBaseTier(node);
                if (baseTier != null) {
                    minIdx = Math.max(minIdx, baseTier.ordinal());
                }
            } else {
                maxIdx = GTVoltageTier.HV.ordinal();
            }
        }

        if (!node.isMultiblock() && node.supportsSteamMode()) {
            SteamMode curSteam = node.getSteamMode();
            if (curSteam == SteamMode.LOW_PRESSURE) {
                if (direction > 0) {
                    node.setSteamMode(SteamMode.HIGH_PRESSURE);
                    if (parent != null) parent.markSummaryDirty();
                    invalidateCache();
                    return true;
                } else if (direction < 0) {
                    if (isVanillaCooking) {
                        node.setSteamMode(SteamMode.NONE);
                        node.setMachineIcon(ResourceLocation.tryParse("minecraft:furnace"));
                        syncSharedFrameHardware(node);
                        if (parent != null) parent.markSummaryDirty();
                        invalidateCache();
                        return true;
                    }
                    return false; // LP Steam is the absolute lowest tier for standard steam machines
                }
            } else if (curSteam == SteamMode.HIGH_PRESSURE) {
                if (direction > 0) {
                    node.setSteamMode(SteamMode.NONE);
                    GTVoltageTier lowestElectric = (minIdx == GTVoltageTier.ULV.ordinal() && !isVanillaCooking) ? GTVoltageTier.ULV : GTVoltageTier.LV;
                    node.setTargetTier(lowestElectric);
                    ResourceLocation sbWs = node.getWorkstationForTier(lowestElectric);
                    if (sbWs != null) {
                        node.setMachineIcon(sbWs);
                    } else if (isVanillaCooking) {
                        node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_electric_furnace"));
                    }
                    syncSharedFrameHardware(node);
                    if (parent != null) parent.markSummaryDirty();
                    invalidateCache();
                    return true;
                } else if (direction < 0) {
                    node.setSteamMode(SteamMode.LOW_PRESSURE);
                    syncSharedFrameHardware(node);
                    if (parent != null) parent.markSummaryDirty();
                    invalidateCache();
                    return true;
                }
            } else {
                if (node.getEnergyType() == com.gtceu.calcboard.api.type.EnergyType.NONE) {
                    if (direction > 0) {
                        if (node.supportsSteamMode()) {
                            node.setSteamMode(SteamMode.LOW_PRESSURE);
                        } else {
                            GTVoltageTier lowestElectric = (minIdx == GTVoltageTier.ULV.ordinal() && !isVanillaCooking) ? GTVoltageTier.ULV : GTVoltageTier.LV;
                            node.setTargetTier(lowestElectric);
                            ResourceLocation sbWs = node.getWorkstationForTier(lowestElectric);
                            if (sbWs != null) {
                                node.setMachineIcon(sbWs);
                            } else if (isVanillaCooking) {
                                node.setMachineIcon(ResourceLocation.tryParse("gtceu:lv_electric_furnace"));
                            }
                        }
                        syncSharedFrameHardware(node);
                        if (parent != null) parent.markSummaryDirty();
                        invalidateCache();
                        return true;
                    }
                    return false;
                }

                int curIdx = node.getTargetTier() != null ? node.getTargetTier().ordinal() : GTVoltageTier.LV.ordinal();
                int lowestAllowedElectric = (minIdx == GTVoltageTier.ULV.ordinal() && !isVanillaCooking) ? GTVoltageTier.ULV.ordinal() : GTVoltageTier.LV.ordinal();
                if (direction < 0) {
                    if (curIdx <= lowestAllowedElectric) {
                        if (node.supportsSteamMode()) {
                            node.setSteamMode(SteamMode.HIGH_PRESSURE);
                            syncSharedFrameHardware(node);
                            if (parent != null) parent.markSummaryDirty();
                            invalidateCache();
                            return true;
                        } else if (isVanillaCooking) {
                            node.setMachineIcon(ResourceLocation.tryParse("minecraft:furnace"));
                            syncSharedFrameHardware(node);
                            if (parent != null) parent.markSummaryDirty();
                            invalidateCache();
                            return true;
                        }
                        return false;
                    }
                }
            }
        } else if (node.getEnergyType() == com.gtceu.calcboard.api.type.EnergyType.NONE) {
            if (direction > 0) {
                GTVoltageTier lowestElectric = (minIdx == GTVoltageTier.ULV.ordinal() && !isVanillaCooking) ? GTVoltageTier.ULV : GTVoltageTier.LV;
                node.setTargetTier(lowestElectric);
                ResourceLocation sbWs = node.getWorkstationForTier(lowestElectric);
                if (sbWs != null) {
                    node.setMachineIcon(sbWs);
                }
                syncSharedFrameHardware(node);
                if (parent != null) parent.markSummaryDirty();
                invalidateCache();
                return true;
            }
            return false;
        }

        int curIdx = node.isLargeTurbine()
                ? com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.getRotorHolderTier(node).ordinal()
                : (node.getTargetTier() != null ? node.getTargetTier().ordinal() : GTVoltageTier.LV.ordinal());
        int newIdx = curIdx + direction;

        if (node.isTurbine() && !node.isMultiblock() && newIdx > GTVoltageTier.HV.ordinal()) {
            if (node.hasMultiblockOption()) {
                GTVoltageTier oldTier = node.getTargetTier();
                GTVoltageTier newTier = GTVoltageTier.getByIndex(newIdx);
                node.setMultiblock(true);
                node.setTargetTier(newTier);
                com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.setRotorHolderTier(node, newTier);
                com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.syncTurbineMachineIcon(node);
                syncSharedFrameHardware(node);
                if (parent != null) {
                    parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, newTier));
                    parent.markSummaryDirty();
                }
                invalidateCache();
                return true;
            } else {
                return false;
            }
        } else if (newIdx < minIdx || newIdx > maxIdx || newIdx == curIdx) {
            return false;
        }

        GTVoltageTier oldTier = node.getTargetTier();
        GTVoltageTier newTier = GTVoltageTier.getByIndex(newIdx);

        node.setTargetTier(newTier);
        if (node.isLargeTurbine()) {
            com.gtceu.calcboard.compat.gtceu.GTTurbineHelper.setRotorHolderTier(node, newTier);
        }
        if (!node.isMultiblock()) {
            ResourceLocation sbWs = node.getWorkstationForTier(newTier);
            if (sbWs != null) {
                node.setMachineIcon(sbWs);
            }
        }
        com.gtceu.calcboard.compat.gtceu.GTCEuModAdapter.syncTurbineMachineIcon(node);
        syncSharedFrameHardware(node);
        if (parent != null) {
            parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.targetTier(node.getId(), oldTier, newTier));
            parent.markSummaryDirty();
        }
        invalidateCache();
        return true;
    }

    private void syncSharedFrameHardware(RecipeNode node) {
        if (parent != null && parent.getGraph() != null) {
            var frame = parent.getGraph().findFrameEnclosingNode(node);
            if (frame != null && frame.isSharedMachineFrame()) {
                frame.syncHardwareConfig(node, parent.getGraph());
                parent.rebuildWidgets();
            }
        }
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        var bounds = getLayoutBounds();
        int inIdx = bounds.getHoveredInputPortIndex(mouseX, mouseY);
        if (inIdx >= 0 && inIdx < node.getInputs().size()) {
            return handleInputPortScroll(inIdx, delta);
        }

        if (bounds.hasRow2Controls()) {
            var handler = com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry.getHandlerForNode(node);
            if (handler.handleControlScroll(this, node, mouseX, mouseY, delta)) {
                return true;
            }
        }
        return false;
    }

    private boolean handleInputPortScroll(int inIdx, double delta) {
        if (parent != null && !parent.ensureEditPermission()) return true;
        IngredientStack in = node.getInputs().get(inIdx);
        if (in.isFluid() && com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.isDynamoToBoilerConvertible(node)) {
            var allFluids = com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.getAllBoilingFluidInputs();
            if (in.getAlternatives().size() != allFluids.size() || in.getAlternatives().stream().anyMatch(id -> !allFluids.contains(id))) {
                in.setAlternatives(allFluids);
            }
        }
        if (!in.hasAlternatives()) {
            return false;
        }

        net.minecraft.resources.ResourceLocation oldAlt = in.getId();
        in.cycleAlternative(delta > 0 ? -1 : 1);
        if (in.isFluid() && com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.isDynamoToBoilerConvertible(node)) {
            com.gtceu.calcboard.compat.systeams.SysteamsRecipeHandler.updateBoilerFluidRecipe(node, in.getId());
        }
        invalidateCache();
        if (parent != null) {
            net.minecraft.resources.ResourceLocation newAlt = in.getId();
            if (oldAlt != null && !oldAlt.equals(newAlt)) {
                parent.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.SelectAlternativeCommand(
                        node.getId(), inIdx, true, oldAlt, newAlt));
            }
            parent.getGraph().cleanupInvalidConnections();
            parent.markSummaryDirty();
        }
        playUiSound(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK.get(), 1.4F);
        return true;
    }

    private void playUiSound(net.minecraft.sounds.SoundEvent sound, float pitch) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.getSoundManager() != null && sound != null) {
                mc.getSoundManager().play(
                        net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, pitch)
                );
            }
        } catch (Throwable ignored) {}
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();

        if (hiddenPortsPopup.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (!isPointInside(mouseX, mouseY)) {
            commitCountEdit();
            return false;
        }

        if (button == 1) {
            // Right click on input or output port -> Hide and disconnect wires
            int inPort = getHoveredInputPortIndex(mouseX, mouseY);
            if (inPort >= 0) {
                hidePortAndDisconnectWires(true, inPort);
                return true;
            }
            int outPort = getHoveredOutputPortIndex(mouseX, mouseY);
            if (outPort >= 0) {
                if (isVoidToggleModifier()) {
                    toggleOutputPortVoid(outPort);
                    return true;
                }
                hidePortAndDisconnectWires(false, outPort);
                return true;
            }
        }

        if (button == 0) {
            // Left click on Hidden Ports badge
            if (isHiddenPortsBadgeHovered(mouseX, mouseY)) {
                hiddenPortsPopup.toggle();
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.1F)
                );
                return true;
            }
        }

        if (node.isReroute()) {
            return handleRerouteClick(mouseX, mouseY, button);
        }

        // Machine Icon Click -> Open Machine / Controller Selector Dialog
        if (button == 0 && isMachineIconHovered(mouseX, mouseY)) {
            if (parent != null) {
                parent.openMachineSelectorDialog(node);
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.1F)
                );
                return true;
            }
        }

        // Switch Recipe Button [⟲]
        if (isSwitchButtonHovered(mouseX, mouseY)) {
            if (parent != null) {
                parent.openRecipeSwitchDialog(node);
                Minecraft.getInstance().getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.1F)
                );
            }
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
            List<com.gtceu.calcboard.api.model.CanvasGroupFrame> subFrames = node.getSubGraph() != null ? new ArrayList<>(node.getSubGraph().getFrames()) : Collections.emptyList();
            List<com.gtceu.calcboard.api.model.CanvasStickyNote> subNotes = node.getSubGraph() != null ? new ArrayList<>(node.getSubGraph().getStickyNotes()) : Collections.emptyList();

            boolean expanded = parent.getGraph().expandModule(node);
            if (expanded) {
                parent.recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.ExpandModuleCommand(node, subNodes, subEdges, moduleEdges, subFrames, subNotes));
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

        // Target Base Node Toggle Button [⌖]
        if (isTargetButtonHovered(mouseX, mouseY)) {
            boolean nowBase = !node.isBaseNode();
            parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.baseAnchor(node.getId(), !nowBase, nowBase));
            parent.getGraph().setBaseNode(nowBase ? node : null);
            parent.rebuildWidgets();
            parent.markSummaryDirty();

            Minecraft mc = Minecraft.getInstance();
            if (nowBase) {
                BoardToast.show(Component.literal("§6⌖ ").append(Component.translatable("message.gtcalcboard.base_set", node.getName())));
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.2F));
            } else {
                BoardToast.show(Component.literal("§7").append(Component.translatable("message.gtcalcboard.base_cleared")));
            }
            return true;
        }

        var bounds = getLayoutBounds();

        // Machine Count Decrement [-]
        if (bounds.getCountMinusBtnBounds().contains(mouseX, mouseY)) {
            commitCountEdit();
            double oldVal = node.getMachineCount();
            double step = net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 0.1 : (oldVal <= 1.0 ? 0.05 : 1.0);
            double newVal = Math.max(0.01, Math.round((oldVal - step) * 1000.0) / 1000.0);
            if (oldVal != newVal) {
                node.setMachineCount(newVal);
                parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.machineCount(node.getId(), oldVal, newVal));
                if (node.isCompoundNode()) {
                    parent.getGraph().syncCompoundParameters(node);
                    parent.rebuildWidgets();
                    parent.markSummaryDirty();
                }
            }
            updateCountBuffer();
            invalidateCache();
            return true;
        }

        // If NameEditor is already editing and clicked inside header
        if (button == 0 && nameEditor.isEditing() && isHeaderHovered(mouseX, mouseY)) {
            var mc = Minecraft.getInstance();
            var font = mc != null ? mc.font : null;
            if (font != null) {
                int titleX = x + (node.getMachineIcon() != null ? 22 : 6);
                nameEditor.onClick(font, mouseX, titleX + 2, net.minecraft.client.gui.screens.Screen.hasShiftDown());
                return true;
            }
        }

        // Count Input Box Click
        if (bounds.getCountBoxBounds().contains(mouseX, mouseY)) {
            if (!countEditor.isEditing()) {
                countEditor.startEditing();
            } else {
                var mc = Minecraft.getInstance();
                if (mc != null && mc.font != null) {
                    countEditor.onClick(mc.font, mouseX, bounds.getCountBoxBounds().x() + 2, net.minecraft.client.gui.screens.Screen.hasShiftDown());
                }
            }
            return true;
        }

        // Machine Count Increment [+]
        if (bounds.getCountPlusBtnBounds().contains(mouseX, mouseY)) {
            commitCountEdit();
            double oldVal = node.getMachineCount();
            double step = net.minecraft.client.gui.screens.Screen.hasShiftDown() ? 0.1 : (oldVal < 1.0 ? 0.05 : 1.0);
            double newVal = Math.round((oldVal + step) * 1000.0) / 1000.0;
            node.setMachineCount(newVal);
            parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.machineCount(node.getId(), oldVal, newVal));
            if (node.isCompoundNode()) {
                parent.getGraph().syncCompoundParameters(node);
                parent.rebuildWidgets();
                parent.markSummaryDirty();
            }
            updateCountBuffer();
            invalidateCache();
            return true;
        }

        // Machine Count Half [/2]
        if (bounds.getCountHalfBtnBounds().contains(mouseX, mouseY)) {
            commitCountEdit();
            double oldVal = node.getMachineCount();
            double newVal = Math.max(0.01, Math.round((oldVal / 2.0) * 1000.0) / 1000.0);
            if (oldVal != newVal) {
                node.setMachineCount(newVal);
                parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.machineCount(node.getId(), oldVal, newVal));
                if (node.isCompoundNode()) {
                    parent.getGraph().syncCompoundParameters(node);
                    parent.rebuildWidgets();
                    parent.markSummaryDirty();
                }
            }
            updateCountBuffer();
            invalidateCache();
            return true;
        }

        // Machine Count Double [x2]
        if (bounds.getCountDoubleBtnBounds().contains(mouseX, mouseY)) {
            commitCountEdit();
            double oldVal = node.getMachineCount();
            double newVal = Math.round((oldVal * 2.0) * 1000.0) / 1000.0;
            node.setMachineCount(newVal);
            parent.recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.machineCount(node.getId(), oldVal, newVal));
            if (node.isCompoundNode()) {
                parent.getGraph().syncCompoundParameters(node);
                parent.rebuildWidgets();
                parent.markSummaryDirty();
            }
            updateCountBuffer();
            invalidateCache();
            return true;
        }

        // Delegate row 2 control clicks (Tier/Speed, OC Mode, Machine Config) to IModGuiHandler
        if (bounds.hasRow2Controls()) {
            var handler = com.gtceu.calcboard.client.gui.compat.ModGuiHandlerRegistry.getHandlerForNode(node);
            if (handler.handleControlClick(this, node, mouseX, mouseY, button)) {
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

    private boolean handleRerouteClick(double mouseX, double mouseY, int button) {
        if (getHoveredInputPortIndex(mouseX, mouseY) >= 0 || getHoveredOutputPortIndex(mouseX, mouseY) >= 0) {
            return false;
        }
        if (button == 0 && isTargetBatchBadgeHovered(mouseX, mouseY)) {
            commitCountEdit();
            targetBatchEditor.startEditing();
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.1F)
            );
            return true;
        }
        if (button == 1 && net.minecraft.client.gui.screens.Screen.hasShiftDown() && parent != null) {
            parent.openJunctionSupplyDialog(node);
            Minecraft.getInstance().getSoundManager().play(
                net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1.1F)
            );
            return true;
        }
        return false;
    }
}




