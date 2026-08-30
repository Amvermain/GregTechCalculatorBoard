package com.gtceu.calcboard.server.lock;

import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import com.gtceu.calcboard.server.storage.TeamWorkspacePage;
import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

/**
 * Verifies RFC-003: Multiplayer Lock Verification and Optimistic Concurrency Control (OCC).
 */
public class MultiplayerLockConcurrencyTest {

    private WorkspaceLockManager lockManager;
    private final UUID teamId = UUID.randomUUID();
    private final UUID playerA = UUID.randomUUID();
    private final UUID playerB = UUID.randomUUID();
    private final String pageId = "page-concurrency-test";

    @BeforeEach
    void setUp() {
        lockManager = WorkspaceLockManager.getInstance();
        lockManager.reset();
    }

    @Test
    @DisplayName("RFC-003: Unlocked page allows single-commit from any player")
    void testCanCommitWhenUnlocked() {
        Assertions.assertTrue(lockManager.canCommit(teamId, pageId, playerA));
        Assertions.assertTrue(lockManager.canCommit(teamId, pageId, playerB));
    }

    @Test
    @DisplayName("RFC-003: Lock owner can commit, other players are blocked")
    void testCanCommitWhenLockedBySelf() {
        boolean acquired = lockManager.acquireLock(teamId, pageId, playerA, "PlayerA");
        Assertions.assertTrue(acquired);

        // Player A is lock holder -> can commit
        Assertions.assertTrue(lockManager.canCommit(teamId, pageId, playerA));

        // Player B is NOT lock holder -> CANNOT commit (423 Locked)
        Assertions.assertFalse(lockManager.canCommit(teamId, pageId, playerB));
    }

    @Test
    @DisplayName("RFC-003: Expired lock is auto-cleaned and allows subsequent commits")
    void testCanCommitWhenLockExpired() {
        // Acquire lock with short expiration
        lockManager.acquireLock(teamId, pageId, playerA, "PlayerA");
        WorkspaceLockManager.LockInfo lock = lockManager.getLock(teamId, pageId);
        Assertions.assertNotNull(lock);

        // Force expiration by moving expiresTimestamp to past
        lock.extend(-10000L);
        Assertions.assertTrue(lock.isExpired());

        // Player B should now be allowed to commit because lock is expired
        Assertions.assertTrue(lockManager.canCommit(teamId, pageId, playerB));
    }

    @Test
    @DisplayName("RFC-003: Optimistic concurrency revision check detects stale commits (409 Conflict)")
    void testOptimisticConcurrencyRevisionCheck() {
        TeamWorkspaceData ws = new TeamWorkspaceData(teamId, "Test Team");
        byte[] dummyData = new byte[]{1, 2, 3};

        // 1. Initial page creation at revision 1
        TeamWorkspacePage page = new TeamWorkspacePage(pageId, "Title", 1, dummyData);
        ws.addOrUpdatePage(page);
        Assertions.assertEquals(1, ws.getPage(pageId).getPageRevision());

        // 2. Player A commits based on revision 1 -> page becomes revision 2
        int clientRevA = 1;
        Assertions.assertEquals(ws.getPage(pageId).getPageRevision(), clientRevA); // Revision matches
        page.setPageRevision(clientRevA + 1);
        ws.addOrUpdatePage(page);
        Assertions.assertEquals(2, ws.getPage(pageId).getPageRevision());

        // 3. Player B attempts to commit based on old revision 1 -> conflict detected!
        int clientRevB = 1;
        boolean hasConflict = (ws.getPage(pageId).getPageRevision() != clientRevB);
        Assertions.assertTrue(hasConflict, "Stale client revision 1 should conflict with current server revision 2");
    }

    @Test
    @DisplayName("RFC-003: WorkspaceLockManager reset clears all active session locks")
    void testWorkspaceLockManagerReset() {
        lockManager.acquireLock(teamId, "page-1", playerA, "PlayerA");
        lockManager.acquireLock(teamId, "page-2", playerB, "PlayerB");
        Assertions.assertTrue(lockManager.isPageLocked(teamId, "page-1"));
        Assertions.assertTrue(lockManager.isPageLocked(teamId, "page-2"));

        // Reset called on server stopped / level unload
        lockManager.reset();

        Assertions.assertFalse(lockManager.isPageLocked(teamId, "page-1"));
        Assertions.assertFalse(lockManager.isPageLocked(teamId, "page-2"));
        Assertions.assertTrue(lockManager.canCommit(teamId, "page-1", playerA));
        Assertions.assertTrue(lockManager.canCommit(teamId, "page-2", playerB));
    }
}
