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
}
