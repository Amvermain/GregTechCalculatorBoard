package com.gtceu.calcboard.server.team;

import net.minecraft.server.level.ServerPlayer;
import java.util.Set;
import java.util.UUID;

/**
 * Abstraction layer for team management providers (FTB Teams, Vanilla Scoreboard, Standalone).
 * Ensures GTCalcBoard operates seamlessly regardless of whether FTB Teams is installed.
 */
public interface ITeamProvider {

    /**
     * Returns the unique UUID of the team the player belongs to.
     * If the player is not in a team, returns a distinct per-player fallback UUID.
     */
    UUID getPlayerTeamId(ServerPlayer player);

    /**
     * Returns a human-readable display name for the specified team ID.
     */
    String getTeamDisplayName(UUID teamId);

    /**
     * Returns the set of player UUIDs that are members of the specified team ID.
     */
    Set<UUID> getTeamMembers(UUID teamId);

    /**
     * Checks if the specified player has permission to edit the team's shared workspace.
     */
    boolean canPlayerEdit(ServerPlayer player, UUID teamId);

    /**
     * Returns the provider identifier (e.g. "ftbteams", "vanilla", "standalone").
     */
    String getProviderId();

    /**
     * Checks whether this provider is available and ready for use in the current runtime.
     */
    boolean isAvailable();
}
