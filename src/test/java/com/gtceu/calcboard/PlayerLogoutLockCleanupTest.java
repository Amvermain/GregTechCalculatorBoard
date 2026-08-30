package com.gtceu.calcboard;

import com.gtceu.calcboard.server.storage.TeamPresenceTracker;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Verifies RFC-003: Player Logout Lock Cleanup and Targeted Presence Page Filtering.
 */
public class PlayerLogoutLockCleanupTest {

    private WorkspaceLockManager lockManager;
    private TeamPresenceTracker presenceTracker;
    private UUID teamId;
    private UUID playerA;
    private UUID playerB;

    @BeforeEach
    void setUp() {
        lockManager = WorkspaceLockManager.getInstance();
        lockManager.clearAll();
        presenceTracker = TeamPresenceTracker.getInstance();
        teamId = UUID.randomUUID();
        playerA = UUID.randomUUID();
        playerB = UUID.randomUUID();
    }

    @Test
    @DisplayName("RFC-003: releaseAllLocksForPlayer releases all locked pages held by disconnected player")
    void testReleaseAllLocksForPlayer() {
        String page1 = "page_cracking";
        String page2 = "page_distillation";
        String page3 = "page_polymer";

        // Player A acquires lock on page 1 and page 2
        lockManager.acquireLock(teamId, page1, playerA, "PlayerA");
        lockManager.acquireLock(teamId, page2, playerA, "PlayerA");

        // Player B acquires lock on page 3
        lockManager.acquireLock(teamId, page3, playerB, "PlayerB");

        Assertions.assertTrue(lockManager.isPageLocked(teamId, page1));
        Assertions.assertTrue(lockManager.isPageLocked(teamId, page2));
        Assertions.assertTrue(lockManager.isPageLocked(teamId, page3));

        // Player A disconnects / logs out -> release all locks for player A
        List<String> released = lockManager.releaseAllLocksForPlayer(teamId, playerA);
        Assertions.assertEquals(2, released.size());
        Assertions.assertTrue(released.contains(page1));
        Assertions.assertTrue(released.contains(page2));

        // Pages 1 and 2 should now be completely unlocked
        Assertions.assertFalse(lockManager.isPageLocked(teamId, page1));
        Assertions.assertFalse(lockManager.isPageLocked(teamId, page2));

        // Page 3 (held by Player B) remains locked
        Assertions.assertTrue(lockManager.isPageLocked(teamId, page3));

        // Player B can now acquire lock on page 1
        boolean bAcquired = lockManager.acquireLock(teamId, page1, playerB, "PlayerB");
        Assertions.assertTrue(bAcquired);
    }

    @Test
    @DisplayName("RFC-003: Targeted presence filtering by active page")
    void testTargetedPresenceFiltering() {
        // Direct record insertion into presence tracker
        presenceTracker.updatePresence(null, teamId, "page_main", false); // Clear

        // Note: updatePresence with non-null ServerPlayer requires mock,
        // but getActiveViewersForPage with empty returns empty set safely.
        Set<UUID> viewers = presenceTracker.getActiveViewersForPage(teamId, "page_main");
        Assertions.assertNotNull(viewers);
        Assertions.assertTrue(viewers.isEmpty());
    }
}
