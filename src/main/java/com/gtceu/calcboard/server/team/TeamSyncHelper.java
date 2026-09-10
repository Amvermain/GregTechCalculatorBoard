package com.gtceu.calcboard.server.team;

import com.gtceu.calcboard.network.NetworkHandler;
import com.gtceu.calcboard.network.packet.s2c.S2CSyncWorkspaceMetaPacket;
import com.gtceu.calcboard.server.storage.TeamBoardSavedData;
import com.gtceu.calcboard.server.storage.TeamWorkspaceData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Utility helper for dispatching synchronized team workspace metadata to connected server players.
 */
public final class TeamSyncHelper {

    private static final UUID EMPTY_TEAM_UUID = new UUID(0L, 0L);

    private TeamSyncHelper() {}

    public static void syncPlayer(ServerPlayer player) {
        if (player == null || player.serverLevel() == null) {
            return;
        }

        UUID playerTeamId = TeamProviderRegistry.getInstance().getPlayerTeamId(player);
        if (playerTeamId == null) {
            NetworkHandler.sendToPlayer(player, new S2CSyncWorkspaceMetaPacket(EMPTY_TEAM_UUID, "", 0, Collections.emptyList()));
            return;
        }

        TeamBoardSavedData savedData = TeamBoardSavedData.get(player.serverLevel());
        if (savedData == null) {
            return;
        }

        String teamName = TeamProviderRegistry.getInstance().getTeamDisplayName(playerTeamId);
        TeamWorkspaceData ws = savedData.getOrCreateWorkspace(playerTeamId, teamName);
        NetworkHandler.sendToPlayer(player, ws.buildMetaPacket());
        NetworkHandler.broadcastPresenceForTeam(player.serverLevel(), playerTeamId);
    }

    public static void syncPlayers(Collection<ServerPlayer> players) {
        if (players == null || players.isEmpty()) {
            return;
        }
        for (ServerPlayer p : players) {
            syncPlayer(p);
        }
    }

    public static void syncTeam(ServerLevel level, UUID teamId) {
        if (level == null || teamId == null) {
            return;
        }
        Set<UUID> memberUuids = TeamProviderRegistry.getInstance().getTeamMembers(teamId);
        for (UUID memberUuid : memberUuids) {
            ServerPlayer member = level.getServer().getPlayerList().getPlayer(memberUuid);
            if (member != null) {
                syncPlayer(member);
            }
        }
    }
}
