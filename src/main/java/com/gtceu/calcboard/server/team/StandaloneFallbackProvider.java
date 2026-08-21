package com.gtceu.calcboard.server.team;

import net.minecraft.server.level.ServerPlayer;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Fallback provider when no team mod or vanilla scoreboards are active.
 * Isolates each player to their own personal workspace to ensure privacy and security.
 */
public class StandaloneFallbackProvider implements ITeamProvider {

    @Override
    public UUID getPlayerTeamId(ServerPlayer player) {
        return player != null ? player.getUUID() : null;
    }

    @Override
    public String getTeamDisplayName(UUID teamId) {
        if (teamId == null) return "Personal Workspace";
        return "Personal (" + teamId.toString().substring(0, 8) + ")";
    }

    @Override
    public Set<UUID> getTeamMembers(UUID teamId) {
        return teamId != null ? Collections.singleton(teamId) : Collections.emptySet();
    }

    @Override
    public boolean canPlayerEdit(ServerPlayer player, UUID teamId) {
        if (player == null || teamId == null) return false;
        return player.getUUID().equals(teamId);
    }

    @Override
    public boolean canPlayerAdministerTeam(ServerPlayer player, UUID teamId) {
        if (player == null || teamId == null) return false;
        return player.hasPermissions(2) || player.getUUID().equals(teamId);
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
