package com.gtceu.calcboard;

import com.gtceu.calcboard.server.storage.WorkspaceLockManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class WorkspaceLockTest {

    private WorkspaceLockManager lockManager;
    private UUID teamId;
    private UUID playerA;
    private UUID playerB;

    @BeforeEach
    public void setUp() {
        lockManager = WorkspaceLockManager.getInstance();
        lockManager.clearAll();
        teamId = UUID.randomUUID();
        playerA = UUID.randomUUID();
        playerB = UUID.randomUUID();
    }

    @Test
    public void testAcquireAndReleaseLock() {
        String pageId = "page_ethylene";

        // Player A acquires lock
        boolean acquired = lockManager.acquireLock(teamId, pageId, playerA, "PlayerA");
        assertTrue(acquired);

        WorkspaceLockManager.LockInfo lock = lockManager.getLock(teamId, pageId);
        assertNotNull(lock);
        assertEquals(playerA, lock.getHolderUUID());
        assertEquals("PlayerA", lock.getHolderName());

        // Player B attempts to acquire the same page lock -> Should fail
        boolean playerBAcquired = lockManager.acquireLock(teamId, pageId, playerB, "PlayerB");
        assertFalse(playerBAcquired);

        // Player A releases lock
        boolean released = lockManager.releaseLock(teamId, pageId, playerA);
        assertTrue(released);

        // Now Player B can acquire lock
        boolean playerBNowAcquired = lockManager.acquireLock(teamId, pageId, playerB, "PlayerB");
        assertTrue(playerBNowAcquired);
    }

    @Test
    public void testGranularPageLevelLocking() {
        // Player A locks Page 1
        boolean p1Locked = lockManager.acquireLock(teamId, "page_1", playerA, "PlayerA");
        assertTrue(p1Locked);

        // Player B should be able to simultaneously lock Page 2 in the same team workspace
        boolean p2Locked = lockManager.acquireLock(teamId, "page_2", playerB, "PlayerB");
        assertTrue(p2Locked);

        assertEquals(playerA, lockManager.getLock(teamId, "page_1").getHolderUUID());
        assertEquals(playerB, lockManager.getLock(teamId, "page_2").getHolderUUID());
    }

    @Test
    public void testLockExpiration() {
        String pageId = "page_test";
        lockManager.acquireLock(teamId, pageId, playerA, "PlayerA");

        WorkspaceLockManager.LockInfo lock = lockManager.getLock(teamId, pageId);
        assertNotNull(lock);

        // Artificially expire the lock
        lock.extend(-1000000L);
        assertTrue(lock.isExpired());

        // Player B should now be able to acquire lock because previous lock expired
        boolean playerBAcquired = lockManager.acquireLock(teamId, pageId, playerB, "PlayerB");
        assertTrue(playerBAcquired);
        assertEquals(playerB, lockManager.getLock(teamId, pageId).getHolderUUID());
    }
}
