package com.gtceu.calcboard.client.team;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceCollaborationSyncTest {

    private ClientWorkspaceState state;

    @BeforeEach
    public void setUp() {
        state = ClientWorkspaceState.getInstance();
        state.clear();
    }

    @Test
    public void testCollaborationDisabledWhenNoTeamAssigned() {
        // Without an assigned team, collaboration mode must be disabled
        assertNull(state.getCurrentTeamId());
        assertFalse(state.isCollaborationEnabled());
    }

    @Test
    public void testCollaborationEnabledWhenTeamAssigned() {
        // When a team ID is assigned (by server sync packet), collaboration mode must be active,
        // regardless of whether running on an integrated singleplayer host (LAN) or dedicated client.
        UUID partyId = UUID.randomUUID();
        state.setCurrentTeamId(partyId);
        state.setCurrentTeamName("Test Party");

        assertEquals(partyId, state.getCurrentTeamId());
        assertTrue(state.isCollaborationEnabled(), "Collaboration should be enabled when team ID is present");
    }

    @Test
    public void testCollaborationEnabledOnMultiplayerServerEvenWithoutTeam() {
        state.setServerSupported(true);
        assertNull(state.getCurrentTeamId());
        assertTrue(state.isCollaborationEnabled(), "Collaboration tabs should be visible on multiplayer server even before joining a party");
    }

    @Test
    public void testClearResetsCollaboration() {
        state.setCurrentTeamId(UUID.randomUUID());
        assertTrue(state.isCollaborationEnabled());

        state.clear();

        assertNull(state.getCurrentTeamId());
        assertFalse(state.isCollaborationEnabled());
        assertEquals(ClientWorkspaceState.WorkspaceMode.LOCAL, state.getCurrentMode());
    }

    @Test
    public void testLockHeldTracking() {
        assertFalse(state.doesHoldLock("page_main"));

        state.setLockHeld("page_main", true);
        assertTrue(state.doesHoldLock("page_main"));

        state.setLockHeld("page_main", false);
        assertFalse(state.doesHoldLock("page_main"));
    }

    @Test
    public void testPageRevisionPreservedIndependentlyFromGlobalRevision() {
        com.gtceu.calcboard.server.storage.TeamWorkspacePage page = new com.gtceu.calcboard.server.storage.TeamWorkspacePage("page_sub", "Sub Factory", 7, new byte[0]);
        state.updateRemotePages(java.util.List.of(page));
        state.setGlobalRevision(24);

        com.gtceu.calcboard.server.storage.TeamWorkspacePage remotePage = state.getRemotePage("page_sub");
        assertNotNull(remotePage);
        assertEquals(7, remotePage.getPageRevision(), "Page revision should remain independent of global revision");
        assertEquals(24, state.getGlobalRevision());
    }

    @Test
    public void testIsCurrentPlayerHeadlessSafety() {
        // In headless test environments without active Minecraft player, isCurrentPlayer must safely return false without crashing
        assertFalse(state.isCurrentPlayer(UUID.randomUUID(), "Kasmov"));
        assertFalse(state.isCurrentPlayer(null, null));
    }

    @Test
    public void testLockHeldRemainsActiveAcrossTicks() {
        state.setCurrentMode(ClientWorkspaceState.WorkspaceMode.TEAM);
        state.setCurrentTeamId(UUID.randomUUID());
        state.setLockHeld("page_main", true);
        state.markPageDirty("page_main");

        assertTrue(state.doesHoldLock("page_main"));
        assertTrue(state.isPageDirty("page_main"));
    }
}
