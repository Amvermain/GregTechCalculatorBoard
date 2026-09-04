package com.gtceu.calcboard.client.gui.editor;

import com.gtceu.calcboard.api.history.BoardCommand;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Handles inline text editing and interactive manipulation for machine parallel factor using InlineTextEditor.
 */
public class NodeParallelEditor {

    private final NodeWidget widget;
    private final InlineTextEditor editor;

    public NodeParallelEditor(NodeWidget widget) {
        this.widget = widget;
        this.editor = new InlineTextEditor(8, Character::isDigit);
    }

    public boolean isEditing() {
        return editor.isEditing();
    }

    public InlineTextEditor getEditor() {
        return editor;
    }

    public void startEditing() {
        editor.startEditing(String.valueOf(widget.getNode().getParallel()));
    }

    public void commit() {
        if (!editor.isEditing()) return;
        int oldVal = widget.getNode().getParallel();
        String text = editor.getText().trim();
        editor.stopEditing();

        try {
            int parsed = Integer.parseInt(text);
            if (parsed >= 1) {
                int newVal = Math.min(65536, parsed);
                if (oldVal != newVal) {
                    widget.getNode().setParallel(newVal);
                    if (widget.getParent() != null) {
                        widget.getParent().recordCommand(BoardCommand.ModifyPropertyCommand.parallel(
                            widget.getNode().getId(),
                            oldVal,
                            newVal
                        ));
                    }
                }
            }
        } catch (NumberFormatException ignored) {}

        widget.invalidateCache();
    }

    public void updateBuffer() {
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
        return Component.translatable("gui.gtcalcboard.parallel", String.valueOf(widget.getNode().getParallel())).getString();
    }

    public boolean charTyped(char codePoint, int modifiers) {
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

    public void stepParallel(int direction, boolean fineStep) {
        int cur = widget.getNode().getParallel();
        int next;
        if (fineStep) {
            next = Math.max(1, Math.min(65536, cur + direction));
        } else {
            if (direction > 0) {
                next = Math.min(65536, cur * 2);
            } else {
                next = Math.max(1, cur / 2);
            }
        }
        widget.getNode().setParallel(next);
        widget.invalidateCache();
    }

    public void setMaxParallel() {
        var node = widget.getNode();
        if (node == null) return;
        var adapter = com.gtceu.calcboard.compat.ModAdapterRegistry.getAdapterForNode(node);
        int maxPar = adapter != null ? adapter.getMaxParallelCapacity(node) : 1;
        if (maxPar >= 1) {
            int oldVal = node.getParallel();
            if (oldVal != maxPar) {
                node.setParallel(maxPar);
                if (widget.getParent() != null) {
                    widget.getParent().recordCommand(BoardCommand.ModifyPropertyCommand.parallel(
                        node.getId(),
                        oldVal,
                        maxPar
                    ));
                    widget.getParent().markSummaryDirty();
                }
                widget.invalidateCache();
            }
        }
    }
}
