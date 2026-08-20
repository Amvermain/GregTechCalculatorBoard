package com.gtceu.calcboard.server.team;

import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Registry and discovery manager for team providers.
 * Evaluates available providers in priority order: FTB Teams -> Vanilla Scoreboard -> Standalone Fallback.
 */
public class TeamProviderRegistry {

    private static TeamProviderRegistry instance;

    private final List<ITeamProvider> providers = new ArrayList<>();
    private final ITeamProvider fallbackProvider = new StandaloneFallbackProvider();

    private TeamProviderRegistry() {
        // Register in priority order
        providers.add(new FTBTeamsProvider());
        providers.add(new VanillaScoreboardProvider());
        providers.add(fallbackProvider);
    }

    public static synchronized TeamProviderRegistry getInstance() {
        if (instance == null) {
            instance = new TeamProviderRegistry();
        }
        return instance;
    }

    public ITeamProvider getActiveProvider() {
        for (ITeamProvider provider : providers) {
            if (provider.isAvailable()) {
                return provider;
            }
        }
        return fallbackProvider;
    }

    public UUID getPlayerTeamId(ServerPlayer player) {
        if (player == null) return null;
        for (ITeamProvider provider : providers) {
            if (provider.isAvailable()) {
                UUID id = provider.getPlayerTeamId(player);
                if (id != null) {
                    return id;
                }
            }
        }
        return fallbackProvider.getPlayerTeamId(player);
    }

    public String getTeamDisplayName(UUID teamId) {
        if (teamId == null) return "Shared Workspace";
        for (ITeamProvider provider : providers) {
            if (provider.isAvailable()) {
                String name = provider.getTeamDisplayName(teamId);
                if (name != null && !name.isEmpty() && !name.startsWith("Unknown")) {
                    return name;
                }
            }
        }
        return "Shared Workspace";
    }

    public Set<UUID> getTeamMembers(UUID teamId) {
        if (teamId == null) return Collections.emptySet();
        for (ITeamProvider provider : providers) {
            if (provider.isAvailable()) {
                Set<UUID> members = provider.getTeamMembers(teamId);
                if (members != null && !members.isEmpty()) {
                    return members;
                }
            }
        }
        return Collections.emptySet();
    }

    public boolean canPlayerEdit(ServerPlayer player, UUID teamId) {
        if (player == null || teamId == null) return false;
        for (ITeamProvider provider : providers) {
            if (provider.isAvailable()) {
                UUID pTeam = provider.getPlayerTeamId(player);
                if (pTeam != null && pTeam.equals(teamId)) {
                    return provider.canPlayerEdit(player, teamId);
                }
            }
        }
        return fallbackProvider.canPlayerEdit(player, teamId);
    }
}
