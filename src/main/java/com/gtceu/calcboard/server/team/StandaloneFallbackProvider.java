package com.gtceu.calcboard.server.team;

import net.minecraft.server.level.ServerPlayer;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Fallback provider when no team mod or vanilla scoreboards are active.
 * Maps each player directly to their individual UUID workspace.
 */
public class StandaloneFallbackProvider implements ITeamProvider {

    @Override
    public UUID getPlayerTeamId(ServerPlayer player) {
        return player != null ? player.getUUID() : UUID.nameUUIDFromBytes("standalone_default".getBytes());
    }

    @Override
    public String getTeamDisplayName(UUID teamId) {
        return "Personal Workspace (" + teamId.toString().substring(0, 8) + ")";
    }

    @Override
    public Set<UUID> getTeamMembers(UUID teamId) {
        return Collections.singleton(teamId);
    }

    @Override
    public boolean canPlayerEdit(ServerPlayer player, UUID teamId) {
        return player != null && player.getUUID().equals(teamId);
    }

    @Override
    public String getProviderId() {
        return "standalone";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
