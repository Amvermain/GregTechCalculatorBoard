package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.storage.BoardManager;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

public class BoardScreenDefensiveTest {

    @BeforeEach
    @AfterEach
    public void cleanup() {
        BoardManager.getInstance().resetToDefault();
    }

    @Test
    @DisplayName("RFC-058: children() returns a defensive copy preventing external mutations")
    public void testChildrenReturnsDefensiveCopy() {
        BoardScreen screen = new BoardScreen();
        List<? extends GuiEventListener> c1 = screen.children();
        List<? extends GuiEventListener> c2 = screen.children();

        Assertions.assertNotNull(c1);
        Assertions.assertNotNull(c2);
        Assertions.assertNotSame(c1, c2);

        int initialSize = c1.size();
        c1.clear();
        Assertions.assertEquals(0, c1.size());
        Assertions.assertEquals(initialSize, screen.children().size());
    }

    @Test
    @DisplayName("RFC-058: Iterating screen.children() does not throw ConcurrentModificationException when widgets are rebuilt")
    public void testConcurrentModificationPreventionOnChildrenIteration() {
        BoardScreen screen = new BoardScreen();
        Assertions.assertDoesNotThrow(() -> {
            for (GuiEventListener child : screen.children()) {
                screen.rebuildWidgets();
            }
        });
    }
}
