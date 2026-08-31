package com.gtceu.calcboard.client.gui.editor;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import org.lwjgl.glfw.GLFW;

/**
 * Handles inline text editing for machine count inside a NodeWidget using InlineTextEditor.
 */
public class NodeCountEditor {

    private final NodeWidget widget;
    private final InlineTextEditor editor;

    public NodeCountEditor(NodeWidget widget) {
        this.widget = widget;
        this.editor = new InlineTextEditor(12, c -> Character.isDigit(c) || c == '.');
    }

    public boolean isEditing() {
        return editor.isEditing();
    }

    public InlineTextEditor getEditor() {
        return editor;
    }

    public void startEditing() {
        editor.startEditing(formatCount(widget.getNode().getMachineCount()));
    }

    public void commit() {
        if (!editor.isEditing()) return;
        double oldVal = widget.getNode().getMachineCount();
        String text = editor.getText().trim();
        editor.stopEditing();

        try {
            double parsed = Double.parseDouble(text);
            if (parsed > 0 && Math.abs(parsed - oldVal) > 0.0001) {
                widget.getNode().setMachineCount(parsed);
                if (widget.getParent() != null) {
                    widget.getParent().recordCommand(BoardCommand.ModifyPropertyCommand.machineCount(
                        widget.getNode().getId(),
                        oldVal,
                        parsed
                    ));
                    if (widget.getNode().isCompoundNode()) {
                        widget.getParent().getGraph().syncCompoundParameters(widget.getNode());
                        widget.getParent().rebuildWidgets();
                        widget.getParent().markSummaryDirty();
                    }
                }
            }
        } catch (NumberFormatException ignored) {}

        widget.invalidateCache();
    }

    public void updateBuffer() {
        if (!editor.isEditing()) {
            // No action needed when not editing
        }
        widget.invalidateCache();
    }

    public String getDisplayText() {
        if (editor.isEditing()) {
            String txt = editor.getText();
            int cursor = editor.getCursorPos();
            boolean blink = (System.currentTimeMillis() / 500) % 2 == 0;
            if (blink && !editor.hasSelection()) {
                if (cursor >= txt.length()) {
                    return txt + "_";
                }
                return txt.substring(0, cursor) + "|" + txt.substring(cursor);
            }
            return txt;
        }
        return formatCount(widget.getNode().getMachineCount());
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!editor.isEditing()) return false;
        if (codePoint == '.' && editor.getText().contains(".") && !editor.getSelectedText().contains(".")) {
            return false;
        }
        return editor.charTyped(codePoint, modifiers);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editor.isEditing()) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER || keyCode == GLFW.GLFW_KEY_ESCAPE) {
            commit();
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

    public static String formatCount(double count) {
        return String.format("%.2f", count).replaceAll("\\.?0+$", "");
    }
}
