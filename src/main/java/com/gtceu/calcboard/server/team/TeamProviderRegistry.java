package com.gtceu.calcboard.server.team;

import net.minecraft.server.level.ServerPlayer;
import java.util.ArrayList;
import java.util.List;
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

    /**
     * Returns the highest-priority active team provider.
     */
    public ITeamProvider getActiveProvider() {
        for (ITeamProvider provider : providers) {
            if (provider.isAvailable()) {
                return provider;
            }
        }
        return fallbackProvider;
    }

    public UUID getPlayerTeamId(ServerPlayer player) {
        return getActiveProvider().getPlayerTeamId(player);
    }

    public String getTeamDisplayName(UUID teamId) {
        return getActiveProvider().getTeamDisplayName(teamId);
    }

    public boolean canPlayerEdit(ServerPlayer player, UUID teamId) {
        return getActiveProvider().canPlayerEdit(player, teamId);
    }
}
