package com.gtceu.calcboard.client.gui.editor;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.client.gui.util.FormatUtil;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import org.lwjgl.glfw.GLFW;

/**
 * Handles inline text editing for target batch goal quantity on reroute and goal nodes using InlineTextEditor.
 */
public class NodeTargetBatchEditor {

    private final NodeWidget widget;
    private final InlineTextEditor editor;

    public NodeTargetBatchEditor(NodeWidget widget) {
        this.widget = widget;
        this.editor = new InlineTextEditor(24, c -> Character.isDigit(c) || c == '.' || c == ' ' || Character.isLetter(c));
    }

    public boolean isEditing() {
        return editor.isEditing();
    }

    public InlineTextEditor getEditor() {
        return editor;
    }

    public void startEditing() {
        RecipeNode node = widget.getNode();
        editor.startEditing(FormatUtil.formatEditAmount(node.getTargetBatchAmount(), isFluid()));
    }

    public void commit() {
        if (!editor.isEditing()) return;
        RecipeNode node = widget.getNode();
        double oldVal = node.getTargetBatchAmount();
        String text = editor.getText().trim();
        editor.stopEditing();

        try {
            double parsed = FormatUtil.parseBatchAmount(text, isFluid());
            if (parsed >= 0 && Math.abs(parsed - oldVal) > 0.0001) {
                node.setTargetBatchAmount(parsed);
                if (widget.getParent() != null) {
                    widget.getParent().markSummaryDirty();
                }
                com.gtceu.calcboard.client.gui.tutorial.TutorialManager.getInstance().onTargetBatchConfigured(node, parsed);
            }
        } catch (Exception ignored) {}

        widget.invalidateCache();
    }

    public void cancel() {
        editor.stopEditing();
    }

    public void updateBuffer() {
        widget.invalidateCache();
    }

    private boolean isFluid() {
        RecipeNode node = widget.getNode();
        return !node.getInputs().isEmpty() && node.getInputs().get(0).isFluid();
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
        double amount = widget.getNode().getTargetBatchAmount();
        if (amount <= 0) return "";
        return FormatUtil.formatBatchAmount(amount, isFluid());
    }

    public boolean charTyped(char codePoint, int modifiers) {
        return editor.charTyped(codePoint, modifiers);
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!editor.isEditing()) return false;

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            commit();
            return true;
        } else if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            cancel();
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

    public static String formatAmount(double amount) {
        return FormatUtil.formatCleanNumber(amount);
    }
}
