package com.gtceu.calcboard.client.gui.editor;

import com.gtceu.calcboard.client.gui.widget.NodeWidget;

import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Handles inline text editing and interactive manipulation for machine parallel factor in a NodeWidget.
 */
public class NodeParallelEditor {
    private final NodeWidget widget;
    private boolean isEditing = false;
    private String buffer = "";

    public NodeParallelEditor(NodeWidget widget) {
        this.widget = widget;
        this.buffer = String.valueOf(widget.getNode().getParallel());
    }

    public boolean isEditing() {
        return isEditing;
    }

    public void startEditing() {
        this.isEditing = true;
        this.buffer = String.valueOf(widget.getNode().getParallel());
    }

    public void commit() {
        if (!isEditing) return;
        isEditing = false;
        int oldVal = widget.getNode().getParallel();
        try {
            int parsed = Integer.parseInt(buffer.trim());
            if (parsed >= 1) {
                int newVal = Math.min(65536, parsed);
                if (oldVal != newVal) {
                    widget.getNode().setParallel(newVal);
                    widget.getParent().recordCommand(com.gtceu.calcboard.api.history.BoardCommand.ModifyPropertyCommand.parallel(
                        widget.getNode().getId(),
                        oldVal,
                        newVal
                    ));
                }
            }
        } catch (NumberFormatException ignored) {
        }
        this.buffer = String.valueOf(widget.getNode().getParallel());
        widget.invalidateCache();
    }

    public void updateBuffer() {
        if (!isEditing) {
            this.buffer = String.valueOf(widget.getNode().getParallel());
        }
        widget.invalidateCache();
    }

    public String getDisplayText() {
        if (isEditing) {
            String text = buffer;
            if ((System.currentTimeMillis() / 500) % 2 == 0) {
                text += "_";
            }
            return text;
        }
        return Component.translatable("gui.gtcalcboard.parallel", String.valueOf(widget.getNode().getParallel())).getString();
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!isEditing) return false;
        if (Character.isDigit(codePoint)) {
            if (buffer.length() < 6) {
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
        this.buffer = String.valueOf(next);
        widget.invalidateCache();
    }
}


