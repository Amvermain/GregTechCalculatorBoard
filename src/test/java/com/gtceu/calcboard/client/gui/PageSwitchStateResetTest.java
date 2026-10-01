package com.gtceu.calcboard.client.gui;

import com.gtceu.calcboard.api.storage.BoardManager;
import com.gtceu.calcboard.api.storage.BoardPage;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasIdleState;
import com.gtceu.calcboard.client.gui.interaction.state.CanvasWireConnectingState;
import com.gtceu.calcboard.api.team.TeamWorkspacePage;
import com.gtceu.calcboard.client.team.ClientWorkspaceState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public class PageSwitchStateResetTest {

    @BeforeEach
    @AfterEach
    public void cleanup() {
        BoardManager.getInstance().resetToDefault();
        ClientWorkspaceState.getInstance().clear();
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

    @Test
    @DisplayName("Team page renaming via right click and double click")
    public void testTeamPageTabBarWidgetRenaming() {
        UUID teamId = UUID.randomUUID();
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        try {
            state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
            state.setCurrentTeamId(teamId);
            TeamWorkspacePage tp = new TeamWorkspacePage("page_team_1", "Initial Team Title", 1, new byte[0]);
            state.updateRemotePages(java.util.List.of(tp));
            state.setActiveTeamPageId("page_team_1");

            BoardScreen screen = new BoardScreen();
            int tabY = screen.getPageTabY();

            boolean rightClicked = screen.getPageTabBar().mouseClicked(80, tabY + 5, 1);
            Assertions.assertTrue(rightClicked);
            Assertions.assertTrue(screen.getPageTabBar().isEditing(), "Right clicking team tab must trigger renaming");

            screen.getPageTabBar().getRenameBox().setValue("Renamed Title");
            screen.getPageTabBar().keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);

            Assertions.assertFalse(screen.getPageTabBar().isEditing());
            Assertions.assertEquals("Renamed Title", tp.getTitle(), "Team page title must be optimistically updated");
        } finally {
            state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
        }
    }

    @Test
    @DisplayName("Team page viewports are preserved across page switches and workspace mode transitions")
    public void testTeamPageViewportsPreservedAcrossPageAndModeSwitches() {
        UUID teamId = UUID.randomUUID();
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        try {
            state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
            state.setCurrentTeamId(teamId);
            TeamWorkspacePage tp1 = new TeamWorkspacePage("page_team_1", "Team Page 1", 1, new byte[0]);
            TeamWorkspacePage tp2 = new TeamWorkspacePage("page_team_2", "Team Page 2", 1, new byte[0]);
            state.updateRemotePages(java.util.List.of(tp1, tp2));
            state.setActiveTeamPageId("page_team_1");

            BoardScreen screen = new BoardScreen();
            screen.setPanX(150.0);
            screen.setPanY(220.0);
            screen.setZoom(1.25);

            // Switch to page 2
            screen.openPage("page_team_2");
            Assertions.assertEquals(40.0, screen.getPanX(), 1e-6);
            Assertions.assertEquals(40.0, screen.getPanY(), 1e-6);
            Assertions.assertEquals(1.0, screen.getZoom(), 1e-6);

            // Mutate viewport on page 2
            screen.setPanX(-300.0);
            screen.setPanY(50.0);
            screen.setZoom(0.5);

            // Switch back to page 1
            screen.openPage("page_team_1");
            Assertions.assertEquals(150.0, screen.getPanX(), 1e-6, "Page 1 panX must be restored");
            Assertions.assertEquals(220.0, screen.getPanY(), 1e-6, "Page 1 panY must be restored");
            Assertions.assertEquals(1.25, screen.getZoom(), 1e-6, "Page 1 zoom must be restored");

            // Switch to page 2 again
            screen.openPage("page_team_2");
            Assertions.assertEquals(-300.0, screen.getPanX(), 1e-6, "Page 2 panX must be restored");
            Assertions.assertEquals(50.0, screen.getPanY(), 1e-6, "Page 2 panY must be restored");
            Assertions.assertEquals(0.5, screen.getZoom(), 1e-6, "Page 2 zoom must be restored");

            // Switch to local mode and then back to team mode
            screen.switchToWorkspaceMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
            Assertions.assertFalse(state.isTeamMode());

            screen.switchToWorkspaceMode(ClientWorkspaceState.WorkspaceMode.TEAM);
            Assertions.assertTrue(state.isTeamMode());
            Assertions.assertEquals(-300.0, screen.getPanX(), 1e-6, "Team page 2 panX restored after mode toggle");
            Assertions.assertEquals(50.0, screen.getPanY(), 1e-6, "Team page 2 panY restored after mode toggle");
            Assertions.assertEquals(0.5, screen.getZoom(), 1e-6, "Team page 2 zoom restored after mode toggle");
        } finally {
            state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
        }
    }

    @Test
    @DisplayName("Regression: PageTabBarWidget tab clicking on team pages preserves and restores viewports independently")
    public void testTeamPageTabBarWidgetTabSwitchPreservesViewports() {
        UUID teamId = UUID.randomUUID();
        ClientWorkspaceState state = ClientWorkspaceState.getInstance();
        try {
            state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
            state.setCurrentTeamId(teamId);
            TeamWorkspacePage tp1 = new TeamWorkspacePage("page_team_1", "Team Page 1", 1, new byte[0]);
            TeamWorkspacePage tp2 = new TeamWorkspacePage("page_team_2", "Team Page 2", 1, new byte[0]);
            state.updateRemotePages(java.util.List.of(tp1, tp2));
            state.setActiveTeamPageId("page_team_1");

            BoardScreen screen = new BoardScreen();
            screen.setPanX(120.0);
            screen.setPanY(340.0);
            screen.setZoom(1.5);

            int tabY = screen.getPageTabY();

            // Click second tab via PageTabBarWidget
            boolean clickedPage2 = screen.getPageTabBar().mouseClicked(220, tabY + 5, 0);
            Assertions.assertTrue(clickedPage2);
            Assertions.assertEquals("page_team_2", state.getActiveTeamPageId());
            Assertions.assertEquals(40.0, screen.getPanX(), 1e-6, "Team page 2 should start at default panX");
            Assertions.assertEquals(40.0, screen.getPanY(), 1e-6, "Team page 2 should start at default panY");
            Assertions.assertEquals(1.0, screen.getZoom(), 1e-6, "Team page 2 should start at default zoom");

            // Pan and zoom on page 2
            screen.setPanX(-500.0);
            screen.setPanY(-200.0);
            screen.setZoom(0.75);

            // Click first tab via PageTabBarWidget
            boolean clickedPage1 = screen.getPageTabBar().mouseClicked(80, tabY + 5, 0);
            Assertions.assertTrue(clickedPage1);
            Assertions.assertEquals("page_team_1", state.getActiveTeamPageId());
            Assertions.assertEquals(120.0, screen.getPanX(), 1e-6, "Team page 1 panX must be restored");
            Assertions.assertEquals(340.0, screen.getPanY(), 1e-6, "Team page 1 panY must be restored");
            Assertions.assertEquals(1.5, screen.getZoom(), 1e-6, "Team page 1 zoom must be restored");

            // Click second tab again via PageTabBarWidget
            boolean clickedPage2Again = screen.getPageTabBar().mouseClicked(220, tabY + 5, 0);
            Assertions.assertTrue(clickedPage2Again);
            Assertions.assertEquals("page_team_2", state.getActiveTeamPageId());
            Assertions.assertEquals(-500.0, screen.getPanX(), 1e-6, "Team page 2 panX must be restored");
            Assertions.assertEquals(-200.0, screen.getPanY(), 1e-6, "Team page 2 panY must be restored");
            Assertions.assertEquals(0.75, screen.getZoom(), 1e-6, "Team page 2 zoom must be restored");
        } finally {
            state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.LOCAL);
        }
    }
}
