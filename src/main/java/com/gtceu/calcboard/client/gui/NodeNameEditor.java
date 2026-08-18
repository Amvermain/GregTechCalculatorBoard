package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.RecipeNode;
import org.lwjgl.glfw.GLFW;

public class NodeNameEditor {
    private final NodeWidget widget;
    private final RecipeNode node;
    private boolean isEditing = false;
    private StringBuilder buffer = new StringBuilder();

    public NodeNameEditor(NodeWidget widget, RecipeNode node) {
        this.widget = widget;
        this.node = node;
    }

    public boolean isEditing() {
        return isEditing;
    }

    public void startEditing() {
        isEditing = true;
        buffer.setLength(0);
        buffer.append(node.getName());
    }

    public void commitEdit() {
        if (!isEditing) return;
        isEditing = false;
        String oldName = node.getName();
        String text = buffer.toString().trim();
        if (!text.isEmpty() && !text.equals(oldName)) {
            node.setName(text);
            if (widget != null && widget.getParent() != null) {
                widget.getParent().recordCommand(new com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand(
                    node.getId(),
                    com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.Property.CUSTOM_NAME,
                    oldName,
                    text
                ));
            }
            com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onNodeRenamed();
        }
    }

    public void cancelEdit() {
        isEditing = false;
        buffer.setLength(0);
    }

    public String getDisplayText() {
        if (isEditing) {
            return buffer.toString() + (System.currentTimeMillis() % 1000 < 500 ? "_" : "");
        }
        return node.getName();
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!isEditing) return false;
        if (buffer.length() < 32 && codePoint >= 32) {
            buffer.append(codePoint);
            return true;
        }
        return false;
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isEditing) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (buffer.length() > 0) {
                buffer.deleteCharAt(buffer.length() - 1);
            }
            return true;
        }
        return false;
    }
}
