package com.gtceu.calcboard.server.storage;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CBroadcastPresencePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks players who currently have the Calculator Board open and viewing the team workspace.
 * Automatically cleans up inactive/disconnected players.
 */
public class TeamPresenceTracker {

    private static final TeamPresenceTracker INSTANCE = new TeamPresenceTracker();
    private static final long TIMEOUT_MS = 6000L; // 6 seconds

    public static TeamPresenceTracker getInstance() {
        return INSTANCE;
    }

    public static class PresenceRecord {
        public final UUID playerUUID;
        public final String playerName;
        public final UUID teamId;
        public final String activePageId;
        public final boolean isEditing;
        public long lastPing;

        public PresenceRecord(UUID playerUUID, String playerName, UUID teamId, String activePageId, boolean isEditing, long lastPing) {
            this.playerUUID = playerUUID;
            this.playerName = playerName;
            this.teamId = teamId;
            this.activePageId = activePageId;
            this.isEditing = isEditing;
            this.lastPing = lastPing;
        }
    }

    // playerUUID -> PresenceRecord
    private final Map<UUID, PresenceRecord> activeViewers = new ConcurrentHashMap<>();

    public synchronized void updatePresence(ServerPlayer player, UUID teamId, String activePageId, boolean isOpen) {
        if (player == null || teamId == null) return;
        UUID pId = player.getUUID();
        if (!isOpen) {
            activeViewers.remove(pId);
        } else {
            boolean isEditing = WorkspaceLockManager.getInstance().isPageLocked(teamId, activePageId);
            activeViewers.put(pId, new PresenceRecord(
                    pId,
                    player.getGameProfile().getName(),
                    teamId,
                    activePageId != null ? activePageId : "page_main",
                    isEditing,
                    System.currentTimeMillis()
            ));
        }
        broadcastPresence(player.serverLevel(), teamId);
    }

    public synchronized void removePlayer(ServerLevel level, UUID playerUUID) {
        PresenceRecord rec = activeViewers.remove(playerUUID);
        if (rec != null && level != null) {
            broadcastPresence(level, rec.teamId);
        }
    }

    public synchronized List<S2CBroadcastPresencePacket.MemberPresence> getActivePresences(UUID teamId) {
        long now = System.currentTimeMillis();
        activeViewers.entrySet().removeIf(e -> (now - e.getValue().lastPing) > TIMEOUT_MS);

        List<S2CBroadcastPresencePacket.MemberPresence> list = new ArrayList<>();
        for (PresenceRecord r : activeViewers.values()) {
            if (teamId.equals(r.teamId)) {
                list.add(new S2CBroadcastPresencePacket.MemberPresence(
                        r.playerUUID,
                        r.playerName,
                        r.activePageId,
                        r.isEditing
                ));
            }
        }
        return list;
    }

    public synchronized void broadcastPresence(ServerLevel level, UUID teamId) {
        if (level == null || teamId == null) return;
        List<S2CBroadcastPresencePacket.MemberPresence> list = getActivePresences(teamId);
        NetworkHandler.broadcastToTeam(level, teamId, new S2CBroadcastPresencePacket(teamId, list), null);
    }
}
