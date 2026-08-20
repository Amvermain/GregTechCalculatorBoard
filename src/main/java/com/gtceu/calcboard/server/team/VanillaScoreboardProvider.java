package com.gtceu.calcboard.server.team;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Team provider using vanilla Minecraft scoreboard teams.
 */
public class VanillaScoreboardProvider implements ITeamProvider {

    private static final Map<UUID, String> TEAM_NAME_CACHE = new ConcurrentHashMap<>();

    @Override
    public UUID getPlayerTeamId(ServerPlayer player) {
        if (player == null) return null;
        Scoreboard scoreboard = player.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (team != null) {
            UUID teamId = UUID.nameUUIDFromBytes(("vanilla_team:" + team.getName()).getBytes(StandardCharsets.UTF_8));
            TEAM_NAME_CACHE.put(teamId, team.getDisplayName().getString());
            return teamId;
        }
        return null; // Not in a vanilla scoreboard team
    }

    @Override
    public String getTeamDisplayName(UUID teamId) {
        if (teamId == null) return null;
        return TEAM_NAME_CACHE.get(teamId);
    }

    @Override
    public Set<UUID> getTeamMembers(UUID teamId) {
        return Collections.singleton(teamId);
    }

    @Override
    public boolean canPlayerEdit(ServerPlayer player, UUID teamId) {
        if (player == null || teamId == null) return false;
        UUID playerTeam = getPlayerTeamId(player);
        return teamId.equals(playerTeam);
    }

    @Override
    public boolean canPlayerAdministerTeam(ServerPlayer player, UUID teamId) {
        if (player == null || teamId == null) return false;
        if (player.hasPermissions(2)) return true;
        UUID playerTeam = getPlayerTeamId(player);
        return teamId.equals(playerTeam);
    }

    @Override
    public String getProviderId() {
        return "vanilla_scoreboard";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
