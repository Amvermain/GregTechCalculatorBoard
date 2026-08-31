package com.gtceu.calcboard.client.gui.editor;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

import java.util.function.Predicate;

/**
 * Reusable inline text editing engine supporting cursor positioning, selection range,
 * Home/End navigation, Shift-selection, Delete/Backspace, and Ctrl+A/C/V/X clipboard operations.
 */
public class InlineTextEditor {

    private final StringBuilder text = new StringBuilder();
    private int cursorPos = 0;
    private int highlightPos = 0;
    private int maxLength = 64;
    private boolean isEditing = false;
    private Predicate<Character> charFilter = c -> true;

    // Fallback clipboard buffer for headless test environments
    private static String fallbackClipboard = "";

    public InlineTextEditor() {}

    public InlineTextEditor(int maxLength) {
        this.maxLength = maxLength;
    }

    public InlineTextEditor(int maxLength, Predicate<Character> charFilter) {
        this.maxLength = maxLength;
        this.charFilter = charFilter != null ? charFilter : c -> true;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }

    public void setCharFilter(Predicate<Character> charFilter) {
        this.charFilter = charFilter != null ? charFilter : c -> true;
    }

    public boolean isEditing() {
        return isEditing;
    }

    public void startEditing(String initialText) {
        this.isEditing = true;
        this.text.setLength(0);
        if (initialText != null) {
            this.text.append(initialText);
        }
        this.cursorPos = this.text.length();
        this.highlightPos = this.cursorPos;
    }

    public void stopEditing() {
        this.isEditing = false;
    }

    public String getText() {
        return text.toString();
    }

    public void setText(String newText) {
        this.text.setLength(0);
        if (newText != null) {
            this.text.append(newText);
        }
        clampCursor();
    }

    public int getCursorPos() {
        return cursorPos;
    }

    public int getHighlightPos() {
        return highlightPos;
    }

    public int getSelectionStart() {
        return Math.min(cursorPos, highlightPos);
    }

    public int getSelectionEnd() {
        return Math.max(cursorPos, highlightPos);
    }

    public boolean hasSelection() {
        return cursorPos != highlightPos;
    }

    public String getSelectedText() {
        if (!hasSelection()) return "";
        return text.substring(getSelectionStart(), getSelectionEnd());
    }

    public void deleteSelection() {
        if (!hasSelection()) return;
        int start = getSelectionStart();
        int end = getSelectionEnd();
        text.delete(start, end);
        cursorPos = start;
        highlightPos = start;
    }

    public void insertText(String str) {
        if (str == null || str.isEmpty()) return;
        if (hasSelection()) {
            deleteSelection();
        }

        StringBuilder filtered = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            if (charFilter.test(c) && (text.length() + filtered.length()) < maxLength) {
                filtered.append(c);
            }
        }

        if (filtered.length() > 0) {
            text.insert(cursorPos, filtered.toString());
            cursorPos += filtered.length();
            highlightPos = cursorPos;
        }
    }

    public boolean charTyped(char codePoint, int modifiers) {
        if (!isEditing) return false;
        if (codePoint < 32 && codePoint != '\t') return false;
        if (!charFilter.test(codePoint)) return false;

        insertText(String.valueOf(codePoint));
        return true;
    }

    public int getCharIndexAt(net.minecraft.client.gui.Font font, double relativeX) {
        if (font == null || relativeX <= 0) return 0;
        String s = text.toString();
        int len = s.length();
        int accW = 0;
        for (int i = 0; i < len; i++) {
            int charW = font.width(String.valueOf(s.charAt(i)));
            if (relativeX < accW + charW / 2.0) {
                return i;
            }
            accW += charW;
        }
        return len;
    }

    public void onClick(net.minecraft.client.gui.Font font, double relativeX, boolean shiftDown) {
        int idx = getCharIndexAt(font, relativeX);
        cursorPos = idx;
        if (!shiftDown) {
            highlightPos = idx;
        }
    }

    public void onDrag(net.minecraft.client.gui.Font font, double relativeX) {
        cursorPos = getCharIndexAt(font, relativeX);
    }

    private static boolean isShiftDown(int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_SHIFT) != 0) return true;
        try {
            return net.minecraft.client.gui.screens.Screen.hasShiftDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean isControlDown(int modifiers) {
        if ((modifiers & GLFW.GLFW_MOD_CONTROL) != 0) return true;
        try {
            return net.minecraft.client.gui.screens.Screen.hasControlDown();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!isEditing) return false;

        boolean isCtrl = isControlDown(modifiers);
        boolean isShift = isShiftDown(modifiers);

        // Clipboard: Ctrl + A (Select All)
        if (isCtrl && keyCode == GLFW.GLFW_KEY_A) {
            selectAll();
            return true;
        }

        // Clipboard: Ctrl + C (Copy)
        if (isCtrl && keyCode == GLFW.GLFW_KEY_C) {
            if (hasSelection()) {
                setClipboard(getSelectedText());
            }
            return true;
        }

        // Clipboard: Ctrl + X (Cut)
        if (isCtrl && keyCode == GLFW.GLFW_KEY_X) {
            if (hasSelection()) {
                setClipboard(getSelectedText());
                deleteSelection();
            }
            return true;
        }

        // Clipboard: Ctrl + V (Paste)
        if (isCtrl && keyCode == GLFW.GLFW_KEY_V) {
            String clip = getClipboard();
            if (clip != null && !clip.isEmpty()) {
                insertText(clip);
            }
            return true;
        }

        // Navigation: Home
        if (keyCode == GLFW.GLFW_KEY_HOME) {
            cursorPos = 0;
            if (!isShift) {
                highlightPos = 0;
            }
            return true;
        }

        // Navigation: End
        if (keyCode == GLFW.GLFW_KEY_END) {
            cursorPos = text.length();
            if (!isShift) {
                highlightPos = text.length();
            }
            return true;
        }

        // Navigation: Left Arrow
        if (keyCode == GLFW.GLFW_KEY_LEFT) {
            if (isCtrl) {
                cursorPos = findPreviousWordBreak(cursorPos);
            } else {
                if (!isShift && hasSelection()) {
                    cursorPos = getSelectionStart();
                } else {
                    cursorPos = Math.max(0, cursorPos - 1);
                }
            }
            if (!isShift) {
                highlightPos = cursorPos;
            }
            return true;
        }

        // Navigation: Right Arrow
        if (keyCode == GLFW.GLFW_KEY_RIGHT) {
            if (isCtrl) {
                cursorPos = findNextWordBreak(cursorPos);
            } else {
                if (!isShift && hasSelection()) {
                    cursorPos = getSelectionEnd();
                } else {
                    cursorPos = Math.min(text.length(), cursorPos + 1);
                }
            }
            if (!isShift) {
                highlightPos = cursorPos;
            }
            return true;
        }

        // Deletion: Backspace
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            if (hasSelection()) {
                deleteSelection();
            } else if (cursorPos > 0) {
                if (isCtrl) {
                    int prevWord = findPreviousWordBreak(cursorPos);
                    text.delete(prevWord, cursorPos);
                    cursorPos = prevWord;
                    highlightPos = prevWord;
                } else {
                    text.deleteCharAt(cursorPos - 1);
                    cursorPos--;
                    highlightPos = cursorPos;
                }
            }
            return true;
        }

        // Deletion: Delete
        if (keyCode == GLFW.GLFW_KEY_DELETE) {
            if (hasSelection()) {
                deleteSelection();
            } else if (cursorPos < text.length()) {
                if (isCtrl) {
                    int nextWord = findNextWordBreak(cursorPos);
                    text.delete(cursorPos, nextWord);
                    highlightPos = cursorPos;
                } else {
                    text.deleteCharAt(cursorPos);
                    highlightPos = cursorPos;
                }
            }
            return true;
        }

        // Consume other keys to prevent leaking to global canvas shortcuts while editing
        return true;
    }

    public void selectAll() {
        this.highlightPos = 0;
        this.cursorPos = text.length();
    }

    private void clampCursor() {
        if (cursorPos < 0) cursorPos = 0;
        if (cursorPos > text.length()) cursorPos = text.length();
        if (highlightPos < 0) highlightPos = 0;
        if (highlightPos > text.length()) highlightPos = text.length();
    }

    private int findPreviousWordBreak(int from) {
        if (from <= 0) return 0;
        int idx = from - 1;
        while (idx > 0 && Character.isWhitespace(text.charAt(idx))) {
            idx--;
        }
        while (idx > 0 && !Character.isWhitespace(text.charAt(idx - 1))) {
            idx--;
        }
        return Math.max(0, idx);
    }

    private int findNextWordBreak(int from) {
        int len = text.length();
        if (from >= len) return len;
        int idx = from;
        while (idx < len && !Character.isWhitespace(text.charAt(idx))) {
            idx++;
        }
        while (idx < len && Character.isWhitespace(text.charAt(idx))) {
            idx++;
        }
        return Math.min(len, idx);
    }

    public static void setClipboard(String text) {
        if (text == null) return;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.keyboardHandler != null) {
                mc.keyboardHandler.setClipboard(text);
                return;
            }
        } catch (Throwable ignored) {}
        fallbackClipboard = text;
    }

    public static String getClipboard() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.keyboardHandler != null) {
                return mc.keyboardHandler.getClipboard();
            }
        } catch (Throwable ignored) {}
        return fallbackClipboard;
    }
}
