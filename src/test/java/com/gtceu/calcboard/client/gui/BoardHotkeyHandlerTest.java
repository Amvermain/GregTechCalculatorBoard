package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.model.FlowGraph;
import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

public class BoardHotkeyHandlerTest {

    @BeforeEach
    @AfterEach
    public void cleanup() {
        BoardManager.getInstance().resetToDefault();
    }

    @Test
    public void testNullScreenHandling() {
        boolean handled = BoardHotkeyHandler.handleKeyPressed(null, GLFW.GLFW_KEY_R, 0, GLFW.GLFW_MOD_ALT, 0, 0);
        Assertions.assertFalse(handled);
    }

    @Test
    public void testAltRAutoRatio() {
        BoardScreen screen = new BoardScreen();
        boolean handled = BoardHotkeyHandler.handleKeyPressed(screen, GLFW.GLFW_KEY_R, 0, GLFW.GLFW_MOD_ALT, 0, 0);
        Assertions.assertTrue(handled);
    }

    @Test
    public void testShiftAltRFractionalAutoRatio() {
        BoardScreen screen = new BoardScreen();
        int mods = GLFW.GLFW_MOD_ALT | GLFW.GLFW_MOD_SHIFT;
        boolean handled = BoardHotkeyHandler.handleKeyPressed(screen, GLFW.GLFW_KEY_R, 0, mods, 0, 0);
        Assertions.assertTrue(handled);
    }

    @Test
    public void testShiftCAutoConnect() {
        BoardScreen screen = new BoardScreen();
        boolean handled = BoardHotkeyHandler.handleKeyPressed(screen, GLFW.GLFW_KEY_C, 0, GLFW.GLFW_MOD_SHIFT, 0, 0);
        Assertions.assertTrue(handled);
    }

    @Test
    public void testGridSnapHotkey() {
        BoardScreen screen = new BoardScreen();
        boolean initial = BoardManager.getInstance().isGridSnapEnabled();
        boolean handled = BoardHotkeyHandler.handleKeyPressed(screen, GLFW.GLFW_KEY_G, 0, 0, 0, 0);
        Assertions.assertTrue(handled);
        Assertions.assertEquals(!initial, BoardManager.getInstance().isGridSnapEnabled());

        BoardHotkeyHandler.handleKeyPressed(screen, GLFW.GLFW_KEY_G, 0, 0, 0, 0);
        Assertions.assertEquals(initial, BoardManager.getInstance().isGridSnapEnabled());
    }

    @Test
    public void testEscapeClosesToolbarDropdown() {
        BoardScreen screen = new BoardScreen();
        screen.getToolbarWidget().closeDropdown();
        Assertions.assertFalse(screen.getToolbarWidget().isOverflowMenuOpen());

        boolean initialEsc = BoardHotkeyHandler.handleKeyPressed(screen, GLFW.GLFW_KEY_ESCAPE, 0, 0, 0, 0);
        Assertions.assertFalse(initialEsc);
    }

    @Test
    public void testActiveEditorPrioritization() {
        BoardScreen screen = new BoardScreen();
        FlowGraph graph = screen.getGraph();
        RecipeNode node = RecipeNode.create("Test Machine", 10.0, 20.0, GTVoltageTier.LV);
        graph.addNode(node);
        NodeWidget widget = new NodeWidget(node);
        screen.getNodeWidgets().add(widget);

        widget.getNameEditor().startEditing();
        Assertions.assertTrue(widget.isAnyEditorActive());

        boolean handled = BoardHotkeyHandler.handleKeyPressed(screen, GLFW.GLFW_KEY_R, 0, GLFW.GLFW_MOD_ALT, 0, 0);
        Assertions.assertTrue(handled);
    }
}
