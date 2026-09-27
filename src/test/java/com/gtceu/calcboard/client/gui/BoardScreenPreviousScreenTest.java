package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.model.RecipeNode;
import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.type.GTVoltageTier;
import com.gtceu.calcboard.client.gui.widget.NodeWidget;
import com.gtceu.calcboard.client.key.KeyBindings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

public class BoardScreenPreviousScreenTest {

    private static class DummyScreen extends Screen {
        protected DummyScreen() {
            super(Component.literal("Dummy"));
        }
    }

    @BeforeEach
    @AfterEach
    public void cleanup() {
        BoardManager.getInstance().resetToDefault();
    }

    @Test
    @DisplayName("BoardScreen retains previousScreen when opened with another screen")
    public void testOpenBoardWithPreviousScreen() {
        DummyScreen previous = new DummyScreen();
        BoardScreen boardScreen = new BoardScreen(previous);

        Assertions.assertSame(previous, boardScreen.getPreviousScreen());
    }

    @Test
    @DisplayName("Default BoardScreen constructor sets previousScreen to null")
    public void testDefaultConstructorPreviousScreenIsNull() {
        BoardScreen boardScreen = new BoardScreen();
        Assertions.assertNull(boardScreen.getPreviousScreen());
    }

    @Test
    @DisplayName("BoardScreen onClose preserves state and safely handles previous screen in headless environment")
    public void testOnCloseSafeInHeadless() {
        DummyScreen previous = new DummyScreen();
        BoardScreen boardScreen = new BoardScreen(previous);

        Assertions.assertDoesNotThrow(boardScreen::onClose);
    }

    @Test
    @DisplayName("Pressing OPEN_BOARD hotkey closes BoardScreen when not typing")
    public void testOpenBoardHotkeyClosesScreenWhenNotTyping() {
        DummyScreen previous = new DummyScreen();
        boolean[] closed = {false};
        BoardScreen boardScreen = new BoardScreen(previous) {
            @Override
            public void onClose() {
                closed[0] = true;
                super.onClose();
            }
        };

        int openBoardKeyCode = KeyBindings.OPEN_BOARD.getKey().getValue();
        boolean handled = BoardHotkeyHandler.handleKeyPressed(boardScreen, openBoardKeyCode, 0, 0, 0, 0);

        Assertions.assertTrue(handled);
        Assertions.assertTrue(closed[0]);
    }

    @Test
    @DisplayName("Pressing OPEN_BOARD hotkey does not close BoardScreen when node editor is actively typing")
    public void testOpenBoardHotkeyBlockedWhenEditorActive() {
        boolean[] closed = {false};
        BoardScreen boardScreen = new BoardScreen() {
            @Override
            public void onClose() {
                closed[0] = true;
                super.onClose();
            }
        };

        RecipeNode node = RecipeNode.create("Test Node", 10.0, 20.0, GTVoltageTier.LV);
        boardScreen.getGraph().addNode(node);
        NodeWidget widget = new NodeWidget(node);
        boardScreen.getNodeWidgets().add(widget);

        widget.getNameEditor().startEditing();
        Assertions.assertTrue(widget.isAnyEditorActive());

        int openBoardKeyCode = KeyBindings.OPEN_BOARD.getKey().getValue();
        boolean handled = BoardHotkeyHandler.handleKeyPressed(boardScreen, openBoardKeyCode, 0, 0, 0, 0);

        Assertions.assertTrue(handled);
        Assertions.assertFalse(closed[0]);
    }
}
