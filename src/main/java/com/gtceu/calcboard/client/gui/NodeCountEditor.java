package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.RecipeNode;
import org.lwjgl.glfw.GLFW;

/**
 * Handles inline text editing for machine count inside a NodeWidget.
 */
public class NodeCountEditor {
    private final NodeWidget widget;
    private boolean isEditing = false;
    private String buffer = "";

    public NodeCountEditor(NodeWidget widget) {
        this.widget = widget;
        this.buffer = formatCount(widget.getNode().getMachineCount());
    }

    public boolean isEditing() {
        return isEditing;
    }

    public void startEditing() {
        this.isEditing = true;
        this.buffer = formatCount(widget.getNode().getMachineCount());
    }

    public void commit() {
        if (!isEditing) return;
        isEditing = false;
        try {
            double parsed = Double.parseDouble(buffer.trim());
            if (parsed > 0) {
                widget.getNode().setMachineCount(parsed);
            }
        } catch (NumberFormatException ignored) {
        }
        this.buffer = formatCount(widget.getNode().getMachineCount());
        widget.invalidateCache();
    }

    public void updateBuffer() {
        if (!isEditing) {
            this.buffer = formatCount(widget.getNode().getMachineCount());
        }
        widget.invalidateCache();
    }

    public String getDisplayText() {
        String text = isEditing ? buffer : formatCount(widget.getNode().getMachineCount());
        if (isEditing && (System.currentTimeMillis() / 500) % 2 == 0) {
            text += "_";
        }
        return text;
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!isEditing) return false;
        if (Character.isDigit(codePoint) || (codePoint == '.' && !buffer.contains("."))) {
            if (buffer.length() < 7) {
                buffer += codePoint;
                return true;
            }
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isEditing) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            commit();
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

    public static String formatCount(double count) {
        return String.format("%.2f", count).replaceAll("\\.?0+$", "");
    }
}
