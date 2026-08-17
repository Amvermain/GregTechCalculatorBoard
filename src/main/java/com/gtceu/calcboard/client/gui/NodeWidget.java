package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.GTVoltageTier;
import com.gtceu.calcboard.api.IngredientStack;
import com.gtceu.calcboard.api.OverclockMode;
import com.gtceu.calcboard.api.RecipeNode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.Map;

/**
 * Interactive card widget representing a single GregTech recipe machine node on the canvas.
 */
public class NodeWidget {
    public static final int WIDTH = 230;
    public static final int HEADER_HEIGHT = 20;

    private final RecipeNode node;
    private final BoardScreen parent;
    private final NodeCountEditor countEditor;

    // Cached rates for 144+ FPS performance
    private Map<IngredientStack, Double> cachedInputRates = null;
    private Map<IngredientStack, Double> cachedOutputRates = null;

    public NodeWidget(RecipeNode node, BoardScreen parent) {
        this.node = node;
        this.parent = parent;
        this.countEditor = new NodeCountEditor(this);
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
    }

    public void commitCountEdit() {
        countEditor.commit();
    }

    public RecipeNode getNode() {
        return node;
    }

    public NodeCountEditor getCountEditor() {
        return countEditor;
    }

    public int getHeight() {
        int maxRows = Math.max(node.getInputs().size(), node.getOutputs().size());
        return HEADER_HEIGHT + 68 + Math.max(1, maxRows) * 18 + 10;
    }

    public float getOutputPortX(int outputIndex) {
        return (float) (node.getPosX() + WIDTH - 6);
    }

    public float getOutputPortY(int outputIndex) {
        int contentY = (int) node.getPosY() + HEADER_HEIGHT + 6 + 18 + 18 + 14 + 4;
        return (float) (contentY + outputIndex * 18 + 8);
    }

    public float getInputPortX(int inputIndex) {
        return (float) (node.getPosX() + 6);
    }

    public float getInputPortY(int inputIndex) {
        int contentY = (int) node.getPosY() + HEADER_HEIGHT + 6 + 18 + 18 + 14 + 4;
        return (float) (contentY + inputIndex * 18 + 8);
    }

    public boolean isHeaderHovered(double canvasMouseX, double canvasMouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        return canvasMouseX >= x && canvasMouseX <= x + WIDTH - 40 && canvasMouseY >= y && canvasMouseY <= y + HEADER_HEIGHT;
    }

    public boolean isTargetButtonHovered(double canvasMouseX, double canvasMouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int targetX = x + WIDTH - 36;
        return canvasMouseX >= targetX && canvasMouseX <= targetX + 18 && canvasMouseY >= y + 2 && canvasMouseY <= y + 18;
    }

    public boolean isCloseButtonHovered(double canvasMouseX, double canvasMouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int closeX = x + WIDTH - 18;
        return canvasMouseX >= closeX && canvasMouseX <= closeX + 16 && canvasMouseY >= y + 2 && canvasMouseY <= y + 18;
    }

    public boolean isPointInside(double canvasMouseX, double canvasMouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        return canvasMouseX >= x && canvasMouseX <= x + WIDTH && canvasMouseY >= y && canvasMouseY <= y + getHeight();
    }

    public int getHoveredInputPortIndex(double canvasMouseX, double canvasMouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int contentY = y + HEADER_HEIGHT + 6 + 18 + 18 + 14 + 4;

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
        int y = (int) node.getPosY();
        int contentY = y + HEADER_HEIGHT + 6 + 18 + 18 + 14 + 4;

        for (int i = 0; i < node.getOutputs().size(); i++) {
            int rowY = contentY + i * 18;
            if (canvasMouseX >= x + WIDTH - 40 && canvasMouseX <= x + WIDTH && canvasMouseY >= rowY - 3 && canvasMouseY <= rowY + 19) {
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

    public Map<IngredientStack, Double> getCachedInputRates() {
        if (cachedInputRates == null) {
            cachedInputRates = node.calculateInputRates();
        }
        return cachedInputRates;
    }

    public Map<IngredientStack, Double> getCachedOutputRates() {
        if (cachedOutputRates == null) {
            cachedOutputRates = node.calculateOutputRates();
        }
        return cachedOutputRates;
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        NodeCardRenderer.render(this, graphics, mouseX, mouseY, partialTicks);
    }

    public boolean isTierButtonHovered(double mouseX, double mouseY) {
        int x = (int) node.getPosX();
        int y = (int) node.getPosY();
        int ctrlY = y + HEADER_HEIGHT + 6;
        return mouseX >= x + 154 && mouseX <= x + 188 && mouseY >= ctrlY && mouseY <= ctrlY + 14;
    }

    public boolean changeTier(int direction) {
        int curIdx = node.getTargetTier().ordinal();
        int minIdx = node.getRecipeTier().ordinal();
        int maxIdx = GTVoltageTier.values().length - 1;

        int newIdx = curIdx + direction;
        if (newIdx < minIdx || newIdx > maxIdx || newIdx == curIdx) {
            return false;
        }

        double speedRatio = node.getOverclockMode().getSpeedFactor();
        if (direction > 0) {
            node.setMachineCount(Math.max(0.01, node.getMachineCount() / speedRatio));
        } else {
            node.setMachineCount(node.getMachineCount() * speedRatio);
        }

        node.setTargetTier(GTVoltageTier.getByIndex(newIdx));
        updateCountBuffer();
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (isTierButtonHovered(mouseX, mouseY)) {
            return changeTier(delta > 0 ? +1 : -1);
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

        // Close Button [X]
        if (isCloseButtonHovered(mouseX, mouseY)) {
            parent.removeNode(this);
            return true;
        }

        // Target Base Node Toggle Button [🎯]
        if (isTargetButtonHovered(mouseX, mouseY)) {
            boolean nowBase = !node.isBaseNode();
            parent.getGraph().setBaseNode(nowBase ? node : null);
            parent.rebuildWidgets();
            parent.markSummaryDirty();

            Minecraft mc = Minecraft.getInstance();
            if (nowBase) {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§6🎯 ").append(Component.translatable("message.gtcalcboard.base_set", node.getName())), true);
                }
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.PLAYER_LEVELUP, 1.2F));
                parent.performAutoRatio();
            } else {
                if (mc.player != null) {
                    mc.player.displayClientMessage(Component.literal("§7").append(Component.translatable("message.gtcalcboard.base_cleared")), true);
                }
            }
            return true;
        }

        int ctrlY = y + HEADER_HEIGHT + 6;

        // Numeric Count Box Click
        int countBoxX = x + 60;
        int countBoxW = 36;
        if (mouseX >= countBoxX && mouseX <= countBoxX + countBoxW && mouseY >= ctrlY && mouseY <= ctrlY + 14) {
            countEditor.startEditing();
            return true;
        } else {
            commitCountEdit();
        }

        // [-]
        if (mouseX >= x + 44 && mouseX <= x + 58 && mouseY >= ctrlY && mouseY <= ctrlY + 14) {
            node.setMachineCount(Math.max(0.1, node.getMachineCount() - 1.0));
            updateCountBuffer();
            return true;
        }
        // [+]
        if (mouseX >= x + 98 && mouseX <= x + 112 && mouseY >= ctrlY && mouseY <= ctrlY + 14) {
            node.setMachineCount(node.getMachineCount() + 1.0);
            updateCountBuffer();
            return true;
        }
        // [/2]
        if (mouseX >= x + 115 && mouseX <= x + 131 && mouseY >= ctrlY && mouseY <= ctrlY + 14) {
            node.setMachineCount(Math.max(0.05, node.getMachineCount() / 2.0));
            updateCountBuffer();
            return true;
        }
        // [x2]
        if (mouseX >= x + 133 && mouseX <= x + 149 && mouseY >= ctrlY && mouseY <= ctrlY + 14) {
            node.setMachineCount(node.getMachineCount() * 2.0);
            updateCountBuffer();
            return true;
        }

        // [Tier Button] Left Click: Tier Up / Right Click: Tier Down
        if (isTierButtonHovered(mouseX, mouseY)) {
            return changeTier(button == 0 ? 1 : -1);
        }

        // Second Row: [STD OC / PERF OC] toggle & [Par: 1x, 2x, 4x, 8x...]
        int row2Y = ctrlY + 18;
        if (mouseX >= x + 6 && mouseX <= x + 60 && mouseY >= row2Y && mouseY <= row2Y + 14) {
            OverclockMode nextMode = node.getOverclockMode() == OverclockMode.STANDARD ? OverclockMode.PERFECT : OverclockMode.STANDARD;
            node.setOverclockMode(nextMode);
            invalidateCache();
            return true;
        }

        // [Parallel Selector]
        if (mouseX >= x + 64 && mouseX <= x + 112 && mouseY >= row2Y && mouseY <= row2Y + 14) {
            int curPar = node.getParallel();
            int nextPar = (button == 0) ? (curPar >= 64 ? 1 : curPar * 2) : (curPar <= 1 ? 64 : curPar / 2);
            node.setParallel(nextPar);
            invalidateCache();
            return true;
        }

        return false;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return countEditor.charTyped(codePoint, modifiers);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        return countEditor.keyPressed(keyCode, scanCode, modifiers);
    }
}
