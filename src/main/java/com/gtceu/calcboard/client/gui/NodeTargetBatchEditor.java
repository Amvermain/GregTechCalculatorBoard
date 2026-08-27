package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.RecipeNode;
import org.lwjgl.glfw.GLFW;

/**
 * Handles inline text editing for target batch goal quantity on reroute and goal nodes.
 */
public class NodeTargetBatchEditor {

    private final NodeWidget widget;
    private boolean isEditing = false;
    private String buffer = "";

    public NodeTargetBatchEditor(NodeWidget widget) {
        this.widget = widget;
        this.buffer = FormatUtil.formatEditAmount(widget.getNode().getTargetBatchAmount(), isFluid());
    }

    public boolean isEditing() {
        return isEditing;
    }

    public void startEditing() {
        this.isEditing = true;
        RecipeNode node = widget.getNode();
        this.buffer = FormatUtil.formatEditAmount(node.getTargetBatchAmount(), isFluid());
    }

    public void commit() {
        if (!isEditing) return;
        isEditing = false;
        RecipeNode node = widget.getNode();
        double oldVal = node.getTargetBatchAmount();
        try {
            double parsed = FormatUtil.parseBatchAmount(buffer.trim(), isFluid());
            if (parsed >= 0 && Math.abs(parsed - oldVal) > 0.0001) {
                node.setTargetBatchAmount(parsed);
                if (widget.getParent() != null) {
                    widget.getParent().markSummaryDirty();
                }
            }
        } catch (Exception ignored) {}
        this.buffer = FormatUtil.formatEditAmount(node.getTargetBatchAmount(), isFluid());
        widget.invalidateCache();
    }

    public void cancel() {
        this.isEditing = false;
        this.buffer = FormatUtil.formatEditAmount(widget.getNode().getTargetBatchAmount(), isFluid());
    }

    public void updateBuffer() {
        if (!isEditing) {
            this.buffer = FormatUtil.formatEditAmount(widget.getNode().getTargetBatchAmount(), isFluid());
        }
        widget.invalidateCache();
    }

    private boolean isFluid() {
        RecipeNode node = widget.getNode();
        return !node.getInputs().isEmpty() && node.getInputs().get(0).isFluid();
    }

    public String getDisplayText() {
        if (isEditing) {
            String text = buffer;
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                text += "_";
            }
            return text;
        }
        double amount = widget.getNode().getTargetBatchAmount();
        if (amount <= 0) return "";
        return FormatUtil.formatBatchAmount(amount, isFluid());
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!isEditing) return false;
        if (Character.isDigit(codePoint) || codePoint == '.' || codePoint == ' ' || Character.isLetter(codePoint)) {
            if (buffer.length() < 16) {
                buffer += codePoint;
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isEditing) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (!buffer.isEmpty()) {
                buffer = buffer.substring(0, buffer.length() - 1);
            }
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_DELETE) {
            buffer = "";
            return true;
        }
        return true;
    }

    public static String formatAmount(double amount) {
        return FormatUtil.formatCleanNumber(amount);
    }
}
