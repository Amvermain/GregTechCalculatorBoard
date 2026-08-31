package com.gtceu.calcboard.client.gui.editor;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.tutorial.TutorialManager;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import org.lwjgl.glfw.GLFW;

/**
 * Handles rich inline text editing for node, group, and module names using InlineTextEditor.
 */
public class NodeNameEditor {

    private final NodeWidget widget;
    private final RecipeNode node;
    private final InlineTextEditor editor;

    public NodeNameEditor(NodeWidget widget, RecipeNode node) {
        this.widget = widget;
        this.node = node;
        this.editor = new InlineTextEditor(48);
    }

    public boolean isEditing() {
        return editor.isEditing();
    }

    public InlineTextEditor getEditor() {
        return editor;
    }

    public void startEditing() {
        editor.startEditing(node.getName());
    }

    public void commitEdit() {
        if (!editor.isEditing()) return;
        String oldName = node.getName();
        String text = editor.getText().trim();
        editor.stopEditing();

        if (!text.isEmpty() && !text.equals(oldName)) {
            node.setName(text);
            if (widget != null && widget.getParent() != null) {
                widget.getParent().recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.customName(
                    node.getId(),
                    oldName,
                    text
                ));
            }
            TutorialManager.getInstance().onNodeRenamed();
        }
    }

    public void cancelEdit() {
        editor.stopEditing();
    }

    public String getDisplayText() {
        if (editor.isEditing()) {
            String txt = editor.getText();
            int cursor = editor.getCursorPos();
            boolean blink = (System.currentTimeMillis() % 1000) < 500;
            if (blink && !editor.hasSelection()) {
                if (cursor >= txt.length()) {
                    return txt + "_";
                }
                return txt.substring(0, cursor) + "|" + txt.substring(cursor);
            }
            return txt;
        }
        return node.getName();
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return editor.charTyped(codePoint, modifiers);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editor.isEditing()) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commitEdit();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancelEdit();
            return true;
        }

        return editor.keyPressed(keyCode, scanCode, modifiers);
    }

    public void onClick(net.minecraft.client.gui.Font font, double mouseX, double textStartX, boolean shiftDown) {
        editor.onClick(font, mouseX - textStartX, shiftDown);
    }

    public void onDrag(net.minecraft.client.gui.Font font, double mouseX, double textStartX) {
        editor.onDrag(font, mouseX - textStartX);
    }
}
