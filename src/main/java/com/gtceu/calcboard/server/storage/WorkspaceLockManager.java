package com.gtceu.calcboard.server.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages page-level granular edit locks and heartbeat expiration on the server.
 */
public class WorkspaceLockManager {

    private static WorkspaceLockManager instance;

    public static final long DEFAULT_LOCK_DURATION_MS = 120_000L; // 2 minutes

    public static class LockInfo {
        private final UUID teamId;
        private final String pageId;
        private final UUID holderUUID;
        private final String holderName;
        private volatile long expiresTimestamp;

        public LockInfo(UUID teamId, String pageId, UUID holderUUID, String holderName, long expiresTimestamp) {
            this.teamId = teamId;
            this.pageId = pageId;
            this.holderUUID = holderUUID;
            this.holderName = holderName != null ? holderName : "Player";
            this.expiresTimestamp = expiresTimestamp;
        }

        public UUID getTeamId() {
            return teamId;
        }

        public String getPageId() {
            return pageId;
        }

        public UUID getHolderUUID() {
            return holderUUID;
        }

        public String getHolderName() {
            return holderName;
        }

        public long getExpiresTimestamp() {
            return expiresTimestamp;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() >= expiresTimestamp;
        }

        public void extend(long durationMs) {
            this.expiresTimestamp = System.currentTimeMillis() + durationMs;
        }
    }

    private final Map<String, LockInfo> activeLocks = new ConcurrentHashMap<>();

    private WorkspaceLockManager() {}

    public static synchronized WorkspaceLockManager getInstance() {
        if (instance == null) {
            instance = new WorkspaceLockManager();
        }
        return instance;
    }

    private String getLockKey(UUID teamId, String pageId) {
        return teamId.toString() + ":" + (pageId != null ? pageId : "default");
    }

    public synchronized boolean acquireLock(UUID teamId, String pageId, UUID playerUUID, String playerName) {
        if (teamId == null || playerUUID == null) return false;
        String key = getLockKey(teamId, pageId);
        LockInfo current = activeLocks.get(key);

        long now = System.currentTimeMillis();
        if (current != null && !current.isExpired()) {
            if (current.getHolderUUID().equals(playerUUID)) {
                current.extend(DEFAULT_LOCK_DURATION_MS);
                return true;
            }
            return false; // Locked by another active user
        }

        LockInfo newLock = new LockInfo(teamId, pageId, playerUUID, playerName, now + DEFAULT_LOCK_DURATION_MS);
        activeLocks.put(key, newLock);
        return true;
    }

    public synchronized boolean releaseLock(UUID teamId, String pageId, UUID playerUUID) {
        if (teamId == null || playerUUID == null) return false;
        String key = getLockKey(teamId, pageId);
        LockInfo current = activeLocks.get(key);
        if (current != null) {
            if (current.getHolderUUID().equals(playerUUID) || current.isExpired()) {
                activeLocks.remove(key);
                return true;
            }
        }
        return false;
    }

    public synchronized List<String> releaseAllLocksForPlayer(UUID teamId, UUID playerUUID) {
        if (playerUUID == null) return Collections.emptyList();
        List<String> releasedPages = new ArrayList<>();
        activeLocks.entrySet().removeIf(entry -> {
            LockInfo info = entry.getValue();
            if (info.getHolderUUID().equals(playerUUID) && (teamId == null || info.getTeamId().equals(teamId))) {
                releasedPages.add(info.getPageId());
                return true;
            }
            return false;
        });
        return releasedPages;
    }

    public synchronized boolean forceReleaseLock(UUID teamId, String pageId) {
        if (teamId == null) return false;
        String key = getLockKey(teamId, pageId);
        return activeLocks.remove(key) != null;
    }

    public synchronized boolean pingHeartbeat(UUID teamId, String pageId, UUID playerUUID) {
        if (teamId == null || playerUUID == null) return false;
        String key = getLockKey(teamId, pageId);
        LockInfo current = activeLocks.get(key);
        if (current != null && current.getHolderUUID().equals(playerUUID) && !current.isExpired()) {
            current.extend(DEFAULT_LOCK_DURATION_MS);
            return true;
        }
        return false;
    }

    public boolean isPageLocked(UUID teamId, String pageId) {
        LockInfo lock = getLock(teamId, pageId);
        return lock != null && !lock.isExpired();
    }

    public LockInfo getLock(UUID teamId, String pageId) {
        if (teamId == null) return null;
        String key = getLockKey(teamId, pageId);
        LockInfo current = activeLocks.get(key);
        if (current != null && current.isExpired()) {
            activeLocks.remove(key);
            return null;
        }
        return current;
    }

    public void cleanExpiredLocks() {
        activeLocks.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    /**
     * Checks if a player has legitimate lock ownership to commit changes to a page,
     * or if the page is currently unlocked.
     */
    public synchronized boolean canCommit(UUID teamId, String pageId, UUID playerUUID) {
        if (teamId == null || playerUUID == null) return false;
        cleanExpiredLocks();
        LockInfo lock = getLock(teamId, pageId);
        if (lock == null) {
            return true; // Unlocked page allows single-commit
        }
        return lock.getHolderUUID().equals(playerUUID);
    }

    /**
     * Resets all active locks (called on world/server unload).
     */
    public synchronized void reset() {
        activeLocks.clear();
    }

    public void clearAll() {
        reset();
    }
}
