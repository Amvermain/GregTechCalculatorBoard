package com.gtceu.calcboard.server.team;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Team provider using vanilla Minecraft scoreboard teams.
 */
public class VanillaScoreboardProvider implements ITeamProvider {

    @Override
    public UUID getPlayerTeamId(ServerPlayer player) {
        if (player == null) return null;
        Scoreboard scoreboard = player.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(player.getScoreboardName());
        if (team != null) {
            return UUID.nameUUIDFromBytes(("vanilla_team:" + team.getName()).getBytes(StandardCharsets.UTF_8));
        }
        return player.getUUID();
    }

    @Override
    public String getTeamDisplayName(UUID teamId) {
        if (teamId == null) return "Unknown Team";
        return "Vanilla Team (" + teamId.toString().substring(0, 8) + ")";
    }

    @Override
    public Set<UUID> getTeamMembers(UUID teamId) {
        // Fallback for vanilla scoreboard team querying
        return Collections.singleton(teamId);
    }

    @Override
    public boolean canPlayerEdit(ServerPlayer player, UUID teamId) {
        if (player == null || teamId == null) return false;
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
