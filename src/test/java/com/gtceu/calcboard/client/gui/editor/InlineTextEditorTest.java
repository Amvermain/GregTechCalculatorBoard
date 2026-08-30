package com.gtceu.calcboard.client.gui.editor;

import com.gtceu.calcboard.testutil.MinecraftBootstrapExtension;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.lwjgl.glfw.GLFW;

@ExtendWith(MinecraftBootstrapExtension.class)
public class InlineTextEditorTest {

    private InlineTextEditor editor;

    @BeforeEach
    public void setUp() {
        editor = new InlineTextEditor(32);
        editor.startEditing("Sulfuric Acid");
    }

    @Test
    public void testInitialState() {
        Assertions.assertTrue(editor.isEditing());
        Assertions.assertEquals("Sulfuric Acid", editor.getText());
        Assertions.assertEquals(13, editor.getCursorPos());
        Assertions.assertEquals(13, editor.getHighlightPos());
        Assertions.assertFalse(editor.hasSelection());
    }

    @Test
    public void testHomeAndEndKeys() {
        // Press Home
        editor.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        Assertions.assertEquals(0, editor.getCursorPos());
        Assertions.assertEquals(0, editor.getHighlightPos());
        Assertions.assertFalse(editor.hasSelection());

        // Press End
        editor.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
        Assertions.assertEquals(13, editor.getCursorPos());
        Assertions.assertEquals(13, editor.getHighlightPos());
        Assertions.assertFalse(editor.hasSelection());

        // Press Shift + Home (Select from End to Beginning)
        editor.keyPressed(GLFW.GLFW_KEY_HOME, 0, GLFW.GLFW_MOD_SHIFT);
        Assertions.assertEquals(0, editor.getCursorPos());
        Assertions.assertEquals(13, editor.getHighlightPos());
        Assertions.assertTrue(editor.hasSelection());
        Assertions.assertEquals("Sulfuric Acid", editor.getSelectedText());

        // Press Shift + End (Select all backwards to end)
        editor.keyPressed(GLFW.GLFW_KEY_END, 0, GLFW.GLFW_MOD_SHIFT);
        Assertions.assertEquals(13, editor.getCursorPos());
        Assertions.assertEquals(13, editor.getHighlightPos());
        Assertions.assertFalse(editor.hasSelection());
    }

    @Test
    public void testArrowKeysAndShiftSelection() {
        // Press Left arrow 4 times (cursor from 13 to 9)
        for (int i = 0; i < 4; i++) {
            editor.keyPressed(GLFW.GLFW_KEY_LEFT, 0, 0);
        }
        Assertions.assertEquals(9, editor.getCursorPos());
        Assertions.assertFalse(editor.hasSelection());

        // Press Shift + Right 4 times (select 'Acid')
        for (int i = 0; i < 4; i++) {
            editor.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, GLFW.GLFW_MOD_SHIFT);
        }
        Assertions.assertEquals(13, editor.getCursorPos());
        Assertions.assertEquals(9, editor.getHighlightPos());
        Assertions.assertTrue(editor.hasSelection());
        Assertions.assertEquals("Acid", editor.getSelectedText());

        // Typing a character replaces selection
        editor.charTyped('P', 0);
        editor.charTyped('l', 0);
        editor.charTyped('a', 0);
        editor.charTyped('n', 0);
        editor.charTyped('t', 0);
        Assertions.assertEquals("Sulfuric Plant", editor.getText());
        Assertions.assertFalse(editor.hasSelection());
    }

    @Test
    public void testDeleteAndBackspace() {
        editor.setText("Hello World");
        editor.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0); // Cursor at 0

        // Delete key deletes character to the right
        editor.keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0); // deletes 'H'
        Assertions.assertEquals("ello World", editor.getText());
        Assertions.assertEquals(0, editor.getCursorPos());

        // End key then Backspace deletes character to the left
        editor.keyPressed(GLFW.GLFW_KEY_END, 0, 0);
        editor.keyPressed(GLFW.GLFW_KEY_BACKSPACE, 0, 0); // deletes 'd'
        Assertions.assertEquals("ello Worl", editor.getText());

        // Shift selection delete
        editor.keyPressed(GLFW.GLFW_KEY_HOME, 0, 0);
        for (int i = 0; i < 4; i++) {
            editor.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, GLFW.GLFW_MOD_SHIFT); // Select 'ello'
        }
        Assertions.assertEquals("ello", editor.getSelectedText());
        editor.keyPressed(GLFW.GLFW_KEY_DELETE, 0, 0); // Deletes 'ello'
        Assertions.assertEquals(" Worl", editor.getText());
    }

    @Test
    public void testSelectAllAndClipboardOperations() {
        editor.setText("Test Blueprint");

        // Ctrl + A (Select All)
        editor.keyPressed(GLFW.GLFW_KEY_A, 0, GLFW.GLFW_MOD_CONTROL);
        Assertions.assertTrue(editor.hasSelection());
        Assertions.assertEquals("Test Blueprint", editor.getSelectedText());

        // Ctrl + C (Copy)
        editor.keyPressed(GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_CONTROL);
        Assertions.assertEquals("Test Blueprint", InlineTextEditor.getClipboard());

        // Ctrl + X (Cut)
        editor.keyPressed(GLFW.GLFW_KEY_X, 0, GLFW.GLFW_MOD_CONTROL);
        Assertions.assertEquals("", editor.getText());
        Assertions.assertEquals("Test Blueprint", InlineTextEditor.getClipboard());

        // Ctrl + V (Paste)
        editor.keyPressed(GLFW.GLFW_KEY_V, 0, GLFW.GLFW_MOD_CONTROL);
        Assertions.assertEquals("Test Blueprint", editor.getText());
    }

    @Test
    public void testWordNavigation() {
        editor.setText("Super Fast Turbine");
        editor.keyPressed(GLFW.GLFW_KEY_END, 0, 0); // at end (18)

        // Ctrl + Left (Skip back to word start)
        editor.keyPressed(GLFW.GLFW_KEY_LEFT, 0, GLFW.GLFW_MOD_CONTROL);
        Assertions.assertEquals(11, editor.getCursorPos()); // start of "Turbine"

        editor.keyPressed(GLFW.GLFW_KEY_LEFT, 0, GLFW.GLFW_MOD_CONTROL);
        Assertions.assertEquals(6, editor.getCursorPos()); // start of "Fast"

        editor.keyPressed(GLFW.GLFW_KEY_LEFT, 0, GLFW.GLFW_MOD_CONTROL);
        Assertions.assertEquals(0, editor.getCursorPos()); // start of "Super"

        // Ctrl + Right
        editor.keyPressed(GLFW.GLFW_KEY_RIGHT, 0, GLFW.GLFW_MOD_CONTROL);
        Assertions.assertEquals(6, editor.getCursorPos()); // after "Super "
    }

    @Test
    public void testCharFilterAndMaxLength() {
        InlineTextEditor numEditor = new InlineTextEditor(5, Character::isDigit);
        numEditor.startEditing("12");

        numEditor.charTyped('a', 0); // Ignored due to non-digit
        numEditor.charTyped('3', 0);
        numEditor.charTyped('4', 0);
        numEditor.charTyped('5', 0);
        numEditor.charTyped('6', 0); // Exceeds maxLength 5

        Assertions.assertEquals("12345", numEditor.getText());
    }

    @Test
    public void testMouseClickAndDragSelection() {
        editor.setText("HelloWorld");
        // Mock Font width in test: font might be null or mock in Extension, but getCharIndexAt with null returns 0
        Assertions.assertEquals(0, editor.getCharIndexAt(null, 50.0));

        // Direct cursorPos / highlightPos selection
        editor.onClick(null, 0, false);
        Assertions.assertEquals(0, editor.getCursorPos());
        Assertions.assertEquals(0, editor.getHighlightPos());
    }
}
