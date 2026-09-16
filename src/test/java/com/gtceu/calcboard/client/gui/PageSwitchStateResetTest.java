package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasIdleState;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasWireConnectingState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class PageSwitchStateResetTest {

    @BeforeEach
    @AfterEach
    public void cleanup() {
        BoardManager.getInstance().resetToDefault();
    }

    @Test
    @DisplayName("RFC-058: openPage resets canvas interaction state machine to IDLE and cancels wire drag")
    public void testOpenPageResetsStateMachineAndWireDrag() {
        BoardScreen screen = new BoardScreen();
        BoardManager bm = BoardManager.getInstance();
        BoardPage page2 = bm.addPage("Page 2");
        Assertions.assertNotNull(page2);

        screen.getCanvasHandler().getStateMachine().transitionTo(new CanvasWireConnectingState());
        Assertions.assertTrue(screen.getCanvasHandler().getStateMachine().isInState(CanvasWireConnectingState.class));

        screen.openPage(page2.getId());

        Assertions.assertTrue(screen.getCanvasHandler().getStateMachine().isInState(CanvasIdleState.class),
                "State machine must be reset to CanvasIdleState upon openPage");
        Assertions.assertFalse(screen.getCanvasHandler().getWireHandler().isDraggingWire(),
                "Wire drag must be cancelled upon openPage");
        Assertions.assertEquals(page2.getId(), bm.getActivePage().getId());
    }

    @Test
    @DisplayName("RFC-058: PageTabBarWidget tab switch properly triggers openPage and state reset")
    public void testPageTabBarWidgetTabSwitch() {
        BoardScreen screen = new BoardScreen();
        BoardManager bm = BoardManager.getInstance();
        BoardPage page1 = bm.getActivePage();
        BoardPage page2 = bm.addPage("Page 2");
        bm.openPage(page1.getId());
        Assertions.assertEquals(page1.getId(), bm.getActivePage().getId());

        screen.getCanvasHandler().getStateMachine().transitionTo(new CanvasWireConnectingState());
        Assertions.assertTrue(screen.getCanvasHandler().getStateMachine().isInState(CanvasWireConnectingState.class));

        int tabY = screen.getPageTabY();
        boolean clicked = screen.getPageTabBar().mouseClicked(220, tabY + 5, 0);
        Assertions.assertTrue(clicked);
        Assertions.assertTrue(screen.getCanvasHandler().getStateMachine().isInState(CanvasIdleState.class),
                "State machine must be reset to CanvasIdleState upon tab switch via widget");
        Assertions.assertEquals(page2.getId(), bm.getActivePage().getId());
    }

    @Test
    @DisplayName("RFC-058: PageTabBarWidget middle-click tab close resets canvas state and cancels wire drag")
    public void testPageTabBarWidgetCloseTabResetsState() {
        BoardScreen screen = new BoardScreen();
        BoardManager bm = BoardManager.getInstance();
        BoardPage page2 = bm.addPage("Page 2");

        screen.getCanvasHandler().getStateMachine().transitionTo(new CanvasWireConnectingState());
        Assertions.assertTrue(screen.getCanvasHandler().getStateMachine().isInState(CanvasWireConnectingState.class));

        int tabY = screen.getPageTabY();
        boolean closed = screen.getPageTabBar().mouseClicked(220, tabY + 5, 2);
        Assertions.assertTrue(closed);
        Assertions.assertTrue(screen.getCanvasHandler().getStateMachine().isInState(CanvasIdleState.class),
                "State machine must be reset to CanvasIdleState upon tab close via widget");
        Assertions.assertEquals(1, bm.getOpenPages().size());
    }

    @Test
    @DisplayName("RFC-058: PageTabBarWidget click on already active tab resets canvas interaction state machine")
    public void testPageTabBarWidgetClickActiveTabResetsState() {
        BoardScreen screen = new BoardScreen();
        BoardManager bm = BoardManager.getInstance();

        screen.getCanvasHandler().getStateMachine().transitionTo(new CanvasWireConnectingState());
        Assertions.assertTrue(screen.getCanvasHandler().getStateMachine().isInState(CanvasWireConnectingState.class));

        int tabY = screen.getPageTabY();
        boolean clicked = screen.getPageTabBar().mouseClicked(80, tabY + 5, 0);
        Assertions.assertTrue(clicked);
        Assertions.assertTrue(screen.getCanvasHandler().getStateMachine().isInState(CanvasIdleState.class),
                "State machine must be reset to CanvasIdleState upon clicking active tab");
    }

    @Test
    @DisplayName("RFC-058: openPage with null or empty pageId safely no-ops")
    public void testOpenPageNullOrEmptySafe() {
        BoardScreen screen = new BoardScreen();
        Assertions.assertDoesNotThrow(() -> {
            screen.openPage((String) null);
            screen.openPage("");
            screen.openPage((java.util.UUID) null);
        });
    }
}
