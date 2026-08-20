package com.gtceu.calcboard.server.team;

import net.minecraft.server.level.ServerPlayer;
import java.util.Collections;
import java.util.Set;
import java.util.UUID;

/**
 * Fallback provider when no team mod or vanilla scoreboards are active.
 * Maps players to a world-level shared workspace.
 */
public class StandaloneFallbackProvider implements ITeamProvider {

    private static final UUID GLOBAL_SHARED_TEAM_ID = UUID.nameUUIDFromBytes("gtcalcboard:global_shared_workspace".getBytes());

    @Override
    public UUID getPlayerTeamId(ServerPlayer player) {
        return GLOBAL_SHARED_TEAM_ID;
    }

    @Override
    public String getTeamDisplayName(UUID teamId) {
        return "Shared Workspace";
    }

    @Override
    public Set<UUID> getTeamMembers(UUID teamId) {
        return Collections.emptySet();
    }

    @Override
    public boolean canPlayerEdit(ServerPlayer player, UUID teamId) {
        return true;
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
